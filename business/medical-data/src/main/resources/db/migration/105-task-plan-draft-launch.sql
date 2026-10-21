--liquibase formatted sql
-- Dedicated N01 launch identity; no TASK plan, approval, successful run or credentials seeded.
--changeset medical-agent:1.0.0-105-plan-draft-launch-intent dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE task_plan_draft_launch_intent (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
 project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 run_id CHAR(36) CHARACTER SET ascii NOT NULL,
 command_id CHAR(36) CHARACTER SET ascii NOT NULL,
 canonical_reference LONGTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
 reference_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 input_artifact_id CHAR(36) CHARACTER SET ascii NOT NULL,
 input_receipt_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 job_admission_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 config_json LONGTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
 config_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 audit_id CHAR(36) CHARACTER SET ascii NOT NULL,
 event_id CHAR(36) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(32) NOT NULL,
 schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL,
 created_at DATETIME(6) NOT NULL,
 classification VARCHAR(24) NOT NULL,
 PRIMARY KEY(tenant_id,run_id),
 UNIQUE KEY uq_plan_draft_launch_command(tenant_id,command_id),
 FOREIGN KEY(tenant_id,run_id) REFERENCES task_plan_draft_job(tenant_id,run_id),
 FOREIGN KEY(tenant_id,audit_id) REFERENCES audit_event(tenant_id,audit_id),
 FOREIGN KEY(tenant_id,event_id) REFERENCES outbox_event(tenant_id,event_id),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK(JSON_VALID(canonical_reference) AND JSON_VALID(config_json)),
 CHECK(reference_sha256 REGEXP '^[a-f0-9]{64}$' AND input_receipt_sha256 REGEXP '^[a-f0-9]{64}$' AND job_admission_sha256 REGEXP '^[a-f0-9]{64}$' AND config_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK(schema_version='1.0.0' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK(classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-105-plan-draft-launch-attempt dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE task_plan_draft_launch_attempt (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
 project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 run_id CHAR(36) CHARACTER SET ascii NOT NULL,
 command_id CHAR(36) CHARACTER SET ascii NOT NULL,
 reference_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(32) NOT NULL,
 schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL,
 created_at DATETIME(6) NOT NULL,
 classification VARCHAR(24) NOT NULL,
 PRIMARY KEY(tenant_id,run_id),
 FOREIGN KEY(tenant_id,run_id) REFERENCES task_plan_draft_launch_intent(tenant_id,run_id),
 FOREIGN KEY(tenant_id,command_id) REFERENCES task_plan_draft_launch_intent(tenant_id,command_id),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK(reference_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK(schema_version='1.0.0' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK(classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-105-plan-draft-launch-receipt dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE task_plan_draft_launch_receipt (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
 project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 run_id CHAR(36) CHARACTER SET ascii NOT NULL,
 workflow_id VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 temporal_run_id CHAR(36) CHARACTER SET ascii NOT NULL,
 reference_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 receipt_json LONGTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
 receipt_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 audit_id CHAR(36) CHARACTER SET ascii NOT NULL,
 event_id CHAR(36) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(32) NOT NULL,
 schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL,
 created_at DATETIME(6) NOT NULL,
 classification VARCHAR(24) NOT NULL,
 PRIMARY KEY(tenant_id,run_id),
 UNIQUE KEY uq_plan_draft_temporal(tenant_id,workflow_id,temporal_run_id),
 FOREIGN KEY(tenant_id,run_id) REFERENCES task_plan_draft_launch_attempt(tenant_id,run_id),
 FOREIGN KEY(tenant_id,audit_id) REFERENCES audit_event(tenant_id,audit_id),
 FOREIGN KEY(tenant_id,event_id) REFERENCES outbox_event(tenant_id,event_id),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK(JSON_VALID(receipt_json) AND reference_sha256 REGEXP '^[a-f0-9]{64}$' AND receipt_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK(schema_version='1.0.0' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK(classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

-- An unaccepted PLAN_DRAFT is a real queued job, not a fabricated workflow.
--changeset medical-agent:105-04-plan-draft-workflow dbms:mysql runInTransaction:false failOnError:true
ALTER TABLE system_job_run DROP CHECK ck_system_simulation_workflow,
 ADD CONSTRAINT ck_system_simulation_workflow CHECK (
  (job_type IN ('SIMULATION_EVALUATION','DOCUMENT_TECHNICAL_CHECK','MODEL_VERIFICATION') AND workflow_id IS NULL)
  OR (job_type='PLAN_DRAFT' AND ((status='QUEUED' AND workflow_id IS NULL) OR workflow_id IS NOT NULL))
  OR (job_type NOT IN ('SIMULATION_EVALUATION','DOCUMENT_TECHNICAL_CHECK','MODEL_VERIFICATION','PLAN_DRAFT') AND workflow_id IS NOT NULL));

--changeset medical-agent:105-05-plan-draft-completion dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE task_plan_draft_completion (
 project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(32) NOT NULL,schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL,classification VARCHAR(24) NOT NULL,
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,run_id CHAR(36) CHARACTER SET ascii NOT NULL,
 receipt_id CHAR(36) CHARACTER SET ascii NOT NULL,receipt_json LONGTEXT NOT NULL,
 receipt_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,tool_receipt_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 audit_id CHAR(36) CHARACTER SET ascii NOT NULL,event_id CHAR(36) CHARACTER SET ascii NOT NULL,created_at DATETIME(6) NOT NULL,
 CHECK(schema_version='1.0.0' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK(classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH')),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 FOREIGN KEY(tenant_id,project_id) REFERENCES project(tenant_id,project_id),
 PRIMARY KEY(tenant_id,run_id),UNIQUE KEY uq_plan_draft_completion(tenant_id,receipt_id),
 FOREIGN KEY(tenant_id,run_id) REFERENCES task_plan_draft_job(tenant_id,run_id),
 FOREIGN KEY(tenant_id,audit_id) REFERENCES audit_event(tenant_id,audit_id),FOREIGN KEY(tenant_id,event_id) REFERENCES outbox_event(tenant_id,event_id),
 CHECK(JSON_VALID(receipt_json) AND receipt_sha256 REGEXP '^[a-f0-9]{64}$' AND tool_receipt_sha256 REGEXP '^[a-f0-9]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
