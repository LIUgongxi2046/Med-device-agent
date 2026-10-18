--liquibase formatted sql

--changeset medical-agent:1.0.0-339-simulation-result-fact-keys dbms:mysql runInTransaction:false failOnError:true
ALTER TABLE simulation_invocation_fact
  ADD UNIQUE KEY uq_simulation_result_fact (tenant_id,project_key,invocation_id,fact_id),
  ADD UNIQUE KEY uq_simulation_result_fact_hash (tenant_id,project_key,invocation_id,fact_id,payload_sha256);

--changeset medical-agent:1.0.0-340-simulation-result-persistence dbms:mysql runInTransaction:false failOnError:true
-- Durable write command contains the two fixed artifact IDs, write intents and verified object references.
-- Planned IDs are not artifacts: real artifact FKs belong to the final settlement only.
CREATE TABLE simulation_result_persistence (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  project_id CHAR(36) CHARACTER SET ascii NULL,
  project_key VARCHAR(36) CHARACTER SET ascii GENERATED ALWAYS AS (COALESCE(project_id,'ENTERPRISE')) STORED,
  invocation_id CHAR(36) CHARACTER SET ascii NOT NULL,
  execution_mode VARCHAR(16) NOT NULL DEFAULT 'SIMULATION',
  fact_id CHAR(36) CHARACTER SET ascii NOT NULL,
  payload_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  binding_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  command_json JSON NOT NULL,
  status VARCHAR(24) NOT NULL, revision BIGINT UNSIGNED NOT NULL, updated_at DATETIME(6) NOT NULL,
  retention_policy_id CHAR(36) CHARACTER SET ascii NOT NULL, retention_deadline DATETIME(6) NOT NULL,
  schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
  PRIMARY KEY (tenant_id,invocation_id),
  FOREIGN KEY (tenant_id,project_key,invocation_id,execution_mode)
    REFERENCES invocation (tenant_id,project_key,invocation_id,execution_mode),
  FOREIGN KEY (tenant_id,project_key,invocation_id,fact_id,payload_sha256)
    REFERENCES simulation_invocation_fact (tenant_id,project_key,invocation_id,fact_id,payload_sha256),
  FOREIGN KEY (tenant_id,retention_policy_id) REFERENCES simulation_retention_policy (tenant_id,policy_id),
  FOREIGN KEY (tenant_id,created_by) REFERENCES tenant_member (tenant_id,user_id),
  CHECK (execution_mode='SIMULATION'),
  CHECK (JSON_TYPE(command_json)='OBJECT'),
  CHECK (status IN ('PREPARED','WRITING','VERIFIED','COMPLETED')),
  CHECK (revision BETWEEN 1 AND 9007199254740991 AND updated_at>=created_at AND retention_deadline>created_at),
  CHECK (payload_sha256 REGEXP '^[a-f0-9]{64}$' AND binding_sha256 REGEXP '^[a-f0-9]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-341-simulation-quota-settlement dbms:mysql runInTransaction:false failOnError:true
-- Immutable, nonfinancial receipt. Does not change canonical invocation settlement_status=PENDING.
-- Fixture receipt IDs can repeat across invocations; invocation identity is the accounting uniqueness key.
CREATE TABLE simulation_quota_settlement (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  project_id CHAR(36) CHARACTER SET ascii NULL,
  project_key VARCHAR(36) CHARACTER SET ascii GENERATED ALWAYS AS (COALESCE(project_id,'ENTERPRISE')) STORED,
  invocation_id CHAR(36) CHARACTER SET ascii NOT NULL,
  execution_mode VARCHAR(16) NOT NULL DEFAULT 'SIMULATION',
  fact_id CHAR(36) CHARACTER SET ascii NOT NULL, receipt_id CHAR(36) CHARACTER SET ascii NOT NULL,
  result_artifact_id CHAR(36) CHARACTER SET ascii NOT NULL, result_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  provenance_artifact_id CHAR(36) CHARACTER SET ascii NOT NULL, provenance_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  input_units BIGINT UNSIGNED NOT NULL, output_units BIGINT UNSIGNED NOT NULL,
  charged_units BIGINT UNSIGNED NOT NULL, charged_calls BIGINT UNSIGNED NOT NULL,
  counter JSON NOT NULL, counter_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  usage_origin VARCHAR(24) NOT NULL, fixture_usage JSON NULL,
  execution_status VARCHAR(24) NOT NULL, production_eligible BOOLEAN NOT NULL DEFAULT FALSE,
  schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
  PRIMARY KEY (tenant_id,invocation_id),
  FOREIGN KEY (tenant_id,project_key,invocation_id,execution_mode)
    REFERENCES invocation (tenant_id,project_key,invocation_id,execution_mode),
  FOREIGN KEY (tenant_id,project_key,invocation_id,fact_id)
    REFERENCES simulation_invocation_fact (tenant_id,project_key,invocation_id,fact_id),
  FOREIGN KEY (tenant_id,project_key,result_artifact_id,result_sha256)
    REFERENCES artifact (tenant_id,project_key,artifact_id,sha256),
  FOREIGN KEY (tenant_id,project_key,provenance_artifact_id,provenance_sha256)
    REFERENCES artifact (tenant_id,project_key,artifact_id,sha256),
  FOREIGN KEY (tenant_id,created_by) REFERENCES tenant_member (tenant_id,user_id),
  CHECK (execution_mode='SIMULATION' AND production_eligible=FALSE),
  CHECK (result_artifact_id<>provenance_artifact_id AND charged_calls=1),
  CHECK (input_units<=9007199254740991 AND output_units<=9007199254740991
    AND charged_units<=9007199254740991 AND charged_units=input_units+output_units),
  CHECK (execution_status IN ('SUCCEEDED','FAILED')),
  CHECK ((usage_origin='UNKNOWN' AND fixture_usage IS NULL)
    OR (usage_origin='SIMULATION_FIXTURE' AND fixture_usage IS NOT NULL AND JSON_TYPE(fixture_usage)='OBJECT')),
  CHECK (JSON_TYPE(counter)='OBJECT' AND counter_sha256 REGEXP '^[a-f0-9]{64}$'),
  CHECK (result_sha256 REGEXP '^[a-f0-9]{64}$' AND provenance_sha256 REGEXP '^[a-f0-9]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-342-simulation-job-terminal-state dbms:mysql runInTransaction:false failOnError:true
-- 061's fourth unnamed CHECK is assigned this deterministic name by MySQL.
-- Replace it atomically; do not rewrite 061 or weaken its other guards.
-- Cancellation is not a success transition: runtime requires a still-RUNNING parent and exact single action/step.
ALTER TABLE simulation_evaluation_job
  DROP CONSTRAINT simulation_evaluation_job_chk_4,
  ADD CONSTRAINT ck_simulation_job_result_state CHECK (
    (status='READY' AND revision=1 AND authority_id IS NULL AND admission_json IS NULL)
    OR (status='CLAIMED' AND revision=2 AND authority_id IS NULL AND admission_json IS NULL)
    OR (status='ISSUED' AND revision=3 AND authority_id IS NOT NULL AND admission_json IS NOT NULL AND JSON_TYPE(admission_json)='OBJECT')
    OR (status IN ('SUCCEEDED','FAILED') AND revision=4 AND authority_id IS NOT NULL AND admission_json IS NOT NULL AND JSON_TYPE(admission_json)='OBJECT'));
