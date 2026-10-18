import argparse
import hashlib
import importlib.metadata
from importlib.resources import files
import json
import os
from pathlib import Path
import subprocess
import sys
import unittest


ROOT = Path(__file__).resolve().parents[1]
CAPTURE = Path("/Users/liuhaoxian/.agents/skills/haonan-s000-pmp/scripts/capture_check.py")
PACKAGES = ("medical-document", "docling", "docling-parse", "python-docx", "pypdf", "markdown-it-py", "openpyxl", "pandas", "python-pptx", "medical-contracts")


def main():
    parser = argparse.ArgumentParser(description="核验 C09 原生解析与七格式原文位置")
    parser.add_argument("--execution")
    parser.add_argument("--run", type=Path)
    parser.add_argument("--installed", action="store_true")
    args = parser.parse_args()
    if args.run is not None:
        output = args.run.resolve(strict=True)
        package_files = {}
        if args.installed:
            package = files("medical_document")
            for source in sorted((ROOT / "document/src/medical_document").glob("*.py")):
                content = package.joinpath(source.name).read_bytes()
                if content != source.read_bytes():
                    raise ValueError("DOCUMENT_PACKAGE_SOURCE_MISMATCH")
                package_files[source.name] = hashlib.sha256(content).hexdigest()
        suite = unittest.defaultTestLoader.discover(str(ROOT / "tests"), pattern="test_document_native.py")
        result = unittest.TextTestRunner(verbosity=2).run(suite)
        evidence = {"tests_run": result.testsRun, "failures": len(result.failures), "errors": len(result.errors),
            "skipped": len(result.skipped), "successful": result.wasSuccessful(),
            "processors": {name: importlib.metadata.version(name) for name in PACKAGES},
            "customer_success_verified": False, "simulation_formats": ["CSV", "XLSX", "PPTX"]}
        evidence["installed_package_verified"] = args.installed
        evidence["package_files"] = package_files
        evidence["source_sha256"] = {str(path.relative_to(ROOT)): hashlib.sha256(path.read_bytes()).hexdigest()
            for path in sorted((ROOT / ".implementation-work/document-tests").glob("simulation-*"))}
        (output / "document-result.json").write_text(json.dumps(evidence, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        raise SystemExit(0 if result.wasSuccessful() else 1)
    if not args.execution or Path(args.execution).name != args.execution or args.execution in (".", ".."):
        raise ValueError("执行身份必须使用单一目录名称")
    output = ROOT / ".implementation-work" / ("IC-08-NATIVE-" + args.execution)
    temporary = ROOT / ".implementation-work/document-tmp"; temporary.mkdir(parents=True, exist_ok=True)
    environment = os.environ.copy()
    environment.update({"PYTHONPATH": "" if args.installed else str(ROOT / "document/src"), "TMPDIR": str(temporary),
        "HF_HUB_OFFLINE": "1", "TRANSFORMERS_OFFLINE": "1", "HF_HOME": str(ROOT / ".implementation-work/document-model-cache")})
    inputs = ["scripts/check_document_native.py", "tests/test_document_native.py", "document/pyproject.toml", "contracts/catalogue.json", "contracts/extensions/evaluation-results.catalogue.json", "business/medical-persistence/src/main/resources/evaluation-results.schema.json",
        "data-design/sample_dataset.json", "PRD.md", "HLD.md", "LLD-数据.md",
        "ui-delivery/assets/public-evidence/clinical-evaluation-2021.docx",
        "ui-delivery/assets/public-evidence/clinical-evaluation-2021.pdf",
        "ui-delivery/research/official/registration-format-2021-121.txt"]
    for directory in ("document/src", "contracts/python/src"):
        inputs.extend(str(path.relative_to(ROOT)) for path in sorted((ROOT / directory).rglob("*.py")))
    for directory in ("contracts", "data-design", "backend-design", "agent-design", "frontend-design"):
        inputs.extend(str(path.relative_to(ROOT)) for path in sorted((ROOT / directory).glob("*.schema.json")))
    command = [sys.executable, str(CAPTURE), "--output-dir", str(output), "--check-id", "IC-08-NATIVE",
        "--kind", "INTEGRATION", "--baseline-id", "DOCUMENT-NATIVE-PARSE-01", "--cwd", str(ROOT), "--timeout", "300"]
    for filename in inputs:
        command.extend(["--input", filename])
    command.extend(["--artifact", str(output / "document-result.json"), "--", sys.executable, str(Path(__file__).resolve()), "--run", str(output)])
    if args.installed:
        command.append("--installed")
    raise SystemExit(subprocess.run(command, cwd=ROOT, env=environment).returncode)


if __name__ == "__main__":
    main()
