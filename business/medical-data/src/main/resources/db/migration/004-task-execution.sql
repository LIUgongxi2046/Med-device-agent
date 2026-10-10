--liquibase formatted sql

--changeset medical-agent:1.0.0-034-execution-registry dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE execution_registry (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  run_id CHAR(36) CHARACTER SET ascii NOT NULL,
  project_id CHAR(36) CHARACTER SET ascii NULL,
  project_key VARCHAR(36) CHARACTER SET ascii GENERATED ALWAYS AS (COALESCE(project_id, 'ENTERPRISE')) STORED,
  execution_kind VARCHAR(24) NOT NULL,
  scope_manifest JSON NOT NULL,
  schema_version VARCHAR(32) NOT NULL,
  schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL,
  created_at DATETIME(6) NOT NULL,
  classification VARCHAR(24) NOT NULL,
  PRIMARY KEY (tenant_id, run_id),
  UNIQUE KEY uq_execution_project (tenant_id, project_id, run_id),
  UNIQUE KEY uq_execution_scope (tenant_id, project_key, run_id),
  UNIQUE KEY uq_execution_kind (tenant_id, run_id, execution_kind),
  FOREIGN KEY (tenant_id) REFERENCES tenant (tenant_id),
  FOREIGN KEY (tenant_id, project_id) REFERENCES project (tenant_id, project_id),
  CHECK (execution_kind IN ('TASK','SYSTEM_JOB')),
  CHECK (execution_kind <> 'TASK' OR project_id IS NOT NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-035-task-head dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE task_head (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  project_id CHAR(36) CHARACTER SET ascii NOT NULL,
  task_id CHAR(36) CHARACTER SET ascii NOT NULL,
  status VARCHAR(24) NOT NULL,
  current_revision CHAR(36) CHARACTER SET ascii NULL,
  owner_id CHAR(36) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL,
  created_at DATETIME(6) NOT NULL,
  classification VARCHAR(24) NOT NULL,
  revision BIGINT UNSIGNED NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (tenant_id, project_id, task_id),
  KEY ix_task_list (tenant_id, project_id, status, updated_at, task_id),
  FOREIGN KEY (tenant_id, project_id) REFERENCES project (tenant_id, project_id),
  FOREIGN KEY (tenant_id, owner_id) REFERENCES tenant_member (tenant_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-036-task-revision dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE task_revision (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  project_id CHAR(36) CHARACTER SET ascii NOT NULL,
  task_revision CHAR(36) CHARACTER SET ascii NOT NULL,
  task_id CHAR(36) CHARACTER SET ascii NOT NULL,
  profile_revision CHAR(36) CHARACTER SET ascii NOT NULL,
  snapshot_id CHAR(36) CHARACTER SET ascii NOT NULL,
  template_version CHAR(36) CHARACTER SET ascii NOT NULL,
  agent_release_id CHAR(36) CHARACTER SET ascii NOT NULL,
  plan_revision CHAR(36) CHARACTER SET ascii NULL,
  parent_revision CHAR(36) CHARACTER SET ascii NULL,
  reason TEXT NOT NULL,
  content_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL,
  created_at DATETIME(6) NOT NULL,
  classification VARCHAR(24) NOT NULL,
  PRIMARY KEY (tenant_id, project_id, task_revision),
  UNIQUE KEY uq_task_revision_identity (tenant_id, project_id, task_id, task_revision),
  UNIQUE KEY uq_task_plan_basis (tenant_id, project_id, task_id, task_revision, template_version, snapshot_id),
  FOREIGN KEY (tenant_id, project_id, task_id) REFERENCES task_head (tenant_id, project_id, task_id),
  FOREIGN KEY (tenant_id, project_id, profile_revision) REFERENCES project_profile_revision (tenant_id, project_id, profile_revision),
  FOREIGN KEY (tenant_id, project_id, snapshot_id) REFERENCES scope_snapshot (tenant_id, project_id, snapshot_id),
  FOREIGN KEY (tenant_id, template_version) REFERENCES template_version (tenant_id, template_version),
  FOREIGN KEY (tenant_id, project_id, task_id, parent_revision) REFERENCES task_revision (tenant_id, project_id, task_id, task_revision),
  FOREIGN KEY (tenant_id, task_revision) REFERENCES version_registry (tenant_id, version_id),
  CHECK (parent_revision IS NULL OR parent_revision <> task_revision)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-037-plan-revision dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE plan_revision (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  project_id CHAR(36) CHARACTER SET ascii NOT NULL,
  plan_revision CHAR(36) CHARACTER SET ascii NOT NULL,
  task_id CHAR(36) CHARACTER SET ascii NOT NULL,
  task_revision CHAR(36) CHARACTER SET ascii NOT NULL,
  template_version CHAR(36) CHARACTER SET ascii NOT NULL,
  snapshot_id CHAR(36) CHARACTER SET ascii NOT NULL,
  status VARCHAR(24) NOT NULL,
  decision_id CHAR(36) CHARACTER SET ascii NULL,
  content_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL,
  created_at DATETIME(6) NOT NULL,
  classification VARCHAR(24) NOT NULL,
  PRIMARY KEY (tenant_id, project_id, plan_revision),
  UNIQUE KEY uq_plan_template (tenant_id, project_id, plan_revision, template_version),
  UNIQUE KEY uq_plan_task_revision (tenant_id, project_id, task_id, task_revision, plan_revision),
  KEY ix_plan_task (tenant_id, project_id, task_id, status),
  FOREIGN KEY (tenant_id, project_id, task_id, task_revision, template_version, snapshot_id)
    REFERENCES task_revision (tenant_id, project_id, task_id, task_revision, template_version, snapshot_id),
  FOREIGN KEY (tenant_id, plan_revision) REFERENCES version_registry (tenant_id, version_id),
  CHECK (status IN ('DRAFT','CONFIRMED','SUPERSEDED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-038-plan-section dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE plan_section (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  project_id CHAR(36) CHARACTER SET ascii NOT NULL,
  plan_revision CHAR(36) CHARACTER SET ascii NOT NULL,
  section_id CHAR(36) CHARACTER SET ascii NOT NULL,
  template_version CHAR(36) CHARACTER SET ascii NOT NULL,
  node_id CHAR(36) CHARACTER SET ascii NOT NULL,
  owner_id CHAR(36) CHARACTER SET ascii NOT NULL,
  goal TEXT NOT NULL,
  requirements JSON NOT NULL,
  evidence_scope JSON NOT NULL,
  schema_version VARCHAR(32) NOT NULL,
  schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL,
  created_at DATETIME(6) NOT NULL,
  classification VARCHAR(24) NOT NULL,
  PRIMARY KEY (tenant_id, project_id, plan_revision, section_id),
  UNIQUE KEY uq_plan_requirement (tenant_id, project_id, plan_revision, node_id),
  FOREIGN KEY (tenant_id, project_id, plan_revision, template_version) REFERENCES plan_revision (tenant_id, project_id, plan_revision, template_version),
  FOREIGN KEY (tenant_id, template_version, node_id) REFERENCES requirement_node (tenant_id, template_version, node_id),
  FOREIGN KEY (tenant_id, owner_id) REFERENCES tenant_member (tenant_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-039-plan-dependency dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE plan_dependency (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  project_id CHAR(36) CHARACTER SET ascii NOT NULL,
  plan_revision CHAR(36) CHARACTER SET ascii NOT NULL,
  section_id CHAR(36) CHARACTER SET ascii NOT NULL,
  depends_on CHAR(36) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL,
  created_at DATETIME(6) NOT NULL,
  classification VARCHAR(24) NOT NULL,
  PRIMARY KEY (tenant_id, project_id, plan_revision, section_id, depends_on),
  FOREIGN KEY (tenant_id, project_id, plan_revision, section_id) REFERENCES plan_section (tenant_id, project_id, plan_revision, section_id),
  FOREIGN KEY (tenant_id, project_id, plan_revision, depends_on) REFERENCES plan_section (tenant_id, project_id, plan_revision, section_id),
  CHECK (section_id <> depends_on)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-040-research-question dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE research_question (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  project_id CHAR(36) CHARACTER SET ascii NOT NULL,
  question_id CHAR(36) CHARACTER SET ascii NOT NULL,
  plan_revision CHAR(36) CHARACTER SET ascii NOT NULL,
  section_id CHAR(36) CHARACTER SET ascii NOT NULL,
  question TEXT NOT NULL,
  completion_rule JSON NOT NULL,
  status VARCHAR(24) NOT NULL,
  evidence_manifest JSON NOT NULL,
  schema_version VARCHAR(32) NOT NULL,
  schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL,
  created_at DATETIME(6) NOT NULL,
  classification VARCHAR(24) NOT NULL,
  revision BIGINT UNSIGNED NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (tenant_id, project_id, question_id),
  KEY ix_question_status (tenant_id, project_id, plan_revision, section_id, status),
  FOREIGN KEY (tenant_id, project_id, plan_revision, section_id) REFERENCES plan_section (tenant_id, project_id, plan_revision, section_id),
  CHECK (status IN ('OPEN','WAITING_INPUT','ANSWERED','VERIFIED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-041-pending-input dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE pending_input (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  project_id CHAR(36) CHARACTER SET ascii NOT NULL,
  input_id CHAR(36) CHARACTER SET ascii NOT NULL,
  run_id CHAR(36) CHARACTER SET ascii NOT NULL,
  target_revision CHAR(36) CHARACTER SET ascii NOT NULL,
  content_ref JSON NOT NULL,
  impact_id CHAR(36) CHARACTER SET ascii NULL,
  decision_id CHAR(36) CHARACTER SET ascii NULL,
  status VARCHAR(24) NOT NULL,
  schema_version VARCHAR(32) NOT NULL,
  schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL,
  created_at DATETIME(6) NOT NULL,
  classification VARCHAR(24) NOT NULL,
  revision BIGINT UNSIGNED NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (tenant_id, project_id, input_id),
  KEY ix_pending_run (tenant_id, project_id, run_id, status),
  FOREIGN KEY (tenant_id, project_id, run_id) REFERENCES execution_registry (tenant_id, project_id, run_id),
  FOREIGN KEY (tenant_id, target_revision) REFERENCES version_registry (tenant_id, version_id),
  FOREIGN KEY (tenant_id, impact_id) REFERENCES impact_manifest (tenant_id, impact_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-042-system-job-run dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE system_job_run (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  run_id CHAR(36) CHARACTER SET ascii NOT NULL,
  execution_kind VARCHAR(24) NOT NULL,
  project_id CHAR(36) CHARACTER SET ascii NULL,
  project_key VARCHAR(36) CHARACTER SET ascii GENERATED ALWAYS AS (COALESCE(project_id, 'ENTERPRISE')) STORED,
  job_type VARCHAR(32) NOT NULL,
  initiator_id CHAR(36) CHARACTER SET ascii NOT NULL,
  input_manifest JSON NOT NULL,
  config_revision CHAR(36) CHARACTER SET ascii NOT NULL,
  budget_revision CHAR(36) CHARACTER SET ascii NULL,
  parent_budget_id CHAR(36) CHARACTER SET ascii NULL,
  target_kind VARCHAR(32) NOT NULL,
  target_id CHAR(36) CHARACTER SET ascii NOT NULL,
  target_revision CHAR(36) CHARACTER SET ascii NOT NULL,
  input_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  scope_manifest_id CHAR(36) CHARACTER SET ascii NOT NULL,
  rule_manifest_id CHAR(36) CHARACTER SET ascii NOT NULL,
  coverage_artifact_id CHAR(36) CHARACTER SET ascii NULL,
  status VARCHAR(24) NOT NULL,
  workflow_id VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  started_at DATETIME(6) NULL,
  ended_at DATETIME(6) NULL,
  schema_version VARCHAR(32) NOT NULL,
  schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL,
  created_at DATETIME(6) NOT NULL,
  classification VARCHAR(24) NOT NULL,
  revision BIGINT UNSIGNED NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (tenant_id, run_id),
  UNIQUE KEY uq_system_workflow (tenant_id, workflow_id),
  KEY ix_system_job_status (tenant_id, status, created_at),
  FOREIGN KEY (tenant_id, project_key, run_id) REFERENCES execution_registry (tenant_id, project_key, run_id),
  FOREIGN KEY (tenant_id, run_id, execution_kind) REFERENCES execution_registry (tenant_id, run_id, execution_kind),
  FOREIGN KEY (tenant_id, initiator_id) REFERENCES tenant_member (tenant_id, user_id),
  FOREIGN KEY (tenant_id, config_revision) REFERENCES version_registry (tenant_id, version_id),
  FOREIGN KEY (tenant_id, target_revision) REFERENCES version_registry (tenant_id, version_id),
  CHECK (execution_kind = 'SYSTEM_JOB'),
  CHECK (ended_at IS NULL OR (started_at IS NOT NULL AND ended_at >= started_at))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-043-task-run dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE task_run (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  project_id CHAR(36) CHARACTER SET ascii NOT NULL,
  run_id CHAR(36) CHARACTER SET ascii NOT NULL,
  execution_kind VARCHAR(24) NOT NULL,
  task_id CHAR(36) CHARACTER SET ascii NOT NULL,
  task_revision CHAR(36) CHARACTER SET ascii NOT NULL,
  attempt_no BIGINT UNSIGNED NOT NULL,
  parent_run_id CHAR(36) CHARACTER SET ascii NULL,
  superseded_by_run_id CHAR(36) CHARACTER SET ascii NULL,
  dispatch_closed_at DATETIME(6) NULL,
  workflow_id VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  status VARCHAR(24) NOT NULL,
  reason_code VARCHAR(64) NULL,
  replay_manifest JSON NOT NULL,
  budget_revision CHAR(36) CHARACTER SET ascii NOT NULL,
  started_at DATETIME(6) NULL,
  ended_at DATETIME(6) NULL,
  schema_version VARCHAR(32) NOT NULL,
  schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL,
  created_at DATETIME(6) NOT NULL,
  classification VARCHAR(24) NOT NULL,
  revision BIGINT UNSIGNED NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (tenant_id, project_id, run_id),
  UNIQUE KEY uq_task_run_identity (tenant_id, project_id, task_id, run_id),
  UNIQUE KEY uq_task_run_attempt (tenant_id, project_id, task_revision, attempt_no),
  UNIQUE KEY uq_task_workflow (tenant_id, workflow_id),
  KEY ix_task_run_status (tenant_id, project_id, task_id, status),
  FOREIGN KEY (tenant_id, project_id, run_id) REFERENCES execution_registry (tenant_id, project_id, run_id),
  FOREIGN KEY (tenant_id, run_id, execution_kind) REFERENCES execution_registry (tenant_id, run_id, execution_kind),
  FOREIGN KEY (tenant_id, project_id, task_id, task_revision) REFERENCES task_revision (tenant_id, project_id, task_id, task_revision),
  FOREIGN KEY (tenant_id, project_id, task_id, parent_run_id) REFERENCES task_run (tenant_id, project_id, task_id, run_id),
  FOREIGN KEY (tenant_id, project_id, task_id, superseded_by_run_id) REFERENCES task_run (tenant_id, project_id, task_id, run_id),
  CHECK (attempt_no > 0),
  CHECK (execution_kind = 'TASK'),
  CHECK (parent_run_id IS NULL OR parent_run_id <> run_id),
  CHECK (superseded_by_run_id IS NULL OR superseded_by_run_id <> run_id),
  CHECK (ended_at IS NULL OR (started_at IS NOT NULL AND ended_at >= started_at))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-044-run-step dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE run_step (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  project_id CHAR(36) CHARACTER SET ascii NULL,
  project_key VARCHAR(36) CHARACTER SET ascii GENERATED ALWAYS AS (COALESCE(project_id, 'ENTERPRISE')) STORED,
  run_id CHAR(36) CHARACTER SET ascii NOT NULL,
  step_id CHAR(36) CHARACTER SET ascii NOT NULL,
  role_version CHAR(36) CHARACTER SET ascii NOT NULL,
  section_id CHAR(36) CHARACTER SET ascii NULL,
  input_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  output_ref JSON NULL,
  status VARCHAR(24) NOT NULL,
  lease_generation BIGINT UNSIGNED NOT NULL,
  schema_version VARCHAR(32) NOT NULL,
  schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL,
  created_at DATETIME(6) NOT NULL,
  classification VARCHAR(24) NOT NULL,
  revision BIGINT UNSIGNED NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (tenant_id, run_id, step_id),
  UNIQUE KEY uq_step_scope (tenant_id, project_key, run_id, step_id),
  UNIQUE KEY uq_step_project (tenant_id, project_id, run_id, step_id),
  KEY ix_run_step_status (tenant_id, project_id, run_id, status, step_id),
  FOREIGN KEY (tenant_id, project_key, run_id) REFERENCES execution_registry (tenant_id, project_key, run_id),
  FOREIGN KEY (tenant_id, role_version) REFERENCES version_registry (tenant_id, version_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-045-step-action dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE step_action_binding (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  project_id CHAR(36) CHARACTER SET ascii NULL,
  project_key VARCHAR(36) CHARACTER SET ascii GENERATED ALWAYS AS (COALESCE(project_id, 'ENTERPRISE')) STORED,
  run_id CHAR(36) CHARACTER SET ascii NOT NULL,
  step_id CHAR(36) CHARACTER SET ascii NOT NULL,
  action_id CHAR(36) CHARACTER SET ascii NOT NULL,
  input_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  origin_run_id CHAR(36) CHARACTER SET ascii NOT NULL,
  origin_step_id CHAR(36) CHARACTER SET ascii NOT NULL,
  origin_action_id CHAR(36) CHARACTER SET ascii NOT NULL,
  kind VARCHAR(32) NOT NULL,
  arguments_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  tool_version VARCHAR(128) NOT NULL,
  state VARCHAR(24) NOT NULL,
  operation_id CHAR(36) CHARACTER SET ascii NULL,
  invocation_id CHAR(36) CHARACTER SET ascii NULL,
  result_artifact_id CHAR(36) CHARACTER SET ascii NULL,
  result_ref JSON NULL,
  lease_generation BIGINT UNSIGNED NOT NULL,
  schema_version VARCHAR(32) NOT NULL,
  schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL,
  created_at DATETIME(6) NOT NULL,
  classification VARCHAR(24) NOT NULL,
  revision BIGINT UNSIGNED NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (tenant_id, run_id, step_id, action_id),
  UNIQUE KEY uq_action_scope (tenant_id, project_key, run_id, step_id, action_id),
  KEY ix_action_origin (tenant_id, origin_run_id, origin_step_id, origin_action_id),
  KEY ix_action_state (tenant_id, state),
  FOREIGN KEY (tenant_id, project_key, run_id, step_id) REFERENCES run_step (tenant_id, project_key, run_id, step_id),
  FOREIGN KEY (tenant_id, project_key, origin_run_id, origin_step_id, origin_action_id)
    REFERENCES step_action_binding (tenant_id, project_key, run_id, step_id, action_id),
  FOREIGN KEY (tenant_id, operation_id) REFERENCES operation_record (tenant_id, operation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-046-task-current dbms:mysql runInTransaction:false failOnError:true
ALTER TABLE task_head ADD CONSTRAINT fk_task_current_revision
  FOREIGN KEY (tenant_id, project_id, task_id, current_revision)
  REFERENCES task_revision (tenant_id, project_id, task_id, task_revision);

--changeset medical-agent:1.0.0-047-task-plan dbms:mysql runInTransaction:false failOnError:true
ALTER TABLE task_revision ADD CONSTRAINT fk_task_confirmed_plan
  FOREIGN KEY (tenant_id, project_id, task_id, task_revision, plan_revision)
  REFERENCES plan_revision (tenant_id, project_id, task_id, task_revision, plan_revision);

--changeset medical-agent:1.0.0-048-principal-run dbms:mysql runInTransaction:false failOnError:true
ALTER TABLE run_principal
  ADD CONSTRAINT fk_principal_run FOREIGN KEY (tenant_id, project_key, run_id)
    REFERENCES execution_registry (tenant_id, project_key, run_id);

--changeset medical-agent:1.0.0-049-processing-run dbms:mysql runInTransaction:false failOnError:true
ALTER TABLE processing_attempt ADD CONSTRAINT fk_processing_execution
  FOREIGN KEY (tenant_id, project_id, run_id) REFERENCES execution_registry (tenant_id, project_id, run_id);
