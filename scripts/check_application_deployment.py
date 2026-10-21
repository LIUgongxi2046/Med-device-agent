import argparse
import json
import os
from pathlib import Path
import sys

from local_runtime import ROOT, verify_application_deployment


from check_support import capture_check_path
CAPTURE = capture_check_path()
sys.path.insert(0, str(CAPTURE.parent))
from capture_check import run_check


def main():
    parser = argparse.ArgumentParser(description="核验当前完整构建、运行进程与全部发布静态文件")
    parser.add_argument("--execution", required=True)
    parser.add_argument("--build-receipt", type=Path, required=True)
    args = parser.parse_args()
    if Path(args.execution).name != args.execution or args.execution in (".", ".."):
        raise ValueError("APPLICATION_DEPLOYMENT_EXECUTION_INVALID")
    receipt_path = args.build_receipt.resolve(strict=True)
    if not receipt_path.is_relative_to(ROOT / ".implementation-work"):
        raise ValueError("APPLICATION_BUILD_RECEIPT_LOCATION_INVALID")
    receipt = json.loads(receipt_path.read_bytes())
    if receipt["check_id"] != "IC-03-JAVA" or receipt["exit_code"] != 0 or receipt["inputs_unchanged"] is not True:
        raise ValueError("APPLICATION_BUILD_NOT_VERIFIED")
    deployment = verify_application_deployment()
    pointer = ROOT / ".implementation-work/java-runtime-checks/current.json"
    summary_path = Path(json.loads(pointer.read_bytes())["report"]).resolve(strict=True)
    summary = json.loads(summary_path.read_bytes())
    inputs = {str(receipt_path), str(pointer), str(summary_path), str(CAPTURE),
        "scripts/check_application_deployment.py", "scripts/local_runtime.py", "tests/test_application_deployment_runtime.py",
        ".implementation-work/runtime/application-deployment.json", ".implementation-work/runtime/application.pid",
        ".implementation-work/runtime/service-identity.json", deployment["artifact"], deployment["source"]}
    inputs.update(item["path"] for item in receipt["inputs"] + receipt["artifacts"] + summary["artifacts"])
    directory = ROOT / (".implementation-work/IC-06-APPLICATION-DEPLOYMENT-" + args.execution)
    os.environ.update({"TMPDIR": str(ROOT / ".implementation-work/migration-check-tmp"),
        "MEDICAL_APPLICATION_BUILD_RECEIPT": str(receipt_path),
        "MEDICAL_APPLICATION_DEPLOYMENT_RESULT": str(directory / "deployment-result.json")})
    result = run_check(argparse.Namespace(output_dir=directory, check_id="IC-06-APPLICATION-DEPLOYMENT", kind="INTEGRATION",
        baseline_id="FRONT-RUOYI-LOGIN-01", cwd=ROOT, timeout=600, input=sorted(inputs),
        artifact=[directory / "deployment-result.json"], command=[sys.executable, "tests/test_application_deployment_runtime.py"]))
    if deployment != verify_application_deployment():
        raise ValueError("APPLICATION_DEPLOYMENT_CHANGED_DURING_CHECK")
    raise SystemExit(result)


if __name__ == "__main__":
    main()
