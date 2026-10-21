import argparse
import os
from pathlib import Path
import subprocess
import sys
from uuid import uuid4

import sqlglot


ROOT = Path(__file__).resolve().parents[1]
from check_support import capture_check_path
CAPTURE = capture_check_path()


def main():
    parser = argparse.ArgumentParser(description="核查上传许可元数据和共用分片持久化")
    parser.add_argument("--execution", required=True)
    parser.add_argument("--check", choices=("inspect", "build", "migrate"), default="inspect")
    args = parser.parse_args()
    if Path(args.execution).name != args.execution or args.execution in (".", ".."):
        raise ValueError("执行身份必须使用单一目录名称")
    inputs = ["scripts/check_upload_storage.py", "tests/test_upload_storage_runtime.py", "scripts/local_runtime.py", "scripts/run_migration.py",
              "business/pom.xml", "business/medical-data/pom.xml", "LLD-数据.md", "LLD-后端.md", "backend-design/接口补充设计.md",
              "data-design/core.sql", ".implementation-work/runtime/service-identity.json", "tests/test_migration_bundle.py",
              str(Path(sqlglot.__file__).resolve(strict=True)), "ui-delivery/assets/public-evidence/clinical-evaluation-2021.docx",
              ".implementation-work/IC-05-OBJECT-STORAGE-20261005-OBJECT-STORAGE-FINAL-01/objects.json"]
    inputs.extend(str(path.relative_to(ROOT)) for path in sorted((ROOT / "business/medical-data/src").rglob("*")) if path.is_file())
    temporary = ROOT / ".implementation-work/upload-storage-tmp"; temporary.mkdir(parents=True, exist_ok=True)
    environment = os.environ.copy(); environment["TMPDIR"] = str(temporary)
    if args.check == "inspect":
        check_id = "IC-05-UPLOAD-STORAGE"; command = [sys.executable, "tests/test_upload_storage_runtime.py"]
        artifacts = [ROOT / ".implementation-work" / (check_id + "-" + args.execution) / "database-evidence.json"]
        environment["MEDICAL_UPLOAD_STORAGE_EVIDENCE"] = str(artifacts[0])
    elif args.check == "migrate":
        migration_id = str(uuid4())
        check_id = "IC-03-UPLOAD-MIGRATION"
        command = [sys.executable, "scripts/local_runtime.py", "migrate", "--migration-id", migration_id]
        inputs.extend(("business/medical-data/target/medical-data-3.9.2-cli.jar", "business/medical-data/target/medical-data-3.9.2.jar"))
        artifacts = [ROOT / ".implementation-work/runtime/migration.log",
                     ROOT / ".implementation-work/runtime/migration-backups" / (migration_id + ".sql.gz"),
                     ROOT / ".implementation-work/runtime/migration-backups" / (migration_id + ".json")]
    else:
        check_id = "IC-03-UPLOAD-BUILD"
        toolchains = ROOT / ".implementation-work/toolchains"
        environment["JAVA_HOME"] = str((toolchains / "jdk-17.0.20.1+1/Contents/Home").resolve(strict=True))
        environment["MAVEN_OPTS"] = "-Dfile.encoding=UTF-8 -Djava.io.tmpdir=" + str(temporary)
        command = [str((toolchains / "apache-maven-3.9.16/bin/mvn").resolve(strict=True)), "--batch-mode", "--no-transfer-progress",
                   "-Dmaven.repo.local=" + str(ROOT / ".implementation-work/maven-repository"), "-f", "business/pom.xml", "-pl", "medical-data", "-am",
                   "-Dtest=MigrationBundleTest", "-Dsurefire.failIfNoSpecifiedTests=false", "package"]
        artifacts = [ROOT / "business/medical-data/target" / name for name in ("medical-data-3.9.2.jar", "medical-data-3.9.2-cli.jar", "surefire-reports/TEST-com.ruoyi.medical.data.MigrationBundleTest.xml")]
    capture = [sys.executable, str(CAPTURE), "--output-dir", ".implementation-work/" + check_id + "-" + args.execution,
               "--check-id", check_id, "--kind", "INTEGRATION", "--baseline-id", "BACK-UPLOAD-STORAGE-01", "--cwd", str(ROOT), "--timeout", "900"]
    for path in inputs: capture.extend(["--input", path])
    for path in artifacts: capture.extend(["--artifact", str(path)])
    subprocess.run([*capture, "--", *command], cwd=ROOT, env=environment, check=True)


if __name__ == "__main__":
    main()
