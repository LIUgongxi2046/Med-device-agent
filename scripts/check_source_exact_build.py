import argparse
from datetime import datetime, timezone
import os
from pathlib import Path
import subprocess
from uuid import uuid4
from xml.etree import ElementTree

import check_source_index_build as builds


ROOT = builds.ROOT
DIRECTORY = ROOT / ".implementation-work/source-exact-build-checks"
TESTS = {
    "com.ruoyi.medical.data.MigrationBundleTest": 1,
    "com.ruoyi.medical.persistence.SourceExactBootstrapTest": 1,
    "com.ruoyi.medical.persistence.SourceExactRangeIT": 2,
    "com.ruoyi.medical.persistence.SourceExactMapperIT": 3,
    "com.ruoyi.medical.persistence.RetrievalSchemaTest": 2,
    "com.ruoyi.medical.persistence.TenantAccessTest": 6,
    "com.ruoyi.medical.persistence.VersionResourceAuthorityTest": 6,
    "com.ruoyi.medical.persistence.SourceProcessingObjectIT": 1,
}


def snapshot(storage):
    values = builds.snapshot()
    values["scripts/check_source_index_build.py"] = builds.digest(ROOT / "scripts/check_source_index_build.py")
    values["scripts/check_source_exact_build.py"] = builds.digest(Path(__file__).resolve())
    values["scripts/check_source_exact_migration.py"] = builds.digest(ROOT / "scripts/check_source_exact_migration.py")
    for name in TESTS:
        module = "medical-data" if ".data." in name else "medical-persistence"
        path = ROOT / "business" / module / "src/test/java" / (name.replace(".", "/") + ".java")
        values[str(path.relative_to(ROOT))] = builds.digest(path)
    if storage is not None:
        values[str(storage.relative_to(ROOT))] = builds.digest(storage)
    return dict(sorted(values.items()))


def main():
    parser = argparse.ArgumentParser(description="核验EXACT原文字段、实际MySQL和完整应用制品")
    parser.add_argument("--check", choices=("technical", "application"), required=True)
    parser.add_argument("--storage-receipt")
    args = parser.parse_args()
    storage = None
    if args.check == "technical":
        if args.storage_receipt is None:
            raise ValueError("EXACT_REAL_STORAGE_RECEIPT_REQUIRED")
        storage = Path(args.storage_receipt).resolve(strict=True)
        storage.relative_to(ROOT / ".implementation-work/source-processing-storage-checks")
    directory = DIRECTORY / str(uuid4())
    directory.mkdir(mode=0o700, parents=True)
    before = snapshot(storage)
    command = [str(builds.MAVEN), "-o", "--batch-mode", "--no-transfer-progress", "-f", str(ROOT / "business/pom.xml"),
        "-Dmaven.repo.local=" + str(ROOT / ".implementation-work/maven-repository")]
    if args.check == "technical":
        command.extend(["-pl", "medical-persistence,medical-data", "-am", "-Dtest=" + ",".join(name.rsplit(".", 1)[1] for name in TESTS),
            "-Dsurefire.failIfNoSpecifiedTests=false", "-Dmedical.processing.storage=" + str(storage), "package"])
    else:
        command.extend(["-pl", "ruoyi-admin", "-am", "-DskipTests", "package"])
    environment = os.environ.copy()
    environment.update({"LANG": "en_US.UTF-8", "LC_ALL": "en_US.UTF-8", "JAVA_HOME": str(builds.JAVA_HOME_PATH),
        "TMPDIR": str(ROOT / ".implementation-work/migration-check-tmp"),
        "MAVEN_OPTS": "-Dfile.encoding=UTF-8 -Djava.io.tmpdir=" + str(ROOT / ".implementation-work/java-temp")})
    log = directory / "maven.log"
    descriptor = os.open(log, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600)
    with os.fdopen(descriptor, "wb") as output:
        result = subprocess.run(command, cwd=ROOT, env=environment, stdout=output, stderr=subprocess.STDOUT, check=False)
    after = snapshot(storage)
    reports = []
    if args.check == "technical" and result.returncode == 0:
        for name, expected in TESTS.items():
            module = "medical-data" if ".data." in name else "medical-persistence"
            path = ROOT / "business" / module / "target/surefire-reports" / ("TEST-" + name + ".xml")
            report = ElementTree.parse(path).getroot()
            counts = {key: int(report.attrib[key]) for key in ("tests", "failures", "errors", "skipped")}
            if report.attrib["name"] != name or counts != {"tests": expected, "failures": 0, "errors": 0, "skipped": 0}:
                raise ValueError("EXACT_REQUIRED_ASSERTIONS_FAILED")
            target = directory / path.name
            target.write_bytes(path.read_bytes())
            target.chmod(0o600)
            reports.append({"name": name, "counts": counts, "path": str(target.relative_to(ROOT)), "sha256": builds.digest(target)})
    artifacts = []
    if result.returncode == 0:
        paths = [ROOT / "business/medical-data/target/medical-data-3.9.2.jar", ROOT / "business/medical-data/target/medical-data-3.9.2-cli.jar",
            ROOT / "business/medical-persistence/target/medical-persistence-3.9.2.jar"]
        if args.check == "application":
            paths.append(ROOT / "business/ruoyi-admin/target/ruoyi-admin.jar")
        artifacts = [{"path": str(path.relative_to(ROOT)), "sha256": builds.digest(path), "size_bytes": path.stat().st_size} for path in paths]
    receipt = {"schema_version": "1.0.0", "check": args.check, "verified_at": datetime.now(timezone.utc).isoformat(),
        "exit_code": result.returncode, "command": command, "inputs_before": before, "inputs_after": after,
        "inputs_unchanged": before == after, "reports": reports, "artifacts": artifacts,
        "log": {"path": str(log.relative_to(ROOT)), "sha256": builds.digest(log)},
        "customer_exact_verified": False, "professional_approval_created": False, "model_request_sent": False}
    target = directory / "receipt.json"
    builds.save(target, receipt)
    print(str(target.relative_to(ROOT)), flush=True)
    if result.returncode != 0 or before != after:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
