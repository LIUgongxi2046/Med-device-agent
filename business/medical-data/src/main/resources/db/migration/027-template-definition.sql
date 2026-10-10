--liquibase formatted sql

--changeset medical-agent:1.0.0-199-version-registry-digest dbms:mysql runInTransaction:false failOnError:true
ALTER TABLE version_registry ADD UNIQUE KEY uq_version_registry_digest (tenant_id,version_id,content_sha256);

--changeset medical-agent:1.0.0-200-template-version-digest dbms:mysql runInTransaction:false failOnError:true
ALTER TABLE template_version
  ADD UNIQUE KEY uq_template_version_digest (tenant_id,template_version,content_sha256),
  ADD CONSTRAINT fk_template_version_digest FOREIGN KEY (tenant_id,template_version,content_sha256)
    REFERENCES version_registry (tenant_id,version_id,content_sha256);

--changeset medical-agent:1.0.0-201-template-definition dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE template_definition (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  template_version CHAR(36) CHARACTER SET ascii NOT NULL,
  content_json JSON NOT NULL,
  content_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  base_version CHAR(36) CHARACTER SET ascii NULL,
  base_version_sha256 CHAR(64) CHARACTER SET ascii NULL,
  reason TEXT NOT NULL,
  authority_grant CHAR(36) CHARACTER SET ascii NOT NULL,
  authority_revision BIGINT UNSIGNED NOT NULL,
  schema_version VARCHAR(32) NOT NULL,
  schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL,
  created_at DATETIME(6) NOT NULL,
  classification VARCHAR(24) NOT NULL,
  revision BIGINT UNSIGNED NOT NULL,
  PRIMARY KEY (tenant_id,template_version),
  CONSTRAINT fk_template_definition_version FOREIGN KEY (tenant_id,template_version,content_sha256)
    REFERENCES template_version (tenant_id,template_version,content_sha256),
  CONSTRAINT fk_template_definition_base FOREIGN KEY (tenant_id,base_version,base_version_sha256)
    REFERENCES template_version (tenant_id,template_version,content_sha256),
  CONSTRAINT fk_template_definition_creator FOREIGN KEY (tenant_id,created_by) REFERENCES tenant_member (tenant_id,user_id),
  CONSTRAINT fk_template_definition_authority FOREIGN KEY (tenant_id,authority_grant) REFERENCES permission_grant (tenant_id,grant_id),
  CONSTRAINT ck_template_definition_revision CHECK (revision > 0 AND authority_revision > 0),
  CONSTRAINT ck_template_definition_reason CHECK (CHAR_LENGTH(reason) BETWEEN 1 AND 2000),
  CONSTRAINT ck_template_definition_base CHECK (
    (base_version IS NULL AND base_version_sha256 IS NULL) OR
    (base_version IS NOT NULL AND base_version_sha256 IS NOT NULL AND base_version <> template_version)),
  CONSTRAINT ck_template_definition_content CHECK (
    JSON_TYPE(content_json) = 'OBJECT' AND JSON_LENGTH(content_json) = 13
    AND JSON_CONTAINS_PATH(content_json,'all','$.schema_version','$.document_type','$.application_path','$.ivd',
      '$.directory_profile','$.nodes','$.field_definitions','$.repeat_definitions','$.attachment_requirements',
      '$.cross_document_rules','$.condition_schema','$.basis_manifest','$.format_profile')
    AND JSON_UNQUOTE(JSON_EXTRACT(content_json,'$.schema_version')) <=> schema_version
    AND JSON_TYPE(JSON_EXTRACT(content_json,'$.ivd')) = 'BOOLEAN'
    AND JSON_TYPE(JSON_EXTRACT(content_json,'$.nodes')) = 'ARRAY' AND JSON_LENGTH(JSON_EXTRACT(content_json,'$.nodes')) > 0
    AND JSON_TYPE(JSON_EXTRACT(content_json,'$.field_definitions')) = 'ARRAY'
    AND JSON_TYPE(JSON_EXTRACT(content_json,'$.repeat_definitions')) = 'ARRAY'
    AND JSON_TYPE(JSON_EXTRACT(content_json,'$.attachment_requirements')) = 'ARRAY'
    AND JSON_TYPE(JSON_EXTRACT(content_json,'$.cross_document_rules')) = 'ARRAY'
    AND JSON_TYPE(JSON_EXTRACT(content_json,'$.condition_schema')) = 'OBJECT'
    AND JSON_TYPE(JSON_EXTRACT(content_json,'$.basis_manifest')) = 'ARRAY' AND JSON_LENGTH(JSON_EXTRACT(content_json,'$.basis_manifest')) > 0
    AND JSON_TYPE(JSON_EXTRACT(content_json,'$.format_profile')) = 'OBJECT'
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-202-template-attachment dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE template_attachment_requirement (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  template_version CHAR(36) CHARACTER SET ascii NOT NULL,
  attachment_requirement_id CHAR(36) CHARACTER SET ascii NOT NULL,
  node_id CHAR(36) CHARACTER SET ascii NOT NULL,
  ordinal BIGINT UNSIGNED NOT NULL,
  requirement_json JSON NOT NULL,
  content_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  schema_version VARCHAR(32) NOT NULL,
  schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL,
  created_at DATETIME(6) NOT NULL,
  classification VARCHAR(24) NOT NULL,
  PRIMARY KEY (tenant_id,template_version,attachment_requirement_id),
  UNIQUE KEY uq_template_attachment_order (tenant_id,template_version,node_id,ordinal),
  CONSTRAINT fk_template_attachment_node FOREIGN KEY (tenant_id,template_version,node_id)
    REFERENCES requirement_node (tenant_id,template_version,node_id),
  CONSTRAINT fk_template_attachment_creator FOREIGN KEY (tenant_id,created_by) REFERENCES tenant_member (tenant_id,user_id),
  CONSTRAINT ck_template_attachment_order CHECK (ordinal > 0),
  CONSTRAINT ck_template_attachment_identity CHECK (
    JSON_TYPE(requirement_json) = 'OBJECT' AND JSON_LENGTH(requirement_json) = 13
    AND JSON_CONTAINS_PATH(requirement_json,'all','$.attachment_requirement_id','$.node_id','$.name','$.kind','$.mode',
      '$.condition','$.required','$.min_count','$.max_count','$.count_basis','$.allowed_media_types','$.evidence_rule','$.ordinal')
    AND JSON_UNQUOTE(JSON_EXTRACT(requirement_json,'$.attachment_requirement_id')) <=> attachment_requirement_id
    AND JSON_UNQUOTE(JSON_EXTRACT(requirement_json,'$.node_id')) <=> node_id
    AND JSON_TYPE(JSON_EXTRACT(requirement_json,'$.ordinal')) = 'INTEGER'
    AND CAST(JSON_UNQUOTE(JSON_EXTRACT(requirement_json,'$.ordinal')) AS UNSIGNED) = ordinal
    AND JSON_UNQUOTE(JSON_EXTRACT(requirement_json,'$.mode')) <=> 'AUTHORIZED_ORIGINAL_ONLY'
    AND JSON_UNQUOTE(JSON_EXTRACT(requirement_json,'$.evidence_rule.mode')) <=> 'AUTHORIZED_ORIGINAL_ONLY'
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-203-template-dependency dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE template_definition_dependency (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  template_version CHAR(36) CHARACTER SET ascii NOT NULL,
  upstream_version CHAR(36) CHARACTER SET ascii NOT NULL,
  upstream_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  ordinal BIGINT UNSIGNED NOT NULL,
  schema_version VARCHAR(32) NOT NULL,
  schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL,
  created_at DATETIME(6) NOT NULL,
  classification VARCHAR(24) NOT NULL,
  PRIMARY KEY (tenant_id,template_version,upstream_version),
  UNIQUE KEY uq_template_dependency_order (tenant_id,template_version,ordinal),
  CONSTRAINT fk_template_dependency_definition FOREIGN KEY (tenant_id,template_version)
    REFERENCES template_definition (tenant_id,template_version),
  CONSTRAINT fk_template_dependency_version FOREIGN KEY (tenant_id,upstream_version,upstream_sha256)
    REFERENCES version_registry (tenant_id,version_id,content_sha256),
  CONSTRAINT fk_template_dependency_creator FOREIGN KEY (tenant_id,created_by) REFERENCES tenant_member (tenant_id,user_id),
  CONSTRAINT ck_template_dependency_order CHECK (ordinal > 0 AND upstream_version <> template_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
