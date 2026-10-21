--liquibase formatted sql
--changeset medical-agent:137-01-review-confirmation-receipts dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE document_review_confirmation_receipt (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 operation_id CHAR(36) CHARACTER SET ascii NOT NULL, document_id CHAR(36) CHARACTER SET ascii NOT NULL,
 document_revision CHAR(36) CHARACTER SET ascii NOT NULL, batch_id CHAR(36) CHARACTER SET ascii NULL,
 actor_id CHAR(36) CHARACTER SET ascii NOT NULL, idempotency_key VARCHAR(128) CHARACTER SET ascii NOT NULL,
 operation_kind VARCHAR(24) NOT NULL, request_json JSON NOT NULL, request_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 baseline_json JSON NOT NULL, baseline_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 receipt_json JSON NOT NULL, receipt_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 resulting_revision BIGINT UNSIGNED NOT NULL,
 schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
 PRIMARY KEY(tenant_id,project_id,operation_id), UNIQUE KEY uq_review_confirmation_key(tenant_id,actor_id,idempotency_key),
 FOREIGN KEY(tenant_id,operation_id) REFERENCES operation_record(tenant_id,operation_id),
 FOREIGN KEY(tenant_id,project_id,batch_id,document_revision) REFERENCES review_batch(tenant_id,project_id,batch_id,document_revision),
 FOREIGN KEY(tenant_id,project_id,document_id,document_revision) REFERENCES document_revision_meta(tenant_id,project_id,document_id,document_revision),
 FOREIGN KEY(tenant_id,actor_id) REFERENCES tenant_member(tenant_id,user_id),
 CHECK(schema_version='1.0.0' AND actor_id=created_by),
 CHECK((operation_kind='DECIDE' AND batch_id IS NOT NULL) OR (operation_kind='RESUBMIT' AND batch_id IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
--changeset medical-agent:137-02-formal-review-assignments dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE document_review_revision_assignment (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 assignment_id CHAR(36) CHARACTER SET ascii NOT NULL, review_id CHAR(36) CHARACTER SET ascii NOT NULL,
 document_id CHAR(36) CHARACTER SET ascii NOT NULL, document_revision CHAR(36) CHARACTER SET ascii NOT NULL,
 task_id CHAR(36) CHARACTER SET ascii NOT NULL, task_revision CHAR(36) CHARACTER SET ascii NOT NULL,
 assignee_id CHAR(36) CHARACTER SET ascii NOT NULL, reason TEXT NOT NULL,
 schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
 PRIMARY KEY(tenant_id,project_id,assignment_id), UNIQUE KEY uq_formal_review_assignment(tenant_id,project_id,review_id),
 FOREIGN KEY(tenant_id,project_id,review_id) REFERENCES review_decision(tenant_id,project_id,review_id),
 FOREIGN KEY(tenant_id,project_id,document_id,document_revision) REFERENCES document_revision_meta(tenant_id,project_id,document_id,document_revision),
 FOREIGN KEY(tenant_id,project_id,task_id,task_revision) REFERENCES task_revision(tenant_id,project_id,task_id,task_revision),
 FOREIGN KEY(tenant_id,assignee_id) REFERENCES tenant_member(tenant_id,user_id),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id), CHECK(schema_version='1.0.0')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
--changeset medical-agent:137-03-review-resubmission dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE document_review_resubmission (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 resubmission_id CHAR(36) CHARACTER SET ascii NOT NULL, operation_id CHAR(36) CHARACTER SET ascii NOT NULL,
 formal_assignment_id CHAR(36) CHARACTER SET ascii NULL, legacy_assignment_id CHAR(36) CHARACTER SET ascii NULL,
 document_id CHAR(36) CHARACTER SET ascii NOT NULL, document_revision CHAR(36) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
 PRIMARY KEY(tenant_id,project_id,resubmission_id), UNIQUE KEY uq_formal_resubmission(tenant_id,project_id,formal_assignment_id),
 UNIQUE KEY uq_legacy_resubmission(tenant_id,project_id,legacy_assignment_id),
 FOREIGN KEY(tenant_id,project_id,operation_id) REFERENCES document_review_confirmation_receipt(tenant_id,project_id,operation_id),
 FOREIGN KEY(tenant_id,project_id,formal_assignment_id) REFERENCES document_review_revision_assignment(tenant_id,project_id,assignment_id),
 FOREIGN KEY(tenant_id,project_id,legacy_assignment_id) REFERENCES document_revision_return_assignment(tenant_id,project_id,assignment_id),
 FOREIGN KEY(tenant_id,project_id,document_id,document_revision) REFERENCES document_revision_meta(tenant_id,project_id,document_id,document_revision),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK((formal_assignment_id IS NULL) <> (legacy_assignment_id IS NULL)), CHECK(schema_version='1.0.0')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:137-04-update dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER b09_4_update BEFORE UPDATE ON document_review_confirmation_receipt FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable review record';

--changeset medical-agent:137-04-delete dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER b09_4_delete BEFORE DELETE ON document_review_confirmation_receipt FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable review record';

--changeset medical-agent:137-05-update dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER b09_5_update BEFORE UPDATE ON document_review_revision_assignment FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable review record';

--changeset medical-agent:137-05-delete dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER b09_5_delete BEFORE DELETE ON document_review_revision_assignment FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable review record';

--changeset medical-agent:137-06-update dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER b09_6_update BEFORE UPDATE ON document_review_resubmission FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable review record';

--changeset medical-agent:137-06-delete dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER b09_6_delete BEFORE DELETE ON document_review_resubmission FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable review record';

--changeset medical-agent:137-07-update dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER b09_7_update BEFORE UPDATE ON review_batch FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable review record';

--changeset medical-agent:137-07-delete dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER b09_7_delete BEFORE DELETE ON review_batch FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable review record';

--changeset medical-agent:137-08-update dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER b09_8_update BEFORE UPDATE ON review_batch_item FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable review record';

--changeset medical-agent:137-08-delete dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER b09_8_delete BEFORE DELETE ON review_batch_item FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable review record';

--changeset medical-agent:137-09-update dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER b09_9_update BEFORE UPDATE ON review_decision FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable review record';

--changeset medical-agent:137-09-delete dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER b09_9_delete BEFORE DELETE ON review_decision FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable review record';
