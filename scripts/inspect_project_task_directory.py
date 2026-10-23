import json
import uuid

import local_runtime as runtime


def main():
    seed = json.loads((runtime.RUNTIME / "acceptance-workspace-seed.json").read_bytes())
    tenant = str(uuid.UUID(seed["tenant_id"]))
    project = str(uuid.UUID(seed["project_id"]))
    query = "SELECT JSON_ARRAYAGG(JSON_OBJECT('task_id',task_id,'status',status)) FROM medical_local.task_head WHERE tenant_id='" + tenant + "' AND project_id='" + project + "'"
    print(runtime.mysql(query, runtime.settings()["root_password"]).stdout)
    references = "SELECT h.task_id,h.project_id,h.current_revision,COUNT(s.task_id) AS same_tenant_identity_count FROM medical_local.task_head h JOIN medical_local.task_head s ON s.tenant_id=h.tenant_id AND s.task_id=h.task_id WHERE h.tenant_id='" + tenant + "' AND h.project_id='" + project + "' GROUP BY h.task_id,h.project_id,h.current_revision"
    print(runtime.mysql(references, runtime.settings()["root_password"]).stdout)
    connectors = "SELECT action,resource_kind,status,COUNT(*) FROM medical_local.permission_grant WHERE tenant_id='" + tenant + "' AND subject_id=(SELECT user_id FROM medical_local.platform_local_account WHERE username='liuhaonan') AND action LIKE 'connector.%' GROUP BY action,resource_kind,status"
    print(runtime.mysql(connectors, runtime.settings()["root_password"]).stdout)


if __name__ == "__main__":
    main()
