import argparse
from contextlib import closing
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
from uuid import uuid4

import rfc8785

import local_object_storage as objects


def main():
    parser = argparse.ArgumentParser(description="恢复对象服务后重读原解析包的固定版本")
    parser.add_argument("--storage-receipt", required=True)
    args = parser.parse_args()
    predecessor = Path(args.storage_receipt).resolve(strict=True)
    predecessor.relative_to(objects.ROOT / ".implementation-work/source-processing-storage-checks")
    evidence_bytes = objects.private_file(predecessor)
    evidence = json.loads(evidence_bytes)
    if evidence["permission"] != "LOCAL_PARSER_CHECK" or evidence["input_data_kind"] != "REGISTERED" \
            or evidence["customer_processing_verified"] is not False or evidence["professional_approval_created"] is not False \
            or evidence["endpoint"] != objects.ENDPOINT or evidence["bucket"] != objects.BUCKET \
            or evidence["region"] != "us-east-1":
        raise ValueError("PROCESSING_RECOVERY_SCOPE_INVALID")
    actual = objects.verify_identity()
    if actual["service_id"] != evidence["service_id"]:
        raise ValueError("PROCESSING_RECOVERY_STORAGE_CHANGED")
    reference = evidence["object_ref"]
    bundle_path = Path(evidence["bundle_path"]).resolve(strict=True)
    bundle_path.relative_to(objects.ROOT / ".implementation-work/source-processing-checks")
    content = objects.private_file(bundle_path)
    if hashlib.sha256(content).hexdigest() != reference["sha256"] or len(content) != int(reference["size_bytes"]) \
            or rfc8785.dumps(json.loads(content)) != content or reference["media_type"] != "application/json" \
            or not reference["object_key"].startswith("local-parser-checks/" + evidence["check_id"] + "/"):
        raise ValueError("PROCESSING_RECOVERY_OBJECT_CHANGED")
    expected_metadata = {"write-id": evidence["write_id"], "sha256": reference["sha256"], "size-bytes": reference["size_bytes"]}
    with closing(objects.client()) as storage:
        if storage.get_bucket_versioning(Bucket=objects.BUCKET)["Status"] != "Enabled":
            raise ValueError("PROCESSING_RECOVERY_VERSIONING_REQUIRED")
        head = storage.head_object(Bucket=objects.BUCKET, Key=reference["object_key"], VersionId=reference["object_version"])
        if head["VersionId"] != reference["object_version"] or head["ContentLength"] != len(content) \
                or head["ContentType"] != reference["media_type"] or head["Metadata"] != expected_metadata \
                or head["ServerSideEncryption"] != "AES256":
            raise ValueError("PROCESSING_RECOVERY_METADATA_CHANGED")
        reading = storage.get_object(Bucket=objects.BUCKET, Key=reference["object_key"], VersionId=reference["object_version"])
        with reading["Body"] as stream:
            reopened = stream.read()
        if reopened != content or reading["VersionId"] != reference["object_version"] \
                or reading["Metadata"] != expected_metadata or reading["ServerSideEncryption"] != "AES256":
            raise ValueError("PROCESSING_RECOVERY_BYTES_CHANGED")
        versions = storage.list_object_versions(Bucket=objects.BUCKET, Prefix=reference["object_key"])
        if versions["IsTruncated"] or versions.get("DeleteMarkers", []) \
                or [(row["Key"], row["VersionId"]) for row in versions["Versions"]] != [(reference["object_key"], reference["object_version"])]:
            raise ValueError("PROCESSING_RECOVERY_VERSION_CHANGED")
    if actual != objects.verify_identity() or objects.private_file(predecessor) != evidence_bytes:
        raise ValueError("PROCESSING_RECOVERY_IDENTITY_CHANGED")
    directory = objects.ROOT / ".implementation-work/source-processing-storage-checks" / str(uuid4())
    directory.mkdir(mode=0o700, parents=True)
    receipt = {**evidence, "deployment_id": actual["deployment_id"],
        "verified_at": datetime.now(timezone.utc).isoformat(), "original_object_preserved": True,
        "predecessor_receipt": str(predecessor), "predecessor_sha256": hashlib.sha256(evidence_bytes).hexdigest(),
        "checker_sha256": hashlib.sha256(Path(__file__).read_bytes()).hexdigest()}
    objects.save_private(directory / "receipt.json", receipt)
    print(json.dumps({"receipt": str(directory / "receipt.json"), "original_object_preserved": True,
        "version_count": 1, "bytes_reopened": True}))


if __name__ == "__main__":
    main()
