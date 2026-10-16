--liquibase formatted sql

--changeset medical-agent:1.0.0-263-source-lifecycle dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE retention_policy_revision (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  policy_revision CHAR(36) CHARACTER SET ascii NOT NULL,
  category VARCHAR(32) NOT NULL,
  retention_duration BIGINT UNSIGNED NOT NULL,
  start_event VARCHAR(32) NOT NULL,
  hold_override BOOLEAN NOT NULL,
  approver_id CHAR(36) CHARACTER SET ascii NOT NULL,
  effective_from DATETIME(6) NOT NULL,
  purge_method VARCHAR(32) NOT NULL,
  backup_expiry BIGINT UNSIGNED NOT NULL,
  status VARCHAR(24) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  revision BIGINT UNSIGNED NOT NULL,
  PRIMARY KEY (tenant_id, policy_revision),
  KEY ix_retention_policy_effective (tenant_id, category, status, effective_from),
  FOREIGN KEY (tenant_id, policy_revision) REFERENCES version_registry (tenant_id, version_id),
  FOREIGN KEY (tenant_id, approver_id) REFERENCES tenant_member (tenant_id, user_id),
  CHECK (category IN ('SOURCE')),
  CHECK (start_event IN ('SOURCE_CREATED')),
  CHECK (purge_method IN ('CRYPTOGRAPHIC_ERASURE','VERIFIED_DELETE')),
  CHECK (status IN ('APPROVED','DISABLED')),
  CHECK (hold_override IN (0,1)),
  CHECK (retention_duration > 0 AND backup_expiry > 0 AND revision > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE source_lifecycle_decision (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  source_version CHAR(36) CHARACTER SET ascii NOT NULL,
  decision_id CHAR(36) CHARACTER SET ascii NOT NULL,
  decision VARCHAR(24) NOT NULL,
  from_status VARCHAR(24) NOT NULL,
  to_status VARCHAR(24) NOT NULL,
  reason TEXT NOT NULL,
  policy_revision CHAR(36) CHARACTER SET ascii NULL,
  deletion_id CHAR(36) CHARACTER SET ascii NULL,
  actor_id CHAR(36) CHARACTER SET ascii NOT NULL,
  decided_at DATETIME(6) NOT NULL,
  expected_revision BIGINT UNSIGNED NOT NULL,
  resulting_revision BIGINT UNSIGNED NOT NULL,
  schema_version VARCHAR(32) NOT NULL,
  schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  classification VARCHAR(24) NOT NULL,
  PRIMARY KEY (tenant_id, decision_id),
  UNIQUE KEY uq_source_lifecycle_decision (tenant_id, source_version, decision_id),
  KEY ix_source_lifecycle_history (tenant_id, source_version, decided_at, decision_id),
  FOREIGN KEY (tenant_id, source_version) REFERENCES source_version (tenant_id, source_version),
  FOREIGN KEY (tenant_id, decision_id) REFERENCES version_registry (tenant_id, version_id),
  FOREIGN KEY (tenant_id, policy_revision) REFERENCES retention_policy_revision (tenant_id, policy_revision),
  FOREIGN KEY (tenant_id, deletion_id) REFERENCES deletion_job (tenant_id, deletion_id),
  FOREIGN KEY (tenant_id, actor_id) REFERENCES tenant_member (tenant_id, user_id),
  CHECK (decision IN ('DISABLE','RECYCLE','RESTORE','PURGE')),
  CHECK (from_status IN ('ACTIVE','DISABLED','RECYCLED','PURGE_PENDING','PURGED')),
  CHECK (to_status IN ('ACTIVE','DISABLED','RECYCLED','PURGE_PENDING','PURGED')),
  CHECK ((decision='PURGE' AND policy_revision IS NOT NULL AND deletion_id IS NOT NULL)
      OR (decision<>'PURGE' AND policy_revision IS NULL AND deletion_id IS NULL)),
  CHECK (resulting_revision = expected_revision + 1),
  CHECK (schema_version = '1.0.0')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE source_lifecycle_state (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  source_version CHAR(36) CHARACTER SET ascii NOT NULL,
  status VARCHAR(24) NOT NULL,
  last_decision_id CHAR(36) CHARACTER SET ascii NULL,
  revision BIGINT UNSIGNED NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (tenant_id, source_version),
  KEY ix_source_lifecycle_status (tenant_id, status, updated_at),
  FOREIGN KEY (tenant_id, source_version) REFERENCES source_version (tenant_id, source_version),
  FOREIGN KEY (tenant_id, source_version, last_decision_id)
    REFERENCES source_lifecycle_decision (tenant_id, source_version, decision_id),
  CHECK (status IN ('ACTIVE','DISABLED','RECYCLED','PURGE_PENDING','PURGED')),
  CHECK (revision > 0),
  CHECK ((last_decision_id IS NULL AND revision=1) OR (last_decision_id IS NOT NULL AND revision>1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

INSERT INTO source_lifecycle_state(tenant_id,source_version,status,last_decision_id,revision,updated_at)
SELECT tenant_id,source_version,'ACTIVE',NULL,1,created_at FROM source_version;
