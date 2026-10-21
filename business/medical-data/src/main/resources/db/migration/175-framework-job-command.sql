--liquibase formatted sql
-- Platform-only administration ledger; never a tenant business table.
--changeset medical-agent:1.0.0-175-framework-job-identity dbms:mysql runInTransaction:false failOnError:true
-- Native composite PK does not uniquely constrain job_id. Fail on legacy duplicate IDs, never choose/delete one.
ALTER TABLE sys_job ADD UNIQUE KEY uq_framework_job_identity (job_id);

--changeset medical-agent:1.0.0-175-framework-job-command dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE platform_framework_job_command (
  command_id CHAR(36) CHARACTER SET ascii NOT NULL,
  actor_id CHAR(36) CHARACTER SET ascii NOT NULL,
  job_id BIGINT NOT NULL,
  handler_key VARCHAR(96) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  job_group VARCHAR(64) NOT NULL,
  action VARCHAR(16) CHARACTER SET ascii NOT NULL,
  request_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  binding_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  state VARCHAR(16) CHARACTER SET ascii NOT NULL,
  original_response JSON NOT NULL,
  outcome_code VARCHAR(64) CHARACTER SET ascii NULL,
  schema_version VARCHAR(32) NOT NULL,
  schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  classification VARCHAR(24) NOT NULL,
  PRIMARY KEY (command_id),
  FOREIGN KEY (actor_id) REFERENCES platform_local_identity_metadata(user_id),
  FOREIGN KEY (created_by) REFERENCES platform_local_identity_metadata(user_id),
  FOREIGN KEY (job_id) REFERENCES sys_job(job_id),
  CHECK (created_by=actor_id),
  CHECK (classification='RESTRICTED'),
  CHECK (action IN ('RUN','PAUSE','RESUME','CREATE','EDIT','RETIRE')),
  CHECK (state IN ('RESERVED','DISPATCHED','UNKNOWN','FAILED')),
  CHECK (request_sha256 REGEXP '^[0-9a-f]{64}$'),
  CHECK (binding_manifest_sha256 REGEXP '^[0-9a-f]{64}$'),
  CHECK (schema_manifest_sha256 REGEXP '^[0-9a-f]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

--changeset medical-agent:1.0.0-175-framework-job-command-immutable dbms:mysql splitStatements:false runInTransaction:false failOnError:true
CREATE TRIGGER platform_framework_job_command_immutable BEFORE UPDATE ON platform_framework_job_command FOR EACH ROW
BEGIN
  IF NOT (OLD.command_id <=> NEW.command_id) OR NOT (OLD.actor_id <=> NEW.actor_id)
    OR NOT (OLD.job_id <=> NEW.job_id) OR NOT (OLD.handler_key <=> NEW.handler_key)
    OR NOT (OLD.job_group <=> NEW.job_group) OR NOT (OLD.action <=> NEW.action)
    OR NOT (OLD.request_sha256 <=> NEW.request_sha256) OR NOT (OLD.binding_manifest_sha256 <=> NEW.binding_manifest_sha256)
    OR NOT (OLD.original_response <=> NEW.original_response) OR NOT (OLD.created_by <=> NEW.created_by)
    OR NOT (OLD.created_at <=> NEW.created_at) OR NOT (OLD.classification <=> NEW.classification)
    OR NOT (OLD.schema_version <=> NEW.schema_version) OR NOT (OLD.schema_manifest_sha256 <=> NEW.schema_manifest_sha256)
    OR NOT ((OLD.state='RESERVED' AND NEW.state IN ('DISPATCHED','UNKNOWN','FAILED'))
         OR (OLD.state='UNKNOWN' AND NEW.state='DISPATCHED')) THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='FRAMEWORK_JOB_COMMAND_IMMUTABLE';
  END IF;
END;

--changeset medical-agent:1.0.0-175-framework-job-command-no-delete dbms:mysql splitStatements:false runInTransaction:false failOnError:true
CREATE TRIGGER platform_framework_job_command_no_delete BEFORE DELETE ON platform_framework_job_command FOR EACH ROW
BEGIN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='FRAMEWORK_JOB_COMMAND_IMMUTABLE';
END;

--changeset medical-agent:1.0.0-175-framework-job-revision dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE platform_framework_job_revision (
  job_id BIGINT NOT NULL,
  revision BIGINT UNSIGNED NOT NULL,
  command_id CHAR(36) CHARACTER SET ascii NOT NULL,
  state VARCHAR(16) CHARACTER SET ascii NOT NULL,
  definition JSON NOT NULL,
  definition_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  schema_version VARCHAR(32) NOT NULL,
  schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL,
  created_at DATETIME(6) NOT NULL,
  classification VARCHAR(24) NOT NULL,
  PRIMARY KEY(job_id,revision),
  UNIQUE KEY uq_framework_job_revision_command(command_id),
  FOREIGN KEY(job_id) REFERENCES sys_job(job_id),
  FOREIGN KEY(command_id) REFERENCES platform_framework_job_command(command_id),
  FOREIGN KEY(created_by) REFERENCES platform_local_identity_metadata(user_id),
  CHECK(revision>0), CHECK(state IN ('ACTIVE','RETIRED')),
  CHECK(classification='RESTRICTED'), CHECK(schema_version='1.0.0'),
  CHECK(definition_sha256 REGEXP '^[0-9a-f]{64}$'), CHECK(schema_manifest_sha256 REGEXP '^[0-9a-f]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

--changeset medical-agent:1.0.0-175-framework-job-revision-immutable dbms:mysql splitStatements:false runInTransaction:false failOnError:true
CREATE TRIGGER platform_framework_job_revision_immutable BEFORE UPDATE ON platform_framework_job_revision FOR EACH ROW
BEGIN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='FRAMEWORK_JOB_REVISION_IMMUTABLE';
END;

--changeset medical-agent:1.0.0-175-framework-job-revision-no-delete dbms:mysql splitStatements:false runInTransaction:false failOnError:true
CREATE TRIGGER platform_framework_job_revision_no_delete BEFORE DELETE ON platform_framework_job_revision FOR EACH ROW
BEGIN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='FRAMEWORK_JOB_REVISION_IMMUTABLE';
END;
