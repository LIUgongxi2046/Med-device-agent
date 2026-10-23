import argparse
import hashlib
import json
import os
from pathlib import Path
from uuid import uuid4

import yaml

import local_object_storage as objects


def main():
    parser = argparse.ArgumentParser(description="绑定当前本地C02受保护配置到独立应用配置")
    parser.add_argument("--configuration-receipt", required=True, type=Path)
    parser.add_argument("--source-profile", required=True, type=Path)
    parser.add_argument("--replace-existing", action="store_true")
    args = parser.parse_args()
    receipt_path = args.configuration_receipt.resolve(strict=True)
    receipt_path.relative_to(objects.ROOT / ".implementation-work/runtime")
    receipt = json.loads(objects.private_file(receipt_path))
    if receipt["scope"] != "LOCAL_RUN_AUTHORITY_CONFIGURATION" or receipt["customer_authorization_created"] is not False:
        raise ValueError("RUN_AUTHORITY_CURRENT_LOCAL_SCOPE_REQUIRED")
    configuration = objects.ROOT / receipt["configuration_directory"]
    configuration.resolve(strict=True).relative_to(objects.ROOT / ".implementation-work/runtime/run-authority")
    if configuration.is_symlink() or configuration.stat().st_mode & 0o777 != 0o700 or configuration.stat().st_uid != os.getuid():
        raise ValueError("RUN_AUTHORITY_CURRENT_LOCAL_DIRECTORY_REQUIRED")
    for name, expected in receipt["files"].items():
        path = configuration / name
        if expected["mode"] != "0o600" or hashlib.sha256(objects.private_file(path)).hexdigest() != expected["sha256"]:
            raise ValueError("RUN_AUTHORITY_CURRENT_LOCAL_FILE_CHANGED")
    source = args.source_profile.resolve(strict=True)
    source.relative_to(objects.ROOT / ".implementation-work/runtime/application-profiles")
    profile = yaml.safe_load(objects.private_file(source))
    previous = profile["medical"].get("run-authority")
    if previous is not None and not args.replace_existing:
        raise ValueError("RUN_AUTHORITY_PROFILE_ALREADY_CONFIGURED")
    if args.replace_existing:
        if not isinstance(previous, dict) or previous.get("enabled") is not True \
                or previous.get("configuration-owner") != receipt["configuration_owner"] \
                or previous.get("maximum-configuration-bytes") != receipt["maximum_configuration_bytes"]:
            raise ValueError("RUN_AUTHORITY_CURRENT_PROFILE_REQUIRED")
        Path(previous["configuration-directory"]).resolve(strict=True).relative_to(objects.ROOT / ".implementation-work/runtime/run-authority")
    profile["medical"]["run-authority"] = {"enabled": True, "configuration-directory": str(configuration),
        "configuration-owner": receipt["configuration_owner"], "maximum-configuration-bytes": receipt["maximum_configuration_bytes"]}
    identity = str(uuid4())
    directory = objects.ROOT / ".implementation-work/runtime/application-profiles" / identity
    directory.mkdir(mode=0o700)
    target = directory / "application-source-workers.yaml"
    descriptor = os.open(target, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600)
    with os.fdopen(descriptor, "w", encoding="utf-8") as output:
        yaml.safe_dump(profile, output, allow_unicode=True, sort_keys=False)
        output.flush()
        os.fsync(output.fileno())
    reread = yaml.safe_load(objects.private_file(target))
    authority = reread["medical"].pop("run-authority")
    original = yaml.safe_load(objects.private_file(source))
    if args.replace_existing:
        original["medical"].pop("run-authority")
    if reread != original:
        raise ValueError("RUN_AUTHORITY_ORIGINAL_APPLICATION_PROFILE_CHANGED")
    proof = directory / "run-authority-profile.json"
    objects.save_private(proof, {"schema_version": "1.0.0", "scope": "LOCAL_RUN_AUTHORITY_APPLICATION_PROFILE",
        "source_profile": str(source.relative_to(objects.ROOT)), "source_sha256": hashlib.sha256(objects.private_file(source)).hexdigest(),
        "configuration_receipt": str(receipt_path.relative_to(objects.ROOT)), "configuration_receipt_sha256": hashlib.sha256(objects.private_file(receipt_path)).hexdigest(),
        "profile": str(target.relative_to(objects.ROOT)), "profile_sha256": hashlib.sha256(objects.private_file(target)).hexdigest(),
        "authority": authority, "customer_authorization_created": False, "worker_started": False})
    print(str(proof.relative_to(objects.ROOT)), flush=True)


if __name__ == "__main__":
    main()
