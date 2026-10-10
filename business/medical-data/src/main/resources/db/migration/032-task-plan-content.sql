--liquibase formatted sql

--changeset medical-agent:1.0.0-223-task-input-revision dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE task_input_revision (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  project_id CHAR(36) CHARACTER SET ascii NOT NULL,
  task_id CHAR(36) CHARACTER SET ascii NOT NULL,
  task_revision CHAR(36) CHARACTER SET ascii NOT NULL,
  operation_id CHAR(36) CHARACTER SET ascii NOT NULL,
  document_type VARCHAR(64) NOT NULL,
  owner_id CHAR(36) CHARACTER SET ascii NOT NULL,
  goal LONGTEXT NOT NULL,
  based_on_document_revision CHAR(36) CHARACTER SET ascii NULL,
  request_json JSON NOT NULL,
  request_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  scope_json JSON NOT NULL,
  scope_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  authority_grant CHAR(36) CHARACTER SET ascii NOT NULL,
  authority_revision BIGINT UNSIGNED NOT NULL,
  auth_epoch BIGINT UNSIGNED NOT NULL,
  governance_epoch BIGINT UNSIGNED NOT NULL,
  schema_version VARCHAR(32) NOT NULL,
  schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL,
  created_at DATETIME(6) NOT NULL,
  classification VARCHAR(24) NOT NULL,
  PRIMARY KEY (tenant_id,project_id,task_revision),
  CONSTRAINT fk_task_input_task FOREIGN KEY (tenant_id,project_id,task_id,task_revision)
    REFERENCES task_revision (tenant_id,project_id,task_id,task_revision),
  CONSTRAINT fk_task_input_document FOREIGN KEY (tenant_id,project_id,based_on_document_revision)
    REFERENCES document_revision (tenant_id,project_id,document_revision),
  CONSTRAINT fk_task_input_operation FOREIGN KEY (tenant_id,operation_id)
    REFERENCES operation_record (tenant_id,operation_id),
  CONSTRAINT fk_task_input_actor FOREIGN KEY (tenant_id,created_by) REFERENCES tenant_member (tenant_id,user_id),
  CONSTRAINT fk_task_input_owner FOREIGN KEY (tenant_id,owner_id) REFERENCES tenant_member (tenant_id,user_id),
  CONSTRAINT fk_task_input_authority FOREIGN KEY (tenant_id,authority_grant) REFERENCES permission_grant (tenant_id,grant_id),
  CONSTRAINT ck_task_input_json CHECK (JSON_TYPE(request_json)='OBJECT' AND JSON_TYPE(scope_json)='OBJECT'
    AND JSON_CONTAINS_PATH(request_json,'all','$.project_id','$.document_type','$.owner_id','$.goal','$.scope_selection')=1
    AND JSON_UNQUOTE(JSON_EXTRACT(request_json,'$.project_id'))=project_id
    AND JSON_UNQUOTE(JSON_EXTRACT(request_json,'$.document_type'))=document_type
    AND JSON_UNQUOTE(JSON_EXTRACT(request_json,'$.owner_id'))=owner_id
    AND JSON_UNQUOTE(JSON_EXTRACT(request_json,'$.goal'))=goal
    AND JSON_EXTRACT(request_json,'$.scope_selection')=scope_json AND CHAR_LENGTH(goal)>0),
  CONSTRAINT ck_task_input_watermarks CHECK (authority_revision>0 AND auth_epoch>0 AND governance_epoch>0),
  CONSTRAINT ck_task_input_schema CHECK (schema_version='1.0.0'
    AND request_sha256 REGEXP '^[a-f0-9]{64}$' AND scope_sha256 REGEXP '^[a-f0-9]{64}$'
    AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-224-plan-content-identity dbms:mysql runInTransaction:false failOnError:true
ALTER TABLE plan_revision
  ADD UNIQUE KEY uq_plan_content_basis (tenant_id,project_id,task_id,task_revision,plan_revision,template_version,snapshot_id,content_sha256),
  ADD UNIQUE KEY uq_plan_task_content (tenant_id,project_id,task_id,plan_revision,content_sha256);

--changeset medical-agent:1.0.0-225-plan-content-revision dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE plan_content_revision (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  project_id CHAR(36) CHARACTER SET ascii NOT NULL,
  task_id CHAR(36) CHARACTER SET ascii NOT NULL,
  task_revision CHAR(36) CHARACTER SET ascii NOT NULL,
  plan_revision CHAR(36) CHARACTER SET ascii NOT NULL,
  template_version CHAR(36) CHARACTER SET ascii NOT NULL,
  snapshot_id CHAR(36) CHARACTER SET ascii NOT NULL,
  content_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  content_json JSON NOT NULL,
  base_version CHAR(36) CHARACTER SET ascii NULL,
  base_version_sha256 CHAR(64) CHARACTER SET ascii NULL,
  operation_id CHAR(36) CHARACTER SET ascii NOT NULL,
  expected_revision BIGINT UNSIGNED NOT NULL,
  reason TEXT NOT NULL,
  schema_ref VARCHAR(255) CHARACTER SET ascii NOT NULL,
  schema_version VARCHAR(32) NOT NULL,
  schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  authority_grant CHAR(36) CHARACTER SET ascii NOT NULL,
  authority_revision BIGINT UNSIGNED NOT NULL,
  auth_epoch BIGINT UNSIGNED NOT NULL,
  governance_epoch BIGINT UNSIGNED NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL,
  created_at DATETIME(6) NOT NULL,
  classification VARCHAR(24) NOT NULL,
  PRIMARY KEY (tenant_id,project_id,plan_revision),
  CONSTRAINT fk_plan_content_basis FOREIGN KEY (tenant_id,project_id,task_id,task_revision,plan_revision,template_version,snapshot_id,content_sha256)
    REFERENCES plan_revision (tenant_id,project_id,task_id,task_revision,plan_revision,template_version,snapshot_id,content_sha256),
  CONSTRAINT fk_plan_content_parent FOREIGN KEY (tenant_id,project_id,task_id,base_version,base_version_sha256)
    REFERENCES plan_revision (tenant_id,project_id,task_id,plan_revision,content_sha256),
  CONSTRAINT fk_plan_content_operation FOREIGN KEY (tenant_id,operation_id) REFERENCES operation_record (tenant_id,operation_id),
  CONSTRAINT fk_plan_content_actor FOREIGN KEY (tenant_id,created_by) REFERENCES tenant_member (tenant_id,user_id),
  CONSTRAINT fk_plan_content_authority FOREIGN KEY (tenant_id,authority_grant) REFERENCES permission_grant (tenant_id,grant_id),
  CONSTRAINT ck_plan_content_json CHECK (JSON_TYPE(content_json)='OBJECT'
    AND JSON_CONTAINS_PATH(content_json,'all','$.template_version','$.profile_revision','$.requirement_node_ids',
      '$.sections','$.questions','$.pending_condition_ids')=1
    AND JSON_UNQUOTE(JSON_EXTRACT(content_json,'$.template_version'))=template_version),
  CONSTRAINT ck_plan_content_parent CHECK ((base_version IS NULL AND base_version_sha256 IS NULL)
    OR (base_version IS NOT NULL AND base_version_sha256 IS NOT NULL AND base_version<>plan_revision)),
  CONSTRAINT ck_plan_content_watermarks CHECK (expected_revision>0 AND authority_revision>0 AND auth_epoch>0 AND governance_epoch>0
    AND CHAR_LENGTH(reason) BETWEEN 1 AND 2000),
  CONSTRAINT ck_plan_content_schema CHECK (schema_version='1.0.0'
    AND schema_ref='https://schemas.medical-agent.invalid/1.0.0/agent-design/artifacts.schema.json#/$defs/PlanCandidate'
    AND content_sha256 REGEXP '^[a-f0-9]{64}$' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
