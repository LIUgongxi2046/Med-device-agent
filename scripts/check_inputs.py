import argparse
import json
import os
from pathlib import Path
import subprocess
import sys


ROOT = Path(__file__).resolve().parents[1]
from check_support import capture_check_path
CAPTURE = capture_check_path()


def main():
    parser = argparse.ArgumentParser(description="保存真实来源、许可和开发记录检查证据")
    parser.add_argument("--execution", required=True)
    args = parser.parse_args()
    if Path(args.execution).name != args.execution or args.execution in (".", ".."):
        raise ValueError("执行身份必须为单一目录名称")
    inputs = {"scripts/check_inputs.py", "tests/test_implementation_inputs.py", "data-design/dataset.schema.json",
              "data-design/sample_dataset.json", "docs/implementation-inputs.md", "docs/implementation-environment.md",
              "docs/development/index.md", "实施任务计划.md", "docs/development/IMP-04.md",
              "docs/development/IMP-06.md", "docs/development/IMP-08.md", "docs/development/IMP-11.md", "docs/development/IMP-12.md", "docs/development/IMP-37.md",
              "docs/commands.md", "docs/testing/full-functional-report.md", "docs/implementation-continuation.md"}
    dataset = json.loads((ROOT / "data-design/sample_dataset.json").read_bytes())
    inputs.update(str(path.relative_to(ROOT)) for path in (ROOT / "docs/development").glob("IMP-*.md"))
    for source in dataset["sources"]:
        path = (ROOT / source["source_ref"]).resolve(strict=True)
        inputs.add(str(path.relative_to(ROOT)))
    command = [sys.executable, str(CAPTURE), "--output-dir", ".implementation-work/IC-02-LOCAL-" + args.execution,
               "--check-id", "IC-02-LOCAL", "--kind", "UNIT", "--baseline-id", "local-evidence-2026-09-28-v1",
               "--cwd", str(ROOT), "--timeout", "60"]
    for path in sorted(inputs):
        command.extend(["--input", path])
    environment = os.environ.copy()
    environment["TMPDIR"] = str(ROOT / ".implementation-work/migration-check-tmp")
    subprocess.run([*command, "--", sys.executable, "-m", "unittest", "discover", "-s", "tests",
                    "-p", "test_implementation_inputs.py", "-v"], cwd=ROOT, env=environment, check=True)


if __name__ == "__main__":
    main()
