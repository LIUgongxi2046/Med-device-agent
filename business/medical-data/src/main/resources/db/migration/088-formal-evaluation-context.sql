--liquibase formatted sql
-- Independently authored/reviewed context bindings only. No grants, approvals, runs or invocation seeds.
--changeset medical-agent:088-01-context-definition dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE formal_evaluation_context_version (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 record_id CHAR(36) CHARACTER SET ascii NOT NULL,operation_id CHAR(36) CHARACTER SET ascii NOT NULL,
 idempotency_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 request_json LONGTEXT NOT NULL,request_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 content_json LONGTEXT NOT NULL,content_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 authorizations_json LONGTEXT NOT NULL,authorizations_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,receipt_json LONGTEXT NOT NULL,
 audit_id CHAR(36) CHARACTER SET ascii NOT NULL,event_id CHAR(36) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(32) NOT NULL,schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL,created_at DATETIME(6) NOT NULL,classification VARCHAR(24) NOT NULL,
 version_id CHAR(36) CHARACTER SET ascii NOT NULL,
 candidate_version CHAR(36) CHARACTER SET ascii NOT NULL,candidate_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 policy_version CHAR(36) CHARACTER SET ascii NOT NULL,policy_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 PRIMARY KEY(tenant_id,version_id),UNIQUE KEY uq_eval_context_record(tenant_id,record_id),
 UNIQUE KEY uq_eval_context_version_key(tenant_id,created_by,idempotency_key),
 UNIQUE KEY uq_eval_context_scope(tenant_id,project_id,version_id),
 UNIQUE KEY uq_eval_context_hash(tenant_id,version_id,content_sha256),
 FOREIGN KEY(tenant_id,version_id,content_sha256) REFERENCES version_registry(tenant_id,version_id,content_sha256),
 FOREIGN KEY(tenant_id,candidate_version,candidate_sha256) REFERENCES version_registry(tenant_id,version_id,content_sha256),
 FOREIGN KEY(tenant_id,policy_version,policy_sha256) REFERENCES version_registry(tenant_id,version_id,content_sha256),
 CHECK(record_id=version_id),
 FOREIGN KEY(tenant_id,project_id) REFERENCES project(tenant_id,project_id),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 FOREIGN KEY(tenant_id,operation_id) REFERENCES operation_record(tenant_id,operation_id),
 FOREIGN KEY(tenant_id,audit_id) REFERENCES audit_event(tenant_id,audit_id),
 FOREIGN KEY(tenant_id,event_id) REFERENCES outbox_event(tenant_id,event_id),
 CHECK(JSON_VALID(request_json) AND JSON_VALID(content_json) AND JSON_VALID(authorizations_json) AND JSON_VALID(receipt_json)),
 CHECK(CHAR_LENGTH(idempotency_key) BETWEEN 16 AND 128 AND idempotency_key REGEXP '^[!-~]+$'),
 CHECK(schema_version='1.0.0' AND request_sha256 REGEXP '^[a-f0-9]{64}$' AND content_sha256 REGEXP '^[a-f0-9]{64}$' AND authorizations_sha256 REGEXP '^[a-f0-9]{64}$' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK(classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:088-02-context-decisions dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE formal_evaluation_context_decision (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 record_id CHAR(36) CHARACTER SET ascii NOT NULL,operation_id CHAR(36) CHARACTER SET ascii NOT NULL,
 idempotency_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 request_json LONGTEXT NOT NULL,request_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 content_json LONGTEXT NOT NULL,content_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 authorizations_json LONGTEXT NOT NULL,authorizations_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,receipt_json LONGTEXT NOT NULL,
 audit_id CHAR(36) CHARACTER SET ascii NOT NULL,event_id CHAR(36) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(32) NOT NULL,schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL,created_at DATETIME(6) NOT NULL,classification VARCHAR(24) NOT NULL,
 version_id CHAR(36) CHARACTER SET ascii NOT NULL,
 before_revision BIGINT UNSIGNED NOT NULL,after_revision BIGINT UNSIGNED NOT NULL,
 decision VARCHAR(16) NOT NULL,status VARCHAR(16) NOT NULL,
 PRIMARY KEY(tenant_id,record_id),UNIQUE KEY uq_eval_context_decision_key(tenant_id,created_by,idempotency_key),
 UNIQUE KEY uq_eval_context_decision_revision(tenant_id,version_id,after_revision),
 UNIQUE KEY uq_eval_context_decision_head(tenant_id,version_id,record_id),
 FOREIGN KEY(tenant_id,project_id,version_id) REFERENCES formal_evaluation_context_version(tenant_id,project_id,version_id),
 CHECK(after_revision=before_revision+1 AND before_revision BETWEEN 1 AND 2),
 CHECK((decision='APPROVE' AND status='APPROVED' AND before_revision=1) OR (decision='REJECT' AND status='REJECTED' AND before_revision=1) OR (decision='DISABLE' AND status='DISABLED' AND before_revision=2)),
 FOREIGN KEY(tenant_id,project_id) REFERENCES project(tenant_id,project_id),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 FOREIGN KEY(tenant_id,operation_id) REFERENCES operation_record(tenant_id,operation_id),
 FOREIGN KEY(tenant_id,audit_id) REFERENCES audit_event(tenant_id,audit_id),
 FOREIGN KEY(tenant_id,event_id) REFERENCES outbox_event(tenant_id,event_id),
 CHECK(JSON_VALID(request_json) AND JSON_VALID(content_json) AND JSON_VALID(authorizations_json) AND JSON_VALID(receipt_json)),
 CHECK(CHAR_LENGTH(idempotency_key) BETWEEN 16 AND 128 AND idempotency_key REGEXP '^[!-~]+$'),
 CHECK(schema_version='1.0.0' AND request_sha256 REGEXP '^[a-f0-9]{64}$' AND content_sha256 REGEXP '^[a-f0-9]{64}$' AND authorizations_sha256 REGEXP '^[a-f0-9]{64}$' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK(classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:088-03-context-head dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE formal_evaluation_context_state (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,project_id CHAR(36) CHARACTER SET ascii NOT NULL,version_id CHAR(36) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(32) NOT NULL,schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL,created_at DATETIME(6) NOT NULL,classification VARCHAR(24) NOT NULL,
 status VARCHAR(16) NOT NULL,revision BIGINT UNSIGNED NOT NULL,
 latest_decision_id CHAR(36) CHARACTER SET ascii NULL,updated_at DATETIME(6) NOT NULL,
 PRIMARY KEY(tenant_id,version_id),
 FOREIGN KEY(tenant_id,project_id,version_id) REFERENCES formal_evaluation_context_version(tenant_id,project_id,version_id),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK(schema_version='1.0.0' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK(classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH')),
 FOREIGN KEY(tenant_id,version_id,latest_decision_id) REFERENCES formal_evaluation_context_decision(tenant_id,version_id,record_id),
 CHECK((status='DRAFT' AND revision=1 AND latest_decision_id IS NULL) OR (status IN ('APPROVED','REJECTED') AND revision=2 AND latest_decision_id IS NOT NULL) OR (status='DISABLED' AND revision=3 AND latest_decision_id IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:088-04-context-execution-binding dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE formal_evaluation_execution_context (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 execution_id CHAR(36) CHARACTER SET ascii NOT NULL,context_version CHAR(36) CHARACTER SET ascii NOT NULL,
 context_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,canonical_binding LONGTEXT NOT NULL,binding_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 audit_id CHAR(36) CHARACTER SET ascii NOT NULL,event_id CHAR(36) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(32) NOT NULL,schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL,created_at DATETIME(6) NOT NULL,classification VARCHAR(24) NOT NULL,
 PRIMARY KEY(tenant_id,execution_id),
 FOREIGN KEY(tenant_id,project_id,execution_id) REFERENCES formal_evaluation_execution(tenant_id,project_id,execution_id),
 FOREIGN KEY(tenant_id,context_version,context_sha256) REFERENCES formal_evaluation_context_version(tenant_id,version_id,content_sha256),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 FOREIGN KEY(tenant_id,audit_id) REFERENCES audit_event(tenant_id,audit_id),
 FOREIGN KEY(tenant_id,event_id) REFERENCES outbox_event(tenant_id,event_id),
 CHECK(JSON_VALID(canonical_binding) AND schema_version='1.0.0'),
 CHECK(context_sha256 REGEXP '^[a-f0-9]{64}$' AND binding_sha256 REGEXP '^[a-f0-9]{64}$' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK(classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
