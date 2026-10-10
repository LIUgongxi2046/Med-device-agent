import argparse
import csv
import hashlib
import io
import json
import os
from pathlib import Path
import subprocess
import sys
from uuid import uuid4

import sqlglot
import sqlparse

from local_runtime import migrate, mysql, settings, verify_application_deployment


ROOT = Path(__file__).resolve().parents[1]
CAPTURE = Path("/Users/liuhaoxian/.agents/skills/haonan-s000-pmp/scripts/capture_check.py")


def database_snapshot():
    password = settings()["root_password"]
    tables = json.loads(mysql("SELECT JSON_ARRAYAGG(TABLE_NAME) FROM information_schema.tables "
        "WHERE TABLE_SCHEMA='medical_local' AND TABLE_TYPE='BASE TABLE' AND TABLE_NAME NOT IN "
        "('DATABASECHANGELOG','DATABASECHANGELOGLOCK')", password).stdout)
    query = "CHECKSUM TABLE " + ",".join(sqlglot.exp.Table(this=sqlglot.exp.to_identifier(name),
        db=sqlglot.exp.to_identifier("medical_local")).sql(dialect="mysql") for name in sorted(tables)) + " EXTENDED"
    checksums = list(csv.reader(io.StringIO(mysql(query, password).stdout), delimiter="\t"))
    if len(checksums) != len(tables) or any(len(row) != 2 or row[1] == "NULL" for row in checksums):
        raise ValueError("完整原表校验结果缺少或无效")
    changes = json.loads(mysql("SELECT JSON_ARRAYAGG(JSON_OBJECT('id',ID,'author',AUTHOR,'filename',FILENAME,"
        "'checksum',MD5SUM)) FROM medical_local.DATABASECHANGELOG", password).stdout)
    return {"tables": {row[0]: row[1] for row in checksums}, "changes": sorted(changes, key=lambda row: (row["filename"], row["id"]))}


def apply_migration(output, identity):
    deployment = verify_application_deployment(); before = database_snapshot()
    (output / "before.json").write_text(json.dumps(before), encoding="utf-8")
    migrate(identity)
    after = database_snapshot(); expected = {"medical_local." + name for name in (
        "sys_oper_log", "sys_dict_type", "sys_dict_data", "sys_logininfor", "sys_job", "sys_job_log", "sys_notice",
        "sys_notice_read", "gen_table", "gen_table_column")}
    if set(after["tables"]) - set(before["tables"]) != expected:
        raise ValueError("新增原管理表集合不完整")
    if any(after["tables"].get(name) != checksum for name, checksum in before["tables"].items()):
        raise ValueError("原数据库内容校验不一致")
    if len(after["changes"]) != len(before["changes"]) + len(expected) or any(row not in after["changes"] for row in before["changes"]):
        raise ValueError("原迁移身份或校验不一致")
    if deployment != verify_application_deployment():
        raise ValueError("原应用运行身份改变")
    backup = ROOT / ".implementation-work/runtime/migration-backups" / (identity + ".sql.gz")
    receipt = json.loads(backup.with_suffix(".json").with_name(identity + ".json").read_bytes())
    if hashlib.sha256(backup.read_bytes()).hexdigest() != receipt["sha256"]:
        raise ValueError("迁移备份摘要不一致")
    (output / "migration-result.json").write_text(json.dumps({"before": before, "after": after,
        "existing_tables_unchanged": True, "existing_changes_unchanged": True, "backup": receipt,
        "deployment": deployment, "application_deployment_unchanged": True, "customer_success_verified": False}), encoding="utf-8")


