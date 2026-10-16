--liquibase formatted sql

--changeset medical-agent:1.0.0-260-retrieval-query dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE retrieval_query (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  project_id CHAR(36) CHARACTER SET ascii NOT NULL,
  retrieval_id CHAR(36) CHARACTER SET ascii NOT NULL,
  subject_id CHAR(36) CHARACTER SET ascii NOT NULL,
  query_text VARCHAR(500) NOT NULL,
  query_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  purpose VARCHAR(24) NOT NULL,
  mode VARCHAR(24) NOT NULL,
  selection_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  auth_epoch BIGINT UNSIGNED NOT NULL,
  governance_epoch BIGINT UNSIGNED NOT NULL,
  result_count BIGINT UNSIGNED NOT NULL,
  status VARCHAR(24) NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL,
  created_at DATETIME(6) NOT NULL,
  classification VARCHAR(24) NOT NULL,
  PRIMARY KEY (tenant_id, retrieval_id),
  KEY ix_retrieval_project (tenant_id, project_id, created_at, retrieval_id),
  FOREIGN KEY (tenant_id, project_id) REFERENCES project (tenant_id, project_id),
  FOREIGN KEY (tenant_id, subject_id) REFERENCES tenant_member (tenant_id, user_id),
  FOREIGN KEY (tenant_id, created_by) REFERENCES tenant_member (tenant_id, user_id),
  CHECK (purpose IN ('VERIFICATION')),
  CHECK (mode IN ('FULLTEXT')),
  CHECK (status IN ('FOUND','EMPTY'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-261-retrieval-hit dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE retrieval_hit (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  project_id CHAR(36) CHARACTER SET ascii NOT NULL,
  retrieval_id CHAR(36) CHARACTER SET ascii NOT NULL,
  rank_no INT UNSIGNED NOT NULL,
  source_id CHAR(36) CHARACTER SET ascii NOT NULL,
  source_version CHAR(36) CHARACTER SET ascii NOT NULL,
  parse_version CHAR(36) CHARACTER SET ascii NOT NULL,
  index_version CHAR(36) CHARACTER SET ascii NOT NULL,
  chunk_id CHAR(36) CHARACTER SET ascii NOT NULL,
  approval_revision CHAR(36) CHARACTER SET ascii NOT NULL,
  rights_revision CHAR(36) CHARACTER SET ascii NOT NULL,
  title VARCHAR(512) NOT NULL,
  excerpt MEDIUMTEXT NOT NULL,
  excerpt_truncated BOOLEAN NOT NULL,
  locator JSON NOT NULL,
  locator_kind VARCHAR(24) NOT NULL,
  quote_hash CHAR(64) CHARACTER SET ascii NOT NULL,
  relevance DECIMAL(20,10) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (tenant_id, retrieval_id, rank_no),
  UNIQUE KEY uq_retrieval_chunk (tenant_id, retrieval_id, source_version, parse_version, chunk_id),
  FOREIGN KEY (tenant_id, retrieval_id) REFERENCES retrieval_query (tenant_id, retrieval_id),
  FOREIGN KEY (tenant_id, project_id) REFERENCES project (tenant_id, project_id),
  FOREIGN KEY (tenant_id, source_id, source_version) REFERENCES source_version (tenant_id, source_id, source_version),
  FOREIGN KEY (tenant_id, source_version, parse_version, chunk_id)
    REFERENCES source_chunk (tenant_id, source_version, parse_version, chunk_id),
  FOREIGN KEY (tenant_id, source_version, parse_version, index_version)
    REFERENCES index_manifest (tenant_id, source_version, parse_version, index_version),
  FOREIGN KEY (tenant_id, source_version, parse_version, approval_revision)
    REFERENCES approval_decision (tenant_id, source_version, parse_version, approval_revision),
  FOREIGN KEY (tenant_id, rights_revision) REFERENCES rights_revision (tenant_id, rights_revision),
  CHECK (rank_no > 0),
  CHECK (relevance >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
