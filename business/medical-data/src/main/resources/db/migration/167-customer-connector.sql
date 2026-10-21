--liquibase formatted sql
-- Customer interface data is authored, not seeded. No default endpoint, identity or approval.

--changeset medical-agent:167-01-customer_connector_head dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE customer_connector_head (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 connector_id CHAR(36) CHARACTER SET ascii NOT NULL,current_revision CHAR(36) CHARACTER SET ascii NULL,
 revision BIGINT UNSIGNED NOT NULL,
 PRIMARY KEY(tenant_id,connector_id),UNIQUE KEY uq_customer_connector_head_owner(tenant_id,project_id,connector_id),
 created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL,
 updated_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
 schema_version VARCHAR(16) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 FOREIGN KEY (tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK (classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH')),
 CHECK (schema_version='1.0.0' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
 FOREIGN KEY (tenant_id,project_id) REFERENCES project(tenant_id,project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:167-02-customer_connector_definition dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE customer_connector_definition (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 connector_id CHAR(36) CHARACTER SET ascii NOT NULL,config_revision CHAR(36) CHARACTER SET ascii NOT NULL,
 definition_json LONGTEXT NOT NULL,definition_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 state VARCHAR(16) NOT NULL,revision BIGINT UNSIGNED NOT NULL,
 PRIMARY KEY(tenant_id,config_revision),UNIQUE KEY uq_customer_connector_definition_owner(tenant_id,project_id,config_revision),
 UNIQUE KEY uq_customer_connector_definition_head(tenant_id,connector_id,config_revision),
 UNIQUE KEY uq_customer_connector_definition_hash(tenant_id,config_revision,definition_sha256),
 FOREIGN KEY(tenant_id,project_id,connector_id) REFERENCES customer_connector_head(tenant_id,project_id,connector_id),
 CHECK(JSON_VALID(definition_json) AND definition_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK(state IN ('DRAFT','APPROVED','REVOKED') AND revision>0),
 created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL,
 updated_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
 schema_version VARCHAR(16) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 FOREIGN KEY (tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK (classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH')),
 CHECK (schema_version='1.0.0' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
 FOREIGN KEY (tenant_id,project_id) REFERENCES project(tenant_id,project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:167-03-current-definition dbms:mysql runInTransaction:false failOnError:true
ALTER TABLE customer_connector_head ADD CONSTRAINT fk_customer_connector_current FOREIGN KEY(tenant_id,connector_id,current_revision) REFERENCES customer_connector_definition(tenant_id,connector_id,config_revision);

--changeset medical-agent:167-04-customer_connector_decision dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE customer_connector_decision (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 config_revision CHAR(36) CHARACTER SET ascii NOT NULL,decision_id CHAR(36) CHARACTER SET ascii NOT NULL,
 decision VARCHAR(16) NOT NULL,definition_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 request_json LONGTEXT NOT NULL,request_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 actor_id CHAR(36) CHARACTER SET ascii NOT NULL,identity_mode VARCHAR(16) NOT NULL,
 grant_id CHAR(36) CHARACTER SET ascii NOT NULL,grant_revision BIGINT UNSIGNED NOT NULL,revision BIGINT UNSIGNED NOT NULL DEFAULT 1,
 CHECK(revision=1),
 PRIMARY KEY(tenant_id,decision_id),UNIQUE KEY uq_customer_connector_decision(tenant_id,config_revision,decision),
 FOREIGN KEY(tenant_id,project_id,config_revision) REFERENCES customer_connector_definition(tenant_id,project_id,config_revision),
 FOREIGN KEY(tenant_id,actor_id) REFERENCES tenant_member(tenant_id,user_id),
 FOREIGN KEY(tenant_id,grant_id) REFERENCES permission_grant(tenant_id,grant_id),
 FOREIGN KEY(tenant_id,config_revision,definition_sha256) REFERENCES customer_connector_definition(tenant_id,config_revision,definition_sha256),
 CHECK(decision IN ('APPROVED','REVOKED') AND identity_mode IN ('local','oidc') AND grant_revision>0),
 CHECK(JSON_VALID(request_json) AND request_sha256 REGEXP '^[a-f0-9]{64}$' AND definition_sha256 REGEXP '^[a-f0-9]{64}$'),
 created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL,
 updated_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
 schema_version VARCHAR(16) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 FOREIGN KEY (tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK (classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH')),
 CHECK (schema_version='1.0.0' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
 FOREIGN KEY (tenant_id,project_id) REFERENCES project(tenant_id,project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:167-05-customer_connector_cursor dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE customer_connector_cursor (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 config_revision CHAR(36) CHARACTER SET ascii NOT NULL,cursor_json LONGTEXT NOT NULL,
 cursor_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,revision BIGINT UNSIGNED NOT NULL,
 last_run_id CHAR(36) CHARACTER SET ascii NULL,
 PRIMARY KEY(tenant_id,config_revision),
 FOREIGN KEY(tenant_id,project_id,config_revision) REFERENCES customer_connector_definition(tenant_id,project_id,config_revision),
 CHECK(JSON_VALID(cursor_json) AND OCTET_LENGTH(cursor_json)<=8192 AND cursor_sha256 REGEXP '^[a-f0-9]{64}$'),
 created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL,
 updated_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
 schema_version VARCHAR(16) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 FOREIGN KEY (tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK (classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH')),
 CHECK (schema_version='1.0.0' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
 FOREIGN KEY (tenant_id,project_id) REFERENCES project(tenant_id,project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:167-06-customer_connector_run dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE customer_connector_run (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 config_revision CHAR(36) CHARACTER SET ascii NOT NULL,run_id CHAR(36) CHARACTER SET ascii NOT NULL,
 kind VARCHAR(16) NOT NULL,state VARCHAR(40) NOT NULL,revision BIGINT UNSIGNED NOT NULL,
 request_json LONGTEXT NOT NULL,request_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 execution_request LONGTEXT NOT NULL,execution_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 cursor_revision BIGINT UNSIGNED NOT NULL,result_json LONGTEXT NULL,result_sha256 CHAR(64) CHARACTER SET ascii NULL,
 failure VARCHAR(64) NULL,
 PRIMARY KEY(tenant_id,run_id),UNIQUE KEY uq_customer_connector_run_owner(tenant_id,project_id,config_revision,run_id),
 KEY ix_customer_connector_run_queue(tenant_id,config_revision,kind,state),
 FOREIGN KEY(tenant_id,project_id,config_revision) REFERENCES customer_connector_definition(tenant_id,project_id,config_revision),
 CHECK(kind IN ('VERIFY','SYNC') AND revision>0),
 CHECK(state IN ('BLOCKED','STARTED','UNKNOWN','VERIFIED','VERIFICATION_FAILED','CAPTURED_PENDING_GOVERNANCE','CONFLICT')),
 CHECK(JSON_VALID(request_json) AND JSON_VALID(execution_request)),
 CHECK(request_sha256 REGEXP '^[a-f0-9]{64}$' AND execution_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK((result_json IS NULL)=(result_sha256 IS NULL)),
 CHECK(result_json IS NULL OR JSON_VALID(result_json)),CHECK(result_sha256 IS NULL OR result_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK((state IN ('STARTED','UNKNOWN'))=(result_json IS NULL)),
 created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL,
 updated_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
 schema_version VARCHAR(16) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 FOREIGN KEY (tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK (classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH')),
 CHECK (schema_version='1.0.0' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
 FOREIGN KEY (tenant_id,project_id) REFERENCES project(tenant_id,project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:167-07-cursor-run dbms:mysql runInTransaction:false failOnError:true
ALTER TABLE customer_connector_cursor ADD CONSTRAINT fk_customer_connector_cursor_run FOREIGN KEY(tenant_id,project_id,config_revision,last_run_id) REFERENCES customer_connector_run(tenant_id,project_id,config_revision,run_id);

--changeset medical-agent:167-08-customer_connector_item dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE customer_connector_item (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 config_revision CHAR(36) CHARACTER SET ascii NOT NULL,run_id CHAR(36) CHARACTER SET ascii NOT NULL,item_id CHAR(36) CHARACTER SET ascii NOT NULL,
 external_id_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,external_version_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 item_json LONGTEXT NOT NULL,item_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,original_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 state VARCHAR(40) NOT NULL,revision BIGINT UNSIGNED NOT NULL,
 import_request LONGTEXT NULL,import_sha256 CHAR(64) CHARACTER SET ascii NULL,import_actor_id CHAR(36) CHARACTER SET ascii NULL,upload_id CHAR(36) CHARACTER SET ascii NULL,
 completion_request LONGTEXT NULL,
 PRIMARY KEY(tenant_id,item_id),UNIQUE KEY uq_customer_connector_external(tenant_id,config_revision,external_id_sha256,external_version_sha256),
 FOREIGN KEY(tenant_id,project_id,config_revision,run_id) REFERENCES customer_connector_run(tenant_id,project_id,config_revision,run_id),
 FOREIGN KEY(tenant_id,import_actor_id) REFERENCES tenant_member(tenant_id,user_id),
 CHECK((import_request IS NULL)=(import_actor_id IS NULL)),
 FOREIGN KEY(tenant_id,project_id,upload_id) REFERENCES upload_item(tenant_id,project_id,upload_id),
 CHECK(state IN ('PENDING_RIGHTS','IMPORTING','UPLOAD_PENDING_GOVERNANCE','REVOKED') AND revision>0),
 CHECK(JSON_VALID(item_json) AND item_sha256 REGEXP '^[a-f0-9]{64}$' AND original_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK(external_id_sha256 REGEXP '^[a-f0-9]{64}$' AND external_version_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK((import_request IS NULL)=(import_sha256 IS NULL)),CHECK(import_request IS NULL OR JSON_VALID(import_request)),
 CHECK(import_sha256 IS NULL OR import_sha256 REGEXP '^[a-f0-9]{64}$'),CHECK(completion_request IS NULL OR JSON_VALID(completion_request)),
 created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL,
 updated_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
 schema_version VARCHAR(16) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 FOREIGN KEY (tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK (classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH')),
 CHECK (schema_version='1.0.0' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
 FOREIGN KEY (tenant_id,project_id) REFERENCES project(tenant_id,project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:167-09-head-transition dbms:mysql runInTransaction:false failOnError:true splitStatements:false
CREATE TRIGGER tr_customer_connector_head_update BEFORE UPDATE ON customer_connector_head FOR EACH ROW
BEGIN
 IF NOT(OLD.tenant_id<=>NEW.tenant_id) OR NOT(OLD.project_id<=>NEW.project_id) OR NOT(OLD.created_by<=>NEW.created_by) OR NOT(OLD.created_at<=>NEW.created_at) OR NOT(OLD.classification<=>NEW.classification) OR NOT(OLD.schema_version<=>NEW.schema_version) OR NOT(OLD.schema_manifest_sha256<=>NEW.schema_manifest_sha256) OR NOT(OLD.connector_id<=>NEW.connector_id) OR NEW.revision<>OLD.revision+1 OR NEW.updated_at<OLD.updated_at OR NEW.current_revision IS NULL OR OLD.current_revision<=>NEW.current_revision THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='CONNECTOR_TRANSITION_INVALID'; END IF;
END;

--changeset medical-agent:167-10-definition-transition dbms:mysql runInTransaction:false failOnError:true splitStatements:false
CREATE TRIGGER tr_customer_connector_definition_update BEFORE UPDATE ON customer_connector_definition FOR EACH ROW
BEGIN
 IF NOT(SHA2(CAST(NEW.definition_json AS BINARY),256)<=>NEW.definition_sha256) OR NOT(OLD.tenant_id<=>NEW.tenant_id) OR NOT(OLD.project_id<=>NEW.project_id) OR NOT(OLD.created_by<=>NEW.created_by) OR NOT(OLD.created_at<=>NEW.created_at) OR NOT(OLD.classification<=>NEW.classification) OR NOT(OLD.schema_version<=>NEW.schema_version) OR NOT(OLD.schema_manifest_sha256<=>NEW.schema_manifest_sha256) OR NOT(OLD.connector_id<=>NEW.connector_id) OR NOT(OLD.config_revision<=>NEW.config_revision) OR NOT(OLD.definition_json<=>NEW.definition_json) OR NOT(OLD.definition_sha256<=>NEW.definition_sha256) OR NEW.revision<>OLD.revision+1 OR NEW.updated_at<OLD.updated_at OR NOT((OLD.state='DRAFT' AND NEW.state='APPROVED') OR (OLD.state='APPROVED' AND NEW.state='REVOKED')) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='CONNECTOR_TRANSITION_INVALID'; END IF;
END;

--changeset medical-agent:167-11-cursor-transition dbms:mysql runInTransaction:false failOnError:true splitStatements:false
CREATE TRIGGER tr_customer_connector_cursor_update BEFORE UPDATE ON customer_connector_cursor FOR EACH ROW
BEGIN
 IF NOT(SHA2(CAST(NEW.cursor_json AS BINARY),256)<=>NEW.cursor_sha256) OR NOT(OLD.tenant_id<=>NEW.tenant_id) OR NOT(OLD.project_id<=>NEW.project_id) OR NOT(OLD.created_by<=>NEW.created_by) OR NOT(OLD.created_at<=>NEW.created_at) OR NOT(OLD.classification<=>NEW.classification) OR NOT(OLD.schema_version<=>NEW.schema_version) OR NOT(OLD.schema_manifest_sha256<=>NEW.schema_manifest_sha256) OR NOT(OLD.config_revision<=>NEW.config_revision) OR NEW.revision<>OLD.revision+1 OR NEW.updated_at<OLD.updated_at THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='CONNECTOR_TRANSITION_INVALID'; END IF;
END;

--changeset medical-agent:167-12-run-transition dbms:mysql runInTransaction:false failOnError:true splitStatements:false
CREATE TRIGGER tr_customer_connector_run_update BEFORE UPDATE ON customer_connector_run FOR EACH ROW
BEGIN
 IF NOT(SHA2(CAST(NEW.request_json AS BINARY),256)<=>NEW.request_sha256) OR NOT(SHA2(CAST(NEW.execution_request AS BINARY),256)<=>NEW.execution_sha256) OR NOT(SHA2(CAST(NEW.result_json AS BINARY),256)<=>NEW.result_sha256) OR NOT(OLD.tenant_id<=>NEW.tenant_id) OR NOT(OLD.project_id<=>NEW.project_id) OR NOT(OLD.created_by<=>NEW.created_by) OR NOT(OLD.created_at<=>NEW.created_at) OR NOT(OLD.classification<=>NEW.classification) OR NOT(OLD.schema_version<=>NEW.schema_version) OR NOT(OLD.schema_manifest_sha256<=>NEW.schema_manifest_sha256) OR NOT(OLD.config_revision<=>NEW.config_revision) OR NOT(OLD.run_id<=>NEW.run_id) OR NOT(OLD.kind<=>NEW.kind) OR NOT(OLD.request_json<=>NEW.request_json) OR NOT(OLD.request_sha256<=>NEW.request_sha256) OR NOT(OLD.execution_request<=>NEW.execution_request) OR NOT(OLD.execution_sha256<=>NEW.execution_sha256) OR NOT(OLD.cursor_revision<=>NEW.cursor_revision) OR NEW.revision<>OLD.revision+1 OR NEW.updated_at<OLD.updated_at OR OLD.result_json IS NOT NULL OR OLD.state NOT IN ('STARTED','UNKNOWN') OR NEW.state='BLOCKED' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='CONNECTOR_TRANSITION_INVALID'; END IF;
END;

--changeset medical-agent:167-13-item-transition dbms:mysql runInTransaction:false failOnError:true splitStatements:false
CREATE TRIGGER tr_customer_connector_item_update BEFORE UPDATE ON customer_connector_item FOR EACH ROW
BEGIN
 IF NOT(SHA2(CAST(NEW.item_json AS BINARY),256)<=>NEW.item_sha256) OR NOT(SHA2(CAST(NEW.import_request AS BINARY),256)<=>NEW.import_sha256) OR NOT(OLD.tenant_id<=>NEW.tenant_id) OR NOT(OLD.project_id<=>NEW.project_id) OR NOT(OLD.created_by<=>NEW.created_by) OR NOT(OLD.created_at<=>NEW.created_at) OR NOT(OLD.classification<=>NEW.classification) OR NOT(OLD.schema_version<=>NEW.schema_version) OR NOT(OLD.schema_manifest_sha256<=>NEW.schema_manifest_sha256) OR NOT(OLD.config_revision<=>NEW.config_revision) OR NOT(OLD.run_id<=>NEW.run_id) OR NOT(OLD.item_id<=>NEW.item_id) OR NOT(OLD.external_id_sha256<=>NEW.external_id_sha256) OR NOT(OLD.external_version_sha256<=>NEW.external_version_sha256) OR NOT(OLD.item_json<=>NEW.item_json) OR NOT(OLD.item_sha256<=>NEW.item_sha256) OR NOT(OLD.original_sha256<=>NEW.original_sha256) OR NEW.revision<>OLD.revision+1 OR NEW.updated_at<OLD.updated_at OR (OLD.state='REVOKED') OR (OLD.import_request IS NOT NULL AND NOT(OLD.import_request<=>NEW.import_request)) OR (OLD.import_sha256 IS NOT NULL AND NOT(OLD.import_sha256<=>NEW.import_sha256)) OR (OLD.import_actor_id IS NOT NULL AND NOT(OLD.import_actor_id<=>NEW.import_actor_id)) OR (OLD.upload_id IS NOT NULL AND NOT(OLD.upload_id<=>NEW.upload_id)) OR (OLD.completion_request IS NOT NULL AND NOT(OLD.completion_request<=>NEW.completion_request)) OR NOT(NEW.state='REVOKED' OR NEW.state=OLD.state OR (OLD.state='PENDING_RIGHTS' AND NEW.state='IMPORTING') OR (OLD.state='IMPORTING' AND NEW.state='UPLOAD_PENDING_GOVERNANCE')) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='CONNECTOR_TRANSITION_INVALID'; END IF;
END;

--changeset medical-agent:167-14-head-delete dbms:mysql runInTransaction:false failOnError:true splitStatements:false
CREATE TRIGGER tr_customer_connector_head_delete BEFORE DELETE ON customer_connector_head FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='CONNECTOR_HISTORY_IMMUTABLE';

--changeset medical-agent:167-15-definition-delete dbms:mysql runInTransaction:false failOnError:true splitStatements:false
CREATE TRIGGER tr_customer_connector_definition_delete BEFORE DELETE ON customer_connector_definition FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='CONNECTOR_HISTORY_IMMUTABLE';

--changeset medical-agent:167-16-decision-delete dbms:mysql runInTransaction:false failOnError:true splitStatements:false
CREATE TRIGGER tr_customer_connector_decision_delete BEFORE DELETE ON customer_connector_decision FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='CONNECTOR_HISTORY_IMMUTABLE';

--changeset medical-agent:167-17-cursor-delete dbms:mysql runInTransaction:false failOnError:true splitStatements:false
CREATE TRIGGER tr_customer_connector_cursor_delete BEFORE DELETE ON customer_connector_cursor FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='CONNECTOR_HISTORY_IMMUTABLE';

--changeset medical-agent:167-18-run-delete dbms:mysql runInTransaction:false failOnError:true splitStatements:false
CREATE TRIGGER tr_customer_connector_run_delete BEFORE DELETE ON customer_connector_run FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='CONNECTOR_HISTORY_IMMUTABLE';

--changeset medical-agent:167-19-item-delete dbms:mysql runInTransaction:false failOnError:true splitStatements:false
CREATE TRIGGER tr_customer_connector_item_delete BEFORE DELETE ON customer_connector_item FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='CONNECTOR_HISTORY_IMMUTABLE';

--changeset medical-agent:167-20-decision-update dbms:mysql runInTransaction:false failOnError:true splitStatements:false
CREATE TRIGGER tr_customer_connector_decision_update BEFORE UPDATE ON customer_connector_decision FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='CONNECTOR_DECISION_IMMUTABLE';

--changeset medical-agent:167-definition-insert-integrity dbms:mysql runInTransaction:false failOnError:true splitStatements:false
CREATE TRIGGER tr_customer_connector_definition_insert BEFORE INSERT ON customer_connector_definition FOR EACH ROW
BEGIN
 IF NOT(SHA2(CAST(NEW.definition_json AS BINARY),256)<=>NEW.definition_sha256) OR NEW.state<>'DRAFT' OR NEW.revision<>1 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='CONNECTOR_INSERT_INTEGRITY'; END IF;
END;

--changeset medical-agent:167-decision-insert-integrity dbms:mysql runInTransaction:false failOnError:true splitStatements:false
CREATE TRIGGER tr_customer_connector_decision_insert BEFORE INSERT ON customer_connector_decision FOR EACH ROW
BEGIN
 IF NOT(SHA2(CAST(NEW.request_json AS BINARY),256)<=>NEW.request_sha256) OR NEW.revision<>1 OR NEW.actor_id<>NEW.created_by THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='CONNECTOR_INSERT_INTEGRITY'; END IF;
END;

--changeset medical-agent:167-cursor-insert-integrity dbms:mysql runInTransaction:false failOnError:true splitStatements:false
CREATE TRIGGER tr_customer_connector_cursor_insert BEFORE INSERT ON customer_connector_cursor FOR EACH ROW
BEGIN
 IF NOT(SHA2(CAST(NEW.cursor_json AS BINARY),256)<=>NEW.cursor_sha256) OR NEW.revision<>0 OR NEW.cursor_json<>'null' OR NEW.last_run_id IS NOT NULL THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='CONNECTOR_INSERT_INTEGRITY'; END IF;
END;

--changeset medical-agent:167-run-insert-integrity dbms:mysql runInTransaction:false failOnError:true splitStatements:false
CREATE TRIGGER tr_customer_connector_run_insert BEFORE INSERT ON customer_connector_run FOR EACH ROW
BEGIN
 IF NOT(SHA2(CAST(NEW.request_json AS BINARY),256)<=>NEW.request_sha256) OR NOT(SHA2(CAST(NEW.execution_request AS BINARY),256)<=>NEW.execution_sha256) OR NOT(SHA2(CAST(NEW.result_json AS BINARY),256)<=>NEW.result_sha256) OR NEW.revision<>1 OR NEW.state NOT IN ('STARTED','BLOCKED') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='CONNECTOR_INSERT_INTEGRITY'; END IF;
END;

--changeset medical-agent:167-item-insert-integrity dbms:mysql runInTransaction:false failOnError:true splitStatements:false
CREATE TRIGGER tr_customer_connector_item_insert BEFORE INSERT ON customer_connector_item FOR EACH ROW
BEGIN
 IF NOT(SHA2(CAST(NEW.item_json AS BINARY),256)<=>NEW.item_sha256) OR NOT(SHA2(CAST(NEW.import_request AS BINARY),256)<=>NEW.import_sha256) OR NEW.revision<>1 OR NEW.state NOT IN ('PENDING_RIGHTS','REVOKED') OR NEW.import_request IS NOT NULL OR NEW.upload_id IS NOT NULL OR NEW.completion_request IS NOT NULL THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='CONNECTOR_INSERT_INTEGRITY'; END IF;
END;

--changeset medical-agent:167-head-insert-integrity dbms:mysql runInTransaction:false failOnError:true splitStatements:false
CREATE TRIGGER tr_customer_connector_head_insert BEFORE INSERT ON customer_connector_head FOR EACH ROW
BEGIN
 IF NEW.revision<>0 OR NEW.current_revision IS NOT NULL THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='CONNECTOR_HEAD_INITIAL_STATE'; END IF;
END;
