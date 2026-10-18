--liquibase formatted sql
-- No credentials, grants, prices, approved inputs, capabilities or provider facts are seeded.
-- MODEL_VERIFICATION is executed by the authenticated model adapter, not a fabricated Temporal workflow.
--changeset medical-agent:076-01-model-verification-workflow dbms:mysql runInTransaction:false failOnError:true
ALTER TABLE system_job_run DROP CHECK ck_system_simulation_workflow,
 ADD CONSTRAINT ck_system_simulation_workflow CHECK (
  (job_type IN ('SIMULATION_EVALUATION','DOCUMENT_TECHNICAL_CHECK','MODEL_VERIFICATION') AND workflow_id IS NULL)
  OR (job_type NOT IN ('SIMULATION_EVALUATION','DOCUMENT_TECHNICAL_CHECK','MODEL_VERIFICATION') AND workflow_id IS NOT NULL));

--changeset medical-agent:076-02-formal-model-manifest dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE formal_model_manifest (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 manifest_id CHAR(36) CHARACTER SET ascii NOT NULL, manifest_kind VARCHAR(32) NOT NULL,
 canonical_document LONGTEXT NOT NULL, content_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
 PRIMARY KEY(tenant_id,manifest_id), UNIQUE KEY uq_formal_model_manifest_scope(tenant_id,project_id,manifest_id,content_sha256),
 FOREIGN KEY(tenant_id,project_id) REFERENCES project(tenant_id,project_id),
 FOREIGN KEY(tenant_id,manifest_id,content_sha256) REFERENCES version_registry(tenant_id,version_id,content_sha256),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK(manifest_kind IN ('SCOPE','RULES','TOKENIZATION')),
 CHECK(JSON_VALID(canonical_document) AND JSON_TYPE(CAST(canonical_document AS JSON))='OBJECT'),
 CHECK(schema_version='1.0.0' AND content_sha256 REGEXP '^[a-f0-9]{64}$' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK(classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:076-03-formal-model-verification-job dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE formal_model_verification_job (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 verification_id CHAR(36) CHARACTER SET ascii NOT NULL, operation_id CHAR(36) CHARACTER SET ascii NOT NULL,
 run_id CHAR(36) CHARACTER SET ascii NOT NULL, principal_id CHAR(36) CHARACTER SET ascii NOT NULL,
 step_id CHAR(36) CHARACTER SET ascii NOT NULL, action_id CHAR(36) CHARACTER SET ascii NOT NULL,
 context_manifest_id CHAR(36) CHARACTER SET ascii NOT NULL,
 scope_manifest_id CHAR(36) CHARACTER SET ascii NOT NULL, rule_manifest_id CHAR(36) CHARACTER SET ascii NOT NULL,
 tokenization_manifest_id CHAR(36) CHARACTER SET ascii NOT NULL,
 model_config_revision CHAR(36) CHARACTER SET ascii NOT NULL,
 execution_mode VARCHAR(24) NOT NULL, job_type VARCHAR(32) NOT NULL,
 idempotency_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 canonical_request LONGTEXT NOT NULL, request_body_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 input_sha256 CHAR(64) CHARACTER SET ascii NOT NULL, dependency_snapshot LONGTEXT NOT NULL,
 dependency_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
 PRIMARY KEY(tenant_id,verification_id), UNIQUE KEY uq_formal_verification_original(tenant_id,created_by,idempotency_key),
 UNIQUE KEY uq_formal_verification_run(tenant_id,run_id),
 FOREIGN KEY(tenant_id,verification_id) REFERENCES connection_verification(tenant_id,verification_id),
 FOREIGN KEY(tenant_id,operation_id) REFERENCES operation_record(tenant_id,operation_id),
 FOREIGN KEY(tenant_id,principal_id) REFERENCES run_principal(tenant_id,principal_id),
 FOREIGN KEY(tenant_id,project_id,run_id) REFERENCES execution_registry(tenant_id,project_id,run_id),
 FOREIGN KEY(tenant_id,project_id,run_id,step_id) REFERENCES run_step(tenant_id,project_id,run_id,step_id),
 FOREIGN KEY(tenant_id,run_id,step_id,action_id) REFERENCES step_action_binding(tenant_id,run_id,step_id,action_id),
 FOREIGN KEY(tenant_id,context_manifest_id) REFERENCES context_manifest(tenant_id,context_manifest_id),
 FOREIGN KEY(tenant_id,scope_manifest_id) REFERENCES formal_model_manifest(tenant_id,manifest_id),
 FOREIGN KEY(tenant_id,rule_manifest_id) REFERENCES formal_model_manifest(tenant_id,manifest_id),
 FOREIGN KEY(tenant_id,tokenization_manifest_id) REFERENCES formal_model_manifest(tenant_id,manifest_id),
 FOREIGN KEY(tenant_id,model_config_revision) REFERENCES version_registry(tenant_id,version_id),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK(execution_mode='ADAPTER_MTLS' AND job_type='MODEL_VERIFICATION'),
 CHECK(CHAR_LENGTH(idempotency_key) BETWEEN 16 AND 128 AND idempotency_key REGEXP '^[!-~]+$'),
 CHECK(JSON_VALID(canonical_request) AND JSON_VALID(dependency_snapshot)),
 CHECK(schema_version='1.0.0' AND request_body_sha256 REGEXP '^[a-f0-9]{64}$' AND input_sha256 REGEXP '^[a-f0-9]{64}$' AND dependency_sha256 REGEXP '^[a-f0-9]{64}$' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK(classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:076-04-formal-invocation-binding dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE formal_invocation_binding (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 invocation_id CHAR(36) CHARACTER SET ascii NOT NULL,
 canonical_request LONGTEXT NOT NULL, request_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 canonical_price LONGTEXT NOT NULL, price_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 authorization_snapshot LONGTEXT NOT NULL, authorization_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 enterprise_reservation_id CHAR(36) CHARACTER SET ascii NOT NULL, parent_reservation_id CHAR(36) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
 PRIMARY KEY(tenant_id,invocation_id),
 FOREIGN KEY(tenant_id,invocation_id) REFERENCES invocation(tenant_id,invocation_id),
 FOREIGN KEY(tenant_id,enterprise_reservation_id) REFERENCES budget_reservation(tenant_id,reservation_id),
 FOREIGN KEY(tenant_id,parent_reservation_id) REFERENCES budget_reservation(tenant_id,reservation_id),
 FOREIGN KEY(tenant_id,project_id) REFERENCES project(tenant_id,project_id),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK(enterprise_reservation_id<>parent_reservation_id),
 CHECK(JSON_VALID(canonical_request) AND JSON_VALID(canonical_price) AND JSON_VALID(authorization_snapshot)),
 CHECK(schema_version='1.0.0' AND request_sha256 REGEXP '^[a-f0-9]{64}$' AND price_sha256 REGEXP '^[a-f0-9]{64}$' AND authorization_sha256 REGEXP '^[a-f0-9]{64}$' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK(classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:076-05-formal-invocation-attempt dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE formal_invocation_attempt (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, invocation_id CHAR(36) CHARACTER SET ascii NOT NULL,
 attempt_id CHAR(36) CHARACTER SET ascii NOT NULL, dispatch_generation BIGINT UNSIGNED NOT NULL,
 lease_generation BIGINT UNSIGNED NOT NULL, service_uri VARCHAR(512) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 certificate_sha256 CHAR(64) CHARACTER SET ascii NOT NULL, trust_revision CHAR(36) CHARACTER SET ascii NOT NULL,
 input_sha256 CHAR(64) CHARACTER SET ascii NOT NULL, request_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 admitted_at DATETIME(6) NOT NULL, deadline_at DATETIME(6) NOT NULL,
 schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
 PRIMARY KEY(tenant_id,attempt_id), UNIQUE KEY uq_formal_single_dispatch(tenant_id,invocation_id),
 FOREIGN KEY(tenant_id,invocation_id) REFERENCES formal_invocation_binding(tenant_id,invocation_id),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK(dispatch_generation=1 AND lease_generation>0 AND deadline_at>admitted_at),
 CHECK(schema_version='1.0.0' AND certificate_sha256 REGEXP '^[a-f0-9]{64}$' AND input_sha256 REGEXP '^[a-f0-9]{64}$' AND request_sha256 REGEXP '^[a-f0-9]{64}$' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK(classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:076-06-formal-invocation-object-intent dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE formal_invocation_object_intent (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 invocation_id CHAR(36) CHARACTER SET ascii NOT NULL,dispatch_attempt_id CHAR(36) CHARACTER SET ascii NOT NULL,
 write_id CHAR(36) CHARACTER SET ascii NOT NULL,artifact_id CHAR(36) CHARACTER SET ascii NOT NULL,
 object_key VARCHAR(1024) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,object_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 size_bytes BIGINT UNSIGNED NOT NULL,canonical_payload LONGTEXT NOT NULL,kind VARCHAR(32) NOT NULL,
 schema_ref VARCHAR(512) CHARACTER SET ascii NOT NULL,payload_schema_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 outcome_binding_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(32) NOT NULL,schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL,created_at DATETIME(6) NOT NULL,classification VARCHAR(24) NOT NULL,
 PRIMARY KEY(tenant_id,write_id),UNIQUE KEY uq_formal_result_object(tenant_id,invocation_id,kind),
 FOREIGN KEY(tenant_id,invocation_id) REFERENCES invocation(tenant_id,invocation_id),
 FOREIGN KEY(tenant_id,dispatch_attempt_id) REFERENCES formal_invocation_attempt(tenant_id,attempt_id),
 FOREIGN KEY(tenant_id,project_id) REFERENCES project(tenant_id,project_id),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK(kind IN ('MODEL_RESULT','PROVIDER_CAPTURE') AND size_bytes>0),CHECK(JSON_VALID(canonical_payload)),
 CHECK(schema_version='1.0.0' AND object_sha256 REGEXP '^[a-f0-9]{64}$' AND payload_schema_sha256 REGEXP '^[a-f0-9]{64}$'
  AND outcome_binding_sha256 REGEXP '^[a-f0-9]{64}$' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK(classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:076-07-formal-invocation-object-attempt dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE formal_invocation_object_attempt (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,write_id CHAR(36) CHARACTER SET ascii NOT NULL,
 invocation_id CHAR(36) CHARACTER SET ascii NOT NULL,dispatch_attempt_id CHAR(36) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(32) NOT NULL,schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL,created_at DATETIME(6) NOT NULL,classification VARCHAR(24) NOT NULL,
 PRIMARY KEY(tenant_id,write_id),
 FOREIGN KEY(tenant_id,write_id) REFERENCES formal_invocation_object_intent(tenant_id,write_id),
 FOREIGN KEY(tenant_id,invocation_id) REFERENCES invocation(tenant_id,invocation_id),
 FOREIGN KEY(tenant_id,dispatch_attempt_id) REFERENCES formal_invocation_attempt(tenant_id,attempt_id),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK(schema_version='1.0.0' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK(classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:076-08-formal-invocation-object-state dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE formal_invocation_object_state (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,write_id CHAR(36) CHARACTER SET ascii NOT NULL,
 stage VARCHAR(24) NOT NULL,revision BIGINT UNSIGNED NOT NULL,
 schema_version VARCHAR(32) NOT NULL,schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL,created_at DATETIME(6) NOT NULL,classification VARCHAR(24) NOT NULL,updated_at DATETIME(6) NOT NULL,
 CHECK(schema_version='1.0.0' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 PRIMARY KEY(tenant_id,write_id),FOREIGN KEY(tenant_id,write_id) REFERENCES formal_invocation_object_intent(tenant_id,write_id),
 CHECK((stage='PREPARED' AND revision=1) OR (stage='ATTEMPTED' AND revision=2))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:076-09-formal-invocation-registered-object dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE formal_invocation_registered_object (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 invocation_id CHAR(36) CHARACTER SET ascii NOT NULL,kind VARCHAR(32) CHARACTER SET ascii NOT NULL,write_id CHAR(36) CHARACTER SET ascii NOT NULL,artifact_id CHAR(36) CHARACTER SET ascii NOT NULL,
 receipt_json LONGTEXT NOT NULL,receipt_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(32) NOT NULL,schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL,created_at DATETIME(6) NOT NULL,classification VARCHAR(24) NOT NULL,
 PRIMARY KEY(tenant_id,invocation_id,kind),CHECK(kind IN ('MODEL_RESULT','PROVIDER_CAPTURE')),UNIQUE KEY uq_formal_registered_write(tenant_id,write_id),UNIQUE KEY uq_formal_registered_artifact(tenant_id,artifact_id),
 FOREIGN KEY(tenant_id,invocation_id) REFERENCES invocation(tenant_id,invocation_id),
 FOREIGN KEY(tenant_id,write_id) REFERENCES formal_invocation_object_intent(tenant_id,write_id),
 FOREIGN KEY(tenant_id,artifact_id) REFERENCES artifact(tenant_id,artifact_id),
 FOREIGN KEY(tenant_id,project_id) REFERENCES project(tenant_id,project_id),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK(JSON_VALID(receipt_json)),CHECK(schema_version='1.0.0' AND receipt_sha256 REGEXP '^[a-f0-9]{64}$' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK(classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:076-10-formal-invocation-accounting dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE formal_invocation_accounting (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 invocation_id CHAR(36) CHARACTER SET ascii NOT NULL,receipt_id CHAR(36) CHARACTER SET ascii NOT NULL,
 receipt_json LONGTEXT NOT NULL,receipt_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(32) NOT NULL,schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL,created_at DATETIME(6) NOT NULL,classification VARCHAR(24) NOT NULL,
 PRIMARY KEY(tenant_id,invocation_id),UNIQUE KEY uq_formal_accounting_receipt(tenant_id,receipt_id),
 FOREIGN KEY(tenant_id,invocation_id,receipt_id) REFERENCES invocation_receipt(tenant_id,invocation_id,receipt_id),
 FOREIGN KEY(tenant_id,project_id) REFERENCES project(tenant_id,project_id),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK(JSON_VALID(receipt_json)),CHECK(schema_version='1.0.0' AND receipt_sha256 REGEXP '^[a-f0-9]{64}$' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK(classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:076-11-formal-invocation-cancellation dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE formal_invocation_cancellation (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 invocation_id CHAR(36) CHARACTER SET ascii NOT NULL,idempotency_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 canonical_request LONGTEXT NOT NULL,request_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 receipt_json LONGTEXT NOT NULL,receipt_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(32) NOT NULL,schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL,created_at DATETIME(6) NOT NULL,classification VARCHAR(24) NOT NULL,
 PRIMARY KEY(tenant_id,created_by,idempotency_key),
 FOREIGN KEY(tenant_id,invocation_id) REFERENCES invocation(tenant_id,invocation_id),
 FOREIGN KEY(tenant_id,project_id) REFERENCES project(tenant_id,project_id),FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK(CHAR_LENGTH(idempotency_key) BETWEEN 16 AND 128 AND idempotency_key REGEXP '^[!-~]+$'),
 CHECK(JSON_VALID(canonical_request) AND JSON_VALID(receipt_json)),
 CHECK(schema_version='1.0.0' AND request_sha256 REGEXP '^[a-f0-9]{64}$' AND receipt_sha256 REGEXP '^[a-f0-9]{64}$' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK(classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:076-12-formal-invocation-uncertainty dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE formal_invocation_uncertainty (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 invocation_id CHAR(36) CHARACTER SET ascii NOT NULL,attempt_id CHAR(36) CHARACTER SET ascii NOT NULL,
 receipt_json LONGTEXT NOT NULL,receipt_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(32) NOT NULL,schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL,created_at DATETIME(6) NOT NULL,classification VARCHAR(24) NOT NULL,
 PRIMARY KEY(tenant_id,invocation_id),UNIQUE KEY uq_formal_uncertain_attempt(tenant_id,attempt_id),
 FOREIGN KEY(tenant_id,invocation_id) REFERENCES invocation(tenant_id,invocation_id),
 FOREIGN KEY(tenant_id,attempt_id) REFERENCES formal_invocation_attempt(tenant_id,attempt_id),
 FOREIGN KEY(tenant_id,project_id) REFERENCES project(tenant_id,project_id),FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK(JSON_VALID(receipt_json)),CHECK(schema_version='1.0.0' AND receipt_sha256 REGEXP '^[a-f0-9]{64}$' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK(classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
