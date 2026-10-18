"""Isolated launcher tests: subprocesses, services and random secrets are mocked."""
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch


SPEC = importlib.util.spec_from_file_location("local_runtime", Path(__file__).resolve().parents[1] / "scripts/local_runtime.py")
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)
FIXTURE_SECRETS = {name: digit * 64 for name, digit in (
    ("root_password", "a"), ("database_password", "b"), ("redis_password", "c"))}
FIXTURE_IDENTITY = {"version": "8.4.3", "server_uuid": "b949dd16-e7f8-4392-b28c-d79342b24a14", "database": "medical_local"}


class RuntimeFixture(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name).resolve()
        self.tools = self.root / ".implementation-work/toolchains"
        self.runtime = self.root / ".implementation-work/runtime"
        self.secret_path = self.runtime / "service-secrets.json"
        for name, value in (("ROOT", self.root), ("RUNTIME", self.runtime), ("SECRETS", self.secret_path)):
            self.enterContext(patch.object(MODULE, name, value))
        # Any unmocked command is a unit-test failure, never a real service call.
        self.run = self.enterContext(patch.object(MODULE.subprocess, "run", side_effect=AssertionError("unexpected subprocess")))
        self.enterContext(patch.object(MODULE.subprocess, "Popen", side_effect=AssertionError("unexpected process")))
        self.enterContext(patch.object(MODULE.secrets, "token_hex", side_effect=["a" * 64, "b" * 64, "c" * 64]))
        self.enterContext(patch("builtins.print"))

    def executable(self, path):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text("#!/bin/sh\nexit 0\n", encoding="ascii")
        path.chmod(0o700)
        return path

    def toolchains(self, mysql_name="mysql-8.4.3", java_suffix=""):
        mysql_home = self.tools / mysql_name
        redis_bin = self.tools / "redis-7.4.8/src"
        java_home = self.tools / "jdk-17.0.20.1+1" / java_suffix
        for name in ("mysql", "mysqld", "mysqldump"):
            self.executable(mysql_home / "bin" / name)
        for name in ("redis-server", "redis-cli"):
            self.executable(redis_bin / name)
        java = self.executable(java_home / "bin/java")
        return mysql_home, redis_bin, java

    def use_toolchains(self):
        paths = self.toolchains()
        for name, value in zip(("MYSQL", "REDIS", "JAVA"), paths):
            self.enterContext(patch.object(MODULE, name, value))
        return paths

    def write_private(self, path, data):
        path.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
        path.write_text(json.dumps(data), encoding="utf-8")
        path.chmod(0o600)

    def initialized(self):
        self.use_toolchains()
        self.write_private(self.secret_path, FIXTURE_SECRETS)
        (self.runtime / "mysql-data").mkdir(mode=0o700)
        self.write_private(self.runtime / "initialization-receipt.json", {**FIXTURE_IDENTITY, "port": 0, "state": "SECURED"})


