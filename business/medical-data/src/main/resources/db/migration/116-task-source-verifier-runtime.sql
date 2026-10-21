--liquibase formatted sql
--changeset medical-agent:1.0.0-116-task-source-verifier-runtime dbms:mysql runInTransaction:false failOnError:true
-- Typed N05 preparation; F1/F2 receipts continue using existing professional runtime tables.
CREATE TABLE task_source_verifier_runtime_prepare (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
 project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 run_id CHAR(36) CHARACTER SET ascii NOT NULL,
 step_id CHAR(36) CHARACTER SET ascii NOT NULL,
 action_id CHAR(36) CHARACTER SET ascii NOT NULL,
 lease_generation BIGINT UNSIGNED NOT NULL,
 session_id CHAR(36) CHARACTER SET ascii NOT NULL,
 input_artifact_id CHAR(36) CHARACTER SET ascii NOT NULL,
 input_receipt_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
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
 UNIQUE KEY uq_source_verifier_prepare_session (tenant_id,session_id),
 FOREIGN KEY (tenant_id,run_id) REFERENCES task_run(tenant_id,run_id),
 FOREIGN KEY (tenant_id,project_id,run_id,step_id) REFERENCES run_step(tenant_id,project_id,run_id,step_id),
 FOREIGN KEY (tenant_id,run_id,step_id,action_id) REFERENCES step_action_binding(tenant_id,run_id,step_id,action_id),
 FOREIGN KEY (tenant_id,input_artifact_id) REFERENCES artifact(tenant_id,artifact_id),
 FOREIGN KEY (tenant_id,audit_id) REFERENCES audit_event(tenant_id,audit_id),
 FOREIGN KEY (tenant_id,event_id) REFERENCES outbox_event(tenant_id,event_id),
 FOREIGN KEY (tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK (lease_generation>0 AND schema_version='1.0.0'),
 CHECK (JSON_VALID(envelope_json) AND JSON_VALID(launch_json)),
 CHECK (input_receipt_sha256 REGEXP '^[a-f0-9]{64}$' AND envelope_sha256 REGEXP '^[a-f0-9]{64}$' AND launch_sha256 REGEXP '^[a-f0-9]{64}$' AND deployment_sha256 REGEXP '^[a-f0-9]{64}$' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK (classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

-- An independent physical-read journal is written only after hash-checked object GETs.
-- Session identity is allocated before process admission, so this deliberately references
-- the N05 input rather than requiring an already-started professional runtime session.
CREATE TABLE task_source_verifier_context_read (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
 project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 run_id CHAR(36) CHARACTER SET ascii NOT NULL,
 step_id CHAR(36) CHARACTER SET ascii NOT NULL,
 session_id CHAR(36) CHARACTER SET ascii NOT NULL,
 input_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 input_receipt_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 canonical_receipt LONGTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
 receipt_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 audit_id CHAR(36) CHARACTER SET ascii NOT NULL,
 event_id CHAR(36) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(32) NOT NULL,
 schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 classification VARCHAR(24) NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL,
 created_at DATETIME(6) NOT NULL,
 PRIMARY KEY (tenant_id,session_id),
 UNIQUE KEY uq_verifier_read_project (tenant_id,project_id,session_id),
 FOREIGN KEY (tenant_id,project_id,run_id,step_id) REFERENCES run_step(tenant_id,project_id,run_id,step_id),
 FOREIGN KEY (tenant_id,audit_id) REFERENCES audit_event(tenant_id,audit_id),
 FOREIGN KEY (tenant_id,event_id) REFERENCES outbox_event(tenant_id,event_id),
 FOREIGN KEY (tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK (JSON_VALID(canonical_receipt) AND schema_version='1.0.0' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK (classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH')),
 CHECK (input_sha256 REGEXP '^[a-f0-9]{64}$' AND input_receipt_sha256 REGEXP '^[a-f0-9]{64}$' AND receipt_sha256 REGEXP '^[a-f0-9]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE task_source_verifier_independent_read (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
 project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 session_id CHAR(36) CHARACTER SET ascii NOT NULL,
 receipt_id CHAR(36) CHARACTER SET ascii NOT NULL,
 read_kind VARCHAR(8) CHARACTER SET ascii NOT NULL,
 canonical_receipt LONGTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
 receipt_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(32) NOT NULL,
 schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL,
 created_at DATETIME(6) NOT NULL,
 classification VARCHAR(24) NOT NULL,
 PRIMARY KEY (tenant_id,receipt_id),
 UNIQUE KEY uq_verifier_session_read (tenant_id,session_id,receipt_id),
 FOREIGN KEY (tenant_id,project_id,session_id) REFERENCES task_source_verifier_context_read(tenant_id,project_id,session_id),
 FOREIGN KEY (tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK (schema_version='1.0.0' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK (classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH')),
 CHECK (read_kind IN ('SOURCE','RULE') AND JSON_VALID(canonical_receipt)),
 CHECK (receipt_sha256 REGEXP '^[a-f0-9]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-116-prepare-update dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER imm_verifier_prepare_update BEFORE UPDATE ON task_source_verifier_runtime_prepare FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IMMUTABLE_N05_RUNTIME_EVIDENCE';

--changeset medical-agent:1.0.0-116-prepare-delete dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER imm_verifier_prepare_delete BEFORE DELETE ON task_source_verifier_runtime_prepare FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IMMUTABLE_N05_RUNTIME_EVIDENCE';

--changeset medical-agent:1.0.0-116-context-read-update dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER imm_verifier_context_read_update BEFORE UPDATE ON task_source_verifier_context_read FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IMMUTABLE_N05_RUNTIME_EVIDENCE';

--changeset medical-agent:1.0.0-116-context-read-delete dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER imm_verifier_context_read_delete BEFORE DELETE ON task_source_verifier_context_read FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IMMUTABLE_N05_RUNTIME_EVIDENCE';

--changeset medical-agent:1.0.0-116-independent-read-update dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER imm_verifier_independent_read_update BEFORE UPDATE ON task_source_verifier_independent_read FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IMMUTABLE_N05_RUNTIME_EVIDENCE';

--changeset medical-agent:1.0.0-116-independent-read-delete dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER imm_verifier_independent_read_delete BEFORE DELETE ON task_source_verifier_independent_read FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IMMUTABLE_N05_RUNTIME_EVIDENCE';
