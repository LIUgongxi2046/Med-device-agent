--liquibase formatted sql

--changeset medical-agent:1.0.0-094-task-run-replay-governance-manifest dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE task_run_replay_governance_manifest (
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
 FOREIGN KEY (tenant_id,project_id,run_id,write_id) REFERENCES task_run_replay_attempt(tenant_id,project_id,run_id,write_id),
 FOREIGN KEY (tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK (replay_sha256 REGEXP '^[a-f0-9]{64}$' AND manifest_sha256 REGEXP '^[a-f0-9]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-094-runtime-governance-member dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE task_run_replay_governance_member (
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
 KEY ix_task_run_replay_governance_impact(tenant_id,upstream_version_id,project_id,run_id),
 UNIQUE KEY uq_task_run_replay_governance_version(tenant_id,project_id,run_id,write_id,upstream_version_id),
 FOREIGN KEY (tenant_id,project_id,run_id,write_id) REFERENCES task_run_replay_governance_manifest(tenant_id,project_id,run_id,write_id),
 FOREIGN KEY (tenant_id,upstream_version_id) REFERENCES version_registry(tenant_id,version_id),
 FOREIGN KEY (tenant_id,upstream_project_id) REFERENCES project(tenant_id,project_id),
 FOREIGN KEY (tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK (content_sha256 REGEXP '^[a-f0-9]{64}$' AND member_ordinal<4096)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-094-runtime-governance-edge dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE task_run_replay_governance_edge (
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
 UNIQUE KEY uq_task_run_replay_governance_edge(tenant_id,project_id,run_id,write_id,consumer_version_id,upstream_version_id,relation_kind),
 FOREIGN KEY (tenant_id,project_id,run_id,write_id,consumer_version_id) REFERENCES task_run_replay_governance_member(tenant_id,project_id,run_id,write_id,upstream_version_id),
 FOREIGN KEY (tenant_id,project_id,run_id,write_id,upstream_version_id) REFERENCES task_run_replay_governance_member(tenant_id,project_id,run_id,write_id,upstream_version_id),
 FOREIGN KEY (tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK (consumer_version_id<>upstream_version_id AND edge_ordinal<16384)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

