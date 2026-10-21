--liquibase formatted sql
-- Run-owned replay bootstrap. No step, lease or RUNNING state is manufactured.
-- Immutable rows: absence/presence of attempt and receipt records defines recovery phase.
-- These records are evidence only; every consumer rechecks current private authority.

--changeset medical-agent:1.0.0-092-task-run-replay-intent dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE task_run_replay_intent (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
 project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 run_id CHAR(36) CHARACTER SET ascii NOT NULL,
 project_key VARCHAR(36) CHARACTER SET ascii GENERATED ALWAYS AS (COALESCE(project_id,'ENTERPRISE')) STORED,
 principal_id CHAR(36) CHARACTER SET ascii NOT NULL,
 task_id CHAR(36) CHARACTER SET ascii NOT NULL,
 command_id CHAR(36) CHARACTER SET ascii NOT NULL,
 outbox_event_id CHAR(36) CHARACTER SET ascii NOT NULL,
 command_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 write_id CHAR(36) CHARACTER SET ascii NOT NULL,
 write_key CHAR(64) CHARACTER SET ascii NOT NULL,
 planned_artifact_id CHAR(36) CHARACTER SET ascii NOT NULL,
 object_key VARCHAR(1024) NOT NULL,
 replay_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 replay_schema_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 size_bytes BIGINT UNSIGNED NOT NULL,
 canonical_payload JSON NOT NULL,
 intent_json JSON NOT NULL,
 intent_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(32) NOT NULL,
 schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL,
 created_at DATETIME(6) NOT NULL,
 classification VARCHAR(24) NOT NULL,
 PRIMARY KEY (tenant_id,project_id,run_id),
 UNIQUE KEY uq_task_replay_write (tenant_id,project_id,run_id,write_id),
 UNIQUE KEY uq_task_replay_write_id (tenant_id,write_id),
 UNIQUE KEY uq_task_replay_planned (tenant_id,planned_artifact_id),
 UNIQUE KEY uq_task_replay_command (tenant_id,command_id),
 UNIQUE KEY uq_task_replay_run_command (tenant_id,project_id,run_id,command_id),
 UNIQUE KEY uq_task_replay_key (tenant_id,write_key),
 FOREIGN KEY (tenant_id,project_id,task_id,run_id) REFERENCES task_run(tenant_id,project_id,task_id,run_id),
 FOREIGN KEY (tenant_id,project_key,principal_id,run_id) REFERENCES run_principal(tenant_id,project_key,principal_id,run_id),
 FOREIGN KEY (tenant_id,outbox_event_id) REFERENCES outbox_event(tenant_id,event_id),
 CHECK(size_bytes>0 AND command_sha256 REGEXP '^[a-f0-9]{64}$' AND write_key REGEXP '^[a-f0-9]{64}$'
   AND replay_sha256 REGEXP '^[a-f0-9]{64}$' AND replay_schema_sha256 REGEXP '^[a-f0-9]{64}$' AND intent_sha256 REGEXP '^[a-f0-9]{64}$'),
 FOREIGN KEY (tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK(schema_version='1.0.0' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK(classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-092-task-run-replay-attempt dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE task_run_replay_attempt (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
 project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 run_id CHAR(36) CHARACTER SET ascii NOT NULL,
 write_id CHAR(36) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(32) NOT NULL,
 schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL,
 created_at DATETIME(6) NOT NULL,
 classification VARCHAR(24) NOT NULL,
 PRIMARY KEY (tenant_id,project_id,run_id,write_id),
 FOREIGN KEY (tenant_id,project_id,run_id,write_id) REFERENCES task_run_replay_intent(tenant_id,project_id,run_id,write_id),
 FOREIGN KEY (tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK(schema_version='1.0.0' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK(classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-092-task-run-replay-registration dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE task_run_replay_registration (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
 project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 run_id CHAR(36) CHARACTER SET ascii NOT NULL,
 project_key VARCHAR(36) CHARACTER SET ascii GENERATED ALWAYS AS (COALESCE(project_id,'ENTERPRISE')) STORED,
 write_id CHAR(36) CHARACTER SET ascii NOT NULL,
 artifact_id CHAR(36) CHARACTER SET ascii NOT NULL,
 object_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 object_version VARCHAR(256) NOT NULL,
 receipt_json JSON NOT NULL,
 receipt_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(32) NOT NULL,
 schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL,
 created_at DATETIME(6) NOT NULL,
 classification VARCHAR(24) NOT NULL,
 PRIMARY KEY (tenant_id,project_id,run_id),
 UNIQUE KEY uq_task_replay_registered_write (tenant_id,project_id,run_id,write_id),
 UNIQUE KEY uq_task_replay_registered_artifact (tenant_id,artifact_id),
 FOREIGN KEY (tenant_id,project_id,run_id,write_id) REFERENCES task_run_replay_attempt(tenant_id,project_id,run_id,write_id),
 FOREIGN KEY (tenant_id,project_key,artifact_id,object_sha256,object_version) REFERENCES artifact(tenant_id,project_key,artifact_id,sha256,object_version),
 CHECK(object_sha256 REGEXP '^[a-f0-9]{64}$' AND receipt_sha256 REGEXP '^[a-f0-9]{64}$' AND CHAR_LENGTH(object_version)>0),
 FOREIGN KEY (tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK(schema_version='1.0.0' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK(classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-092-task-run-workflow-launch dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE task_run_workflow_launch (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
 project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 run_id CHAR(36) CHARACTER SET ascii NOT NULL,
 launch_id CHAR(36) CHARACTER SET ascii NOT NULL,
 command_id CHAR(36) CHARACTER SET ascii NOT NULL,
 command_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 workflow_id VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 reference_json JSON NOT NULL,
 reference_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 intent_json JSON NOT NULL,
 intent_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(32) NOT NULL,
 schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL,
 created_at DATETIME(6) NOT NULL,
 classification VARCHAR(24) NOT NULL,
 PRIMARY KEY (tenant_id,project_id,run_id),
 UNIQUE KEY uq_task_launch_identity (tenant_id,project_id,run_id,launch_id),
 UNIQUE KEY uq_task_launch_id (tenant_id,launch_id),
 UNIQUE KEY uq_task_launch_command (tenant_id,command_id),
 UNIQUE KEY uq_task_launch_workflow (tenant_id,workflow_id),
 FOREIGN KEY (tenant_id,project_id,run_id) REFERENCES task_run_replay_registration(tenant_id,project_id,run_id),
 FOREIGN KEY (tenant_id,project_id,run_id,command_id) REFERENCES task_run_replay_intent(tenant_id,project_id,run_id,command_id),
 CHECK(workflow_id=CONCAT(tenant_id,'/',run_id) AND command_sha256 REGEXP '^[a-f0-9]{64}$'
   AND reference_sha256 REGEXP '^[a-f0-9]{64}$' AND intent_sha256 REGEXP '^[a-f0-9]{64}$'),
 FOREIGN KEY (tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK(schema_version='1.0.0' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK(classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-092-task-run-workflow-attempt dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE task_run_workflow_attempt (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
 project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 run_id CHAR(36) CHARACTER SET ascii NOT NULL,
 launch_id CHAR(36) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(32) NOT NULL,
 schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL,
 created_at DATETIME(6) NOT NULL,
 classification VARCHAR(24) NOT NULL,
 PRIMARY KEY (tenant_id,project_id,run_id,launch_id),
 FOREIGN KEY (tenant_id,project_id,run_id,launch_id) REFERENCES task_run_workflow_launch(tenant_id,project_id,run_id,launch_id),
 FOREIGN KEY (tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK(schema_version='1.0.0' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK(classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-092-task-run-workflow-acceptance dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE task_run_workflow_acceptance (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
 project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 run_id CHAR(36) CHARACTER SET ascii NOT NULL,
 launch_id CHAR(36) CHARACTER SET ascii NOT NULL,
 temporal_run_id CHAR(36) CHARACTER SET ascii NOT NULL,
 receipt_json JSON NOT NULL,
 receipt_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(32) NOT NULL,
 schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL,
 created_at DATETIME(6) NOT NULL,
 classification VARCHAR(24) NOT NULL,
 PRIMARY KEY (tenant_id,project_id,run_id),
 UNIQUE KEY uq_task_launch_accepted_id (tenant_id,project_id,run_id,launch_id),
 UNIQUE KEY uq_task_launch_temporal_run (tenant_id,temporal_run_id),
 FOREIGN KEY (tenant_id,project_id,run_id,launch_id) REFERENCES task_run_workflow_attempt(tenant_id,project_id,run_id,launch_id),
 CHECK(receipt_sha256 REGEXP '^[a-f0-9]{64}$'),
 FOREIGN KEY (tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK(schema_version='1.0.0' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK(classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

