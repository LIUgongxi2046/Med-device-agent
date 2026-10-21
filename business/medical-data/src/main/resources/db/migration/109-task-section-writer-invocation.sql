--liquibase formatted sql
-- Fresh-install correction: tenant-scoped run UUID is the parent identity of the following journals.
--changeset medical-agent:1.0.0-109-task-run-tenant-identity dbms:mysql runInTransaction:false failOnError:true
ALTER TABLE task_run ADD UNIQUE KEY uq_task_run_tenant_identity (tenant_id,run_id);

--changeset medical-agent:1.0.0-109-section-writer-invocation dbms:mysql runInTransaction:false failOnError:true
-- N04 exact input producer journal. These records are not model-send permissions.
CREATE TABLE task_section_writer_exact_intent (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NOT NULL, run_id CHAR(36) CHARACTER SET ascii NOT NULL,
 step_id CHAR(36) CHARACTER SET ascii NOT NULL, write_key CHAR(64) CHARACTER SET ascii NOT NULL, write_id CHAR(36) CHARACTER SET ascii NOT NULL,
 artifact_id CHAR(36) CHARACTER SET ascii NOT NULL, payload LONGTEXT NOT NULL, intent_json LONGTEXT NOT NULL,
 intent_sha256 CHAR(64) CHARACTER SET ascii NOT NULL, created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL,
 classification VARCHAR(24) NOT NULL, schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 PRIMARY KEY(tenant_id,write_key),
 UNIQUE KEY uq_writer_exact_write(tenant_id,write_id), UNIQUE KEY uq_writer_exact_artifact(tenant_id,artifact_id),
 UNIQUE KEY uq_writer_exact_owner(tenant_id,project_id,run_id,write_id),
 UNIQUE KEY uq_writer_exact_step(tenant_id,run_id,step_id),
 FOREIGN KEY(tenant_id,project_id,run_id,step_id) REFERENCES run_step(tenant_id,project_id,run_id,step_id),
 CHECK(JSON_VALID(payload) AND JSON_VALID(intent_json) AND intent_sha256 REGEXP '^[a-f0-9]{64}$'),
 FOREIGN KEY(tenant_id,run_id) REFERENCES task_run(tenant_id,run_id),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK(schema_version='1.0.0' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK(classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
--changeset medical-agent:1.0.0-109-exact-attempt dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE task_section_writer_exact_attempt (
 project_id CHAR(36) CHARACTER SET ascii NOT NULL, run_id CHAR(36) CHARACTER SET ascii NOT NULL,
 classification VARCHAR(24) NOT NULL,
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, write_id CHAR(36) CHARACTER SET ascii NOT NULL, intent_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 PRIMARY KEY(tenant_id,write_id),
 UNIQUE KEY uq_writer_exact_attempt_owner(tenant_id,project_id,run_id,write_id),
 FOREIGN KEY(tenant_id,project_id,run_id,write_id) REFERENCES task_section_writer_exact_intent(tenant_id,project_id,run_id,write_id),
 FOREIGN KEY(tenant_id,run_id) REFERENCES task_run(tenant_id,run_id),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK(schema_version='1.0.0' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK(classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
--changeset medical-agent:1.0.0-109-exact-registration dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE task_section_writer_exact_registration (
 project_id CHAR(36) CHARACTER SET ascii NOT NULL, run_id CHAR(36) CHARACTER SET ascii NOT NULL,
 classification VARCHAR(24) NOT NULL,
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, write_id CHAR(36) CHARACTER SET ascii NOT NULL, artifact_id CHAR(36) CHARACTER SET ascii NOT NULL,
 receipt_json LONGTEXT NOT NULL, receipt_sha256 CHAR(64) CHARACTER SET ascii NOT NULL, created_by CHAR(36) CHARACTER SET ascii NOT NULL,
 created_at DATETIME(6) NOT NULL, schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 PRIMARY KEY(tenant_id,write_id), UNIQUE KEY uq_writer_exact_registered(tenant_id,artifact_id),
 FOREIGN KEY(tenant_id,project_id,run_id,write_id) REFERENCES task_section_writer_exact_attempt(tenant_id,project_id,run_id,write_id),
 FOREIGN KEY(tenant_id,artifact_id) REFERENCES artifact(tenant_id,artifact_id),
 CHECK(JSON_VALID(receipt_json) AND receipt_sha256 REGEXP '^[a-f0-9]{64}$'),
 FOREIGN KEY(tenant_id,run_id) REFERENCES task_run(tenant_id,run_id),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK(schema_version='1.0.0' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK(classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-109-task_section_writer_exact_intent-update dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER imm_writer_exact_intent_update BEFORE UPDATE ON task_section_writer_exact_intent FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IMMUTABLE_N04_EXACT_INPUT';

--changeset medical-agent:1.0.0-109-task_section_writer_exact_intent-delete dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER imm_writer_exact_intent_delete BEFORE DELETE ON task_section_writer_exact_intent FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IMMUTABLE_N04_EXACT_INPUT';

--changeset medical-agent:1.0.0-109-task_section_writer_exact_attempt-update dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER imm_writer_exact_attempt_update BEFORE UPDATE ON task_section_writer_exact_attempt FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IMMUTABLE_N04_EXACT_INPUT';

--changeset medical-agent:1.0.0-109-task_section_writer_exact_attempt-delete dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER imm_writer_exact_attempt_delete BEFORE DELETE ON task_section_writer_exact_attempt FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IMMUTABLE_N04_EXACT_INPUT';

--changeset medical-agent:1.0.0-109-task_section_writer_exact_registration-update dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER imm_writer_exact_registration_update BEFORE UPDATE ON task_section_writer_exact_registration FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IMMUTABLE_N04_EXACT_INPUT';

--changeset medical-agent:1.0.0-109-task_section_writer_exact_registration-delete dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER imm_writer_exact_registration_delete BEFORE DELETE ON task_section_writer_exact_registration FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IMMUTABLE_N04_EXACT_INPUT';
