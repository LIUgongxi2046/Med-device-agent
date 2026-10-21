--liquibase formatted sql
-- Independent saved-document execution. No existing TASK lifecycle is modified.
--changeset medical-agent:1.0.0-124-0 dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE document_review_execution (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
 project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 run_id CHAR(36) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(16) NOT NULL,
 schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL,
 created_at DATETIME(6) NOT NULL,
 classification VARCHAR(32) NOT NULL,
 intent_id CHAR(36) CHARACTER SET ascii NOT NULL,
 document_id CHAR(36) CHARACTER SET ascii NOT NULL,
 document_revision CHAR(36) CHARACTER SET ascii NOT NULL,
 operation_id CHAR(36) CHARACTER SET ascii NOT NULL,
 admission_json LONGTEXT NOT NULL,
 admission_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 baseline_json LONGTEXT NOT NULL,
 input_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 audit_id CHAR(36) CHARACTER SET ascii NOT NULL,
 event_id CHAR(36) CHARACTER SET ascii NOT NULL,
 UNIQUE KEY uq_document_check_intent(tenant_id,intent_id),
 FOREIGN KEY(tenant_id,intent_id) REFERENCES document_review_intent(tenant_id,intent_id),
 FOREIGN KEY(tenant_id,audit_id) REFERENCES audit_event(tenant_id,audit_id),
 FOREIGN KEY(tenant_id,event_id) REFERENCES outbox_event(tenant_id,event_id),
 CHECK(JSON_VALID(admission_json) AND JSON_VALID(baseline_json)),
 PRIMARY KEY(tenant_id,run_id),
 FOREIGN KEY(tenant_id,run_id) REFERENCES system_job_run(tenant_id,run_id),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK(schema_version='1.0.0' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK(classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
--changeset medical-agent:1.0.0-124-0-update dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER imm_n07_0_update BEFORE UPDATE ON document_review_execution FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IMMUTABLE_DOCUMENT_CHECK_EXECUTION';
--changeset medical-agent:1.0.0-124-0-delete dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER imm_n07_0_delete BEFORE DELETE ON document_review_execution FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IMMUTABLE_DOCUMENT_CHECK_EXECUTION';
--changeset medical-agent:1.0.0-124-1 dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE document_review_launch_intent (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
 project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 run_id CHAR(36) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(16) NOT NULL,
 schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL,
 created_at DATETIME(6) NOT NULL,
 classification VARCHAR(32) NOT NULL,
 command_id CHAR(36) CHARACTER SET ascii NOT NULL,
 reference_json LONGTEXT NOT NULL,
 reference_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 configuration_json LONGTEXT NOT NULL,
 configuration_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 UNIQUE KEY uq_document_check_command(tenant_id,command_id),
 CHECK(JSON_VALID(reference_json) AND JSON_VALID(configuration_json)),
 PRIMARY KEY(tenant_id,run_id),
 FOREIGN KEY(tenant_id,run_id) REFERENCES system_job_run(tenant_id,run_id),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK(schema_version='1.0.0' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK(classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
--changeset medical-agent:1.0.0-124-1-update dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER imm_n07_1_update BEFORE UPDATE ON document_review_launch_intent FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IMMUTABLE_DOCUMENT_CHECK_EXECUTION';
--changeset medical-agent:1.0.0-124-1-delete dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER imm_n07_1_delete BEFORE DELETE ON document_review_launch_intent FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IMMUTABLE_DOCUMENT_CHECK_EXECUTION';
--changeset medical-agent:1.0.0-124-2 dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE document_review_launch_attempt (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
 project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 run_id CHAR(36) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(16) NOT NULL,
 schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL,
 created_at DATETIME(6) NOT NULL,
 classification VARCHAR(32) NOT NULL,
 command_id CHAR(36) CHARACTER SET ascii NOT NULL,
 reference_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 PRIMARY KEY(tenant_id,run_id),
 FOREIGN KEY(tenant_id,run_id) REFERENCES system_job_run(tenant_id,run_id),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK(schema_version='1.0.0' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK(classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
--changeset medical-agent:1.0.0-124-2-update dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER imm_n07_2_update BEFORE UPDATE ON document_review_launch_attempt FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IMMUTABLE_DOCUMENT_CHECK_EXECUTION';
--changeset medical-agent:1.0.0-124-2-delete dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER imm_n07_2_delete BEFORE DELETE ON document_review_launch_attempt FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IMMUTABLE_DOCUMENT_CHECK_EXECUTION';
--changeset medical-agent:1.0.0-124-3 dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE document_review_launch_receipt (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
 project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 run_id CHAR(36) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(16) NOT NULL,
 schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL,
 created_at DATETIME(6) NOT NULL,
 classification VARCHAR(32) NOT NULL,
 command_id CHAR(36) CHARACTER SET ascii NOT NULL,
 reference_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 workflow_id VARCHAR(128) CHARACTER SET ascii NOT NULL,
 temporal_run_id CHAR(36) CHARACTER SET ascii NOT NULL,
 receipt_json LONGTEXT NOT NULL,
 receipt_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 UNIQUE KEY uq_document_check_workflow(tenant_id,workflow_id),
 CHECK(JSON_VALID(receipt_json)),
 PRIMARY KEY(tenant_id,run_id),
 FOREIGN KEY(tenant_id,run_id) REFERENCES system_job_run(tenant_id,run_id),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK(schema_version='1.0.0' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK(classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
--changeset medical-agent:1.0.0-124-3-update dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER imm_n07_3_update BEFORE UPDATE ON document_review_launch_receipt FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IMMUTABLE_DOCUMENT_CHECK_EXECUTION';
--changeset medical-agent:1.0.0-124-3-delete dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER imm_n07_3_delete BEFORE DELETE ON document_review_launch_receipt FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IMMUTABLE_DOCUMENT_CHECK_EXECUTION';
--changeset medical-agent:1.0.0-124-completion dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE document_review_completion (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
 project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 run_id CHAR(36) CHARACTER SET ascii NOT NULL,
 input_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 receipt_json LONGTEXT NOT NULL,
 receipt_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(16) NOT NULL,
 schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL,
 created_at DATETIME(6) NOT NULL,
 classification VARCHAR(32) NOT NULL,
 PRIMARY KEY(tenant_id,run_id),
 FOREIGN KEY(tenant_id,run_id) REFERENCES system_job_run(tenant_id,run_id),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK(JSON_VALID(receipt_json)),
 CHECK(schema_version='1.0.0' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK(classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
--changeset medical-agent:1.0.0-124-completion-update dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER imm_n07_completion_update BEFORE UPDATE ON document_review_completion FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IMMUTABLE_DOCUMENT_CHECK_COMPLETION';
--changeset medical-agent:1.0.0-124-completion-delete dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER imm_n07_completion_delete BEFORE DELETE ON document_review_completion FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IMMUTABLE_DOCUMENT_CHECK_COMPLETION';
