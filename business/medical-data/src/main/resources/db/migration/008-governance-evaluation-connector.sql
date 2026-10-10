--liquibase formatted sql

--changeset medical-agent:1.0.0-119-knowledge-submission dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE knowledge_submission (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NULL,
  submission_id CHAR(36) CHARACTER SET ascii NOT NULL, artifact_id CHAR(36) CHARACTER SET ascii NOT NULL,
  artifact_owner_project_id CHAR(36) CHARACTER SET ascii NULL,
  artifact_project_key VARCHAR(36) CHARACTER SET ascii GENERATED ALWAYS AS (COALESCE(artifact_owner_project_id, 'ENTERPRISE')) STORED,
  target_source_id CHAR(36) CHARACTER SET ascii NOT NULL, target_source_version CHAR(36) CHARACTER SET ascii NOT NULL,
  sharing_decision CHAR(36) CHARACTER SET ascii NOT NULL, original_evidence JSON NOT NULL, status VARCHAR(24) NOT NULL,
  schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
  revision BIGINT UNSIGNED NOT NULL, updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (tenant_id, submission_id),
  KEY ix_knowledge_submission_target (tenant_id, target_source_version, status),
  FOREIGN KEY (tenant_id) REFERENCES tenant (tenant_id),
  FOREIGN KEY (tenant_id, project_id) REFERENCES project (tenant_id, project_id),
  FOREIGN KEY (tenant_id, artifact_project_key, artifact_id) REFERENCES artifact (tenant_id, project_key, artifact_id),
  FOREIGN KEY (tenant_id, target_source_id, target_source_version) REFERENCES source_version (tenant_id, source_id, source_version),
  FOREIGN KEY (tenant_id, sharing_decision) REFERENCES version_registry (tenant_id, version_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-120-reference-hold dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE reference_hold (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NULL,
  hold_id CHAR(36) CHARACTER SET ascii NOT NULL, consumer_version_id CHAR(36) CHARACTER SET ascii NOT NULL,
  upstream_version_id CHAR(36) CHARACTER SET ascii NOT NULL, retain_until DATETIME(6) NULL, legal_hold BOOLEAN NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
  revision BIGINT UNSIGNED NOT NULL, updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (tenant_id, hold_id),
  UNIQUE KEY uq_reference_hold_pair (tenant_id, consumer_version_id, upstream_version_id),
  KEY ix_reference_hold_upstream (tenant_id, upstream_version_id, legal_hold, retain_until),
  FOREIGN KEY (tenant_id, project_id) REFERENCES project (tenant_id, project_id),
  FOREIGN KEY (tenant_id, consumer_version_id) REFERENCES version_registry (tenant_id, version_id),
  FOREIGN KEY (tenant_id, upstream_version_id) REFERENCES version_registry (tenant_id, version_id),
  CHECK (consumer_version_id <> upstream_version_id),
  CHECK (legal_hold IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-121-deletion-job dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE deletion_job (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NULL,
  deletion_id CHAR(36) CHARACTER SET ascii NOT NULL, target_version CHAR(36) CHARACTER SET ascii NOT NULL,
  policy_revision CHAR(36) CHARACTER SET ascii NOT NULL, status VARCHAR(24) NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
  revision BIGINT UNSIGNED NOT NULL, updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (tenant_id, deletion_id),
  UNIQUE KEY uq_deletion_target_policy (tenant_id, target_version, policy_revision),
  KEY ix_deletion_status (tenant_id, status, created_at),
  FOREIGN KEY (tenant_id, project_id) REFERENCES project (tenant_id, project_id),
  FOREIGN KEY (tenant_id, target_version) REFERENCES version_registry (tenant_id, version_id),
  FOREIGN KEY (tenant_id, policy_revision) REFERENCES version_registry (tenant_id, version_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-122-deletion-item dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE deletion_item (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, item_id CHAR(36) CHARACTER SET ascii NOT NULL,
  deletion_id CHAR(36) CHARACTER SET ascii NOT NULL, store_kind VARCHAR(32) NOT NULL,
  target_ref JSON NOT NULL, target_sha256 CHAR(64) CHARACTER SET ascii NOT NULL, status VARCHAR(24) NOT NULL,
  receipt_ref JSON NULL, backup_expiry DATETIME(6) NULL,
  schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
  revision BIGINT UNSIGNED NOT NULL, updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (tenant_id, item_id),
  UNIQUE KEY uq_deletion_storage_target (tenant_id, deletion_id, store_kind, target_sha256),
  KEY ix_deletion_item_status (tenant_id, deletion_id, status, backup_expiry),
  FOREIGN KEY (tenant_id, deletion_id) REFERENCES deletion_job (tenant_id, deletion_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-123-evaluation-batch dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE evaluation_batch (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NULL,
  project_key VARCHAR(36) CHARACTER SET ascii GENERATED ALWAYS AS (COALESCE(project_id, 'ENTERPRISE')) STORED,
  evaluation_id CHAR(36) CHARACTER SET ascii NOT NULL, dataset_version CHAR(36) CHARACTER SET ascii NOT NULL,
  sample_manifest JSON NOT NULL, sample_manifest_artifact_id CHAR(36) CHARACTER SET ascii NOT NULL,
  rule_revision CHAR(36) CHARACTER SET ascii NOT NULL, release_candidates JSON NOT NULL,
  authorized_use JSON NOT NULL, split_manifest JSON NOT NULL, variance_policy JSON NOT NULL,
  missing_requirements JSON NOT NULL, status VARCHAR(24) NOT NULL, content_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
  revision BIGINT UNSIGNED NOT NULL, updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (tenant_id, evaluation_id),
  KEY ix_evaluation_status (tenant_id, project_id, status, created_at),
  FOREIGN KEY (tenant_id, project_id) REFERENCES project (tenant_id, project_id),
  FOREIGN KEY (tenant_id, dataset_version) REFERENCES version_registry (tenant_id, version_id),
  FOREIGN KEY (tenant_id, rule_revision) REFERENCES version_registry (tenant_id, version_id),
  FOREIGN KEY (tenant_id, project_key, sample_manifest_artifact_id) REFERENCES artifact (tenant_id, project_key, artifact_id),
  FOREIGN KEY (tenant_id, evaluation_id) REFERENCES version_registry (tenant_id, version_id),
  CHECK (status IN ('QUEUED','RUNNING','COMPLETED','FAILED','INSUFFICIENT_EVIDENCE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-124-evaluation-score dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE evaluation_score (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, score_id CHAR(36) CHARACTER SET ascii NOT NULL,
  evaluation_id CHAR(36) CHARACTER SET ascii NOT NULL, sample_id CHAR(36) CHARACTER SET ascii NOT NULL,
  agent_version_id CHAR(36) CHARACTER SET ascii NOT NULL, dimension VARCHAR(64) NOT NULL,
  value DECIMAL(7,4) NULL, value_text VARCHAR(256) NULL, numerator VARCHAR(256) NULL, denominator VARCHAR(256) NULL,
  rule_revision CHAR(36) CHARACTER SET ascii NOT NULL,
  input_ref JSON NOT NULL, output_ref JSON NULL, evidence_ref JSON NOT NULL,
  scorer_id CHAR(36) CHARACTER SET ascii NOT NULL, supersedes CHAR(36) CHARACTER SET ascii NULL, status VARCHAR(24) NOT NULL,
  schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
  PRIMARY KEY (tenant_id, score_id),
  UNIQUE KEY uq_score_comparison (tenant_id, evaluation_id, sample_id, agent_version_id, dimension, score_id),
  KEY ix_score_dimension (tenant_id, evaluation_id, dimension, sample_id),
  FOREIGN KEY (tenant_id, evaluation_id) REFERENCES evaluation_batch (tenant_id, evaluation_id),
  FOREIGN KEY (tenant_id, agent_version_id) REFERENCES agent_version (tenant_id, agent_version_id),
  FOREIGN KEY (tenant_id, rule_revision) REFERENCES version_registry (tenant_id, version_id),
  FOREIGN KEY (tenant_id, scorer_id) REFERENCES tenant_member (tenant_id, user_id),
  FOREIGN KEY (tenant_id, evaluation_id, sample_id, agent_version_id, dimension, supersedes)
    REFERENCES evaluation_score (tenant_id, evaluation_id, sample_id, agent_version_id, dimension, score_id),
  CHECK (status IN ('SCORED','UNKNOWN','BLOCKED')),
  CHECK (supersedes IS NULL OR supersedes <> score_id),
  CHECK (status <> 'SCORED' OR value IS NOT NULL OR value_text IS NOT NULL),
  CHECK ((numerator IS NULL AND denominator IS NULL) OR (numerator IS NOT NULL AND denominator IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-125-feedback dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE feedback (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NULL,
  feedback_id CHAR(36) CHARACTER SET ascii NOT NULL, target_version CHAR(36) CHARACTER SET ascii NOT NULL,
  task_id CHAR(36) CHARACTER SET ascii NULL, document_revision CHAR(36) CHARACTER SET ascii NULL,
  reason TEXT NOT NULL, actor_id CHAR(36) CHARACTER SET ascii NOT NULL, status VARCHAR(24) NOT NULL,
  proposal_id CHAR(36) CHARACTER SET ascii NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
  revision BIGINT UNSIGNED NOT NULL, updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (tenant_id, feedback_id),
  KEY ix_feedback_target (tenant_id, target_version, status),
  FOREIGN KEY (tenant_id, project_id) REFERENCES project (tenant_id, project_id),
  FOREIGN KEY (tenant_id, target_version) REFERENCES version_registry (tenant_id, version_id),
  FOREIGN KEY (tenant_id, project_id, task_id) REFERENCES task_head (tenant_id, project_id, task_id),
  FOREIGN KEY (tenant_id, project_id, document_revision) REFERENCES document_revision_meta (tenant_id, project_id, document_revision),
  FOREIGN KEY (tenant_id, actor_id) REFERENCES tenant_member (tenant_id, user_id),
  FOREIGN KEY (tenant_id, proposal_id) REFERENCES change_proposal (tenant_id, proposal_id),
  CHECK ((task_id IS NULL AND document_revision IS NULL) OR project_id IS NOT NULL),
  CHECK (CHAR_LENGTH(reason) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-126-connector-config dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE connector_config (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NULL,
  project_key VARCHAR(36) CHARACTER SET ascii GENERATED ALWAYS AS (COALESCE(project_id, 'ENTERPRISE')) STORED,
  connector_id CHAR(36) CHARACTER SET ascii NOT NULL, config_revision CHAR(36) CHARACTER SET ascii NOT NULL,
  configuration_kind VARCHAR(16) NOT NULL,
  endpoint VARCHAR(2048) NOT NULL, auth_ref VARCHAR(512) NOT NULL,
  credential_reference_id CHAR(36) CHARACTER SET ascii NOT NULL, credential_revision VARCHAR(128) NOT NULL,
  mapping_revision CHAR(36) CHARACTER SET ascii NOT NULL, status VARCHAR(24) NOT NULL, content_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
  revision BIGINT UNSIGNED NOT NULL, updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (tenant_id, config_revision),
  UNIQUE KEY uq_connector_owner (tenant_id, connector_id, config_revision),
  UNIQUE KEY uq_connector_scope (tenant_id, project_key, connector_id, config_revision),
  UNIQUE KEY uq_connector_credential (tenant_id, connector_id, config_revision, credential_reference_id, credential_revision),
  FOREIGN KEY (tenant_id, project_key, connector_id, configuration_kind) REFERENCES configuration_head (tenant_id, project_key, config_id, kind),
  FOREIGN KEY (tenant_id, mapping_revision) REFERENCES version_registry (tenant_id, version_id),
  FOREIGN KEY (tenant_id, config_revision) REFERENCES version_registry (tenant_id, version_id),
  CHECK (configuration_kind = 'CONNECTOR'),
  CHECK (status IN ('DRAFT','VALIDATING','EVALUATING','REVIEW_PENDING','PUBLISHED','DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-127-connector-cursor dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE connector_cursor (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NULL,
  project_key VARCHAR(36) CHARACTER SET ascii GENERATED ALWAYS AS (COALESCE(project_id, 'ENTERPRISE')) STORED,
  connector_id CHAR(36) CHARACTER SET ascii NOT NULL, config_revision CHAR(36) CHARACTER SET ascii NOT NULL,
  cursor_ref JSON NOT NULL, last_receipt JSON NULL,
  schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
  revision BIGINT UNSIGNED NOT NULL, updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (tenant_id, connector_id),
  FOREIGN KEY (tenant_id, project_key, connector_id, config_revision) REFERENCES connector_config (tenant_id, project_key, connector_id, config_revision)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-128-sync-item dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE sync_item (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NULL,
  project_key VARCHAR(36) CHARACTER SET ascii GENERATED ALWAYS AS (COALESCE(project_id, 'ENTERPRISE')) STORED,
  sync_id CHAR(36) CHARACTER SET ascii NOT NULL, connector_id CHAR(36) CHARACTER SET ascii NOT NULL,
  config_revision CHAR(36) CHARACTER SET ascii NOT NULL,
  external_id VARCHAR(512) NOT NULL, external_version VARCHAR(256) NOT NULL,
  external_identity_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  source_version CHAR(36) CHARACTER SET ascii NULL, status VARCHAR(24) NOT NULL, error_code VARCHAR(64) NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
  revision BIGINT UNSIGNED NOT NULL, updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (tenant_id, sync_id),
  UNIQUE KEY uq_sync_external_identity (tenant_id, connector_id, external_identity_sha256),
  KEY ix_sync_status (tenant_id, connector_id, status, created_at),
  FOREIGN KEY (tenant_id, project_key, connector_id, config_revision) REFERENCES connector_config (tenant_id, project_key, connector_id, config_revision),
  FOREIGN KEY (tenant_id, source_version) REFERENCES source_version (tenant_id, source_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-129-current-connector dbms:mysql runInTransaction:false failOnError:true
ALTER TABLE configuration_head ADD CONSTRAINT fk_configuration_current_connector
  FOREIGN KEY (tenant_id, config_id, current_connector_revision) REFERENCES connector_config (tenant_id, connector_id, config_revision);

--changeset medical-agent:1.0.0-130-agent-evaluation dbms:mysql runInTransaction:false failOnError:true
ALTER TABLE agent_version ADD CONSTRAINT fk_agent_evaluation_batch
  FOREIGN KEY (tenant_id, evaluation_id) REFERENCES evaluation_batch (tenant_id, evaluation_id);
