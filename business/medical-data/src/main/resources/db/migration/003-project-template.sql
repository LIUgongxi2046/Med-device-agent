--liquibase formatted sql

--changeset medical-agent:1.0.0-027-project-profile dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE project_profile_revision (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  project_id CHAR(36) CHARACTER SET ascii NOT NULL,
  profile_revision CHAR(36) CHARACTER SET ascii NOT NULL,
  attributes JSON NOT NULL,
  authority_refs JSON NOT NULL,
  content_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  schema_version VARCHAR(32) NOT NULL,
  schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL,
  created_at DATETIME(6) NOT NULL,
  classification VARCHAR(24) NOT NULL,
  PRIMARY KEY (tenant_id, project_id, profile_revision),
  FOREIGN KEY (tenant_id, project_id) REFERENCES project (tenant_id, project_id),
  FOREIGN KEY (tenant_id, profile_revision) REFERENCES version_registry (tenant_id, version_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-028-applicability dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE applicability_decision (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  project_id CHAR(36) CHARACTER SET ascii NOT NULL,
  decision_id CHAR(36) CHARACTER SET ascii NOT NULL,
  profile_revision CHAR(36) CHARACTER SET ascii NOT NULL,
  target_version CHAR(36) CHARACTER SET ascii NOT NULL,
  result VARCHAR(24) NOT NULL,
  reason TEXT NOT NULL,
  evidence_refs JSON NOT NULL,
  actor_id CHAR(36) CHARACTER SET ascii NOT NULL,
  decided_at DATETIME(6) NOT NULL,
  schema_version VARCHAR(32) NOT NULL,
  schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL,
  created_at DATETIME(6) NOT NULL,
  classification VARCHAR(24) NOT NULL,
  PRIMARY KEY (tenant_id, project_id, decision_id),
  KEY ix_applicability_target (tenant_id, project_id, target_version, profile_revision),
  FOREIGN KEY (tenant_id, project_id, profile_revision) REFERENCES project_profile_revision (tenant_id, project_id, profile_revision),
  FOREIGN KEY (tenant_id, target_version) REFERENCES version_registry (tenant_id, version_id),
  FOREIGN KEY (tenant_id, actor_id) REFERENCES tenant_member (tenant_id, user_id),
  CHECK (result IN ('APPLICABLE','NOT_APPLICABLE','UNKNOWN'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-029-template-version dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE template_version (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  template_version CHAR(36) CHARACTER SET ascii NOT NULL,
  document_type VARCHAR(64) NOT NULL,
  application_path VARCHAR(32) NOT NULL,
  status VARCHAR(24) NOT NULL,
  basis_manifest JSON NOT NULL,
  format_profile JSON NOT NULL,
  condition_schema JSON NOT NULL,
  content_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  schema_version VARCHAR(32) NOT NULL,
  schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL,
  created_at DATETIME(6) NOT NULL,
  classification VARCHAR(24) NOT NULL,
  PRIMARY KEY (tenant_id, template_version),
  KEY ix_template_path (tenant_id, document_type, application_path, status),
  FOREIGN KEY (tenant_id, template_version) REFERENCES version_registry (tenant_id, version_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-030-requirement-node dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE requirement_node (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  template_version CHAR(36) CHARACTER SET ascii NOT NULL,
  node_id CHAR(36) CHARACTER SET ascii NOT NULL,
  parent_id CHAR(36) CHARACTER SET ascii NULL,
  official_number VARCHAR(64) NOT NULL,
  title VARCHAR(512) NOT NULL,
  ordinal BIGINT UNSIGNED NOT NULL,
  `condition` JSON NOT NULL,
  required BOOLEAN NOT NULL,
  content_mode VARCHAR(32) NOT NULL,
  evidence_rule JSON NOT NULL,
  schema_version VARCHAR(32) NOT NULL,
  schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL,
  created_at DATETIME(6) NOT NULL,
  classification VARCHAR(24) NOT NULL,
  PRIMARY KEY (tenant_id, template_version, node_id),
  UNIQUE KEY uq_requirement_number (tenant_id, template_version, official_number),
  KEY ix_requirement_parent (tenant_id, template_version, parent_id, ordinal),
  FOREIGN KEY (tenant_id, template_version) REFERENCES template_version (tenant_id, template_version),
  FOREIGN KEY (tenant_id, template_version, parent_id) REFERENCES requirement_node (tenant_id, template_version, node_id),
  CHECK (parent_id IS NULL OR parent_id <> node_id),
  CHECK (required IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-031-field-definition dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE field_definition (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  template_version CHAR(36) CHARACTER SET ascii NOT NULL,
  field_id CHAR(36) CHARACTER SET ascii NOT NULL,
  node_id CHAR(36) CHARACTER SET ascii NOT NULL,
  value_type VARCHAR(32) NOT NULL,
  constraints JSON NOT NULL,
  `condition` JSON NOT NULL,
  evidence_rule JSON NOT NULL,
  output_mapping JSON NOT NULL,
  schema_version VARCHAR(32) NOT NULL,
  schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL,
  created_at DATETIME(6) NOT NULL,
  classification VARCHAR(24) NOT NULL,
  PRIMARY KEY (tenant_id, template_version, field_id),
  KEY ix_definition_node (tenant_id, template_version, node_id),
  FOREIGN KEY (tenant_id, template_version, node_id) REFERENCES requirement_node (tenant_id, template_version, node_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-032-repeat-definition dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE repeat_definition (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  template_version CHAR(36) CHARACTER SET ascii NOT NULL,
  repeat_id CHAR(36) CHARACTER SET ascii NOT NULL,
  row_schema JSON NOT NULL,
  min_count BIGINT UNSIGNED NOT NULL,
  max_count BIGINT UNSIGNED NULL,
  schema_version VARCHAR(32) NOT NULL,
  schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL,
  created_at DATETIME(6) NOT NULL,
  classification VARCHAR(24) NOT NULL,
  PRIMARY KEY (tenant_id, template_version, repeat_id),
  FOREIGN KEY (tenant_id, template_version) REFERENCES template_version (tenant_id, template_version),
  CHECK (max_count IS NULL OR max_count >= min_count)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-033-rule-version dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE rule_version (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  rule_version CHAR(36) CHARACTER SET ascii NOT NULL,
  engine_revision VARCHAR(128) NOT NULL,
  expression JSON NOT NULL,
  basis_manifest JSON NOT NULL,
  status VARCHAR(24) NOT NULL,
  content_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  schema_version VARCHAR(32) NOT NULL,
  schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL,
  created_at DATETIME(6) NOT NULL,
  classification VARCHAR(24) NOT NULL,
  PRIMARY KEY (tenant_id, rule_version),
  FOREIGN KEY (tenant_id, rule_version) REFERENCES version_registry (tenant_id, version_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
