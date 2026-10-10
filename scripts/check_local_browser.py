import argparse
import fcntl
import json
import os
from pathlib import Path
import plistlib
import subprocess
import sys
import uuid

from local_runtime import mysql, settings, verify_application_deployment


ROOT = Path(__file__).resolve().parents[1]
RUNTIME = ROOT / ".implementation-work/runtime"
CAPTURE = Path("/Users/liuhaoxian/.agents/skills/haonan-s000-pmp/scripts/capture_check.py")
CHROME = Path("/Applications/Google Chrome.app/Contents")


def browser_identity():
    metadata = plistlib.loads((CHROME / "Info.plist").read_bytes())
    version = metadata["CFBundleShortVersionString"]
    if metadata["CFBundleIdentifier"] != "com.google.Chrome" or not isinstance(version, str):
        raise ValueError("浏览器发行身份无效")
    framework = CHROME / "Frameworks/Google Chrome Framework.framework/Versions" / version / "Google Chrome Framework"
    return version, [str((CHROME / "Info.plist").resolve(strict=True)),
                     str((CHROME / "MacOS/Google Chrome").resolve(strict=True)), str(framework.resolve(strict=True)),
                     "/usr/local/bin/node"]


def run_checks():
    before = verify_application_deployment()
    with (RUNTIME / "owner-recovery.lock").open("a", encoding="utf-8") as lock:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        subprocess.run(["/usr/local/bin/node", "tests/browser/local-account-checks.js"], cwd=ROOT, check=True)
        result = json.loads((RUNTIME / "local-browser-receipt.json").read_bytes())
        if result["browser_version"] != browser_identity()[0]:
            raise ValueError("运行浏览器版本与原生发行文件不一致")
        account_check = result["checks"][-1]
        key = str(uuid.UUID(account_check["idempotency_key"]))
        user_id = str(uuid.UUID(account_check["user_id"]))
        selection = "SELECT operation_id FROM medical_local.platform_local_operation WHERE subject_id='" + user_id + "' AND idempotency_key='" + key + "'"
        operation = str(uuid.UUID(mysql(selection, settings()["root_password"]).stdout.strip()))
        persisted = json.loads(mysql("SELECT JSON_OBJECT('operation_count',COUNT(*),'audit_count',"
            "(SELECT COUNT(*) FROM medical_local.platform_local_audit WHERE operation_id='" + operation + "'),"
            "'audit',(SELECT resource_ref FROM medical_local.platform_local_audit WHERE operation_id='" + operation + "'),"
            "'response',MIN(CAST(response_ref AS CHAR)),'current_credential_revision',"
            "(SELECT CAST(credential_revision AS CHAR) FROM medical_local.platform_local_account WHERE user_id='" + user_id + "')) "
            "FROM medical_local.platform_local_operation WHERE operation_id='" + operation + "'", settings()["root_password"]).stdout)
        if persisted["operation_count"] != 1 or persisted["audit_count"] != 1:
            raise ValueError("浏览器变更必须具有唯一操作及审计")
        envelope = json.loads(persisted["response"])
        account = envelope["data"]["account"]
        if envelope["request_id"] != account_check["request_id"] or envelope["data"]["operation_id"] != operation:
            raise ValueError("数据库原响应必须对应浏览器的实际请求及操作")
        if account["user_id"] != user_id or account["revision"] != str(int(account_check["previous_revision"]) + 1):
            raise ValueError("浏览器变更目标或原响应版本无效")
        if envelope["resource_revision"] != account["revision"] or not envelope["data"]["reauthentication_required"]:
            raise ValueError("原响应版本及重新认证要求无效")
        if account["credential_revision"] != account_check["credential_revision"] or int(account["credential_revision"]) != int(account_check["previous_credential_revision"]) + 1:
            raise ValueError("浏览器变更凭据版本无效")
        expected = {"schema_version": "1.0.0", "resource_kind": "LOCAL_ACCOUNT", "user_id": account["user_id"],
                    "revision": account["revision"], "credential_revision": account["credential_revision"], "status": "ACTIVE"}
        if persisted["audit"] != expected:
            raise ValueError("浏览器响应与数据库操作或审计不一致")
        if persisted["current_credential_revision"] != account["credential_revision"]:
            raise ValueError("当前数据库凭据版本与浏览器变更不一致")
        if verify_application_deployment() != before:
            raise ValueError("浏览器检查期间运行程序发生变化")
        result["deployment_id"] = before["deployment_id"]
        result["artifact_sha256"] = before["sha256"]
        account_check["operation_id"] = operation
        account_check["result_envelope"] = envelope
        result["database_audit"] = {"operation_count": 1, "audit_count": 1, "audit": persisted["audit"], "response_matches": True}
        (RUNTIME / "local-browser-receipt.json").write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
    print("LOCAL_BROWSER_DATABASE_AUDIT PASS")


def main():
    parser = argparse.ArgumentParser(description="执行真实本机管理员的浏览器及数据库检查")
    parser.add_argument("--execution")
    parser.add_argument("--run", action="store_true")
    args = parser.parse_args()
    if args.run:
        if args.execution is not None:
            raise ValueError("原生执行与证据登记入口不能同时指定")
        run_checks()
        return
    if not args.execution or Path(args.execution).name != args.execution or args.execution in (".", ".."):
        raise ValueError("执行身份必须为单一目录名称")
    deployment = verify_application_deployment()
    inputs = ["scripts/check_local_browser.py", "scripts/local_runtime.py", "tests/browser/local-account-checks.js",
              "tests/browser/package.json", "tests/browser/package-lock.json", ".implementation-work/browser-cli.json",
              "backend-design/接口补充设计.md", "LLD-后端.md", "business/medical-identity/pom.xml",
              "business/ruoyi-admin/target/ruoyi-admin.jar", ".implementation-work/runtime/application-deployment.json",
              ".implementation-work/runtime/service-identity.json", deployment["artifact"],
              "business/ruoyi-admin/src/main/resources/application-medical-local.yml"]
    for directory in ("business/medical-identity/src", "business/medical-persistence/src", "business/medical-data/src"):
        inputs.extend(sorted(str(path.relative_to(ROOT)) for path in (ROOT / directory).rglob("*") if path.is_file()))
    for package in ("@playwright/cli", "playwright", "playwright-core"):
        inputs.append("tests/browser/node_modules/" + package + "/package.json")
    inputs.extend(["tests/browser/node_modules/playwright/index.mjs", "tests/browser/node_modules/playwright-core/index.js",
                   "tests/browser/node_modules/playwright-core/lib/coreBundle.js",
                   "tests/browser/node_modules/playwright-core/lib/utilsBundle.js"])
    inputs.extend(browser_identity()[1])
    command = [sys.executable, str(CAPTURE), "--output-dir", ".implementation-work/IC-04-LOCAL-BROWSER-" + args.execution,
               "--check-id", "IC-04-LOCAL-BROWSER", "--kind", "INTEGRATION", "--baseline-id", "BACK-LOCAL-01",
               "--cwd", str(ROOT), "--timeout", "300"]
    for path in inputs:
        command.extend(["--input", path])
    command.extend(["--artifact", ".implementation-work/runtime/local-browser-receipt.json"])
    environment = os.environ.copy()
    environment["TMPDIR"] = str(ROOT / ".implementation-work/migration-check-tmp")
    subprocess.run([*command, "--", sys.executable, "scripts/check_local_browser.py", "--run"], cwd=ROOT, env=environment, check=True)


if __name__ == "__main__":
    main()
