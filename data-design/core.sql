CREATE TABLE tenant (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  auth_epoch BIGINT UNSIGNED NOT NULL,
  governance_epoch BIGINT UNSIGNED NOT NULL,
  PRIMARY KEY (tenant_id)
) ENGINE=InnoDB;

CREATE TABLE project (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  project_id CHAR(36) CHARACTER SET ascii NOT NULL,
  status VARCHAR(24) NOT NULL,
  revision BIGINT UNSIGNED NOT NULL,
  PRIMARY KEY (tenant_id, project_id),
  FOREIGN KEY (tenant_id) REFERENCES tenant (tenant_id),
  CHECK (status IN ('PENDING','ACTIVE','PAUSED','ARCHIVED'))
) ENGINE=InnoDB;

CREATE TABLE source (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  source_id CHAR(36) CHARACTER SET ascii NOT NULL,
  owner_project_id CHAR(36) CHARACTER SET ascii NULL,
  ownership VARCHAR(24) NOT NULL,
  title VARCHAR(512) NOT NULL,
  classification VARCHAR(24) NOT NULL,
  PRIMARY KEY (tenant_id, source_id),
  FOREIGN KEY (tenant_id) REFERENCES tenant (tenant_id),
  FOREIGN KEY (tenant_id, owner_project_id) REFERENCES project (tenant_id, project_id),
  CHECK (ownership IN ('ENTERPRISE','PROJECT','SESSION')),
  CHECK ((ownership = 'ENTERPRISE' AND owner_project_id IS NULL) OR
         (ownership IN ('PROJECT','SESSION') AND owner_project_id IS NOT NULL)),
  CHECK (classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE source_version (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  source_id CHAR(36) CHARACTER SET ascii NOT NULL,
  source_version CHAR(36) CHARACTER SET ascii NOT NULL,
  version_no BIGINT UNSIGNED NOT NULL,
  original_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  object_key VARCHAR(1024) NOT NULL,
  object_version VARCHAR(256) NOT NULL,
  media_type VARCHAR(128) NOT NULL,
  size_bytes BIGINT UNSIGNED NOT NULL,
  publisher_version VARCHAR(256) NULL,
  provenance JSON NOT NULL,
  rights_revision CHAR(36) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (tenant_id, source_version),
  UNIQUE KEY uq_source_identity (tenant_id, source_id, source_version),
  UNIQUE KEY uq_source_version_no (tenant_id, source_id, version_no),
  KEY ix_source_digest (tenant_id, original_sha256),
  FOREIGN KEY (tenant_id, source_id) REFERENCES source (tenant_id, source_id),
  CHECK (version_no > 0 AND size_bytes > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE parse_version (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  source_version CHAR(36) CHARACTER SET ascii NOT NULL,
  parse_version CHAR(36) CHARACTER SET ascii NOT NULL,
  processor_manifest JSON NOT NULL,
  content_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  artifact_key VARCHAR(1024) NOT NULL,
  parse_status VARCHAR(24) NOT NULL,
  failure_manifest JSON NOT NULL,
  PRIMARY KEY (tenant_id, source_version, parse_version),
  FOREIGN KEY (tenant_id, source_version) REFERENCES source_version (tenant_id, source_version),
  CHECK (parse_status IN ('QUEUED','PROCESSING','COMPLETE','PARTIAL_FAILED','FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE source_chunk (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  source_version CHAR(36) CHARACTER SET ascii NOT NULL,
  parse_version CHAR(36) CHARACTER SET ascii NOT NULL,
  chunk_id CHAR(36) CHARACTER SET ascii NOT NULL,
  ordinal INT UNSIGNED NOT NULL,
  node_id CHAR(36) CHARACTER SET ascii NOT NULL,
  locator JSON NOT NULL,
  quote_hash CHAR(64) CHARACTER SET ascii NOT NULL,
  raw_text MEDIUMTEXT NOT NULL,
  search_text MEDIUMTEXT NOT NULL,
  PRIMARY KEY (tenant_id, source_version, parse_version, chunk_id),
  UNIQUE KEY uq_chunk_order (tenant_id, source_version, parse_version, ordinal),
  FOREIGN KEY (tenant_id, source_version, parse_version)
    REFERENCES parse_version (tenant_id, source_version, parse_version),
  FULLTEXT KEY ft_chunk (search_text) WITH PARSER ngram
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE index_manifest (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  source_version CHAR(36) CHARACTER SET ascii NOT NULL,
  parse_version CHAR(36) CHARACTER SET ascii NOT NULL,
  index_version CHAR(36) CHARACTER SET ascii NOT NULL,
  mode VARCHAR(24) NOT NULL,
  model_config_revision CHAR(36) CHARACTER SET ascii NULL,
  dimension INT UNSIGNED NULL,
  config_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  expected_count BIGINT UNSIGNED NOT NULL,
  verified_count BIGINT UNSIGNED NOT NULL,
  status VARCHAR(24) NOT NULL,
  PRIMARY KEY (tenant_id, source_version, parse_version, index_version),
  FOREIGN KEY (tenant_id, source_version, parse_version)
    REFERENCES parse_version (tenant_id, source_version, parse_version),
  CHECK (mode IN ('FULLTEXT','DENSE','HYBRID')),
  CHECK (status IN ('NOT_BUILT','BUILDING','READY','FAILED','WITHDRAWN')),
  CHECK (status <> 'READY' OR (expected_count > 0 AND verified_count = expected_count)),
  CHECK (mode = 'FULLTEXT' OR (model_config_revision IS NOT NULL AND dimension IS NOT NULL AND dimension > 0))
) ENGINE=InnoDB;

CREATE TABLE approval_decision (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  source_version CHAR(36) CHARACTER SET ascii NOT NULL,
  parse_version CHAR(36) CHARACTER SET ascii NOT NULL,
  approval_revision CHAR(36) CHARACTER SET ascii NOT NULL,
  decision VARCHAR(24) NOT NULL,
  reviewer_id CHAR(36) CHARACTER SET ascii NOT NULL,
  reviewed_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  reason TEXT NOT NULL,
  decided_at DATETIME(6) NOT NULL,
  PRIMARY KEY (tenant_id, source_version, parse_version, approval_revision),
  FOREIGN KEY (tenant_id, source_version, parse_version)
    REFERENCES parse_version (tenant_id, source_version, parse_version),
  CHECK (decision IN ('APPROVE','RETURN','DISABLE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE source_use_gate (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  source_version CHAR(36) CHARACTER SET ascii NOT NULL,
  parse_version CHAR(36) CHARACTER SET ascii NOT NULL,
  approval_revision CHAR(36) CHARACTER SET ascii NULL,
  upload_status VARCHAR(24) NOT NULL,
  approval_status VARCHAR(24) NOT NULL,
  lifecycle VARCHAR(24) NOT NULL,
  revision BIGINT UNSIGNED NOT NULL,
  PRIMARY KEY (tenant_id, source_version, parse_version),
  FOREIGN KEY (tenant_id, source_version, parse_version)
    REFERENCES parse_version (tenant_id, source_version, parse_version),
  FOREIGN KEY (tenant_id, source_version, parse_version, approval_revision)
    REFERENCES approval_decision (tenant_id, source_version, parse_version, approval_revision),
  CHECK (upload_status IN ('PENDING','UPLOADING','RECEIVED','FAILED')),
  CHECK (approval_status IN ('PENDING','APPROVED','DISABLED')),
  CHECK (lifecycle IN ('ACTIVE','RECYCLED','PURGE_PENDING','PURGED')),
  CHECK (approval_status <> 'APPROVED' OR approval_revision IS NOT NULL)
) ENGINE=InnoDB;

CREATE TABLE project_source_binding (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  project_id CHAR(36) CHARACTER SET ascii NOT NULL,
  source_version CHAR(36) CHARACTER SET ascii NOT NULL,
  binding_revision CHAR(36) CHARACTER SET ascii NOT NULL,
  applicability VARCHAR(24) NOT NULL,
  decision_manifest JSON NOT NULL,
  PRIMARY KEY (tenant_id, project_id, source_version, binding_revision),
  FOREIGN KEY (tenant_id, project_id) REFERENCES project (tenant_id, project_id),
  FOREIGN KEY (tenant_id, source_version) REFERENCES source_version (tenant_id, source_version),
  CHECK (applicability IN ('APPLICABLE','NOT_APPLICABLE','UNKNOWN'))
) ENGINE=InnoDB;

CREATE TABLE scope_snapshot (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  project_id CHAR(36) CHARACTER SET ascii NOT NULL,
  snapshot_id CHAR(36) CHARACTER SET ascii NOT NULL,
  manifest JSON NOT NULL,
  manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  auth_epoch BIGINT UNSIGNED NOT NULL,
  governance_epoch BIGINT UNSIGNED NOT NULL,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (tenant_id, project_id, snapshot_id),
  FOREIGN KEY (tenant_id, project_id) REFERENCES project (tenant_id, project_id)
) ENGINE=InnoDB;

CREATE TABLE scope_member (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  project_id CHAR(36) CHARACTER SET ascii NOT NULL,
  snapshot_id CHAR(36) CHARACTER SET ascii NOT NULL,
  source_id CHAR(36) CHARACTER SET ascii NOT NULL,
  source_version CHAR(36) CHARACTER SET ascii NOT NULL,
  parse_version CHAR(36) CHARACTER SET ascii NOT NULL,
  index_version CHAR(36) CHARACTER SET ascii NOT NULL,
  approval_revision CHAR(36) CHARACTER SET ascii NOT NULL,
  binding_revision CHAR(36) CHARACTER SET ascii NOT NULL,
  PRIMARY KEY (tenant_id, project_id, snapshot_id, source_version, parse_version),
  FOREIGN KEY (tenant_id, project_id, snapshot_id)
    REFERENCES scope_snapshot (tenant_id, project_id, snapshot_id),
  FOREIGN KEY (tenant_id, source_id, source_version)
    REFERENCES source_version (tenant_id, source_id, source_version),
  FOREIGN KEY (tenant_id, source_version, parse_version, index_version)
    REFERENCES index_manifest (tenant_id, source_version, parse_version, index_version),
  FOREIGN KEY (tenant_id, source_version, parse_version, approval_revision)
    REFERENCES approval_decision (tenant_id, source_version, parse_version, approval_revision),
  FOREIGN KEY (tenant_id, project_id, source_version, binding_revision)
    REFERENCES project_source_binding (tenant_id, project_id, source_version, binding_revision)
) ENGINE=InnoDB;

CREATE TABLE document_revision (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  project_id CHAR(36) CHARACTER SET ascii NOT NULL,
  document_revision CHAR(36) CHARACTER SET ascii NOT NULL,
  document_id CHAR(36) CHARACTER SET ascii NOT NULL,
  snapshot_id CHAR(36) CHARACTER SET ascii NOT NULL,
  body_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  body_object_key VARCHAR(1024) NOT NULL,
  PRIMARY KEY (tenant_id, project_id, document_revision),
  FOREIGN KEY (tenant_id, project_id, snapshot_id)
    REFERENCES scope_snapshot (tenant_id, project_id, snapshot_id)
) ENGINE=InnoDB;

CREATE TABLE claim (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  project_id CHAR(36) CHARACTER SET ascii NOT NULL,
  document_revision CHAR(36) CHARACTER SET ascii NOT NULL,
  claim_id CHAR(36) CHARACTER SET ascii NOT NULL,
  node_id CHAR(36) CHARACTER SET ascii NOT NULL,
  field_id CHAR(36) CHARACTER SET ascii NULL,
  row_id CHAR(36) CHARACTER SET ascii NULL,
  statement_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  PRIMARY KEY (tenant_id, project_id, document_revision, claim_id),
  FOREIGN KEY (tenant_id, project_id, document_revision)
    REFERENCES document_revision (tenant_id, project_id, document_revision)
) ENGINE=InnoDB;

CREATE TABLE evidence_link (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  project_id CHAR(36) CHARACTER SET ascii NOT NULL,
  evidence_id CHAR(36) CHARACTER SET ascii NOT NULL,
  document_revision CHAR(36) CHARACTER SET ascii NOT NULL,
  claim_id CHAR(36) CHARACTER SET ascii NOT NULL,
  source_version CHAR(36) CHARACTER SET ascii NOT NULL,
  parse_version CHAR(36) CHARACTER SET ascii NOT NULL,
  chunk_id CHAR(36) CHARACTER SET ascii NOT NULL,
  approval_revision CHAR(36) CHARACTER SET ascii NOT NULL,
  relation_kind VARCHAR(24) NOT NULL,
  checks JSON NOT NULL,
  PRIMARY KEY (tenant_id, project_id, evidence_id),
  KEY ix_evidence_source (tenant_id, source_version, parse_version),
  FOREIGN KEY (tenant_id, project_id, document_revision, claim_id)
    REFERENCES claim (tenant_id, project_id, document_revision, claim_id),
  FOREIGN KEY (tenant_id, source_version, parse_version, chunk_id)
    REFERENCES source_chunk (tenant_id, source_version, parse_version, chunk_id),
  FOREIGN KEY (tenant_id, source_version, parse_version, approval_revision)
    REFERENCES approval_decision (tenant_id, source_version, parse_version, approval_revision),
  CHECK (relation_kind IN ('SUPPORTS','CONTRADICTS','BACKGROUND'))
) ENGINE=InnoDB;

CREATE TABLE version_registry (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  version_id CHAR(36) CHARACTER SET ascii NOT NULL,
  owner_project_id CHAR(36) CHARACTER SET ascii NULL,
  entity_kind VARCHAR(32) NOT NULL,
  content_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  PRIMARY KEY (tenant_id, version_id),
  FOREIGN KEY (tenant_id) REFERENCES tenant (tenant_id),
  FOREIGN KEY (tenant_id, owner_project_id) REFERENCES project (tenant_id, project_id)
) ENGINE=InnoDB;

CREATE TABLE lineage_edge (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  consumer_version_id CHAR(36) CHARACTER SET ascii NOT NULL,
  upstream_version_id CHAR(36) CHARACTER SET ascii NOT NULL,
  relation_kind VARCHAR(32) NOT NULL,
  actor_id CHAR(36) CHARACTER SET ascii NOT NULL,
  decision_ref CHAR(36) CHARACTER SET ascii NULL,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (tenant_id, consumer_version_id, upstream_version_id, relation_kind),
  KEY ix_lineage_reverse (tenant_id, upstream_version_id, consumer_version_id),
  FOREIGN KEY (tenant_id, consumer_version_id) REFERENCES version_registry (tenant_id, version_id),
  FOREIGN KEY (tenant_id, upstream_version_id) REFERENCES version_registry (tenant_id, version_id),
  CHECK (consumer_version_id <> upstream_version_id)
) ENGINE=InnoDB;

CREATE TABLE outbox_event (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  event_id CHAR(36) CHARACTER SET ascii NOT NULL,
  aggregate_id CHAR(36) CHARACTER SET ascii NOT NULL,
  aggregate_revision BIGINT UNSIGNED NOT NULL,
  event_type VARCHAR(64) NOT NULL,
  payload JSON NOT NULL,
  occurred_at DATETIME(6) NOT NULL,
  published_at DATETIME(6) NULL,
  PRIMARY KEY (tenant_id, event_id),
  UNIQUE KEY uq_aggregate_event (tenant_id, aggregate_id, aggregate_revision, event_type),
  KEY ix_outbox_pending (published_at, occurred_at),
  FOREIGN KEY (tenant_id) REFERENCES tenant (tenant_id)
) ENGINE=InnoDB;
