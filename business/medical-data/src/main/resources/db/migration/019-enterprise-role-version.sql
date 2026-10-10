--liquibase formatted sql

--changeset medical-agent:1.0.0-176-enterprise-role-version dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE enterprise_role_version (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  version_id CHAR(36) CHARACTER SET ascii NOT NULL,
  role_id CHAR(36) CHARACTER SET ascii NOT NULL,
  definition JSON NOT NULL,
  status VARCHAR(24) COLLATE utf8mb4_0900_bin NOT NULL,
  approved_by CHAR(36) CHARACTER SET ascii NOT NULL,
  approved_at DATETIME(6) NOT NULL,
  valid_from DATETIME(6) NOT NULL,
  valid_to DATETIME(6) NULL,
  schema_version VARCHAR(32) NOT NULL,
  schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL,
  created_at DATETIME(6) NOT NULL,
  classification VARCHAR(24) NOT NULL,
  revision BIGINT UNSIGNED NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (tenant_id, version_id),
  KEY ix_role_current (tenant_id, role_id, status, valid_to),
  CONSTRAINT fk_role_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (tenant_id),
  CONSTRAINT fk_role_version FOREIGN KEY (tenant_id, version_id) REFERENCES version_registry (tenant_id, version_id),
  CONSTRAINT fk_role_approver FOREIGN KEY (tenant_id, approved_by) REFERENCES tenant_member (tenant_id, user_id),
  CONSTRAINT ck_role_status CHECK (status IN ('ACTIVE','REVOKED')),
  CONSTRAINT ck_role_definition CHECK (JSON_TYPE(definition)='OBJECT'),
  CONSTRAINT ck_role_validity CHECK (valid_to IS NULL OR valid_to>valid_from),
  CONSTRAINT ck_role_approval CHECK (approved_at>=created_at),
  CONSTRAINT ck_role_revision CHECK (revision>0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
