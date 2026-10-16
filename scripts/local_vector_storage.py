import argparse
import fcntl
import hashlib
import json
import os
from pathlib import Path
import platform
import secrets
import shutil
import signal
import stat
import subprocess
import tarfile
import time
import urllib.request
import uuid

import yaml


ROOT = Path(__file__).resolve().parents[1]
DIRECTORY = ROOT / ".implementation-work/runtime/vector-storage"
TOOLCHAIN = ROOT / ".implementation-work/toolchains/qdrant-1.19.2"
ARCHIVE = ROOT / ".implementation-work/qdrant-x86_64-apple-darwin-1.19.2.tar.gz"
ARCHIVE_SHA256 = "e095b682ad7460baff43827eaa13647b5ec97c48edcb676646ae2f4108395044"
ENDPOINT = "http://127.0.0.1:16333"
CONFIGURATION = DIRECTORY / "config.yaml"
IDENTITY = DIRECTORY / "deployment.json"
API_KEY = DIRECTORY / "api-key.txt"


def digest(path):
    with path.open("rb") as source:
        return hashlib.file_digest(source, "sha256").hexdigest()


def private_file(path):
    descriptor = os.open(path, os.O_RDONLY | os.O_NOFOLLOW)
    with os.fdopen(descriptor, "rb") as source:
        identity = os.fstat(source.fileno())
        if not stat.S_ISREG(identity.st_mode) or identity.st_uid != os.getuid() or stat.S_IMODE(identity.st_mode) != 0o600:
            raise ValueError("VECTOR_PRIVATE_FILE_INVALID")
        return source.read()


def save_private(path, value):
    descriptor = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600)
    with os.fdopen(descriptor, "wb") as output:
        output.write(value)
        output.flush()
        os.fsync(output.fileno())


def install():
    if platform.system() != "Darwin" or platform.machine() != "x86_64" or ARCHIVE.is_symlink() or digest(ARCHIVE) != ARCHIVE_SHA256:
        raise ValueError("VECTOR_OFFICIAL_ARCHIVE_MISMATCH")
    TOOLCHAIN.mkdir(mode=0o700)
    target = TOOLCHAIN / "qdrant"
    with tarfile.open(ARCHIVE, "r:gz") as source:
        members = [member for member in source.getmembers() if Path(member.name).name == "qdrant"]
        if len(members) != 1 or not members[0].isfile():
            raise ValueError("VECTOR_ARCHIVE_BINARY_INVALID")
        with source.extractfile(members[0]) as original, target.open("xb") as output:
            shutil.copyfileobj(original, output)
    target.chmod(0o500)
    register()


def register():
    target = TOOLCHAIN / "qdrant"
    if ARCHIVE.is_symlink() or digest(ARCHIVE) != ARCHIVE_SHA256 or target.is_symlink() \
            or target.stat().st_uid != os.getuid() or stat.S_IMODE(target.stat().st_mode) != 0o500:
        raise ValueError("VECTOR_TOOLCHAIN_IDENTITY_INVALID")
    with tarfile.open(ARCHIVE,"r:gz") as archive:
        members = [member for member in archive.getmembers() if Path(member.name).name == "qdrant"]
        if len(members) != 1 or not members[0].isfile():
            raise ValueError("VECTOR_ARCHIVE_BINARY_INVALID")
        with archive.extractfile(members[0]) as source:
            if hashlib.file_digest(source,"sha256").hexdigest() != digest(target):
                raise ValueError("VECTOR_EXTRACTED_BINARY_MISMATCH")
    environment = {**os.environ, "TMPDIR": str(ROOT / ".implementation-work/java-temp")}
    version = subprocess.run([str(target), "--version"], check=True, capture_output=True, text=True,
        timeout=10, env=environment).stdout.strip()
    if version != "qdrant 1.19.2":
        raise ValueError("VECTOR_BINARY_VERSION_INVALID")
    save_private(TOOLCHAIN / "installation.json", json.dumps({"version": version, "binary_sha256": digest(target),
        "archive_sha256": ARCHIVE_SHA256, "os_uid": os.getuid(), "license": "Apache-2.0",
        "source": "https://github.com/qdrant/qdrant/releases/tag/v1.19.2"}).encode())
    print("VECTOR_TOOLCHAIN_INSTALLED " + version)


