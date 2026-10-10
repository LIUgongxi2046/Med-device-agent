--liquibase formatted sql

--changeset medical-agent:1.0.0-150-platform-identity dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE platform_identity (
  identity_id CHAR(36) CHARACTER SET ascii NOT NULL,
  issuer VARCHAR(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,
  subject VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,
  user_id CHAR(36) CHARACTER SET ascii NOT NULL,
  status VARCHAR(24) NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL,
  created_at DATETIME(6) NOT NULL,
  classification VARCHAR(24) NOT NULL,
  revision BIGINT UNSIGNED NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (identity_id),
  UNIQUE KEY uq_platform_subject (issuer, subject),
  KEY ix_platform_user (user_id, status),
  CHECK (status IN ('ACTIVE','DISABLED')),
  CHECK (revision > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;
