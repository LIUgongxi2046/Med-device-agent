--liquibase formatted sql
-- Independent EVAL contexts reference actual compiled input and observed SDK request; 076 owns all billing and receipts.
--changeset medical-agent:145-01-evaluation-invocation-context dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE formal_evaluation_invocation_context (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
 project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 run_id CHAR(36) CHARACTER SET ascii NOT NULL,
 step_id CHAR(36) CHARACTER SET ascii NOT NULL,
 session_id CHAR(36) CHARACTER SET ascii NOT NULL,
 request_id CHAR(36) CHARACTER SET ascii NOT NULL,
 ordinal INT UNSIGNED NOT NULL,
 context_manifest_id CHAR(36) CHARACTER SET ascii NOT NULL,
 action_id CHAR(36) CHARACTER SET ascii NOT NULL,
 lease_generation BIGINT UNSIGNED NOT NULL,
 canonical_request LONGTEXT NOT NULL,
 request_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 canonical_dependencies LONGTEXT NOT NULL,
 dependencies_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 source_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(32) NOT NULL,
 schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL,
 created_at DATETIME(6) NOT NULL,
 classification VARCHAR(24) NOT NULL,
 PRIMARY KEY(tenant_id,context_manifest_id),
 UNIQUE KEY uq_eval_invocation_ordinal(tenant_id,run_id,step_id,session_id,ordinal),
 UNIQUE KEY uq_eval_invocation_request(tenant_id,session_id,request_id),
 UNIQUE KEY uq_eval_invocation_action(tenant_id,run_id,step_id,action_id),
 FOREIGN KEY(tenant_id,project_id,run_id) REFERENCES formal_evaluation_job(tenant_id,project_id,run_id),
 FOREIGN KEY(tenant_id,run_id,step_id) REFERENCES formal_evaluation_node(tenant_id,run_id,step_id),
 FOREIGN KEY(tenant_id,context_manifest_id) REFERENCES context_manifest(tenant_id,context_manifest_id),
 FOREIGN KEY(tenant_id,session_id) REFERENCES professional_runtime_session(tenant_id,session_id),
 FOREIGN KEY(tenant_id,run_id,step_id,lease_generation) REFERENCES professional_runtime_session(tenant_id,run_id,step_id,lease_generation),
 FOREIGN KEY(tenant_id,run_id,step_id,action_id) REFERENCES step_action_binding(tenant_id,run_id,step_id,action_id),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK(ordinal BETWEEN 1 AND 257 AND lease_generation=1 AND JSON_VALID(canonical_request) AND JSON_VALID(canonical_dependencies)),
 CHECK(JSON_UNQUOTE(JSON_EXTRACT(canonical_request,'$.execution_kind'))='SYSTEM_JOB'),
 CHECK(JSON_UNQUOTE(JSON_EXTRACT(canonical_request,'$.job_type'))='EVALUATION_VERIFICATION'),
 CHECK(JSON_UNQUOTE(JSON_EXTRACT(canonical_request,'$.request_parameters.purpose'))='EVALUATION'),
 CHECK(schema_version='1.0.0' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK(request_sha256 REGEXP '^[a-f0-9]{64}$' AND dependencies_sha256 REGEXP '^[a-f0-9]{64}$' AND source_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK(classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
--changeset medical-agent:145-02-evaluation-invocation-immutable dbms:mysql runInTransaction:false failOnError:true splitStatements:false
CREATE TRIGGER formal_evaluation_invocation_context_no_update BEFORE UPDATE ON formal_evaluation_invocation_context FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IMMUTABLE_EVALUATION_INVOCATION_CONTEXT';
--changeset medical-agent:145-03-evaluation-invocation-no-delete dbms:mysql runInTransaction:false failOnError:true splitStatements:false
CREATE TRIGGER formal_evaluation_invocation_context_no_delete BEFORE DELETE ON formal_evaluation_invocation_context FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IMMUTABLE_EVALUATION_INVOCATION_CONTEXT';
