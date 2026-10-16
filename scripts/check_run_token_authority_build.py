import argparse
from datetime import datetime, timezone
import os
import subprocess
from uuid import uuid4
from xml.etree import ElementTree

import check_source_index_build as builds


ROOT = builds.ROOT
TESTS = {"RunTokenAuthoritySchemaIT": 5, "TenantAccessTest": 6, "PermissionStructureTest": 3,
    "RunTokenSigningKeyStoreTest": 4, "RunTokenPolicyStoreTest": 18, "RunTokenSignatureTest": 42}


def snapshot():
    values = builds.snapshot()
    for name in TESTS:
        module = "medical-identity" if name.startswith("RunToken") and name != "RunTokenAuthoritySchemaIT" else "medical-persistence"
        package = "identity" if module == "medical-identity" else "persistence"
        path = ROOT / "business" / module / "src/test/java/com/ruoyi/medical" / package / (name + ".java")
        values[str(path.relative_to(ROOT))] = builds.digest(path)
    for name in ("check_run_token_authority_build.py", "check_run_token_authority_migration.py"):
        values["scripts/" + name] = builds.digest(ROOT / "scripts" / name)
    return dict(sorted(values.items()))


def main():
    parser = argparse.ArgumentParser(description="核验持久运行令牌及当前制品")
    parser.add_argument("--check", choices=("data", "technical", "application"), required=True)
    args = parser.parse_args()
    directory = ROOT / ".implementation-work/run-token-authority-build-checks" / str(uuid4())
    directory.mkdir(mode=0o700, parents=True)
    before = snapshot()
    command = [str(builds.MAVEN), "-o", "--batch-mode", "--no-transfer-progress", "-f", str(ROOT / "business/pom.xml"),
        "-Dmaven.repo.local=" + str(ROOT / ".implementation-work/maven-repository")]
    if args.check == "technical":
        command.extend(["-pl", "medical-persistence", "-am", "-Dtest=" + ",".join(TESTS), "-Dsurefire.failIfNoSpecifiedTests=false", "package"])
    else:
        command.extend(["-pl", "medical-data" if args.check == "data" else "ruoyi-admin", "-am", "-DskipTests", "package"])
    environment = os.environ.copy()
    environment.update({"LANG": "en_US.UTF-8", "LC_ALL": "en_US.UTF-8", "JAVA_HOME": str(builds.JAVA_HOME_PATH),
        "TMPDIR": str(ROOT / ".implementation-work/migration-check-tmp"),
        "MAVEN_OPTS": "-Dfile.encoding=UTF-8 -Djava.io.tmpdir=" + str(ROOT / ".implementation-work/java-temp")})
    log = directory / "maven.log"
    with os.fdopen(os.open(log, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600), "wb") as output:
        result = subprocess.run(command, cwd=ROOT, env=environment, stdout=output, stderr=subprocess.STDOUT, check=False)
    after = snapshot()
    reports = []
    if args.check == "technical" and result.returncode == 0:
        for name, expected in TESTS.items():
            module = "medical-identity" if name.startswith("RunToken") and name != "RunTokenAuthoritySchemaIT" else "medical-persistence"
            package = "identity" if module == "medical-identity" else "persistence"
            path = ROOT / "business" / module / "target/surefire-reports" / ("TEST-com.ruoyi.medical." + package + "." + name + ".xml")
            counts = {key: int(ElementTree.parse(path).getroot().attrib[key]) for key in ("tests", "failures", "errors", "skipped")}
            if counts != {"tests": expected, "failures": 0, "errors": 0, "skipped": 0}:
                raise ValueError("RUN_AUTHORITY_REQUIRED_ASSERTIONS_FAILED")
            target = directory / path.name
            target.write_bytes(path.read_bytes())
            target.chmod(0o600)
            reports.append({"name": name, "counts": counts, "path": str(target.relative_to(ROOT)), "sha256": builds.digest(target)})
    artifacts = []
    if result.returncode == 0:
        module = "medical-data" if args.check == "data" else "medical-persistence" if args.check == "technical" else "ruoyi-admin"
        path = ROOT / "business" / module / "target" / ("ruoyi-admin.jar" if module == "ruoyi-admin" else module + "-3.9.2.jar")
        artifacts.append({"path": str(path.relative_to(ROOT)), "sha256": builds.digest(path), "size_bytes": path.stat().st_size})
    target = directory / "receipt.json"
    builds.save(target, {"schema_version": "1.0.0", "check": args.check, "verified_at": datetime.now(timezone.utc).isoformat(),
        "exit_code": result.returncode, "command": command, "inputs_before": before, "inputs_after": after, "inputs_unchanged": before == after,
        "reports": reports, "artifacts": artifacts, "log": {"path": str(log.relative_to(ROOT)), "sha256": builds.digest(log)},
        "customer_run_verified": False, "model_request_sent": False})
    print(str(target.relative_to(ROOT)), flush=True)
    if result.returncode != 0 or before != after:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
