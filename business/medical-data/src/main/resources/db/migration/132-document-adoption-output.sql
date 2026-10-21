--liquibase formatted sql
-- DC03 immutable physical output intents. Unknown writes recover the same object;
-- they never mint a replacement write identity or fake a DC02 upload grant.
--changeset medical-agent:1.0.0-132-document-adoption-save dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE document_adoption_save (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
 operation_id CHAR(36) CHARACTER SET ascii NOT NULL,
 project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 document_id CHAR(36) CHARACTER SET ascii NOT NULL,
 document_revision CHAR(36) CHARACTER SET ascii NOT NULL,
 access_set_id CHAR(36) CHARACTER SET ascii NOT NULL,
 body_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(32) NOT NULL,
 schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 classification VARCHAR(24) NOT NULL,
 registration_json JSON NULL,
 registration_sha256 CHAR(64) CHARACTER SET ascii NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL,
 created_at DATETIME(6) NOT NULL,
 PRIMARY KEY (tenant_id,operation_id),
 UNIQUE KEY uq_adoption_save_project (tenant_id,project_id,operation_id),
 UNIQUE KEY uq_adoption_save_revision (tenant_id,document_revision),
 UNIQUE KEY uq_adoption_save_access (tenant_id,access_set_id),
 FOREIGN KEY (tenant_id,operation_id) REFERENCES operation_record(tenant_id,operation_id),
 FOREIGN KEY (tenant_id,project_id,document_id) REFERENCES document_head(tenant_id,project_id,document_id),
 FOREIGN KEY (tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK (body_sha256 REGEXP '^[0-9a-f]{64}$' AND schema_manifest_sha256 REGEXP '^[0-9a-f]{64}$'),
 CHECK ((registration_json IS NULL) = (registration_sha256 IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
--changeset medical-agent:1.0.0-132-document-adoption-output dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE document_adoption_output (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
 operation_id CHAR(36) CHARACTER SET ascii NOT NULL,
 project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(32) NOT NULL,
 schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL,
 created_at DATETIME(6) NOT NULL,
 classification VARCHAR(24) NOT NULL,
 artifact_kind VARCHAR(32) CHARACTER SET ascii NOT NULL,
 artifact_id CHAR(36) CHARACTER SET ascii NOT NULL,
 schema_ref VARCHAR(2048) NOT NULL,
 schema_document_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 intent_json JSON NOT NULL,
 intent_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 state VARCHAR(16) CHARACTER SET ascii NOT NULL,
 object_json JSON NULL,
 PRIMARY KEY (tenant_id,operation_id,artifact_kind),
 UNIQUE KEY uq_adoption_output_artifact (tenant_id,artifact_id),
 FOREIGN KEY (tenant_id,project_id,operation_id) REFERENCES document_adoption_save(tenant_id,project_id,operation_id),
 FOREIGN KEY (tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 CHECK (intent_sha256 REGEXP '^[0-9a-f]{64}$' AND schema_document_sha256 REGEXP '^[0-9a-f]{64}$' AND schema_manifest_sha256 REGEXP '^[0-9a-f]{64}$'),
 CHECK (artifact_kind IN ('DOCUMENT_BODY','DOCUMENT_FIELDS','DOCUMENT_EVIDENCE')),
 CHECK (state IN ('PREPARED','WRITING','STORED')),
 CHECK ((state='STORED') = (object_json IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
