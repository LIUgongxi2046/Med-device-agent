import argparse
import os
from pathlib import Path
import subprocess
import sys

from check_local_browser import browser_identity
from local_runtime import verify_application_deployment


ROOT = Path(__file__).resolve().parents[1]
from check_support import capture_check_path
CAPTURE = capture_check_path()


def main():
    parser = argparse.ArgumentParser(description="运行真实企业登记页面的浏览器DOM检查")
    parser.add_argument("--execution", required=True)
    args = parser.parse_args()
    if Path(args.execution).name != args.execution or args.execution in (".", ".."):
        raise ValueError("执行身份必须为单一目录名称")
    deployment = verify_application_deployment()
    inputs = ["scripts/check_enterprise_registration_browser.py", "scripts/check_local_browser.py", "scripts/local_runtime.py",
              "tests/browser/enterprise-registration-checks.js", "tests/browser/package.json", "tests/browser/package-lock.json",
              "backend-design/接口补充设计.md", ".implementation-work/runtime/application-deployment.json",
              ".implementation-work/runtime/service-identity.json", deployment["artifact"]]
    for directory in ("business/medical-identity/src", "business/medical-persistence/src", "tests/browser/node_modules"):
        inputs.extend(str(path.relative_to(ROOT)) for path in sorted((ROOT / directory).rglob("*"))
                      if path.is_file() and ("node_modules" not in path.parts or path.suffix in (".js", ".json", ".node")))
    inputs.extend(browser_identity()[1])
    command = [sys.executable, str(CAPTURE), "--output-dir", ".implementation-work/IC-04-REGISTRATION-BROWSER-" + args.execution,
               "--check-id", "IC-04-REGISTRATION-BROWSER", "--kind", "INTEGRATION", "--baseline-id", "BACK-REGISTRATION-01",
               "--cwd", str(ROOT), "--timeout", "90"]
    for path in inputs:
        command.extend(["--input", path])
    environment = os.environ.copy()
    environment["TMPDIR"] = str(ROOT / ".implementation-work/migration-check-tmp")
    subprocess.run([*command, "--", "/usr/local/bin/node", "tests/browser/enterprise-registration-checks.js"],
                   cwd=ROOT, env=environment, check=True)
    if verify_application_deployment() != deployment:
        raise ValueError("检查期间运行程序发生变化")


if __name__ == "__main__":
    main()
