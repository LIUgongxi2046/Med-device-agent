--liquibase formatted sql
--changeset medical-agent:1.0.0-150-evaluation-tools dbms:mysql runInTransaction:false failOnError:true
-- Independent EVAL tool originals; a waiting result is an unanswered business input, not PASS.
CREATE TABLE formal_evaluation_tool_receipt (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 run_id CHAR(36) CHARACTER SET ascii NOT NULL, step_id CHAR(36) CHARACTER SET ascii NOT NULL,
 session_id CHAR(36) CHARACTER SET ascii NOT NULL, receipt_id CHAR(36) CHARACTER SET ascii NOT NULL,
 call_id VARCHAR(256) COLLATE utf8mb4_bin NOT NULL, invocation_id CHAR(36) CHARACTER SET ascii NOT NULL,
 tool_id VARCHAR(8) CHARACTER SET ascii NOT NULL, tool_version CHAR(36) CHARACTER SET ascii NOT NULL,
 action_id CHAR(36) CHARACTER SET ascii NOT NULL, lease_generation BIGINT UNSIGNED NOT NULL,
 input_sha256 CHAR(64) CHARACTER SET ascii NOT NULL, receipt_json LONGTEXT NOT NULL, receipt_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 audit_id CHAR(36) CHARACTER SET ascii NOT NULL, event_id CHAR(36) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL,
 classification VARCHAR(24) NOT NULL, schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 PRIMARY KEY(tenant_id,receipt_id), UNIQUE KEY uq_eval_tool_call(tenant_id,session_id,call_id),
 FOREIGN KEY(tenant_id,project_id,run_id) REFERENCES formal_evaluation_job(tenant_id,project_id,run_id),
 FOREIGN KEY(tenant_id,run_id,step_id) REFERENCES formal_evaluation_node(tenant_id,run_id,step_id),
 FOREIGN KEY(tenant_id,project_id,run_id,step_id) REFERENCES run_step(tenant_id,project_id,run_id,step_id),
 FOREIGN KEY(tenant_id,session_id) REFERENCES professional_runtime_session(tenant_id,session_id),
 FOREIGN KEY(tenant_id,invocation_id) REFERENCES invocation(tenant_id,invocation_id),
 FOREIGN KEY(tenant_id,run_id,step_id,action_id) REFERENCES step_action_binding(tenant_id,run_id,step_id,action_id),
 FOREIGN KEY(tenant_id,run_id) REFERENCES system_job_run(tenant_id,run_id),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK(tool_id IN ('AT-01','AT-02','AT-03','AT-04','AT-08')),
 CHECK(lease_generation>0 AND JSON_VALID(receipt_json) AND receipt_sha256 REGEXP '^[a-f0-9]{64}$' AND input_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK(JSON_UNQUOTE(JSON_EXTRACT(receipt_json,'$.receipt_id'))=receipt_id AND JSON_UNQUOTE(JSON_EXTRACT(receipt_json,'$.session_id'))=session_id AND JSON_UNQUOTE(JSON_EXTRACT(receipt_json,'$.tool_call.invocation_id'))=invocation_id AND JSON_UNQUOTE(JSON_EXTRACT(receipt_json,'$.tool_call.tool_call_id'))=call_id),
 CHECK(schema_version='1.0.0' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK(classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
--changeset medical-agent:1.0.0-150-evaluation-tools-update dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER imm_eval_tool_receipt_update BEFORE UPDATE ON formal_evaluation_tool_receipt FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IMMUTABLE_EVAL_TOOL_RECEIPT';
--changeset medical-agent:1.0.0-150-evaluation-tools-delete dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER imm_eval_tool_receipt_delete BEFORE DELETE ON formal_evaluation_tool_receipt FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IMMUTABLE_EVAL_TOOL_RECEIPT';
--changeset medical-agent:1.0.0-150-evaluation-tool-question dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE formal_evaluation_tool_question (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 run_id CHAR(36) CHARACTER SET ascii NOT NULL, step_id CHAR(36) CHARACTER SET ascii NOT NULL,
 session_id CHAR(36) CHARACTER SET ascii NOT NULL, question_id CHAR(36) CHARACTER SET ascii NOT NULL,
 question_json LONGTEXT NOT NULL, question_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL,
 classification VARCHAR(24) NOT NULL, schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 PRIMARY KEY(tenant_id,question_id), UNIQUE KEY uq_eval_tool_question_session(tenant_id,session_id),
 FOREIGN KEY(tenant_id,question_id) REFERENCES formal_evaluation_tool_receipt(tenant_id,receipt_id),
 FOREIGN KEY(tenant_id,project_id,run_id) REFERENCES formal_evaluation_job(tenant_id,project_id,run_id),
 FOREIGN KEY(tenant_id,run_id,step_id) REFERENCES formal_evaluation_node(tenant_id,run_id,step_id),
 FOREIGN KEY(tenant_id,session_id) REFERENCES professional_runtime_session(tenant_id,session_id),
 FOREIGN KEY(tenant_id,run_id) REFERENCES system_job_run(tenant_id,run_id),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK(JSON_VALID(question_json) AND question_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK(schema_version='1.0.0' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK(classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
--changeset medical-agent:1.0.0-150-evaluation-question-update dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER imm_eval_tool_question_update BEFORE UPDATE ON formal_evaluation_tool_question FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IMMUTABLE_EVAL_TOOL_QUESTION';
--changeset medical-agent:1.0.0-150-evaluation-question-delete dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER imm_eval_tool_question_delete BEFORE DELETE ON formal_evaluation_tool_question FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IMMUTABLE_EVAL_TOOL_QUESTION';
