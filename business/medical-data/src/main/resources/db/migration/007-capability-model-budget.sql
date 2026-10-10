--liquibase formatted sql

--changeset medical-agent:1.0.0-093-configuration-head dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE configuration_head (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NULL,
  project_key VARCHAR(36) CHARACTER SET ascii GENERATED ALWAYS AS (COALESCE(project_id, 'ENTERPRISE')) STORED,
  config_id CHAR(36) CHARACTER SET ascii NOT NULL, kind VARCHAR(16) NOT NULL, name VARCHAR(100) NULL,
  current_version CHAR(36) CHARACTER SET ascii NULL, status VARCHAR(24) NOT NULL,
  current_agent_version CHAR(36) CHARACTER SET ascii GENERATED ALWAYS AS (CASE WHEN kind = 'AGENT' THEN current_version ELSE NULL END) STORED,
  current_capability_version CHAR(36) CHARACTER SET ascii GENERATED ALWAYS AS (CASE WHEN kind = 'CAPABILITY' THEN current_version ELSE NULL END) STORED,
  current_model_revision CHAR(36) CHARACTER SET ascii GENERATED ALWAYS AS (CASE WHEN kind = 'MODEL' THEN current_version ELSE NULL END) STORED,
  current_connector_revision CHAR(36) CHARACTER SET ascii GENERATED ALWAYS AS (CASE WHEN kind = 'CONNECTOR' THEN current_version ELSE NULL END) STORED,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
  revision BIGINT UNSIGNED NOT NULL, updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (tenant_id, config_id),
  UNIQUE KEY uq_configuration_kind (tenant_id, config_id, kind),
  UNIQUE KEY uq_configuration_scope (tenant_id, project_key, config_id, kind),
  UNIQUE KEY uq_configuration_name (tenant_id, kind, name),
  KEY ix_configuration_status (tenant_id, kind, status, updated_at),
  FOREIGN KEY (tenant_id) REFERENCES tenant (tenant_id),
  FOREIGN KEY (tenant_id, project_id) REFERENCES project (tenant_id, project_id),
  FOREIGN KEY (tenant_id, current_version) REFERENCES version_registry (tenant_id, version_id),
  CHECK (kind IN ('AGENT','CAPABILITY','MODEL','CONNECTOR')),
  CHECK (kind <> 'AGENT' OR (name IS NOT NULL AND CHAR_LENGTH(name) > 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-094-capability-version dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE capability_version (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NULL,
  project_key VARCHAR(36) CHARACTER SET ascii GENERATED ALWAYS AS (COALESCE(project_id, 'ENTERPRISE')) STORED,
  capability_version CHAR(36) CHARACTER SET ascii NOT NULL, config_id CHAR(36) CHARACTER SET ascii NOT NULL,
  configuration_kind VARCHAR(16) NOT NULL, kind VARCHAR(24) NOT NULL,
  input_schema_ref JSON NOT NULL, output_schema_ref JSON NOT NULL, tool_scope JSON NOT NULL,
  artifact_digest CHAR(64) CHARACTER SET ascii NOT NULL, content_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  status VARCHAR(24) NOT NULL,
  schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
  revision BIGINT UNSIGNED NOT NULL, updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (tenant_id, capability_version),
  UNIQUE KEY uq_capability_owner (tenant_id, config_id, capability_version),
  UNIQUE KEY uq_capability_kind (tenant_id, capability_version, kind),
  FOREIGN KEY (tenant_id, project_key, config_id, configuration_kind) REFERENCES configuration_head (tenant_id, project_key, config_id, kind),
  FOREIGN KEY (tenant_id, capability_version) REFERENCES version_registry (tenant_id, version_id),
  CHECK (configuration_kind = 'CAPABILITY'),
  CHECK (kind IN ('ROLE','SKILL','TOOL','PROMPT','SCENARIO')),
  CHECK (status IN ('DRAFT','VALIDATING','EVALUATING','REVIEW_PENDING','PUBLISHED','DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-095-model-config dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE model_config_revision (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NULL,
  project_key VARCHAR(36) CHARACTER SET ascii GENERATED ALWAYS AS (COALESCE(project_id, 'ENTERPRISE')) STORED,
  model_config_revision CHAR(36) CHARACTER SET ascii NOT NULL, config_id CHAR(36) CHARACTER SET ascii NOT NULL,
  configuration_kind VARCHAR(16) NOT NULL,
  provider VARCHAR(128) NOT NULL, base_url VARCHAR(2048) NOT NULL, region VARCHAR(128) NOT NULL,
  model_id VARCHAR(256) NOT NULL, model_version VARCHAR(256) NOT NULL,
  auth_ref VARCHAR(512) NOT NULL, credential_reference_id CHAR(36) CHARACTER SET ascii NOT NULL,
  credential_revision VARCHAR(128) NOT NULL,
  limits JSON NOT NULL, price_version CHAR(36) CHARACTER SET ascii NOT NULL,
  currency CHAR(3) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  data_policy JSON NOT NULL, request_parameter_schema_ref VARCHAR(512) NOT NULL,
  model_protocol_revision VARCHAR(64) NOT NULL, content_sha256 CHAR(64) CHARACTER SET ascii NOT NULL, status VARCHAR(24) NOT NULL,
  schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
  revision BIGINT UNSIGNED NOT NULL, updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (tenant_id, model_config_revision),
  UNIQUE KEY uq_model_owner (tenant_id, config_id, model_config_revision),
  UNIQUE KEY uq_model_credential (tenant_id, config_id, model_config_revision, credential_reference_id, credential_revision),
  UNIQUE KEY uq_model_price (tenant_id, model_config_revision, price_version, currency),
  FOREIGN KEY (tenant_id, project_key, config_id, configuration_kind) REFERENCES configuration_head (tenant_id, project_key, config_id, kind),
  FOREIGN KEY (tenant_id, model_config_revision) REFERENCES version_registry (tenant_id, version_id),
  FOREIGN KEY (tenant_id, price_version) REFERENCES version_registry (tenant_id, version_id),
  CHECK (configuration_kind = 'MODEL'),
  CHECK (status IN ('DRAFT','VALIDATING','EVALUATING','REVIEW_PENDING','PUBLISHED','DISABLED')),
  CHECK (currency REGEXP '^[A-Z]{3}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-096-agent-version dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE agent_version (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NULL,
  project_key VARCHAR(36) CHARACTER SET ascii GENERATED ALWAYS AS (COALESCE(project_id, 'ENTERPRISE')) STORED,
  agent_version_id CHAR(36) CHARACTER SET ascii NOT NULL, agent_id CHAR(36) CHARACTER SET ascii NOT NULL,
  configuration_kind VARCHAR(16) NOT NULL, name VARCHAR(100) NOT NULL, status VARCHAR(24) NOT NULL,
  dependency_manifest JSON NOT NULL, limits JSON NOT NULL, evaluation_id CHAR(36) CHARACTER SET ascii NULL,
  content_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
  revision BIGINT UNSIGNED NOT NULL, updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (tenant_id, agent_version_id),
  UNIQUE KEY uq_agent_version_owner (tenant_id, agent_id, agent_version_id),
  FOREIGN KEY (tenant_id, project_key, agent_id, configuration_kind) REFERENCES configuration_head (tenant_id, project_key, config_id, kind),
  FOREIGN KEY (tenant_id, agent_version_id) REFERENCES version_registry (tenant_id, version_id),
  FOREIGN KEY (tenant_id, evaluation_id) REFERENCES version_registry (tenant_id, version_id),
  CHECK (configuration_kind = 'AGENT'),
  CHECK (status IN ('DRAFT','VALIDATING','EVALUATING','REVIEW_PENDING','PUBLISHED','DISABLED')),
  CHECK (status NOT IN ('REVIEW_PENDING','PUBLISHED') OR evaluation_id IS NOT NULL),
  CHECK (CHAR_LENGTH(name) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-097-agent-release dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE release_record (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NULL,
  project_key VARCHAR(36) CHARACTER SET ascii GENERATED ALWAYS AS (COALESCE(project_id, 'ENTERPRISE')) STORED,
  release_id CHAR(36) CHARACTER SET ascii NOT NULL, agent_version_id CHAR(36) CHARACTER SET ascii NOT NULL,
  manifest_artifact_id CHAR(36) CHARACTER SET ascii NOT NULL, manifest JSON NOT NULL,
  manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL, content_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  approved_by CHAR(36) CHARACTER SET ascii NOT NULL, released_at DATETIME(6) NOT NULL,
  schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
  PRIMARY KEY (tenant_id, release_id),
  FOREIGN KEY (tenant_id) REFERENCES tenant (tenant_id),
  FOREIGN KEY (tenant_id, project_id) REFERENCES project (tenant_id, project_id),
  FOREIGN KEY (tenant_id, agent_version_id) REFERENCES agent_version (tenant_id, agent_version_id),
  FOREIGN KEY (tenant_id, project_key, manifest_artifact_id, manifest_sha256) REFERENCES artifact (tenant_id, project_key, artifact_id, sha256),
  FOREIGN KEY (tenant_id, approved_by) REFERENCES tenant_member (tenant_id, user_id),
  FOREIGN KEY (tenant_id, release_id) REFERENCES version_registry (tenant_id, version_id),
  CHECK (content_sha256 = manifest_sha256)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-098-release-scope dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE release_scope (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NULL,
  project_key VARCHAR(36) CHARACTER SET ascii GENERATED ALWAYS AS (COALESCE(project_id, 'ENTERPRISE')) STORED,
  scope_revision CHAR(36) CHARACTER SET ascii NOT NULL, release_id CHAR(36) CHARACTER SET ascii NOT NULL,
  audience_manifest JSON NOT NULL, status VARCHAR(24) NOT NULL,
  content_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
  revision BIGINT UNSIGNED NOT NULL, updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (tenant_id, scope_revision),
  UNIQUE KEY uq_release_scope_revision (tenant_id, project_key, release_id, scope_revision),
  KEY ix_release_scope_status (tenant_id, project_key, release_id, status),
  FOREIGN KEY (tenant_id, project_id) REFERENCES project (tenant_id, project_id),
  FOREIGN KEY (tenant_id, release_id) REFERENCES release_record (tenant_id, release_id),
  FOREIGN KEY (tenant_id, scope_revision) REFERENCES version_registry (tenant_id, version_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-099-connection-verification dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE connection_verification (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NULL,
  project_key VARCHAR(36) CHARACTER SET ascii GENERATED ALWAYS AS (COALESCE(project_id, 'ENTERPRISE')) STORED,
  verification_id CHAR(36) CHARACTER SET ascii NOT NULL, config_id CHAR(36) CHARACTER SET ascii NOT NULL,
  configuration_kind VARCHAR(16) NOT NULL, config_revision CHAR(36) CHARACTER SET ascii NOT NULL,
  credential_reference_id CHAR(36) CHARACTER SET ascii NOT NULL, credential_revision VARCHAR(128) NOT NULL,
  status VARCHAR(24) NOT NULL, receipt_ref JSON NULL,
  receipt_artifact_id CHAR(36) CHARACTER SET ascii NULL, endpoint_identity VARCHAR(2048) NULL,
  verified_at DATETIME(6) NULL, error_code VARCHAR(64) NULL,
  schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
  revision BIGINT UNSIGNED NOT NULL, updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (tenant_id, verification_id),
  UNIQUE KEY uq_connection_config_credential (tenant_id, verification_id, config_id, config_revision, credential_reference_id, credential_revision),
  KEY ix_connection_status (tenant_id, config_id, status, verified_at),
  FOREIGN KEY (tenant_id, project_key, config_id, configuration_kind) REFERENCES configuration_head (tenant_id, project_key, config_id, kind),
  FOREIGN KEY (tenant_id, config_revision) REFERENCES version_registry (tenant_id, version_id),
  FOREIGN KEY (tenant_id, project_key, receipt_artifact_id) REFERENCES artifact (tenant_id, project_key, artifact_id),
  CHECK (status IN ('PENDING','RUNNING','PASSED','FAILED','INVALIDATED')),
  CHECK (status <> 'PASSED' OR (receipt_ref IS NOT NULL AND receipt_artifact_id IS NOT NULL AND endpoint_identity IS NOT NULL AND verified_at IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-100-budget-account dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE budget_account (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NULL,
  project_key VARCHAR(36) CHARACTER SET ascii GENERATED ALWAYS AS (COALESCE(project_id, 'ENTERPRISE')) STORED,
  budget_id CHAR(36) CHARACTER SET ascii NOT NULL, current_budget_revision CHAR(36) CHARACTER SET ascii NULL,
  currency CHAR(3) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  spent DECIMAL(24,8) NOT NULL, reserved DECIMAL(24,8) NOT NULL,
  spent_tokens BIGINT UNSIGNED NOT NULL, reserved_tokens BIGINT UNSIGNED NOT NULL,
  spent_calls BIGINT UNSIGNED NOT NULL, reserved_calls BIGINT UNSIGNED NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
  revision BIGINT UNSIGNED NOT NULL, updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (tenant_id, budget_id),
  UNIQUE KEY uq_budget_scope (tenant_id, project_key, budget_id),
  UNIQUE KEY uq_budget_currency (tenant_id, budget_id, currency),
  FOREIGN KEY (tenant_id) REFERENCES tenant (tenant_id),
  FOREIGN KEY (tenant_id, project_id) REFERENCES project (tenant_id, project_id),
  CHECK (spent >= 0 AND reserved >= 0),
  CHECK (currency REGEXP '^[A-Z]{3}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-101-budget-revision dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE budget_revision (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NULL,
  project_key VARCHAR(36) CHARACTER SET ascii GENERATED ALWAYS AS (COALESCE(project_id, 'ENTERPRISE')) STORED,
  budget_revision CHAR(36) CHARACTER SET ascii NOT NULL, budget_id CHAR(36) CHARACTER SET ascii NOT NULL,
  currency CHAR(3) CHARACTER SET ascii COLLATE ascii_bin NOT NULL, limits JSON NOT NULL,
  approver_id CHAR(36) CHARACTER SET ascii NOT NULL, reason TEXT NOT NULL,
  content_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
  PRIMARY KEY (tenant_id, budget_revision),
  UNIQUE KEY uq_budget_revision_owner (tenant_id, budget_id, budget_revision),
  UNIQUE KEY uq_budget_revision_project (tenant_id, project_id, budget_revision),
  UNIQUE KEY uq_budget_revision_scope (tenant_id, project_key, budget_revision),
  UNIQUE KEY uq_budget_revision_currency (tenant_id, budget_id, budget_revision, currency),
  FOREIGN KEY (tenant_id, project_key, budget_id) REFERENCES budget_account (tenant_id, project_key, budget_id),
  FOREIGN KEY (tenant_id, budget_id, currency) REFERENCES budget_account (tenant_id, budget_id, currency),
  FOREIGN KEY (tenant_id, approver_id) REFERENCES tenant_member (tenant_id, user_id),
  FOREIGN KEY (tenant_id, budget_revision) REFERENCES version_registry (tenant_id, version_id),
  CHECK (CHAR_LENGTH(reason) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-102-model-invocation dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE invocation (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NULL,
  project_key VARCHAR(36) CHARACTER SET ascii GENERATED ALWAYS AS (COALESCE(project_id, 'ENTERPRISE')) STORED,
  invocation_id CHAR(36) CHARACTER SET ascii NOT NULL,
  run_id CHAR(36) CHARACTER SET ascii NOT NULL, step_id CHAR(36) CHARACTER SET ascii NOT NULL, action_id CHAR(36) CHARACTER SET ascii NOT NULL,
  config_id CHAR(36) CHARACTER SET ascii NOT NULL, config_revision CHAR(36) CHARACTER SET ascii NOT NULL,
  credential_reference_id CHAR(36) CHARACTER SET ascii NOT NULL, credential_revision VARCHAR(128) NOT NULL,
  verification_id CHAR(36) CHARACTER SET ascii NOT NULL, context_manifest_id CHAR(36) CHARACTER SET ascii NOT NULL,
  price_version CHAR(36) CHARACTER SET ascii NOT NULL, currency CHAR(3) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  request_parameters JSON NOT NULL, input_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  actual_send_manifest JSON NULL, actual_send_sha256 CHAR(64) CHARACTER SET ascii NULL,
  status VARCHAR(24) NOT NULL, settlement_status VARCHAR(24) NOT NULL,
  provider_request_id VARCHAR(256) NULL, dispatch_generation BIGINT UNSIGNED NOT NULL, lease_generation BIGINT UNSIGNED NOT NULL,
  result_artifact_id CHAR(36) CHARACTER SET ascii NULL, result_sha256 CHAR(64) CHARACTER SET ascii NULL, failure_code VARCHAR(64) NULL,
  usage_receipt_id CHAR(36) CHARACTER SET ascii NULL, settled_amount DECIMAL(24,8) NULL, total_tokens BIGINT UNSIGNED NULL,
  schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
  revision BIGINT UNSIGNED NOT NULL, updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (tenant_id, invocation_id),
  UNIQUE KEY uq_invocation_action (tenant_id, run_id, step_id, action_id),
  UNIQUE KEY uq_invocation_scope (tenant_id, project_key, invocation_id),
  UNIQUE KEY uq_invocation_currency (tenant_id, invocation_id, currency),
  KEY ix_invocation_status (tenant_id, project_key, status, settlement_status, created_at),
  FOREIGN KEY (tenant_id, project_key, run_id, step_id, action_id) REFERENCES step_action_binding (tenant_id, project_key, run_id, step_id, action_id),
  FOREIGN KEY (tenant_id, config_id, config_revision, credential_reference_id, credential_revision)
    REFERENCES model_config_revision (tenant_id, config_id, model_config_revision, credential_reference_id, credential_revision),
  FOREIGN KEY (tenant_id, verification_id, config_id, config_revision, credential_reference_id, credential_revision)
    REFERENCES connection_verification (tenant_id, verification_id, config_id, config_revision, credential_reference_id, credential_revision),
  FOREIGN KEY (tenant_id, config_revision, price_version, currency) REFERENCES model_config_revision (tenant_id, model_config_revision, price_version, currency),
  FOREIGN KEY (tenant_id, project_key, context_manifest_id) REFERENCES context_manifest (tenant_id, project_key, context_manifest_id),
  FOREIGN KEY (tenant_id, project_key, result_artifact_id, result_sha256) REFERENCES artifact (tenant_id, project_key, artifact_id, sha256),
  CHECK (status IN ('RESERVED','DISPATCHING','IN_FLIGHT','SUCCEEDED','FAILED','CANCEL_REQUESTED','CANCELLED','UNKNOWN')),
  CHECK (settlement_status IN ('PENDING','SETTLED','DISPUTED')),
  CHECK ((result_artifact_id IS NULL AND result_sha256 IS NULL) OR (result_artifact_id IS NOT NULL AND result_sha256 IS NOT NULL)),
  CHECK (status <> 'SUCCEEDED' OR result_artifact_id IS NOT NULL),
  CHECK ((actual_send_manifest IS NULL AND actual_send_sha256 IS NULL) OR (actual_send_manifest IS NOT NULL AND actual_send_sha256 IS NOT NULL)),
  CHECK (settlement_status <> 'SETTLED' OR (usage_receipt_id IS NOT NULL AND settled_amount IS NOT NULL)),
  CHECK (settled_amount IS NULL OR settled_amount >= 0),
  CHECK (lease_generation > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-103-invocation-receipt dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE invocation_receipt (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NULL,
  project_key VARCHAR(36) CHARACTER SET ascii GENERATED ALWAYS AS (COALESCE(project_id, 'ENTERPRISE')) STORED,
  receipt_id CHAR(36) CHARACTER SET ascii NOT NULL, invocation_id CHAR(36) CHARACTER SET ascii NOT NULL,
  kind VARCHAR(32) NOT NULL, receipt_ref JSON NOT NULL,
  receipt_artifact_id CHAR(36) CHARACTER SET ascii NOT NULL, receipt_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  received_at DATETIME(6) NOT NULL,
  schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
  PRIMARY KEY (tenant_id, receipt_id),
  UNIQUE KEY uq_invocation_receipt_identity (tenant_id, invocation_id, receipt_id),
  UNIQUE KEY uq_invocation_receipt_content (tenant_id, invocation_id, kind, receipt_sha256),
  FOREIGN KEY (tenant_id, project_key, invocation_id) REFERENCES invocation (tenant_id, project_key, invocation_id),
  FOREIGN KEY (tenant_id, project_key, receipt_artifact_id, receipt_sha256) REFERENCES artifact (tenant_id, project_key, artifact_id, sha256)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-104-budget-reservation dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE budget_reservation (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NULL,
  project_key VARCHAR(36) CHARACTER SET ascii GENERATED ALWAYS AS (COALESCE(project_id, 'ENTERPRISE')) STORED,
  reservation_id CHAR(36) CHARACTER SET ascii NOT NULL, invocation_id CHAR(36) CHARACTER SET ascii NOT NULL,
  budget_id CHAR(36) CHARACTER SET ascii NOT NULL, budget_revision CHAR(36) CHARACTER SET ascii NOT NULL,
  currency CHAR(3) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  upper_amount DECIMAL(24,8) NOT NULL, upper_tokens BIGINT UNSIGNED NOT NULL, status VARCHAR(24) NOT NULL,
  settlement_receipt_id CHAR(36) CHARACTER SET ascii NULL, settled_amount DECIMAL(24,8) NULL, settled_tokens BIGINT UNSIGNED NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
  revision BIGINT UNSIGNED NOT NULL, updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (tenant_id, reservation_id),
  UNIQUE KEY uq_invocation_budget (tenant_id, invocation_id, budget_id),
  KEY ix_reservation_budget_status (tenant_id, budget_id, status),
  FOREIGN KEY (tenant_id, project_key, invocation_id) REFERENCES invocation (tenant_id, project_key, invocation_id),
  FOREIGN KEY (tenant_id, invocation_id, currency) REFERENCES invocation (tenant_id, invocation_id, currency),
  FOREIGN KEY (tenant_id, budget_id, budget_revision, currency) REFERENCES budget_revision (tenant_id, budget_id, budget_revision, currency),
  FOREIGN KEY (tenant_id, invocation_id, settlement_receipt_id) REFERENCES invocation_receipt (tenant_id, invocation_id, receipt_id),
  CHECK (status IN ('RESERVED','SETTLED','RELEASED','DISPUTED')),
  CHECK (upper_amount >= 0),
  CHECK (settled_amount IS NULL OR settled_amount >= 0),
  CHECK (status <> 'SETTLED' OR (settlement_receipt_id IS NOT NULL AND settled_amount IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-105-current-agent dbms:mysql runInTransaction:false failOnError:true
ALTER TABLE configuration_head ADD CONSTRAINT fk_configuration_current_agent
  FOREIGN KEY (tenant_id, config_id, current_agent_version) REFERENCES agent_version (tenant_id, agent_id, agent_version_id);

--changeset medical-agent:1.0.0-106-current-capability dbms:mysql runInTransaction:false failOnError:true
ALTER TABLE configuration_head ADD CONSTRAINT fk_configuration_current_capability
  FOREIGN KEY (tenant_id, config_id, current_capability_version) REFERENCES capability_version (tenant_id, config_id, capability_version);

--changeset medical-agent:1.0.0-107-current-model dbms:mysql runInTransaction:false failOnError:true
ALTER TABLE configuration_head ADD CONSTRAINT fk_configuration_current_model
  FOREIGN KEY (tenant_id, config_id, current_model_revision) REFERENCES model_config_revision (tenant_id, config_id, model_config_revision);

--changeset medical-agent:1.0.0-108-current-budget dbms:mysql runInTransaction:false failOnError:true
ALTER TABLE budget_account ADD CONSTRAINT fk_budget_current_revision
  FOREIGN KEY (tenant_id, budget_id, current_budget_revision) REFERENCES budget_revision (tenant_id, budget_id, budget_revision);

--changeset medical-agent:1.0.0-109-task-agent-release dbms:mysql runInTransaction:false failOnError:true
ALTER TABLE task_revision ADD CONSTRAINT fk_task_agent_release
  FOREIGN KEY (tenant_id, agent_release_id) REFERENCES release_record (tenant_id, release_id);

--changeset medical-agent:1.0.0-110-task-budget dbms:mysql runInTransaction:false failOnError:true
ALTER TABLE task_run ADD CONSTRAINT fk_task_run_budget
  FOREIGN KEY (tenant_id, project_id, budget_revision) REFERENCES budget_revision (tenant_id, project_id, budget_revision);

--changeset medical-agent:1.0.0-111-system-job-budget dbms:mysql runInTransaction:false failOnError:true
ALTER TABLE system_job_run ADD CONSTRAINT fk_system_job_budget
  FOREIGN KEY (tenant_id, project_key, budget_revision) REFERENCES budget_revision (tenant_id, project_key, budget_revision);

--changeset medical-agent:1.0.0-112-system-job-parent-budget dbms:mysql runInTransaction:false failOnError:true
ALTER TABLE system_job_run ADD CONSTRAINT fk_system_job_parent_budget
  FOREIGN KEY (tenant_id, parent_budget_id) REFERENCES budget_account (tenant_id, budget_id);

--changeset medical-agent:1.0.0-113-action-invocation dbms:mysql runInTransaction:false failOnError:true
ALTER TABLE step_action_binding ADD CONSTRAINT fk_action_invocation
  FOREIGN KEY (tenant_id, project_key, invocation_id) REFERENCES invocation (tenant_id, project_key, invocation_id);

--changeset medical-agent:1.0.0-114-invocation-usage-receipt dbms:mysql runInTransaction:false failOnError:true
ALTER TABLE invocation ADD CONSTRAINT fk_invocation_usage_receipt
  FOREIGN KEY (tenant_id, invocation_id, usage_receipt_id) REFERENCES invocation_receipt (tenant_id, invocation_id, receipt_id);

--changeset medical-agent:1.0.0-115-index-model-config dbms:mysql runInTransaction:false failOnError:true
ALTER TABLE index_manifest ADD CONSTRAINT fk_index_model_config
  FOREIGN KEY (tenant_id, model_config_revision) REFERENCES model_config_revision (tenant_id, model_config_revision);

--changeset medical-agent:1.0.0-116-index-embedding-invocation dbms:mysql runInTransaction:false failOnError:true
ALTER TABLE index_item ADD CONSTRAINT fk_index_embedding_invocation
  FOREIGN KEY (tenant_id, embedding_invocation_id) REFERENCES invocation (tenant_id, invocation_id);

--changeset medical-agent:1.0.0-117-step-role-kind dbms:mysql runInTransaction:false failOnError:true
ALTER TABLE run_step ADD COLUMN role_kind VARCHAR(24) GENERATED ALWAYS AS ('ROLE') STORED;

--changeset medical-agent:1.0.0-118-step-role-version dbms:mysql runInTransaction:false failOnError:true
ALTER TABLE run_step ADD CONSTRAINT fk_step_role_version
  FOREIGN KEY (tenant_id, role_version, role_kind) REFERENCES capability_version (tenant_id, capability_version, kind);
