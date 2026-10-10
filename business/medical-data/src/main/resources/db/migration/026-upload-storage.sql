--liquibase formatted sql

--changeset medical-agent:1.0.0-193-upload-grant-scope dbms:mysql runInTransaction:false failOnError:true
ALTER TABLE artifact_upload_grant ADD UNIQUE KEY uq_upload_grant_scope
  (tenant_id, project_key, grant_id, subject_id, purpose);

--changeset medical-agent:1.0.0-194-upload-grant-document dbms:mysql runInTransaction:false failOnError:true
ALTER TABLE artifact_upload_grant ADD UNIQUE KEY uq_upload_grant_document
  (tenant_id, project_key, grant_id, subject_id, document_id);

--changeset medical-agent:1.0.0-195-upload-grant-step dbms:mysql runInTransaction:false failOnError:true
ALTER TABLE artifact_upload_grant ADD UNIQUE KEY uq_upload_grant_step
  (tenant_id, project_key, grant_id, subject_id, run_id, step_id);

--changeset medical-agent:1.0.0-196-object-upload-session dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE object_upload_session (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NULL,
  project_key VARCHAR(36) CHARACTER SET ascii GENERATED ALWAYS AS (COALESCE(project_id, 'ENTERPRISE')) STORED,
  upload_id CHAR(36) CHARACTER SET ascii NOT NULL, subject_id CHAR(36) CHARACTER SET ascii NOT NULL,
  purpose VARCHAR(32) NOT NULL, source_upload_id CHAR(36) CHARACTER SET ascii NULL, grant_id CHAR(36) CHARACTER SET ascii NULL,
  expected_size BIGINT UNSIGNED NOT NULL, expected_sha256 CHAR(64) CHARACTER SET ascii NULL,
  received_size BIGINT UNSIGNED NOT NULL, media_type VARCHAR(128) NOT NULL,
  object_key VARCHAR(1024) NOT NULL, object_key_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  max_part_bytes BIGINT UNSIGNED NOT NULL, expires_at DATETIME(6) NOT NULL, state VARCHAR(24) NOT NULL,
  object_version VARCHAR(256) NULL, content_sha256 CHAR(64) CHARACTER SET ascii NULL, object_ref JSON NULL,
  schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
  revision BIGINT UNSIGNED NOT NULL, updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (tenant_id, upload_id),
  UNIQUE KEY uq_object_upload_range (tenant_id, project_key, upload_id, expected_size, max_part_bytes),
  UNIQUE KEY uq_object_upload_binding (tenant_id, project_key, upload_id, grant_id, subject_id, purpose),
  UNIQUE KEY uq_object_upload_grant (tenant_id, grant_id),
  UNIQUE KEY uq_object_upload_source (tenant_id, project_id, source_upload_id),
  UNIQUE KEY uq_object_upload_key (tenant_id, object_key_sha256),
  KEY ix_object_upload_subject (tenant_id, subject_id, state, expires_at),
  CONSTRAINT fk_object_upload_subject FOREIGN KEY (tenant_id, subject_id) REFERENCES tenant_member (tenant_id, user_id),
  CONSTRAINT fk_object_upload_project FOREIGN KEY (tenant_id, project_id) REFERENCES project (tenant_id, project_id),
  CONSTRAINT fk_object_upload_source FOREIGN KEY (tenant_id, project_id, source_upload_id)
    REFERENCES upload_item (tenant_id, project_id, upload_id),
  CONSTRAINT fk_object_upload_grant FOREIGN KEY (tenant_id, project_key, grant_id, subject_id, purpose)
    REFERENCES artifact_upload_grant (tenant_id, project_key, grant_id, subject_id, purpose),
  CONSTRAINT ck_object_upload_origin CHECK (
    (purpose = 'SOURCE' AND source_upload_id IS NOT NULL AND project_id IS NOT NULL AND grant_id IS NULL)
    OR (purpose IN ('DOCUMENT_BODY','AGENT_ARTIFACT') AND grant_id IS NOT NULL AND source_upload_id IS NULL)
  ),
  CONSTRAINT ck_object_upload_digest CHECK (purpose = 'SOURCE' OR expected_sha256 IS NOT NULL),
  CONSTRAINT ck_object_upload_size CHECK (expected_size > 0 AND received_size <= expected_size AND max_part_bytes = 8388608),
  CONSTRAINT ck_object_upload_state CHECK (state IN ('ACTIVE','COMPLETING','COMPLETED','CANCELLED','EXPIRED','FAILED')),
  CONSTRAINT ck_object_upload_time CHECK (expires_at > created_at AND revision > 0 AND updated_at >= created_at),
  CONSTRAINT ck_object_upload_labels CHECK (CHAR_LENGTH(object_key) > 0 AND CHAR_LENGTH(media_type) > 0),
  CONSTRAINT ck_object_upload_object CHECK (
    (object_version IS NULL AND content_sha256 IS NULL AND object_ref IS NULL)
    OR (object_version IS NOT NULL AND content_sha256 IS NOT NULL AND object_ref IS NOT NULL
      AND CHAR_LENGTH(object_version) > 0 AND object_version <> 'null'
      AND JSON_TYPE(object_ref) = 'OBJECT' AND JSON_LENGTH(object_ref) = 5
      AND JSON_CONTAINS_PATH(object_ref, 'all', '$.key', '$.version_id', '$.sha256', '$.size_bytes', '$.media_type')
      AND JSON_TYPE(JSON_EXTRACT(object_ref, '$.key')) <=> 'STRING'
      AND JSON_TYPE(JSON_EXTRACT(object_ref, '$.version_id')) <=> 'STRING'
      AND JSON_TYPE(JSON_EXTRACT(object_ref, '$.sha256')) <=> 'STRING'
      AND JSON_TYPE(JSON_EXTRACT(object_ref, '$.media_type')) <=> 'STRING'
      AND JSON_TYPE(JSON_EXTRACT(object_ref, '$.size_bytes')) <=> 'INTEGER'
      AND JSON_UNQUOTE(JSON_EXTRACT(object_ref, '$.key')) <=> object_key
      AND JSON_UNQUOTE(JSON_EXTRACT(object_ref, '$.version_id')) <=> object_version
      AND JSON_UNQUOTE(JSON_EXTRACT(object_ref, '$.sha256')) <=> content_sha256
      AND JSON_EXTRACT(object_ref, '$.size_bytes') <=> expected_size
      AND JSON_UNQUOTE(JSON_EXTRACT(object_ref, '$.media_type')) <=> media_type
      AND (expected_sha256 IS NULL OR expected_sha256 = content_sha256))
  ),
  CONSTRAINT ck_object_upload_complete CHECK (
    state <> 'COMPLETED' OR (object_version IS NOT NULL AND object_ref IS NOT NULL AND received_size = expected_size)
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-197-object-upload-part dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE object_upload_part (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NULL,
  project_key VARCHAR(36) CHARACTER SET ascii GENERATED ALWAYS AS (COALESCE(project_id, 'ENTERPRISE')) STORED,
  upload_id CHAR(36) CHARACTER SET ascii NOT NULL, part_no INT UNSIGNED NOT NULL, write_id CHAR(36) CHARACTER SET ascii NOT NULL,
  total_size BIGINT UNSIGNED NOT NULL, max_part_bytes BIGINT UNSIGNED NOT NULL,
  start_byte BIGINT UNSIGNED NOT NULL, end_byte BIGINT UNSIGNED NOT NULL, size_bytes BIGINT UNSIGNED NOT NULL,
  sha256 CHAR(64) CHARACTER SET ascii NOT NULL, object_key VARCHAR(1024) NOT NULL,
  object_key_sha256 CHAR(64) CHARACTER SET ascii NOT NULL, state VARCHAR(24) NOT NULL,
  object_version VARCHAR(256) NULL, object_ref JSON NULL,
  schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
  revision BIGINT UNSIGNED NOT NULL, updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (tenant_id, upload_id, part_no),
  UNIQUE KEY uq_object_part_write (tenant_id, write_id),
  UNIQUE KEY uq_object_part_key (tenant_id, object_key_sha256),
  CONSTRAINT fk_object_part_upload FOREIGN KEY (tenant_id, project_key, upload_id, total_size, max_part_bytes)
    REFERENCES object_upload_session (tenant_id, project_key, upload_id, expected_size, max_part_bytes),
  CONSTRAINT ck_object_part_range CHECK (
    part_no > 0 AND max_part_bytes = 8388608 AND total_size > 0 AND size_bytes > 0 AND size_bytes <= max_part_bytes
    AND start_byte < end_byte AND end_byte <= total_size AND size_bytes = end_byte - start_byte
    AND start_byte = (part_no - 1) * max_part_bytes AND (end_byte = total_size OR size_bytes = max_part_bytes)
  ),
  CONSTRAINT ck_object_part_state CHECK (state IN ('RESERVED','WRITING','STORED','FAILED')),
  CONSTRAINT ck_object_part_revision CHECK (revision > 0 AND updated_at >= created_at AND CHAR_LENGTH(object_key) > 0),
  CONSTRAINT ck_object_part_object CHECK (
    (object_version IS NULL AND object_ref IS NULL)
    OR (object_version IS NOT NULL AND object_ref IS NOT NULL
      AND CHAR_LENGTH(object_version) > 0 AND object_version <> 'null'
      AND JSON_TYPE(object_ref) = 'OBJECT' AND JSON_LENGTH(object_ref) = 5
      AND JSON_CONTAINS_PATH(object_ref, 'all', '$.key', '$.version_id', '$.sha256', '$.size_bytes', '$.media_type')
      AND JSON_TYPE(JSON_EXTRACT(object_ref, '$.key')) <=> 'STRING'
      AND JSON_TYPE(JSON_EXTRACT(object_ref, '$.version_id')) <=> 'STRING'
      AND JSON_TYPE(JSON_EXTRACT(object_ref, '$.sha256')) <=> 'STRING'
      AND JSON_TYPE(JSON_EXTRACT(object_ref, '$.media_type')) <=> 'STRING'
      AND JSON_TYPE(JSON_EXTRACT(object_ref, '$.size_bytes')) <=> 'INTEGER'
      AND JSON_UNQUOTE(JSON_EXTRACT(object_ref, '$.key')) <=> object_key
      AND JSON_UNQUOTE(JSON_EXTRACT(object_ref, '$.version_id')) <=> object_version
      AND JSON_UNQUOTE(JSON_EXTRACT(object_ref, '$.sha256')) <=> sha256
      AND JSON_EXTRACT(object_ref, '$.size_bytes') <=> size_bytes
      AND JSON_UNQUOTE(JSON_EXTRACT(object_ref, '$.media_type')) <=> 'application/octet-stream')
  ),
  CONSTRAINT ck_object_part_stored CHECK (state <> 'STORED' OR (object_version IS NOT NULL AND object_ref IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-198-artifact-upload-binding dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE artifact_upload_binding (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NULL,
  project_key VARCHAR(36) CHARACTER SET ascii GENERATED ALWAYS AS (COALESCE(project_id, 'ENTERPRISE')) STORED,
  grant_id CHAR(36) CHARACTER SET ascii NOT NULL, upload_id CHAR(36) CHARACTER SET ascii NOT NULL,
  subject_id CHAR(36) CHARACTER SET ascii NOT NULL, purpose VARCHAR(32) NOT NULL,
  document_id CHAR(36) CHARACTER SET ascii NULL, run_id CHAR(36) CHARACTER SET ascii NULL, step_id CHAR(36) CHARACTER SET ascii NULL,
  expected_revision BIGINT UNSIGNED NULL, context_revision BIGINT UNSIGNED NULL,
  principal_id CHAR(36) CHARACTER SET ascii NULL, lease_generation BIGINT UNSIGNED NULL,
  dependency_access_set_id CHAR(36) CHARACTER SET ascii NOT NULL, dependency_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  schema_document_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  authorization_manifest JSON NOT NULL, authorization_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
  PRIMARY KEY (tenant_id, grant_id),
  UNIQUE KEY uq_upload_binding_upload (tenant_id, upload_id),
  CONSTRAINT fk_upload_binding_grant FOREIGN KEY (tenant_id, project_key, grant_id, subject_id, purpose)
    REFERENCES artifact_upload_grant (tenant_id, project_key, grant_id, subject_id, purpose),
  CONSTRAINT fk_upload_binding_session FOREIGN KEY (tenant_id, project_key, upload_id, grant_id, subject_id, purpose)
    REFERENCES object_upload_session (tenant_id, project_key, upload_id, grant_id, subject_id, purpose),
  CONSTRAINT fk_upload_binding_document FOREIGN KEY (tenant_id, project_key, grant_id, subject_id, document_id)
    REFERENCES artifact_upload_grant (tenant_id, project_key, grant_id, subject_id, document_id),
  CONSTRAINT fk_upload_binding_step FOREIGN KEY (tenant_id, project_key, grant_id, subject_id, run_id, step_id)
    REFERENCES artifact_upload_grant (tenant_id, project_key, grant_id, subject_id, run_id, step_id),
  CONSTRAINT fk_upload_binding_principal FOREIGN KEY (tenant_id, project_key, principal_id, run_id)
    REFERENCES run_principal (tenant_id, project_key, principal_id, run_id),
  CONSTRAINT fk_upload_binding_dependency FOREIGN KEY (tenant_id, project_key, dependency_access_set_id, dependency_manifest_sha256)
    REFERENCES dependency_access_manifest (tenant_id, project_key, access_set_id, manifest_sha256),
  CONSTRAINT ck_upload_binding_execution CHECK (
    (purpose = 'DOCUMENT_BODY' AND project_id IS NOT NULL AND document_id IS NOT NULL
      AND expected_revision IS NOT NULL AND context_revision IS NOT NULL
      AND run_id IS NULL AND step_id IS NULL AND principal_id IS NULL AND lease_generation IS NULL)
    OR (purpose = 'AGENT_ARTIFACT' AND document_id IS NULL AND expected_revision IS NULL AND context_revision IS NULL
      AND run_id IS NOT NULL AND step_id IS NOT NULL AND principal_id IS NOT NULL AND lease_generation IS NOT NULL AND lease_generation > 0)
  ),
  CONSTRAINT ck_upload_binding_manifest CHECK (JSON_TYPE(authorization_manifest) = 'OBJECT')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
