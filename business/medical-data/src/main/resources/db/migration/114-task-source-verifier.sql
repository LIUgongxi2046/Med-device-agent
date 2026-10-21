--liquibase formatted sql
-- Actual N05 input, physical object and candidate-only receipts. No adoption authority.

--changeset medical-agent:1.0.0-114-object-1 dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE task_source_verifier_object_intent (
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
  UNIQUE KEY uq_verifier_intent_stage_kind (tenant_id,run_id,step_id,kind),
  UNIQUE KEY uq_verifier_intent_artifact (tenant_id,artifact_id),
  FOREIGN KEY (tenant_id) REFERENCES tenant(tenant_id),
  FOREIGN KEY (tenant_id,project_id) REFERENCES project(tenant_id,project_id),
  FOREIGN KEY (tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
  CHECK (schema_version='1.0.0' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
  CHECK (classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH')),
  FOREIGN KEY (tenant_id,project_id,task_id,run_id) REFERENCES task_run(tenant_id,project_id,task_id,run_id),
  FOREIGN KEY (tenant_id,run_id,step_id,bound_action_id) REFERENCES step_action_binding(tenant_id,run_id,step_id,action_id),
  CHECK ((kind='TASK_SOURCE_VERIFIER_INPUT' AND bound_action_id IS NULL) OR (kind<>'TASK_SOURCE_VERIFIER_INPUT' AND bound_action_id IS NOT NULL AND bound_action_id=action_id)),
  CHECK (kind IN ('TASK_SOURCE_VERIFIER_INPUT','REVIEW_FINDINGS','REVIEW_COVERAGE','STEP_ENVELOPE')),
  CHECK (JSON_VALID(canonical_payload)),
  CHECK (JSON_VALID(producer_pins_json)),
  CHECK (JSON_VALID(intent_json)),
  CHECK (payload_sha256 REGEXP '^[a-f0-9]{64}$'),
  CHECK (intent_sha256 REGEXP '^[a-f0-9]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-114-object-1-update dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER imm_verifier_1_update BEFORE UPDATE ON task_source_verifier_object_intent FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IMMUTABLE_N05_RECEIPT';

--changeset medical-agent:1.0.0-114-object-1-delete dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER imm_verifier_1_delete BEFORE DELETE ON task_source_verifier_object_intent FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IMMUTABLE_N05_RECEIPT';

--changeset medical-agent:1.0.0-114-object-2 dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE task_source_verifier_object_attempt (
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
  FOREIGN KEY (tenant_id,write_id) REFERENCES task_source_verifier_object_intent(tenant_id,write_id),
  CHECK (intent_sha256 REGEXP '^[a-f0-9]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-114-object-2-update dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER imm_verifier_2_update BEFORE UPDATE ON task_source_verifier_object_attempt FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IMMUTABLE_N05_RECEIPT';

--changeset medical-agent:1.0.0-114-object-2-delete dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER imm_verifier_2_delete BEFORE DELETE ON task_source_verifier_object_attempt FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IMMUTABLE_N05_RECEIPT';

--changeset medical-agent:1.0.0-114-object-3 dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE task_source_verifier_object (
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
  UNIQUE KEY uq_verifier_object_write (tenant_id,write_id),
  UNIQUE KEY uq_verifier_object_stage_kind (tenant_id,run_id,step_id,kind),
  FOREIGN KEY (tenant_id) REFERENCES tenant(tenant_id),
  FOREIGN KEY (tenant_id,project_id) REFERENCES project(tenant_id,project_id),
  FOREIGN KEY (tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
  CHECK (schema_version='1.0.0' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
  CHECK (classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH')),
  FOREIGN KEY (tenant_id,project_id,task_id,run_id) REFERENCES task_run(tenant_id,project_id,task_id,run_id),
  FOREIGN KEY (tenant_id,run_id,step_id,bound_action_id) REFERENCES step_action_binding(tenant_id,run_id,step_id,action_id),
  CHECK ((kind='TASK_SOURCE_VERIFIER_INPUT' AND bound_action_id IS NULL) OR (kind<>'TASK_SOURCE_VERIFIER_INPUT' AND bound_action_id IS NOT NULL AND bound_action_id=action_id)),
  CHECK (kind IN ('TASK_SOURCE_VERIFIER_INPUT','REVIEW_FINDINGS','REVIEW_COVERAGE','STEP_ENVELOPE')),
  FOREIGN KEY (tenant_id,write_id) REFERENCES task_source_verifier_object_attempt(tenant_id,write_id),
  FOREIGN KEY (tenant_id,artifact_id) REFERENCES artifact(tenant_id,artifact_id),
  FOREIGN KEY (tenant_id,audit_id) REFERENCES audit_event(tenant_id,audit_id),
  FOREIGN KEY (tenant_id,event_id) REFERENCES outbox_event(tenant_id,event_id),
  CHECK (JSON_VALID(canonical_payload)),
  CHECK (JSON_VALID(receipt_json)),
  CHECK (payload_sha256 REGEXP '^[a-f0-9]{64}$'),
  CHECK (receipt_sha256 REGEXP '^[a-f0-9]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-114-object-3-update dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER imm_verifier_3_update BEFORE UPDATE ON task_source_verifier_object FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IMMUTABLE_N05_RECEIPT';

--changeset medical-agent:1.0.0-114-object-3-delete dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER imm_verifier_3_delete BEFORE DELETE ON task_source_verifier_object FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IMMUTABLE_N05_RECEIPT';

--changeset medical-agent:1.0.0-114-object-4 dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE task_source_verifier_input (
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
  rule_manifest_json LONGTEXT NOT NULL,
  audit_id CHAR(36) CHARACTER SET ascii NOT NULL,
  event_id CHAR(36) CHARACTER SET ascii NOT NULL,
  receipt_json LONGTEXT NOT NULL,
  producer_pins_json LONGTEXT NOT NULL,
  object_version VARCHAR(256) NOT NULL,
  PRIMARY KEY (tenant_id,input_id),
  UNIQUE KEY uq_verifier_input_step (tenant_id,run_id,step_id),
  FOREIGN KEY (tenant_id) REFERENCES tenant(tenant_id),
  FOREIGN KEY (tenant_id,project_id) REFERENCES project(tenant_id,project_id),
  FOREIGN KEY (tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
  CHECK (schema_version='1.0.0' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
  CHECK (classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH')),
  FOREIGN KEY (tenant_id,project_id,task_id,run_id) REFERENCES task_run(tenant_id,project_id,task_id,run_id),
  FOREIGN KEY (tenant_id,input_id) REFERENCES task_source_verifier_object(tenant_id,artifact_id),
  FOREIGN KEY (tenant_id,audit_id) REFERENCES audit_event(tenant_id,audit_id),
  FOREIGN KEY (tenant_id,event_id) REFERENCES outbox_event(tenant_id,event_id),
  CHECK (JSON_VALID(canonical_input)),
  CHECK (JSON_VALID(rule_manifest_json)),
  CHECK (JSON_VALID(receipt_json)),
  CHECK (JSON_VALID(producer_pins_json)),
  CHECK (input_sha256 REGEXP '^[a-f0-9]{64}$'),
  CHECK (receipt_sha256 REGEXP '^[a-f0-9]{64}$'),
  CHECK (producer_sha256 REGEXP '^[a-f0-9]{64}$'),
  CHECK (producer_decision_sha256 REGEXP '^[a-f0-9]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-114-object-4-update dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER imm_verifier_4_update BEFORE UPDATE ON task_source_verifier_input FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IMMUTABLE_N05_RECEIPT';

--changeset medical-agent:1.0.0-114-object-4-delete dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER imm_verifier_4_delete BEFORE DELETE ON task_source_verifier_input FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IMMUTABLE_N05_RECEIPT';



-- Additive bounded N05 producer governance. No 078/093 changes or seeded approval.
--changeset medical-agent:1.0.0-114-producer-task-section-verifier-producer-rule dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE task_source_verifier_producer_rule (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
 project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 task_id CHAR(36) CHARACTER SET ascii NOT NULL,
 rule_id CHAR(36) CHARACTER SET ascii NOT NULL,
 task_revision CHAR(36) CHARACTER SET ascii NOT NULL,
 task_input_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 plan_revision CHAR(36) CHARACTER SET ascii NOT NULL,
 plan_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 template_version CHAR(36) CHARACTER SET ascii NOT NULL,
 template_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 release_id CHAR(36) CHARACTER SET ascii NOT NULL,
 publication_approval_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 role_version CHAR(36) CHARACTER SET ascii NOT NULL,
 role_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 policy_version CHAR(36) CHARACTER SET ascii NOT NULL,
 policy_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 scope_snapshot CHAR(36) CHARACTER SET ascii NOT NULL,
 source_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 original_rule_id CHAR(36) CHARACTER SET ascii NOT NULL,
 original_rule_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 original_approval_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 budget_revision CHAR(36) CHARACTER SET ascii NOT NULL,
 maximum_bytes BIGINT UNSIGNED NOT NULL,
 maximum_artifacts BIGINT UNSIGNED NOT NULL,
 maximum_tokens BIGINT UNSIGNED NOT NULL,
 maximum_calls BIGINT UNSIGNED NOT NULL,
 maximum_amount DECIMAL(22,6) NOT NULL,
 currency CHAR(3) CHARACTER SET ascii NOT NULL,
 section_ids JSON NOT NULL,
 fixed_json JSON NOT NULL,
 content_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 request_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 idempotency_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 operation_id CHAR(36) CHARACTER SET ascii NOT NULL,
 audit_id CHAR(36) CHARACTER SET ascii NOT NULL,
 event_id CHAR(36) CHARACTER SET ascii NOT NULL,
 receipt_json JSON NOT NULL,
 authority_grant CHAR(36) CHARACTER SET ascii NOT NULL,
 authority_revision BIGINT UNSIGNED NOT NULL,
 auth_epoch BIGINT UNSIGNED NOT NULL,
 governance_epoch BIGINT UNSIGNED NOT NULL,
 schema_version VARCHAR(32) NOT NULL,
 schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL,
 created_at DATETIME(6) NOT NULL,
 classification VARCHAR(24) NOT NULL,
 PRIMARY KEY (tenant_id,project_id,task_id,rule_id),
 UNIQUE KEY uq_verifier_producer_id (tenant_id,rule_id),
 UNIQUE KEY uq_verifier_producer_digest (tenant_id,rule_id,content_sha256),
 UNIQUE KEY uq_verifier_producer_author_key (tenant_id,created_by,idempotency_key),
 FOREIGN KEY (tenant_id,rule_id,content_sha256) REFERENCES version_registry (tenant_id,version_id,content_sha256),
 FOREIGN KEY (tenant_id,project_id,task_id,task_revision,task_input_sha256) REFERENCES task_input_revision (tenant_id,project_id,task_id,task_revision,request_sha256),
 FOREIGN KEY (tenant_id,project_id,task_id,plan_revision,plan_sha256) REFERENCES plan_revision (tenant_id,project_id,task_id,plan_revision,content_sha256),
 FOREIGN KEY (tenant_id,template_version,template_sha256) REFERENCES template_version (tenant_id,template_version,content_sha256),
 FOREIGN KEY (tenant_id,release_id) REFERENCES release_record (tenant_id,release_id),
 FOREIGN KEY (tenant_id,role_version,role_sha256) REFERENCES version_registry (tenant_id,version_id,content_sha256),
 FOREIGN KEY (tenant_id,policy_version,policy_sha256) REFERENCES version_registry (tenant_id,version_id,content_sha256),
 FOREIGN KEY (tenant_id,operation_id) REFERENCES operation_record (tenant_id,operation_id),
 FOREIGN KEY (tenant_id,audit_id) REFERENCES audit_event (tenant_id,audit_id),
 FOREIGN KEY (tenant_id,event_id) REFERENCES outbox_event (tenant_id,event_id),
 FOREIGN KEY (tenant_id,authority_grant) REFERENCES permission_grant (tenant_id,grant_id),
 CHECK (authority_revision>0 AND auth_epoch>0 AND governance_epoch>0),
 FOREIGN KEY (tenant_id,created_by) REFERENCES tenant_member (tenant_id,user_id),
 CHECK (schema_version='1.0.0' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK (classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH')),
 CHECK (maximum_bytes>0 AND maximum_artifacts BETWEEN 1 AND 1024 AND maximum_tokens>0 AND maximum_calls>0 AND maximum_amount>=0),
 CHECK (JSON_TYPE(section_ids)='ARRAY' AND JSON_LENGTH(section_ids) BETWEEN 1 AND 256 AND JSON_TYPE(fixed_json)='OBJECT' AND JSON_TYPE(receipt_json)='OBJECT'),
 FOREIGN KEY (tenant_id,original_rule_id,original_rule_sha256) REFERENCES version_registry (tenant_id,version_id,content_sha256),
 FOREIGN KEY (tenant_id,budget_revision) REFERENCES version_registry (tenant_id,version_id),
 FOREIGN KEY (tenant_id,scope_snapshot) REFERENCES version_registry (tenant_id,version_id),
 CHECK (source_manifest_sha256 REGEXP '^[a-f0-9]{64}$' AND original_approval_sha256 REGEXP '^[a-f0-9]{64}$' AND content_sha256 REGEXP '^[a-f0-9]{64}$' AND request_sha256 REGEXP '^[a-f0-9]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-114-producer-task-section-verifier-producer-state dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE task_source_verifier_producer_state (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
 project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 task_id CHAR(36) CHARACTER SET ascii NOT NULL,
 rule_id CHAR(36) CHARACTER SET ascii NOT NULL,
 status VARCHAR(16) NOT NULL,
 revision BIGINT UNSIGNED NOT NULL,
 latest_decision_id CHAR(36) CHARACTER SET ascii NULL,
 updated_at DATETIME(6) NOT NULL,
 schema_version VARCHAR(32) NOT NULL,
 schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL,
 created_at DATETIME(6) NOT NULL,
 classification VARCHAR(24) NOT NULL,
 PRIMARY KEY (tenant_id,project_id,task_id,rule_id),
 FOREIGN KEY (tenant_id,project_id,task_id,rule_id) REFERENCES task_source_verifier_producer_rule (tenant_id,project_id,task_id,rule_id),
 FOREIGN KEY (tenant_id,created_by) REFERENCES tenant_member (tenant_id,user_id),
 CHECK (schema_version='1.0.0' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK (classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH')),
 CHECK (status IN ('DRAFT','APPROVED','DISABLED') AND revision>0 AND updated_at>=created_at),
 CHECK ((status='DRAFT' AND revision=1 AND latest_decision_id IS NULL) OR (status<>'DRAFT' AND revision>1 AND latest_decision_id IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-114-producer-task-section-verifier-producer-decision dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE task_source_verifier_producer_decision (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
 project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 task_id CHAR(36) CHARACTER SET ascii NOT NULL,
 rule_id CHAR(36) CHARACTER SET ascii NOT NULL,
 decision_id CHAR(36) CHARACTER SET ascii NOT NULL,
 decision_json JSON NOT NULL,
 content_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 request_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 idempotency_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 operation_id CHAR(36) CHARACTER SET ascii NOT NULL,
 audit_id CHAR(36) CHARACTER SET ascii NOT NULL,
 event_id CHAR(36) CHARACTER SET ascii NOT NULL,
 receipt_json JSON NOT NULL,
 expected_revision BIGINT UNSIGNED NOT NULL,
 decision VARCHAR(16) NOT NULL,
 authority_grant CHAR(36) CHARACTER SET ascii NOT NULL,
 authority_revision BIGINT UNSIGNED NOT NULL,
 auth_epoch BIGINT UNSIGNED NOT NULL,
 governance_epoch BIGINT UNSIGNED NOT NULL,
 schema_version VARCHAR(32) NOT NULL,
 schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL,
 created_at DATETIME(6) NOT NULL,
 classification VARCHAR(24) NOT NULL,
 PRIMARY KEY (tenant_id,project_id,task_id,rule_id,decision_id),
 UNIQUE KEY uq_verifier_producer_decision_id (tenant_id,decision_id),
 UNIQUE KEY uq_verifier_producer_decision_key (tenant_id,created_by,idempotency_key),
 UNIQUE KEY uq_verifier_producer_decision_revision (tenant_id,project_id,task_id,rule_id,expected_revision),
 FOREIGN KEY (tenant_id,project_id,task_id,rule_id) REFERENCES task_source_verifier_producer_rule (tenant_id,project_id,task_id,rule_id),
 FOREIGN KEY (tenant_id,operation_id) REFERENCES operation_record (tenant_id,operation_id),
 FOREIGN KEY (tenant_id,audit_id) REFERENCES audit_event (tenant_id,audit_id),
 FOREIGN KEY (tenant_id,event_id) REFERENCES outbox_event (tenant_id,event_id),
 FOREIGN KEY (tenant_id,authority_grant) REFERENCES permission_grant (tenant_id,grant_id),
 CHECK (authority_revision>0 AND auth_epoch>0 AND governance_epoch>0),
 FOREIGN KEY (tenant_id,created_by) REFERENCES tenant_member (tenant_id,user_id),
 CHECK (schema_version='1.0.0' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK (classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH')),
 CHECK (expected_revision>0 AND decision IN ('APPROVE','DISABLE') AND JSON_TYPE(decision_json)='OBJECT' AND JSON_TYPE(receipt_json)='OBJECT'),
 CHECK (content_sha256 REGEXP '^[a-f0-9]{64}$' AND request_sha256 REGEXP '^[a-f0-9]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-114-producer-owned-verifier-producer-decision dbms:mysql runInTransaction:false failOnError:true
ALTER TABLE task_source_verifier_producer_state ADD CONSTRAINT fk_verifier_producer_owned_decision
 FOREIGN KEY (tenant_id,project_id,task_id,rule_id,latest_decision_id)
 REFERENCES task_source_verifier_producer_decision (tenant_id,project_id,task_id,rule_id,decision_id);



-- Immutable real N05 public output family. Governance rules are not source-rights members.
--changeset medical-agent:1.0.0-114-task_source_verifier_rule_set dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE task_source_verifier_rule_set (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  project_id CHAR(36) CHARACTER SET ascii NOT NULL,
  revision_id CHAR(36) CHARACTER SET ascii NOT NULL,
  run_id CHAR(36) CHARACTER SET ascii NOT NULL,
  content_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  manifest_json JSON NOT NULL,
  receipt_json JSON NOT NULL,
  receipt_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  audit_id CHAR(36) CHARACTER SET ascii NOT NULL,
  event_id CHAR(36) CHARACTER SET ascii NOT NULL,
  schema_version VARCHAR(32) NOT NULL,
  schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL,
  created_at DATETIME(6) NOT NULL,
  classification VARCHAR(24) NOT NULL,
  PRIMARY KEY (tenant_id,revision_id),
  FOREIGN KEY (tenant_id,revision_id,content_sha256) REFERENCES version_registry(tenant_id,version_id,content_sha256),
  FOREIGN KEY (tenant_id,project_id) REFERENCES project(tenant_id,project_id),
  FOREIGN KEY (tenant_id,audit_id) REFERENCES audit_event(tenant_id,audit_id),
  FOREIGN KEY (tenant_id,event_id) REFERENCES outbox_event(tenant_id,event_id),
  FOREIGN KEY (tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
  CHECK (schema_version='1.0.0' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
  CHECK (receipt_sha256 REGEXP '^[a-f0-9]{64}$' AND JSON_TYPE(receipt_json)='OBJECT'),
  CHECK (classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;


--changeset medical-agent:1.0.0-114-submission dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE task_source_verifier_submission (
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
  check_id CHAR(36) CHARACTER SET ascii NOT NULL,
  candidate_id CHAR(36) CHARACTER SET ascii NOT NULL,
  findings_artifact_id CHAR(36) CHARACTER SET ascii NOT NULL,
  coverage_artifact_id CHAR(36) CHARACTER SET ascii NOT NULL,
  receipt_json LONGTEXT NOT NULL,
  receipt_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  audit_id CHAR(36) CHARACTER SET ascii NOT NULL,
  event_id CHAR(36) CHARACTER SET ascii NOT NULL,
  PRIMARY KEY (tenant_id,check_id),
  UNIQUE KEY uq_verifier_submission_action (tenant_id,run_id,step_id,action_id),
  FOREIGN KEY (tenant_id,run_id,step_id,action_id) REFERENCES step_action_binding(tenant_id,run_id,step_id,action_id),
  FOREIGN KEY (tenant_id,findings_artifact_id) REFERENCES artifact(tenant_id,artifact_id),
  FOREIGN KEY (tenant_id,coverage_artifact_id) REFERENCES artifact(tenant_id,artifact_id),
  FOREIGN KEY (tenant_id,audit_id) REFERENCES audit_event(tenant_id,audit_id),
  FOREIGN KEY (tenant_id,event_id) REFERENCES outbox_event(tenant_id,event_id),
  CHECK (JSON_VALID(receipt_json) AND receipt_sha256 REGEXP '^[a-f0-9]{64}$'),
  FOREIGN KEY (tenant_id,project_id,check_id) REFERENCES check_run(tenant_id,project_id,check_id),
  FOREIGN KEY (tenant_id,project_id,candidate_id) REFERENCES candidate(tenant_id,project_id,candidate_id),
  FOREIGN KEY (tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
  CHECK (schema_version='1.0.0' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
  CHECK (classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-114-issue_proposal dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE task_source_verifier_issue_proposal (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  project_id CHAR(36) CHARACTER SET ascii NOT NULL,
  schema_version VARCHAR(16) NOT NULL,
  schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL,
  created_at DATETIME(6) NOT NULL,
  classification VARCHAR(32) NOT NULL,
  proposal_id CHAR(36) CHARACTER SET ascii NOT NULL,
  check_id CHAR(36) CHARACTER SET ascii NOT NULL,
  candidate_id CHAR(36) CHARACTER SET ascii NOT NULL,
  target_revision CHAR(36) CHARACTER SET ascii NOT NULL,
  proposal_json LONGTEXT NOT NULL,
  proposal_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  status VARCHAR(16) NOT NULL,
  PRIMARY KEY (tenant_id,proposal_id),
  UNIQUE KEY uq_verifier_issue (tenant_id,check_id,proposal_sha256),
  FOREIGN KEY (tenant_id,project_id,candidate_id,target_revision) REFERENCES candidate(tenant_id,project_id,candidate_id,content_artifact_id),
  CHECK (status='OPEN' AND JSON_VALID(proposal_json) AND proposal_sha256 REGEXP '^[a-f0-9]{64}$'),
  FOREIGN KEY (tenant_id,project_id,check_id) REFERENCES check_run(tenant_id,project_id,check_id),
  FOREIGN KEY (tenant_id,project_id,candidate_id) REFERENCES candidate(tenant_id,project_id,candidate_id),
  FOREIGN KEY (tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
  CHECK (schema_version='1.0.0' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
  CHECK (classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-114-rule_set-update dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER imm_sv_rule_set_update BEFORE UPDATE ON task_source_verifier_rule_set FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IMMUTABLE_N05_RECEIPT';

--changeset medical-agent:1.0.0-114-rule_set-delete dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER imm_sv_rule_set_delete BEFORE DELETE ON task_source_verifier_rule_set FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IMMUTABLE_N05_RECEIPT';

--changeset medical-agent:1.0.0-114-submission-update dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER imm_sv_submission_update BEFORE UPDATE ON task_source_verifier_submission FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IMMUTABLE_N05_RECEIPT';

--changeset medical-agent:1.0.0-114-submission-delete dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER imm_sv_submission_delete BEFORE DELETE ON task_source_verifier_submission FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IMMUTABLE_N05_RECEIPT';

--changeset medical-agent:1.0.0-114-issue_proposal-update dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER imm_sv_issue_proposal_update BEFORE UPDATE ON task_source_verifier_issue_proposal FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IMMUTABLE_N05_RECEIPT';

--changeset medical-agent:1.0.0-114-issue_proposal-delete dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER imm_sv_issue_proposal_delete BEFORE DELETE ON task_source_verifier_issue_proposal FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IMMUTABLE_N05_RECEIPT';

-- Define ownership after producer tables exist; authorization still verifies exact hashes.
--changeset medical-agent:1.0.0-114-input-producer-ownership dbms:mysql runInTransaction:false failOnError:true
ALTER TABLE task_source_verifier_input
 ADD CONSTRAINT fk_verifier_input_producer FOREIGN KEY (tenant_id,project_id,task_id,producer_rule_id)
 REFERENCES task_source_verifier_producer_rule(tenant_id,project_id,task_id,rule_id),
 ADD CONSTRAINT fk_verifier_input_decision FOREIGN KEY (tenant_id,project_id,task_id,producer_rule_id,producer_decision_id)
 REFERENCES task_source_verifier_producer_decision(tenant_id,project_id,task_id,rule_id,decision_id);

--changeset medical-agent:1.0.0-114-rule-set-run-ownership dbms:mysql runInTransaction:false failOnError:true
ALTER TABLE task_source_verifier_rule_set
 ADD CONSTRAINT fk_verifier_rules_run FOREIGN KEY (tenant_id,project_id,run_id)
 REFERENCES task_run(tenant_id,project_id,run_id);
