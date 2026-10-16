import base64
import hashlib
import json
from pathlib import Path
import sys
from typing import Literal

from pydantic import BaseModel, ConfigDict, field_validator
import rfc8785

from .processing_command import Identifier, ProcessingCommand, U64, private_directory, private_file, save
from .native import NativeParser
from .scan import ClamAVScanner, Hash, MAX_FILE_BYTES


class SourceScanCommand(BaseModel):
    model_config = ConfigDict(extra="forbid", frozen=True, strict=True)
    schema_version: Literal["1.0.0"]
    event_id: Identifier
    operation_id: Identifier
    upload_id: Identifier
    worker_id: Identifier
    lease_generation: U64
    source_path: str
    original_sha256: Hash
    size_bytes: U64
    scanner_release_path: str
    scanner_release_sha256: Hash
    output_directory: str

    @field_validator("event_id", "operation_id", "upload_id", "worker_id")
    @classmethod
    def identifier(cls, value):
        return ProcessingCommand.identifier(value)

    @field_validator("lease_generation", "size_bytes")
    @classmethod
    def unsigned(cls, value):
        return ProcessingCommand.unsigned(value)


def execute(command_path: Path, receipt_path: Path, scanner: ClamAVScanner | None = None) -> None:
    command_path = private_file(command_path.absolute())
    work = private_directory(command_path.parent)
    command_bytes = command_path.read_bytes()
    if len(command_bytes) > 65536:
        raise ValueError("COMPLETION_SCAN_COMMAND_LIMIT")
    command = SourceScanCommand.model_validate_json(command_bytes)
    if rfc8785.dumps(command.model_dump(mode="json")) != command_bytes:
        raise ValueError("COMPLETION_SCAN_COMMAND_NOT_CANONICAL")
    output = private_directory(Path(command.output_directory))
    source = private_file(Path(command.source_path))
    receipt_path = receipt_path.absolute()
    if source.parent != work or output.parent != work or receipt_path.parent != work \
            or receipt_path.exists() or receipt_path.is_symlink() or receipt_path in (source, command_path):
        raise ValueError("COMPLETION_SCAN_OUTPUT_SCOPE_INVALID")
    if any(output.iterdir()):
        raise ValueError("COMPLETION_SCAN_OUTPUT_NOT_EMPTY")
    if source.stat().st_size != int(command.size_bytes) or not 0 < int(command.size_bytes) <= MAX_FILE_BYTES:
        raise ValueError("SOURCE_FILE_LIMIT")
    original = source.read_bytes()
    if hashlib.sha256(original).hexdigest() != command.original_sha256:
        raise ValueError("SOURCE_CONTENT_MISMATCH")
    release_path = private_file(Path(command.scanner_release_path))
    release_bytes = release_path.read_bytes()
    if len(release_bytes) > 262144 or hashlib.sha256(release_bytes).hexdigest() != command.scanner_release_sha256:
        raise ValueError("SCANNER_RELEASE_CHANGED")
    if scanner is None:
        scanner = ClamAVScanner(json.loads(release_bytes))
    if scanner.release_sha256 != command.scanner_release_sha256:
        raise ValueError("SCANNER_RELEASE_CHANGED")
    result = scanner.scan(original, command.original_sha256, source.suffix, work)
    if result.original_sha256 != command.original_sha256 or result.size_bytes != len(original) \
            or result.release_sha256 != command.scanner_release_sha256 or result.returncode != 0 \
            or len(result.signature_outputs) != 3:
        raise ValueError("COMPLETION_SCAN_RESULT_MISMATCH")
    format_name = NativeParser.verify_format(original, source.suffix)
    outputs = [("scan.txt", result.output), *[("signature-" + str(index) + ".txt", value)
        for index, value in enumerate(result.signature_outputs)]]
    if any(not value for _, value in outputs) or sum(len(value) for _, value in outputs) > 262144:
        raise ValueError("COMPLETION_SCAN_ARTIFACT_LIMIT")
    if command_path.read_bytes() != command_bytes or release_path.read_bytes() != release_bytes \
            or source.read_bytes() != original:
        raise ValueError("COMPLETION_SCAN_INPUT_CHANGED")
    scanner.verify_release()
    artifacts = []
    for name, content in outputs:
        save(output / name, content)
        artifacts.append({"name": name, "sha256": hashlib.sha256(content).hexdigest(),
            "size_bytes": str(len(content)), "content_base64": base64.b64encode(content).decode("ascii")})
    identity = {field: getattr(command, field) for field in ("schema_version", "event_id", "operation_id",
        "upload_id", "worker_id", "lease_generation", "original_sha256", "size_bytes", "scanner_release_sha256")}
    receipt = {**identity, "command_sha256": hashlib.sha256(command_bytes).hexdigest(), "status": "SCANNED",
        "returncode": result.returncode, "format": format_name, "format_verified": True, "artifacts": artifacts}
    save(receipt_path, rfc8785.dumps(receipt))


if __name__ == "__main__":
    if len(sys.argv) != 3:
        raise ValueError("COMPLETION_SCAN_ARGUMENTS_INVALID")
    execute(Path(sys.argv[1]), Path(sys.argv[2]))
