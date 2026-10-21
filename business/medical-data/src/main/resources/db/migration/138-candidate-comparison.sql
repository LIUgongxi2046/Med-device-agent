--liquibase formatted sql
--changeset medical-agent:1.0.0-138-candidate-comparison-intent dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE candidate_comparison_intent (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 comparison_id CHAR(36) CHARACTER SET ascii NOT NULL, operation_id CHAR(36) CHARACTER SET ascii NOT NULL,
 candidate_id CHAR(36) CHARACTER SET ascii NOT NULL, target_document_revision CHAR(36) CHARACTER SET ascii NOT NULL,
 artifact_id CHAR(36) CHARACTER SET ascii NOT NULL, access_set_id CHAR(36) CHARACTER SET ascii NOT NULL,
 intent_json JSON NOT NULL, intent_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 canonical_core LONGTEXT NOT NULL, core_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
 PRIMARY KEY(tenant_id,comparison_id), UNIQUE KEY uq_candidate_compare_operation(tenant_id,operation_id),
 FOREIGN KEY(tenant_id,project_id) REFERENCES project(tenant_id,project_id),
 FOREIGN KEY(tenant_id,project_id,candidate_id) REFERENCES candidate(tenant_id,project_id,candidate_id),
 FOREIGN KEY(tenant_id,project_id,target_document_revision) REFERENCES document_revision_meta(tenant_id,project_id,document_revision),
 FOREIGN KEY(tenant_id,operation_id) REFERENCES operation_record(tenant_id,operation_id),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK(schema_version='1.0.0' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK(classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
--changeset medical-agent:1.0.0-138-candidate-comparison-completion dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE candidate_comparison_completion (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 project_key VARCHAR(36) CHARACTER SET ascii GENERATED ALWAYS AS (COALESCE(project_id,'ENTERPRISE')) STORED,
 comparison_id CHAR(36) CHARACTER SET ascii NOT NULL, operation_id CHAR(36) CHARACTER SET ascii NOT NULL,
 artifact_id CHAR(36) CHARACTER SET ascii NOT NULL, object_json JSON NOT NULL,
 receipt_json JSON NOT NULL, receipt_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 audit_id CHAR(36) CHARACTER SET ascii NOT NULL, event_id CHAR(36) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
 PRIMARY KEY(tenant_id,comparison_id), UNIQUE KEY uq_candidate_compare_done_operation(tenant_id,operation_id),
 FOREIGN KEY(tenant_id,comparison_id) REFERENCES candidate_comparison_intent(tenant_id,comparison_id),
 FOREIGN KEY(tenant_id,operation_id) REFERENCES operation_record(tenant_id,operation_id),
 FOREIGN KEY(tenant_id,project_id) REFERENCES project(tenant_id,project_id),
 FOREIGN KEY(tenant_id,project_key,artifact_id) REFERENCES artifact(tenant_id,project_key,artifact_id),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 FOREIGN KEY(tenant_id,audit_id) REFERENCES audit_event(tenant_id,audit_id),
 FOREIGN KEY(tenant_id,event_id) REFERENCES outbox_event(tenant_id,event_id),
 CHECK(schema_version='1.0.0' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK(classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-138-candidate_comparison_intent-update dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER candidate_comparison_intent_update BEFORE UPDATE ON candidate_comparison_intent FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable candidate comparison original';

--changeset medical-agent:1.0.0-138-candidate_comparison_intent-delete dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER candidate_comparison_intent_delete BEFORE DELETE ON candidate_comparison_intent FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable candidate comparison original';

--changeset medical-agent:1.0.0-138-candidate_comparison_completion-update dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER candidate_comparison_completion_update BEFORE UPDATE ON candidate_comparison_completion FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable candidate comparison original';

--changeset medical-agent:1.0.0-138-candidate_comparison_completion-delete dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER candidate_comparison_completion_delete BEFORE DELETE ON candidate_comparison_completion FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable candidate comparison original';
