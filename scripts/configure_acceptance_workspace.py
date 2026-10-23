import hashlib
import argparse
import json
import os
from pathlib import Path
import secrets
import subprocess
import uuid
from urllib.parse import urljoin

import yaml

import local_runtime as runtime
from configure_local_discovery import private_metadata


ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-profile")
    arguments = parser.parse_args()
    if arguments.source_profile:
        original = ROOT / arguments.source_profile
        private_metadata(original)
        if not original.resolve().is_relative_to(runtime.RUNTIME / "application-profiles"):
            raise ValueError("ACCEPTANCE_PROFILE_LOCATION_INVALID")
    else:
        deployment = runtime.verify_application_deployment()
        original = ROOT / deployment["additional_configuration"]
    configuration = yaml.safe_load(original.read_text(encoding="utf-8"))
    private = runtime.RUNTIME / "acceptance-generator-settings.json"
    if private.exists():
        generator = json.loads(private.read_bytes())
    else:
        generator = {"password": secrets.token_urlsafe(32)}
        runtime.save_private_json(private, generator)
    password = generator["password"]
    if not all(character.isalnum() or character in "_-" for character in password):
        raise ValueError("ACCEPTANCE_GENERATOR_CREDENTIAL_INVALID")
    sql = "CREATE DATABASE IF NOT EXISTS framework_dev_acceptance CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci; CREATE USER IF NOT EXISTS 'acceptance_generator'@'%' IDENTIFIED BY '" + password + "'; GRANT ALL PRIVILEGES ON framework_dev_acceptance.* TO 'acceptance_generator'@'%';"
    runtime.mysql(sql, runtime.settings()["root_password"])
    libraries = [ROOT / ".implementation-work/maven-repository/com/github/jsqlparser/jsqlparser/5.2/jsqlparser-5.2.jar", ROOT / ".implementation-work/maven-repository/com/mysql/mysql-connector-j/9.7.0/mysql-connector-j-9.7.0.jar"]
    environment = runtime.environment()
    environment["MEDICAL_GENERATOR_PASSWORD"] = password
    subprocess.run([str(runtime.JAVA), "-Dfile.encoding=UTF-8", "-Djava.io.tmpdir=" + str(runtime.RUNTIME / "tmp"), "--class-path", os.pathsep.join(str(path) for path in libraries), str(ROOT / "scripts/SeedAcceptanceGenerator.java"), str(ROOT / "business/sql/ry_20260417.sql")], cwd=ROOT, env=environment, check=True)
    medical = configuration["medical"]
    medical["business-audit"] = {"enabled": True, "maximum-page-size": 100, "maximum-scan-rows": 1000, "maximum-payload-bytes": 1048576, "maximum-response-bytes": 4194304, "maximum-window-seconds": 2592000, "cursor-seconds": 900, "maximum-cursor-entries": 1000}
    catalogue = json.loads((ROOT / "contracts/catalogue.json").read_bytes())
    entry = next(item for item in catalogue["schemas"] if item["name"] == "interfaces")
    interfaces = ROOT / entry["path"]
    if not interfaces.is_file():
        raise ValueError("ACCEPTANCE_INTERFACES_SCHEMA_REQUIRED")
    manifest = hashlib.sha256(interfaces.read_bytes()).hexdigest()
    if manifest != entry["sha256"]:
        raise ValueError("ACCEPTANCE_INTERFACES_SCHEMA_CHANGED")
    medical["templates"] = {"environment": "LOCAL", "region": "LOCAL", "maximum-versions": 100, "maximum-relations": 200, "artifact-profiles": [{"role": "DEPENDENCY", "resource-kind": "ARTIFACT", "action": "artifact.read", "schema-ref": urljoin(catalogue["base_uri"], entry["path"]) + "#/$defs/TextContent", "schema-document-sha256": manifest, "artifact-schema-manifest": manifest}]}
    key_path = runtime.RUNTIME / "acceptance-object-keys.json"
    if key_path.exists():
        keys = json.loads(key_path.read_bytes())
    else:
        keys = {kind: {"id": str(uuid.uuid4()), "key": secrets.token_urlsafe(32)} for kind in ("document", "artifact")}
        runtime.save_private_json(key_path, keys)
    medical["document-upload"] = {"environment": "LOCAL", "region": "LOCAL", "maximum-sources": 1000, "maximum-versions": 10000, "maximum-relations": 40000, "maximum-bytes": 104857600, "expires-after-seconds": 3600, "artifact-schema-manifest": manifest, "artifact-profiles": [], "maximum-nodes": 10000, "maximum-table-cells": 100000, "maximum-body-relationships": 200000}
    medical["document-read"] = {"cursor-key-id": keys["document"]["id"], "cursor-key": keys["document"]["key"], "cursor-lifetime": "PT15M", "maximum-page-bytes": 1048576}
    medical["artifact-content-read"] = {"cursor-key-id": keys["artifact"]["id"], "cursor-key": keys["artifact"]["key"], "cursor-lifetime": "PT15M", "maximum-chunk-bytes": 1048576, "maximum-cache-bytes": 67108864, "maximum-cache-entries": 100, "maximum-tenant-cache-bytes": 33554432, "maximum-subject-cache-bytes": 16777216, "maximum-concurrent-transfers": 4}
    for kind in ("agent-authoring", "formal-agent-authoring", "agent-execution-policy"):
        medical[kind] = {**medical.get(kind, {}), "enabled": True}
    medical["runtime-retention"] = {**medical.get("runtime-retention", {}), "enabled": True, "maximum-candidates": 100}
    medical["framework"] = {**medical.get("framework", {}), "environment": "DEVELOPMENT", "generator": {"enabled": True, "datasource": {"url": "jdbc:mysql://127.0.0.1:13306/framework_dev_acceptance?characterEncoding=UTF-8&connectionTimeZone=UTC&useSSL=false&allowPublicKeyRetrieval=true", "username": "acceptance_generator", "password": password}}}
    directory = runtime.RUNTIME / "application-profiles" / str(uuid.uuid4())
    directory.mkdir(mode=0o700)
    target = directory / "application-workspace.yaml"
    descriptor = os.open(target, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600)
    with os.fdopen(descriptor, "w", encoding="utf-8") as output:
        yaml.safe_dump(configuration, output, allow_unicode=True, sort_keys=False)
        output.flush()
        os.fsync(output.fileno())
    receipt = {"profile": str(target.relative_to(ROOT)), "sha256": hashlib.sha256(target.read_bytes()).hexdigest(), "generator_database": "framework_dev_acceptance", "business_database_preserved": True}
    runtime.save_private_json(runtime.RUNTIME / "acceptance-profile.json", receipt, replace=True)
    print(json.dumps(receipt))


if __name__ == "__main__":
    main()
