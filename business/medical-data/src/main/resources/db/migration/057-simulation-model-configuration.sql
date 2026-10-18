--liquibase formatted sql

--changeset medical-agent:1.0.0-311-simulation-model-head dbms:mysql runInTransaction:false failOnError:true
-- Legacy rows remain EXTERNAL. No historical hash, approval or credential is synthesized.
ALTER TABLE configuration_head
  ADD COLUMN execution_mode VARCHAR(16) NOT NULL DEFAULT 'EXTERNAL',
  ADD UNIQUE KEY uq_configuration_execution_mode (tenant_id,project_key,config_id,kind,execution_mode),
  ADD CONSTRAINT ck_configuration_execution_mode CHECK (
    execution_mode='EXTERNAL' OR (execution_mode='SIMULATION' AND kind='MODEL' AND status='DRAFT'));

--changeset medical-agent:1.0.0-312-simulation-model-revision dbms:mysql runInTransaction:false failOnError:true
-- One ALTER applies nullable conversion and compensating checks atomically. Existing EXTERNAL requirements remain enforced.
-- The mode-inclusive owner FK binds EVERY immutable revision, preventing head conversion while history exists.
ALTER TABLE model_config_revision
  ADD COLUMN execution_mode VARCHAR(16) NOT NULL DEFAULT 'EXTERNAL',
  ADD COLUMN simulation_definition JSON NULL,
  MODIFY COLUMN base_url VARCHAR(2048) NULL,
  MODIFY COLUMN auth_ref VARCHAR(512) NULL,
  MODIFY COLUMN credential_reference_id CHAR(36) CHARACTER SET ascii NULL,
  MODIFY COLUMN credential_revision VARCHAR(128) NULL,
  MODIFY COLUMN price_version CHAR(36) CHARACTER SET ascii NULL,
  MODIFY COLUMN currency CHAR(3) CHARACTER SET ascii COLLATE ascii_bin NULL,
  MODIFY COLUMN data_policy JSON NULL,
  ADD CONSTRAINT fk_model_execution_mode FOREIGN KEY (tenant_id,project_key,config_id,configuration_kind,execution_mode)
    REFERENCES configuration_head (tenant_id,project_key,config_id,kind,execution_mode),
  ADD CONSTRAINT ck_model_execution_mode CHECK (
    (execution_mode='EXTERNAL' AND simulation_definition IS NULL
      AND base_url IS NOT NULL AND auth_ref IS NOT NULL AND credential_reference_id IS NOT NULL
      AND credential_revision IS NOT NULL AND price_version IS NOT NULL AND currency IS NOT NULL AND data_policy IS NOT NULL)
    OR
    (execution_mode='SIMULATION' AND status='DRAFT' AND provider='SIMULATION' AND region='LOCAL'
      AND base_url IS NULL AND auth_ref IS NULL AND credential_reference_id IS NULL AND credential_revision IS NULL
      AND price_version IS NULL AND currency IS NULL AND data_policy IS NULL
      AND simulation_definition IS NOT NULL AND JSON_TYPE(simulation_definition)='OBJECT'
      AND JSON_CONTAINS_PATH(simulation_definition,'all','$.execution_mode','$.production_eligible','$.case_binding.validation',
        '$.provenance_schema_sha256','$.provider_charge_amount','$.charge_basis')=1
      AND JSON_UNQUOTE(JSON_EXTRACT(simulation_definition,'$.execution_mode'))='SIMULATION'
      AND JSON_TYPE(JSON_EXTRACT(simulation_definition,'$.production_eligible'))='BOOLEAN'
      AND JSON_UNQUOTE(JSON_EXTRACT(simulation_definition,'$.production_eligible'))='false'
      AND JSON_UNQUOTE(JSON_EXTRACT(simulation_definition,'$.case_binding.validation'))='DECLARED_ONLY'
      AND JSON_UNQUOTE(JSON_EXTRACT(simulation_definition,'$.provenance_schema_sha256'))='ba6ee6a53f72ed4f62bec2243e35853706add8bf4e24e272b441fa50ef6dbc4c'
      AND JSON_UNQUOTE(JSON_EXTRACT(simulation_definition,'$.provider_charge_amount'))='0.00000000'
      AND JSON_UNQUOTE(JSON_EXTRACT(simulation_definition,'$.charge_basis'))='LOCAL_SIMULATION_NO_PROVIDER_REQUEST'));
