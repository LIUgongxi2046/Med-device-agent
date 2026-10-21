--liquibase formatted sql
--changeset medical-agent:163-01-web-query dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE approved_web_query (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 query_revision CHAR(36) CHARACTER SET ascii NOT NULL,question_id CHAR(36) CHARACTER SET ascii NOT NULL,
 canonical_definition LONGTEXT NOT NULL,definition_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 state VARCHAR(16) NOT NULL DEFAULT 'DRAFT',revision BIGINT UNSIGNED NOT NULL DEFAULT 1,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL,created_at DATETIME(6) NOT NULL,classification VARCHAR(24) NOT NULL,schema_version VARCHAR(16) NOT NULL,schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 CHECK(schema_version='1.0.0' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK(classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH')),
 PRIMARY KEY(tenant_id,query_revision),UNIQUE KEY uq_web_query_owner(tenant_id,project_id,query_revision),
 FOREIGN KEY(tenant_id,project_id) REFERENCES project(tenant_id,project_id),
 FOREIGN KEY(tenant_id,project_id,question_id) REFERENCES research_question(tenant_id,project_id,question_id),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK(JSON_VALID(canonical_definition)),CHECK(state IN ('DRAFT','APPROVED','REVOKED')),
 CHECK(definition_sha256 REGEXP '^[a-f0-9]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
--changeset medical-agent:163-02-web-approval dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE approved_web_query_decision (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,project_id CHAR(36) CHARACTER SET ascii NOT NULL,query_revision CHAR(36) CHARACTER SET ascii NOT NULL,
 decision_id CHAR(36) CHARACTER SET ascii NOT NULL,decision VARCHAR(16) NOT NULL,
 definition_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,actor_id CHAR(36) CHARACTER SET ascii NOT NULL,
 identity_mode VARCHAR(16) NOT NULL,grant_id CHAR(36) CHARACTER SET ascii NOT NULL,grant_revision BIGINT UNSIGNED NOT NULL,
 reason VARCHAR(4096) NOT NULL,created_by CHAR(36) CHARACTER SET ascii NOT NULL,created_at DATETIME(6) NOT NULL,classification VARCHAR(24) NOT NULL,schema_version VARCHAR(16) NOT NULL,schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 CHECK(schema_version='1.0.0' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK(classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH')),
 PRIMARY KEY(tenant_id,decision_id),FOREIGN KEY(tenant_id,project_id,query_revision) REFERENCES approved_web_query(tenant_id,project_id,query_revision),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 FOREIGN KEY(tenant_id,grant_id) REFERENCES permission_grant(tenant_id,grant_id),
 FOREIGN KEY(tenant_id,actor_id) REFERENCES tenant_member(tenant_id,user_id),CHECK(decision IN ('APPROVED','REVOKED')),CHECK(identity_mode IN ('local','oidc')),CHECK(definition_sha256 REGEXP '^[a-f0-9]{64}$' AND grant_revision>0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
--changeset medical-agent:163-03-web-acquisition dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE approved_web_acquisition (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 acquisition_id CHAR(36) CHARACTER SET ascii NOT NULL,query_revision CHAR(36) CHARACTER SET ascii NOT NULL,
 definition_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,actor_id CHAR(36) CHARACTER SET ascii NOT NULL,
 state VARCHAR(32) NOT NULL,request_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 result_json LONGTEXT NULL,result_sha256 CHAR(64) CHARACTER SET ascii NULL,failure VARCHAR(64) NULL,created_by CHAR(36) CHARACTER SET ascii NOT NULL,
 created_at DATETIME(6) NOT NULL,classification VARCHAR(24) NOT NULL,schema_version VARCHAR(16) NOT NULL,schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,updated_at DATETIME(6) NOT NULL,
 CHECK(schema_version='1.0.0' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK(classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH')),
 PRIMARY KEY(tenant_id,acquisition_id),UNIQUE KEY uq_web_acquisition_owner(tenant_id,project_id,acquisition_id),
 FOREIGN KEY(tenant_id,project_id,query_revision) REFERENCES approved_web_query(tenant_id,project_id,query_revision),
 FOREIGN KEY(tenant_id,actor_id) REFERENCES tenant_member(tenant_id,user_id),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK(state IN ('STARTED','CAPTURED_PENDING_REVIEW','UNKNOWN')),
 CHECK(request_sha256 REGEXP '^[a-f0-9]{64}$' AND definition_sha256 REGEXP '^[a-f0-9]{64}$'),CHECK(result_sha256 IS NULL OR result_sha256 REGEXP '^[a-f0-9]{64}$'),CHECK(result_json IS NULL OR JSON_VALID(result_json)),CHECK((result_json IS NULL)=(result_sha256 IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
--changeset medical-agent:163-04-web-candidate dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE approved_web_candidate (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 candidate_id CHAR(36) CHARACTER SET ascii NOT NULL,acquisition_id CHAR(36) CHARACTER SET ascii NOT NULL,
 ordinal INT UNSIGNED NOT NULL,original_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 capture_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,state VARCHAR(32) NOT NULL,
 completion_request LONGTEXT NULL,upload_id CHAR(36) CHARACTER SET ascii NULL,import_request LONGTEXT NULL,import_sha256 CHAR(64) CHARACTER SET ascii NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL,created_at DATETIME(6) NOT NULL,classification VARCHAR(24) NOT NULL,schema_version VARCHAR(16) NOT NULL,schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 CHECK(schema_version='1.0.0' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK(classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH')),
 PRIMARY KEY(tenant_id,candidate_id),UNIQUE KEY uq_web_candidate_capture(tenant_id,acquisition_id,ordinal),
 FOREIGN KEY(tenant_id,project_id,acquisition_id) REFERENCES approved_web_acquisition(tenant_id,project_id,acquisition_id),
 FOREIGN KEY(tenant_id,project_id,upload_id) REFERENCES source_upload_request(tenant_id,project_id,upload_id),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK(state IN ('PENDING_RIGHTS','IMPORTING','UPLOAD_PENDING_GOVERNANCE')),CHECK(ordinal BETWEEN 1 AND 10),CHECK((import_request IS NULL)=(import_sha256 IS NULL)),CHECK(import_sha256 IS NULL OR import_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK(original_sha256 REGEXP '^[a-f0-9]{64}$' AND capture_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK(completion_request IS NULL OR JSON_VALID(completion_request)),CHECK(import_request IS NULL OR JSON_VALID(import_request))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
--changeset medical-agent:163-05-decision-immutable dbms:mysql runInTransaction:false failOnError:true splitStatements:false
CREATE TRIGGER tr_web_decision_update BEFORE UPDATE ON approved_web_query_decision FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='WEB_DECISION_IMMUTABLE';
--changeset medical-agent:163-06-decision-delete dbms:mysql runInTransaction:false failOnError:true splitStatements:false
CREATE TRIGGER tr_web_decision_delete BEFORE DELETE ON approved_web_query_decision FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='WEB_DECISION_IMMUTABLE';
--changeset medical-agent:163-07-definition-immutable dbms:mysql runInTransaction:false failOnError:true splitStatements:false
CREATE TRIGGER tr_web_query_fixed BEFORE UPDATE ON approved_web_query FOR EACH ROW
BEGIN
 IF NOT(OLD.classification<=>NEW.classification) OR NOT(OLD.schema_version<=>NEW.schema_version) OR NOT(OLD.schema_manifest_sha256<=>NEW.schema_manifest_sha256) OR NOT(OLD.created_by<=>NEW.created_by) OR NOT(OLD.created_at<=>NEW.created_at) OR NOT(OLD.tenant_id<=>NEW.tenant_id) OR NOT(OLD.project_id<=>NEW.project_id) OR NOT(OLD.query_revision<=>NEW.query_revision) OR NOT(OLD.question_id<=>NEW.question_id) OR NOT(OLD.canonical_definition<=>NEW.canonical_definition) OR NOT(OLD.definition_sha256<=>NEW.definition_sha256) OR NOT(OLD.created_by<=>NEW.created_by) OR NOT(OLD.created_at<=>NEW.created_at) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='WEB_QUERY_DEFINITION_IMMUTABLE'; END IF;
 IF NEW.revision<>OLD.revision+1 OR NOT((OLD.state='DRAFT' AND NEW.state='APPROVED') OR (OLD.state='APPROVED' AND NEW.state='REVOKED')) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='WEB_QUERY_TRANSITION_INVALID'; END IF;
END;
--changeset medical-agent:163-08-acquisition-original-immutable dbms:mysql runInTransaction:false failOnError:true splitStatements:false
CREATE TRIGGER tr_web_acquisition_fixed BEFORE UPDATE ON approved_web_acquisition FOR EACH ROW
BEGIN
 IF NOT(OLD.classification<=>NEW.classification) OR NOT(OLD.schema_version<=>NEW.schema_version) OR NOT(OLD.schema_manifest_sha256<=>NEW.schema_manifest_sha256) OR NOT(OLD.created_by<=>NEW.created_by) OR NOT(OLD.created_at<=>NEW.created_at) OR NOT(OLD.tenant_id<=>NEW.tenant_id) OR NOT(OLD.project_id<=>NEW.project_id) OR NOT(OLD.acquisition_id<=>NEW.acquisition_id) OR NOT(OLD.query_revision<=>NEW.query_revision) OR NOT(OLD.definition_sha256<=>NEW.definition_sha256) OR NOT(OLD.request_sha256<=>NEW.request_sha256) OR NOT(OLD.actor_id<=>NEW.actor_id) OR NOT(OLD.created_at<=>NEW.created_at) OR (OLD.result_json IS NOT NULL AND (NOT(OLD.result_json<=>NEW.result_json) OR NOT(OLD.result_sha256<=>NEW.result_sha256) OR NOT(OLD.state<=>NEW.state))) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='WEB_ACQUISITION_ORIGINAL_IMMUTABLE'; END IF;
 IF NOT(NEW.state=OLD.state OR (OLD.state IN ('STARTED','UNKNOWN') AND NEW.state IN ('UNKNOWN','CAPTURED_PENDING_REVIEW'))) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='WEB_ACQUISITION_TRANSITION_INVALID'; END IF;
END;
--changeset medical-agent:163-09-candidate-original-immutable dbms:mysql runInTransaction:false failOnError:true splitStatements:false
CREATE TRIGGER tr_web_candidate_fixed BEFORE UPDATE ON approved_web_candidate FOR EACH ROW
BEGIN
 IF NOT(OLD.classification<=>NEW.classification) OR NOT(OLD.schema_version<=>NEW.schema_version) OR NOT(OLD.schema_manifest_sha256<=>NEW.schema_manifest_sha256) OR NOT(OLD.created_by<=>NEW.created_by) OR NOT(OLD.created_at<=>NEW.created_at) OR NOT(OLD.tenant_id<=>NEW.tenant_id) OR NOT(OLD.project_id<=>NEW.project_id) OR NOT(OLD.candidate_id<=>NEW.candidate_id) OR NOT(OLD.acquisition_id<=>NEW.acquisition_id) OR NOT(OLD.ordinal<=>NEW.ordinal) OR NOT(OLD.original_sha256<=>NEW.original_sha256) OR NOT(OLD.capture_sha256<=>NEW.capture_sha256) OR (OLD.upload_id IS NOT NULL AND NOT(OLD.upload_id<=>NEW.upload_id)) OR (OLD.import_request IS NOT NULL AND (NOT(OLD.import_request<=>NEW.import_request) OR NOT(OLD.import_sha256<=>NEW.import_sha256))) OR (OLD.completion_request IS NOT NULL AND NOT(OLD.completion_request<=>NEW.completion_request)) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='WEB_CANDIDATE_ORIGINAL_IMMUTABLE'; END IF;
 IF NOT(NEW.state=OLD.state OR (OLD.state='PENDING_RIGHTS' AND NEW.state='IMPORTING') OR (OLD.state='IMPORTING' AND NEW.state='UPLOAD_PENDING_GOVERNANCE')) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='WEB_CANDIDATE_TRANSITION_INVALID'; END IF;
END;
--changeset medical-agent:163-10-query-delete dbms:mysql runInTransaction:false failOnError:true splitStatements:false
CREATE TRIGGER tr_web_query_delete BEFORE DELETE ON approved_web_query FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='WEB_QUERY_HISTORY_IMMUTABLE';
--changeset medical-agent:163-11-acquisition-delete dbms:mysql runInTransaction:false failOnError:true splitStatements:false
CREATE TRIGGER tr_web_acquisition_delete BEFORE DELETE ON approved_web_acquisition FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='WEB_ACQUISITION_ORIGINAL_IMMUTABLE';
--changeset medical-agent:163-12-candidate-delete dbms:mysql runInTransaction:false failOnError:true splitStatements:false
CREATE TRIGGER tr_web_candidate_delete BEFORE DELETE ON approved_web_candidate FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='WEB_CANDIDATE_ORIGINAL_IMMUTABLE';
