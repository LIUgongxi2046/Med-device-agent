import hashlib
import json
import os
from contextlib import closing
from pathlib import Path
import sys
import unittest


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
from local_object_storage import BUCKET, client, verify_identity


class NativeStorageRecoveryChecks(unittest.TestCase):
    def test_saved_original_versions_survive_native_restart(self):
        previous = json.loads(Path(os.environ["MEDICAL_OBJECT_STORAGE_RECOVERY_FROM"]).read_bytes())
        current = verify_identity()
        self.assertEqual(previous["scope"], "LOCAL_PRIVATE_STORAGE_PUBLIC_ORIGINALS")
        self.assertFalse(previous["formal_evidence_eligible"])
        self.assertFalse(previous["customer_records_created"])
        self.assertNotEqual(previous["deployment"]["deployment_id"], current["deployment_id"])
        for field in ("service_id", "os_uid", "endpoint", "bucket", "binary_sha256", "archive_sha256"):
            self.assertEqual(previous["deployment"][field], current[field])
        references = previous["objects"]
        self.assertEqual(len(references), 2)
        sources = json.loads((ROOT / "data-design/sample_dataset.json").read_bytes())["sources"]
        observations = []
        with closing(client()) as storage:
            self.assertEqual(storage.get_bucket_versioning(Bucket=BUCKET)["Status"], "Enabled")
            encryption = storage.get_bucket_encryption(Bucket=BUCKET)["ServerSideEncryptionConfiguration"]["Rules"]
            self.assertEqual(len(encryption), 1)
            self.assertEqual(encryption[0]["ApplyServerSideEncryptionByDefault"]["SSEAlgorithm"], "AES256")
            for reference in references:
                source = next(source for source in sources if source["source_ref"] == reference["source_ref"])
                self.assertEqual(reference["sha256"], source["sha256"])
                self.assertEqual(reference["size_bytes"], str(source["size_bytes"]))
                original = (ROOT / source["source_ref"]).read_bytes()
                self.assertEqual(hashlib.sha256(original).hexdigest(), source["sha256"])
                response = storage.get_object(Bucket=BUCKET, Key=reference["object_key"], VersionId=reference["object_version"])
                with response["Body"] as body:
                    content = body.read()
                self.assertEqual(content, original)
                self.assertEqual(hashlib.sha256(content).hexdigest(), reference["sha256"])
                self.assertEqual(response["ContentLength"], source["size_bytes"])
                self.assertEqual(response["VersionId"], reference["object_version"])
                self.assertEqual(response["Metadata"]["sha256"], reference["sha256"])
                self.assertEqual(response["ServerSideEncryption"], "AES256")
                observations.append(reference)
        self.assertEqual(current, verify_identity())
        with Path(os.environ["MEDICAL_OBJECT_STORAGE_RESULT"]).open("x", encoding="utf-8") as output:
            json.dump({"scope": "LOCAL_OBJECT_STORAGE_RESTART", "previous_deployment_id": previous["deployment"]["deployment_id"],
                "deployment": current, "objects": observations, "formal_evidence_eligible": False,
                "customer_records_created": False}, output, ensure_ascii=False)


if __name__ == "__main__":
    unittest.main(verbosity=2)
