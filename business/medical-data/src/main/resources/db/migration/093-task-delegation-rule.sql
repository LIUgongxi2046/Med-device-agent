--liquibase formatted sql
-- Additive; does not widen frozen ROLE or execution-policy manifests.
--changeset medical-agent:1.0.0-093-task-delegation-rule dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE task_delegation_rule (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
 project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 rule_id CHAR(36) CHARACTER SET ascii NOT NULL,
 release_id CHAR(36) CHARACTER SET ascii NOT NULL,
 role_version CHAR(36) CHARACTER SET ascii NOT NULL,
 policy_version CHAR(36) CHARACTER SET ascii NOT NULL,
 budget_revision CHAR(36) CHARACTER SET ascii NOT NULL,
 fixed_json JSON NOT NULL,
 content_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 request_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 idempotency_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 operation_id CHAR(36) CHARACTER SET ascii NOT NULL,
 audit_id CHAR(36) CHARACTER SET ascii NOT NULL,
 event_id CHAR(36) CHARACTER SET ascii NOT NULL,
 receipt_json JSON NOT NULL,
 schema_version VARCHAR(32) NOT NULL,
 schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 classification VARCHAR(24) NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL,
 created_at DATETIME(6) NOT NULL,
 PRIMARY KEY(tenant_id,rule_id),
 UNIQUE KEY uq_task_delegation_author(tenant_id,created_by,idempotency_key),
 KEY ix_task_delegation_binding(tenant_id,project_id,release_id,role_version,policy_version),
 FOREIGN KEY(tenant_id,project_id) REFERENCES project(tenant_id,project_id),
 FOREIGN KEY(tenant_id,rule_id) REFERENCES version_registry(tenant_id,version_id),
 FOREIGN KEY(tenant_id,release_id) REFERENCES version_registry(tenant_id,version_id),
 FOREIGN KEY(tenant_id,role_version) REFERENCES version_registry(tenant_id,version_id),
 FOREIGN KEY(tenant_id,policy_version) REFERENCES version_registry(tenant_id,version_id),
 FOREIGN KEY(tenant_id,budget_revision) REFERENCES version_registry(tenant_id,version_id),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 FOREIGN KEY(tenant_id,operation_id) REFERENCES operation_record(tenant_id,operation_id),
 FOREIGN KEY(tenant_id,audit_id) REFERENCES audit_event(tenant_id,audit_id),
 FOREIGN KEY(tenant_id,event_id) REFERENCES outbox_event(tenant_id,event_id),
 CHECK(schema_version='1.0.0'),
 CHECK(content_sha256 REGEXP '^[a-f0-9]{64}$' AND request_sha256 REGEXP '^[a-f0-9]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-093-task-delegation-state dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE task_delegation_state (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
 rule_id CHAR(36) CHARACTER SET ascii NOT NULL,
 status VARCHAR(16) NOT NULL,
 revision BIGINT UNSIGNED NOT NULL,
 latest_decision_id CHAR(36) CHARACTER SET ascii NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL,
 classification VARCHAR(24) NOT NULL,
 created_at DATETIME(6) NOT NULL,
 updated_at DATETIME(6) NOT NULL,
 PRIMARY KEY(tenant_id,rule_id),
 FOREIGN KEY(tenant_id,rule_id) REFERENCES task_delegation_rule(tenant_id,rule_id),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK(updated_at>=created_at),
 CHECK(status IN ('DRAFT','APPROVED','REJECTED','DISABLED') AND revision>0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-093-task-delegation-decision dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE task_delegation_decision (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
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
 schema_version VARCHAR(32) NOT NULL,
 schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 classification VARCHAR(24) NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL,
 created_at DATETIME(6) NOT NULL,
 PRIMARY KEY(tenant_id,decision_id),
 UNIQUE KEY uq_task_delegation_decision_key(tenant_id,created_by,idempotency_key),
 FOREIGN KEY(tenant_id,rule_id) REFERENCES task_delegation_rule(tenant_id,rule_id),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 FOREIGN KEY(tenant_id,operation_id) REFERENCES operation_record(tenant_id,operation_id),
 FOREIGN KEY(tenant_id,audit_id) REFERENCES audit_event(tenant_id,audit_id),
 FOREIGN KEY(tenant_id,event_id) REFERENCES outbox_event(tenant_id,event_id),
 CHECK(schema_version='1.0.0'),
 CHECK(schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK(content_sha256 REGEXP '^[a-f0-9]{64}$' AND request_sha256 REGEXP '^[a-f0-9]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
ALTER TABLE task_delegation_state ADD CONSTRAINT fk_task_delegation_latest FOREIGN KEY(tenant_id,latest_decision_id) REFERENCES task_delegation_decision(tenant_id,decision_id);
