import hashlib
import json
import os
from pathlib import Path
import stat
from tempfile import TemporaryDirectory
import unittest
from uuid import NAMESPACE_URL, uuid5, uuid4

import rfc8785

from medical_contracts.catalogue import SchemaCatalogue
from medical_contracts.dataset import DatasetBindings
from medical_document import NativeParser
from test_source_scan_native import scanner_release


ROOT = Path(__file__).resolve().parents[1]
WORK = ROOT / ".implementation-work/source-processing-command-tests"
ORIGINAL = ROOT / "ui-delivery/assets/public-evidence/clinical-evaluation-2021.docx"


class SourceProcessingCommandTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        WORK.mkdir(mode=0o700, parents=True, exist_ok=True)
        WORK.chmod(0o700)
        cls.parser = NativeParser(SchemaCatalogue(ROOT))
        cls.release = scanner_release()
        cls.original_hash = hashlib.sha256(ORIGINAL.read_bytes()).hexdigest()

    def prepare(self, directory):
        private = Path(directory)
        release_path = private / "scanner-release.json"
        release_path.write_bytes(rfc8785.dumps(self.release))
        release_path.chmod(0o600)
        output = private / "output"
        output.mkdir(mode=0o700)
        configuration_hash = hashlib.sha256(rfc8785.dumps(self.release)).hexdigest()
        command = {
            "schema_version": "1.0.0", "attempt_id": str(uuid4()), "run_id": str(uuid4()),
            "source_version": str(uuid5(NAMESPACE_URL, "LOCAL_PARSER_CHECK:" + self.original_hash)),
            "parse_version": str(uuid4()), "config_revision": str(uuid5(NAMESPACE_URL, configuration_hash)),
            "config_sha256": configuration_hash, "worker_id": str(uuid4()), "lease_generation": "1",
            "format": "DOCX", "original_sha256": self.original_hash,
            "size_bytes": str(ORIGINAL.stat().st_size), "source_path": str(ORIGINAL),
            "catalogue_root": str(ROOT), "scanner_release_path": str(release_path),
            "scanner_release_sha256": configuration_hash, "output_directory": str(output),
        }
        return command, private / "command.json", private / "result.json"

    def execute(self, command, path, result):
        path.write_bytes(rfc8785.dumps(command))
        path.chmod(0o600)
        self.assertTrue(callable(getattr(self.parser, "process_command", None)),
            "C09需要通过实际处理命令保存完整节点、位置、扫描及版本回执")
        self.parser.process_command(path, result)

    def test_registered_original_produces_complete_fixed_bundle_and_scan_receipts(self):
        with TemporaryDirectory(dir=WORK) as directory:
            command, path, result = self.prepare(directory)
            self.execute(command, path, result)
            receipt = json.loads(result.read_bytes())
            bundle_path = Path(receipt["bundle_path"])
            bundle = json.loads(bundle_path.read_bytes())
            for field in ("attempt_id", "run_id", "source_version", "parse_version", "config_revision",
                    "config_sha256", "worker_id", "lease_generation", "original_sha256"):
                self.assertEqual(receipt[field], command[field])
                self.assertEqual(bundle[field], command[field])
            self.assertEqual(receipt["command_sha256"], hashlib.sha256(path.read_bytes()).hexdigest())
            self.assertEqual(receipt["bundle_sha256"], hashlib.sha256(bundle_path.read_bytes()).hexdigest())
            self.assertEqual(bundle_path.parent, Path(command["output_directory"]))
            self.assertEqual(int(receipt["bundle_size_bytes"]), bundle_path.stat().st_size)
            self.assertEqual(receipt["node_count"], len(bundle["nodes"]))
            self.assertEqual(receipt["chunk_count"], len(bundle["chunks"]))
            self.assertGreater(receipt["node_count"], 175)
            bindings = DatasetBindings(SchemaCatalogue(ROOT))
            for ordinal, node in enumerate(bundle["nodes"]):
                self.assertEqual(node["ordinal"], ordinal)
                self.assertEqual(node["quote_hash"], hashlib.sha256(node["raw_text"].encode("utf-8")).hexdigest())
                bindings.bind(node["locator"], "locator")
                chunk = bundle["chunks"][ordinal]
                self.assertEqual(chunk["node_id"], node["node_id"])
                self.assertEqual(chunk["quote_hash"], node["quote_hash"])
                self.assertEqual(chunk["locator"], node["locator"])
                self.assertEqual(chunk["raw_text"], node["raw_text"])
            self.assertEqual(len({item["node_id"] for item in bundle["nodes"]}), receipt["node_count"])
            self.assertEqual(len({item["chunk_id"] for item in bundle["chunks"]}), receipt["chunk_count"])
            self.assertEqual(bundle["status"], "PARTIAL_FAILED" if bundle["issues"] else "COMPLETE")
            self.assertEqual(receipt["status"], bundle["status"])
            self.assertEqual({item["locator"]["address"]["table_index"] for item in bundle["nodes"]
                if item["locator"]["address"]["kind"] == "CELL"}, set(range(5)))
            scan = bundle["scan"]
            self.assertEqual(scan["returncode"], 0)
            self.assertEqual(scan["release_sha256"], command["scanner_release_sha256"])
            self.assertEqual(len(scan["artifacts"]), 4)
            for artifact in scan["artifacts"]:
                content = (bundle_path.parent / artifact["name"]).read_bytes()
                self.assertEqual(artifact["sha256"], hashlib.sha256(content).hexdigest())
                self.assertEqual(int(artifact["size_bytes"]), len(content))
            for item in (result, *bundle_path.parent.iterdir()):
                self.assertEqual(stat.S_IMODE(item.stat().st_mode), 0o600)
            self.assertEqual(hashlib.sha256(ORIGINAL.read_bytes()).hexdigest(), self.original_hash)

    def test_wrong_original_digest_never_produces_a_processing_receipt(self):
        with TemporaryDirectory(dir=WORK) as directory:
            command, path, result = self.prepare(directory)
            other = ROOT / "ui-delivery/assets/public-evidence/clinical-evaluation-2021.pdf"
            command["original_sha256"] = hashlib.sha256(other.read_bytes()).hexdigest()
            with self.assertRaisesRegex(ValueError, "SOURCE_CONTENT_MISMATCH"):
                self.execute(command, path, result)
            self.assertFalse(result.exists())
            self.assertEqual(list(Path(command["output_directory"]).iterdir()), [])

    def test_unknown_fields_and_stale_scanner_identity_are_rejected(self):
        with TemporaryDirectory(dir=WORK) as directory:
            command, path, result = self.prepare(directory)
            command["approval_status"] = "APPROVED"
            with self.assertRaises(ValueError):
                self.execute(command, path, result)
            del command["approval_status"]
            command["scanner_release_sha256"] = self.original_hash
            with self.assertRaisesRegex(ValueError, "SCANNER_RELEASE_CHANGED"):
                self.execute(command, path, result)
            self.assertFalse(result.exists())

    def test_processing_output_must_be_private_empty_and_inside_command_directory(self):
        with TemporaryDirectory(dir=WORK) as directory:
            command, path, result = self.prepare(directory)
            output = Path(command["output_directory"])
            output.chmod(0o755)
            with self.assertRaisesRegex(ValueError, "PROCESSING_DIRECTORY_INVALID"):
                self.execute(command, path, result)
            output.chmod(0o700)
            existing = output / "retained-original.docx"
            existing.write_bytes(ORIGINAL.read_bytes())
            with self.assertRaisesRegex(ValueError, "PROCESSING_OUTPUT_NOT_EMPTY"):
                self.execute(command, path, result)
            self.assertEqual(existing.read_bytes(), ORIGINAL.read_bytes())


if __name__ == "__main__":
    unittest.main()
