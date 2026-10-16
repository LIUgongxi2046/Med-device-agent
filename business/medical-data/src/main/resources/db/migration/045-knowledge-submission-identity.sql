--liquibase formatted sql

--changeset medical-agent:1.0.0-269-knowledge-submission-pending-identity dbms:mysql runInTransaction:false failOnError:true
ALTER TABLE knowledge_submission
  ADD COLUMN target_project_key VARCHAR(36) CHARACTER SET ascii
    GENERATED ALWAYS AS (COALESCE(project_id, 'ENTERPRISE')) STORED,
  ADD COLUMN pending_artifact_id CHAR(36) CHARACTER SET ascii
    GENERATED ALWAYS AS (CASE WHEN status='PENDING' THEN artifact_id ELSE NULL END) STORED,
  ADD UNIQUE KEY uq_knowledge_submission_pending
    (tenant_id, target_project_key, pending_artifact_id);

--changeset medical-agent:1.0.0-270-source-organization-category dbms:mysql runInTransaction:false failOnError:true
ALTER TABLE source_organization
  ADD COLUMN category VARCHAR(32) NULL AFTER folder_id,
  ADD KEY ix_source_organization_category (tenant_id, category);
