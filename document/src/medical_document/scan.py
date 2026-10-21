from dataclasses import dataclass
import hashlib
import os
from pathlib import Path
import stat
import subprocess
from tempfile import TemporaryDirectory
from typing import Annotated, Literal, Mapping

from pydantic import BaseModel, ConfigDict, Field, StringConstraints
import rfc8785


MAX_FILE_BYTES = 100 * 1024 * 1024
Hash = Annotated[str, StringConstraints(strict=True, pattern=r"^[0-9a-f]{64}$")]
PositiveSize = Annotated[int, Field(strict=True, gt=0)]


class ScannerArtifact(BaseModel):
    model_config = ConfigDict(extra="forbid", frozen=True, strict=True)
    path: str
    sha256: Hash
    size_bytes: PositiveSize


class SignedDatabase(BaseModel):
    model_config = ConfigDict(extra="forbid", frozen=True, strict=True)
    name: Literal["main", "daily", "bytecode"]
    database: ScannerArtifact
    signature: ScannerArtifact


class ScannerRelease(BaseModel):
    model_config = ConfigDict(extra="forbid", frozen=True, strict=True)
    schema_version: Literal["1.0.0"]
    version: Literal["1.5.4"]
    clamscan: ScannerArtifact
    sigtool: ScannerArtifact
    certificate: ScannerArtifact
    libraries: Annotated[tuple[ScannerArtifact, ...], Field(min_length=1)]
    databases: Annotated[tuple[SignedDatabase, ...], Field(min_length=3, max_length=3)]


@dataclass(frozen=True)
class NativeScanResult:
    original_sha256: str
    size_bytes: int
    release_sha256: str
    returncode: int
    output: bytes
    signature_outputs: tuple[bytes, ...]


class ClamAVScanner:
    def __init__(self, release: Mapping[str, object]):
        # 配置只能来自处理服务发布清单，文件请求不能提供扫描选项。
        self.release = ScannerRelease.model_validate_json(rfc8785.dumps(dict(release)))
        self.release_sha256 = hashlib.sha256(rfc8785.dumps(self.release.model_dump(mode="json"))).hexdigest()
        if tuple(item.name for item in self.release.databases) != ("main", "daily", "bytecode"):
            raise ValueError("SCANNER_DATABASE_SET_INVALID")
        self.artifacts = (self.release.clamscan, self.release.sigtool, self.release.certificate,
            *self.release.libraries, *(asset for database in self.release.databases
                for asset in (database.database, database.signature)))
        if len({asset.path for asset in self.artifacts}) != len(self.artifacts):
            raise ValueError("SCANNER_RELEASE_ASSETS_DUPLICATED")
        for database in self.release.databases:
            original = Path(database.database.path)
            signature = Path(database.signature.path)
            if original.name != database.name + ".cvd" or signature.parent != original.parent:
                raise ValueError("SCANNER_DATABASE_IDENTITY_INVALID")
        self.verify_release()

    def verify_release(self):
        for asset in self.artifacts:
            path = Path(asset.path)
            if not path.is_absolute() or path.resolve(strict=True) != path or not stat.S_ISREG(path.stat().st_mode):
                raise ValueError("SCANNER_RELEASE_PATH_INVALID")
            with path.open("rb") as source:
                if path.stat().st_size != asset.size_bytes or hashlib.file_digest(source, "sha256").hexdigest() != asset.sha256:
                    raise ValueError("SCANNER_RELEASE_CHANGED")

    def scan_command(self, command_path: Path, receipt_path: Path) -> None:
        from .scan_command import execute
        execute(command_path, receipt_path, self)

    def scan(self, content: bytes, original_sha256: str, suffix: str, work_directory: Path) -> NativeScanResult:
        if not content or len(content) > MAX_FILE_BYTES:
            raise ValueError("SOURCE_FILE_LIMIT")
        if hashlib.sha256(content).hexdigest() != original_sha256:
            raise ValueError("SOURCE_CONTENT_MISMATCH")
        directory = work_directory.absolute()
        attributes = directory.stat()
        if directory.resolve(strict=True) != directory or not stat.S_ISDIR(attributes.st_mode) \
                or attributes.st_uid != os.getuid() or stat.S_IMODE(attributes.st_mode) != 0o700:
            raise ValueError("SCANNER_WORK_DIRECTORY_INVALID")
        if suffix not in (".docx", ".pdf", ".xlsx", ".csv", ".txt", ".md", ".markdown", ".pptx", ".png", ".jpg", ".jpeg", ".tif", ".tiff", ".image", ".html", ".htm"):
            raise ValueError("SOURCE_FORMAT_UNSUPPORTED")
        self.verify_release()
        with TemporaryDirectory(prefix="source-scan-", dir=directory) as private:
            temporary = Path(private)
            snapshot = temporary / ("original" + suffix)
            descriptor = os.open(snapshot, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
            with os.fdopen(descriptor, "wb") as saved:
                saved.write(content)
                saved.flush()
                os.fsync(saved.fileno())
            environment = os.environ.copy()
            environment["TMPDIR"] = str(temporary)
            certificate_directory = str(Path(self.release.certificate.path).parent)
            signature_outputs = []
            for database in self.release.databases:
                verification = subprocess.run([self.release.sigtool.path, "--tempdir=" + str(temporary),
                    "--cvdcertsdir=" + certificate_directory, "--verify", database.database.path],
                    cwd=temporary, env=environment, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=60, check=True)
                signature_outputs.append(verification.stdout)
            command = [self.release.clamscan.path,
                *("--database=" + item.database.path for item in self.release.databases),
                "--cvdcertsdir=" + certificate_directory, "--tempdir=" + str(temporary),
                "--official-db-only=yes", "--alert-encrypted=yes", "--alert-macros=yes", "--alert-exceeds-max=yes",
                "--max-filesize=104857600", "--max-scansize=536870912", "--max-files=10000", "--max-scantime=120000",
                "--follow-file-symlinks=0", "--follow-dir-symlinks=0", str(snapshot)]
            outcome = subprocess.run(command, cwd=temporary, env=environment, stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT, timeout=180, check=False)
            if outcome.returncode == 1:
                raise ValueError("SOURCE_SCAN_REJECTED")
            if outcome.returncode != 0:
                raise subprocess.CalledProcessError(outcome.returncode, command, output=outcome.stdout)
            with snapshot.open("rb") as saved:
                if snapshot.stat().st_size != len(content) or hashlib.file_digest(saved, "sha256").hexdigest() != original_sha256:
                    raise ValueError("SOURCE_CONTENT_MISMATCH")
            self.verify_release()
            return NativeScanResult(original_sha256, len(content), self.release_sha256, outcome.returncode,
                outcome.stdout, tuple(signature_outputs))
