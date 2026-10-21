--liquibase formatted sql
-- Dedicated approved case originals and predispatch EVAL input. No TASK/SIM rows or placeholder sessions.
--changeset medical-agent:152-10-case-original dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE formal_evaluation_case_original (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 run_id CHAR(36) CHARACTER SET ascii NOT NULL,step_id CHAR(36) CHARACTER SET ascii NOT NULL,
 registration_id CHAR(36) CHARACTER SET ascii NOT NULL,artifact_id CHAR(36) CHARACTER SET ascii NOT NULL,
 artifact_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,canonical_original LONGTEXT NOT NULL,
 canonical_receipt LONGTEXT NOT NULL,receipt_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL,created_at DATETIME(6) NOT NULL,classification VARCHAR(24) NOT NULL,
 schema_version VARCHAR(32) NOT NULL,schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 PRIMARY KEY(tenant_id,registration_id),UNIQUE KEY uq_eval_case_node(tenant_id,run_id,step_id),
 UNIQUE KEY uq_eval_case_owner(tenant_id,project_id,run_id,step_id,registration_id),
 FOREIGN KEY(tenant_id,project_id,run_id,step_id) REFERENCES run_step(tenant_id,project_id,run_id,step_id),
 FOREIGN KEY(tenant_id,run_id,step_id) REFERENCES formal_evaluation_node(tenant_id,run_id,step_id),
 FOREIGN KEY(tenant_id,artifact_id) REFERENCES artifact(tenant_id,artifact_id),
 FOREIGN KEY(tenant_id,artifact_id,artifact_sha256) REFERENCES version_registry(tenant_id,version_id,content_sha256),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK(JSON_VALID(canonical_original) AND JSON_VALID(canonical_receipt)),
 CHECK(JSON_UNQUOTE(JSON_EXTRACT(canonical_original,'$.purpose'))='EVALUATION_VERIFICATION'),
 CHECK(JSON_UNQUOTE(JSON_EXTRACT(canonical_original,'$.run_id'))=run_id AND JSON_UNQUOTE(JSON_EXTRACT(canonical_original,'$.step_id'))=step_id),
 CHECK(schema_version='1.0.0' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$' AND artifact_sha256 REGEXP '^[a-f0-9]{64}$' AND receipt_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK(classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
--changeset medical-agent:152-11-case-approval dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE formal_evaluation_case_approval (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 run_id CHAR(36) CHARACTER SET ascii NOT NULL,step_id CHAR(36) CHARACTER SET ascii NOT NULL,
 registration_id CHAR(36) CHARACTER SET ascii NOT NULL,approval_id CHAR(36) CHARACTER SET ascii NOT NULL,
 canonical_receipt LONGTEXT NOT NULL,receipt_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL,created_at DATETIME(6) NOT NULL,classification VARCHAR(24) NOT NULL,
 schema_version VARCHAR(32) NOT NULL,schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 PRIMARY KEY(tenant_id,registration_id),UNIQUE KEY uq_eval_case_approval(tenant_id,approval_id),
 FOREIGN KEY(tenant_id,project_id,run_id,step_id,registration_id) REFERENCES formal_evaluation_case_original(tenant_id,project_id,run_id,step_id,registration_id),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK(JSON_VALID(canonical_receipt)),
 CHECK(schema_version='1.0.0' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$' AND receipt_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK(classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
-- Dedicated physical EVAL objects, never TASK 075 rows or caller-supplied completion assertions.
--changeset medical-agent:152-01-stage-input-intent dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE formal_evaluation_stage_input_intent (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 run_id CHAR(36) CHARACTER SET ascii NOT NULL,step_id CHAR(36) CHARACTER SET ascii NOT NULL,
 write_id CHAR(36) CHARACTER SET ascii NOT NULL,artifact_id CHAR(36) CHARACTER SET ascii NOT NULL,
 kind VARCHAR(32) CHARACTER SET ascii NOT NULL,object_key VARCHAR(1024) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 payload_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,payload_size INT UNSIGNED NOT NULL,
 canonical_payload LONGTEXT NOT NULL,schema_ref VARCHAR(512) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 payload_schema_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 canonical_provenance LONGTEXT NOT NULL,provenance_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 content_roots LONGTEXT NOT NULL,content_members LONGTEXT NOT NULL,
 schema_version VARCHAR(32) NOT NULL,schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL,created_at DATETIME(6) NOT NULL,classification VARCHAR(24) NOT NULL,
 PRIMARY KEY(tenant_id,write_id),
 UNIQUE KEY uq_eval_stage_input_object_kind(tenant_id,run_id,step_id,kind),
 UNIQUE KEY uq_eval_stage_input_object_artifact(tenant_id,artifact_id),
 UNIQUE KEY uq_eval_stage_input_owner(tenant_id,project_id,write_id),
 UNIQUE KEY uq_eval_stage_input_object_pin(tenant_id,write_id,artifact_id,payload_sha256),
 FOREIGN KEY(tenant_id,run_id,step_id) REFERENCES formal_evaluation_node(tenant_id,run_id,step_id),
 FOREIGN KEY(tenant_id,project_id,run_id,step_id) REFERENCES run_step(tenant_id,project_id,run_id,step_id),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK(kind IN ('STAGE_INPUT','CASE_INPUT')),
 CHECK(payload_size BETWEEN 1 AND 16777216 AND JSON_VALID(canonical_payload) AND JSON_VALID(canonical_provenance) AND JSON_VALID(content_roots) AND JSON_VALID(content_members)),
 CHECK(schema_version='1.0.0' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK(payload_sha256 REGEXP '^[a-f0-9]{64}$' AND provenance_sha256 REGEXP '^[a-f0-9]{64}$' AND payload_schema_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK(classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:152-02-stage-input-attempt dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE formal_evaluation_stage_input_attempt (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 write_id CHAR(36) CHARACTER SET ascii NOT NULL,payload_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(32) NOT NULL,schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL,created_at DATETIME(6) NOT NULL,classification VARCHAR(24) NOT NULL,
 PRIMARY KEY(tenant_id,write_id),
 UNIQUE KEY uq_eval_stage_attempt_owner(tenant_id,project_id,write_id),
 FOREIGN KEY(tenant_id,project_id,write_id) REFERENCES formal_evaluation_stage_input_intent(tenant_id,project_id,write_id),
 FOREIGN KEY(tenant_id,project_id) REFERENCES project(tenant_id,project_id),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK(schema_version='1.0.0' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$' AND payload_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK(classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:152-03-stage-input-registration dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE formal_evaluation_stage_input_registered (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 write_id CHAR(36) CHARACTER SET ascii NOT NULL,artifact_id CHAR(36) CHARACTER SET ascii NOT NULL,
 object_version VARCHAR(256) NOT NULL,payload_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 canonical_receipt LONGTEXT NOT NULL,receipt_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 audit_id CHAR(36) CHARACTER SET ascii NOT NULL,event_id CHAR(36) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(32) NOT NULL,schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL,created_at DATETIME(6) NOT NULL,classification VARCHAR(24) NOT NULL,
 PRIMARY KEY(tenant_id,write_id),UNIQUE KEY uq_eval_stage_input_registered_artifact(tenant_id,artifact_id),
 FOREIGN KEY(tenant_id,write_id,artifact_id,payload_sha256) REFERENCES formal_evaluation_stage_input_intent(tenant_id,write_id,artifact_id,payload_sha256),
 FOREIGN KEY(tenant_id,project_id,write_id) REFERENCES formal_evaluation_stage_input_attempt(tenant_id,project_id,write_id),
 FOREIGN KEY(tenant_id,artifact_id) REFERENCES artifact(tenant_id,artifact_id),
 FOREIGN KEY(tenant_id,artifact_id,payload_sha256) REFERENCES version_registry(tenant_id,version_id,content_sha256),
 FOREIGN KEY(tenant_id,project_id) REFERENCES project(tenant_id,project_id),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 FOREIGN KEY(tenant_id,audit_id) REFERENCES audit_event(tenant_id,audit_id),
 FOREIGN KEY(tenant_id,event_id) REFERENCES outbox_event(tenant_id,event_id),
 CHECK(schema_version='1.0.0' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$' AND payload_sha256 REGEXP '^[a-f0-9]{64}$' AND receipt_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK(CHAR_LENGTH(object_version)>0 AND JSON_VALID(canonical_receipt)),
 CHECK(classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:152-04-update-immutable dbms:mysql runInTransaction:false failOnError:true splitStatements:false
CREATE TRIGGER tr_152_4_update BEFORE UPDATE ON formal_evaluation_stage_input_intent FOR EACH ROW
SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='EVALUATION_STAGE_INPUT_IMMUTABLE';

--changeset medical-agent:152-04-delete-immutable dbms:mysql runInTransaction:false failOnError:true splitStatements:false
CREATE TRIGGER tr_152_4_delete BEFORE DELETE ON formal_evaluation_stage_input_intent FOR EACH ROW
SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='EVALUATION_STAGE_INPUT_IMMUTABLE';

--changeset medical-agent:152-05-update-immutable dbms:mysql runInTransaction:false failOnError:true splitStatements:false
CREATE TRIGGER tr_152_5_update BEFORE UPDATE ON formal_evaluation_stage_input_attempt FOR EACH ROW
SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='EVALUATION_STAGE_INPUT_IMMUTABLE';

--changeset medical-agent:152-05-delete-immutable dbms:mysql runInTransaction:false failOnError:true splitStatements:false
CREATE TRIGGER tr_152_5_delete BEFORE DELETE ON formal_evaluation_stage_input_attempt FOR EACH ROW
SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='EVALUATION_STAGE_INPUT_IMMUTABLE';

--changeset medical-agent:152-06-update-immutable dbms:mysql runInTransaction:false failOnError:true splitStatements:false
CREATE TRIGGER tr_152_6_update BEFORE UPDATE ON formal_evaluation_stage_input_registered FOR EACH ROW
SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='EVALUATION_STAGE_INPUT_IMMUTABLE';

--changeset medical-agent:152-06-delete-immutable dbms:mysql runInTransaction:false failOnError:true splitStatements:false
CREATE TRIGGER tr_152_6_delete BEFORE DELETE ON formal_evaluation_stage_input_registered FOR EACH ROW
SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='EVALUATION_STAGE_INPUT_IMMUTABLE';

--changeset medical-agent:152-12-update dbms:mysql runInTransaction:false failOnError:true splitStatements:false
CREATE TRIGGER tr_152_12_update BEFORE UPDATE ON formal_evaluation_case_original FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IMMUTABLE_EVAL_CASE_ORIGINAL';

--changeset medical-agent:152-12-delete dbms:mysql runInTransaction:false failOnError:true splitStatements:false
CREATE TRIGGER tr_152_12_delete BEFORE DELETE ON formal_evaluation_case_original FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IMMUTABLE_EVAL_CASE_ORIGINAL';

--changeset medical-agent:152-13-update dbms:mysql runInTransaction:false failOnError:true splitStatements:false
CREATE TRIGGER tr_152_13_update BEFORE UPDATE ON formal_evaluation_case_approval FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IMMUTABLE_EVAL_CASE_ORIGINAL';

--changeset medical-agent:152-13-delete dbms:mysql runInTransaction:false failOnError:true splitStatements:false
CREATE TRIGGER tr_152_13_delete BEFORE DELETE ON formal_evaluation_case_approval FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IMMUTABLE_EVAL_CASE_ORIGINAL';
