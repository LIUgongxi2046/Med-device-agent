import argparse
import os
from pathlib import Path
import subprocess
import sys


ROOT = Path(__file__).resolve().parents[1]
CAPTURE = Path("/Users/liuhaoxian/.agents/skills/haonan-s000-pmp/scripts/capture_check.py")


def main():
    parser = argparse.ArgumentParser(description="核查实际模板完整定义、附件及原版本摘要的保存约束")
    parser.add_argument("--execution", required=True)
    args = parser.parse_args()
    if Path(args.execution).name != args.execution or args.execution in (".", ".."):
        raise ValueError("执行身份必须使用单一目录名称")
    inputs = ["scripts/check_template_storage.py", "scripts/local_runtime.py", "tests/test_template_storage_runtime.py",
              "PRD.md", "LLD-数据.md", "LLD-后端.md", "backend-design/接口补充设计.md",
              ".implementation-work/runtime/service-identity.json"]
    for module in ("medical-contracts", "medical-persistence", "medical-data"):
        inputs.extend(str(path.relative_to(ROOT)) for path in sorted((ROOT / "business" / module / "src").rglob("*")) if path.is_file())
    command = [sys.executable, str(CAPTURE), "--output-dir", ".implementation-work/IC-11-STORAGE-" + args.execution,
               "--check-id", "IC-11-STORAGE", "--kind", "INTEGRATION", "--baseline-id", "BACK-TEMPLATE-STORAGE-01",
               "--cwd", str(ROOT), "--timeout", "180"]
    for path in inputs:
        command.extend(["--input", path])
    environment = os.environ.copy()
    environment["TMPDIR"] = str(ROOT / ".implementation-work/migration-check-tmp")
    subprocess.run([*command, "--", sys.executable, "tests/test_template_storage_runtime.py"], cwd=ROOT, env=environment, check=True)


if __name__ == "__main__":
    main()
