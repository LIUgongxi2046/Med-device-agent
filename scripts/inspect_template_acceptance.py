import json
import uuid

import local_runtime as runtime


def main():
    ledger = json.loads((runtime.RUNTIME / "acceptance-template-workflow.json").read_bytes())
    tenant = str(uuid.UUID(ledger["tenant_id"]))
    rule = str(uuid.UUID(ledger["rule_version"]))
    password = runtime.settings()["root_password"]
    queries = [
        "SELECT a.artifact_id,a.artifact_kind,a.project_id,a.schema_manifest_sha256,b.schema_ref,s.access_set_id,m.access_set_id,v.entity_kind FROM medical_local.artifact a LEFT JOIN medical_local.artifact_content_binding b ON b.tenant_id=a.tenant_id AND b.artifact_id=a.artifact_id LEFT JOIN medical_local.dependency_access_set s ON s.tenant_id=a.tenant_id AND s.access_set_id=a.dependency_access_set_id LEFT JOIN medical_local.dependency_access_manifest m ON m.tenant_id=s.tenant_id AND m.project_key=s.project_key AND m.access_set_id=s.access_set_id AND m.manifest_sha256=s.manifest_sha256 AND m.content_sha256=s.content_sha256 LEFT JOIN medical_local.version_registry v ON v.tenant_id=a.tenant_id AND v.version_id=a.artifact_id WHERE a.tenant_id='" + tenant + "' AND a.artifact_kind LIKE 'ACCEPTANCE_%'",
        "SELECT action,subject_id,resource_kind,resource_id,status,`condition` FROM medical_local.permission_grant WHERE tenant_id='" + tenant + "' AND action IN ('template.write','template.read','artifact.read','rule.read','dependency.read')",
        "WITH RECURSIVE versions AS (SELECT version_id,entity_kind,content_sha256 FROM medical_local.version_registry WHERE tenant_id='" + tenant + "' AND version_id='" + rule + "' UNION DISTINCT SELECT v.version_id,v.entity_kind,v.content_sha256 FROM versions p JOIN medical_local.lineage_edge e ON e.consumer_version_id=p.version_id AND e.tenant_id='" + tenant + "' JOIN medical_local.version_registry v ON v.version_id=e.upstream_version_id AND v.tenant_id=e.tenant_id) SELECT * FROM versions",
        "SELECT rule_version,status,classification FROM medical_local.rule_version WHERE tenant_id='" + tenant + "' AND rule_version='" + rule + "'",
        "SELECT action,result,resource_ref FROM medical_local.audit_event WHERE tenant_id='" + tenant + "' AND action='rule.approval_decided' ORDER BY created_at DESC LIMIT 1",
    ]
    if "template_pending" in ledger:
        key = str(uuid.UUID(ledger["template_pending"]["key"]))
        queries.append("SELECT operation_id,operation_name,state,http_status,target_id FROM medical_local.operation_record WHERE tenant_id='" + tenant + "' AND idempotency_key='" + key + "'")
    for query in queries:
        print(runtime.mysql(query, password).stdout)


if __name__ == "__main__":
    main()
