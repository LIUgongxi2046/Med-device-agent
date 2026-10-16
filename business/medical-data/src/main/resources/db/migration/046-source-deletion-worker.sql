--liquibase formatted sql

--changeset medical-agent:1.0.0-271-source-deletion-worker dbms:mysql runInTransaction:false failOnError:true
ALTER TABLE deletion_item
  ADD COLUMN lease_owner CHAR(36) CHARACTER SET ascii NULL,
  ADD COLUMN lease_generation BIGINT UNSIGNED NOT NULL DEFAULT 0,
  ADD COLUMN lease_expires_at DATETIME(6) NULL,
  ADD COLUMN execution_evidence JSON NULL,
  ADD COLUMN worker_receipt JSON NULL,
  ADD KEY ix_deletion_worker_ready (tenant_id,status,lease_expires_at,updated_at),
  ADD CONSTRAINT ck_deletion_worker_lease CHECK (
    (lease_owner IS NULL AND lease_expires_at IS NULL)
    OR (lease_owner IS NOT NULL AND lease_expires_at IS NOT NULL AND lease_generation>0 AND status='RUNNING'));
