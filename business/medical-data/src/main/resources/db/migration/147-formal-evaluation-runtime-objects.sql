--liquibase formatted sql
-- Dedicated physical EVAL objects, never TASK 075 rows or caller-supplied completion assertions.
--changeset medical-agent:147-01-runtime-object-intent dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE formal_evaluation_runtime_object_intent (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 run_id CHAR(36) CHARACTER SET ascii NOT NULL,step_id CHAR(36) CHARACTER SET ascii NOT NULL,
 write_id CHAR(36) CHARACTER SET ascii NOT NULL,artifact_id CHAR(36) CHARACTER SET ascii NOT NULL,
 session_id CHAR(36) CHARACTER SET ascii NOT NULL,lease_generation BIGINT UNSIGNED NOT NULL,
 kind VARCHAR(32) CHARACTER SET ascii NOT NULL,object_key VARCHAR(1024) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 payload_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,payload_size INT UNSIGNED NOT NULL,
 canonical_payload LONGTEXT NOT NULL,schema_ref VARCHAR(512) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 payload_schema_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 canonical_provenance LONGTEXT NOT NULL,provenance_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 content_roots LONGTEXT NOT NULL,content_members LONGTEXT NOT NULL,
 closed_receipt_id CHAR(36) CHARACTER SET ascii NULL,closed_sha256 CHAR(64) CHARACTER SET ascii NULL,
 schema_version VARCHAR(32) NOT NULL,schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL,created_at DATETIME(6) NOT NULL,classification VARCHAR(24) NOT NULL,
 PRIMARY KEY(tenant_id,write_id),
 UNIQUE KEY uq_eval_runtime_object_kind(tenant_id,run_id,step_id,kind),
 UNIQUE KEY uq_eval_runtime_object_artifact(tenant_id,artifact_id),
 UNIQUE KEY uq_eval_runtime_object_pin(tenant_id,write_id,artifact_id,payload_sha256),
 FOREIGN KEY(tenant_id,run_id,step_id) REFERENCES formal_evaluation_node(tenant_id,run_id,step_id),
 FOREIGN KEY(tenant_id,project_id,run_id,step_id) REFERENCES run_step(tenant_id,project_id,run_id,step_id),
 FOREIGN KEY(tenant_id,session_id) REFERENCES formal_evaluation_runtime_prepare(tenant_id,session_id),
 FOREIGN KEY(tenant_id,closed_receipt_id,closed_sha256) REFERENCES professional_runtime_supervision(tenant_id,receipt_id,evidence_sha256),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK(kind IN ('STEP_ENVELOPE','SESSION_EXPORT','STEP_RESULT','BUSINESS_OUTPUT') AND lease_generation>0),
 CHECK((kind='STEP_ENVELOPE' AND closed_receipt_id IS NULL AND closed_sha256 IS NULL) OR (kind<>'STEP_ENVELOPE' AND closed_receipt_id IS NOT NULL AND closed_sha256 IS NOT NULL)),
 CHECK(payload_size BETWEEN 1 AND 16777216 AND JSON_VALID(canonical_payload) AND JSON_VALID(canonical_provenance) AND JSON_VALID(content_roots) AND JSON_VALID(content_members)),
 CHECK(schema_version='1.0.0' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK(payload_sha256 REGEXP '^[a-f0-9]{64}$' AND provenance_sha256 REGEXP '^[a-f0-9]{64}$' AND payload_schema_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK(classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:147-02-runtime-object-attempt dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE formal_evaluation_runtime_object_attempt (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 write_id CHAR(36) CHARACTER SET ascii NOT NULL,payload_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(32) NOT NULL,schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL,created_at DATETIME(6) NOT NULL,classification VARCHAR(24) NOT NULL,
 PRIMARY KEY(tenant_id,write_id),
 FOREIGN KEY(tenant_id,write_id) REFERENCES formal_evaluation_runtime_object_intent(tenant_id,write_id),
 FOREIGN KEY(tenant_id,project_id) REFERENCES project(tenant_id,project_id),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK(schema_version='1.0.0' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$' AND payload_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK(classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:147-03-runtime-object-registration dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE formal_evaluation_runtime_object_registered (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 write_id CHAR(36) CHARACTER SET ascii NOT NULL,artifact_id CHAR(36) CHARACTER SET ascii NOT NULL,
 object_version VARCHAR(256) NOT NULL,payload_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 canonical_receipt LONGTEXT NOT NULL,receipt_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 audit_id CHAR(36) CHARACTER SET ascii NOT NULL,event_id CHAR(36) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(32) NOT NULL,schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL,created_at DATETIME(6) NOT NULL,classification VARCHAR(24) NOT NULL,
 PRIMARY KEY(tenant_id,write_id),UNIQUE KEY uq_eval_runtime_registered_artifact(tenant_id,artifact_id),
 FOREIGN KEY(tenant_id,write_id,artifact_id,payload_sha256) REFERENCES formal_evaluation_runtime_object_intent(tenant_id,write_id,artifact_id,payload_sha256),
 FOREIGN KEY(tenant_id,write_id) REFERENCES formal_evaluation_runtime_object_attempt(tenant_id,write_id),
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

--changeset medical-agent:147-04-update-immutable dbms:mysql runInTransaction:false failOnError:true splitStatements:false
CREATE TRIGGER tr_147_4_update BEFORE UPDATE ON formal_evaluation_runtime_object_intent FOR EACH ROW
SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='EVALUATION_RUNTIME_OBJECT_IMMUTABLE';

--changeset medical-agent:147-04-delete-immutable dbms:mysql runInTransaction:false failOnError:true splitStatements:false
CREATE TRIGGER tr_147_4_delete BEFORE DELETE ON formal_evaluation_runtime_object_intent FOR EACH ROW
SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='EVALUATION_RUNTIME_OBJECT_IMMUTABLE';

--changeset medical-agent:147-05-update-immutable dbms:mysql runInTransaction:false failOnError:true splitStatements:false
CREATE TRIGGER tr_147_5_update BEFORE UPDATE ON formal_evaluation_runtime_object_attempt FOR EACH ROW
SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='EVALUATION_RUNTIME_OBJECT_IMMUTABLE';

--changeset medical-agent:147-05-delete-immutable dbms:mysql runInTransaction:false failOnError:true splitStatements:false
CREATE TRIGGER tr_147_5_delete BEFORE DELETE ON formal_evaluation_runtime_object_attempt FOR EACH ROW
SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='EVALUATION_RUNTIME_OBJECT_IMMUTABLE';

--changeset medical-agent:147-06-update-immutable dbms:mysql runInTransaction:false failOnError:true splitStatements:false
CREATE TRIGGER tr_147_6_update BEFORE UPDATE ON formal_evaluation_runtime_object_registered FOR EACH ROW
SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='EVALUATION_RUNTIME_OBJECT_IMMUTABLE';

--changeset medical-agent:147-06-delete-immutable dbms:mysql runInTransaction:false failOnError:true splitStatements:false
CREATE TRIGGER tr_147_6_delete BEFORE DELETE ON formal_evaluation_runtime_object_registered FOR EACH ROW
SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='EVALUATION_RUNTIME_OBJECT_IMMUTABLE';
