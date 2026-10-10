import argparse
import json
import os
from pathlib import Path
import subprocess
import sys


ROOT = Path(__file__).resolve().parents[1]
CAPTURE = Path("/Users/liuhaoxian/.agents/skills/haonan-s000-pmp/scripts/capture_check.py")


def main():
    parser = argparse.ArgumentParser(description="保存本机真实账户和数据库运行证据")
    parser.add_argument("--execution", required=True)
    parser.add_argument("--test", choices=("test_actual_owner_reads_business_session_without_enterprise_permissions",
                                         "test_tenant_selection_rejects_unjoined_owner_without_context_update",
                                         "test_local_admin_reads_accounts_from_actual_directory",
                                         "test_current_session_returns_prebound_local_identity"))
    args = parser.parse_args()
    if Path(args.execution).name != args.execution or args.execution in (".", ".."):
        raise ValueError("执行身份必须为单一目录名称")
    inputs = ["scripts/check_local_identity.py", "scripts/local_runtime.py", "scripts/RecoverLocalOwner.java",
              "contracts/python/requirements.lock", "contracts/python/pyproject.toml",
              "scripts/run_migration.py", "tests/test_local_identity_runtime.py", "LLD-后端.md",
              "backend-design/接口补充设计.md", "business/medical-identity/pom.xml",
              "business/medical-persistence/pom.xml",
              "business/ruoyi-admin/pom.xml", "business/ruoyi-admin/target/ruoyi-admin.jar",
              ".implementation-work/runtime/application-deployment.json",
              ".implementation-work/runtime/service-identity.json",
              "business/ruoyi-admin/src/main/resources/application-medical-local.yml",
              "business/ruoyi-admin/src/main/resources/logback-medical.xml"]
    deployment = json.loads((ROOT / ".implementation-work/runtime/application-deployment.json").read_bytes())
    artifact = Path(deployment["artifact"])
    if artifact.is_absolute() or ".." in artifact.parts or not artifact.is_relative_to(".implementation-work/runtime/applications"):
        raise ValueError("运行程序的路径必须位于本项目的版本目录")
    inputs.append(str(artifact))
    for directory in ("business/medical-identity/src", "business/medical-persistence/src", "business/medical-data/src",
                      "business/ruoyi-admin/src/main/java/com/ruoyi/medical", "business/ruoyi-framework/src",
                      "business/ruoyi-system/src", "business/ruoyi-common/src"):
        inputs.extend(sorted(str(path.relative_to(ROOT)) for path in (ROOT / directory).rglob("*") if path.is_file()))
    command = [sys.executable, str(CAPTURE), "--output-dir", ".implementation-work/IC-04-LOCAL-HTTP-" + args.execution,
               "--check-id", "IC-04-LOCAL-HTTP", "--kind", "INTEGRATION", "--baseline-id", "BACK-LOCAL-01",
               "--cwd", str(ROOT), "--timeout", "600"]
    for path in inputs:
        command.extend(["--input", path])
    for path in (".implementation-work/runtime/owner-account-receipt.json",
                 ".implementation-work/runtime/database-audit-receipt.json",
                 ".implementation-work/runtime/owner-lockout-receipt.json",
                 ".implementation-work/runtime/owner-credential-receipt.json",
                 ".implementation-work/runtime/business-session-receipt.json",
                 ".implementation-work/runtime/tenant-selection-rejection-receipt.json",
                 ".implementation-work/runtime/local-account-management-receipt.json",
                 ".implementation-work/runtime/local-account-credential-receipt.json",
                 ".implementation-work/runtime/current-identity-binding-receipt.json"):
        command.extend(["--artifact", path])
    environment = os.environ.copy()
    environment["TMPDIR"] = str(ROOT / ".implementation-work/migration-check-tmp")
    checks = [sys.executable, "tests/test_local_identity_runtime.py"]
    if args.test is not None:
        checks.append("LocalIdentityRuntimeChecks." + args.test)
    subprocess.run([*command, "--", *checks],
                   cwd=ROOT, env=environment, check=True)


if __name__ == "__main__":
    main()
