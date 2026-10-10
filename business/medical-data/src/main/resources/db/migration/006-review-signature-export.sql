--liquibase formatted sql

--changeset medical-agent:1.0.0-073-candidate dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE candidate (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NOT NULL,
  project_key VARCHAR(36) CHARACTER SET ascii GENERATED ALWAYS AS (project_id) STORED,
  candidate_id CHAR(36) CHARACTER SET ascii NOT NULL,
  candidate_kind VARCHAR(16) NOT NULL,
  task_id CHAR(36) CHARACTER SET ascii NOT NULL, task_revision CHAR(36) CHARACTER SET ascii NOT NULL,
  template_version CHAR(36) CHARACTER SET ascii NOT NULL,
  run_id CHAR(36) CHARACTER SET ascii NOT NULL, step_id CHAR(36) CHARACTER SET ascii NOT NULL,
  action_id CHAR(36) CHARACTER SET ascii NOT NULL, lease_generation BIGINT UNSIGNED NOT NULL,
  base_document_revision CHAR(36) CHARACTER SET ascii NULL, input_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  snapshot_id CHAR(36) CHARACTER SET ascii NOT NULL, plan_revision CHAR(36) CHARACTER SET ascii NULL,
  target_sections JSON NOT NULL, target_node_ids JSON NOT NULL,
  content_ref JSON NOT NULL, content_artifact_id CHAR(36) CHARACTER SET ascii NOT NULL,
  evidence_refs JSON NOT NULL, original_evidence_artifact_id CHAR(36) CHARACTER SET ascii NULL,
  new_evidence_artifact_id CHAR(36) CHARACTER SET ascii NULL, issue_proposals JSON NOT NULL,
  dependency_access_set_id CHAR(36) CHARACTER SET ascii NOT NULL, content_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  status VARCHAR(24) NOT NULL,
  schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
  revision BIGINT UNSIGNED NOT NULL, updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (tenant_id, project_id, candidate_id),
  UNIQUE KEY uq_candidate_content (tenant_id, project_id, candidate_id, content_artifact_id),
  UNIQUE KEY uq_candidate_kind (tenant_id, project_id, candidate_id, candidate_kind),
  UNIQUE KEY uq_candidate_action (tenant_id, project_id, run_id, step_id, action_id),
  KEY ix_candidate_document (tenant_id, project_id, base_document_revision, status),
  FOREIGN KEY (tenant_id, project_key, run_id, step_id, action_id) REFERENCES step_action_binding (tenant_id, project_key, run_id, step_id, action_id),
  FOREIGN KEY (tenant_id, project_id, base_document_revision) REFERENCES document_revision_meta (tenant_id, project_id, document_revision),
  FOREIGN KEY (tenant_id, project_id, task_id, task_revision, template_version, snapshot_id)
    REFERENCES task_revision (tenant_id, project_id, task_id, task_revision, template_version, snapshot_id),
  FOREIGN KEY (tenant_id, project_id, task_id, task_revision, plan_revision, template_version, snapshot_id)
    REFERENCES plan_revision (tenant_id, project_id, task_id, task_revision, plan_revision, template_version, snapshot_id),
  FOREIGN KEY (tenant_id, project_key, content_artifact_id, content_sha256) REFERENCES artifact (tenant_id, project_key, artifact_id, sha256),
  FOREIGN KEY (tenant_id, project_key, original_evidence_artifact_id) REFERENCES artifact (tenant_id, project_key, artifact_id),
  FOREIGN KEY (tenant_id, project_key, new_evidence_artifact_id) REFERENCES artifact (tenant_id, project_key, artifact_id),
  FOREIGN KEY (tenant_id, project_key, dependency_access_set_id) REFERENCES dependency_access_set (tenant_id, project_key, access_set_id),
  FOREIGN KEY (tenant_id, candidate_id) REFERENCES version_registry (tenant_id, version_id),
  CHECK (status IN ('READY','ADOPTED','REJECTED','STALE')),
  CHECK (candidate_kind IN ('PLAN','DOCUMENT')),
  CHECK ((candidate_kind = 'PLAN' AND base_document_revision IS NULL AND plan_revision IS NULL)
    OR (candidate_kind = 'DOCUMENT' AND base_document_revision IS NOT NULL AND plan_revision IS NOT NULL
      AND original_evidence_artifact_id IS NOT NULL AND new_evidence_artifact_id IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-074-candidate-adoption dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE candidate_adoption (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NOT NULL,
  adoption_id CHAR(36) CHARACTER SET ascii NOT NULL, candidate_id CHAR(36) CHARACTER SET ascii NOT NULL,
  candidate_kind VARCHAR(16) NOT NULL,
  operation_id CHAR(36) CHARACTER SET ascii NOT NULL, selected_nodes JSON NOT NULL,
  result_document_revision CHAR(36) CHARACTER SET ascii NULL, result_plan_revision CHAR(36) CHARACTER SET ascii NULL,
  actor_id CHAR(36) CHARACTER SET ascii NOT NULL,
  schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
  PRIMARY KEY (tenant_id, project_id, adoption_id),
  UNIQUE KEY uq_adoption_request (tenant_id, project_id, operation_id),
  UNIQUE KEY uq_adoption_result (tenant_id, project_id, result_document_revision),
  UNIQUE KEY uq_adoption_plan (tenant_id, project_id, result_plan_revision),
  FOREIGN KEY (tenant_id, project_id, candidate_id, candidate_kind) REFERENCES candidate (tenant_id, project_id, candidate_id, candidate_kind),
  FOREIGN KEY (tenant_id, project_id, result_document_revision) REFERENCES document_revision_meta (tenant_id, project_id, document_revision),
  FOREIGN KEY (tenant_id, project_id, result_plan_revision) REFERENCES plan_revision (tenant_id, project_id, plan_revision),
  FOREIGN KEY (tenant_id, operation_id) REFERENCES operation_record (tenant_id, operation_id),
  FOREIGN KEY (tenant_id, actor_id) REFERENCES tenant_member (tenant_id, user_id),
  CHECK ((candidate_kind = 'PLAN' AND result_plan_revision IS NOT NULL AND result_document_revision IS NULL)
    OR (candidate_kind = 'DOCUMENT' AND result_document_revision IS NOT NULL AND result_plan_revision IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-075-check-run dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE check_run (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NOT NULL,
  project_key VARCHAR(36) CHARACTER SET ascii GENERATED ALWAYS AS (project_id) STORED,
  check_id CHAR(36) CHARACTER SET ascii NOT NULL, target_kind VARCHAR(16) NOT NULL,
  target_id CHAR(36) CHARACTER SET ascii NOT NULL, target_revision CHAR(36) CHARACTER SET ascii NOT NULL,
  document_id CHAR(36) CHARACTER SET ascii GENERATED ALWAYS AS (CASE WHEN target_kind = 'DOCUMENT' THEN target_id ELSE NULL END) STORED,
  document_revision CHAR(36) CHARACTER SET ascii GENERATED ALWAYS AS (CASE WHEN target_kind = 'DOCUMENT' THEN target_revision ELSE NULL END) STORED,
  candidate_id CHAR(36) CHARACTER SET ascii GENERATED ALWAYS AS (CASE WHEN target_kind = 'CANDIDATE' THEN target_id ELSE NULL END) STORED,
  candidate_content_revision CHAR(36) CHARACTER SET ascii GENERATED ALWAYS AS (CASE WHEN target_kind = 'CANDIDATE' THEN target_revision ELSE NULL END) STORED,
  input_sha256 CHAR(64) CHARACTER SET ascii NOT NULL, scope_manifest_id CHAR(36) CHARACTER SET ascii NOT NULL,
  rule_manifest_id CHAR(36) CHARACTER SET ascii NOT NULL, rule_manifest JSON NOT NULL, input_watermarks JSON NOT NULL,
  evidence_watermark BIGINT UNSIGNED NOT NULL, issue_watermark BIGINT UNSIGNED NOT NULL,
  coverage_artifact_id CHAR(36) CHARACTER SET ascii NULL, findings_artifact_id CHAR(36) CHARACTER SET ascii NULL,
  result_ref JSON NULL, invalidation_reason VARCHAR(64) NULL,
  run_id CHAR(36) CHARACTER SET ascii NULL, step_id CHAR(36) CHARACTER SET ascii NULL, action_id CHAR(36) CHARACTER SET ascii NULL,
  status VARCHAR(24) NOT NULL,
  schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
  revision BIGINT UNSIGNED NOT NULL, updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (tenant_id, project_id, check_id),
  UNIQUE KEY uq_check_input (tenant_id, project_id, target_kind, target_revision, rule_manifest_id, input_sha256),
  UNIQUE KEY uq_check_document (tenant_id, project_id, check_id, document_revision),
  KEY ix_check_status (tenant_id, project_id, status, created_at),
  FOREIGN KEY (tenant_id, project_id, document_id, document_revision) REFERENCES document_revision_meta (tenant_id, project_id, document_id, document_revision),
  FOREIGN KEY (tenant_id, project_id, candidate_id, candidate_content_revision) REFERENCES candidate (tenant_id, project_id, candidate_id, content_artifact_id),
  FOREIGN KEY (tenant_id, scope_manifest_id) REFERENCES version_registry (tenant_id, version_id),
  FOREIGN KEY (tenant_id, rule_manifest_id) REFERENCES version_registry (tenant_id, version_id),
  FOREIGN KEY (tenant_id, project_key, coverage_artifact_id) REFERENCES artifact (tenant_id, project_key, artifact_id),
  FOREIGN KEY (tenant_id, project_key, findings_artifact_id) REFERENCES artifact (tenant_id, project_key, artifact_id),
  FOREIGN KEY (tenant_id, project_key, run_id, step_id, action_id) REFERENCES step_action_binding (tenant_id, project_key, run_id, step_id, action_id),
  CHECK (target_kind IN ('DOCUMENT','CANDIDATE')),
  CHECK (status IN ('QUEUED','RUNNING','SUCCEEDED','WAITING_INPUT','FAILED','CANCELLED')),
  CHECK (status <> 'SUCCEEDED' OR (coverage_artifact_id IS NOT NULL AND findings_artifact_id IS NOT NULL AND result_ref IS NOT NULL)),
  CHECK ((run_id IS NULL AND step_id IS NULL AND action_id IS NULL) OR (run_id IS NOT NULL AND step_id IS NOT NULL AND action_id IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-076-issue dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE issue (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NOT NULL,
  issue_id CHAR(36) CHARACTER SET ascii NOT NULL, check_id CHAR(36) CHARACTER SET ascii NULL,
  document_revision CHAR(36) CHARACTER SET ascii NOT NULL,
  position JSON NOT NULL, severity VARCHAR(24) NOT NULL, code VARCHAR(64) NOT NULL,
  finding_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  status VARCHAR(24) NOT NULL, assignee_id CHAR(36) CHARACTER SET ascii NULL, resolution_ref JSON NULL,
  schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
  revision BIGINT UNSIGNED NOT NULL, updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (tenant_id, project_id, issue_id),
  UNIQUE KEY uq_issue_finding (tenant_id, project_id, document_revision, finding_sha256),
  KEY ix_issue_document_status (tenant_id, project_id, document_revision, status, severity, issue_id),
  FOREIGN KEY (tenant_id, project_id, document_revision) REFERENCES document_revision_meta (tenant_id, project_id, document_revision),
  FOREIGN KEY (tenant_id, project_id, check_id, document_revision) REFERENCES check_run (tenant_id, project_id, check_id, document_revision),
  FOREIGN KEY (tenant_id, assignee_id) REFERENCES tenant_member (tenant_id, user_id),
  CHECK (status IN ('OPEN','IN_PROGRESS','RESOLVED','REOPENED')),
  CHECK (status <> 'RESOLVED' OR resolution_ref IS NOT NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-077-annotation dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE annotation (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NOT NULL,
  project_key VARCHAR(36) CHARACTER SET ascii GENERATED ALWAYS AS (project_id) STORED,
  annotation_id CHAR(36) CHARACTER SET ascii NOT NULL, document_revision CHAR(36) CHARACTER SET ascii NOT NULL,
  node_id CHAR(36) CHARACTER SET ascii NOT NULL, selection JSON NOT NULL,
  selection_artifact_id CHAR(36) CHARACTER SET ascii NOT NULL, quoted_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  status VARCHAR(24) NOT NULL, content TEXT NOT NULL, actor_id CHAR(36) CHARACTER SET ascii NOT NULL,
  schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
  revision BIGINT UNSIGNED NOT NULL, updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (tenant_id, project_id, annotation_id),
  KEY ix_annotation_document_status (tenant_id, project_id, document_revision, status, created_at),
  FOREIGN KEY (tenant_id, project_id, document_revision, node_id) REFERENCES document_node (tenant_id, project_id, document_revision, node_id),
  FOREIGN KEY (tenant_id, project_key, selection_artifact_id) REFERENCES artifact (tenant_id, project_key, artifact_id),
  FOREIGN KEY (tenant_id, actor_id) REFERENCES tenant_member (tenant_id, user_id),
  CHECK (status IN ('OPEN','RESOLVED','RETURNED','REANCHOR_REQUIRED')),
  CHECK (CHAR_LENGTH(content) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-078-annotation-reply dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE annotation_reply (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NOT NULL,
  reply_id CHAR(36) CHARACTER SET ascii NOT NULL, annotation_id CHAR(36) CHARACTER SET ascii NOT NULL,
  content TEXT NOT NULL, actor_id CHAR(36) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
  PRIMARY KEY (tenant_id, project_id, reply_id),
  KEY ix_reply_annotation (tenant_id, project_id, annotation_id, created_at),
  FOREIGN KEY (tenant_id, project_id, annotation_id) REFERENCES annotation (tenant_id, project_id, annotation_id),
  FOREIGN KEY (tenant_id, actor_id) REFERENCES tenant_member (tenant_id, user_id),
  CHECK (CHAR_LENGTH(content) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-079-evidence-review-batch dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE evidence_review_batch (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NOT NULL,
  batch_id CHAR(36) CHARACTER SET ascii NOT NULL, document_revision CHAR(36) CHARACTER SET ascii NOT NULL,
  check_manifest_id CHAR(36) CHARACTER SET ascii NOT NULL, check_ids JSON NOT NULL,
  evidence_watermark BIGINT UNSIGNED NOT NULL, issue_watermark BIGINT UNSIGNED NOT NULL,
  expected_revision BIGINT UNSIGNED NOT NULL, actor_id CHAR(36) CHARACTER SET ascii NOT NULL, decided_at DATETIME(6) NOT NULL,
  operation_id CHAR(36) CHARACTER SET ascii NOT NULL,
  schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
  PRIMARY KEY (tenant_id, project_id, batch_id),
  UNIQUE KEY uq_evidence_batch_document (tenant_id, project_id, batch_id, document_revision),
  UNIQUE KEY uq_evidence_batch_request (tenant_id, operation_id),
  FOREIGN KEY (tenant_id, project_id, document_revision) REFERENCES document_revision_meta (tenant_id, project_id, document_revision),
  FOREIGN KEY (tenant_id, check_manifest_id) REFERENCES version_registry (tenant_id, version_id),
  FOREIGN KEY (tenant_id, actor_id) REFERENCES tenant_member (tenant_id, user_id),
  FOREIGN KEY (tenant_id, operation_id) REFERENCES operation_record (tenant_id, operation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-080-evidence-document-key dbms:mysql runInTransaction:false failOnError:true
ALTER TABLE evidence_link ADD UNIQUE KEY uq_evidence_document (tenant_id, project_id, document_revision, evidence_id);

--changeset medical-agent:1.0.0-081-evidence-review-decision dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE evidence_review_decision (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NOT NULL,
  project_key VARCHAR(36) CHARACTER SET ascii GENERATED ALWAYS AS (project_id) STORED,
  decision_id CHAR(36) CHARACTER SET ascii NOT NULL, batch_id CHAR(36) CHARACTER SET ascii NOT NULL,
  document_revision CHAR(36) CHARACTER SET ascii NOT NULL, evidence_link_id CHAR(36) CHARACTER SET ascii NOT NULL,
  selection_artifact_id CHAR(36) CHARACTER SET ascii NOT NULL,
  access VARCHAR(24) NOT NULL, location VARCHAR(24) NOT NULL, support VARCHAR(24) NOT NULL,
  reason TEXT NOT NULL, rule_revision CHAR(36) CHARACTER SET ascii NULL,
  check_manifest_id CHAR(36) CHARACTER SET ascii NOT NULL,
  evidence_watermark BIGINT UNSIGNED NOT NULL, issue_watermark BIGINT UNSIGNED NOT NULL,
  validity VARCHAR(24) NOT NULL, invalidation_reason VARCHAR(64) NULL,
  actor_id CHAR(36) CHARACTER SET ascii NOT NULL, decided_at DATETIME(6) NOT NULL,
  schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
  revision BIGINT UNSIGNED NOT NULL, updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (tenant_id, project_id, decision_id),
  UNIQUE KEY uq_evidence_batch_link (tenant_id, project_id, batch_id, evidence_link_id),
  KEY ix_evidence_decision_history (tenant_id, project_id, document_revision, evidence_link_id, created_at),
  FOREIGN KEY (tenant_id, project_id, batch_id, document_revision) REFERENCES evidence_review_batch (tenant_id, project_id, batch_id, document_revision),
  FOREIGN KEY (tenant_id, project_id, document_revision, evidence_link_id) REFERENCES evidence_link (tenant_id, project_id, document_revision, evidence_id),
  FOREIGN KEY (tenant_id, project_key, selection_artifact_id) REFERENCES artifact (tenant_id, project_key, artifact_id),
  FOREIGN KEY (tenant_id, rule_revision) REFERENCES rule_version (tenant_id, rule_version),
  FOREIGN KEY (tenant_id, check_manifest_id) REFERENCES version_registry (tenant_id, version_id),
  FOREIGN KEY (tenant_id, actor_id) REFERENCES tenant_member (tenant_id, user_id),
  CHECK (access IN ('PASS','FAIL') AND location IN ('PASS','FAIL')),
  CHECK (support IN ('SUPPORTED','CONTRADICTED','INSUFFICIENT','NOT_APPLICABLE')),
  CHECK (validity IN ('VALID','INVALIDATED')),
  CHECK ((validity = 'VALID' AND invalidation_reason IS NULL) OR (validity = 'INVALIDATED' AND invalidation_reason IS NOT NULL)),
  CHECK (CHAR_LENGTH(reason) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-082-review-batch dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE review_batch (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NOT NULL,
  batch_id CHAR(36) CHARACTER SET ascii NOT NULL, document_revision CHAR(36) CHARACTER SET ascii NOT NULL,
  baseline_manifest JSON NOT NULL, check_ids JSON NOT NULL,
  evidence_watermark BIGINT UNSIGNED NOT NULL, issue_watermark BIGINT UNSIGNED NOT NULL,
  expected_revision BIGINT UNSIGNED NOT NULL, actor_id CHAR(36) CHARACTER SET ascii NOT NULL, decided_at DATETIME(6) NOT NULL,
  operation_id CHAR(36) CHARACTER SET ascii NOT NULL,
  schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
  PRIMARY KEY (tenant_id, project_id, batch_id),
  UNIQUE KEY uq_review_batch_document (tenant_id, project_id, batch_id, document_revision),
  UNIQUE KEY uq_review_batch_request (tenant_id, operation_id),
  FOREIGN KEY (tenant_id, project_id, document_revision) REFERENCES document_revision_meta (tenant_id, project_id, document_revision),
  FOREIGN KEY (tenant_id, actor_id) REFERENCES tenant_member (tenant_id, user_id),
  FOREIGN KEY (tenant_id, operation_id) REFERENCES operation_record (tenant_id, operation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-083-review-batch-item dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE review_batch_item (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NOT NULL,
  project_key VARCHAR(36) CHARACTER SET ascii GENERATED ALWAYS AS (project_id) STORED,
  batch_id CHAR(36) CHARACTER SET ascii NOT NULL, document_revision CHAR(36) CHARACTER SET ascii NOT NULL,
  target_node_id CHAR(36) CHARACTER SET ascii NULL, section_id CHAR(36) CHARACTER SET ascii NULL,
  section_revision CHAR(36) CHARACTER SET ascii NULL, selection_artifact_id CHAR(36) CHARACTER SET ascii NULL,
  target_key VARCHAR(64) GENERATED ALWAYS AS (CASE WHEN target_node_id IS NOT NULL THEN CONCAT('NODE:', target_node_id)
    WHEN section_revision IS NOT NULL THEN CONCAT('SECTION:', section_revision) ELSE 'DOCUMENT' END) STORED,
  decision VARCHAR(16) NOT NULL, assignee_id CHAR(36) CHARACTER SET ascii NULL, reason TEXT NOT NULL,
  evidence_watermark BIGINT UNSIGNED NOT NULL, issue_watermark BIGINT UNSIGNED NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
  PRIMARY KEY (tenant_id, project_id, batch_id, target_key),
  UNIQUE KEY uq_review_item_document (tenant_id, project_id, batch_id, target_key, document_revision),
  FOREIGN KEY (tenant_id, project_id, batch_id, document_revision) REFERENCES review_batch (tenant_id, project_id, batch_id, document_revision),
  FOREIGN KEY (tenant_id, project_id, document_revision, target_node_id) REFERENCES document_node (tenant_id, project_id, document_revision, node_id),
  FOREIGN KEY (tenant_id, project_id, document_revision, section_id, section_revision) REFERENCES document_section (tenant_id, project_id, document_revision, section_id, section_revision),
  FOREIGN KEY (tenant_id, project_key, selection_artifact_id) REFERENCES artifact (tenant_id, project_key, artifact_id),
  FOREIGN KEY (tenant_id, assignee_id) REFERENCES tenant_member (tenant_id, user_id),
  CHECK (decision IN ('CONFIRM','RETURN')),
  CHECK ((section_id IS NULL AND section_revision IS NULL) OR (section_id IS NOT NULL AND section_revision IS NOT NULL)),
  CHECK (decision <> 'RETURN' OR assignee_id IS NOT NULL),
  CHECK (CHAR_LENGTH(reason) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-084-review-decision dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE review_decision (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NOT NULL,
  review_id CHAR(36) CHARACTER SET ascii NOT NULL, document_revision CHAR(36) CHARACTER SET ascii NOT NULL,
  batch_id CHAR(36) CHARACTER SET ascii NOT NULL, target_key VARCHAR(64) NOT NULL,
  section_id CHAR(36) CHARACTER SET ascii NULL, section_revision CHAR(36) CHARACTER SET ascii NULL,
  decision VARCHAR(24) NOT NULL, baseline_manifest JSON NOT NULL,
  actor_id CHAR(36) CHARACTER SET ascii NOT NULL, reason TEXT NOT NULL, decided_at DATETIME(6) NOT NULL,
  schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
  PRIMARY KEY (tenant_id, project_id, review_id),
  UNIQUE KEY uq_review_decision_target (tenant_id, project_id, batch_id, target_key),
  KEY ix_review_document (tenant_id, project_id, document_revision, decided_at),
  FOREIGN KEY (tenant_id, project_id, batch_id, target_key, document_revision) REFERENCES review_batch_item (tenant_id, project_id, batch_id, target_key, document_revision),
  FOREIGN KEY (tenant_id, project_id, document_revision, section_id, section_revision) REFERENCES document_section (tenant_id, project_id, document_revision, section_id, section_revision),
  FOREIGN KEY (tenant_id, actor_id) REFERENCES tenant_member (tenant_id, user_id),
  CHECK (decision IN ('CONFIRM','RETURN')),
  CHECK ((section_id IS NULL AND section_revision IS NULL) OR (section_id IS NOT NULL AND section_revision IS NOT NULL)),
  CHECK (CHAR_LENGTH(reason) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-085-review-invalidation dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE review_invalidation (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NOT NULL,
  invalidation_id CHAR(36) CHARACTER SET ascii NOT NULL, review_id CHAR(36) CHARACTER SET ascii NOT NULL,
  trigger_version CHAR(36) CHARACTER SET ascii NOT NULL, reason_code VARCHAR(64) NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
  PRIMARY KEY (tenant_id, project_id, invalidation_id),
  UNIQUE KEY uq_review_invalidation_trigger (tenant_id, project_id, review_id, trigger_version, reason_code),
  FOREIGN KEY (tenant_id, project_id, review_id) REFERENCES review_decision (tenant_id, project_id, review_id),
  FOREIGN KEY (tenant_id, trigger_version) REFERENCES version_registry (tenant_id, version_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-086-reauthentication-challenge dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE reauthentication_challenge (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NOT NULL,
  challenge_id CHAR(36) CHARACTER SET ascii NOT NULL, subject_id CHAR(36) CHARACTER SET ascii NOT NULL,
  document_id CHAR(36) CHARACTER SET ascii NOT NULL, document_revision CHAR(36) CHARACTER SET ascii NOT NULL,
  purpose VARCHAR(64) NOT NULL, policy_revision CHAR(36) CHARACTER SET ascii NOT NULL,
  state_hash CHAR(64) CHARACTER SET ascii NOT NULL, nonce_hash CHAR(64) CHARACTER SET ascii NOT NULL,
  pkce_encrypted_reference_id CHAR(36) CHARACTER SET ascii NOT NULL,
  callback_receipt_id CHAR(36) CHARACTER SET ascii NULL,
  expires_at DATETIME(6) NOT NULL, consumed_at DATETIME(6) NULL, status VARCHAR(24) NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
  revision BIGINT UNSIGNED NOT NULL, updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (tenant_id, project_id, challenge_id),
  UNIQUE KEY uq_reauthentication_state (tenant_id, state_hash),
  UNIQUE KEY uq_reauthentication_callback (tenant_id, callback_receipt_id),
  UNIQUE KEY uq_reauthentication_challenge_callback (tenant_id, project_id, challenge_id, callback_receipt_id),
  UNIQUE KEY uq_reauthentication_challenge_binding (tenant_id, project_id, challenge_id, subject_id, document_id, document_revision, purpose, policy_revision),
  KEY ix_reauthentication_subject (tenant_id, project_id, subject_id, status, expires_at),
  FOREIGN KEY (tenant_id, subject_id) REFERENCES tenant_member (tenant_id, user_id),
  FOREIGN KEY (tenant_id, project_id, document_id, document_revision) REFERENCES document_revision_meta (tenant_id, project_id, document_id, document_revision),
  FOREIGN KEY (tenant_id, policy_revision) REFERENCES version_registry (tenant_id, version_id),
  CHECK (expires_at > created_at),
  CHECK (consumed_at IS NULL OR consumed_at <= expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-087-reauthentication-receipt dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE reauthentication_receipt (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NOT NULL,
  reauthentication_ref CHAR(36) CHARACTER SET ascii NOT NULL, challenge_id CHAR(36) CHARACTER SET ascii NOT NULL,
  subject_id CHAR(36) CHARACTER SET ascii NOT NULL, document_id CHAR(36) CHARACTER SET ascii NOT NULL,
  document_revision CHAR(36) CHARACTER SET ascii NOT NULL, purpose VARCHAR(64) NOT NULL,
  policy_revision CHAR(36) CHARACTER SET ascii NOT NULL, callback_receipt_id CHAR(36) CHARACTER SET ascii NOT NULL,
  provider_receipt_ref JSON NOT NULL, expires_at DATETIME(6) NOT NULL, consumed_at DATETIME(6) NULL, status VARCHAR(24) NOT NULL,
  schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
  revision BIGINT UNSIGNED NOT NULL, updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (tenant_id, project_id, reauthentication_ref),
  UNIQUE KEY uq_reauthentication_receipt_callback (tenant_id, callback_receipt_id),
  UNIQUE KEY uq_reauthentication_receipt_challenge (tenant_id, project_id, challenge_id),
  UNIQUE KEY uq_reauthentication_signing_binding (tenant_id, project_id, reauthentication_ref, subject_id, document_id, document_revision, purpose, policy_revision),
  KEY ix_reauthentication_receipt_subject (tenant_id, project_id, subject_id, status, expires_at),
  FOREIGN KEY (tenant_id, project_id, challenge_id, subject_id, document_id, document_revision, purpose, policy_revision)
    REFERENCES reauthentication_challenge (tenant_id, project_id, challenge_id, subject_id, document_id, document_revision, purpose, policy_revision),
  FOREIGN KEY (tenant_id, project_id, challenge_id, callback_receipt_id)
    REFERENCES reauthentication_challenge (tenant_id, project_id, challenge_id, callback_receipt_id),
  CHECK (expires_at > created_at),
  CHECK (consumed_at IS NULL OR consumed_at <= expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-088-signature-record dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE signature_record (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NOT NULL,
  signature_id CHAR(36) CHARACTER SET ascii NOT NULL, document_id CHAR(36) CHARACTER SET ascii NOT NULL,
  document_revision CHAR(36) CHARACTER SET ascii NOT NULL, purpose VARCHAR(64) NOT NULL,
  signer_id CHAR(36) CHARACTER SET ascii NOT NULL, policy_revision CHAR(36) CHARACTER SET ascii NOT NULL,
  reauthentication_ref CHAR(36) CHARACTER SET ascii NOT NULL,
  manifest JSON NOT NULL, manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL, content_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  auth_epoch BIGINT UNSIGNED NOT NULL, governance_epoch BIGINT UNSIGNED NOT NULL, signed_at DATETIME(6) NOT NULL,
  schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
  PRIMARY KEY (tenant_id, project_id, signature_id),
  UNIQUE KEY uq_signature_purpose (tenant_id, project_id, document_revision, purpose),
  UNIQUE KEY uq_signature_document (tenant_id, project_id, signature_id, document_revision),
  UNIQUE KEY uq_signature_reauthentication (tenant_id, project_id, reauthentication_ref),
  FOREIGN KEY (tenant_id, project_id, document_id, document_revision) REFERENCES document_revision_meta (tenant_id, project_id, document_id, document_revision),
  FOREIGN KEY (tenant_id, project_id, reauthentication_ref, signer_id, document_id, document_revision, purpose, policy_revision)
    REFERENCES reauthentication_receipt (tenant_id, project_id, reauthentication_ref, subject_id, document_id, document_revision, purpose, policy_revision),
  FOREIGN KEY (tenant_id, signature_id) REFERENCES version_registry (tenant_id, version_id),
  CHECK (content_sha256 = manifest_sha256)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-089-export-manifest dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE export_manifest (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NOT NULL,
  project_key VARCHAR(36) CHARACTER SET ascii GENERATED ALWAYS AS (project_id) STORED,
  export_id CHAR(36) CHARACTER SET ascii NOT NULL, document_revision CHAR(36) CHARACTER SET ascii NOT NULL,
  purpose VARCHAR(32) NOT NULL, manifest JSON NOT NULL, manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  content_sha256 CHAR(64) CHARACTER SET ascii NOT NULL, status VARCHAR(24) NOT NULL,
  signature_id CHAR(36) CHARACTER SET ascii NULL, operation_id CHAR(36) CHARACTER SET ascii NOT NULL,
  template_version CHAR(36) CHARACTER SET ascii NOT NULL, dependency_access_set_id CHAR(36) CHARACTER SET ascii NOT NULL,
  review_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  format_profile_revision CHAR(36) CHARACTER SET ascii NOT NULL, channel_profile_revision CHAR(36) CHARACTER SET ascii NULL,
  schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
  revision BIGINT UNSIGNED NOT NULL, updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (tenant_id, project_id, export_id),
  UNIQUE KEY uq_export_request (tenant_id, operation_id),
  KEY ix_export_document_status (tenant_id, project_id, document_revision, status, created_at),
  FOREIGN KEY (tenant_id, project_id, document_revision, template_version) REFERENCES document_revision_meta (tenant_id, project_id, document_revision, template_version),
  FOREIGN KEY (tenant_id, project_id, signature_id, document_revision) REFERENCES signature_record (tenant_id, project_id, signature_id, document_revision),
  FOREIGN KEY (tenant_id, project_key, dependency_access_set_id) REFERENCES dependency_access_set (tenant_id, project_key, access_set_id),
  FOREIGN KEY (tenant_id, operation_id) REFERENCES operation_record (tenant_id, operation_id),
  FOREIGN KEY (tenant_id, format_profile_revision) REFERENCES version_registry (tenant_id, version_id),
  FOREIGN KEY (tenant_id, channel_profile_revision) REFERENCES version_registry (tenant_id, version_id),
  FOREIGN KEY (tenant_id, export_id) REFERENCES version_registry (tenant_id, version_id),
  CHECK (purpose IN ('FORMAL_BODY','REVIEW_PACKAGE','INTERNAL_EXCHANGE')),
  CHECK (status IN ('QUEUED','RUNNING','PARTIAL_FAILED','SUCCEEDED','FAILED','CANCEL_REQUESTED','CANCELLED')),
  CHECK (content_sha256 = manifest_sha256)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-090-export-item dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE export_item (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NOT NULL,
  project_key VARCHAR(36) CHARACTER SET ascii GENERATED ALWAYS AS (project_id) STORED,
  export_item_id CHAR(36) CHARACTER SET ascii NOT NULL, export_id CHAR(36) CHARACTER SET ascii NOT NULL,
  format VARCHAR(16) NOT NULL, attempt_no BIGINT UNSIGNED NOT NULL, status VARCHAR(24) NOT NULL,
  object_ref JSON NULL, artifact_id CHAR(36) CHARACTER SET ascii NULL,
  validation_ref JSON NULL, validation_artifact_id CHAR(36) CHARACTER SET ascii NULL,
  converter_revision VARCHAR(128) NOT NULL, font_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
  revision BIGINT UNSIGNED NOT NULL, updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (tenant_id, project_id, export_item_id),
  UNIQUE KEY uq_export_format_attempt (tenant_id, project_id, export_id, format, attempt_no),
  FOREIGN KEY (tenant_id, project_id, export_id) REFERENCES export_manifest (tenant_id, project_id, export_id),
  FOREIGN KEY (tenant_id, project_key, artifact_id) REFERENCES artifact (tenant_id, project_key, artifact_id),
  FOREIGN KEY (tenant_id, project_key, validation_artifact_id) REFERENCES artifact (tenant_id, project_key, artifact_id),
  CHECK (format IN ('DOCX','PDF','MARKDOWN')),
  CHECK (attempt_no > 0),
  CHECK (status IN ('QUEUED','RUNNING','VERIFYING','SUCCEEDED','FAILED','CANCEL_REQUESTED','CANCELLED')),
  CHECK (status <> 'SUCCEEDED' OR (object_ref IS NOT NULL AND artifact_id IS NOT NULL AND validation_ref IS NOT NULL AND validation_artifact_id IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-091-task-active-run dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE task_active_run (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NOT NULL,
  task_id CHAR(36) CHARACTER SET ascii NOT NULL, run_id CHAR(36) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
  revision BIGINT UNSIGNED NOT NULL, updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (tenant_id, project_id, task_id),
  UNIQUE KEY uq_task_active_run (tenant_id, project_id, run_id),
  FOREIGN KEY (tenant_id, project_id, task_id, run_id) REFERENCES task_run (tenant_id, project_id, task_id, run_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
