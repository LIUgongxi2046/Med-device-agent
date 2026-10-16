import argparse
from datetime import datetime, timezone
import gzip
import hashlib
from io import BytesIO
import json
import os
from pathlib import Path
import subprocess
import urllib.request
import uuid
import zipfile

import yaml

import local_object_storage as objects
import local_runtime as runtime
import local_vector_storage as vectors


ROOT = Path(__file__).resolve().parents[1]


def digest(path):
    with path.open("rb") as source:
        return hashlib.file_digest(source, "sha256").hexdigest()


def main():
    parser = argparse.ArgumentParser(description="核验真实删除服务配置、迁移、备份和当前应用")
    parser.add_argument("--inventory", required=True)
    args = parser.parse_args()
    directory = ROOT / ".implementation-work" / ("source-deletion-verification-" + str(uuid.uuid4()))
    directory.mkdir(mode=0o700)
    application = runtime.verify_application_deployment()
    object_identity = objects.verify_identity()
    vector_identity = vectors.verify_identity()
    configuration = ROOT / application["additional_configuration"]
    policy = yaml.safe_load(objects.private_file(configuration))["medical"]["source-deletion"]
    if policy["enabled"] is not False or policy["tenant-ids"] or policy["vectors"]:
        raise ValueError("DELETION_RUNTIME_REQUIRES_CURRENT_APPROVED_SCOPE")
    for path in (objects.SECRET, vectors.API_KEY, Path(policy["backups"][0]["signing-key-file"])):
        objects.private_file(path)
    statement = """
        SELECT JSON_OBJECT('database',DATABASE(),'server_uuid',@@server_uuid,'mysql_version',VERSION(),
          'source_count',(SELECT COUNT(*) FROM source_version),
          'deletion_jobs',(SELECT COUNT(*) FROM deletion_job),
          'approved_retention_policies',(SELECT COUNT(*) FROM retention_policy_revision WHERE status='APPROVED'),
          'invalid_leases',(SELECT COUNT(*) FROM deletion_item WHERE
            (lease_owner IS NULL AND lease_expires_at IS NOT NULL)
            OR (lease_owner IS NOT NULL AND (lease_expires_at IS NULL OR lease_generation=0 OR status<>'RUNNING'))),
          'migration_count',(SELECT COUNT(*) FROM DATABASECHANGELOG),
          'new_migrations',(SELECT COUNT(*) FROM DATABASECHANGELOG WHERE ID IN
            ('1.0.0-269-knowledge-submission-pending-identity','1.0.0-270-source-organization-category','1.0.0-271-source-deletion-worker')),
          'worker_columns',(SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
            AND TABLE_NAME='deletion_item' AND COLUMN_NAME IN
            ('lease_owner','lease_generation','lease_expires_at','execution_evidence','worker_receipt')),
          'lease_constraints',(SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA=DATABASE()
            AND TABLE_NAME='deletion_item' AND CONSTRAINT_NAME='ck_deletion_worker_lease'))
        FROM tenant LIMIT 1
    """
    observed = runtime.mysql("USE medical_local;" + statement, runtime.settings()["root_password"])
    database = json.loads(observed.stdout)
    expected = json.loads((runtime.RUNTIME / "service-identity.json").read_bytes())
    if database["database"] != expected["database"] or database["server_uuid"] != expected["server_uuid"] \
            or database["invalid_leases"] != 0 or database["worker_columns"] != 5 \
            or database["lease_constraints"] != 1 or database["new_migrations"] != 3:
        raise ValueError("DELETION_DATABASE_READBACK_MISMATCH")
    backups = []
    for path in sorted((runtime.RUNTIME / "migration-backups").glob("*.sql.gz")):
        bytes_read = 0
        with gzip.open(path, "rb") as source:
            while block := source.read(1048576):
                bytes_read += len(block)
        if bytes_read == 0:
            raise ValueError("DELETION_DATABASE_BACKUP_EMPTY")
        identity = json.loads(objects.private_file(path.with_suffix("").with_suffix(".json")))
        if identity["database"] != expected["database"] or identity["server_uuid"] != expected["server_uuid"] \
                or identity["sha256"] != digest(path):
            raise ValueError("DELETION_DATABASE_BACKUP_IDENTITY_INVALID")
        backups.append({"path": str(path.relative_to(ROOT)), "sha256": identity["sha256"], "decoded_bytes": bytes_read})
    inventory_path = Path(args.inventory).resolve(strict=True)
    inventory_path.relative_to(ROOT / ".implementation-work/runtime/source-deletion")
    inventory = json.loads(objects.private_file(inventory_path))
    if inventory["complete"] is not False or inventory["source_deletion_confirmation"] is not False \
            or inventory["config_revision"] != policy["revision"]:
        raise ValueError("DELETION_BACKUP_INVENTORY_MISMATCH")
    with zipfile.ZipFile(ROOT / application["artifact"]) as jar:
        if "BOOT-INF/lib/medical-persistence-3.9.2.jar" not in jar.namelist():
            raise ValueError("DELETION_APPLICATION_COMPONENT_MISSING")
        component = jar.read("BOOT-INF/lib/medical-persistence-3.9.2.jar")
    classes = []
    target = ROOT / "business/medical-persistence/target/classes"
    package = target / "com/ruoyi/medical/persistence"
    compiled = [*package.glob("SourceDeletion*.class"), package / "SourceGovernanceService.class",
        package / "mapper/SourceDeletionMapper.class"]
    with zipfile.ZipFile(BytesIO(component)) as library:
        for path in sorted(compiled):
            content = path.read_bytes()
            if content != library.read(path.relative_to(target).as_posix()):
                raise ValueError("DELETION_RUNTIME_CLASS_BYTES_MISMATCH")
            classes.append({"path":str(path.relative_to(ROOT)),"sha256":hashlib.sha256(content).hexdigest()})
    sources = []
    source_directory = ROOT / "business/medical-persistence/src/main/java/com/ruoyi/medical/persistence"
    for path in sorted([*source_directory.glob("SourceDeletion*.java"), source_directory / "SourceGovernanceService.java",
            source_directory / "mapper/SourceDeletionMapper.java"]):
        class_file = package / path.relative_to(source_directory).with_suffix(".class")
        if path.stat().st_mtime_ns > class_file.stat().st_mtime_ns:
            raise ValueError("DELETION_SOURCE_REQUIRES_CURRENT_COMPILATION")
        sources.append({"path":str(path.relative_to(ROOT)),"sha256":digest(path)})
    body = directory / "unauthenticated-source-list.json"
    response = subprocess.run(["curl", "--noproxy", "*", "--silent", "--show-error", "--max-time", "10",
        "--output", str(body), "--write-out", "%{http_code}", "http://127.0.0.1:18080/api/source-versions"],
        check=True, capture_output=True, text=True)
    if response.stdout != "401":
        raise ValueError("DELETION_APPLICATION_UNAUTHENTICATED_ACCESS_INVALID")
    json.loads(body.read_bytes())
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    with opener.open(urllib.request.Request(vectors.ENDPOINT + "/collections",
            headers={"api-key": vectors.private_file(vectors.API_KEY).decode()}), timeout=10) as source:
        collection_response = json.load(source)
    if collection_response["status"] != "ok":
        raise ValueError("DELETION_VECTOR_READBACK_INVALID")
    receipt = {"scope": "ACTUAL_RUNTIME_CONFIGURATION_AND_DATABASE_READBACK", "verified_at": datetime.now(timezone.utc).isoformat(),
        "application": application, "object_identity": object_identity, "vector_identity": vector_identity,
        "database": database, "backups": backups, "inventory": str(inventory_path.relative_to(ROOT)),
        "sources":sources,"runtime_classes":classes,
        "inventory_sha256": digest(inventory_path), "unauthenticated_status": response.stdout,
        "deletion_enabled": False, "customer_deletion_verified": False, "backup_restore_verified": False,
        "source_retention_status": "UNKNOWN", "backup_retention_status": "UNKNOWN"}
    objects.save_private(directory / "receipt.json", receipt)
    print("SOURCE_DELETION_RUNTIME_VERIFIED " + json.dumps({"receipt": str((directory / "receipt.json").relative_to(ROOT)),
        "backup_count": len(backups), "migration_count": database["migration_count"], "customer_deletion_verified": False}))


if __name__ == "__main__":
    main()
