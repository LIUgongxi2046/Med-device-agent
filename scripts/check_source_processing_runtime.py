import argparse
from datetime import datetime, timezone
import gzip
import hashlib
from io import BytesIO
import json
from pathlib import Path
import subprocess
from uuid import UUID, uuid4
import zipfile

import yaml

import local_object_storage as objects
import local_runtime as runtime


ROOT = objects.ROOT


def digest(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def main():
    parser = argparse.ArgumentParser(description="重新读取来源处理迁移、私有配置和当前制品")
    parser.add_argument("--scope", choices=("database", "application"), required=True)
    parser.add_argument("--backup-id", required=True)
    args = parser.parse_args()
    if str(UUID(args.backup_id)) != args.backup_id:
        raise ValueError("PROCESSING_BACKUP_IDENTITY_INVALID")
    directory = ROOT / ".implementation-work/source-processing-runtime-checks" / str(uuid4())
    directory.mkdir(mode=0o700, parents=True)
    statement = """
        SELECT JSON_OBJECT('database',DATABASE(),'server_uuid',@@server_uuid,'mysql_version',VERSION(),
          'migration_count',(SELECT COUNT(*) FROM DATABASECHANGELOG),
          'worker_migrations',(SELECT COUNT(*) FROM DATABASECHANGELOG WHERE ID IN
            ('1.0.0-272-source-processing-lease','1.0.0-273-parse-object-version','1.0.0-274-processing-failure-manifest')),
          'worker_columns',(SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
            AND TABLE_NAME='processing_attempt' AND COLUMN_NAME IN
            ('lease_owner','lease_generation','lease_expires_at','execution_deadline','command_ref','result_ref','write_intent')),
          'parse_object_columns',(SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
            AND TABLE_NAME='parse_version' AND COLUMN_NAME IN ('artifact_version','artifact_size_bytes')),
          'worker_constraints',(SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA=DATABASE()
            AND CONSTRAINT_NAME IN ('ck_processing_worker_lease','ck_processing_worker_result','ck_parse_object_version')),
          'invalid_leases',(SELECT COUNT(*) FROM processing_attempt WHERE
            (lease_owner IS NULL AND lease_expires_at IS NOT NULL) OR
            (lease_owner IS NOT NULL AND (lease_expires_at IS NULL OR lease_generation=0 OR status<>'RUNNING'))),
          'invalid_failure_manifests',(SELECT COUNT(*) FROM processing_attempt WHERE JSON_TYPE(failure_manifest)<>'OBJECT'
            OR JSON_EXTRACT(failure_manifest,'$.schema_version')<>'1.0.0' OR JSON_TYPE(JSON_EXTRACT(failure_manifest,'$.issues'))<>'ARRAY'),
          'source_count',(SELECT COUNT(*) FROM source_version),
          'completion_migrations',(SELECT COUNT(*) FROM DATABASECHANGELOG WHERE ID IN
            ('1.0.0-275-source-completion-checkpoint','1.0.0-276-source-upload-result','1.0.0-277-source-completion-deadline')),
          'completion_tables',(SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()
            AND TABLE_NAME IN ('source_completion_checkpoint','source_upload_result')),
          'completion_deadline_columns',(SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
            AND TABLE_NAME='outbox_delivery' AND COLUMN_NAME IN ('completion_started_at','completion_deadline','completion_manifest')),
          'completion_deadline_constraints',(SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA=DATABASE()
            AND TABLE_NAME='outbox_delivery' AND CONSTRAINT_NAME='ck_completion_deadline'),
          'completion_foreign_keys',(SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA=DATABASE()
            AND TABLE_NAME IN ('source_completion_checkpoint','source_upload_result') AND CONSTRAINT_TYPE='FOREIGN KEY'),
          'completion_table_columns',(SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
            AND TABLE_NAME IN ('source_completion_checkpoint','source_upload_result')),
          'completion_check_constraints',(SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA=DATABASE()
            AND TABLE_NAME IN ('source_completion_checkpoint','source_upload_result') AND CONSTRAINT_TYPE='CHECK'),
          'completion_checkpoint_count',(SELECT COUNT(*) FROM source_completion_checkpoint),
          'completion_result_count',(SELECT COUNT(*) FROM source_upload_result),
          'processing_attempt_count',(SELECT COUNT(*) FROM processing_attempt))
    """
    result = runtime.mysql("USE medical_local;" + statement, runtime.settings()["root_password"])
    database = json.loads(result.stdout)
    expected = json.loads((runtime.RUNTIME / "service-identity.json").read_bytes())
    if database["database"] != expected["database"] or database["server_uuid"] != expected["server_uuid"] \
            or database["worker_migrations"] != 3 or database["worker_columns"] != 7 \
            or database["parse_object_columns"] != 2 or database["worker_constraints"] != 3 \
            or database["invalid_leases"] != 0 or database["invalid_failure_manifests"] != 0 \
            or database["completion_migrations"] != 3 or database["completion_tables"] != 2 \
            or database["completion_deadline_columns"] != 3 or database["completion_deadline_constraints"] != 1 \
            or database["completion_foreign_keys"] != 9 or database["completion_table_columns"] != 38 \
            or database["completion_check_constraints"] != 9:
        raise ValueError("PROCESSING_DATABASE_READBACK_MISMATCH")
    backup = runtime.RUNTIME / "migration-backups" / (args.backup_id + ".sql.gz")
    backup_receipt = json.loads(objects.private_file(backup.with_suffix("").with_suffix(".json")))
    if backup_receipt["database"] != expected["database"] or backup_receipt["server_uuid"] != expected["server_uuid"] \
            or backup_receipt["sha256"] != digest(backup):
        raise ValueError("PROCESSING_BACKUP_IDENTITY_CHANGED")
    expanded = 0
    with gzip.open(backup, "rb") as stream:
        while block := stream.read(1048576):
            expanded += len(block)
    if expanded == 0:
        raise ValueError("PROCESSING_BACKUP_EMPTY")
    receipt = {"schema_version": "1.0.0", "scope": args.scope, "verified_at": datetime.now(timezone.utc).isoformat(),
        "database": database, "backup": backup_receipt, "backup_expanded_bytes": expanded,
        "customer_processing_verified": False, "customer_upload_completed": False,
        "business_worker_verified": False, "professional_approval_created": False}
    if args.scope == "application":
        application = runtime.verify_application_deployment()
        profile_path = ROOT / application["additional_configuration"]
        profile = yaml.safe_load(objects.private_file(profile_path))["medical"]
        for name in ("source-processing", "source-deletion"):
            if profile[name]["enabled"] is not False or profile[name]["tenant-ids"]:
                raise ValueError("PROCESSING_CURRENT_APPLICATION_SCOPE_CHANGED")
        if profile["source-completion"] != {"enabled": False, "bindings": []}:
            raise ValueError("COMPLETION_CURRENT_APPLICATION_SCOPE_CHANGED")
        if "source-rights" in profile and profile["source-rights"].get("enabled") is not False:
            raise ValueError("RIGHTS_CURRENT_APPLICATION_SCOPE_CHANGED")
        for item in profile["source-processing"]["source-files"]:
            if digest(ROOT / item["path"]) != item["sha256"]:
                raise ValueError("PROCESSING_CURRENT_PYTHON_SOURCE_CHANGED")
        source = ROOT / "business/medical-persistence/src/main/java/com/ruoyi/medical/persistence"
        classes = ROOT / "business/medical-persistence/target/classes"
        sources = sorted([*source.glob("SourceProcessing*.java"), *source.glob("SourceCompletion*.java"), source / "SourceGovernanceService.java",
            source / "SourceGovernanceProtocol.java", source / "SourceUploadService.java", source / "SourceUploadTransport.java", source / "TenantScope.java",
            source / "SourceUploadSchema.java", source / "SourceUploadProtocol.java", *source.glob("SourceUploadStatus*.java"),
            *source.glob("SourceRights*.java"), *source.glob("SourceDeletion*.java"),
            *source.glob("SourceIndex*.java"), *source.glob("SourceExact*.java"), *source.glob("RunTokenAuthority*.java"), source / "VersionResourceProtocol.java",
            source / "mapper/SourceProcessingMapper.java", source / "mapper/SourceCompletionMapper.java",
            source / "mapper/SourceUploadMapper.java", source / "mapper/SourceDeletionMapper.java", source / "mapper/SourceRightsMapper.java",
            source / "mapper/SourceGovernanceMapper.java", source / "mapper/SourceIndexMapper.java", source / "mapper/SourceExactMapper.java"])
        for path in sources:
            compiled = classes / "com/ruoyi/medical/persistence" / path.relative_to(source).with_suffix(".class")
            if path.stat().st_mtime_ns > compiled.stat().st_mtime_ns:
                raise ValueError("PROCESSING_CURRENT_SOURCE_REQUIRES_COMPILATION")
        observed_classes = []
        identity_source = ROOT / "business/medical-identity/src/main/java/com/ruoyi/medical/identity"
        identity_classes = ROOT / "business/medical-identity/target/classes"
        identity_names = ("ProtectedConfigurationFile", "RunTokenSignature", "RunTokenPolicyStore", "RunTokenSigningKeyStore",
            "ServiceCertificateIdentity", "ServiceCertificateTrustStore")
        identity_sources = [identity_source / (name + ".java") for name in identity_names]
        for path in identity_sources:
            compiled = identity_classes / "com/ruoyi/medical/identity" / path.with_suffix(".class").name
            if path.stat().st_mtime_ns > compiled.stat().st_mtime_ns:
                raise ValueError("RUN_TOKEN_CURRENT_SOURCE_REQUIRES_COMPILATION")
        with zipfile.ZipFile(ROOT / application["artifact"]) as jar:
            application_profile = yaml.safe_load(jar.read("BOOT-INF/classes/application-medical-local.yml"))["medical"]
            if "source-rights" in application_profile and application_profile["source-rights"].get("enabled") is not False:
                raise ValueError("RIGHTS_APPLICATION_PROFILE_CHANGED")
            if application_profile["source-upload-status"] != {"maximum-reviews": 1000}:
                raise ValueError("UPLOAD_STATUS_APPLICATION_PROFILE_CHANGED")
            if application_profile["source-index"] != {"enabled": False} \
                    or application_profile["source-index-views"] != {"maximum-builds": 1000}:
                raise ValueError("SOURCE_INDEX_APPLICATION_PROFILE_CHANGED")
            if application_profile["source-exact"] != {"maximum-fields": 1000, "maximum-matches": 5000}:
                raise ValueError("SOURCE_EXACT_APPLICATION_PROFILE_CHANGED")
            if "source-index" in profile and profile["source-index"].get("enabled") is not False:
                raise ValueError("SOURCE_INDEX_CURRENT_APPLICATION_SCOPE_CHANGED")
            if application_profile.get("run-authority", {}).get("enabled", False) is not False:
                raise ValueError("RUN_AUTHORITY_CURRENT_APPLICATION_SCOPE_CHANGED")
            authority = profile.get("run-authority")
            authority_proof = None
            if authority is not None:
                if set(authority) != {"enabled", "configuration-directory", "configuration-owner", "maximum-configuration-bytes"} \
                        or authority["enabled"] is not True:
                    raise ValueError("RUN_AUTHORITY_CURRENT_CONFIGURATION_REQUIRED")
                authority_directory = Path(authority["configuration-directory"]).resolve(strict=True)
                authority_directory.relative_to(runtime.RUNTIME / "run-authority")
                matches = []
                for path in runtime.RUNTIME.glob("run-authority-configuration-*.json"):
                    candidate = json.loads(objects.private_file(path))
                    if ROOT / candidate["configuration_directory"] == authority_directory:
                        matches.append((path, candidate))
                if len(matches) != 1:
                    raise ValueError("RUN_AUTHORITY_CURRENT_CONFIGURATION_IDENTITY_REQUIRED")
                path, current = matches[0]
                if current["scope"] != "LOCAL_RUN_AUTHORITY_CONFIGURATION" or current["customer_authorization_created"] is not False \
                        or current["configuration_owner"] != authority["configuration-owner"] \
                        or current["maximum_configuration_bytes"] != authority["maximum-configuration-bytes"]:
                    raise ValueError("RUN_AUTHORITY_CURRENT_CONFIGURATION_CHANGED")
                for name, metadata in current["files"].items():
                    if digest(authority_directory / name) != metadata["sha256"] or metadata["mode"] != "0o600":
                        raise ValueError("RUN_AUTHORITY_CURRENT_CONFIGURATION_FILE_CHANGED")
                authority_proof = {"path": str(path.relative_to(ROOT)), "sha256": digest(path), "service_uri": current["service_uri"],
                    "policy_revision": current["policy_revision"], "certificate_sha256": current["certificate_sha256"]}
            with zipfile.ZipFile(BytesIO(jar.read("BOOT-INF/lib/medical-identity-3.9.2.jar"))) as identity_module:
                for name in identity_names:
                    for path in sorted((identity_classes / "com/ruoyi/medical/identity").glob(name + "*.class")):
                        entry = str(path.relative_to(identity_classes))
                        if identity_module.read(entry) != path.read_bytes():
                            raise ValueError("RUN_TOKEN_CURRENT_APPLICATION_CLASS_CHANGED")
                        observed_classes.append({"path": entry, "sha256": digest(path)})
                for name in ("run-token-policy.schema.json", "run-token-signing-key.schema.json", "service-certificate-trust.schema.json"):
                    path = ROOT / "business/medical-identity/src/main/resources" / name
                    if identity_module.read(name) != path.read_bytes():
                        raise ValueError("RUN_TOKEN_CURRENT_APPLICATION_RESOURCE_CHANGED")
            with zipfile.ZipFile(BytesIO(jar.read("BOOT-INF/lib/medical-persistence-3.9.2.jar"))) as module:
                package = classes / "com/ruoyi/medical/persistence"
                current_classes = [*package.glob("SourceProcessing*.class"), *package.glob("SourceCompletion*.class"),
                    *package.glob("SourceDeletion*.class"), *package.glob("SourceGovernance*.class"), *package.glob("TenantScope*.class"),
                    *package.glob("SourceUploadService*.class"), package / "SourceUploadTransport.class",
                    *package.glob("SourceRights*.class"), *package.glob("SourceUploadStatus*.class"), package / "SourceUploadSchema.class",
                    *package.glob("SourceIndex*.class"), *package.glob("SourceExact*.class"), *package.glob("RunTokenAuthority*.class"), *package.glob("VersionResourceProtocol*.class"),
                    *package.glob("SourceUploadProtocol*.class"),
                    package / "mapper/SourceProcessingMapper.class", package / "mapper/SourceCompletionMapper.class",
                    package / "mapper/SourceUploadMapper.class", package / "mapper/SourceDeletionMapper.class", package / "mapper/SourceRightsMapper.class",
                    package / "mapper/SourceGovernanceMapper.class", package / "mapper/SourceIndexMapper.class", package / "mapper/SourceExactMapper.class"]
                for path in sorted(current_classes):
                    entry = str(path.relative_to(classes))
                    if module.read(entry) != path.read_bytes():
                        raise ValueError("PROCESSING_CURRENT_APPLICATION_CLASS_CHANGED")
                    observed_classes.append({"path": entry, "sha256": digest(path)})
                resources = ["run-token-authority.schema.json", "source-processing.schema.json", "source-completion-objects.schema.json", "source-completion-scan.schema.json",
                    "source-completion-command.schema.json", "source-completion-result.schema.json", "source-rights.schema.json",
                    "source-rights-pending.schema.json", "source-rights-decisions.schema.json", "source-uploads.schema.json",
                    "source-upload-status.schema.json", "source-governance.schema.json", "medical-mappers/SourceRightsMapper.xml",
                    "medical-mappers/SourceProcessingMapper.xml", "medical-mappers/SourceGovernanceMapper.xml",
                    "medical-mappers/SourceCompletionMapper.xml", "medical-mappers/SourceUploadMapper.xml", "medical-mappers/SourceDeletionMapper.xml",
                    "medical-mappers/RetrievalMapper.xml"]
                resources.extend(("source-index.schema.json", "source-index-view.schema.json", "medical-mappers/SourceIndexMapper.xml"))
                resources.extend(("source-exact.schema.json", "medical-mappers/SourceExactMapper.xml", "medical-mappers/VersionResourceMapper.xml"))
                for name in resources:
                    path = ROOT / "business/medical-persistence/src/main/resources" / name
                    if module.read(name) != path.read_bytes():
                        raise ValueError("PROCESSING_CURRENT_APPLICATION_RESOURCE_CHANGED")
        body = directory / "unauthenticated-processing-queue.json"
        response = subprocess.run(["curl", "--noproxy", "*", "--silent", "--show-error", "--max-time", "10",
            "--output", str(body), "--write-out", "%{http_code}", "http://127.0.0.1:18080/api/processing-attempts"],
            capture_output=True, text=True, check=True)
        if response.stdout != "401":
            raise ValueError("PROCESSING_UNAUTHENTICATED_ACCESS_INVALID")
        json.loads(body.read_bytes())
        original = runtime.mysql("USE medical_local; SELECT source_version FROM source_version ORDER BY source_version LIMIT 1;",
            runtime.settings()["root_password"]).stdout.strip()
        if str(UUID(original)) != original:
            raise ValueError("UPLOAD_STATUS_ACTUAL_IDENTITY_REQUIRED")
        status_body = directory / "unauthenticated-upload-status.json"
        status_response = subprocess.run(["curl", "--noproxy", "*", "--silent", "--show-error", "--max-time", "10",
            "--output", str(status_body), "--write-out", "%{http_code}",
            "http://127.0.0.1:18080/api/uploads/" + original + "/status"], capture_output=True, text=True, check=True)
        if status_response.stdout != "401":
            raise ValueError("UPLOAD_STATUS_UNAUTHENTICATED_ACCESS_INVALID")
        json.loads(status_body.read_bytes())
        original_project = runtime.mysql("USE medical_local; SELECT project_id FROM project ORDER BY project_id LIMIT 1;",
            runtime.settings()["root_password"]).stdout.strip()
        if str(UUID(original_project)) != original_project:
            raise ValueError("SOURCE_INDEX_ACTUAL_PROJECT_REQUIRED")
        index_body = directory / "unauthenticated-index-builds.json"
        index_response = subprocess.run(["curl", "--noproxy", "*", "--silent", "--show-error", "--max-time", "10",
            "--output", str(index_body), "--write-out", "%{http_code}",
            "http://127.0.0.1:18080/api/source-versions/" + original + "/index-builds?project_id=" + original_project],
            capture_output=True, text=True, check=True)
        if index_response.stdout != "401":
            raise ValueError("SOURCE_INDEX_UNAUTHENTICATED_ACCESS_INVALID")
        json.loads(index_body.read_bytes())
        exact_request = directory / "unauthenticated-exact-request.json"
        published = json.loads((ROOT / "data-design/sample_dataset.json").read_bytes())["sources"][0]["publisher_version"]
        objects.save_private(exact_request, {"schema_version": "1.0.0", "project_id": original_project,
            "field_kind": "DOCUMENT_NUMBER", "query": published, "purpose": "VERIFICATION"})
        exact_body = directory / "unauthenticated-exact-response.json"
        cookie_jar = directory / "unauthenticated-exact-cookies.txt"
        cookie_jar.touch(mode=0o600)
        csrf_body = directory / "unauthenticated-exact-csrf.json"
        csrf_response = subprocess.run(["curl", "--noproxy", "*", "--silent", "--show-error", "--max-time", "10",
            "--cookie-jar", str(cookie_jar), "--output", str(csrf_body), "--write-out", "%{http_code}",
            "http://127.0.0.1:18080/auth/csrf"], capture_output=True, text=True, check=True)
        csrf = json.loads(csrf_body.read_bytes())
        if csrf_response.stdout != "200" or set(csrf) != {"header_name", "parameter_name", "token"} \
                or csrf["header_name"] != "X-CSRF-TOKEN" or not isinstance(csrf["token"], str) or not csrf["token"]:
            raise ValueError("SOURCE_EXACT_ACTUAL_CSRF_REQUIRED")
        exact_response = subprocess.run(["curl", "--noproxy", "*", "--silent", "--show-error", "--max-time", "10",
            "--cookie", str(cookie_jar), "--header", csrf["header_name"] + ": " + csrf["token"],
            "--header", "Origin: " + application_profile["identity"]["local"]["browser-origin"],
            "--request", "POST", "--header", "Content-Type: application/json", "--data-binary", "@" + str(exact_request),
            "--output", str(exact_body), "--write-out", "%{http_code}", "http://127.0.0.1:18080/api/retrievals/exact"],
            capture_output=True, text=True, check=True)
        if exact_response.stdout != "401":
            raise ValueError("SOURCE_EXACT_UNAUTHENTICATED_ACCESS_INVALID")
        json.loads(exact_body.read_bytes())
        field_body = directory / "unauthenticated-exact-fields.json"
        field_response = subprocess.run(["curl", "--noproxy", "*", "--silent", "--show-error", "--max-time", "10",
            "--output", str(field_body), "--write-out", "%{http_code}",
            "http://127.0.0.1:18080/api/source-versions/" + original + "/exact-fields"], capture_output=True, text=True, check=True)
        if field_response.stdout != "401":
            raise ValueError("SOURCE_EXACT_FIELDS_UNAUTHENTICATED_ACCESS_INVALID")
        json.loads(field_body.read_bytes())
        receipt.update({"application": application, "profile_sha256": digest(profile_path),
            "run_authority_enabled": authority is not None, "run_authority_configuration": authority_proof,
            "verification_script_sha256": digest(Path(__file__).resolve()),
            "sources": [{"path": str(path.relative_to(ROOT)), "sha256": digest(path)} for path in sources],
            "identity_sources": [{"path": str(path.relative_to(ROOT)), "sha256": digest(path)} for path in identity_sources],
            "classes": observed_classes, "unauthenticated_status": response.stdout,
            "processing_enabled": False, "deletion_enabled": False, "completion_enabled": False, "rights_enabled": False,
            "upload_status_unauthenticated": status_response.stdout, "upload_status_checked_with_existing_source_identity": True,
            "index_enabled": False, "index_builds_unauthenticated": index_response.stdout,
            "index_builds_checked_with_existing_source_and_project": True,
            "exact_search_unauthenticated": exact_response.stdout, "exact_fields_unauthenticated": field_response.stdout,
            "exact_check_project_identity": original_project, "exact_actual_csrf_session_verified": True, "customer_exact_verified": False})
    objects.save_private(directory / "receipt.json", receipt)
    print(json.dumps({"receipt": str(directory / "receipt.json"), "migration_count": database["migration_count"],
        "processing_attempt_count": database["processing_attempt_count"], "scope": args.scope}))


if __name__ == "__main__":
    main()
