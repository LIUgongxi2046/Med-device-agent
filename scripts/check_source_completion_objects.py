import argparse
from contextlib import closing
import hashlib
import json
from pathlib import Path
from uuid import uuid4

import local_object_storage as objects


ROOT = objects.ROOT
PART_BYTES = 8388608


def main():
    parser = argparse.ArgumentParser(description="保存并重读实际登记原件的固定分段对象")
    parser.add_argument("--parser-receipt", required=True)
    args = parser.parse_args()
    parser_path = Path(args.parser_receipt).resolve(strict=True)
    parser_path.relative_to(ROOT / ".implementation-work/source-processing-checks")
    evidence = json.loads(objects.private_file(parser_path))
    if evidence["permission"] != "LOCAL_PARSER_CHECK" or evidence["input_data_kind"] != "REGISTERED" \
            or evidence["customer_processing_verified"] is not False or evidence["professional_approval_created"] is not False:
        raise ValueError("COMPLETION_CHECK_SCOPE_INVALID")
    command_path = Path(evidence["command_path"])
    command_bytes = objects.private_file(command_path)
    if [item["sha256"] for item in evidence["artifacts"] if item["path"] == str(command_path)] \
            != [hashlib.sha256(command_bytes).hexdigest()]:
        raise ValueError("COMPLETION_ORIGINAL_COMMAND_CHANGED")
    command = json.loads(command_bytes)
    source = Path(command["source_path"]).resolve(strict=True)
    source.relative_to(ROOT / "ui-delivery/assets/public-evidence")
    content = source.read_bytes()
    sha256 = hashlib.sha256(content).hexdigest()
    if sha256 != evidence["original_sha256"] or sha256 != command["original_sha256"] \
            or str(len(content)) != command["size_bytes"] or not 0 < len(content) <= 104857600:
        raise ValueError("COMPLETION_ORIGINAL_CHANGED")
    deployment = objects.verify_identity()
    check_id = str(uuid4())
    directory = ROOT / ".implementation-work/source-completion-object-checks" / check_id
    directory.mkdir(mode=0o700, parents=True)
    prefix = "local-parser-checks/" + check_id + "/original-parts"
    parts = []
    with closing(objects.client()) as storage:
        if storage.get_bucket_versioning(Bucket=objects.BUCKET)["Status"] != "Enabled":
            raise ValueError("COMPLETION_VERSIONING_REQUIRED")
        for start in range(0, len(content), PART_BYTES):
            number = len(parts) + 1
            chunk = content[start:start + PART_BYTES]
            chunk_sha256 = hashlib.sha256(chunk).hexdigest()
            write_id = str(uuid4())
            key = prefix + "/" + str(number) + "/" + write_id
            metadata = {"write-id": write_id, "sha256": chunk_sha256, "size-bytes": str(len(chunk))}
            response = storage.put_object(Bucket=objects.BUCKET, Key=key, Body=chunk, ContentLength=len(chunk),
                ContentType="application/octet-stream", Metadata=metadata, ServerSideEncryption="AES256", IfNoneMatch="*")
            version = response["VersionId"]
            if not version or version == "null":
                raise ValueError("COMPLETION_OBJECT_VERSION_REQUIRED")
            head = storage.head_object(Bucket=objects.BUCKET, Key=key, VersionId=version)
            if head["VersionId"] != version or head["ContentLength"] != len(chunk) or head["Metadata"] != metadata \
                    or head["ContentType"] != "application/octet-stream" or head["ServerSideEncryption"] != "AES256":
                raise ValueError("COMPLETION_OBJECT_HEAD_MISMATCH")
            response = storage.get_object(Bucket=objects.BUCKET, Key=key, VersionId=version)
            with response["Body"] as stream:
                actual = stream.read()
            if actual != chunk or response["VersionId"] != version or response["ServerSideEncryption"] != "AES256":
                raise ValueError("COMPLETION_OBJECT_BYTES_MISMATCH")
            versions = storage.list_object_versions(Bucket=objects.BUCKET, Prefix=key)
            if versions["IsTruncated"] or versions.get("DeleteMarkers", []) \
                    or [(item["Key"], item["VersionId"]) for item in versions["Versions"]] != [(key, version)]:
                raise ValueError("COMPLETION_OBJECT_IDENTITY_CHANGED")
            parts.append({"part_no": number, "write_id": write_id, "revision": str(len(versions["Versions"])),
                "sha256": chunk_sha256, "size_bytes": str(len(chunk)), "object_ref": {"object_key": key,
                    "object_version": version, "sha256": chunk_sha256, "size_bytes": str(len(chunk)),
                    "media_type": "application/octet-stream"}})
    if source.read_bytes() != content or deployment != objects.verify_identity():
        raise ValueError("COMPLETION_INPUT_IDENTITY_CHANGED")
    receipt = {"schema_version": "1.0.0", "check_id": check_id, "permission": "LOCAL_PARSER_CHECK",
        "input_data_kind": "REGISTERED", "parser_receipt": str(parser_path),
        "parser_receipt_sha256": hashlib.sha256(parser_path.read_bytes()).hexdigest(),
        "original_path": str(source), "original_sha256": sha256, "size_bytes": str(len(content)), "parts": parts,
        "object_prefix": prefix, "service_id": deployment["service_id"], "deployment_id": deployment["deployment_id"],
        "endpoint": objects.ENDPOINT, "bucket": objects.BUCKET, "region": "us-east-1",
        "customer_upload_completed": False, "professional_approval_created": False}
    objects.save_private(directory / "receipt.json", receipt)
    print(json.dumps({"receipt": str(directory / "receipt.json"), "parts": len(parts), "size_bytes": str(len(content))}))


if __name__ == "__main__":
    main()
