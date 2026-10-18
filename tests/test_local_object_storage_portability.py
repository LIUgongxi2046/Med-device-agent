"""Offline installer/provenance and process-identity tests; no service is started."""

from contextlib import ExitStack
import hashlib
import io
import json
import os
from pathlib import Path
import subprocess
import sys
import tarfile
import tempfile
import unittest
from unittest.mock import patch


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
import local_object_storage as storage


class OfficialPlatformTests(unittest.TestCase):
    def test_original_darwin_pin_is_preserved(self):
        with patch.object(storage.platform, "system", return_value="Darwin"), \
                patch.object(storage.platform, "machine", return_value="x86_64"):
            release = storage.official_archive()
        self.assertEqual(release["name"], "darwin_amd64.tar.gz")
        self.assertEqual(release["sha256"],
            "425ef61d2ee773d5d5175003f38bd6cc891070ae6319370354aaf5378c45d5b3")
        self.assertTrue(release["source"].endswith("/595824418"))

    def test_linux_pin_matches_official_release_asset(self):
        with patch.object(storage.platform, "system", return_value="Linux"), \
                patch.object(storage.platform, "machine", return_value="x86_64"):
            release = storage.official_archive()
        self.assertEqual(release["name"], "linux_amd64.tar.gz")
        self.assertEqual(release["sha256"],
            "4a7d108384d044d95212d1342cdda9533fa55842c1c9b41f606ca3c8a9561124")
        self.assertTrue(release["source"].endswith("/595905333"))

    def test_unsupported_platforms_fail_closed(self):
        for system, architecture in (("Windows", "AMD64"), ("Linux", "aarch64"), ("Darwin", "arm64")):
            with self.subTest(system=system, architecture=architecture), \
                    patch.object(storage.platform, "system", return_value=system), \
                    patch.object(storage.platform, "machine", return_value=architecture):
                with self.assertRaisesRegex(ValueError, "PLATFORM_UNSUPPORTED"):
                    storage.official_archive()

    def test_environment_cannot_override_pinned_source_or_hash(self):
        with patch.dict(os.environ, {"OBJECT_STORAGE_ARCHIVE_SHA256": "0" * 64,
                "OBJECT_STORAGE_ARCHIVE": "/tmp/arbitrary-archive", "MEDICAL_SEAWEEDFS_BIN": "/tmp/weed"}), \
                patch.object(storage.platform, "system", return_value="Linux"), \
                patch.object(storage.platform, "machine", return_value="x86_64"):
            self.assertEqual(storage.official_archive()["sha256"],
                "4a7d108384d044d95212d1342cdda9533fa55842c1c9b41f606ca3c8a9561124")


class InstallerTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.directory = Path(self.temporary.name)
        self.stack = ExitStack()
        self.addCleanup(self.stack.close)
        self.stack.enter_context(patch.object(storage, "TOOLCHAIN", self.directory))
        self.stack.enter_context(patch.object(storage.platform, "system", return_value="Linux"))
        self.stack.enter_context(patch.object(storage.platform, "machine", return_value="x86_64"))
        self.run = self.stack.enter_context(patch.object(storage.subprocess, "run",
            return_value=subprocess.CompletedProcess([], 0, "version 30GB 4.48 abcdef\n", "")))
        license_bytes = b"offline fixture license"
        (self.directory / "LICENSE").write_bytes(license_bytes)
        self.stack.enter_context(patch.object(storage, "LICENSE_SHA256", hashlib.sha256(license_bytes).hexdigest()))

    def archive(self, entries=(("weed", tarfile.REGTYPE),), system="Linux"):
        name = "linux_amd64.tar.gz" if system == "Linux" else "darwin_amd64.tar.gz"
        archive = self.directory / name
        with tarfile.open(archive, "w:gz") as output:
            for filename, kind in entries:
                member = tarfile.TarInfo(filename)
                member.type = kind
                content = b"offline fixture binary"
                if kind == tarfile.REGTYPE:
                    member.size = len(content)
                    output.addfile(member, io.BytesIO(content))
                else:
                    member.linkname = "elsewhere"
                    output.addfile(member)
        release = {**storage.OFFICIAL_ARCHIVES[(system, "x86_64")], "sha256": storage.digest(archive)}
        # Fixture pins are patched in tests only; production pins never come from files or environment.
        self.stack.enter_context(patch.dict(storage.OFFICIAL_ARCHIVES, {(system, "x86_64"): release}))
        return archive, release

    def test_installs_linux_with_pinned_provenance(self):
        _, release = self.archive()
        storage.install()
        self.assertEqual((self.directory / "weed").read_bytes(), b"offline fixture binary")
        self.assertEqual((self.directory / "weed").stat().st_mode & 0o777, 0o500)
        installation = json.loads(storage.private_file(self.directory / "installation.json"))
        self.assertEqual(installation["archive_sha256"], release["sha256"])
        self.assertEqual(installation["source"], release["source"])
        self.assertEqual(installation["platform"], "Linux")
        self.assertEqual(installation["architecture"], "x86_64")
        self.run.assert_called_once_with([str(self.directory / "weed"), "version"],
            capture_output=True, text=True, check=True, timeout=15)

    def test_installs_darwin_with_its_own_pin(self):
        _, release = self.archive(system="Darwin")
        with patch.object(storage.platform, "system", return_value="Darwin"):
            storage.install()
        installation = json.loads(storage.private_file(self.directory / "installation.json"))
        self.assertEqual(installation["archive_sha256"], release["sha256"])
        self.assertEqual(installation["source"], release["source"])

    def test_rejects_changed_archive_before_execution(self):
        archive, _ = self.archive()
        archive.write_bytes(b"tampered")
        with self.assertRaisesRegex(ValueError, "OFFICIAL_ARCHIVE_MISMATCH"):
            storage.install()
        self.run.assert_not_called()

    def test_rejects_missing_archive(self):
        with self.assertRaisesRegex(ValueError, "OFFICIAL_ARCHIVE_MISMATCH"):
            storage.install()
        self.run.assert_not_called()

    def test_rejects_archive_symlink(self):
        archive, _ = self.archive()
        renamed = archive.with_name("real-archive")
        archive.rename(renamed)
        archive.symlink_to(renamed)
        with self.assertRaisesRegex(ValueError, "OFFICIAL_ARCHIVE_MISMATCH"):
            storage.install()
        self.run.assert_not_called()

    def test_rejects_untrusted_license(self):
        self.archive()
        (self.directory / "LICENSE").write_bytes(b"untrusted license")
        with self.assertRaisesRegex(ValueError, "OFFICIAL_LICENSE_MISMATCH"):
            storage.install()
        self.run.assert_not_called()

    def test_rejects_existing_or_dangling_binary(self):
        self.archive()
        target = self.directory / "weed"
        target.symlink_to(self.directory / "absent")
        with self.assertRaisesRegex(ValueError, "BINARY_ALREADY_PRESENT"):
            storage.install()
        self.run.assert_not_called()

    def test_rejects_traversal_link_duplicate_and_missing_member(self):
        cases = ((("../weed", tarfile.REGTYPE),), (("weed", tarfile.SYMTYPE),),
            (("weed", tarfile.LNKTYPE),), (("weed", tarfile.REGTYPE), ("weed", tarfile.REGTYPE)),
            (("another", tarfile.REGTYPE),))
        for entries in cases:
            with self.subTest(entries=entries):
                self.archive(entries)
                with self.assertRaisesRegex(ValueError, "ARCHIVE_BINARY_INVALID"):
                    storage.install()
        self.run.assert_not_called()

    def test_rejects_partial_version_match(self):
        self.archive()
        self.run.return_value.stdout = "version 30GB 4.480 abcdef"
        with self.assertRaisesRegex(ValueError, "BINARY_VERSION_INVALID"):
            storage.install()
        self.assertFalse((self.directory / "installation.json").exists())


