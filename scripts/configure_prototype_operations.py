import argparse
import hashlib
import json
from pathlib import Path
import uuid

import yaml

import local_runtime as runtime
from configure_local_discovery import private_metadata


ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-profile", required=True)
    arguments = parser.parse_args()
    source = (ROOT / arguments.source_profile).resolve(strict=True)
    source.relative_to(runtime.RUNTIME / "application-profiles")
    private_metadata(source)
    profile = yaml.safe_load(source.read_text())
    workflow = json.loads((runtime.RUNTIME / "acceptance-template-workflow.json").read_bytes())
    if workflow["data_kind"] != "SIMULATION" or workflow["regulatory_approval"] is not False:
        raise ValueError("PUBLIC_RULE_LOCAL_SCOPE_REQUIRED")
    tenant = str(uuid.UUID(workflow["tenant_id"]))
    rule = str(uuid.UUID(workflow["rule_version"]))
    data = runtime.mysql("SELECT JSON_OBJECT('hash',content_sha256,'classification',classification) FROM medical_local.rule_version WHERE tenant_id='" + tenant + "' AND rule_version='" + rule + "'", runtime.settings()["root_password"]).stdout
    current = json.loads(data)
    if current["hash"] != workflow["rule_sha256"]:
        raise ValueError("PUBLIC_RULE_CONTENT_CHANGED")
    binding = {"tenant-id": tenant, "kind": "RULE", "version-id": rule, "content-sha256": current["hash"], "metadata-classification": current["classification"], "classification": "INTERNAL", "reason": "用户授权根据公开资料拟定的本地编号字段规则；仅含字段存在性表达式及公开字体许可与编辑说明，按内部测试资料管理。"}
    policy = profile["medical"].setdefault("business-operations", {})
    bindings = policy.setdefault("classification-bindings", [])
    if any(item["version-id"] == rule and item["tenant-id"] == tenant for item in bindings):
        if [item for item in bindings if item["version-id"] == rule and item["tenant-id"] == tenant] != [binding]:
            raise ValueError("PUBLIC_RULE_CLASSIFICATION_CHANGED")
    else:
        bindings.append(binding)
    directory = runtime.RUNTIME / "application-profiles" / str(uuid.uuid4())
    directory.mkdir(mode=0o700)
    target = directory / "application-workspace.yaml"
    target.write_text(yaml.safe_dump(profile, allow_unicode=True, sort_keys=False), encoding="utf-8")
    target.chmod(0o600)
    receipt = {"profile": str(target.relative_to(ROOT)), "sha256": hashlib.sha256(target.read_bytes()).hexdigest(), "classification_bindings": bindings, "regulatory_approval": False}
    runtime.save_private_json(runtime.RUNTIME / "prototype-operations-profile.json", receipt, replace=True)
    print(json.dumps({"profile": receipt["profile"], "sha256": receipt["sha256"]}))


if __name__ == "__main__":
    main()
