import argparse
import fcntl
from contextlib import closing
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import secrets
import stat
import uuid

import yaml

import local_object_storage as objects


ROOT = Path(__file__).resolve().parents[1]
DIRECTORY = ROOT / ".implementation-work/runtime/source-deletion"
DIAGNOSTIC_BUCKET = "medical-local-diagnostics"
BACKUP_BUCKET = "medical-local-backups"


def digest(path):
    with path.open("rb") as source:
        return hashlib.file_digest(source, "sha256").hexdigest()


def save_private(path, value):
    descriptor = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600)
    with os.fdopen(descriptor, "wb") as output:
        output.write(value)
        output.flush()
        os.fsync(output.fileno())


def protect(storage, bucket):
    storage.put_bucket_versioning(Bucket=bucket, VersioningConfiguration={"Status": "Enabled"})
    storage.put_bucket_encryption(Bucket=bucket, ServerSideEncryptionConfiguration={"Rules": [
        {"ApplyServerSideEncryptionByDefault": {"SSEAlgorithm": "AES256"}}]})
    storage.put_bucket_acl(Bucket=bucket, ACL="private")
    return verify_bucket(storage, bucket)


def verify_bucket(storage, bucket):
    storage.head_bucket(Bucket=bucket)
    versioning = storage.get_bucket_versioning(Bucket=bucket)
    encryption = storage.get_bucket_encryption(Bucket=bucket)["ServerSideEncryptionConfiguration"]
    acl = storage.get_bucket_acl(Bucket=bucket)
    if versioning.get("Status") != "Enabled" or len(encryption["Rules"]) != 1 \
            or encryption["Rules"][0]["ApplyServerSideEncryptionByDefault"] != {"SSEAlgorithm": "AES256"}:
        raise ValueError("DELETION_BUCKET_PROTECTION_INVALID")
    if len(acl["Grants"]) != 1 or acl["Grants"][0]["Grantee"].get("ID") != acl["Owner"]["ID"] \
            or acl["Grants"][0]["Permission"] != "FULL_CONTROL":
        raise ValueError("DELETION_BUCKET_ACCESS_INVALID")
    return {"bucket": bucket, "versioning": "Enabled", "encryption": "AES256", "access": "PRIVATE_OWNER_ONLY"}


def object_store(identifier, bucket, prefix):
    return {"id": identifier, "endpoint": objects.ENDPOINT, "bucket": bucket, "region": "us-east-1",
        "prefix": prefix, "network": "LOCAL_LOOPBACK", "credentials-file": str(objects.SECRET)}


def prepare():
    deployment = objects.verify_identity()
    DIRECTORY.mkdir(mode=0o700)
    buckets = []
    with closing(objects.client()) as storage:
        names = {bucket["Name"] for bucket in storage.list_buckets()["Buckets"]}
        if DIAGNOSTIC_BUCKET in names or BACKUP_BUCKET in names:
            raise ValueError("DELETION_BUCKET_REQUIRES_EXISTING_IDENTITY")
        for bucket in (DIAGNOSTIC_BUCKET, BACKUP_BUCKET):
            storage.create_bucket(Bucket=bucket)
            buckets.append(protect(storage, bucket))
    if objects.verify_identity() != deployment:
        raise ValueError("DELETION_STORAGE_DEPLOYMENT_CHANGED")
    signing = DIRECTORY / "backup-signing-key.bin"
    save_private(signing, secrets.token_bytes(32))
    revision = str(uuid.uuid4())
    policy = {"enabled": False, "revision": revision, "tenant-ids": [], "polling": "5s", "lease": "120s",
        "maximum-entries": 100000, "maximum-object-bytes": 1000000000,
        "originals": [object_store("local-originals", objects.BUCKET, "tenants/")],
        "parsed": object_store("local-parsed", objects.BUCKET, "tenants/"),
        "diagnostics": object_store("local-diagnostics", DIAGNOSTIC_BUCKET, "diagnostics/"),
        "vectors": [], "backups": [{"store": object_store("local-backups", BACKUP_BUCKET, "backups/"),
            "catalogue-key": "deletion-catalogues/", "signing-key-file": str(signing)}],
        "cache-namespace": "medical:source-content"}
    configuration = DIRECTORY / "application-source-deletion.yaml"
    save_private(configuration, yaml.safe_dump({"medical": {"source-deletion": policy}}, allow_unicode=True).encode())
    record = {"config_revision": revision, "scope": "LOCAL_TECHNICAL_STORAGE", "object_deployment_id": deployment["deployment_id"],
        "buckets": buckets, "configuration": str(configuration.relative_to(ROOT)), "configuration_sha256": digest(configuration),
        "vector_endpoint": "http://127.0.0.1:16333", "vector_grpc_port": 16334,
        "vector_key_reference": ".implementation-work/runtime/vector-storage/api-key.txt",
        "source_retention_status": "UNKNOWN", "backup_retention_status": "UNKNOWN",
        "deletion_enabled": False, "created_at": datetime.now(timezone.utc).isoformat()}
    save_private(DIRECTORY / "storage-configuration.json", json.dumps(record, ensure_ascii=False, indent=2).encode())
    print("DELETION_STORES_PREPARED " + json.dumps(record, ensure_ascii=False))


