--liquibase formatted sql

--changeset medical-agent:180-rule-business-definition dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE rule_business_definition (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
 rule_version CHAR(36) CHARACTER SET ascii NOT NULL,
 definition_json LONGTEXT NOT NULL,
 definition_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 rule_content_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(32) NOT NULL,
 schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 operation_id CHAR(36) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL,
 created_at DATETIME(6) NOT NULL,
 classification VARCHAR(24) NOT NULL,
 PRIMARY KEY(tenant_id,rule_version),
 FOREIGN KEY(tenant_id,rule_version) REFERENCES rule_version(tenant_id,rule_version),
 FOREIGN KEY(tenant_id,rule_version,rule_content_sha256) REFERENCES version_registry(tenant_id,version_id,content_sha256),
 FOREIGN KEY(tenant_id,operation_id) REFERENCES operation_record(tenant_id,operation_id),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK(JSON_VALID(definition_json)),
 CHECK(classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;
