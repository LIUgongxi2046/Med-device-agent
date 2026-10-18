--liquibase formatted sql
--changeset medical-agent:084-01-formal-evaluation-policy-input dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE formal_evaluation_policy_input (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
 record_id CHAR(36) CHARACTER SET ascii NOT NULL,
 content_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 snapshot_json LONGTEXT NOT NULL,
 snapshot_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 input_schema_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 policy_schema_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 PRIMARY KEY(tenant_id,record_id),
 FOREIGN KEY(tenant_id,record_id) REFERENCES agent_publication_record(tenant_id,record_id),
 FOREIGN KEY(tenant_id,record_id,content_sha256) REFERENCES version_registry(tenant_id,version_id,content_sha256),
 CHECK(JSON_VALID(snapshot_json) AND JSON_TYPE(CAST(snapshot_json AS JSON))='OBJECT'),
 CHECK(content_sha256 REGEXP '^[a-f0-9]{64}$' AND snapshot_sha256 REGEXP '^[a-f0-9]{64}$' AND input_schema_sha256 REGEXP '^[a-f0-9]{64}$' AND policy_schema_sha256 REGEXP '^[a-f0-9]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
