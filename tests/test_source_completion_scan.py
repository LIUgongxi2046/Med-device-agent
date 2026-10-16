import base64
import hashlib
import json
from pathlib import Path
import stat
from tempfile import TemporaryDirectory
import unittest
from uuid import uuid4
from zipfile import BadZipFile

import rfc8785

from medical_document.scan import ClamAVScanner
from medical_document.native import NativeParser
from test_source_scan_native import scanner_release


ROOT = Path(__file__).resolve().parents[1]
WORK = ROOT / ".implementation-work/source-completion-scan-tests"
ORIGINAL = ROOT / "ui-delivery/assets/public-evidence/clinical-evaluation-2021.docx"


class SourceCompletionScanTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        WORK.mkdir(mode=0o700, parents=True, exist_ok=True)
        WORK.chmod(0o700)
        cls.release = scanner_release()
        cls.scanner = ClamAVScanner(cls.release)
        cls.original = ORIGINAL.read_bytes()

    def prepare(self, directory):
        private = Path(directory)
        source = private / ORIGINAL.name
        source.write_bytes(self.original)
        source.chmod(0o600)
        release = private / "scanner-release.json"
        release.write_bytes(rfc8785.dumps(self.release))
        release.chmod(0o600)
        output = private / "output"
        output.mkdir(mode=0o700)
        command = {"schema_version": "1.0.0", "event_id": str(uuid4()), "operation_id": str(uuid4()),
            "upload_id": str(uuid4()), "worker_id": str(uuid4()), "lease_generation": "1",
            "source_path": str(source), "original_sha256": hashlib.sha256(self.original).hexdigest(),
            "size_bytes": str(len(self.original)), "scanner_release_path": str(release),
            "scanner_release_sha256": hashlib.sha256(release.read_bytes()).hexdigest(),
            "output_directory": str(output)}
        return command, private / "command.json", private / "receipt.json"

    def execute(self, command, path, receipt):
        path.write_bytes(rfc8785.dumps(command))
        path.chmod(0o600)
        self.assertTrue(callable(getattr(self.scanner, "scan_command", None)),
            "上传完成消费者需要保存固定原件扫描命令、官方签名输出和完整扫描回执")
        self.scanner.scan_command(path, receipt)

    def test_registered_original_retains_fixed_command_and_actual_scanner_outputs(self):
        with TemporaryDirectory(dir=WORK) as directory:
            command, path, receipt = self.prepare(directory)
            self.execute(command, path, receipt)
            result = json.loads(receipt.read_bytes())
            self.assertEqual(result["status"], "SCANNED")
            self.assertEqual(result["returncode"], 0)
            self.assertEqual(result["format"], "DOCX")
            self.assertIs(result["format_verified"], True)
            self.assertEqual(result["command_sha256"], hashlib.sha256(path.read_bytes()).hexdigest())
            for field in ("schema_version", "event_id", "operation_id", "upload_id", "worker_id",
                    "lease_generation", "original_sha256", "size_bytes", "scanner_release_sha256"):
                self.assertEqual(result[field], command[field])
            self.assertEqual([item["name"] for item in result["artifacts"]],
                ["scan.txt", "signature-0.txt", "signature-1.txt", "signature-2.txt"])
            for item in result["artifacts"]:
                content = (Path(command["output_directory"]) / item["name"]).read_bytes()
                self.assertEqual(content, base64.b64decode(item["content_base64"], validate=True))
                self.assertEqual(item["sha256"], hashlib.sha256(content).hexdigest())
                self.assertEqual(int(item["size_bytes"]), len(content))
            self.assertEqual(Path(command["source_path"]).read_bytes(), self.original)
            for item in (receipt, *Path(command["output_directory"]).iterdir()):
                self.assertEqual(stat.S_IMODE(item.stat().st_mode), 0o600)

    def test_other_actual_original_digest_and_changed_release_are_rejected(self):
        with TemporaryDirectory(dir=WORK) as directory:
            command, path, receipt = self.prepare(directory)
            command["original_sha256"] = hashlib.sha256(
                (ROOT / "ui-delivery/assets/public-evidence/clinical-evaluation-2021.pdf").read_bytes()).hexdigest()
            with self.assertRaisesRegex(ValueError, "SOURCE_CONTENT_MISMATCH"):
                self.execute(command, path, receipt)
            command["original_sha256"] = hashlib.sha256(self.original).hexdigest()
            command["scanner_release_sha256"] = command["original_sha256"]
            with self.assertRaisesRegex(ValueError, "SCANNER_RELEASE_CHANGED"):
                self.execute(command, path, receipt)
            self.assertFalse(receipt.exists())

    def test_private_original_and_empty_output_are_required(self):
        with TemporaryDirectory(dir=WORK) as directory:
            command, path, receipt = self.prepare(directory)
            source = Path(command["source_path"])
            source.chmod(0o644)
            with self.assertRaisesRegex(ValueError, "PROCESSING_FILE_INVALID"):
                self.execute(command, path, receipt)
            source.chmod(0o600)
            existing = Path(command["output_directory"]) / ORIGINAL.name
            existing.write_bytes(self.original)
            with self.assertRaisesRegex(ValueError, "COMPLETION_SCAN_OUTPUT_NOT_EMPTY"):
                self.execute(command, path, receipt)
            self.assertEqual(existing.read_bytes(), self.original)
            self.assertFalse(receipt.exists())

    def test_unknown_fields_and_existing_receipt_do_not_produce_scan_success(self):
        with TemporaryDirectory(dir=WORK) as directory:
            command, path, receipt = self.prepare(directory)
            command["approval_status"] = "APPROVED"
            with self.assertRaises(ValueError):
                self.execute(command, path, receipt)
            del command["approval_status"]
            receipt.write_bytes(ORIGINAL.read_bytes())
            with self.assertRaisesRegex(ValueError, "COMPLETION_SCAN_OUTPUT_SCOPE_INVALID"):
                self.execute(command, path, receipt)
            self.assertEqual(receipt.read_bytes(), self.original)

    def test_native_format_libraries_verify_registered_document_structures(self):
        pdf = (ROOT / "ui-delivery/assets/public-evidence/clinical-evaluation-2021.pdf").read_bytes()
        self.assertEqual(NativeParser.verify_format(self.original, ".docx"), "DOCX")
        self.assertEqual(NativeParser.verify_format(pdf, ".pdf"), "PDF")
        with self.assertRaises(BadZipFile):
            NativeParser.verify_format(pdf, ".docx")


if __name__ == "__main__":
    unittest.main()
