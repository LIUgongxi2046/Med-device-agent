--liquibase formatted sql

--changeset medical-agent:1.0.0-300-run-step-lease dbms:mysql runInTransaction:false failOnError:true
ALTER TABLE run_step
  ADD COLUMN lease_owner VARCHAR(512) NULL,
  ADD COLUMN lease_expires_at DATETIME(6) NULL,
  ADD KEY ix_step_lease_expiry (tenant_id,status,lease_expires_at),
  ADD CONSTRAINT ck_step_lease_binding CHECK (
    (lease_owner IS NULL AND lease_expires_at IS NULL)
    OR (lease_owner IS NOT NULL AND CHAR_LENGTH(lease_owner)>0 AND lease_expires_at IS NOT NULL AND lease_generation>0));

--changeset medical-agent:1.0.0-301-principal-token-binding dbms:mysql runInTransaction:false failOnError:true
ALTER TABLE principal_token
  ADD COLUMN token_sha256 CHAR(64) CHARACTER SET ascii NULL,
  ADD COLUMN service_uri VARCHAR(512) NULL,
  ADD COLUMN policy_revision CHAR(36) CHARACTER SET ascii NULL,
  ADD COLUMN policy_sha256 CHAR(64) CHARACTER SET ascii NULL,
  ADD COLUMN lease_generation BIGINT UNSIGNED NULL,
  ADD CONSTRAINT ck_token_authority_binding CHECK (
    (token_sha256 IS NULL AND service_uri IS NULL AND policy_revision IS NULL AND policy_sha256 IS NULL AND lease_generation IS NULL)
    OR (token_sha256 IS NOT NULL AND token_sha256 REGEXP '^[a-f0-9]{64}$'
      AND service_uri IS NOT NULL AND CHAR_LENGTH(service_uri)>0
      AND policy_revision IS NOT NULL AND policy_sha256 IS NOT NULL AND policy_sha256 REGEXP '^[a-f0-9]{64}$'
      AND lease_generation IS NOT NULL AND lease_generation>0 AND step_id IS NOT NULL));
