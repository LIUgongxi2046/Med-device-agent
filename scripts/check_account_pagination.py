import argparse
import os
from pathlib import Path
import subprocess
import sys


ROOT = Path(__file__).resolve().parents[1]
CAPTURE = Path("/Users/liuhaoxian/.agents/skills/haonan-s000-pmp/scripts/capture_check.py")


def main():
    parser = argparse.ArgumentParser(description="核验原框架账户分页及真实身份扩展查询")
    parser.add_argument("--execution", required=True)
    parser.add_argument("--suite", choices=("pagination", "identity"), default="pagination")
    args = parser.parse_args()
    if Path(args.execution).name != args.execution or args.execution in (".", ".."):
        raise ValueError("执行身份必须使用单一目录名称")
    tools = ROOT / ".implementation-work/toolchains"
    temporary = ROOT / ".implementation-work/account-pagination-tmp"
    temporary.mkdir(parents=True, exist_ok=True)
    environment = os.environ.copy()
    environment.update({"JAVA_HOME": str(tools / "jdk-17.0.20.1+1/Contents/Home"), "TMPDIR": str(temporary),
        "MAVEN_OPTS": "-Dfile.encoding=UTF-8 -Djava.io.tmpdir=" + str(temporary)})
    inputs = ["scripts/check_account_pagination.py", "business/pom.xml", "business/medical-identity/pom.xml",
        "backend-design/接口补充设计.md", ".implementation-work/runtime/service-identity.json"]
    for directory in ("business/medical-identity/src", "business/ruoyi-system/src", "business/ruoyi-framework/src", "business/ruoyi-common/src"):
        inputs.extend(str(path.relative_to(ROOT)) for path in sorted((ROOT / directory).rglob("*")) if path.is_file())
    command = [sys.executable, str(CAPTURE), "--output-dir", ".implementation-work/IC-04-ACCOUNT-PAGINATION-" + args.execution,
        "--check-id", "IC-04-ACCOUNT-PAGINATION", "--kind", "INTEGRATION", "--baseline-id", "BACK-RUOYI-ACCOUNT-PAGINATION-01",
        "--cwd", str(ROOT), "--timeout", "600"]
    for path in inputs:
        command.extend(["--input", path])
    command.extend(["--artifact", "business/medical-identity/target/surefire-reports/TEST-com.ruoyi.medical.identity.LocalAccountPaginationTest.xml",
        "--", str(tools / "apache-maven-3.9.16/bin/mvn"), "-f", "business/pom.xml", "--batch-mode", "--no-transfer-progress",
        "-Dmaven.repo.local=" + str(ROOT / ".implementation-work/maven-repository"), "-pl", "medical-identity", "-am"])
    if args.suite == "pagination":
        command.extend(["-Dtest=LocalAccountPaginationTest", "-Dsurefire.failIfNoSpecifiedTests=false"])
    command.append("test")
    result = subprocess.run(command, cwd=ROOT, env=environment, check=False)
    raise SystemExit(result.returncode)


if __name__ == "__main__":
    main()
