import argparse
import json
import os
from pathlib import Path
import subprocess
import sys


ROOT = Path(__file__).resolve().parents[1]
CAPTURE = Path("/Users/liuhaoxian/.agents/skills/haonan-s000-pmp/scripts/capture_check.py")


def main():
    parser = argparse.ArgumentParser(description="保存 TypeScript 共享类型及真实来源检查证据")
    parser.add_argument("--check", choices=("typecheck", "test"), required=True)
    parser.add_argument("--execution", required=True)
    args = parser.parse_args()
    identity = Path(args.execution)
    if identity.name != args.execution or args.execution in (".", ".."):
        raise ValueError("执行身份必须为单一目录名称")

    package = ROOT / "contracts/typescript"
    catalogue = json.loads((ROOT / "contracts/catalogue.json").read_bytes())
    dataset = json.loads((ROOT / "data-design/sample_dataset.json").read_bytes())
    inputs = {"scripts/check_typescript.py", "contracts/catalogue.json", "data-design/sample_dataset.json", "LLD-后端.md"}
    inputs.update(entry["path"] for entry in catalogue["schemas"])
    inputs.update(str(path.relative_to(ROOT)) for directory in ("src", "tests") for path in (package / directory).rglob("*.ts"))
    inputs.update("contracts/typescript/" + name for name in ("package.json", "package-lock.json", "tsconfig.json"))
    for source in dataset["sources"]:
        path = (ROOT / source["source_ref"]).resolve(strict=True)
        inputs.add(str(path.relative_to(ROOT)))

    check_id = "IC-03-TS-TYPES" if args.check == "typecheck" else "IC-03-TS"
    kind = "STATIC" if args.check == "typecheck" else "UNIT"
    capture = [sys.executable, str(CAPTURE), "--output-dir", str(ROOT / ".implementation-work" / (check_id + "-" + args.execution)),
               "--check-id", check_id, "--kind", kind, "--baseline-id", "shared-schemas-and-migration-1.0.0",
               "--cwd", str(package), "--timeout", "60"]
    for path in sorted(inputs):
        capture.extend(["--input", str(ROOT / path)])

    environment = os.environ.copy()
    temporary = ROOT / ".implementation-work/typescript-temp"
    temporary.mkdir(parents=True, exist_ok=True)
    environment["TMPDIR"] = str(temporary)
    command = ["npm", "run", "typecheck"] if args.check == "typecheck" else ["npm", "test"]
    subprocess.run([*capture, "--", *command], cwd=ROOT, env=environment, check=True)


if __name__ == "__main__":
    main()
