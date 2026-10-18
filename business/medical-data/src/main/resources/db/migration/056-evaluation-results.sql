--liquibase formatted sql

--changeset medical-agent:1.0.0-308-evaluation-definition dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE evaluation_definition (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  evaluation_id CHAR(36) CHARACTER SET ascii NOT NULL,
  definition_json JSON NOT NULL,
  content_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  schema_version VARCHAR(32) NOT NULL,
  schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL,
  created_at DATETIME(6) NOT NULL,
  classification VARCHAR(24) NOT NULL,
  PRIMARY KEY (tenant_id,evaluation_id),
  UNIQUE KEY uq_evaluation_definition_hash (tenant_id,evaluation_id,content_sha256),
  FOREIGN KEY (tenant_id,evaluation_id) REFERENCES evaluation_batch (tenant_id,evaluation_id),
  FOREIGN KEY (tenant_id,evaluation_id,content_sha256) REFERENCES version_registry (tenant_id,version_id,content_sha256),
  FOREIGN KEY (tenant_id,created_by) REFERENCES tenant_member (tenant_id,user_id),
  CHECK (JSON_TYPE(definition_json)='OBJECT')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-309-evaluation-observation dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE evaluation_score_observation (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  score_id CHAR(36) CHARACTER SET ascii NOT NULL,
  evaluation_id CHAR(36) CHARACTER SET ascii NOT NULL,
  definition_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  sample_id CHAR(36) CHARACTER SET ascii NOT NULL,
  agent_version_id CHAR(36) CHARACTER SET ascii NOT NULL,
  dimension VARCHAR(64) NOT NULL,
  repeat_index INT UNSIGNED NOT NULL,
  supersedes CHAR(36) CHARACTER SET ascii NULL,
  expected_revision BIGINT UNSIGNED NOT NULL,
  score_json JSON NOT NULL,
  content_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  schema_version VARCHAR(32) NOT NULL,
  schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL,
  created_at DATETIME(6) NOT NULL,
  classification VARCHAR(24) NOT NULL,
  PRIMARY KEY (tenant_id,score_id),
  UNIQUE KEY uq_evaluation_observation_revision (tenant_id,evaluation_id,expected_revision),
  UNIQUE KEY uq_evaluation_observation_successor (tenant_id,supersedes),
  UNIQUE KEY uq_evaluation_observation_cell (tenant_id,evaluation_id,sample_id,agent_version_id,dimension,repeat_index,score_id),
  FOREIGN KEY (tenant_id,score_id) REFERENCES evaluation_score (tenant_id,score_id),
  FOREIGN KEY (tenant_id,evaluation_id,definition_sha256) REFERENCES evaluation_definition (tenant_id,evaluation_id,content_sha256),
  FOREIGN KEY (tenant_id,evaluation_id,sample_id,agent_version_id,dimension,repeat_index,supersedes)
    REFERENCES evaluation_score_observation (tenant_id,evaluation_id,sample_id,agent_version_id,dimension,repeat_index,score_id),
  FOREIGN KEY (tenant_id,created_by) REFERENCES tenant_member (tenant_id,user_id),
  CHECK (repeat_index < 100 AND expected_revision > 0),
  CHECK (supersedes IS NULL OR supersedes <> score_id),
  CHECK (JSON_TYPE(score_json)='OBJECT')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-310-evaluation-result dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE evaluation_result (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  evaluation_id CHAR(36) CHARACTER SET ascii NOT NULL,
  definition_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  result_json JSON NOT NULL,
  result_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  verdict VARCHAR(32) NOT NULL,
  production_eligible BOOLEAN NOT NULL,
  expected_revision BIGINT UNSIGNED NOT NULL,
  resulting_revision BIGINT UNSIGNED NOT NULL,
  operation_id CHAR(36) CHARACTER SET ascii NOT NULL,
  authority_grant CHAR(36) CHARACTER SET ascii NOT NULL,
  authority_revision BIGINT UNSIGNED NOT NULL,
  schema_version VARCHAR(32) NOT NULL,
  schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL,
  created_at DATETIME(6) NOT NULL,
  classification VARCHAR(24) NOT NULL,
  PRIMARY KEY (tenant_id,evaluation_id),
  UNIQUE KEY uq_evaluation_result_operation (tenant_id,operation_id),
  FOREIGN KEY (tenant_id,evaluation_id,definition_sha256) REFERENCES evaluation_definition (tenant_id,evaluation_id,content_sha256),
  FOREIGN KEY (tenant_id,operation_id) REFERENCES operation_record (tenant_id,operation_id),
  FOREIGN KEY (tenant_id,authority_grant) REFERENCES permission_grant (tenant_id,grant_id),
  FOREIGN KEY (tenant_id,created_by) REFERENCES tenant_member (tenant_id,user_id),
  CHECK (verdict IN ('PASS','FAIL','INSUFFICIENT_EVIDENCE')),
  CHECK (production_eligible=0),
  CHECK (expected_revision > 0 AND resulting_revision=expected_revision+1 AND authority_revision > 0),
  CHECK (JSON_TYPE(result_json)='OBJECT'
    AND JSON_UNQUOTE(JSON_EXTRACT(result_json,'$.verdict')) <=> verdict
    AND JSON_EXTRACT(result_json,'$.production_eligible')=FALSE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
