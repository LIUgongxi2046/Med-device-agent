import argparse
import os
from pathlib import Path
import subprocess
import sys


ROOT = Path(__file__).resolve().parents[1]
from check_support import capture_check_path
CAPTURE = capture_check_path()


def main():
    parser = argparse.ArgumentParser(description="保存真实数据库中的企业身份绑定约束证据")
    parser.add_argument("--execution", required=True)
    args = parser.parse_args()
    if Path(args.execution).name != args.execution or args.execution in (".", ".."):
        raise ValueError("执行身份必须为单一目录名称")
    inputs = ["scripts/check_member_identity.py", "scripts/local_runtime.py",
              "tests/test_member_identity_runtime.py", "backend-design/接口补充设计.md",
              ".implementation-work/runtime/service-identity.json",
              "business/medical-data/target/medical-data-3.9.2.jar",
              "business/medical-data/target/medical-data-3.9.2-cli.jar"]
    inputs.extend(sorted(str(path.relative_to(ROOT)) for path in
                         (ROOT / "business/medical-data/src").rglob("*") if path.is_file()))
    command = [sys.executable, str(CAPTURE), "--output-dir", ".implementation-work/IC-04-MEMBER-IDENTITY-" + args.execution,
               "--check-id", "IC-04-MEMBER-IDENTITY", "--kind", "INTEGRATION",
               "--baseline-id", "BACK-MEMBER-IDENTITY-01", "--cwd", str(ROOT), "--timeout", "120"]
    for path in inputs:
        command.extend(["--input", path])
    command.extend(["--artifact", ".implementation-work/runtime/member-identity-receipt.json"])
    environment = os.environ.copy()
    environment["TMPDIR"] = str(ROOT / ".implementation-work/migration-check-tmp")
    subprocess.run([*command, "--", sys.executable, "tests/test_member_identity_runtime.py"],
                   cwd=ROOT, env=environment, check=True)


if __name__ == "__main__":
    main()
