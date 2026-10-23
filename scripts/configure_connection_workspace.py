import json
import uuid

import local_runtime as runtime


def main():
    runtime.verify_application_deployment()
    workspace = json.loads((runtime.RUNTIME / "acceptance-workspace-seed.json").read_bytes())
    owner = json.loads((runtime.RUNTIME / "owner-credentials.json").read_bytes())
    if workspace["data_kind"] != "SIMULATION" or workspace["state"] != "CREATED" or owner["username"] != "liuhaonan":
        raise ValueError("LOCAL_CONNECTION_WORKSPACE_REQUIRED")
    tenant = str(uuid.UUID(workspace["tenant_id"]))
    password = runtime.settings()["root_password"]
    candidates = json.loads(runtime.mysql("SELECT JSON_ARRAYAGG(JSON_OBJECT('user_id',g.subject_id,'basis_version',g.basis_version,'manifest',g.schema_manifest_sha256)) FROM medical_local.permission_grant g JOIN medical_local.platform_local_account a ON a.user_id=g.subject_id AND a.username='liuhaonan' AND a.platform_role='PLATFORM_ADMIN' AND a.status='ACTIVE' JOIN medical_local.tenant_member m ON m.tenant_id=g.tenant_id AND m.user_id=g.subject_id AND m.status='ACTIVE' WHERE g.tenant_id='" + tenant + "' AND g.action='project.create' AND g.resource_kind='ENTERPRISE' AND g.resource_id=g.tenant_id AND g.status='ACTIVE' AND g.valid_from<=UTC_TIMESTAMP(6) AND (g.valid_to IS NULL OR g.valid_to>UTC_TIMESTAMP(6))", password).stdout)
    if not candidates:
        raise ValueError("LOCAL_ADMINISTRATOR_WORKSPACE_REQUIRED")
    source = candidates[0]
    actor = str(uuid.UUID(source["user_id"]))
    basis = str(uuid.UUID(source["basis_version"]))
    manifest = source["manifest"]
    if len(manifest) != 64 or any(character not in "0123456789abcdef" for character in manifest):
        raise ValueError("LOCAL_PERMISSION_SCHEMA_REQUIRED")
    condition = json.dumps({"schema_version": "1.0.0", "identity_modes": ["local"], "purposes": ["CUSTOMER_CONNECTOR"], "delegation": {"actions": [], "role_ids": [], "resource_kinds": [], "project_ids": [], "resource_ids": []}}, separators=(",", ":"))
    actions = ("connector.read", "connector.configure", "connector.approve", "connector.sync")
    statements = ["START TRANSACTION", "SET @connection_workspace_grants_changed=0"]
    for action in actions:
        grant = str(uuid.uuid4())
        request = str(uuid.uuid4())
        audit = str(uuid.uuid4())
        event = str(uuid.uuid4())
        statements.append("INSERT INTO medical_local.permission_grant(tenant_id,grant_id,subject_id,resource_kind,resource_id,action,`condition`,status,valid_from,basis_version,schema_version,schema_manifest_sha256,created_by,created_at,classification,revision,updated_at) SELECT '" + tenant + "','" + grant + "','" + actor + "','ENTERPRISE','" + tenant + "','" + action + "','" + condition + "','ACTIVE',UTC_TIMESTAMP(6),'" + basis + "','1.0.0','" + manifest + "','" + actor + "',UTC_TIMESTAMP(6),'LOCAL_AUTH_METADATA',1,UTC_TIMESTAMP(6) WHERE NOT EXISTS(SELECT 1 FROM medical_local.permission_grant WHERE tenant_id='" + tenant + "' AND subject_id='" + actor + "' AND resource_kind='ENTERPRISE' AND resource_id='" + tenant + "' AND action='" + action + "' AND status='ACTIVE')")
        statements.append("SET @connection_workspace_grants_changed=@connection_workspace_grants_changed+ROW_COUNT()")
        payload = "JSON_OBJECT('schema_version','1.0.0','scope','LOCAL_ACCEPTANCE_WORKSPACE','grant_id',g.grant_id,'subject_id',g.subject_id,'tenant_id',g.tenant_id,'action',g.action,'basis_version',g.basis_version,'reason','根据用户要求，为本地工作组织中的超管登记完整连接管理权限')"
        statements.append("INSERT INTO medical_local.audit_event(tenant_id,project_id,audit_id,subject_id,action,resource_ref,result,reason_code,request_id,trace_id,schema_version,schema_manifest_sha256,created_by,created_at,classification) SELECT g.tenant_id,NULL,'" + audit + "',g.subject_id,'workspace.administrator.granted'," + payload + ",'SUCCESS',NULL,'" + request + "','" + request + "','1.0.0',g.schema_manifest_sha256,g.subject_id,UTC_TIMESTAMP(6),'LOCAL_AUTH_METADATA' FROM medical_local.permission_grant g WHERE g.tenant_id='" + tenant + "' AND g.grant_id='" + grant + "'")
        statements.append("INSERT INTO medical_local.outbox_event(tenant_id,event_id,aggregate_id,aggregate_revision,event_type,payload,occurred_at,published_at) SELECT g.tenant_id,'" + event + "',g.grant_id,1,'workspace.administrator.granted'," + payload + ",UTC_TIMESTAMP(6),NULL FROM medical_local.permission_grant g WHERE g.tenant_id='" + tenant + "' AND g.grant_id='" + grant + "'")
    statements.append("UPDATE medical_local.tenant SET auth_epoch=auth_epoch+1 WHERE tenant_id='" + tenant + "' AND @connection_workspace_grants_changed>0")
    statements.append("SELECT @connection_workspace_grants_changed")
    statements.append("COMMIT")
    result = runtime.mysql(";".join(statements), password)
    changed = int(result.stdout.strip())
    runtime.save_private_json(runtime.RUNTIME / "connection-workspace-authorization.json", {"tenant_id": tenant, "subject_id": actor, "scope": "LOCAL_ACCEPTANCE_WORKSPACE", "actions": actions, "grants_created": changed, "audit_recorded": True, "customer_connection_created": False}, replace=True)
    print(json.dumps({"state": "AUTHORIZED", "grants_created": changed, "audit_recorded": True}))


if __name__ == "__main__":
    main()
