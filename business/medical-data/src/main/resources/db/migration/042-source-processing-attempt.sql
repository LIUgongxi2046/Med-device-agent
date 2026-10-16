--liquibase formatted sql

--changeset medical-agent:1.0.0-260-queued-parse-materialization dbms:mysql runInTransaction:false failOnError:true
ALTER TABLE parse_version
  MODIFY content_sha256 CHAR(64) CHARACTER SET ascii NULL,
  MODIFY artifact_key VARCHAR(1024) NULL,
  ADD CONSTRAINT ck_parse_materialization CHECK (
    (parse_status IN ('QUEUED','PROCESSING','FAILED') AND content_sha256 IS NULL AND artifact_key IS NULL) OR
    (content_sha256 IS NOT NULL AND artifact_key IS NOT NULL)
  );

--changeset medical-agent:1.0.0-261-processing-request-manifest dbms:mysql runInTransaction:false failOnError:true
ALTER TABLE processing_attempt
  ADD COLUMN request_manifest JSON NOT NULL DEFAULT (JSON_OBJECT()) AFTER failure_manifest,
  ADD CONSTRAINT ck_processing_request_manifest CHECK (JSON_TYPE(request_manifest) = 'OBJECT');
