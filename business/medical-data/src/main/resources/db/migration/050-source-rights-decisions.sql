--liquibase formatted sql

--changeset medical-agent:1.0.0-291-source-rights-decision dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE source_rights_decision (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  source_version CHAR(36) CHARACTER SET ascii NOT NULL,
  decision_id CHAR(36) CHARACTER SET ascii NOT NULL,
  operation_id CHAR(36) CHARACTER SET ascii NOT NULL,
  from_rights_revision CHAR(36) CHARACTER SET ascii NOT NULL,
  to_rights_revision CHAR(36) CHARACTER SET ascii NOT NULL,
  decision VARCHAR(24) NOT NULL,
  rights_status VARCHAR(24) NOT NULL,
  expected_revision BIGINT UNSIGNED NOT NULL,
  resulting_revision BIGINT UNSIGNED NOT NULL,
  reason TEXT NOT NULL,
  request_manifest JSON NOT NULL,
  reviewed_manifest JSON NOT NULL,
  reviewed_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  evidence_proof JSON NOT NULL,
  receipt_ref JSON NOT NULL,
  schema_version VARCHAR(32) NOT NULL,
  schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL,
  created_at DATETIME(6) NOT NULL,
  classification VARCHAR(24) NOT NULL,
  PRIMARY KEY (tenant_id,decision_id),
  UNIQUE KEY uq_rights_decision_source (tenant_id,source_version,decision_id),
  UNIQUE KEY uq_rights_decision_operation (tenant_id,operation_id),
  UNIQUE KEY uq_rights_decision_revision (tenant_id,source_version,resulting_revision),
  FOREIGN KEY (tenant_id,source_version) REFERENCES source_version (tenant_id,source_version),
  FOREIGN KEY (tenant_id,source_version,from_rights_revision) REFERENCES rights_revision (tenant_id,source_version,rights_revision),
  FOREIGN KEY (tenant_id,source_version,to_rights_revision) REFERENCES rights_revision (tenant_id,source_version,rights_revision),
  FOREIGN KEY (tenant_id,operation_id) REFERENCES operation_record (tenant_id,operation_id),
  FOREIGN KEY (tenant_id,created_by) REFERENCES tenant_member (tenant_id,user_id),
  CHECK (decision IN ('APPROVE','RETURN','REVOKE')),
  CHECK (rights_status IN ('PENDING_REVIEW','APPROVED','REVOKED')),
  CHECK (expected_revision>0 AND resulting_revision=expected_revision+1),
  CHECK (schema_version='1.0.0'),
  CHECK (JSON_TYPE(request_manifest)='OBJECT' AND JSON_TYPE(reviewed_manifest)='OBJECT'
      AND JSON_TYPE(evidence_proof)='ARRAY' AND JSON_TYPE(receipt_ref)='OBJECT'),
  CHECK ((decision='APPROVE' AND rights_status='APPROVED' AND from_rights_revision<>to_rights_revision
      AND JSON_LENGTH(evidence_proof)>0)
      OR (decision='RETURN' AND rights_status='PENDING_REVIEW' AND from_rights_revision=to_rights_revision)
      OR (decision='REVOKE' AND rights_status='REVOKED' AND from_rights_revision=to_rights_revision))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-292-source-rights-state dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE source_rights_state (
  tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
  source_version CHAR(36) CHARACTER SET ascii NOT NULL,
  rights_revision CHAR(36) CHARACTER SET ascii NOT NULL,
  last_decision_id CHAR(36) CHARACTER SET ascii NULL,
  revision BIGINT UNSIGNED NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  schema_version VARCHAR(32) NOT NULL,
  schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
  created_by CHAR(36) CHARACTER SET ascii NOT NULL,
  created_at DATETIME(6) NOT NULL,
  classification VARCHAR(24) NOT NULL,
  PRIMARY KEY (tenant_id,source_version),
  FOREIGN KEY (tenant_id,source_version,rights_revision) REFERENCES rights_revision (tenant_id,source_version,rights_revision),
  FOREIGN KEY (tenant_id,source_version,last_decision_id) REFERENCES source_rights_decision (tenant_id,source_version,decision_id),
  FOREIGN KEY (tenant_id,created_by) REFERENCES tenant_member (tenant_id,user_id),
  CHECK (revision>0 AND schema_version='1.0.0'),
  CHECK ((revision=1 AND last_decision_id IS NULL) OR (revision>1 AND last_decision_id IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:1.0.0-293-source-rights-state-copy dbms:mysql runInTransaction:false failOnError:true
--preconditions onFail:HALT onError:HALT
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM source_version v LEFT JOIN rights_revision r ON r.tenant_id=v.tenant_id AND r.source_version=v.source_version AND r.rights_revision=v.rights_revision WHERE r.rights_revision IS NULL
INSERT INTO source_rights_state(tenant_id,source_version,rights_revision,last_decision_id,revision,updated_at,
  schema_version,schema_manifest_sha256,created_by,created_at,classification)
SELECT v.tenant_id,v.source_version,v.rights_revision,NULL,1,v.created_at,
  r.schema_version,r.schema_manifest_sha256,v.created_by,v.created_at,s.classification
FROM source_version v
JOIN rights_revision r ON r.tenant_id=v.tenant_id AND r.source_version=v.source_version AND r.rights_revision=v.rights_revision
JOIN source s ON s.tenant_id=v.tenant_id AND s.source_id=v.source_id;
