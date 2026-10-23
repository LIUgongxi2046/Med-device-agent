import json
from datetime import datetime, timezone
from pathlib import Path

import local_runtime


ROOT = Path(__file__).resolve().parents[1]


def main():
    password = local_runtime.settings()["root_password"]
    table_rows = json.loads(local_runtime.mysql("SELECT JSON_ARRAYAGG(TABLE_NAME) FROM information_schema.TABLES WHERE TABLE_SCHEMA='medical_local' AND TABLE_TYPE='BASE TABLE'", password).stdout)
    names = {"tenant", "tenant_member", "project_record", "project", "task_head", "source_version", "source", "source_upload_request", "source_folder", "template_version", "template_approval_decision", "rule_version", "rule_approval_decision", "model_config_revision", "simulation_model_config_revision", "agent_version", "agent_draft", "release_record", "budget_revision", "document", "document_revision", "document_review_intent", "audit_event", "business_audit_cursor", "evaluation_job", "evaluation_result", "feedback_record", "operations_feedback", "formal_agent_acceptance", "connector_binding", "source_collection", "collection_revision", "execution_policy", "agent_execution_policy", "runtime_retention_policy", "sys_user", "sys_role", "sys_dept", "sys_post", "sys_notice", "sys_job", "sys_config", "platform_account_workspace"}
    counts = {}
    migrations = int(local_runtime.mysql("SELECT COUNT(*) FROM medical_local.DATABASECHANGELOG", password).stdout.strip())
    for name in sorted(names.intersection(table_rows)):
        counts[name] = int(local_runtime.mysql("SELECT COUNT(*) FROM medical_local.`" + name + "`", password).stdout.strip())
    memberships = json.loads(local_runtime.mysql("SELECT JSON_ARRAYAGG(JSON_OBJECT('user_id',r.user_id,'tenant_id',r.tenant_id,'member_id',r.member_id,'status',m.status)) FROM medical_local.platform_member_route r JOIN medical_local.tenant_member m ON m.tenant_id=r.tenant_id AND m.member_id=r.member_id", password).stdout)
    result = {"verified_at": datetime.now(timezone.utc).isoformat(), "database": "medical_local", "migration_count": migrations, "table_count": len(table_rows), "counts": counts, "memberships": memberships}
    target = ROOT / ".implementation-work/acceptance-workspace-inventory.json"
    target.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({"migration_count": migrations, "table_count": len(table_rows), "counts": counts}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
