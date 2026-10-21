--liquibase formatted sql

--changeset medical-agent:148-01-record dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE scenario_record (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 record_id CHAR(36) CHARACTER SET ascii NOT NULL, config_id CHAR(36) CHARACTER SET ascii NOT NULL,
 kind VARCHAR(16) NOT NULL, parent_id CHAR(36) CHARACTER SET ascii NULL,
 idempotency_key VARCHAR(128) CHARACTER SET ascii NOT NULL, request_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 content_sha256 CHAR(64) CHARACTER SET ascii NOT NULL, content_json JSON NOT NULL,
 audit_id CHAR(36) CHARACTER SET ascii NOT NULL, event_id CHAR(36) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
 PRIMARY KEY(tenant_id,record_id), UNIQUE KEY uq_scenario_record_owner(tenant_id,project_id,record_id),
 UNIQUE KEY uq_scenario_operation(tenant_id,created_by,kind,idempotency_key),
 FOREIGN KEY(tenant_id,record_id,content_sha256) REFERENCES version_registry(tenant_id,version_id,content_sha256),
 FOREIGN KEY(tenant_id,project_id,parent_id) REFERENCES scenario_record(tenant_id,project_id,record_id),
 FOREIGN KEY(tenant_id,config_id) REFERENCES configuration_head(tenant_id,config_id),
 FOREIGN KEY(tenant_id,audit_id) REFERENCES audit_event(tenant_id,audit_id),
 FOREIGN KEY(tenant_id,event_id) REFERENCES outbox_event(tenant_id,event_id),
 FOREIGN KEY(tenant_id,project_id) REFERENCES project(tenant_id,project_id),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK(kind IN ('Author','Review','Publish','Disable')),
 CHECK((kind='Author' AND parent_id IS NULL) OR (kind<>'Author' AND parent_id IS NOT NULL)),
 CHECK(schema_version='1.0.0' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK(content_sha256 REGEXP '^[a-f0-9]{64}$' AND request_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK(classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:148-02-entry dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE scenario_task_entry (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 release_id CHAR(36) CHARACTER SET ascii NOT NULL, document_type VARCHAR(128) NOT NULL,
 publication_id CHAR(36) CHARACTER SET ascii NOT NULL, last_record_id CHAR(36) CHARACTER SET ascii NOT NULL,
 status VARCHAR(16) NOT NULL, revision BIGINT UNSIGNED NOT NULL, updated_at DATETIME(6) NOT NULL,
 schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
 PRIMARY KEY(tenant_id,project_id,release_id,document_type),
 FOREIGN KEY(tenant_id,project_id,publication_id) REFERENCES scenario_record(tenant_id,project_id,record_id),
 FOREIGN KEY(tenant_id,project_id,last_record_id) REFERENCES scenario_record(tenant_id,project_id,record_id),
 FOREIGN KEY(tenant_id,release_id) REFERENCES release_record(tenant_id,release_id),
 FOREIGN KEY(tenant_id,project_id) REFERENCES project(tenant_id,project_id),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK(status IN ('ACTIVE','DISABLED') AND revision>0 AND CHAR_LENGTH(document_type)>0),
 CHECK(schema_version='1.0.0' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK(classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:148-03-record-update dbms:mysql runInTransaction:false failOnError:true splitStatements:false
CREATE TRIGGER tr_148_record_update BEFORE UPDATE ON scenario_record FOR EACH ROW
SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='SCENARIO_RECORD_IMMUTABLE';

--changeset medical-agent:148-04-record-delete dbms:mysql runInTransaction:false failOnError:true splitStatements:false
CREATE TRIGGER tr_148_record_delete BEFORE DELETE ON scenario_record FOR EACH ROW
SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='SCENARIO_RECORD_IMMUTABLE';

--changeset medical-agent:148-05-entry-delete dbms:mysql runInTransaction:false failOnError:true splitStatements:false
CREATE TRIGGER tr_148_entry_delete BEFORE DELETE ON scenario_task_entry FOR EACH ROW
SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='SCENARIO_ENTRY_DISABLE_REQUIRED';

--changeset medical-agent:148-06-entry-cas dbms:mysql runInTransaction:false failOnError:true splitStatements:false
CREATE TRIGGER tr_148_entry_cas BEFORE UPDATE ON scenario_task_entry FOR EACH ROW
BEGIN
 IF NEW.tenant_id<>OLD.tenant_id OR NEW.project_id<>OLD.project_id OR NEW.release_id<>OLD.release_id
  OR NEW.document_type<>OLD.document_type OR NEW.created_by<>OLD.created_by OR NEW.created_at<>OLD.created_at
  OR NEW.classification<>OLD.classification OR NEW.schema_version<>OLD.schema_version
  OR NEW.schema_manifest_sha256<>OLD.schema_manifest_sha256 OR NEW.revision<>OLD.revision+1
  OR NEW.updated_at<OLD.updated_at OR NEW.last_record_id=OLD.last_record_id THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='SCENARIO_ENTRY_CAS_REQUIRED';
 END IF;
END;
