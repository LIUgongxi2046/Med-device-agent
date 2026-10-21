--liquibase formatted sql
-- N04 original runtime preparation must remain an immutable witness. Dispatch and
-- one-time worker launch use independently checked operation/audit/outbox journals.
--changeset medical-agent:1.0.0-113-1 dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER imm_writer_runtime_prepare_update BEFORE UPDATE ON task_section_writer_runtime_prepare FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IMMUTABLE_N04_RUNTIME_PREPARATION';
--changeset medical-agent:1.0.0-113-2 dbms:mysql runInTransaction:false failOnError:true
CREATE TRIGGER imm_writer_runtime_prepare_delete BEFORE DELETE ON task_section_writer_runtime_prepare FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='IMMUTABLE_N04_RUNTIME_PREPARATION';
