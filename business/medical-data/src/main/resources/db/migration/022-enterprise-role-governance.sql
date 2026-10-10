--liquibase formatted sql

--changeset medical-agent:1.0.0-181-enterprise-role-head dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE enterprise_role_head (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  role_id CHAR(36) CHARACTER SET ascii NOT NULL,
  current_version CHAR(36) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL,
  created_at DATETIME(6) NOT NULL,
  classification VARCHAR(24) NOT NULL,
  revision BIGINT UNSIGNED NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (tenant_id,role_id),
  CONSTRAINT fk_role_head_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (tenant_id),
  CONSTRAINT fk_role_head_version FOREIGN KEY (tenant_id,current_version) REFERENCES enterprise_role_version (tenant_id,version_id),
  CONSTRAINT ck_role_head_revision CHECK (revision>0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-182-enterprise-role-publication dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE enterprise_role_publication (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  version_id CHAR(36) CHARACTER SET ascii NOT NULL,
  name VARCHAR(120) NOT NULL,
  approval_basis CHAR(36) CHARACTER SET ascii NOT NULL,
  approval_basis_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  approval_reason TEXT NOT NULL,
  authority_grant CHAR(36) CHARACTER SET ascii NOT NULL,
  authority_revision BIGINT UNSIGNED NOT NULL,
  schema_version VARCHAR(32) NOT NULL,
  schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL,
  created_at DATETIME(6) NOT NULL,
  classification VARCHAR(24) NOT NULL,
  revision BIGINT UNSIGNED NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (tenant_id,version_id),
  CONSTRAINT fk_role_publication_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (tenant_id),
  CONSTRAINT fk_role_publication_version FOREIGN KEY (tenant_id,version_id) REFERENCES enterprise_role_version (tenant_id,version_id),
  CONSTRAINT fk_role_publication_basis FOREIGN KEY (tenant_id,approval_basis) REFERENCES version_registry (tenant_id,version_id),
  CONSTRAINT fk_role_publication_authority FOREIGN KEY (tenant_id,authority_grant) REFERENCES permission_grant (tenant_id,grant_id),
  CONSTRAINT ck_role_publication_name CHECK (CHAR_LENGTH(TRIM(name))>0),
  CONSTRAINT ck_role_publication_reason CHECK (CHAR_LENGTH(TRIM(approval_reason))>0),
  CONSTRAINT ck_role_publication_basis_sha CHECK (REGEXP_LIKE(approval_basis_sha256,'^[0-9a-f]{64}$','c')),
  CONSTRAINT ck_role_publication_revisions CHECK (revision=1 AND authority_revision>0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-183-member-role-grant dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE member_role_grant (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  grant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  role_version CHAR(36) CHARACTER SET ascii NOT NULL,
  invitation_id CHAR(36) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL,
  created_at DATETIME(6) NOT NULL,
  classification VARCHAR(24) NOT NULL,
  revision BIGINT UNSIGNED NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (tenant_id,grant_id),
  KEY ix_role_grant_version (tenant_id,role_version,grant_id),
  KEY ix_role_grant_invitation (tenant_id,invitation_id,grant_id),
  CONSTRAINT fk_role_grant_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (tenant_id),
  CONSTRAINT fk_role_grant_permission FOREIGN KEY (tenant_id,grant_id) REFERENCES permission_grant (tenant_id,grant_id),
  CONSTRAINT fk_role_grant_version FOREIGN KEY (tenant_id,role_version) REFERENCES enterprise_role_version (tenant_id,version_id),
  CONSTRAINT fk_role_grant_invitation FOREIGN KEY (tenant_id,invitation_id) REFERENCES member_invitation (tenant_id,invitation_id),
  CONSTRAINT ck_role_grant_revision CHECK (revision=1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
