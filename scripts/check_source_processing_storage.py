import argparse
from contextlib import closing
import hashlib
import json
from pathlib import Path
from uuid import uuid4

import rfc8785

import local_object_storage as objects


ROOT = objects.ROOT


def main():
    parser = argparse.ArgumentParser(description="保存并重新读取真实解析包的固定对象版本")
    parser.add_argument("--parser-receipt", required=True)
    args = parser.parse_args()
    parser_path = Path(args.parser_receipt).resolve(strict=True)
    parser_path.relative_to(ROOT / ".implementation-work/source-processing-checks")
    evidence = json.loads(parser_path.read_bytes())
    if evidence["permission"] != "LOCAL_PARSER_CHECK" or evidence["input_data_kind"] != "REGISTERED" \
            or evidence["customer_processing_verified"] is not False or evidence["professional_approval_created"] is not False:
        raise ValueError("PROCESSING_CHECK_SCOPE_INVALID")
    result_path = Path(evidence["result_path"])
    result = json.loads(result_path.read_bytes())
    content_path = Path(evidence["bundle_path"])
    content = content_path.read_bytes()
    bundle = json.loads(content)
    content_sha256 = hashlib.sha256(content).hexdigest()
    if rfc8785.dumps(bundle) != content or result["bundle_sha256"] != content_sha256 \
            or result["bundle_size_bytes"] != str(len(content)) or result["status"] != bundle["status"]:
        raise ValueError("PROCESSING_BUNDLE_CHANGED")
    deployment = objects.verify_identity()
    check_id = str(uuid4())
    directory = ROOT / ".implementation-work/source-processing-storage-checks" / check_id
    directory.mkdir(mode=0o700, parents=True)
    key = "local-parser-checks/" + check_id + "/parsed.json"
    write_id = str(uuid4())
    metadata = {"write-id": write_id, "sha256": content_sha256, "size-bytes": str(len(content))}
    with closing(objects.client()) as storage:
        if storage.get_bucket_versioning(Bucket=objects.BUCKET)["Status"] != "Enabled":
            raise ValueError("PROCESSING_OBJECT_VERSIONING_REQUIRED")
        response = storage.put_object(Bucket=objects.BUCKET, Key=key, Body=content, ContentLength=len(content),
            ContentType="application/json", Metadata=metadata, ServerSideEncryption="AES256", IfNoneMatch="*")
        version = response["VersionId"]
        if not version or version == "null":
            raise ValueError("PROCESSING_OBJECT_VERSION_REQUIRED")
        head = storage.head_object(Bucket=objects.BUCKET, Key=key, VersionId=version)
        if head["VersionId"] != version or head["ContentLength"] != len(content) \
                or head["Metadata"] != metadata or head["ContentType"] != "application/json" \
                or head["ServerSideEncryption"] != "AES256":
            raise ValueError("PROCESSING_OBJECT_HEAD_MISMATCH")
        reading = storage.get_object(Bucket=objects.BUCKET, Key=key, VersionId=version)
        with reading["Body"] as stream:
            actual = stream.read()
        if actual != content or hashlib.sha256(actual).hexdigest() != content_sha256 \
                or reading["VersionId"] != version or reading["ServerSideEncryption"] != "AES256":
            raise ValueError("PROCESSING_OBJECT_BYTES_MISMATCH")
        versions = storage.list_object_versions(Bucket=objects.BUCKET, Prefix=key)
        if versions["IsTruncated"] or versions.get("DeleteMarkers", []) \
                or [(item["Key"], item["VersionId"]) for item in versions["Versions"]] != [(key, version)]:
            raise ValueError("PROCESSING_OBJECT_IDENTITY_MISMATCH")
    if deployment != objects.verify_identity():
        raise ValueError("PROCESSING_OBJECT_SERVICE_CHANGED")
    receipt = {"schema_version": "1.0.0", "check_id": check_id, "permission": "LOCAL_PARSER_CHECK",
        "input_data_kind": "REGISTERED", "parser_receipt": str(parser_path),
        "parser_receipt_sha256": hashlib.sha256(parser_path.read_bytes()).hexdigest(),
        "command_path": evidence["command_path"], "result_path": str(result_path), "bundle_path": str(content_path),
        "deployment_id": deployment["deployment_id"], "service_id": deployment["service_id"],
        "endpoint": objects.ENDPOINT, "bucket": objects.BUCKET, "region": "us-east-1",
        "write_id": write_id, "object_ref": {"object_key": key, "object_version": version,
            "sha256": content_sha256, "size_bytes": str(len(content)), "media_type": "application/json"},
        "version_count": 1, "encrypted": True, "bytes_reopened": True,
        "customer_processing_verified": False, "professional_approval_created": False}
    receipt_path = directory / "receipt.json"
    objects.save_private(receipt_path, receipt)
    print(json.dumps({"receipt": str(receipt_path), "version_count": 1, "bytes_reopened": True}))


if __name__ == "__main__":
    main()
