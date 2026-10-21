--liquibase formatted sql
--changeset medical-agent:1.0.0-160-success-reuse dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE task_run_success_reuse (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 run_id CHAR(36) CHARACTER SET ascii NOT NULL, step_id CHAR(36) CHARACTER SET ascii NOT NULL,
 original_run_id CHAR(36) CHARACTER SET ascii NOT NULL, original_step_id CHAR(36) CHARACTER SET ascii NOT NULL,
 stage_kind VARCHAR(8) NOT NULL, operation_id CHAR(36) CHARACTER SET ascii NOT NULL,
 receipt_json JSON NOT NULL, receipt_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
 PRIMARY KEY(tenant_id,run_id,step_id), UNIQUE KEY uq_160_reuse_operation(tenant_id,operation_id),
 FOREIGN KEY(tenant_id,project_id,run_id,step_id) REFERENCES run_step(tenant_id,project_id,run_id,step_id),
 FOREIGN KEY(tenant_id,project_id,original_run_id,original_step_id) REFERENCES run_step(tenant_id,project_id,run_id,step_id),
 FOREIGN KEY(tenant_id,operation_id) REFERENCES operation_record(tenant_id,operation_id),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK(run_id<>original_run_id AND stage_kind IN ('N02','N03','N04','N05')),
 CHECK(schema_version='1.0.0' AND receipt_sha256 REGEXP '^[a-f0-9]{64}$' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK(classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
--changeset medical-agent:1.0.0-160-success-reuse-update dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER trg_160_reuse_update BEFORE UPDATE ON task_run_success_reuse FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable original-success reuse';
--changeset medical-agent:1.0.0-160-success-reuse-delete dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER trg_160_reuse_delete BEFORE DELETE ON task_run_success_reuse FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable original-success reuse';

--changeset medical-agent:1.0.0-160-assembly-success-reuse dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE task_run_assembly_success_reuse (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 run_id CHAR(36) CHARACTER SET ascii NOT NULL, original_run_id CHAR(36) CHARACTER SET ascii NOT NULL,
 operation_id CHAR(36) CHARACTER SET ascii NOT NULL, receipt_json JSON NOT NULL,
 receipt_sha256 CHAR(64) CHARACTER SET ascii NOT NULL, schema_version VARCHAR(32) NOT NULL,
 schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL, created_by CHAR(36) CHARACTER SET ascii NOT NULL,
 created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
 PRIMARY KEY(tenant_id,run_id), UNIQUE KEY uq_160_assembly_reuse_operation(tenant_id,operation_id),
 FOREIGN KEY(tenant_id,project_id,run_id) REFERENCES execution_registry(tenant_id,project_id,run_id),
 FOREIGN KEY(tenant_id,project_id,original_run_id) REFERENCES execution_registry(tenant_id,project_id,run_id),
 FOREIGN KEY(tenant_id,run_id) REFERENCES task_run(tenant_id,run_id),
 FOREIGN KEY(tenant_id,original_run_id) REFERENCES task_run(tenant_id,run_id),
 FOREIGN KEY(tenant_id,operation_id) REFERENCES operation_record(tenant_id,operation_id),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK(run_id<>original_run_id AND schema_version='1.0.0'),
 CHECK(receipt_sha256 REGEXP '^[a-f0-9]{64}$' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK(classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
--changeset medical-agent:1.0.0-160-assembly-reuse-update dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER trg_160_assembly_reuse_update BEFORE UPDATE ON task_run_assembly_success_reuse FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable original assembly success reuse';
--changeset medical-agent:1.0.0-160-assembly-reuse-delete dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER trg_160_assembly_reuse_delete BEFORE DELETE ON task_run_assembly_success_reuse FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable original assembly success reuse';
