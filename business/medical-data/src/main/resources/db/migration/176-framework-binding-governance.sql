--liquibase formatted sql
-- Actual approvals are inserted only by authenticated, independently authorized humans.
-- No seed release, fake source commit, approval, grant or environment is installed.

--changeset medical-agent:176-01-framework-manifest dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE framework_binding_manifest (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, release_id CHAR(36) CHARACTER SET ascii NOT NULL,
 environment_id CHAR(36) CHARACTER SET ascii NOT NULL, manifest_json JSON NOT NULL,
 manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
 PRIMARY KEY(tenant_id,release_id), UNIQUE KEY uq_fbm_hash(tenant_id,release_id,manifest_sha256),
 FOREIGN KEY(tenant_id,release_id) REFERENCES version_registry(tenant_id,version_id),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK(schema_version='1.0.0'), CHECK(manifest_sha256 REGEXP '^[0-9a-f]{64}$'),
 CHECK(schema_manifest_sha256 REGEXP '^[0-9a-f]{64}$'), CHECK(classification='INTERNAL'),
 CHECK(JSON_UNQUOTE(JSON_EXTRACT(manifest_json,'$.release_id')) <=> release_id),
 CHECK(JSON_UNQUOTE(JSON_EXTRACT(manifest_json,'$.environment_id')) <=> environment_id),
 CHECK(JSON_LENGTH(JSON_EXTRACT(manifest_json,'$.modules'))=18)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:176-02-framework-approval dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE framework_binding_approval (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, approval_id CHAR(36) CHARACTER SET ascii NOT NULL,
 release_id CHAR(36) CHARACTER SET ascii NOT NULL, approval_json JSON NOT NULL, content_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
 PRIMARY KEY(tenant_id,approval_id), UNIQUE KEY uq_fba_release(tenant_id,release_id),
 FOREIGN KEY(tenant_id,release_id) REFERENCES framework_binding_manifest(tenant_id,release_id),
 FOREIGN KEY(tenant_id,approval_id) REFERENCES version_registry(tenant_id,version_id),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK(schema_version='1.0.0'), CHECK(classification='INTERNAL'),
 CHECK(content_sha256 REGEXP '^[0-9a-f]{64}$'), CHECK(schema_manifest_sha256 REGEXP '^[0-9a-f]{64}$'),
 CHECK(JSON_UNQUOTE(JSON_EXTRACT(approval_json,'$.approval_id')) <=> approval_id),
 CHECK(JSON_UNQUOTE(JSON_EXTRACT(approval_json,'$.release_id')) <=> release_id),
 CHECK(JSON_UNQUOTE(JSON_EXTRACT(approval_json,'$.created_by')) <=> created_by)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:176-03-framework-operation dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE framework_binding_operation (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, actor_id CHAR(36) CHARACTER SET ascii NOT NULL,
 idempotency_key VARCHAR(128) CHARACTER SET ascii NOT NULL, action VARCHAR(16) NOT NULL,
 request_sha256 CHAR(64) CHARACTER SET ascii NOT NULL, response_json JSON NOT NULL,
 schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
 PRIMARY KEY(tenant_id,actor_id,idempotency_key,action),
 FOREIGN KEY(tenant_id,actor_id) REFERENCES tenant_member(tenant_id,user_id),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK(actor_id=created_by), CHECK(action IN ('DRAFT','APPROVE','REVOKE')), CHECK(schema_version='1.0.0'),
 CHECK(classification='INTERNAL'), CHECK(request_sha256 REGEXP '^[0-9a-f]{64}$'), CHECK(schema_manifest_sha256 REGEXP '^[0-9a-f]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:176-04-independent-approval dbms:mysql splitStatements:false runInTransaction:false failOnError:true
CREATE TRIGGER trg_fba_independent BEFORE INSERT ON framework_binding_approval FOR EACH ROW
BEGIN
 IF EXISTS(SELECT 1 FROM framework_binding_manifest WHERE tenant_id=NEW.tenant_id AND release_id=NEW.release_id AND created_by=NEW.created_by)
 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='FRAMEWORK_INDEPENDENT_APPROVER_REQUIRED'; END IF;
END;

--changeset medical-agent:176-05-update dbms:mysql splitStatements:false runInTransaction:false failOnError:true
CREATE TRIGGER trg_fbm_no_update BEFORE UPDATE ON framework_binding_manifest FOR EACH ROW
BEGIN
 SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='FRAMEWORK_BINDING_HISTORY_IMMUTABLE';
END;

--changeset medical-agent:176-05-delete dbms:mysql splitStatements:false runInTransaction:false failOnError:true
CREATE TRIGGER trg_fbm_no_delete BEFORE DELETE ON framework_binding_manifest FOR EACH ROW
BEGIN
 SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='FRAMEWORK_BINDING_HISTORY_IMMUTABLE';
END;

--changeset medical-agent:176-06-update dbms:mysql splitStatements:false runInTransaction:false failOnError:true
CREATE TRIGGER trg_fba_no_update BEFORE UPDATE ON framework_binding_approval FOR EACH ROW
BEGIN
 SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='FRAMEWORK_BINDING_HISTORY_IMMUTABLE';
END;

--changeset medical-agent:176-06-delete dbms:mysql splitStatements:false runInTransaction:false failOnError:true
CREATE TRIGGER trg_fba_no_delete BEFORE DELETE ON framework_binding_approval FOR EACH ROW
BEGIN
 SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='FRAMEWORK_BINDING_HISTORY_IMMUTABLE';
END;

--changeset medical-agent:176-07-update dbms:mysql splitStatements:false runInTransaction:false failOnError:true
CREATE TRIGGER trg_fbo_no_update BEFORE UPDATE ON framework_binding_operation FOR EACH ROW
BEGIN
 SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='FRAMEWORK_BINDING_HISTORY_IMMUTABLE';
END;

--changeset medical-agent:176-07-delete dbms:mysql splitStatements:false runInTransaction:false failOnError:true
CREATE TRIGGER trg_fbo_no_delete BEFORE DELETE ON framework_binding_operation FOR EACH ROW
BEGIN
 SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='FRAMEWORK_BINDING_HISTORY_IMMUTABLE';
END;

--changeset medical-agent:176-08-release-transition dbms:mysql splitStatements:false runInTransaction:false failOnError:true
CREATE TRIGGER trg_framework_release_transition BEFORE UPDATE ON management_binding_release FOR EACH ROW
BEGIN
 IF EXISTS(SELECT 1 FROM framework_binding_manifest WHERE tenant_id=OLD.tenant_id AND release_id=OLD.release_id) THEN
  IF NOT(NEW.tenant_id <=> OLD.tenant_id) OR NOT(NEW.project_id <=> OLD.project_id)
   OR NEW.release_id<>OLD.release_id OR NEW.environment_id<>OLD.environment_id OR NEW.source_commit<>OLD.source_commit
   OR NEW.manifest_artifact_id<>OLD.manifest_artifact_id OR NEW.manifest_sha256<>OLD.manifest_sha256 OR NEW.approval_id<>OLD.approval_id
   OR NEW.created_by<>OLD.created_by OR NEW.created_at<>OLD.created_at OR NEW.classification<>OLD.classification
   OR OLD.status<>'ACTIVE' OR NEW.status<>'REVOKED' OR NEW.revision<>OLD.revision+1 OR NEW.updated_at<OLD.updated_at
  THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='FRAMEWORK_RELEASE_TRANSITION_INVALID'; END IF;
 END IF;
END;

--changeset medical-agent:176-09-release-delete dbms:mysql splitStatements:false runInTransaction:false failOnError:true
CREATE TRIGGER trg_framework_release_delete BEFORE DELETE ON management_binding_release FOR EACH ROW
BEGIN
 IF EXISTS(SELECT 1 FROM framework_binding_manifest WHERE tenant_id=OLD.tenant_id AND release_id=OLD.release_id)
 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='FRAMEWORK_BINDING_HISTORY_IMMUTABLE'; END IF;
END;
