--liquibase formatted sql
-- Local deterministic technical checks are not approved RuleVersions.
-- No seed data, grants, credentials, rules or runtime defaults are installed.
--changeset medical-agent:068-01-document-technical-check-workflow dbms:mysql runInTransaction:false failOnError:true
ALTER TABLE system_job_run DROP CHECK ck_system_simulation_workflow,
 ADD CONSTRAINT ck_system_simulation_workflow CHECK (
  (job_type IN ('SIMULATION_EVALUATION','DOCUMENT_TECHNICAL_CHECK') AND workflow_id IS NULL)
  OR (job_type NOT IN ('SIMULATION_EVALUATION','DOCUMENT_TECHNICAL_CHECK') AND workflow_id IS NOT NULL));

--changeset medical-agent:068-03-document-technical-manifest dbms:mysql runInTransaction:false failOnError:true
-- One real version and canonical manifest per project/content hash, never a rule_version row.
CREATE TABLE document_technical_check_manifest (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
 project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 execution_manifest_id CHAR(36) CHARACTER SET ascii NOT NULL,
 canonical_manifest LONGTEXT NOT NULL,
 manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(32) NOT NULL,
 schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL,
 created_at DATETIME(6) NOT NULL,
 classification VARCHAR(24) NOT NULL,
 PRIMARY KEY(tenant_id,project_id,execution_manifest_id),
 UNIQUE KEY uq_document_technical_manifest_hash(tenant_id,project_id,manifest_sha256),
 UNIQUE KEY uq_document_technical_manifest_digest(tenant_id,project_id,execution_manifest_id,manifest_sha256),
 FOREIGN KEY(tenant_id,project_id) REFERENCES project(tenant_id,project_id),
 FOREIGN KEY(tenant_id,execution_manifest_id,manifest_sha256) REFERENCES version_registry(tenant_id,version_id,content_sha256),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK(schema_version='1.0.0'),
 CHECK(classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH')),
 CHECK(JSON_VALID(canonical_manifest) AND JSON_TYPE(CAST(canonical_manifest AS JSON))='OBJECT'),
 CHECK(manifest_sha256 REGEXP '^[a-f0-9]{64}$' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:068-02-document-technical-check dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE document_technical_check (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
 project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 check_id CHAR(36) CHARACTER SET ascii NOT NULL,
 run_id CHAR(36) CHARACTER SET ascii NOT NULL,
 document_id CHAR(36) CHARACTER SET ascii NOT NULL,
 document_revision CHAR(36) CHARACTER SET ascii NOT NULL,
 execution_manifest_id CHAR(36) CHARACTER SET ascii NOT NULL,
 canonical_manifest LONGTEXT NOT NULL,
 manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 canonical_input LONGTEXT NOT NULL,
 input_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 canonical_coverage LONGTEXT NOT NULL,
 canonical_findings LONGTEXT NOT NULL,
 coverage_artifact_id CHAR(36) CHARACTER SET ascii NOT NULL,
 findings_artifact_id CHAR(36) CHARACTER SET ascii NOT NULL,
 coverage_intent LONGTEXT NOT NULL,
 findings_intent LONGTEXT NOT NULL,
 coverage_reference LONGTEXT NOT NULL,
 findings_reference LONGTEXT NOT NULL,
 dependency_access_set_id CHAR(36) CHARACTER SET ascii NOT NULL,
 dependency_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 baseline_json LONGTEXT NOT NULL,
 post_evidence_watermark BIGINT UNSIGNED NOT NULL,
 post_issue_watermark BIGINT UNSIGNED NOT NULL,
 canonical_request LONGTEXT NOT NULL,
 request_body_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 operation_request_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 operation_id CHAR(36) CHARACTER SET ascii NOT NULL,
 receipt_json LONGTEXT NOT NULL,
 schema_version VARCHAR(32) NOT NULL,
 schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL,
 created_at DATETIME(6) NOT NULL,
 classification VARCHAR(24) NOT NULL,
 execution_mode VARCHAR(24) NOT NULL,
 project_key VARCHAR(36) CHARACTER SET ascii GENERATED ALWAYS AS (project_id) STORED,
 PRIMARY KEY(tenant_id,project_id,check_id),
 UNIQUE KEY uq_document_technical_operation(tenant_id,operation_id),
 UNIQUE KEY uq_document_technical_run(tenant_id,run_id),
 FOREIGN KEY(tenant_id,project_id,execution_manifest_id,manifest_sha256) REFERENCES document_technical_check_manifest(tenant_id,project_id,execution_manifest_id,manifest_sha256),
 FOREIGN KEY(tenant_id,project_id,check_id,document_revision) REFERENCES check_run(tenant_id,project_id,check_id,document_revision),
 FOREIGN KEY(tenant_id,project_id,document_id,document_revision) REFERENCES document_revision_meta(tenant_id,project_id,document_id,document_revision),
 FOREIGN KEY(tenant_id,project_key,run_id) REFERENCES execution_registry(tenant_id,project_key,run_id),
 FOREIGN KEY(tenant_id,run_id) REFERENCES system_job_run(tenant_id,run_id),
 FOREIGN KEY(tenant_id,execution_manifest_id,manifest_sha256) REFERENCES version_registry(tenant_id,version_id,content_sha256),
 FOREIGN KEY(tenant_id,project_key,coverage_artifact_id,dependency_access_set_id) REFERENCES artifact(tenant_id,project_key,artifact_id,dependency_access_set_id),
 FOREIGN KEY(tenant_id,project_key,findings_artifact_id,dependency_access_set_id) REFERENCES artifact(tenant_id,project_key,artifact_id,dependency_access_set_id),
 FOREIGN KEY(tenant_id,project_key,dependency_access_set_id,dependency_manifest_sha256) REFERENCES dependency_access_manifest(tenant_id,project_key,access_set_id,manifest_sha256),
 FOREIGN KEY(tenant_id,operation_id) REFERENCES operation_record(tenant_id,operation_id),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK(schema_version='1.0.0' AND execution_mode='SYNC_REQUEST'),
 CHECK(classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH')),
 CHECK(coverage_artifact_id<>findings_artifact_id),
 CHECK(JSON_VALID(baseline_json) AND JSON_VALID(canonical_coverage) AND JSON_VALID(canonical_findings) AND JSON_VALID(canonical_input) AND JSON_VALID(canonical_manifest) AND JSON_VALID(canonical_request) AND JSON_VALID(coverage_intent) AND JSON_VALID(coverage_reference) AND JSON_VALID(findings_intent) AND JSON_VALID(findings_reference) AND JSON_VALID(receipt_json)),
 CHECK(manifest_sha256 REGEXP '^[a-f0-9]{64}$' AND input_sha256 REGEXP '^[a-f0-9]{64}$' AND dependency_manifest_sha256 REGEXP '^[a-f0-9]{64}$' AND request_body_sha256 REGEXP '^[a-f0-9]{64}$' AND operation_request_sha256 REGEXP '^[a-f0-9]{64}$' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
