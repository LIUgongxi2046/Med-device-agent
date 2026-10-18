--liquibase formatted sql

--changeset medical-agent:1.0.0-320-context-execution-mode dbms:mysql runInTransaction:false failOnError:true
ALTER TABLE context_manifest
  ADD COLUMN execution_mode VARCHAR(16) NOT NULL DEFAULT 'EXTERNAL',
  MODIFY COLUMN tokenizer_revision VARCHAR(128) NULL,
  MODIFY COLUMN token_count BIGINT UNSIGNED NULL,
  ADD UNIQUE KEY uq_context_execution_mode (tenant_id,project_key,context_manifest_id,execution_mode),
  ADD CONSTRAINT ck_context_execution_mode CHECK (
    (execution_mode='EXTERNAL' AND tokenizer_revision IS NOT NULL AND token_count IS NOT NULL)
    OR (execution_mode='SIMULATION' AND tokenizer_revision IS NULL AND token_count IS NULL));

--changeset medical-agent:1.0.0-321-model-invocation-mode-key dbms:mysql runInTransaction:false failOnError:true
ALTER TABLE model_config_revision
  ADD UNIQUE KEY uq_model_invocation_mode (tenant_id,config_id,model_config_revision,execution_mode);

--changeset medical-agent:1.0.0-322-invocation-execution-mode dbms:mysql runInTransaction:false failOnError:true
-- Existing action, context, successful-result artifact and settlement receipt constraints remain intact.
ALTER TABLE invocation
  ADD COLUMN execution_mode VARCHAR(16) NOT NULL DEFAULT 'EXTERNAL',
  MODIFY COLUMN credential_reference_id CHAR(36) CHARACTER SET ascii NULL,
  MODIFY COLUMN credential_revision VARCHAR(128) NULL,
  MODIFY COLUMN verification_id CHAR(36) CHARACTER SET ascii NULL,
  MODIFY COLUMN price_version CHAR(36) CHARACTER SET ascii NULL,
  MODIFY COLUMN currency CHAR(3) CHARACTER SET ascii COLLATE ascii_bin NULL,
  ADD UNIQUE KEY uq_invocation_execution_mode (tenant_id,project_key,invocation_id,execution_mode),
  ADD CONSTRAINT fk_invocation_model_mode FOREIGN KEY (tenant_id,config_id,config_revision,execution_mode)
    REFERENCES model_config_revision (tenant_id,config_id,model_config_revision,execution_mode),
  ADD CONSTRAINT fk_invocation_context_mode FOREIGN KEY (tenant_id,project_key,context_manifest_id,execution_mode)
    REFERENCES context_manifest (tenant_id,project_key,context_manifest_id,execution_mode),
  ADD CONSTRAINT ck_invocation_execution_mode CHECK (
    (execution_mode='EXTERNAL' AND credential_reference_id IS NOT NULL AND credential_revision IS NOT NULL
      AND verification_id IS NOT NULL AND price_version IS NOT NULL AND currency IS NOT NULL)
    OR (execution_mode='SIMULATION' AND credential_reference_id IS NULL AND credential_revision IS NULL
      AND verification_id IS NULL AND price_version IS NULL AND currency IS NULL
      AND provider_request_id IS NULL AND usage_receipt_id IS NULL AND settled_amount IS NULL AND total_tokens IS NULL
      AND settlement_status='PENDING' AND actual_send_manifest IS NULL AND actual_send_sha256 IS NULL));

