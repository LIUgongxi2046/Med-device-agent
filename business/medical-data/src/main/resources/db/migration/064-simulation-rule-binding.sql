--liquibase formatted sql
-- Additive SIM-only purpose bindings. Existing rule content bytes and digests are unchanged.
--changeset medical-agent:064-01-simulation-rule-binding dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE simulation_rule_binding (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  rule_version CHAR(36) CHARACTER SET ascii NOT NULL,
  content_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  dependency_use VARCHAR(32) CHARACTER SET ascii NOT NULL,
  binding_json JSON NOT NULL,
  binding_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  schema_version VARCHAR(32) NOT NULL,
  schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL,
  created_at DATETIME(6) NOT NULL,
  classification VARCHAR(24) NOT NULL,
  PRIMARY KEY (tenant_id,rule_version),
  UNIQUE KEY uq_sim_rule_binding_hash (tenant_id,rule_version,binding_sha256),
  FOREIGN KEY (tenant_id,rule_version,content_sha256) REFERENCES rule_version (tenant_id,rule_version,content_sha256),
  FOREIGN KEY (tenant_id,created_by) REFERENCES tenant_member (tenant_id,user_id),
  CHECK (dependency_use='SIMULATION_EVALUATION' AND schema_version='1.0.0'),
  CHECK (content_sha256 REGEXP '^[a-f0-9]{64}$' AND binding_sha256 REGEXP '^[a-f0-9]{64}$' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
  CHECK (JSON_TYPE(binding_json)='OBJECT' AND JSON_LENGTH(binding_json)=6
    AND JSON_CONTAINS_PATH(binding_json,'all','$.schema_version','$.tenant_id','$.rule_version','$.content_sha256','$.dependency_use','$.production_eligible')
    AND JSON_UNQUOTE(JSON_EXTRACT(binding_json,'$.schema_version')) <=> schema_version
    AND JSON_UNQUOTE(JSON_EXTRACT(binding_json,'$.tenant_id')) <=> tenant_id
    AND JSON_UNQUOTE(JSON_EXTRACT(binding_json,'$.rule_version')) <=> rule_version
    AND JSON_UNQUOTE(JSON_EXTRACT(binding_json,'$.content_sha256')) <=> content_sha256
    AND JSON_UNQUOTE(JSON_EXTRACT(binding_json,'$.dependency_use')) <=> dependency_use
    AND JSON_TYPE(JSON_EXTRACT(binding_json,'$.production_eligible'))='BOOLEAN'
    AND JSON_EXTRACT(binding_json,'$.production_eligible')=CAST('false' AS JSON)),
  CHECK (classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:064-02-simulation-rule-approval-binding dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE simulation_rule_approval_binding (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  decision_id CHAR(36) CHARACTER SET ascii NOT NULL,
  rule_version CHAR(36) CHARACTER SET ascii NOT NULL,
  binding_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL,
  created_at DATETIME(6) NOT NULL,
  classification VARCHAR(24) NOT NULL,
  PRIMARY KEY (tenant_id,decision_id),
  FOREIGN KEY (tenant_id,decision_id) REFERENCES rule_approval_decision (tenant_id,decision_id),
  FOREIGN KEY (tenant_id,rule_version,binding_sha256) REFERENCES simulation_rule_binding (tenant_id,rule_version,binding_sha256),
  FOREIGN KEY (tenant_id,created_by) REFERENCES tenant_member (tenant_id,user_id),
  CHECK (classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH')),
  CHECK (binding_sha256 REGEXP '^[a-f0-9]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
