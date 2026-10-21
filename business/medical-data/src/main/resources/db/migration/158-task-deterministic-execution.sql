--liquibase formatted sql
--changeset medical-agent:1.0.0-158-deterministic-execution dbms:mysql runInTransaction:false failOnError:true
-- Real in-process deterministic activity ownership. OPEN is uncertainty, not exit.
CREATE TABLE task_deterministic_execution (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
 project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 run_id CHAR(36) CHARACTER SET ascii NOT NULL,
 stage_kind VARCHAR(8) NOT NULL,
 command_id CHAR(36) CHARACTER SET ascii NOT NULL,
 execution_id CHAR(36) CHARACTER SET ascii NOT NULL,
 state VARCHAR(16) NOT NULL,
 started_json JSON NOT NULL,
 started_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 closed_json JSON NULL,
 closed_sha256 CHAR(64) CHARACTER SET ascii NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(32) NOT NULL,
 schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 classification VARCHAR(24) NOT NULL,
 created_at DATETIME(6) NOT NULL,
 closed_at DATETIME(6) NULL,
 PRIMARY KEY (tenant_id, run_id, stage_kind, command_id),
 UNIQUE KEY uk_task_deterministic_execution (tenant_id, execution_id),
 FOREIGN KEY(tenant_id,project_id,run_id) REFERENCES execution_registry(tenant_id,project_id,run_id),
 FOREIGN KEY(tenant_id,run_id) REFERENCES task_run(tenant_id,run_id),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK(schema_version='1.0.0' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$' AND started_sha256 REGEXP '^[a-f0-9]{64}$' AND (closed_sha256 IS NULL OR closed_sha256 REGEXP '^[a-f0-9]{64}$')),
 CHECK(classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH')),
 CHECK(closed_at IS NULL OR closed_at>=created_at),
 CONSTRAINT ck_task_deterministic_stage CHECK (stage_kind IN ('N03','N06')),
 CONSTRAINT ck_task_deterministic_state CHECK ((state='OPEN' AND closed_json IS NULL AND closed_sha256 IS NULL AND closed_at IS NULL) OR (state='CLOSED' AND closed_json IS NOT NULL AND closed_sha256 IS NOT NULL AND closed_at IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-158-deterministic-update dbms:mysql runInTransaction:false failOnError:true splitStatements:false
CREATE TRIGGER trg_158_deterministic_update BEFORE UPDATE ON task_deterministic_execution FOR EACH ROW
BEGIN
 IF OLD.state<>'OPEN' OR NEW.state<>'CLOSED' OR
 NOT (OLD.tenant_id<=>NEW.tenant_id) OR NOT (OLD.project_id<=>NEW.project_id) OR
 NOT (OLD.run_id<=>NEW.run_id) OR NOT (OLD.stage_kind<=>NEW.stage_kind) OR
 NOT (OLD.command_id<=>NEW.command_id) OR NOT (OLD.execution_id<=>NEW.execution_id) OR
 NOT (OLD.started_json<=>NEW.started_json) OR NOT (OLD.started_sha256<=>NEW.started_sha256) OR
 NOT (OLD.created_by<=>NEW.created_by) OR NOT (OLD.classification<=>NEW.classification) OR
 NOT (OLD.created_at<=>NEW.created_at) OR NOT (OLD.schema_version<=>NEW.schema_version) OR
 NOT (OLD.schema_manifest_sha256<=>NEW.schema_manifest_sha256)
 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Only original deterministic owner exit may close OPEN execution'; END IF;
END;
--changeset medical-agent:1.0.0-158-deterministic-delete dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER trg_158_deterministic_delete BEFORE DELETE ON task_deterministic_execution FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable deterministic execution identity';
