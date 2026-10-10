--liquibase formatted sql

--changeset medical-agent:1.0.0-187-dependency-manifest-key dbms:mysql runInTransaction:false failOnError:true
ALTER TABLE dependency_access_set ADD UNIQUE KEY uq_dependency_manifest_identity
  (tenant_id, project_key, access_set_id, manifest_sha256);

--changeset medical-agent:1.0.0-188-artifact-dependency-key dbms:mysql runInTransaction:false failOnError:true
ALTER TABLE artifact ADD UNIQUE KEY uq_artifact_dependency_identity
  (tenant_id, project_key, artifact_id, dependency_access_set_id);

--changeset medical-agent:1.0.0-189-content-receipt-key dbms:mysql runInTransaction:false failOnError:true
ALTER TABLE content_read_receipt ADD UNIQUE KEY uq_content_receipt_identity
  (tenant_id, project_key, read_receipt_id, artifact_id, resource_id);

--changeset medical-agent:1.0.0-190-dependency-access-manifest dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE dependency_access_manifest (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NULL,
  project_key VARCHAR(36) CHARACTER SET ascii GENERATED ALWAYS AS (COALESCE(project_id, 'ENTERPRISE')) STORED,
  access_set_id CHAR(36) CHARACTER SET ascii NOT NULL,
  manifest_json JSON NOT NULL, manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  content_sha256 CHAR(64) CHARACTER SET ascii NOT NULL, member_count BIGINT UNSIGNED NOT NULL,
  schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
  PRIMARY KEY (tenant_id, access_set_id),
  UNIQUE KEY uq_dependency_manifest_scope (tenant_id, project_key, access_set_id, manifest_sha256),
  CONSTRAINT fk_dependency_manifest_header FOREIGN KEY (tenant_id, project_key, access_set_id, manifest_sha256)
    REFERENCES dependency_access_set (tenant_id, project_key, access_set_id, manifest_sha256),
  CONSTRAINT ck_dependency_manifest_digest CHECK (content_sha256 = manifest_sha256),
  CONSTRAINT ck_dependency_manifest_members CHECK (
    JSON_TYPE(JSON_EXTRACT(manifest_json, '$.members')) = 'ARRAY'
    AND member_count = JSON_LENGTH(JSON_EXTRACT(manifest_json, '$.members'))
  ),
  CONSTRAINT ck_dependency_manifest_identity CHECK (
    JSON_TYPE(manifest_json) = 'OBJECT' AND JSON_LENGTH(manifest_json) = 5
    AND JSON_CONTAINS_PATH(manifest_json, 'all', '$.schema_version', '$.tenant_id', '$.project_id', '$.access_set_id', '$.members')
    AND JSON_UNQUOTE(JSON_EXTRACT(manifest_json, '$.schema_version')) <=> schema_version
    AND JSON_UNQUOTE(JSON_EXTRACT(manifest_json, '$.tenant_id')) <=> tenant_id
    AND JSON_UNQUOTE(JSON_EXTRACT(manifest_json, '$.access_set_id')) <=> access_set_id
    AND ((project_id IS NULL AND JSON_TYPE(JSON_EXTRACT(manifest_json, '$.project_id')) = 'NULL')
      OR (project_id IS NOT NULL AND JSON_UNQUOTE(JSON_EXTRACT(manifest_json, '$.project_id')) <=> project_id))
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-191-artifact-content-binding dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE artifact_content_binding (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NULL,
  project_key VARCHAR(36) CHARACTER SET ascii GENERATED ALWAYS AS (COALESCE(project_id, 'ENTERPRISE')) STORED,
  artifact_id CHAR(36) CHARACTER SET ascii NOT NULL,
  resource_kind VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  resource_id CHAR(36) CHARACTER SET ascii NOT NULL,
  schema_ref VARCHAR(512) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  schema_document_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  dependency_access_set_id CHAR(36) CHARACTER SET ascii NOT NULL,
  dependency_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
  PRIMARY KEY (tenant_id, artifact_id),
  UNIQUE KEY uq_artifact_content_identity
    (tenant_id, project_key, artifact_id, resource_kind, resource_id, schema_ref, schema_document_sha256,
     dependency_access_set_id, dependency_manifest_sha256),
  KEY ix_artifact_content_resource (tenant_id, project_key, resource_kind, resource_id, artifact_id),
  CONSTRAINT fk_artifact_content_dependency FOREIGN KEY (tenant_id, project_key, artifact_id, dependency_access_set_id)
    REFERENCES artifact (tenant_id, project_key, artifact_id, dependency_access_set_id),
  CONSTRAINT fk_artifact_content_manifest FOREIGN KEY (tenant_id, project_key, dependency_access_set_id, dependency_manifest_sha256)
    REFERENCES dependency_access_manifest (tenant_id, project_key, access_set_id, manifest_sha256),
  CONSTRAINT ck_artifact_content_schema CHECK (CHAR_LENGTH(schema_ref) > 0),
  CONSTRAINT ck_artifact_content_kind CHECK (resource_kind IN
    ('ENTERPRISE','PROJECT','MEMBER','SOURCE','TASK','DOCUMENT','ARTIFACT','CONFIGURATION','TEMPLATE','RULE','BUDGET','EVALUATION','AUDIT','REPORT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-192-content-read-range dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE content_read_range (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NULL,
  project_key VARCHAR(36) CHARACTER SET ascii GENERATED ALWAYS AS (COALESCE(project_id, 'ENTERPRISE')) STORED,
  read_receipt_id CHAR(36) CHARACTER SET ascii NOT NULL, artifact_id CHAR(36) CHARACTER SET ascii NOT NULL,
  resource_kind VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  resource_id CHAR(36) CHARACTER SET ascii NOT NULL,
  schema_ref VARCHAR(512) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  schema_document_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  dependency_access_set_id CHAR(36) CHARACTER SET ascii NOT NULL,
  dependency_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  object_ref_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  start_byte BIGINT UNSIGNED NOT NULL, end_byte BIGINT UNSIGNED NOT NULL, size_bytes BIGINT UNSIGNED NOT NULL,
  chunk_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  purpose VARCHAR(128) NOT NULL, authorization_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  context_revision BIGINT UNSIGNED NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
  PRIMARY KEY (tenant_id, read_receipt_id),
  KEY ix_content_range_artifact (tenant_id, project_key, artifact_id, object_ref_sha256, start_byte, end_byte),
  CONSTRAINT fk_content_range_receipt FOREIGN KEY (tenant_id, project_key, read_receipt_id, artifact_id, resource_id)
    REFERENCES content_read_receipt (tenant_id, project_key, read_receipt_id, artifact_id, resource_id),
  CONSTRAINT fk_content_range_binding FOREIGN KEY
    (tenant_id, project_key, artifact_id, resource_kind, resource_id, schema_ref, schema_document_sha256,
     dependency_access_set_id, dependency_manifest_sha256)
    REFERENCES artifact_content_binding
    (tenant_id, project_key, artifact_id, resource_kind, resource_id, schema_ref, schema_document_sha256,
     dependency_access_set_id, dependency_manifest_sha256),
  CONSTRAINT ck_content_range_bytes CHECK (size_bytes > 0 AND start_byte < end_byte AND end_byte <= size_bytes),
  CONSTRAINT ck_content_range_purpose CHECK (CHAR_LENGTH(purpose) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
