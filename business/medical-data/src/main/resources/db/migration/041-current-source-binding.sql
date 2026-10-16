--liquibase formatted sql

--changeset medical-agent:1.0.0-259-current-source-binding dbms:mysql runInTransaction:false failOnError:true
ALTER TABLE project_source_binding
  ADD COLUMN is_current BOOLEAN NOT NULL DEFAULT FALSE AFTER decision_manifest;

UPDATE project_source_binding b
JOIN (
  SELECT tenant_id,project_id,source_version,MIN(binding_revision) AS binding_revision
  FROM project_source_binding
  GROUP BY tenant_id,project_id,source_version
  HAVING COUNT(*)=1
) current_binding ON current_binding.tenant_id=b.tenant_id AND current_binding.project_id=b.project_id
  AND current_binding.source_version=b.source_version AND current_binding.binding_revision=b.binding_revision
SET b.is_current=TRUE;

ALTER TABLE project_source_binding
  ADD COLUMN current_source_version CHAR(36) CHARACTER SET ascii
    GENERATED ALWAYS AS (IF(is_current,source_version,NULL)) STORED,
  ADD UNIQUE KEY uq_project_source_current (tenant_id,project_id,current_source_version),
  ADD KEY ix_project_source_current (tenant_id,project_id,is_current,applicability,source_version);
