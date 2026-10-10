--liquibase formatted sql

--changeset medical-agent:1.0.0-152-platform-oidc-client dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE platform_oidc_client (
  client_key_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  issuer VARCHAR(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,
  client_id VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,
  registration_id VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL,
  created_at DATETIME(6) NOT NULL,
  classification VARCHAR(24) NOT NULL,
  revision BIGINT UNSIGNED NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (client_key_sha256),
  CHECK (revision > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

--changeset medical-agent:1.0.0-153-platform-logout-receipt dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE platform_logout_receipt (
  logout_key_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  client_key_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  jti LONGTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,
  subject VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NULL,
  provider_session_id VARCHAR(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NULL,
  token_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  oidc_issued_at DATETIME(6) NOT NULL,
  oidc_expires_at DATETIME(6) NOT NULL,
  status VARCHAR(24) NOT NULL,
  completed_at DATETIME(6) NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL,
  created_at DATETIME(6) NOT NULL,
  classification VARCHAR(24) NOT NULL,
  revision BIGINT UNSIGNED NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (logout_key_sha256),
  KEY ix_platform_logout_pending (client_key_sha256,status,created_at),
  CONSTRAINT fk_platform_logout_client FOREIGN KEY (client_key_sha256) REFERENCES platform_oidc_client(client_key_sha256),
  CHECK (subject IS NOT NULL OR provider_session_id IS NOT NULL),
  CHECK (subject IS NULL OR CHAR_LENGTH(subject) > 0),
  CHECK (provider_session_id IS NULL OR CHAR_LENGTH(provider_session_id) > 0),
  CHECK (oidc_expires_at > oidc_issued_at),
  CHECK (((status='PENDING' AND completed_at IS NULL) OR (status='COMPLETED' AND completed_at IS NOT NULL)) AND revision > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

--changeset medical-agent:1.0.0-154-platform-logout-session dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE platform_logout_session (
  logout_key_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  session_id VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL,
  created_at DATETIME(6) NOT NULL,
  classification VARCHAR(24) NOT NULL,
  PRIMARY KEY (logout_key_sha256,session_id),
  KEY ix_platform_logout_session (session_id),
  CONSTRAINT fk_platform_logout_receipt FOREIGN KEY (logout_key_sha256) REFERENCES platform_logout_receipt(logout_key_sha256),
  CONSTRAINT fk_platform_logout_session FOREIGN KEY (session_id) REFERENCES platform_oidc_session(session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;
