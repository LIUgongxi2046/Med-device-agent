--liquibase formatted sql

--changeset medical-agent:140-01-table dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE document_signing_policy (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
 policy_revision CHAR(36) CHARACTER SET ascii NOT NULL, purpose VARCHAR(64) NOT NULL,
 policy_json JSON NOT NULL, policy_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 provider_json JSON NOT NULL, provider_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 PRIMARY KEY(tenant_id,project_id,policy_revision),
 UNIQUE KEY uq_sign_policy_purpose(tenant_id,project_id,policy_revision,purpose),
 FOREIGN KEY(tenant_id,policy_revision) REFERENCES version_registry(tenant_id,version_id),
 FOREIGN KEY(tenant_id,project_id) REFERENCES project(tenant_id,project_id),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id), CHECK(schema_version='1.0.0')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:140-02-table dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE document_signing_policy_approval (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
 approval_id CHAR(36) CHARACTER SET ascii NOT NULL, policy_revision CHAR(36) CHARACTER SET ascii NOT NULL,
 reason TEXT NOT NULL, authority_json JSON NOT NULL,
 PRIMARY KEY(tenant_id,project_id,approval_id), UNIQUE KEY uq_sign_approval_policy(tenant_id,project_id,approval_id,policy_revision),
 FOREIGN KEY(tenant_id,project_id,policy_revision) REFERENCES document_signing_policy(tenant_id,project_id,policy_revision), CHECK(CHAR_LENGTH(reason)>0),
 FOREIGN KEY(tenant_id,project_id) REFERENCES project(tenant_id,project_id),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id), CHECK(schema_version='1.0.0')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:140-03-table dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE document_signing_policy_head (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
 purpose VARCHAR(64) NOT NULL, policy_revision CHAR(36) CHARACTER SET ascii NOT NULL, approval_id CHAR(36) CHARACTER SET ascii NOT NULL,
 revision BIGINT UNSIGNED NOT NULL, updated_at DATETIME(6) NOT NULL,
 PRIMARY KEY(tenant_id,project_id,purpose),
 FOREIGN KEY(tenant_id,project_id,policy_revision,purpose) REFERENCES document_signing_policy(tenant_id,project_id,policy_revision,purpose),
 FOREIGN KEY(tenant_id,project_id,approval_id,policy_revision) REFERENCES document_signing_policy_approval(tenant_id,project_id,approval_id,policy_revision),
 FOREIGN KEY(tenant_id,project_id) REFERENCES project(tenant_id,project_id),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id), CHECK(schema_version='1.0.0')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:140-04-table dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE document_signing_challenge_binding (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
 challenge_id CHAR(36) CHARACTER SET ascii NOT NULL, secret_id CHAR(36) CHARACTER SET ascii NOT NULL,
 encrypted_verifier TEXT NOT NULL, session_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 context_revision BIGINT UNSIGNED NOT NULL, baseline_sha256 CHAR(64) CHARACTER SET ascii NOT NULL, baseline_json JSON NOT NULL,
 body_sha256 CHAR(64) CHARACTER SET ascii NOT NULL, provider_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 PRIMARY KEY(tenant_id,project_id,challenge_id), UNIQUE KEY uq_sign_pkce_secret(tenant_id,secret_id),
 FOREIGN KEY(tenant_id,project_id,challenge_id) REFERENCES reauthentication_challenge(tenant_id,project_id,challenge_id),
 FOREIGN KEY(tenant_id,project_id) REFERENCES project(tenant_id,project_id),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id), CHECK(schema_version='1.0.0')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:140-05-table dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE document_signing_callback (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
 callback_receipt_id CHAR(36) CHARACTER SET ascii NOT NULL, challenge_id CHAR(36) CHARACTER SET ascii NOT NULL,
 subject_id CHAR(36) CHARACTER SET ascii NOT NULL, provider_evidence JSON NOT NULL, evidence_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 PRIMARY KEY(tenant_id,project_id,callback_receipt_id), UNIQUE KEY uq_sign_callback_challenge(tenant_id,project_id,challenge_id),
 UNIQUE KEY uq_sign_callback_binding(tenant_id,project_id,challenge_id,callback_receipt_id),
 FOREIGN KEY(tenant_id,project_id,challenge_id) REFERENCES reauthentication_challenge(tenant_id,project_id,challenge_id),
 FOREIGN KEY(tenant_id,subject_id) REFERENCES tenant_member(tenant_id,user_id), CHECK(subject_id=created_by),
 FOREIGN KEY(tenant_id,project_id) REFERENCES project(tenant_id,project_id),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id), CHECK(schema_version='1.0.0')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:140-06-table dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE document_signed_content (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
 signature_id CHAR(36) CHARACTER SET ascii NOT NULL, document_revision CHAR(36) CHARACTER SET ascii NOT NULL,
 body_bytes LONGBLOB NOT NULL, body_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 manifest_json LONGTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL, manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 PRIMARY KEY(tenant_id,project_id,signature_id),
 FOREIGN KEY(tenant_id,project_id,signature_id,document_revision) REFERENCES signature_record(tenant_id,project_id,signature_id,document_revision), CHECK(OCTET_LENGTH(body_bytes)>0), CHECK(JSON_VALID(manifest_json)),
 FOREIGN KEY(tenant_id,project_id) REFERENCES project(tenant_id,project_id),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id), CHECK(schema_version='1.0.0')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:140-07-table dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE document_signing_operation (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL, project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(32) NOT NULL, schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL, created_at DATETIME(6) NOT NULL, classification VARCHAR(24) NOT NULL,
 operation_id CHAR(36) CHARACTER SET ascii NOT NULL, actor_id CHAR(36) CHARACTER SET ascii NOT NULL,
 signature_id CHAR(36) CHARACTER SET ascii NULL, operation_kind VARCHAR(32) NOT NULL, idempotency_key VARCHAR(128) CHARACTER SET ascii NOT NULL,
 request_sha256 CHAR(64) CHARACTER SET ascii NOT NULL, request_json JSON NOT NULL, target_id CHAR(36) CHARACTER SET ascii NOT NULL,
 response_json JSON NOT NULL, response_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 PRIMARY KEY(tenant_id,project_id,operation_id), UNIQUE KEY uq_sign_operation_key(tenant_id,actor_id,operation_kind,idempotency_key),
 FOREIGN KEY(tenant_id,operation_id) REFERENCES operation_record(tenant_id,operation_id),
 FOREIGN KEY(tenant_id,actor_id) REFERENCES tenant_member(tenant_id,user_id),
 FOREIGN KEY(tenant_id,project_id,signature_id) REFERENCES signature_record(tenant_id,project_id,signature_id),
 UNIQUE KEY uq_sign_operation_signature(tenant_id,project_id,signature_id),
 CHECK((operation_kind='SIGN')=(signature_id IS NOT NULL)), CHECK(actor_id=created_by), CHECK(operation_kind IN ('POLICY_DRAFT','POLICY_APPROVE','CHALLENGE','COMPLETE','SIGN')),
 FOREIGN KEY(tenant_id,project_id) REFERENCES project(tenant_id,project_id),
 FOREIGN KEY(tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id), CHECK(schema_version='1.0.0')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:140-20-update dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER b10_20_update BEFORE UPDATE ON document_signing_policy FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable signing evidence';

--changeset medical-agent:140-20-delete dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER b10_20_delete BEFORE DELETE ON document_signing_policy FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable signing evidence';

--changeset medical-agent:140-21-update dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER b10_21_update BEFORE UPDATE ON document_signing_policy_approval FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable signing evidence';

--changeset medical-agent:140-21-delete dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER b10_21_delete BEFORE DELETE ON document_signing_policy_approval FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable signing evidence';

--changeset medical-agent:140-22-update dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER b10_22_update BEFORE UPDATE ON document_signing_challenge_binding FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable signing evidence';

--changeset medical-agent:140-22-delete dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER b10_22_delete BEFORE DELETE ON document_signing_challenge_binding FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable signing evidence';

--changeset medical-agent:140-23-update dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER b10_23_update BEFORE UPDATE ON document_signing_callback FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable signing evidence';

--changeset medical-agent:140-23-delete dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER b10_23_delete BEFORE DELETE ON document_signing_callback FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable signing evidence';

--changeset medical-agent:140-24-update dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER b10_24_update BEFORE UPDATE ON document_signed_content FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable signing evidence';

--changeset medical-agent:140-24-delete dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER b10_24_delete BEFORE DELETE ON document_signed_content FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable signing evidence';

--changeset medical-agent:140-25-update dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER b10_25_update BEFORE UPDATE ON document_signing_operation FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable signing evidence';

--changeset medical-agent:140-25-delete dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER b10_25_delete BEFORE DELETE ON document_signing_operation FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable signing evidence';

--changeset medical-agent:140-26-update dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER b10_26_update BEFORE UPDATE ON signature_record FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable signing evidence';

--changeset medical-agent:140-26-delete dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER b10_26_delete BEFORE DELETE ON signature_record FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable signing evidence';

--changeset medical-agent:140-40-receipt-consume dbms:mysql runInTransaction:false failOnError:true splitStatements:false
CREATE TRIGGER b10_receipt_consume BEFORE UPDATE ON reauthentication_receipt FOR EACH ROW
BEGIN
 IF NOT(NEW.tenant_id <=> OLD.tenant_id) OR NOT(NEW.project_id <=> OLD.project_id) OR NOT(NEW.reauthentication_ref <=> OLD.reauthentication_ref) OR NOT(NEW.challenge_id <=> OLD.challenge_id) OR NOT(NEW.subject_id <=> OLD.subject_id) OR NOT(NEW.document_id <=> OLD.document_id) OR NOT(NEW.document_revision <=> OLD.document_revision) OR NOT(NEW.purpose <=> OLD.purpose) OR NOT(NEW.policy_revision <=> OLD.policy_revision) OR NOT(NEW.callback_receipt_id <=> OLD.callback_receipt_id) OR NOT(NEW.provider_receipt_ref <=> OLD.provider_receipt_ref) OR NOT(NEW.expires_at <=> OLD.expires_at) OR NOT(NEW.schema_version <=> OLD.schema_version) OR NOT(NEW.schema_manifest_sha256 <=> OLD.schema_manifest_sha256) OR NOT(NEW.created_by <=> OLD.created_by) OR NOT(NEW.created_at <=> OLD.created_at) OR NOT(NEW.classification <=> OLD.classification) OR OLD.status<>'VALID' OR OLD.consumed_at IS NOT NULL OR NEW.status<>'CONSUMED' OR NEW.consumed_at IS NULL OR NEW.consumed_at>=OLD.expires_at OR NEW.revision<>OLD.revision+1 OR NOT EXISTS(SELECT 1 FROM signature_record s WHERE s.tenant_id=OLD.tenant_id AND s.project_id=OLD.project_id AND s.reauthentication_ref=OLD.reauthentication_ref AND s.signer_id=OLD.subject_id AND s.signed_at=NEW.consumed_at) THEN
 SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Invalid one-use authentication consumption';
 END IF;
END;
--changeset medical-agent:140-41-receipt-delete dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER b10_receipt_delete BEFORE DELETE ON reauthentication_receipt FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable authentication evidence';

--changeset medical-agent:140-42-authentication-provenance dbms:mysql runInTransaction:false failOnError:true
ALTER TABLE reauthentication_challenge ADD CONSTRAINT fk_b10_challenge_policy FOREIGN KEY(tenant_id,project_id,policy_revision,purpose) REFERENCES document_signing_policy(tenant_id,project_id,policy_revision,purpose),
 ADD CONSTRAINT fk_b10_challenge_callback FOREIGN KEY(tenant_id,project_id,challenge_id,callback_receipt_id) REFERENCES document_signing_callback(tenant_id,project_id,challenge_id,callback_receipt_id);
--changeset medical-agent:140-43-receipt-provenance dbms:mysql runInTransaction:false failOnError:true
ALTER TABLE reauthentication_receipt ADD CONSTRAINT fk_b10_receipt_callback FOREIGN KEY(tenant_id,project_id,challenge_id,callback_receipt_id) REFERENCES document_signing_callback(tenant_id,project_id,challenge_id,callback_receipt_id);
--changeset medical-agent:140-44-challenge-transition dbms:mysql runInTransaction:false failOnError:true splitStatements:false
CREATE TRIGGER b10_challenge_transition BEFORE UPDATE ON reauthentication_challenge FOR EACH ROW
BEGIN
 IF NOT(NEW.tenant_id <=> OLD.tenant_id) OR NOT(NEW.project_id <=> OLD.project_id) OR NOT(NEW.challenge_id <=> OLD.challenge_id) OR NOT(NEW.subject_id <=> OLD.subject_id) OR NOT(NEW.document_id <=> OLD.document_id) OR NOT(NEW.document_revision <=> OLD.document_revision) OR NOT(NEW.purpose <=> OLD.purpose) OR NOT(NEW.policy_revision <=> OLD.policy_revision) OR NOT(NEW.state_hash <=> OLD.state_hash) OR NOT(NEW.nonce_hash <=> OLD.nonce_hash) OR NOT(NEW.pkce_encrypted_reference_id <=> OLD.pkce_encrypted_reference_id) OR NOT(NEW.expires_at <=> OLD.expires_at) OR NOT(NEW.created_by <=> OLD.created_by) OR NOT(NEW.created_at <=> OLD.created_at) OR NOT(NEW.classification <=> OLD.classification) OR NEW.revision<>OLD.revision+1 OR NOT (
 (OLD.status='PENDING' AND NEW.status='EXCHANGING' AND NEW.callback_receipt_id IS NULL AND NEW.consumed_at IS NULL)
 OR (OLD.status='EXCHANGING' AND NEW.status='VERIFIED' AND NEW.callback_receipt_id IS NOT NULL AND NEW.consumed_at IS NULL)
 OR (OLD.status='VERIFIED' AND NEW.status='COMPLETED' AND NEW.callback_receipt_id=OLD.callback_receipt_id AND NEW.consumed_at IS NOT NULL AND NEW.consumed_at<OLD.expires_at)) THEN
 SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Invalid authentication challenge transition';
 END IF;
END;
--changeset medical-agent:140-45-challenge-delete dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER b10_challenge_delete BEFORE DELETE ON reauthentication_challenge FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Immutable authentication challenge';
