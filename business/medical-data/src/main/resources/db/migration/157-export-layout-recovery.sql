--liquibase formatted sql
--changeset medical-agent:1.0.0-157-export-layout-recovery dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE export_layout_profile (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 profile_id CHAR(36) CHARACTER SET ascii NOT NULL, document_id CHAR(36) CHARACTER SET ascii NOT NULL,
 document_revision CHAR(36) CHARACTER SET ascii NOT NULL, request_key VARCHAR(128) NOT NULL,
 profile_json JSON NOT NULL, content_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 specification_artifact_id CHAR(36) CHARACTER SET ascii NULL, specification_sha256 CHAR(64) CHARACTER SET ascii NULL,
 schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
 PRIMARY KEY(tenant_id,project_id,profile_id), UNIQUE KEY uq_export_layout_request(tenant_id,created_by,request_key),
 FOREIGN KEY(tenant_id,project_id,document_id,document_revision) REFERENCES document_revision_meta(tenant_id,project_id,document_id,document_revision),
 FOREIGN KEY(tenant_id,profile_id,content_sha256) REFERENCES version_registry(tenant_id,version_id,content_sha256),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 FOREIGN KEY(tenant_id,specification_artifact_id) REFERENCES artifact(tenant_id,artifact_id),
 FOREIGN KEY(tenant_id,specification_artifact_id,specification_sha256) REFERENCES version_registry(tenant_id,version_id,content_sha256),
 CHECK((specification_artifact_id IS NULL)=(specification_sha256 IS NULL)),
 CHECK(schema_version='1.0.0' AND JSON_TYPE(profile_json)='OBJECT'),
 CHECK(content_sha256 REGEXP '^[0-9a-f]{64}$'),
 CHECK(classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-157-export-verification dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE export_layout_verification (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 verification_id CHAR(36) CHARACTER SET ascii NOT NULL, export_item_id CHAR(36) CHARACTER SET ascii NOT NULL,
 verification_no BIGINT UNSIGNED NOT NULL,
 request_key VARCHAR(128) NOT NULL, request_json JSON NOT NULL, content_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 output_sha256 CHAR(64) CHARACTER SET ascii NOT NULL, output_artifact_id CHAR(36) CHARACTER SET ascii NOT NULL, profile_id CHAR(36) CHARACTER SET ascii NOT NULL,
 channel_check VARCHAR(24) NOT NULL, visual_check VARCHAR(24) NOT NULL,
 evidence_artifact_id CHAR(36) CHARACTER SET ascii NULL, evidence_sha256 CHAR(64) CHARACTER SET ascii NULL,
 schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
 PRIMARY KEY(tenant_id,project_id,verification_id), UNIQUE KEY uq_export_verification_sequence(tenant_id,project_id,export_item_id,verification_no),
 UNIQUE KEY uq_export_verification_request(tenant_id,created_by,request_key),
 FOREIGN KEY(tenant_id,project_id,export_item_id) REFERENCES export_item(tenant_id,project_id,export_item_id),
 FOREIGN KEY(tenant_id,project_id,profile_id) REFERENCES export_layout_profile(tenant_id,project_id,profile_id),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 FOREIGN KEY(tenant_id,evidence_artifact_id) REFERENCES artifact(tenant_id,artifact_id),
 FOREIGN KEY(tenant_id,evidence_artifact_id,evidence_sha256) REFERENCES version_registry(tenant_id,version_id,content_sha256),
 FOREIGN KEY(tenant_id,output_artifact_id) REFERENCES artifact(tenant_id,artifact_id),
 FOREIGN KEY(tenant_id,output_artifact_id,output_sha256) REFERENCES version_registry(tenant_id,version_id,content_sha256),
 CHECK((evidence_artifact_id IS NULL)=(evidence_sha256 IS NULL)),
 CHECK(schema_version='1.0.0' AND JSON_TYPE(request_json)='OBJECT'),
 CHECK(content_sha256 REGEXP '^[0-9a-f]{64}$'),
 CHECK(classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH')),
 CHECK(verification_no > 0),
 CHECK(channel_check IN ('APPROVED','REJECTED','UNKNOWN')), CHECK(visual_check IN ('PASSED','FAILED','NOT_RUN')),
 CHECK((channel_check <> 'APPROVED' AND visual_check <> 'PASSED') OR (evidence_artifact_id IS NOT NULL AND evidence_sha256 IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:157-0-update dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER b29_0_update BEFORE UPDATE ON export_layout_profile FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable export layout evidence';

--changeset medical-agent:157-0-delete dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER b29_0_delete BEFORE DELETE ON export_layout_profile FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable export layout evidence';

--changeset medical-agent:157-1-update dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER b29_1_update BEFORE UPDATE ON export_layout_verification FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable export layout evidence';

--changeset medical-agent:157-1-delete dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER b29_1_delete BEFORE DELETE ON export_layout_verification FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable export layout evidence';
