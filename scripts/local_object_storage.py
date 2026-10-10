import argparse
from contextlib import closing
import fcntl
import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import shutil
import signal
import stat
import subprocess
import tarfile
import time
import uuid

import boto3
from botocore.config import Config


ROOT = Path(__file__).resolve().parents[1]
TOOLCHAIN = ROOT / ".implementation-work/toolchains/seaweedfs-4.48"
DIRECTORY = ROOT / ".implementation-work/runtime/object-storage"
ARCHIVE_SHA256 = "425ef61d2ee773d5d5175003f38bd6cc891070ae6319370354aaf5378c45d5b3"
LICENSE_SHA256 = "d789d433cc11da163273d1e39be2e8fa67642f9a58ef220d3f258fa9c14ef613"
BUCKET = "medical-local-artifacts"
ENDPOINT = "http://127.0.0.1:18333"
IDENTITY = DIRECTORY / "deployment.json"
SECRET = DIRECTORY / "service-secret.json"


def digest(path):
    with path.open("rb") as source:
        return hashlib.file_digest(source, "sha256").hexdigest()


def private_file(path):
    descriptor = os.open(path, os.O_RDONLY | os.O_NOFOLLOW)
    with os.fdopen(descriptor, "rb") as source:
        identity = os.fstat(source.fileno())
        if not stat.S_ISREG(identity.st_mode) or identity.st_uid != os.getuid() or stat.S_IMODE(identity.st_mode) != 0o600:
            raise ValueError("OBJECT_STORAGE_PRIVATE_FILE_INVALID")
        return source.read()


def save_private(path, value):
    descriptor = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600)
    with os.fdopen(descriptor, "w", encoding="utf-8") as output:
        json.dump(value, output, ensure_ascii=False)
        output.flush()
        os.fsync(output.fileno())


def install():
    archive = TOOLCHAIN / "darwin_amd64.tar.gz"
    if archive.is_symlink() or digest(archive) != ARCHIVE_SHA256:
        raise ValueError("OBJECT_STORAGE_OFFICIAL_ARCHIVE_MISMATCH")
    license_file = TOOLCHAIN / "LICENSE"
    if license_file.is_symlink() or digest(license_file) != LICENSE_SHA256:
        raise ValueError("OBJECT_STORAGE_OFFICIAL_LICENSE_MISMATCH")
    target = TOOLCHAIN / "weed"
    if target.exists():
        raise ValueError("OBJECT_STORAGE_BINARY_ALREADY_PRESENT")
    with tarfile.open(archive, "r:gz") as source:
        members = [member for member in source.getmembers() if Path(member.name).name == "weed"]
        if len(members) != 1 or not members[0].isfile():
            raise ValueError("OBJECT_STORAGE_ARCHIVE_BINARY_INVALID")
        with source.extractfile(members[0]) as original, target.open("xb") as output:
            shutil.copyfileobj(original, output)
            output.flush()
            os.fsync(output.fileno())
    target.chmod(0o500)
    version = subprocess.run([str(target), "version"], capture_output=True, text=True, check=True, timeout=15).stdout.strip()
    if "4.48" not in version:
        raise ValueError("OBJECT_STORAGE_BINARY_VERSION_INVALID")
    save_private(TOOLCHAIN / "installation.json", {"scope": "OFFICIAL_LOCAL_TOOLCHAIN", "version": version,
        "archive_sha256": ARCHIVE_SHA256, "binary_sha256": digest(target), "os_uid": os.getuid(),
        "license": "Apache-2.0", "license_sha256": LICENSE_SHA256,
        "source": "https://api.github.com/repos/seaweedfs/seaweedfs/releases/assets/595824418"})
    print("OBJECT_STORAGE_TOOLCHAIN_INSTALLED " + version)


def prepare():
    DIRECTORY.mkdir(parents=True, exist_ok=True, mode=0o700)
    if DIRECTORY.is_symlink() or DIRECTORY.stat().st_uid != os.getuid() or stat.S_IMODE(DIRECTORY.stat().st_mode) != 0o700:
        raise ValueError("OBJECT_STORAGE_DIRECTORY_INVALID")
    if SECRET.exists() or IDENTITY.exists() or (DIRECTORY / "data").exists():
        raise ValueError("OBJECT_STORAGE_ALREADY_PREPARED")
    for name in ("data", "tmp"):
        (DIRECTORY / name).mkdir(mode=0o700)
    # 独立用途的技术秘密不能由客户资料或业务批准编号推导。
    save_private(SECRET, {"service_id": str(uuid.uuid4()), "access_key": secrets.token_hex(16),
        "secret_key": secrets.token_hex(32), "sse_kek": secrets.token_hex(32)})
    print("OBJECT_STORAGE_TECHNICAL_IDENTITY_PREPARED")


