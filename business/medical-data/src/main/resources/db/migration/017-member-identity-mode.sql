--liquibase formatted sql

--changeset medical-agent:1.0.0-166-member-identity-mode dbms:mysql runInTransaction:false failOnError:true
ALTER TABLE identity_binding ADD COLUMN mode VARCHAR(16) COLLATE utf8mb4_0900_bin NOT NULL DEFAULT 'oidc';

--changeset medical-agent:1.0.0-167-member-identity-explicit-mode dbms:mysql runInTransaction:false failOnError:true
ALTER TABLE identity_binding ALTER COLUMN mode DROP DEFAULT;

--changeset medical-agent:1.0.0-168-member-identity-nullable-issuer dbms:mysql runInTransaction:false failOnError:true
ALTER TABLE identity_binding MODIFY COLUMN issuer VARCHAR(512) CHARACTER SET ascii COLLATE ascii_bin NULL;

--changeset medical-agent:1.0.0-169-member-identity-exact-subject dbms:mysql runInTransaction:false failOnError:true
ALTER TABLE identity_binding MODIFY COLUMN subject VARCHAR(255) COLLATE utf8mb4_0900_bin NULL;

--changeset medical-agent:1.0.0-170-member-identity-local-account dbms:mysql runInTransaction:false failOnError:true
ALTER TABLE identity_binding ADD COLUMN local_user_id CHAR(36) CHARACTER SET ascii NULL;

--changeset medical-agent:1.0.0-171-member-identity-local-reference dbms:mysql runInTransaction:false failOnError:true
ALTER TABLE identity_binding ADD CONSTRAINT fk_identity_local_account
  FOREIGN KEY (local_user_id) REFERENCES platform_local_account (user_id);

--changeset medical-agent:1.0.0-172-member-identity-local-unique dbms:mysql runInTransaction:false failOnError:true
ALTER TABLE identity_binding ADD CONSTRAINT uq_identity_local_account UNIQUE (tenant_id, local_user_id);

--changeset medical-agent:1.0.0-173-member-identity-mode-check dbms:mysql runInTransaction:false failOnError:true
ALTER TABLE identity_binding ADD CONSTRAINT ck_identity_binding_mode CHECK (
  (mode = 'local' AND local_user_id IS NOT NULL AND user_id = local_user_id AND issuer IS NULL AND subject IS NULL)
  OR
  (mode = 'oidc' AND local_user_id IS NULL AND issuer IS NOT NULL AND subject IS NOT NULL
    AND CHAR_LENGTH(issuer) > 0 AND CHAR_LENGTH(subject) > 0)
);