class RuntimeToolchainTests(RuntimeFixture):
    def test_linux_standalone_layout(self):
        expected = self.toolchains(mysql_name="mysql-8.4.3-linux-glibc2.28-x86_64")
        self.assertEqual(MODULE.resolve_runtime_toolchains(self.root, {}, required=True), expected)
        self.run.assert_not_called()

    def test_original_macos_layout(self):
        expected = self.toolchains(mysql_name="mysql-8.4.3-macos14-x86_64", java_suffix="Contents/Home")
        with patch.object(MODULE.platform, "system", return_value="Darwin"), patch.object(MODULE.platform, "machine", return_value="x86_64"):
            self.assertEqual(MODULE.resolve_runtime_toolchains(self.root, {}, required=True), expected)

    def test_other_operating_system_package_is_not_selected(self):
        expected = self.toolchains(mysql_name="mysql-8.4.3-linux-glibc2.28-x86_64")
        self.toolchains(mysql_name="mysql-8.4.3-macos14-x86_64")
        with patch.object(MODULE.platform, "system", return_value="Linux"), patch.object(MODULE.platform, "machine", return_value="x86_64"):
            self.assertEqual(MODULE.resolve_runtime_toolchains(self.root, {}, required=True), expected)

    def test_other_cpu_package_is_not_selected(self):
        expected = self.toolchains(mysql_name="mysql-8.4.3-macos14-arm64", java_suffix="Contents/Home")
        self.toolchains(mysql_name="mysql-8.4.3-macos14-x86_64", java_suffix="Contents/Home")
        with patch.object(MODULE.platform, "system", return_value="Darwin"), patch.object(MODULE.platform, "machine", return_value="arm64"):
            self.assertEqual(MODULE.resolve_runtime_toolchains(self.root, {}, required=True), expected)

    def test_explicit_paths(self):
        mysql_home, redis_bin, java = self.toolchains()
        values = {"MEDICAL_MYSQL_HOME": str(mysql_home), "MEDICAL_REDIS_BIN": str(redis_bin), "MEDICAL_JAVA_HOME": str(java.parent.parent)}
        self.assertEqual(MODULE.resolve_runtime_toolchains(self.root, values, required=True), (mysql_home, redis_bin, java))

    def test_invalid_explicit_overrides_never_fall_back(self):
        self.toolchains()
        for name in ("MEDICAL_MYSQL_HOME", "MEDICAL_REDIS_BIN", "MEDICAL_JAVA_HOME", "JAVA_HOME"):
            with self.subTest(name=name), self.assertRaisesRegex(ValueError, "NOT_FOUND"):
                MODULE.resolve_runtime_toolchains(self.root, {name: str(self.root / "missing")}, required=True)

    def test_empty_explicit_overrides_are_rejected(self):
        self.toolchains()
        for name in ("MEDICAL_MYSQL_HOME", "MEDICAL_REDIS_BIN", "MEDICAL_JAVA_HOME", "JAVA_HOME"):
            with self.subTest(name=name), self.assertRaisesRegex(ValueError, "EMPTY"):
                MODULE.resolve_runtime_toolchains(self.root, {name: " "})

    def test_medical_java_home_takes_precedence(self):
        mysql_home, redis_bin, java = self.toolchains()
        values = {"MEDICAL_JAVA_HOME": str(java.parent.parent), "JAVA_HOME": str(self.root / "missing")}
        self.assertEqual(MODULE.resolve_runtime_toolchains(self.root, values, required=True), (mysql_home, redis_bin, java))

    def test_path_discovery(self):
        prefix = self.root / "installed"
        for name in ("mysql", "mysqld", "mysqldump", "redis-server", "redis-cli", "java"):
            self.executable(prefix / "bin" / name)
        self.assertEqual(MODULE.resolve_runtime_toolchains(self.root, {"PATH": str(prefix / "bin")}, required=True),
                         (prefix, prefix / "bin", prefix / "bin/java"))

    def test_missing_defaults_can_be_imported_without_side_effects(self):
        paths = MODULE.resolve_runtime_toolchains(self.root, {})
        self.assertEqual(len(paths), 3)
        self.assertFalse((self.root / ".implementation-work").exists())
        self.run.assert_not_called()

    def test_incomplete_mysql_installation_is_rejected(self):
        mysql_home, _, _ = self.toolchains()
        (mysql_home / "bin/mysqldump").unlink()
        with self.assertRaisesRegex(ValueError, "MYSQL_TOOLCHAIN_NOT_FOUND"):
            MODULE.resolve_runtime_toolchains(self.root, {}, required=True)

    def test_nonexecutable_tools_are_rejected(self):
        _, redis_bin, _ = self.toolchains()
        (redis_bin / "redis-cli").chmod(0o600)
        with self.assertRaisesRegex(ValueError, "REDIS_TOOLCHAIN_NOT_FOUND"):
            MODULE.resolve_runtime_toolchains(self.root, {}, required=True)


