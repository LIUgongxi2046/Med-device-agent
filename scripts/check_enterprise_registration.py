import argparse
import json
import os
from pathlib import Path
import subprocess
import sys


ROOT = Path(__file__).resolve().parents[1]
CAPTURE = Path("/Users/liuhaoxian/.agents/skills/haonan-s000-pmp/scripts/capture_check.py")


def main():
    parser = argparse.ArgumentParser(description="核验真实所有者的企业登记入口和授权边界")
    parser.add_argument("--execution", required=True)
    args = parser.parse_args()
    if Path(args.execution).name != args.execution or args.execution in (".", ".."):
        raise ValueError("执行身份必须为单一目录名称")
    inputs = ["scripts/check_enterprise_registration.py", "scripts/local_runtime.py",
              "tests/test_enterprise_registration_runtime.py", "tests/test_local_identity_runtime.py",
              "backend-design/接口补充设计.md", "LLD-后端.md", "business/ruoyi-admin/target/ruoyi-admin.jar",
              ".implementation-work/runtime/application-deployment.json", ".implementation-work/runtime/service-identity.json"]
    deployment = json.loads((ROOT / inputs[-2]).read_bytes())
    artifact = Path(deployment["artifact"])
    if artifact.is_absolute() or ".." in artifact.parts or not artifact.is_relative_to(".implementation-work/runtime/applications"):
        raise ValueError("运行制品必须位于本项目的版本目录")
    inputs.append(str(artifact))
    for directory in ("business/medical-persistence/src", "business/medical-identity/src", "business/medical-data/src"):
        inputs.extend(sorted(str(path.relative_to(ROOT)) for path in (ROOT / directory).rglob("*") if path.is_file()))
    command = [sys.executable, str(CAPTURE), "--output-dir", ".implementation-work/IC-04-REGISTRATION-" + args.execution,
               "--check-id", "IC-04-REGISTRATION", "--kind", "INTEGRATION", "--baseline-id", "BACK-REGISTRATION-01",
               "--cwd", str(ROOT), "--timeout", "120"]
    for path in inputs:
        command.extend(["--input", path])
    environment = os.environ.copy()
    environment["TMPDIR"] = str(ROOT / ".implementation-work/migration-check-tmp")
    subprocess.run([*command, "--", sys.executable, "tests/test_enterprise_registration_runtime.py"],
                   cwd=ROOT, env=environment, check=True)


if __name__ == "__main__":
    main()
