--liquibase formatted sql
-- Saved-document N07 intent. This is not a candidate check and never reopens a TASK.
--changeset medical-agent:1.0.0-122-intent dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE document_review_intent (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
 project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 intent_id CHAR(36) CHARACTER SET ascii NOT NULL,
 operation_id CHAR(36) CHARACTER SET ascii NOT NULL,
 document_id CHAR(36) CHARACTER SET ascii NOT NULL,
 document_revision CHAR(36) CHARACTER SET ascii NOT NULL,
 intent_json LONGTEXT NOT NULL,
 intent_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 audit_id CHAR(36) CHARACTER SET ascii NOT NULL,
 event_id CHAR(36) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(16) NOT NULL,
 schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL,
 created_at DATETIME(6) NOT NULL,
 classification VARCHAR(32) NOT NULL,
 PRIMARY KEY(tenant_id,intent_id),
 UNIQUE KEY uq_document_review_origin(tenant_id,operation_id),
 FOREIGN KEY(tenant_id,project_id,document_id,document_revision) REFERENCES document_revision_meta(tenant_id,project_id,document_id,document_revision),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK(JSON_VALID(intent_json)),
 CHECK(intent_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK(schema_version='1.0.0' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
--changeset medical-agent:1.0.0-122-intent-update dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER imm_document_review_intent_update BEFORE UPDATE ON document_review_intent FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IMMUTABLE_N07_ORIGINAL_OPERATION';
--changeset medical-agent:1.0.0-122-intent-delete dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER imm_document_review_intent_delete BEFORE DELETE ON document_review_intent FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IMMUTABLE_N07_ORIGINAL_OPERATION';

--changeset medical-agent:1.0.0-122-object-intent dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE document_review_object_intent (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 run_id CHAR(36) CHARACTER SET ascii NOT NULL, step_id CHAR(36) CHARACTER SET ascii NOT NULL, action_id CHAR(36) CHARACTER SET ascii NOT NULL,
 write_id CHAR(36) CHARACTER SET ascii NOT NULL, artifact_id CHAR(36) CHARACTER SET ascii NOT NULL,
 kind VARCHAR(32) NOT NULL, object_key VARCHAR(1024) NOT NULL,
 payload_sha256 CHAR(64) CHARACTER SET ascii NOT NULL, canonical_payload LONGTEXT NOT NULL,
 intent_json LONGTEXT NOT NULL, intent_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(16) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(32) NOT NULL,
 PRIMARY KEY(tenant_id,write_id), UNIQUE KEY uq_n07_object_stage(tenant_id,run_id,step_id,kind), UNIQUE KEY uq_n07_object_artifact(tenant_id,artifact_id),
 FOREIGN KEY(tenant_id,run_id,step_id,action_id) REFERENCES step_action_binding(tenant_id,run_id,step_id,action_id),
 FOREIGN KEY(tenant_id,project_id) REFERENCES project(tenant_id,project_id), FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK(kind IN ('REVIEW_FINDINGS','REVIEW_COVERAGE')), CHECK(JSON_VALID(canonical_payload) AND JSON_VALID(intent_json)),
 CHECK(payload_sha256 REGEXP '^[a-f0-9]{64}$' AND intent_sha256 REGEXP '^[a-f0-9]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
--changeset medical-agent:1.0.0-122-object-attempt dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE document_review_object_attempt (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 write_id CHAR(36) CHARACTER SET ascii NOT NULL, intent_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(16) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(32) NOT NULL,
 PRIMARY KEY(tenant_id,write_id), FOREIGN KEY(tenant_id,write_id) REFERENCES document_review_object_intent(tenant_id,write_id),
 FOREIGN KEY(tenant_id,project_id) REFERENCES project(tenant_id,project_id), FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
--changeset medical-agent:1.0.0-122-object-registered dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE document_review_object (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 run_id CHAR(36) CHARACTER SET ascii NOT NULL, step_id CHAR(36) CHARACTER SET ascii NOT NULL, action_id CHAR(36) CHARACTER SET ascii NOT NULL,
 write_id CHAR(36) CHARACTER SET ascii NOT NULL, artifact_id CHAR(36) CHARACTER SET ascii NOT NULL, kind VARCHAR(32) NOT NULL,
 receipt_json LONGTEXT NOT NULL, receipt_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(16) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(32) NOT NULL,
 PRIMARY KEY(tenant_id,artifact_id), UNIQUE KEY uq_n07_registered_stage(tenant_id,run_id,step_id,kind),
 FOREIGN KEY(tenant_id,write_id) REFERENCES document_review_object_intent(tenant_id,write_id),
 FOREIGN KEY(tenant_id,artifact_id) REFERENCES artifact(tenant_id,artifact_id),
 FOREIGN KEY(tenant_id,project_id) REFERENCES project(tenant_id,project_id), FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK(kind IN ('REVIEW_FINDINGS','REVIEW_COVERAGE')), CHECK(JSON_VALID(receipt_json)), CHECK(receipt_sha256 REGEXP '^[a-f0-9]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
--changeset medical-agent:1.0.0-122-result dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE document_review_result (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 run_id CHAR(36) CHARACTER SET ascii NOT NULL, step_id CHAR(36) CHARACTER SET ascii NOT NULL, action_id CHAR(36) CHARACTER SET ascii NOT NULL,
 check_id CHAR(36) CHARACTER SET ascii NOT NULL, receipt_id CHAR(36) CHARACTER SET ascii NOT NULL,
 findings_artifact_id CHAR(36) CHARACTER SET ascii NOT NULL, coverage_artifact_id CHAR(36) CHARACTER SET ascii NOT NULL,
 receipt_json LONGTEXT NOT NULL, receipt_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(16) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(32) NOT NULL,
 PRIMARY KEY(tenant_id,run_id,step_id,action_id), UNIQUE KEY uq_n07_receipt(tenant_id,receipt_id),
 FOREIGN KEY(tenant_id,project_id,check_id) REFERENCES check_run(tenant_id,project_id,check_id),
 FOREIGN KEY(tenant_id,findings_artifact_id) REFERENCES document_review_object(tenant_id,artifact_id),
 FOREIGN KEY(tenant_id,coverage_artifact_id) REFERENCES document_review_object(tenant_id,artifact_id),
 CHECK(JSON_VALID(receipt_json)), CHECK(receipt_sha256 REGEXP '^[a-f0-9]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-122-object_intent-update dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER imm_n07_object_intent_update BEFORE UPDATE ON document_review_object_intent FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IMMUTABLE_N07_RECEIPT';

--changeset medical-agent:1.0.0-122-object_intent-delete dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER imm_n07_object_intent_delete BEFORE DELETE ON document_review_object_intent FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IMMUTABLE_N07_RECEIPT';

--changeset medical-agent:1.0.0-122-object_attempt-update dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER imm_n07_object_attempt_update BEFORE UPDATE ON document_review_object_attempt FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IMMUTABLE_N07_RECEIPT';

--changeset medical-agent:1.0.0-122-object_attempt-delete dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER imm_n07_object_attempt_delete BEFORE DELETE ON document_review_object_attempt FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IMMUTABLE_N07_RECEIPT';

--changeset medical-agent:1.0.0-122-object-update dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER imm_n07_object_update BEFORE UPDATE ON document_review_object FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IMMUTABLE_N07_RECEIPT';

--changeset medical-agent:1.0.0-122-object-delete dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER imm_n07_object_delete BEFORE DELETE ON document_review_object FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IMMUTABLE_N07_RECEIPT';

--changeset medical-agent:1.0.0-122-result-update dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER imm_n07_result_update BEFORE UPDATE ON document_review_result FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IMMUTABLE_N07_RECEIPT';

--changeset medical-agent:1.0.0-122-result-delete dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER imm_n07_result_delete BEFORE DELETE ON document_review_result FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IMMUTABLE_N07_RECEIPT';
