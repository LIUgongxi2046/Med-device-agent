import hashlib
import json
from datetime import datetime, timezone
from pathlib import Path
from uuid import UUID

import local_runtime

ROOT = Path(__file__).resolve().parents[1]
RUNTIME = ROOT / ".implementation-work/runtime"


def main():
    local_runtime.verify_application_deployment()
    seed = json.loads((RUNTIME / "acceptance-workspace-seed.json").read_bytes())
    if seed["data_kind"] != "SIMULATION" or seed["scope"] != "LOCAL_ACCEPTANCE_WORKSPACE" or seed["state"] != "CREATED":
        raise ValueError("本地资料登记需要已核查的本地工作区身份")
    tenant, version = seed["tenant_id"], seed["source_version"]
    if any(str(UUID(value)) != value for value in (tenant, version)):
        raise ValueError("资料登记身份无效")
    password = local_runtime.settings()["root_password"]
    condition = "sv.tenant_id='" + tenant + "' AND sv.source_version='" + version + "'"
    query = """
        SELECT JSON_OBJECT('tenant_id',sv.tenant_id,'source_version',sv.source_version,
          'source_id',sv.source_id,'original_sha256',sv.original_sha256,
          'registry_sha256',vr.content_sha256,'data_kind',JSON_UNQUOTE(JSON_EXTRACT(sv.provenance,'$.data_kind')),
          'ownership',s.ownership,'created_by',sv.created_by,'classification',s.classification,
          'gate_count',(SELECT COUNT(*) FROM medical_local.source_use_gate g WHERE g.tenant_id=sv.tenant_id AND g.source_version=sv.source_version),
          'active_gate_count',(SELECT COUNT(*) FROM medical_local.source_use_gate g WHERE g.tenant_id=sv.tenant_id AND g.source_version=sv.source_version AND g.lifecycle='ACTIVE'),
          'lifecycle_count',(SELECT COUNT(*) FROM medical_local.source_lifecycle_state ls WHERE ls.tenant_id=sv.tenant_id AND ls.source_version=sv.source_version))
        FROM medical_local.source_version sv
        JOIN medical_local.source s ON s.tenant_id=sv.tenant_id AND s.source_id=sv.source_id
        JOIN medical_local.version_registry vr ON vr.tenant_id=sv.tenant_id AND vr.version_id=sv.source_version
        WHERE """ + condition
    before = json.loads(local_runtime.mysql(query, password).stdout)
    if before["data_kind"] != "SIMULATION" or before["ownership"] != "ENTERPRISE" or before["registry_sha256"] != before["original_sha256"]:
        raise ValueError("实际资料原件登记及本地归属需要完整一致")
    if before["gate_count"] == 0 or before["gate_count"] != before["active_gate_count"]:
        raise ValueError("资料当前状态不能登记为初始状态")
    receipt_path = RUNTIME / "source-lifecycle-registration.json"
    if before["lifecycle_count"] != 0:
        previous = json.loads(receipt_path.read_bytes())
        if previous["state"] != "REGISTERED" or previous["source_version"] != version or previous["after"] != before:
            raise ValueError("现有资料生命周期需要按照原登记核查")
        print(json.dumps({"state": "REGISTERED", "source_version": version, "existing_registration_verified": True}))
        return
    receipt = {"state": "PREPARED", "scope": "LOCAL_ACCEPTANCE_SOURCE_REGISTRATION", "source_version": version,
        "before": before, "prepared_at": datetime.now(timezone.utc).isoformat()}
    local_runtime.save_private_json(receipt_path, receipt)
    inserted = local_runtime.mysql("""
        INSERT INTO medical_local.source_lifecycle_state
          (tenant_id,source_version,status,last_decision_id,revision,updated_at,created_by,created_at,classification)
        SELECT sv.tenant_id,sv.source_version,'ACTIVE',NULL,1,sv.created_at,sv.created_by,sv.created_at,s.classification
        FROM medical_local.source_version sv
        JOIN medical_local.source s ON s.tenant_id=sv.tenant_id AND s.source_id=sv.source_id
        JOIN medical_local.version_registry vr ON vr.tenant_id=sv.tenant_id AND vr.version_id=sv.source_version
        WHERE """ + condition + """
          AND JSON_UNQUOTE(JSON_EXTRACT(sv.provenance,'$.data_kind'))='SIMULATION'
          AND s.ownership='ENTERPRISE' AND vr.content_sha256=sv.original_sha256
          AND EXISTS (SELECT 1 FROM medical_local.source_use_gate g WHERE g.tenant_id=sv.tenant_id AND g.source_version=sv.source_version AND g.lifecycle='ACTIVE')
          AND NOT EXISTS (SELECT 1 FROM medical_local.source_use_gate g WHERE g.tenant_id=sv.tenant_id AND g.source_version=sv.source_version AND g.lifecycle<>'ACTIVE');
        SELECT ROW_COUNT();""", password)
    if inserted.stdout.strip() != "1":
        raise RuntimeError("资料初始生命周期登记数量无效")
    after = json.loads(local_runtime.mysql(query, password).stdout)
    expected = {**before, "lifecycle_count": 1}
    if after != expected:
        raise RuntimeError("资料登记后的原件与状态核查未通过")
    receipt.update({"state": "REGISTERED", "after": after, "verified_at": datetime.now(timezone.utc).isoformat(),
        "original_sha256": before["original_sha256"], "script_sha256": hashlib.sha256(Path(__file__).read_bytes()).hexdigest()})
    local_runtime.save_private_json(receipt_path, receipt, replace=True)
    print(json.dumps({"state": "REGISTERED", "source_version": version, "original_sha256": before["original_sha256"]}))


if __name__ == "__main__":
    main()
