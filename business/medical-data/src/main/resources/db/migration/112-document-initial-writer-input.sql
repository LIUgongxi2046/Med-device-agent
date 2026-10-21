--liquibase formatted sql
--changeset medical-agent:1.0.0-1120-document-initial-intent dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE document_initial_intent (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 run_id CHAR(36) CHARACTER SET ascii NOT NULL, task_id CHAR(36) CHARACTER SET ascii NOT NULL,
 document_id CHAR(36) CHARACTER SET ascii NOT NULL, document_revision CHAR(36) CHARACTER SET ascii NOT NULL,
 body_schema_revision CHAR(36) CHARACTER SET ascii NOT NULL, schema_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 schema_source MEDIUMTEXT NOT NULL, canonical_body MEDIUMTEXT NOT NULL, body_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 producer_pins_json JSON NOT NULL, authority_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 intent_json JSON NOT NULL, intent_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
 schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 FOREIGN KEY(tenant_id,project_id) REFERENCES project(tenant_id,project_id),
 CHECK(schema_version='1.0.0' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK(classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH')),
 UNIQUE KEY uq_initial_project_run(tenant_id,project_id,run_id),
 CHECK(schema_sha256 REGEXP '^[a-f0-9]{64}$' AND body_sha256 REGEXP '^[a-f0-9]{64}$' AND authority_sha256 REGEXP '^[a-f0-9]{64}$' AND intent_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK(JSON_TYPE(intent_json)='OBJECT' AND JSON_TYPE(producer_pins_json)='OBJECT'),
 PRIMARY KEY(tenant_id,run_id), UNIQUE KEY uq_document_initial_id(tenant_id,project_id,document_id),
 FOREIGN KEY(tenant_id,project_id,run_id) REFERENCES task_run(tenant_id,project_id,run_id),
 FOREIGN KEY(tenant_id,project_id,task_id) REFERENCES task_head(tenant_id,project_id,task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
--changeset medical-agent:1.0.0-1121-document-initial-object dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE document_initial_object (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, run_id CHAR(36) CHARACTER SET ascii NOT NULL,
 kind VARCHAR(40) CHARACTER SET ascii NOT NULL, write_id CHAR(36) CHARACTER SET ascii NOT NULL,
 artifact_id CHAR(36) CHARACTER SET ascii NOT NULL, object_key VARCHAR(1024) CHARACTER SET ascii NOT NULL,
 canonical_payload MEDIUMTEXT NOT NULL, payload_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 intent_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL,
 schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 project_id CHAR(36) CHARACTER SET ascii NOT NULL, classification VARCHAR(24) NOT NULL,
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 FOREIGN KEY(tenant_id,project_id) REFERENCES project(tenant_id,project_id),
 CHECK(schema_version='1.0.0' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK(classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH')),
 FOREIGN KEY(tenant_id,project_id,run_id) REFERENCES document_initial_intent(tenant_id,project_id,run_id),
 CHECK(payload_sha256 REGEXP '^[a-f0-9]{64}$' AND intent_sha256 REGEXP '^[a-f0-9]{64}$' AND JSON_VALID(canonical_payload)),
 PRIMARY KEY(tenant_id,run_id,kind), UNIQUE KEY uq_document_initial_write(tenant_id,write_id),
 FOREIGN KEY(tenant_id,run_id) REFERENCES document_initial_intent(tenant_id,run_id),
 CHECK(kind IN ('DOCUMENT_BODY','DOCUMENT_FIELDS','DOCUMENT_EVIDENCE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
--changeset medical-agent:1.0.0-1122-document-initial-receipt dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE document_initial_registration (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 run_id CHAR(36) CHARACTER SET ascii NOT NULL, document_id CHAR(36) CHARACTER SET ascii NOT NULL,
 document_revision CHAR(36) CHARACTER SET ascii NOT NULL, receipt_json JSON NOT NULL,
 receipt_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL,
 schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 classification VARCHAR(24) NOT NULL,
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 FOREIGN KEY(tenant_id,project_id) REFERENCES project(tenant_id,project_id),
 CHECK(schema_version='1.0.0' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK(classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH')),
 FOREIGN KEY(tenant_id,project_id,run_id) REFERENCES document_initial_intent(tenant_id,project_id,run_id),
 CHECK(receipt_sha256 REGEXP '^[a-f0-9]{64}$' AND JSON_TYPE(receipt_json)='OBJECT'),
 PRIMARY KEY(tenant_id,run_id),
 FOREIGN KEY(tenant_id,run_id) REFERENCES document_initial_intent(tenant_id,run_id),
 FOREIGN KEY(tenant_id,project_id,document_id,document_revision) REFERENCES document_revision_meta(tenant_id,project_id,document_id,document_revision)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-1123-initial-immutable-document_initial_intent-update dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER imm_initial_1123 BEFORE UPDATE ON document_initial_intent FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IMMUTABLE_DOCUMENT_INITIAL';

--changeset medical-agent:1.0.0-1124-initial-immutable-document_initial_intent-delete dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER imm_initial_1124 BEFORE DELETE ON document_initial_intent FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IMMUTABLE_DOCUMENT_INITIAL';

--changeset medical-agent:1.0.0-1125-initial-immutable-document_initial_object-update dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER imm_initial_1125 BEFORE UPDATE ON document_initial_object FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IMMUTABLE_DOCUMENT_INITIAL';

--changeset medical-agent:1.0.0-1126-initial-immutable-document_initial_object-delete dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER imm_initial_1126 BEFORE DELETE ON document_initial_object FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IMMUTABLE_DOCUMENT_INITIAL';

--changeset medical-agent:1.0.0-1127-initial-immutable-document_initial_registration-update dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER imm_initial_1127 BEFORE UPDATE ON document_initial_registration FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IMMUTABLE_DOCUMENT_INITIAL';

--changeset medical-agent:1.0.0-1128-initial-immutable-document_initial_registration-delete dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER imm_initial_1128 BEFORE DELETE ON document_initial_registration FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IMMUTABLE_DOCUMENT_INITIAL';
