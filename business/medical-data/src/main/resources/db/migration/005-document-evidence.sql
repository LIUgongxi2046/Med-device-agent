--liquibase formatted sql

--changeset medical-agent:1.0.0-050-dependency-access-set dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE dependency_access_set (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  project_id CHAR(36) CHARACTER SET ascii NULL,
  project_key VARCHAR(36) CHARACTER SET ascii GENERATED ALWAYS AS (COALESCE(project_id, 'ENTERPRISE')) STORED,
  access_set_id CHAR(36) CHARACTER SET ascii NOT NULL,
  manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  content_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
  PRIMARY KEY (tenant_id, access_set_id),
  UNIQUE KEY uq_access_set_scope (tenant_id, project_key, access_set_id),
  FOREIGN KEY (tenant_id) REFERENCES tenant (tenant_id),
  FOREIGN KEY (tenant_id, project_id) REFERENCES project (tenant_id, project_id),
  FOREIGN KEY (tenant_id, access_set_id) REFERENCES version_registry (tenant_id, version_id),
  CHECK (content_sha256 = manifest_sha256)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-051-dependency-access-member dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE dependency_access_member (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  access_set_id CHAR(36) CHARACTER SET ascii NOT NULL,
  upstream_version_id CHAR(36) CHARACTER SET ascii NOT NULL,
  rights_revision CHAR(36) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
  PRIMARY KEY (tenant_id, access_set_id, upstream_version_id),
  KEY ix_access_upstream (tenant_id, upstream_version_id, access_set_id),
  FOREIGN KEY (tenant_id, access_set_id) REFERENCES dependency_access_set (tenant_id, access_set_id),
  FOREIGN KEY (tenant_id, upstream_version_id) REFERENCES version_registry (tenant_id, version_id),
  FOREIGN KEY (tenant_id, rights_revision) REFERENCES rights_revision (tenant_id, rights_revision)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-052-context-manifest dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE context_manifest (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  project_id CHAR(36) CHARACTER SET ascii NULL,
  project_key VARCHAR(36) CHARACTER SET ascii GENERATED ALWAYS AS (COALESCE(project_id, 'ENTERPRISE')) STORED,
  context_manifest_id CHAR(36) CHARACTER SET ascii NOT NULL,
  exact_input_ref JSON NOT NULL, source_manifest JSON NOT NULL,
  prompt_revision CHAR(36) CHARACTER SET ascii NOT NULL,
  tokenizer_revision VARCHAR(128) NOT NULL, token_count BIGINT UNSIGNED NOT NULL,
  dependency_access_set_id CHAR(36) CHARACTER SET ascii NOT NULL,
  content_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
  PRIMARY KEY (tenant_id, context_manifest_id),
  UNIQUE KEY uq_context_scope (tenant_id, project_key, context_manifest_id),
  FOREIGN KEY (tenant_id) REFERENCES tenant (tenant_id),
  FOREIGN KEY (tenant_id, project_id) REFERENCES project (tenant_id, project_id),
  FOREIGN KEY (tenant_id, context_manifest_id) REFERENCES version_registry (tenant_id, version_id),
  FOREIGN KEY (tenant_id, project_key, dependency_access_set_id) REFERENCES dependency_access_set (tenant_id, project_key, access_set_id),
  FOREIGN KEY (tenant_id, prompt_revision) REFERENCES version_registry (tenant_id, version_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-053-document-head dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE document_head (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NOT NULL,
  document_id CHAR(36) CHARACTER SET ascii NOT NULL,
  current_document_revision CHAR(36) CHARACTER SET ascii NULL,
  task_id CHAR(36) CHARACTER SET ascii NOT NULL, status VARCHAR(24) NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
  revision BIGINT UNSIGNED NOT NULL, updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (tenant_id, project_id, document_id),
  UNIQUE KEY uq_document_task (tenant_id, project_id, document_id, task_id),
  KEY ix_document_task_status (tenant_id, project_id, task_id, status),
  FOREIGN KEY (tenant_id, project_id, task_id) REFERENCES task_head (tenant_id, project_id, task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-054-document-identity-key dbms:mysql runInTransaction:false failOnError:true
ALTER TABLE document_revision ADD UNIQUE KEY uq_document_fixed_identity
  (tenant_id, project_id, document_id, document_revision);

--changeset medical-agent:1.0.0-055-document-body-key dbms:mysql runInTransaction:false failOnError:true
ALTER TABLE document_revision ADD UNIQUE KEY uq_document_fixed_body
  (tenant_id, project_id, document_revision, snapshot_id, body_sha256);

--changeset medical-agent:1.0.0-056-document-revision-head dbms:mysql runInTransaction:false failOnError:true
ALTER TABLE document_revision ADD CONSTRAINT fk_document_revision_head
  FOREIGN KEY (tenant_id, project_id, document_id) REFERENCES document_head (tenant_id, project_id, document_id);

--changeset medical-agent:1.0.0-057-document-current dbms:mysql runInTransaction:false failOnError:true
ALTER TABLE document_head ADD CONSTRAINT fk_document_current
  FOREIGN KEY (tenant_id, project_id, document_id, current_document_revision)
  REFERENCES document_revision (tenant_id, project_id, document_id, document_revision);

--changeset medical-agent:1.0.0-058-plan-document-basis dbms:mysql runInTransaction:false failOnError:true
ALTER TABLE plan_revision ADD UNIQUE KEY uq_plan_document_basis
  (tenant_id, project_id, task_id, task_revision, plan_revision, template_version, snapshot_id);

--changeset medical-agent:1.0.0-059-document-revision-meta dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE document_revision_meta (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NOT NULL,
  project_key VARCHAR(36) CHARACTER SET ascii GENERATED ALWAYS AS (project_id) STORED,
  document_revision CHAR(36) CHARACTER SET ascii NOT NULL, document_id CHAR(36) CHARACTER SET ascii NOT NULL,
  task_id CHAR(36) CHARACTER SET ascii NOT NULL, task_revision CHAR(36) CHARACTER SET ascii NOT NULL,
  snapshot_id CHAR(36) CHARACTER SET ascii NOT NULL,
  based_on_project_id CHAR(36) CHARACTER SET ascii NULL,
  based_on_document_id CHAR(36) CHARACTER SET ascii NULL,
  based_on_document_revision CHAR(36) CHARACTER SET ascii NULL,
  body_object_version VARCHAR(256) NOT NULL, schema_revision VARCHAR(64) NOT NULL,
  body_schema_revision CHAR(36) CHARACTER SET ascii NOT NULL, body_artifact_id CHAR(36) CHARACTER SET ascii NOT NULL,
  body_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  plan_revision CHAR(36) CHARACTER SET ascii NOT NULL, template_version CHAR(36) CHARACTER SET ascii NOT NULL,
  issue_watermark BIGINT UNSIGNED NOT NULL, dependency_access_set_id CHAR(36) CHARACTER SET ascii NOT NULL,
  content_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
  PRIMARY KEY (tenant_id, project_id, document_revision),
  UNIQUE KEY uq_document_meta_identity (tenant_id, project_id, document_id, document_revision),
  UNIQUE KEY uq_document_template (tenant_id, project_id, document_revision, template_version),
  FOREIGN KEY (tenant_id, project_id, document_id, document_revision)
    REFERENCES document_revision (tenant_id, project_id, document_id, document_revision),
  FOREIGN KEY (tenant_id, project_id, document_revision, snapshot_id, body_sha256)
    REFERENCES document_revision (tenant_id, project_id, document_revision, snapshot_id, body_sha256),
  FOREIGN KEY (tenant_id, project_id, document_id, task_id) REFERENCES document_head (tenant_id, project_id, document_id, task_id),
  FOREIGN KEY (tenant_id, based_on_project_id, based_on_document_id, based_on_document_revision)
    REFERENCES document_revision (tenant_id, project_id, document_id, document_revision),
  FOREIGN KEY (tenant_id, project_id, task_id, task_revision, plan_revision, template_version, snapshot_id)
    REFERENCES plan_revision (tenant_id, project_id, task_id, task_revision, plan_revision, template_version, snapshot_id),
  FOREIGN KEY (tenant_id, project_key, dependency_access_set_id) REFERENCES dependency_access_set (tenant_id, project_key, access_set_id),
  FOREIGN KEY (tenant_id, body_schema_revision) REFERENCES version_registry (tenant_id, version_id),
  FOREIGN KEY (tenant_id, document_revision) REFERENCES version_registry (tenant_id, version_id),
  CHECK (based_on_document_revision IS NULL OR based_on_document_revision <> document_revision),
  CHECK ((based_on_project_id IS NULL AND based_on_document_id IS NULL AND based_on_document_revision IS NULL)
    OR (based_on_project_id IS NOT NULL AND based_on_document_id IS NOT NULL AND based_on_document_revision IS NOT NULL)),
  CHECK (content_sha256 = body_sha256)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-060-section-revision dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE section_revision (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NOT NULL,
  section_revision CHAR(36) CHARACTER SET ascii NOT NULL, section_id CHAR(36) CHARACTER SET ascii NOT NULL,
  plan_revision CHAR(36) CHARACTER SET ascii NOT NULL,
  body_ref JSON NOT NULL, evidence_manifest JSON NOT NULL, status VARCHAR(24) NOT NULL,
  content_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
  PRIMARY KEY (tenant_id, project_id, section_revision),
  UNIQUE KEY uq_section_fixed_identity (tenant_id, project_id, section_id, section_revision),
  FOREIGN KEY (tenant_id, project_id, plan_revision, section_id) REFERENCES plan_section (tenant_id, project_id, plan_revision, section_id),
  FOREIGN KEY (tenant_id, section_revision) REFERENCES version_registry (tenant_id, version_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-061-document-section dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE document_section (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NOT NULL,
  document_revision CHAR(36) CHARACTER SET ascii NOT NULL, section_id CHAR(36) CHARACTER SET ascii NOT NULL,
  section_revision CHAR(36) CHARACTER SET ascii NOT NULL, ordinal BIGINT UNSIGNED NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
  PRIMARY KEY (tenant_id, project_id, document_revision, section_id),
  UNIQUE KEY uq_document_section_order (tenant_id, project_id, document_revision, ordinal),
  UNIQUE KEY uq_document_section_revision (tenant_id, project_id, document_revision, section_id, section_revision),
  FOREIGN KEY (tenant_id, project_id, document_revision) REFERENCES document_revision_meta (tenant_id, project_id, document_revision),
  FOREIGN KEY (tenant_id, project_id, section_id, section_revision) REFERENCES section_revision (tenant_id, project_id, section_id, section_revision)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-062-document-node dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE document_node (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NOT NULL,
  document_revision CHAR(36) CHARACTER SET ascii NOT NULL, node_id CHAR(36) CHARACTER SET ascii NOT NULL,
  node_kind VARCHAR(32) NOT NULL, kind VARCHAR(24) GENERATED ALWAYS AS (node_kind) STORED,
  parent_id CHAR(36) CHARACTER SET ascii NULL,
  parent_key VARCHAR(36) CHARACTER SET ascii GENERATED ALWAYS AS (COALESCE(parent_id, 'ROOT')) STORED,
  ordinal BIGINT UNSIGNED NOT NULL, node_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  content_sha256 CHAR(64) CHARACTER SET ascii GENERATED ALWAYS AS (node_sha256) STORED,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
  PRIMARY KEY (tenant_id, project_id, document_revision, node_id),
  UNIQUE KEY uq_document_node_order (tenant_id, project_id, document_revision, parent_key, ordinal),
  FOREIGN KEY (tenant_id, project_id, document_revision) REFERENCES document_revision_meta (tenant_id, project_id, document_revision),
  FOREIGN KEY (tenant_id, project_id, document_revision, parent_id) REFERENCES document_node (tenant_id, project_id, document_revision, node_id),
  CHECK (parent_id IS NULL OR parent_id <> node_id),
  CHECK (CHAR_LENGTH(node_kind) <= 24)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-063-shared-fact dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE shared_fact_version (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NOT NULL,
  fact_version CHAR(36) CHARACTER SET ascii NOT NULL, fact_key VARCHAR(128) NOT NULL,
  raw_value TEXT NOT NULL, value DECIMAL(38,12) NULL, unit VARCHAR(64) NULL,
  model_ids JSON NOT NULL, condition_id CHAR(36) CHARACTER SET ascii NULL,
  evidence_manifest JSON NOT NULL, verifier_id CHAR(36) CHARACTER SET ascii NOT NULL, status VARCHAR(24) NOT NULL,
  content_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
  PRIMARY KEY (tenant_id, project_id, fact_version),
  UNIQUE KEY uq_fact_key_version (tenant_id, project_id, fact_key, fact_version),
  FOREIGN KEY (tenant_id, project_id) REFERENCES project (tenant_id, project_id),
  FOREIGN KEY (tenant_id, fact_version) REFERENCES version_registry (tenant_id, version_id),
  FOREIGN KEY (tenant_id, verifier_id) REFERENCES tenant_member (tenant_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-064-field-value dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE field_value (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NOT NULL,
  document_revision CHAR(36) CHARACTER SET ascii NOT NULL, template_version CHAR(36) CHARACTER SET ascii NOT NULL,
  field_id CHAR(36) CHARACTER SET ascii NOT NULL, row_id CHAR(36) CHARACTER SET ascii NULL,
  row_key VARCHAR(36) CHARACTER SET ascii GENERATED ALWAYS AS (COALESCE(row_id, 'SCALAR')) STORED,
  node_id CHAR(36) CHARACTER SET ascii NOT NULL, value_json JSON NOT NULL, raw_value TEXT NULL,
  status VARCHAR(24) NOT NULL, source_refs JSON NOT NULL, fact_version CHAR(36) CHARACTER SET ascii NULL,
  model_ids JSON NOT NULL, condition_id CHAR(36) CHARACTER SET ascii NULL, empty_reason VARCHAR(64) NULL,
  schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
  PRIMARY KEY (tenant_id, project_id, document_revision, field_id, row_key),
  UNIQUE KEY uq_field_node (tenant_id, project_id, document_revision, node_id, field_id, row_key),
  FOREIGN KEY (tenant_id, project_id, document_revision, template_version) REFERENCES document_revision_meta (tenant_id, project_id, document_revision, template_version),
  FOREIGN KEY (tenant_id, template_version, field_id) REFERENCES field_definition (tenant_id, template_version, field_id),
  FOREIGN KEY (tenant_id, project_id, document_revision, node_id) REFERENCES document_node (tenant_id, project_id, document_revision, node_id),
  FOREIGN KEY (tenant_id, project_id, fact_version) REFERENCES shared_fact_version (tenant_id, project_id, fact_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-065-fact-usage dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE fact_usage (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NOT NULL,
  usage_id CHAR(36) CHARACTER SET ascii NOT NULL, fact_version CHAR(36) CHARACTER SET ascii NOT NULL,
  document_revision CHAR(36) CHARACTER SET ascii NOT NULL, node_id CHAR(36) CHARACTER SET ascii NOT NULL,
  field_id CHAR(36) CHARACTER SET ascii NULL, row_id CHAR(36) CHARACTER SET ascii NULL,
  row_key VARCHAR(36) CHARACTER SET ascii GENERATED ALWAYS AS (COALESCE(row_id, 'SCALAR')) STORED,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
  PRIMARY KEY (tenant_id, project_id, usage_id),
  KEY ix_fact_usage_reverse (tenant_id, project_id, fact_version, document_revision),
  KEY ix_fact_usage_document (tenant_id, project_id, document_revision, node_id),
  FOREIGN KEY (tenant_id, project_id, fact_version) REFERENCES shared_fact_version (tenant_id, project_id, fact_version),
  FOREIGN KEY (tenant_id, project_id, document_revision, node_id) REFERENCES document_node (tenant_id, project_id, document_revision, node_id),
  FOREIGN KEY (tenant_id, project_id, document_revision, node_id, field_id, row_key) REFERENCES field_value (tenant_id, project_id, document_revision, node_id, field_id, row_key),
  CHECK (row_id IS NULL OR field_id IS NOT NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-066-metric dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE metric (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NOT NULL,
  document_revision CHAR(36) CHARACTER SET ascii NOT NULL, metric_id CHAR(36) CHARACTER SET ascii NOT NULL,
  number VARCHAR(64) NOT NULL, requirement JSON NOT NULL,
  schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
  PRIMARY KEY (tenant_id, project_id, document_revision, metric_id),
  UNIQUE KEY uq_metric_number (tenant_id, project_id, document_revision, number),
  FOREIGN KEY (tenant_id, project_id, document_revision) REFERENCES document_revision_meta (tenant_id, project_id, document_revision)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-067-method dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE method (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NOT NULL,
  document_revision CHAR(36) CHARACTER SET ascii NOT NULL, method_id CHAR(36) CHARACTER SET ascii NOT NULL,
  number VARCHAR(64) NOT NULL, procedure_ref JSON NOT NULL, basis_refs JSON NOT NULL,
  schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
  PRIMARY KEY (tenant_id, project_id, document_revision, method_id),
  UNIQUE KEY uq_method_number (tenant_id, project_id, document_revision, number),
  FOREIGN KEY (tenant_id, project_id, document_revision) REFERENCES document_revision_meta (tenant_id, project_id, document_revision)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-068-metric-method-coverage dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE metric_method_coverage (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NOT NULL,
  document_revision CHAR(36) CHARACTER SET ascii NOT NULL, metric_id CHAR(36) CHARACTER SET ascii NOT NULL,
  model_id CHAR(36) CHARACTER SET ascii NOT NULL, condition_id CHAR(36) CHARACTER SET ascii NOT NULL,
  method_id CHAR(36) CHARACTER SET ascii NOT NULL, evidence_refs JSON NOT NULL,
  schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
  PRIMARY KEY (tenant_id, project_id, document_revision, metric_id, model_id, condition_id, method_id),
  FOREIGN KEY (tenant_id, project_id, document_revision, metric_id) REFERENCES metric (tenant_id, project_id, document_revision, metric_id),
  FOREIGN KEY (tenant_id, project_id, document_revision, method_id) REFERENCES method (tenant_id, project_id, document_revision, method_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-069-attachment-binding dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE attachment_binding (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NOT NULL,
  binding_id CHAR(36) CHARACTER SET ascii NOT NULL, document_revision CHAR(36) CHARACTER SET ascii NOT NULL,
  node_id CHAR(36) CHARACTER SET ascii NOT NULL, source_version CHAR(36) CHARACTER SET ascii NOT NULL,
  evidence_kind VARCHAR(32) NOT NULL, verification_revision CHAR(36) CHARACTER SET ascii NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
  PRIMARY KEY (tenant_id, project_id, binding_id),
  KEY ix_attachment_document (tenant_id, project_id, document_revision, node_id),
  FOREIGN KEY (tenant_id, project_id, document_revision, node_id) REFERENCES document_node (tenant_id, project_id, document_revision, node_id),
  FOREIGN KEY (tenant_id, source_version) REFERENCES source_version (tenant_id, source_version),
  FOREIGN KEY (tenant_id, verification_revision) REFERENCES version_registry (tenant_id, version_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-070-artifact dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE artifact (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NULL,
  project_key VARCHAR(36) CHARACTER SET ascii GENERATED ALWAYS AS (COALESCE(project_id, 'ENTERPRISE')) STORED,
  artifact_id CHAR(36) CHARACTER SET ascii NOT NULL, run_id CHAR(36) CHARACTER SET ascii NULL,
  document_revision CHAR(36) CHARACTER SET ascii NULL, artifact_kind VARCHAR(32) NOT NULL,
  object_ref JSON NOT NULL, object_key VARCHAR(1024) NOT NULL, object_version VARCHAR(256) NOT NULL,
  sha256 CHAR(64) CHARACTER SET ascii NOT NULL, size_bytes BIGINT UNSIGNED NOT NULL, media_type VARCHAR(128) NOT NULL,
  dependency_access_set_id CHAR(36) CHARACTER SET ascii NOT NULL, content_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
  PRIMARY KEY (tenant_id, artifact_id),
  UNIQUE KEY uq_artifact_scope (tenant_id, project_key, artifact_id),
  UNIQUE KEY uq_artifact_body (tenant_id, project_key, artifact_id, sha256, object_version),
  KEY ix_artifact_document (tenant_id, project_id, document_revision),
  FOREIGN KEY (tenant_id) REFERENCES tenant (tenant_id),
  FOREIGN KEY (tenant_id, project_key, run_id) REFERENCES execution_registry (tenant_id, project_key, run_id),
  FOREIGN KEY (tenant_id, project_id, document_revision) REFERENCES document_revision (tenant_id, project_id, document_revision),
  FOREIGN KEY (tenant_id, project_key, dependency_access_set_id) REFERENCES dependency_access_set (tenant_id, project_key, access_set_id),
  FOREIGN KEY (tenant_id, artifact_id) REFERENCES version_registry (tenant_id, version_id),
  CHECK (document_revision IS NULL OR project_id IS NOT NULL),
  CHECK (size_bytes > 0 AND content_sha256 = sha256),
  CHECK (JSON_TYPE(object_ref) = 'OBJECT'),
  CHECK (JSON_UNQUOTE(JSON_EXTRACT(object_ref, '$.sha256')) <=> sha256),
  CHECK (JSON_UNQUOTE(JSON_EXTRACT(object_ref, '$.object_key')) <=> object_key),
  CHECK (JSON_UNQUOTE(JSON_EXTRACT(object_ref, '$.object_version')) <=> object_version),
  CHECK (JSON_EXTRACT(object_ref, '$.size_bytes') <=> size_bytes),
  CHECK (JSON_UNQUOTE(JSON_EXTRACT(object_ref, '$.media_type')) <=> media_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-071-document-body-artifact dbms:mysql runInTransaction:false failOnError:true
ALTER TABLE document_revision_meta ADD CONSTRAINT fk_document_body_artifact
  FOREIGN KEY (tenant_id, project_key, body_artifact_id, body_sha256, body_object_version)
  REFERENCES artifact (tenant_id, project_key, artifact_id, sha256, object_version);

--changeset medical-agent:1.0.0-072-action-result-artifact dbms:mysql runInTransaction:false failOnError:true
ALTER TABLE step_action_binding ADD CONSTRAINT fk_action_result_artifact
  FOREIGN KEY (tenant_id, project_key, result_artifact_id) REFERENCES artifact (tenant_id, project_key, artifact_id);

--changeset medical-agent:1.0.0-092-artifact-content-key dbms:mysql runInTransaction:false failOnError:true
ALTER TABLE artifact ADD UNIQUE KEY uq_artifact_content (tenant_id, project_key, artifact_id, sha256);
