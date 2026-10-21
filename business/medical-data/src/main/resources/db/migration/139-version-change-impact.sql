--liquibase formatted sql
--changeset medical-agent:1.0.0-139-change-proposal-scope dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE change_proposal_scope (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
 project_id CHAR(36) CHARACTER SET ascii NOT NULL, proposal_id CHAR(36) CHARACTER SET ascii NOT NULL,
 kind VARCHAR(16) NOT NULL, proposal_json JSON NOT NULL, proposal_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 PRIMARY KEY(tenant_id,proposal_id),
 FOREIGN KEY(tenant_id,proposal_id) REFERENCES change_proposal(tenant_id,proposal_id),
 FOREIGN KEY(tenant_id,project_id) REFERENCES project(tenant_id,project_id),
 CHECK(kind IN ('SOURCE','RULE','FACT')),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
--changeset medical-agent:1.0.0-139-change-impact-decision dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE change_impact_decision (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
 project_id CHAR(36) CHARACTER SET ascii NOT NULL, decision_id CHAR(36) CHARACTER SET ascii NOT NULL,
 proposal_id CHAR(36) CHARACTER SET ascii NOT NULL, impact_id CHAR(36) CHARACTER SET ascii NOT NULL,
 decision VARCHAR(16) NOT NULL, decision_json JSON NOT NULL, decision_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 auth_epoch BIGINT UNSIGNED NOT NULL, governance_epoch BIGINT UNSIGNED NOT NULL,
 authority_json JSON NOT NULL, identity_mode VARCHAR(16) NOT NULL,
 PRIMARY KEY(tenant_id,decision_id), UNIQUE KEY uq_change_decision_proposal(tenant_id,proposal_id),
 FOREIGN KEY(tenant_id,project_id) REFERENCES project(tenant_id,project_id),
 FOREIGN KEY(tenant_id,proposal_id) REFERENCES change_proposal_scope(tenant_id,proposal_id),
 FOREIGN KEY(tenant_id,impact_id) REFERENCES impact_manifest(tenant_id,impact_id),
 CHECK(decision IN ('ACCEPT','REJECT')),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
--changeset medical-agent:1.0.0-139-change-revision-entry dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE change_revision_entry (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
 entry_id CHAR(36) CHARACTER SET ascii NOT NULL, decision_id CHAR(36) CHARACTER SET ascii NOT NULL,
 project_id CHAR(36) CHARACTER SET ascii NOT NULL, document_id CHAR(36) CHARACTER SET ascii NOT NULL,
 document_revision CHAR(36) CHARACTER SET ascii NOT NULL, requested_action VARCHAR(24) NOT NULL,
 state VARCHAR(32) NOT NULL, reason TEXT NOT NULL,
 PRIMARY KEY(tenant_id,entry_id), UNIQUE KEY uq_change_entry_fixed(tenant_id,project_id,entry_id,document_revision), UNIQUE KEY uq_change_entry_document(tenant_id,decision_id,project_id,document_revision),
 FOREIGN KEY(tenant_id,decision_id) REFERENCES change_impact_decision(tenant_id,decision_id),
 FOREIGN KEY(tenant_id,project_id,document_id,document_revision) REFERENCES document_revision(tenant_id,project_id,document_id,document_revision),
 CHECK(state='PENDING_CONFIRMATION'), CHECK(requested_action IN ('NEW_REVISION','REVALIDATE')), CHECK(CHAR_LENGTH(reason)>0),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
--changeset medical-agent:1.0.0-139-change-impact-application dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE change_impact_application (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
 decision_id CHAR(36) CHARACTER SET ascii NOT NULL, proposal_id CHAR(36) CHARACTER SET ascii NOT NULL,
 impact_id CHAR(36) CHARACTER SET ascii NOT NULL, invalidations_json JSON NOT NULL,
 snapshot_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 PRIMARY KEY(tenant_id,decision_id),
 FOREIGN KEY(tenant_id,decision_id) REFERENCES change_impact_decision(tenant_id,decision_id),
 FOREIGN KEY(tenant_id,proposal_id) REFERENCES change_proposal(tenant_id,proposal_id),
 FOREIGN KEY(tenant_id,impact_id) REFERENCES impact_manifest(tenant_id,impact_id),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
--changeset medical-agent:1.0.0-139-change-impact-operation dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE change_impact_operation (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
 actor_id CHAR(36) CHARACTER SET ascii NOT NULL, operation_name VARCHAR(24) NOT NULL,
 idempotency_key VARCHAR(128) CHARACTER SET ascii NOT NULL, request_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 receipt_json JSON NOT NULL, receipt_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 PRIMARY KEY(tenant_id,actor_id,operation_name,idempotency_key),
 FOREIGN KEY(tenant_id,actor_id) REFERENCES tenant_member(tenant_id,user_id),
 CHECK(operation_name IN ('PROPOSE','SCAN','DECIDE')),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
--changeset medical-agent:1.0.0-139-change_proposal_scope-update-guard dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER trg_139_change_proposal_scope_update BEFORE UPDATE ON change_proposal_scope FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable version change proof';
--changeset medical-agent:1.0.0-139-change_proposal_scope-delete-guard dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER trg_139_change_proposal_scope_delete BEFORE DELETE ON change_proposal_scope FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable version change proof';
--changeset medical-agent:1.0.0-139-change_impact_decision-update-guard dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER trg_139_change_impact_decision_update BEFORE UPDATE ON change_impact_decision FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable version change proof';
--changeset medical-agent:1.0.0-139-change_impact_decision-delete-guard dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER trg_139_change_impact_decision_delete BEFORE DELETE ON change_impact_decision FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable version change proof';
--changeset medical-agent:1.0.0-139-change_revision_entry-update-guard dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER trg_139_change_revision_entry_update BEFORE UPDATE ON change_revision_entry FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable version change proof';
--changeset medical-agent:1.0.0-139-change_revision_entry-delete-guard dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER trg_139_change_revision_entry_delete BEFORE DELETE ON change_revision_entry FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable version change proof';
--changeset medical-agent:1.0.0-139-change_impact_application-update-guard dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER trg_139_change_impact_application_update BEFORE UPDATE ON change_impact_application FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable version change proof';
--changeset medical-agent:1.0.0-139-change_impact_application-delete-guard dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER trg_139_change_impact_application_delete BEFORE DELETE ON change_impact_application FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable version change proof';
--changeset medical-agent:1.0.0-139-change_impact_operation-update-guard dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER trg_139_change_impact_operation_update BEFORE UPDATE ON change_impact_operation FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable version change proof';
--changeset medical-agent:1.0.0-139-change_impact_operation-delete-guard dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER trg_139_change_impact_operation_delete BEFORE DELETE ON change_impact_operation FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable version change proof';
--changeset medical-agent:1.0.0-139-impact_manifest-update-guard dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER trg_139_impact_manifest_update BEFORE UPDATE ON impact_manifest FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable version change proof';
--changeset medical-agent:1.0.0-139-impact_manifest-delete-guard dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER trg_139_impact_manifest_delete BEFORE DELETE ON impact_manifest FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable version change proof';
--changeset medical-agent:1.0.0-139-proposal-identity-guard dbms:mysql runInTransaction:false failOnError:true splitStatements:false
CREATE TRIGGER trg_139_proposal_identity BEFORE UPDATE ON change_proposal FOR EACH ROW
BEGIN
 IF NOT (OLD.tenant_id <=> NEW.tenant_id) OR NOT (OLD.proposal_id <=> NEW.proposal_id)
 OR NOT (OLD.old_version <=> NEW.old_version) OR NOT (OLD.new_version <=> NEW.new_version)
 OR NOT (OLD.reason <=> NEW.reason) OR NOT (OLD.reviewer_id <=> NEW.reviewer_id)
 OR NOT (OLD.created_by <=> NEW.created_by) OR NOT (OLD.created_at <=> NEW.created_at)
 OR NOT (OLD.classification <=> NEW.classification) THEN
 SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable change proposal identity';
 END IF;
END;
--changeset medical-agent:1.0.0-139-impact-revalidation-resolution dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE change_impact_resolution (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 entry_id CHAR(36) CHARACTER SET ascii NOT NULL, batch_id CHAR(36) CHARACTER SET ascii NOT NULL,
 document_revision CHAR(36) CHARACTER SET ascii NOT NULL, baseline_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 check_ids_json JSON NOT NULL, schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
 PRIMARY KEY(tenant_id,project_id,entry_id,batch_id),
 FOREIGN KEY(tenant_id,project_id,entry_id,document_revision) REFERENCES change_revision_entry(tenant_id,project_id,entry_id,document_revision),
 FOREIGN KEY(tenant_id,project_id,batch_id,document_revision) REFERENCES review_batch(tenant_id,project_id,batch_id,document_revision),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
--changeset medical-agent:1.0.0-139-impact-resolution-update dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER trg_139_resolution_update BEFORE UPDATE ON change_impact_resolution FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable impact resolution';
--changeset medical-agent:1.0.0-139-impact-resolution-delete dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER trg_139_resolution_delete BEFORE DELETE ON change_impact_resolution FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable impact resolution';