def settings():
    directory = DIRECTORY.lstat()
    if not stat.S_ISDIR(directory.st_mode) or directory.st_uid != os.getuid() or stat.S_IMODE(directory.st_mode) != 0o700:
        raise ValueError("OBJECT_STORAGE_DIRECTORY_INVALID")
    value = json.loads(private_file(SECRET))
    if set(value) != {"service_id", "access_key", "secret_key", "sse_kek"}:
        raise ValueError("OBJECT_STORAGE_SECRET_FIELDS_INVALID")
    if str(uuid.UUID(value["service_id"])) != value["service_id"]:
        raise ValueError("OBJECT_STORAGE_SERVICE_ID_INVALID")
    for name, length in (("access_key", 32), ("secret_key", 64), ("sse_kek", 64)):
        if not isinstance(value[name], str) or re.fullmatch("[a-f0-9]{" + str(length) + "}", value[name]) is None:
            raise ValueError("OBJECT_STORAGE_SECRET_INVALID")
    return value


def command():
    return [str(TOOLCHAIN / "weed"), "mini", "-dir=" + str(DIRECTORY / "data"),
        "-ip=127.0.0.1", "-ip.bind=127.0.0.1", "-master.port=19333", "-volume.port=19340",
        "-filer.port=18888", "-s3.port=18333", "-s3.port.iceberg=0", "-s3.port.lance=0",
        "-webdav=false", "-admin.ui=false", "-admin.port=19646", "-s3.iam.readOnly=true",
        "-master.telemetry=false", "-filer.localSocket=filer.sock", "-s3.localSocket=s3.sock",
        "-s3.allowDeleteBucketNotEmpty=false", "-s3.autoCreateBucket=false", "-bucket=" + BUCKET]


def start():
    values = settings()
    installation = json.loads(private_file(TOOLCHAIN / "installation.json"))
    binary = TOOLCHAIN / "weed"
    if binary.is_symlink() or digest(binary) != installation["binary_sha256"] or installation["os_uid"] != os.getuid():
        raise ValueError("OBJECT_STORAGE_TOOLCHAIN_IDENTITY_CHANGED")
    if IDENTITY.exists():
        raise ValueError("OBJECT_STORAGE_DEPLOYMENT_ALREADY_PRESENT")
    environment = os.environ.copy()
    environment.update({"TMPDIR": str(DIRECTORY / "tmp"), "AWS_ACCESS_KEY_ID": values["access_key"],
        "AWS_SECRET_ACCESS_KEY": values["secret_key"], "WEED_S3_SSE_KEK": values["sse_kek"], "LC_ALL": "en_US.UTF-8"})
    mask = os.umask(0o077)
    try:
        with (DIRECTORY / "service.log").open("ab") as output:
            process = subprocess.Popen(command(), cwd=DIRECTORY, env=environment, stdout=output,
                stderr=output, start_new_session=True)
    finally:
        os.umask(mask)
    deployment = {"scope": "LOCAL_PRIVATE_OBJECT_STORAGE", "deployment_id": str(uuid.uuid4()),
        "service_id": values["service_id"], "pid": process.pid, "os_uid": os.getuid(), "endpoint": ENDPOINT,
        "bucket": BUCKET, "binary_sha256": installation["binary_sha256"], "archive_sha256": ARCHIVE_SHA256,
        "command": command(), "started_at": time.time()}
    save_private(DIRECTORY / ("deployment-" + deployment["deployment_id"] + ".json"), deployment)
    save_private(IDENTITY, deployment)
    print("OBJECT_STORAGE_PROCESS_STARTED " + json.dumps({key: deployment[key] for key in ("deployment_id", "pid", "scope")}))


def verify_identity():
    value = json.loads(private_file(IDENTITY))
    if value["os_uid"] != os.getuid() or value["endpoint"] != ENDPOINT or value["bucket"] != BUCKET:
        raise ValueError("OBJECT_STORAGE_DEPLOYMENT_IDENTITY_INVALID")
    if value["service_id"] != settings()["service_id"] or value["command"] != command():
        raise ValueError("OBJECT_STORAGE_DEPLOYMENT_CONFIG_CHANGED")
    if value["archive_sha256"] != ARCHIVE_SHA256 or digest(TOOLCHAIN / "weed") != value["binary_sha256"]:
        raise ValueError("OBJECT_STORAGE_DEPLOYMENT_BINARY_CHANGED")
    pid = value["pid"]
    if type(pid) is not int or pid <= 1:
        raise ValueError("OBJECT_STORAGE_PID_INVALID")
    for field, expected in (("uid", str(os.getuid())), ("comm", str(TOOLCHAIN / "weed")), ("args", " ".join(command()))):
        observed = subprocess.run(["/bin/ps", "-ww", "-p", str(pid), "-o", field + "="],
            capture_output=True, text=True, check=True, timeout=10, env={**os.environ, "LC_ALL": "en_US.UTF-8"}).stdout.strip()
        if observed != expected:
            raise ValueError("OBJECT_STORAGE_PROCESS_IDENTITY_CHANGED")
    return value


