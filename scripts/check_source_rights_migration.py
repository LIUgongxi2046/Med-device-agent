import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
from uuid import uuid4

import local_object_storage as objects
import local_runtime as runtime


MIGRATIONS = (
    "1.0.0-291-source-rights-decision",
    "1.0.0-292-source-rights-state",
    "1.0.0-293-source-rights-state-copy",
)


def database_read(statement):
    result = runtime.mysql("USE medical_local;" + statement, runtime.settings()["root_password"], check=False)
    if result.returncode != 0:
        raise RuntimeError(result.stderr)
    return json.loads(result.stdout)


def main():
    parser = argparse.ArgumentParser(description="核验来源权利迁移及现有记录")
    parser.add_argument("--scope", choices=("before", "after"), required=True)
    parser.add_argument("--before-receipt")
    args = parser.parse_args()
    database = database_read("""
        SELECT JSON_OBJECT('database',DATABASE(),'server_uuid',@@server_uuid,
          'migration_count',(SELECT COUNT(*) FROM DATABASECHANGELOG),
          'source_count',(SELECT COUNT(*) FROM source_version),
          'rights_count',(SELECT COUNT(*) FROM rights_revision),
          'rights_tables',(SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()
            AND TABLE_NAME IN ('source_rights_decision','source_rights_state')),
          'rights_migrations',(SELECT COUNT(*) FROM DATABASECHANGELOG WHERE ID IN
            ('1.0.0-291-source-rights-decision','1.0.0-292-source-rights-state','1.0.0-293-source-rights-state-copy')));
    """)
    identity = json.loads((runtime.RUNTIME / "service-identity.json").read_bytes())
    if database["database"] != identity["database"] or database["server_uuid"] != identity["server_uuid"]:
        raise ValueError("SOURCE_RIGHTS_DATABASE_CHANGED")
    preserved = database_read("""
        SELECT JSON_OBJECT(
          'sources',(SELECT JSON_ARRAYAGG(JSON_OBJECT('tenant_id',tenant_id,'source_version',source_version,
            'rights_revision',rights_revision,'original_sha256',original_sha256,'created_by',created_by,
            'created_at',CAST(created_at AS CHAR))) FROM source_version),
          'rights',(SELECT JSON_ARRAYAGG(JSON_OBJECT('tenant_id',tenant_id,'rights_revision',rights_revision,
            'source_version',source_version,'status',status,'purpose',purpose,
            'allowed_actions',allowed_actions,'providers',providers,'regions',regions,
            'valid_from',CAST(valid_from AS CHAR),'valid_to',CAST(valid_to AS CHAR),
            'evidence_manifest',evidence_manifest,'content_sha256',content_sha256,
            'schema_version',schema_version,'schema_manifest_sha256',schema_manifest_sha256,
            'created_by',created_by,'created_at',CAST(created_at AS CHAR),'classification',classification))
            FROM rights_revision),
          'migrations',(SELECT JSON_ARRAYAGG(JSON_OBJECT('id',ID,'author',AUTHOR,'file',FILENAME,'checksum',MD5SUM))
            FROM DATABASECHANGELOG));
    """)
    preserved["sources"] = sorted(preserved["sources"] or [], key=lambda row: (row["tenant_id"], row["source_version"]))
    preserved["rights"] = sorted(preserved["rights"] or [], key=lambda row: (row["tenant_id"], row["rights_revision"]))
    preserved["migrations"] = sorted(preserved["migrations"], key=lambda row: (row["id"], row["author"], row["file"]))
    if args.scope == "before":
        if database["rights_tables"] != 0 or database["rights_migrations"] != 0:
            raise ValueError("SOURCE_RIGHTS_MIGRATION_PRECONDITION_FAILED")
    else:
        if args.before_receipt is None:
            raise ValueError("SOURCE_RIGHTS_BEFORE_RECEIPT_REQUIRED")
        before_path = Path(args.before_receipt).resolve(strict=True)
        before_path.relative_to(objects.ROOT / ".implementation-work/source-rights-migration-checks")
        before = json.loads(objects.private_file(before_path))
        if before["scope"] != "before" or before["database"]["database"] != database["database"] \
                or before["database"]["server_uuid"] != database["server_uuid"]:
            raise ValueError("SOURCE_RIGHTS_BEFORE_RECEIPT_INVALID")
        for name in ("sources", "rights"):
            if preserved[name] != before["preserved_records"][name]:
                raise ValueError("SOURCE_RIGHTS_EXISTING_RECORD_CHANGED")
        existing = {(row["id"], row["author"], row["file"]): row["checksum"] for row in before["preserved_records"]["migrations"]}
        current = {(row["id"], row["author"], row["file"]): row["checksum"] for row in preserved["migrations"]}
        if len(current) != len(existing) + len(MIGRATIONS) or any(current.get(key) != value for key, value in existing.items()) \
                or {key[0] for key in current.keys() - existing.keys()} != set(MIGRATIONS):
            raise ValueError("SOURCE_RIGHTS_EXISTING_MIGRATION_CHANGED")
        if database["rights_tables"] != 2 or database["rights_migrations"] != 3:
            raise ValueError("SOURCE_RIGHTS_STRUCTURE_INVALID")
        database.update(database_read("""
            SELECT JSON_OBJECT(
              'state_count',(SELECT COUNT(*) FROM source_rights_state),
              'decision_count',(SELECT COUNT(*) FROM source_rights_decision),
              'foreign_keys',(SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA=DATABASE()
                AND TABLE_NAME IN ('source_rights_state','source_rights_decision') AND CONSTRAINT_TYPE='FOREIGN KEY'),
              'checks',(SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA=DATABASE()
                AND TABLE_NAME IN ('source_rights_state','source_rights_decision') AND CONSTRAINT_TYPE='CHECK'),
              'invalid_states',(SELECT COUNT(*) FROM source_version v
                JOIN rights_revision r ON r.tenant_id=v.tenant_id AND r.source_version=v.source_version AND r.rights_revision=v.rights_revision
                JOIN source o ON o.tenant_id=v.tenant_id AND o.source_id=v.source_id
                LEFT JOIN source_rights_state s ON s.tenant_id=v.tenant_id AND s.source_version=v.source_version
                WHERE s.source_version IS NULL OR NOT(s.rights_revision <=> v.rights_revision)
                  OR s.last_decision_id IS NOT NULL OR s.revision<>1 OR NOT(s.created_by <=> v.created_by)
                  OR NOT(s.created_at <=> v.created_at) OR NOT(s.updated_at <=> v.created_at)
                  OR NOT(CAST(s.classification AS BINARY) <=> CAST(o.classification AS BINARY))
                  OR NOT(CAST(s.schema_version AS BINARY) <=> CAST(r.schema_version AS BINARY))
                  OR NOT(s.schema_manifest_sha256 <=> r.schema_manifest_sha256)));
        """))
        if database["state_count"] != database["source_count"] or database["decision_count"] != 0 \
                or database["foreign_keys"] != 8 or database["checks"] != 8 or database["invalid_states"] != 0:
            raise ValueError("SOURCE_RIGHTS_READBACK_MISMATCH")
    directory = objects.ROOT / ".implementation-work/source-rights-migration-checks" / str(uuid4())
    directory.mkdir(mode=0o700, parents=True)
    receipt = {"schema_version": "1.0.0", "scope": args.scope, "verified_at": datetime.now(timezone.utc).isoformat(),
        "database": database, "preserved_records": preserved, "customer_approval_verified": False,
        "checker_sha256": hashlib.sha256(Path(__file__).read_bytes()).hexdigest()}
    objects.save_private(directory / "receipt.json", receipt)
    print(json.dumps({"receipt": str(directory / "receipt.json"), "database": database}))


if __name__ == "__main__":
    main()
