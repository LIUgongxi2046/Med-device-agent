--liquibase formatted sql

--changeset medical-agent:1.0.0-131-conversation dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE conversation (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NOT NULL,
  conversation_id CHAR(36) CHARACTER SET ascii NOT NULL, owner_id CHAR(36) CHARACTER SET ascii NOT NULL,
  document_id CHAR(36) CHARACTER SET ascii NULL, status VARCHAR(24) NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
  revision BIGINT UNSIGNED NOT NULL, updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (tenant_id, conversation_id),
  UNIQUE KEY uq_conversation_project (tenant_id, project_id, conversation_id),
  KEY ix_conversation_owner (tenant_id, project_id, owner_id, status, updated_at),
  FOREIGN KEY (tenant_id, project_id) REFERENCES project (tenant_id, project_id),
  FOREIGN KEY (tenant_id, owner_id) REFERENCES tenant_member (tenant_id, user_id),
  FOREIGN KEY (tenant_id, project_id, document_id) REFERENCES document_head (tenant_id, project_id, document_id),
  CHECK (status IN ('ACTIVE','ARCHIVED','DELETED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-132-conversation-message dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE conversation_message (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NOT NULL,
  project_key VARCHAR(36) CHARACTER SET ascii GENERATED ALWAYS AS (project_id) STORED,
  message_id CHAR(36) CHARACTER SET ascii NOT NULL, conversation_id CHAR(36) CHARACTER SET ascii NOT NULL,
  scope_snapshot_id CHAR(36) CHARACTER SET ascii NOT NULL, document_revision CHAR(36) CHARACTER SET ascii NULL,
  selection JSON NULL, content_ref JSON NOT NULL, sender_kind VARCHAR(16) NOT NULL,
  run_id CHAR(36) CHARACTER SET ascii NULL, operation_id CHAR(36) CHARACTER SET ascii NULL,
  candidate_ids JSON NOT NULL, dependency_access_set_id CHAR(36) CHARACTER SET ascii NOT NULL,
  content_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
  PRIMARY KEY (tenant_id, message_id),
  UNIQUE KEY uq_message_conversation (tenant_id, project_id, conversation_id, message_id),
  KEY ix_conversation_messages (tenant_id, project_id, conversation_id, created_at, message_id),
  KEY ix_message_run (tenant_id, project_id, run_id),
  FOREIGN KEY (tenant_id, project_id, conversation_id) REFERENCES conversation (tenant_id, project_id, conversation_id),
  FOREIGN KEY (tenant_id, project_id, scope_snapshot_id) REFERENCES scope_snapshot (tenant_id, project_id, snapshot_id),
  FOREIGN KEY (tenant_id, project_id, document_revision) REFERENCES document_revision_meta (tenant_id, project_id, document_revision),
  FOREIGN KEY (tenant_id, project_key, dependency_access_set_id) REFERENCES dependency_access_set (tenant_id, project_key, access_set_id),
  FOREIGN KEY (tenant_id, project_id, run_id) REFERENCES execution_registry (tenant_id, project_id, run_id),
  FOREIGN KEY (tenant_id, operation_id) REFERENCES operation_record (tenant_id, operation_id),
  CHECK (sender_kind IN ('USER','AGENT','SYSTEM')),
  CHECK (selection IS NULL OR document_revision IS NOT NULL),
  CHECK (JSON_TYPE(candidate_ids) = 'ARRAY')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-133-pending-action dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE pending_action (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NULL,
  action_id CHAR(36) CHARACTER SET ascii NOT NULL, conversation_id CHAR(36) CHARACTER SET ascii NULL,
  target JSON NOT NULL, arguments_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  expected_revision BIGINT UNSIGNED NOT NULL, status VARCHAR(24) NOT NULL, expires_at DATETIME(6) NOT NULL,
  decision_id CHAR(36) CHARACTER SET ascii NULL, decided_at DATETIME(6) NULL, actor_id CHAR(36) CHARACTER SET ascii NULL,
  schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
  revision BIGINT UNSIGNED NOT NULL, updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (tenant_id, action_id),
  KEY ix_pending_action_expiry (tenant_id, project_id, status, expires_at),
  KEY ix_pending_conversation (tenant_id, project_id, conversation_id, status),
  FOREIGN KEY (tenant_id) REFERENCES tenant (tenant_id),
  FOREIGN KEY (tenant_id, project_id) REFERENCES project (tenant_id, project_id),
  FOREIGN KEY (tenant_id, project_id, conversation_id) REFERENCES conversation (tenant_id, project_id, conversation_id),
  FOREIGN KEY (tenant_id, decision_id) REFERENCES version_registry (tenant_id, version_id),
  FOREIGN KEY (tenant_id, actor_id) REFERENCES tenant_member (tenant_id, user_id),
  CHECK (status IN ('PENDING','CONFIRMED','REJECTED','EXPIRED','STALE')),
  CHECK (conversation_id IS NULL OR project_id IS NOT NULL),
  CHECK (expires_at > created_at),
  CHECK ((decision_id IS NULL AND decided_at IS NULL AND actor_id IS NULL)
      OR (decision_id IS NOT NULL AND decided_at IS NOT NULL AND actor_id IS NOT NULL)),
  CHECK (status NOT IN ('CONFIRMED','REJECTED') OR decision_id IS NOT NULL),
  CHECK (status <> 'CONFIRMED' OR decided_at < expires_at),
  CHECK (decided_at IS NULL OR decided_at >= created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-134-user-preference dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE user_preference (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NULL,
  preference_id CHAR(36) CHARACTER SET ascii NOT NULL, user_id CHAR(36) CHARACTER SET ascii NOT NULL,
  content TEXT NOT NULL, status VARCHAR(24) NOT NULL, disabled_at DATETIME(6) NULL,
  automatic_learning_enabled BOOLEAN NOT NULL DEFAULT FALSE,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
  revision BIGINT UNSIGNED NOT NULL, updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (tenant_id, preference_id),
  KEY ix_preference_user (tenant_id, user_id, status, project_id),
  FOREIGN KEY (tenant_id, user_id) REFERENCES tenant_member (tenant_id, user_id),
  FOREIGN KEY (tenant_id, project_id) REFERENCES project (tenant_id, project_id),
  CHECK (automatic_learning_enabled IN (0,1)),
  CHECK (disabled_at IS NULL OR disabled_at >= created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-135-task-summary dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE task_summary (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NULL,
  project_key VARCHAR(36) CHARACTER SET ascii GENERATED ALWAYS AS (COALESCE(project_id, 'ENTERPRISE')) STORED,
  summary_id CHAR(36) CHARACTER SET ascii NOT NULL, run_id CHAR(36) CHARACTER SET ascii NOT NULL,
  source_message_ids JSON NOT NULL, source_refs JSON NOT NULL, content_ref JSON NOT NULL, unresolved_items JSON NOT NULL,
  dependency_access_set_id CHAR(36) CHARACTER SET ascii NOT NULL, content_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
  PRIMARY KEY (tenant_id, summary_id),
  KEY ix_summary_run (tenant_id, project_id, run_id, created_at),
  FOREIGN KEY (tenant_id, project_key, run_id) REFERENCES execution_registry (tenant_id, project_key, run_id),
  FOREIGN KEY (tenant_id, project_key, dependency_access_set_id) REFERENCES dependency_access_set (tenant_id, project_key, access_set_id),
  FOREIGN KEY (tenant_id, summary_id) REFERENCES version_registry (tenant_id, version_id),
  CHECK (JSON_TYPE(source_message_ids) = 'ARRAY'),
  CHECK (JSON_TYPE(unresolved_items) = 'ARRAY')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-136-outbox-delivery dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE outbox_delivery (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, event_id CHAR(36) CHARACTER SET ascii NOT NULL,
  consumer_id VARCHAR(64) NOT NULL, status VARCHAR(24) NOT NULL, attempt_no BIGINT UNSIGNED NOT NULL,
  next_attempt_at DATETIME(6) NOT NULL, lease_owner VARCHAR(128) NULL, lease_until DATETIME(6) NULL,
  lease_generation BIGINT UNSIGNED NOT NULL, last_error VARCHAR(64) NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
  revision BIGINT UNSIGNED NOT NULL, updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (tenant_id, event_id, consumer_id),
  KEY ix_delivery_due (status, next_attempt_at, lease_until, tenant_id, event_id),
  FOREIGN KEY (tenant_id, event_id) REFERENCES outbox_event (tenant_id, event_id),
  CHECK ((lease_owner IS NULL AND lease_until IS NULL) OR (lease_owner IS NOT NULL AND lease_until IS NOT NULL)),
  CHECK (lease_owner IS NULL OR lease_generation > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-137-consumer-receipt dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE consumer_receipt (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, event_id CHAR(36) CHARACTER SET ascii NOT NULL,
  consumer_id VARCHAR(64) NOT NULL, result_ref JSON NOT NULL, input_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  received_at DATETIME(6) NOT NULL,
  schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
  PRIMARY KEY (tenant_id, event_id, consumer_id),
  FOREIGN KEY (tenant_id, event_id, consumer_id) REFERENCES outbox_delivery (tenant_id, event_id, consumer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-138-waiting-item dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE waiting_item (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NULL,
  project_key VARCHAR(36) CHARACTER SET ascii GENERATED ALWAYS AS (COALESCE(project_id, 'ENTERPRISE')) STORED,
  waiting_item_id CHAR(36) CHARACTER SET ascii NOT NULL,
  run_id CHAR(36) CHARACTER SET ascii NOT NULL, step_id CHAR(36) CHARACTER SET ascii NOT NULL, action_id CHAR(36) CHARACTER SET ascii NOT NULL,
  target_revision CHAR(36) CHARACTER SET ascii NOT NULL, reason_code VARCHAR(64) NOT NULL, question TEXT NOT NULL,
  requirement_refs JSON NOT NULL, allowed_resolution_actions JSON NOT NULL, assignee_id CHAR(36) CHARACTER SET ascii NOT NULL,
  status VARCHAR(24) NOT NULL, resolution_id CHAR(36) CHARACTER SET ascii NULL,
  schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
  revision BIGINT UNSIGNED NOT NULL, updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (tenant_id, waiting_item_id),
  UNIQUE KEY uq_waiting_action (tenant_id, run_id, step_id, action_id),
  UNIQUE KEY uq_waiting_scope (tenant_id, project_key, waiting_item_id),
  KEY ix_waiting_assignee (tenant_id, assignee_id, status, created_at),
  FOREIGN KEY (tenant_id, project_key, run_id, step_id, action_id) REFERENCES step_action_binding (tenant_id, project_key, run_id, step_id, action_id),
  FOREIGN KEY (tenant_id, target_revision) REFERENCES version_registry (tenant_id, version_id),
  FOREIGN KEY (tenant_id, assignee_id) REFERENCES tenant_member (tenant_id, user_id),
  FOREIGN KEY (tenant_id, resolution_id) REFERENCES version_registry (tenant_id, version_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-139-notification dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE notification (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NULL,
  project_key VARCHAR(36) CHARACTER SET ascii GENERATED ALWAYS AS (COALESCE(project_id, 'ENTERPRISE')) STORED,
  notification_id CHAR(36) CHARACTER SET ascii NOT NULL, event_id CHAR(36) CHARACTER SET ascii NOT NULL,
  recipient_id CHAR(36) CHARACTER SET ascii NOT NULL, resource_ref JSON NOT NULL, read_at DATETIME(6) NULL,
  schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
  revision BIGINT UNSIGNED NOT NULL, updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (tenant_id, notification_id),
  UNIQUE KEY uq_notification_recipient (tenant_id, event_id, recipient_id),
  UNIQUE KEY uq_notification_scope (tenant_id, project_key, notification_id),
  KEY ix_notification_inbox (tenant_id, recipient_id, read_at, created_at),
  FOREIGN KEY (tenant_id, project_id) REFERENCES project (tenant_id, project_id),
  FOREIGN KEY (tenant_id, event_id) REFERENCES outbox_event (tenant_id, event_id),
  FOREIGN KEY (tenant_id, recipient_id) REFERENCES tenant_member (tenant_id, user_id),
  CHECK (read_at IS NULL OR read_at >= created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-140-stream-registry dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE stream_registry (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NULL,
  project_key VARCHAR(36) CHARACTER SET ascii GENERATED ALWAYS AS (COALESCE(project_id, 'ENTERPRISE')) STORED,
  stream_id CHAR(36) CHARACTER SET ascii NOT NULL, kind VARCHAR(24) NOT NULL, owner_resource_id CHAR(36) CHARACTER SET ascii NOT NULL,
  owner_run_id CHAR(36) CHARACTER SET ascii GENERATED ALWAYS AS (CASE WHEN kind = 'RUN' THEN owner_resource_id ELSE NULL END) STORED,
  owner_conversation_id CHAR(36) CHARACTER SET ascii GENERATED ALWAYS AS (CASE WHEN kind = 'CONVERSATION' THEN owner_resource_id ELSE NULL END) STORED,
  last_sequence BIGINT UNSIGNED NOT NULL, retention_policy_revision CHAR(36) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
  revision BIGINT UNSIGNED NOT NULL, updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (tenant_id, stream_id),
  UNIQUE KEY uq_stream_owner (tenant_id, kind, owner_resource_id),
  UNIQUE KEY uq_stream_scope (tenant_id, project_key, stream_id),
  FOREIGN KEY (tenant_id, project_key, owner_run_id) REFERENCES execution_registry (tenant_id, project_key, run_id),
  FOREIGN KEY (tenant_id, project_id, owner_conversation_id) REFERENCES conversation (tenant_id, project_id, conversation_id),
  FOREIGN KEY (tenant_id, project_id) REFERENCES project (tenant_id, project_id),
  FOREIGN KEY (tenant_id, retention_policy_revision) REFERENCES version_registry (tenant_id, version_id),
  CHECK (kind IN ('RUN','CONVERSATION')),
  CHECK (kind <> 'CONVERSATION' OR project_id IS NOT NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-141-stream-event dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE stream_event (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NULL,
  project_key VARCHAR(36) CHARACTER SET ascii GENERATED ALWAYS AS (COALESCE(project_id, 'ENTERPRISE')) STORED,
  event_id CHAR(36) CHARACTER SET ascii NOT NULL, stream_id CHAR(36) CHARACTER SET ascii NOT NULL, sequence BIGINT UNSIGNED NOT NULL,
  task_id CHAR(36) CHARACTER SET ascii NULL, run_id CHAR(36) CHARACTER SET ascii NULL,
  resource_kind VARCHAR(32) NOT NULL, resource_id CHAR(36) CHARACTER SET ascii NOT NULL, resource_revision BIGINT UNSIGNED NOT NULL,
  changed_run_id CHAR(36) CHARACTER SET ascii GENERATED ALWAYS AS (CASE WHEN resource_kind = 'RUN' THEN resource_id ELSE NULL END) STORED,
  changed_step_id CHAR(36) CHARACTER SET ascii GENERATED ALWAYS AS (CASE WHEN resource_kind = 'STEP' THEN resource_id ELSE NULL END) STORED,
  changed_waiting_item_id CHAR(36) CHARACTER SET ascii GENERATED ALWAYS AS (CASE WHEN resource_kind = 'WAITING_ITEM' THEN resource_id ELSE NULL END) STORED,
  changed_candidate_id CHAR(36) CHARACTER SET ascii GENERATED ALWAYS AS (CASE WHEN resource_kind = 'CANDIDATE' THEN resource_id ELSE NULL END) STORED,
  changed_document_id CHAR(36) CHARACTER SET ascii GENERATED ALWAYS AS (CASE WHEN resource_kind = 'DOCUMENT' THEN resource_id ELSE NULL END) STORED,
  changed_source_version CHAR(36) CHARACTER SET ascii GENERATED ALWAYS AS (CASE WHEN resource_kind = 'SOURCE_VERSION' THEN resource_id ELSE NULL END) STORED,
  changed_export_id CHAR(36) CHARACTER SET ascii GENERATED ALWAYS AS (CASE WHEN resource_kind = 'EXPORT' THEN resource_id ELSE NULL END) STORED,
  changed_notification_id CHAR(36) CHARACTER SET ascii GENERATED ALWAYS AS (CASE WHEN resource_kind = 'NOTIFICATION' THEN resource_id ELSE NULL END) STORED,
  event_type VARCHAR(64) NOT NULL, payload JSON NOT NULL, occurred_at DATETIME(6) NOT NULL,
  schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
  PRIMARY KEY (tenant_id, event_id),
  UNIQUE KEY uq_stream_sequence (tenant_id, stream_id, sequence),
  KEY ix_stream_resource (tenant_id, resource_kind, resource_id, resource_revision),
  FOREIGN KEY (tenant_id, event_id) REFERENCES outbox_event (tenant_id, event_id),
  FOREIGN KEY (tenant_id, project_key, stream_id) REFERENCES stream_registry (tenant_id, project_key, stream_id),
  FOREIGN KEY (tenant_id, project_id, task_id) REFERENCES task_head (tenant_id, project_id, task_id),
  FOREIGN KEY (tenant_id, project_key, run_id) REFERENCES execution_registry (tenant_id, project_key, run_id),
  FOREIGN KEY (tenant_id, project_id, task_id, run_id) REFERENCES task_run (tenant_id, project_id, task_id, run_id),
  FOREIGN KEY (tenant_id, project_key, changed_run_id) REFERENCES execution_registry (tenant_id, project_key, run_id),
  FOREIGN KEY (tenant_id, project_key, run_id, changed_step_id) REFERENCES run_step (tenant_id, project_key, run_id, step_id),
  FOREIGN KEY (tenant_id, project_key, changed_waiting_item_id) REFERENCES waiting_item (tenant_id, project_key, waiting_item_id),
  FOREIGN KEY (tenant_id, project_id, changed_candidate_id) REFERENCES candidate (tenant_id, project_id, candidate_id),
  FOREIGN KEY (tenant_id, project_id, changed_document_id) REFERENCES document_head (tenant_id, project_id, document_id),
  FOREIGN KEY (tenant_id, changed_source_version) REFERENCES source_version (tenant_id, source_version),
  FOREIGN KEY (tenant_id, project_id, changed_export_id) REFERENCES export_manifest (tenant_id, project_id, export_id),
  FOREIGN KEY (tenant_id, project_key, changed_notification_id) REFERENCES notification (tenant_id, project_key, notification_id),
  CHECK (sequence > 0),
  CHECK (task_id IS NULL OR project_id IS NOT NULL),
  CHECK (resource_kind NOT IN ('CANDIDATE','DOCUMENT','EXPORT') OR project_id IS NOT NULL),
  CHECK (resource_kind <> 'STEP' OR run_id IS NOT NULL),
  CHECK (resource_kind <> 'RUN' OR (run_id IS NOT NULL AND run_id = resource_id)),
  CHECK ((event_type = 'run.updated' AND resource_kind = 'RUN')
      OR (event_type = 'step.updated' AND resource_kind = 'STEP')
      OR (event_type = 'waiting_input.created' AND resource_kind = 'WAITING_ITEM')
      OR (event_type = 'candidate.updated' AND resource_kind = 'CANDIDATE')
      OR (event_type IN ('document.revised','review.invalidated') AND resource_kind = 'DOCUMENT')
      OR (event_type = 'source.governance_changed' AND resource_kind = 'SOURCE_VERSION')
      OR (event_type = 'export.updated' AND resource_kind = 'EXPORT')
      OR (event_type = 'notification.created' AND resource_kind = 'NOTIFICATION')),
  CHECK (schema_version = '1.0.0')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-142-audit-event dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE audit_event (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NULL,
  audit_id CHAR(36) CHARACTER SET ascii NOT NULL, subject_id CHAR(36) CHARACTER SET ascii NOT NULL,
  action VARCHAR(64) NOT NULL, resource_ref JSON NOT NULL, result VARCHAR(24) NOT NULL, reason_code VARCHAR(64) NULL,
  request_id CHAR(36) CHARACTER SET ascii NOT NULL, trace_id VARCHAR(64) NOT NULL,
  schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
  PRIMARY KEY (tenant_id, audit_id),
  KEY ix_audit_subject (tenant_id, subject_id, created_at, audit_id),
  KEY ix_audit_request (tenant_id, request_id, trace_id),
  FOREIGN KEY (tenant_id) REFERENCES tenant (tenant_id),
  FOREIGN KEY (tenant_id, project_id) REFERENCES project (tenant_id, project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-143-artifact-upload-grant dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE artifact_upload_grant (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NULL,
  project_key VARCHAR(36) CHARACTER SET ascii GENERATED ALWAYS AS (COALESCE(project_id, 'ENTERPRISE')) STORED,
  grant_id CHAR(36) CHARACTER SET ascii NOT NULL, subject_id CHAR(36) CHARACTER SET ascii NOT NULL, purpose VARCHAR(32) NOT NULL,
  document_id CHAR(36) CHARACTER SET ascii NULL, run_id CHAR(36) CHARACTER SET ascii NULL, step_id CHAR(36) CHARACTER SET ascii NULL,
  object_key VARCHAR(1024) NOT NULL, object_version VARCHAR(256) NULL, object_identity_sha256 CHAR(64) CHARACTER SET ascii NULL,
  sha256 CHAR(64) CHARACTER SET ascii NOT NULL, size_bytes BIGINT UNSIGNED NOT NULL, media_type VARCHAR(128) NOT NULL,
  schema_ref VARCHAR(512) NOT NULL, expires_at DATETIME(6) NOT NULL, status VARCHAR(24) NOT NULL, artifact_id CHAR(36) CHARACTER SET ascii NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
  revision BIGINT UNSIGNED NOT NULL, updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (tenant_id, grant_id),
  UNIQUE KEY uq_grant_object_identity (tenant_id, object_identity_sha256),
  UNIQUE KEY uq_grant_completed_artifact (tenant_id, artifact_id),
  KEY ix_grant_subject_expiry (tenant_id, subject_id, status, expires_at),
  FOREIGN KEY (tenant_id, subject_id) REFERENCES tenant_member (tenant_id, user_id),
  FOREIGN KEY (tenant_id, project_id) REFERENCES project (tenant_id, project_id),
  FOREIGN KEY (tenant_id, project_id, document_id) REFERENCES document_head (tenant_id, project_id, document_id),
  FOREIGN KEY (tenant_id, project_key, run_id) REFERENCES execution_registry (tenant_id, project_key, run_id),
  FOREIGN KEY (tenant_id, project_key, run_id, step_id) REFERENCES run_step (tenant_id, project_key, run_id, step_id),
  FOREIGN KEY (tenant_id, project_key, artifact_id, sha256, object_version) REFERENCES artifact (tenant_id, project_key, artifact_id, sha256, object_version),
  CHECK (document_id IS NULL OR project_id IS NOT NULL),
  CHECK (step_id IS NULL OR run_id IS NOT NULL),
  CHECK (size_bytes > 0 AND expires_at > created_at),
  CHECK ((object_version IS NULL AND object_identity_sha256 IS NULL) OR (object_version IS NOT NULL AND object_identity_sha256 IS NOT NULL)),
  CHECK (artifact_id IS NULL OR object_version IS NOT NULL),
  CHECK (status IN ('ISSUED','UPLOADING','COMPLETED','EXPIRED','REVOKED','FAILED')),
  CHECK (status <> 'COMPLETED' OR artifact_id IS NOT NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-144-content-read-receipt dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE content_read_receipt (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NULL,
  project_key VARCHAR(36) CHARACTER SET ascii GENERATED ALWAYS AS (COALESCE(project_id, 'ENTERPRISE')) STORED,
  read_receipt_id CHAR(36) CHARACTER SET ascii NOT NULL, resource_id CHAR(36) CHARACTER SET ascii NOT NULL,
  artifact_id CHAR(36) CHARACTER SET ascii NOT NULL, subject_id CHAR(36) CHARACTER SET ascii NOT NULL,
  run_id CHAR(36) CHARACTER SET ascii NULL, step_id CHAR(36) CHARACTER SET ascii NULL, lease_generation BIGINT UNSIGNED NULL,
  selection_sha256 CHAR(64) CHARACTER SET ascii NOT NULL, delivered_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  content_sha256 CHAR(64) CHARACTER SET ascii NOT NULL, auth_epoch BIGINT UNSIGNED NOT NULL, governance_epoch BIGINT UNSIGNED NOT NULL,
  read_at DATETIME(6) NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
  PRIMARY KEY (tenant_id, read_receipt_id),
  KEY ix_content_read_action (tenant_id, run_id, step_id, resource_id, read_at),
  FOREIGN KEY (tenant_id, project_key, artifact_id) REFERENCES artifact (tenant_id, project_key, artifact_id),
  FOREIGN KEY (tenant_id, project_key, run_id) REFERENCES execution_registry (tenant_id, project_key, run_id),
  FOREIGN KEY (tenant_id, project_key, run_id, step_id) REFERENCES run_step (tenant_id, project_key, run_id, step_id),
  FOREIGN KEY (tenant_id, project_id) REFERENCES project (tenant_id, project_id),
  CHECK ((step_id IS NULL AND lease_generation IS NULL)
      OR (run_id IS NOT NULL AND step_id IS NOT NULL AND lease_generation IS NOT NULL AND lease_generation > 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-145-management-binding-release dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE management_binding_release (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NULL,
  project_key VARCHAR(36) CHARACTER SET ascii GENERATED ALWAYS AS (COALESCE(project_id, 'ENTERPRISE')) STORED,
  release_id CHAR(36) CHARACTER SET ascii NOT NULL, environment_id CHAR(36) CHARACTER SET ascii NOT NULL,
  source_commit VARCHAR(64) NOT NULL, manifest_artifact_id CHAR(36) CHARACTER SET ascii NOT NULL,
  manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL, approval_id CHAR(36) CHARACTER SET ascii NOT NULL, status VARCHAR(24) NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
  revision BIGINT UNSIGNED NOT NULL, updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (tenant_id, release_id),
  UNIQUE KEY uq_management_environment_release (tenant_id, environment_id, release_id),
  KEY ix_management_environment_status (tenant_id, environment_id, status, created_at),
  FOREIGN KEY (tenant_id, project_key, manifest_artifact_id, manifest_sha256) REFERENCES artifact (tenant_id, project_key, artifact_id, sha256),
  FOREIGN KEY (tenant_id, approval_id) REFERENCES version_registry (tenant_id, version_id),
  FOREIGN KEY (tenant_id, release_id) REFERENCES version_registry (tenant_id, version_id),
  CHECK (source_commit REGEXP '^([0-9a-f]{40}|[0-9a-f]{64})$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-146-report-export dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE report_export (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NULL,
  project_key VARCHAR(36) CHARACTER SET ascii GENERATED ALWAYS AS (COALESCE(project_id, 'ENTERPRISE')) STORED,
  report_id CHAR(36) CHARACTER SET ascii NOT NULL, kind VARCHAR(24) NOT NULL, filters JSON NOT NULL,
  filter_sha256 CHAR(64) CHARACTER SET ascii NOT NULL, dataset_watermark BIGINT UNSIGNED NOT NULL,
  metric_revision CHAR(36) CHARACTER SET ascii NOT NULL, manifest_artifact_id CHAR(36) CHARACTER SET ascii NULL,
  status VARCHAR(24) NOT NULL, operation_id CHAR(36) CHARACTER SET ascii NOT NULL, format VARCHAR(16) NOT NULL,
  record_count BIGINT UNSIGNED NULL, export_item_ids JSON NOT NULL, error_code VARCHAR(64) NULL,
  schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
  revision BIGINT UNSIGNED NOT NULL, updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (tenant_id, report_id),
  UNIQUE KEY uq_report_operation (tenant_id, operation_id),
  KEY ix_report_owner_status (tenant_id, created_by, status, created_at),
  FOREIGN KEY (tenant_id, project_id) REFERENCES project (tenant_id, project_id),
  FOREIGN KEY (tenant_id, project_key, manifest_artifact_id) REFERENCES artifact (tenant_id, project_key, artifact_id),
  FOREIGN KEY (tenant_id, metric_revision) REFERENCES version_registry (tenant_id, version_id),
  FOREIGN KEY (tenant_id, operation_id) REFERENCES operation_record (tenant_id, operation_id),
  CHECK (kind IN ('USAGE','AUDIT','EVALUATION')),
  CHECK (format IN ('CSV','XLSX')),
  CHECK (status IN ('QUEUED','RUNNING','SUCCEEDED','FAILED','CANCELLED')),
  CHECK (status <> 'SUCCEEDED' OR (manifest_artifact_id IS NOT NULL AND record_count IS NOT NULL)),
  CHECK (JSON_TYPE(export_item_ids) = 'ARRAY')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-147-source-session-conversation dbms:mysql runInTransaction:false failOnError:true
ALTER TABLE source_session_binding ADD CONSTRAINT fk_source_session_conversation
  FOREIGN KEY (tenant_id, project_id, session_id) REFERENCES conversation (tenant_id, project_id, conversation_id);

--changeset medical-agent:1.0.0-148-principal-token-step dbms:mysql runInTransaction:false failOnError:true
ALTER TABLE principal_token ADD CONSTRAINT fk_token_step
  FOREIGN KEY (tenant_id, project_key, run_id, step_id) REFERENCES run_step (tenant_id, project_key, run_id, step_id);

--changeset medical-agent:1.0.0-149-pending-input-decision dbms:mysql runInTransaction:false failOnError:true
ALTER TABLE pending_input ADD CONSTRAINT fk_pending_input_decision
  FOREIGN KEY (tenant_id, decision_id) REFERENCES version_registry (tenant_id, version_id);
