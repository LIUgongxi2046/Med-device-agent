--liquibase formatted sql

--changeset medical-agent:1.0.0-334-simulation-system-job-workflow dbms:mysql runInTransaction:false failOnError:true
-- The simulation producer is synchronous and does not invent a Temporal workflow.
-- Keep the historical NOT NULL requirement for every other job kind in the same ALTER.
ALTER TABLE system_job_run
  MODIFY COLUMN workflow_id VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NULL,
  ADD UNIQUE KEY uq_system_simulation_job (tenant_id,project_key,run_id,scope_manifest_id,job_type),
  ADD CONSTRAINT ck_system_simulation_workflow CHECK (
    (job_type='SIMULATION_EVALUATION' AND workflow_id IS NULL)
    OR (job_type<>'SIMULATION_EVALUATION' AND workflow_id IS NOT NULL));

--changeset medical-agent:1.0.0-335-simulation-scope-manifest dbms:mysql runInTransaction:false failOnError:true
-- Actual canonical scope content, registered as a version; never a fabricated ID-only record.
CREATE TABLE simulation_scope_manifest (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  project_id CHAR(36) CHARACTER SET ascii NULL,
  project_key VARCHAR(36) CHARACTER SET ascii GENERATED ALWAYS AS (COALESCE(project_id,'ENTERPRISE')) STORED,
  scope_manifest_id CHAR(36) CHARACTER SET ascii NOT NULL,
  manifest JSON NOT NULL, content_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
  PRIMARY KEY (tenant_id,scope_manifest_id),
  UNIQUE KEY uq_simulation_scope_manifest (tenant_id,project_key,scope_manifest_id),
  FOREIGN KEY (tenant_id,project_id) REFERENCES project (tenant_id,project_id),
  FOREIGN KEY (tenant_id,scope_manifest_id,content_sha256) REFERENCES version_registry (tenant_id,version_id,content_sha256),
  FOREIGN KEY (tenant_id,created_by) REFERENCES tenant_member (tenant_id,user_id),
  CHECK (JSON_TYPE(manifest)='OBJECT' AND content_sha256 REGEXP '^[a-f0-9]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-336-simulation-parent-account-key dbms:mysql runInTransaction:false failOnError:true
ALTER TABLE simulation_quota_account
  ADD UNIQUE KEY uq_simulation_job_parent_account (tenant_id,project_key,account_id,scope,parent_run_id),
  ADD UNIQUE KEY uq_simulation_job_account_kind (tenant_id,account_id,scope);

ALTER TABLE simulation_execution_authority
  ADD UNIQUE KEY uq_simulation_job_authority_binding (tenant_id,project_key,authority_id,run_id,step_id,action_id,principal_id,evaluation_id);

ALTER TABLE model_config_revision
  ADD UNIQUE KEY uq_model_simulation_job_mode (tenant_id,model_config_revision,execution_mode);

--changeset medical-agent:1.0.0-337-simulation-evaluation-job dbms:mysql runInTransaction:false failOnError:true
-- One immutable cell identity. Runtime updates may only CAS status/revision and append issued authority.
-- invocation_id is allocated here, NOT a claim that an invocation has executed or even been admitted.
-- No invocation FK: admission creates that row later using this exact allocated ID.
CREATE TABLE simulation_evaluation_job (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  project_id CHAR(36) CHARACTER SET ascii NULL,
  project_key VARCHAR(36) CHARACTER SET ascii GENERATED ALWAYS AS (COALESCE(project_id,'ENTERPRISE')) STORED,
  job_id CHAR(36) CHARACTER SET ascii NOT NULL,
  evaluation_id CHAR(36) CHARACTER SET ascii NOT NULL, definition_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  sample_id CHAR(36) CHARACTER SET ascii NOT NULL, agent_version_id CHAR(36) CHARACTER SET ascii NOT NULL,
  repeat_index INT UNSIGNED NOT NULL,
  run_id CHAR(36) CHARACTER SET ascii NOT NULL, principal_id CHAR(36) CHARACTER SET ascii NOT NULL,
  step_id CHAR(36) CHARACTER SET ascii NOT NULL, action_id CHAR(36) CHARACTER SET ascii NOT NULL,
  context_manifest_id CHAR(36) CHARACTER SET ascii NOT NULL, invocation_id CHAR(36) CHARACTER SET ascii NOT NULL,
  scope_manifest_id CHAR(36) CHARACTER SET ascii NOT NULL,
  role_kind VARCHAR(24) NOT NULL DEFAULT 'ROLE',
  role_version CHAR(36) CHARACTER SET ascii NOT NULL, model_config_revision CHAR(36) CHARACTER SET ascii NOT NULL,
  prompt_revision CHAR(36) CHARACTER SET ascii NOT NULL, case_id CHAR(36) CHARACTER SET ascii NOT NULL,
  enterprise_account_id CHAR(36) CHARACTER SET ascii NOT NULL, parent_account_id CHAR(36) CHARACTER SET ascii NOT NULL,
  parent_account_scope VARCHAR(24) NOT NULL DEFAULT 'PARENT_RUN',
  enterprise_account_scope VARCHAR(24) NOT NULL DEFAULT 'ENTERPRISE',
  retention_policy_id CHAR(36) CHARACTER SET ascii NOT NULL,
  execution_mode VARCHAR(16) NOT NULL DEFAULT 'SIMULATION',
  job_type VARCHAR(32) NOT NULL DEFAULT 'SIMULATION_EVALUATION',
  service_uri VARCHAR(512) NOT NULL,
  grant_id CHAR(36) CHARACTER SET ascii NOT NULL, grant_revision BIGINT UNSIGNED NOT NULL,
  start_request JSON NOT NULL, start_request_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  source_binding JSON NOT NULL, source_binding_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  prepared_payload JSON NOT NULL, prepared_payload_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  authority_id CHAR(36) CHARACTER SET ascii NULL, admission_json JSON NULL,
  status VARCHAR(24) NOT NULL, revision BIGINT UNSIGNED NOT NULL, updated_at DATETIME(6) NOT NULL,
  schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
  PRIMARY KEY (tenant_id,job_id),
  UNIQUE KEY uq_simulation_job_cell (tenant_id,evaluation_id,sample_id,agent_version_id,repeat_index),
  UNIQUE KEY uq_simulation_job_run (tenant_id,run_id),
  UNIQUE KEY uq_simulation_job_invocation (tenant_id,invocation_id),
  UNIQUE KEY uq_simulation_job_authority (tenant_id,authority_id),
  FOREIGN KEY (tenant_id,project_key,evaluation_id) REFERENCES evaluation_batch (tenant_id,project_key,evaluation_id),
  FOREIGN KEY (tenant_id,evaluation_id,definition_sha256) REFERENCES evaluation_definition (tenant_id,evaluation_id,content_sha256),
  FOREIGN KEY (tenant_id,agent_version_id) REFERENCES agent_version (tenant_id,agent_version_id),
  FOREIGN KEY (tenant_id,project_key,run_id,scope_manifest_id,job_type)
    REFERENCES system_job_run (tenant_id,project_key,run_id,scope_manifest_id,job_type),
  FOREIGN KEY (tenant_id,project_key,principal_id,run_id) REFERENCES run_principal (tenant_id,project_key,principal_id,run_id),
  FOREIGN KEY (tenant_id,project_key,run_id,step_id,action_id) REFERENCES step_action_binding (tenant_id,project_key,run_id,step_id,action_id),
  FOREIGN KEY (tenant_id,project_key,context_manifest_id,execution_mode) REFERENCES context_manifest (tenant_id,project_key,context_manifest_id,execution_mode),
  FOREIGN KEY (tenant_id,project_key,scope_manifest_id) REFERENCES simulation_scope_manifest (tenant_id,project_key,scope_manifest_id),
  FOREIGN KEY (tenant_id,role_version,role_kind) REFERENCES capability_version (tenant_id,capability_version,kind),
  FOREIGN KEY (tenant_id,prompt_revision) REFERENCES version_registry (tenant_id,version_id),
  FOREIGN KEY (tenant_id,model_config_revision,execution_mode) REFERENCES model_config_revision (tenant_id,model_config_revision,execution_mode),
  FOREIGN KEY (tenant_id,project_key,case_id,model_config_revision) REFERENCES simulation_fixture_registration (tenant_id,project_key,case_id,model_config_revision),
  FOREIGN KEY (tenant_id,enterprise_account_id,enterprise_account_scope) REFERENCES simulation_quota_account (tenant_id,account_id,scope),
  FOREIGN KEY (tenant_id,project_key,parent_account_id,parent_account_scope,run_id)
    REFERENCES simulation_quota_account (tenant_id,project_key,account_id,scope,parent_run_id),
  FOREIGN KEY (tenant_id,project_key,evaluation_id,retention_policy_id)
    REFERENCES simulation_retention_policy (tenant_id,project_key,evaluation_id,policy_id),
  FOREIGN KEY (tenant_id,grant_id) REFERENCES permission_grant (tenant_id,grant_id),
  FOREIGN KEY (tenant_id,project_key,authority_id,run_id,step_id,action_id,principal_id,evaluation_id)
    REFERENCES simulation_execution_authority (tenant_id,project_key,authority_id,run_id,step_id,action_id,principal_id,evaluation_id),
  FOREIGN KEY (tenant_id,created_by) REFERENCES tenant_member (tenant_id,user_id),
  CHECK (execution_mode='SIMULATION' AND job_type='SIMULATION_EVALUATION' AND parent_account_scope='PARENT_RUN' AND role_kind='ROLE' AND enterprise_account_scope='ENTERPRISE'),
  CHECK (repeat_index<100 AND grant_revision BETWEEN 1 AND 9007199254740991),
  CHECK (enterprise_account_id<>parent_account_id AND CHAR_LENGTH(service_uri)>0 AND updated_at>=created_at),
  CHECK ((status='READY' AND revision=1 AND authority_id IS NULL AND admission_json IS NULL)
    OR (status='CLAIMED' AND revision=2 AND authority_id IS NULL AND admission_json IS NULL)
    OR (status='ISSUED' AND revision=3 AND authority_id IS NOT NULL AND admission_json IS NOT NULL AND JSON_TYPE(admission_json)='OBJECT')),
  CHECK (JSON_TYPE(start_request)='OBJECT' AND JSON_TYPE(source_binding)='OBJECT' AND JSON_TYPE(prepared_payload)='OBJECT'),
  CHECK (definition_sha256 REGEXP '^[a-f0-9]{64}$' AND start_request_sha256 REGEXP '^[a-f0-9]{64}$'
    AND source_binding_sha256 REGEXP '^[a-f0-9]{64}$' AND prepared_payload_sha256 REGEXP '^[a-f0-9]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-338-simulation-context-dependency-manifest dbms:mysql runInTransaction:false failOnError:true
-- Frozen candidate/prompt graph bytes supplement the existing source-only access manifest.
CREATE TABLE simulation_context_dependency_manifest (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  project_id CHAR(36) CHARACTER SET ascii NULL,
  project_key VARCHAR(36) CHARACTER SET ascii GENERATED ALWAYS AS (COALESCE(project_id,'ENTERPRISE')) STORED,
  context_manifest_id CHAR(36) CHARACTER SET ascii NOT NULL,
  execution_mode VARCHAR(16) NOT NULL DEFAULT 'SIMULATION',
  candidate_id CHAR(36) CHARACTER SET ascii NOT NULL, prompt_revision CHAR(36) CHARACTER SET ascii NOT NULL,
  manifest_json JSON NOT NULL, content_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
  PRIMARY KEY (tenant_id,context_manifest_id),
  FOREIGN KEY (tenant_id,project_key,context_manifest_id,execution_mode)
    REFERENCES context_manifest (tenant_id,project_key,context_manifest_id,execution_mode),
  FOREIGN KEY (tenant_id,candidate_id) REFERENCES version_registry (tenant_id,version_id),
  FOREIGN KEY (tenant_id,prompt_revision) REFERENCES version_registry (tenant_id,version_id),
  FOREIGN KEY (tenant_id,created_by) REFERENCES tenant_member (tenant_id,user_id),
  CHECK (execution_mode='SIMULATION'),
  CHECK (JSON_TYPE(manifest_json)='OBJECT' AND content_sha256 REGEXP '^[a-f0-9]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
