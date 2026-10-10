--liquibase formatted sql

--changeset medical-agent:1.0.0-222-template-approval dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE template_approval_decision (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  decision_id CHAR(36) CHARACTER SET ascii NOT NULL,
  operation_id CHAR(36) CHARACTER SET ascii NOT NULL,
  template_version CHAR(36) CHARACTER SET ascii NOT NULL,
  template_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  decision VARCHAR(24) NOT NULL,
  from_status VARCHAR(24) NOT NULL,
  to_status VARCHAR(24) NOT NULL,
  expected_revision BIGINT UNSIGNED NOT NULL,
  resulting_revision BIGINT UNSIGNED NOT NULL,
  reason TEXT NOT NULL,
  request_json JSON NOT NULL,
  baseline_manifest JSON NOT NULL,
  baseline_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  dependency_authorizations JSON NOT NULL,
  receipt_json JSON NOT NULL,
  receipt_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  authority_grant CHAR(36) CHARACTER SET ascii NOT NULL,
  authority_revision BIGINT UNSIGNED NOT NULL,
  auth_epoch BIGINT UNSIGNED NOT NULL,
  governance_epoch BIGINT UNSIGNED NOT NULL,
  schema_version VARCHAR(32) NOT NULL,
  schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL,
  created_at DATETIME(6) NOT NULL,
  classification VARCHAR(24) NOT NULL,
  PRIMARY KEY (tenant_id,decision_id),
  UNIQUE KEY uq_template_approval_operation (tenant_id,operation_id),
  UNIQUE KEY uq_template_approval_revision (tenant_id,template_version,resulting_revision),
  CONSTRAINT fk_template_approval_template FOREIGN KEY (tenant_id,template_version,template_sha256)
    REFERENCES template_version (tenant_id,template_version,content_sha256),
  CONSTRAINT fk_template_approval_operation FOREIGN KEY (tenant_id,operation_id) REFERENCES operation_record (tenant_id,operation_id),
  CONSTRAINT fk_template_approval_actor FOREIGN KEY (tenant_id,created_by) REFERENCES tenant_member (tenant_id,user_id),
  CONSTRAINT fk_template_approval_authority FOREIGN KEY (tenant_id,authority_grant) REFERENCES permission_grant (tenant_id,grant_id),
  CONSTRAINT ck_template_approval_revision CHECK (expected_revision > 0 AND resulting_revision = expected_revision + 1
    AND authority_revision > 0 AND auth_epoch > 0 AND governance_epoch > 0),
  CONSTRAINT ck_template_approval_reason CHECK (CHAR_LENGTH(reason) BETWEEN 1 AND 2000),
  CONSTRAINT ck_template_approval_state CHECK (
    (decision='APPROVE' AND from_status IN ('DRAFT','RETURNED') AND to_status='APPROVED') OR
    (decision='RETURN' AND from_status IN ('DRAFT','APPROVED') AND to_status='RETURNED') OR
    (decision='DISABLE' AND from_status IN ('DRAFT','APPROVED','RETURNED') AND to_status='DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
