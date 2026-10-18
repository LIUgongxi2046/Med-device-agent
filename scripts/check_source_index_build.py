import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import subprocess
from uuid import uuid4
from xml.etree import ElementTree


ROOT = Path(__file__).resolve().parents[1]
DIRECTORY = ROOT / ".implementation-work/source-index-build-checks"
JAVA_HOME_PATH = ROOT / ".implementation-work/toolchains/jdk-17.0.20.1+1/Contents/Home"
MAVEN = ROOT / ".implementation-work/toolchains/apache-maven-3.9.16/bin/mvn"
TESTS = {
    "com.ruoyi.medical.data.MigrationBundleTest": 1,
    "com.ruoyi.medical.persistence.SourceIndexManifestIT": 3,
    "com.ruoyi.medical.persistence.SourceIndexBootstrapTest": 1,
    "com.ruoyi.medical.persistence.SourceIndexViewBootstrapTest": 1,
    "com.ruoyi.medical.persistence.TenantAccessTest": 6,
    "com.ruoyi.medical.persistence.SourceProcessingObjectIT": 1,
}


def digest(path):
    with path.open("rb") as source:
        return hashlib.file_digest(source, "sha256").hexdigest()


def snapshot():
    paths = {ROOT / "scripts/check_source_index_build.py", ROOT / "data-design/core.sql", ROOT / "contracts/catalogue.json", ROOT / "contracts/extensions/evaluation-results.catalogue.json", ROOT / "business/medical-persistence/src/main/resources/evaluation-results.schema.json"}
    for module in (ROOT / "business").iterdir():
        if not module.is_dir() or not (module / "pom.xml").is_file():
            continue
        paths.add(module / "pom.xml")
        source = module / "src/main"
        if source.is_dir():
            paths.update(path for path in source.rglob("*") if path.is_file())
    paths.add(ROOT / "business/pom.xml")
    for name in TESTS:
        module = "medical-data" if ".data." in name else "medical-persistence"
        paths.add(ROOT / "business" / module / "src/test/java" / (name.replace(".", "/") + ".java"))
    for directory in (ROOT / "contracts", ROOT / "data-design", ROOT / "agent-design", ROOT / "backend-design"):
        paths.update(path for path in directory.rglob("*") if path.is_file() and path.suffix in (".json", ".yaml", ".yml"))
    return {str(path.relative_to(ROOT)): digest(path) for path in sorted(paths)}


def save(path, value):
    descriptor = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600)
    with os.fdopen(descriptor, "w", encoding="utf-8") as output:
        json.dump(value, output, ensure_ascii=False, sort_keys=True, indent=2)
        output.flush()
        os.fsync(output.fileno())


def main():
    parser = argparse.ArgumentParser(description="固定索引源码、真实组件检查与应用制品")
    parser.add_argument("--check", choices=("technical", "application"), required=True)
    parser.add_argument("--storage-receipt")
    args = parser.parse_args()
    directory = DIRECTORY / str(uuid4())
    directory.mkdir(mode=0o700, parents=True)
    before = snapshot()
    command = [str(MAVEN), "-o", "--batch-mode", "--no-transfer-progress", "-f", str(ROOT / "business/pom.xml"),
        "-Dmaven.repo.local=" + str(ROOT / ".implementation-work/maven-repository")]
    if args.check == "technical":
        if args.storage_receipt is None:
            raise ValueError("SOURCE_INDEX_REAL_STORAGE_RECEIPT_REQUIRED")
        storage = Path(args.storage_receipt).resolve(strict=True)
        storage.relative_to(ROOT / ".implementation-work/source-processing-storage-checks")
        before[str(storage.relative_to(ROOT))] = digest(storage)
        command.extend(["-pl", "medical-persistence,medical-data", "-am",
            "-Dtest=" + ",".join(name.rsplit(".", 1)[1] for name in TESTS),
            "-Dsurefire.failIfNoSpecifiedTests=false", "-Dmedical.processing.storage=" + str(storage), "package"])
    else:
        command.extend(["-pl", "ruoyi-admin", "-am", "-DskipTests", "package"])
    environment = os.environ.copy()
    environment.update({"LANG": "en_US.UTF-8", "LC_ALL": "en_US.UTF-8", "JAVA_HOME": str(JAVA_HOME_PATH),
        "TMPDIR": str(ROOT / ".implementation-work/migration-check-tmp"),
        "MAVEN_OPTS": "-Dfile.encoding=UTF-8 -Djava.io.tmpdir=" + str(ROOT / ".implementation-work/java-temp")})
    log = directory / "maven.log"
    descriptor = os.open(log, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600)
    with os.fdopen(descriptor, "wb") as output:
        result = subprocess.run(command, cwd=ROOT, env=environment, stdout=output, stderr=subprocess.STDOUT, check=False)
    after = snapshot()
    if args.check == "technical":
        after[str(storage.relative_to(ROOT))] = digest(storage)
    reports = []
    if result.returncode == 0 and args.check == "technical":
        for name, expected in TESTS.items():
            module = "medical-data" if ".data." in name else "medical-persistence"
            path = ROOT / "business" / module / "target/surefire-reports" / ("TEST-" + name + ".xml")
            report = ElementTree.parse(path).getroot()
            counts = {field: int(report.attrib[field]) for field in ("tests", "failures", "errors", "skipped")}
            if report.attrib["name"] != name or counts != {"tests": expected, "failures": 0, "errors": 0, "skipped": 0}:
                raise ValueError("SOURCE_INDEX_REQUIRED_ASSERTIONS_FAILED")
            target = directory / path.name
            target.write_bytes(path.read_bytes())
            target.chmod(0o600)
            reports.append({"name": name, "counts": counts, "path": str(target.relative_to(ROOT)), "sha256": digest(target)})
    artifacts = []
    if result.returncode == 0:
        artifact_paths = [ROOT / "business/medical-persistence/target/medical-persistence-3.9.2.jar",
            ROOT / "business/medical-data/target/medical-data-3.9.2.jar", ROOT / "business/medical-data/target/medical-data-3.9.2-cli.jar"]
        if args.check == "application":
            artifact_paths.append(ROOT / "business/ruoyi-admin/target/ruoyi-admin.jar")
        artifacts = [{"path": str(path.relative_to(ROOT)), "sha256": digest(path), "size_bytes": path.stat().st_size}
            for path in artifact_paths]
    receipt = {"schema_version": "1.0.0", "check": args.check, "verified_at": datetime.now(timezone.utc).isoformat(),
        "exit_code": result.returncode, "command": command, "inputs_before": before, "inputs_after": after,
        "inputs_unchanged": before == after, "log": {"path": str(log.relative_to(ROOT)), "sha256": digest(log)},
        "reports": reports, "artifacts": artifacts, "customer_index_verified": False, "worker_enabled": False}
    target = directory / "receipt.json"
    save(target, receipt)
    print(json.dumps({"receipt": str(target.relative_to(ROOT)), "sha256": digest(target),
        "exit_code": result.returncode, "inputs_unchanged": before == after, "test_count": sum(row["counts"]["tests"] for row in reports)}))
    if result.returncode != 0 or before != after:
        raise RuntimeError("SOURCE_INDEX_BUILD_VERIFICATION_FAILED")


if __name__ == "__main__":
    main()
