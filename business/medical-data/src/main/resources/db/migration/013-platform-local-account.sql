--liquibase formatted sql

--changeset medical-agent:1.0.0-155-platform-local-account dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE platform_local_account (
  user_id CHAR(36) CHARACTER SET ascii NOT NULL,
  username VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,
  password_hash VARCHAR(100) NOT NULL,
  display_name VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,
  status VARCHAR(24) NOT NULL,
  platform_role VARCHAR(32) NOT NULL,
  failed_attempts BIGINT UNSIGNED NOT NULL,
  locked_until DATETIME(6) NULL,
  credential_revision BIGINT UNSIGNED NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL,
  created_at DATETIME(6) NOT NULL,
  classification VARCHAR(24) NOT NULL,
  revision BIGINT UNSIGNED NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (user_id),
  UNIQUE KEY uq_platform_local_username (username),
  CHECK (status IN ('ACTIVE','DISABLED')),
  CHECK (platform_role IN ('PLATFORM_ADMIN','USER')),
  CHECK (credential_revision > 0 AND revision > 0),
  CHECK (CHAR_LENGTH(username)>0 AND CHAR_LENGTH(display_name)>0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

--changeset medical-agent:1.0.0-156-platform-local-session dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE platform_local_session (
  session_id VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,
  user_id CHAR(36) CHARACTER SET ascii NOT NULL,
  credential_revision BIGINT UNSIGNED NOT NULL,
  status VARCHAR(24) NOT NULL,
  revoked_at DATETIME(6) NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL,
  created_at DATETIME(6) NOT NULL,
  classification VARCHAR(24) NOT NULL,
  revision BIGINT UNSIGNED NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (session_id),
  KEY ix_platform_local_user_session (user_id,status),
  CONSTRAINT fk_platform_local_session_user FOREIGN KEY (user_id) REFERENCES platform_local_account(user_id),
  CHECK ((status='ACTIVE' AND revoked_at IS NULL) OR (status='REVOKED' AND revoked_at IS NOT NULL)),
  CHECK (credential_revision > 0 AND revision > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

--changeset medical-agent:1.0.0-157-platform-local-bootstrap dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE platform_local_bootstrap (
  coordination_key VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NULL,
  created_at DATETIME(6) NOT NULL,
  classification VARCHAR(24) NOT NULL,
  revision BIGINT UNSIGNED NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (coordination_key),
  CHECK (coordination_key='LOCAL'),
  CHECK (revision > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;
