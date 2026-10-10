--liquibase formatted sql

--changeset medical-agent:1.0.0-174-member-invitation dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE member_invitation (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  invitation_id CHAR(36) CHARACTER SET ascii NOT NULL,
  user_id CHAR(36) CHARACTER SET ascii NOT NULL,
  identity_id CHAR(36) CHARACTER SET ascii NOT NULL,
  roles JSON NOT NULL,
  scope JSON NOT NULL,
  authorization_manifest JSON NOT NULL,
  basis_version CHAR(36) CHARACTER SET ascii NOT NULL,
  credential_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  expires_at DATETIME(6) NOT NULL,
  status VARCHAR(24) COLLATE utf8mb4_0900_bin NOT NULL,
  accepted_by CHAR(36) CHARACTER SET ascii NULL,
  accepted_at DATETIME(6) NULL,
  terminal_at DATETIME(6) NULL,
  terminal_reason TEXT NULL,
  pending_user_id CHAR(36) CHARACTER SET ascii GENERATED ALWAYS AS (IF(status='PENDING',user_id,NULL)) STORED,
  schema_version VARCHAR(32) NOT NULL,
  schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL,
  created_at DATETIME(6) NOT NULL,
  classification VARCHAR(24) NOT NULL,
  revision BIGINT UNSIGNED NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (tenant_id, invitation_id),
  UNIQUE KEY uq_invitation_subject (tenant_id, invitation_id, user_id),
  UNIQUE KEY uq_invitation_pending (tenant_id, pending_user_id),
  KEY ix_invitation_expiry (tenant_id, status, expires_at),
  CONSTRAINT fk_invitation_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (tenant_id),
  CONSTRAINT fk_invitation_identity FOREIGN KEY (tenant_id, identity_id) REFERENCES identity_binding (tenant_id, identity_id),
  CONSTRAINT fk_invitation_member FOREIGN KEY (tenant_id, user_id) REFERENCES tenant_member (tenant_id, user_id),
  CONSTRAINT fk_invitation_basis FOREIGN KEY (tenant_id, basis_version) REFERENCES version_registry (tenant_id, version_id),
  CONSTRAINT fk_invitation_acceptor FOREIGN KEY (tenant_id, accepted_by) REFERENCES tenant_member (tenant_id, user_id),
  CONSTRAINT ck_invitation_status CHECK (status IN ('PENDING','ACCEPTED','REVOKED','EXPIRED')),
  CONSTRAINT ck_invitation_expiry CHECK (expires_at > created_at),
  CONSTRAINT ck_invitation_roles CHECK (JSON_TYPE(roles)='ARRAY' AND JSON_LENGTH(roles)>0),
  CONSTRAINT ck_invitation_scope CHECK (JSON_TYPE(scope)='OBJECT'),
  CONSTRAINT ck_invitation_authorizations CHECK (JSON_TYPE(authorization_manifest)='ARRAY'),
  CONSTRAINT ck_invitation_credential CHECK (REGEXP_LIKE(credential_sha256,'^[0-9a-f]{64}$','c')),
  CONSTRAINT ck_invitation_result CHECK (
      (status='PENDING' AND accepted_by IS NULL AND accepted_at IS NULL AND terminal_at IS NULL AND terminal_reason IS NULL)
      OR (status='ACCEPTED' AND accepted_by IS NOT NULL AND accepted_by=user_id AND accepted_at IS NOT NULL
          AND accepted_at>=created_at AND accepted_at<expires_at AND terminal_at IS NOT NULL
          AND terminal_at=accepted_at AND terminal_reason IS NULL)
      OR (status IN ('REVOKED','EXPIRED') AND accepted_by IS NULL AND accepted_at IS NULL
          AND terminal_at IS NOT NULL AND terminal_at>=created_at AND terminal_reason IS NOT NULL
          AND CHAR_LENGTH(TRIM(terminal_reason))>0
          AND ((status='REVOKED' AND terminal_at<expires_at) OR (status='EXPIRED' AND terminal_at>=expires_at)))
  ),
  CONSTRAINT ck_invitation_revision CHECK (revision > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-175-platform-invitation-route dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE platform_invitation_route (
  user_id CHAR(36) CHARACTER SET ascii NOT NULL,
  invitation_id CHAR(36) CHARACTER SET ascii NOT NULL,
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL,
  created_at DATETIME(6) NOT NULL,
  classification VARCHAR(24) NOT NULL,
  revision BIGINT UNSIGNED NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (user_id, invitation_id),
  UNIQUE KEY uq_platform_invitation_id (invitation_id),
  CONSTRAINT fk_invitation_route FOREIGN KEY (tenant_id, invitation_id, user_id)
    REFERENCES member_invitation (tenant_id, invitation_id, user_id),
  CONSTRAINT ck_invitation_route_revision CHECK (revision > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;