def main():
    parser = argparse.ArgumentParser(description="核查原RuoYi管理数据库与原Mapper兼容性")
    parser.add_argument("--execution", required=True)
    parser.add_argument("--check", choices=("schema", "build", "mappers", "migrate"), default="schema")
    parser.add_argument("--run-migration", type=Path)
    parser.add_argument("--migration-id")
    args = parser.parse_args()
    if Path(args.execution).name != args.execution or args.execution in (".", ".."):
        raise ValueError("执行身份必须使用单一目录名称")
    output = ROOT / ".implementation-work" / ("IC-03-RUOYI-MANAGEMENT-" + args.execution)
    if args.run_migration is not None:
        if args.check != "migrate" or args.migration_id is None or args.run_migration.resolve(strict=True) != output.resolve(strict=True):
            raise ValueError("迁移执行参数不一致")
        apply_migration(output, args.migration_id)
        return
    inputs = ["scripts/check_ruoyi_management_data.py", "tests/test_ruoyi_management_data.py",
        "tests/test_migration_bundle.py", "tests/InspectAlterMigrations.java", "tests/InspectFrameworkTables.java",
        "tests/test_local_identity_runtime.py", "scripts/local_runtime.py", "scripts/run_migration.py",
        "business/sql/ry_20260417.sql", "business/pom.xml", "business/medical-data/pom.xml", "data-design/core.sql",
        ".implementation-work/runtime/service-identity.json"]
    inputs.extend([str(Path(sqlglot.__file__).resolve(strict=True)), str(Path(sqlparse.__file__).resolve(strict=True)),
        ".implementation-work/toolchains/jdk-17.0.20.1+1/Contents/Home/bin/java"])
    inputs.extend(str(path.relative_to(ROOT)) for path in sorted((ROOT / "business/medical-data/src").rglob("*")) if path.is_file())
    artifacts = []
    environment = os.environ.copy()
    tools = ROOT / ".implementation-work/toolchains"
    environment.update({"JAVA_HOME": str(tools / "jdk-17.0.20.1+1/Contents/Home"),
        "TMPDIR": str(ROOT / ".implementation-work/java-temp"),
        "MAVEN_OPTS": "-Xms128m -Xmx512m -Dfile.encoding=UTF-8 -Djava.io.tmpdir=" + str(ROOT / ".implementation-work/java-temp")})
    if args.check == "migrate":
        identity = str(uuid4())
        inputs.extend(["business/medical-data/target/medical-data-3.9.2.jar", "business/medical-data/target/medical-data-3.9.2-cli.jar",
            ".implementation-work/runtime/application-deployment.json"])
        command = [sys.executable, "scripts/check_ruoyi_management_data.py", "--execution", args.execution,
            "--check", "migrate", "--run-migration", str(output), "--migration-id", identity]
        artifacts.extend([str(output / "before.json"), str(output / "migration-result.json"),
            ".implementation-work/runtime/migration.log", ".implementation-work/runtime/migration-backups/" + identity + ".sql.gz",
            ".implementation-work/runtime/migration-backups/" + identity + ".json"])
    elif args.check == "schema":
        command = [sys.executable, "tests/test_ruoyi_management_data.py"]
    else:
        selected = "medical-data" if args.check == "build" else "ruoyi-admin"
        command = [str(tools / "apache-maven-3.9.16/bin/mvn"), "-f", "business/pom.xml", "--batch-mode", "--no-transfer-progress",
            "-Dmaven.repo.local=" + str(ROOT / ".implementation-work/maven-repository"), "-pl", selected, "-am"]
        if args.check == "build":
            command.extend(["clean", "verify"])
            artifacts.extend(["business/medical-data/target/medical-data-3.9.2.jar", "business/medical-data/target/medical-data-3.9.2-cli.jar",
                "business/medical-data/target/surefire-reports/TEST-com.ruoyi.medical.data.MigrationBundleTest.xml"])
        else:
            command.extend(["-Dtest=RuoYiManagementMapperTest", "-Dsurefire.failIfNoSpecifiedTests=false",
                "-Dmedical.managementDataEvidence=" + str(output / "mapper-result.json"), "test"])
            for directory in ("business/ruoyi-admin/src", "business/medical-identity/src", "business/medical-persistence/src",
                    "business/ruoyi-common/src", "business/ruoyi-system/src", "business/ruoyi-framework/src",
                    "business/ruoyi-quartz/src", "business/ruoyi-generator/src"):
                inputs.extend(str(path.relative_to(ROOT)) for path in sorted((ROOT / directory).rglob("*")) if path.is_file())
            inputs.extend("business/" + module + "/pom.xml" for module in ("ruoyi-admin", "medical-contracts", "medical-persistence",
                "medical-identity", "ruoyi-common", "ruoyi-system", "ruoyi-framework", "ruoyi-quartz", "ruoyi-generator"))
            artifacts.extend(["business/ruoyi-admin/target/surefire-reports/TEST-com.ruoyi.medical.runtime.RuoYiManagementMapperTest.xml",
                str(output / "mapper-result.json")])
    capture = [sys.executable, str(CAPTURE), "--output-dir", str(output), "--check-id", "IC-03-RUOYI-MANAGEMENT",
        "--kind", "UNIT" if args.check == "build" else "INTEGRATION", "--baseline-id", "BACK-RUOYI-MANAGEMENT-DATA-01",
        "--cwd", str(ROOT), "--timeout", "900"]
    for path in sorted(set(inputs)):
        capture.extend(["--input", path])
    for artifact in artifacts:
        capture.extend(["--artifact", artifact])
    subprocess.run([*capture, "--", *command], cwd=ROOT, env=environment, check=True)
    receipt = json.loads((output / "receipt.json").read_bytes())
    if receipt["exit_code"] != 0 or not receipt["inputs_unchanged"]:
        raise ValueError("原框架检查必须通过且保持当前输入")


if __name__ == "__main__":
    main()
