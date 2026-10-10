import argparse
import hashlib
from importlib.resources import files
import json
import os
from pathlib import Path
import subprocess
import sys
import unittest


ROOT = Path(__file__).resolve().parents[1]
CAPTURE = Path("/Users/liuhaoxian/.agents/skills/haonan-s000-pmp/scripts/capture_check.py")


def main():
    parser = argparse.ArgumentParser(description="核验C09正式扫描与原件解析的实际调用")
    parser.add_argument("--execution")
    parser.add_argument("--run", type=Path)
    parser.add_argument("--installed", action="store_true")
    args = parser.parse_args()
    if args.run is not None:
        directory = args.run.resolve(strict=True)
        package_files = {}
        if args.installed:
            package = files("medical_document")
            for source in sorted((ROOT / "document/src/medical_document").glob("*.py")):
                content = package.joinpath(source.name).read_bytes()
                if content != source.read_bytes():
                    raise ValueError("DOCUMENT_PACKAGE_SOURCE_MISMATCH")
                package_files[source.name] = hashlib.sha256(content).hexdigest()
        suite = unittest.defaultTestLoader.discover(str(ROOT / "tests"), pattern="test_source_scan_native.py")
        result = unittest.TextTestRunner(verbosity=2).run(suite)
        evidence = {"tests_run": result.testsRun, "failures": len(result.failures), "errors": len(result.errors),
            "skipped": len(result.skipped), "successful": result.wasSuccessful(),
            "installed_package_verified": args.installed, "package_files": package_files,
            "source_upload_complete": False, "processing_consumer_complete": False,
            "malicious_upload_rejection_verified": False, "professional_approval_created": False,
            "customer_success_verified": False}
        if result.wasSuccessful():
            sys.path.insert(0, str(ROOT / "tests"))
            from test_source_scan_native import SourceScanNativeTest
            scan = SourceScanNativeTest.actual_scan
            (directory / "original-scan.txt").write_bytes(scan.output)
            for index, output in enumerate(scan.signature_outputs):
                (directory / ("signature-" + str(index) + ".txt")).write_bytes(output)
            evidence.update({"original_sha256": scan.original_sha256, "size_bytes": scan.size_bytes,
                "release_sha256": scan.release_sha256, "actual_scan_returncode": scan.returncode,
                "native_scan_and_parse_verified": True,
                "native_outputs": [{"path": str(path), "sha256": hashlib.sha256(path.read_bytes()).hexdigest()}
                    for path in sorted(directory.glob("*.txt"))]})
        (directory / "scan-parser-result.json").write_text(json.dumps(evidence, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        raise SystemExit(0 if result.wasSuccessful() else 1)
    if not args.execution or Path(args.execution).name != args.execution or args.execution in (".", ".."):
        raise ValueError("SCAN_PARSER_EXECUTION_INVALID")
    directory = ROOT / (".implementation-work/IC-08-SOURCE-PROCESSING-" + args.execution)
    receipt_path = ROOT / ".implementation-work/IC-08-SOURCE-SCAN-20261008-OFFICIAL-01/receipt.json"
    receipt = json.loads(receipt_path.read_bytes())
    inputs = {str(receipt_path), str(receipt_path.parent / "scan-result.json"), str(Path(__file__)),
        "tests/test_source_scan_native.py", "document/pyproject.toml", "PRD.md", "HLD.md", "LLD-数据.md",
        "data-design/sample_dataset.json", "contracts/catalogue.json",
        "ui-delivery/assets/public-evidence/clinical-evaluation-2021.docx",
        "ui-delivery/assets/public-evidence/clinical-evaluation-2021.pdf"}
    inputs.update(item["path"] for item in receipt["inputs"])
    for folder in ("document/src", "contracts/python/src"):
        inputs.update(str(path) for path in (ROOT / folder).rglob("*.py"))
    for folder in ("contracts", "data-design", "backend-design", "agent-design", "frontend-design"):
        inputs.update(str(path) for path in (ROOT / folder).glob("*.schema.json"))
    temporary = ROOT / ".implementation-work/source-scan-parser-temp"
    temporary.mkdir(parents=True, exist_ok=True, mode=0o700)
    temporary.chmod(0o700)
    environment = os.environ.copy()
    environment.update({"TMPDIR": str(temporary), "PYTHONPATH": "" if args.installed else str(ROOT / "document/src"),
        "HF_HUB_OFFLINE": "1", "TRANSFORMERS_OFFLINE": "1", "HF_HOME": str(ROOT / ".implementation-work/document-model-cache")})
    command = [sys.executable, str(CAPTURE), "--output-dir", str(directory), "--check-id", "IC-08-SOURCE-PROCESSING",
        "--kind", "INTEGRATION", "--baseline-id", "SOURCE-NATIVE-SCAN-PARSER-01", "--cwd", str(ROOT), "--timeout", "360"]
    for path in sorted(inputs):
        command.extend(["--input", path])
    command.extend(["--artifact", str(directory / "scan-parser-result.json"), "--", sys.executable,
        str(Path(__file__)), "--run", str(directory)])
    if args.installed:
        command.append("--installed")
    raise SystemExit(subprocess.run(command, cwd=ROOT, env=environment).returncode)


if __name__ == "__main__":
    main()
