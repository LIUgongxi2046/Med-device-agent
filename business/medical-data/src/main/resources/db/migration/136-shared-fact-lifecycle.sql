--liquibase formatted sql
--changeset medical-agent:1.0.0-136-shared_fact_policy dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE shared_fact_policy (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 policy_id CHAR(36) CHARACTER SET ascii NOT NULL, fact_class VARCHAR(64) NOT NULL,
 content_json JSON NOT NULL, content_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 authority_json JSON NOT NULL,
 schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
 PRIMARY KEY(tenant_id,project_id,policy_id),
 FOREIGN KEY(tenant_id,project_id) REFERENCES project(tenant_id,project_id),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK(schema_version='1.0.0')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
--changeset medical-agent:1.0.0-136-shared_fact_proposal dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE shared_fact_proposal (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 proposal_id CHAR(36) CHARACTER SET ascii NOT NULL, fact_key VARCHAR(128) NOT NULL,
 base_proposal_id CHAR(36) CHARACTER SET ascii NULL,
 document_id CHAR(36) CHARACTER SET ascii NOT NULL, document_revision CHAR(36) CHARACTER SET ascii NOT NULL,
 content_json JSON NOT NULL, content_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
 PRIMARY KEY(tenant_id,project_id,proposal_id),
 FOREIGN KEY(tenant_id,project_id) REFERENCES project(tenant_id,project_id),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 FOREIGN KEY(tenant_id,project_id,base_proposal_id) REFERENCES shared_fact_proposal(tenant_id,project_id,proposal_id),
 FOREIGN KEY(tenant_id,proposal_id) REFERENCES version_registry(tenant_id,version_id),
 FOREIGN KEY(tenant_id,project_id,document_revision) REFERENCES document_revision_meta(tenant_id,project_id,document_revision),
 FOREIGN KEY(tenant_id,project_id,document_id) REFERENCES document_head(tenant_id,project_id,document_id),
 CHECK(schema_version='1.0.0')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
--changeset medical-agent:1.0.0-136-shared_fact_verification dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE shared_fact_verification (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 verification_id CHAR(36) CHARACTER SET ascii NOT NULL, proposal_id CHAR(36) CHARACTER SET ascii NOT NULL,
 policy_id CHAR(36) CHARACTER SET ascii NOT NULL, decision VARCHAR(24) NOT NULL, reason TEXT NOT NULL,
 baseline_json JSON NOT NULL, baseline_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
 PRIMARY KEY(tenant_id,project_id,verification_id),
 FOREIGN KEY(tenant_id,project_id) REFERENCES project(tenant_id,project_id),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 FOREIGN KEY(tenant_id,project_id,proposal_id) REFERENCES shared_fact_proposal(tenant_id,project_id,proposal_id),
 FOREIGN KEY(tenant_id,project_id,policy_id) REFERENCES shared_fact_policy(tenant_id,project_id,policy_id),
 CHECK(decision IN ('SUPPORTS','CONFLICT','NEEDS_EVIDENCE')),
 CHECK(schema_version='1.0.0')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
--changeset medical-agent:1.0.0-136-shared_fact_adoption dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE shared_fact_adoption (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 fact_version CHAR(36) CHARACTER SET ascii NOT NULL, proposal_id CHAR(36) CHARACTER SET ascii NOT NULL,
 verification_id CHAR(36) CHARACTER SET ascii NOT NULL, policy_id CHAR(36) CHARACTER SET ascii NOT NULL,
 baseline_json JSON NOT NULL, baseline_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
 PRIMARY KEY(tenant_id,project_id,fact_version),
 FOREIGN KEY(tenant_id,project_id) REFERENCES project(tenant_id,project_id),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 FOREIGN KEY(tenant_id,project_id,proposal_id) REFERENCES shared_fact_proposal(tenant_id,project_id,proposal_id),
 FOREIGN KEY(tenant_id,project_id,policy_id) REFERENCES shared_fact_policy(tenant_id,project_id,policy_id),
 FOREIGN KEY(tenant_id,project_id,verification_id) REFERENCES shared_fact_verification(tenant_id,project_id,verification_id),
 FOREIGN KEY(tenant_id,project_id,fact_version) REFERENCES shared_fact_version(tenant_id,project_id,fact_version),
 CHECK(schema_version='1.0.0')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
--changeset medical-agent:1.0.0-136-shared_fact_lifecycle_operation dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE shared_fact_lifecycle_operation (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 actor_id CHAR(36) CHARACTER SET ascii NOT NULL, operation_name VARCHAR(64) NOT NULL,
 idempotency_key VARCHAR(128) CHARACTER SET ascii NOT NULL, request_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 receipt_json JSON NOT NULL, receipt_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
 PRIMARY KEY(tenant_id,project_id,actor_id,operation_name,idempotency_key),
 FOREIGN KEY(tenant_id,project_id) REFERENCES project(tenant_id,project_id),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 FOREIGN KEY(tenant_id,actor_id) REFERENCES tenant_member(tenant_id,user_id),
 CHECK(schema_version='1.0.0' AND actor_id=created_by)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
--changeset medical-agent:1.0.0-136-policy-head dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE shared_fact_policy_head (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 fact_class VARCHAR(128) NOT NULL, policy_id CHAR(36) CHARACTER SET ascii NOT NULL,
 revision BIGINT UNSIGNED NOT NULL, updated_at DATETIME(6) NOT NULL,
 schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
 PRIMARY KEY(tenant_id,project_id,fact_class),
 FOREIGN KEY(tenant_id,project_id,policy_id) REFERENCES shared_fact_policy(tenant_id,project_id,policy_id),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK(schema_version='1.0.0' AND revision>0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
--changeset medical-agent:1.0.0-136-proposal-head dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE shared_fact_proposal_head (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 fact_key VARCHAR(128) NOT NULL, proposal_id CHAR(36) CHARACTER SET ascii NOT NULL,
 revision BIGINT UNSIGNED NOT NULL, updated_at DATETIME(6) NOT NULL,
 schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
 PRIMARY KEY(tenant_id,project_id,fact_key),
 FOREIGN KEY(tenant_id,project_id,proposal_id) REFERENCES shared_fact_proposal(tenant_id,project_id,proposal_id),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK(schema_version='1.0.0' AND revision>0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
--changeset medical-agent:1.0.0-136-adopted-head dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE shared_fact_adopted_head (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 fact_key VARCHAR(128) NOT NULL, fact_version CHAR(36) CHARACTER SET ascii NOT NULL,
 revision BIGINT UNSIGNED NOT NULL, updated_at DATETIME(6) NOT NULL,
 schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
 PRIMARY KEY(tenant_id,project_id,fact_key),
 FOREIGN KEY(tenant_id,project_id,fact_version) REFERENCES shared_fact_version(tenant_id,project_id,fact_version),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK(schema_version='1.0.0' AND revision>0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
--changeset medical-agent:1.0.0-136-shared_fact_check_rule dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE shared_fact_check_rule (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 rule_id CHAR(36) CHARACTER SET ascii NOT NULL,
 fact_class VARCHAR(64) NOT NULL, definition_json JSON NOT NULL, definition_sha256 CHAR(64) CHARACTER SET ascii NOT NULL, authority_json JSON NOT NULL,
 schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
 PRIMARY KEY(tenant_id,project_id,rule_id),
 FOREIGN KEY(tenant_id,project_id) REFERENCES project(tenant_id,project_id),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),

 CHECK(schema_version='1.0.0')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
--changeset medical-agent:1.0.0-136-shared_fact_check dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE shared_fact_check (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 check_id CHAR(36) CHARACTER SET ascii NOT NULL,
 proposal_id CHAR(36) CHARACTER SET ascii NOT NULL, policy_id CHAR(36) CHARACTER SET ascii NOT NULL, baseline_json JSON NOT NULL, baseline_sha256 CHAR(64) CHARACTER SET ascii NOT NULL, result VARCHAR(16) NOT NULL,
 schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
 PRIMARY KEY(tenant_id,project_id,check_id),
 FOREIGN KEY(tenant_id,project_id) REFERENCES project(tenant_id,project_id),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 FOREIGN KEY(tenant_id,project_id,proposal_id) REFERENCES shared_fact_proposal(tenant_id,project_id,proposal_id),
 FOREIGN KEY(tenant_id,project_id,policy_id) REFERENCES shared_fact_policy(tenant_id,project_id,policy_id),
 CHECK(result IN ('PASS','FAIL')),
 CHECK(schema_version='1.0.0')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
