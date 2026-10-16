import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
from uuid import uuid4

import rfc8785

from local_source_processing import ROOT, RUNTIME, save


def main():
    parser = argparse.ArgumentParser(description="扫描登记官方原件并保存上传完成消费者的固定工程回执")
    parser.add_argument("--object-receipt", required=True)
    args = parser.parse_args()
    object_path = Path(args.object_receipt).resolve(strict=True)
    object_path.relative_to(ROOT / ".implementation-work/source-completion-object-checks")
    object_bytes = object_path.read_bytes()
    objects = json.loads(object_bytes)
    if objects["permission"] != "LOCAL_PARSER_CHECK" or objects["input_data_kind"] != "REGISTERED" \
            or objects["customer_upload_completed"] is not False or objects["professional_approval_created"] is not False:
        raise ValueError("COMPLETION_SCAN_CHECK_SCOPE_INVALID")
    original_path = Path(objects["original_path"]).resolve(strict=True)
    original_path.relative_to(ROOT / "ui-delivery/assets/public-evidence")
    original = original_path.read_bytes()
    if hashlib.sha256(original).hexdigest() != objects["original_sha256"] or str(len(original)) != objects["size_bytes"]:
        raise ValueError("COMPLETION_SCAN_ORIGINAL_CHANGED")
    check_id = str(uuid4())
    work = ROOT / ".implementation-work/source-completion-scan-checks" / check_id
    work.mkdir(mode=0o700, parents=True)
    source = work / original_path.name
    save(source, original)
    output = work / "output"
    output.mkdir(mode=0o700)
    release = RUNTIME / "scanner-release.json"
    release_bytes = release.read_bytes()
    command = {"schema_version": "1.0.0", "event_id": check_id, "operation_id": str(uuid4()),
        "upload_id": str(uuid4()), "worker_id": str(uuid4()), "lease_generation": "1", "source_path": str(source),
        "original_sha256": objects["original_sha256"], "size_bytes": objects["size_bytes"],
        "scanner_release_path": str(release), "scanner_release_sha256": hashlib.sha256(release_bytes).hexdigest(),
        "output_directory": str(output)}
    command_path = work / "command.json"
    result_path = work / "scan-receipt.json"
    log_path = work / "scanner.log"
    save(command_path, rfc8785.dumps(command))
    save(log_path, b"")
    environment = {"PATH": "/usr/bin:/bin", "LANG": "en_US.UTF-8", "LC_ALL": "en_US.UTF-8", "PYTHONNOUSERSITE": "1",
        "TMPDIR": str(work), "PYTHONPYCACHEPREFIX": str(work / "python-cache"),
        "OMP_NUM_THREADS": "1", "MKL_NUM_THREADS": "1", "OPENBLAS_NUM_THREADS": "1", "HF_HUB_OFFLINE": "1",
        "TRANSFORMERS_OFFLINE": "1", "HF_HOME": str(work / "model-cache"),
        "PYTHONPATH": str(ROOT / "document/src") + os.pathsep + str(ROOT / "contracts/python/src")}
    inputs = [object_path, original_path, release]
    inputs.extend(sorted((ROOT / "document/src/medical_document").rglob("*.py")))
    inputs.extend(sorted((ROOT / "contracts/python/src/medical_contracts").rglob("*.py")))
    before = [{"path": str(path), "sha256": hashlib.sha256(path.read_bytes()).hexdigest()} for path in inputs]
    with log_path.open("wb") as log:
        subprocess.run([sys.executable, "-m", "medical_document.scan_command", str(command_path), str(result_path)],
            cwd=work, env=environment, stdout=log, stderr=subprocess.STDOUT, timeout=300, check=True)
    result = json.loads(result_path.read_bytes())
    if result["status"] != "SCANNED" or result["returncode"] != 0 \
            or result["command_sha256"] != hashlib.sha256(command_path.read_bytes()).hexdigest() \
            or source.read_bytes() != original or object_path.read_bytes() != object_bytes:
        raise ValueError("COMPLETION_SCAN_RESULT_MISMATCH")
    if before != [{"path": str(path), "sha256": hashlib.sha256(path.read_bytes()).hexdigest()} for path in inputs]:
        raise ValueError("COMPLETION_SCAN_CHECK_INPUT_CHANGED")
    artifacts = [source, command_path, result_path, log_path, *sorted(output.iterdir())]
    receipt = {"schema_version": "1.0.0", "check_id": check_id, "permission": "LOCAL_PARSER_CHECK",
        "input_data_kind": "REGISTERED", "inputs": before, "command_path": str(command_path),
        "scan_receipt_path": str(result_path), "object_receipt_path": str(object_path),
        "artifacts": [{"path": str(path), "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
            "size_bytes": str(path.stat().st_size)} for path in artifacts], "process_environment": environment,
        "customer_upload_completed": False, "professional_approval_created": False}
    save(work / "receipt.json", rfc8785.dumps(receipt))
    print(json.dumps({"receipt": str(work / "receipt.json"), "status": result["status"],
        "original_sha256": result["original_sha256"], "artifact_count": len(result["artifacts"])}))


if __name__ == "__main__":
    main()
