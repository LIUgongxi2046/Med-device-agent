import argparse
from importlib.metadata import version
import os
from pathlib import Path
import subprocess
import sys


ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description="构建及验证 Python 共享库的实际安装制品")
    parser.add_argument("--output-dir", type=Path, required=True)
    args = parser.parse_args()
    output = args.output_dir.resolve()
    parent = ROOT / ".implementation-work/python-package"
    if not output.is_relative_to(parent) or output == parent:
        raise ValueError("Python 制品目录必须位于项目登记目录")
    output.mkdir(parents=True, exist_ok=False)
    if version("setuptools") != "80.9.0":
        raise ValueError("Python 构建组件版本不一致")
    environment = os.environ.copy()
    environment["TMPDIR"] = str(ROOT / ".implementation-work/python-tmp")
    environment["PIP_CACHE_DIR"] = str(ROOT / ".implementation-work/python-cache")
    environment.pop("PYTHONPATH", None)
    subprocess.run([
        sys.executable, "-m", "pip", "wheel", "--no-deps", "--no-build-isolation",
        "--no-index", "--wheel-dir", str(output), str(ROOT / "contracts/python"),
    ], cwd=ROOT, env=environment, check=True)
    wheel = output / "medical_contracts-1.0.0-py3-none-any.whl"
    if not wheel.is_file() or len(list(output.glob("*.whl"))) != 1:
        raise ValueError("Python 构建制品不完整")
    subprocess.run([
        sys.executable, "-m", "pip", "install", "--no-index", "--no-deps", "--force-reinstall", str(wheel),
    ], cwd=ROOT, env=environment, check=True)
    subprocess.run([sys.executable, "-m", "pip", "check"], cwd=ROOT, env=environment, check=True)
    subprocess.run([
        sys.executable, "-I", "tests/inspect_python_package.py", str(wheel),
    ], cwd=ROOT, env=environment, check=True)
    subprocess.run([sys.executable, "-I", "tests/check_python_contracts.py"], cwd=ROOT, env=environment, check=True)


if __name__ == "__main__":
    main()
