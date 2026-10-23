--liquibase formatted sql

--changeset medical-agent:181-connector-business-profile dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE customer_connector_business_profile (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
 config_revision CHAR(36) CHARACTER SET ascii NOT NULL,
 definition_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 profile_json LONGTEXT NOT NULL,
 profile_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 owner_user_id CHAR(36) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(32) NOT NULL,
 schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL,
 created_at DATETIME(6) NOT NULL,
 classification VARCHAR(24) NOT NULL,
 PRIMARY KEY(tenant_id,config_revision),
 FOREIGN KEY(tenant_id,config_revision,definition_sha256) REFERENCES customer_connector_definition(tenant_id,config_revision,definition_sha256),
 FOREIGN KEY(tenant_id,owner_user_id) REFERENCES tenant_member(tenant_id,user_id),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK(JSON_VALID(profile_json)),
 CHECK(classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;
