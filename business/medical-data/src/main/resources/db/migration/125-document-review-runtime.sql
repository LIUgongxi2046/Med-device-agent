--liquibase formatted sql
--changeset medical-agent:1.0.0-125-document-review-runtime dbms:mysql runInTransaction:false failOnError:true
-- Typed N07 preparation; F1/F2 receipts continue using existing professional runtime tables.
CREATE TABLE document_review_runtime_prepare (
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
 UNIQUE KEY uq_document_review_prepare_session (tenant_id,session_id),
 FOREIGN KEY (tenant_id,run_id) REFERENCES system_job_run(tenant_id,run_id),
 FOREIGN KEY (tenant_id,project_id,run_id,step_id) REFERENCES run_step(tenant_id,project_id,run_id,step_id),
 FOREIGN KEY (tenant_id,run_id,step_id,action_id) REFERENCES step_action_binding(tenant_id,run_id,step_id,action_id),
 FOREIGN KEY (tenant_id,audit_id) REFERENCES audit_event(tenant_id,audit_id),
 FOREIGN KEY (tenant_id,event_id) REFERENCES outbox_event(tenant_id,event_id),
 FOREIGN KEY (tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK (lease_generation>0 AND schema_version='1.0.0'),
 CHECK (JSON_VALID(envelope_json) AND JSON_VALID(launch_json)),
 CHECK (envelope_sha256 REGEXP '^[a-f0-9]{64}$' AND launch_sha256 REGEXP '^[a-f0-9]{64}$' AND deployment_sha256 REGEXP '^[a-f0-9]{64}$' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK (classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;


--changeset medical-agent:1.0.0-125-document-review-prepare-update dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER imm_document_review_prepare_update BEFORE UPDATE ON document_review_runtime_prepare FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IMMUTABLE_N07_RUNTIME_EVIDENCE';

--changeset medical-agent:1.0.0-125-document-review-prepare-delete dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER imm_document_review_prepare_delete BEFORE DELETE ON document_review_runtime_prepare FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IMMUTABLE_N07_RUNTIME_EVIDENCE';
