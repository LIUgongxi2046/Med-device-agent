import json
from pathlib import Path
import uuid
import subprocess

import yaml

import local_runtime as runtime


ROOT = Path(__file__).resolve().parents[1]


def main():
    runtime.verify_application_deployment()
    ledger = json.loads((runtime.RUNTIME / "acceptance-workspace-seed.json").read_bytes())
    if ledger["data_kind"] != "SIMULATION" or ledger["state"] != "CREATED":
        raise ValueError("ACCEPTANCE_WORKSPACE_REQUIRED")
    tenant = str(uuid.UUID(ledger["tenant_id"]))
    password = runtime.settings()["root_password"]
    task_manifest = json.loads(subprocess.run(["node", str(ROOT / "web/scripts/acceptance-task-manifest.mjs")], cwd=ROOT, capture_output=True, text=True, check=True).stdout)
    configuration = yaml.safe_load((ROOT / "business/ruoyi-admin/src/main/resources/application-medical-local.yml").read_text())
    classification = configuration["medical"]["identity"]["directory"]["classification"]
    if classification != "LOCAL_AUTH_METADATA":
        raise ValueError("ACCEPTANCE_METADATA_CONFIGURATION_CHANGED")
    statements = ["START TRANSACTION"]
    task = str(uuid.UUID(ledger["task_id"]))
    document = str(uuid.UUID(ledger["document_id"]))
    statements.append("UPDATE medical_local.plan_content_revision SET schema_manifest_sha256='" + task_manifest["sha256"] + "' WHERE tenant_id='" + tenant + "' AND task_id='" + task + "' AND reason='SIMULATION 开发计划'")
    statements.append("UPDATE medical_local.version_registry v JOIN medical_local.document_revision_meta m ON m.tenant_id=v.tenant_id AND m.body_schema_revision=v.version_id SET v.owner_project_id=m.project_id WHERE v.tenant_id='" + tenant + "' AND m.document_id='" + document + "' AND v.entity_kind='DOCUMENT_BODY_SCHEMA' AND v.owner_project_id IS NULL")
    names = ("template_version", "template_definition", "requirement_node", "field_definition", "repeat_definition", "template_attachment_requirement", "template_definition_dependency", "budget_account", "budget_revision")
    for name in names:
        classifications = "('SIMULATION','INTERNAL')" if name in ("budget_account", "budget_revision") else "('SIMULATION')"
        statements.append("UPDATE medical_local.`" + name + "` SET classification='LOCAL_AUTH_METADATA' WHERE tenant_id='" + tenant + "' AND classification IN " + classifications)
    statements.append("COMMIT")
    runtime.mysql(";".join(statements), password)
    condition = {"schema_version": "1.0.0", "identity_modes": ["local"], "purposes": None, "delegation": {"actions": [], "role_ids": [], "resource_kinds": ["MEMBER"], "project_ids": None, "resource_ids": None}}
    encoded = json.dumps(condition, separators=(",", ":"))
    runtime.mysql("UPDATE medical_local.permission_grant SET `condition`='" + encoded + "',revision=revision+1,updated_at=UTC_TIMESTAMP(6) WHERE tenant_id='" + tenant + "' AND action='member.manage' AND resource_kind='ENTERPRISE' AND resource_id='" + tenant + "' AND JSON_CONTAINS_PATH(`condition`,'one','$.delegation.resource_kinds') AND JSON_LENGTH(JSON_EXTRACT(`condition`,'$.delegation.resource_kinds'))=0", password)
    actions = ("agent.configure", "agent.publish", "capability.configure", "retention.read", "retention.approve", "evaluation.create", "evaluation.score", "evaluation.finalize", "document.check", "audit.read", "report.read", "report.export", "feedback.create", "feedback.read", "feedback.manage")
    condition["delegation"]["resource_kinds"] = []
    encoded = json.dumps(condition, separators=(",", ":"))
    grants = ["START TRANSACTION", "SET @acceptance_grants_changed=0"]
    for action in actions:
        grants.append("INSERT INTO medical_local.permission_grant(tenant_id,grant_id,subject_id,resource_kind,resource_id,action,`condition`,status,valid_from,basis_version,schema_version,schema_manifest_sha256,created_by,created_at,classification,revision,updated_at) SELECT original.tenant_id,'" + str(uuid.uuid4()) + "',original.subject_id,'ENTERPRISE',original.tenant_id,'" + action + "','" + encoded + "','ACTIVE',UTC_TIMESTAMP(6),original.basis_version,original.schema_version,original.schema_manifest_sha256,original.created_by,UTC_TIMESTAMP(6),'LOCAL_AUTH_METADATA',1,UTC_TIMESTAMP(6) FROM medical_local.permission_grant original JOIN medical_local.platform_local_account administrator ON administrator.user_id=original.subject_id AND administrator.platform_role='PLATFORM_ADMIN' AND administrator.status='ACTIVE' WHERE original.tenant_id='" + tenant + "' AND original.action='project.create' AND original.status='ACTIVE' AND NOT EXISTS(SELECT 1 FROM medical_local.permission_grant present WHERE present.tenant_id=original.tenant_id AND present.subject_id=original.subject_id AND present.action='" + action + "' AND present.status='ACTIVE') LIMIT 1")
        grants.append("SET @acceptance_grants_changed=@acceptance_grants_changed+ROW_COUNT()")
    grants.append("UPDATE medical_local.tenant SET auth_epoch=auth_epoch+1 WHERE tenant_id='" + tenant + "' AND @acceptance_grants_changed>0")
    grants.append("COMMIT")
    runtime.mysql(";".join(grants), password)
    runtime.save_private_json(runtime.RUNTIME / "acceptance-metadata.json", {"data_kind": "SIMULATION", "tenant_id": tenant, "metadata_classification": classification, "domain_source_classifications_preserved": True, "tables": names}, replace=True)
    print("ACCEPTANCE_METADATA_VALIDATED")


if __name__ == "__main__":
    main()
