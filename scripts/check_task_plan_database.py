import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import sys
from xml.etree import ElementTree

from local_runtime import mysql, settings


ROOT = Path(__file__).resolve().parents[1]
CAPTURE = Path("/Users/liuhaoxian/.agents/skills/haonan-s000-pmp/scripts/capture_check.py")
MIGRATION = "business/medical-data/src/main/resources/db/migration/036-task-plan-relations.xml"


def inspect(output):
    identity = json.loads((ROOT / ".implementation-work/runtime/service-identity.json").read_bytes())
    password = settings()["root_password"]
    actual = json.loads(mysql("SELECT JSON_OBJECT('server_uuid',@@server_uuid,'version',VERSION(),"
        "'database','medical_local','port',@@port)", password).stdout)
    if any(actual[key] != value for key, value in identity.items()) or actual["port"] != 13306:
        raise ValueError("当前数据库身份与登记服务不一致")
    query = """
        SELECT JSON_OBJECT(
            'plan_section',(SELECT COUNT(*) FROM medical_local.plan_section),
            'plan_dependency',(SELECT COUNT(*) FROM medical_local.plan_dependency),
            'research_question',(SELECT COUNT(*) FROM medical_local.research_question),
            'task_head',(SELECT COUNT(*) FROM medical_local.task_head),
            'task_revision',(SELECT COUNT(*) FROM medical_local.task_revision),
            'plan_revision',(SELECT COUNT(*) FROM medical_local.plan_revision))
        """
    counts = json.loads(mysql(query, password).stdout)
    if any(counts[name] != 0 for name in ("plan_section", "plan_dependency", "research_question")):
        raise ValueError("计划关系迁移需要的原表为空条件尚未满足")
    changes = json.loads(mysql("SELECT JSON_ARRAYAGG(JSON_OBJECT('id',ID,'author',AUTHOR,'filename',FILENAME,"
        "'checksum',MD5SUM)) FROM medical_local.DATABASECHANGELOG", password).stdout)
    namespace = {"lb": "http://www.liquibase.org/xml/ns/dbchangelog"}
    expected_changes = [entry.attrib["id"] for entry in ElementTree.parse(ROOT / MIGRATION).findall("lb:changeSet", namespace)]
    if len(expected_changes) != 12 or any(row["id"] in expected_changes for row in changes):
        raise ValueError("计划迁移必须包含十二项尚未应用的原变更")
    tables = json.loads(mysql("SELECT JSON_ARRAYAGG(TABLE_NAME) FROM information_schema.tables "
        "WHERE TABLE_SCHEMA='medical_local' AND TABLE_TYPE='BASE TABLE'", password).stdout)
    coverage = json.loads((ROOT / "business/medical-data/src/main/resources/db/migration/coverage.json").read_bytes())
    expected_tables = coverage["required_tables"]["task_plan_relations"]
    if len(expected_tables) != 8 or set(expected_tables) & set(tables):
        raise ValueError("计划关系新增表已经存在或集合不完整")
    constraints = json.loads(mysql("SELECT JSON_ARRAYAGG(JSON_OBJECT('table',TABLE_NAME,'constraint',CONSTRAINT_NAME,"
        "'column',COLUMN_NAME,'position',ORDINAL_POSITION,'referenced_table',REFERENCED_TABLE_NAME,"
        "'referenced_column',REFERENCED_COLUMN_NAME)) FROM information_schema.key_column_usage "
        "WHERE TABLE_SCHEMA='medical_local' AND TABLE_NAME IN ('plan_section','plan_dependency','research_question','plan_revision')",
        password).stdout)
    original = sorted((row for row in constraints if row["table"] == "plan_section"
        and row["constraint"] == "plan_section_ibfk_2"), key=lambda row: row["position"])
    if [(row["column"], row["referenced_table"], row["referenced_column"]) for row in original] != [
            ("tenant_id", "requirement_node", "tenant_id"),
            ("template_version", "requirement_node", "template_version"),
            ("node_id", "requirement_node", "node_id")]:
        raise ValueError("章节原节点外键与迁移依据不一致")
    columns = json.loads(mysql("SELECT JSON_ARRAYAGG(JSON_OBJECT('table',TABLE_NAME,'column',COLUMN_NAME,'type',COLUMN_TYPE,"
        "'nullable',IS_NULLABLE,'position',ORDINAL_POSITION)) FROM information_schema.columns "
        "WHERE TABLE_SCHEMA='medical_local' AND TABLE_NAME IN ('plan_section','plan_dependency','research_question','plan_revision')",
        password).stdout)
    report = {"database_identity": actual, "counts": counts, "changes": changes, "tables": sorted(tables),
        "constraints": constraints, "columns": columns, "pending_changes": expected_changes, "new_tables": expected_tables,
        "migration_sha256": hashlib.sha256((ROOT / MIGRATION).read_bytes()).hexdigest(),
        "migration_preconditions_verified": True, "migration_applied": False, "public_task_write_verified": False,
        "customer_success_verified": False}
    (output / "database.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def main():
    parser = argparse.ArgumentParser(description="实际读取当前计划迁移条件与数据库结构")
    parser.add_argument("--execution", required=True)
    parser.add_argument("--inspect", action="store_true")
    args = parser.parse_args()
    if Path(args.execution).name != args.execution or args.execution in (".", ".."):
        raise ValueError("执行标识必须是单一目录名称")
    output = ROOT / ".implementation-work" / ("IC-12-PLAN-DATABASE-" + args.execution)
    if args.inspect:
        inspect(output.resolve(strict=True))
        return
    inputs = [MIGRATION, "scripts/check_task_plan_database.py", "scripts/local_runtime.py",
        ".implementation-work/runtime/service-identity.json",
        "business/medical-data/src/main/resources/db/changelog/master.xml",
        "business/medical-data/src/main/resources/db/migration/coverage.json",
        ".implementation-work/toolchains/mysql-8.4.3-macos14-x86_64/bin/mysql"]
    command = [sys.executable, str(CAPTURE), "--output-dir", str(output), "--check-id", "IC-12-PLAN-DATABASE",
        "--kind", "INTEGRATION", "--baseline-id", "BACK-TASK-PLAN-RELATIONS-01", "--cwd", str(ROOT), "--timeout", "120"]
    for path in inputs:
        command.extend(["--input", path])
    command.extend(["--artifact", str(output / "database.json"), "--", sys.executable, __file__,
        "--execution", args.execution, "--inspect"])
    subprocess.run(command, cwd=ROOT, check=True)


if __name__ == "__main__":
    main()
