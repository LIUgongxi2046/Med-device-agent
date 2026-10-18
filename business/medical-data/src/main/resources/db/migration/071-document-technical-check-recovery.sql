--liquibase formatted sql
-- Independent immutable recovery evidence. No backfill from mutable operation JSON.
-- Existing 068 checks without independent intent/attempt evidence remain unavailable.
--changeset medical-agent:071-01-document-technical-check-intent dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE document_technical_check_intent (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
 project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 operation_id CHAR(36) CHARACTER SET ascii NOT NULL,
 check_id CHAR(36) CHARACTER SET ascii NOT NULL,
 document_revision CHAR(36) CHARACTER SET ascii NOT NULL,
 idempotency_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 canonical_command LONGTEXT NOT NULL,
 intent_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 start_event_id CHAR(36) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(32) NOT NULL,
 schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL,
 created_at DATETIME(6) NOT NULL,
 classification VARCHAR(24) NOT NULL,
 PRIMARY KEY(tenant_id,operation_id),
 UNIQUE KEY uq_technical_intent_key(tenant_id,created_by,idempotency_key),
 UNIQUE KEY uq_technical_intent_check(tenant_id,project_id,check_id),
 UNIQUE KEY uq_technical_intent_binding(tenant_id,project_id,operation_id,check_id,intent_sha256),
 UNIQUE KEY uq_technical_intent_event(tenant_id,start_event_id),
 FOREIGN KEY(tenant_id,project_id) REFERENCES project(tenant_id,project_id),
 FOREIGN KEY(tenant_id,operation_id) REFERENCES operation_record(tenant_id,operation_id),
 FOREIGN KEY(tenant_id,operation_id,intent_sha256) REFERENCES version_registry(tenant_id,version_id,content_sha256),
 FOREIGN KEY(tenant_id,project_id,check_id,document_revision) REFERENCES check_run(tenant_id,project_id,check_id,document_revision),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK(schema_version='1.0.0'),
 CHECK(classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH')),
 CHECK(CHAR_LENGTH(idempotency_key) BETWEEN 16 AND 128),
 CHECK(JSON_VALID(canonical_command) AND JSON_TYPE(CAST(canonical_command AS JSON))='OBJECT'),
 CHECK(intent_sha256 REGEXP '^[a-f0-9]{64}$' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:071-02-document-technical-check-attempt dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE document_technical_check_attempt (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
 project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 operation_id CHAR(36) CHARACTER SET ascii NOT NULL,
 check_id CHAR(36) CHARACTER SET ascii NOT NULL,
 output_kind VARCHAR(16) NOT NULL,
 write_id CHAR(36) CHARACTER SET ascii NOT NULL,
 artifact_id CHAR(36) CHARACTER SET ascii NOT NULL,
 intent_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(32) NOT NULL,
 schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL,
 created_at DATETIME(6) NOT NULL,
 classification VARCHAR(24) NOT NULL,
 PRIMARY KEY(tenant_id,operation_id,output_kind),
 UNIQUE KEY uq_technical_attempt_write(tenant_id,write_id),
 UNIQUE KEY uq_technical_attempt_artifact(tenant_id,artifact_id),
 FOREIGN KEY(tenant_id,project_id,operation_id,check_id,intent_sha256)
  REFERENCES document_technical_check_intent(tenant_id,project_id,operation_id,check_id,intent_sha256),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK(output_kind IN ('COVERAGE','FINDINGS')),
 CHECK(schema_version='1.0.0'),
 CHECK(classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH')),
 CHECK(intent_sha256 REGEXP '^[a-f0-9]{64}$' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
