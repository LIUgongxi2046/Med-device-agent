--liquibase formatted sql
-- Human project application decisions are not professional template approval.
--changeset medical-agent:1.0.0-130-template-application-head dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE template_application_head (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
 project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 profile_revision CHAR(36) CHARACTER SET ascii NOT NULL,
 template_version CHAR(36) CHARACTER SET ascii NOT NULL,
 revision BIGINT UNSIGNED NOT NULL,
 schema_version VARCHAR(32) NOT NULL,
 schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL,
 created_at DATETIME(6) NOT NULL,
 classification VARCHAR(24) NOT NULL,
 PRIMARY KEY (tenant_id,project_id,profile_revision,template_version),
 FOREIGN KEY (tenant_id,project_id,profile_revision) REFERENCES project_profile_revision(tenant_id,project_id,profile_revision),
 FOREIGN KEY (tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 FOREIGN KEY (tenant_id,template_version) REFERENCES template_definition(tenant_id,template_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
--changeset medical-agent:1.0.0-130-template-application-decision dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE template_application_decision (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
 decision_id CHAR(36) CHARACTER SET ascii NOT NULL,
 operation_id CHAR(36) CHARACTER SET ascii NOT NULL,
 project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 profile_revision CHAR(36) CHARACTER SET ascii NOT NULL,
 template_version CHAR(36) CHARACTER SET ascii NOT NULL,
 resulting_revision BIGINT UNSIGNED NOT NULL,
 reason TEXT NOT NULL,
 receipt_json JSON NOT NULL,
 receipt_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(32) NOT NULL,
 schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL,
 created_at DATETIME(6) NOT NULL,
 classification VARCHAR(24) NOT NULL,
 PRIMARY KEY (tenant_id,decision_id),
 UNIQUE KEY uq_template_application_operation (tenant_id,operation_id),
 UNIQUE KEY uq_template_application_revision (tenant_id,project_id,profile_revision,template_version,resulting_revision),
 FOREIGN KEY (tenant_id,project_id,profile_revision,template_version) REFERENCES template_application_head(tenant_id,project_id,profile_revision,template_version),
 FOREIGN KEY (tenant_id,operation_id) REFERENCES operation_record(tenant_id,operation_id),
 FOREIGN KEY (tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK (resulting_revision > 0 AND CHAR_LENGTH(TRIM(reason)) BETWEEN 1 AND 2000),
 CHECK (JSON_UNQUOTE(JSON_EXTRACT(receipt_json,'$.data.decision.request.reason')) = reason
    AND JSON_UNQUOTE(JSON_EXTRACT(receipt_json,'$.data.decision.decision_id')) = decision_id
    AND JSON_UNQUOTE(JSON_EXTRACT(receipt_json,'$.data.operation_id')) = operation_id
    AND JSON_EXTRACT(receipt_json,'$.data.professional_approval') = false)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
