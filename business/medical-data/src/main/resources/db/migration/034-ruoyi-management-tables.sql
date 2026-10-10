--liquibase formatted sql

--changeset medical-agent:1.0.0-227-ruoyi-operation-log dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE sys_oper_log (
  oper_id BIGINT NOT NULL AUTO_INCREMENT, title VARCHAR(50) DEFAULT '', business_type INT DEFAULT 0,
  method VARCHAR(200) DEFAULT '', request_method VARCHAR(10) DEFAULT '', operator_type INT DEFAULT 0,
  oper_name VARCHAR(50) DEFAULT '', dept_name VARCHAR(50) DEFAULT '', oper_url VARCHAR(255) DEFAULT '',
  oper_ip VARCHAR(128) DEFAULT '', oper_location VARCHAR(255) DEFAULT '', oper_param VARCHAR(2000) DEFAULT '',
  json_result VARCHAR(2000) DEFAULT '', status INT DEFAULT 0, error_msg VARCHAR(2000) DEFAULT '',
  oper_time DATETIME, cost_time BIGINT DEFAULT 0, PRIMARY KEY (oper_id),
  KEY idx_sys_oper_log_bt (business_type), KEY idx_sys_oper_log_s (status), KEY idx_sys_oper_log_ot (oper_time)
) ENGINE=InnoDB AUTO_INCREMENT=100 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

--changeset medical-agent:1.0.0-228-ruoyi-dictionary-type dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE sys_dict_type (
  dict_id BIGINT NOT NULL AUTO_INCREMENT, dict_name VARCHAR(100) DEFAULT '', dict_type VARCHAR(100) DEFAULT '',
  status CHAR(1) DEFAULT '0', create_by VARCHAR(64) DEFAULT '', create_time DATETIME,
  update_by VARCHAR(64) DEFAULT '', update_time DATETIME, remark VARCHAR(500) DEFAULT NULL,
  PRIMARY KEY (dict_id), UNIQUE (dict_type)
) ENGINE=InnoDB AUTO_INCREMENT=100 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

--changeset medical-agent:1.0.0-229-ruoyi-dictionary-data dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE sys_dict_data (
  dict_code BIGINT NOT NULL AUTO_INCREMENT, dict_sort INT DEFAULT 0, dict_label VARCHAR(100) DEFAULT '',
  dict_value VARCHAR(100) DEFAULT '', dict_type VARCHAR(100) DEFAULT '', css_class VARCHAR(100) DEFAULT NULL,
  list_class VARCHAR(100) DEFAULT NULL, is_default CHAR(1) DEFAULT 'N', status CHAR(1) DEFAULT '0',
  create_by VARCHAR(64) DEFAULT '', create_time DATETIME, update_by VARCHAR(64) DEFAULT '', update_time DATETIME,
  remark VARCHAR(500) DEFAULT NULL, PRIMARY KEY (dict_code)
) ENGINE=InnoDB AUTO_INCREMENT=100 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

--changeset medical-agent:1.0.0-230-ruoyi-login-log dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE sys_logininfor (
  info_id BIGINT NOT NULL AUTO_INCREMENT, user_name VARCHAR(50) DEFAULT '', ipaddr VARCHAR(128) DEFAULT '',
  login_location VARCHAR(255) DEFAULT '', browser VARCHAR(50) DEFAULT '', os VARCHAR(50) DEFAULT '',
  status CHAR(1) DEFAULT '0', msg VARCHAR(255) DEFAULT '', login_time DATETIME, PRIMARY KEY (info_id),
  KEY idx_sys_logininfor_s (status), KEY idx_sys_logininfor_lt (login_time)
) ENGINE=InnoDB AUTO_INCREMENT=100 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

