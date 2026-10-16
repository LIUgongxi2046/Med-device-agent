import argparse
import hashlib
import json
import os
from pathlib import Path
import stat
from uuid import uuid4

import rfc8785
import yaml

from medical_document.scan import ClamAVScanner


ROOT = Path(__file__).resolve().parents[1]
RUNTIME = ROOT / ".implementation-work/runtime/source-processing"
RECEIPT = ROOT / ".implementation-work/IC-08-SOURCE-SCAN-20261008-OFFICIAL-01/receipt.json"
PREFIX = ROOT / ".implementation-work/toolchains/clamav-1.5.4-native"
DATABASE = ROOT / ".implementation-work/runtime/clamav/database"
CERTIFICATE = ROOT / ".implementation-work/runtime/clamav/config/certs/clamav.crt"


def save(path, content):
    descriptor = os.open(path, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
    with os.fdopen(descriptor, "wb") as stream:
        stream.write(content)
        stream.flush()
        os.fsync(stream.fileno())


def scanner_release():
    receipt = json.loads(RECEIPT.read_bytes())
    if receipt["exit_code"] != 0 or receipt["inputs_unchanged"] is not True:
        raise ValueError("SCANNER_RELEASE_NOT_VERIFIED")
    assets = {item["path"]: item for item in receipt["inputs"]}

    def artifact(path):
        path = path.resolve(strict=True)
        item = assets[str(path)]
        with path.open("rb") as content:
            if hashlib.file_digest(content, "sha256").hexdigest() != item["sha256"]:
                raise ValueError("SCANNER_RELEASE_CHANGED")
        return {"path": str(path), "sha256": item["sha256"], "size_bytes": path.stat().st_size}

    signatures = json.loads((RECEIPT.parent / "scan-result.json").read_bytes())["databases"]
    libraries = sorted({path.resolve(strict=True) for path in (PREFIX / "lib").glob("*.dylib")})
    release = {"schema_version": "1.0.0", "version": "1.5.4", "clamscan": artifact(PREFIX / "bin/clamscan"),
        "sigtool": artifact(PREFIX / "bin/sigtool"), "certificate": artifact(CERTIFICATE),
        "libraries": [artifact(path) for path in libraries],
        "databases": [{"name": name, "database": artifact(DATABASE / (name + ".cvd")),
            "signature": artifact(Path(next(item["path"] for item in signatures
                if Path(item["path"]).name.startswith(name + "-") and Path(item["path"]).suffix == ".sign")))}
            for name in ("main", "daily", "bytecode")]}
    ClamAVScanner(release)
    return release


def prepare(configuration_directory=None):
    RUNTIME.mkdir(mode=0o700, parents=True, exist_ok=True)
    if RUNTIME.resolve(strict=True) != RUNTIME or stat.S_IMODE(RUNTIME.stat().st_mode) != 0o700:
        raise ValueError("PROCESSING_RUNTIME_DIRECTORY_INVALID")
    release = scanner_release()
    release_bytes = rfc8785.dumps(release)
    release_path = RUNTIME / "scanner-release.json"
    if release_path.exists():
        if stat.S_IMODE(release_path.stat().st_mode) != 0o600 or release_path.read_bytes() != release_bytes:
            raise ValueError("PROCESSING_SCANNER_CONFIGURATION_CHANGED")
    else:
        save(release_path, release_bytes)
    python = ROOT / ".implementation-work/document-venv/bin/python"
    python.resolve(strict=True)
    with python.open("rb") as stream:
        python_sha256 = hashlib.file_digest(stream, "sha256").hexdigest()
    files = []
    for directory in ("document/src/medical_document", "contracts/python/src/medical_contracts"):
        for path in sorted((ROOT / directory).rglob("*.py")):
            files.append({"path": str(path.relative_to(ROOT)), "sha256": hashlib.sha256(path.read_bytes()).hexdigest()})
    settings = {"enabled": False, "revision": str(uuid4()), "tenant-ids": [], "bindings": [],
        "project-root": str(ROOT), "python-executable": str(python), "python-sha256": python_sha256,
        "polling": "5s", "lease": "45s", "heartbeat": "10s", "timeout": "15m", "maximum-source-bytes": 104857600,
        "maximum-bundle-bytes": 268435456, "environment": "LOCAL", "region": "LOCAL_PRIVATE", "source-files": files}
    profile_directory = RUNTIME if configuration_directory is None else Path(configuration_directory).absolute()
    profile_directory.mkdir(mode=0o700, parents=True, exist_ok=True)
    profile_directory.resolve(strict=True).relative_to(RUNTIME)
    if profile_directory.resolve(strict=True) != profile_directory or stat.S_IMODE(profile_directory.stat().st_mode) != 0o700:
        raise ValueError("PROCESSING_CONFIGURATION_DIRECTORY_INVALID")
    profile_path = profile_directory / "application-source-processing.yaml"
    profile = {"medical": {"source-processing": settings}}
    if profile_path.exists():
        current = yaml.safe_load(profile_path.read_bytes())
        settings["revision"] = current["medical"]["source-processing"]["revision"]
        if current != profile or stat.S_IMODE(profile_path.stat().st_mode) != 0o600:
            raise ValueError("PROCESSING_PROFILE_CHANGED")
    else:
        save(profile_path, yaml.safe_dump(profile, allow_unicode=True, sort_keys=False).encode("utf-8"))
    receipt = {"schema_version": "1.0.0", "profile_path": str(profile_path), "profile_sha256": hashlib.sha256(profile_path.read_bytes()).hexdigest(),
        "revision": settings["revision"], "scanner_release_path": str(release_path),
        "scanner_release_sha256": hashlib.sha256(release_bytes).hexdigest(), "python_sha256": python_sha256,
        "source_files": files, "enabled": False, "published_binding_verified": False, "customer_processing_verified": False}
    receipt_path = profile_directory / ("preparation-" + str(uuid4()) + ".json")
    save(receipt_path, rfc8785.dumps(receipt))
    print(json.dumps({"receipt": str(receipt_path), "profile": str(profile_path), "source_file_count": len(files), "enabled": False}))


def prepare_application(deletion_profile, processing_profile=None):
    path = Path(deletion_profile).resolve(strict=True)
    path.relative_to(ROOT / ".implementation-work/runtime")
    processing_path = RUNTIME / "application-source-processing.yaml" if processing_profile is None else Path(processing_profile).absolute()
    processing_path.resolve(strict=True).relative_to(RUNTIME)
    for item in (path, processing_path):
        if item.is_symlink() or item.stat().st_uid != os.getuid() or stat.S_IMODE(item.stat().st_mode) != 0o600:
            raise ValueError("PROCESSING_APPLICATION_PROFILE_INVALID")
    deletion = yaml.safe_load(path.read_bytes())
    processing = yaml.safe_load(processing_path.read_bytes())
    if set(deletion) != {"medical"} or set(processing) != {"medical"} \
            or set(deletion["medical"]) != {"source-deletion"} or set(processing["medical"]) != {"source-processing"}:
        raise ValueError("PROCESSING_APPLICATION_PROFILE_SCOPE_INVALID")
    for configuration in (deletion["medical"]["source-deletion"], processing["medical"]["source-processing"]):
        if configuration["enabled"] is not False or configuration["tenant-ids"]:
            raise ValueError("PROCESSING_APPLICATION_REQUIRES_APPROVED_TENANT_SCOPE")
    identifier = str(uuid4())
    directory = ROOT / ".implementation-work/runtime/application-profiles" / identifier
    directory.mkdir(mode=0o700, parents=True)
    combined = {"medical": {**deletion["medical"], **processing["medical"], "source-completion": {"enabled": False, "bindings": []}}}
    profile = directory / "application-source-workers.yaml"
    save(profile, yaml.safe_dump(combined, allow_unicode=True, sort_keys=False).encode("utf-8"))
    receipt = {"schema_version": "1.0.0", "profile": str(profile), "profile_sha256": hashlib.sha256(profile.read_bytes()).hexdigest(),
        "inputs": [{"path": str(item), "sha256": hashlib.sha256(item.read_bytes()).hexdigest()} for item in (path, processing_path)],
        "source_processing_enabled": False, "source_deletion_enabled": False, "source_completion_enabled": False,
        "customer_processing_verified": False}
    save(directory / "receipt.json", rfc8785.dumps(receipt))
    print(json.dumps({"profile": str(profile), "receipt": str(directory / "receipt.json")}))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="准备实际本地来源处理制品与扫描配置")
    parser.add_argument("action", choices=("prepare", "prepare-application"))
    parser.add_argument("--deletion-profile")
    parser.add_argument("--processing-profile")
    parser.add_argument("--configuration-directory")
    args = parser.parse_args()
    if args.action == "prepare":
        if args.deletion_profile is not None or args.processing_profile is not None:
            parser.error("prepare不能指定删除配置")
        prepare(args.configuration_directory)
    else:
        if args.deletion_profile is None:
            parser.error("prepare-application需要原删除配置路径")
        if args.configuration_directory is not None:
            parser.error("prepare-application不能指定配置输出目录")
        prepare_application(args.deletion_profile, args.processing_profile)
