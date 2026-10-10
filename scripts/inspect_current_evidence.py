import argparse
import hashlib
import json
from pathlib import Path
import subprocess

from local_runtime import verify_application_deployment


ROOT = Path(__file__).resolve().parents[1]


def evidence_path(value):
    path = (ROOT / value).resolve(strict=True)
    path.relative_to((ROOT / ".implementation-work").resolve(strict=True))
    return path


def main():
    parser = argparse.ArgumentParser(description="核对指定本地回归及补充回执的当前输入和实际产物")
    parser.add_argument("--summary", required=True)
    parser.add_argument("--receipt", action="append", default=[])
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    summary = evidence_path(args.summary)
    evidence = json.loads(summary.read_bytes())
    if evidence["scope"] != "REGISTERED_LOCAL_REGRESSION" or evidence["deployment"] != verify_application_deployment():
        raise ValueError("回归范围或者实际部署身份不匹配")
    object_command = [str(ROOT / ".implementation-work/object-storage-venv/bin/python"),
        "scripts/local_object_storage.py", "identity"]
    object_deployment = json.loads(subprocess.run(object_command, cwd=ROOT, capture_output=True,
        text=True, check=True, timeout=30).stdout)
    if evidence["object_storage_deployment"] != object_deployment:
        raise ValueError("回归对象服务与实际部署身份不匹配")
    for item in evidence["inputs"]:
        if hashlib.sha256(Path(item["path"]).read_bytes()).hexdigest() != item["sha256"]:
            raise ValueError("回归检查程序与当前文件不匹配：" + item["path"])
    if not evidence["checks"] or len({item["check_id"] for item in evidence["checks"]}) != len(evidence["checks"]):
        raise ValueError("回归检查身份缺少或重复")
    paths = []
    for item in evidence["checks"]:
        path = evidence_path(item["receipt"])
        if hashlib.sha256(path.read_bytes()).hexdigest() != item["sha256"]:
            raise ValueError("回归回执摘要不匹配")
        paths.append(path)
    paths.extend(evidence_path(value) for value in args.receipt)
    if len(paths) != len(set(paths)):
        raise ValueError("回执重复")
    result = []
    for path in paths:
        receipt = json.loads(path.read_bytes())
        if receipt["exit_code"] != 0 or not receipt["inputs_unchanged"]:
            raise ValueError("检查回执未通过：" + receipt["check_id"])
        for item in [*receipt["inputs"], *receipt["artifacts"]]:
            if hashlib.sha256(Path(item["path"]).read_bytes()).hexdigest() != item["sha256"]:
                raise ValueError("当前文件不符合回执：" + item["path"])
        result.append({"check_id": receipt["check_id"], "receipt": str(path.relative_to(ROOT)),
                       "sha256": hashlib.sha256(path.read_bytes()).hexdigest(), "inputs": len(receipt["inputs"]),
                       "artifacts": len(receipt["artifacts"]), "status": "PASS"})
    output = ROOT / args.output
    output.parent.resolve(strict=True).relative_to((ROOT / ".implementation-work").resolve(strict=True))
    with output.open("x", encoding="utf-8") as target:
        json.dump({"scope": "CURRENT_EVIDENCE_BYTES", "summary": str(summary.relative_to(ROOT)), "receipts": result}, target, ensure_ascii=False, indent=2)
    print(json.dumps({"result": "PASS", "receipts": len(result), "output": str(output.relative_to(ROOT))}))


if __name__ == "__main__":
    main()
