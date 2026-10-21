import argparse
import importlib.metadata
import os
from pathlib import Path
import sys
import uuid

from local_object_storage import ROOT, DIRECTORY, TOOLCHAIN, verify_identity


from check_support import capture_check_path
CAPTURE = capture_check_path()
sys.path.insert(0, str(CAPTURE.parent))
from capture_check import run_check


def main():
    parser = argparse.ArgumentParser(description="保存真实私有存储、固定对象版本及公开原件检查证据")
    parser.add_argument("--execution", required=True)
    parser.add_argument("--recovery-from", type=Path)
    args = parser.parse_args()
    if Path(args.execution).name != args.execution or args.execution in (".", ".."):
        raise ValueError("OBJECT_STORAGE_EXECUTION_INVALID")
    deployment = verify_identity()
    directory = ROOT / (".implementation-work/IC-05-OBJECT-STORAGE-" + args.execution)
    inputs = ["scripts/check_object_storage.py", "scripts/local_object_storage.py", "tests/test_object_storage_runtime.py",
        "tests/object-storage-requirements.lock", "data-design/sample_dataset.json", "HLD.md", "LLD-数据.md", "LLD-后端.md",
        "ui-delivery/assets/public-evidence/clinical-evaluation-2021.docx",
        "ui-delivery/assets/public-evidence/safety-principles-2021.doc",
        str(DIRECTORY / "deployment.json"), str(TOOLCHAIN / "installation.json"), str(TOOLCHAIN / "weed"),
        str(TOOLCHAIN / "darwin_amd64.tar.gz"), str(TOOLCHAIN / "LICENSE"), str(CAPTURE)]
    test = "tests/test_object_storage_runtime.py"
    check_id = "IC-05-OBJECT-STORAGE"
    if args.recovery_from is not None:
        origin = args.recovery_from.resolve(strict=True)
        if not origin.is_file() or not origin.is_relative_to(ROOT / ".implementation-work"):
            raise ValueError("OBJECT_STORAGE_RECOVERY_RECEIPT_INVALID")
        inputs.extend([str(origin), "tests/test_object_storage_recovery.py"])
        test = "tests/test_object_storage_recovery.py"
        check_id = "IC-05-OBJECT-STORAGE-RECOVERY"
    # 使用实际安装文件绑定客户端，秘密文件不进入普通回执。
    for name in ("boto3", "botocore", "s3transfer", "jmespath", "python-dateutil", "six", "urllib3"):
        distribution = importlib.metadata.distribution(name)
        for item in distribution.files:
            path = Path(distribution.locate_file(item)).resolve()
            if path.is_file() and path.suffix != ".pyc":
                inputs.append(str(path))
    environment = os.environ.copy()
    environment.update({"TMPDIR": str(ROOT / ".implementation-work/object-storage-tmp"),
        "MEDICAL_OBJECT_STORAGE_EXECUTION": str(uuid.uuid4()), "MEDICAL_OBJECT_STORAGE_RESULT": str(directory / "objects.json")})
    if args.recovery_from is not None:
        environment["MEDICAL_OBJECT_STORAGE_RECOVERY_FROM"] = str(origin)
    os.environ.update(environment)
    result = run_check(argparse.Namespace(output_dir=directory, check_id=check_id, kind="INTEGRATION",
        baseline_id="BACK-OBJECT-STORAGE-01", cwd=ROOT, timeout=180, input=sorted(set(inputs)),
        artifact=[directory / "objects.json"], command=[sys.executable, test]))
    if deployment != verify_identity():
        raise ValueError("OBJECT_STORAGE_CHECK_DEPLOYMENT_CHANGED")
    raise SystemExit(result)


if __name__ == "__main__":
    main()
