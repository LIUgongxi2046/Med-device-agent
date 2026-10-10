--liquibase formatted sql

--changeset medical-agent:1.0.0-204-ruoyi-dept dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE sys_dept (
  dept_id BIGINT NOT NULL AUTO_INCREMENT, parent_id BIGINT DEFAULT 0, ancestors VARCHAR(50) DEFAULT '',
  dept_name VARCHAR(30) DEFAULT '', order_num INT DEFAULT 0, leader VARCHAR(20) NULL,
  phone VARCHAR(11) NULL, email VARCHAR(50) NULL, status CHAR(1) DEFAULT '0', del_flag CHAR(1) DEFAULT '0',
  create_by VARCHAR(64) DEFAULT '', create_time DATETIME NULL, update_by VARCHAR(64) DEFAULT '', update_time DATETIME NULL,
  PRIMARY KEY (dept_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

--changeset medical-agent:1.0.0-205-ruoyi-user dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE sys_user (
  user_id BIGINT NOT NULL AUTO_INCREMENT, dept_id BIGINT NULL,
  user_name VARCHAR(128) NOT NULL, nick_name VARCHAR(128) NOT NULL, user_type VARCHAR(2) DEFAULT '00',
  email VARCHAR(50) DEFAULT '', phonenumber VARCHAR(11) DEFAULT '', sex CHAR(1) DEFAULT '0', avatar VARCHAR(100) DEFAULT '',
  password VARCHAR(100) DEFAULT '', status CHAR(1) DEFAULT '0', del_flag CHAR(1) DEFAULT '0',
  login_ip VARCHAR(128) DEFAULT '', login_date DATETIME NULL, pwd_update_date DATETIME NULL,
  create_by VARCHAR(64) DEFAULT '', create_time DATETIME NULL, update_by VARCHAR(64) DEFAULT '', update_time DATETIME NULL,
  remark VARCHAR(500) NULL, PRIMARY KEY (user_id), UNIQUE KEY uq_ruoyi_username (user_name)
) ENGINE=InnoDB AUTO_INCREMENT=100 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

--changeset medical-agent:1.0.0-206-ruoyi-post dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE sys_post (
  post_id BIGINT NOT NULL AUTO_INCREMENT, post_code VARCHAR(64) NOT NULL, post_name VARCHAR(50) NOT NULL,
  post_sort INT NOT NULL, status CHAR(1) NOT NULL, create_by VARCHAR(64) DEFAULT '', create_time DATETIME NULL,
  update_by VARCHAR(64) DEFAULT '', update_time DATETIME NULL, remark VARCHAR(500) NULL, PRIMARY KEY (post_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

--changeset medical-agent:1.0.0-207-ruoyi-role dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE sys_role (
  role_id BIGINT NOT NULL AUTO_INCREMENT, role_name VARCHAR(30) NOT NULL, role_key VARCHAR(100) NOT NULL,
  role_sort INT NOT NULL, data_scope CHAR(1) DEFAULT '1', menu_check_strictly TINYINT DEFAULT 1,
  dept_check_strictly TINYINT DEFAULT 1, status CHAR(1) NOT NULL, del_flag CHAR(1) DEFAULT '0',
  create_by VARCHAR(64) DEFAULT '', create_time DATETIME NULL, update_by VARCHAR(64) DEFAULT '', update_time DATETIME NULL,
  remark VARCHAR(500) NULL, PRIMARY KEY (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

--changeset medical-agent:1.0.0-208-ruoyi-user-role dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE sys_user_role (user_id BIGINT NOT NULL, role_id BIGINT NOT NULL, PRIMARY KEY (user_id,role_id))
ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

--changeset medical-agent:1.0.0-209-ruoyi-role-dept dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE sys_role_dept (role_id BIGINT NOT NULL, dept_id BIGINT NOT NULL, PRIMARY KEY (role_id,dept_id))
ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

--changeset medical-agent:1.0.0-210-ruoyi-user-post dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE sys_user_post (user_id BIGINT NOT NULL, post_id BIGINT NOT NULL, PRIMARY KEY (user_id,post_id))
ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

--changeset medical-agent:1.0.0-211-ruoyi-config dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE sys_config (
  config_id INT NOT NULL AUTO_INCREMENT, config_name VARCHAR(100) DEFAULT '', config_key VARCHAR(100) DEFAULT '',
  config_value VARCHAR(500) DEFAULT '', config_type CHAR(1) DEFAULT 'N', create_by VARCHAR(64) DEFAULT '',
  create_time DATETIME NULL, update_by VARCHAR(64) DEFAULT '', update_time DATETIME NULL, remark VARCHAR(500) NULL,
  PRIMARY KEY (config_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;
