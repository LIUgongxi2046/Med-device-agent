import argparse
import datetime
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys

from local_runtime import verify_application_deployment


ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description="顺序执行全部现有运行检查并核对当前证据")
    parser.add_argument("--execution", required=True)
    args = parser.parse_args()
    if Path(args.execution).name != args.execution or args.execution in (".", ".."):
        raise ValueError("执行身份必须使用单一目录名称")
    output = ROOT / ".implementation-work" / ("FULL-REGRESSION-" + args.execution)
    output.mkdir()
    inputs = [{"path": str(ROOT / name), "sha256": hashlib.sha256((ROOT / name).read_bytes()).hexdigest()}
        for name in ("scripts/check_full_regression.py", "scripts/inspect_current_evidence.py",
            "scripts/check_full_function_inventory.py", "scripts/local_runtime.py")]
    before = verify_application_deployment()
    object_python = str(ROOT / ".implementation-work/object-storage-venv/bin/python")
    object_command = [object_python, "scripts/local_object_storage.py", "identity"]
    object_before = json.loads(subprocess.run(object_command, cwd=ROOT, capture_output=True,
        text=True, check=True, timeout=30).stdout)
    started = datetime.datetime.now(datetime.timezone.utc).isoformat()
    environment = os.environ.copy()
    environment["TMPDIR"] = str(ROOT / ".implementation-work/migration-check-tmp")
    steps = [
        ("IC-04-AUTH-ERRORS", "scripts/check_local_auth_errors.py", []),
        ("IC-04-REGISTRATION", "scripts/check_enterprise_registration.py", []),
        ("IC-04-REGISTRATION-BROWSER", "scripts/check_enterprise_registration_browser.py", []),
        ("IC-04-CONTEXT-BROWSER", "scripts/check_enterprise_context_browser.py", []),
        ("IC-04-ROLES", "scripts/check_enterprise_roles.py", []),
        ("IC-04-ROLE-BROWSER", "scripts/check_enterprise_role_browser.py", []),
        ("IC-04-PROJECTS", "scripts/check_projects.py", []),
        ("IC-05-OPERATIONS", "scripts/check_operations.py", []),
        ("IC-05-OBJECT-STORAGE", "scripts/check_object_storage.py", []),
        ("IC-05-ARTIFACT-REGISTRY", "scripts/check_artifact_registry.py", []),
        ("IC-07-BUDGETS", "scripts/check_budgets.py", []),
        ("IC-06-SESSION-BROWSER", "scripts/check_medical_session_browser.py", []),
        ("IC-06-ACCOUNT-BROWSER", "scripts/check_medical_account_browser.py", []),
        ("IC-11-APPROVAL-BROWSER", "scripts/check_template_approval_browser.py", []),
        ("IC-04-LOCAL-HTTP", "scripts/check_local_identity.py", []),
        ("IC-04-LOCAL-BROWSER", "scripts/check_local_browser.py", []),
        ("IC-04-RUOYI", "scripts/check_ruoyi_accounts.py", []),
        ("IC-11-STORAGE", "scripts/check_template_storage.py", []),
        ("IC-11-HTTP", "scripts/check_template_runtime.py", []),
        ("IC-04-MEMBER-BINDING", "scripts/check_member_binding.py", []),
        ("IC-04-MEMBER-IDENTITY", "scripts/check_member_identity.py", []),
        ("IC-04-MEMBER-INVITATION", "scripts/check_member_invitation.py", []),
        ("IC-04-PERMISSION", "scripts/check_permission_decision.py", []),
        ("IC-04-INVITATION-FLOW", "scripts/check_member_invitation_flow.py", []),
        ("IC-01-WEB-COMPAT", "scripts/check_web.py", []),
        ("IC-03-CROSS", "scripts/check_migration.py", ["--check", "cross"]),
        ("IC-03-SHARED-LANGUAGES", "scripts/check_shared_contracts.py", []),
        ("IC-03-MIGRATION-CLI", "scripts/check_migration.py", ["--check", "entrypoint"]),
        ("IC-03-MIGRATION-COVERAGE", "scripts/check_migration.py", ["--check", "coverage"]),
        ("IC-02-LOCAL", "scripts/check_inputs.py", []),
    ]
    receipts = []
    for check_id, script, arguments in steps:
        if check_id == "IC-01-WEB-COMPAT":
            executable = str(ROOT / ".implementation-work/web-check-venv/bin/python")
        elif check_id == "IC-05-OBJECT-STORAGE":
            executable = object_python
        else:
            executable = sys.executable
        command = [executable, script, *arguments, "--execution", args.execution]
        print("RUN " + check_id, flush=True)
        subprocess.run(command, cwd=ROOT, env=environment, check=True)
        receipts.append(ROOT / ".implementation-work" / (check_id + "-" + args.execution) / "receipt.json")
    subprocess.run([sys.executable, "scripts/check_full_function_inventory.py"], cwd=ROOT, env=environment, check=True)
    if verify_application_deployment() != before:
        raise ValueError("完整回归期间运行程序发生变化")
    object_after = json.loads(subprocess.run(object_command, cwd=ROOT, capture_output=True,
        text=True, check=True, timeout=30).stdout)
    if object_after != object_before:
        raise ValueError("完整回归期间对象服务发生变化")
    for item in inputs:
        if hashlib.sha256(Path(item["path"]).read_bytes()).hexdigest() != item["sha256"]:
            raise ValueError("完整回归期间检查程序发生变化：" + item["path"])
    records = []
    for filename in receipts:
        receipt = json.loads(filename.read_bytes())
        if receipt["exit_code"] != 0 or not receipt["inputs_unchanged"]:
            raise ValueError("运行检查未通过：" + receipt["check_id"])
        for item in [*receipt["inputs"], *receipt["artifacts"]]:
            if hashlib.sha256(Path(item["path"]).read_bytes()).hexdigest() != item["sha256"]:
                raise ValueError("运行检查证据与当前文件不一致：" + item["path"])
        records.append({"check_id": receipt["check_id"], "kind": receipt["kind"],
                        "receipt": str(filename.relative_to(ROOT)), "sha256": hashlib.sha256(filename.read_bytes()).hexdigest(),
                        "started_at": receipt["started_at"], "ended_at": receipt["ended_at"],
                        "command": receipt["command"], "exit_code": receipt["exit_code"], "current_evidence": True})
    result = {"scope": "REGISTERED_LOCAL_REGRESSION", "started_at": started,
              "ended_at": datetime.datetime.now(datetime.timezone.utc).isoformat(),
              "deployment": before, "object_storage_deployment": object_before,
              "inputs": inputs, "checks": records, "business_acceptance": "NOT_RUN",
              "external_integration": "BLOCKED", "ai_evals": "BLOCKED", "visual": "NOT_RUN"}
    (output / "evidence.json").write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"result": "PASS", "scope": result["scope"], "check_suites": len(records),
                      "evidence": str(output.relative_to(ROOT) / "evidence.json")}))


if __name__ == "__main__":
    main()
