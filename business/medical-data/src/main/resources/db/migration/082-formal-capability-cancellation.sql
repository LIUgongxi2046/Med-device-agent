--liquibase formatted sql

--changeset medical-agent:1.0.0-082-formal-capability-cancellation dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE formal_capability_cancellation (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
 project_id CHAR(36) CHARACTER SET ascii NULL,
 cancellation_id CHAR(36) CHARACTER SET ascii NOT NULL,
 fixed_json JSON NOT NULL,
 content_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 operation_id CHAR(36) CHARACTER SET ascii NOT NULL,
 cancel_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 original_kind VARCHAR(32) NOT NULL,
 original_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 original_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(32) NOT NULL,
 schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 classification VARCHAR(24) NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL,
 created_at DATETIME(6) NOT NULL,
 PRIMARY KEY(tenant_id,cancellation_id),
 UNIQUE KEY uq_capability_cancel_key(tenant_id,created_by,cancel_key),
 UNIQUE KEY uq_capability_cancel_original(tenant_id,created_by,original_kind,original_key),
 UNIQUE KEY uq_capability_cancel_operation(tenant_id,operation_id),
 FOREIGN KEY(tenant_id) REFERENCES tenant(tenant_id),
 FOREIGN KEY(tenant_id,project_id) REFERENCES project(tenant_id,project_id),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 FOREIGN KEY(tenant_id,operation_id) REFERENCES operation_record(tenant_id,operation_id),
 CONSTRAINT chk_capability_cancel_kind CHECK(original_kind='FORMAL_CAPABILITY_AUTHOR'),
 CONSTRAINT chk_capability_cancel_json CHECK(JSON_TYPE(fixed_json)='OBJECT')
) ENGINE=InnoDB;
