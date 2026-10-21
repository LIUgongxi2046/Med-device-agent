--liquibase formatted sql
--changeset medical-agent:159-01-stage-input-intent dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE formal_evaluation_deterministic_object_intent (
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
 UNIQUE KEY uq_eval_deterministic_input_object_kind(tenant_id,run_id,step_id,kind),
 UNIQUE KEY uq_eval_deterministic_input_object_artifact(tenant_id,artifact_id),
 UNIQUE KEY uq_eval_deterministic_input_owner(tenant_id,project_id,write_id),
 UNIQUE KEY uq_eval_deterministic_input_object_pin(tenant_id,write_id,artifact_id,payload_sha256),
 FOREIGN KEY(tenant_id,run_id,step_id) REFERENCES formal_evaluation_node(tenant_id,run_id,step_id),
 FOREIGN KEY(tenant_id,project_id,run_id,step_id) REFERENCES run_step(tenant_id,project_id,run_id,step_id),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK(kind IN ('STEP_ENVELOPE','STEP_RESULT','BUSINESS_OUTPUT')),
 CHECK(payload_size BETWEEN 1 AND 16777216 AND JSON_VALID(canonical_payload) AND JSON_VALID(canonical_provenance) AND JSON_VALID(content_roots) AND JSON_VALID(content_members)),
 CHECK(schema_version='1.0.0' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK(payload_sha256 REGEXP '^[a-f0-9]{64}$' AND provenance_sha256 REGEXP '^[a-f0-9]{64}$' AND payload_schema_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK(classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:159-02-stage-input-attempt dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE formal_evaluation_deterministic_object_attempt (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 write_id CHAR(36) CHARACTER SET ascii NOT NULL,payload_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(32) NOT NULL,schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL,created_at DATETIME(6) NOT NULL,classification VARCHAR(24) NOT NULL,
 PRIMARY KEY(tenant_id,write_id),
 UNIQUE KEY uq_eval_deterministic_attempt_owner(tenant_id,project_id,write_id),
 FOREIGN KEY(tenant_id,project_id,write_id) REFERENCES formal_evaluation_deterministic_object_intent(tenant_id,project_id,write_id),
 FOREIGN KEY(tenant_id,project_id) REFERENCES project(tenant_id,project_id),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK(schema_version='1.0.0' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$' AND payload_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK(classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:159-03-stage-input-registration dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE formal_evaluation_deterministic_object_registered (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 write_id CHAR(36) CHARACTER SET ascii NOT NULL,artifact_id CHAR(36) CHARACTER SET ascii NOT NULL,
 object_version VARCHAR(256) NOT NULL,payload_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 canonical_receipt LONGTEXT NOT NULL,receipt_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 audit_id CHAR(36) CHARACTER SET ascii NOT NULL,event_id CHAR(36) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(32) NOT NULL,schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL,created_at DATETIME(6) NOT NULL,classification VARCHAR(24) NOT NULL,
 PRIMARY KEY(tenant_id,write_id),UNIQUE KEY uq_eval_deterministic_input_registered_artifact(tenant_id,artifact_id),
 FOREIGN KEY(tenant_id,write_id,artifact_id,payload_sha256) REFERENCES formal_evaluation_deterministic_object_intent(tenant_id,write_id,artifact_id,payload_sha256),
 FOREIGN KEY(tenant_id,project_id,write_id) REFERENCES formal_evaluation_deterministic_object_attempt(tenant_id,project_id,write_id),
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

--changeset medical-agent:159-04-update-immutable dbms:mysql runInTransaction:false failOnError:true splitStatements:false
CREATE TRIGGER tr_159_4_update BEFORE UPDATE ON formal_evaluation_deterministic_object_intent FOR EACH ROW
SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='EVALUATION_DETERMINISTIC_OBJECT_IMMUTABLE';

--changeset medical-agent:159-04-delete-immutable dbms:mysql runInTransaction:false failOnError:true splitStatements:false
CREATE TRIGGER tr_159_4_delete BEFORE DELETE ON formal_evaluation_deterministic_object_intent FOR EACH ROW
SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='EVALUATION_DETERMINISTIC_OBJECT_IMMUTABLE';

--changeset medical-agent:159-05-update-immutable dbms:mysql runInTransaction:false failOnError:true splitStatements:false
CREATE TRIGGER tr_159_5_update BEFORE UPDATE ON formal_evaluation_deterministic_object_attempt FOR EACH ROW
SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='EVALUATION_DETERMINISTIC_OBJECT_IMMUTABLE';

--changeset medical-agent:159-05-delete-immutable dbms:mysql runInTransaction:false failOnError:true splitStatements:false
CREATE TRIGGER tr_159_5_delete BEFORE DELETE ON formal_evaluation_deterministic_object_attempt FOR EACH ROW
SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='EVALUATION_DETERMINISTIC_OBJECT_IMMUTABLE';

--changeset medical-agent:159-06-update-immutable dbms:mysql runInTransaction:false failOnError:true splitStatements:false
CREATE TRIGGER tr_159_6_update BEFORE UPDATE ON formal_evaluation_deterministic_object_registered FOR EACH ROW
SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='EVALUATION_DETERMINISTIC_OBJECT_IMMUTABLE';

--changeset medical-agent:159-06-delete-immutable dbms:mysql runInTransaction:false failOnError:true splitStatements:false
CREATE TRIGGER tr_159_6_delete BEFORE DELETE ON formal_evaluation_deterministic_object_registered FOR EACH ROW
SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='EVALUATION_DETERMINISTIC_OBJECT_IMMUTABLE';


--changeset medical-agent:159-20-execution dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE formal_evaluation_deterministic_execution (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 run_id CHAR(36) CHARACTER SET ascii NOT NULL, step_id CHAR(36) CHARACTER SET ascii NOT NULL,
 lease_generation BIGINT UNSIGNED NOT NULL,
 execution_id CHAR(36) CHARACTER SET ascii NOT NULL,action_id CHAR(36) CHARACTER SET ascii NOT NULL,
 input_artifact_id CHAR(36) CHARACTER SET ascii NOT NULL,input_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 canonical_execution LONGTEXT NOT NULL,execution_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 audit_id CHAR(36) CHARACTER SET ascii NOT NULL,event_id CHAR(36) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(32) NOT NULL,schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL,created_at DATETIME(6) NOT NULL,classification VARCHAR(24) NOT NULL,
 PRIMARY KEY(tenant_id,run_id,step_id),
 FOREIGN KEY(tenant_id,run_id,step_id) REFERENCES formal_evaluation_node(tenant_id,run_id,step_id),
 FOREIGN KEY(tenant_id,project_id,run_id,step_id) REFERENCES run_step(tenant_id,project_id,run_id,step_id),
 FOREIGN KEY(tenant_id,audit_id) REFERENCES audit_event(tenant_id,audit_id),FOREIGN KEY(tenant_id,event_id) REFERENCES outbox_event(tenant_id,event_id),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK(lease_generation=1 AND schema_version='1.0.0' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK(classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH')),
 UNIQUE KEY uq_eval_deterministic_execution(tenant_id,execution_id),
 FOREIGN KEY(tenant_id,input_artifact_id,input_sha256) REFERENCES version_registry(tenant_id,version_id,content_sha256),
 CHECK(JSON_VALID(canonical_execution) AND execution_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK(JSON_UNQUOTE(JSON_EXTRACT(canonical_execution,'$.envelope.node_kind')) IN ('N03','N06'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
--changeset medical-agent:159-21-outcome dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE formal_evaluation_deterministic_outcome (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 run_id CHAR(36) CHARACTER SET ascii NOT NULL, step_id CHAR(36) CHARACTER SET ascii NOT NULL,
 lease_generation BIGINT UNSIGNED NOT NULL,
 receipt_id CHAR(36) CHARACTER SET ascii NOT NULL,state VARCHAR(24) CHARACTER SET ascii NOT NULL,
 canonical_receipt LONGTEXT NOT NULL,receipt_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 canonical_record LONGTEXT NOT NULL,record_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 audit_id CHAR(36) CHARACTER SET ascii NOT NULL,event_id CHAR(36) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(32) NOT NULL,schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL,created_at DATETIME(6) NOT NULL,classification VARCHAR(24) NOT NULL,
 PRIMARY KEY(tenant_id,run_id,step_id),
 FOREIGN KEY(tenant_id,run_id,step_id) REFERENCES formal_evaluation_node(tenant_id,run_id,step_id),
 FOREIGN KEY(tenant_id,project_id,run_id,step_id) REFERENCES run_step(tenant_id,project_id,run_id,step_id),
 FOREIGN KEY(tenant_id,audit_id) REFERENCES audit_event(tenant_id,audit_id),FOREIGN KEY(tenant_id,event_id) REFERENCES outbox_event(tenant_id,event_id),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK(lease_generation=1 AND schema_version='1.0.0' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK(classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH')),
 UNIQUE KEY uq_eval_deterministic_outcome_receipt(tenant_id,receipt_id),
 FOREIGN KEY(tenant_id,run_id,step_id) REFERENCES formal_evaluation_deterministic_execution(tenant_id,run_id,step_id),
 CHECK(state IN ('SUCCEEDED','WAITING_INPUT','FAILED') AND JSON_VALID(canonical_receipt) AND JSON_VALID(canonical_record)),
 CHECK(receipt_sha256 REGEXP '^[a-f0-9]{64}$' AND record_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK(JSON_UNQUOTE(JSON_EXTRACT(canonical_record,'$.node.node_kind')) IN ('N03','N06') AND JSON_LENGTH(JSON_EXTRACT(canonical_record,'$.invocations'))=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:159-22-update dbms:mysql runInTransaction:false failOnError:true splitStatements:false
CREATE TRIGGER tr_159_22_update BEFORE UPDATE ON formal_evaluation_deterministic_execution FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IMMUTABLE_EVALUATION_DETERMINISTIC_EVIDENCE';

--changeset medical-agent:159-22-delete dbms:mysql runInTransaction:false failOnError:true splitStatements:false
CREATE TRIGGER tr_159_22_delete BEFORE DELETE ON formal_evaluation_deterministic_execution FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IMMUTABLE_EVALUATION_DETERMINISTIC_EVIDENCE';

--changeset medical-agent:159-23-update dbms:mysql runInTransaction:false failOnError:true splitStatements:false
CREATE TRIGGER tr_159_23_update BEFORE UPDATE ON formal_evaluation_deterministic_outcome FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IMMUTABLE_EVALUATION_DETERMINISTIC_EVIDENCE';

--changeset medical-agent:159-23-delete dbms:mysql runInTransaction:false failOnError:true splitStatements:false
CREATE TRIGGER tr_159_23_delete BEFORE DELETE ON formal_evaluation_deterministic_outcome FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IMMUTABLE_EVALUATION_DETERMINISTIC_EVIDENCE';
