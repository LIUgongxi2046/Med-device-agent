--liquibase formatted sql
--changeset medical-agent:1.0.0-127-context-read dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE document_review_context_read (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
 project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 run_id CHAR(36) CHARACTER SET ascii NOT NULL,
 step_id CHAR(36) CHARACTER SET ascii NOT NULL,
 session_id CHAR(36) CHARACTER SET ascii NOT NULL,
 input_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 baseline_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
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
 UNIQUE KEY uq_document_review_read_project (tenant_id,project_id,session_id),
 FOREIGN KEY (tenant_id,project_id,run_id,step_id) REFERENCES run_step(tenant_id,project_id,run_id,step_id),
 FOREIGN KEY (tenant_id,audit_id) REFERENCES audit_event(tenant_id,audit_id),
 FOREIGN KEY (tenant_id,event_id) REFERENCES outbox_event(tenant_id,event_id),
 FOREIGN KEY (tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK (JSON_VALID(canonical_receipt) AND schema_version='1.0.0' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK (classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH')),
 CHECK (input_sha256 REGEXP '^[a-f0-9]{64}$' AND baseline_sha256 REGEXP '^[a-f0-9]{64}$' AND receipt_sha256 REGEXP '^[a-f0-9]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE document_review_independent_read (
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
 UNIQUE KEY uq_document_review_session_read (tenant_id,session_id,receipt_id),
 FOREIGN KEY (tenant_id,project_id,session_id) REFERENCES document_review_context_read(tenant_id,project_id,session_id),
 FOREIGN KEY (tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK (schema_version='1.0.0' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK (classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH')),
 CHECK (read_kind IN ('SOURCE','RULE') AND JSON_VALID(canonical_receipt)),
 CHECK (receipt_sha256 REGEXP '^[a-f0-9]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-127-context-read-update dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER imm_document_review_context_read_update BEFORE UPDATE ON document_review_context_read FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IMMUTABLE_N07_RUNTIME_EVIDENCE';

--changeset medical-agent:1.0.0-127-context-read-delete dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER imm_document_review_context_read_delete BEFORE DELETE ON document_review_context_read FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IMMUTABLE_N07_RUNTIME_EVIDENCE';

--changeset medical-agent:1.0.0-127-independent-read-update dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER imm_document_review_independent_read_update BEFORE UPDATE ON document_review_independent_read FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IMMUTABLE_N07_RUNTIME_EVIDENCE';

--changeset medical-agent:1.0.0-127-independent-read-delete dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER imm_document_review_independent_read_delete BEFORE DELETE ON document_review_independent_read FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IMMUTABLE_N07_RUNTIME_EVIDENCE';