class ProcessIdentityTests(unittest.TestCase):
    def setUp(self):
        self.stack = ExitStack()
        self.addCleanup(self.stack.close)
        self.stack.enter_context(patch.object(storage.platform, "system", return_value="Linux"))
        self.command = [str(storage.TOOLCHAIN / "weed"), "mini", "-dir=/private/space in name"]
        self.stack.enter_context(patch.object(storage, "command", return_value=self.command))
        self.exe = self.stack.enter_context(patch.object(storage.os, "readlink", return_value=self.command[0]))
        self.cmdline = self.stack.enter_context(patch.object(Path, "read_bytes",
            return_value=b"\0".join(os.fsencode(value) for value in self.command) + b"\0"))
        self.status = self.stack.enter_context(patch.object(Path, "read_text",
            return_value="Name:\tweed\nUid:\t" + "\t".join([str(os.getuid())] * 4) + "\n"))
        self.digest = self.stack.enter_context(patch.object(storage, "digest", return_value="a" * 64))
        self.run = self.stack.enter_context(patch.object(storage.subprocess, "run"))

    def test_linux_checks_actual_executable_uid_argv_and_hash(self):
        storage.verify_process_identity(123, "a" * 64)
        self.exe.assert_called_once_with(Path("/proc/123/exe"))
        self.digest.assert_called_once_with(Path("/proc/123/exe"))
        self.run.assert_not_called()

    def test_linux_rejects_mismatched_path_uid_argv_and_hash(self):
        mutations = ((self.exe, self.command[0] + " (deleted)"),
            (self.status, "Uid:\t0\t0\t0\t0\n" if os.getuid() else "Uid:\t1\t1\t1\t1\n"),
            (self.cmdline, (" ".join(self.command) + "\0").encode()), (self.digest, "b" * 64))
        for mocked, replacement in mutations:
            with self.subTest(target=mocked):
                original = mocked.return_value
                mocked.return_value = replacement
                with self.assertRaisesRegex(ValueError, "PROCESS_IDENTITY_CHANGED"):
                    storage.verify_process_identity(123, "a" * 64)
                mocked.return_value = original

    def test_linux_missing_or_unreadable_proc_fails_closed(self):
        for error in (FileNotFoundError(), PermissionError()):
            with self.subTest(error=type(error)), patch.object(storage.os, "readlink", side_effect=error):
                with self.assertRaisesRegex(ValueError, "PROCESS_IDENTITY_CHANGED"):
                    storage.verify_process_identity(123, "a" * 64)

    def test_unsupported_platform_never_reads_proc(self):
        with patch.object(storage.platform, "system", return_value="FreeBSD"):
            with self.assertRaisesRegex(ValueError, "PLATFORM_UNSUPPORTED"):
                storage.verify_process_identity(123, "a" * 64)
        self.exe.assert_not_called()
        self.cmdline.assert_not_called()

    def test_invalid_pid_never_reads_proc(self):
        for pid in (0, 1, -3, True, "123"):
            with self.subTest(pid=pid), self.assertRaisesRegex(ValueError, "PID_INVALID"):
                storage.verify_process_identity(pid, "a" * 64)
        self.exe.assert_not_called()

    def test_darwin_retains_exact_ps_identity_checks(self):
        self.run.side_effect = [subprocess.CompletedProcess([], 0, value + "\n", "")
            for value in (str(os.getuid()), self.command[0], " ".join(self.command))]
        with patch.object(storage.platform, "system", return_value="Darwin"):
            storage.verify_process_identity(123, "a" * 64)
        self.assertEqual(self.run.call_count, 3)
        self.exe.assert_not_called()

    def test_darwin_rejects_mismatch(self):
        self.run.return_value = subprocess.CompletedProcess([], 0, "another identity\n", "")
        with patch.object(storage.platform, "system", return_value="Darwin"):
            with self.assertRaisesRegex(ValueError, "PROCESS_IDENTITY_CHANGED"):
                storage.verify_process_identity(123, "a" * 64)
        self.exe.assert_not_called()


class StopSafetyTests(unittest.TestCase):
    def test_identity_failure_never_sends_signal(self):
        with patch.object(storage, "verify_identity", side_effect=ValueError("identity mismatch")), \
                patch.object(storage.os, "kill") as kill:
            with self.assertRaisesRegex(ValueError, "identity mismatch"):
                storage.stop()
        kill.assert_not_called()

    def test_missing_proc_after_signal_is_not_reported_as_clean_stop(self):
        identity = {"pid": 123, "binary_sha256": "a" * 64, "deployment_id": "test"}
        with patch.object(storage, "verify_identity", return_value=identity), \
                patch.object(storage.os, "kill") as kill, \
                patch.object(storage.subprocess, "run",
                    return_value=subprocess.CompletedProcess([], 0, "123\n", "")), \
                patch.object(storage, "verify_process_identity", side_effect=ValueError("process unavailable")), \
                patch.object(storage, "save_private") as save:
            with self.assertRaisesRegex(ValueError, "process unavailable"):
                storage.stop()
        kill.assert_called_once_with(123, storage.signal.SIGTERM)
        save.assert_not_called()


if __name__ == "__main__":
    unittest.main(verbosity=2)
