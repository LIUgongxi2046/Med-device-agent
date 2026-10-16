--liquibase formatted sql

--changeset medical-agent:1.0.0-272-source-processing-lease dbms:mysql runInTransaction:false failOnError:true
ALTER TABLE processing_attempt
  ADD COLUMN lease_owner CHAR(36) CHARACTER SET ascii NULL,
  ADD COLUMN lease_generation BIGINT UNSIGNED NOT NULL DEFAULT 0,
  ADD COLUMN lease_expires_at DATETIME(6) NULL,
  ADD COLUMN execution_deadline DATETIME(6) NULL,
  ADD COLUMN command_ref JSON NULL,
  ADD COLUMN result_ref JSON NULL,
  ADD COLUMN write_intent JSON NULL,
  ADD KEY ix_processing_worker (tenant_id,job_type,status,lease_expires_at,created_at),
  ADD CONSTRAINT ck_processing_worker_lease CHECK (
    (lease_owner IS NULL AND lease_expires_at IS NULL) OR
    (lease_owner IS NOT NULL AND lease_expires_at IS NOT NULL AND lease_generation>0 AND status='RUNNING')
  ),
  ADD CONSTRAINT ck_processing_worker_result CHECK (
    (write_intent IS NULL AND result_ref IS NULL) OR
    (write_intent IS NOT NULL AND result_ref IS NOT NULL AND command_ref IS NOT NULL)
  );

--changeset medical-agent:1.0.0-273-parse-object-version dbms:mysql runInTransaction:false failOnError:true
ALTER TABLE parse_version
  ADD COLUMN artifact_version VARCHAR(256) NULL AFTER artifact_key,
  ADD COLUMN artifact_size_bytes BIGINT UNSIGNED NULL AFTER artifact_version,
  ADD CONSTRAINT ck_parse_object_version CHECK (
    (artifact_version IS NULL AND artifact_size_bytes IS NULL) OR
    (artifact_key IS NOT NULL AND content_sha256 IS NOT NULL AND artifact_version IS NOT NULL
      AND artifact_size_bytes IS NOT NULL AND artifact_size_bytes>0)
  );

--changeset medical-agent:1.0.0-274-processing-failure-manifest dbms:mysql runInTransaction:false failOnError:true
UPDATE processing_attempt SET failure_manifest=JSON_OBJECT('schema_version','1.0.0','issues',failure_manifest)
WHERE JSON_TYPE(failure_manifest)='ARRAY';
