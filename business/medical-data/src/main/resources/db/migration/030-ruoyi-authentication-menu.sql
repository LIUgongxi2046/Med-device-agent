--liquibase formatted sql

--changeset medical-agent:1.0.0-220-ruoyi-menu dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE sys_menu (
  menu_id BIGINT NOT NULL AUTO_INCREMENT, menu_name VARCHAR(50) NOT NULL, parent_id BIGINT DEFAULT 0,
  order_num INT DEFAULT 0, path VARCHAR(200) DEFAULT '', component VARCHAR(255) DEFAULT NULL,
  query VARCHAR(255) DEFAULT NULL, route_name VARCHAR(50) DEFAULT '', is_frame INT DEFAULT 1,
  is_cache INT DEFAULT 0, menu_type CHAR(1) DEFAULT '', visible CHAR(1) DEFAULT '0',
  status CHAR(1) DEFAULT '0', perms VARCHAR(100) DEFAULT NULL, icon VARCHAR(100) DEFAULT '#',
  create_by VARCHAR(64) DEFAULT '', create_time DATETIME NULL, update_by VARCHAR(64) DEFAULT '',
  update_time DATETIME NULL, remark VARCHAR(500) DEFAULT '', PRIMARY KEY (menu_id)
) ENGINE=InnoDB AUTO_INCREMENT=2000 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

--changeset medical-agent:1.0.0-221-ruoyi-role-menu dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE sys_role_menu (role_id BIGINT NOT NULL, menu_id BIGINT NOT NULL, PRIMARY KEY (role_id,menu_id))
ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;
