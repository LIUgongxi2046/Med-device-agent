import argparse
import os
from pathlib import Path
import subprocess
import sys


ROOT = Path(__file__).resolve().parents[1]
CAPTURE = Path("/Users/liuhaoxian/.agents/skills/haonan-s000-pmp/scripts/capture_check.py")


def main():
    parser = argparse.ArgumentParser(description="核查Python与TypeScript的完整共享发布集合")
    parser.add_argument("--execution")
    parser.add_argument("--execute-tests", action="store_true")
    args = parser.parse_args()
    environment = os.environ.copy()
    environment["PYTHONPATH"] = str(ROOT / "contracts/python/src")
    environment["TMPDIR"] = str(ROOT / ".implementation-work/migration-check-tmp")
    if args.execute_tests:
        if args.execution is not None:
            raise ValueError("检查执行与回执登记必须分别指定")
        subprocess.run([sys.executable, "tests/test_shared_contracts.py"], cwd=ROOT, env=environment, check=True)
        subprocess.run(["npm", "run", "typecheck"], cwd=ROOT / "contracts/typescript", env=environment, check=True)
        subprocess.run(["npm", "test"], cwd=ROOT / "contracts/typescript", env=environment, check=True)
        return
    if not args.execution or Path(args.execution).name != args.execution or args.execution in (".", ".."):
        raise ValueError("执行身份必须为单一目录名称")
    inputs = ["scripts/check_shared_contracts.py", "tests/test_shared_contracts.py", "contracts/catalogue.json",
        "contracts/python/pyproject.toml", "contracts/python/requirements.lock", "contracts/typescript/package.json",
        "contracts/typescript/package-lock.json", "contracts/typescript/tsconfig.json", "data-design/sample_dataset.json",
        "data-design/dataset.schema.json", "backend-design/interfaces.schema.yaml", "backend-design/resource-details.schema.yaml",
        "agent-design/artifacts.schema.json", "agent-design/model-stream.schema.json",
        "business/medical-persistence/src/main/resources/templates.schema.json",
        "business/medical-persistence/src/main/resources/template-operations.schema.json",
        "business/medical-persistence/src/main/resources/tasks.schema.json",
        "business/medical-persistence/src/main/resources/collections.schema.json",
        "business/medical-persistence/src/main/resources/rule-expressions.schema.json",
        "business/medical-persistence/src/main/resources/template-applicability.schema.json",
        "business/medical-persistence/src/main/resources/agent-release.schema.json",
        "business/medical-persistence/src/main/resources/task-plan-view.schema.json"]
    for directory, pattern in (("contracts/python/src", "*.py"), ("contracts/typescript/src", "*.ts"),
            ("contracts/typescript/tests", "*.ts")):
        inputs.extend(str(path.relative_to(ROOT)) for path in sorted((ROOT / directory).rglob(pattern)))
    capture = [sys.executable, str(CAPTURE), "--output-dir", ".implementation-work/IC-03-SHARED-LANGUAGES-" + args.execution,
        "--check-id", "IC-03-SHARED-LANGUAGES", "--kind", "UNIT", "--baseline-id", "shared-published-schemas",
        "--cwd", str(ROOT), "--timeout", "600"]
    for path in inputs:
        capture.extend(["--input", path])
    subprocess.run([*capture, "--", sys.executable, "scripts/check_shared_contracts.py", "--execute-tests"],
        cwd=ROOT, env=environment, check=True)


if __name__ == "__main__":
    main()
