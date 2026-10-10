--liquibase formatted sql

--changeset medical-agent:1.0.0-151-platform-oidc-session dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE platform_oidc_session (
  session_id VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,
  identity_id CHAR(36) CHARACTER SET ascii NOT NULL,
  registration_id VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,
  client_id VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,
  provider_session_id VARCHAR(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NULL,
  oidc_issued_at DATETIME(6) NOT NULL,
  status VARCHAR(24) NOT NULL,
  revoked_at DATETIME(6) NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL,
  created_at DATETIME(6) NOT NULL,
  classification VARCHAR(24) NOT NULL,
  revision BIGINT UNSIGNED NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (session_id),
  KEY ix_platform_session_identity (identity_id, status),
  KEY ix_platform_session_provider (registration_id, provider_session_id, status),
  CONSTRAINT fk_platform_session_identity FOREIGN KEY (identity_id) REFERENCES platform_identity(identity_id),
  CHECK ((status = 'ACTIVE' AND revoked_at IS NULL) OR (status = 'REVOKED' AND revoked_at IS NOT NULL)),
  CHECK (revision > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;
