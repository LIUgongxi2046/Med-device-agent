import base64
import hashlib
import json
import os
import secrets
import stat
import uuid

import yaml

import local_runtime


def private_metadata(path):
    metadata = path.lstat()
    if path.is_symlink() or not stat.S_ISREG(metadata.st_mode) or metadata.st_uid != os.getuid() or stat.S_IMODE(metadata.st_mode) != 0o600:
        raise RuntimeError("LOCAL_DISCOVERY_PRIVATE_FILE_INVALID")


def main():
    deployment = local_runtime.verify_application_deployment()
    source = local_runtime.ROOT / deployment["source"]
    if hashlib.sha256(source.read_bytes()).hexdigest() != deployment["sha256"]:
        raise RuntimeError("LOCAL_DISCOVERY_APPLICATION_SOURCE_CHANGED")
    original = local_runtime.ROOT / deployment["additional_configuration"]
    private_metadata(original)
    configuration = yaml.safe_load(original.read_text(encoding="utf-8"))
    if not isinstance(configuration, dict) or not isinstance(configuration.get("medical"), dict):
        raise RuntimeError("LOCAL_DISCOVERY_CONFIGURATION_INVALID")
    if "configuration-discovery" in configuration["medical"]:
        raise RuntimeError("LOCAL_DISCOVERY_ALREADY_CONFIGURED")
    key_path = local_runtime.RUNTIME / "configuration-discovery-key.json"
    if key_path.exists():
        private_metadata(key_path)
        key = json.loads(key_path.read_bytes())
    else:
        key = {"key_id": str(uuid.uuid4()), "key": secrets.token_urlsafe(32)}
        local_runtime.save_private_json(key_path, key)
    uuid.UUID(key["key_id"])
    if len(base64.urlsafe_b64decode(key["key"] + "=")) != 32:
        raise RuntimeError("LOCAL_DISCOVERY_KEY_INVALID")
    configuration["medical"]["configuration-discovery"] = {
        "cursor-key-id": key["key_id"], "cursor-key": key["key"], "cursor-lifetime": "PT15M"}
    directory = local_runtime.RUNTIME / "application-profiles" / str(uuid.uuid4())
    directory.mkdir(mode=0o700)
    profile = directory / "application-workspace.yaml"
    descriptor = os.open(profile, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600)
    with os.fdopen(descriptor, "w", encoding="utf-8") as output:
        yaml.safe_dump(configuration, output, allow_unicode=True, sort_keys=False)
        output.flush()
        os.fsync(output.fileno())
    private_metadata(profile)
    receipt = {"profile": str(profile.relative_to(local_runtime.ROOT)), "sha256": hashlib.sha256(profile.read_bytes()).hexdigest(),
        "previous_deployment": deployment["deployment_id"], "artifact_sha256": deployment["sha256"], "cursor_key_id": key["key_id"], "lifetime": "PT15M"}
    local_runtime.save_private_json(local_runtime.RUNTIME / "configuration-discovery-deployment.json", receipt, replace=True)
    print(json.dumps(receipt, ensure_ascii=False))


if __name__ == "__main__":
    main()
