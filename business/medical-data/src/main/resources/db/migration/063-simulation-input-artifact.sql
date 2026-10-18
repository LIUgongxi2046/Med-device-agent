--liquibase formatted sql

--changeset medical-agent:063-00-fixture-input-identity dbms:mysql runInTransaction:false failOnError:true
ALTER TABLE simulation_fixture_registration ADD UNIQUE KEY uq_sim_fixture_input_identity
  (tenant_id,project_key,case_id,model_config_revision,input_source_version,input_rights_revision);

--changeset medical-agent:063-01-simulation-input-artifact dbms:mysql runInTransaction:false failOnError:true
-- Immutable bridge: artifact keeps the SOURCE original ObjectRef and raw byte digest.
-- No object, permission, fixture or approval is seeded.
CREATE TABLE simulation_fixture_input_artifact (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  project_id CHAR(36) CHARACTER SET ascii NULL,
  project_key VARCHAR(36) CHARACTER SET ascii GENERATED ALWAYS AS (COALESCE(project_id,'ENTERPRISE')) STORED,
  case_id CHAR(36) CHARACTER SET ascii NOT NULL,
  model_config_revision CHAR(36) CHARACTER SET ascii NOT NULL,
  input_source_version CHAR(36) CHARACTER SET ascii NOT NULL,
  input_rights_revision CHAR(36) CHARACTER SET ascii NOT NULL,
  artifact_id CHAR(36) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL,
  created_at DATETIME(6) NOT NULL,
  classification VARCHAR(24) NOT NULL,
  PRIMARY KEY (tenant_id,project_key,case_id,model_config_revision),
  UNIQUE KEY uq_sim_input_artifact (tenant_id,artifact_id),
  FOREIGN KEY (tenant_id,project_key,case_id,model_config_revision,input_source_version,input_rights_revision)
    REFERENCES simulation_fixture_registration (tenant_id,project_key,case_id,model_config_revision,input_source_version,input_rights_revision),
  FOREIGN KEY (tenant_id,input_source_version,input_rights_revision)
    REFERENCES rights_revision (tenant_id,source_version,rights_revision),
  FOREIGN KEY (tenant_id,project_key,artifact_id) REFERENCES artifact (tenant_id,project_key,artifact_id),
  FOREIGN KEY (tenant_id,created_by) REFERENCES tenant_member (tenant_id,user_id),
  CHECK (artifact_id <> input_source_version),
  CHECK (classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:063-02-simulation-input-artifact-operation dbms:mysql runInTransaction:false failOnError:true
-- Actor + tenant key is separate from the fixture business identity. No update path.
CREATE TABLE simulation_input_artifact_operation (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  actor_id CHAR(36) CHARACTER SET ascii NOT NULL,
  idempotency_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  request_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  canonical_request_json JSON NOT NULL,
  request_body_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  receipt_json JSON NOT NULL,
  schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  schema_version VARCHAR(32) NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL,
  created_at DATETIME(6) NOT NULL,
  classification VARCHAR(24) NOT NULL,
  PRIMARY KEY (tenant_id,actor_id,idempotency_key),
  FOREIGN KEY (tenant_id,actor_id) REFERENCES tenant_member (tenant_id,user_id),
  FOREIGN KEY (tenant_id,created_by) REFERENCES tenant_member (tenant_id,user_id),
  CHECK (created_by = actor_id),
  CHECK (schema_version = '1.0.0'),
  CHECK (request_body_sha256 REGEXP '^[a-f0-9]{64}$'),
  CHECK (JSON_TYPE(canonical_request_json)='OBJECT' AND JSON_LENGTH(canonical_request_json)=2
    AND JSON_CONTAINS_PATH(canonical_request_json,'all','$.case_id','$.request')
    AND JSON_TYPE(JSON_EXTRACT(canonical_request_json,'$.case_id'))='STRING'
    AND JSON_TYPE(JSON_EXTRACT(canonical_request_json,'$.request'))='OBJECT'),
  CHECK (request_sha256 REGEXP '^[a-f0-9]{64}$' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
  CHECK (CHAR_LENGTH(idempotency_key) BETWEEN 16 AND 128),
  CHECK (JSON_TYPE(receipt_json)='OBJECT'
    AND JSON_CONTAINS_PATH(receipt_json,'all','$.schema_version')
    AND JSON_UNQUOTE(JSON_EXTRACT(receipt_json,'$.schema_version')) = schema_version),
  CHECK (classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
