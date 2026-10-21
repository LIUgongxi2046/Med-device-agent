--liquibase formatted sql

-- Adds author-only 1.1.0 without modifying historical schema bytes or signed rows.
-- The existing generated name is used ONLY after exact normalized-clause verification.
-- Other version/hash/classification checks and all tenant/project FKs remain unchanged.
--changeset medical-agent:166-01-author-contract dbms:mysql runInTransaction:false failOnError:true
--preconditions onFail:HALT onError:HALT
--precondition-sql-check expectedResult:1 SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS tc JOIN information_schema.CHECK_CONSTRAINTS cc ON cc.CONSTRAINT_SCHEMA=tc.CONSTRAINT_SCHEMA AND cc.CONSTRAINT_NAME=tc.CONSTRAINT_NAME WHERE tc.TABLE_SCHEMA=DATABASE() AND tc.TABLE_NAME='scenario_record' AND tc.CONSTRAINT_TYPE='CHECK' AND tc.CONSTRAINT_NAME='scenario_record_chk_3' AND REGEXP_REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(LOWER(cc.CHECK_CLAUSE),CONCAT(CHAR(92),CHAR(39)),CHAR(39)),'`',''),'_utf8mb4',''),'(',''),')',''),'[[:space:]]','') IN ('schema_version=''1.0.0''andschema_manifest_sha256regexp''^[a-f0-9]{64}$''','schema_version=''1.0.0''andregexp_likeschema_manifest_sha256,''^[a-f0-9]{64}$''')
ALTER TABLE scenario_record DROP CHECK scenario_record_chk_3,
 ADD CONSTRAINT scenario_record_contract_version_166 CHECK (
  (schema_version='1.0.0' OR (kind='Author' AND schema_version='1.1.0'))
  AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'
 );