class RuntimeInitializationTests(RuntimeFixture):
    def initialization_mocks(self, version="mysqld  Ver 8.4.3 for Linux on x86_64 (MySQL Community Server - GPL)"):
        self.use_toolchains()
        self.run.side_effect = None
        self.run.return_value = subprocess.CompletedProcess([], 0, stdout=version, stderr="")
        self.wait_mysql = self.enterContext(patch.object(MODULE, "wait_mysql"))
        self.secure = self.enterContext(patch.object(MODULE, "secure"))

    def test_fresh_initialize_creates_private_directories_before_commands(self):
        self.initialization_mocks()
        MODULE.initialize()
        for path in (self.runtime, self.runtime / "mysql-data", self.runtime / "redis-data", self.runtime / "tmp"):
            self.assertTrue(path.is_dir())
            self.assertEqual(path.stat().st_mode & 0o777, 0o700)
        self.assertEqual(self.secret_path.stat().st_mode & 0o777, 0o600)
        self.assertEqual(MODULE.settings(), FIXTURE_SECRETS)
        commands = [item.args[0] for item in self.run.call_args_list]
        self.assertIn("--initialize-insecure", commands[1])
        self.assertIn("--skip-networking", commands[2])
        self.assertIn("--mysqlx=OFF", commands[2])
        self.assertEqual(commands[2][1], "--no-defaults")
        self.assertIn("--socket=mysql.sock", commands[2])
        self.assertEqual(self.run.call_args_list[2].kwargs["cwd"], self.runtime / "mysql-data")
        self.wait_mysql.assert_called_once_with("", socket=True)
        self.secure.assert_called_once_with()

    def test_nonempty_datadir_is_not_reinitialized(self):
        self.initialization_mocks()
        data = self.runtime / "mysql-data"
        data.mkdir(mode=0o700, parents=True)
        self.runtime.chmod(0o700)
        (data / "existing").write_text("existing data")
        with self.assertRaisesRegex(RuntimeError, "DIRECTORY_NOT_EMPTY"):
            MODULE.initialize()
        self.run.assert_not_called()
        self.assertFalse(self.secret_path.exists())

    def test_unsupported_mysql_version_stops_before_initialization(self):
        self.initialization_mocks("mysqld  Ver 9.0.0 for Linux")
        with self.assertRaisesRegex(RuntimeError, "MYSQL_84_REQUIRED"):
            MODULE.initialize()
        self.assertEqual(self.run.call_count, 1)
        self.assertFalse(self.secret_path.exists())

    def test_existing_secret_is_not_replaced(self):
        self.initialization_mocks()
        self.write_private(self.secret_path, FIXTURE_SECRETS)
        previous = self.secret_path.read_bytes()
        with self.assertRaisesRegex(RuntimeError, "ALREADY_INITIALIZED"):
            MODULE.initialize()
        self.assertEqual(self.secret_path.read_bytes(), previous)
        self.run.assert_not_called()

    def test_symlinked_runtime_is_rejected_without_chmod(self):
        self.initialization_mocks()
        target = self.root / "other"
        target.mkdir(mode=0o755)
        self.runtime.parent.mkdir(parents=True, exist_ok=True)
        self.runtime.symlink_to(target, target_is_directory=True)
        with self.assertRaisesRegex(RuntimeError, "PRIVATE_DIRECTORY_INVALID"):
            MODULE.initialize()
        self.assertEqual(target.stat().st_mode & 0o777, 0o755)
        self.assertEqual(list(target.iterdir()), [])
        self.run.assert_not_called()

    def test_symlinked_datadir_is_rejected(self):
        self.initialization_mocks()
        MODULE.prepare_runtime_directories()
        target = self.root / "other-data"
        target.mkdir(mode=0o700)
        (self.runtime / "mysql-data").symlink_to(target, target_is_directory=True)
        with self.assertRaisesRegex(RuntimeError, "PRIVATE_DIRECTORY_INVALID"):
            MODULE.initialize()
        self.run.assert_not_called()

    def test_symlinked_service_log_is_rejected_before_any_command(self):
        self.initialization_mocks()
        MODULE.prepare_runtime_directories()
        (self.runtime / "mysql-initialize.log").symlink_to(self.root / "outside.log")
        with self.assertRaisesRegex(RuntimeError, "OUTPUT_PATH_INVALID"):
            MODULE.initialize()
        self.run.assert_not_called()
        self.assertFalse((self.root / "outside.log").exists())


class RuntimeSecurityAndClientTests(RuntimeFixture):
    def test_secret_permissions_are_checked_before_read(self):
        self.write_private(self.secret_path, FIXTURE_SECRETS)
        self.secret_path.chmod(0o644)
        with self.assertRaisesRegex(RuntimeError, "SECRETS_PERMISSIONS_INVALID"):
            MODULE.settings()

    def test_secret_symlinks_are_rejected(self):
        other = self.root / "other.json"
        self.write_private(other, FIXTURE_SECRETS)
        self.runtime.mkdir(mode=0o700, parents=True)
        self.secret_path.symlink_to(other)
        with self.assertRaises(OSError):
            MODULE.settings()

    def test_invalid_secret_shape_is_rejected(self):
        self.write_private(self.secret_path, ["not a secret object"])
        with self.assertRaisesRegex(ValueError, "SECRETS_INVALID"):
            MODULE.settings()

    def test_mysql_socket_uses_server_datadir_and_keeps_password_out_of_arguments(self):
        self.use_toolchains()
        self.run.side_effect = None
        MODULE.mysql("SELECT 1", "fixture-password", socket=True)
        call = self.run.call_args
        self.assertIn("--socket=mysql.sock", call.args[0])
        self.assertIn("--protocol=SOCKET", call.args[0])
        self.assertEqual(call.kwargs["cwd"], self.runtime / "mysql-data")
        self.assertNotIn("fixture-password", " ".join(call.args[0]))
        self.assertEqual(call.kwargs["env"]["MYSQL_PWD"], "fixture-password")

    def test_mysql_tcp_is_explicitly_loopback(self):
        self.use_toolchains()
        self.run.side_effect = None
        MODULE.mysql("SELECT 1", "fixture-password")
        command = self.run.call_args.args[0]
        self.assertIn("--protocol=TCP", command)
        self.assertIn("--host=127.0.0.1", command)
        self.assertIn("--port=13306", command)
        self.assertEqual(command[1], "--no-defaults")
        self.assertIn("--no-login-paths", command)

    def test_environment_does_not_require_platform_locale_or_create_directories(self):
        values = MODULE.environment()
        self.assertEqual(values["LC_ALL"], "C")
        self.assertEqual(values["TMPDIR"], str(self.runtime / "tmp"))
        self.assertFalse(self.runtime.exists())


