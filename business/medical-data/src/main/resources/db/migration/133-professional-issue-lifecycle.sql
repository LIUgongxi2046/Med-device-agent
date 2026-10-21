--liquibase formatted sql
--changeset medical-agent:1.0.0-133-professional-issue-decision dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE professional_issue_decision (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
 project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 decision_id CHAR(36) CHARACTER SET ascii NOT NULL,
 issue_id CHAR(36) CHARACTER SET ascii NOT NULL,
 actor_id CHAR(36) CHARACTER SET ascii NOT NULL,
 idempotency_key VARCHAR(128) CHARACTER SET ascii NOT NULL,
 request_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 request_json JSON NOT NULL,
 result_json JSON NOT NULL,
 result_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(32) NOT NULL,
 schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL,
 created_at DATETIME(6) NOT NULL,
 classification VARCHAR(24) NOT NULL,
 PRIMARY KEY(tenant_id,decision_id),
 UNIQUE KEY uq_professional_issue_operation(tenant_id,actor_id,idempotency_key),
 FOREIGN KEY(tenant_id,project_id,issue_id) REFERENCES issue(tenant_id,project_id,issue_id),
 FOREIGN KEY(tenant_id,actor_id) REFERENCES tenant_member(tenant_id,user_id),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK(actor_id=created_by),
 CHECK(classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH')),
 CHECK(schema_version='1.0.0'),
 CHECK(REGEXP_LIKE(request_sha256,'^[0-9a-f]{64}$','c')),
 CHECK(REGEXP_LIKE(result_sha256,'^[0-9a-f]{64}$','c')),
 CHECK(REGEXP_LIKE(schema_manifest_sha256,'^[0-9a-f]{64}$','c')),
 CHECK(CHAR_LENGTH(idempotency_key) BETWEEN 1 AND 128)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-133-professional-issue-decision-update dbms:mysql runInTransaction:false failOnError:true splitStatements:false
CREATE TRIGGER imm_professional_issue_decision_update BEFORE UPDATE ON professional_issue_decision FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IMMUTABLE_ISSUE_DECISION';
--changeset medical-agent:1.0.0-133-professional-issue-decision-delete dbms:mysql runInTransaction:false failOnError:true splitStatements:false
CREATE TRIGGER imm_professional_issue_decision_delete BEFORE DELETE ON professional_issue_decision FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IMMUTABLE_ISSUE_DECISION';

--changeset medical-agent:1.0.0-133-issue-semantic-key dbms:mysql runInTransaction:false failOnError:true
ALTER TABLE issue ADD COLUMN semantic_sha256 CHAR(64) CHARACTER SET ascii NULL,
 ADD CONSTRAINT ck_issue_semantic_sha CHECK(semantic_sha256 IS NULL OR REGEXP_LIKE(semantic_sha256,'^[0-9a-f]{64}$','c')),
 ADD UNIQUE KEY uq_issue_semantic(tenant_id,project_id,document_revision,semantic_sha256);

--changeset medical-agent:1.0.0-133-professional-issue-occurrence dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE professional_issue_occurrence (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
 project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 check_id CHAR(36) CHARACTER SET ascii NOT NULL,
 issue_id CHAR(36) CHARACTER SET ascii NOT NULL,
 document_revision CHAR(36) CHARACTER SET ascii NOT NULL,
 finding_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 semantic_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 effect VARCHAR(16) NOT NULL,
 schema_version VARCHAR(32) NOT NULL,
 schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL,
 created_at DATETIME(6) NOT NULL,
 classification VARCHAR(24) NOT NULL,
 PRIMARY KEY(tenant_id,project_id,check_id,issue_id),
 UNIQUE KEY uq_issue_occurrence_finding(tenant_id,project_id,check_id,finding_sha256),
 FOREIGN KEY(tenant_id,project_id,check_id,document_revision) REFERENCES check_run(tenant_id,project_id,check_id,document_revision),
 FOREIGN KEY(tenant_id,project_id,issue_id) REFERENCES issue(tenant_id,project_id,issue_id),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK(effect IN ('CREATED','REOPENED','UNCHANGED')),
 CHECK(schema_version='1.0.0'),
 CHECK(classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH')),
 CHECK(REGEXP_LIKE(finding_sha256,'^[0-9a-f]{64}$','c')),
 CHECK(REGEXP_LIKE(semantic_sha256,'^[0-9a-f]{64}$','c')),
 CHECK(REGEXP_LIKE(schema_manifest_sha256,'^[0-9a-f]{64}$','c'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
--changeset medical-agent:1.0.0-133-professional-issue-occurrence-update dbms:mysql runInTransaction:false failOnError:true splitStatements:false
CREATE TRIGGER imm_professional_issue_occurrence_update BEFORE UPDATE ON professional_issue_occurrence FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IMMUTABLE_ISSUE_OCCURRENCE';
--changeset medical-agent:1.0.0-133-professional-issue-occurrence-delete dbms:mysql runInTransaction:false failOnError:true splitStatements:false
CREATE TRIGGER imm_professional_issue_occurrence_delete BEFORE DELETE ON professional_issue_occurrence FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IMMUTABLE_ISSUE_OCCURRENCE';
