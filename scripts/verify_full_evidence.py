import hashlib
import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def main():
    summary = ROOT / ".implementation-work/FULL-REGRESSION-20261003-FINAL-01/evidence.json"
    evidence = json.loads(summary.read_bytes())
    paths = [ROOT / entry["receipt"] for entry in evidence["checks"]]
    paths.extend(ROOT / ".implementation-work" / identity / "receipt.json" for identity in [
        "IC-03-JAVA-20261003-FULL-03", "IC-03-PYTHON-20261003-FULL-01",
        "IC-01-PYTHON-PACKAGE-20261003-FULL-01", "IC-03-TS-TYPES-20261003-FULL-01",
        "IC-03-TS-20261003-FULL-01"])
    result = []
    for path in paths:
        receipt = json.loads(path.read_bytes())
        if receipt["exit_code"] != 0 or not receipt["inputs_unchanged"]:
            raise ValueError("检查回执未通过：" + receipt["check_id"])
        for item in [*receipt["inputs"], *receipt["artifacts"]]:
            if hashlib.sha256(Path(item["path"]).read_bytes()).hexdigest() != item["sha256"]:
                raise ValueError("当前文件不符合回执：" + item["path"])
        result.append({"check_id": receipt["check_id"], "path": str(path.relative_to(ROOT)),
                       "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
                       "inputs": len(receipt["inputs"]), "artifacts": len(receipt["artifacts"]),
                       "started_at": receipt["started_at"], "ended_at": receipt["ended_at"], "status": "PASS"})
    output = ROOT / ".implementation-work/full-functional-20261003/verified-evidence.json"
    output.write_text(json.dumps({"scope": "CURRENT_EVIDENCE_BYTES", "receipts": result}, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"result": "PASS", "receipts": len(result), "output": str(output.relative_to(ROOT))}))


if __name__ == "__main__":
    main()
