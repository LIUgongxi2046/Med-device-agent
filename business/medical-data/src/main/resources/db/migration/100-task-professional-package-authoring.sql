--liquibase formatted sql
-- Project-only package originals. No approval, candidate, capability, credential, grant or model call is seeded.
-- All three tables are immutable. Mutable progress remains the existing operation_record CAS journal.
-- Every create/reject first locks the same tenant_member row, excluding cross-table same-key races.

--changeset medical-agent:1.0.0-100-package-intent dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE task_package_authoring_intent (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
 project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 operation_id CHAR(36) CHARACTER SET ascii NOT NULL,
 artifact_id CHAR(36) CHARACTER SET ascii NOT NULL,
 original_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 request_body_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 request_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 intent_json LONGTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
 intent_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(32) NOT NULL,
 schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL,
 created_at DATETIME(6) NOT NULL,
 classification VARCHAR(24) NOT NULL,
 PRIMARY KEY (tenant_id,operation_id),
 UNIQUE KEY uq_task_package_key (tenant_id,created_by,original_key),
 UNIQUE KEY uq_task_package_artifact (tenant_id,artifact_id),
 UNIQUE KEY uq_task_package_owner (tenant_id,project_id,operation_id,artifact_id),
 FOREIGN KEY (tenant_id,project_id) REFERENCES project (tenant_id,project_id),
 FOREIGN KEY (tenant_id,operation_id) REFERENCES operation_record (tenant_id,operation_id),
 FOREIGN KEY (tenant_id,created_by) REFERENCES tenant_member (tenant_id,user_id),
 CHECK (schema_version='1.0.0' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK (request_body_sha256 REGEXP '^[a-f0-9]{64}$' AND request_sha256 REGEXP '^[a-f0-9]{64}$' AND intent_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK (original_key REGEXP '^[A-Za-z0-9._:-]{16,128}$'),
 CHECK (classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH')),
 CHECK (JSON_VALID(intent_json) AND JSON_TYPE(CAST(intent_json AS JSON))='OBJECT'),
 CHECK (JSON_UNQUOTE(JSON_EXTRACT(intent_json,'$.tenant_id'))=tenant_id AND JSON_UNQUOTE(JSON_EXTRACT(intent_json,'$.project_id'))=project_id AND JSON_UNQUOTE(JSON_EXTRACT(intent_json,'$.operation_id'))=operation_id AND JSON_UNQUOTE(JSON_EXTRACT(intent_json,'$.artifact_id'))=artifact_id),
 CHECK (JSON_UNQUOTE(JSON_EXTRACT(intent_json,'$.kind')) IN ('PROMPT','PROFILE','ROLE') AND JSON_UNQUOTE(JSON_EXTRACT(intent_json,'$.operation_name'))='TASK-PROFESSIONAL-PACKAGE'),
 CHECK (JSON_UNQUOTE(JSON_EXTRACT(intent_json,'$.created_by'))=created_by AND JSON_UNQUOTE(JSON_EXTRACT(intent_json,'$.original_key'))=original_key AND JSON_UNQUOTE(JSON_EXTRACT(intent_json,'$.request_sha256'))=request_sha256 AND JSON_UNQUOTE(JSON_EXTRACT(intent_json,'$.request_body_sha256'))=request_body_sha256)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-100-package-registration dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE task_package_authoring_registration (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
 project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 operation_id CHAR(36) CHARACTER SET ascii NOT NULL,
 artifact_id CHAR(36) CHARACTER SET ascii NOT NULL,
 receipt_json LONGTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
 receipt_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(32) NOT NULL,
 schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL,
 created_at DATETIME(6) NOT NULL,
 classification VARCHAR(24) NOT NULL,
 PRIMARY KEY (tenant_id,operation_id),
 UNIQUE KEY uq_task_package_registered_artifact (tenant_id,artifact_id),
 FOREIGN KEY (tenant_id,project_id,operation_id,artifact_id) REFERENCES task_package_authoring_intent (tenant_id,project_id,operation_id,artifact_id),
 FOREIGN KEY (tenant_id,artifact_id) REFERENCES artifact (tenant_id,artifact_id),
 FOREIGN KEY (tenant_id,created_by) REFERENCES tenant_member (tenant_id,user_id),
 CHECK (schema_version='1.0.0' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$' AND receipt_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK (classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH')),
 CHECK (JSON_VALID(receipt_json) AND JSON_TYPE(CAST(receipt_json AS JSON))='OBJECT'),
 CHECK (JSON_UNQUOTE(JSON_EXTRACT(receipt_json,'$.status.state'))='REGISTERED' AND JSON_EXTRACT(receipt_json,'$.status.production_eligible')=FALSE),
 CHECK (JSON_UNQUOTE(JSON_EXTRACT(receipt_json,'$.status.operation_id'))=operation_id AND JSON_UNQUOTE(JSON_EXTRACT(receipt_json,'$.status.target_id'))=artifact_id AND JSON_UNQUOTE(JSON_EXTRACT(receipt_json,'$.status.tenant_id'))=tenant_id AND JSON_UNQUOTE(JSON_EXTRACT(receipt_json,'$.status.project_id'))=project_id AND JSON_UNQUOTE(JSON_EXTRACT(receipt_json,'$.status.created_by'))=created_by)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-100-package-rejection dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE task_package_authoring_rejection (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
 project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL,
 original_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 context_revision BIGINT UNSIGNED NOT NULL,
 session_id CHAR(36) CHARACTER SET ascii NOT NULL,
 wire_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 error_code VARCHAR(64) CHARACTER SET ascii NOT NULL,
 request_id CHAR(36) CHARACTER SET ascii NOT NULL,
 proof_json LONGTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
 proof_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(32) NOT NULL,
 schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 classification VARCHAR(24) NOT NULL,
 created_at DATETIME(6) NOT NULL,
 PRIMARY KEY (tenant_id,created_by,original_key),
 UNIQUE KEY uq_task_package_rejection_receipt (tenant_id,request_id),
 FOREIGN KEY (tenant_id,project_id) REFERENCES project (tenant_id,project_id),
 FOREIGN KEY (tenant_id,created_by) REFERENCES tenant_member (tenant_id,user_id),
 CHECK (original_key REGEXP '^[A-Za-z0-9._:-]{16,128}$' AND context_revision>0),
 CHECK (wire_sha256 REGEXP '^[a-f0-9]{64}$' AND proof_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK (error_code IN ('SCHEMA_INVALID','ACCESS_DENIED','REVISION_CONFLICT','SCOPE_STALE')),
 CHECK (schema_version='1.0.0' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$' AND classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH')),
 CHECK (JSON_UNQUOTE(JSON_EXTRACT(proof_json,'$.schema_version'))=schema_version AND JSON_UNQUOTE(JSON_EXTRACT(proof_json,'$.schema_manifest_sha256'))=schema_manifest_sha256 AND JSON_UNQUOTE(JSON_EXTRACT(proof_json,'$.classification'))=classification),
 CHECK (JSON_VALID(proof_json) AND JSON_TYPE(CAST(proof_json AS JSON))='OBJECT'),
 CHECK (JSON_UNQUOTE(JSON_EXTRACT(proof_json,'$.outcome'))='REJECTED_PRE_INTENT' AND JSON_UNQUOTE(JSON_EXTRACT(proof_json,'$.operation_name'))='TASK-PROFESSIONAL-PACKAGE'),
 CHECK (JSON_UNQUOTE(JSON_EXTRACT(proof_json,'$.tenant_id'))=tenant_id AND JSON_UNQUOTE(JSON_EXTRACT(proof_json,'$.project_id'))=project_id AND JSON_UNQUOTE(JSON_EXTRACT(proof_json,'$.created_by'))=created_by AND JSON_UNQUOTE(JSON_EXTRACT(proof_json,'$.original_key'))=original_key AND JSON_UNQUOTE(JSON_EXTRACT(proof_json,'$.session_id'))=session_id AND JSON_UNQUOTE(JSON_EXTRACT(proof_json,'$.wire_sha256'))=wire_sha256 AND JSON_UNQUOTE(JSON_EXTRACT(proof_json,'$.error_code'))=error_code AND JSON_UNQUOTE(JSON_EXTRACT(proof_json,'$.request_id'))=request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
