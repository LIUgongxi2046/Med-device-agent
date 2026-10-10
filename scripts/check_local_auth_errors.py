import argparse
import os
from pathlib import Path
import subprocess
import sys

from check_local_browser import browser_identity
from local_runtime import verify_application_deployment


ROOT = Path(__file__).resolve().parents[1]
CAPTURE = Path("/Users/liuhaoxian/.agents/skills/haonan-s000-pmp/scripts/capture_check.py")


def main():
    parser = argparse.ArgumentParser(description="检查真实浏览器连接中断与账户输入校验")
    parser.add_argument("--execution", required=True)
    args = parser.parse_args()
    if Path(args.execution).name != args.execution or args.execution in (".", ".."):
        raise ValueError("执行身份必须使用单一目录名称")
    deployment = verify_application_deployment()
    inputs = ["scripts/check_local_auth_errors.py", "scripts/check_local_browser.py", "scripts/local_runtime.py",
              "tests/browser/local-auth-errors.js", "tests/browser/package.json", "tests/browser/package-lock.json",
              "backend-design/接口补充设计.md", ".implementation-work/runtime/application-deployment.json",
              ".implementation-work/runtime/service-identity.json", deployment["artifact"]]
    inputs.extend(str(path.relative_to(ROOT)) for path in sorted((ROOT / "business/medical-identity/src").rglob("*")) if path.is_file())
    inputs.extend(browser_identity()[1])
    command = [sys.executable, str(CAPTURE), "--output-dir", ".implementation-work/IC-04-AUTH-ERRORS-" + args.execution,
               "--check-id", "IC-04-AUTH-ERRORS", "--kind", "INTEGRATION", "--baseline-id", "BACK-LOCAL-MANAGEMENT-01",
               "--cwd", str(ROOT), "--timeout", "90"]
    for path in inputs:
        command.extend(["--input", path])
    environment = os.environ.copy()
    environment["TMPDIR"] = str(ROOT / ".implementation-work/migration-check-tmp")
    subprocess.run([*command, "--", "/usr/local/bin/node", "tests/browser/local-auth-errors.js"],
                   cwd=ROOT, env=environment, check=True)
    if verify_application_deployment() != deployment:
        raise ValueError("测试期间运行程序发生变化")


if __name__ == "__main__":
    main()
