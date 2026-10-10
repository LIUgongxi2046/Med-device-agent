--liquibase formatted sql

--changeset medical-agent:1.0.0-186-operation-retention dbms:mysql runInTransaction:false failOnError:true
ALTER TABLE operation_record
  ADD COLUMN dedup_expires_at DATETIME(6) NULL,
  ADD COLUMN dedup_policy_revision CHAR(36) CHARACTER SET ascii NULL,
  ADD CONSTRAINT fk_operation_dedup_policy FOREIGN KEY (tenant_id,dedup_policy_revision)
    REFERENCES version_registry (tenant_id,version_id),
  ADD CONSTRAINT ck_operation_dedup_policy CHECK (
    (dedup_expires_at IS NULL AND dedup_policy_revision IS NULL) OR
    (dedup_expires_at IS NOT NULL AND dedup_policy_revision IS NOT NULL AND dedup_expires_at > created_at)
  );
