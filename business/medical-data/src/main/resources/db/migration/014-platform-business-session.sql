--liquibase formatted sql

--changeset medical-agent:1.0.0-158-member-route dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE platform_member_route (
  user_id CHAR(36) CHARACTER SET ascii NOT NULL,
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  member_id CHAR(36) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL,
  created_at DATETIME(6) NOT NULL,
  classification VARCHAR(24) NOT NULL,
  revision BIGINT UNSIGNED NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (user_id, tenant_id),
  FOREIGN KEY (tenant_id, member_id) REFERENCES tenant_member (tenant_id, member_id),
  CHECK (revision > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

--changeset medical-agent:1.0.0-159-session-context dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE platform_session_context (
  session_id VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,
  user_id CHAR(36) CHARACTER SET ascii NOT NULL,
  tenant_id CHAR(36) CHARACTER SET ascii NULL,
  context_revision BIGINT UNSIGNED NOT NULL,
  auth_epoch BIGINT UNSIGNED NULL,
  governance_epoch BIGINT UNSIGNED NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL,
  created_at DATETIME(6) NOT NULL,
  classification VARCHAR(24) NOT NULL,
  revision BIGINT UNSIGNED NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (session_id),
  KEY ix_context_subject (user_id, tenant_id),
  FOREIGN KEY (tenant_id) REFERENCES tenant (tenant_id),
  CHECK (revision > 0),
  CHECK ((tenant_id IS NULL AND context_revision = 0 AND auth_epoch IS NULL AND governance_epoch IS NULL)
      OR (tenant_id IS NOT NULL AND context_revision > 0 AND auth_epoch IS NOT NULL AND governance_epoch IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;
