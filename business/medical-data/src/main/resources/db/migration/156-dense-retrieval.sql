--liquibase formatted sql

--changeset medical-agent:1.0.0-15601-dense-configuration dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE dense_index_configuration (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
 config_revision CHAR(36) CHARACTER SET ascii NOT NULL,
 project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 model_revision CHAR(36) CHARACTER SET ascii NOT NULL,
 definition JSON NOT NULL,
 content_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL,
 created_at DATETIME(6) NOT NULL,
 schema_version VARCHAR(32) NOT NULL,
 schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 classification VARCHAR(24) NOT NULL,
 PRIMARY KEY(tenant_id,config_revision),
 FOREIGN KEY(tenant_id,config_revision,content_sha256) REFERENCES version_registry(tenant_id,version_id,content_sha256),
 FOREIGN KEY(tenant_id,model_revision) REFERENCES model_config_revision(tenant_id,model_config_revision),
 FOREIGN KEY(tenant_id,project_id) REFERENCES project(tenant_id,project_id),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK(schema_version='1.0.0'),
 CHECK(schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK(classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-15602-dense-operation dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE dense_operation (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
 operation_id CHAR(36) CHARACTER SET ascii NOT NULL,
 project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL,
 idempotency_key VARCHAR(128) CHARACTER SET ascii NOT NULL,
 kind VARCHAR(16) NOT NULL,
 config_revision CHAR(36) CHARACTER SET ascii NOT NULL,
 request JSON NOT NULL,
 request_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 exact_input JSON NOT NULL,
 exact_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 input_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 authorization_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 status VARCHAR(24) NOT NULL,
 result JSON NULL,
 result_sha256 CHAR(64) CHARACTER SET ascii NULL,
 response_sha256 CHAR(64) CHARACTER SET ascii NULL,
 created_at DATETIME(6) NOT NULL,
 updated_at DATETIME(6) NOT NULL,
 schema_version VARCHAR(32) NOT NULL,
 schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 classification VARCHAR(24) NOT NULL,
 revision BIGINT UNSIGNED NOT NULL DEFAULT 1,
 PRIMARY KEY(tenant_id,operation_id),
 UNIQUE KEY uq_dense_idempotency(tenant_id,created_by,idempotency_key),
 FOREIGN KEY(tenant_id,config_revision) REFERENCES dense_index_configuration(tenant_id,config_revision),
 CHECK(kind IN ('INDEX','QUERY')),
 CHECK(status IN ('PREPARED','DISPATCHING','UNKNOWN','COMPLETE','WITHDRAWN')),
 FOREIGN KEY(tenant_id,project_id) REFERENCES project(tenant_id,project_id),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK(schema_version='1.0.0'),
 CHECK(schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK(classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-15603-dense-reservation dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE dense_budget_reservation (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
 operation_id CHAR(36) CHARACTER SET ascii NOT NULL,
 budget_id CHAR(36) CHARACTER SET ascii NOT NULL,
 budget_revision CHAR(36) CHARACTER SET ascii NOT NULL,
 upper_amount DECIMAL(24,8) NOT NULL,
 upper_tokens BIGINT UNSIGNED NOT NULL,
 settled BOOLEAN NOT NULL DEFAULT FALSE,
 actual_amount DECIMAL(24,8) NULL,
 actual_tokens BIGINT UNSIGNED NULL,
 project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL,
 created_at DATETIME(6) NOT NULL,
 schema_version VARCHAR(32) NOT NULL,
 schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 classification VARCHAR(24) NOT NULL,
 revision BIGINT UNSIGNED NOT NULL DEFAULT 1,
 updated_at DATETIME(6) NOT NULL,
 PRIMARY KEY(tenant_id,operation_id,budget_id),
 FOREIGN KEY(tenant_id,operation_id) REFERENCES dense_operation(tenant_id,operation_id),
 FOREIGN KEY(tenant_id,budget_id) REFERENCES budget_account(tenant_id,budget_id),
 FOREIGN KEY(tenant_id,project_id) REFERENCES project(tenant_id,project_id),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK(schema_version='1.0.0'),
 CHECK(schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK(classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-15604-dense-vector dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE dense_index_vector (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
 index_version CHAR(36) CHARACTER SET ascii NOT NULL,
 source_version CHAR(36) CHARACTER SET ascii NOT NULL,
 parse_version CHAR(36) CHARACTER SET ascii NOT NULL,
 chunk_id CHAR(36) CHARACTER SET ascii NOT NULL,
 operation_id CHAR(36) CHARACTER SET ascii NOT NULL,
 config_revision CHAR(36) CHARACTER SET ascii NOT NULL,
 vector_json JSON NOT NULL,
 vector_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL,
 created_at DATETIME(6) NOT NULL,
 schema_version VARCHAR(32) NOT NULL,
 schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 classification VARCHAR(24) NOT NULL,
 PRIMARY KEY(tenant_id,index_version,chunk_id),
 FOREIGN KEY(tenant_id,source_version,parse_version,index_version,chunk_id)
   REFERENCES index_item(tenant_id,source_version,parse_version,index_version,chunk_id) ON DELETE CASCADE,
 FOREIGN KEY(tenant_id,operation_id) REFERENCES dense_operation(tenant_id,operation_id),
 FOREIGN KEY(tenant_id,config_revision) REFERENCES dense_index_configuration(tenant_id,config_revision),
 FOREIGN KEY(tenant_id,project_id) REFERENCES project(tenant_id,project_id),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK(schema_version='1.0.0'),
 CHECK(schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK(classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-15605-dense-operation-source dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE dense_operation_source (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
 operation_id CHAR(36) CHARACTER SET ascii NOT NULL,
 source_version CHAR(36) CHARACTER SET ascii NOT NULL,
 project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL,
 created_at DATETIME(6) NOT NULL,
 schema_version VARCHAR(32) NOT NULL,
 schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 classification VARCHAR(24) NOT NULL,
 PRIMARY KEY(tenant_id,operation_id,source_version),
 KEY ix_dense_source(tenant_id,source_version,operation_id),
 FOREIGN KEY(tenant_id,operation_id) REFERENCES dense_operation(tenant_id,operation_id),
 FOREIGN KEY(tenant_id,source_version) REFERENCES source_version(tenant_id,source_version),
 FOREIGN KEY(tenant_id,project_id) REFERENCES project(tenant_id,project_id),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK(schema_version='1.0.0'),
 CHECK(schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK(classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-15606-index-embedding-operation dbms:mysql runInTransaction:false failOnError:true
-- Source indexing is not a fabricated task run/action. Preserve the existing invocation FK and
-- add an exclusive reference to the real durable source embedding operation instead.
ALTER TABLE index_item
 ADD COLUMN embedding_operation_id CHAR(36) CHARACTER SET ascii NULL,
 ADD CONSTRAINT fk_index_embedding_operation FOREIGN KEY(tenant_id,embedding_operation_id)
   REFERENCES dense_operation(tenant_id,operation_id),
 ADD CONSTRAINT ck_index_embedding_provenance CHECK(embedding_invocation_id IS NULL OR embedding_operation_id IS NULL);

--changeset medical-agent:1.0.0-15607-dense-scope-integrity dbms:mysql runInTransaction:false failOnError:true
ALTER TABLE dense_index_configuration ADD UNIQUE KEY uq_dense_config_scope(tenant_id,project_id,config_revision),
 ADD CONSTRAINT ck_dense_config_hash CHECK(content_sha256 REGEXP '^[a-f0-9]{64}$');
ALTER TABLE dense_operation ADD UNIQUE KEY uq_dense_operation_scope(tenant_id,project_id,operation_id),
 ADD CONSTRAINT fk_dense_operation_config_scope FOREIGN KEY(tenant_id,project_id,config_revision) REFERENCES dense_index_configuration(tenant_id,project_id,config_revision),
 ADD CONSTRAINT ck_dense_operation_hash CHECK(request_sha256 REGEXP '^[a-f0-9]{64}$' AND exact_sha256 REGEXP '^[a-f0-9]{64}$' AND input_sha256 REGEXP '^[a-f0-9]{64}$' AND authorization_sha256 REGEXP '^[a-f0-9]{64}$'),
 ADD CONSTRAINT ck_dense_operation_revision CHECK(revision>0),
 ADD CONSTRAINT ck_dense_operation_complete CHECK(status<>'COMPLETE' OR (result IS NOT NULL AND result_sha256 IS NOT NULL AND response_sha256 IS NOT NULL AND result_sha256 REGEXP '^[a-f0-9]{64}$' AND response_sha256 REGEXP '^[a-f0-9]{64}$'));
ALTER TABLE dense_budget_reservation ADD CONSTRAINT fk_dense_budget_operation_scope FOREIGN KEY(tenant_id,project_id,operation_id) REFERENCES dense_operation(tenant_id,project_id,operation_id),
 ADD CONSTRAINT fk_dense_budget_revision FOREIGN KEY(tenant_id,budget_revision) REFERENCES budget_revision(tenant_id,budget_revision),
 ADD CONSTRAINT ck_dense_budget_amount CHECK(upper_amount>=0 AND (actual_amount IS NULL OR actual_amount BETWEEN 0 AND upper_amount)),
 ADD CONSTRAINT ck_dense_budget_tokens CHECK(actual_tokens IS NULL OR actual_tokens<=upper_tokens),
 ADD CONSTRAINT ck_dense_budget_settled CHECK(NOT settled OR (actual_amount IS NOT NULL AND actual_tokens IS NOT NULL)),
 ADD CONSTRAINT ck_dense_budget_revision CHECK(revision>0);
ALTER TABLE dense_index_vector ADD CONSTRAINT fk_dense_vector_operation_scope FOREIGN KEY(tenant_id,project_id,operation_id) REFERENCES dense_operation(tenant_id,project_id,operation_id),
 ADD CONSTRAINT fk_dense_vector_config_scope FOREIGN KEY(tenant_id,project_id,config_revision) REFERENCES dense_index_configuration(tenant_id,project_id,config_revision),
 ADD CONSTRAINT ck_dense_vector_hash CHECK(vector_sha256 REGEXP '^[a-f0-9]{64}$');
ALTER TABLE dense_operation_source ADD CONSTRAINT fk_dense_source_operation_scope FOREIGN KEY(tenant_id,project_id,operation_id) REFERENCES dense_operation(tenant_id,project_id,operation_id);

--changeset medical-agent:1.0.0-15608-config-update-guard dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER trg_156_config_update BEFORE UPDATE ON dense_index_configuration FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable dense configuration';
--changeset medical-agent:1.0.0-15609-config-delete-guard dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER trg_156_config_delete BEFORE DELETE ON dense_index_configuration FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable dense configuration';
--changeset medical-agent:1.0.0-15610-source-update-guard dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER trg_156_source_update BEFORE UPDATE ON dense_operation_source FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable dense source provenance';
--changeset medical-agent:1.0.0-15611-source-delete-guard dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER trg_156_source_delete BEFORE DELETE ON dense_operation_source FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable dense source provenance';
--changeset medical-agent:1.0.0-15612-vector-update-guard dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER trg_156_vector_update BEFORE UPDATE ON dense_index_vector FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable dense vector; purge uses deletion';
--changeset medical-agent:1.0.0-15613-vector-purge-guard dbms:mysql runInTransaction:false failOnError:true splitStatements:false
CREATE TRIGGER trg_156_vector_delete BEFORE DELETE ON dense_index_vector FOR EACH ROW
BEGIN
 IF NOT EXISTS(SELECT 1 FROM source_lifecycle_state s WHERE s.tenant_id=OLD.tenant_id AND s.source_version=OLD.source_version AND s.status IN ('PURGE_PENDING','PURGED')) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Dense vector deletion requires source purge';
 END IF;
END;
--changeset medical-agent:1.0.0-15614-operation-delete-guard dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER trg_156_operation_delete BEFORE DELETE ON dense_operation FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Retain dense operation audit tombstone';
--changeset medical-agent:1.0.0-15615-operation-transition-guard dbms:mysql runInTransaction:false failOnError:true splitStatements:false
CREATE TRIGGER trg_156_operation_update BEFORE UPDATE ON dense_operation FOR EACH ROW
BEGIN
 IF NOT(OLD.tenant_id<=>NEW.tenant_id) OR NOT(OLD.operation_id<=>NEW.operation_id) OR NOT(OLD.project_id<=>NEW.project_id)
 OR NOT(OLD.created_by<=>NEW.created_by) OR NOT(OLD.created_at<=>NEW.created_at) OR NOT(OLD.config_revision<=>NEW.config_revision)
 OR NOT(OLD.idempotency_key<=>NEW.idempotency_key) OR NOT(OLD.kind<=>NEW.kind)
 OR NOT(OLD.request_sha256<=>NEW.request_sha256) OR NOT(OLD.exact_sha256<=>NEW.exact_sha256)
 OR NOT(OLD.input_sha256<=>NEW.input_sha256) OR NOT(OLD.authorization_sha256<=>NEW.authorization_sha256)
 OR NOT(OLD.schema_version<=>NEW.schema_version) OR NOT(OLD.schema_manifest_sha256<=>NEW.schema_manifest_sha256)
 OR NOT(OLD.classification<=>NEW.classification) OR NEW.revision<>OLD.revision+1 OR NEW.updated_at<OLD.updated_at THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable dense operation identity';
 END IF;
 IF NOT((OLD.status='PREPARED' AND NEW.status IN ('DISPATCHING','WITHDRAWN'))
 OR (OLD.status='DISPATCHING' AND NEW.status IN ('DISPATCHING','UNKNOWN','COMPLETE','WITHDRAWN'))
 OR (OLD.status='UNKNOWN' AND NEW.status IN ('UNKNOWN','COMPLETE','WITHDRAWN'))
 OR (OLD.status='COMPLETE' AND NEW.status='WITHDRAWN')) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Invalid dense operation transition';
 END IF;
 IF NEW.status='WITHDRAWN' THEN
  IF JSON_LENGTH(NEW.request)<>0 OR JSON_LENGTH(NEW.exact_input)<>0 OR NEW.result IS NOT NULL
  OR NOT EXISTS(SELECT 1 FROM dense_operation_source d JOIN source_lifecycle_state s ON s.tenant_id=d.tenant_id AND s.source_version=d.source_version
    WHERE d.tenant_id=OLD.tenant_id AND d.operation_id=OLD.operation_id AND s.status IN ('PURGE_PENDING','PURGED')) THEN
   SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Dense content scrubbing requires source purge';
  END IF;
 ELSEIF NOT(OLD.request<=>NEW.request) OR NOT(OLD.exact_input<=>NEW.exact_input) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable dense exact input';
 END IF;
END;
--changeset medical-agent:1.0.0-15616-reservation-delete-guard dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER trg_156_budget_delete BEFORE DELETE ON dense_budget_reservation FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Retain dense budget evidence';
--changeset medical-agent:1.0.0-15617-reservation-settle-guard dbms:mysql runInTransaction:false failOnError:true splitStatements:false
CREATE TRIGGER trg_156_budget_update BEFORE UPDATE ON dense_budget_reservation FOR EACH ROW
BEGIN
 IF OLD.settled OR NOT NEW.settled OR NEW.revision<>OLD.revision+1 OR NEW.updated_at<OLD.updated_at
 OR NOT(OLD.tenant_id<=>NEW.tenant_id) OR NOT(OLD.operation_id<=>NEW.operation_id) OR NOT(OLD.project_id<=>NEW.project_id)
 OR NOT(OLD.budget_id<=>NEW.budget_id) OR NOT(OLD.budget_revision<=>NEW.budget_revision)
 OR NOT(OLD.upper_amount<=>NEW.upper_amount) OR NOT(OLD.upper_tokens<=>NEW.upper_tokens)
 OR NOT(OLD.created_by<=>NEW.created_by) OR NOT(OLD.created_at<=>NEW.created_at)
 OR NOT(OLD.schema_version<=>NEW.schema_version) OR NOT(OLD.schema_manifest_sha256<=>NEW.schema_manifest_sha256)
 OR NOT(OLD.classification<=>NEW.classification) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable dense reservation; settle once only';
 END IF;
END;
