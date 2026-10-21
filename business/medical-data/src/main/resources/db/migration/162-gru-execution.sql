--liquibase formatted sql
--changeset medical-agent:1.0.0-162-gru-execution dbms:mysql runInTransaction:false failOnError:true
-- Additive: RECORD_ONLY conversation/message/export tables retain their original contracts.
CREATE TABLE gru_execution_action (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
 owner_id CHAR(36) CHARACTER SET ascii NOT NULL,
 conversation_id CHAR(36) CHARACTER SET ascii NOT NULL,
 action_id CHAR(36) CHARACTER SET ascii NOT NULL,
 idempotency_key VARCHAR(128) COLLATE utf8mb4_bin NOT NULL,
 origin_session_id CHAR(36) CHARACTER SET ascii NOT NULL,
 request_json LONGTEXT NOT NULL,
 request_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 state VARCHAR(24) NOT NULL, revision BIGINT UNSIGNED NOT NULL,
 confirmation_json LONGTEXT NULL, confirmed_at DATETIME(6) NULL,
 result_json LONGTEXT NULL, result_sha256 CHAR(64) CHARACTER SET ascii NULL,
 created_at DATETIME(6) NOT NULL, expires_at DATETIME(6) NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL, classification VARCHAR(24) NOT NULL,
 schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 CHECK(created_by=owner_id AND schema_version='1.0.0' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK(classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH')),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 PRIMARY KEY(tenant_id,action_id),
 UNIQUE KEY uq_gru_execution_owner_key(tenant_id,owner_id,conversation_id,idempotency_key),
 UNIQUE KEY uq_gru_execution_owner_action(tenant_id,owner_id,conversation_id,action_id),
 FOREIGN KEY(tenant_id,conversation_id) REFERENCES conversation(tenant_id,conversation_id),
 FOREIGN KEY(tenant_id,owner_id) REFERENCES tenant_member(tenant_id,user_id),
 CHECK(JSON_VALID(request_json) AND request_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK(confirmation_json IS NULL OR JSON_VALID(confirmation_json)),
 CHECK((result_json IS NULL AND result_sha256 IS NULL) OR (JSON_VALID(result_json) AND result_sha256 REGEXP '^[a-f0-9]{64}$')),
 CHECK(state IN ('PENDING','STARTING','RUNNING','COMPLETED','FAILED','CANCELLED')),
 CHECK(revision>0 AND expires_at>created_at),
 CHECK(JSON_UNQUOTE(JSON_EXTRACT(request_json,'$.intent')) IN ('QUERY','RETRIEVE','CHECK','PROPOSE_EDIT','ADOPT_EDIT')),
 CHECK(state IN ('PENDING','CANCELLED') OR (confirmation_json IS NOT NULL AND confirmed_at IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
--changeset medical-agent:1.0.0-162-gru-execution-message dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE gru_execution_message (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, owner_id CHAR(36) CHARACTER SET ascii NOT NULL,
 conversation_id CHAR(36) CHARACTER SET ascii NOT NULL, action_id CHAR(36) CHARACTER SET ascii NOT NULL,
 message_id CHAR(36) CHARACTER SET ascii NOT NULL, result_json LONGTEXT NOT NULL,
 result_sha256 CHAR(64) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL, classification VARCHAR(24) NOT NULL,
 schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 CHECK(created_by=owner_id AND schema_version='1.0.0' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK(classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH')),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 PRIMARY KEY(tenant_id,message_id), UNIQUE KEY uq_gru_execution_result(tenant_id,action_id,result_sha256),
 FOREIGN KEY(tenant_id,owner_id,conversation_id,action_id) REFERENCES gru_execution_action(tenant_id,owner_id,conversation_id,action_id),
 CHECK(JSON_VALID(result_json) AND result_sha256 REGEXP '^[a-f0-9]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
--changeset medical-agent:1.0.0-162-gru-owner-memory dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE gru_owner_memory (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, owner_id CHAR(36) CHARACTER SET ascii NOT NULL,
 conversation_id CHAR(36) CHARACTER SET ascii NOT NULL, memory_id CHAR(36) CHARACTER SET ascii NOT NULL,
 kind VARCHAR(16) NOT NULL, content TEXT NOT NULL, created_at DATETIME(6) NOT NULL,
 presentation_json LONGTEXT NOT NULL, presentation_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 source_refs_json LONGTEXT NOT NULL, source_refs_sha256 CHAR(64) CHARACTER SET ascii NOT NULL, status VARCHAR(16) NOT NULL, revision BIGINT UNSIGNED NOT NULL, updated_at DATETIME(6) NOT NULL,
 CHECK(JSON_VALID(presentation_json) AND JSON_VALID(source_refs_json) AND presentation_sha256 REGEXP '^[a-f0-9]{64}$' AND source_refs_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK(status IN ('ACTIVE','REMOVED') AND revision>0),
 created_by CHAR(36) CHARACTER SET ascii NOT NULL, classification VARCHAR(24) NOT NULL,
 schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 CHECK(created_by=owner_id AND schema_version='1.0.0' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK(classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH')),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 PRIMARY KEY(tenant_id,memory_id), KEY ix_gru_owner_memory(tenant_id,owner_id,conversation_id,created_at),
 FOREIGN KEY(tenant_id,conversation_id) REFERENCES conversation(tenant_id,conversation_id),
 FOREIGN KEY(tenant_id,owner_id) REFERENCES tenant_member(tenant_id,user_id), CHECK(kind IN ('HABIT','SUMMARY'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
--changeset medical-agent:1.0.0-162-gru-message-immutable-update dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER imm_gru_execution_message_update BEFORE UPDATE ON gru_execution_message FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IMMUTABLE_GRU_EXECUTION_MESSAGE';
--changeset medical-agent:1.0.0-162-gru-message-immutable-delete dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER imm_gru_execution_message_delete BEFORE DELETE ON gru_execution_message FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IMMUTABLE_GRU_EXECUTION_MESSAGE';
--changeset medical-agent:1.0.0-162-gru-action-immutable-basis dbms:mysql runInTransaction:false failOnError:true splitStatements:false
-- Exact request/identity/expiration are permanent; confirmation can only be set once.
CREATE TRIGGER guard_gru_execution_action_update BEFORE UPDATE ON gru_execution_action FOR EACH ROW
BEGIN
 IF NOT (OLD.created_by <=> NEW.created_by) OR NOT (OLD.classification <=> NEW.classification)
 OR NOT (OLD.schema_version <=> NEW.schema_version) OR NOT (OLD.schema_manifest_sha256 <=> NEW.schema_manifest_sha256)
 OR (OLD.result_json IS NOT NULL AND NOT (OLD.result_json <=> NEW.result_json))
 OR (OLD.result_sha256 IS NOT NULL AND NOT (OLD.result_sha256 <=> NEW.result_sha256))
 OR NOT (OLD.tenant_id <=> NEW.tenant_id) OR NOT (OLD.owner_id <=> NEW.owner_id)
 OR NOT (OLD.action_id <=> NEW.action_id) OR NOT (OLD.conversation_id <=> NEW.conversation_id)
 OR NOT (OLD.request_json <=> NEW.request_json) OR NOT (OLD.request_sha256 <=> NEW.request_sha256)
 OR NOT (OLD.idempotency_key <=> NEW.idempotency_key) OR NOT (OLD.origin_session_id <=> NEW.origin_session_id)
 OR NOT (OLD.created_at <=> NEW.created_at) OR NOT (OLD.expires_at <=> NEW.expires_at)
 OR (OLD.confirmation_json IS NOT NULL AND NOT (OLD.confirmation_json <=> NEW.confirmation_json))
 OR (OLD.confirmed_at IS NOT NULL AND NOT (OLD.confirmed_at <=> NEW.confirmed_at))
 OR NEW.revision<>OLD.revision+1
 OR NOT ((OLD.state='PENDING' AND NEW.state IN ('STARTING','CANCELLED'))
 OR (OLD.state='STARTING' AND NEW.state IN ('RUNNING','COMPLETED'))
 OR (OLD.state='RUNNING' AND NEW.state IN ('COMPLETED','FAILED','CANCELLED')))
 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='GRU_ACTION_BASIS_OR_TRANSITION_INVALID'; END IF;
END;

--changeset medical-agent:1.0.0-162-gru-presentation-context dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE gru_execution_presentation_context (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, owner_id CHAR(36) CHARACTER SET ascii NOT NULL,
 conversation_id CHAR(36) CHARACTER SET ascii NOT NULL, action_id CHAR(36) CHARACTER SET ascii NOT NULL,
 context_json LONGTEXT NOT NULL, context_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
 schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 PRIMARY KEY(tenant_id,action_id),
 FOREIGN KEY(tenant_id,owner_id,conversation_id,action_id) REFERENCES gru_execution_action(tenant_id,owner_id,conversation_id,action_id),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK(created_by=owner_id AND JSON_VALID(context_json) AND context_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK(schema_version='1.0.0' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK(classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
--changeset medical-agent:1.0.0-162-gru-presentation-immutable-update dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER imm_gru_presentation_update BEFORE UPDATE ON gru_execution_presentation_context FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IMMUTABLE_GRU_PRESENTATION_CONTEXT';
--changeset medical-agent:1.0.0-162-gru-presentation-immutable-delete dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER imm_gru_presentation_delete BEFORE DELETE ON gru_execution_presentation_context FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IMMUTABLE_GRU_PRESENTATION_CONTEXT';
--changeset medical-agent:1.0.0-162-gru-memory-current-guard dbms:mysql runInTransaction:false failOnError:true splitStatements:false
CREATE TRIGGER guard_gru_owner_memory_update BEFORE UPDATE ON gru_owner_memory FOR EACH ROW
BEGIN
 IF NOT (OLD.tenant_id <=> NEW.tenant_id) OR NOT (OLD.owner_id <=> NEW.owner_id)
 OR NOT (OLD.conversation_id <=> NEW.conversation_id) OR NOT (OLD.memory_id <=> NEW.memory_id)
 OR NOT (OLD.kind <=> NEW.kind) OR NOT (OLD.source_refs_json <=> NEW.source_refs_json) OR NOT (OLD.source_refs_sha256 <=> NEW.source_refs_sha256)
 OR NOT (OLD.created_by <=> NEW.created_by) OR NOT (OLD.created_at <=> NEW.created_at)
 OR NOT (OLD.classification <=> NEW.classification) OR NOT (OLD.schema_version <=> NEW.schema_version)
 OR NOT (OLD.schema_manifest_sha256 <=> NEW.schema_manifest_sha256)
 OR OLD.status<>'ACTIVE' OR NEW.revision<>OLD.revision+1
 OR (NEW.status='REMOVED' AND (NOT (OLD.content <=> NEW.content) OR NOT (OLD.presentation_json <=> NEW.presentation_json) OR NOT (OLD.presentation_sha256 <=> NEW.presentation_sha256)))
 OR (NEW.status='ACTIVE' AND OLD.kind<>'HABIT')
 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='GRU_MEMORY_BASIS_OR_CAS_INVALID'; END IF;
END;