def prepare():
    DIRECTORY.mkdir(mode=0o700)
    for name in ("data", "snapshots", "tmp"):
        (DIRECTORY / name).mkdir(mode=0o700)
    save_private(API_KEY, secrets.token_hex(32).encode())
    config = {"log_level": "INFO", "telemetry_disabled": True,
        "storage": {"storage_path": str(DIRECTORY / "data"), "snapshots_path": str(DIRECTORY / "snapshots"),
            "temp_path": str(DIRECTORY / "tmp"), "performance": {"max_search_threads": 2, "optimizer_cpu_budget": 1}},
        "service": {"host": "127.0.0.1", "http_port": 16333, "grpc_port": 16334, "enable_cors": False,
            "enable_tls": False, "max_workers": 2, "api_key": private_file(API_KEY).decode()},
        "cluster": {"enabled": False}}
    save_private(CONFIGURATION, yaml.safe_dump(config, allow_unicode=True).encode())
    print("VECTOR_PRIVATE_CONFIGURATION_PREPARED")


def command():
    return [str(TOOLCHAIN / "qdrant"), "--config-path", str(CONFIGURATION), "--disable-telemetry"]


def start():
    installation = json.loads(private_file(TOOLCHAIN / "installation.json"))
    if IDENTITY.exists() or digest(TOOLCHAIN / "qdrant") != installation["binary_sha256"] or installation["os_uid"] != os.getuid():
        raise ValueError("VECTOR_DEPLOYMENT_IDENTITY_INVALID")
    configuration = yaml.safe_load(private_file(CONFIGURATION))
    if configuration["service"]["api_key"] != private_file(API_KEY).decode():
        raise ValueError("VECTOR_CONFIGURATION_CHANGED")
    environment = {**os.environ, "TMPDIR": str(DIRECTORY / "tmp"), "LC_ALL": "en_US.UTF-8"}
    descriptor = os.open(DIRECTORY / "service.log", os.O_WRONLY | os.O_CREAT | os.O_APPEND | os.O_NOFOLLOW, 0o600)
    with os.fdopen(descriptor, "ab") as output:
        process = subprocess.Popen(command(), cwd=DIRECTORY, env=environment, stdout=output,
            stderr=output, start_new_session=True)
    identity = {"deployment_id": str(uuid.uuid4()), "pid": process.pid, "os_uid": os.getuid(),
        "binary_sha256": installation["binary_sha256"], "config_sha256": digest(CONFIGURATION),
        "endpoint": ENDPOINT, "grpc_port": 16334, "command": command(), "scope": "LOCAL_PRIVATE_VECTOR_STORAGE"}
    save_private(IDENTITY, json.dumps(identity, ensure_ascii=False).encode())
    save_private(DIRECTORY / ("deployment-" + identity["deployment_id"] + ".json"), private_file(IDENTITY))
    print("VECTOR_PROCESS_STARTED " + json.dumps({key: identity[key] for key in ("deployment_id", "pid", "scope")}))


def verify_identity():
    identity = json.loads(private_file(IDENTITY))
    if identity["os_uid"] != os.getuid() or identity["binary_sha256"] != digest(TOOLCHAIN / "qdrant") \
            or identity["config_sha256"] != digest(CONFIGURATION) or identity["command"] != command():
        raise ValueError("VECTOR_PROCESS_CONFIGURATION_CHANGED")
    if type(identity["pid"]) is not int or identity["pid"] <= 1:
        raise ValueError("VECTOR_PROCESS_IDENTITY_INVALID")
    for field, expected in (("uid", str(os.getuid())), ("comm", str(TOOLCHAIN / "qdrant")), ("args", " ".join(command()))):
        observed = subprocess.run(["/bin/ps", "-ww", "-p", str(identity["pid"]), "-o", field + "="],
            check=True, capture_output=True, text=True, timeout=10,
            env={**os.environ, "LC_ALL": "en_US.UTF-8"}).stdout.strip()
        if observed != expected:
            raise ValueError("VECTOR_PROCESS_IDENTITY_CHANGED")
    return identity


