--liquibase formatted sql
-- No grants, approval records, evaluations, runtime receipts or result rows are seeded.
-- Separate SYSTEM_JOB evidence. 075 task_run foreign keys must not be weakened for this path.
--changeset medical-agent:083-01-evaluation-execution dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE formal_evaluation_execution (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 execution_id CHAR(36) CHARACTER SET ascii NOT NULL,evaluation_id CHAR(36) CHARACTER SET ascii NOT NULL,
 definition_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 candidate_version CHAR(36) CHARACTER SET ascii NOT NULL,candidate_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 policy_version CHAR(36) CHARACTER SET ascii NOT NULL,policy_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 idempotency_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 canonical_request LONGTEXT NOT NULL,request_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 canonical_admission LONGTEXT NOT NULL,admission_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 runtime_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 expected_cells INT UNSIGNED NOT NULL,expected_nodes INT UNSIGNED NOT NULL,
 audit_id CHAR(36) CHARACTER SET ascii NOT NULL,event_id CHAR(36) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(32) NOT NULL,schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL,created_at DATETIME(6) NOT NULL,classification VARCHAR(24) NOT NULL,
 PRIMARY KEY(tenant_id,execution_id),
 UNIQUE KEY uq_eval_execution_key(tenant_id,created_by,idempotency_key),
 UNIQUE KEY uq_eval_execution_candidate(tenant_id,evaluation_id,candidate_version),
 UNIQUE KEY uq_eval_execution_project(tenant_id,project_id,execution_id),
 FOREIGN KEY(tenant_id,project_id) REFERENCES project(tenant_id,project_id),
 FOREIGN KEY(tenant_id,evaluation_id,definition_sha256) REFERENCES version_registry(tenant_id,version_id,content_sha256),
 FOREIGN KEY(tenant_id,candidate_version,candidate_sha256) REFERENCES version_registry(tenant_id,version_id,content_sha256),
 FOREIGN KEY(tenant_id,policy_version,policy_sha256) REFERENCES version_registry(tenant_id,version_id,content_sha256),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 FOREIGN KEY(tenant_id,audit_id) REFERENCES audit_event(tenant_id,audit_id),
 FOREIGN KEY(tenant_id,event_id) REFERENCES outbox_event(tenant_id,event_id),
 CHECK(JSON_VALID(canonical_request) AND JSON_VALID(canonical_admission)),
 CHECK(expected_cells>0 AND expected_cells<=10000 AND expected_nodes>=expected_cells AND expected_nodes<=10000),
 CHECK(CHAR_LENGTH(idempotency_key) BETWEEN 16 AND 128 AND idempotency_key REGEXP '^[!-~]+$'),
 CHECK(schema_version='1.0.0' AND request_sha256 REGEXP '^[a-f0-9]{64}$' AND admission_sha256 REGEXP '^[a-f0-9]{64}$' AND runtime_manifest_sha256 REGEXP '^[a-f0-9]{64}$' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK(classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:083-02-evaluation-cells dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE formal_evaluation_cell (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 execution_id CHAR(36) CHARACTER SET ascii NOT NULL,sample_id CHAR(36) CHARACTER SET ascii NOT NULL,
 repeat_index INT UNSIGNED NOT NULL,source_family CHAR(36) CHARACTER SET ascii NOT NULL,split VARCHAR(16) NOT NULL,
 run_id CHAR(36) CHARACTER SET ascii NOT NULL,principal_id CHAR(36) CHARACTER SET ascii NOT NULL,
 input_version CHAR(36) CHARACTER SET ascii NOT NULL,input_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 expected_version CHAR(36) CHARACTER SET ascii NOT NULL,expected_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 input_manifest_id CHAR(36) CHARACTER SET ascii NOT NULL,input_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 canonical_cell LONGTEXT NOT NULL,cell_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 canonical_scope LONGTEXT NOT NULL,canonical_principal LONGTEXT NOT NULL,canonical_input LONGTEXT NOT NULL,
 schema_version VARCHAR(32) NOT NULL,schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL,created_at DATETIME(6) NOT NULL,classification VARCHAR(24) NOT NULL,
 PRIMARY KEY(tenant_id,execution_id,sample_id,repeat_index),UNIQUE KEY uq_evaluation_cell_run(tenant_id,run_id),
 FOREIGN KEY(tenant_id,project_id,execution_id) REFERENCES formal_evaluation_execution(tenant_id,project_id,execution_id),
 FOREIGN KEY(tenant_id,run_id) REFERENCES system_job_run(tenant_id,run_id),
 FOREIGN KEY(tenant_id,principal_id) REFERENCES run_principal(tenant_id,principal_id),
 FOREIGN KEY(tenant_id,input_version,input_sha256) REFERENCES version_registry(tenant_id,version_id,content_sha256),
 FOREIGN KEY(tenant_id,expected_version,expected_sha256) REFERENCES version_registry(tenant_id,version_id,content_sha256),
 FOREIGN KEY(tenant_id,input_manifest_id,input_manifest_sha256) REFERENCES version_registry(tenant_id,version_id,content_sha256),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK(repeat_index<100 AND split IN ('DEVELOPMENT','TUNING','ACCEPTANCE')),
 CHECK(JSON_VALID(canonical_cell) AND JSON_VALID(canonical_scope) AND JSON_VALID(canonical_principal) AND JSON_VALID(canonical_input)),
 CHECK(schema_version='1.0.0' AND cell_sha256 REGEXP '^[a-f0-9]{64}$' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK(classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:083-03-evaluation-nodes dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE formal_evaluation_node (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 run_id CHAR(36) CHARACTER SET ascii NOT NULL,step_id CHAR(36) CHARACTER SET ascii NOT NULL,
 node_kind CHAR(3) CHARACTER SET ascii NOT NULL,canonical_node LONGTEXT NOT NULL,node_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(32) NOT NULL,schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL,created_at DATETIME(6) NOT NULL,classification VARCHAR(24) NOT NULL,
 PRIMARY KEY(tenant_id,run_id,step_id),UNIQUE KEY uq_evaluation_node_kind(tenant_id,run_id,node_kind),
 FOREIGN KEY(tenant_id,run_id) REFERENCES formal_evaluation_cell(tenant_id,run_id),
 FOREIGN KEY(tenant_id,project_id,run_id,step_id) REFERENCES run_step(tenant_id,project_id,run_id,step_id),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK(node_kind REGEXP '^N0[0-9]$' AND JSON_VALID(canonical_node)),
 CHECK(schema_version='1.0.0' AND node_sha256 REGEXP '^[a-f0-9]{64}$' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK(classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:083-04-professional-host-session dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE professional_runtime_session (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 run_id CHAR(36) CHARACTER SET ascii NOT NULL,step_id CHAR(36) CHARACTER SET ascii NOT NULL,
 lease_generation BIGINT UNSIGNED NOT NULL,session_id CHAR(36) CHARACTER SET ascii NOT NULL,
 action_id CHAR(36) CHARACTER SET ascii NOT NULL,input_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 envelope_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,service_uri VARCHAR(512) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 certificate_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,trust_revision CHAR(36) CHARACTER SET ascii NOT NULL,
 canonical_start LONGTEXT NOT NULL,start_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 admission_state VARCHAR(24) NOT NULL,revision BIGINT UNSIGNED NOT NULL,
 schema_version VARCHAR(32) NOT NULL,schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL,created_at DATETIME(6) NOT NULL,classification VARCHAR(24) NOT NULL,updated_at DATETIME(6) NOT NULL,
 PRIMARY KEY(tenant_id,run_id,step_id,lease_generation),UNIQUE KEY uq_professional_session_id(tenant_id,session_id),
 FOREIGN KEY(tenant_id,project_id,run_id,step_id) REFERENCES run_step(tenant_id,project_id,run_id,step_id),
 FOREIGN KEY(tenant_id,run_id,step_id,action_id) REFERENCES step_action_binding(tenant_id,run_id,step_id,action_id),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK(lease_generation>0 AND revision>0 AND admission_state IN ('OPEN','CLOSED')),
 CHECK(JSON_VALID(canonical_start) AND schema_version='1.0.0' AND start_sha256 REGEXP '^[a-f0-9]{64}$' AND input_sha256 REGEXP '^[a-f0-9]{64}$' AND envelope_sha256 REGEXP '^[a-f0-9]{64}$' AND certificate_sha256 REGEXP '^[a-f0-9]{64}$' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK(classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:083-05-professional-host-observation dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE professional_runtime_observation (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,session_id CHAR(36) CHARACTER SET ascii NOT NULL,
 observation_id CHAR(36) CHARACTER SET ascii NOT NULL,kind VARCHAR(24) NOT NULL,
 canonical_observation LONGTEXT NOT NULL,observation_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL,created_at DATETIME(6) NOT NULL,
 PRIMARY KEY(tenant_id,observation_id),UNIQUE KEY uq_professional_observation(tenant_id,session_id,kind,observation_sha256),
 FOREIGN KEY(tenant_id,session_id) REFERENCES professional_runtime_session(tenant_id,session_id),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK(kind IN ('HOST_FAILURE','HOST_FINISH','ADMISSION_CLOSED') AND JSON_VALID(canonical_observation) AND observation_sha256 REGEXP '^[a-f0-9]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:083-06-evaluation-original-result dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE formal_evaluation_node_result (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 run_id CHAR(36) CHARACTER SET ascii NOT NULL,step_id CHAR(36) CHARACTER SET ascii NOT NULL,lease_generation BIGINT UNSIGNED NOT NULL,
 canonical_record LONGTEXT NOT NULL,record_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 session_id CHAR(36) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(32) NOT NULL,schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL,created_at DATETIME(6) NOT NULL,classification VARCHAR(24) NOT NULL,
 PRIMARY KEY(tenant_id,run_id,step_id),
 FOREIGN KEY(tenant_id,run_id,step_id) REFERENCES formal_evaluation_node(tenant_id,run_id,step_id),
 FOREIGN KEY(tenant_id,session_id) REFERENCES professional_runtime_session(tenant_id,session_id),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK(lease_generation>0 AND JSON_VALID(canonical_record) AND schema_version='1.0.0' AND record_sha256 REGEXP '^[a-f0-9]{64}$' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK(classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
