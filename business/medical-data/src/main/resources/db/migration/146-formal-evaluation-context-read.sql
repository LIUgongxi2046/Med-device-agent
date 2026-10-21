--liquibase formatted sql
-- Actual complete EVAL physical reads only. No approvals, tokens or result rows are seeded.
--changeset medical-agent:146-01-context-read dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE formal_evaluation_context_read (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 run_id CHAR(36) CHARACTER SET ascii NOT NULL,step_id CHAR(36) CHARACTER SET ascii NOT NULL,
 receipt_id CHAR(36) CHARACTER SET ascii NOT NULL,node_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 canonical_receipt LONGTEXT NOT NULL,receipt_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 audit_id CHAR(36) CHARACTER SET ascii NOT NULL,event_id CHAR(36) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(32) NOT NULL,schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL,created_at DATETIME(6) NOT NULL,classification VARCHAR(24) NOT NULL,
 PRIMARY KEY(tenant_id,receipt_id),UNIQUE KEY uq_eval_context_exact(tenant_id,receipt_id,receipt_sha256),
 FOREIGN KEY(tenant_id,project_id,run_id) REFERENCES formal_evaluation_job(tenant_id,project_id,run_id),
 FOREIGN KEY(tenant_id,run_id,step_id) REFERENCES formal_evaluation_node(tenant_id,run_id,step_id),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 FOREIGN KEY(tenant_id,audit_id) REFERENCES audit_event(tenant_id,audit_id),
 FOREIGN KEY(tenant_id,event_id) REFERENCES outbox_event(tenant_id,event_id),
 CHECK(JSON_VALID(canonical_receipt) AND receipt_sha256 REGEXP '^[a-f0-9]{64}$' AND node_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK(schema_version='1.0.0' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK(classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
--changeset medical-agent:146-02-context-read-immutable-update dbms:mysql runInTransaction:false failOnError:true splitStatements:false
CREATE TRIGGER formal_evaluation_context_read_no_update BEFORE UPDATE ON formal_evaluation_context_read FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='EVAL_CONTEXT_READ_IMMUTABLE';
--changeset medical-agent:146-03-context-read-immutable-delete dbms:mysql runInTransaction:false failOnError:true splitStatements:false
CREATE TRIGGER formal_evaluation_context_read_no_delete BEFORE DELETE ON formal_evaluation_context_read FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='EVAL_CONTEXT_READ_IMMUTABLE';
