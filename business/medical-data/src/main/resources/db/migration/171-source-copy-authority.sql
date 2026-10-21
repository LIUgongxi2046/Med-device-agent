--liquibase formatted sql
--changeset medical-agent:1.0.0-171-source-copy-authority dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE source_copy_authority (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
 authority_id CHAR(36) CHARACTER SET ascii NOT NULL,
 source_version CHAR(36) CHARACTER SET ascii NOT NULL,
 project_id CHAR(36) CHARACTER SET ascii NULL,
 declaration_id CHAR(36) CHARACTER SET ascii NULL,
 decision_kind VARCHAR(16) NOT NULL,
 fixed_json JSON NOT NULL,
 content_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 request_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 idempotency_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL,
 created_at DATETIME(6) NOT NULL,
 classification VARCHAR(24) NOT NULL,
 schema_version VARCHAR(16) NOT NULL,
 schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 audit_id CHAR(36) CHARACTER SET ascii NOT NULL,
 event_id CHAR(36) CHARACTER SET ascii NOT NULL,
 PRIMARY KEY(tenant_id,authority_id),
 UNIQUE KEY uq_source_copy_authority_owner(tenant_id,source_version,authority_id),
 UNIQUE KEY uq_source_copy_authority_key(tenant_id,created_by,idempotency_key),
 UNIQUE KEY uq_source_copy_authority_decision(tenant_id,declaration_id,decision_kind),
 KEY ix_source_copy_authority_source(tenant_id,source_version,created_at),
 FOREIGN KEY(tenant_id) REFERENCES tenant(tenant_id),
 FOREIGN KEY(tenant_id,source_version) REFERENCES source_version(tenant_id,source_version),
 FOREIGN KEY(tenant_id,project_id) REFERENCES project(tenant_id,project_id),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 FOREIGN KEY(tenant_id,audit_id) REFERENCES audit_event(tenant_id,audit_id),
 FOREIGN KEY(tenant_id,event_id) REFERENCES outbox_event(tenant_id,event_id),
 FOREIGN KEY(tenant_id,source_version,declaration_id) REFERENCES source_copy_authority(tenant_id,source_version,authority_id),
 CHECK(decision_kind IN ('DECLARE','APPROVE','INCOMPLETE','REVOKE')),
 CHECK((decision_kind='DECLARE' AND declaration_id IS NULL) OR (decision_kind<>'DECLARE' AND declaration_id IS NOT NULL)),
 CHECK(schema_version='2.0.0'),
 CHECK(classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH')),
 CHECK(JSON_UNQUOTE(JSON_EXTRACT(fixed_json,'$.tenant_id'))=tenant_id),
 CHECK(JSON_UNQUOTE(JSON_EXTRACT(fixed_json,'$.classification'))=classification),
 CHECK(content_sha256 REGEXP '^[a-f0-9]{64}$' AND request_sha256 REGEXP '^[a-f0-9]{64}$' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK(JSON_UNQUOTE(JSON_EXTRACT(fixed_json,'$.authority_id'))=authority_id),
 CHECK(JSON_UNQUOTE(JSON_EXTRACT(fixed_json,'$.source_version'))=source_version),
 CHECK(JSON_UNQUOTE(JSON_EXTRACT(fixed_json,'$.created_by'))=created_by),
 CHECK(JSON_UNQUOTE(JSON_EXTRACT(fixed_json,'$.decision_kind'))=decision_kind)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-171-source-copy-authority-update dbms:mysql runInTransaction:false failOnError:true splitStatements:false
CREATE TRIGGER source_copy_authority_no_update BEFORE UPDATE ON source_copy_authority
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Source copy authority evidence is immutable';
--changeset medical-agent:1.0.0-171-source-copy-authority-delete dbms:mysql runInTransaction:false failOnError:true splitStatements:false
CREATE TRIGGER source_copy_authority_no_delete BEFORE DELETE ON source_copy_authority
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Source copy authority evidence is immutable';
