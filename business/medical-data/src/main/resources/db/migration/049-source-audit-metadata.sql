--liquibase formatted sql

--changeset medical-agent:1.0.0-278-retrieval-hit-metadata dbms:mysql runInTransaction:false failOnError:true
ALTER TABLE retrieval_hit
  ADD COLUMN created_by CHAR(36) CHARACTER SET ascii NULL,
  ADD COLUMN classification VARCHAR(24) NULL,
  ADD COLUMN schema_version VARCHAR(32) NULL,
  ADD COLUMN schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NULL;

--changeset medical-agent:1.0.0-279-retrieval-hit-copy dbms:mysql runInTransaction:false failOnError:true
--preconditions onFail:HALT onError:HALT
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM retrieval_hit h WHERE (SELECT COUNT(*) FROM retrieval_query q JOIN operation_record o ON o.tenant_id=q.tenant_id AND o.target_id=q.retrieval_id AND o.subject_id=q.subject_id AND o.operation_name='KN-14-S04' AND o.state='COMPLETED' WHERE q.tenant_id=h.tenant_id AND q.project_id=h.project_id AND q.retrieval_id=h.retrieval_id) <> 1
UPDATE retrieval_hit h
JOIN retrieval_query q ON q.tenant_id=h.tenant_id AND q.project_id=h.project_id AND q.retrieval_id=h.retrieval_id
JOIN operation_record o ON o.tenant_id=q.tenant_id AND o.target_id=q.retrieval_id AND o.subject_id=q.subject_id
  AND o.operation_name='KN-14-S04' AND o.state='COMPLETED'
SET h.created_by=q.created_by,h.classification=q.classification,
  h.schema_version=o.schema_version,h.schema_manifest_sha256=o.schema_manifest_sha256;

--changeset medical-agent:1.0.0-280-retrieval-hit-required dbms:mysql runInTransaction:false failOnError:true
ALTER TABLE retrieval_hit
  MODIFY COLUMN created_by CHAR(36) CHARACTER SET ascii NOT NULL,
  MODIFY COLUMN classification VARCHAR(24) NOT NULL,
  MODIFY COLUMN schema_version VARCHAR(32) NOT NULL,
  MODIFY COLUMN schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  ADD FOREIGN KEY (tenant_id,created_by) REFERENCES tenant_member (tenant_id,user_id),
  ADD CONSTRAINT ck_retrieval_hit_schema CHECK (schema_version='1.0.0');

--changeset medical-agent:1.0.0-281-retention-policy-metadata dbms:mysql runInTransaction:false failOnError:true
--preconditions onFail:HALT onError:HALT
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM retention_policy_revision
ALTER TABLE retention_policy_revision
  ADD COLUMN created_by CHAR(36) CHARACTER SET ascii NOT NULL,
  ADD COLUMN classification VARCHAR(24) NOT NULL,
  ADD FOREIGN KEY (tenant_id,created_by) REFERENCES tenant_member (tenant_id,user_id);

--changeset medical-agent:1.0.0-288-outbox-delivery-schema dbms:mysql runInTransaction:false failOnError:true
ALTER TABLE outbox_delivery
  ADD COLUMN schema_version VARCHAR(32) NULL,
  ADD COLUMN schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NULL;

--changeset medical-agent:1.0.0-289-outbox-delivery-schema-copy dbms:mysql runInTransaction:false failOnError:true
--preconditions onFail:HALT onError:HALT
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM outbox_delivery d JOIN outbox_event e ON e.tenant_id=d.tenant_id AND e.event_id=d.event_id WHERE JSON_TYPE(JSON_EXTRACT(e.payload,'$.schema_version')) IS NULL OR JSON_TYPE(JSON_EXTRACT(e.payload,'$.schema_version')) <> 'STRING' OR JSON_UNQUOTE(JSON_EXTRACT(e.payload,'$.schema_version')) <> '1.0.0' OR JSON_TYPE(JSON_EXTRACT(e.payload,'$.schema_manifest_sha256')) IS NULL OR JSON_TYPE(JSON_EXTRACT(e.payload,'$.schema_manifest_sha256')) <> 'STRING' OR NOT REGEXP_LIKE(JSON_UNQUOTE(JSON_EXTRACT(e.payload,'$.schema_manifest_sha256')),'^[a-f0-9]{64}$','c')
UPDATE outbox_delivery d
JOIN outbox_event e ON e.tenant_id=d.tenant_id AND e.event_id=d.event_id
SET d.schema_version=JSON_UNQUOTE(JSON_EXTRACT(e.payload,'$.schema_version')),
  d.schema_manifest_sha256=JSON_UNQUOTE(JSON_EXTRACT(e.payload,'$.schema_manifest_sha256'));

--changeset medical-agent:1.0.0-290-outbox-delivery-schema-required dbms:mysql runInTransaction:false failOnError:true
ALTER TABLE outbox_delivery
  MODIFY COLUMN schema_version VARCHAR(32) NOT NULL,
  MODIFY COLUMN schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  ADD CONSTRAINT ck_delivery_schema_version CHECK (schema_version='1.0.0');

--changeset medical-agent:1.0.0-282-lifecycle-decision-metadata dbms:mysql runInTransaction:false failOnError:true
ALTER TABLE source_lifecycle_decision
  ADD COLUMN created_by CHAR(36) CHARACTER SET ascii NULL,
  ADD COLUMN created_at DATETIME(6) NULL;

--changeset medical-agent:1.0.0-283-lifecycle-decision-copy dbms:mysql runInTransaction:false failOnError:true
UPDATE source_lifecycle_decision SET created_by=actor_id,created_at=decided_at;

--changeset medical-agent:1.0.0-284-lifecycle-decision-required dbms:mysql runInTransaction:false failOnError:true
ALTER TABLE source_lifecycle_decision
  MODIFY COLUMN created_by CHAR(36) CHARACTER SET ascii NOT NULL,
  MODIFY COLUMN created_at DATETIME(6) NOT NULL,
  ADD FOREIGN KEY (tenant_id,created_by) REFERENCES tenant_member (tenant_id,user_id);

--changeset medical-agent:1.0.0-285-lifecycle-state-metadata dbms:mysql runInTransaction:false failOnError:true
ALTER TABLE source_lifecycle_state
  ADD COLUMN created_by CHAR(36) CHARACTER SET ascii NULL,
  ADD COLUMN created_at DATETIME(6) NULL,
  ADD COLUMN classification VARCHAR(24) NULL;

--changeset medical-agent:1.0.0-286-lifecycle-state-copy dbms:mysql runInTransaction:false failOnError:true
UPDATE source_lifecycle_state s
JOIN source_version v ON v.tenant_id=s.tenant_id AND v.source_version=s.source_version
JOIN source o ON o.tenant_id=v.tenant_id AND o.source_id=v.source_id
SET s.created_by=v.created_by,s.created_at=v.created_at,s.classification=o.classification;

--changeset medical-agent:1.0.0-287-lifecycle-state-required dbms:mysql runInTransaction:false failOnError:true
ALTER TABLE source_lifecycle_state
  MODIFY COLUMN created_by CHAR(36) CHARACTER SET ascii NOT NULL,
  MODIFY COLUMN created_at DATETIME(6) NOT NULL,
  MODIFY COLUMN classification VARCHAR(24) NOT NULL,
  ADD FOREIGN KEY (tenant_id,created_by) REFERENCES tenant_member (tenant_id,user_id);
