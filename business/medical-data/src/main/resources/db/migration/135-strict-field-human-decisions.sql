--liquibase formatted sql
--changeset medical-agent:1.0.0-135-strict-field-human-decisions dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE strict_field_human_decision (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 decision_id CHAR(36) CHARACTER SET ascii NOT NULL, document_id CHAR(36) CHARACTER SET ascii NOT NULL,
 document_revision CHAR(36) CHARACTER SET ascii NOT NULL, field_id CHAR(36) CHARACTER SET ascii NOT NULL,
 row_key VARCHAR(36) CHARACTER SET ascii NOT NULL, revision BIGINT UNSIGNED NOT NULL,
 actor_id CHAR(36) CHARACTER SET ascii NOT NULL, operation_id CHAR(36) CHARACTER SET ascii NOT NULL,
 idempotency_key VARCHAR(128) CHARACTER SET ascii NOT NULL, request_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 request_json JSON NOT NULL, baseline_json JSON NOT NULL, baseline_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 receipt_json JSON NOT NULL, receipt_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 auth_epoch BIGINT UNSIGNED NOT NULL, governance_epoch BIGINT UNSIGNED NOT NULL,
 schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
 PRIMARY KEY(tenant_id,project_id,decision_id), UNIQUE KEY uq_strict_field_decision_operation(tenant_id,actor_id,idempotency_key),
 UNIQUE KEY uq_strict_field_decision_revision(tenant_id,project_id,document_revision,field_id,row_key,revision),
 FOREIGN KEY(tenant_id,project_id,document_id) REFERENCES document_head(tenant_id,project_id,document_id),
 FOREIGN KEY(tenant_id,project_id,document_revision,field_id,row_key) REFERENCES field_value(tenant_id,project_id,document_revision,field_id,row_key),
 FOREIGN KEY(tenant_id,decision_id) REFERENCES version_registry(tenant_id,version_id),
 FOREIGN KEY(tenant_id,operation_id) REFERENCES operation_record(tenant_id,operation_id),
 FOREIGN KEY(tenant_id,actor_id) REFERENCES tenant_member(tenant_id,user_id),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK(schema_version='1.0.0' AND revision>0 AND created_by=actor_id),
 CHECK(classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH')),
 CHECK(REGEXP_LIKE(schema_manifest_sha256,'^[0-9a-f]{64}$','c') AND REGEXP_LIKE(request_sha256,'^[0-9a-f]{64}$','c') AND REGEXP_LIKE(baseline_sha256,'^[0-9a-f]{64}$','c') AND REGEXP_LIKE(receipt_sha256,'^[0-9a-f]{64}$','c'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
--changeset medical-agent:1.0.0-135-strict-field-human-head dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE strict_field_human_head (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 document_revision CHAR(36) CHARACTER SET ascii NOT NULL, field_id CHAR(36) CHARACTER SET ascii NOT NULL,
 row_key VARCHAR(36) CHARACTER SET ascii NOT NULL, revision BIGINT UNSIGNED NOT NULL, decision_id CHAR(36) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
 PRIMARY KEY(tenant_id,project_id,document_revision,field_id,row_key),
 FOREIGN KEY(tenant_id,project_id,document_revision,field_id,row_key) REFERENCES field_value(tenant_id,project_id,document_revision,field_id,row_key),
 FOREIGN KEY(tenant_id,project_id,decision_id) REFERENCES strict_field_human_decision(tenant_id,project_id,decision_id),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id), CHECK(schema_version='1.0.0' AND revision>0),
 CHECK(classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH')),
 CHECK(REGEXP_LIKE(schema_manifest_sha256,'^[0-9a-f]{64}$','c'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
--changeset medical-agent:1.0.0-135-strict-field-evidence-question dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE strict_field_evidence_question (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 question_id CHAR(36) CHARACTER SET ascii NOT NULL, decision_id CHAR(36) CHARACTER SET ascii NOT NULL,
 document_revision CHAR(36) CHARACTER SET ascii NOT NULL, question TEXT NOT NULL,
 assignee_id CHAR(36) CHARACTER SET ascii NOT NULL, status VARCHAR(24) NOT NULL,
 schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
 PRIMARY KEY(tenant_id,project_id,question_id),
 FOREIGN KEY(tenant_id,project_id,decision_id) REFERENCES strict_field_human_decision(tenant_id,project_id,decision_id),
 FOREIGN KEY(tenant_id,project_id,document_revision) REFERENCES document_revision_meta(tenant_id,project_id,document_revision),
 FOREIGN KEY(tenant_id,assignee_id) REFERENCES tenant_member(tenant_id,user_id),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK(schema_version='1.0.0' AND status='OPEN' AND created_by=assignee_id AND CHAR_LENGTH(TRIM(question))>0),
 CHECK(classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH')),
 CHECK(REGEXP_LIKE(schema_manifest_sha256,'^[0-9a-f]{64}$','c'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-135-strict_field_human_decision-update dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER imm_strict_field_human_decision_update BEFORE UPDATE ON strict_field_human_decision FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IMMUTABLE_STRICT_FIELD_HUMAN_DECISION';

--changeset medical-agent:1.0.0-135-strict_field_human_decision-delete dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER imm_strict_field_human_decision_delete BEFORE DELETE ON strict_field_human_decision FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IMMUTABLE_STRICT_FIELD_HUMAN_DECISION';

--changeset medical-agent:1.0.0-135-strict_field_evidence_question-update dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER imm_strict_field_evidence_question_update BEFORE UPDATE ON strict_field_evidence_question FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IMMUTABLE_STRICT_FIELD_HUMAN_DECISION';

--changeset medical-agent:1.0.0-135-strict_field_evidence_question-delete dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER imm_strict_field_evidence_question_delete BEFORE DELETE ON strict_field_evidence_question FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IMMUTABLE_STRICT_FIELD_HUMAN_DECISION';
