import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
from uuid import NAMESPACE_URL, uuid4, uuid5

import rfc8785
import yaml

from local_source_processing import ROOT, RUNTIME, save


def main():
    parser = argparse.ArgumentParser(description="通过实际C09命令生成可供Java核验的登记原件解析包")
    parser.add_argument("--installed", action="store_true")
    parser.add_argument("--profile")
    args = parser.parse_args()
    profile_path = RUNTIME / "application-source-processing.yaml" if args.profile is None else Path(args.profile).absolute()
    profile_path.resolve(strict=True).relative_to(RUNTIME)
    profile = yaml.safe_load(profile_path.read_bytes())["medical"]["source-processing"]
    for file in profile["source-files"]:
        if hashlib.sha256((ROOT / file["path"]).read_bytes()).hexdigest() != file["sha256"]:
            raise ValueError("PROCESSING_SOURCE_CHANGED")
    original = ROOT / "ui-delivery/assets/public-evidence/clinical-evaluation-2021.docx"
    original_sha256 = hashlib.sha256(original.read_bytes()).hexdigest()
    release = RUNTIME / "scanner-release.json"
    release_sha256 = hashlib.sha256(release.read_bytes()).hexdigest()
    check_id = str(uuid4())
    work = ROOT / ".implementation-work/source-processing-checks" / check_id
    work.mkdir(mode=0o700, parents=True)
    output = work / "output"
    output.mkdir(mode=0o700)
    command = {"schema_version": "1.0.0", "attempt_id": check_id, "run_id": str(uuid4()),
        "source_version": str(uuid5(NAMESPACE_URL, "LOCAL_PARSER_CHECK:" + original_sha256)), "parse_version": str(uuid4()),
        "config_revision": profile["revision"], "config_sha256": hashlib.sha256(rfc8785.dumps(profile)).hexdigest(),
        "worker_id": str(uuid4()), "lease_generation": "1", "format": "DOCX", "original_sha256": original_sha256,
        "size_bytes": str(original.stat().st_size), "source_path": str(original), "catalogue_root": str(ROOT),
        "scanner_release_path": str(release), "scanner_release_sha256": release_sha256, "output_directory": str(output)}
    command_path = work / "command.json"
    result_path = work / "result.json"
    save(command_path, rfc8785.dumps(command))
    environment = {"PATH": "/usr/bin:/bin", "LANG": "en_US.UTF-8", "LC_ALL": "en_US.UTF-8", "PYTHONNOUSERSITE": "1",
        "PYTHONPYCACHEPREFIX": str(work / "python-cache"), "OMP_NUM_THREADS": "1", "MKL_NUM_THREADS": "1", "OPENBLAS_NUM_THREADS": "1"}
    environment.update({"TMPDIR": str(work), "HF_HUB_OFFLINE": "1", "TRANSFORMERS_OFFLINE": "1",
        "HF_HOME": str(work / "model-cache"),
        "PYTHONPATH": "" if args.installed else str(ROOT / "document/src") + os.pathsep + str(ROOT / "contracts/python/src")})
    log_path = work / "processor.log"
    save(log_path, b"")
    with log_path.open("wb") as log:
        subprocess.run([sys.executable, "-m", "medical_document.processing_command", str(command_path), str(result_path)],
            cwd=work, env=environment, stdout=log, stderr=subprocess.STDOUT, timeout=900, check=True)
    result = json.loads(result_path.read_bytes())
    bundle_path = Path(result["bundle_path"])
    bundle = json.loads(bundle_path.read_bytes())
    if result["command_sha256"] != hashlib.sha256(command_path.read_bytes()).hexdigest() \
            or result["bundle_sha256"] != hashlib.sha256(bundle_path.read_bytes()).hexdigest() \
            or result["node_count"] != len(bundle["nodes"]) or result["chunk_count"] != len(bundle["chunks"]):
        raise ValueError("PROCESSING_RESULT_MISMATCH")
    for file in profile["source-files"]:
        if hashlib.sha256((ROOT / file["path"]).read_bytes()).hexdigest() != file["sha256"]:
            raise ValueError("PROCESSING_SOURCE_CHANGED")
    evidence = {"schema_version": "1.0.0", "check_id": check_id, "input_data_kind": "REGISTERED", "permission": "LOCAL_PARSER_CHECK",
        "command_path": str(command_path), "result_path": str(result_path), "bundle_path": str(bundle_path),
        "original_sha256": original_sha256, "installed": args.installed,
        "profile_path": str(profile_path), "profile_sha256": hashlib.sha256(profile_path.read_bytes()).hexdigest(),
        "source_files": profile["source-files"], "process_environment": environment,
        "artifacts": [{"path": str(path), "sha256": hashlib.sha256(path.read_bytes()).hexdigest(), "size_bytes": str(path.stat().st_size)}
            for path in sorted((command_path, result_path, bundle_path, log_path, *output.glob("*.txt")))],
        "node_count": result["node_count"], "chunk_count": result["chunk_count"], "issue_count": result["issue_count"],
        "status": result["status"], "customer_processing_verified": False, "professional_approval_created": False}
    evidence_path = work / "receipt.json"
    save(evidence_path, rfc8785.dumps(evidence))
    print(json.dumps({"evidence": str(evidence_path), "command_path": str(command_path), "result_path": str(result_path),
        "node_count": result["node_count"], "status": result["status"]}))


if __name__ == "__main__":
    main()
