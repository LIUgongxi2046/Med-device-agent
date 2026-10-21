import argparse
import json
import os
from pathlib import Path
import subprocess
import sys
from uuid import UUID
from xml.etree import ElementTree

import check_source_index_build as builds


ROOT = builds.ROOT
from check_support import capture_check_path
CAPTURE = capture_check_path()
TESTS = {"RunTokenSigningKeyStoreTest": 4, "RunTokenPolicyStoreTest": 18, "RunTokenSignatureTest": 42}


def main():
    parser = argparse.ArgumentParser(description="固定C02当前私钥及原密码组件验证输入")
    parser.add_argument("--execution", required=True)
    args = parser.parse_args()
    if str(UUID(args.execution)) != args.execution:
        raise ValueError("SIGNING_KEY_CHECK_IDENTITY_REQUIRED")
    directory = ROOT / ".implementation-work/run-token-signing-key-build-checks" / args.execution
    paths = {Path(__file__).resolve(), ROOT / "business/pom.xml", ROOT / "data-design/sample_dataset.json"}
    for module in ("medical-contracts", "medical-identity", "ruoyi-common", "ruoyi-system", "ruoyi-framework"):
        base = ROOT / "business" / module
        paths.add(base / "pom.xml")
        paths.update(path for path in (base / "src/main").rglob("*") if path.is_file())
    for name in TESTS:
        paths.add(ROOT / "business/medical-identity/src/test/java/com/ruoyi/medical/identity" / (name + ".java"))
    dataset = json.loads((ROOT / "data-design/sample_dataset.json").read_bytes())
    paths.add(ROOT / dataset["sources"][0]["source_ref"])
    capture = [sys.executable, str(CAPTURE), "--output-dir", str(directory), "--check-id", "IC-04-RUN-TOKEN-SIGNING-KEY",
        "--kind", "UNIT", "--baseline-id", "RUN-TOKEN-SIGNING-KEY-01", "--cwd", str(ROOT / "business"), "--timeout", "600"]
    for path in sorted(paths):
        capture.extend(("--input", str(path)))
    reports = ROOT / "business/medical-identity/target/surefire-reports"
    for name in TESTS:
        capture.extend(("--artifact", str(reports / ("TEST-com.ruoyi.medical.identity." + name + ".xml"))))
    command = [str(builds.MAVEN), "-o", "--batch-mode", "--no-transfer-progress", "-f", str(ROOT / "business/pom.xml"),
        "-Dmaven.repo.local=" + str(ROOT / ".implementation-work/maven-repository"), "-pl", "medical-identity", "-am",
        "-Dtest=" + ",".join(TESTS), "-Dsurefire.failIfNoSpecifiedTests=false", "package"]
    environment = os.environ.copy()
    environment.update({"LANG": "en_US.UTF-8", "LC_ALL": "en_US.UTF-8", "JAVA_HOME": str(builds.JAVA_HOME_PATH),
        "TMPDIR": str(ROOT / ".implementation-work/migration-check-tmp"),
        "MAVEN_OPTS": "-Dfile.encoding=UTF-8 -Djava.io.tmpdir=" + str(ROOT / ".implementation-work/java-temp")})
    result = subprocess.run([*capture, "--", *command], cwd=ROOT, env=environment, check=False)
    if result.returncode != 0:
        raise SystemExit(result.returncode)
    for name, expected in TESTS.items():
        source = reports / ("TEST-com.ruoyi.medical.identity." + name + ".xml")
        report = ElementTree.parse(source).getroot()
        if {key: int(report.attrib[key]) for key in ("tests", "failures", "errors", "skipped")} \
                != {"tests": expected, "failures": 0, "errors": 0, "skipped": 0}:
            raise ValueError("SIGNING_KEY_REQUIRED_ASSERTIONS_FAILED")
        target = directory / source.name
        target.write_bytes(source.read_bytes())
        target.chmod(0o600)
    print(str(directory.relative_to(ROOT) / "receipt.json"), flush=True)


if __name__ == "__main__":
    main()
