import argparse
import json
import os
from pathlib import Path
import subprocess
import sys


ROOT = Path(__file__).resolve().parents[1]
CAPTURE = Path("/Users/liuhaoxian/.agents/skills/haonan-s000-pmp/scripts/capture_check.py")


def main():
    parser = argparse.ArgumentParser(description="保存 Python 共享类型及真实来源检查证据")
    parser.add_argument("--check", choices=("test", "package"), default="test")
    parser.add_argument("--execution", required=True)
    args = parser.parse_args()
    identity = Path(args.execution)
    if identity.name != args.execution or args.execution in (".", ".."):
        raise ValueError("执行身份必须为单一目录名称")

    catalogue = json.loads((ROOT / "contracts/catalogue.json").read_bytes())
    dataset = json.loads((ROOT / "data-design/sample_dataset.json").read_bytes())
    inputs = {"scripts/check_python.py", "contracts/catalogue.json", "business/medical-persistence/src/main/resources/evaluation-results.schema.json", "contracts/extensions/evaluation-results.catalogue.json", "data-design/sample_dataset.json",
              "contracts/python/pyproject.toml", "contracts/python/requirements.lock",
              "tests/test_shared_contracts.py", "tests/test_simulation_contract_extension.py", "tests/test_python_domain.py", "tests/check_python_contracts.py",
              "contracts/python/.gitignore", "LLD-后端.md", "tests/error_design.py", "tests/test_error_codes.py"}
    inputs.add("contracts/extensions/simulation-execution.catalogue.json")
    inputs.add("contracts/fixtures/simulation-context.synthetic.json")
    inputs.update(entry["path"] for entry in json.loads((ROOT / "contracts/extensions/simulation-execution.catalogue.json").read_bytes())["schemas"])
    inputs.update(entry["path"] for entry in catalogue["schemas"])
    inputs.update(entry["path"] for entry in json.loads((ROOT / "contracts/extensions/evaluation-results.catalogue.json").read_bytes())["schemas"])
    inputs.update(str(path.relative_to(ROOT)) for path in (ROOT / "contracts/python/src").rglob("*.py"))
    inputs.update(str(path.relative_to(ROOT)) for path in (ROOT / ".implementation-work/python-wheels").glob("*.whl"))
    for source in dataset["sources"]:
        path = (ROOT / source["source_ref"]).resolve(strict=True)
        inputs.add(str(path.relative_to(ROOT)))

    check_id = "IC-03-PYTHON" if args.check == "test" else "IC-01-PYTHON-PACKAGE"
    capture = [sys.executable, str(CAPTURE), "--output-dir",
               str(ROOT / ".implementation-work" / (check_id + "-" + args.execution)),
               "--check-id", check_id, "--kind", "UNIT" if args.check == "test" else "INTEGRATION",
               "--baseline-id", "shared-schemas-and-migration-1.0.0", "--cwd", str(ROOT), "--timeout", "60"]
    if args.check == "package":
        inputs.update(("scripts/build_python.py", "tests/inspect_python_package.py"))
    for path in sorted(inputs):
        capture.extend(["--input", str(ROOT / path)])

    environment = os.environ.copy()
    temporary = ROOT / ".implementation-work/python-tmp"
    temporary.mkdir(parents=True, exist_ok=True)
    environment["TMPDIR"] = str(temporary)
    environment["PYTHONPATH"] = str(ROOT / "contracts/python/src")
    command = [sys.executable, "tests/check_python_contracts.py"]
    if args.check == "package":
        output = ROOT / ".implementation-work/python-package" / args.execution
        capture.extend(["--artifact", str(output / "medical_contracts-1.0.0-py3-none-any.whl")])
        command = [sys.executable, "scripts/build_python.py", "--output-dir", str(output)]
    subprocess.run([*capture, "--", *command], cwd=ROOT, env=environment, check=True)


if __name__ == "__main__":
    main()
