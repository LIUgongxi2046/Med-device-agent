import argparse
from datetime import datetime, timezone
import json
from pathlib import Path
from uuid import uuid4

from check_source_exact_migration import preserved_records
from check_source_index_migration import read
import local_object_storage as objects
import local_runtime as runtime


DIRECTORY = objects.ROOT / ".implementation-work/run-token-authority-migration-checks"
MIGRATIONS = {"1.0.0-300-run-step-lease", "1.0.0-301-principal-token-binding"}
COLUMNS = {"run_step": {"lease_owner", "lease_expires_at"},
    "principal_token": {"token_sha256", "service_uri", "policy_revision", "policy_sha256", "lease_generation"}}


def run_records():
    return read("""
        SELECT JSON_OBJECT(
          'principals',(SELECT JSON_ARRAYAGG(JSON_OBJECT('tenant',tenant_id,'principal',principal_id,'project',project_id,
            'actor',initiator_id,'run',run_id,'manifest',capability_manifest,'status',status,'revision',CAST(revision AS CHAR))) FROM run_principal),
          'tokens',(SELECT JSON_ARRAYAGG(JSON_OBJECT('tenant',tenant_id,'jti',jti,'principal',principal_id,'project',project_id,
            'run',run_id,'step',step_id,'audience',audience,'actions',action_scope,'auth',CAST(auth_epoch AS CHAR),
            'issued',issued_at,'expires',expires_at,'revoked',revoked_at,'revision',CAST(revision AS CHAR))) FROM principal_token),
          'steps',(SELECT JSON_ARRAYAGG(JSON_OBJECT('tenant',tenant_id,'project',project_id,'run',run_id,'step',step_id,
            'role',role_version,'input',input_sha256,'output',output_ref,'status',status,'generation',CAST(lease_generation AS CHAR),
            'revision',CAST(revision AS CHAR))) FROM run_step));
    """)


def main():
    parser = argparse.ArgumentParser(description="核验运行令牌迁移与原数据身份")
    parser.add_argument("--scope", choices=("before", "after"), required=True)
    parser.add_argument("--before-receipt")
    args = parser.parse_args()
    database = read("SELECT JSON_OBJECT('database',DATABASE(),'server_uuid',@@server_uuid,'migration_count',(SELECT COUNT(*) FROM DATABASECHANGELOG));")
    identity = json.loads((runtime.RUNTIME / "service-identity.json").read_bytes())
    if database["database"] != identity["database"] or database["server_uuid"] != identity["server_uuid"]:
        raise ValueError("RUN_AUTHORITY_DATABASE_IDENTITY_CHANGED")
    preserved = preserved_records()
    runs = run_records()
    structures = {}
    for table, columns in COLUMNS.items():
        value = read("SELECT JSON_OBJECT('columns',JSON_ARRAYAGG(COLUMN_NAME)) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='" + table + "';")
        actual = set(value["columns"])
        structures[table] = sorted(actual)
        if args.scope == "before" and actual.intersection(columns) or args.scope == "after" and not columns.issubset(actual):
            raise ValueError("RUN_AUTHORITY_MIGRATION_STRUCTURE_MISMATCH")
    if args.scope == "before":
        if any(row["id"] in MIGRATIONS for row in preserved["migrations"]):
            raise ValueError("RUN_AUTHORITY_MIGRATION_ALREADY_APPLIED")
    else:
        path = Path(args.before_receipt).resolve(strict=True)
        path.relative_to(DIRECTORY)
        before = json.loads(objects.private_file(path))
        if before["scope"] != "before" or before["database"]["server_uuid"] != database["server_uuid"] or before["runs"] != runs:
            raise ValueError("RUN_AUTHORITY_ORIGINAL_IDENTITY_CHANGED")
        for key, value in preserved.items():
            if key != "migrations" and value != before["preserved_records"][key]:
                raise ValueError("RUN_AUTHORITY_ORIGINAL_SOURCE_CHANGED")
        old = {(row["id"], row["author"], row["file"]): row["checksum"] for row in before["preserved_records"]["migrations"]}
        current = {(row["id"], row["author"], row["file"]): row["checksum"] for row in preserved["migrations"]}
        if len(current) != len(old) + 2 or any(current.get(key) != value for key, value in old.items()) \
                or {key[0] for key in current.keys() - old.keys()} != MIGRATIONS:
            raise ValueError("RUN_AUTHORITY_ORIGINAL_CHECKSUM_CHANGED")
        for table, columns in COLUMNS.items():
            if set(structures[table]) != set(before["structures"][table]) | columns:
                raise ValueError("RUN_AUTHORITY_COLUMN_SET_CHANGED")
    directory = DIRECTORY / str(uuid4())
    directory.mkdir(mode=0o700, parents=True)
    target = directory / "receipt.json"
    objects.save_private(target, {"schema_version": "1.0.0", "scope": args.scope, "verified_at": datetime.now(timezone.utc).isoformat(),
        "database": database, "preserved_records": preserved, "runs": runs, "structures": structures,
        "customer_authorization_created": False, "model_request_sent": False})
    print(str(target.relative_to(objects.ROOT)), flush=True)


if __name__ == "__main__":
    main()
