--liquibase formatted sql
-- Real EVAL-only original node dispatch. No runtime result, grant or approval is seeded.
--changeset medical-agent:143-01-evaluation-node-dispatch dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE formal_evaluation_node_dispatch (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
 project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 run_id CHAR(36) CHARACTER SET ascii NOT NULL,
 step_id CHAR(36) CHARACTER SET ascii NOT NULL,
 command_id CHAR(36) CHARACTER SET ascii NOT NULL,
 action_id CHAR(36) CHARACTER SET ascii NOT NULL,
 lease_generation BIGINT UNSIGNED NOT NULL,
 node_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 input_artifact_id CHAR(36) CHARACTER SET ascii NOT NULL,
 input_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 worker_service VARCHAR(512) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 canonical_dispatch LONGTEXT NOT NULL,
 dispatch_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 audit_id CHAR(36) CHARACTER SET ascii NOT NULL,
 event_id CHAR(36) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(32) NOT NULL,
 schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL,
 created_at DATETIME(6) NOT NULL,
 classification VARCHAR(24) NOT NULL,
 PRIMARY KEY(tenant_id,run_id,step_id),
 UNIQUE KEY uq_eval_node_dispatch_command(tenant_id,command_id),
 UNIQUE KEY uq_eval_node_dispatch_action(tenant_id,run_id,step_id,action_id),
 FOREIGN KEY(tenant_id,project_id,run_id) REFERENCES formal_evaluation_job(tenant_id,project_id,run_id),
 FOREIGN KEY(tenant_id,run_id,step_id) REFERENCES formal_evaluation_node(tenant_id,run_id,step_id),
 FOREIGN KEY(tenant_id,run_id,step_id,action_id) REFERENCES step_action_binding(tenant_id,run_id,step_id,action_id),
 FOREIGN KEY(tenant_id,input_artifact_id,input_sha256) REFERENCES version_registry(tenant_id,version_id,content_sha256),
 FOREIGN KEY(tenant_id,input_artifact_id) REFERENCES artifact(tenant_id,artifact_id),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 FOREIGN KEY(tenant_id,audit_id) REFERENCES audit_event(tenant_id,audit_id),
 FOREIGN KEY(tenant_id,event_id) REFERENCES outbox_event(tenant_id,event_id),
 CHECK(lease_generation=1 AND JSON_VALID(canonical_dispatch)),
 CHECK(schema_version='1.0.0' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK(node_sha256 REGEXP '^[a-f0-9]{64}$' AND input_sha256 REGEXP '^[a-f0-9]{64}$' AND dispatch_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK(classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
--changeset medical-agent:143-02-evaluation-dispatch-immutable dbms:mysql runInTransaction:false failOnError:true splitStatements:false
CREATE TRIGGER formal_evaluation_node_dispatch_no_update BEFORE UPDATE ON formal_evaluation_node_dispatch FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IMMUTABLE_EVALUATION_NODE_DISPATCH';
--changeset medical-agent:143-03-evaluation-dispatch-no-delete dbms:mysql runInTransaction:false failOnError:true splitStatements:false
CREATE TRIGGER formal_evaluation_node_dispatch_no_delete BEFORE DELETE ON formal_evaluation_node_dispatch FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IMMUTABLE_EVALUATION_NODE_DISPATCH';

--changeset medical-agent:143-04-evaluation-runtime-prepare dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE formal_evaluation_runtime_prepare (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
 project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 run_id CHAR(36) CHARACTER SET ascii NOT NULL,
 step_id CHAR(36) CHARACTER SET ascii NOT NULL,
 action_id CHAR(36) CHARACTER SET ascii NOT NULL,
 lease_generation BIGINT UNSIGNED NOT NULL,
 session_id CHAR(36) CHARACTER SET ascii NOT NULL,
 envelope_json LONGTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
 envelope_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 launch_json LONGTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
 launch_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 deployment_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 audit_id CHAR(36) CHARACTER SET ascii NOT NULL,
 event_id CHAR(36) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(32) NOT NULL,
 schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL,
 created_at DATETIME(6) NOT NULL,
 classification VARCHAR(24) NOT NULL,
 PRIMARY KEY (tenant_id,run_id,step_id,lease_generation),
 UNIQUE KEY uq_eval_runtime_prepare_session (tenant_id,session_id),
 FOREIGN KEY (tenant_id,run_id) REFERENCES system_job_run(tenant_id,run_id),
 FOREIGN KEY (tenant_id,project_id,run_id,step_id) REFERENCES run_step(tenant_id,project_id,run_id,step_id),
 FOREIGN KEY (tenant_id,run_id,step_id,action_id) REFERENCES step_action_binding(tenant_id,run_id,step_id,action_id),
 FOREIGN KEY (tenant_id,audit_id) REFERENCES audit_event(tenant_id,audit_id),
 FOREIGN KEY (tenant_id,event_id) REFERENCES outbox_event(tenant_id,event_id),
 FOREIGN KEY (tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 FOREIGN KEY (tenant_id,run_id,step_id,action_id) REFERENCES formal_evaluation_node_dispatch(tenant_id,run_id,step_id,action_id),
 CHECK (lease_generation=1 AND schema_version='1.0.0'),
 CHECK (JSON_VALID(envelope_json) AND JSON_VALID(launch_json)),
 CHECK (envelope_sha256 REGEXP '^[a-f0-9]{64}$' AND launch_sha256 REGEXP '^[a-f0-9]{64}$' AND deployment_sha256 REGEXP '^[a-f0-9]{64}$' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK (classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:143-05-evaluation-runtime-update dbms:mysql runInTransaction:false failOnError:true splitStatements:false
CREATE TRIGGER formal_evaluation_runtime_prepare_no_update BEFORE UPDATE ON formal_evaluation_runtime_prepare FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IMMUTABLE_EVALUATION_RUNTIME_PREPARE';

--changeset medical-agent:143-05-evaluation-runtime-delete dbms:mysql runInTransaction:false failOnError:true splitStatements:false
CREATE TRIGGER formal_evaluation_runtime_prepare_no_delete BEFORE DELETE ON formal_evaluation_runtime_prepare FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IMMUTABLE_EVALUATION_RUNTIME_PREPARE';

--changeset medical-agent:143-06-evaluation-node-outcome dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE formal_evaluation_node_outcome (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 run_id CHAR(36) CHARACTER SET ascii NOT NULL,step_id CHAR(36) CHARACTER SET ascii NOT NULL,
 receipt_id CHAR(36) CHARACTER SET ascii NOT NULL,session_id CHAR(36) CHARACTER SET ascii NOT NULL,
 state VARCHAR(24) CHARACTER SET ascii NOT NULL,canonical_receipt LONGTEXT NOT NULL,receipt_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 output_artifact_id CHAR(36) CHARACTER SET ascii NOT NULL,output_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 audit_id CHAR(36) CHARACTER SET ascii NOT NULL,event_id CHAR(36) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(32) NOT NULL,schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL,created_at DATETIME(6) NOT NULL,classification VARCHAR(24) NOT NULL,
 PRIMARY KEY(tenant_id,run_id,step_id),UNIQUE KEY uq_eval_node_outcome_receipt(tenant_id,receipt_id),
 FOREIGN KEY(tenant_id,run_id,step_id) REFERENCES formal_evaluation_node_dispatch(tenant_id,run_id,step_id),
 FOREIGN KEY(tenant_id,session_id) REFERENCES professional_runtime_session(tenant_id,session_id),
 FOREIGN KEY(tenant_id,output_artifact_id) REFERENCES artifact(tenant_id,artifact_id),
 FOREIGN KEY(tenant_id,output_artifact_id,output_sha256) REFERENCES version_registry(tenant_id,version_id,content_sha256),
 FOREIGN KEY(tenant_id,audit_id) REFERENCES audit_event(tenant_id,audit_id),FOREIGN KEY(tenant_id,event_id) REFERENCES outbox_event(tenant_id,event_id),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK(state IN ('SUCCEEDED','WAITING_INPUT','FAILED') AND JSON_VALID(canonical_receipt)),
 CHECK(schema_version='1.0.0' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$' AND receipt_sha256 REGEXP '^[a-f0-9]{64}$' AND output_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK(classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
--changeset medical-agent:143-07-evaluation-outcome-no-update dbms:mysql runInTransaction:false failOnError:true splitStatements:false
CREATE TRIGGER formal_evaluation_node_outcome_no_update BEFORE UPDATE ON formal_evaluation_node_outcome FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IMMUTABLE_EVALUATION_NODE_OUTCOME';
--changeset medical-agent:143-08-evaluation-outcome-no-delete dbms:mysql runInTransaction:false failOnError:true splitStatements:false
CREATE TRIGGER formal_evaluation_node_outcome_no_delete BEFORE DELETE ON formal_evaluation_node_outcome FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IMMUTABLE_EVALUATION_NODE_OUTCOME';

--changeset medical-agent:143-09-evaluation-host-launch-attempt dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE formal_evaluation_host_launch_attempt (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,project_id CHAR(36) CHARACTER SET ascii NOT NULL,run_id CHAR(36) CHARACTER SET ascii NOT NULL,step_id CHAR(36) CHARACTER SET ascii NOT NULL,session_id CHAR(36) CHARACTER SET ascii NOT NULL,
 launch_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,audit_id CHAR(36) CHARACTER SET ascii NOT NULL,event_id CHAR(36) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(32) NOT NULL,schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,created_by CHAR(36) CHARACTER SET ascii NOT NULL,created_at DATETIME(6) NOT NULL,classification VARCHAR(24) NOT NULL,
 PRIMARY KEY(tenant_id,session_id),UNIQUE KEY uq_eval_host_launch_node(tenant_id,run_id,step_id),
 FOREIGN KEY(tenant_id,session_id) REFERENCES formal_evaluation_runtime_prepare(tenant_id,session_id),
 FOREIGN KEY(tenant_id,run_id,step_id) REFERENCES formal_evaluation_node_dispatch(tenant_id,run_id,step_id),
 FOREIGN KEY(tenant_id,audit_id) REFERENCES audit_event(tenant_id,audit_id),FOREIGN KEY(tenant_id,event_id) REFERENCES outbox_event(tenant_id,event_id),FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK(schema_version='1.0.0' AND launch_sha256 REGEXP '^[a-f0-9]{64}$' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),CHECK(classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
--changeset medical-agent:143-10-evaluation-host-launch-no-update dbms:mysql runInTransaction:false failOnError:true splitStatements:false
CREATE TRIGGER formal_evaluation_host_launch_attempt_no_update BEFORE UPDATE ON formal_evaluation_host_launch_attempt FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IMMUTABLE_EVALUATION_HOST_LAUNCH';
--changeset medical-agent:143-11-evaluation-host-launch-no-delete dbms:mysql runInTransaction:false failOnError:true splitStatements:false
CREATE TRIGGER formal_evaluation_host_launch_attempt_no_delete BEFORE DELETE ON formal_evaluation_host_launch_attempt FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IMMUTABLE_EVALUATION_HOST_LAUNCH';

--changeset medical-agent:143-12-evaluation-node-waiting dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE formal_evaluation_node_waiting (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,project_id CHAR(36) CHARACTER SET ascii NOT NULL,run_id CHAR(36) CHARACTER SET ascii NOT NULL,step_id CHAR(36) CHARACTER SET ascii NOT NULL,
 receipt_id CHAR(36) CHARACTER SET ascii NOT NULL,canonical_receipt LONGTEXT NOT NULL,receipt_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,audit_id CHAR(36) CHARACTER SET ascii NOT NULL,event_id CHAR(36) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(32) NOT NULL,schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,created_by CHAR(36) CHARACTER SET ascii NOT NULL,created_at DATETIME(6) NOT NULL,classification VARCHAR(24) NOT NULL,
 PRIMARY KEY(tenant_id,run_id,step_id),UNIQUE KEY uq_eval_waiting_receipt(tenant_id,receipt_id),FOREIGN KEY(tenant_id,run_id,step_id) REFERENCES formal_evaluation_node(tenant_id,run_id,step_id),
 FOREIGN KEY(tenant_id,audit_id) REFERENCES audit_event(tenant_id,audit_id),FOREIGN KEY(tenant_id,event_id) REFERENCES outbox_event(tenant_id,event_id),FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK(JSON_VALID(canonical_receipt) AND schema_version='1.0.0' AND receipt_sha256 REGEXP '^[a-f0-9]{64}$' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),CHECK(classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
--changeset medical-agent:143-13-evaluation-waiting-no-update dbms:mysql runInTransaction:false failOnError:true splitStatements:false
CREATE TRIGGER formal_evaluation_node_waiting_no_update BEFORE UPDATE ON formal_evaluation_node_waiting FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IMMUTABLE_EVALUATION_NODE_WAITING';
--changeset medical-agent:143-14-evaluation-waiting-no-delete dbms:mysql runInTransaction:false failOnError:true splitStatements:false
CREATE TRIGGER formal_evaluation_node_waiting_no_delete BEFORE DELETE ON formal_evaluation_node_waiting FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IMMUTABLE_EVALUATION_NODE_WAITING';

--changeset medical-agent:143-15-evaluation-cell-outcome dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE formal_evaluation_cell_outcome (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,project_id CHAR(36) CHARACTER SET ascii NOT NULL,run_id CHAR(36) CHARACTER SET ascii NOT NULL,receipt_id CHAR(36) CHARACTER SET ascii NOT NULL,state VARCHAR(24) CHARACTER SET ascii NOT NULL,
 canonical_receipt LONGTEXT NOT NULL,receipt_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,audit_id CHAR(36) CHARACTER SET ascii NOT NULL,event_id CHAR(36) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(32) NOT NULL,schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,created_by CHAR(36) CHARACTER SET ascii NOT NULL,created_at DATETIME(6) NOT NULL,classification VARCHAR(24) NOT NULL,
 PRIMARY KEY(tenant_id,run_id),UNIQUE KEY uq_eval_cell_outcome_receipt(tenant_id,receipt_id),FOREIGN KEY(tenant_id,project_id,run_id) REFERENCES formal_evaluation_job(tenant_id,project_id,run_id),FOREIGN KEY(tenant_id,audit_id) REFERENCES audit_event(tenant_id,audit_id),FOREIGN KEY(tenant_id,event_id) REFERENCES outbox_event(tenant_id,event_id),FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK(state IN ('SUCCEEDED','WAITING_INPUT','FAILED','CANCELLED') AND JSON_VALID(canonical_receipt)),CHECK(schema_version='1.0.0' AND receipt_sha256 REGEXP '^[a-f0-9]{64}$' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),CHECK(classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
--changeset medical-agent:143-16-evaluation-cell-outcome-no-update dbms:mysql runInTransaction:false failOnError:true splitStatements:false
CREATE TRIGGER formal_evaluation_cell_outcome_no_update BEFORE UPDATE ON formal_evaluation_cell_outcome FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IMMUTABLE_EVALUATION_CELL_OUTCOME';
--changeset medical-agent:143-17-evaluation-cell-outcome-no-delete dbms:mysql runInTransaction:false failOnError:true splitStatements:false
CREATE TRIGGER formal_evaluation_cell_outcome_no_delete BEFORE DELETE ON formal_evaluation_cell_outcome FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IMMUTABLE_EVALUATION_CELL_OUTCOME';

--changeset medical-agent:143-18-evaluation-cancel-intent dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE formal_evaluation_cancel_intent (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,project_id CHAR(36) CHARACTER SET ascii NOT NULL,run_id CHAR(36) CHARACTER SET ascii NOT NULL,receipt_id CHAR(36) CHARACTER SET ascii NOT NULL,
 canonical_receipt LONGTEXT NOT NULL,receipt_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,audit_id CHAR(36) CHARACTER SET ascii NOT NULL,event_id CHAR(36) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(32) NOT NULL,schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,created_by CHAR(36) CHARACTER SET ascii NOT NULL,created_at DATETIME(6) NOT NULL,classification VARCHAR(24) NOT NULL,
 PRIMARY KEY(tenant_id,run_id),UNIQUE KEY uq_eval_cancel_receipt(tenant_id,receipt_id),FOREIGN KEY(tenant_id,project_id,run_id) REFERENCES formal_evaluation_job(tenant_id,project_id,run_id),FOREIGN KEY(tenant_id,audit_id) REFERENCES audit_event(tenant_id,audit_id),FOREIGN KEY(tenant_id,event_id) REFERENCES outbox_event(tenant_id,event_id),FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK(JSON_VALID(canonical_receipt) AND schema_version='1.0.0' AND receipt_sha256 REGEXP '^[a-f0-9]{64}$' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),CHECK(classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
--changeset medical-agent:143-19-evaluation-cancel-no-update dbms:mysql runInTransaction:false failOnError:true splitStatements:false
CREATE TRIGGER formal_evaluation_cancel_intent_no_update BEFORE UPDATE ON formal_evaluation_cancel_intent FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IMMUTABLE_EVALUATION_CANCEL_INTENT';
--changeset medical-agent:143-20-evaluation-cancel-no-delete dbms:mysql runInTransaction:false failOnError:true splitStatements:false
CREATE TRIGGER formal_evaluation_cancel_intent_no_delete BEFORE DELETE ON formal_evaluation_cancel_intent FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IMMUTABLE_EVALUATION_CANCEL_INTENT';
