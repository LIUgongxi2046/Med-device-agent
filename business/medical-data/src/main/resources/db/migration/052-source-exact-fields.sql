--liquibase formatted sql

--changeset medical-agent:1.0.0-295-source-exact-revision dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE source_exact_revision (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  source_version CHAR(36) CHARACTER SET ascii NOT NULL,
  parse_version CHAR(36) CHARACTER SET ascii NOT NULL,
  index_version CHAR(36) CHARACTER SET ascii NOT NULL,
  approval_revision CHAR(36) CHARACTER SET ascii NOT NULL,
  rights_revision CHAR(36) CHARACTER SET ascii NOT NULL,
  field_revision CHAR(36) CHARACTER SET ascii NOT NULL,
  manifest JSON NOT NULL,
  manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  field_count INT UNSIGNED NOT NULL,
  publication_revision BIGINT UNSIGNED NOT NULL,
  schema_version VARCHAR(32) NOT NULL,
  schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL,
  created_at DATETIME(6) NOT NULL,
  classification VARCHAR(24) NOT NULL,
  PRIMARY KEY (tenant_id,field_revision),
  UNIQUE KEY uq_exact_revision_identity (tenant_id,source_version,parse_version,approval_revision,field_revision),
  UNIQUE KEY uq_exact_publication_revision (tenant_id,source_version,parse_version,approval_revision,publication_revision),
  FOREIGN KEY (tenant_id,field_revision) REFERENCES version_registry (tenant_id,version_id),
  FOREIGN KEY (tenant_id,source_version,parse_version,index_version) REFERENCES index_manifest (tenant_id,source_version,parse_version,index_version),
  FOREIGN KEY (tenant_id,source_version,parse_version,approval_revision) REFERENCES approval_decision (tenant_id,source_version,parse_version,approval_revision),
  FOREIGN KEY (tenant_id,source_version,rights_revision) REFERENCES rights_revision (tenant_id,source_version,rights_revision),
  FOREIGN KEY (tenant_id,created_by) REFERENCES tenant_member (tenant_id,user_id),
  CHECK (field_count>0 AND publication_revision>0),
  CHECK (schema_version='1.0.0' AND JSON_TYPE(manifest)='OBJECT')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-296-source-exact-head dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE source_exact_head (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  source_version CHAR(36) CHARACTER SET ascii NOT NULL,
  parse_version CHAR(36) CHARACTER SET ascii NOT NULL,
  approval_revision CHAR(36) CHARACTER SET ascii NOT NULL,
  field_revision CHAR(36) CHARACTER SET ascii NOT NULL,
  revision BIGINT UNSIGNED NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  schema_version VARCHAR(32) NOT NULL,
  schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL,
  created_at DATETIME(6) NOT NULL,
  classification VARCHAR(24) NOT NULL,
  PRIMARY KEY (tenant_id,source_version,parse_version,approval_revision),
  FOREIGN KEY (tenant_id,source_version,parse_version,approval_revision,field_revision)
    REFERENCES source_exact_revision (tenant_id,source_version,parse_version,approval_revision,field_revision),
  FOREIGN KEY (tenant_id,created_by) REFERENCES tenant_member (tenant_id,user_id),
  CHECK (revision>0 AND schema_version='1.0.0' AND updated_at>=created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-297-source-exact-item dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE source_exact_item (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  source_version CHAR(36) CHARACTER SET ascii NOT NULL,
  parse_version CHAR(36) CHARACTER SET ascii NOT NULL,
  approval_revision CHAR(36) CHARACTER SET ascii NOT NULL,
  field_revision CHAR(36) CHARACTER SET ascii NOT NULL,
  ordinal INT UNSIGNED NOT NULL,
  field_kind VARCHAR(24) NOT NULL,
  raw_value VARCHAR(512) NOT NULL,
  raw_value_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  alias_value VARCHAR(512) NULL,
  alias_value_sha256 CHAR(64) CHARACTER SET ascii NULL,
  chunk_id CHAR(36) CHARACTER SET ascii NOT NULL,
  field_manifest JSON NOT NULL,
  schema_version VARCHAR(32) NOT NULL,
  schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL,
  created_at DATETIME(6) NOT NULL,
  classification VARCHAR(24) NOT NULL,
  PRIMARY KEY (tenant_id,field_revision,ordinal),
  KEY ix_exact_raw_value (tenant_id,field_kind,raw_value_sha256),
  KEY ix_exact_alias_value (tenant_id,field_kind,alias_value_sha256),
  FOREIGN KEY (tenant_id,source_version,parse_version,approval_revision,field_revision)
    REFERENCES source_exact_revision (tenant_id,source_version,parse_version,approval_revision,field_revision),
  FOREIGN KEY (tenant_id,source_version,parse_version,chunk_id) REFERENCES source_chunk (tenant_id,source_version,parse_version,chunk_id),
  FOREIGN KEY (tenant_id,created_by) REFERENCES tenant_member (tenant_id,user_id),
  CHECK (field_kind IN ('MODEL','PTR_NUMBER','DOCUMENT_NUMBER','STANDARD_NUMBER','STANDARD_YEAR')),
  CHECK (schema_version='1.0.0' AND JSON_TYPE(field_manifest)='OBJECT' AND CHAR_LENGTH(raw_value)>0),
  CHECK ((alias_value IS NULL AND alias_value_sha256 IS NULL) OR (alias_value IS NOT NULL AND alias_value_sha256 IS NOT NULL AND CHAR_LENGTH(alias_value)>0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-298-source-exact-query dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE source_exact_query (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  project_id CHAR(36) CHARACTER SET ascii NOT NULL,
  retrieval_id CHAR(36) CHARACTER SET ascii NOT NULL,
  subject_id CHAR(36) CHARACTER SET ascii NOT NULL,
  request_manifest JSON NOT NULL,
  query_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  selection_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  result_count INT UNSIGNED NOT NULL,
  match_count INT UNSIGNED NOT NULL,
  status VARCHAR(24) NOT NULL,
  auth_epoch BIGINT UNSIGNED NOT NULL,
  governance_epoch BIGINT UNSIGNED NOT NULL,
  schema_version VARCHAR(32) NOT NULL,
  schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL,
  created_at DATETIME(6) NOT NULL,
  classification VARCHAR(24) NOT NULL,
  PRIMARY KEY (tenant_id,retrieval_id),
  KEY ix_exact_query_subject (tenant_id,subject_id,created_at,retrieval_id),
  FOREIGN KEY (tenant_id,retrieval_id) REFERENCES version_registry (tenant_id,version_id),
  FOREIGN KEY (tenant_id,project_id) REFERENCES project (tenant_id,project_id),
  FOREIGN KEY (tenant_id,subject_id) REFERENCES tenant_member (tenant_id,user_id),
  FOREIGN KEY (tenant_id,created_by) REFERENCES tenant_member (tenant_id,user_id),
  CHECK (status IN ('FOUND','EMPTY') AND match_count>=result_count),
  CHECK ((status='FOUND' AND result_count>0) OR (status='EMPTY' AND result_count=0 AND match_count=0)),
  CHECK (schema_version='1.0.0' AND JSON_TYPE(request_manifest)='OBJECT' AND subject_id=created_by)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-299-source-exact-hit dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE source_exact_hit (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  project_id CHAR(36) CHARACTER SET ascii NOT NULL,
  retrieval_id CHAR(36) CHARACTER SET ascii NOT NULL,
  rank_no INT UNSIGNED NOT NULL,
  source_version CHAR(36) CHARACTER SET ascii NOT NULL,
  parse_version CHAR(36) CHARACTER SET ascii NOT NULL,
  index_version CHAR(36) CHARACTER SET ascii NOT NULL,
  approval_revision CHAR(36) CHARACTER SET ascii NOT NULL,
  rights_revision CHAR(36) CHARACTER SET ascii NOT NULL,
  binding_revision CHAR(36) CHARACTER SET ascii NOT NULL,
  chunk_id CHAR(36) CHARACTER SET ascii NOT NULL,
  field_revision CHAR(36) CHARACTER SET ascii NOT NULL,
  hit_manifest JSON NOT NULL,
  schema_version VARCHAR(32) NOT NULL,
  schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL,
  created_at DATETIME(6) NOT NULL,
  classification VARCHAR(24) NOT NULL,
  PRIMARY KEY (tenant_id,retrieval_id,rank_no),
  UNIQUE KEY uq_exact_hit_chunk (tenant_id,retrieval_id,source_version,parse_version,chunk_id),
  FOREIGN KEY (tenant_id,retrieval_id) REFERENCES source_exact_query (tenant_id,retrieval_id),
  FOREIGN KEY (tenant_id,project_id) REFERENCES project (tenant_id,project_id),
  FOREIGN KEY (tenant_id,source_version,parse_version,chunk_id) REFERENCES source_chunk (tenant_id,source_version,parse_version,chunk_id),
  FOREIGN KEY (tenant_id,source_version,parse_version,index_version) REFERENCES index_manifest (tenant_id,source_version,parse_version,index_version),
  FOREIGN KEY (tenant_id,source_version,parse_version,approval_revision,field_revision)
    REFERENCES source_exact_revision (tenant_id,source_version,parse_version,approval_revision,field_revision),
  FOREIGN KEY (tenant_id,source_version,rights_revision) REFERENCES rights_revision (tenant_id,source_version,rights_revision),
  FOREIGN KEY (tenant_id,project_id,source_version,binding_revision) REFERENCES project_source_binding (tenant_id,project_id,source_version,binding_revision),
  FOREIGN KEY (tenant_id,created_by) REFERENCES tenant_member (tenant_id,user_id),
  CHECK (rank_no>0 AND schema_version='1.0.0' AND JSON_TYPE(hit_manifest)='OBJECT')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
