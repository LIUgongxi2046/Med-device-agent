--liquibase formatted sql
-- 078 execution-policy fragment. Merge only after 073's full scoped journal parent key.
-- Three immutable tables and one mutable CAS head; no network approvals or service identities are created.
--changeset medical-agent:078-execution-policy-1 dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE agent_execution_policy_version (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
 project_id CHAR(36) CHARACTER SET ascii NULL,
 project_key VARCHAR(36) CHARACTER SET ascii GENERATED ALWAYS AS (COALESCE(project_id,'ENTERPRISE')) STORED,
 policy_id CHAR(36) CHARACTER SET ascii NOT NULL,
 version_id CHAR(36) CHARACTER SET ascii NOT NULL,
 content_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 author_record_id CHAR(36) CHARACTER SET ascii NOT NULL,
 author_kind VARCHAR(32) NOT NULL,
 fixed_json LONGTEXT NOT NULL,
 schema_version VARCHAR(20) CHARACTER SET ascii NOT NULL,
 schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL,
 created_at DATETIME(6) NOT NULL,
 classification VARCHAR(32) CHARACTER SET ascii NOT NULL,
 PRIMARY KEY(tenant_id,version_id),
 UNIQUE KEY uq_aep_policy_identity(tenant_id,policy_id),
 UNIQUE KEY uq_aep_scope(tenant_id,project_key,policy_id,version_id,content_sha256),
 UNIQUE KEY uq_aep_author(tenant_id,author_record_id),
 FOREIGN KEY(tenant_id,version_id,content_sha256) REFERENCES version_registry(tenant_id,version_id,content_sha256),
 FOREIGN KEY(tenant_id,project_key,policy_id,author_record_id,author_kind)
   REFERENCES agent_publication_record(tenant_id,project_key,resource_id,record_id,kind),
 CHECK(author_kind='EXECUTION_POLICY_AUTHOR'),
 CHECK(JSON_VALID(fixed_json) AND JSON_TYPE(CAST(fixed_json AS JSON))='OBJECT'),
 FOREIGN KEY(tenant_id,project_id) REFERENCES project(tenant_id,project_id),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK(schema_version='1.0.0'),
 CHECK(classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH')),
 CHECK(content_sha256 REGEXP '^[a-f0-9]{64}$' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:078-execution-policy-2 dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE agent_execution_policy_component (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
 project_id CHAR(36) CHARACTER SET ascii NULL,
 project_key VARCHAR(36) CHARACTER SET ascii GENERATED ALWAYS AS (COALESCE(project_id,'ENTERPRISE')) STORED,
 policy_id CHAR(36) CHARACTER SET ascii NOT NULL,
 version_id CHAR(36) CHARACTER SET ascii NOT NULL,
 policy_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 component_id CHAR(36) CHARACTER SET ascii NOT NULL,
 kind VARCHAR(24) CHARACTER SET ascii NOT NULL,
 content_json LONGTEXT NOT NULL,
 content_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(20) CHARACTER SET ascii NOT NULL,
 schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL,
 created_at DATETIME(6) NOT NULL,
 classification VARCHAR(32) CHARACTER SET ascii NOT NULL,
 PRIMARY KEY(tenant_id,component_id),
 UNIQUE KEY uq_aep_component_owner(tenant_id,project_key,policy_id,version_id,component_id,kind),
 FOREIGN KEY(tenant_id,component_id,content_sha256) REFERENCES version_registry(tenant_id,version_id,content_sha256),
 FOREIGN KEY(tenant_id,project_key,policy_id,version_id,policy_sha256)
   REFERENCES agent_execution_policy_version(tenant_id,project_key,policy_id,version_id,content_sha256),
 CHECK(kind IN ('SOURCE_SCOPE','RETRIEVAL_PROFILE','NETWORK_POLICY')),
 CHECK(policy_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK(component_id<>version_id),
 CHECK(JSON_VALID(content_json) AND JSON_TYPE(CAST(content_json AS JSON))='OBJECT'),
 FOREIGN KEY(tenant_id,project_id) REFERENCES project(tenant_id,project_id),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK(schema_version='1.0.0'),
 CHECK(classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH')),
 CHECK(content_sha256 REGEXP '^[a-f0-9]{64}$' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:078-execution-policy-3 dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE agent_execution_policy_decision (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
 project_id CHAR(36) CHARACTER SET ascii NULL,
 project_key VARCHAR(36) CHARACTER SET ascii GENERATED ALWAYS AS (COALESCE(project_id,'ENTERPRISE')) STORED,
 policy_id CHAR(36) CHARACTER SET ascii NOT NULL,
 version_id CHAR(36) CHARACTER SET ascii NOT NULL,
 content_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 decision_record_id CHAR(36) CHARACTER SET ascii NOT NULL,
 decision_kind VARCHAR(32) NOT NULL,
 decision_json LONGTEXT NOT NULL,
 decision_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 decision VARCHAR(16) CHARACTER SET ascii NOT NULL,
 status VARCHAR(16) CHARACTER SET ascii NOT NULL,
 before_revision BIGINT UNSIGNED NOT NULL,
 after_revision BIGINT UNSIGNED NOT NULL,
 schema_version VARCHAR(20) CHARACTER SET ascii NOT NULL,
 schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL,
 created_at DATETIME(6) NOT NULL,
 classification VARCHAR(32) CHARACTER SET ascii NOT NULL,
 PRIMARY KEY(tenant_id,decision_record_id),
 UNIQUE KEY uq_aep_decision_before(tenant_id,version_id,before_revision),
 UNIQUE KEY uq_aep_decision_after(tenant_id,version_id,after_revision),
 UNIQUE KEY uq_aep_decision_head(tenant_id,project_key,policy_id,version_id,decision_record_id,after_revision,status),
 FOREIGN KEY(tenant_id,project_key,policy_id,version_id,content_sha256)
   REFERENCES agent_execution_policy_version(tenant_id,project_key,policy_id,version_id,content_sha256),
 FOREIGN KEY(tenant_id,project_key,policy_id,decision_record_id,decision_kind)
   REFERENCES agent_publication_record(tenant_id,project_key,resource_id,record_id,kind),
 FOREIGN KEY(tenant_id,decision_record_id,decision_sha256) REFERENCES version_registry(tenant_id,version_id,content_sha256),
 CHECK(decision_kind='EXECUTION_POLICY_DECISION'),
 CHECK((before_revision=1 AND after_revision=2 AND ((decision='APPROVE' AND status='APPROVED') OR (decision='REJECT' AND status='REJECTED')))
   OR (before_revision=2 AND after_revision=3 AND decision='DISABLE' AND status='DISABLED')),
 CHECK(decision_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK(JSON_VALID(decision_json) AND JSON_TYPE(CAST(decision_json AS JSON))='OBJECT'),
 FOREIGN KEY(tenant_id,project_id) REFERENCES project(tenant_id,project_id),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK(schema_version='1.0.0'),
 CHECK(classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH')),
 CHECK(content_sha256 REGEXP '^[a-f0-9]{64}$' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:078-execution-policy-4 dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE agent_execution_policy_state (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
 project_id CHAR(36) CHARACTER SET ascii NULL,
 project_key VARCHAR(36) CHARACTER SET ascii GENERATED ALWAYS AS (COALESCE(project_id,'ENTERPRISE')) STORED,
 policy_id CHAR(36) CHARACTER SET ascii NOT NULL,
 version_id CHAR(36) CHARACTER SET ascii NOT NULL,
 content_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 status VARCHAR(16) CHARACTER SET ascii NOT NULL,
 revision BIGINT UNSIGNED NOT NULL,
 latest_decision_id CHAR(36) CHARACTER SET ascii NULL,
 updated_at DATETIME(6) NOT NULL,
 schema_version VARCHAR(20) CHARACTER SET ascii NOT NULL,
 schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL,
 created_at DATETIME(6) NOT NULL,
 classification VARCHAR(32) CHARACTER SET ascii NOT NULL,
 PRIMARY KEY(tenant_id,version_id),
 UNIQUE KEY uq_aep_state_owner(tenant_id,project_key,policy_id,version_id),
 FOREIGN KEY(tenant_id,project_key,policy_id,version_id,content_sha256)
   REFERENCES agent_execution_policy_version(tenant_id,project_key,policy_id,version_id,content_sha256),
 FOREIGN KEY(tenant_id,project_key,policy_id,version_id,latest_decision_id,revision,status)
   REFERENCES agent_execution_policy_decision(tenant_id,project_key,policy_id,version_id,decision_record_id,after_revision,status),
 CHECK((status='DRAFT' AND revision=1 AND latest_decision_id IS NULL) OR (status IN ('APPROVED','REJECTED') AND revision=2 AND latest_decision_id IS NOT NULL) OR (status='DISABLED' AND revision=3 AND latest_decision_id IS NOT NULL)),
 CHECK(updated_at>=created_at),
 FOREIGN KEY(tenant_id,project_id) REFERENCES project(tenant_id,project_id),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK(schema_version='1.0.0'),
 CHECK(classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH')),
 CHECK(content_sha256 REGEXP '^[a-f0-9]{64}$' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
