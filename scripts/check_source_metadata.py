import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
from uuid import uuid4

import local_object_storage as objects
import local_runtime as runtime


def main():
    parser = argparse.ArgumentParser(description="重新读取来源及检索记录的实际审计字段")
    parser.add_argument("--scope", choices=("before", "after"), required=True)
    parser.add_argument("--before-receipt")
    args = parser.parse_args()
    statement = """
        SELECT JSON_OBJECT('database',DATABASE(),'server_uuid',@@server_uuid,
          'migration_count',(SELECT COUNT(*) FROM DATABASECHANGELOG),
          'source_count',(SELECT COUNT(*) FROM source_version),
          'retrieval_hit_count',(SELECT COUNT(*) FROM retrieval_hit),
          'retention_policy_count',(SELECT COUNT(*) FROM retention_policy_revision),
          'lifecycle_decision_count',(SELECT COUNT(*) FROM source_lifecycle_decision),
          'lifecycle_state_count',(SELECT COUNT(*) FROM source_lifecycle_state),
          'delivery_count',(SELECT COUNT(*) FROM outbox_delivery),
          'metadata_columns',(SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
            AND ((TABLE_NAME='retrieval_hit' AND COLUMN_NAME IN ('created_by','classification','schema_version','schema_manifest_sha256'))
            OR (TABLE_NAME='retention_policy_revision' AND COLUMN_NAME IN ('created_by','classification'))
            OR (TABLE_NAME='source_lifecycle_decision' AND COLUMN_NAME IN ('created_by','created_at'))
            OR (TABLE_NAME='source_lifecycle_state' AND COLUMN_NAME IN ('created_by','created_at','classification'))
            OR (TABLE_NAME='outbox_delivery' AND COLUMN_NAME IN ('schema_version','schema_manifest_sha256')))),
          'metadata_migrations',(SELECT COUNT(*) FROM DATABASECHANGELOG WHERE ID IN
            ('1.0.0-278-retrieval-hit-metadata','1.0.0-279-retrieval-hit-copy','1.0.0-280-retrieval-hit-required',
            '1.0.0-281-retention-policy-metadata','1.0.0-282-lifecycle-decision-metadata','1.0.0-283-lifecycle-decision-copy',
            '1.0.0-284-lifecycle-decision-required','1.0.0-285-lifecycle-state-metadata',
            '1.0.0-286-lifecycle-state-copy','1.0.0-287-lifecycle-state-required',
            '1.0.0-288-outbox-delivery-schema','1.0.0-289-outbox-delivery-schema-copy','1.0.0-290-outbox-delivery-schema-required')))
    """
    result = runtime.mysql("USE medical_local;" + statement, runtime.settings()["root_password"])
    database = json.loads(result.stdout)
    preserved = runtime.mysql("""
        USE medical_local;
        SELECT JSON_OBJECT(
          'states',(SELECT JSON_ARRAYAGG(JSON_OBJECT('tenant_id',tenant_id,'source_version',source_version,
            'status',status,'last_decision_id',last_decision_id,'revision',CAST(revision AS CHAR),
            'updated_at',CAST(updated_at AS CHAR))) FROM source_lifecycle_state),
          'migrations',(SELECT JSON_ARRAYAGG(JSON_OBJECT('id',ID,'author',AUTHOR,'file',FILENAME,'checksum',MD5SUM))
            FROM DATABASECHANGELOG));
    """, runtime.settings()["root_password"])
    preserved_records = json.loads(preserved.stdout)
    identity = json.loads((runtime.RUNTIME / "service-identity.json").read_bytes())
    if database["database"] != identity["database"] or database["server_uuid"] != identity["server_uuid"]:
        raise ValueError("SOURCE_METADATA_DATABASE_CHANGED")
    if args.scope == "before":
        if database["metadata_columns"] != 0 or database["metadata_migrations"] != 0 \
                or database["retrieval_hit_count"] != 0 or database["retention_policy_count"] != 0:
            raise ValueError("SOURCE_METADATA_MIGRATION_PRECONDITION_FAILED")
    else:
        if args.before_receipt is None:
            raise ValueError("SOURCE_METADATA_BEFORE_RECEIPT_REQUIRED")
        before_path = Path(args.before_receipt).resolve(strict=True)
        before_path.relative_to(objects.ROOT / ".implementation-work/source-metadata-checks")
        before = json.loads(objects.private_file(before_path))
        if before["scope"] != "before" or before["database"]["server_uuid"] != database["server_uuid"]:
            raise ValueError("SOURCE_METADATA_BEFORE_RECEIPT_INVALID")
        if before["preserved_records"]["states"] != preserved_records["states"]:
            raise ValueError("SOURCE_METADATA_EXISTING_STATE_CHANGED")
        existing = {(row["id"], row["author"], row["file"]): row["checksum"] for row in before["preserved_records"]["migrations"]}
        current = {(row["id"], row["author"], row["file"]): row["checksum"] for row in preserved_records["migrations"]}
        if len(current) != len(existing) + 13 or any(current.get(key) != value for key, value in existing.items()):
            raise ValueError("SOURCE_METADATA_EXISTING_MIGRATION_CHANGED")
        if database["metadata_columns"] != 13 or database["metadata_migrations"] != 13:
            raise ValueError("SOURCE_METADATA_STRUCTURE_INVALID")
        verification = runtime.mysql("""
            USE medical_local;
            SELECT JSON_OBJECT(
              'invalid_decisions',(SELECT COUNT(*) FROM source_lifecycle_decision
                WHERE NOT(created_by <=> actor_id) OR NOT(created_at <=> decided_at)),
              'invalid_states',(SELECT COUNT(*) FROM source_lifecycle_state s JOIN source_version v
                ON v.tenant_id=s.tenant_id AND v.source_version=s.source_version
                JOIN source o ON o.tenant_id=v.tenant_id AND o.source_id=v.source_id
                WHERE NOT(s.created_by <=> v.created_by) OR NOT(s.created_at <=> v.created_at)
                  OR NOT(CAST(s.classification AS BINARY) <=> CAST(o.classification AS BINARY))),
              'nullable_columns',(SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
                AND TABLE_NAME IN ('retrieval_hit','retention_policy_revision','source_lifecycle_decision','source_lifecycle_state','outbox_delivery')
                AND COLUMN_NAME IN ('created_by','created_at','classification','schema_version','schema_manifest_sha256')
                AND IS_NULLABLE='YES'));
        """, runtime.settings()["root_password"], check=False)
        if verification.returncode != 0:
            raise RuntimeError(verification.stderr)
        database.update(json.loads(verification.stdout))
        if database["invalid_decisions"] != 0 or database["invalid_states"] != 0 or database["nullable_columns"] != 0:
            raise ValueError("SOURCE_METADATA_READBACK_MISMATCH")
    directory = objects.ROOT / ".implementation-work/source-metadata-checks" / str(uuid4())
    directory.mkdir(mode=0o700, parents=True)
    receipt = {"schema_version": "1.0.0", "scope": args.scope, "verified_at": datetime.now(timezone.utc).isoformat(),
        "database": database, "preserved_records": preserved_records, "customer_success_verified": False,
        "checker_sha256": hashlib.sha256(Path(__file__).read_bytes()).hexdigest()}
    objects.save_private(directory / "receipt.json", receipt)
    print(json.dumps({"receipt": str(directory / "receipt.json"), "database": database}))


if __name__ == "__main__":
    main()
