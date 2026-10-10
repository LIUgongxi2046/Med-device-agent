import argparse
import json
import os
from pathlib import Path
import subprocess
import sys

from check_local_browser import browser_identity
from local_runtime import mysql, settings, verify_application_deployment


ROOT = Path(__file__).resolve().parents[1]
CAPTURE = Path("/Users/liuhaoxian/.agents/skills/haonan-s000-pmp/scripts/capture_check.py")


def counts():
    result = mysql("SELECT JSON_OBJECT('tenants',(SELECT COUNT(*) FROM medical_local.tenant),"
                   "'roles',(SELECT COUNT(*) FROM medical_local.enterprise_role_version),"
                   "'grants',(SELECT COUNT(*) FROM medical_local.permission_grant))", settings()["root_password"])
    value = json.loads(result.stdout)
    if value != {"tenants": 0, "roles": 0, "grants": 0}:
        raise ValueError("当前拒绝检查要求实际账户没有企业及客户岗位；合法状态分别执行业务验收")
    return value


def main():
    parser = argparse.ArgumentParser(description="验证真实账户的企业岗位页面与数据库")
    parser.add_argument("--execution")
    parser.add_argument("--run", action="store_true")
    args = parser.parse_args()
    deployment = verify_application_deployment()
    if args.run:
        if args.execution is not None:
            raise ValueError("原生执行及证据登记入口不能同时指定")
        before = counts()
        subprocess.run(["/usr/local/bin/node", "tests/browser/enterprise-role-checks.js"], cwd=ROOT, check=True)
        after = counts()
        if after != before or verify_application_deployment() != deployment:
            raise ValueError("岗位页面检查期间企业记录或者程序身份发生变化")
        print(json.dumps({"check_id": "IC-04-ROLE-BROWSER-DATABASE", "result": "PASS", "deployment_id": deployment["deployment_id"],
                          "artifact_sha256": deployment["sha256"], "before": before, "after": after}, ensure_ascii=False))
        return
    if not args.execution or Path(args.execution).name != args.execution or args.execution in (".", ".."):
        raise ValueError("执行身份必须为单一目录名称")
    inputs = ["scripts/check_enterprise_role_browser.py", "scripts/check_local_browser.py", "scripts/local_runtime.py",
              "tests/browser/enterprise-role-checks.js", "tests/browser/package.json", "tests/browser/package-lock.json",
              "backend-design/接口补充设计.md", "LLD-前端.md", ".implementation-work/runtime/application-deployment.json",
              ".implementation-work/runtime/service-identity.json", deployment["artifact"]]
    for directory in ("business/medical-identity/src", "business/medical-persistence/src", "tests/browser/node_modules"):
        inputs.extend(str(path.relative_to(ROOT)) for path in sorted((ROOT / directory).rglob("*"))
                      if path.is_file() and ("node_modules" not in path.parts or path.suffix in (".js", ".json", ".node")))
    inputs.extend(browser_identity()[1])
    command = [sys.executable, str(CAPTURE), "--output-dir", ".implementation-work/IC-04-ROLE-BROWSER-" + args.execution,
               "--check-id", "IC-04-ROLE-BROWSER", "--kind", "INTEGRATION", "--baseline-id", "BACK-ROLE-GOVERNANCE-01", "--cwd", str(ROOT), "--timeout", "90"]
    for path in inputs:
        command.extend(["--input", path])
    environment = os.environ.copy(); environment["TMPDIR"] = str(ROOT / ".implementation-work/migration-check-tmp")
    subprocess.run([*command, "--", sys.executable, "scripts/check_enterprise_role_browser.py", "--run"], cwd=ROOT, env=environment, check=True)
    if verify_application_deployment() != deployment:
        raise ValueError("岗位页面检查期间程序身份发生变化")


if __name__ == "__main__":
    main()
