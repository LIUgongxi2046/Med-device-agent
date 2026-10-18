--liquibase formatted sql

--changeset medical-agent:1.0.0-315-annotation-anchor dbms:mysql runInTransaction:false failOnError:true
-- Immutable locations. The original annotation and 058 decision document identities never change.
CREATE TABLE annotation_anchor (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  project_id CHAR(36) CHARACTER SET ascii NOT NULL,
  project_key VARCHAR(36) CHARACTER SET ascii GENERATED ALWAYS AS (project_id) STORED,
  anchor_id CHAR(36) CHARACTER SET ascii NOT NULL,
  annotation_id CHAR(36) CHARACTER SET ascii NOT NULL,
  document_revision CHAR(36) CHARACTER SET ascii NOT NULL,
  node_id CHAR(36) CHARACTER SET ascii NOT NULL,
  selection JSON NOT NULL,
  schema_version VARCHAR(32) NOT NULL,
  schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  selection_artifact_id CHAR(36) CHARACTER SET ascii NOT NULL,
  selection_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  selection_object_version VARCHAR(256) NOT NULL,
  previous_anchor_id CHAR(36) CHARACTER SET ascii NULL,
  annotation_revision BIGINT UNSIGNED NOT NULL,
  reason TEXT NULL,
  actor_id CHAR(36) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL,
  created_at DATETIME(6) NOT NULL,
  classification VARCHAR(24) NOT NULL,
  PRIMARY KEY (tenant_id,project_id,anchor_id),
  UNIQUE KEY uq_annotation_anchor_identity (tenant_id,project_id,annotation_id,anchor_id),
  UNIQUE KEY uq_annotation_anchor_revision (tenant_id,project_id,annotation_id,annotation_revision),
  KEY ix_annotation_anchor_document (tenant_id,project_id,document_revision,annotation_id),
  FOREIGN KEY (tenant_id,project_id,annotation_id)
    REFERENCES annotation (tenant_id,project_id,annotation_id),
  FOREIGN KEY (tenant_id,project_id,document_revision,node_id)
    REFERENCES document_node (tenant_id,project_id,document_revision,node_id),
  FOREIGN KEY (tenant_id,project_key,selection_artifact_id,selection_sha256,selection_object_version)
    REFERENCES artifact (tenant_id,project_key,artifact_id,sha256,object_version),
  FOREIGN KEY (tenant_id,project_id,annotation_id,previous_anchor_id)
    REFERENCES annotation_anchor (tenant_id,project_id,annotation_id,anchor_id),
  FOREIGN KEY (tenant_id,actor_id) REFERENCES tenant_member (tenant_id,user_id),
  FOREIGN KEY (tenant_id,created_by) REFERENCES tenant_member (tenant_id,user_id),
  CHECK (JSON_TYPE(selection)='OBJECT'),
  CHECK ((previous_anchor_id IS NULL AND anchor_id=annotation_id AND annotation_revision=1 AND reason IS NULL)
    OR (previous_anchor_id IS NOT NULL AND previous_anchor_id<>anchor_id AND annotation_revision>1
      AND reason IS NOT NULL AND CHAR_LENGTH(reason) BETWEEN 1 AND 16000 AND CHAR_LENGTH(TRIM(reason))>0)),
  CHECK (actor_id=created_by)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-316-annotation-effective-anchor dbms:mysql runInTransaction:false failOnError:true
-- Only this pointer is mutable; revision is its independent compare-and-swap counter.
CREATE TABLE annotation_effective_anchor (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  project_id CHAR(36) CHARACTER SET ascii NOT NULL,
  annotation_id CHAR(36) CHARACTER SET ascii NOT NULL,
  anchor_id CHAR(36) CHARACTER SET ascii NOT NULL,
  revision BIGINT UNSIGNED NOT NULL DEFAULT 1,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL,
  created_at DATETIME(6) NOT NULL,
  classification VARCHAR(24) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (tenant_id,project_id,annotation_id),
  FOREIGN KEY (tenant_id,project_id,annotation_id,anchor_id)
    REFERENCES annotation_anchor (tenant_id,project_id,annotation_id,anchor_id),
  FOREIGN KEY (tenant_id,created_by) REFERENCES tenant_member (tenant_id,user_id),
  CHECK (revision>0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-317-annotation-transition dbms:mysql runInTransaction:false failOnError:true
-- Append-only lifecycle history, interleaved with replies and 058 decisions by annotation revision.
CREATE TABLE annotation_transition (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  project_id CHAR(36) CHARACTER SET ascii NOT NULL,
  transition_id CHAR(36) CHARACTER SET ascii NOT NULL,
  annotation_id CHAR(36) CHARACTER SET ascii NOT NULL,
  kind VARCHAR(24) NOT NULL,
  previous_revision BIGINT UNSIGNED NOT NULL,
  revision BIGINT UNSIGNED NOT NULL,
  previous_status VARCHAR(24) NOT NULL,
  status VARCHAR(24) NOT NULL,
  previous_anchor_id CHAR(36) CHARACTER SET ascii NOT NULL,
  anchor_id CHAR(36) CHARACTER SET ascii NOT NULL,
  document_revision CHAR(36) CHARACTER SET ascii NOT NULL,
  actor_id CHAR(36) CHARACTER SET ascii NOT NULL,
  reason TEXT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL,
  created_at DATETIME(6) NOT NULL,
  classification VARCHAR(24) NOT NULL,
  PRIMARY KEY (tenant_id,project_id,transition_id),
  UNIQUE KEY uq_annotation_transition_revision (tenant_id,project_id,annotation_id,revision),
  KEY ix_annotation_transition_history (tenant_id,project_id,annotation_id,created_at,transition_id),
  FOREIGN KEY (tenant_id,project_id,annotation_id,previous_anchor_id)
    REFERENCES annotation_anchor (tenant_id,project_id,annotation_id,anchor_id),
  FOREIGN KEY (tenant_id,project_id,annotation_id,anchor_id)
    REFERENCES annotation_anchor (tenant_id,project_id,annotation_id,anchor_id),
  FOREIGN KEY (tenant_id,project_id,document_revision)
    REFERENCES document_revision_meta (tenant_id,project_id,document_revision),
  FOREIGN KEY (tenant_id,actor_id) REFERENCES tenant_member (tenant_id,user_id),
  FOREIGN KEY (tenant_id,created_by) REFERENCES tenant_member (tenant_id,user_id),
  CHECK (previous_revision>0 AND revision=previous_revision+1),
  CHECK ((kind='INVALIDATE' AND previous_status IN ('OPEN','RESOLVED','RETURNED','REANCHOR_REQUIRED')
      AND status='REANCHOR_REQUIRED' AND previous_anchor_id=anchor_id)
    OR (kind='REANCHOR' AND previous_status='REANCHOR_REQUIRED' AND status='OPEN'
      AND previous_anchor_id<>anchor_id AND reason IS NOT NULL
      AND CHAR_LENGTH(reason) BETWEEN 1 AND 16000 AND CHAR_LENGTH(TRIM(reason))>0)),
  CHECK (actor_id=created_by)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-318-annotation-origin-anchor-backfill dbms:mysql runInTransaction:false failOnError:true
-- quoted_sha256 is the existing canonical Selection digest, not a replacement text-only hash.
-- LEFT JOIN keeps missing artifacts visible as a NOT NULL failure instead of silently dropping annotations.
INSERT INTO annotation_anchor
  (tenant_id,project_id,anchor_id,annotation_id,document_revision,node_id,selection,schema_version,schema_manifest_sha256,
   selection_artifact_id,selection_sha256,selection_object_version,previous_anchor_id,
   annotation_revision,reason,actor_id,created_by,created_at,classification)
SELECT a.tenant_id,a.project_id,a.annotation_id,a.annotation_id,a.document_revision,a.node_id,a.selection,a.schema_version,a.schema_manifest_sha256,
  a.selection_artifact_id,a.quoted_sha256,r.object_version,NULL,1,NULL,a.actor_id,a.created_by,a.created_at,a.classification
FROM annotation a LEFT JOIN artifact r
  ON r.tenant_id=a.tenant_id AND r.project_key=a.project_id AND r.artifact_id=a.selection_artifact_id;

--changeset medical-agent:1.0.0-319-annotation-effective-anchor-backfill dbms:mysql runInTransaction:false failOnError:true
INSERT INTO annotation_effective_anchor
  (tenant_id,project_id,annotation_id,anchor_id,revision,created_by,created_at,classification,updated_at)
SELECT tenant_id,project_id,annotation_id,annotation_id,1,created_by,created_at,classification,updated_at
FROM annotation;
