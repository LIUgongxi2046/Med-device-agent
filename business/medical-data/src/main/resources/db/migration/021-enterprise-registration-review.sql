--liquibase formatted sql

--changeset medical-agent:1.0.0-179-enterprise-registration-decision dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE platform_enterprise_registration_decision (
  registration_id CHAR(36) CHARACTER SET ascii NOT NULL,
  operation_id CHAR(36) CHARACTER SET ascii NOT NULL,
  subject_id CHAR(36) CHARACTER SET ascii NOT NULL,
  idempotency_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  request_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  approved_company_sha256 CHAR(64) CHARACTER SET ascii NULL,
  decision VARCHAR(16) COLLATE utf8mb4_0900_bin NOT NULL,
  tenant_id CHAR(36) CHARACTER SET ascii NULL,
  member_id CHAR(36) CHARACTER SET ascii NULL,
  content_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  response_ref JSON NOT NULL,
  schema_version VARCHAR(32) NOT NULL,
  schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL,
  created_at DATETIME(6) NOT NULL,
  classification VARCHAR(24) NOT NULL,
  PRIMARY KEY (registration_id),
  UNIQUE KEY uq_registration_decision_operation (operation_id),
  UNIQUE KEY uq_registration_decision_request (subject_id,idempotency_key),
  UNIQUE KEY uq_registration_approved_company (approved_company_sha256),
  UNIQUE KEY uq_registration_decision_audit (registration_id,operation_id,subject_id),
  FOREIGN KEY (registration_id) REFERENCES platform_enterprise_registration (registration_id),
  FOREIGN KEY (subject_id) REFERENCES platform_local_account (user_id),
  FOREIGN KEY (created_by) REFERENCES platform_local_account (user_id),
  FOREIGN KEY (tenant_id,member_id) REFERENCES tenant_member (tenant_id,member_id),
  CHECK (decision IN ('APPROVE','REJECT')),
  CHECK ((decision='APPROVE' AND tenant_id IS NOT NULL AND member_id IS NOT NULL AND approved_company_sha256 IS NOT NULL)
    OR (decision='REJECT' AND tenant_id IS NULL AND member_id IS NULL AND approved_company_sha256 IS NULL)),
  CHECK (CHAR_LENGTH(idempotency_key) BETWEEN 16 AND 128),
  CHECK (JSON_TYPE(response_ref)='OBJECT'),
  CHECK (created_by=subject_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-180-enterprise-registration-decision-audit dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE platform_enterprise_registration_decision_audit (
  audit_id CHAR(36) CHARACTER SET ascii NOT NULL,
  registration_id CHAR(36) CHARACTER SET ascii NOT NULL,
  operation_id CHAR(36) CHARACTER SET ascii NOT NULL,
  subject_id CHAR(36) CHARACTER SET ascii NOT NULL,
  resource_ref JSON NOT NULL,
  schema_version VARCHAR(32) NOT NULL,
  schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL,
  created_at DATETIME(6) NOT NULL,
  classification VARCHAR(24) NOT NULL,
  PRIMARY KEY (audit_id),
  UNIQUE KEY uq_registration_decision_audit_record (registration_id),
  UNIQUE KEY uq_registration_decision_audit_operation (operation_id),
  FOREIGN KEY (registration_id,operation_id,subject_id)
    REFERENCES platform_enterprise_registration_decision (registration_id,operation_id,subject_id),
  CHECK (JSON_TYPE(resource_ref)='OBJECT'),
  CHECK (created_by=subject_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
