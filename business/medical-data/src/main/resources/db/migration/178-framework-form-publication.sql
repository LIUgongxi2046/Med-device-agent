--liquibase formatted sql
-- No approvals, release pins or grants are seeded.

--changeset medical-agent:178-01 dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE framework_form_schema (
tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, form_id CHAR(36) CHARACTER SET ascii NOT NULL,
form_json JSON NOT NULL, content_sha256 CHAR(64) CHARACTER SET ascii NOT NULL, reason VARCHAR(1000) NOT NULL, binding_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL, created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
PRIMARY KEY(tenant_id,form_id), FOREIGN KEY(tenant_id,form_id) REFERENCES version_registry(tenant_id,version_id),
CHECK(content_sha256 REGEXP '^[0-9a-f]{64}$'), CHECK(binding_sha256 REGEXP '^[0-9a-f]{64}$'),
CHECK(JSON_UNQUOTE(JSON_EXTRACT(form_json,'$.schema_version')) <=> '1.0.0'), FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id), CHECK(schema_version='1.0.0'), CHECK(classification='INTERNAL'), CHECK(schema_manifest_sha256 REGEXP '^[0-9a-f]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:178-02 dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE framework_form_approval (
tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, approval_id CHAR(36) CHARACTER SET ascii NOT NULL, form_id CHAR(36) CHARACTER SET ascii NOT NULL,
schema_artifact_id CHAR(36) CHARACTER SET ascii NOT NULL, approval_json JSON NOT NULL, content_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL, created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
PRIMARY KEY(tenant_id,approval_id), UNIQUE KEY uq_framework_form_published(tenant_id,form_id),
FOREIGN KEY(tenant_id,form_id) REFERENCES framework_form_schema(tenant_id,form_id),
FOREIGN KEY(tenant_id,approval_id) REFERENCES version_registry(tenant_id,version_id),
FOREIGN KEY(tenant_id,schema_artifact_id) REFERENCES artifact(tenant_id,artifact_id),
CHECK(content_sha256 REGEXP '^[0-9a-f]{64}$'),
CHECK(JSON_UNQUOTE(JSON_EXTRACT(approval_json,'$.approval_id')) <=> approval_id),
CHECK(JSON_UNQUOTE(JSON_EXTRACT(approval_json,'$.form_id')) <=> form_id),
CHECK(JSON_UNQUOTE(JSON_EXTRACT(approval_json,'$.created_by')) <=> created_by),
CHECK(JSON_UNQUOTE(JSON_EXTRACT(approval_json,'$.schema_artifact_id')) <=> schema_artifact_id), FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id), CHECK(schema_version='1.0.0'), CHECK(classification='INTERNAL'), CHECK(schema_manifest_sha256 REGEXP '^[0-9a-f]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:178-03 dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE framework_form_operation (
tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, actor_id CHAR(36) CHARACTER SET ascii NOT NULL,
idempotency_key VARCHAR(128) CHARACTER SET ascii NOT NULL, action VARCHAR(16) NOT NULL, request_sha256 CHAR(64) CHARACTER SET ascii NOT NULL, response_json JSON NOT NULL,
schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL, created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
PRIMARY KEY(tenant_id,actor_id,idempotency_key,action), FOREIGN KEY(tenant_id,actor_id) REFERENCES tenant_member(tenant_id,user_id),
CHECK(actor_id=created_by), CHECK(action IN ('DRAFT','APPROVE')), CHECK(request_sha256 REGEXP '^[0-9a-f]{64}$'), FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id), CHECK(schema_version='1.0.0'), CHECK(classification='INTERNAL'), CHECK(schema_manifest_sha256 REGEXP '^[0-9a-f]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:178-04 dbms:mysql splitStatements:false runInTransaction:false failOnError:true
CREATE TRIGGER trg_framework_form_schema_update BEFORE UPDATE ON framework_form_schema FOR EACH ROW
BEGIN
 SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='FRAMEWORK_FORM_HISTORY_IMMUTABLE';
END;

--changeset medical-agent:178-05 dbms:mysql splitStatements:false runInTransaction:false failOnError:true
CREATE TRIGGER trg_framework_form_schema_delete BEFORE DELETE ON framework_form_schema FOR EACH ROW
BEGIN
 SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='FRAMEWORK_FORM_HISTORY_IMMUTABLE';
END;

--changeset medical-agent:178-06 dbms:mysql splitStatements:false runInTransaction:false failOnError:true
CREATE TRIGGER trg_framework_form_approval_update BEFORE UPDATE ON framework_form_approval FOR EACH ROW
BEGIN
 SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='FRAMEWORK_FORM_HISTORY_IMMUTABLE';
END;

--changeset medical-agent:178-07 dbms:mysql splitStatements:false runInTransaction:false failOnError:true
CREATE TRIGGER trg_framework_form_approval_delete BEFORE DELETE ON framework_form_approval FOR EACH ROW
BEGIN
 SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='FRAMEWORK_FORM_HISTORY_IMMUTABLE';
END;

--changeset medical-agent:178-08 dbms:mysql splitStatements:false runInTransaction:false failOnError:true
CREATE TRIGGER trg_framework_form_operation_update BEFORE UPDATE ON framework_form_operation FOR EACH ROW
BEGIN
 SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='FRAMEWORK_FORM_HISTORY_IMMUTABLE';
END;

--changeset medical-agent:178-09 dbms:mysql splitStatements:false runInTransaction:false failOnError:true
CREATE TRIGGER trg_framework_form_operation_delete BEFORE DELETE ON framework_form_operation FOR EACH ROW
BEGIN
 SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='FRAMEWORK_FORM_HISTORY_IMMUTABLE';
END;

--changeset medical-agent:178-10 dbms:mysql splitStatements:false runInTransaction:false failOnError:true
CREATE TRIGGER trg_framework_form_independent BEFORE INSERT ON framework_form_approval FOR EACH ROW
BEGIN
 IF EXISTS(SELECT 1 FROM framework_form_schema WHERE tenant_id=NEW.tenant_id AND form_id=NEW.form_id AND created_by=NEW.created_by) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='INDEPENDENT_FORM_APPROVAL_REQUIRED';
 END IF;
END;
