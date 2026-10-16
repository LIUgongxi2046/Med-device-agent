--liquibase formatted sql

--changeset medical-agent:1.0.0-262-source-usage-indexes dbms:mysql runInTransaction:false failOnError:true
ALTER TABLE project_source_binding
  ADD KEY ix_project_source_usage (tenant_id,source_version,is_current,project_id,binding_revision);

ALTER TABLE collection_member
  ADD KEY ix_collection_member_source (tenant_id,source_version,collection_version,parse_version);

ALTER TABLE scope_member
  ADD KEY ix_scope_member_source (tenant_id,source_version,project_id,snapshot_id,parse_version);
