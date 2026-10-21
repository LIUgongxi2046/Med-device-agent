--liquibase formatted sql

--changeset medical-agent:1.0.0-131-document-adoption-preview dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE document_adoption_preview (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 pending_action_id CHAR(36) CHARACTER SET ascii NOT NULL, candidate_id CHAR(36) CHARACTER SET ascii NOT NULL,
 document_id CHAR(36) CHARACTER SET ascii NOT NULL, base_document_revision CHAR(36) CHARACTER SET ascii NOT NULL,
 preview_operation_id CHAR(36) CHARACTER SET ascii NOT NULL, arguments_json JSON NOT NULL,
 arguments_sha256 CHAR(64) CHARACTER SET ascii NOT NULL, canonical_body LONGTEXT NOT NULL,
 body_sha256 CHAR(64) CHARACTER SET ascii NOT NULL, receipt_json JSON NOT NULL, receipt_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 audit_id CHAR(36) CHARACTER SET ascii NOT NULL, event_id CHAR(36) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
 schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 PRIMARY KEY (tenant_id,pending_action_id), UNIQUE KEY uq_adoption_preview_operation (tenant_id,preview_operation_id),
 FOREIGN KEY (tenant_id,pending_action_id) REFERENCES pending_action(tenant_id,action_id),
 FOREIGN KEY (tenant_id,project_id,candidate_id) REFERENCES candidate(tenant_id,project_id,candidate_id),
 FOREIGN KEY (tenant_id,project_id,document_id) REFERENCES document_head(tenant_id,project_id,document_id),
 FOREIGN KEY (tenant_id,project_id,base_document_revision) REFERENCES document_revision_meta(tenant_id,project_id,document_revision),
 FOREIGN KEY (tenant_id,preview_operation_id) REFERENCES operation_record(tenant_id,operation_id),
 FOREIGN KEY(tenant_id,project_id) REFERENCES project(tenant_id,project_id),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 FOREIGN KEY(tenant_id,audit_id) REFERENCES audit_event(tenant_id,audit_id),
 FOREIGN KEY(tenant_id,event_id) REFERENCES outbox_event(tenant_id,event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-131-document-adoption-intent dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE document_adoption_intent (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 operation_id CHAR(36) CHARACTER SET ascii NOT NULL, adoption_id CHAR(36) CHARACTER SET ascii NOT NULL,
 pending_action_id CHAR(36) CHARACTER SET ascii NOT NULL, candidate_id CHAR(36) CHARACTER SET ascii NOT NULL,
 arguments_sha256 CHAR(64) CHARACTER SET ascii NOT NULL, body_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
 schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 PRIMARY KEY(tenant_id,operation_id), UNIQUE KEY uq_document_adoption_action(tenant_id,pending_action_id),
 UNIQUE KEY uq_document_adoption_id(tenant_id,adoption_id),
 FOREIGN KEY(tenant_id,operation_id) REFERENCES operation_record(tenant_id,operation_id),
 FOREIGN KEY(tenant_id,pending_action_id) REFERENCES document_adoption_preview(tenant_id,pending_action_id),
 FOREIGN KEY(tenant_id,project_id,candidate_id) REFERENCES candidate(tenant_id,project_id,candidate_id),
 FOREIGN KEY(tenant_id,project_id) REFERENCES project(tenant_id,project_id),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-131-document-adoption-completion dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE document_adoption_completion (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 operation_id CHAR(36) CHARACTER SET ascii NOT NULL, adoption_id CHAR(36) CHARACTER SET ascii NOT NULL,
 document_revision CHAR(36) CHARACTER SET ascii NOT NULL, decision_id CHAR(36) CHARACTER SET ascii NOT NULL,
 receipt_json JSON NOT NULL, receipt_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 audit_id CHAR(36) CHARACTER SET ascii NOT NULL, event_id CHAR(36) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
 schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 PRIMARY KEY(tenant_id,operation_id), UNIQUE KEY uq_document_adoption_complete(tenant_id,adoption_id),
 FOREIGN KEY(tenant_id,operation_id) REFERENCES document_adoption_intent(tenant_id,operation_id),
 FOREIGN KEY(tenant_id,project_id,adoption_id) REFERENCES candidate_adoption(tenant_id,project_id,adoption_id),
 FOREIGN KEY(tenant_id,project_id,document_revision) REFERENCES document_revision_meta(tenant_id,project_id,document_revision),
 FOREIGN KEY(tenant_id,decision_id) REFERENCES version_registry(tenant_id,version_id),
 FOREIGN KEY(tenant_id,project_id) REFERENCES project(tenant_id,project_id),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 FOREIGN KEY(tenant_id,audit_id) REFERENCES audit_event(tenant_id,audit_id),
 FOREIGN KEY(tenant_id,event_id) REFERENCES outbox_event(tenant_id,event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-131-document_adoption_preview-update dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER document_adoption_preview_update BEFORE UPDATE ON document_adoption_preview FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable document adoption original';

--changeset medical-agent:1.0.0-131-document_adoption_preview-delete dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER document_adoption_preview_delete BEFORE DELETE ON document_adoption_preview FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable document adoption original';

--changeset medical-agent:1.0.0-131-document_adoption_intent-update dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER document_adoption_intent_update BEFORE UPDATE ON document_adoption_intent FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable document adoption original';

--changeset medical-agent:1.0.0-131-document_adoption_intent-delete dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER document_adoption_intent_delete BEFORE DELETE ON document_adoption_intent FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable document adoption original';

--changeset medical-agent:1.0.0-131-document_adoption_completion-update dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER document_adoption_completion_update BEFORE UPDATE ON document_adoption_completion FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable document adoption original';

--changeset medical-agent:1.0.0-131-document_adoption_completion-delete dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER document_adoption_completion_delete BEFORE DELETE ON document_adoption_completion FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable document adoption original';
