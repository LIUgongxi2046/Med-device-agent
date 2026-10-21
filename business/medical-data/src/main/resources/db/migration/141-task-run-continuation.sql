--liquibase formatted sql
-- Original RN-04 decision plus locked absence of actual start/dispatch permits
-- pre-runtime cancellation. This never manufactures a runtime or process receipt.
--changeset medical-agent:1.0.0-141-preruntime-termination dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE task_run_preruntime_termination (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
 project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 task_id CHAR(36) CHARACTER SET ascii NOT NULL,
 run_id CHAR(36) CHARACTER SET ascii NOT NULL,
 receipt_id CHAR(36) CHARACTER SET ascii NOT NULL,
 decision_id CHAR(36) CHARACTER SET ascii NOT NULL,
 command_id CHAR(36) CHARACTER SET ascii NOT NULL,
 receipt_json JSON NOT NULL, receipt_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
 PRIMARY KEY(tenant_id,project_id,run_id), UNIQUE KEY uq_141_receipt(tenant_id,receipt_id),
 FOREIGN KEY(tenant_id,project_id,task_id,run_id) REFERENCES task_run(tenant_id,project_id,task_id,run_id),
 FOREIGN KEY(tenant_id,decision_id) REFERENCES operation_record(tenant_id,operation_id),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK(schema_version='1.0.0' AND receipt_sha256 REGEXP '^[a-f0-9]{64}$' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK(classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
--changeset medical-agent:1.0.0-141-preruntime-update-guard dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER trg_141_preruntime_update BEFORE UPDATE ON task_run_preruntime_termination FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable pre-runtime cancellation';
--changeset medical-agent:1.0.0-141-preruntime-delete-guard dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER trg_141_preruntime_delete BEFORE DELETE ON task_run_preruntime_termination FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable pre-runtime cancellation';
-- The fifth check is the immutable 004-task-execution.sql ended/start check.
-- Never invent started_at for a workflow which was not started.
--changeset medical-agent:1.0.0-141-preruntime-ended dbms:mysql runInTransaction:false failOnError:true
ALTER TABLE task_run DROP CHECK task_run_chk_5,
 ADD CONSTRAINT ck_141_run_end CHECK(ended_at IS NULL OR (started_at IS NOT NULL AND ended_at>=started_at) OR
   (status='CANCELLED' AND started_at IS NULL AND dispatch_closed_at IS NOT NULL AND ended_at>=dispatch_closed_at AND ended_at>=created_at));
--changeset medical-agent:1.0.0-141-preruntime-proof-guard dbms:mysql runInTransaction:false failOnError:true splitStatements:false
CREATE TRIGGER trg_141_preruntime_terminal BEFORE UPDATE ON task_run FOR EACH ROW
BEGIN
 IF NEW.ended_at IS NOT NULL AND NEW.started_at IS NULL AND NOT EXISTS
 (SELECT 1 FROM task_run_preruntime_termination t WHERE t.tenant_id=NEW.tenant_id AND t.project_id=NEW.project_id AND t.run_id=NEW.run_id)
 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Original pre-runtime termination proof required'; END IF;
 IF OLD.status IN ('SUCCEEDED','FAILED','CANCELLED') AND
 (NEW.status<>OLD.status OR NOT (NEW.replay_manifest<=>OLD.replay_manifest) OR NOT (NEW.task_revision<=>OLD.task_revision) OR NOT (NEW.budget_revision<=>OLD.budget_revision) OR NOT (NEW.ended_at<=>OLD.ended_at) OR NOT (NEW.started_at<=>OLD.started_at))
 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Original run terminal and fixed inputs immutable'; END IF;
END;

-- TK-05 and TK-06 are immutable decisions, never an approval shortcut.
--changeset medical-agent:1.0.0-141-input-continuation dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE task_input_continuation (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 task_id CHAR(36) CHARACTER SET ascii NOT NULL, operation_id CHAR(36) CHARACTER SET ascii NOT NULL,
 operation_name VARCHAR(24) NOT NULL, proposal_id CHAR(36) CHARACTER SET ascii NOT NULL,
 basis_task_revision CHAR(36) CHARACTER SET ascii NOT NULL, resulting_task_revision CHAR(36) CHARACTER SET ascii NULL,
 input_json JSON NOT NULL, input_sha256 CHAR(64) CHARACTER SET ascii NOT NULL, request_json JSON NOT NULL,
 receipt_json JSON NOT NULL, receipt_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
 PRIMARY KEY(tenant_id,operation_id), UNIQUE KEY uq_141_proposal_decision(tenant_id,project_id,task_id,proposal_id,operation_name),
 FOREIGN KEY(tenant_id,operation_id) REFERENCES operation_record(tenant_id,operation_id),
 FOREIGN KEY(tenant_id,project_id,task_id,basis_task_revision) REFERENCES task_revision(tenant_id,project_id,task_id,task_revision),
 FOREIGN KEY(tenant_id,project_id,task_id,resulting_task_revision) REFERENCES task_revision(tenant_id,project_id,task_id,task_revision),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK(schema_version='1.0.0' AND input_sha256 REGEXP '^[a-f0-9]{64}$' AND receipt_sha256 REGEXP '^[a-f0-9]{64}$' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK(operation_name IN ('TK-05','TK-06') AND ((operation_name='TK-06')=(resulting_task_revision IS NOT NULL))),
 CHECK(classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
--changeset medical-agent:1.0.0-141-input-update-guard dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER trg_141_input_update BEFORE UPDATE ON task_input_continuation FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable input continuation decision';
--changeset medical-agent:1.0.0-141-input-delete-guard dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER trg_141_input_delete BEFORE DELETE ON task_input_continuation FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable input continuation decision';

--changeset medical-agent:1.0.0-141-run-continuation dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE task_run_continuation (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 task_id CHAR(36) CHARACTER SET ascii NOT NULL, parent_run_id CHAR(36) CHARACTER SET ascii NOT NULL,
 run_id CHAR(36) CHARACTER SET ascii NOT NULL, operation_id CHAR(36) CHARACTER SET ascii NOT NULL, mode VARCHAR(32) NOT NULL,
 receipt_json JSON NOT NULL, receipt_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
 PRIMARY KEY(tenant_id,project_id,run_id), UNIQUE KEY uq_141_run_parent(tenant_id,project_id,parent_run_id),
 UNIQUE KEY uq_141_run_operation(tenant_id,operation_id),
 FOREIGN KEY(tenant_id,project_id,task_id,parent_run_id) REFERENCES task_run(tenant_id,project_id,task_id,run_id),
 FOREIGN KEY(tenant_id,project_id,task_id,run_id) REFERENCES task_run(tenant_id,project_id,task_id,run_id),
 FOREIGN KEY(tenant_id,operation_id) REFERENCES operation_record(tenant_id,operation_id),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK(schema_version='1.0.0' AND receipt_sha256 REGEXP '^[a-f0-9]{64}$' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK(mode IN ('RESUME','REEXECUTE_PINNED','RERUN_UPDATED') AND parent_run_id<>run_id),
 CHECK(classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
--changeset medical-agent:1.0.0-141-run-continuation-update dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER trg_141_continuation_update BEFORE UPDATE ON task_run_continuation FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable run continuation';
--changeset medical-agent:1.0.0-141-run-continuation-delete dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER trg_141_continuation_delete BEFORE DELETE ON task_run_continuation FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable run continuation';

-- A killed owned process has no business session-export. Keep it distinct from CLOSED.
--changeset medical-agent:1.0.0-141-supervisor-termination dbms:mysql runInTransaction:false failOnError:true
ALTER TABLE professional_runtime_supervision DROP CHECK professional_runtime_supervision_chk_1,
 ADD CONSTRAINT ck_141_supervision_phase CHECK(lease_generation>0 AND phase IN ('ADMITTED','CLOSED','TERMINATED') AND JSON_VALID(canonical_evidence));
