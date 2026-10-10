import argparse
import os
from pathlib import Path
import subprocess
import sys


ROOT = Path(__file__).resolve().parents[1]
CAPTURE = Path("/Users/liuhaoxian/.agents/skills/haonan-s000-pmp/scripts/capture_check.py")


def main():
    parser = argparse.ArgumentParser(description="核查原框架项目分页、完整筛选和企业保护")
    parser.add_argument("--execution", required=True)
    parser.add_argument("--suite", choices=("pagination", "project"), default="pagination")
    args = parser.parse_args()
    if Path(args.execution).name != args.execution or args.execution in (".", ".."):
        raise ValueError("执行身份必须使用单一目录名称")
    tools = ROOT / ".implementation-work/toolchains"
    temporary = ROOT / ".implementation-work/project-pagination-tmp"
    temporary.mkdir(parents=True, exist_ok=True)
    environment = os.environ.copy()
    environment.update({"JAVA_HOME": str(tools / "jdk-17.0.20.1+1/Contents/Home"), "TMPDIR": str(temporary),
        "MAVEN_OPTS": "-Dfile.encoding=UTF-8 -Djava.io.tmpdir=" + str(temporary)})
    inputs = ["scripts/check_project_pagination.py", "business/pom.xml", "business/medical-persistence/pom.xml",
        "backend-design/接口补充设计.md", "LLD-后端.md", ".implementation-work/runtime/service-identity.json"]
    for directory in ("business/medical-contracts/src", "business/medical-data/src", "business/medical-persistence/src", "business/medical-identity/src"):
        inputs.extend(str(path.relative_to(ROOT)) for path in sorted((ROOT / directory).rglob("*")) if path.is_file())
    if args.suite == "project":
        inputs.append("tests/check_project_sql_columns.py")
    command = [sys.executable, str(CAPTURE), "--output-dir", ".implementation-work/IC-04-PROJECT-PAGINATION-" + args.execution,
        "--check-id", "IC-04-PROJECT-PAGINATION", "--kind", "INTEGRATION", "--baseline-id", "BACK-PROJECT-PAGINATION-01",
        "--cwd", str(ROOT), "--timeout", "600"]
    reports = ["ProjectPaginationTest"]
    if args.suite == "project":
        reports.append("ProjectSchemaTest")
        command.extend(["--artifact", ".implementation-work/project-query-columns.json"])
    for path in inputs:
        command.extend(["--input", path])
    for name in reports:
        command.extend(["--artifact", "business/medical-persistence/target/surefire-reports/TEST-com.ruoyi.medical.persistence." + name + ".xml"])
    command.extend(["--", str(tools / "apache-maven-3.9.16/bin/mvn"), "-f", "business/pom.xml", "--batch-mode", "--no-transfer-progress",
        "-Dmaven.repo.local=" + str(ROOT / ".implementation-work/maven-repository"), "-pl", "medical-persistence", "-am",
        "-Dtest=" + ",".join(reports), "-Dsurefire.failIfNoSpecifiedTests=false", "test"])
    result = subprocess.run(command, cwd=ROOT, env=environment, check=False)
    raise SystemExit(result.returncode)


if __name__ == "__main__":
    main()
