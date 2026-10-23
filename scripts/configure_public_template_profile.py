import argparse
import hashlib
import json
from pathlib import Path
import uuid

import yaml

import local_runtime as runtime
from configure_local_discovery import private_metadata


ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-profile", required=True)
    arguments = parser.parse_args()
    source = (ROOT / arguments.source_profile).resolve(strict=True)
    source.relative_to(runtime.RUNTIME / "application-profiles")
    private_metadata(source)
    configuration = yaml.safe_load(source.read_text())
    manifest = hashlib.sha256((ROOT / "backend-design/interfaces.schema.yaml").read_bytes()).hexdigest()
    profiles = configuration["medical"]["templates"]["artifact-profiles"]
    for role in ("FONT", "FORMAT"):
        profile = {"role": role, "resource-kind": "ARTIFACT", "action": "artifact.read",
            "schema-ref": "https://schemas.medical-agent.invalid/1.0.0/backend-design/interfaces.schema.yaml#/$defs/ObjectRef",
            "schema-document-sha256": manifest, "artifact-schema-manifest": manifest}
        if any(item["role"] == role for item in profiles):
            if [item for item in profiles if item["role"] == role] != [profile]:
                raise ValueError("PUBLIC_TEMPLATE_PROFILE_CHANGED")
        else:
            profiles.append(profile)
    directory = runtime.RUNTIME / "application-profiles" / str(uuid.uuid4())
    directory.mkdir(mode=0o700)
    target = directory / "application-workspace.yaml"
    target.write_text(yaml.safe_dump(configuration, allow_unicode=True, sort_keys=False), encoding="utf-8")
    target.chmod(0o600)
    receipt = {"profile": str(target.relative_to(ROOT)), "sha256": hashlib.sha256(target.read_bytes()).hexdigest(), "roles": ["FONT", "FORMAT", "DEPENDENCY"]}
    runtime.save_private_json(runtime.RUNTIME / "acceptance-public-template-profile.json", receipt, replace=True)
    print(json.dumps(receipt))


if __name__ == "__main__":
    main()
