import argparse
import os
from pathlib import Path
import subprocess
import sys


ROOT = Path(__file__).resolve().parents[1]
from check_support import capture_check_path
CAPTURE = capture_check_path()


def main():
    parser = argparse.ArgumentParser(description="保存真实原操作查询与当前企业权限检查证据")
    parser.add_argument("--execution", required=True)
    args = parser.parse_args()
    if Path(args.execution).name != args.execution or args.execution in (".", ".."):
        raise ValueError("执行身份必须为单一目录名称")
    from local_runtime import verify_application_deployment
    deployment = verify_application_deployment()
    inputs = ["scripts/check_operations.py", "scripts/local_runtime.py", "tests/test_operation_runtime.py",
              "tests/test_local_identity_runtime.py", "backend-design/接口补充设计.md", "LLD-后端.md",
              ".implementation-work/runtime/application-deployment.json", ".implementation-work/runtime/service-identity.json",
              deployment["artifact"], "business/ruoyi-admin/target/ruoyi-admin.jar"]
    for directory in ("business/medical-identity/src", "business/medical-persistence/src", "business/medical-data/src"):
        inputs.extend(sorted(str(path.relative_to(ROOT)) for path in (ROOT / directory).rglob("*") if path.is_file()))
    command = [sys.executable, str(CAPTURE), "--output-dir", ".implementation-work/IC-05-OPERATIONS-" + args.execution,
               "--check-id", "IC-05-OPERATIONS", "--kind", "INTEGRATION", "--baseline-id", "BACK-OPERATION-READ-01",
               "--cwd", str(ROOT), "--timeout", "180"]
    for path in inputs:
        command.extend(["--input", path])
    environment = os.environ.copy()
    environment["TMPDIR"] = str(ROOT / ".implementation-work/migration-check-tmp")
    subprocess.run([*command, "--", sys.executable, "tests/test_operation_runtime.py"], cwd=ROOT, env=environment, check=True)
    if deployment != verify_application_deployment():
        raise ValueError("原操作检查期间运行程序身份发生变化")


if __name__ == "__main__":
    main()
