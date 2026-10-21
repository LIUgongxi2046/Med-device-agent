import argparse
import os
from pathlib import Path
import subprocess
import sys

from local_runtime import verify_application_deployment


ROOT = Path(__file__).resolve().parents[1]
from check_support import capture_check_path
CAPTURE = capture_check_path()


def main():
    parser = argparse.ArgumentParser(description="核查实际登录会话下的模板草稿公开入口")
    parser.add_argument("--execution", required=True)
    args = parser.parse_args()
    if Path(args.execution).name != args.execution or args.execution in (".", ".."):
        raise ValueError("执行身份必须使用单一目录名称")
    inputs = ["scripts/check_template_runtime.py", "tests/test_template_runtime.py", "scripts/local_runtime.py",
              "tests/test_local_identity_runtime.py", "PRD.md", "LLD-数据.md", "LLD-后端.md",
              "backend-design/接口补充设计.md", ".implementation-work/runtime/service-identity.json",
              ".implementation-work/runtime/application-deployment.json", "business/ruoyi-admin/src/main/resources/application-medical-local.yml"]
    before = verify_application_deployment()
    inputs.append(before["artifact"])
    for module in ("medical-contracts", "medical-identity", "medical-persistence", "medical-data"):
        inputs.extend(str(path.relative_to(ROOT)) for path in sorted((ROOT / "business" / module / "src").rglob("*")) if path.is_file())
    capture = [sys.executable, str(CAPTURE), "--output-dir", ".implementation-work/IC-11-HTTP-" + args.execution,
               "--check-id", "IC-11-HTTP", "--kind", "INTEGRATION", "--baseline-id", "BACK-TEMPLATE-CREATE-01",
               "--cwd", str(ROOT), "--timeout", "180"]
    for path in inputs:
        capture.extend(["--input", path])
    environment = os.environ.copy()
    environment["TMPDIR"] = str(ROOT / ".implementation-work/migration-check-tmp")
    subprocess.run([*capture, "--", sys.executable, "tests/test_template_runtime.py"], cwd=ROOT, env=environment, check=True)
    if verify_application_deployment() != before:
        raise ValueError("模板检查期间应用运行身份发生变化")


if __name__ == "__main__":
    main()
