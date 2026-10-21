--liquibase formatted sql
-- Real execution registration only. No evaluations, grants, approvals, provider results or scores are seeded.
--changeset medical-agent:142-01-formal-evaluation-job dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE formal_evaluation_job (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 execution_id CHAR(36) CHARACTER SET ascii NOT NULL,run_id CHAR(36) CHARACTER SET ascii NOT NULL,
 original_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 canonical_admission LONGTEXT NOT NULL,admission_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 audit_id CHAR(36) CHARACTER SET ascii NOT NULL,event_id CHAR(36) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(32) NOT NULL,schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL,created_at DATETIME(6) NOT NULL,classification VARCHAR(24) NOT NULL,
 PRIMARY KEY(tenant_id,run_id),UNIQUE KEY uq_eval_job_project(tenant_id,project_id,run_id),
 FOREIGN KEY(tenant_id,project_id,execution_id) REFERENCES formal_evaluation_execution(tenant_id,project_id,execution_id),
 FOREIGN KEY(tenant_id,run_id) REFERENCES formal_evaluation_cell(tenant_id,run_id),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 FOREIGN KEY(tenant_id,audit_id) REFERENCES audit_event(tenant_id,audit_id),FOREIGN KEY(tenant_id,event_id) REFERENCES outbox_event(tenant_id,event_id),
 CHECK(JSON_VALID(canonical_admission) AND admission_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK(schema_version='1.0.0' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK(CHAR_LENGTH(original_key) BETWEEN 16 AND 128 AND original_key REGEXP '^[!-~]+$'),
 CHECK(classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:142-02-formal-evaluation-launch-intent dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE formal_evaluation_launch_intent (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,project_id CHAR(36) CHARACTER SET ascii NOT NULL,run_id CHAR(36) CHARACTER SET ascii NOT NULL,
 command_id CHAR(36) CHARACTER SET ascii NOT NULL,canonical_reference LONGTEXT NOT NULL,reference_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 audit_id CHAR(36) CHARACTER SET ascii NOT NULL,event_id CHAR(36) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(32) NOT NULL,schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL,created_at DATETIME(6) NOT NULL,classification VARCHAR(24) NOT NULL,
 PRIMARY KEY(tenant_id,run_id),UNIQUE KEY uq_eval_launch_command(tenant_id,command_id),
 UNIQUE KEY uq_eval_launch_exact(tenant_id,run_id,command_id,reference_sha256),
 FOREIGN KEY(tenant_id,project_id,run_id) REFERENCES formal_evaluation_job(tenant_id,project_id,run_id),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),FOREIGN KEY(tenant_id,audit_id) REFERENCES audit_event(tenant_id,audit_id),FOREIGN KEY(tenant_id,event_id) REFERENCES outbox_event(tenant_id,event_id),
 CHECK(JSON_VALID(canonical_reference) AND reference_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK(schema_version='1.0.0' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),CHECK(classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:142-03-formal-evaluation-launch-attempt dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE formal_evaluation_launch_attempt (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,project_id CHAR(36) CHARACTER SET ascii NOT NULL,run_id CHAR(36) CHARACTER SET ascii NOT NULL,
 command_id CHAR(36) CHARACTER SET ascii NOT NULL,reference_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(32) NOT NULL,schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL,created_at DATETIME(6) NOT NULL,classification VARCHAR(24) NOT NULL,
 PRIMARY KEY(tenant_id,run_id),
 FOREIGN KEY(tenant_id,project_id,run_id) REFERENCES formal_evaluation_job(tenant_id,project_id,run_id),
 FOREIGN KEY(tenant_id,run_id,command_id,reference_sha256) REFERENCES formal_evaluation_launch_intent(tenant_id,run_id,command_id,reference_sha256),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK(schema_version='1.0.0' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),CHECK(classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:142-04-formal-evaluation-launch-receipt dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE formal_evaluation_launch_receipt (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,project_id CHAR(36) CHARACTER SET ascii NOT NULL,run_id CHAR(36) CHARACTER SET ascii NOT NULL,
 command_id CHAR(36) CHARACTER SET ascii NOT NULL,reference_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 workflow_id VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,temporal_run_id CHAR(36) CHARACTER SET ascii NOT NULL,
 canonical_receipt LONGTEXT NOT NULL,receipt_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 audit_id CHAR(36) CHARACTER SET ascii NOT NULL,event_id CHAR(36) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(32) NOT NULL,schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL,created_at DATETIME(6) NOT NULL,classification VARCHAR(24) NOT NULL,
 PRIMARY KEY(tenant_id,run_id),UNIQUE KEY uq_eval_launch_temporal(tenant_id,temporal_run_id),
 FOREIGN KEY(tenant_id,project_id,run_id) REFERENCES formal_evaluation_job(tenant_id,project_id,run_id),
 FOREIGN KEY(tenant_id,run_id,command_id,reference_sha256) REFERENCES formal_evaluation_launch_intent(tenant_id,run_id,command_id,reference_sha256),
 FOREIGN KEY(tenant_id,run_id) REFERENCES formal_evaluation_launch_attempt(tenant_id,run_id),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),FOREIGN KEY(tenant_id,audit_id) REFERENCES audit_event(tenant_id,audit_id),FOREIGN KEY(tenant_id,event_id) REFERENCES outbox_event(tenant_id,event_id),
 CHECK(JSON_VALID(canonical_receipt) AND receipt_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK(schema_version='1.0.0' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),CHECK(classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

-- A queued EVAL job is not an accepted workflow. Existing job-type constraints remain intact.
--changeset medical-agent:142-05-formal-evaluation-workflow dbms:mysql runInTransaction:false failOnError:true
ALTER TABLE system_job_run DROP CHECK ck_system_simulation_workflow,
 ADD CONSTRAINT ck_system_simulation_workflow CHECK (
  (job_type IN ('SIMULATION_EVALUATION','DOCUMENT_TECHNICAL_CHECK','MODEL_VERIFICATION') AND workflow_id IS NULL)
  OR (job_type IN ('PLAN_DRAFT','EVALUATION_VERIFICATION') AND ((status='QUEUED' AND workflow_id IS NULL) OR workflow_id IS NOT NULL))
  OR (job_type NOT IN ('SIMULATION_EVALUATION','DOCUMENT_TECHNICAL_CHECK','MODEL_VERIFICATION','PLAN_DRAFT','EVALUATION_VERIFICATION') AND workflow_id IS NOT NULL));

--changeset medical-agent:142-06-update-immutable dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER tr_142_6_update BEFORE UPDATE ON formal_evaluation_job FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='FORMAL_EVALUATION_ORIGINAL_IMMUTABLE';

--changeset medical-agent:142-06-delete-immutable dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER tr_142_6_delete BEFORE DELETE ON formal_evaluation_job FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='FORMAL_EVALUATION_ORIGINAL_IMMUTABLE';

--changeset medical-agent:142-07-update-immutable dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER tr_142_7_update BEFORE UPDATE ON formal_evaluation_launch_intent FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='FORMAL_EVALUATION_ORIGINAL_IMMUTABLE';

--changeset medical-agent:142-07-delete-immutable dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER tr_142_7_delete BEFORE DELETE ON formal_evaluation_launch_intent FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='FORMAL_EVALUATION_ORIGINAL_IMMUTABLE';

--changeset medical-agent:142-08-update-immutable dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER tr_142_8_update BEFORE UPDATE ON formal_evaluation_launch_attempt FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='FORMAL_EVALUATION_ORIGINAL_IMMUTABLE';

--changeset medical-agent:142-08-delete-immutable dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER tr_142_8_delete BEFORE DELETE ON formal_evaluation_launch_attempt FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='FORMAL_EVALUATION_ORIGINAL_IMMUTABLE';

--changeset medical-agent:142-09-update-immutable dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER tr_142_9_update BEFORE UPDATE ON formal_evaluation_launch_receipt FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='FORMAL_EVALUATION_ORIGINAL_IMMUTABLE';

--changeset medical-agent:142-09-delete-immutable dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER tr_142_9_delete BEFORE DELETE ON formal_evaluation_launch_receipt FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='FORMAL_EVALUATION_ORIGINAL_IMMUTABLE';

--changeset medical-agent:142-10-update-immutable dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER tr_142_10_update BEFORE UPDATE ON formal_evaluation_cell FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='FORMAL_EVALUATION_ORIGINAL_IMMUTABLE';

--changeset medical-agent:142-10-delete-immutable dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER tr_142_10_delete BEFORE DELETE ON formal_evaluation_cell FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='FORMAL_EVALUATION_ORIGINAL_IMMUTABLE';

--changeset medical-agent:142-11-update-immutable dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER tr_142_11_update BEFORE UPDATE ON formal_evaluation_node FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='FORMAL_EVALUATION_ORIGINAL_IMMUTABLE';

--changeset medical-agent:142-11-delete-immutable dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER tr_142_11_delete BEFORE DELETE ON formal_evaluation_node FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='FORMAL_EVALUATION_ORIGINAL_IMMUTABLE';
