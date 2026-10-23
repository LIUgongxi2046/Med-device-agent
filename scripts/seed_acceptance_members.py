import argparse
import json
import uuid

import local_runtime as runtime


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("user_id")
    arguments = parser.parse_args()
    runtime.verify_application_deployment()
    user = str(uuid.UUID(arguments.user_id))
    ledger = json.loads((runtime.RUNTIME / "acceptance-workspace-seed.json").read_bytes())
    if ledger["data_kind"] != "SIMULATION":
        raise ValueError("ACCEPTANCE_MEMBERSHIP_REQUIRED")
    tenant = str(uuid.UUID(ledger["tenant_id"]))
    password = runtime.settings()["root_password"]
    present = runtime.mysql("SELECT COUNT(*) FROM medical_local.platform_member_route WHERE tenant_id='" + tenant + "' AND user_id='" + user + "'", password).stdout.strip()
    if present == "1":
        print("ACCEPTANCE_MEMBERSHIP_EXISTS")
        return
    if present != "0":
        raise ValueError("ACCEPTANCE_MEMBERSHIP_AMBIGUOUS")
    actor = runtime.mysql("SELECT DISTINCT created_by FROM medical_local.project_record WHERE tenant_id='" + tenant + "' AND project_id='" + str(uuid.UUID(ledger["project_id"])) + "'", password).stdout.strip()
    actor = str(uuid.UUID(actor))
    member = str(uuid.uuid4())
    statements = ["START TRANSACTION", "INSERT INTO medical_local.tenant_member(tenant_id,member_id,user_id,status,valid_from,created_by,created_at,classification,revision,updated_at) VALUES('" + tenant + "','" + member + "','" + user + "','ACTIVE',UTC_TIMESTAMP(6),'" + actor + "',UTC_TIMESTAMP(6),'SIMULATION',1,UTC_TIMESTAMP(6))", "INSERT INTO medical_local.identity_binding(tenant_id,identity_id,user_id,mode,local_user_id,status,created_by,created_at,classification,revision,updated_at) VALUES('" + tenant + "','" + str(uuid.uuid4()) + "','" + user + "','local','" + user + "','ACTIVE','" + actor + "',UTC_TIMESTAMP(6),'SIMULATION',1,UTC_TIMESTAMP(6))", "INSERT INTO medical_local.platform_member_route(user_id,tenant_id,member_id,created_by,created_at,classification,revision,updated_at) VALUES('" + user + "','" + tenant + "','" + member + "','" + actor + "',UTC_TIMESTAMP(6),'SIMULATION',1,UTC_TIMESTAMP(6))", "UPDATE medical_local.tenant SET auth_epoch=auth_epoch+1 WHERE tenant_id='" + tenant + "'", "COMMIT"]
    runtime.mysql(";".join(statements), password)
    print("ACCEPTANCE_MEMBERSHIP_CREATED")


if __name__ == "__main__":
    main()