class RuntimeStartupTests(RuntimeFixture):
    def startup_mocks(self):
        self.initialized()
        self.run.side_effect = None
        self.run.return_value = subprocess.CompletedProcess([], 0, stdout="", stderr="")
        self.enterContext(patch.object(MODULE, "wait_mysql"))
        self.enterContext(patch.object(MODULE, "wait_redis"))
        self.mysql = self.enterContext(patch.object(MODULE, "mysql", return_value=subprocess.CompletedProcess([], 0, stdout=json.dumps(FIXTURE_IDENTITY))))

    def test_start_creates_redis_data_and_pins_service_identity(self):
        self.startup_mocks()
        MODULE.start()
        self.assertTrue((self.runtime / "redis-data").is_dir())
        self.assertEqual((self.runtime / "redis-data").stat().st_mode & 0o777, 0o700)
        mysql_call, redis_call = self.run.call_args_list
        self.assertEqual(mysql_call.kwargs["cwd"], self.runtime / "mysql-data")
        self.assertIn("--bind-address=127.0.0.1", mysql_call.args[0])
        self.assertIn("--socket=mysql.sock", mysql_call.args[0])
        self.assertIn("bind 127.0.0.1\n", redis_call.kwargs["input"])
        self.assertIn("protected-mode yes\n", redis_call.kwargs["input"])
        self.assertNotIn(FIXTURE_SECRETS["redis_password"], " ".join(redis_call.args[0]))
        self.assertEqual(json.loads((self.runtime / "service-identity.json").read_bytes()), FIXTURE_IDENTITY)

    def test_pid_file_prevents_duplicate_start(self):
        self.startup_mocks()
        (self.runtime / "mysql.pid").write_text("12345")
        with self.assertRaisesRegex(RuntimeError, "PID_ALREADY_PRESENT"):
            MODULE.start()
        self.run.assert_not_called()

    def test_unsecured_receipt_prevents_start(self):
        self.startup_mocks()
        self.write_private(self.runtime / "initialization-receipt.json", {**FIXTURE_IDENTITY, "state": "PENDING"})
        with self.assertRaisesRegex(RuntimeError, "INITIALIZATION_RECEIPT_INVALID"):
            MODULE.start()
        self.run.assert_not_called()

    def test_wrong_database_identity_does_not_start_redis_or_replace_receipt(self):
        self.startup_mocks()
        self.mysql.return_value.stdout = json.dumps({**FIXTURE_IDENTITY, "server_uuid": "a different server"})
        with self.assertRaisesRegex(RuntimeError, "DATABASE_IDENTITY_CHANGED"):
            MODULE.start()
        self.assertEqual(self.run.call_count, 1)
        self.assertFalse((self.runtime / "service-identity.json").exists())

    def test_redis_startup_retries_connection_refusal_and_loading(self):
        self.use_toolchains()
        self.run.side_effect = [subprocess.CompletedProcess([], 1, "", "Connection refused"),
                                subprocess.CompletedProcess([], 0, "LOADING Redis is loading\n", ""),
                                subprocess.CompletedProcess([], 0, "PONG\n", "")]
        with patch.object(MODULE.time, "sleep"):
            MODULE.wait_redis({"REDISCLI_AUTH": "fixture"})
        self.assertEqual(self.run.call_count, 3)

    def test_redis_authentication_error_does_not_become_ready(self):
        self.use_toolchains()
        self.run.side_effect = None
        self.run.return_value = subprocess.CompletedProcess([], 0, "NOAUTH Authentication required.\n", "")
        with self.assertRaisesRegex(RuntimeError, "REDIS_NOT_READY"):
            MODULE.wait_redis({"REDISCLI_AUTH": "fixture"})


