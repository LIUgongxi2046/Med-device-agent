import json
from pathlib import Path

import local_runtime

ROOT = Path(__file__).resolve().parents[1]
SOURCE_VERSION = "20161267-4370-409d-b1b8-8cffd8d0d11f"


def main():
    result = local_runtime.mysql("""
        SELECT JSON_OBJECT(
          'source_version', sv.source_version, 'title', s.title,
          'ownership', s.ownership, 'owner_project_id', s.owner_project_id,
          'original_sha256', sv.original_sha256,
          'lifecycle_rows', (SELECT COUNT(*) FROM medical_local.source_lifecycle_state ls WHERE ls.tenant_id=sv.tenant_id AND ls.source_version=sv.source_version),
          'parse_rows', (SELECT COUNT(*) FROM medical_local.parse_version p WHERE p.tenant_id=sv.tenant_id AND p.source_version=sv.source_version),
          'rights_rows', (SELECT COUNT(*) FROM medical_local.rights_revision r WHERE r.tenant_id=sv.tenant_id AND r.rights_revision=sv.rights_revision),
          'gate_rows', (SELECT COUNT(*) FROM medical_local.source_use_gate g WHERE g.tenant_id=sv.tenant_id AND g.source_version=sv.source_version))
        FROM medical_local.source_version sv
        JOIN medical_local.source s ON s.tenant_id=sv.tenant_id AND s.source_id=sv.source_id
        WHERE sv.source_version='""" + SOURCE_VERSION + "'", local_runtime.settings()["root_password"])
    value = json.loads(result.stdout)
    (ROOT / ".implementation-work/project-profile-source-inventory.json").write_text(json.dumps(value, ensure_ascii=False, indent=2))
    print(json.dumps(value, ensure_ascii=False))


if __name__ == "__main__":
    main()
