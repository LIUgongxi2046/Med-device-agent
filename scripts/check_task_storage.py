import argparse
import os
from pathlib import Path
import subprocess
import sys


ROOT = Path(__file__).resolve().parents[1]
CAPTURE = Path("/Users/liuhaoxian/.agents/skills/haonan-s000-pmp/scripts/capture_check.py")


def main():
    parser = argparse.ArgumentParser(description="核查真实MySQL中的完整任务输入和计划版本保存结构")
    parser.add_argument("--execution", required=True)
    arguments = parser.parse_args()
    if Path(arguments.execution).name != arguments.execution or arguments.execution in (".", ".."):
        raise ValueError("执行身份必须使用独立目录名称")
    inputs = ["scripts/check_task_storage.py", "scripts/local_runtime.py", "tests/test_task_storage_runtime.py",
        "LLD-后端.md", "LLD-数据.md", "contracts/catalogue.json",
        "data-design/core.sql", ".implementation-work/runtime/service-identity.json"]
    inputs.extend(str(path.relative_to(ROOT)) for path in sorted((ROOT / "business/medical-data/src/main/resources").rglob("*"))
        if path.is_file())
    command = [sys.executable, str(CAPTURE), "--output-dir", ".implementation-work/IC-12-STORAGE-" + arguments.execution,
        "--check-id", "IC-12-STORAGE", "--kind", "INTEGRATION", "--baseline-id", "task-plan-version-storage-1.0.0",
        "--cwd", str(ROOT), "--timeout", "120"]
    for value in sorted(set(inputs)):
        command.extend(["--input", value])
    environment = os.environ.copy()
    temporary = ROOT / ".implementation-work/migration-check-tmp"
    temporary.mkdir(parents=True, exist_ok=True)
    environment["TMPDIR"] = str(temporary)
    completed = subprocess.run([*command, "--", sys.executable, "tests/test_task_storage_runtime.py"],
        cwd=ROOT, env=environment, check=False)
    raise SystemExit(completed.returncode)


if __name__ == "__main__":
    main()
