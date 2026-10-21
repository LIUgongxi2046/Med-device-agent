import argparse
import gzip
import hashlib
import json
from pathlib import Path
import subprocess
import sys
from uuid import UUID, uuid4
from xml.etree import ElementTree
from zipfile import ZipFile

from local_runtime import ROOT, RUNTIME, migrate, mysql, settings


from check_support import capture_check_path
CAPTURE = capture_check_path()
RESOURCES = ROOT / "business/medical-data/src/main/resources"
MIGRATION = RESOURCES / "db/migration/036-task-plan-relations.xml"


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def changes(password):
    return json.loads(mysql("SELECT JSON_ARRAYAGG(JSON_OBJECT('id',ID,'author',AUTHOR,'filename',FILENAME,"
        "'checksum',MD5SUM)) FROM medical_local.DATABASECHANGELOG", password).stdout)


def perform(output, migration_id, document_reading=False, projection_kind=False, revision_projections=False):
    identity = json.loads((RUNTIME / "service-identity.json").read_bytes())
    password = settings()["root_password"]
    actual = json.loads(mysql("SELECT JSON_OBJECT('server_uuid',@@server_uuid,'version',VERSION(),"
        "'database','medical_local','port',@@port)", password).stdout)
    if any(actual[name] != value for name, value in identity.items()) or actual["port"] != 13306:
        raise ValueError("当前数据库身份不符合原本地服务登记")
    migration = RESOURCES / "db/migration/039-document-revision-projections.xml" if revision_projections else RESOURCES / "db/migration/038-document-projection-kind.xml" if projection_kind else RESOURCES / "db/migration/037-document-reading.xml" if document_reading else MIGRATION
    counts = json.loads(mysql("SELECT JSON_OBJECT('plan_section',(SELECT COUNT(*) FROM medical_local.plan_section),"
        "'plan_dependency',(SELECT COUNT(*) FROM medical_local.plan_dependency),"
        "'research_question',(SELECT COUNT(*) FROM medical_local.research_question))", password).stdout)
    if not document_reading and not projection_kind and not revision_projections and any(counts.values()):
        raise ValueError("本次计划关系迁移要求原三张计划表为空")
    namespace = {"lb": "http://www.liquibase.org/xml/ns/dbchangelog"}
    expected = [item.attrib["id"] for item in ElementTree.parse(migration).findall("lb:changeSet", namespace)]
    before = changes(password)
    before_count, added_count = (252, 2) if revision_projections else (251, 1) if projection_kind else (249, 2) if document_reading else (237, 12)
    if len(before) != before_count or len(expected) != added_count or any(row["id"] in expected for row in before):
        raise ValueError("当前迁移登记与指定独立变更范围不一致")
    library = ROOT / "business/medical-data/target/medical-data-3.9.2.jar"
    cli = ROOT / "business/medical-data/target/medical-data-3.9.2-cli.jar"
    with ZipFile(library) as archive:
        for path in sorted((RESOURCES / "db").rglob("*")):
            if path.is_file() and archive.read(str(path.relative_to(RESOURCES))) != path.read_bytes():
                raise ValueError("当前正式迁移制品与完整源码资源不一致")
    library_hash, cli_hash = digest(library), digest(cli)
    migrate(migration_id)
    after = changes(password)
    current = {row["id"]: row for row in after}
    if len(current) != before_count + added_count or len(after) != before_count + added_count or any(current.get(row["id"]) != row for row in before):
        raise ValueError("原迁移登记变化或当前登记数量不完整")
    if set(current) - {row["id"] for row in before} != set(expected):
        raise ValueError("新增迁移登记必须恰好对应指定原变更")
    if digest(library) != library_hash or digest(cli) != cli_hash:
        raise ValueError("原迁移执行过程中正式制品变化")
    backup = RUNTIME / "migration-backups" / (migration_id + ".sql.gz")
    backup_receipt = backup.with_suffix("").with_suffix(".json")
    recorded = json.loads(backup_receipt.read_bytes())
    if recorded["database"] != identity["database"] or recorded["server_uuid"] != identity["server_uuid"] \
            or recorded["path"] != str(backup.relative_to(ROOT)) or recorded["sha256"] != digest(backup):
        raise ValueError("迁移前备份身份或字节摘要不一致")
    for path in (backup, backup_receipt):
        if path.stat().st_mode & 0o7777 != 0o600:
            raise ValueError("迁移前备份必须保持0600权限")
    decompressed_bytes = 0
    with gzip.open(backup, "rb") as stream:
        for chunk in iter(lambda: stream.read(65536), b""):
            decompressed_bytes += len(chunk)
    if decompressed_bytes <= 0:
        raise ValueError("实际数据库备份内容为空")
    report = {"database_identity": actual, "original_empty_counts": counts,
        "before_changes": before, "after_changes": after, "applied_changes": expected,
        "library_sha256": library_hash, "cli_sha256": cli_hash, "migration_sha256": digest(migration),
        "backup": recorded, "backup_decompressed_bytes": decompressed_bytes,
        "migration_applied": True, "physical_relation_shape_verified": False,
        "public_task_write_verified": False, "customer_success_verified": False, "full_goal_complete": False}
    (output / "migration.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def main():
    parser = argparse.ArgumentParser(description="核验正式计划关系制品，实际备份并应用原十二项迁移")
    parser.add_argument("--execution", required=True)
    parser.add_argument("--migration-id")
    parser.add_argument("--perform", action="store_true")
    parser.add_argument("--document-reading", action="store_true")
    parser.add_argument("--projection-kind", action="store_true")
    parser.add_argument("--revision-projections", action="store_true")
    args = parser.parse_args()
    if sum((args.document_reading, args.projection_kind, args.revision_projections)) > 1:
        raise ValueError("迁移检查必须选择单一变更范围")
    if Path(args.execution).name != args.execution or args.execution in (".", ".."):
        raise ValueError("执行身份必须是单一目录名称")
    migration_id = str(uuid4()) if args.migration_id is None else args.migration_id
    if str(UUID(migration_id)) != migration_id:
        raise ValueError("原迁移身份必须使用标准UUID")
    output = ROOT / ".implementation-work" / ("IC-03-TASK-PLAN-MIGRATION-" + args.execution)
    if args.perform:
        perform(output.resolve(strict=True), migration_id, args.document_reading, args.projection_kind, args.revision_projections)
        return
    inputs = [Path(__file__).resolve(), ROOT / "scripts/local_runtime.py", ROOT / "scripts/run_migration.py",
        RUNTIME / "service-identity.json", ROOT / "business/medical-data/target/medical-data-3.9.2.jar",
        ROOT / "business/medical-data/target/medical-data-3.9.2-cli.jar",
        ROOT / ".implementation-work/toolchains/mysql-8.4.3-macos14-x86_64/bin/mysql",
        ROOT / ".implementation-work/toolchains/mysql-8.4.3-macos14-x86_64/bin/mysqldump",
        ROOT / ".implementation-work/toolchains/jdk-17.0.20.1+1/Contents/Home/bin/java"]
    inputs.extend(path for path in sorted((RESOURCES / "db").rglob("*")) if path.is_file())
    capture = [sys.executable, str(CAPTURE), "--output-dir", str(output), "--check-id", "IC-03-TASK-PLAN-MIGRATION",
        "--kind", "INTEGRATION", "--baseline-id", "BACK-TASK-PLAN-RELATIONS-01", "--cwd", str(ROOT), "--timeout", "600"]
    for path in inputs:
        capture.extend(("--input", str(path)))
    for path in (output / "migration.json", RUNTIME / "migration.log",
            RUNTIME / "migration-backups" / (migration_id + ".json"), RUNTIME / "migration-backups" / (migration_id + ".sql.gz")):
        capture.extend(("--artifact", str(path)))
    capture.extend(("--", sys.executable, __file__, "--execution", args.execution,
        "--migration-id", migration_id, "--perform"))
    if args.document_reading:
        capture.append("--document-reading")
    if args.projection_kind:
        capture.append("--projection-kind")
    if args.revision_projections:
        capture.append("--revision-projections")
    subprocess.run(capture, cwd=ROOT, check=True)


if __name__ == "__main__":
    main()
