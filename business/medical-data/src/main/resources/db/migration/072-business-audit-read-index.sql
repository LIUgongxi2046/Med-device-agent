--liquibase formatted sql
-- Additive supported project/action/time audit scan index. No data/grants/producer changes.
--changeset medical-agent:072-01-business-audit-read-index dbms:mysql runInTransaction:false failOnError:true
ALTER TABLE audit_event ADD KEY ix_audit_project_action_time (tenant_id,project_id,action,created_at,audit_id);
