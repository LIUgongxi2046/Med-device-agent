import json
from pathlib import Path
from uuid import UUID

import local_runtime

ROOT = Path(__file__).resolve().parents[1]


def main():
    seed = json.loads((ROOT / ".implementation-work/runtime/project-directory-workspace-input.json").read_bytes())
    tenant, actor = seed["tenant_id"], seed["actor"]
    if any(str(UUID(value)) != value for value in (tenant, actor)):
        raise ValueError("工作区身份需要标准UUID")
    query = """
        SELECT COALESCE(JSON_ARRAYAGG(JSON_OBJECT(
          'release_id',r.release_id,'agent_id',a.agent_id,'name',a.name,'status',a.status,
          'project_id',r.project_id,'current',h.current_version=a.agent_version_id,
          'formal_candidate',EXISTS(SELECT 1 FROM medical_local.formal_agent_candidate_binding c WHERE c.tenant_id=a.tenant_id AND c.version_id=a.agent_version_id),
          'audience_mode',JSON_UNQUOTE(JSON_EXTRACT(r.manifest,'$.release_scope.audience_mode')),
          'member_in_audience',JSON_CONTAINS(r.manifest,JSON_QUOTE('""" + actor + """'),'$.release_scope.member_ids'))),JSON_ARRAY())
        FROM medical_local.release_record r
        JOIN medical_local.agent_version a ON a.tenant_id=r.tenant_id AND a.agent_version_id=r.agent_version_id
        JOIN medical_local.configuration_head h ON h.tenant_id=a.tenant_id AND h.config_id=a.agent_id AND h.project_id<=>a.project_id AND h.kind='AGENT'
        WHERE r.tenant_id='""" + tenant + "'"
    rows = json.loads(local_runtime.mysql(query, local_runtime.settings()["root_password"]).stdout)
    print(json.dumps(rows, ensure_ascii=False))
    (ROOT / ".implementation-work/task-release-directory-inventory.json").write_text(json.dumps(rows, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
