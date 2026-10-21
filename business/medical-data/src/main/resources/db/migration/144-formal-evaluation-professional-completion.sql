--liquibase formatted sql
-- Original completion receipts only. No fake evaluation, qualification, score or PASS history.
--changeset medical-agent:144-01-completion-pins dbms:mysql runInTransaction:false failOnError:true
ALTER TABLE formal_evaluation_node_result ADD UNIQUE KEY uq_eval_result_completion_pin (tenant_id,run_id,step_id,record_sha256);

--changeset medical-agent:144-02-professional-completion dbms:mysql runInTransaction:false failOnError:true
CREATE TABLE formal_evaluation_professional_completion (
 tenant_id CHAR(36) CHARACTER SET ascii NOT NULL,
 project_id CHAR(36) CHARACTER SET ascii NOT NULL,
 completion_id CHAR(36) CHARACTER SET ascii NOT NULL,
 run_id CHAR(36) CHARACTER SET ascii NOT NULL,
 step_id CHAR(36) CHARACTER SET ascii NOT NULL,
 lease_generation BIGINT UNSIGNED NOT NULL,
 record_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 session_id CHAR(36) CHARACTER SET ascii NOT NULL,
 closed_receipt_id CHAR(36) CHARACTER SET ascii NOT NULL,
 closed_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 output_version CHAR(36) CHARACTER SET ascii NULL,
 output_sha256 CHAR(64) CHARACTER SET ascii NULL,
 canonical_receipt LONGTEXT NOT NULL,
 receipt_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 audit_id CHAR(36) CHARACTER SET ascii NOT NULL,
 event_id CHAR(36) CHARACTER SET ascii NOT NULL,
 schema_version VARCHAR(32) NOT NULL,
 schema_manifest_sha256 CHAR(64) CHARACTER SET ascii NOT NULL,
 created_by CHAR(36) CHARACTER SET ascii NOT NULL,
 created_at DATETIME(6) NOT NULL,
 classification VARCHAR(24) NOT NULL,
 PRIMARY KEY (tenant_id,completion_id),
 UNIQUE KEY uq_eval_completion_node (tenant_id,run_id,step_id),
 UNIQUE KEY uq_eval_completion_receipt (tenant_id,completion_id,receipt_sha256),
 FOREIGN KEY (tenant_id,project_id,run_id,step_id) REFERENCES run_step(tenant_id,project_id,run_id,step_id),
 FOREIGN KEY (tenant_id,run_id,step_id,record_sha256) REFERENCES formal_evaluation_node_result(tenant_id,run_id,step_id,record_sha256),
 FOREIGN KEY (tenant_id,session_id) REFERENCES professional_runtime_session(tenant_id,session_id),
 FOREIGN KEY (tenant_id,closed_receipt_id,closed_sha256) REFERENCES professional_runtime_supervision(tenant_id,receipt_id,evidence_sha256),
 FOREIGN KEY (tenant_id,output_version,output_sha256) REFERENCES version_registry(tenant_id,version_id,content_sha256),
 FOREIGN KEY (tenant_id,created_by) REFERENCES tenant_member(tenant_id,user_id),
 FOREIGN KEY (tenant_id,audit_id) REFERENCES audit_event(tenant_id,audit_id),
 FOREIGN KEY (tenant_id,event_id) REFERENCES outbox_event(tenant_id,event_id),
 CHECK (lease_generation>0 AND JSON_VALID(canonical_receipt)),
 CHECK ((output_version IS NULL AND output_sha256 IS NULL) OR (output_version IS NOT NULL AND output_sha256 IS NOT NULL AND output_sha256 REGEXP '^[a-f0-9]{64}$')),
 CHECK (schema_version='1.0.0' AND schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK (record_sha256 REGEXP '^[a-f0-9]{64}$' AND closed_sha256 REGEXP '^[a-f0-9]{64}$' AND receipt_sha256 REGEXP '^[a-f0-9]{64}$'),
 CHECK (classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

--changeset medical-agent:144-03-completion-no-update dbms:mysql runInTransaction:false failOnError:true splitStatements:false
CREATE TRIGGER trg_eval_completion_no_update BEFORE UPDATE ON formal_evaluation_professional_completion FOR EACH ROW
SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='EVALUATION_COMPLETION_IMMUTABLE';

--changeset medical-agent:144-04-completion-no-delete dbms:mysql runInTransaction:false failOnError:true splitStatements:false
CREATE TRIGGER trg_eval_completion_no_delete BEFORE DELETE ON formal_evaluation_professional_completion FOR EACH ROW
SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='EVALUATION_COMPLETION_IMMUTABLE';

--changeset medical-agent:144-05-update-original dbms:mysql runInTransaction:false failOnError:true splitStatements:false
CREATE TRIGGER tr_144_5_update BEFORE UPDATE ON formal_evaluation_node_result FOR EACH ROW
SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='FORMAL_EVALUATION_ORIGINAL_IMMUTABLE';

--changeset medical-agent:144-05-delete-original dbms:mysql runInTransaction:false failOnError:true splitStatements:false
CREATE TRIGGER tr_144_5_delete BEFORE DELETE ON formal_evaluation_node_result FOR EACH ROW
SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='FORMAL_EVALUATION_ORIGINAL_IMMUTABLE';

--changeset medical-agent:144-06-update-original dbms:mysql runInTransaction:false failOnError:true splitStatements:false
CREATE TRIGGER tr_144_6_update BEFORE UPDATE ON evaluation_score FOR EACH ROW
SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='FORMAL_EVALUATION_ORIGINAL_IMMUTABLE';

--changeset medical-agent:144-06-delete-original dbms:mysql runInTransaction:false failOnError:true splitStatements:false
CREATE TRIGGER tr_144_6_delete BEFORE DELETE ON evaluation_score FOR EACH ROW
SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='FORMAL_EVALUATION_ORIGINAL_IMMUTABLE';

--changeset medical-agent:144-07-update-original dbms:mysql runInTransaction:false failOnError:true splitStatements:false
CREATE TRIGGER tr_144_7_update BEFORE UPDATE ON evaluation_score_observation FOR EACH ROW
SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='FORMAL_EVALUATION_ORIGINAL_IMMUTABLE';

--changeset medical-agent:144-07-delete-original dbms:mysql runInTransaction:false failOnError:true splitStatements:false
CREATE TRIGGER tr_144_7_delete BEFORE DELETE ON evaluation_score_observation FOR EACH ROW
SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='FORMAL_EVALUATION_ORIGINAL_IMMUTABLE';

--changeset medical-agent:144-08-update-original dbms:mysql runInTransaction:false failOnError:true splitStatements:false
CREATE TRIGGER tr_144_8_update BEFORE UPDATE ON evaluation_result FOR EACH ROW
SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='FORMAL_EVALUATION_ORIGINAL_IMMUTABLE';

--changeset medical-agent:144-08-delete-original dbms:mysql runInTransaction:false failOnError:true splitStatements:false
CREATE TRIGGER tr_144_8_delete BEFORE DELETE ON evaluation_result FOR EACH ROW
SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='FORMAL_EVALUATION_ORIGINAL_IMMUTABLE';

--changeset medical-agent:144-09-update-original dbms:mysql runInTransaction:false failOnError:true splitStatements:false
CREATE TRIGGER tr_144_9_update BEFORE UPDATE ON expert_score_binding FOR EACH ROW
SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='FORMAL_EVALUATION_ORIGINAL_IMMUTABLE';

--changeset medical-agent:144-09-delete-original dbms:mysql runInTransaction:false failOnError:true splitStatements:false
CREATE TRIGGER tr_144_9_delete BEFORE DELETE ON expert_score_binding FOR EACH ROW
SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='FORMAL_EVALUATION_ORIGINAL_IMMUTABLE';

--changeset medical-agent:144-10-update-original dbms:mysql runInTransaction:false failOnError:true splitStatements:false
CREATE TRIGGER tr_144_10_update BEFORE UPDATE ON expert_score_authority_witness FOR EACH ROW
SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='FORMAL_EVALUATION_ORIGINAL_IMMUTABLE';

--changeset medical-agent:144-10-delete-original dbms:mysql runInTransaction:false failOnError:true splitStatements:false
CREATE TRIGGER tr_144_10_delete BEFORE DELETE ON expert_score_authority_witness FOR EACH ROW
SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='FORMAL_EVALUATION_ORIGINAL_IMMUTABLE';
