--liquibase formatted sql
-- Actual N04 input, physical object and candidate-only receipts. No adoption authority.

--changeset medical-agent:1.0.0-108-1 dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE task_section_writer_object_intent (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  project_id CHAR(36) CHARACTER SET ascii NOT NULL,
  schema_version VARCHAR(16) NOT NULL,
  schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL,
  created_at DATETIME(6) NOT NULL,
  classification VARCHAR(32) NOT NULL,
  task_id CHAR(36) CHARACTER SET ascii NOT NULL,
  run_id CHAR(36) CHARACTER SET ascii NOT NULL,
  step_id CHAR(36) CHARACTER SET ascii NOT NULL,
  bound_action_id CHAR(36) CHARACTER SET ascii NULL,
  action_id CHAR(36) CHARACTER SET ascii NOT NULL,
  write_id CHAR(36) CHARACTER SET ascii NOT NULL,
  artifact_id CHAR(36) CHARACTER SET ascii NOT NULL,
  payload_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  intent_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  canonical_payload LONGTEXT NOT NULL,
  producer_pins_json LONGTEXT NOT NULL,
  intent_json LONGTEXT NOT NULL,
  kind VARCHAR(40) NOT NULL,
  object_key VARCHAR(1024) NOT NULL,
  PRIMARY KEY (tenant_id,write_id),
  UNIQUE KEY uq_writer_intent_stage_kind (tenant_id,run_id,step_id,kind),
  UNIQUE KEY uq_writer_intent_artifact (tenant_id,artifact_id),
  FOREIGN KEY (tenant_id) REFERENCES tenant(tenant_id),
  FOREIGN KEY (tenant_id,project_id) REFERENCES project(tenant_id,project_id),
  FOREIGN KEY (tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
  CHECK (schema_version='1.0.0' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
  CHECK (classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH')),
  FOREIGN KEY (tenant_id,project_id,task_id,run_id) REFERENCES task_run(tenant_id,project_id,task_id,run_id),
  FOREIGN KEY (tenant_id,run_id,step_id,bound_action_id) REFERENCES step_action_binding(tenant_id,run_id,step_id,action_id),
  CHECK ((kind='TASK_SECTION_WRITER_INPUT' AND bound_action_id IS NULL) OR (kind<>'TASK_SECTION_WRITER_INPUT' AND bound_action_id IS NOT NULL AND bound_action_id=action_id)),
  CHECK (kind IN ('TASK_SECTION_WRITER_INPUT','SECTION_BODY','SECTION_CANDIDATE','STEP_ENVELOPE')),
  CHECK (JSON_VALID(canonical_payload)),
  CHECK (JSON_VALID(producer_pins_json)),
  CHECK (JSON_VALID(intent_json)),
  CHECK (payload_sha256 REGEXP '^[a-f0-9]{64}$'),
  CHECK (intent_sha256 REGEXP '^[a-f0-9]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-108-1-update dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER imm_writer_1_update BEFORE UPDATE ON task_section_writer_object_intent FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IMMUTABLE_N04_RECEIPT';

--changeset medical-agent:1.0.0-108-1-delete dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER imm_writer_1_delete BEFORE DELETE ON task_section_writer_object_intent FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IMMUTABLE_N04_RECEIPT';

--changeset medical-agent:1.0.0-108-2 dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE task_section_writer_object_attempt (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  project_id CHAR(36) CHARACTER SET ascii NOT NULL,
  schema_version VARCHAR(16) NOT NULL,
  schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL,
  created_at DATETIME(6) NOT NULL,
  classification VARCHAR(32) NOT NULL,
  write_id CHAR(36) CHARACTER SET ascii NOT NULL,
  intent_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  PRIMARY KEY (tenant_id,write_id),
  FOREIGN KEY (tenant_id) REFERENCES tenant(tenant_id),
  FOREIGN KEY (tenant_id,project_id) REFERENCES project(tenant_id,project_id),
  FOREIGN KEY (tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
  CHECK (schema_version='1.0.0' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
  CHECK (classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH')),
  FOREIGN KEY (tenant_id,write_id) REFERENCES task_section_writer_object_intent(tenant_id,write_id),
  CHECK (intent_sha256 REGEXP '^[a-f0-9]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-108-2-update dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER imm_writer_2_update BEFORE UPDATE ON task_section_writer_object_attempt FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IMMUTABLE_N04_RECEIPT';

--changeset medical-agent:1.0.0-108-2-delete dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER imm_writer_2_delete BEFORE DELETE ON task_section_writer_object_attempt FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IMMUTABLE_N04_RECEIPT';

--changeset medical-agent:1.0.0-108-3 dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE task_section_writer_object (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  project_id CHAR(36) CHARACTER SET ascii NOT NULL,
  schema_version VARCHAR(16) NOT NULL,
  schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL,
  created_at DATETIME(6) NOT NULL,
  classification VARCHAR(32) NOT NULL,
  task_id CHAR(36) CHARACTER SET ascii NOT NULL,
  run_id CHAR(36) CHARACTER SET ascii NOT NULL,
  step_id CHAR(36) CHARACTER SET ascii NOT NULL,
  bound_action_id CHAR(36) CHARACTER SET ascii NULL,
  action_id CHAR(36) CHARACTER SET ascii NOT NULL,
  write_id CHAR(36) CHARACTER SET ascii NOT NULL,
  artifact_id CHAR(36) CHARACTER SET ascii NOT NULL,
  producer_rule_id CHAR(36) CHARACTER SET ascii NOT NULL,
  payload_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  receipt_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  canonical_payload LONGTEXT NOT NULL,
  audit_id CHAR(36) CHARACTER SET ascii NOT NULL,
  event_id CHAR(36) CHARACTER SET ascii NOT NULL,
  receipt_json LONGTEXT NOT NULL,
  kind VARCHAR(40) NOT NULL,
  state VARCHAR(16) NOT NULL CHECK (state='REGISTERED'),
  object_version VARCHAR(256) NOT NULL,
  PRIMARY KEY (tenant_id,artifact_id),
  UNIQUE KEY uq_writer_object_write (tenant_id,write_id),
  UNIQUE KEY uq_writer_object_stage_kind (tenant_id,run_id,step_id,kind),
  FOREIGN KEY (tenant_id) REFERENCES tenant(tenant_id),
  FOREIGN KEY (tenant_id,project_id) REFERENCES project(tenant_id,project_id),
  FOREIGN KEY (tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
  CHECK (schema_version='1.0.0' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
  CHECK (classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH')),
  FOREIGN KEY (tenant_id,project_id,task_id,run_id) REFERENCES task_run(tenant_id,project_id,task_id,run_id),
  FOREIGN KEY (tenant_id,run_id,step_id,bound_action_id) REFERENCES step_action_binding(tenant_id,run_id,step_id,action_id),
  CHECK ((kind='TASK_SECTION_WRITER_INPUT' AND bound_action_id IS NULL) OR (kind<>'TASK_SECTION_WRITER_INPUT' AND bound_action_id IS NOT NULL AND bound_action_id=action_id)),
  CHECK (kind IN ('TASK_SECTION_WRITER_INPUT','SECTION_BODY','SECTION_CANDIDATE','STEP_ENVELOPE')),
  FOREIGN KEY (tenant_id,write_id) REFERENCES task_section_writer_object_attempt(tenant_id,write_id),
  FOREIGN KEY (tenant_id,artifact_id) REFERENCES artifact(tenant_id,artifact_id),
  FOREIGN KEY (tenant_id,audit_id) REFERENCES audit_event(tenant_id,audit_id),
  FOREIGN KEY (tenant_id,event_id) REFERENCES outbox_event(tenant_id,event_id),
  CHECK (JSON_VALID(canonical_payload)),
  CHECK (JSON_VALID(receipt_json)),
  CHECK (payload_sha256 REGEXP '^[a-f0-9]{64}$'),
  CHECK (receipt_sha256 REGEXP '^[a-f0-9]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-108-3-update dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER imm_writer_3_update BEFORE UPDATE ON task_section_writer_object FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IMMUTABLE_N04_RECEIPT';

--changeset medical-agent:1.0.0-108-3-delete dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER imm_writer_3_delete BEFORE DELETE ON task_section_writer_object FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IMMUTABLE_N04_RECEIPT';

--changeset medical-agent:1.0.0-108-4 dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE task_section_writer_input (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  project_id CHAR(36) CHARACTER SET ascii NOT NULL,
  schema_version VARCHAR(16) NOT NULL,
  schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL,
  created_at DATETIME(6) NOT NULL,
  classification VARCHAR(32) NOT NULL,
  task_id CHAR(36) CHARACTER SET ascii NOT NULL,
  run_id CHAR(36) CHARACTER SET ascii NOT NULL,
  step_id CHAR(36) CHARACTER SET ascii NOT NULL,
  input_id CHAR(36) CHARACTER SET ascii NOT NULL,
  producer_rule_id CHAR(36) CHARACTER SET ascii NOT NULL,
  producer_decision_id CHAR(36) CHARACTER SET ascii NOT NULL,
  input_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  receipt_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  producer_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  producer_decision_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  canonical_input LONGTEXT NOT NULL,
  baseline_json LONGTEXT NOT NULL,
  audit_id CHAR(36) CHARACTER SET ascii NOT NULL,
  event_id CHAR(36) CHARACTER SET ascii NOT NULL,
  receipt_json LONGTEXT NOT NULL,
  producer_pins_json LONGTEXT NOT NULL,
  object_version VARCHAR(256) NOT NULL,
  PRIMARY KEY (tenant_id,input_id),
  UNIQUE KEY uq_writer_input_step (tenant_id,run_id,step_id),
  FOREIGN KEY (tenant_id) REFERENCES tenant(tenant_id),
  FOREIGN KEY (tenant_id,project_id) REFERENCES project(tenant_id,project_id),
  FOREIGN KEY (tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
  CHECK (schema_version='1.0.0' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
  CHECK (classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH')),
  FOREIGN KEY (tenant_id,project_id,task_id,run_id) REFERENCES task_run(tenant_id,project_id,task_id,run_id),
  FOREIGN KEY (tenant_id,input_id) REFERENCES task_section_writer_object(tenant_id,artifact_id),
  FOREIGN KEY (tenant_id,audit_id) REFERENCES audit_event(tenant_id,audit_id),
  FOREIGN KEY (tenant_id,event_id) REFERENCES outbox_event(tenant_id,event_id),
  CHECK (JSON_VALID(canonical_input)),
  CHECK (JSON_VALID(baseline_json)),
  CHECK (JSON_VALID(receipt_json)),
  CHECK (JSON_VALID(producer_pins_json)),
  CHECK (input_sha256 REGEXP '^[a-f0-9]{64}$'),
  CHECK (receipt_sha256 REGEXP '^[a-f0-9]{64}$'),
  CHECK (producer_sha256 REGEXP '^[a-f0-9]{64}$'),
  CHECK (producer_decision_sha256 REGEXP '^[a-f0-9]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-108-4-update dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER imm_writer_4_update BEFORE UPDATE ON task_section_writer_input FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IMMUTABLE_N04_RECEIPT';

--changeset medical-agent:1.0.0-108-4-delete dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER imm_writer_4_delete BEFORE DELETE ON task_section_writer_input FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IMMUTABLE_N04_RECEIPT';

--changeset medical-agent:1.0.0-108-5 dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE task_section_writer_submission (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  project_id CHAR(36) CHARACTER SET ascii NOT NULL,
  schema_version VARCHAR(16) NOT NULL,
  schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL,
  created_at DATETIME(6) NOT NULL,
  classification VARCHAR(32) NOT NULL,
  run_id CHAR(36) CHARACTER SET ascii NOT NULL,
  step_id CHAR(36) CHARACTER SET ascii NOT NULL,
  action_id CHAR(36) CHARACTER SET ascii NOT NULL,
  candidate_id CHAR(36) CHARACTER SET ascii NOT NULL,
  candidate_artifact_id CHAR(36) CHARACTER SET ascii NOT NULL,
  receipt_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  audit_id CHAR(36) CHARACTER SET ascii NOT NULL,
  event_id CHAR(36) CHARACTER SET ascii NOT NULL,
  receipt_json LONGTEXT NOT NULL,
  PRIMARY KEY (tenant_id,candidate_id),
  UNIQUE KEY uq_writer_submit_action (tenant_id,run_id,step_id,action_id),
  FOREIGN KEY (tenant_id) REFERENCES tenant(tenant_id),
  FOREIGN KEY (tenant_id,project_id) REFERENCES project(tenant_id,project_id),
  FOREIGN KEY (tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
  CHECK (schema_version='1.0.0' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
  CHECK (classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH')),
  FOREIGN KEY (tenant_id,project_id,run_id,step_id) REFERENCES run_step(tenant_id,project_id,run_id,step_id),
  FOREIGN KEY (tenant_id,run_id,step_id,action_id) REFERENCES step_action_binding(tenant_id,run_id,step_id,action_id),
  FOREIGN KEY (tenant_id,project_id,candidate_id) REFERENCES candidate(tenant_id,project_id,candidate_id),
  FOREIGN KEY (tenant_id,candidate_artifact_id) REFERENCES task_section_writer_object(tenant_id,artifact_id),
  FOREIGN KEY (tenant_id,audit_id) REFERENCES audit_event(tenant_id,audit_id),
  FOREIGN KEY (tenant_id,event_id) REFERENCES outbox_event(tenant_id,event_id),
  CHECK (JSON_VALID(receipt_json)),
  CHECK (receipt_sha256 REGEXP '^[a-f0-9]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-108-5-update dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER imm_writer_5_update BEFORE UPDATE ON task_section_writer_submission FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IMMUTABLE_N04_RECEIPT';

--changeset medical-agent:1.0.0-108-5-delete dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER imm_writer_5_delete BEFORE DELETE ON task_section_writer_submission FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IMMUTABLE_N04_RECEIPT';
