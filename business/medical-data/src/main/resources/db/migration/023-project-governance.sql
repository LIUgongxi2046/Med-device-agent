--liquibase formatted sql

--changeset medical-agent:1.0.0-184-project-record dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE project_record (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  project_id CHAR(36) CHARACTER SET ascii NOT NULL,
  name VARCHAR(256) NOT NULL,
  responsible_user_id CHAR(36) CHARACTER SET ascii NOT NULL,
  current_profile_revision CHAR(36) CHARACTER SET ascii NOT NULL,
  creation_basis CHAR(36) CHARACTER SET ascii NOT NULL,
  creation_basis_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  creation_reason TEXT NOT NULL,
  authority_grant CHAR(36) CHARACTER SET ascii NOT NULL,
  authority_revision BIGINT UNSIGNED NOT NULL,
  schema_version VARCHAR(32) NOT NULL,
  schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL,
  created_at DATETIME(6) NOT NULL,
  classification VARCHAR(24) NOT NULL,
  revision BIGINT UNSIGNED NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (tenant_id,project_id),
  KEY ix_project_record_name (tenant_id,name,project_id),
  KEY ix_project_record_responsible (tenant_id,responsible_user_id,project_id),
  CONSTRAINT fk_project_record_project FOREIGN KEY (tenant_id,project_id) REFERENCES project (tenant_id,project_id),
  CONSTRAINT fk_project_record_profile FOREIGN KEY (tenant_id,project_id,current_profile_revision) REFERENCES project_profile_revision (tenant_id,project_id,profile_revision),
  CONSTRAINT fk_project_record_responsible FOREIGN KEY (tenant_id,responsible_user_id) REFERENCES tenant_member (tenant_id,user_id),
  CONSTRAINT fk_project_record_basis FOREIGN KEY (tenant_id,creation_basis) REFERENCES version_registry (tenant_id,version_id),
  CONSTRAINT fk_project_record_authority FOREIGN KEY (tenant_id,authority_grant) REFERENCES permission_grant (tenant_id,grant_id),
  CHECK (CHAR_LENGTH(name) BETWEEN 1 AND 256),
  CHECK (CHAR_LENGTH(creation_reason) BETWEEN 1 AND 2000),
  CHECK (revision > 0 AND authority_revision > 0),
  CHECK (creation_basis_sha256 REGEXP '^[0-9a-f]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-185-project-state-decision dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE project_state_decision (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  project_id CHAR(36) CHARACTER SET ascii NOT NULL,
  decision_id CHAR(36) CHARACTER SET ascii NOT NULL,
  operation_id CHAR(36) CHARACTER SET ascii NOT NULL,
  decision VARCHAR(24) NOT NULL,
  from_status VARCHAR(24) NOT NULL,
  to_status VARCHAR(24) NOT NULL,
  expected_revision BIGINT UNSIGNED NOT NULL,
  resulting_revision BIGINT UNSIGNED NOT NULL,
  profile_revision CHAR(36) CHARACTER SET ascii NOT NULL,
  reason TEXT NOT NULL,
  impact JSON NOT NULL,
  authority_grant CHAR(36) CHARACTER SET ascii NOT NULL,
  authority_revision BIGINT UNSIGNED NOT NULL,
  schema_version VARCHAR(32) NOT NULL,
  schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL,
  created_at DATETIME(6) NOT NULL,
  classification VARCHAR(24) NOT NULL,
  PRIMARY KEY (tenant_id,project_id,decision_id),
  UNIQUE KEY uq_project_state_operation (tenant_id,operation_id),
  CONSTRAINT fk_project_state_project FOREIGN KEY (tenant_id,project_id) REFERENCES project (tenant_id,project_id),
  CONSTRAINT fk_project_state_profile FOREIGN KEY (tenant_id,project_id,profile_revision) REFERENCES project_profile_revision (tenant_id,project_id,profile_revision),
  CONSTRAINT fk_project_state_operation FOREIGN KEY (tenant_id,operation_id) REFERENCES operation_record (tenant_id,operation_id),
  CONSTRAINT fk_project_state_authority FOREIGN KEY (tenant_id,authority_grant) REFERENCES permission_grant (tenant_id,grant_id),
  CHECK (expected_revision > 0 AND resulting_revision=expected_revision+1 AND authority_revision > 0),
  CHECK (CHAR_LENGTH(reason) BETWEEN 1 AND 2000),
  CHECK ((decision='ACTIVATE' AND from_status='PENDING' AND to_status='ACTIVE') OR
         (decision='PAUSE' AND from_status='ACTIVE' AND to_status='PAUSED') OR
         (decision='ARCHIVE' AND from_status IN ('PENDING','ACTIVE','PAUSED') AND to_status='ARCHIVED') OR
         (decision='RESTORE' AND from_status IN ('PAUSED','ARCHIVED') AND to_status='ACTIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
