import argparse
import os
from pathlib import Path
import subprocess
import sys

from local_runtime import verify_application_deployment


ROOT = Path(__file__).resolve().parents[1]
from check_support import capture_check_path
CAPTURE = capture_check_path()


def main():
    parser = argparse.ArgumentParser(description="保存真实预算接口、权限拒绝及零收费证据")
    parser.add_argument("--execution", required=True)
    args = parser.parse_args()
    if Path(args.execution).name != args.execution or args.execution in (".", ".."):
        raise ValueError("执行身份必须为单一目录名称")
    deployment = verify_application_deployment()
    inputs = ["scripts/check_budgets.py", "scripts/local_runtime.py", "tests/test_budget_runtime.py",
              "tests/test_local_identity_runtime.py", "backend-design/接口补充设计.md", "LLD-后端.md",
              ".implementation-work/runtime/application-deployment.json", ".implementation-work/runtime/service-identity.json",
              deployment["artifact"], "business/ruoyi-admin/target/ruoyi-admin.jar"]
    for directory in ("business/medical-identity/src", "business/medical-persistence/src", "business/medical-data/src"):
        inputs.extend(sorted(str(path.relative_to(ROOT)) for path in (ROOT / directory).rglob("*") if path.is_file()))
    command = [sys.executable, str(CAPTURE), "--output-dir", ".implementation-work/IC-07-BUDGETS-" + args.execution,
               "--check-id", "IC-07-BUDGETS", "--kind", "INTEGRATION", "--baseline-id", "BACK-BUDGET-MANAGEMENT-01",
               "--cwd", str(ROOT), "--timeout", "180"]
    for path in inputs:
        command.extend(["--input", path])
    environment = os.environ.copy()
    environment["TMPDIR"] = str(ROOT / ".implementation-work/migration-check-tmp")
    subprocess.run([*command, "--", sys.executable, "tests/test_budget_runtime.py"], cwd=ROOT, env=environment, check=True)
    if deployment != verify_application_deployment():
        raise ValueError("预算检查期间运行程序身份发生变化")


if __name__ == "__main__":
    main()
