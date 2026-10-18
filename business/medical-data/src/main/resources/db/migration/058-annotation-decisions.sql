--liquibase formatted sql

--changeset medical-agent:1.0.0-313-annotation-document-identity dbms:mysql runInTransaction:false failOnError:true
ALTER TABLE annotation
  ADD UNIQUE KEY uq_annotation_document_identity (tenant_id,project_id,annotation_id,document_revision);

--changeset medical-agent:1.0.0-314-annotation-decision dbms:mysql runInTransaction:false failOnError:true
-- Append-only history contract; writes are INSERT-only in the application mapper.
-- document_revision refers to the existing document snapshot, never a synthetic version.
CREATE TABLE annotation_decision (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  project_id CHAR(36) CHARACTER SET ascii NOT NULL,
  decision_id CHAR(36) CHARACTER SET ascii NOT NULL,
  annotation_id CHAR(36) CHARACTER SET ascii NOT NULL,
  document_revision CHAR(36) CHARACTER SET ascii NOT NULL,
  decision VARCHAR(24) NOT NULL,
  reason TEXT NOT NULL,
  previous_status VARCHAR(24) NOT NULL,
  status VARCHAR(24) NOT NULL,
  previous_revision BIGINT UNSIGNED NOT NULL,
  revision BIGINT UNSIGNED NOT NULL,
  actor_id CHAR(36) CHARACTER SET ascii NOT NULL,
  schema_version VARCHAR(32) NOT NULL,
  schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL,
  created_at DATETIME(6) NOT NULL,
  classification VARCHAR(24) NOT NULL,
  PRIMARY KEY (tenant_id,project_id,decision_id),
  UNIQUE KEY uq_annotation_decision_revision (tenant_id,project_id,annotation_id,revision),
  KEY ix_annotation_decision_history (tenant_id,project_id,annotation_id,created_at,decision_id),
  FOREIGN KEY (tenant_id,project_id,annotation_id,document_revision)
    REFERENCES annotation (tenant_id,project_id,annotation_id,document_revision),
  FOREIGN KEY (tenant_id,project_id,document_revision)
    REFERENCES document_revision_meta (tenant_id,project_id,document_revision),
  FOREIGN KEY (tenant_id,actor_id) REFERENCES tenant_member (tenant_id,user_id),
  FOREIGN KEY (tenant_id,created_by) REFERENCES tenant_member (tenant_id,user_id),
  CHECK ((decision='RESOLVE' AND previous_status='OPEN' AND status='RESOLVED')
    OR (decision='REOPEN' AND previous_status='RESOLVED' AND status='OPEN')),
  CHECK (previous_revision > 0 AND revision=previous_revision+1),
  CHECK (actor_id=created_by),
  CHECK (CHAR_LENGTH(reason) BETWEEN 1 AND 16000 AND CHAR_LENGTH(TRIM(reason)) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
