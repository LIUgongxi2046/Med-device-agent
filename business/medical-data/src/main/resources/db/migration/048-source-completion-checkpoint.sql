--liquibase formatted sql

--changeset medical-agent:1.0.0-275-source-completion-checkpoint dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE source_completion_checkpoint (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  project_id CHAR(36) CHARACTER SET ascii NOT NULL,
  upload_id CHAR(36) CHARACTER SET ascii NOT NULL,
  operation_id CHAR(36) CHARACTER SET ascii NOT NULL,
  event_id CHAR(36) CHARACTER SET ascii NOT NULL,
  consumer_id VARCHAR(64) NOT NULL,
  input_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  scan_command JSON NOT NULL,
  scan_receipt JSON NOT NULL,
  write_intent JSON NOT NULL,
  object_ref JSON NULL,
  status VARCHAR(24) NOT NULL,
  schema_version VARCHAR(32) NOT NULL,
  schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL,
  created_at DATETIME(6) NOT NULL,
  classification VARCHAR(24) NOT NULL,
  revision BIGINT UNSIGNED NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (tenant_id,event_id,consumer_id),
  UNIQUE KEY uq_completion_upload (tenant_id,upload_id,operation_id),
  UNIQUE KEY uq_completion_identity (tenant_id,event_id,consumer_id,upload_id,operation_id),
  FOREIGN KEY (tenant_id,event_id,consumer_id) REFERENCES outbox_delivery (tenant_id,event_id,consumer_id),
  FOREIGN KEY (tenant_id,project_id,upload_id) REFERENCES upload_item (tenant_id,project_id,upload_id),
  FOREIGN KEY (tenant_id,operation_id) REFERENCES operation_record (tenant_id,operation_id),
  CHECK (consumer_id='SOURCE_UPLOAD_COMPLETION'),
  CHECK (status IN ('WRITING','ORIGINAL_VERIFIED')),
  CHECK ((status='WRITING' AND object_ref IS NULL) OR (status='ORIGINAL_VERIFIED' AND object_ref IS NOT NULL)),
  CHECK (JSON_TYPE(scan_command)='OBJECT' AND JSON_TYPE(scan_receipt)='OBJECT' AND JSON_TYPE(write_intent)='OBJECT'),
  CHECK (object_ref IS NULL OR JSON_TYPE(object_ref)='OBJECT'),
  CHECK (revision>0 AND updated_at>=created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-277-source-completion-deadline dbms:mysql runInTransaction:false failOnError:true
ALTER TABLE outbox_delivery
  ADD COLUMN completion_started_at DATETIME(6) NULL,
  ADD COLUMN completion_deadline DATETIME(6) NULL,
  ADD COLUMN completion_manifest JSON NULL,
  ADD CONSTRAINT ck_completion_deadline CHECK (
    (completion_started_at IS NULL AND completion_deadline IS NULL AND completion_manifest IS NULL) OR
    (consumer_id='SOURCE_UPLOAD_COMPLETION' AND completion_started_at IS NOT NULL AND completion_deadline>completion_started_at
      AND completion_manifest IS NOT NULL AND JSON_TYPE(completion_manifest)='OBJECT')
  );

--changeset medical-agent:1.0.0-276-source-upload-result dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE source_upload_result (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  project_id CHAR(36) CHARACTER SET ascii NOT NULL,
  upload_id CHAR(36) CHARACTER SET ascii NOT NULL,
  operation_id CHAR(36) CHARACTER SET ascii NOT NULL,
  event_id CHAR(36) CHARACTER SET ascii NOT NULL,
  consumer_id VARCHAR(64) NOT NULL,
  source_id CHAR(36) CHARACTER SET ascii NOT NULL,
  source_version CHAR(36) CHARACTER SET ascii NOT NULL,
  rights_revision CHAR(36) CHARACTER SET ascii NOT NULL,
  parse_version CHAR(36) CHARACTER SET ascii NULL,
  attempt_id CHAR(36) CHARACTER SET ascii NULL,
  original_object_ref JSON NOT NULL,
  result_ref JSON NOT NULL,
  input_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  schema_version VARCHAR(32) NOT NULL,
  schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL,
  created_at DATETIME(6) NOT NULL,
  classification VARCHAR(24) NOT NULL,
  PRIMARY KEY (tenant_id,upload_id),
  UNIQUE KEY uq_upload_result_event (tenant_id,event_id,consumer_id),
  KEY ix_upload_result_source (tenant_id,source_version,upload_id),
  FOREIGN KEY (tenant_id,project_id,upload_id) REFERENCES upload_item (tenant_id,project_id,upload_id),
  FOREIGN KEY (tenant_id,event_id,consumer_id,upload_id,operation_id)
    REFERENCES source_completion_checkpoint (tenant_id,event_id,consumer_id,upload_id,operation_id),
  FOREIGN KEY (tenant_id,source_id,source_version) REFERENCES source_version (tenant_id,source_id,source_version),
  FOREIGN KEY (tenant_id,source_version,rights_revision) REFERENCES rights_revision (tenant_id,source_version,rights_revision),
  FOREIGN KEY (tenant_id,source_version,parse_version) REFERENCES parse_version (tenant_id,source_version,parse_version),
  FOREIGN KEY (tenant_id,project_id,attempt_id) REFERENCES processing_attempt (tenant_id,project_id,attempt_id),
  CHECK (consumer_id='SOURCE_UPLOAD_COMPLETION'),
  CHECK ((parse_version IS NULL AND attempt_id IS NULL) OR (parse_version IS NOT NULL AND attempt_id IS NOT NULL)),
  CHECK (JSON_TYPE(original_object_ref)='OBJECT' AND JSON_TYPE(result_ref)='OBJECT')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
