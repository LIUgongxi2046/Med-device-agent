import base64
import hashlib
import json
import os
from pathlib import Path
import stat
import sys
from typing import Annotated, Literal
from uuid import UUID, uuid5

from pydantic import BaseModel, ConfigDict, StringConstraints, field_validator
import rfc8785

from medical_contracts.catalogue import SchemaCatalogue
from .native import NativeParser, SourceInput
from .scan import ClamAVScanner, Hash, MAX_FILE_BYTES


U64 = Annotated[str, StringConstraints(strict=True, pattern=r"^[1-9][0-9]{0,19}$")]
Identifier = Annotated[str, StringConstraints(strict=True, min_length=36, max_length=36)]
MAX_BUNDLE_BYTES = 256 * 1024 * 1024


class ProcessingCommand(BaseModel):
    model_config = ConfigDict(extra="forbid", frozen=True, strict=True)
    schema_version: Literal["1.0.0"]
    attempt_id: Identifier
    run_id: Identifier
    source_version: Identifier
    parse_version: Identifier
    config_revision: Identifier
    config_sha256: Hash
    worker_id: Identifier
    lease_generation: U64
    format: Literal["DOCX", "PDF", "XLSX", "CSV", "TXT", "MARKDOWN", "PPTX", "IMAGE", "HTML"]
    html_parser_revision: Literal["HTML-STATIC-TEXT-1"] | None = None
    original_sha256: Hash
    size_bytes: U64
    source_path: str
    catalogue_root: str
    scanner_release_path: str
    scanner_release_sha256: Hash
    output_directory: str
    complex_release_path: str | None = None
    complex_release_sha256: Hash | None = None

    @field_validator("attempt_id", "run_id", "source_version", "parse_version", "config_revision", "worker_id")
    @classmethod
    def identifier(cls, value):
        identifier = UUID(value)
        if str(identifier) != value or identifier.version not in (4, 5) or identifier.variant != "specified in RFC 4122":
            raise ValueError("PROCESSING_IDENTITY_INVALID")
        return value

    @field_validator("lease_generation", "size_bytes")
    @classmethod
    def unsigned(cls, value):
        if int(value) > 18446744073709551615:
            raise ValueError("PROCESSING_U64_INVALID")
        return value


def private_directory(path: Path) -> Path:
    attributes = path.stat()
    if not path.is_absolute() or path.resolve(strict=True) != path or not stat.S_ISDIR(attributes.st_mode) \
            or attributes.st_uid != os.getuid() or stat.S_IMODE(attributes.st_mode) != 0o700:
        raise ValueError("PROCESSING_DIRECTORY_INVALID")
    return path


def private_file(path: Path) -> Path:
    attributes = path.stat()
    if not path.is_absolute() or path.resolve(strict=True) != path or not stat.S_ISREG(attributes.st_mode) \
            or attributes.st_uid != os.getuid() or stat.S_IMODE(attributes.st_mode) != 0o600:
        raise ValueError("PROCESSING_FILE_INVALID")
    return path


