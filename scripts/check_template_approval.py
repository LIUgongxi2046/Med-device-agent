import argparse
import os
from pathlib import Path
import subprocess
import sys
from uuid import uuid4


ROOT = Path(__file__).resolve().parents[1]
CAPTURE = Path("/Users/liuhaoxian/.agents/skills/haonan-s000-pmp/scripts/capture_check.py")


def main():
    parser = argparse.ArgumentParser(description="验证模板人工核准组件及追加迁移制品")
    parser.add_argument("--execution", required=True)
    parser.add_argument("--check", choices=("schema", "migration-build", "migrate"), default="schema")
    args = parser.parse_args()
    if Path(args.execution).name != args.execution or args.execution in (".", ".."):
        raise ValueError("执行身份必须使用单一目录名称")
    inputs = ["scripts/check_template_approval.py", "business/pom.xml", "PRD.md", "LLD-数据.md", "LLD-后端.md",
              "backend-design/接口补充设计.md", "contracts/catalogue.json", "contracts/extensions/evaluation-results.catalogue.json", "business/medical-persistence/src/main/resources/evaluation-results.schema.json"]
    for module in ("medical-contracts", "medical-data", "medical-identity", "medical-persistence"):
        inputs.append("business/" + module + "/pom.xml")
        inputs.extend(str(path.relative_to(ROOT)) for path in sorted((ROOT / "business" / module / "src").rglob("*")) if path.is_file())
    output = ".implementation-work/IC-11-APPROVAL-" + args.execution
    artifacts = ["business/medical-persistence/target/surefire-reports/TEST-com.ruoyi.medical.persistence.TemplateApprovalSchemaTest.xml"]
    options = ["-pl", "medical-persistence", "-am", "-Dtest=TemplateApprovalSchemaTest", "-Dsurefire.failIfNoSpecifiedTests=false", "test"]
    if args.check == "migration-build":
        artifacts = ["business/medical-data/target/medical-data-3.9.2.jar", "business/medical-data/target/medical-data-3.9.2-cli.jar"]
        options = ["-pl", "medical-data", "-am", "package"]
    working = ROOT / "business"
    if args.check == "migrate":
        migration = str(uuid4())
        inputs.extend(["scripts/local_runtime.py", "scripts/run_migration.py", ".implementation-work/runtime/service-identity.json",
                       "business/medical-data/target/medical-data-3.9.2.jar", "business/medical-data/target/medical-data-3.9.2-cli.jar"])
        artifacts = [".implementation-work/runtime/migration.log", ".implementation-work/runtime/migration-backups/" + migration + ".sql.gz",
                     ".implementation-work/runtime/migration-backups/" + migration + ".json"]
        working = ROOT
    capture = [sys.executable, str(CAPTURE), "--output-dir", output, "--check-id", "IC-11-APPROVAL",
               "--kind", "INTEGRATION" if args.check == "migrate" else "UNIT", "--baseline-id", "BACK-TEMPLATE-APPROVAL-01",
               "--cwd", str(working), "--timeout", "900"]
    for path in inputs:
        capture.extend(["--input", str(ROOT / path)])
    for path in artifacts:
        capture.extend(["--artifact", str(ROOT / path)])
    temporary = ROOT / ".implementation-work/template-approval-tmp"
    temporary.mkdir(parents=True, exist_ok=True)
    java = ROOT / ".implementation-work/toolchains/jdk-17.0.20.1+1/Contents/Home"
    maven = ROOT / ".implementation-work/toolchains/apache-maven-3.9.16/bin/mvn"
    environment = os.environ.copy()
    environment.update(JAVA_HOME=str(java), TMPDIR=str(temporary), MAVEN_OPTS="-Dfile.encoding=UTF-8 -Djava.io.tmpdir=" + str(temporary))
    command = [str(maven), "--batch-mode", "--no-transfer-progress", "-Dmaven.repo.local=" + str(ROOT / ".implementation-work/maven-repository"), *options]
    if args.check == "migrate":
        command = [sys.executable, str(ROOT / "scripts/local_runtime.py"), "migrate", "--migration-id", migration]
    subprocess.run([*capture, "--", *command], cwd=ROOT, env=environment, check=True)


if __name__ == "__main__":
    main()
