import argparse
import os
from pathlib import Path
import subprocess
import sys
from uuid import uuid4

import sqlglot


ROOT = Path(__file__).resolve().parents[1]
CAPTURE = Path("/Users/liuhaoxian/.agents/skills/haonan-s000-pmp/scripts/capture_check.py")


def main():
    parser = argparse.ArgumentParser(description="保存真实MySQL的Artifact资源、完整依赖和读取范围结构检查")
    parser.add_argument("--execution", required=True)
    parser.add_argument("--check", choices=("inspect", "migrate"), default="inspect")
    args = parser.parse_args()
    if Path(args.execution).name != args.execution or args.execution in (".", ".."):
        raise ValueError("执行身份必须使用单一目录名称")
    inputs = ["scripts/check_artifact_registry.py", "tests/test_artifact_registry_runtime.py", "scripts/local_runtime.py",
              "LLD-后端.md", "LLD-数据.md", "backend-design/接口补充设计.md", "backend-design/interfaces.schema.yaml",
              "data-design/core.sql", "data-design/requirements.txt", str(Path(sqlglot.__file__).resolve(strict=True)),
              ".implementation-work/runtime/service-identity.json",
              "ui-delivery/assets/public-evidence/clinical-evaluation-2021.docx"]
    inputs.extend(str(path.relative_to(ROOT)) for path in sorted((ROOT / "business/medical-data/src").rglob("*")) if path.is_file())
    if args.check == "migrate":
        check_id = "IC-03-ARTIFACT-MIGRATION"
        migration_id = str(uuid4())
        command = [sys.executable, "scripts/local_runtime.py", "migrate", "--migration-id", migration_id]
        artifacts = [ROOT / ".implementation-work/runtime/migration.log",
                     ROOT / ".implementation-work/runtime/migration-backups" / (migration_id + ".sql.gz"),
                     ROOT / ".implementation-work/runtime/migration-backups" / (migration_id + ".json")]
        inputs.extend(("scripts/run_migration.py", "business/medical-data/target/medical-data-3.9.2-cli.jar",
                       "business/medical-data/target/medical-data-3.9.2.jar"))
    else:
        check_id = "IC-05-ARTIFACT-REGISTRY"
        command = [sys.executable, "tests/test_artifact_registry_runtime.py"]
        artifacts = [ROOT / ".implementation-work" / (check_id + "-" + args.execution) / "database-evidence.json"]
    capture = [sys.executable, str(CAPTURE), "--output-dir", ".implementation-work/" + check_id + "-" + args.execution,
               "--check-id", check_id, "--kind", "INTEGRATION",
               "--baseline-id", "actual-local-mysql-artifact-registry", "--cwd", str(ROOT), "--timeout", "240"]
    for path in inputs:
        capture.extend(["--input", path])
    for artifact in artifacts:
        capture.extend(["--artifact", str(artifact)])
    temporary = ROOT / ".implementation-work/artifact-registry-tmp"
    temporary.mkdir(parents=True, exist_ok=True)
    environment = os.environ.copy()
    environment["TMPDIR"] = str(temporary)
    if args.check == "inspect":
        environment["MEDICAL_ARTIFACT_REGISTRY_EVIDENCE"] = str(artifacts[0])
    subprocess.run([*capture, "--", *command], cwd=ROOT, env=environment, check=True)


if __name__ == "__main__":
    main()
