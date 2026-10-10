import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import time


ROOT = Path(__file__).resolve().parents[1]
CAPTURE = Path("/Users/liuhaoxian/.agents/skills/haonan-s000-pmp/scripts/capture_check.py")


def main():
    parser = argparse.ArgumentParser(description="验证完整Agent发布清单及当前共享Schema")
    parser.add_argument("--execution", required=True)
    parser.add_argument("--positive-only", action="store_true")
    parser.add_argument("--store-only", action="store_true")
    args = parser.parse_args()
    if Path(args.execution).name != args.execution or args.execution in (".", ".."):
        raise ValueError("执行身份必须使用单一目录名称")
    output = ROOT / ".implementation-work" / ("IC-12-AGENT-MANIFEST-" + args.execution)
    inputs = {ROOT / "scripts/check_agent_release_manifest.py", ROOT / "business/pom.xml", ROOT / "contracts/catalogue.json",
        ROOT / "LLD-Agent.md", ROOT / "LLD-后端.md", ROOT / "docs/development/IMP-12.md",
        ROOT / ".implementation-work/document-venv/bin/python"}
    for module in ("medical-contracts", "medical-data", "ruoyi-common", "ruoyi-system", "ruoyi-framework", "medical-identity", "medical-persistence"):
        inputs.add(ROOT / "business" / module / "pom.xml")
        inputs.update(path for path in (ROOT / "business" / module / "src").rglob("*") if path.is_file())
    for entry in json.loads((ROOT / "contracts/catalogue.json").read_bytes())["schemas"]:
        inputs.add(ROOT / entry["path"])
    report = ROOT / "business/medical-persistence/target/surefire-reports/TEST-com.ruoyi.medical.persistence.AgentReleaseManifestSchemaTest.xml"
    capture = [sys.executable, str(CAPTURE), "--output-dir", str(output), "--check-id", "IC-12-AGENT-MANIFEST",
        "--kind", "UNIT", "--baseline-id", "BACK-AGENT-RELEASE-STORE-01" if args.store_only else "BACK-AGENT-RELEASE-MANIFEST-01",
        "--cwd", str(ROOT / "business"), "--timeout", "600"]
    for path in sorted(inputs):
        capture.extend(("--input", str(path)))
    temporary = ROOT / ".implementation-work/agent-manifest-tmp"
    temporary.mkdir(parents=True, exist_ok=True)
    tools = ROOT / ".implementation-work/toolchains"
    environment = os.environ.copy()
    environment.update({"JAVA_HOME": str(tools / "jdk-17.0.20.1+1/Contents/Home"), "TMPDIR": str(temporary),
        "MAVEN_OPTS": "-Dfile.encoding=UTF-8 -Djava.io.tmpdir=" + str(temporary)})
    selection = "AgentReleaseManifestSchemaTest#bindsCompleteCurrentSchemaAndPreservesEveryInput" if args.positive_only else "AgentReleaseManifestSchemaTest,PublishedSchemaReferencesTest,SharedContractTest,ArtifactBindingsTest"
    if args.store_only:
        if args.positive_only:
            raise ValueError("检查范围不能同时选择结构绑定与持久读取")
        selection = "AgentReleaseStoreTest"
    command = [str(tools / "apache-maven-3.9.16/bin/mvn"), "--offline", "--batch-mode", "--no-transfer-progress",
        "-Dmaven.repo.local=" + str(ROOT / ".implementation-work/maven-repository"), "-pl", "medical-persistence", "-am",
        "-Dtest=" + selection, "-Dsurefire.failIfNoSpecifiedTests=false", "test"]
    started = time.time_ns()
    result = subprocess.run([*capture, "--", *command], cwd=ROOT, env=environment)
    receipt = json.loads((output / "receipt.json").read_bytes())
    reports = []
    for source in sorted((ROOT / "business").glob("*/target/surefire-reports/TEST-*.xml")):
        if source.stat().st_mtime_ns < started:
            continue
        if source.name not in (report.name, "TEST-com.ruoyi.medical.persistence.AgentReleaseStoreTest.xml", "TEST-com.ruoyi.medical.contracts.PublishedSchemaReferencesTest.xml",
                "TEST-com.ruoyi.medical.contracts.SharedContractTest.xml", "TEST-com.ruoyi.medical.contracts.ArtifactBindingsTest.xml"):
            continue
        target = output / source.name
        shutil.copyfile(source, target)
        reports.append({"path": str(target.relative_to(ROOT)), "sha256": hashlib.sha256(target.read_bytes()).hexdigest()})
    expected_report = "TEST-com.ruoyi.medical.persistence.AgentReleaseStoreTest.xml" if args.store_only else report.name
    current = any(item["path"].endswith(expected_report) for item in reports)
    if result.returncode == 0 and not current:
        raise ValueError("本次成功需要实际当前Agent清单JUnit")
    classes = []
    if result.returncode == 0 and args.store_only:
        directory = ROOT / "business/medical-persistence/target/classes/com/ruoyi/medical/persistence"
        for source in sorted(directory.rglob("*.class")):
            if not source.name.startswith(("AgentReleaseStore", "AgentReleaseStoredProtocol", "AgentReleaseMapper", "AgentReleaseScopeAuthority", "AgentReleaseManifestSchema")):
                continue
            target = output / "classes" / source.relative_to(directory)
            target.parent.mkdir(parents=True, exist_ok=True)
            original = hashlib.sha256(source.read_bytes()).hexdigest()
            shutil.copyfile(source, target)
            if hashlib.sha256(target.read_bytes()).hexdigest() != original:
                raise ValueError("实际编译制品副本不一致")
            classes.append({"path": str(target.relative_to(ROOT)), "sha256": original})
    with (output / "manifest-check.json").open("x", encoding="utf-8") as stream:
        stream.write(json.dumps({"scope": "AGENT_RELEASE_STORED_METADATA_ONLY" if args.store_only else "AGENT_RELEASE_MANIFEST_STRUCTURE_ONLY", "receipt_sha256": hashlib.sha256((output / "receipt.json").read_bytes()).hexdigest(),
            "current_manifest_tests_executed": current, "reports": reports, "classes": classes, "agent_publish_verified": False,
            "evaluation_verified": False, "public_task_create_verified": False}, ensure_ascii=False, indent=2) + "\n")
    raise SystemExit(result.returncode)


if __name__ == "__main__":
    main()
