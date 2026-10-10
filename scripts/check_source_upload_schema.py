import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[1]
CAPTURE = Path("/Users/liuhaoxian/.agents/skills/haonan-s000-pmp/scripts/capture_check.py")
REPORT = "TEST-com.ruoyi.medical.persistence.SourceUploadSchemaTest.xml"


def main():
    parser = argparse.ArgumentParser(description="核验公开来源上传的完整输入与声明")
    parser.add_argument("--execution")
    parser.add_argument("--run", type=Path)
    args = parser.parse_args()
    temporary = ROOT / ".implementation-work/source-upload-schema-tmp"
    temporary.mkdir(parents=True, exist_ok=True)
    toolchains = ROOT / ".implementation-work/toolchains"
    environment = os.environ.copy()
    environment.update({"JAVA_HOME": str(toolchains / "jdk-17.0.20.1+1/Contents/Home"), "TMPDIR": str(temporary),
        "MAVEN_OPTS": "-Dfile.encoding=UTF-8 -Djava.io.tmpdir=" + str(temporary)})
    if args.run is not None:
        output = args.run.resolve(strict=True)
        command = [str(toolchains / "apache-maven-3.9.16/bin/mvn"), "-f", "business/pom.xml", "--batch-mode",
            "--no-transfer-progress", "-Dmaven.repo.local=" + str(ROOT / ".implementation-work/maven-repository"),
            "-pl", "medical-persistence", "-am", "-Dtest=SourceUploadSchemaTest", "-Dsurefire.failIfNoSpecifiedTests=false", "test"]
        result = subprocess.run(command, cwd=ROOT, env=environment)
        original = ROOT / "business/medical-persistence/target/surefire-reports" / REPORT
        saved = output / REPORT
        shutil.copyfile(original, saved)
        report = ET.parse(saved).getroot()
        totals = {name: int(report.attrib[name]) for name in ("tests", "failures", "errors", "skipped")}
        evidence = {"scope": "SOURCE_UPLOAD_CREATE_AND_COMPLETION_INPUT_DECLARATIONS", "totals": totals,
            "command": command, "junit_sha256": hashlib.sha256(saved.read_bytes()).hexdigest(),
            "data_kind": "SIMULATION_BUSINESS_IDENTIFIERS_AND_REGISTERED_PUBLIC_ORIGINAL",
            "customer_success_verified": False, "public_upload_transaction_verified": False,
            "source_completion_consumer_verified": False}
        (output / "source-upload-schema-result.json").write_text(json.dumps(evidence, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        raise SystemExit(result.returncode)
    if not args.execution or Path(args.execution).name != args.execution or args.execution in (".", ".."):
        raise ValueError("执行身份必须使用单一目录名称")
    output = ROOT / ".implementation-work" / ("IC-08-SOURCE-INPUT-" + args.execution)
    inputs = {"scripts/check_source_upload_schema.py", "contracts/catalogue.json", "data-design/sample_dataset.json",
        "LLD-后端.md", "LLD-数据.md", "PRD.md", "backend-design/接口补充设计.md",
        "business/medical-persistence/src/main/resources/source-rights.schema.json"}
    dataset = json.loads((ROOT / "data-design/sample_dataset.json").read_bytes())
    for source in dataset["sources"]:
        inputs.add(source["source_ref"])
    inputs.update(str(path.relative_to(ROOT)) for path in (ROOT / "business").rglob("pom.xml") if "target" not in path.parts)
    for directory in ("business/medical-contracts/src", "business/medical-identity/src", "business/medical-persistence/src"):
        inputs.update(str(path.relative_to(ROOT)) for path in (ROOT / directory).rglob("*") if path.is_file())
    for entry in json.loads((ROOT / "contracts/catalogue.json").read_bytes())["schemas"]:
        inputs.add(entry["path"])
    command = [sys.executable, str(CAPTURE), "--output-dir", str(output), "--check-id", "IC-08-SOURCE-INPUT",
        "--kind", "UNIT", "--baseline-id", "BACK-SOURCE-UPLOAD-01", "--cwd", str(ROOT), "--timeout", "600"]
    for path in sorted(inputs):
        command.extend(["--input", path])
    for filename in (REPORT, "source-upload-schema-result.json"):
        command.extend(["--artifact", str(output / filename)])
    command.extend(["--", sys.executable, str(Path(__file__).resolve()), "--run", str(output)])
    raise SystemExit(subprocess.run(command, cwd=ROOT, env=environment).returncode)


if __name__ == "__main__":
    main()
