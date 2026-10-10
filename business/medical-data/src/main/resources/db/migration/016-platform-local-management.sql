--liquibase formatted sql

--changeset medical-agent:1.0.0-164-local-operation dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE platform_local_operation (
  operation_id CHAR(36) CHARACTER SET ascii NOT NULL,
  subject_id CHAR(36) CHARACTER SET ascii NOT NULL,
  operation_name VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  idempotency_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  request_hmac_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  hmac_key_id CHAR(36) CHARACTER SET ascii NOT NULL,
  http_status SMALLINT NOT NULL,
  response_ref JSON NOT NULL,
  target_id CHAR(36) CHARACTER SET ascii NOT NULL,
  schema_version VARCHAR(32) NOT NULL,
  schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL,
  created_at DATETIME(6) NOT NULL,
  classification VARCHAR(24) NOT NULL,
  PRIMARY KEY (operation_id),
  UNIQUE KEY uq_local_operation_request (subject_id,operation_name,idempotency_key),
  FOREIGN KEY (subject_id) REFERENCES platform_local_account (user_id),
  FOREIGN KEY (target_id) REFERENCES platform_local_account (user_id),
  FOREIGN KEY (created_by) REFERENCES platform_local_account (user_id),
  CHECK (http_status IN (200,201)),
  CHECK (CHAR_LENGTH(idempotency_key) BETWEEN 16 AND 128)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

--changeset medical-agent:1.0.0-165-local-audit dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE platform_local_audit (
  audit_id CHAR(36) CHARACTER SET ascii NOT NULL,
  operation_id CHAR(36) CHARACTER SET ascii NOT NULL,
  subject_id CHAR(36) CHARACTER SET ascii NOT NULL,
  target_id CHAR(36) CHARACTER SET ascii NOT NULL,
  action VARCHAR(64) CHARACTER SET ascii NOT NULL,
  resource_ref JSON NOT NULL,
  schema_version VARCHAR(32) NOT NULL,
  schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL,
  created_at DATETIME(6) NOT NULL,
  classification VARCHAR(24) NOT NULL,
  PRIMARY KEY (audit_id),
  UNIQUE KEY uq_local_audit_operation (operation_id),
  KEY ix_local_audit_subject (subject_id,created_at,audit_id),
  KEY ix_local_audit_target (target_id,created_at,audit_id),
  FOREIGN KEY (operation_id) REFERENCES platform_local_operation (operation_id),
  FOREIGN KEY (subject_id) REFERENCES platform_local_account (user_id),
  FOREIGN KEY (target_id) REFERENCES platform_local_account (user_id),
  FOREIGN KEY (created_by) REFERENCES platform_local_account (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;
