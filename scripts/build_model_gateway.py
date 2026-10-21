import argparse
import importlib.metadata
import json
import os
from pathlib import Path
import subprocess
import sys
import tomllib
import zipfile


ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description="构建并核验实际模型适配器wheel")
    parser.add_argument("--execution", required=True)
    args = parser.parse_args()
    if Path(args.execution).name != args.execution or args.execution in (".", ".."):
        raise ValueError("执行身份必须为单一目录名称")
    output = ROOT / ".implementation-work/model-wheels" / args.execution
    output.mkdir(parents=True)
    environment = os.environ.copy()
    environment["TMPDIR"] = str(ROOT / ".implementation-work/migration-check-tmp")
    subprocess.run([sys.executable, "-m", "pip", "check"], cwd=ROOT, env=environment, check=True)
    subprocess.run([sys.executable, "-m", "pip", "wheel", "--no-deps", "--no-build-isolation",
                    "--wheel-dir", str(output), str(ROOT / "model-gateway")],
                   cwd=ROOT, env=environment, check=True)
    wheel = output / "medical_model_gateway-1.0.0-py3-none-any.whl"
    sources = sorted((ROOT / "model-gateway/src/medical_model_gateway").rglob("*.py"))
    configuration = tomllib.loads((ROOT / "model-gateway/pyproject.toml").read_text())
    scripts = configuration["tool"]["setuptools"].get("script-files", [])
    script_sources = {"medical_model_gateway-1.0.0.data/scripts/" + Path(name).name: ROOT / "model-gateway" / name for name in scripts}
    if len(script_sources) != len(scripts) or any(not path.is_file() or path.parent != ROOT / "model-gateway" for path in script_sources.values()):
        raise ValueError("wheel入口源码集合不完整")
    with zipfile.ZipFile(wheel) as artifact:
        packaged = {name for name in artifact.namelist() if name.endswith(".py")}
        expected = {str(path.relative_to(ROOT / "model-gateway/src")) for path in sources}
        expected.update(script_sources)
        if packaged != expected:
            raise ValueError("wheel源码集合不完整")
        for path in sources:
            if artifact.read(str(path.relative_to(ROOT / "model-gateway/src"))) != path.read_bytes():
                raise ValueError("wheel源码字节与当前文件不一致")
        for name, path in script_sources.items():
            if artifact.read(name) != path.read_bytes():
                raise ValueError("wheel入口字节与当前文件不一致")
    target = output / "installed"
    subprocess.run([sys.executable, "-m", "pip", "install", "--no-deps", "--no-index",
                    "--target", str(target), str(wheel)], cwd=ROOT, env=environment, check=True)
    subprocess.run([sys.executable, "-I", str(ROOT / "tests/inspect_model_package.py"), str(target)],
                   cwd=ROOT, env=environment, check=True)
    result = {"wheel": str(wheel.relative_to(ROOT)), "packaged_sources": len(sources),
              "sdk_version": importlib.metadata.version("openai"),
              "http_version": importlib.metadata.version("httpx2"), "isolated_install_checks": "PASS"}
    (output / "build.json").write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(result, ensure_ascii=False))


if __name__ == "__main__":
    main()
