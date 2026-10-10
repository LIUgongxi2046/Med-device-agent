--liquibase formatted sql

--changeset medical-agent:1.0.0-237-source-upload-request dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE source_upload_request (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NOT NULL,
  upload_id CHAR(36) CHARACTER SET ascii NOT NULL, operation_id CHAR(36) CHARACTER SET ascii NOT NULL,
  source_id CHAR(36) CHARACTER SET ascii NULL, version_intent VARCHAR(24) NOT NULL,
  maintainer_id CHAR(36) CHARACTER SET ascii NOT NULL, request_json JSON NOT NULL,
  request_sha256 CHAR(64) CHARACTER SET ascii NOT NULL, schema_ref VARCHAR(256) NOT NULL,
  schema_document_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  authority_grant CHAR(36) CHARACTER SET ascii NOT NULL, authority_revision BIGINT UNSIGNED NOT NULL,
  context_revision BIGINT UNSIGNED NOT NULL, auth_epoch BIGINT UNSIGNED NOT NULL, governance_epoch BIGINT UNSIGNED NOT NULL,
  schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
  PRIMARY KEY (tenant_id, project_id, upload_id),
  UNIQUE KEY uq_source_upload_operation (tenant_id, operation_id),
  FOREIGN KEY (tenant_id, project_id, upload_id) REFERENCES upload_item (tenant_id, project_id, upload_id),
  FOREIGN KEY (tenant_id, operation_id) REFERENCES operation_record (tenant_id, operation_id),
  FOREIGN KEY (tenant_id, source_id) REFERENCES source (tenant_id, source_id),
  FOREIGN KEY (tenant_id, maintainer_id) REFERENCES tenant_member (tenant_id, user_id),
  FOREIGN KEY (tenant_id, created_by) REFERENCES tenant_member (tenant_id, user_id),
  FOREIGN KEY (tenant_id, authority_grant) REFERENCES permission_grant (tenant_id, grant_id),
  CHECK (version_intent IN ('NEW_SOURCE','NEW_VERSION')),
  CHECK ((version_intent='NEW_SOURCE' AND source_id IS NULL) OR (version_intent='NEW_VERSION' AND source_id IS NOT NULL)),
  CHECK (authority_revision > 0),
  CHECK (classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH')),
  CHECK (JSON_TYPE(request_json)='OBJECT' AND JSON_CONTAINS_PATH(request_json,'all','$.schema_version','$.project_id',
    '$.batch_id','$.ownership','$.file_name','$.media_type','$.size_bytes','$.version_intent','$.provenance','$.classification','$.rights_declaration')),
  CHECK (JSON_UNQUOTE(JSON_EXTRACT(request_json,'$.schema_version')) <=> schema_version
    AND JSON_UNQUOTE(JSON_EXTRACT(request_json,'$.project_id')) <=> project_id
    AND JSON_UNQUOTE(JSON_EXTRACT(request_json,'$.version_intent')) <=> version_intent
    AND JSON_UNQUOTE(JSON_EXTRACT(request_json,'$.provenance.maintainer_id')) <=> maintainer_id
    AND JSON_UNQUOTE(JSON_EXTRACT(request_json,'$.classification')) <=> classification)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