def inventory():
    metadata = DIRECTORY.lstat()
    if not stat.S_ISDIR(metadata.st_mode) or stat.S_IMODE(metadata.st_mode) != 0o700 or metadata.st_uid != os.getuid():
        raise ValueError("DELETION_PRIVATE_DIRECTORY_INVALID")
    record = json.loads(objects.private_file(DIRECTORY / "storage-configuration.json"))
    configuration = ROOT / record["configuration"]
    if configuration.is_symlink() or digest(configuration) != record["configuration_sha256"]:
        raise ValueError("DELETION_CONFIGURATION_CHANGED")
    files = []
    for directory in (ROOT / ".implementation-work/runtime/migration-backups",
            ROOT / ".implementation-work/runtime/vector-storage/snapshots", ROOT / ".implementation-work/restore-checks"):
        if directory.is_symlink() or not directory.is_dir():
            raise ValueError("DELETION_BACKUP_DIRECTORY_INVALID")
        for path in sorted(directory.rglob("*")):
            if path.is_symlink():
                raise ValueError("DELETION_BACKUP_SYMLINK_REJECTED")
            if path.is_file():
                if directory.name == "restore-checks" and path.name != "database.sql.gz":
                    continue
                files.append({"path": str(path.relative_to(ROOT)), "bytes": path.stat().st_size,
                    "sha256": digest(path), "modified_at": datetime.fromtimestamp(path.stat().st_mtime, timezone.utc).isoformat(),
                    "source_scope": "SHARED_DATABASE_OR_VECTOR_BACKUP", "expires_at": None})
    object_versions = []
    with closing(objects.client()) as storage:
        buckets = [verify_bucket(storage, bucket) for bucket in (DIAGNOSTIC_BUCKET, BACKUP_BUCKET)]
        paginator = storage.get_paginator("list_object_versions")
        for page in paginator.paginate(Bucket=BACKUP_BUCKET, Prefix="backups/"):
            for version in page.get("Versions", []):
                object_versions.append({"object_key": version["Key"], "object_version": version["VersionId"],
                    "bytes": version["Size"], "modified_at": version["LastModified"].isoformat()})
                if len(object_versions) > 100000:
                    raise ValueError("DELETION_BACKUP_INVENTORY_CAPACITY_EXCEEDED")
    receipt = {"scope": "ACTUAL_LOCAL_REGISTERED_BACKUP_INVENTORY", "config_revision": record["config_revision"],
        "captured_at": datetime.now(timezone.utc).isoformat(), "buckets": buckets, "files": files,
        "object_versions": object_versions, "complete": False, "source_deletion_confirmation": False,
        "pending": ["APPROVED_SOURCE_RETENTION", "APPROVED_SHARED_BACKUP_EXPIRY", "COMPLETE_COPY_INVENTORY",
            "REGISTERED_VECTOR_COLLECTION", "AUTHORIZED_DELETION_JOB"]}
    target = DIRECTORY / ("inventory-" + str(uuid.uuid4()) + ".json")
    save_private(target, json.dumps(receipt, ensure_ascii=False, indent=2).encode())
    print("DELETION_BACKUP_INVENTORY " + json.dumps({"receipt": str(target.relative_to(ROOT)),
        "file_count": len(files), "object_version_count": len(object_versions), "complete": False}))


def main():
    parser = argparse.ArgumentParser(description="配置本地删除存储并核查真实备份目录")
    parser.add_argument("action", choices=("prepare", "inventory"))
    args = parser.parse_args()
    with (ROOT / ".implementation-work/source-deletion-runtime.lock").open("a+") as lock:
        os.chmod(lock.fileno(),0o600)
        fcntl.flock(lock,fcntl.LOCK_EX | fcntl.LOCK_NB)
        {"prepare": prepare, "inventory": inventory}[args.action]()


if __name__ == "__main__":
    main()