def verify():
    identity = verify_identity()
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    headers = {"api-key": private_file(API_KEY).decode()}
    with opener.open(urllib.request.Request(ENDPOINT + "/", headers=headers), timeout=10) as response:
        metadata = json.load(response)
    with opener.open(urllib.request.Request(ENDPOINT + "/collections", headers=headers), timeout=10) as response:
        collections = json.load(response)
    if metadata["version"] != "1.19.2" or collections["status"] != "ok":
        raise ValueError("VECTOR_CONNECTION_READBACK_INVALID")
    anonymous = subprocess.run(["curl", "--noproxy", "*", "--silent", "--max-time", "10", "--output", os.devnull,
        "--write-out", "%{http_code}", ENDPOINT + "/collections"], check=True, capture_output=True, text=True)
    if anonymous.stdout != "401":
        raise ValueError("VECTOR_ANONYMOUS_ACCESS_NOT_REJECTED")
    if verify_identity() != identity:
        raise ValueError("VECTOR_VERIFICATION_PROCESS_CHANGED")
    receipt = {"deployment_id": identity["deployment_id"], "version": metadata["version"],
        "collections": [item["name"] for item in collections["result"]["collections"]],
        "anonymous_status": anonymous.stdout, "config_sha256": identity["config_sha256"], "verified_at": time.time()}
    save_private(DIRECTORY / ("verification-" + str(uuid.uuid4()) + ".json"), json.dumps(receipt).encode())
    print("VECTOR_CONNECTION_VERIFIED " + json.dumps(receipt))


def stop():
    identity = verify_identity()
    os.kill(identity["pid"], signal.SIGTERM)
    deadline = time.monotonic() + 30
    while time.monotonic() < deadline:
        process = subprocess.run(["/bin/ps", "-p", str(identity["pid"]), "-o", "pid="],
            capture_output=True, text=True, check=False, timeout=5)
        if process.returncode == 1 and not process.stdout.strip():
            IDENTITY.rename(DIRECTORY / ("stopped-" + identity["deployment_id"] + ".json"))
            print("VECTOR_PROCESS_STOPPED")
            return
        if process.returncode != 0:
            raise ValueError("VECTOR_STOP_IDENTITY_INVALID")
        time.sleep(0.2)
    raise TimeoutError("VECTOR_STOP_TIMEOUT")


def archive_terminated():
    identity = json.loads(private_file(IDENTITY))
    if identity["os_uid"] != os.getuid() or identity["endpoint"] != ENDPOINT or identity["command"] != command() \
            or digest(TOOLCHAIN / "qdrant") != identity["binary_sha256"] or digest(CONFIGURATION) != identity["config_sha256"] \
            or type(identity["pid"]) is not int or identity["pid"] <= 1:
        raise ValueError("VECTOR_TERMINATED_IDENTITY_INVALID")
    result = subprocess.run(["/bin/ps", "-p", str(identity["pid"]), "-o", "pid="],
        capture_output=True, text=True, check=False, timeout=5)
    if result.returncode != 1 or result.stdout.strip():
        raise ValueError("VECTOR_ORIGINAL_PROCESS_STILL_PRESENT")
    destination = DIRECTORY / ("terminated-" + identity["deployment_id"] + ".json")
    if destination.exists():
        raise ValueError("VECTOR_TERMINATED_IDENTITY_ALREADY_PRESENT")
    IDENTITY.rename(destination)
    print("VECTOR_TERMINATED_IDENTITY_ARCHIVED " + identity["deployment_id"])


def main():
    parser = argparse.ArgumentParser(description="运行项目专用 Qdrant 并核验当前连接")
    parser.add_argument("action", choices=("install", "register", "prepare", "start", "verify", "stop", "archive-terminated"))
    args = parser.parse_args()
    with (ROOT / ".implementation-work/vector-storage-runtime.lock").open("a+") as lock:
        os.chmod(lock.fileno(),0o600)
        fcntl.flock(lock,fcntl.LOCK_EX | fcntl.LOCK_NB)
        {"install": install, "register": register, "prepare": prepare, "start": start, "verify": verify, "stop": stop,
            "archive-terminated": archive_terminated}[args.action]()


if __name__ == "__main__":
    main()
