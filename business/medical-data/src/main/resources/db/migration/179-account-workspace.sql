--liquibase formatted sql

--changeset medical-agent:account-workspace-20261022 dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE platform_account_workspace (
  user_id CHAR(36) CHARACTER SET ascii NOT NULL,
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL,
  created_at DATETIME(6) NOT NULL,
  classification VARCHAR(24) NOT NULL,
  revision BIGINT UNSIGNED NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (user_id),
  FOREIGN KEY (user_id, tenant_id) REFERENCES platform_member_route (user_id, tenant_id),
  CHECK (revision > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;
