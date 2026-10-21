import argparse
import importlib.metadata
import os
from pathlib import Path
import subprocess
import sys


ROOT = Path(__file__).resolve().parents[1]
from check_support import capture_check_path
CAPTURE = capture_check_path()


def main():
    parser = argparse.ArgumentParser(description="保存模型适配器原生验证及输入摘要")
    parser.add_argument("--execution", required=True)
    parser.add_argument("--check", choices=("test", "package", "simulation"), default="test")
    args = parser.parse_args()
    if Path(args.execution).name != args.execution or args.execution in (".", ".."):
        raise ValueError("执行身份必须为单一目录名称")
    check_id = {"test": "IC-07-ADAPTER", "package": "IC-07-PACKAGE", "simulation": "IC-07-SIMULATION"}[args.check]
    output = ROOT / ".implementation-work" / (check_id + "-" + args.execution)
    inputs = {"scripts/check_model_gateway.py", "scripts/configure_deepseek.py", "tests/test_model_gateway.py", "tests/test_model_simulation.py",
              "model-gateway/pyproject.toml", "model-gateway/requirements.lock", "agent-design/model-stream.schema.json",
              "contracts/python/pyproject.toml", "LLD-Agent.md", "LLD-后端.md",
              "scripts/build_model_gateway.py", "tests/inspect_model_package.py", "model-gateway/.gitignore"}
    inputs.update(str(path.relative_to(ROOT)) for path in (ROOT / "model-gateway/src").rglob("*.py"))
    inputs.update(str(path.relative_to(ROOT)) for path in (ROOT / "contracts/python/src").rglob("*.py"))
    for package in ("openai", "httpx2", "httpcore2", "pydantic", "pydantic-core", "jsonschema", "referencing"):
        distribution = importlib.metadata.distribution(package)
        for path in distribution.files:
            if str(path).endswith((".py", ".so", "/METADATA", "/RECORD")):
                inputs.add(str(distribution.locate_file(path).resolve(strict=True)))
    command = [sys.executable, str(CAPTURE), "--output-dir", str(output), "--check-id", check_id,
               "--kind", "UNIT" if args.check == "test" else "INTEGRATION",
               "--baseline-id", "deepseek-adapter-1.0.0", "--cwd", str(ROOT), "--timeout", "180"]
    for path in sorted(inputs):
        command.extend(["--input", str(ROOT / path)])
    environment = os.environ.copy()
    environment["TMPDIR"] = str(ROOT / ".implementation-work/migration-check-tmp")
    environment["PIP_CACHE_DIR"] = str(ROOT / ".implementation-work/pip-cache")
    environment["PYTHONPATH"] = os.pathsep.join((str(ROOT / "model-gateway/src"), str(ROOT / "contracts/python/src")))
    check = [sys.executable, "-m", "unittest", "discover", "-s", "tests", "-p", "test_model_gateway.py", "-v"]
    if args.check == "simulation":
        check = [sys.executable, "-m", "unittest", "discover", "-s", "tests", "-p", "test_model_simulation.py", "-v"]
    if args.check == "package":
        artifacts = ROOT / ".implementation-work/model-wheels" / args.execution
        command.extend(["--artifact", str(artifacts / "medical_model_gateway-1.0.0-py3-none-any.whl"),
                        "--artifact", str(artifacts / "build.json")])
        check = [sys.executable, "scripts/build_model_gateway.py", "--execution", args.execution]
    result = subprocess.run([*command, "--", *check], cwd=ROOT, env=environment)
    raise SystemExit(result.returncode)


if __name__ == "__main__":
    main()