class RuntimeMigrationApprovalTests(RuntimeFixture):
    def test_missing_current_approval_stops_before_any_backup_or_database_access(self):
        with patch.dict(os.environ, {}, clear=True), self.assertRaisesRegex(ValueError, "MIGRATION_APPROVAL_REF_REQUIRED"):
            MODULE.migrate()
        self.run.assert_not_called()
        self.assertFalse(self.runtime.exists())

    def test_current_approval_reference_is_propagated(self):
        self.initialized()
        self.write_private(self.runtime / "service-identity.json", FIXTURE_IDENTITY)
        for name in ("medical-data-3.9.2-cli.jar", "medical-data-3.9.2.jar"):
            artifact = self.root / "business/medical-data/target" / name
            artifact.parent.mkdir(parents=True, exist_ok=True)
            artifact.write_bytes(b"unit test artifact")
        self.run.side_effect = None
        self.run.return_value = subprocess.CompletedProcess([], 0, b"-- fixture SQL\n", b"")
        with patch.dict(os.environ, {"MEDICAL_MIGRATION_APPROVAL_REF": "unit-test-current-approval"}):
            MODULE.migrate("0e8696bc-2a5b-48d2-ac97-2d54148f2dd3")
        self.assertEqual(self.run.call_args.kwargs["env"]["MEDICAL_MIGRATION_APPROVAL_REF"], "unit-test-current-approval")
        self.assertIn("--no-login-paths", self.run.call_args_list[0].args[0])
        self.assertIn("--protocol=TCP", self.run.call_args_list[0].args[0])
        backup = self.runtime / "migration-backups/0e8696bc-2a5b-48d2-ac97-2d54148f2dd3.sql.gz"
        self.assertEqual(backup.stat().st_mode & 0o777, 0o600)

    def test_symlinked_backup_directory_is_rejected(self):
        self.initialized()
        self.write_private(self.runtime / "service-identity.json", FIXTURE_IDENTITY)
        target = self.root / "other-backups"
        target.mkdir(mode=0o700)
        (self.runtime / "migration-backups").symlink_to(target, target_is_directory=True)
        with patch.dict(os.environ, {"MEDICAL_MIGRATION_APPROVAL_REF": "unit-test"}), self.assertRaisesRegex(RuntimeError, "PRIVATE_DIRECTORY_INVALID"):
            MODULE.migrate()
        self.run.assert_not_called()
        self.assertEqual(list(target.iterdir()), [])

    def test_symlinked_migration_log_is_rejected_before_backup(self):
        self.initialized()
        self.write_private(self.runtime / "service-identity.json", FIXTURE_IDENTITY)
        (self.runtime / "migration.log").symlink_to(self.root / "outside.log")
        with patch.dict(os.environ, {"MEDICAL_MIGRATION_APPROVAL_REF": "unit-test"}), self.assertRaisesRegex(RuntimeError, "OUTPUT_PATH_INVALID"):
            MODULE.migrate()
        self.run.assert_not_called()
        self.assertFalse((self.root / "outside.log").exists())
        self.assertFalse((self.runtime / "migration-backups").exists())

    def test_dangling_backup_symlink_is_rejected(self):
        self.initialized()
        self.write_private(self.runtime / "service-identity.json", FIXTURE_IDENTITY)
        backup_dir = self.runtime / "migration-backups"
        backup_dir.mkdir(mode=0o700)
        migration = "0e8696bc-2a5b-48d2-ac97-2d54148f2dd3"
        (backup_dir / (migration + ".sql.gz")).symlink_to(self.root / "outside.sql.gz")
        with patch.dict(os.environ, {"MEDICAL_MIGRATION_APPROVAL_REF": "unit-test"}), self.assertRaisesRegex(RuntimeError, "BACKUP_ALREADY_PRESENT"):
            MODULE.migrate(migration)
        self.run.assert_not_called()
        self.assertFalse((self.root / "outside.sql.gz").exists())

    def test_private_json_replacement_never_follows_symlinks(self):
        target = self.root / "untouched.json"
        target.write_bytes(b"unchanged")
        link = self.root / "link.json"
        link.symlink_to(target)
        with self.assertRaises(OSError):
            MODULE.save_private_json(link, {"changed": True}, replace=True)
        self.assertEqual(target.read_bytes(), b"unchanged")


if __name__ == "__main__":
    unittest.main(verbosity=2)
