--liquibase formatted sql
-- Existing DOCUMENT/CANDIDATE foreign keys retain their exact meaning. An N06
-- aggregate check is neither a saved document nor any individual candidate.
--changeset medical-agent:1.0.0-128-task-candidate-assembly-check dbms:mysql runInTransaction:false failOnError:true
ALTER TABLE check_run DROP CHECK check_run_chk_1,
 ADD CONSTRAINT ck_check_run_target_kind CHECK (target_kind IN ('DOCUMENT','CANDIDATE','CANDIDATE_SET'));