--changeset medical-agent:1.0.0-231-ruoyi-scheduled-job dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE sys_job (
  job_id BIGINT NOT NULL AUTO_INCREMENT, job_name VARCHAR(64) DEFAULT '', job_group VARCHAR(64) DEFAULT 'DEFAULT',
  invoke_target VARCHAR(500) NOT NULL, cron_expression VARCHAR(255) DEFAULT '', misfire_policy VARCHAR(20) DEFAULT '3',
  concurrent CHAR(1) DEFAULT '1', status CHAR(1) DEFAULT '0', create_by VARCHAR(64) DEFAULT '', create_time DATETIME,
  update_by VARCHAR(64) DEFAULT '', update_time DATETIME, remark VARCHAR(500) DEFAULT '',
  PRIMARY KEY (job_id, job_name, job_group)
) ENGINE=InnoDB AUTO_INCREMENT=100 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

--changeset medical-agent:1.0.0-232-ruoyi-scheduled-job-log dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE sys_job_log (
  job_log_id BIGINT NOT NULL AUTO_INCREMENT, job_name VARCHAR(64) NOT NULL, job_group VARCHAR(64) NOT NULL,
  invoke_target VARCHAR(500) NOT NULL, job_message VARCHAR(500), status CHAR(1) DEFAULT '0',
  exception_info VARCHAR(2000) DEFAULT '', start_time DATETIME, end_time DATETIME, create_time DATETIME,
  PRIMARY KEY (job_log_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

--changeset medical-agent:1.0.0-233-ruoyi-notice dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE sys_notice (
  notice_id INT NOT NULL AUTO_INCREMENT, notice_title VARCHAR(50) NOT NULL, notice_type CHAR(1) NOT NULL,
  notice_content LONGBLOB DEFAULT NULL, status CHAR(1) DEFAULT '0', create_by VARCHAR(64) DEFAULT '',
  create_time DATETIME, update_by VARCHAR(64) DEFAULT '', update_time DATETIME, remark VARCHAR(255) DEFAULT NULL,
  PRIMARY KEY (notice_id)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

--changeset medical-agent:1.0.0-234-ruoyi-notice-read dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE sys_notice_read (
  read_id BIGINT NOT NULL AUTO_INCREMENT, notice_id INT NOT NULL, user_id BIGINT NOT NULL, read_time DATETIME NOT NULL,
  PRIMARY KEY (read_id), UNIQUE KEY uk_user_notice (user_id, notice_id)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

--changeset medical-agent:1.0.0-235-ruoyi-generation-table dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE gen_table (
  table_id BIGINT NOT NULL AUTO_INCREMENT, table_name VARCHAR(200) DEFAULT '', table_comment VARCHAR(500) DEFAULT '',
  sub_table_name VARCHAR(64) DEFAULT NULL, sub_table_fk_name VARCHAR(64) DEFAULT NULL, class_name VARCHAR(100) DEFAULT '',
  tpl_category VARCHAR(200) DEFAULT 'crud', tpl_web_type VARCHAR(30) DEFAULT '', package_name VARCHAR(100),
  module_name VARCHAR(30), business_name VARCHAR(30), function_name VARCHAR(50), function_author VARCHAR(50),
  form_col_num INT DEFAULT 1, gen_type CHAR(1) DEFAULT '0', gen_path VARCHAR(200) DEFAULT '/', options VARCHAR(1000),
  create_by VARCHAR(64) DEFAULT '', create_time DATETIME, update_by VARCHAR(64) DEFAULT '', update_time DATETIME,
  remark VARCHAR(500) DEFAULT NULL, PRIMARY KEY (table_id)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

--changeset medical-agent:1.0.0-236-ruoyi-generation-column dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE gen_table_column (
  column_id BIGINT NOT NULL AUTO_INCREMENT, table_id BIGINT, column_name VARCHAR(200), column_comment VARCHAR(500),
  column_type VARCHAR(100), java_type VARCHAR(500), java_field VARCHAR(200), is_pk CHAR(1), is_increment CHAR(1),
  is_required CHAR(1), is_insert CHAR(1), is_edit CHAR(1), is_list CHAR(1), is_query CHAR(1), query_type VARCHAR(200) DEFAULT 'EQ',
  html_type VARCHAR(200), dict_type VARCHAR(200) DEFAULT '', sort INT, create_by VARCHAR(64) DEFAULT '', create_time DATETIME,
  update_by VARCHAR(64) DEFAULT '', update_time DATETIME, PRIMARY KEY (column_id)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;
