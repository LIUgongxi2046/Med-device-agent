--liquibase formatted sql
-- Separate from 172's formal-Agent feedback. No existing contract, source/rule content or signed history changes.
--changeset medical-agent:177-knowledge-feedback-origin dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE knowledge_feedback_origin (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
 project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 feedback_id CHAR(36) CHARACTER SET ascii NOT NULL,
 kind VARCHAR(16) CHARACTER SET ascii NOT NULL,
 target_version CHAR(36) CHARACTER SET ascii NOT NULL,
 target_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 document_revision CHAR(36) CHARACTER SET ascii NULL,
 revised_document_revision CHAR(36) CHARACTER SET ascii NULL,
 review_id CHAR(36) CHARACTER SET ascii NULL,
 return_id CHAR(36) CHARACTER SET ascii NULL,
 original_json LONGTEXT NOT NULL,
 original_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(32) NOT NULL,
 schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL,
 created_at DATETIME(6) NOT NULL,
 classification VARCHAR(24) NOT NULL,
 PRIMARY KEY(tenant_id,feedback_id),
 UNIQUE KEY uq_knowledge_feedback_project(tenant_id,project_id,feedback_id),
 FOREIGN KEY(tenant_id,project_id,feedback_id) REFERENCES feedback(tenant_id,project_id,feedback_id),
 FOREIGN KEY(tenant_id,project_id) REFERENCES project(tenant_id,project_id),
 FOREIGN KEY(tenant_id,target_version,target_sha256) REFERENCES version_registry(tenant_id,version_id,content_sha256),
 FOREIGN KEY(tenant_id,project_id,document_revision) REFERENCES document_revision_meta(tenant_id,project_id,document_revision),
 FOREIGN KEY(tenant_id,project_id,revised_document_revision) REFERENCES document_revision_meta(tenant_id,project_id,document_revision),
 FOREIGN KEY(tenant_id,project_id,review_id) REFERENCES review_decision(tenant_id,project_id,review_id),
 FOREIGN KEY(tenant_id,project_id,return_id) REFERENCES document_revision_return(tenant_id,project_id,return_id),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK(kind IN ('SOURCE','RULE')),
 CHECK((revised_document_revision IS NULL AND review_id IS NULL AND return_id IS NULL) OR document_revision IS NOT NULL),
 CHECK(revised_document_revision IS NULL OR revised_document_revision<>document_revision),
 CHECK(JSON_VALID(original_json)),
 CHECK(schema_version='1.0.0'),
 CHECK(target_sha256 REGEXP '^[a-f0-9]{64}$' AND original_sha256 REGEXP '^[a-f0-9]{64}$' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK(classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:177-knowledge-feedback-event dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE knowledge_feedback_event (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
 project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 event_id CHAR(36) CHARACTER SET ascii NOT NULL,
 feedback_id CHAR(36) CHARACTER SET ascii NOT NULL,
 expected_revision BIGINT UNSIGNED NOT NULL,
 resulting_revision BIGINT UNSIGNED NOT NULL,
 state VARCHAR(24) CHARACTER SET ascii NOT NULL,
 proposal_id CHAR(36) CHARACTER SET ascii NULL,
 revision_version CHAR(36) CHARACTER SET ascii NULL,
 decision_id CHAR(36) CHARACTER SET ascii NULL,
 source_approval_revision CHAR(36) CHARACTER SET ascii NULL,
 rule_approval_decision_id CHAR(36) CHARACTER SET ascii NULL,
 idempotency_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 request_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 previous_receipt_sha256 CHAR(64) CHARACTER SET ascii NULL,
 receipt_json LONGTEXT NOT NULL,
 receipt_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(32) NOT NULL,
 schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL,
 created_at DATETIME(6) NOT NULL,
 classification VARCHAR(24) NOT NULL,
 PRIMARY KEY(tenant_id,event_id),
 UNIQUE KEY uq_knowledge_feedback_event_revision(tenant_id,feedback_id,resulting_revision),
 UNIQUE KEY uq_knowledge_feedback_event_idempotency(tenant_id,created_by,idempotency_key),
 FOREIGN KEY(tenant_id,project_id,feedback_id) REFERENCES knowledge_feedback_origin(tenant_id,project_id,feedback_id),
 FOREIGN KEY(tenant_id,project_id) REFERENCES project(tenant_id,project_id),
 FOREIGN KEY(tenant_id,proposal_id) REFERENCES change_proposal_scope(tenant_id,proposal_id),
 FOREIGN KEY(tenant_id,revision_version) REFERENCES version_registry(tenant_id,version_id),
 FOREIGN KEY(tenant_id,decision_id) REFERENCES change_impact_application(tenant_id,decision_id),
 FOREIGN KEY(tenant_id,source_approval_revision) REFERENCES version_registry(tenant_id,version_id),
 FOREIGN KEY(tenant_id,rule_approval_decision_id) REFERENCES rule_approval_decision(tenant_id,decision_id),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK(resulting_revision=expected_revision+1),
 CHECK(state IN ('PENDING_REVIEW','PROPOSAL_LINKED','REVIEWED','CLOSED','REJECTED')),
 CHECK((expected_revision=0)=(state='PENDING_REVIEW')),
 CHECK((expected_revision=0)=(previous_receipt_sha256 IS NULL)),
 CHECK((proposal_id IS NULL)=(revision_version IS NULL)),
 CHECK(state NOT IN ('PROPOSAL_LINKED','REVIEWED','CLOSED') OR proposal_id IS NOT NULL),
 CHECK((state IN ('REVIEWED','CLOSED'))=(decision_id IS NOT NULL)),
 CHECK((state='CLOSED')=((source_approval_revision IS NOT NULL) OR (rule_approval_decision_id IS NOT NULL))),
 CHECK(source_approval_revision IS NULL OR rule_approval_decision_id IS NULL),
 CHECK(JSON_VALID(receipt_json)),
 CHECK(schema_version='1.0.0'),
 CHECK(request_sha256 REGEXP '^[a-f0-9]{64}$' AND receipt_sha256 REGEXP '^[a-f0-9]{64}$' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK(previous_receipt_sha256 IS NULL OR previous_receipt_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK(classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:177-knowledge-feedback-origin-insert dbms:mysql runInTransaction:false failOnError:true splitStatements:false
CREATE TRIGGER knowledge_feedback_origin_insert BEFORE INSERT ON knowledge_feedback_origin FOR EACH ROW
BEGIN
 IF NOT(SHA2(CAST(NEW.original_json AS BINARY),256)<=>NEW.original_sha256)
 OR NOT(JSON_UNQUOTE(JSON_EXTRACT(NEW.original_json,'$.feedback_id'))<=>NEW.feedback_id)
 OR NOT(JSON_UNQUOTE(JSON_EXTRACT(NEW.original_json,'$.project_id'))<=>NEW.project_id)
 OR NOT(JSON_UNQUOTE(JSON_EXTRACT(NEW.original_json,'$.request.kind'))<=>NEW.kind)
 OR NOT(JSON_UNQUOTE(JSON_EXTRACT(NEW.original_json,'$.request.target_version'))<=>NEW.target_version)
 OR NOT(JSON_UNQUOTE(JSON_EXTRACT(NEW.original_json,'$.target.content_sha256'))<=>NEW.target_sha256)
 OR NOT(JSON_UNQUOTE(JSON_EXTRACT(NEW.original_json,'$.actor_id'))<=>NEW.created_by)
 OR NOT(JSON_UNQUOTE(JSON_EXTRACT(NEW.original_json,'$.classification'))<=>NEW.classification)
 OR NOT EXISTS(SELECT 1 FROM feedback f WHERE f.tenant_id=NEW.tenant_id AND f.project_id=NEW.project_id
    AND f.feedback_id=NEW.feedback_id AND f.target_version=NEW.target_version AND f.status='PENDING_REVIEW'
    AND f.revision=1 AND f.proposal_id IS NULL AND f.document_revision<=>NEW.document_revision
    AND f.created_by=NEW.created_by AND f.actor_id=NEW.created_by AND f.created_at=NEW.created_at AND f.classification=NEW.classification)
 OR EXISTS(SELECT 1 FROM operations_feedback_event e WHERE e.tenant_id=NEW.tenant_id AND e.feedback_id=NEW.feedback_id)
 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Knowledge feedback original mismatch'; END IF;
 IF NEW.kind='RULE' AND NOT EXISTS(SELECT 1 FROM rule_version r WHERE r.tenant_id=NEW.tenant_id AND r.rule_version=NEW.target_version AND r.content_sha256=NEW.target_sha256)
 OR NEW.kind='SOURCE' AND NOT EXISTS(SELECT 1 FROM source_version v JOIN source s ON s.tenant_id=v.tenant_id AND s.source_id=v.source_id
    WHERE v.tenant_id=NEW.tenant_id AND v.source_version=NEW.target_version AND v.original_sha256=NEW.target_sha256
    AND (s.ownership='ENTERPRISE' OR s.ownership='PROJECT' AND s.owner_project_id=NEW.project_id))
 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Knowledge feedback target mismatch'; END IF;
 IF NEW.review_id IS NOT NULL AND NOT EXISTS(SELECT 1 FROM review_decision d WHERE d.tenant_id=NEW.tenant_id AND d.project_id=NEW.project_id AND d.review_id=NEW.review_id AND d.document_revision=NEW.document_revision AND d.decision='RETURN')
 OR NEW.return_id IS NOT NULL AND NOT EXISTS(SELECT 1 FROM document_revision_return r WHERE r.tenant_id=NEW.tenant_id AND r.project_id=NEW.project_id AND r.return_id=NEW.return_id AND r.document_revision=NEW.document_revision AND r.decision='RETURN_FOR_REVISION')
 OR NEW.revised_document_revision IS NOT NULL AND NOT EXISTS(SELECT 1 FROM document_revision_meta r WHERE r.tenant_id=NEW.tenant_id AND r.project_id=NEW.project_id AND r.document_revision=NEW.revised_document_revision AND r.based_on_project_id=NEW.project_id AND r.based_on_document_revision=NEW.document_revision AND r.document_id=r.based_on_document_id)
 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Knowledge feedback human origin mismatch'; END IF;
END;

--changeset medical-agent:177-knowledge-feedback-event-insert dbms:mysql runInTransaction:false failOnError:true splitStatements:false
CREATE TRIGGER knowledge_feedback_event_insert BEFORE INSERT ON knowledge_feedback_event FOR EACH ROW
BEGIN
 IF NOT(SHA2(CAST(NEW.receipt_json AS BINARY),256)<=>NEW.receipt_sha256)
 OR NOT(JSON_UNQUOTE(JSON_EXTRACT(NEW.receipt_json,'$.feedback_id'))<=>NEW.feedback_id)
 OR NOT(JSON_UNQUOTE(JSON_EXTRACT(NEW.receipt_json,'$.project_id'))<=>NEW.project_id)
 OR NOT(JSON_UNQUOTE(JSON_EXTRACT(NEW.receipt_json,'$.state'))<=>NEW.state)
 OR NOT(JSON_UNQUOTE(JSON_EXTRACT(NEW.receipt_json,'$.revision'))<=>CAST(NEW.resulting_revision AS CHAR))
 OR NOT(JSON_UNQUOTE(JSON_EXTRACT(NEW.receipt_json,'$.actor_id'))<=>NEW.created_by)
 OR NOT(JSON_UNQUOTE(JSON_EXTRACT(NEW.receipt_json,'$.closure_scope'))<=>'KNOWLEDGE_RULE_FEEDBACK_ONLY')
 OR NOT(JSON_EXTRACT(NEW.receipt_json,'$.downstream_document_confirmation_claimed')<=>CAST('false' AS JSON))
 OR NOT EXISTS(SELECT 1 FROM knowledge_feedback_origin o WHERE o.tenant_id=NEW.tenant_id AND o.project_id=NEW.project_id AND o.feedback_id=NEW.feedback_id
    AND o.classification=NEW.classification AND o.schema_version=NEW.schema_version AND o.schema_manifest_sha256=NEW.schema_manifest_sha256
    AND o.original_sha256=JSON_UNQUOTE(JSON_EXTRACT(NEW.receipt_json,'$.original_sha256')))
 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Knowledge feedback event mismatch'; END IF;
 IF NEW.expected_revision=0 THEN
  IF NEW.proposal_id IS NOT NULL OR NOT EXISTS(SELECT 1 FROM feedback f WHERE f.tenant_id=NEW.tenant_id AND f.project_id=NEW.project_id AND f.feedback_id=NEW.feedback_id AND f.status='PENDING_REVIEW' AND f.revision=1 AND f.created_at=NEW.created_at)
  THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Knowledge feedback initial event mismatch'; END IF;
 ELSE
  IF NOT EXISTS(SELECT 1 FROM knowledge_feedback_event e JOIN feedback f ON f.tenant_id=e.tenant_id AND f.feedback_id=e.feedback_id
    WHERE e.tenant_id=NEW.tenant_id AND e.project_id=NEW.project_id AND e.feedback_id=NEW.feedback_id
    AND e.resulting_revision=NEW.expected_revision AND e.receipt_sha256=NEW.previous_receipt_sha256
    AND f.revision=NEW.expected_revision AND f.status=e.state AND NEW.created_at>=e.created_at
    AND ((e.state='PENDING_REVIEW' AND NEW.state IN ('PROPOSAL_LINKED','REJECTED'))
      OR (e.state='PROPOSAL_LINKED' AND NEW.state IN ('REVIEWED','REJECTED') AND e.proposal_id<=>NEW.proposal_id AND e.revision_version<=>NEW.revision_version)
      OR (e.state='REVIEWED' AND NEW.state='CLOSED' AND e.proposal_id=NEW.proposal_id AND e.revision_version=NEW.revision_version AND e.decision_id=NEW.decision_id)))
  THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Knowledge feedback event chain mismatch'; END IF;
 END IF;
 IF NEW.proposal_id IS NOT NULL AND NOT EXISTS(SELECT 1 FROM knowledge_feedback_origin o
    JOIN change_proposal_scope s ON s.tenant_id=o.tenant_id AND s.project_id=o.project_id AND s.kind=o.kind
    JOIN change_proposal p ON p.tenant_id=s.tenant_id AND p.proposal_id=s.proposal_id
    WHERE o.tenant_id=NEW.tenant_id AND o.feedback_id=NEW.feedback_id AND p.proposal_id=NEW.proposal_id
    AND p.old_version=o.target_version AND p.new_version=NEW.revision_version AND p.old_version<>p.new_version AND p.created_at>=o.created_at)
 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Knowledge feedback proposal mismatch'; END IF;
 IF NEW.state IN ('REVIEWED','CLOSED') AND NOT EXISTS(SELECT 1 FROM change_impact_decision d
    JOIN change_impact_application a ON a.tenant_id=d.tenant_id AND a.decision_id=d.decision_id AND a.proposal_id=d.proposal_id AND a.impact_id=d.impact_id
    JOIN change_proposal p ON p.tenant_id=d.tenant_id AND p.proposal_id=d.proposal_id
    JOIN impact_manifest i ON i.tenant_id=a.tenant_id AND i.impact_id=a.impact_id AND i.proposal_id=a.proposal_id AND i.content_sha256=a.snapshot_sha256 AND i.complete=TRUE
    WHERE d.tenant_id=NEW.tenant_id AND d.project_id=NEW.project_id AND d.decision_id=NEW.decision_id
    AND d.proposal_id=NEW.proposal_id AND d.decision='ACCEPT' AND p.status='ACCEPTED' AND p.reviewer_id=d.created_by)
 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Knowledge feedback actual application missing'; END IF;
 IF NEW.state='CLOSED' THEN
  IF NOT EXISTS(SELECT 1 FROM knowledge_feedback_origin o WHERE o.tenant_id=NEW.tenant_id AND o.feedback_id=NEW.feedback_id
    AND ((o.kind='SOURCE' AND NEW.source_approval_revision IS NOT NULL AND NEW.rule_approval_decision_id IS NULL)
      OR (o.kind='RULE' AND NEW.rule_approval_decision_id IS NOT NULL AND NEW.source_approval_revision IS NULL)))
  THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Knowledge feedback approval kind mismatch'; END IF;
  IF NEW.source_approval_revision IS NOT NULL AND NOT EXISTS(SELECT 1 FROM approval_decision d
    JOIN source_use_gate g ON g.tenant_id=d.tenant_id AND g.source_version=d.source_version AND g.parse_version=d.parse_version AND g.approval_revision=d.approval_revision
    WHERE d.tenant_id=NEW.tenant_id AND d.source_version=NEW.revision_version AND d.approval_revision=NEW.source_approval_revision
    AND d.decision='APPROVE' AND g.approval_status='APPROVED' AND g.upload_status='RECEIVED' AND g.lifecycle='ACTIVE')
  OR NEW.rule_approval_decision_id IS NOT NULL AND NOT EXISTS(SELECT 1 FROM rule_approval_decision d
    JOIN rule_version r ON r.tenant_id=d.tenant_id AND r.rule_version=d.rule_version AND r.content_sha256=d.rule_sha256
    JOIN rule_definition f ON f.tenant_id=r.tenant_id AND f.rule_version=r.rule_version AND f.revision=d.resulting_revision
    WHERE d.tenant_id=NEW.tenant_id AND d.rule_version=NEW.revision_version AND d.decision_id=NEW.rule_approval_decision_id
    AND d.decision='APPROVE' AND d.to_status='APPROVED' AND r.status='APPROVED')
  THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Knowledge feedback current approval missing'; END IF;
 END IF;
END;

--changeset medical-agent:177-knowledge-feedback-origin-no-update dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER knowledge_feedback_origin_no_update BEFORE UPDATE ON knowledge_feedback_origin FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable knowledge feedback original';
--changeset medical-agent:177-knowledge-feedback-origin-no-delete dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER knowledge_feedback_origin_no_delete BEFORE DELETE ON knowledge_feedback_origin FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable knowledge feedback original';
--changeset medical-agent:177-knowledge-feedback-event-no-update dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER knowledge_feedback_event_no_update BEFORE UPDATE ON knowledge_feedback_event FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable knowledge feedback event';
--changeset medical-agent:177-knowledge-feedback-event-no-delete dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER knowledge_feedback_event_no_delete BEFORE DELETE ON knowledge_feedback_event FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable knowledge feedback event';
--changeset medical-agent:177-feedback-domain-exclusivity dbms:mysql runInTransaction:false failOnError:true splitStatements:false
CREATE TRIGGER operations_feedback_knowledge_exclusion BEFORE INSERT ON operations_feedback_event FOR EACH ROW
BEGIN
 IF EXISTS(SELECT 1 FROM knowledge_feedback_origin o WHERE o.tenant_id=NEW.tenant_id AND o.feedback_id=NEW.feedback_id)
 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Feedback domain is immutable'; END IF;
END;
--changeset medical-agent:177-knowledge-feedback-head-transition dbms:mysql runInTransaction:false failOnError:true splitStatements:false
CREATE TRIGGER knowledge_feedback_head_transition BEFORE UPDATE ON feedback FOR EACH ROW
BEGIN
 IF EXISTS(SELECT 1 FROM knowledge_feedback_origin o WHERE o.tenant_id=OLD.tenant_id AND o.feedback_id=OLD.feedback_id) THEN
  IF NOT(NEW.tenant_id<=>OLD.tenant_id AND NEW.project_id<=>OLD.project_id AND NEW.feedback_id<=>OLD.feedback_id
    AND NEW.target_version<=>OLD.target_version AND NEW.task_id<=>OLD.task_id AND NEW.document_revision<=>OLD.document_revision
    AND NEW.reason<=>OLD.reason AND NEW.actor_id<=>OLD.actor_id AND NEW.proposal_id<=>OLD.proposal_id
    AND NEW.created_by<=>OLD.created_by AND NEW.created_at<=>OLD.created_at AND NEW.classification<=>OLD.classification
    AND NEW.revision=OLD.revision+1 AND NEW.updated_at>=OLD.updated_at)
    OR NOT((OLD.status='PENDING_REVIEW' AND NEW.status IN ('PROPOSAL_LINKED','REJECTED'))
      OR (OLD.status='PROPOSAL_LINKED' AND NEW.status IN ('REVIEWED','REJECTED')) OR (OLD.status='REVIEWED' AND NEW.status='CLOSED'))
    OR NOT EXISTS(SELECT 1 FROM knowledge_feedback_event e WHERE e.tenant_id=OLD.tenant_id AND e.project_id=OLD.project_id AND e.feedback_id=OLD.feedback_id
      AND e.expected_revision=OLD.revision AND e.resulting_revision=NEW.revision AND e.state=NEW.status AND e.created_at=NEW.updated_at)
  THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Invalid knowledge feedback head transition'; END IF;
 END IF;
END;
