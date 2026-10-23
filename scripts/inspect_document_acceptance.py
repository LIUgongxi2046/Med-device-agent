import json
import uuid

import local_runtime as runtime


def main():
    seed = json.loads((runtime.RUNTIME / "acceptance-workspace-seed.json").read_bytes())
    tenant = str(uuid.UUID(seed["tenant_id"]))
    query = "SELECT operation_name,state,http_status,created_at,updated_at,target_id FROM medical_local.operation_record WHERE tenant_id='" + tenant + "' AND operation_name IN ('BX-02','BX-03','BX-04','DC-02') ORDER BY created_at DESC LIMIT 12"
    print(runtime.mysql(query, runtime.settings()["root_password"]).stdout)
    print(runtime.mysql("SELECT action,status,COUNT(*) FROM medical_local.permission_grant WHERE tenant_id='" + tenant + "' AND action IN ('document.write','document.check') GROUP BY action,status", runtime.settings()["root_password"]).stdout)
    print(runtime.mysql("SHOW GLOBAL STATUS WHERE Variable_name IN ('Com_stmt_execute','Com_stmt_prepare','Prepared_stmt_count')", runtime.settings()["root_password"]).stdout)
    print(runtime.mysql("SELECT UTC_TIMESTAMP(6),NOW(6)", runtime.settings()["root_password"]).stdout)
    print(runtime.mysql("SELECT action,created_at,resource_ref FROM medical_local.audit_event WHERE tenant_id='" + tenant + "' AND action='document.revision.saved' ORDER BY created_at DESC LIMIT 3", runtime.settings()["root_password"]).stdout)
    print(runtime.mysql("SELECT action,resource_kind,resource_id,status,`condition` FROM medical_local.permission_grant WHERE tenant_id='" + tenant + "' AND action IN ('audit.read','document.read','artifact.read')", runtime.settings()["root_password"]).stdout)


if __name__ == "__main__":
    main()
