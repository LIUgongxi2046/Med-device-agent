--liquibase formatted sql

--changeset medical-agent:1.0.0-302-model-configuration-definition dbms:mysql runInTransaction:false failOnError:true
-- No backfill: old partial model rows cannot acquire fabricated provenance or policy.
CREATE TABLE model_configuration_definition (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  model_config_revision CHAR(36) CHARACTER SET ascii NOT NULL,
  content_json JSON NOT NULL,
  content_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  schema_version VARCHAR(32) NOT NULL,
  schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL,
  created_at DATETIME(6) NOT NULL,
  classification VARCHAR(24) NOT NULL,
  PRIMARY KEY (tenant_id,model_config_revision),
  FOREIGN KEY (tenant_id,model_config_revision) REFERENCES model_config_revision (tenant_id,model_config_revision),
  FOREIGN KEY (tenant_id,model_config_revision,content_sha256) REFERENCES version_registry (tenant_id,version_id,content_sha256),
  FOREIGN KEY (tenant_id,created_by) REFERENCES tenant_member (tenant_id,user_id),
  CHECK (content_sha256 REGEXP '^[a-f0-9]{64}$'),
  CHECK (JSON_TYPE(content_json)='OBJECT')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
