--liquibase formatted sql
--changeset medical-agent:1.0.0-134-human-evidence-review dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE human_evidence_batch (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 batch_id CHAR(36) CHARACTER SET ascii NOT NULL, document_id CHAR(36) CHARACTER SET ascii NOT NULL,
 document_revision CHAR(36) CHARACTER SET ascii NOT NULL, actor_id CHAR(36) CHARACTER SET ascii NOT NULL,
 operation_id CHAR(36) CHARACTER SET ascii NOT NULL, idempotency_key VARCHAR(128) CHARACTER SET ascii NOT NULL,
 request_sha256 CHAR(64) CHARACTER SET ascii NOT NULL, request_json JSON NOT NULL,
 baseline_json JSON NOT NULL, baseline_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 receipt_json JSON NOT NULL, receipt_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 evidence_watermark BIGINT UNSIGNED NOT NULL, issue_watermark BIGINT UNSIGNED NOT NULL,
 resulting_revision BIGINT UNSIGNED NOT NULL,
 schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
 PRIMARY KEY(tenant_id,project_id,batch_id), UNIQUE KEY uq_human_evidence_operation(tenant_id,actor_id,idempotency_key),
 FOREIGN KEY(tenant_id,project_id,document_revision) REFERENCES document_revision_meta(tenant_id,project_id,document_revision),
 FOREIGN KEY(tenant_id,project_id,document_id) REFERENCES document_head(tenant_id,project_id,document_id),
 FOREIGN KEY(tenant_id,actor_id) REFERENCES tenant_member(tenant_id,user_id),
 FOREIGN KEY(tenant_id,operation_id) REFERENCES operation_record(tenant_id,operation_id),
 CHECK(schema_version='1.0.0' AND created_by=actor_id),
 CHECK(classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH')),
 CHECK(schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$' AND request_sha256 REGEXP '^[a-f0-9]{64}$' AND baseline_sha256 REGEXP '^[a-f0-9]{64}$' AND receipt_sha256 REGEXP '^[a-f0-9]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
--changeset medical-agent:1.0.0-134-human-evidence-decision dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE human_evidence_decision (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 decision_id CHAR(36) CHARACTER SET ascii NOT NULL, batch_id CHAR(36) CHARACTER SET ascii NOT NULL,
 document_revision CHAR(36) CHARACTER SET ascii NOT NULL, evidence_link_id CHAR(36) CHARACTER SET ascii NOT NULL,
 decision_json JSON NOT NULL, decision_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
 PRIMARY KEY(tenant_id,project_id,decision_id), UNIQUE KEY uq_human_evidence_link(tenant_id,project_id,batch_id,evidence_link_id),
 FOREIGN KEY(tenant_id,project_id,batch_id) REFERENCES human_evidence_batch(tenant_id,project_id,batch_id),
 -- evidence_link_id is the logical body link, not the per-node normalized evidence_id.
 -- The service verifies every actual body/projection link under this exact revision.
 FOREIGN KEY(tenant_id,project_id,document_revision) REFERENCES document_revision_meta(tenant_id,project_id,document_revision),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id), CHECK(schema_version='1.0.0'),
 CHECK(classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH')),
 CHECK(schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$' AND decision_sha256 REGEXP '^[a-f0-9]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-134-human_evidence_batch-update dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER imm_human_evidence_batch_update BEFORE UPDATE ON human_evidence_batch FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IMMUTABLE_HUMAN_EVIDENCE';

--changeset medical-agent:1.0.0-134-human_evidence_batch-delete dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER imm_human_evidence_batch_delete BEFORE DELETE ON human_evidence_batch FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IMMUTABLE_HUMAN_EVIDENCE';

--changeset medical-agent:1.0.0-134-human_evidence_decision-update dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER imm_human_evidence_decision_update BEFORE UPDATE ON human_evidence_decision FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IMMUTABLE_HUMAN_EVIDENCE';

--changeset medical-agent:1.0.0-134-human_evidence_decision-delete dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER imm_human_evidence_decision_delete BEFORE DELETE ON human_evidence_decision FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IMMUTABLE_HUMAN_EVIDENCE';
