import hashlib
import os
from pathlib import Path
import subprocess
import sys
import unittest
from zipfile import ZipFile


ROOT = Path(__file__).resolve().parents[1]
CLI = ROOT / "business/medical-data/target/medical-data-3.9.2-cli.jar"
LIBRARY = ROOT / "business/medical-data/target/medical-data-3.9.2.jar"
RESOURCES = ROOT / "business/medical-data/src/main/resources"


class MigrationCliChecks(unittest.TestCase):
    def test_actual_entrypoint_rejects_missing_inputs_and_another_artifact_digest(self):
        # 子进程明确省略目标配置，验证连接启动之前的拒绝路径。
        environment = {name: value for name, value in os.environ.items()
                       if not name.startswith(("MEDICAL_MIGRATION_", "LIQUIBASE_"))}
        with CLI.open("rb") as stream:
            jar_hash = hashlib.file_digest(stream, "sha256").hexdigest()
        with (ROOT / "data-design/core.sql").open("rb") as stream:
            core_hash = hashlib.file_digest(stream, "sha256").hexdigest()
        self.assertNotEqual(core_hash, jar_hash)
        cases = (
            ({}, "缺少迁移参数：MEDICAL_MIGRATION_JAR_SHA256"),
            ({"MEDICAL_MIGRATION_JAR_SHA256": core_hash}, "迁移制品摘要不匹配"),
            ({"MEDICAL_MIGRATION_JAR_SHA256": jar_hash}, "缺少迁移参数：LIQUIBASE_COMMAND_URL"),
        )
        for provided, reason in cases:
            with self.subTest(reason=reason):
                result = subprocess.run(
                    [sys.executable, "scripts/run_migration.py", "validate"], cwd=ROOT, env=environment | provided,
                    stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, encoding="utf-8", timeout=30,
                )
                print("MIGRATION_CLI_GUARD exit_code=" + str(result.returncode) + " reason=" + reason, flush=True)
                print(result.stdout, flush=True)
                self.assertNotEqual(result.returncode, 0)
                self.assertIn(reason, result.stdout)
                self.assertNotIn("Starting Liquibase", result.stdout)

    def test_actual_cli_and_library_preserve_every_migration_resource(self):
        resources = sorted(path for path in RESOURCES.rglob("*") if path.is_file())
        with ZipFile(CLI) as executable, ZipFile(LIBRARY) as library:
            names = executable.namelist()
            self.assertEqual(len(names), len(set(names)))
            for path in resources:
                name = path.relative_to(RESOURCES).as_posix()
                with self.subTest(resource=name):
                    self.assertEqual(executable.read("BOOT-INF/classes/" + name), path.read_bytes())
                    self.assertEqual(library.read(name), path.read_bytes())
            core = (ROOT / "data-design/core.sql").read_bytes()
            self.assertEqual(executable.read("BOOT-INF/classes/data-design/core.sql"), core)
            self.assertEqual(library.read("data-design/core.sql"), core)
            self.assertIn("BOOT-INF/lib/liquibase-core-4.31.1.jar", names)
            self.assertIn("BOOT-INF/lib/picocli-4.7.6.jar", names)
            self.assertEqual(len([name for name in names if name.startswith("BOOT-INF/lib/mysql-connector-j-")]), 1)
        print("MIGRATION_CLI_RESOURCE_CHECK resources=" + str(len(resources))
              + " cli_sha256=" + hashlib.sha256(CLI.read_bytes()).hexdigest()
              + " scope=LOCAL_ARTIFACT_CHECK", flush=True)

    def test_actual_packaged_liquibase_starts_for_information_commands(self):
        for command in ("version", "help"):
            with self.subTest(command=command):
                result = subprocess.run(
                    [sys.executable, "scripts/run_migration.py", command], cwd=ROOT,
                    stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, encoding="utf-8", timeout=30,
                )
                print("MIGRATION_CLI_COMMAND " + command + " exit_code=" + str(result.returncode), flush=True)
                print(result.stdout, flush=True)
                self.assertEqual(result.returncode, 0)
                if command == "version":
                    self.assertIn("4.31.1", result.stdout)
                else:
                    for required in ("validate", "status", "update-sql", "update"):
                        self.assertIn(required, result.stdout)

    def test_actual_native_database_commands_declare_required_connection_options(self):
        for command in ("validate", "status", "update-sql", "update"):
            with self.subTest(command=command):
                result = subprocess.run(
                    [sys.executable, "scripts/run_migration.py", "help", "--for-command", command], cwd=ROOT,
                    stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, encoding="utf-8", timeout=30,
                )
                print("MIGRATION_CLI_HELP " + command + " exit_code=" + str(result.returncode), flush=True)
                print(result.stdout, flush=True)
                self.assertEqual(result.returncode, 0)
                for required in ("--changelog-file=", "--url=", "--username=", "--password=", "--driver=", "-D=PARAM"):
                    self.assertIn(required, result.stdout)


if __name__ == "__main__":
    unittest.main(verbosity=2)
