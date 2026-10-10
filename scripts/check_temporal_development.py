import argparse
import os
from pathlib import Path
import subprocess
import sys


ROOT = Path(__file__).resolve().parents[1]
CAPTURE = Path("/Users/liuhaoxian/.agents/skills/haonan-s000-pmp/scripts/capture_check.py")


def main():
    parser = argparse.ArgumentParser(description="保存官方Temporal本地持久服务的实际生命周期证据")
    parser.add_argument("--execution", required=True)
    args = parser.parse_args()
    if Path(args.execution).name != args.execution or args.execution in (".", ".."):
        raise ValueError("执行身份必须使用单一目录名称")
    output = ROOT / ".implementation-work" / ("IC-13-TEMPORAL-DEV-" + args.execution)
    inputs = ["scripts/check_temporal_development.py", "scripts/local_temporal.py", "tests/test_temporal_development_runtime.py",
        "HLD.md", "LLD-后端.md", "LLD-Agent.md", ".implementation-work/temporal-install-20261008/release.json",
        ".implementation-work/temporal-install-20261008/temporal_cli_1.9.1_darwin_amd64.tar.gz",
        ".implementation-work/toolchains/temporal-cli-1.9.1/temporal", ".implementation-work/toolchains/temporal-cli-1.9.1/LICENSE"]
    command = [sys.executable, str(CAPTURE), "--output-dir", str(output / "command"), "--check-id", "IC-13-TEMPORAL-DEV",
        "--kind", "INTEGRATION", "--baseline-id", "ACTUAL_LOCAL_TEMPORAL_SERVICE", "--cwd", str(ROOT), "--timeout", "180"]
    for filename in inputs:
        command.extend(["--input", filename])
    command.extend(["--artifact", str(output / "temporal-result.json"), "--", sys.executable,
        "tests/test_temporal_development_runtime.py", "--output", str(output)])
    temporary = ROOT / ".implementation-work/temporal-check-tmp"
    temporary.mkdir(parents=True, exist_ok=True)
    environment = os.environ.copy()
    environment["TMPDIR"] = str(temporary)
    subprocess.run(command, cwd=ROOT, env=environment, check=True)


if __name__ == "__main__":
    main()
