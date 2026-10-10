import copy
import hashlib
import json
import os
from pathlib import Path
import unittest

from medical_contracts.catalogue import SchemaCatalogue
from medical_document import NativeParser, SourceInput


ROOT = Path(__file__).resolve().parents[1]
WORK = ROOT / ".implementation-work/source-scan-parser-tests"
ORIGINAL = ROOT / "ui-delivery/assets/public-evidence/clinical-evaluation-2021.docx"
PREFIX = ROOT / ".implementation-work/toolchains/clamav-1.5.4-native"
DATABASE = ROOT / ".implementation-work/runtime/clamav/database"
CERTIFICATE = ROOT / ".implementation-work/runtime/clamav/config/certs/clamav.crt"
RECEIPT = ROOT / ".implementation-work/IC-08-SOURCE-SCAN-20261008-OFFICIAL-01/receipt.json"


def scanner_release():
    receipt = json.loads(RECEIPT.read_bytes())
    if receipt["exit_code"] != 0 or receipt["inputs_unchanged"] is not True:
        raise ValueError("SCANNER_ORIGINAL_RELEASE_NOT_VERIFIED")
    assets = {item["path"]: item for item in receipt["inputs"]}

    def artifact(path):
        item = assets[str(path.resolve(strict=True))]
        return {"path": item["path"], "sha256": item["sha256"], "size_bytes": path.stat().st_size}

    libraries = sorted({path.resolve(strict=True) for path in (PREFIX / "lib").glob("*.dylib")})
    signatures = json.loads((RECEIPT.parent / "scan-result.json").read_bytes())["databases"]
    return {"schema_version": "1.0.0", "version": "1.5.4",
        "clamscan": artifact(PREFIX / "bin/clamscan"), "sigtool": artifact(PREFIX / "bin/sigtool"),
        "certificate": artifact(CERTIFICATE), "libraries": [artifact(path) for path in libraries],
        "databases": [{"name": name, "database": artifact(DATABASE / (name + ".cvd")),
            "signature": artifact(Path(next(item["path"] for item in signatures
                if Path(item["path"]).name.startswith(name + "-") and Path(item["path"]).suffix == ".sign")))}
            for name in ("main", "daily", "bytecode")]}


class SourceScanNativeTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        WORK.mkdir(parents=True, exist_ok=True, mode=0o700)
        WORK.chmod(0o700)
        cls.parser = NativeParser(SchemaCatalogue(ROOT))
        cls.release = scanner_release()
        cls.request = SourceInput(ORIGINAL, "DOCX", "31dd0d5d-060a-4ed8-a56a-e6ad9617c60d",
            "d6f4cc72-8db7-4e80-a13f-119cc7cbe59b", hashlib.sha256(ORIGINAL.read_bytes()).hexdigest())

    def test_original_scan_is_required_before_native_parse_and_preserves_both_results(self):
        self.assertTrue(callable(getattr(self.parser, "scan_and_parse", None)),
            "C09需要通过正式扫描以后调用原件解析")
        before = set(WORK.iterdir())
        result = self.parser.scan_and_parse(self.request, self.release, WORK)
        self.assertEqual(result.scan.original_sha256, self.request.sha256)
        self.assertEqual(result.scan.size_bytes, ORIGINAL.stat().st_size)
        self.assertEqual(result.scan.returncode, 0)
        self.assertEqual(len(result.scan.release_sha256), 64)
        self.assertIn(b"Infected files: 0", result.scan.output)
        self.assertEqual(len(result.scan.signature_outputs), 3)
        for output in result.scan.signature_outputs:
            self.assertIn(b"Successfully verified", output)
            self.assertIn(b"ClamAV_datafiles_release", output)
        self.assertEqual(result.document.original_sha256, self.request.sha256)
        self.assertEqual(result.document.source_version, self.request.source_version)
        self.assertEqual(result.document.parse_version, self.request.parse_version)
        self.assertGreater(len(result.document.nodes), 175)
        self.assertEqual(result.document.processor_manifest["docling_pipeline"], "SimplePipeline")
        self.assertEqual({node.locator.address.table_index for node in result.document.nodes
            if node.locator.address.kind == "CELL"}, set(range(5)))
        self.assertEqual(set(WORK.iterdir()), before)
        type(self).actual_scan = result.scan

    def test_actual_other_source_hash_is_rejected_before_scanning(self):
        other = ROOT / "ui-delivery/assets/public-evidence/clinical-evaluation-2021.pdf"
        request = SourceInput(ORIGINAL, "DOCX", self.request.source_version, self.request.parse_version,
            hashlib.sha256(other.read_bytes()).hexdigest())
        before = set(WORK.iterdir())
        with self.assertRaisesRegex(ValueError, "SOURCE_CONTENT_MISMATCH"):
            self.parser.scan_and_parse(request, self.release, WORK)
        self.assertEqual(set(WORK.iterdir()), before)

    def test_native_release_hash_must_match_the_registered_binary(self):
        release = copy.deepcopy(self.release)
        release["clamscan"]["sha256"] = release["sigtool"]["sha256"]
        before = set(WORK.iterdir())
        with self.assertRaisesRegex(ValueError, "SCANNER_RELEASE_CHANGED"):
            self.parser.scan_and_parse(self.request, release, WORK)
        self.assertEqual(set(WORK.iterdir()), before)

    def test_scan_configuration_does_not_accept_client_approval_or_disable_options(self):
        for field, value in (("approval_status", "APPROVED"), ("official_db_only", False), ("fallback", True)):
            release = {**self.release, field: value}
            with self.subTest(field=field), self.assertRaises(ValueError):
                self.parser.scan_and_parse(self.request, release, WORK)

    def test_scanner_rejects_shared_work_directory_before_execution(self):
        directory = WORK / "shared-permissions"
        directory.mkdir(mode=0o755)
        directory.chmod(0o755)
        try:
            with self.assertRaisesRegex(ValueError, "SCANNER_WORK_DIRECTORY_INVALID"):
                self.parser.scan_and_parse(self.request, self.release, directory)
            self.assertEqual(list(directory.iterdir()), [])
        finally:
            directory.rmdir()


if __name__ == "__main__":
    unittest.main()
