import argparse
import json
import os
from pathlib import Path
import subprocess
import sys

from check_local_browser import browser_identity
from local_runtime import mysql, settings, verify_application_deployment


ROOT = Path(__file__).resolve().parents[1]
from check_support import capture_check_path
CAPTURE = capture_check_path()


def customer_counts():
    result = mysql("SELECT JSON_OBJECT('tenants',(SELECT COUNT(*) FROM medical_local.tenant),"
                   "'members',(SELECT COUNT(*) FROM medical_local.tenant_member),"
                   "'selections',(SELECT COUNT(*) FROM medical_local.operation_record WHERE operation_name='ID-04'))",
                   settings()["root_password"])
    counts = json.loads(result.stdout)
    if counts != {"tenants": 0, "members": 0, "selections": 0}:
        raise ValueError("当前检查要求真实账户尚未取得企业身份；已有企业时使用对应成功验收")
    return counts


def main():
    parser = argparse.ArgumentParser(description="验证真实账户企业选择页面及服务端状态")
    parser.add_argument("--execution")
    parser.add_argument("--run", action="store_true")
    args = parser.parse_args()
    if args.run:
        if args.execution is not None:
            raise ValueError("原生执行与证据登记入口不能同时指定")
        deployment = verify_application_deployment()
        before = customer_counts()
        subprocess.run(["/usr/local/bin/node", "tests/browser/enterprise-context-checks.js"], cwd=ROOT, check=True)
        after = customer_counts()
        if verify_application_deployment() != deployment or after != before:
            raise ValueError("检查期间程序或企业业务状态发生变化")
        print(json.dumps({"check_id": "IC-04-CONTEXT-BROWSER-DATABASE", "result": "PASS",
                          "deployment_id": deployment["deployment_id"], "artifact_sha256": deployment["sha256"],
                          "before": before, "after": after}, ensure_ascii=False))
        return
    if not args.execution or Path(args.execution).name != args.execution or args.execution in (".", ".."):
        raise ValueError("执行身份必须为单一目录名称")
    deployment = verify_application_deployment()
    inputs = ["scripts/check_enterprise_context_browser.py", "scripts/check_local_browser.py", "scripts/local_runtime.py",
              "tests/browser/enterprise-context-checks.js", "tests/browser/package.json", "tests/browser/package-lock.json",
              "backend-design/接口补充设计.md", "LLD-前端.md", ".implementation-work/runtime/application-deployment.json",
              ".implementation-work/runtime/service-identity.json", deployment["artifact"]]
    for directory in ("business/medical-identity/src", "business/medical-persistence/src", "tests/browser/node_modules"):
        inputs.extend(str(path.relative_to(ROOT)) for path in sorted((ROOT / directory).rglob("*"))
                      if path.is_file() and ("node_modules" not in path.parts or path.suffix in (".js", ".json", ".node")))
    inputs.extend(browser_identity()[1])
    command = [sys.executable, str(CAPTURE), "--output-dir", ".implementation-work/IC-04-CONTEXT-BROWSER-" + args.execution,
               "--check-id", "IC-04-CONTEXT-BROWSER", "--kind", "INTEGRATION", "--baseline-id", "BACK-CONTEXT-01",
               "--cwd", str(ROOT), "--timeout", "90"]
    for path in inputs:
        command.extend(["--input", path])
    environment = os.environ.copy()
    environment["TMPDIR"] = str(ROOT / ".implementation-work/migration-check-tmp")
    subprocess.run([*command, "--", sys.executable, "scripts/check_enterprise_context_browser.py", "--run"],
                   cwd=ROOT, env=environment, check=True)
    if verify_application_deployment() != deployment:
        raise ValueError("检查期间运行程序发生变化")


if __name__ == "__main__":
    main()
