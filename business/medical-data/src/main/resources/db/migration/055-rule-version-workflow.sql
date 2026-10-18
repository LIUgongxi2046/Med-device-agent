--liquibase formatted sql

--changeset medical-agent:1.0.0-305-rule-version-digest dbms:mysql runInTransaction:false failOnError:true
ALTER TABLE rule_version
  ADD UNIQUE KEY uq_rule_version_digest (tenant_id,rule_version,content_sha256),
  ADD CONSTRAINT fk_rule_version_digest FOREIGN KEY (tenant_id,rule_version,content_sha256)
    REFERENCES version_registry (tenant_id,version_id,content_sha256);

--changeset medical-agent:1.0.0-306-rule-definition dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE rule_definition (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  rule_version CHAR(36) CHARACTER SET ascii NOT NULL,
  content_json JSON NOT NULL,
  content_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  base_version CHAR(36) CHARACTER SET ascii NULL,
  base_version_sha256 CHAR(64) CHARACTER SET ascii NULL,
  reason TEXT NOT NULL,
  authority_grant CHAR(36) CHARACTER SET ascii NOT NULL,
  authority_revision BIGINT UNSIGNED NOT NULL,
  schema_version VARCHAR(32) NOT NULL,
  schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL,
  created_at DATETIME(6) NOT NULL,
  classification VARCHAR(24) NOT NULL,
  revision BIGINT UNSIGNED NOT NULL,
  PRIMARY KEY (tenant_id,rule_version),
  CONSTRAINT fk_rule_definition_version FOREIGN KEY (tenant_id,rule_version,content_sha256)
    REFERENCES rule_version (tenant_id,rule_version,content_sha256),
  CONSTRAINT fk_rule_definition_base FOREIGN KEY (tenant_id,base_version,base_version_sha256)
    REFERENCES rule_version (tenant_id,rule_version,content_sha256),
  CONSTRAINT fk_rule_definition_creator FOREIGN KEY (tenant_id,created_by) REFERENCES tenant_member (tenant_id,user_id),
  CONSTRAINT fk_rule_definition_authority FOREIGN KEY (tenant_id,authority_grant) REFERENCES permission_grant (tenant_id,grant_id),
  CONSTRAINT ck_rule_definition_revision CHECK (revision > 0 AND authority_revision > 0),
  CONSTRAINT ck_rule_definition_reason CHECK (CHAR_LENGTH(reason) BETWEEN 1 AND 2000),
  CONSTRAINT ck_rule_definition_base CHECK (
    (base_version IS NULL AND base_version_sha256 IS NULL) OR
    (base_version IS NOT NULL AND base_version_sha256 IS NOT NULL AND base_version <> rule_version)),
  CONSTRAINT ck_rule_definition_content CHECK (
    JSON_TYPE(content_json) = 'OBJECT' AND JSON_LENGTH(content_json) = 4
    AND JSON_CONTAINS_PATH(content_json,'all','$.schema_version','$.engine_revision','$.expression','$.basis_manifest')
    AND JSON_UNQUOTE(JSON_EXTRACT(content_json,'$.schema_version')) <=> schema_version
    AND JSON_TYPE(JSON_EXTRACT(content_json,'$.expression')) = 'OBJECT'
    AND JSON_TYPE(JSON_EXTRACT(content_json,'$.basis_manifest')) = 'ARRAY'
    AND JSON_LENGTH(JSON_EXTRACT(content_json,'$.basis_manifest')) > 0
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-307-rule-approval dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE rule_approval_decision (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  decision_id CHAR(36) CHARACTER SET ascii NOT NULL,
  operation_id CHAR(36) CHARACTER SET ascii NOT NULL,
  rule_version CHAR(36) CHARACTER SET ascii NOT NULL,
  rule_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
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
  UNIQUE KEY uq_rule_approval_operation (tenant_id,operation_id),
  UNIQUE KEY uq_rule_approval_revision (tenant_id,rule_version,resulting_revision),
  CONSTRAINT fk_rule_approval_rule FOREIGN KEY (tenant_id,rule_version,rule_sha256)
    REFERENCES rule_version (tenant_id,rule_version,content_sha256),
  CONSTRAINT fk_rule_approval_operation FOREIGN KEY (tenant_id,operation_id) REFERENCES operation_record (tenant_id,operation_id),
  CONSTRAINT fk_rule_approval_actor FOREIGN KEY (tenant_id,created_by) REFERENCES tenant_member (tenant_id,user_id),
  CONSTRAINT fk_rule_approval_authority FOREIGN KEY (tenant_id,authority_grant) REFERENCES permission_grant (tenant_id,grant_id),
  CONSTRAINT ck_rule_approval_revision CHECK (expected_revision > 0 AND resulting_revision = expected_revision + 1
    AND authority_revision > 0 AND auth_epoch > 0 AND governance_epoch > 0),
  CONSTRAINT ck_rule_approval_reason CHECK (CHAR_LENGTH(reason) BETWEEN 1 AND 2000),
  CONSTRAINT ck_rule_approval_state CHECK (
    (decision='APPROVE' AND from_status IN ('DRAFT','RETURNED') AND to_status='APPROVED') OR
    (decision='RETURN' AND from_status IN ('DRAFT','APPROVED') AND to_status='RETURNED') OR
    (decision='DISABLE' AND from_status IN ('DRAFT','APPROVED','RETURNED') AND to_status='DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
