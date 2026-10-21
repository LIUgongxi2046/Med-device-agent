"""Prepare an OFFLINE OCR release from already-installed, approved local assets.

This command hashes files only: it never downloads, installs, launches engines,
changes processing rights, or publishes/enables a configuration.
"""
import argparse
import hashlib
from importlib.metadata import version
import os
from pathlib import Path
import rfc8785


def artifact(path):
    path = Path(path).absolute()
    if path.resolve(strict=True) != path or not path.is_file():
        raise ValueError("OCR_ASSET_PATH_INVALID")
    with path.open("rb") as stream:
        digest = hashlib.file_digest(stream, "sha256").hexdigest()
    return {"path": str(path), "sha256": digest}


def prepare(tesseract, artifacts, tessdata, languages, output):
    roots = [Path(artifacts).absolute(), Path(tessdata).absolute()]
    for root in roots:
        if not root.is_dir() or root.resolve(strict=True) != root:
            raise ValueError("OCR_ASSET_DIRECTORY_INVALID")
    if roots[0].is_relative_to(roots[1]) or roots[1].is_relative_to(roots[0]):
        raise ValueError("OCR_ASSET_DIRECTORIES_OVERLAP")
    release = {"schema_version": "1.0.0", "engine": "DOCLING_TESSERACT_LOCAL", "network_enabled": False,
        "docling_version": "2.73.1", "packages": {name: version(name) for name in
            ("docling", "docling-core", "docling-parse", "torch", "transformers", "pillow")},
        "tesseract": artifact(tesseract), "artifacts_directory": str(roots[0]), "tessdata_directory": str(roots[1]),
        "assets": [artifact(path) for root in roots for path in sorted(root.rglob("*")) if path.is_file()],
        "languages": languages, "timeout_seconds": 120}
    content = rfc8785.dumps(release)
    from medical_document.complex_parse import ComplexParser
    ComplexParser(content, hashlib.sha256(content).hexdigest())  # validates pins, never invokes conversion
    descriptor = os.open(output, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(descriptor, "wb") as stream:
        stream.write(content); stream.flush(); os.fsync(stream.fileno())
    return {"complex-release-path": str(Path(output).absolute()), "complex-release-sha256": hashlib.sha256(content).hexdigest(),
        "published": False, "ocr_execution": "NOT_RUN"}


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    for name in ("tesseract", "artifacts", "tessdata", "output"):
        parser.add_argument("--" + name, required=True)
    parser.add_argument("--language", action="append", required=True)
    args = parser.parse_args()
    print(rfc8785.dumps(prepare(args.tesseract, args.artifacts, args.tessdata, args.language, args.output)).decode())
