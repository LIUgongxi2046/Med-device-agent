import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
from uuid import uuid4

import local_object_storage as objects
import local_runtime as runtime


MIGRATION = "1.0.0-294-source-index-build"
DIRECTORY = objects.ROOT / ".implementation-work/source-index-migration-checks"


def read(statement):
    result = runtime.mysql("USE medical_local;" + statement, runtime.settings()["root_password"], check=False)
    if result.returncode != 0:
        raise RuntimeError(result.stderr)
    return json.loads(result.stdout)


def main():
    parser = argparse.ArgumentParser(description="核验全文索引迁移及原来源身份")
    parser.add_argument("--scope", choices=("before", "after"), required=True)
    parser.add_argument("--before-receipt")
    args = parser.parse_args()
    database = read("""
        SELECT JSON_OBJECT('database',DATABASE(),'server_uuid',@@server_uuid,'ngram_token_size',@@ngram_token_size,
          'migration_count',(SELECT COUNT(*) FROM DATABASECHANGELOG),
          'build_tables',(SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()
            AND TABLE_NAME='source_index_build'),
          'index_migrations',(SELECT COUNT(*) FROM DATABASECHANGELOG WHERE ID='1.0.0-294-source-index-build'));
    """)
    identity = json.loads((runtime.RUNTIME / "service-identity.json").read_bytes())
    if database["database"] != identity["database"] or database["server_uuid"] != identity["server_uuid"]:
        raise ValueError("SOURCE_INDEX_DATABASE_CHANGED")
    preserved = read("""
        START TRANSACTION WITH CONSISTENT SNAPSHOT;
        SELECT JSON_OBJECT(
          'sources',(SELECT JSON_ARRAYAGG(JSON_OBJECT('tenant_id',tenant_id,'source_id',source_id,
            'source_version',source_version,'rights_revision',rights_revision,'original_sha256',original_sha256,
            'object_key',object_key,'object_version',object_version,'size_bytes',CAST(size_bytes AS CHAR))) FROM source_version),
          'rights',(SELECT JSON_ARRAYAGG(JSON_OBJECT('tenant_id',tenant_id,'rights_revision',rights_revision,
            'source_version',source_version,'status',status,'content_sha256',content_sha256)) FROM rights_revision),
          'parses',(SELECT JSON_ARRAYAGG(JSON_OBJECT('tenant_id',tenant_id,'source_version',source_version,
            'parse_version',parse_version,'parse_status',parse_status,'processor_manifest',processor_manifest,
            'content_sha256',content_sha256,'artifact_key',artifact_key,'artifact_version',artifact_version,
            'artifact_size_bytes',CAST(artifact_size_bytes AS CHAR),'failure_manifest',failure_manifest)) FROM parse_version),
          'indexes',(SELECT JSON_ARRAYAGG(JSON_OBJECT('tenant_id',tenant_id,'source_version',source_version,
            'parse_version',parse_version,'index_version',index_version,'mode',mode,'model_config_revision',model_config_revision,
            'dimension',dimension,'config_sha256',config_sha256,'manifest_sha256',manifest_sha256,
            'expected_count',CAST(expected_count AS CHAR),'verified_count',CAST(verified_count AS CHAR),'status',status)) FROM index_manifest),
          'chunk_count',(SELECT COUNT(*) FROM source_chunk),'item_count',(SELECT COUNT(*) FROM index_item),
          'migrations',(SELECT JSON_ARRAYAGG(JSON_OBJECT('id',ID,'author',AUTHOR,'file',FILENAME,'checksum',MD5SUM)) FROM DATABASECHANGELOG));
        COMMIT;
    """)
    for name in ("sources", "rights", "parses", "indexes", "migrations"):
        preserved[name] = sorted(preserved[name] or [], key=lambda row: json.dumps(row, sort_keys=True, ensure_ascii=False))
    if args.scope == "before":
        if database["build_tables"] != 0 or database["index_migrations"] != 0:
            raise ValueError("SOURCE_INDEX_MIGRATION_PRECONDITION_FAILED")
    else:
        if args.before_receipt is None:
            raise ValueError("SOURCE_INDEX_BEFORE_RECEIPT_REQUIRED")
        path = Path(args.before_receipt).resolve(strict=True)
        path.relative_to(DIRECTORY)
        before = json.loads(objects.private_file(path))
        if before["scope"] != "before" or before["database"]["database"] != database["database"] \
                or before["database"]["server_uuid"] != database["server_uuid"]:
            raise ValueError("SOURCE_INDEX_BEFORE_RECEIPT_INVALID")
        for name in ("sources", "rights", "parses", "indexes", "chunk_count", "item_count"):
            if preserved[name] != before["preserved_records"][name]:
                raise ValueError("SOURCE_INDEX_EXISTING_RECORD_CHANGED")
        previous = {(row["id"], row["author"], row["file"]): row["checksum"] for row in before["preserved_records"]["migrations"]}
        current = {(row["id"], row["author"], row["file"]): row["checksum"] for row in preserved["migrations"]}
        if len(current) != len(previous) + 1 or any(current.get(key) != value for key, value in previous.items()) \
                or {key[0] for key in current.keys() - previous.keys()} != {MIGRATION}:
            raise ValueError("SOURCE_INDEX_EXISTING_MIGRATION_CHANGED")
        database.update(read("""
            SELECT JSON_OBJECT('build_count',(SELECT COUNT(*) FROM source_index_build),
              'columns',(SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='source_index_build'),
              'foreign_keys',(SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA=DATABASE()
                AND TABLE_NAME='source_index_build' AND CONSTRAINT_TYPE='FOREIGN KEY'),
              'checks',(SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA=DATABASE()
                AND TABLE_NAME='source_index_build' AND CONSTRAINT_TYPE='CHECK'),
              'fulltext_index',(SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE()
                AND TABLE_NAME='source_chunk' AND INDEX_NAME='ft_chunk' AND INDEX_TYPE='FULLTEXT' AND COLUMN_NAME='search_text'));
        """))
        if database["build_tables"] != 1 or database["index_migrations"] != 1 or database["build_count"] != 0 \
                or database["columns"] != 24 or database["foreign_keys"] != 7 or database["checks"] != 6 \
                or database["fulltext_index"] != 1:
            raise ValueError("SOURCE_INDEX_STRUCTURE_MISMATCH")
    directory = DIRECTORY / str(uuid4())
    directory.mkdir(mode=0o700, parents=True)
    receipt = {"schema_version": "1.0.0", "scope": args.scope, "verified_at": datetime.now(timezone.utc).isoformat(),
        "database": database, "preserved_records": preserved, "customer_index_verified": False,
        "customer_approval_created": False, "worker_enabled": False}
    target = directory / "receipt.json"
    objects.save_private(target, receipt)
    print(json.dumps({"receipt": str(target.relative_to(objects.ROOT)), "sha256": hashlib.sha256(target.read_bytes()).hexdigest(),
        "scope": args.scope, "migration_count": database["migration_count"]}))


if __name__ == "__main__":
    main()
