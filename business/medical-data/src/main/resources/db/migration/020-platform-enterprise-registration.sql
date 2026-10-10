--liquibase formatted sql

--changeset medical-agent:1.0.0-177-enterprise-registration dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE platform_enterprise_registration (
  registration_id CHAR(36) CHARACTER SET ascii NOT NULL,
  operation_id CHAR(36) CHARACTER SET ascii NOT NULL,
  subject_id CHAR(36) CHARACTER SET ascii NOT NULL,
  idempotency_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  request_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  company_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  request_body JSON NOT NULL,
  first_admin_user_id CHAR(36) CHARACTER SET ascii NOT NULL,
  first_admin_revision BIGINT UNSIGNED NOT NULL,
  content_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  response_ref JSON NOT NULL,
  schema_version VARCHAR(32) NOT NULL,
  schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  status VARCHAR(24) NOT NULL,
  revision BIGINT UNSIGNED NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL,
  created_at DATETIME(6) NOT NULL,
  classification VARCHAR(24) NOT NULL,
  PRIMARY KEY (registration_id),
  UNIQUE KEY uq_enterprise_registration_operation (operation_id),
  UNIQUE KEY uq_enterprise_registration_request (subject_id,idempotency_key),
  UNIQUE KEY uq_enterprise_registration_company (subject_id,company_sha256),
  UNIQUE KEY uq_enterprise_registration_audit_binding (registration_id,operation_id,subject_id,first_admin_user_id),
  FOREIGN KEY (subject_id) REFERENCES platform_local_account (user_id),
  FOREIGN KEY (first_admin_user_id) REFERENCES platform_local_account (user_id),
  FOREIGN KEY (created_by) REFERENCES platform_local_account (user_id),
  CHECK (status = 'PENDING_REVIEW'),
  CHECK (revision = 1 AND first_admin_revision > 0),
  CHECK (CHAR_LENGTH(idempotency_key) BETWEEN 16 AND 128),
  CHECK (JSON_TYPE(request_body) = 'OBJECT' AND JSON_TYPE(response_ref) = 'OBJECT'),
  CHECK (created_by = subject_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

--changeset medical-agent:1.0.0-178-enterprise-registration-audit dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE platform_enterprise_registration_audit (
  audit_id CHAR(36) CHARACTER SET ascii NOT NULL,
  registration_id CHAR(36) CHARACTER SET ascii NOT NULL,
  operation_id CHAR(36) CHARACTER SET ascii NOT NULL,
  subject_id CHAR(36) CHARACTER SET ascii NOT NULL,
  first_admin_user_id CHAR(36) CHARACTER SET ascii NOT NULL,
  action VARCHAR(64) CHARACTER SET ascii NOT NULL,
  resource_ref JSON NOT NULL,
  schema_version VARCHAR(32) NOT NULL,
  schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL,
  created_at DATETIME(6) NOT NULL,
  classification VARCHAR(24) NOT NULL,
  PRIMARY KEY (audit_id),
  UNIQUE KEY uq_enterprise_registration_audit (registration_id),
  UNIQUE KEY uq_enterprise_registration_audit_operation (operation_id),
  FOREIGN KEY (registration_id,operation_id,subject_id,first_admin_user_id)
    REFERENCES platform_enterprise_registration (registration_id,operation_id,subject_id,first_admin_user_id),
  FOREIGN KEY (created_by) REFERENCES platform_local_account (user_id),
  CHECK (action = 'LOCAL-REG-01'),
  CHECK (JSON_TYPE(resource_ref) = 'OBJECT'),
  CHECK (created_by = subject_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;