def client():
    values = settings()
    return boto3.client("s3", endpoint_url=ENDPOINT, region_name="us-east-1", aws_access_key_id=values["access_key"],
        aws_secret_access_key=values["secret_key"], config=Config(signature_version="s3v4", connect_timeout=5,
        read_timeout=60, retries={"total_max_attempts": 1}, s3={"addressing_style": "path"},
        proxies={}, request_checksum_calculation="when_required", response_checksum_validation="when_required"))


def protect():
    deployment = verify_identity()
    with closing(client()) as storage:
        storage.head_bucket(Bucket=BUCKET)
        storage.put_bucket_versioning(Bucket=BUCKET, VersioningConfiguration={"Status": "Enabled"})
        storage.put_bucket_encryption(Bucket=BUCKET, ServerSideEncryptionConfiguration={"Rules": [
            {"ApplyServerSideEncryptionByDefault": {"SSEAlgorithm": "AES256"}}]})
        storage.put_bucket_acl(Bucket=BUCKET, ACL="private")
        versioning = storage.get_bucket_versioning(Bucket=BUCKET)
        encryption = storage.get_bucket_encryption(Bucket=BUCKET)["ServerSideEncryptionConfiguration"]
        acl = storage.get_bucket_acl(Bucket=BUCKET)
        rules = encryption["Rules"]
        if versioning.get("Status") != "Enabled" or len(rules) != 1 or rules[0]["ApplyServerSideEncryptionByDefault"] != {"SSEAlgorithm": "AES256"}:
            raise ValueError("OBJECT_STORAGE_PROTECTION_READBACK_MISMATCH")
        grants = acl["Grants"]
        if len(grants) != 1 or grants[0]["Permission"] != "FULL_CONTROL" or grants[0]["Grantee"]["Type"] != "CanonicalUser" or grants[0]["Grantee"]["ID"] != acl["Owner"]["ID"]:
            raise ValueError("OBJECT_STORAGE_PRIVATE_ACL_MISMATCH")
    if deployment != verify_identity():
        raise ValueError("OBJECT_STORAGE_PROTECTION_PROCESS_CHANGED")
    save_private(DIRECTORY / ("protection-" + str(uuid.uuid4()) + ".json"), {
        "scope": "LOCAL_PRIVATE_OBJECT_STORAGE", "deployment_id": deployment["deployment_id"],
        "service_id": deployment["service_id"], "bucket": BUCKET, "versioning": "Enabled", "encryption": "AES256",
        "access": "PRIVATE_OWNER_ONLY", "verified_at": time.time()})
    print("OBJECT_STORAGE_PROTECTION_CONFIGURED_AND_READ_BACK")


def stop():
    value = verify_identity()
    os.kill(value["pid"], signal.SIGTERM)
    deadline = time.monotonic() + 30
    while time.monotonic() < deadline:
        process = subprocess.run(["/bin/ps", "-ww", "-p", str(value["pid"]), "-o", "comm="],
            capture_output=True, text=True, check=False, timeout=5, env={**os.environ, "LC_ALL": "en_US.UTF-8"})
        if process.returncode == 1 and not process.stdout.strip():
            save_private(DIRECTORY / ("stopped-" + value["deployment_id"] + ".json"), value)
            IDENTITY.unlink()
            print("OBJECT_STORAGE_PROCESS_STOPPED " + value["deployment_id"])
            return
        if process.returncode != 0 or process.stdout.strip() != str(TOOLCHAIN / "weed"):
            raise ValueError("OBJECT_STORAGE_STOP_PROCESS_IDENTITY_CHANGED")
        time.sleep(0.2)
    raise TimeoutError("OBJECT_STORAGE_PROCESS_STOP_TIMEOUT")


def main():
    parser = argparse.ArgumentParser(description="运行本地私有对象存储及核查技术服务身份")
    parser.add_argument("action", choices=("install", "prepare", "start", "protect", "identity", "stop"))
    args = parser.parse_args()
    lock = ROOT / ".implementation-work/object-storage-runtime.lock"
    with lock.open("a+") as handle:
        fcntl.flock(handle, fcntl.LOCK_EX | fcntl.LOCK_NB)
        if args.action == "identity":
            print(json.dumps(verify_identity(), ensure_ascii=False))
        else:
            {"install": install, "prepare": prepare, "start": start, "protect": protect, "stop": stop}[args.action]()


if __name__ == "__main__":
    main()
