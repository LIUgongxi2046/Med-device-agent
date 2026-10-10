--liquibase formatted sql

--changeset medical-agent:1.0.0-002-identity-binding dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE identity_binding (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  identity_id CHAR(36) CHARACTER SET ascii NOT NULL,
  issuer VARCHAR(512) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  subject VARCHAR(255) COLLATE utf8mb4_0900_as_cs NOT NULL,
  user_id CHAR(36) CHARACTER SET ascii NOT NULL,
  status VARCHAR(24) NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL,
  created_at DATETIME(6) NOT NULL,
  classification VARCHAR(24) NOT NULL,
  revision BIGINT UNSIGNED NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (tenant_id, identity_id),
  UNIQUE KEY uq_identity_subject (tenant_id, issuer, subject),
  KEY ix_identity_user (tenant_id, user_id, status),
  FOREIGN KEY (tenant_id) REFERENCES tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-003-tenant-member dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE tenant_member (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  member_id CHAR(36) CHARACTER SET ascii NOT NULL,
  user_id CHAR(36) CHARACTER SET ascii NOT NULL,
  status VARCHAR(24) NOT NULL,
  valid_from DATETIME(6) NOT NULL,
  valid_to DATETIME(6) NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL,
  created_at DATETIME(6) NOT NULL,
  classification VARCHAR(24) NOT NULL,
  revision BIGINT UNSIGNED NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (tenant_id, member_id),
  UNIQUE KEY uq_member_user (tenant_id, user_id),
  KEY ix_member_status (tenant_id, status, valid_to),
  FOREIGN KEY (tenant_id) REFERENCES tenant (tenant_id),
  CHECK (status IN ('INVITED','ACTIVE','DISABLED')),
  CHECK (valid_to IS NULL OR valid_to > valid_from)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-004-permission-grant dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE permission_grant (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  grant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  subject_id CHAR(36) CHARACTER SET ascii NOT NULL,
  project_id CHAR(36) CHARACTER SET ascii NULL,
  resource_kind VARCHAR(32) NOT NULL,
  resource_id CHAR(36) CHARACTER SET ascii NULL,
  action VARCHAR(64) NOT NULL,
  `condition` JSON NOT NULL,
  status VARCHAR(24) NOT NULL,
  valid_from DATETIME(6) NOT NULL,
  valid_to DATETIME(6) NULL,
  basis_version CHAR(36) CHARACTER SET ascii NOT NULL,
  schema_version VARCHAR(32) NOT NULL,
  schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL,
  created_at DATETIME(6) NOT NULL,
  classification VARCHAR(24) NOT NULL,
  revision BIGINT UNSIGNED NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (tenant_id, grant_id),
  KEY ix_grant_subject (tenant_id, subject_id, project_id, status),
  FOREIGN KEY (tenant_id) REFERENCES tenant (tenant_id),
  FOREIGN KEY (tenant_id, project_id) REFERENCES project (tenant_id, project_id),
  FOREIGN KEY (tenant_id, basis_version) REFERENCES version_registry (tenant_id, version_id),
  CHECK (valid_to IS NULL OR valid_to > valid_from)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-005-operation-record dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE operation_record (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  operation_id CHAR(36) CHARACTER SET ascii NOT NULL,
  subject_id CHAR(36) CHARACTER SET ascii NOT NULL,
  operation_name VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  idempotency_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  request_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  state VARCHAR(24) NOT NULL,
  http_status SMALLINT NULL,
  response_ref JSON NULL,
  target_id CHAR(36) CHARACTER SET ascii NULL,
  error_code VARCHAR(64) NULL,
  schema_version VARCHAR(32) NOT NULL,
  schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL,
  created_at DATETIME(6) NOT NULL,
  classification VARCHAR(24) NOT NULL,
  revision BIGINT UNSIGNED NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (tenant_id, operation_id),
  UNIQUE KEY uq_operation_request (tenant_id, subject_id, operation_name, idempotency_key),
  KEY ix_operation_status (tenant_id, state, created_at),
  FOREIGN KEY (tenant_id) REFERENCES tenant (tenant_id),
  CHECK (state IN ('PROCESSING','COMPLETED','FAILED')),
  CHECK (CHAR_LENGTH(idempotency_key) BETWEEN 16 AND 128),
  CHECK (http_status IS NULL OR http_status BETWEEN 100 AND 599)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-006-upload-item dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE upload_item (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  project_id CHAR(36) CHARACTER SET ascii NOT NULL,
  upload_id CHAR(36) CHARACTER SET ascii NOT NULL,
  batch_id CHAR(36) CHARACTER SET ascii NOT NULL,
  session_id CHAR(36) CHARACTER SET ascii NULL,
  ownership VARCHAR(24) NOT NULL,
  file_name VARCHAR(512) NOT NULL,
  expected_size BIGINT UNSIGNED NOT NULL,
  received_size BIGINT UNSIGNED NOT NULL,
  expected_sha256 CHAR(64) CHARACTER SET ascii NULL,
  actual_sha256 CHAR(64) CHARACTER SET ascii NULL,
  object_ref JSON NULL,
  status VARCHAR(24) NOT NULL,
  expires_at DATETIME(6) NOT NULL,
  schema_version VARCHAR(32) NOT NULL,
  schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL,
  created_at DATETIME(6) NOT NULL,
  classification VARCHAR(24) NOT NULL,
  revision BIGINT UNSIGNED NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (tenant_id, project_id, upload_id),
  KEY ix_upload_batch (tenant_id, project_id, batch_id, status),
  FOREIGN KEY (tenant_id, project_id) REFERENCES project (tenant_id, project_id),
  CHECK (ownership IN ('ENTERPRISE','PROJECT','SESSION')),
  CHECK (ownership <> 'SESSION' OR session_id IS NOT NULL),
  CHECK (expected_size > 0 AND received_size <= expected_size)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-007-upload-part dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE upload_part (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  project_id CHAR(36) CHARACTER SET ascii NOT NULL,
  upload_id CHAR(36) CHARACTER SET ascii NOT NULL,
  part_no INT UNSIGNED NOT NULL,
  size_bytes BIGINT UNSIGNED NOT NULL,
  sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  storage_receipt JSON NOT NULL,
  schema_version VARCHAR(32) NOT NULL,
  schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL,
  created_at DATETIME(6) NOT NULL,
  classification VARCHAR(24) NOT NULL,
  PRIMARY KEY (tenant_id, project_id, upload_id, part_no),
  FOREIGN KEY (tenant_id, project_id, upload_id) REFERENCES upload_item (tenant_id, project_id, upload_id),
  CHECK (part_no > 0 AND size_bytes > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-008-rights-revision dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE rights_revision (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  rights_revision CHAR(36) CHARACTER SET ascii NOT NULL,
  source_version CHAR(36) CHARACTER SET ascii NOT NULL,
  purpose VARCHAR(128) NOT NULL,
  allowed_actions JSON NOT NULL,
  providers JSON NOT NULL,
  regions JSON NOT NULL,
  valid_from DATETIME(6) NULL,
  valid_to DATETIME(6) NULL,
  status VARCHAR(24) NOT NULL,
  evidence_manifest JSON NOT NULL,
  content_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  schema_version VARCHAR(32) NOT NULL,
  schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL,
  created_at DATETIME(6) NOT NULL,
  classification VARCHAR(24) NOT NULL,
  PRIMARY KEY (tenant_id, rights_revision),
  UNIQUE KEY uq_rights_source (tenant_id, source_version, rights_revision),
  KEY ix_rights_expiry (tenant_id, status, valid_to),
  FOREIGN KEY (tenant_id, source_version) REFERENCES source_version (tenant_id, source_version),
  FOREIGN KEY (tenant_id, rights_revision) REFERENCES version_registry (tenant_id, version_id),
  CHECK (valid_from IS NULL OR valid_to IS NULL OR valid_to > valid_from)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-009-content-node dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE content_node (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  source_version CHAR(36) CHARACTER SET ascii NOT NULL,
  parse_version CHAR(36) CHARACTER SET ascii NOT NULL,
  node_id CHAR(36) CHARACTER SET ascii NOT NULL,
  parent_id CHAR(36) CHARACTER SET ascii NULL,
  ordinal BIGINT UNSIGNED NOT NULL,
  node_kind VARCHAR(32) NOT NULL,
  locator JSON NOT NULL,
  content_ref JSON NOT NULL,
  node_hash CHAR(64) CHARACTER SET ascii NOT NULL,
  schema_version VARCHAR(32) NOT NULL,
  schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL,
  created_at DATETIME(6) NOT NULL,
  classification VARCHAR(24) NOT NULL,
  PRIMARY KEY (tenant_id, source_version, parse_version, node_id),
  KEY ix_content_parent (tenant_id, source_version, parse_version, parent_id, ordinal),
  FOREIGN KEY (tenant_id, source_version, parse_version) REFERENCES parse_version (tenant_id, source_version, parse_version),
  FOREIGN KEY (tenant_id, source_version, parse_version, parent_id) REFERENCES content_node (tenant_id, source_version, parse_version, node_id),
  CHECK (parent_id IS NULL OR parent_id <> node_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-010-chunk-node dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE chunk_node (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  source_version CHAR(36) CHARACTER SET ascii NOT NULL,
  parse_version CHAR(36) CHARACTER SET ascii NOT NULL,
  chunk_id CHAR(36) CHARACTER SET ascii NOT NULL,
  node_id CHAR(36) CHARACTER SET ascii NOT NULL,
  `range` JSON NOT NULL,
  schema_version VARCHAR(32) NOT NULL,
  schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL,
  created_at DATETIME(6) NOT NULL,
  classification VARCHAR(24) NOT NULL,
  PRIMARY KEY (tenant_id, source_version, parse_version, chunk_id, node_id),
  FOREIGN KEY (tenant_id, source_version, parse_version, chunk_id) REFERENCES source_chunk (tenant_id, source_version, parse_version, chunk_id),
  FOREIGN KEY (tenant_id, source_version, parse_version, node_id) REFERENCES content_node (tenant_id, source_version, parse_version, node_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-011-index-item dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE index_item (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  source_version CHAR(36) CHARACTER SET ascii NOT NULL,
  parse_version CHAR(36) CHARACTER SET ascii NOT NULL,
  index_version CHAR(36) CHARACTER SET ascii NOT NULL,
  chunk_id CHAR(36) CHARACTER SET ascii NOT NULL,
  point_id CHAR(36) CHARACTER SET ascii NOT NULL,
  vector_sha256 CHAR(64) CHARACTER SET ascii NULL,
  embedding_invocation_id CHAR(36) CHARACTER SET ascii NULL,
  verified_at DATETIME(6) NULL,
  status VARCHAR(24) NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL,
  created_at DATETIME(6) NOT NULL,
  classification VARCHAR(24) NOT NULL,
  revision BIGINT UNSIGNED NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (tenant_id, source_version, parse_version, index_version, chunk_id),
  UNIQUE KEY uq_index_chunk (tenant_id, index_version, chunk_id),
  UNIQUE KEY uq_index_point (tenant_id, point_id),
  FOREIGN KEY (tenant_id, source_version, parse_version, index_version) REFERENCES index_manifest (tenant_id, source_version, parse_version, index_version),
  FOREIGN KEY (tenant_id, source_version, parse_version, chunk_id) REFERENCES source_chunk (tenant_id, source_version, parse_version, chunk_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-012-collection-head dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE collection_head (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  collection_id CHAR(36) CHARACTER SET ascii NOT NULL,
  owner_project_id CHAR(36) CHARACTER SET ascii NULL,
  title VARCHAR(256) NOT NULL,
  current_version CHAR(36) CHARACTER SET ascii NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL,
  created_at DATETIME(6) NOT NULL,
  classification VARCHAR(24) NOT NULL,
  revision BIGINT UNSIGNED NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (tenant_id, collection_id),
  KEY ix_collection_owner (tenant_id, owner_project_id, updated_at),
  FOREIGN KEY (tenant_id) REFERENCES tenant (tenant_id),
  FOREIGN KEY (tenant_id, owner_project_id) REFERENCES project (tenant_id, project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-013-collection-version dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE collection_version (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  collection_version CHAR(36) CHARACTER SET ascii NOT NULL,
  collection_id CHAR(36) CHARACTER SET ascii NOT NULL,
  purpose VARCHAR(512) NOT NULL,
  manifest JSON NOT NULL,
  content_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  schema_version VARCHAR(32) NOT NULL,
  schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL,
  created_at DATETIME(6) NOT NULL,
  classification VARCHAR(24) NOT NULL,
  PRIMARY KEY (tenant_id, collection_version),
  UNIQUE KEY uq_collection_version (tenant_id, collection_id, collection_version),
  FOREIGN KEY (tenant_id, collection_id) REFERENCES collection_head (tenant_id, collection_id),
  FOREIGN KEY (tenant_id, collection_version) REFERENCES version_registry (tenant_id, version_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-014-collection-member dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE collection_member (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  collection_version CHAR(36) CHARACTER SET ascii NOT NULL,
  source_version CHAR(36) CHARACTER SET ascii NOT NULL,
  parse_version CHAR(36) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL,
  created_at DATETIME(6) NOT NULL,
  classification VARCHAR(24) NOT NULL,
  PRIMARY KEY (tenant_id, collection_version, source_version, parse_version),
  FOREIGN KEY (tenant_id, collection_version) REFERENCES collection_version (tenant_id, collection_version),
  FOREIGN KEY (tenant_id, source_version, parse_version) REFERENCES parse_version (tenant_id, source_version, parse_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-015-source-folder dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE source_folder (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  folder_id CHAR(36) CHARACTER SET ascii NOT NULL,
  parent_id CHAR(36) CHARACTER SET ascii NULL,
  owner_project_id CHAR(36) CHARACTER SET ascii NULL,
  category VARCHAR(32) NOT NULL,
  name VARCHAR(256) NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL,
  created_at DATETIME(6) NOT NULL,
  classification VARCHAR(24) NOT NULL,
  revision BIGINT UNSIGNED NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (tenant_id, folder_id),
  KEY ix_folder_parent (tenant_id, owner_project_id, parent_id),
  FOREIGN KEY (tenant_id) REFERENCES tenant (tenant_id),
  FOREIGN KEY (tenant_id, owner_project_id) REFERENCES project (tenant_id, project_id),
  FOREIGN KEY (tenant_id, parent_id) REFERENCES source_folder (tenant_id, folder_id),
  CHECK (parent_id IS NULL OR parent_id <> folder_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-016-source-organization dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE source_organization (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  source_id CHAR(36) CHARACTER SET ascii NOT NULL,
  folder_id CHAR(36) CHARACTER SET ascii NULL,
  labels JSON NOT NULL,
  maintainer_id CHAR(36) CHARACTER SET ascii NOT NULL,
  schema_version VARCHAR(32) NOT NULL,
  schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL,
  created_at DATETIME(6) NOT NULL,
  classification VARCHAR(24) NOT NULL,
  revision BIGINT UNSIGNED NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (tenant_id, source_id),
  KEY ix_source_folder (tenant_id, folder_id),
  FOREIGN KEY (tenant_id, source_id) REFERENCES source (tenant_id, source_id),
  FOREIGN KEY (tenant_id, folder_id) REFERENCES source_folder (tenant_id, folder_id),
  FOREIGN KEY (tenant_id, maintainer_id) REFERENCES tenant_member (tenant_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-017-user-source-bookmark dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE user_source_bookmark (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  user_id CHAR(36) CHARACTER SET ascii NOT NULL,
  source_version CHAR(36) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL,
  created_at DATETIME(6) NOT NULL,
  classification VARCHAR(24) NOT NULL,
  PRIMARY KEY (tenant_id, user_id, source_version),
  FOREIGN KEY (tenant_id, user_id) REFERENCES tenant_member (tenant_id, user_id),
  FOREIGN KEY (tenant_id, source_version) REFERENCES source_version (tenant_id, source_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-018-source-read-history dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE source_read_history (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  user_id CHAR(36) CHARACTER SET ascii NOT NULL,
  source_version CHAR(36) CHARACTER SET ascii NOT NULL,
  last_read_at DATETIME(6) NOT NULL,
  locator JSON NOT NULL,
  schema_version VARCHAR(32) NOT NULL,
  schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL,
  created_at DATETIME(6) NOT NULL,
  classification VARCHAR(24) NOT NULL,
  revision BIGINT UNSIGNED NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (tenant_id, user_id, source_version),
  KEY ix_source_read_recent (tenant_id, user_id, last_read_at),
  FOREIGN KEY (tenant_id, user_id) REFERENCES tenant_member (tenant_id, user_id),
  FOREIGN KEY (tenant_id, source_version) REFERENCES source_version (tenant_id, source_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-019-change-proposal dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE change_proposal (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  proposal_id CHAR(36) CHARACTER SET ascii NOT NULL,
  old_version CHAR(36) CHARACTER SET ascii NOT NULL,
  new_version CHAR(36) CHARACTER SET ascii NOT NULL,
  reason TEXT NOT NULL,
  reviewer_id CHAR(36) CHARACTER SET ascii NOT NULL,
  status VARCHAR(24) NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL,
  created_at DATETIME(6) NOT NULL,
  classification VARCHAR(24) NOT NULL,
  revision BIGINT UNSIGNED NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (tenant_id, proposal_id),
  KEY ix_proposal_old (tenant_id, old_version, status),
  KEY ix_proposal_new (tenant_id, new_version),
  FOREIGN KEY (tenant_id, old_version) REFERENCES version_registry (tenant_id, version_id),
  FOREIGN KEY (tenant_id, new_version) REFERENCES version_registry (tenant_id, version_id),
  FOREIGN KEY (tenant_id, reviewer_id) REFERENCES tenant_member (tenant_id, user_id),
  CHECK (old_version <> new_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-020-impact-manifest dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE impact_manifest (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  impact_id CHAR(36) CHARACTER SET ascii NOT NULL,
  proposal_id CHAR(36) CHARACTER SET ascii NOT NULL,
  governance_epoch BIGINT UNSIGNED NOT NULL,
  result_ref JSON NOT NULL,
  complete BOOLEAN NOT NULL,
  content_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  schema_version VARCHAR(32) NOT NULL,
  schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL,
  created_at DATETIME(6) NOT NULL,
  classification VARCHAR(24) NOT NULL,
  PRIMARY KEY (tenant_id, impact_id),
  KEY ix_impact_proposal (tenant_id, proposal_id, governance_epoch),
  FOREIGN KEY (tenant_id, proposal_id) REFERENCES change_proposal (tenant_id, proposal_id),
  FOREIGN KEY (tenant_id, impact_id) REFERENCES version_registry (tenant_id, version_id),
  CHECK (complete IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-021-source-dedup dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE source_dedup (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  ownership_scope_id CHAR(36) CHARACTER SET ascii NOT NULL,
  original_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  source_version CHAR(36) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL,
  created_at DATETIME(6) NOT NULL,
  classification VARCHAR(24) NOT NULL,
  PRIMARY KEY (tenant_id, ownership_scope_id, original_sha256),
  UNIQUE KEY uq_source_dedup_identity (tenant_id, ownership_scope_id, original_sha256, source_version),
  FOREIGN KEY (tenant_id, source_version) REFERENCES source_version (tenant_id, source_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-022-collection-current dbms:mysql runInTransaction:false failOnError:true
ALTER TABLE collection_head ADD CONSTRAINT fk_collection_current
  FOREIGN KEY (tenant_id, collection_id, current_version)
  REFERENCES collection_version (tenant_id, collection_id, collection_version);

--changeset medical-agent:1.0.0-023-run-principal dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE run_principal (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  principal_id CHAR(36) CHARACTER SET ascii NOT NULL,
  project_id CHAR(36) CHARACTER SET ascii NULL,
  project_key VARCHAR(36) CHARACTER SET ascii GENERATED ALWAYS AS (COALESCE(project_id, 'ENTERPRISE')) STORED,
  initiator_id CHAR(36) CHARACTER SET ascii NOT NULL,
  run_id CHAR(36) CHARACTER SET ascii NOT NULL,
  capability_manifest JSON NOT NULL,
  status VARCHAR(24) NOT NULL,
  schema_version VARCHAR(32) NOT NULL,
  schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL,
  created_at DATETIME(6) NOT NULL,
  classification VARCHAR(24) NOT NULL,
  revision BIGINT UNSIGNED NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (tenant_id, principal_id),
  UNIQUE KEY uq_principal_scope (tenant_id, project_key, principal_id, run_id),
  UNIQUE KEY uq_principal_run (tenant_id, run_id),
  KEY ix_principal_project (tenant_id, project_id, status),
  FOREIGN KEY (tenant_id, initiator_id) REFERENCES tenant_member (tenant_id, user_id),
  FOREIGN KEY (tenant_id, project_id) REFERENCES project (tenant_id, project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-024-principal-token dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE principal_token (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  jti CHAR(36) CHARACTER SET ascii NOT NULL,
  principal_id CHAR(36) CHARACTER SET ascii NOT NULL,
  project_id CHAR(36) CHARACTER SET ascii NULL,
  project_key VARCHAR(36) CHARACTER SET ascii GENERATED ALWAYS AS (COALESCE(project_id, 'ENTERPRISE')) STORED,
  run_id CHAR(36) CHARACTER SET ascii NOT NULL,
  step_id CHAR(36) CHARACTER SET ascii NULL,
  audience VARCHAR(255) NOT NULL,
  action_scope JSON NOT NULL,
  auth_epoch BIGINT UNSIGNED NOT NULL,
  issued_at DATETIME(6) NOT NULL,
  expires_at DATETIME(6) NOT NULL,
  revoked_at DATETIME(6) NULL,
  schema_version VARCHAR(32) NOT NULL,
  schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL,
  created_at DATETIME(6) NOT NULL,
  classification VARCHAR(24) NOT NULL,
  revision BIGINT UNSIGNED NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (tenant_id, jti),
  KEY ix_token_expiry (tenant_id, principal_id, revoked_at, expires_at),
  FOREIGN KEY (tenant_id, project_key, principal_id, run_id) REFERENCES run_principal (tenant_id, project_key, principal_id, run_id),
  FOREIGN KEY (tenant_id, project_id) REFERENCES project (tenant_id, project_id),
  CHECK (expires_at > issued_at),
  CHECK (revoked_at IS NULL OR revoked_at >= issued_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-025-processing-attempt dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE processing_attempt (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  project_id CHAR(36) CHARACTER SET ascii NOT NULL,
  attempt_id CHAR(36) CHARACTER SET ascii NOT NULL,
  source_version CHAR(36) CHARACTER SET ascii NOT NULL,
  parse_version CHAR(36) CHARACTER SET ascii NULL,
  job_type VARCHAR(32) NOT NULL,
  config_revision CHAR(36) CHARACTER SET ascii NOT NULL,
  status VARCHAR(24) NOT NULL,
  failure_manifest JSON NOT NULL,
  run_id CHAR(36) CHARACTER SET ascii NOT NULL,
  schema_version VARCHAR(32) NOT NULL,
  schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL,
  created_at DATETIME(6) NOT NULL,
  classification VARCHAR(24) NOT NULL,
  revision BIGINT UNSIGNED NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (tenant_id, project_id, attempt_id),
  KEY ix_processing_source (tenant_id, project_id, source_version, job_type, created_at),
  FOREIGN KEY (tenant_id, project_id) REFERENCES project (tenant_id, project_id),
  FOREIGN KEY (tenant_id, source_version) REFERENCES source_version (tenant_id, source_version),
  FOREIGN KEY (tenant_id, source_version, parse_version) REFERENCES parse_version (tenant_id, source_version, parse_version),
  FOREIGN KEY (tenant_id, config_revision) REFERENCES version_registry (tenant_id, version_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-026-source-session-binding dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE source_session_binding (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  project_id CHAR(36) CHARACTER SET ascii NOT NULL,
  source_id CHAR(36) CHARACTER SET ascii NOT NULL,
  session_id CHAR(36) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL,
  created_at DATETIME(6) NOT NULL,
  classification VARCHAR(24) NOT NULL,
  PRIMARY KEY (tenant_id, project_id, source_id, session_id),
  KEY ix_session_sources (tenant_id, project_id, session_id),
  FOREIGN KEY (tenant_id, project_id) REFERENCES project (tenant_id, project_id),
  FOREIGN KEY (tenant_id, source_id) REFERENCES source (tenant_id, source_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
