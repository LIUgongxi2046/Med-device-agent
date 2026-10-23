import json
import uuid

import local_runtime as runtime


def main():
    runtime.verify_application_deployment()
    workspace = json.loads((runtime.RUNTIME / "acceptance-workspace-seed.json").read_bytes())
    credentials = json.loads((runtime.RUNTIME / "acceptance-member-credentials.json").read_bytes())
    if workspace["data_kind"] != "SIMULATION" or workspace["state"] != "CREATED":
        raise ValueError("LOCAL_TEMPLATE_WORKSPACE_REQUIRED")
    tenant = str(uuid.UUID(workspace["tenant_id"]))
    member = str(uuid.UUID(workspace["member_user_id"]))
    password = runtime.settings()["root_password"]
    members = json.loads(runtime.mysql("SELECT JSON_ARRAYAGG(JSON_OBJECT('username',a.username,'role',a.platform_role)) FROM medical_local.platform_local_account a JOIN medical_local.tenant_member m ON m.user_id=a.user_id AND m.tenant_id='" + tenant + "' AND m.status='ACTIVE' AND m.valid_from<=UTC_TIMESTAMP(6) AND (m.valid_to IS NULL OR m.valid_to>UTC_TIMESTAMP(6)) WHERE a.user_id='" + member + "' AND a.status='ACTIVE'", password).stdout)
    if not members or len(members) != 1 or members[0] != {"username": credentials["username"], "role": "USER"}:
        raise ValueError("LOCAL_TEMPLATE_EDITOR_MEMBERSHIP_REQUIRED")
    owners = json.loads(runtime.mysql("SELECT JSON_ARRAYAGG(JSON_OBJECT('actor',g.subject_id,'basis',g.basis_version,'manifest',g.schema_manifest_sha256)) FROM medical_local.permission_grant g JOIN medical_local.platform_local_account a ON a.user_id=g.subject_id AND a.username='liuhaonan' AND a.platform_role='PLATFORM_ADMIN' AND a.status='ACTIVE' WHERE g.tenant_id='" + tenant + "' AND g.action='project.create' AND g.resource_kind='ENTERPRISE' AND g.resource_id=g.tenant_id AND g.status='ACTIVE' AND g.valid_from<=UTC_TIMESTAMP(6) AND (g.valid_to IS NULL OR g.valid_to>UTC_TIMESTAMP(6))", password).stdout)
    if not owners or len({value["actor"] for value in owners}) != 1:
        raise ValueError("LOCAL_TEMPLATE_OWNER_REQUIRED")
    owner = owners[0]
    actor = str(uuid.UUID(owner["actor"]))
    basis = str(uuid.UUID(owner["basis"]))
    manifest = owner["manifest"]
    if len(manifest) != 64 or any(value not in "0123456789abcdef" for value in manifest):
        raise ValueError("LOCAL_TEMPLATE_PERMISSION_SCHEMA_REQUIRED")
    actions = ("template.read", "template.write", "source.read", "rule.read", "artifact.read")
    condition = json.dumps({"schema_version": "1.0.0", "identity_modes": ["local"], "purposes": ["TEMPLATE_MAINTENANCE"], "delegation": {"actions": [], "role_ids": [], "resource_kinds": [], "project_ids": [], "resource_ids": []}}, separators=(",", ":"))
    statements = ["START TRANSACTION", "SET @template_editor_grants_changed=0"]
    for action in actions:
        grant, request, audit, event = (str(uuid.uuid4()) for _ in range(4))
        statements.append("INSERT INTO medical_local.permission_grant(tenant_id,grant_id,subject_id,resource_kind,resource_id,action,`condition`,status,valid_from,basis_version,schema_version,schema_manifest_sha256,created_by,created_at,classification,revision,updated_at) SELECT '" + tenant + "','" + grant + "','" + member + "','ENTERPRISE','" + tenant + "','" + action + "','" + condition + "','ACTIVE',UTC_TIMESTAMP(6),'" + basis + "','1.0.0','" + manifest + "','" + actor + "',UTC_TIMESTAMP(6),'LOCAL_AUTH_METADATA',1,UTC_TIMESTAMP(6) WHERE NOT EXISTS(SELECT 1 FROM medical_local.permission_grant g WHERE g.tenant_id='" + tenant + "' AND g.subject_id='" + member + "' AND g.resource_kind='ENTERPRISE' AND g.resource_id='" + tenant + "' AND g.action='" + action + "' AND g.status='ACTIVE' AND (JSON_EXTRACT(g.condition,'$.purposes')=CAST('null' AS JSON) OR JSON_CONTAINS(g.condition,JSON_QUOTE('TEMPLATE_MAINTENANCE'),'$.purposes')))")
        statements.append("SET @template_editor_grants_changed=@template_editor_grants_changed+ROW_COUNT()")
        payload = "JSON_OBJECT('schema_version','1.0.0','scope','LOCAL_ACCEPTANCE_WORKSPACE','role','TEMPLATE_EDITOR_WITHOUT_APPROVAL','subject_id',g.subject_id,'grant_id',g.grant_id,'action',g.action,'basis_version',g.basis_version,'reason','根据用户的完整功能测试资料要求，为本地普通测试账户登记模板读取和修订权限，核准权限保持独立')"
        statements.append("INSERT INTO medical_local.audit_event(tenant_id,project_id,audit_id,subject_id,action,resource_ref,result,request_id,trace_id,schema_version,schema_manifest_sha256,created_by,created_at,classification) SELECT g.tenant_id,NULL,'" + audit + "','" + actor + "','workspace.test_role.granted'," + payload + ",'SUCCESS','" + request + "','" + request + "','1.0.0',g.schema_manifest_sha256,'" + actor + "',UTC_TIMESTAMP(6),'LOCAL_AUTH_METADATA' FROM medical_local.permission_grant g WHERE g.tenant_id='" + tenant + "' AND g.grant_id='" + grant + "'")
        statements.append("INSERT INTO medical_local.outbox_event(tenant_id,event_id,aggregate_id,aggregate_revision,event_type,payload,occurred_at,published_at) SELECT g.tenant_id,'" + event + "',g.grant_id,1,'workspace.test_role.granted'," + payload + ",UTC_TIMESTAMP(6),NULL FROM medical_local.permission_grant g WHERE g.tenant_id='" + tenant + "' AND g.grant_id='" + grant + "'")
    statements.append("UPDATE medical_local.tenant SET auth_epoch=auth_epoch+1 WHERE tenant_id='" + tenant + "' AND @template_editor_grants_changed>0")
    statements.append("SELECT @template_editor_grants_changed")
    statements.append("COMMIT")
    changed = int(runtime.mysql(";".join(statements), password).stdout.strip())
    runtime.save_private_json(runtime.RUNTIME / "template-editor-workspace-authorization.json", {"tenant_id": tenant, "subject_id": member, "role": "TEMPLATE_EDITOR_WITHOUT_APPROVAL", "actions": actions, "purpose": "TEMPLATE_MAINTENANCE", "grants_created": changed, "audit_recorded": True, "template_approval_granted": False}, replace=True)
    print(json.dumps({"state": "AUTHORIZED", "role": "TEMPLATE_EDITOR_WITHOUT_APPROVAL", "grants_created": changed, "audit_recorded": True}))


if __name__ == "__main__":
    main()
