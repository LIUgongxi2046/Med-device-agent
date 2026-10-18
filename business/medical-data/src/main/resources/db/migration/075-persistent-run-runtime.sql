--liquibase formatted sql
-- Staging: additive immutable runtime evidence. Existing run_step/task_run remain authoritative.
--changeset medical-agent:1.0.0-075-runtime-intent dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE run_runtime_intent (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
 project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 project_key VARCHAR(36) CHARACTER SET ascii GENERATED ALWAYS AS (COALESCE(project_id, 'ENTERPRISE')) STORED,
 run_id CHAR(36) CHARACTER SET ascii NOT NULL,
 intent_id CHAR(36) CHARACTER SET ascii NOT NULL,
 plan_revision CHAR(36) CHARACTER SET ascii NOT NULL,
 replay_manifest_id CHAR(36) CHARACTER SET ascii NOT NULL,
 replay_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 replay_object_version VARCHAR(256) NOT NULL,
 canonical_intent JSON NOT NULL,
 intent_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(32) NOT NULL,
 schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL,
 created_at DATETIME(6) NOT NULL,
 classification VARCHAR(24) NOT NULL,
 CHECK (classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH')),
 CHECK (schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
 PRIMARY KEY (tenant_id, project_id, run_id),
 UNIQUE KEY uq_runtime_intent_id (tenant_id, intent_id),
 FOREIGN KEY (tenant_id, project_id, run_id) REFERENCES task_run (tenant_id, project_id, run_id),
 FOREIGN KEY (tenant_id, plan_revision) REFERENCES version_registry (tenant_id, version_id),
 FOREIGN KEY (tenant_id, project_key, replay_manifest_id, replay_sha256, replay_object_version) REFERENCES artifact (tenant_id, project_key, artifact_id, sha256, object_version),
 FOREIGN KEY (tenant_id, created_by) REFERENCES tenant_member (tenant_id, user_id),
 CHECK (schema_version='1.0.0' AND intent_sha256 REGEXP '^[a-f0-9]{64}$' AND replay_sha256 REGEXP '^[a-f0-9]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-075-step-runtime-attempt dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE step_runtime_attempt (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
 project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 project_key VARCHAR(36) CHARACTER SET ascii GENERATED ALWAYS AS (COALESCE(project_id, 'ENTERPRISE')) STORED,
 run_id CHAR(36) CHARACTER SET ascii NOT NULL,
 step_id CHAR(36) CHARACTER SET ascii NOT NULL,
 lease_generation BIGINT UNSIGNED NOT NULL,
 command_id CHAR(36) CHARACTER SET ascii NOT NULL,
 action_id CHAR(36) CHARACTER SET ascii NOT NULL,
 envelope_artifact_id CHAR(36) CHARACTER SET ascii NOT NULL,
 envelope_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 envelope_object_version VARCHAR(256) NOT NULL,
 input_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 worker_service_uri VARCHAR(512) NOT NULL,
 canonical_dispatch JSON NOT NULL,
 dispatch_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(32) NOT NULL,
 schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL,
 created_at DATETIME(6) NOT NULL,
 classification VARCHAR(24) NOT NULL,
 CHECK (classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH')),
 CHECK (schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
 PRIMARY KEY (tenant_id, project_id, run_id, step_id, lease_generation),
 UNIQUE KEY uq_runtime_dispatch_command (tenant_id, project_id, run_id, command_id),
 FOREIGN KEY (tenant_id, project_id, run_id) REFERENCES run_runtime_intent (tenant_id, project_id, run_id),
 FOREIGN KEY (tenant_id, project_id, run_id, step_id) REFERENCES run_step (tenant_id, project_id, run_id, step_id),
 FOREIGN KEY (tenant_id, project_key, envelope_artifact_id, envelope_sha256, envelope_object_version) REFERENCES artifact (tenant_id, project_key, artifact_id, sha256, object_version),
 FOREIGN KEY (tenant_id, created_by) REFERENCES tenant_member (tenant_id, user_id),
 CHECK (lease_generation>0 AND schema_version='1.0.0' AND CHAR_LENGTH(worker_service_uri)>0
  AND input_sha256 REGEXP '^[a-f0-9]{64}$' AND envelope_sha256 REGEXP '^[a-f0-9]{64}$' AND dispatch_sha256 REGEXP '^[a-f0-9]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-075-runtime-receipt dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE run_runtime_receipt (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
 project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 project_key VARCHAR(36) CHARACTER SET ascii GENERATED ALWAYS AS (COALESCE(project_id, 'ENTERPRISE')) STORED,
 run_id CHAR(36) CHARACTER SET ascii NOT NULL,
 receipt_id CHAR(36) CHARACTER SET ascii NOT NULL,
 command_id CHAR(36) CHARACTER SET ascii NOT NULL,
 command_kind VARCHAR(32) NOT NULL,
 request_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 canonical_request JSON NOT NULL,
 canonical_receipt JSON NOT NULL,
 receipt_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(32) NOT NULL,
 schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL,
 created_at DATETIME(6) NOT NULL,
 classification VARCHAR(24) NOT NULL,
 CHECK (classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH')),
 CHECK (schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
 PRIMARY KEY (tenant_id, project_id, run_id, command_id),
 UNIQUE KEY uq_runtime_receipt_id (tenant_id, receipt_id),
 FOREIGN KEY (tenant_id, project_id, run_id) REFERENCES run_runtime_intent (tenant_id, project_id, run_id),
 FOREIGN KEY (tenant_id, created_by) REFERENCES tenant_member (tenant_id, user_id),
 CHECK (command_kind IN ('COMMIT','FAILURE','DEPENDENCY_FAILED','RESOLVE_WAITING','REQUEST_CANCEL','TERMINATION_PROOF','FINISH')
  AND schema_version='1.0.0' AND request_sha256 REGEXP '^[a-f0-9]{64}$' AND receipt_sha256 REGEXP '^[a-f0-9]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-075-runtime-object-intent dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE runtime_object_intent (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
 project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 run_id CHAR(36) CHARACTER SET ascii NOT NULL,
 step_id CHAR(36) CHARACTER SET ascii NOT NULL,
 write_key CHAR(64) CHARACTER SET ascii NOT NULL,
 write_id CHAR(36) CHARACTER SET ascii NOT NULL,
 planned_artifact_id CHAR(36) CHARACTER SET ascii NOT NULL,
 write_kind VARCHAR(32) NOT NULL,
 object_key VARCHAR(1024) NOT NULL,
 object_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 size_bytes BIGINT UNSIGNED NOT NULL,
 canonical_payload JSON NOT NULL,
 schema_ref VARCHAR(256) NOT NULL,
 payload_schema_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(32) NOT NULL,
 schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL,
 created_at DATETIME(6) NOT NULL,
 classification VARCHAR(24) NOT NULL,
 CHECK (classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH')),
 CHECK (schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
 PRIMARY KEY (tenant_id,project_id,run_id,write_key),
 UNIQUE KEY uq_runtime_object_write (tenant_id,project_id,run_id,write_id),
 UNIQUE KEY uq_runtime_object_planned (tenant_id,planned_artifact_id),
 FOREIGN KEY (tenant_id,project_id,run_id) REFERENCES task_run (tenant_id,project_id,run_id),
 FOREIGN KEY (tenant_id,project_id,run_id,step_id) REFERENCES run_step (tenant_id,project_id,run_id,step_id),
 FOREIGN KEY (tenant_id,created_by) REFERENCES tenant_member (tenant_id,user_id),
 CHECK (write_kind IN ('REPLAY_MANIFEST','STEP_ENVELOPE','STEP_RESULT') AND schema_version='1.0.0' AND size_bytes>0
   AND object_sha256 REGEXP '^[a-f0-9]{64}$' AND write_key REGEXP '^[a-f0-9]{64}$' AND payload_schema_sha256 REGEXP '^[a-f0-9]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-075-runtime-object-attempt dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE runtime_object_attempt (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
 project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 run_id CHAR(36) CHARACTER SET ascii NOT NULL,
 write_id CHAR(36) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(32) NOT NULL,
 schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL,
 created_at DATETIME(6) NOT NULL,
 classification VARCHAR(24) NOT NULL,
 CHECK (classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH')),
 CHECK (schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
 PRIMARY KEY (tenant_id,project_id,run_id,write_id),
 FOREIGN KEY (tenant_id,project_id,run_id,write_id) REFERENCES runtime_object_intent (tenant_id,project_id,run_id,write_id),
 FOREIGN KEY (tenant_id,created_by) REFERENCES tenant_member (tenant_id,user_id),
 CHECK (schema_version='1.0.0')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

-- Mutable phase witness; immutable intent/attempt must agree before every send/recovery.
--changeset medical-agent:1.0.0-075-runtime-object-state dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE runtime_object_state (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
 project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 run_id CHAR(36) CHARACTER SET ascii NOT NULL,
 write_id CHAR(36) CHARACTER SET ascii NOT NULL,
 stage VARCHAR(24) NOT NULL,
 revision BIGINT UNSIGNED NOT NULL,
 updated_at DATETIME(6) NOT NULL,
 schema_version VARCHAR(32) NOT NULL,
 schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL,
 created_at DATETIME(6) NOT NULL,
 classification VARCHAR(24) NOT NULL,
 CHECK (classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH')),
 CHECK (schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
 PRIMARY KEY (tenant_id,project_id,run_id,write_id),
 FOREIGN KEY (tenant_id,project_id,run_id,write_id) REFERENCES runtime_object_intent (tenant_id,project_id,run_id,write_id),
 FOREIGN KEY (tenant_id,created_by) REFERENCES tenant_member (tenant_id,user_id),
 CHECK (schema_version='1.0.0' AND ((stage='PREPARED' AND revision=1) OR (stage='ATTEMPTED' AND revision=2)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

-- Node mapping is produced from the approved fixed execution policy, never role-list order.
--changeset medical-agent:1.0.0-075-runtime-node-definition dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE runtime_node_definition (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
 project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 run_id CHAR(36) CHARACTER SET ascii NOT NULL,
 step_id CHAR(36) CHARACTER SET ascii NOT NULL,
 node_kind VARCHAR(64) NOT NULL,
 role_version CHAR(36) CHARACTER SET ascii NOT NULL,
 role_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 policy_version CHAR(36) CHARACTER SET ascii NOT NULL,
 policy_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 canonical_definition JSON NOT NULL,
 definition_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(32) NOT NULL,
 schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL,
 created_at DATETIME(6) NOT NULL,
 classification VARCHAR(24) NOT NULL,
 CHECK (classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH')),
 CHECK (schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
 PRIMARY KEY (tenant_id,project_id,run_id,step_id),
 FOREIGN KEY (tenant_id,project_id,run_id,step_id) REFERENCES run_step (tenant_id,project_id,run_id,step_id),
 FOREIGN KEY (tenant_id,role_version) REFERENCES version_registry (tenant_id,version_id),
 FOREIGN KEY (tenant_id,policy_version) REFERENCES version_registry (tenant_id,version_id),
 FOREIGN KEY (tenant_id,created_by) REFERENCES tenant_member (tenant_id,user_id),
 CHECK (schema_version='1.0.0' AND role_sha256 REGEXP '^[a-f0-9]{64}$' AND policy_sha256 REGEXP '^[a-f0-9]{64}$' AND definition_sha256 REGEXP '^[a-f0-9]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

-- Full fixed runtime dependency graph; source-rights access manifests remain separate.

--changeset medical-agent:1.0.0-075-runtime-governance-manifest dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE runtime_governance_manifest (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
 project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 run_id CHAR(36) CHARACTER SET ascii NOT NULL,
 write_id CHAR(36) CHARACTER SET ascii NOT NULL,
 principal_id CHAR(36) CHARACTER SET ascii NOT NULL,
 replay_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 canonical_manifest JSON NOT NULL,
 manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(32) NOT NULL,
 schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL,
 created_at DATETIME(6) NOT NULL,
 classification VARCHAR(24) NOT NULL,
 CHECK (classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH')),
 CHECK (schema_version='1.0.0' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
 PRIMARY KEY (tenant_id,project_id,run_id,write_id),
 FOREIGN KEY (tenant_id,project_id,run_id,write_id) REFERENCES runtime_object_intent(tenant_id,project_id,run_id,write_id),
 FOREIGN KEY (tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK (replay_sha256 REGEXP '^[a-f0-9]{64}$' AND manifest_sha256 REGEXP '^[a-f0-9]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-075-runtime-governance-member dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE runtime_governance_member (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
 project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 run_id CHAR(36) CHARACTER SET ascii NOT NULL,
 write_id CHAR(36) CHARACTER SET ascii NOT NULL,
 member_ordinal INT UNSIGNED NOT NULL,
 upstream_version_id CHAR(36) CHARACTER SET ascii NOT NULL,
 upstream_project_id CHAR(36) CHARACTER SET ascii NULL,
 entity_kind VARCHAR(64) NOT NULL,
 content_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(32) NOT NULL,
 schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL,
 created_at DATETIME(6) NOT NULL,
 classification VARCHAR(24) NOT NULL,
 CHECK (classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH')),
 CHECK (schema_version='1.0.0' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
 PRIMARY KEY (tenant_id,project_id,run_id,write_id,member_ordinal),
 KEY ix_runtime_governance_impact(tenant_id,upstream_version_id,project_id,run_id),
 UNIQUE KEY uq_runtime_governance_version(tenant_id,project_id,run_id,write_id,upstream_version_id),
 FOREIGN KEY (tenant_id,project_id,run_id,write_id) REFERENCES runtime_governance_manifest(tenant_id,project_id,run_id,write_id),
 FOREIGN KEY (tenant_id,upstream_version_id) REFERENCES version_registry(tenant_id,version_id),
 FOREIGN KEY (tenant_id,upstream_project_id) REFERENCES project(tenant_id,project_id),
 FOREIGN KEY (tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK (content_sha256 REGEXP '^[a-f0-9]{64}$' AND member_ordinal<4096)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-075-runtime-governance-edge dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE runtime_governance_edge (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
 project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 run_id CHAR(36) CHARACTER SET ascii NOT NULL,
 write_id CHAR(36) CHARACTER SET ascii NOT NULL,
 edge_ordinal INT UNSIGNED NOT NULL,
 consumer_version_id CHAR(36) CHARACTER SET ascii NOT NULL,
 upstream_version_id CHAR(36) CHARACTER SET ascii NOT NULL,
 relation_kind VARCHAR(64) NOT NULL,
 schema_version VARCHAR(32) NOT NULL,
 schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL,
 created_at DATETIME(6) NOT NULL,
 classification VARCHAR(24) NOT NULL,
 CHECK (classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH')),
 CHECK (schema_version='1.0.0' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
 PRIMARY KEY (tenant_id,project_id,run_id,write_id,edge_ordinal),
 UNIQUE KEY uq_runtime_governance_edge(tenant_id,project_id,run_id,write_id,consumer_version_id,upstream_version_id,relation_kind),
 FOREIGN KEY (tenant_id,project_id,run_id,write_id,consumer_version_id) REFERENCES runtime_governance_member(tenant_id,project_id,run_id,write_id,upstream_version_id),
 FOREIGN KEY (tenant_id,project_id,run_id,write_id,upstream_version_id) REFERENCES runtime_governance_member(tenant_id,project_id,run_id,write_id,upstream_version_id),
 FOREIGN KEY (tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK (consumer_version_id<>upstream_version_id AND edge_ordinal<16384)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-075-runtime-registered-object dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE runtime_registered_object (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
 project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 project_key VARCHAR(36) CHARACTER SET ascii GENERATED ALWAYS AS (COALESCE(project_id, 'ENTERPRISE')) STORED,
 run_id CHAR(36) CHARACTER SET ascii NOT NULL,
 write_id CHAR(36) CHARACTER SET ascii NOT NULL,
 artifact_id CHAR(36) CHARACTER SET ascii NOT NULL,
 object_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 object_version VARCHAR(256) NOT NULL,
 receipt_json JSON NOT NULL,
 receipt_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(32) NOT NULL,
 schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL,
 created_at DATETIME(6) NOT NULL,
 classification VARCHAR(24) NOT NULL,
 PRIMARY KEY (tenant_id,project_id,run_id,write_id),
 UNIQUE KEY uq_runtime_registered_artifact(tenant_id,artifact_id),
 FOREIGN KEY (tenant_id,project_id,run_id,write_id) REFERENCES runtime_object_attempt(tenant_id,project_id,run_id,write_id),
 FOREIGN KEY (tenant_id,project_id,run_id,write_id) REFERENCES runtime_governance_manifest(tenant_id,project_id,run_id,write_id),
 FOREIGN KEY (tenant_id,project_key,artifact_id,object_sha256,object_version) REFERENCES artifact(tenant_id,project_key,artifact_id,sha256,object_version),
 FOREIGN KEY (tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK (classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH')),
 CHECK (schema_version='1.0.0' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$' AND receipt_sha256 REGEXP '^[a-f0-9]{64}$' AND object_sha256 REGEXP '^[a-f0-9]{64}$' AND CHAR_LENGTH(object_version)>0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
