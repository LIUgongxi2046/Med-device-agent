--liquibase formatted sql
-- Additive v2 professional object intent and independently authenticated supervision.
-- No approval, credential, endpoint, session, isolation or successful result is seeded.
-- Historical 075 bytes and v1 interpretation stay unchanged. The shared attempt/state/
-- registration envelopes remain v1 journals; only the typed intent discriminator is v2.
--changeset medical-agent:099-01-runtime-professional-v2 dbms:mysql runInTransaction:false failOnError:true
ALTER TABLE runtime_object_intent DROP CHECK runtime_object_intent_chk_3,
 ADD CONSTRAINT ck_runtime_object_typed_version CHECK (
  ((schema_version='1.0.0' AND write_kind IN ('REPLAY_MANIFEST','STEP_ENVELOPE','STEP_RESULT'))
   OR (schema_version='2.0.0' AND (
    (write_kind='STEP_ENVELOPE' AND schema_ref='urn:medical:task-research-step:1.0.0#/$defs/StepEnvelope')
    OR (write_kind='STEP_RESULT' AND schema_ref='urn:medical:task-research-step:1.0.0#/$defs/StepResult')
    OR (write_kind='TASK_EXACT_INPUT' AND schema_ref='urn:medical:task-invocation-context:1.0.0#/$defs/ExactInput')
    OR (write_kind='SESSION_EXPORT' AND schema_ref='urn:medical:task-research-step:1.0.0#/$defs/SessionExport')
    OR (write_kind='EVIDENCE_BUNDLE' AND schema_ref='https://schemas.medical-agent.invalid/1.0.0/agent-design/artifacts.schema.json#/$defs/EvidenceBundle'))))
  AND size_bytes>0 AND object_sha256 REGEXP '^[a-f0-9]{64}$'
  AND write_key REGEXP '^[a-f0-9]{64}$' AND payload_schema_sha256 REGEXP '^[a-f0-9]{64}$');

-- Admission reserves a server-owned session UUID before083 creates it. Thus admission
-- cannot have a session FK. Closure and all consumers verify the exact083 original.
--changeset medical-agent:099-02-professional-supervision dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE professional_runtime_supervision (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
 project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 run_id CHAR(36) CHARACTER SET ascii NOT NULL,
 step_id CHAR(36) CHARACTER SET ascii NOT NULL,
 lease_generation BIGINT UNSIGNED NOT NULL,
 session_id CHAR(36) CHARACTER SET ascii NOT NULL,
 receipt_id CHAR(36) CHARACTER SET ascii NOT NULL,
 phase VARCHAR(16) NOT NULL,
 supervisor_service_uri VARCHAR(512) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 certificate_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 trust_revision CHAR(36) CHARACTER SET ascii NOT NULL,
 deployment_revision CHAR(36) CHARACTER SET ascii NOT NULL,
 deployment_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 input_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 envelope_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 canonical_evidence LONGTEXT NOT NULL,
 evidence_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 audit_id CHAR(36) CHARACTER SET ascii NOT NULL,
 event_id CHAR(36) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(32) NOT NULL,
 schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL,
 created_at DATETIME(6) NOT NULL,
 classification VARCHAR(24) NOT NULL,
 PRIMARY KEY (tenant_id,receipt_id),
 UNIQUE KEY uq_professional_supervision_phase (tenant_id,session_id,phase),
 UNIQUE KEY uq_professional_supervision_pin (tenant_id,receipt_id,evidence_sha256),
 FOREIGN KEY (tenant_id,project_id,run_id,step_id) REFERENCES run_step (tenant_id,project_id,run_id,step_id),
 FOREIGN KEY (tenant_id,created_by) REFERENCES tenant_member (tenant_id,user_id),
 FOREIGN KEY (tenant_id,audit_id) REFERENCES audit_event (tenant_id,audit_id),
 FOREIGN KEY (tenant_id,event_id) REFERENCES outbox_event (tenant_id,event_id),
 CHECK (lease_generation>0 AND phase IN ('ADMITTED','CLOSED') AND JSON_VALID(canonical_evidence)),
 CHECK (schema_version='1.0.0' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK (evidence_sha256 REGEXP '^[a-f0-9]{64}$' AND deployment_sha256 REGEXP '^[a-f0-9]{64}$' AND certificate_sha256 REGEXP '^[a-f0-9]{64}$' AND input_sha256 REGEXP '^[a-f0-9]{64}$' AND envelope_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK (classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:099-03-professional-object-provenance dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE runtime_professional_object_binding (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
 project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 run_id CHAR(36) CHARACTER SET ascii NOT NULL,
 step_id CHAR(36) CHARACTER SET ascii NOT NULL,
 write_id CHAR(36) CHARACTER SET ascii NOT NULL,
 session_id CHAR(36) CHARACTER SET ascii NOT NULL,
 lease_generation BIGINT UNSIGNED NOT NULL,
 producer_rule_id CHAR(36) CHARACTER SET ascii NOT NULL,
 producer_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 producer_decision_id CHAR(36) CHARACTER SET ascii NOT NULL,
 producer_decision_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 input_artifact_id CHAR(36) CHARACTER SET ascii NOT NULL,
 input_receipt_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 supervision_receipt_id CHAR(36) CHARACTER SET ascii NOT NULL,
 supervision_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 canonical_provenance LONGTEXT NOT NULL,
 provenance_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(32) NOT NULL,
 schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL,
 created_at DATETIME(6) NOT NULL,
 classification VARCHAR(24) NOT NULL,
 PRIMARY KEY (tenant_id,project_id,run_id,write_id),
 FOREIGN KEY (tenant_id,project_id,run_id,write_id) REFERENCES runtime_object_intent (tenant_id,project_id,run_id,write_id),
 FOREIGN KEY (tenant_id,session_id) REFERENCES professional_runtime_session (tenant_id,session_id),
 FOREIGN KEY (tenant_id,producer_rule_id,producer_sha256) REFERENCES task_node_producer_rule (tenant_id,rule_id,content_sha256),
 FOREIGN KEY (tenant_id,input_artifact_id) REFERENCES task_node_input_intent (tenant_id,artifact_id),
 FOREIGN KEY (tenant_id,supervision_receipt_id,supervision_sha256) REFERENCES professional_runtime_supervision (tenant_id,receipt_id,evidence_sha256),
 FOREIGN KEY (tenant_id,created_by) REFERENCES tenant_member (tenant_id,user_id),
 CHECK (lease_generation>0 AND JSON_VALID(canonical_provenance)),
 CHECK (schema_version='1.0.0' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK (provenance_sha256 REGEXP '^[a-f0-9]{64}$' AND producer_decision_sha256 REGEXP '^[a-f0-9]{64}$' AND input_receipt_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK (classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
