import hashlib
import importlib.metadata
import json
import os
from contextlib import closing
from pathlib import Path
import sys
import unittest
import uuid

import boto3
from botocore import UNSIGNED
from botocore.config import Config
from botocore.exceptions import ClientError


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
from local_object_storage import BUCKET, ENDPOINT, SECRET, client, private_file, verify_identity


class PrivateObjectStorageChecks(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.deployment = verify_identity()
        cls.storage = client()
        cls.storage.head_bucket(Bucket=BUCKET)
        cls.execution = os.environ["MEDICAL_OBJECT_STORAGE_EXECUTION"]
        if str(uuid.UUID(cls.execution)) != cls.execution:
            raise ValueError("OBJECT_STORAGE_CHECK_ID_INVALID")
        cls.results = []

    @classmethod
    def tearDownClass(cls):
        cls.storage.close()
        if cls.deployment != verify_identity():
            raise ValueError("OBJECT_STORAGE_CHECK_PROCESS_CHANGED")
        output = Path(os.environ["MEDICAL_OBJECT_STORAGE_RESULT"])
        with output.open("x", encoding="utf-8") as destination:
            json.dump({"scope": "LOCAL_PRIVATE_STORAGE_PUBLIC_ORIGINALS", "deployment": cls.deployment,
                "dependencies": {name: importlib.metadata.version(name) for name in ("boto3", "botocore", "s3transfer")},
                "objects": cls.results, "customer_records_created": False, "formal_evidence_eligible": False}, destination)

    def require_protection(self):
        self.assertEqual(self.storage.get_bucket_versioning(Bucket=BUCKET).get("Status"), "Enabled")
        rules = self.storage.get_bucket_encryption(Bucket=BUCKET)["ServerSideEncryptionConfiguration"]["Rules"]
        self.assertEqual(len(rules), 1)
        self.assertEqual(rules[0]["ApplyServerSideEncryptionByDefault"]["SSEAlgorithm"], "AES256")
        acl = self.storage.get_bucket_acl(Bucket=BUCKET)
        self.assertEqual(len(acl["Grants"]), 1)
        grant = acl["Grants"][0]
        self.assertEqual(grant["Permission"], "FULL_CONTROL")
        self.assertEqual(grant["Grantee"]["Type"], "CanonicalUser")
        self.assertEqual(grant["Grantee"]["ID"], acl["Owner"]["ID"])

    def test_bucket_requires_version_and_encryption(self):
        self.require_protection()

    def test_anonymous_cannot_read_private_bucket(self):
        unsigned = boto3.client("s3", endpoint_url=ENDPOINT, region_name="us-east-1",
            config=Config(signature_version=UNSIGNED, connect_timeout=5, read_timeout=10,
                retries={"total_max_attempts": 1}, proxies={}, s3={"addressing_style": "path"}))
        with closing(unsigned):
            with self.assertRaises(ClientError) as rejected:
                unsigned.list_objects_v2(Bucket=BUCKET, MaxKeys=1)
        self.assertEqual(rejected.exception.response["ResponseMetadata"]["HTTPStatusCode"], 403)
        self.assertEqual(rejected.exception.response["Error"]["Code"], "AccessDenied")

    def test_real_originals_keep_fixed_versions_and_ranges(self):
        self.require_protection()
        dataset = json.loads((ROOT / "data-design/sample_dataset.json").read_bytes())
        identifiers = ("official-clinical-2021-73-a4", "official-safety-2021-121-a9")
        sources = [next(source for source in dataset["sources"] if source["source_id"] == identifier) for identifier in identifiers]
        key = "engineering-public-originals/" + self.execution + "/original"
        versions = []
        for source in sources:
            self.assertEqual(source["sensitivity"], "PUBLIC")
            self.assertIn("LOCAL_DESIGN_REVIEW", source["allowed_use"])
            content = (ROOT / source["source_ref"]).read_bytes()
            self.assertEqual(len(content), source["size_bytes"])
            self.assertEqual(hashlib.sha256(content).hexdigest(), source["sha256"])
            mime = {"DOCX": "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "DOC_REFERENCE": "application/msword"}[source["format"]]
            response = self.storage.put_object(Bucket=BUCKET, Key=key, Body=content, ContentLength=len(content),
                ContentType=mime, ServerSideEncryption="AES256", Metadata={"sha256": source["sha256"],
                "source-id": source["source_id"], "purpose": "LOCAL_DESIGN_REVIEW"})
            version = response["VersionId"]
            self.assertTrue(version)
            self.assertNotEqual(version, "null")
            self.assertNotIn(version, versions)
            self.assertEqual(response["ServerSideEncryption"], "AES256")
            versions.append(version)
            self.results.append({"object_key": key, "object_version": version, "sha256": source["sha256"],
                "size_bytes": str(len(content)), "media_type": mime, "source_ref": source["source_ref"]})

        for reference in self.results:
            content = (ROOT / reference["source_ref"]).read_bytes()
            response = self.storage.get_object(Bucket=BUCKET, Key=key, VersionId=reference["object_version"])
            with response["Body"] as body:
                stored = body.read()
            self.assertEqual(response["VersionId"], reference["object_version"])
            self.assertEqual(response["ServerSideEncryption"], "AES256")
            self.assertEqual(response["ContentType"], reference["media_type"])
            self.assertEqual(response["ContentLength"], int(reference["size_bytes"]))
            self.assertEqual(response["Metadata"]["sha256"], reference["sha256"])
            self.assertEqual(stored, content)
            self.assertEqual(hashlib.sha256(stored).hexdigest(), reference["sha256"])
            end = len(content) // 2
            ranged = self.storage.get_object(Bucket=BUCKET, Key=key, VersionId=reference["object_version"], Range="bytes=0-" + str(end))
            with ranged["Body"] as body:
                portion = body.read()
            self.assertEqual(ranged["ResponseMetadata"]["HTTPStatusCode"], 206)
            self.assertEqual(ranged["VersionId"], reference["object_version"])
            self.assertEqual(portion, content[:end + 1])
            self.assertEqual(ranged["ContentRange"], "bytes 0-" + str(end) + "/" + str(len(content)))
            unsigned = boto3.client("s3", endpoint_url=ENDPOINT, region_name="us-east-1",
                config=Config(signature_version=UNSIGNED, connect_timeout=5, read_timeout=10,
                    retries={"total_max_attempts": 1}, proxies={}, s3={"addressing_style": "path"}))
            with closing(unsigned):
                with self.assertRaises(ClientError) as denied:
                    unsigned.get_object(Bucket=BUCKET, Key=key, VersionId=reference["object_version"])
            self.assertEqual(denied.exception.response["ResponseMetadata"]["HTTPStatusCode"], 403)

        from local_object_storage import DIRECTORY
        volumes = list((DIRECTORY / "data").rglob("*.dat"))
        self.assertTrue(volumes)
        for path in volumes:
            stored = path.read_bytes()
            for reference in self.results:
                self.assertTrue((ROOT / reference["source_ref"]).read_bytes() not in stored, "OBJECT_STORAGE_VOLUME_HAS_ORIGINAL_BYTES")

    def test_service_secrets_are_absent_from_native_log(self):
        value = json.loads(private_file(SECRET))
        from local_object_storage import DIRECTORY
        log = (DIRECTORY / "service.log").read_bytes()
        for key in ("access_key", "secret_key", "sse_kek"):
            self.assertTrue(value[key].encode("ascii") not in log, "OBJECT_STORAGE_LOG_CONTAINS_SECRET")


if __name__ == "__main__":
    unittest.main(verbosity=2)
