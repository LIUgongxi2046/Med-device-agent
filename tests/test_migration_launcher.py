"""Portable launcher and release gates, with no Java or database execution."""
import hashlib
import importlib.util
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch


ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location("run_migration", ROOT / "scripts/run_migration.py")
launcher = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(launcher)


def executable(path):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("#!/bin/sh\nexit 99\n", encoding="utf-8")
    path.chmod(0o700)
    return path


class JavaResolutionTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.toolchains = self.root / ".implementation-work/toolchains"

    def test_medical_java_home_precedes_java_home_without_maven_or_javac(self):
        medical = executable(self.root / "medical jre/bin/java")
        other = executable(self.root / "other jre/bin/java")
        environment = {"MEDICAL_JAVA_HOME": str(medical.parent.parent),
                       "JAVA_HOME": str(other.parent.parent), "PATH": ""}
        self.assertEqual(medical.resolve(), launcher.resolve_java(self.root, environment))
        self.assertFalse((medical.parent / "javac").exists())

    def test_java_home_is_supported(self):
        java = executable(self.root / "runtime/bin/java")
        self.assertEqual(java.resolve(), launcher.resolve_java(
            self.root, {"JAVA_HOME": str(java.parent.parent)}))

    def test_invalid_explicit_home_never_falls_back(self):
        project = executable(self.toolchains / "jdk-17.0.20.1+1/bin/java")
        path_java = executable(self.root / "path/bin/java")
        for name in ("MEDICAL_JAVA_HOME", "JAVA_HOME"):
            for value in ("", "   ", str(self.root / "missing")):
                environment = {"PATH": str(path_java.parent), name: value}
                if name == "MEDICAL_JAVA_HOME":
                    environment["JAVA_HOME"] = str(project.parent.parent)
                with self.subTest(name=name, value=value):
                    with self.assertRaisesRegex(ValueError, "JAVA_NOT_FOUND: " + name):
                        launcher.resolve_java(self.root, environment)

    def test_explicit_home_requires_an_executable_file(self):
        java = executable(self.root / "runtime/bin/java")
        java.chmod(0o600)
        with self.assertRaisesRegex(ValueError, "JAVA_NOT_FOUND: JAVA_HOME"):
            launcher.resolve_java(self.root, {"JAVA_HOME": str(java.parent.parent)})
        java.unlink()
        java.mkdir()
        with self.assertRaisesRegex(ValueError, "JAVA_NOT_FOUND: JAVA_HOME"):
            launcher.resolve_java(self.root, {"JAVA_HOME": str(java.parent.parent)})

    def test_project_linux_layout_precedes_path(self):
        java = executable(self.toolchains / "jdk-17.0.20.1+1/bin/java")
        path_java = executable(self.root / "path/bin/java")
        self.assertEqual(java.resolve(), launcher.resolve_java(self.root, {"PATH": str(path_java.parent)}))

    def test_project_macos_layout_is_retained_and_precedes_linux_layout(self):
        java = executable(self.toolchains / "jdk-17.0.20.1+1/Contents/Home/bin/java")
        executable(self.toolchains / "jdk-17.0.20.1+1/bin/java")
        self.assertEqual(java.resolve(), launcher.resolve_java(self.root, {}))

    def test_unusable_project_candidate_does_not_block_path(self):
        project = executable(self.toolchains / "jdk-17.0.20.1+1/bin/java")
        project.chmod(0o600)
        java = executable(self.root / "path/bin/java")
        self.assertEqual(java.resolve(), launcher.resolve_java(self.root, {"PATH": str(java.parent)}))

    def test_path_uses_supplied_environment_and_resolves_symlinks(self):
        java = executable(self.root / "runtime/bin/java")
        directory = self.root / "path"
        directory.mkdir()
        (directory / "java").symlink_to(java)
        with patch.dict(os.environ, {"PATH": "/unrelated", "JAVA_HOME": "/unrelated"}, clear=True):
            self.assertEqual(java.resolve(), launcher.resolve_java(self.root, {"PATH": str(directory)}))
            with self.assertRaisesRegex(ValueError, "JAVA_NOT_FOUND"):
                launcher.resolve_java(self.root, {})

    def test_missing_java_has_actionable_error(self):
        with self.assertRaisesRegex(ValueError, "set MEDICAL_JAVA_HOME or JAVA_HOME"):
            launcher.resolve_java(self.root, {"PATH": ""})


class MigrationReleaseGateTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.java = executable(self.root / "java runtime/bin/java")
        self.jar = self.root / "medical-data-cli.jar"
        self.jar.write_bytes(b"release-cli")
        self.library = self.root / "medical-data.jar"
        self.library.write_bytes(b"release-library")
        self.environment = {
            "MEDICAL_JAVA_HOME": str(self.java.parent.parent),
            "MEDICAL_MIGRATION_JAR_SHA256": hashlib.sha256(self.jar.read_bytes()).hexdigest(),
            "MEDICAL_MIGRATION_LIBRARY_SHA256": hashlib.sha256(self.library.read_bytes()).hexdigest(),
            "LIQUIBASE_COMMAND_URL": "jdbc:mysql://127.0.0.1:13306/medical_test",
            "LIQUIBASE_COMMAND_USERNAME": "migration-test-user",
            "LIQUIBASE_COMMAND_PASSWORD": "mock-password-never-executed",
            "MEDICAL_MIGRATION_DATABASE": "medical_test",
            "MEDICAL_MIGRATION_SERVER_UUID": "25d50e22-0abe-44dc-8312-19b6e6ef7ff1",
            "MEDICAL_MIGRATION_APPROVAL_REF": "test-approval",
            "MEDICAL_MIGRATION_BACKUP_RECEIPT_REF": "test-backup-receipt",
        }
        for name, value in (("ROOT", self.root), ("JAR", self.jar), ("LIBRARY", self.library)):
            patcher = patch.object(launcher, name, value)
            patcher.start()
            self.addCleanup(patcher.stop)
        patcher = patch.object(launcher.subprocess, "run")
        self.run = patcher.start()
        self.addCleanup(patcher.stop)

    def invoke(self, command, environment=None, *arguments):
        values = self.environment if environment is None else environment
        with patch.dict(os.environ, values, clear=True), patch("sys.argv", ["run_migration.py", command, *arguments]):
            launcher.main()

    def test_all_database_commands_preserve_target_and_strict_invocation(self):
        for command in launcher.DATABASE_COMMANDS:
            with self.subTest(command=command):
                self.run.reset_mock()
                self.invoke(command)
                self.run.assert_called_once()
                arguments = self.run.call_args.args[0]
                options = self.run.call_args.kwargs
                temporary = self.root / ".implementation-work/java-temp"
                self.assertEqual(arguments, [
                    str(self.java.resolve()), "-Dfile.encoding=UTF-8", "-Djava.io.tmpdir=" + str(temporary),
                    "-jar", str(self.jar), "--analytics-enabled=false", "--strict=true",
                    "--changelog-parse-mode=STRICT", "--search-path=" + str(self.library), command,
                    "--changelog-file=db/changelog/master.xml", "--driver=com.mysql.cj.jdbc.Driver",
                    "-DmedicalMigrationDatabase=medical_test",
                    "-DmedicalMigrationServerUuid=" + self.environment["MEDICAL_MIGRATION_SERVER_UUID"],
                ])
                self.assertEqual(options["cwd"], self.root)
                self.assertTrue(options["check"])
                self.assertEqual(options["env"], self.environment | {"TMPDIR": str(temporary)})
                self.assertTrue(temporary.is_dir())
                self.assertNotIn(self.environment["LIQUIBASE_COMMAND_PASSWORD"], " ".join(arguments))

    def test_every_database_command_requires_all_release_inputs(self):
        names = set(self.environment) - {"MEDICAL_JAVA_HOME", "MEDICAL_MIGRATION_BACKUP_RECEIPT_REF"}
        for command in launcher.DATABASE_COMMANDS:
            for name in sorted(names):
                for value in (None, "", "   "):
                    environment = self.environment.copy()
                    if value is None:
                        del environment[name]
                    else:
                        environment[name] = value
                    with self.subTest(command=command, name=name, value=value):
                        with self.assertRaisesRegex(ValueError, "缺少迁移参数：" + name):
                            self.invoke(command, environment)
                        self.run.assert_not_called()

    def test_cli_and_library_hashes_must_match_and_use_lowercase_sha256(self):
        for name, label in (("MEDICAL_MIGRATION_JAR_SHA256", "迁移制品"),
                            ("MEDICAL_MIGRATION_LIBRARY_SHA256", "迁移资源制品")):
            for value, reason in (("f" * 64, label + "摘要不匹配"),
                                  ("F" * 64, label + "摘要必须使用小写 SHA-256"),
                                  ("abc", label + "摘要必须使用小写 SHA-256")):
                for command in launcher.DATABASE_COMMANDS:
                    with self.subTest(name=name, value=value, command=command):
                        with self.assertRaisesRegex(ValueError, reason):
                            self.invoke(command, self.environment | {name: value})
                        self.run.assert_not_called()

    def test_only_mysql_connections_are_accepted(self):
        for command in launcher.DATABASE_COMMANDS:
            with self.subTest(command=command):
                with self.assertRaisesRegex(ValueError, "迁移连接必须使用 MySQL JDBC"):
                    self.invoke(command, self.environment | {"LIQUIBASE_COMMAND_URL": "jdbc:postgresql://localhost/test"})
                self.run.assert_not_called()

    def test_target_uuid_must_be_valid_lowercase_and_canonical(self):
        value = self.environment["MEDICAL_MIGRATION_SERVER_UUID"]
        for invalid in ("not-a-uuid", value.upper(), value.replace("-", ""), "{" + value + "}"):
            for command in launcher.DATABASE_COMMANDS:
                with self.subTest(value=invalid, command=command):
                    with self.assertRaises(ValueError):
                        self.invoke(command, self.environment | {"MEDICAL_MIGRATION_SERVER_UUID": invalid})
                    self.run.assert_not_called()

    def test_update_requires_backup_receipt(self):
        for value in (None, "", "   "):
            environment = self.environment.copy()
            if value is None:
                del environment["MEDICAL_MIGRATION_BACKUP_RECEIPT_REF"]
            else:
                environment["MEDICAL_MIGRATION_BACKUP_RECEIPT_REF"] = value
            with self.subTest(value=value):
                with self.assertRaisesRegex(ValueError, "缺少迁移参数：MEDICAL_MIGRATION_BACKUP_RECEIPT_REF"):
                    self.invoke("update", environment)
                self.run.assert_not_called()

    def test_non_update_commands_do_not_require_backup_receipt(self):
        environment = self.environment.copy()
        del environment["MEDICAL_MIGRATION_BACKUP_RECEIPT_REF"]
        for command in ("validate", "status", "update-sql"):
            with self.subTest(command=command):
                self.run.reset_mock()
                self.invoke(command, environment)
                self.run.assert_called_once()

    def test_missing_artifacts_never_start_java(self):
        self.jar.unlink()
        with self.assertRaises(FileNotFoundError):
            self.invoke("validate")
        self.run.assert_not_called()
        self.jar.write_bytes(b"release-cli")
        self.library.unlink()
        with self.assertRaises(FileNotFoundError):
            self.invoke("validate")
        self.run.assert_not_called()

    def test_information_commands_remain_offline_without_release_inputs(self):
        environment = {"JAVA_HOME": str(self.java.parent.parent)}
        for command, arguments, suffix in (("version", (), ["--version"]),
                                           ("help", (), ["--help"]),
                                           ("help", ("--for-command", "update"), ["update", "--help"])):
            with self.subTest(command=command, arguments=arguments):
                self.run.reset_mock()
                self.invoke(command, environment, *arguments)
                self.run.assert_called_once()
                self.assertEqual(self.run.call_args.args[0][-len(suffix):], suffix)

    def test_invalid_explicit_java_home_prevents_launch_even_with_path_fallback(self):
        environment = self.environment | {"MEDICAL_JAVA_HOME": str(self.root / "missing"),
                                          "PATH": str(self.java.parent)}
        with self.assertRaisesRegex(ValueError, "JAVA_NOT_FOUND: MEDICAL_JAVA_HOME"):
            self.invoke("update", environment)
        self.run.assert_not_called()


if __name__ == "__main__":
    unittest.main(verbosity=2)
