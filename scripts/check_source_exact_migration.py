import argparse
from datetime import datetime, timezone
import json
from pathlib import Path
from uuid import uuid4

from check_source_index_migration import read
import local_object_storage as objects
import local_runtime as runtime


DIRECTORY = objects.ROOT / ".implementation-work/source-exact-migration-checks"
TABLES = ("source_exact_revision", "source_exact_head", "source_exact_item", "source_exact_query", "source_exact_hit")
MIGRATIONS = ("1.0.0-295-source-exact-revision", "1.0.0-296-source-exact-head", "1.0.0-297-source-exact-item",
    "1.0.0-298-source-exact-query", "1.0.0-299-source-exact-hit")


def preserved_records():
    value = read("""
        START TRANSACTION WITH CONSISTENT SNAPSHOT;
        SELECT JSON_OBJECT(
          'sources',(SELECT JSON_ARRAYAGG(JSON_OBJECT('tenant_id',tenant_id,'source_id',source_id,'source_version',source_version,
            'rights_revision',rights_revision,'original_sha256',original_sha256,'object_key',object_key,'object_version',object_version,
            'size_bytes',CAST(size_bytes AS CHAR))) FROM source_version),
          'rights',(SELECT JSON_ARRAYAGG(JSON_OBJECT('tenant_id',tenant_id,'rights_revision',rights_revision,'source_version',source_version,
            'status',status,'content_sha256',content_sha256)) FROM rights_revision),
          'parses',(SELECT JSON_ARRAYAGG(JSON_OBJECT('tenant_id',tenant_id,'source_version',source_version,'parse_version',parse_version,
            'parse_status',parse_status,'processor_manifest',processor_manifest,'content_sha256',content_sha256,'artifact_key',artifact_key,
            'artifact_version',artifact_version,'artifact_size_bytes',CAST(artifact_size_bytes AS CHAR),'failure_manifest',failure_manifest)) FROM parse_version),
          'indexes',(SELECT JSON_ARRAYAGG(JSON_OBJECT('tenant_id',tenant_id,'source_version',source_version,'parse_version',parse_version,
            'index_version',index_version,'mode',mode,'manifest_sha256',manifest_sha256,'expected_count',CAST(expected_count AS CHAR),
            'verified_count',CAST(verified_count AS CHAR),'status',status)) FROM index_manifest),
          'retrievals',(SELECT JSON_ARRAYAGG(JSON_OBJECT('tenant_id',tenant_id,'retrieval_id',retrieval_id,'project_id',project_id,
            'subject_id',subject_id,'query_sha256',query_sha256,'mode',mode,'selection_sha256',selection_sha256,
            'result_count',CAST(result_count AS CHAR),'status',status)) FROM retrieval_query),
          'chunk_count',(SELECT COUNT(*) FROM source_chunk),'index_item_count',(SELECT COUNT(*) FROM index_item),
          'hit_count',(SELECT COUNT(*) FROM retrieval_hit),'index_build_count',(SELECT COUNT(*) FROM source_index_build),
          'migrations',(SELECT JSON_ARRAYAGG(JSON_OBJECT('id',ID,'author',AUTHOR,'file',FILENAME,'checksum',MD5SUM)) FROM DATABASECHANGELOG));
        COMMIT;
    """)
    for name in ("sources", "rights", "parses", "indexes", "retrievals", "migrations"):
        value[name] = sorted(value[name] or [], key=lambda row: json.dumps(row, ensure_ascii=False, sort_keys=True))
    return value


def main():
    parser = argparse.ArgumentParser(description="核验EXACT迁移、完整原身份及历史checksum")
    parser.add_argument("--scope", choices=("before", "after"), required=True)
    parser.add_argument("--before-receipt")
    args = parser.parse_args()
    database = read("SELECT JSON_OBJECT('database',DATABASE(),'server_uuid',@@server_uuid,'migration_count',(SELECT COUNT(*) FROM DATABASECHANGELOG));")
    identity = json.loads((runtime.RUNTIME / "service-identity.json").read_bytes())
    if database["database"] != identity["database"] or database["server_uuid"] != identity["server_uuid"]:
        raise ValueError("EXACT_DATABASE_IDENTITY_CHANGED")
    preserved = preserved_records()
    table_names = ",".join("'" + name + "'" for name in TABLES)
    existing = read("SELECT JSON_OBJECT('count',COUNT(*)) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN (" + table_names + ");")["count"]
    if args.scope == "before":
        if existing != 0 or any(row["id"] in MIGRATIONS for row in preserved["migrations"]):
            raise ValueError("EXACT_MIGRATION_PRECONDITION_FAILED")
        structures = []
    else:
        if args.before_receipt is None:
            raise ValueError("EXACT_BEFORE_RECEIPT_REQUIRED")
        path = Path(args.before_receipt).resolve(strict=True)
        path.relative_to(DIRECTORY)
        before = json.loads(objects.private_file(path))
        if before["scope"] != "before" or before["database"]["database"] != database["database"] \
                or before["database"]["server_uuid"] != database["server_uuid"] or existing != len(TABLES):
            raise ValueError("EXACT_BEFORE_RECEIPT_INVALID")
        for name, value in preserved.items():
            if name != "migrations" and value != before["preserved_records"][name]:
                raise ValueError("EXACT_EXISTING_RECORD_CHANGED")
        previous = {(row["id"], row["author"], row["file"]): row["checksum"] for row in before["preserved_records"]["migrations"]}
        current = {(row["id"], row["author"], row["file"]): row["checksum"] for row in preserved["migrations"]}
        if len(current) != len(previous) + len(MIGRATIONS) or any(current.get(key) != value for key, value in previous.items()) \
                or {key[0] for key in current.keys() - previous.keys()} != set(MIGRATIONS):
            raise ValueError("EXACT_EXISTING_MIGRATION_CHANGED")
        expected = dict(zip(TABLES, ((16, 5, 2), (12, 2, 1), (18, 3, 3), (17, 4, 3), (18, 8, 1)), strict=True))
        structures = []
        for name, counts in expected.items():
            structure = read("""
                SELECT JSON_OBJECT('table',%s,'row_count',(SELECT COUNT(*) FROM %s),
                  'columns',(SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=%s),
                  'foreign_keys',(SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=%s AND CONSTRAINT_TYPE='FOREIGN KEY'),
                  'checks',(SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=%s AND CONSTRAINT_TYPE='CHECK'));
            """ % ("'" + name + "'", name, "'" + name + "'", "'" + name + "'", "'" + name + "'"))
            if structure["row_count"] != 0 or tuple(structure[key] for key in ("columns", "foreign_keys", "checks")) != counts:
                raise ValueError("EXACT_STRUCTURE_MISMATCH")
            structures.append(structure)
    directory = DIRECTORY / str(uuid4())
    directory.mkdir(mode=0o700, parents=True)
    receipt = {"schema_version": "1.0.0", "scope": args.scope, "verified_at": datetime.now(timezone.utc).isoformat(),
        "database": database, "preserved_records": preserved, "structures": structures,
        "customer_exact_verified": False, "professional_approval_created": False}
    target = directory / "receipt.json"
    objects.save_private(target, receipt)
    print(str(target.relative_to(objects.ROOT)), flush=True)


if __name__ == "__main__":
    main()
