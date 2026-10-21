--liquibase formatted sql
--changeset medical-agent:172-feedback-project-identity dbms:mysql runInTransaction:false failOnError:true
ALTER TABLE feedback ADD UNIQUE KEY uq_feedback_project_identity(tenant_id,project_id,feedback_id);
--changeset medical-agent:172-operations-feedback-event dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE operations_feedback_event (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
 project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 event_id CHAR(36) CHARACTER SET ascii NOT NULL,
 feedback_id CHAR(36) CHARACTER SET ascii NOT NULL,
 expected_revision BIGINT UNSIGNED NOT NULL,
 resulting_revision BIGINT UNSIGNED NOT NULL,
 state VARCHAR(24) CHARACTER SET ascii NOT NULL,
 revision_version CHAR(36) CHARACTER SET ascii NULL,
 acceptance_id CHAR(36) CHARACTER SET ascii NULL,
 release_id CHAR(36) CHARACTER SET ascii NULL,
 idempotency_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 request_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 receipt_json LONGTEXT NOT NULL,
 receipt_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(32) NOT NULL,
 schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL,
 created_at DATETIME(6) NOT NULL,
 classification VARCHAR(24) NOT NULL,
 PRIMARY KEY(tenant_id,event_id),
 UNIQUE KEY uq_operations_feedback_revision(tenant_id,feedback_id,resulting_revision),
 UNIQUE KEY uq_operations_feedback_key(tenant_id,created_by,idempotency_key),
 FOREIGN KEY(tenant_id,project_id,feedback_id) REFERENCES feedback(tenant_id,project_id,feedback_id),
 FOREIGN KEY(tenant_id,project_id) REFERENCES project(tenant_id,project_id),
 FOREIGN KEY(tenant_id,revision_version) REFERENCES formal_agent_candidate_binding(tenant_id,version_id),
 FOREIGN KEY(tenant_id,acceptance_id) REFERENCES formal_agent_acceptance(tenant_id,acceptance_id),
 FOREIGN KEY(tenant_id,release_id) REFERENCES formal_agent_publication_registration(tenant_id,release_id),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK(resulting_revision=expected_revision+1),
 CHECK(state IN ('OPEN','REVIEWED','REVISION_LINKED','EVALUATED','PUBLISHED','CLOSED','REJECTED')),
 CHECK(state NOT IN ('REVISION_LINKED','EVALUATED','PUBLISHED','CLOSED') OR revision_version IS NOT NULL),
 CHECK(state NOT IN ('EVALUATED','PUBLISHED','CLOSED') OR acceptance_id IS NOT NULL),
 CHECK(state NOT IN ('PUBLISHED','CLOSED') OR release_id IS NOT NULL),
 CHECK(schema_version='1.0.0'),
 CHECK(request_sha256 REGEXP '^[a-f0-9]{64}$' AND receipt_sha256 REGEXP '^[a-f0-9]{64}$' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK(JSON_VALID(receipt_json)),
 CHECK((expected_revision=0)=(state='OPEN')),
 CHECK(JSON_UNQUOTE(JSON_EXTRACT(receipt_json,'$.feedback_id'))=feedback_id
   AND JSON_UNQUOTE(JSON_EXTRACT(receipt_json,'$.project_id'))=project_id
   AND JSON_UNQUOTE(JSON_EXTRACT(receipt_json,'$.state'))=state
   AND JSON_UNQUOTE(JSON_EXTRACT(receipt_json,'$.revision'))=CAST(resulting_revision AS CHAR)
   AND JSON_UNQUOTE(JSON_EXTRACT(receipt_json,'$.actor_id'))=created_by),
 CHECK(classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
--changeset medical-agent:172-operations-feedback-no-update dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER operations_feedback_event_no_update BEFORE UPDATE ON operations_feedback_event FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable feedback transition';
--changeset medical-agent:172-operations-feedback-no-delete dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER operations_feedback_event_no_delete BEFORE DELETE ON operations_feedback_event FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable feedback transition';
--changeset medical-agent:172-operations-feedback-head-transition dbms:mysql runInTransaction:false failOnError:true splitStatements:false
CREATE TRIGGER operations_feedback_head_transition BEFORE UPDATE ON feedback FOR EACH ROW
BEGIN
 IF EXISTS(SELECT 1 FROM operations_feedback_event e WHERE e.tenant_id=OLD.tenant_id AND e.feedback_id=OLD.feedback_id) THEN
  IF NOT (NEW.tenant_id<=>OLD.tenant_id AND NEW.feedback_id<=>OLD.feedback_id AND NEW.project_id<=>OLD.project_id
      AND NEW.target_version<=>OLD.target_version AND NEW.task_id<=>OLD.task_id AND NEW.document_revision<=>OLD.document_revision
      AND NEW.reason<=>OLD.reason AND NEW.actor_id<=>OLD.actor_id AND NEW.proposal_id<=>OLD.proposal_id
      AND NEW.created_by<=>OLD.created_by AND NEW.created_at<=>OLD.created_at AND NEW.classification<=>OLD.classification
      AND NEW.revision=OLD.revision+1 AND NEW.updated_at>=OLD.updated_at)
      OR NOT ((OLD.status='OPEN' AND NEW.status IN ('REVIEWED','REJECTED'))
        OR (OLD.status='REVIEWED' AND NEW.status IN ('REVISION_LINKED','REJECTED'))
        OR (OLD.status='REVISION_LINKED' AND NEW.status='EVALUATED')
        OR (OLD.status='EVALUATED' AND NEW.status='PUBLISHED')
        OR (OLD.status='PUBLISHED' AND NEW.status='CLOSED'))
      OR NOT EXISTS(SELECT 1 FROM operations_feedback_event e WHERE e.tenant_id=OLD.tenant_id AND e.project_id=OLD.project_id
        AND e.feedback_id=OLD.feedback_id AND e.expected_revision=OLD.revision AND e.resulting_revision=NEW.revision AND e.state=NEW.status AND e.created_at=NEW.updated_at)
  THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Invalid managed feedback transition'; END IF;
 END IF;
END;
