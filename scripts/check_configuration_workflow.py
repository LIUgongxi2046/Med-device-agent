"""Run only the synthetic model/rule/discovery HTTP acceptance slice on an initialized isolated runtime.

Prerequisites are supplied by the operator: fresh MySQL 8.4/Redis, current migrations,
the existing local bootstrap account, and a private additional configuration containing
the dedicated discovery cursor key. This runner never initializes services or keys.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import stat
import subprocess
import time
import xml.etree.ElementTree as ET

from build_toolchains import resolve_toolchains


ROOT = Path(__file__).resolve().parents[1]
TEST = "ConfigurationWorkflowHttpTest"


def private_file(path):
    metadata = path.lstat()
    if not stat.S_ISREG(metadata.st_mode) or metadata.st_uid != os.getuid() or stat.S_IMODE(metadata.st_mode) != 0o600:
        raise ValueError("ISOLATED_CONFIGURATION_FILE_MUST_BE_OWNED_REGULAR_MODE_0600")
    path.resolve(strict=True).relative_to(ROOT)
    return path.resolve(strict=True)


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def inputs():
    paths = [ROOT / "scripts/check_configuration_workflow.py", ROOT / "scripts/build_toolchains.py",
             ROOT / "business/pom.xml", ROOT / "contracts/catalogue.json",
             ROOT / "contracts/extensions/evaluation-results.catalogue.json"]
    for module in ("medical-contracts", "medical-data", "medical-identity", "medical-persistence", "ruoyi-admin"):
        paths.append(ROOT / "business" / module / "pom.xml")
        paths.extend(p for p in (ROOT / "business" / module / "src").rglob("*") if p.is_file())
    return {str(p.relative_to(ROOT)): digest(p) for p in sorted(set(paths))}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--execution", required=True, help="New single-component evidence directory identity")
    parser.add_argument("--additional-config", required=True, type=Path,
                        help="Existing project-owned mode-0600 properties/YAML with fresh discovery cursor settings")
    args = parser.parse_args()
    if Path(args.execution).name != args.execution or args.execution in (".", ".."):
        raise ValueError("EXECUTION_MUST_BE_A_NEW_SINGLE_DIRECTORY_NAME")
    config = private_file(args.additional_config if args.additional_config.is_absolute() else ROOT / args.additional_config)
    runtime = ROOT / ".implementation-work/runtime"
    for name in ("service-secrets", "local-management-secret", "invitation-response-secret", "ruoyi-token-secret", "owner-credentials"):
        private_file(runtime / (name + ".json"))
    identity = json.loads((runtime / "service-identity.json").read_bytes())
    if identity.get("database") != "medical_local" or not str(identity.get("version", "")).startswith("8.4."):
        raise ValueError("EXPECTED_FRESH_MEDICAL_LOCAL_MYSQL_84_IDENTITY")
    output = ROOT / ".implementation-work" / ("IC-CONFIGURATION-WORKFLOW-" + args.execution)
    output.mkdir(parents=True, mode=0o700, exist_ok=False)
    temporary = output / "tmp"
    temporary.mkdir(mode=0o700)
    java_home, maven = resolve_toolchains(ROOT)
    before = inputs()
    environment = os.environ.copy()
    environment.update(JAVA_HOME=str(java_home), TMPDIR=str(temporary))
    command = [str(maven)]
    if environment.get("MEDICAL_MAVEN_SETTINGS"):
        command.extend(["--settings", str(Path(environment["MEDICAL_MAVEN_SETTINGS"]).resolve(strict=True))])
    command.extend(["-f", "business/pom.xml", "--batch-mode", "--no-transfer-progress",
        "-Dmaven.repo.local=" + str(ROOT / ".implementation-work/maven-repository"), "-pl", "ruoyi-admin", "-am",
        "-Dtest=" + TEST, "-Dsurefire.failIfNoSpecifiedTests=false",
        "-Dmedical.configurationWorkflowConfig=" + str(config),
        "-Dmedical.configurationWorkflowEvidence=" + str(output / "workflow-result.json"), "test"])
    started = time.time()
    with (output / "command.log").open("xb") as log:
        os.fchmod(log.fileno(), 0o600)
        completed = subprocess.run(command, cwd=ROOT, env=environment, stdout=log, stderr=subprocess.STDOUT, timeout=600)
    report = ROOT / "business/ruoyi-admin/target/surefire-reports/TEST-com.ruoyi.medical.runtime.ConfigurationWorkflowHttpTest.xml"
    summary = {"scope": "SIMULATION_MODEL_RULE_DISCOVERY_HTTP", "exit_code": completed.returncode,
               "inputs_unchanged": inputs() == before, "input_sha256": before, "customer_success_verified": False,
               "provider_calls": False, "production_eligible": False,
               "database_server_uuid": identity["server_uuid"], "duration_seconds": round(time.time()-started, 3)}
    if report.is_file() and report.stat().st_mtime >= started:
        target = output / report.name
        target.write_bytes(report.read_bytes())
        suite = ET.fromstring(target.read_bytes())
        summary["junit"] = {key: int(suite.attrib[key]) for key in ("tests", "failures", "errors", "skipped")}
        summary["junit_sha256"] = digest(target)
    evidence = output / "workflow-result.json"
    passed = completed.returncode == 0 and summary["inputs_unchanged"] and summary.get("junit") == {
        "tests": 1, "failures": 0, "errors": 0, "skipped": 0} and evidence.is_file()
    if evidence.is_file():
        summary["workflow_evidence_sha256"] = digest(evidence)
    summary["result"] = "PASS" if passed else "FAIL"
    (output / "receipt.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"result": summary["result"], "scope": summary["scope"],
                      "evidence_directory": str(output.relative_to(ROOT)), "junit": summary.get("junit")}, ensure_ascii=False))
    raise SystemExit(0 if passed else 1)


if __name__ == "__main__":
    main()
