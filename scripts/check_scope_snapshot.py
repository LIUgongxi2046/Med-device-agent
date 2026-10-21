import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import time


ROOT = Path(__file__).resolve().parents[1]
from check_support import capture_check_path
CAPTURE = capture_check_path()


def main():
    parser = argparse.ArgumentParser(description="验证任务来源快照的实际保存和企业访问控制")
    parser.add_argument("--execution", required=True)
    parser.add_argument("--red-only", action="store_true")
    args = parser.parse_args()
    if Path(args.execution).name != args.execution or args.execution in (".", ".."):
        raise ValueError("执行身份必须使用单一目录名称")
    output = ROOT / ".implementation-work" / ("IC-12-SNAPSHOT-" + args.execution)
    inputs = {ROOT / "scripts/check_scope_snapshot.py", ROOT / "business/pom.xml", ROOT / "contracts/catalogue.json", ROOT / "contracts/extensions/evaluation-results.catalogue.json", ROOT / "business/medical-persistence/src/main/resources/evaluation-results.schema.json",
        ROOT / "data-design/core.sql", ROOT / "docs/development/IMP-12.md"}
    for module in ("medical-contracts", "medical-data", "ruoyi-common", "ruoyi-system", "ruoyi-framework", "medical-identity", "medical-persistence"):
        inputs.add(ROOT / "business" / module / "pom.xml")
        inputs.update(path for path in (ROOT / "business" / module / "src").rglob("*") if path.is_file())
    for entry in json.loads((ROOT / "contracts/catalogue.json").read_bytes())["schemas"]:
        inputs.add(ROOT / entry["path"])
    capture = [sys.executable, str(CAPTURE), "--output-dir", str(output), "--check-id", "IC-12-SNAPSHOT",
        "--kind", "INTEGRATION", "--baseline-id", "BACK-SCOPE-SNAPSHOT-STORE-01", "--cwd", str(ROOT / "business"), "--timeout", "600"]
    for path in sorted(inputs):
        capture.extend(("--input", str(path)))
    temporary = ROOT / ".implementation-work/scope-snapshot-tmp"
    temporary.mkdir(parents=True, exist_ok=True)
    tools = ROOT / ".implementation-work/toolchains"
    environment = os.environ.copy()
    environment.update({"JAVA_HOME": str(tools / "jdk-17.0.20.1+1/Contents/Home"), "TMPDIR": str(temporary),
        "MAVEN_OPTS": "-Dfile.encoding=UTF-8 -Djava.io.tmpdir=" + str(temporary)})
    selection = "ScopeSnapshotStoreTest#providesImmutableSnapshotWritesWithProtectedActualMappings" if args.red_only else "ScopeSnapshotStoreTest,TenantAccessTest,TaskInputSchemaTest"
    command = [str(tools / "apache-maven-3.9.16/bin/mvn"), "--offline", "--batch-mode", "--no-transfer-progress",
        "-Dmaven.repo.local=" + str(ROOT / ".implementation-work/maven-repository"), "-pl", "medical-persistence", "-am",
        "-Dtest=" + selection, "-Dsurefire.failIfNoSpecifiedTests=false", "test"]
    started = time.time_ns()
    result = subprocess.run([*capture, "--", *command], cwd=ROOT, env=environment)
    for source in sorted((ROOT / "business/medical-persistence/target/surefire-reports").glob("TEST-*.xml")):
        if source.stat().st_mtime_ns >= started:
            shutil.copyfile(source, output / source.name)
    receipt = output / "receipt.json"
    print(json.dumps({"receipt_sha256": hashlib.sha256(receipt.read_bytes()).hexdigest(),
        "public_task_create_verified": False, "customer_success_verified": False}))
    raise SystemExit(result.returncode)


if __name__ == "__main__":
    main()