def save(path: Path, content: bytes) -> None:
    descriptor = os.open(path, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
    with os.fdopen(descriptor, "wb") as stream:
        stream.write(content)
        stream.flush()
        os.fsync(stream.fileno())


def execute(command_path: Path, result_path: Path, parser: NativeParser | None = None) -> None:
    command_path = private_file(command_path.absolute())
    private_directory(command_path.parent)
    original_command = command_path.read_bytes()
    if len(original_command) > 65536:
        raise ValueError("PROCESSING_COMMAND_LIMIT")
    command = ProcessingCommand.model_validate_json(original_command)
    # 命令采用唯一规范表示，重复字段及非规范字节在执行以前拒绝。
    if rfc8785.dumps(command.model_dump(mode="json", exclude_unset=True)) != original_command:
        raise ValueError("PROCESSING_COMMAND_NOT_CANONICAL")
    output = private_directory(Path(command.output_directory))
    result_path = result_path.absolute()
    if output.parent != command_path.parent or result_path.parent != command_path.parent \
            or result_path.exists() or result_path.is_symlink() or result_path == command_path:
        raise ValueError("PROCESSING_OUTPUT_SCOPE_INVALID")
    if any(output.iterdir()):
        raise ValueError("PROCESSING_OUTPUT_NOT_EMPTY")
    source = Path(command.source_path)
    if not source.is_absolute() or source.resolve(strict=True) != source or not source.is_file():
        raise ValueError("PROCESSING_SOURCE_PATH_INVALID")
    if source.stat().st_size != int(command.size_bytes) or not 0 < int(command.size_bytes) <= MAX_FILE_BYTES:
        raise ValueError("SOURCE_FILE_LIMIT")
    release_path = private_file(Path(command.scanner_release_path))
    release_bytes = release_path.read_bytes()
    if len(release_bytes) > 262144 or hashlib.sha256(release_bytes).hexdigest() != command.scanner_release_sha256:
        raise ValueError("SCANNER_RELEASE_CHANGED")
    scanner = ClamAVScanner(json.loads(release_bytes))
    if scanner.release_sha256 != command.scanner_release_sha256:
        raise ValueError("SCANNER_RELEASE_CHANGED")
    catalogue = SchemaCatalogue(Path(command.catalogue_root).resolve(strict=True))
    complex_parser = None
    if (command.complex_release_path is None) != (command.complex_release_sha256 is None):
        raise ValueError("OCR_RELEASE_BINDING_INVALID")
    if command.complex_release_path is not None:
        from .complex_parse import ComplexParser
        complex_bytes = private_file(Path(command.complex_release_path)).read_bytes()
        if len(complex_bytes) > 2097152:
            raise ValueError("OCR_RELEASE_LIMIT")
        complex_parser = ComplexParser(complex_bytes, command.complex_release_sha256)
    if parser is None:
        parser = NativeParser(catalogue, complex_parser)
    elif getattr(parser.complex_parser, "release_sha256", None) != command.complex_release_sha256:
        raise ValueError("OCR_RELEASE_BINDING_INVALID")
    if parser.bindings.catalogue.digests != catalogue.digests:
        raise ValueError("PROCESSING_CATALOGUE_CHANGED")
    if (command.format == "HTML") != (command.html_parser_revision == "HTML-STATIC-TEXT-1"):
        raise ValueError("HTML_PARSER_BINDING_INVALID")
    request = SourceInput(source, command.format, command.source_version, command.parse_version,
        command.original_sha256, data_kind="REGISTERED", page_basis="ORIGINAL")
    scanned = parser.scan_and_parse(request, scanner.release.model_dump(mode="json"), command_path.parent)
    document = scanned.document
    if not document.nodes:
        raise ValueError("PROCESSING_CONTENT_EMPTY")
    identity = {field: getattr(command, field) for field in ("schema_version", "attempt_id", "run_id", "source_version",
        "parse_version", "config_revision", "config_sha256", "worker_id", "lease_generation", "original_sha256")}
    identity["command_sha256"] = hashlib.sha256(original_command).hexdigest()
    nodes = [{"node_id": node.node_id, "ordinal": ordinal, "node_kind": "TEXT", "raw_text": node.raw_text,
        "quote_hash": node.quote_hash, "heading_path": list(node.heading_path),
        "locator": node.locator.model_dump(mode="json")} for ordinal, node in enumerate(document.nodes)]
    chunks = [{"chunk_id": str(uuid5(UUID(node["node_id"]), "source-chunk:1.0.0")),
        "node_id": node["node_id"], "ordinal": node["ordinal"], "raw_text": node["raw_text"],
        "search_text": node["raw_text"], "quote_hash": node["quote_hash"], "locator": node["locator"]}
        for node in nodes]
    issues = [{"code": issue.code, "locator": None if issue.locator is None else issue.locator.model_dump(mode="json")}
        for issue in document.issues]
    if document.status != ("PARTIAL_FAILED" if issues else "COMPLETE"):
        raise ValueError("PROCESSING_STATUS_MISMATCH")
    outputs = [("scan.txt", scanned.scan.output), *[("signature-" + str(index) + ".txt", value)
        for index, value in enumerate(scanned.scan.signature_outputs)]]
    artifacts = [{"name": name, "sha256": hashlib.sha256(value).hexdigest(), "size_bytes": str(len(value)),
        "content_base64": base64.b64encode(value).decode("ascii")}
        for name, value in outputs]
    processor = dict(document.processor_manifest)
    processor.update({"config_revision": command.config_revision, "config_sha256": command.config_sha256,
        "scanner_release_sha256": scanner.release_sha256, "chunking_revision": "SOURCE-NODE-CHUNKS-1"})
    bundle = {**identity, "status": document.status, "format": command.format,
        "processor_manifest": processor, "nodes": nodes, "chunks": chunks, "issues": issues,
        "scan": {"original_sha256": scanned.scan.original_sha256, "size_bytes": str(scanned.scan.size_bytes),
            "release_sha256": scanner.release_sha256, "returncode": scanned.scan.returncode, "artifacts": artifacts}}
    bundle_bytes = rfc8785.dumps(bundle)
    if len(bundle_bytes) > MAX_BUNDLE_BYTES:
        raise ValueError("PROCESSING_BUNDLE_LIMIT")
    if command_path.read_bytes() != original_command or release_path.read_bytes() != release_bytes:
        raise ValueError("PROCESSING_INPUT_CHANGED")
    bundle_path = output / "parsed.json"
    for name, value in outputs:
        save(output / name, value)
    save(bundle_path, bundle_bytes)
    receipt = {**identity, "status": document.status, "bundle_path": str(bundle_path),
        "bundle_sha256": hashlib.sha256(bundle_bytes).hexdigest(), "bundle_size_bytes": str(len(bundle_bytes)),
        "node_count": len(nodes), "chunk_count": len(chunks), "issue_count": len(issues)}
    save(result_path, rfc8785.dumps(receipt))


if __name__ == "__main__":
    if len(sys.argv) != 3:
        raise ValueError("PROCESSING_COMMAND_ARGUMENTS_INVALID")
    execute(Path(sys.argv[1]), Path(sys.argv[2]))