--changeset medical-agent:1.0.0-323-simulation-quota-account dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE simulation_quota_account (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  project_id CHAR(36) CHARACTER SET ascii NULL,
  project_key VARCHAR(36) CHARACTER SET ascii GENERATED ALWAYS AS (COALESCE(project_id,'ENTERPRISE')) STORED,
  account_id CHAR(36) CHARACTER SET ascii NOT NULL,
  scope VARCHAR(24) NOT NULL, parent_run_id CHAR(36) CHARACTER SET ascii NULL,
  parent_run_key VARCHAR(36) CHARACTER SET ascii GENERATED ALWAYS AS (COALESCE(parent_run_id,'ENTERPRISE')) STORED,
  max_units BIGINT UNSIGNED NOT NULL, max_calls BIGINT UNSIGNED NOT NULL,
  spent_units BIGINT UNSIGNED NOT NULL, reserved_units BIGINT UNSIGNED NOT NULL,
  spent_calls BIGINT UNSIGNED NOT NULL, reserved_calls BIGINT UNSIGNED NOT NULL,
  counter JSON NOT NULL, counter_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  approval_grant_id CHAR(36) CHARACTER SET ascii NOT NULL, approval_grant_revision BIGINT UNSIGNED NOT NULL,
  status VARCHAR(24) NOT NULL, revision BIGINT UNSIGNED NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL,
  classification VARCHAR(24) NOT NULL,
  PRIMARY KEY (tenant_id,account_id),
  UNIQUE KEY uq_simulation_quota_scope (tenant_id,project_key,account_id),
  UNIQUE KEY uq_simulation_quota_parent (tenant_id,project_key,scope,parent_run_key),
  FOREIGN KEY (tenant_id) REFERENCES tenant (tenant_id),
  FOREIGN KEY (tenant_id,project_id) REFERENCES project (tenant_id,project_id),
  FOREIGN KEY (tenant_id,project_key,parent_run_id) REFERENCES execution_registry (tenant_id,project_key,run_id),
  FOREIGN KEY (tenant_id,approval_grant_id) REFERENCES permission_grant (tenant_id,grant_id),
  CHECK ((scope='ENTERPRISE' AND project_id IS NULL AND parent_run_id IS NULL)
    OR (scope='PARENT_RUN' AND parent_run_id IS NOT NULL)),
  CHECK (max_units BETWEEN 1 AND 9007199254740991 AND max_calls BETWEEN 1 AND 9007199254740991 AND revision>0 AND approval_grant_revision>0),
  CHECK (spent_units<=max_units AND reserved_units<=max_units-spent_units
    AND spent_calls<=max_calls AND reserved_calls<=max_calls-spent_calls),
  CHECK (status IN ('ACTIVE','DISABLED')),
  CHECK (JSON_TYPE(counter)='OBJECT' AND counter_sha256 REGEXP '^[a-f0-9]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-324-simulation-execution-authority dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE simulation_execution_authority (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  project_id CHAR(36) CHARACTER SET ascii NULL,
  project_key VARCHAR(36) CHARACTER SET ascii GENERATED ALWAYS AS (COALESCE(project_id,'ENTERPRISE')) STORED,
  authority_id CHAR(36) CHARACTER SET ascii NOT NULL, authority_revision BIGINT UNSIGNED NOT NULL,
  run_id CHAR(36) CHARACTER SET ascii NOT NULL, step_id CHAR(36) CHARACTER SET ascii NOT NULL,
  action_id CHAR(36) CHARACTER SET ascii NOT NULL,
  evaluation_id CHAR(36) CHARACTER SET ascii NOT NULL, evaluation_run_id CHAR(36) CHARACTER SET ascii NOT NULL,
  principal_id CHAR(36) CHARACTER SET ascii NOT NULL,
  grant_id CHAR(36) CHARACTER SET ascii NOT NULL, grant_revision BIGINT UNSIGNED NOT NULL,
  service_uri VARCHAR(512) NOT NULL, lease_generation BIGINT UNSIGNED NOT NULL,
  proof JSON NOT NULL, proof_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  status VARCHAR(24) NOT NULL, expires_at DATETIME(6) NOT NULL,
  schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL,
  classification VARCHAR(24) NOT NULL,
  PRIMARY KEY (tenant_id,authority_id),
  UNIQUE KEY uq_simulation_authority_scope (tenant_id,project_key,authority_id),
  UNIQUE KEY uq_simulation_authority_action (tenant_id,run_id,step_id,action_id,authority_revision),
  FOREIGN KEY (tenant_id,project_key,run_id,step_id,action_id)
    REFERENCES step_action_binding (tenant_id,project_key,run_id,step_id,action_id),
  FOREIGN KEY (tenant_id,evaluation_id) REFERENCES evaluation_batch (tenant_id,evaluation_id),
  FOREIGN KEY (tenant_id,project_key,evaluation_run_id) REFERENCES execution_registry (tenant_id,project_key,run_id),
  FOREIGN KEY (tenant_id,project_key,principal_id,run_id) REFERENCES run_principal (tenant_id,project_key,principal_id,run_id),
  FOREIGN KEY (tenant_id,grant_id) REFERENCES permission_grant (tenant_id,grant_id),
  CHECK (authority_revision>0 AND grant_revision>0 AND lease_generation>0),
  CHECK (status IN ('ACTIVE','REVOKED')),
  CHECK (expires_at>created_at AND CHAR_LENGTH(service_uri)>0),
  CHECK (JSON_TYPE(proof)='OBJECT' AND proof_sha256 REGEXP '^[a-f0-9]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-325-simulation-invocation-provenance dbms:mysql runInTransaction:false failOnError:true
-- Admitted immutable copies only: these JSON values do not establish source/fixture authorization.
CREATE TABLE simulation_invocation_provenance (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  project_id CHAR(36) CHARACTER SET ascii NULL,
  project_key VARCHAR(36) CHARACTER SET ascii GENERATED ALWAYS AS (COALESCE(project_id,'ENTERPRISE')) STORED,
  invocation_id CHAR(36) CHARACTER SET ascii NOT NULL,
  execution_mode VARCHAR(16) NOT NULL DEFAULT 'SIMULATION',
  authority_id CHAR(36) CHARACTER SET ascii NOT NULL,
  binding JSON NOT NULL, settings JSON NOT NULL, exact_input JSON NOT NULL, request_parameters JSON NOT NULL, simulation_case JSON NOT NULL,
  binding_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  retention_deadline DATETIME(6) NOT NULL,
  schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL,
  classification VARCHAR(24) NOT NULL,
  PRIMARY KEY (tenant_id,invocation_id),
  FOREIGN KEY (tenant_id,project_key,invocation_id,execution_mode)
    REFERENCES invocation (tenant_id,project_key,invocation_id,execution_mode),
  FOREIGN KEY (tenant_id,project_key,authority_id)
    REFERENCES simulation_execution_authority (tenant_id,project_key,authority_id),
  CHECK (execution_mode='SIMULATION'),
  CHECK (JSON_TYPE(binding)='OBJECT' AND JSON_TYPE(settings)='OBJECT' AND JSON_TYPE(exact_input)='OBJECT'
    AND JSON_TYPE(request_parameters)='OBJECT' AND JSON_TYPE(simulation_case)='OBJECT'),
  CHECK (binding_sha256 REGEXP '^[a-f0-9]{64}$' AND retention_deadline>created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-326-simulation-quota-reservation dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE simulation_quota_reservation (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  project_id CHAR(36) CHARACTER SET ascii NULL,
  project_key VARCHAR(36) CHARACTER SET ascii GENERATED ALWAYS AS (COALESCE(project_id,'ENTERPRISE')) STORED,
  invocation_id CHAR(36) CHARACTER SET ascii NOT NULL, account_id CHAR(36) CHARACTER SET ascii NOT NULL,
  execution_mode VARCHAR(16) NOT NULL DEFAULT 'SIMULATION',
  reserved_units BIGINT UNSIGNED NOT NULL, reserved_calls BIGINT UNSIGNED NOT NULL,
  status VARCHAR(24) NOT NULL, charged_units BIGINT UNSIGNED NULL,
  revision BIGINT UNSIGNED NOT NULL, updated_at DATETIME(6) NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL,
  classification VARCHAR(24) NOT NULL,
  PRIMARY KEY (tenant_id,invocation_id,account_id),
  FOREIGN KEY (tenant_id,project_key,invocation_id,execution_mode)
    REFERENCES invocation (tenant_id,project_key,invocation_id,execution_mode),
  FOREIGN KEY (tenant_id,account_id) REFERENCES simulation_quota_account (tenant_id,account_id),
  CHECK (execution_mode='SIMULATION' AND reserved_calls=1 AND reserved_units>0 AND revision>0),
  CHECK ((status='HELD' AND charged_units IS NULL)
    OR (status='CONSUMED' AND charged_units IS NOT NULL AND charged_units<=reserved_units)
    OR (status='RELEASED' AND charged_units IS NOT NULL AND charged_units=0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-327-simulation-invocation-fact dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE simulation_invocation_fact (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  project_id CHAR(36) CHARACTER SET ascii NULL,
  project_key VARCHAR(36) CHARACTER SET ascii GENERATED ALWAYS AS (COALESCE(project_id,'ENTERPRISE')) STORED,
  fact_id CHAR(36) CHARACTER SET ascii NOT NULL, invocation_id CHAR(36) CHARACTER SET ascii NOT NULL,
  execution_mode VARCHAR(16) NOT NULL DEFAULT 'SIMULATION',
  kind VARCHAR(32) NOT NULL, payload JSON NOT NULL, payload_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  received_at DATETIME(6) NOT NULL,
  schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL,
  classification VARCHAR(24) NOT NULL,
  PRIMARY KEY (tenant_id,fact_id),
  UNIQUE KEY uq_simulation_fact_content (tenant_id,invocation_id,kind,payload_sha256),
  FOREIGN KEY (tenant_id,project_key,invocation_id,execution_mode)
    REFERENCES invocation (tenant_id,project_key,invocation_id,execution_mode),
  CHECK (execution_mode='SIMULATION'),
  CHECK (kind IN ('RESULT','FAILURE','CANCELLATION','LATE_RESULT','LATE_FAILURE')),
  CHECK (JSON_TYPE(payload)='OBJECT' AND payload_sha256 REGEXP '^[a-f0-9]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-328-simulation-fixture-registration dbms:mysql runInTransaction:false failOnError:true
-- No fixture is seeded. A registration producer must verify real source bytes and current rights first.
CREATE TABLE simulation_fixture_registration (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  project_id CHAR(36) CHARACTER SET ascii NULL,
  project_key VARCHAR(36) CHARACTER SET ascii GENERATED ALWAYS AS (COALESCE(project_id,'ENTERPRISE')) STORED,
  case_id CHAR(36) CHARACTER SET ascii NOT NULL,
  model_config_revision CHAR(36) CHARACTER SET ascii NOT NULL,
  case_source_version CHAR(36) CHARACTER SET ascii NOT NULL, case_rights_revision CHAR(36) CHARACTER SET ascii NOT NULL,
  input_source_version CHAR(36) CHARACTER SET ascii NOT NULL, input_rights_revision CHAR(36) CHARACTER SET ascii NOT NULL,
  case_sha256 CHAR(64) CHARACTER SET ascii NOT NULL, input_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  settings_sha256 CHAR(64) CHARACTER SET ascii NOT NULL, parameters_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  case_receipt_id CHAR(36) CHARACTER SET ascii NOT NULL,
  schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
  PRIMARY KEY (tenant_id,case_receipt_id),
  UNIQUE KEY uq_simulation_fixture_case (tenant_id,project_key,case_id),
  UNIQUE KEY uq_simulation_fixture_config (tenant_id,project_key,case_id,model_config_revision),
  FOREIGN KEY (tenant_id,project_id) REFERENCES project (tenant_id,project_id),
  FOREIGN KEY (tenant_id,model_config_revision) REFERENCES model_config_revision (tenant_id,model_config_revision),
  FOREIGN KEY (tenant_id,case_source_version,case_rights_revision) REFERENCES rights_revision (tenant_id,source_version,rights_revision),
  FOREIGN KEY (tenant_id,input_source_version,input_rights_revision) REFERENCES rights_revision (tenant_id,source_version,rights_revision),
  CHECK (case_sha256 REGEXP '^[a-f0-9]{64}$' AND input_sha256 REGEXP '^[a-f0-9]{64}$'
    AND settings_sha256 REGEXP '^[a-f0-9]{64}$' AND parameters_sha256 REGEXP '^[a-f0-9]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-329-simulation-evaluation-scope dbms:mysql runInTransaction:false failOnError:true
ALTER TABLE evaluation_batch
  ADD UNIQUE KEY uq_evaluation_simulation_scope (tenant_id,project_key,evaluation_id);

--changeset medical-agent:1.0.0-330-simulation-retention-policy dbms:mysql runInTransaction:false failOnError:true
-- Explicit, separately authorized simulation retention. Never reuse SOURCE retention or seed approval.
CREATE TABLE simulation_retention_policy (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  project_id CHAR(36) CHARACTER SET ascii NULL,
  project_key VARCHAR(36) CHARACTER SET ascii GENERATED ALWAYS AS (COALESCE(project_id,'ENTERPRISE')) STORED,
  policy_id CHAR(36) CHARACTER SET ascii NOT NULL, evaluation_id CHAR(36) CHARACTER SET ascii NOT NULL,
  status VARCHAR(24) NOT NULL, retain_until DATETIME(6) NOT NULL,
  maximum_record_bytes BIGINT UNSIGNED NOT NULL,
  approval_grant_id CHAR(36) CHARACTER SET ascii NOT NULL, approval_grant_revision BIGINT UNSIGNED NOT NULL,
  revision BIGINT UNSIGNED NOT NULL,
  schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL,
  classification VARCHAR(24) NOT NULL,
  PRIMARY KEY (tenant_id,policy_id),
  UNIQUE KEY uq_simulation_retention_scope (tenant_id,project_key,evaluation_id,policy_id),
  FOREIGN KEY (tenant_id,project_key,evaluation_id) REFERENCES evaluation_batch (tenant_id,project_key,evaluation_id),
  FOREIGN KEY (tenant_id,approval_grant_id) REFERENCES permission_grant (tenant_id,grant_id),
  FOREIGN KEY (tenant_id,created_by) REFERENCES tenant_member (tenant_id,user_id),
  CHECK (status IN ('ACTIVE','REVOKED')),
  CHECK (retain_until>created_at AND maximum_record_bytes BETWEEN 1 AND 9007199254740991),
  CHECK (approval_grant_revision BETWEEN 1 AND 9007199254740991 AND revision BETWEEN 1 AND 9007199254740991)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-331-simulation-authority-retention dbms:mysql runInTransaction:false failOnError:true
ALTER TABLE simulation_execution_authority
  ADD COLUMN retention_policy_id CHAR(36) CHARACTER SET ascii NOT NULL,
  ADD CONSTRAINT fk_simulation_authority_retention FOREIGN KEY (tenant_id,project_key,evaluation_id,retention_policy_id)
    REFERENCES simulation_retention_policy (tenant_id,project_key,evaluation_id,policy_id);

--changeset medical-agent:1.0.0-332-simulation-token-authority-key dbms:mysql runInTransaction:false failOnError:true
-- This key authenticates the existing token row; it does not mint a token or bypass production role checks.
ALTER TABLE principal_token
  ADD UNIQUE KEY uq_token_simulation_authority (tenant_id,project_key,principal_id,run_id,step_id,jti,service_uri,lease_generation);

--changeset medical-agent:1.0.0-333-simulation-authority-token dbms:mysql runInTransaction:false failOnError:true
-- Dedicated simulation audience/policy validation remains mandatory in the admission verifier.
ALTER TABLE simulation_execution_authority
  ADD COLUMN token_jti CHAR(36) CHARACTER SET ascii NOT NULL,
  ADD CONSTRAINT fk_simulation_authority_token
    FOREIGN KEY (tenant_id,project_key,principal_id,run_id,step_id,token_jti,service_uri,lease_generation)
    REFERENCES principal_token (tenant_id,project_key,principal_id,run_id,step_id,jti,service_uri,lease_generation);
