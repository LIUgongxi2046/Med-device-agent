import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import time
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[1]
CAPTURE = Path("/Users/liuhaoxian/.agents/skills/haonan-s000-pmp/scripts/capture_check.py")
REPORT = "TEST-com.ruoyi.medical.runtime.SourceUploadHttpTest.xml"


def main():
    parser = argparse.ArgumentParser(description="核验公开来源上传的实际HTTP与企业事务")
    parser.add_argument("--execution")
    parser.add_argument("--scope", choices=("all", "completion"), default="all")
    parser.add_argument("--run", type=Path)
    args = parser.parse_args()
    temporary = ROOT / ".implementation-work/source-upload-http-tmp"
    temporary.mkdir(parents=True, exist_ok=True)
    tools = ROOT / ".implementation-work/toolchains"
    environment = os.environ.copy()
    environment.update({"JAVA_HOME": str(tools / "jdk-17.0.20.1+1/Contents/Home"), "TMPDIR": str(temporary),
        "MAVEN_OPTS": "-Xms128m -Xmx512m -Dfile.encoding=UTF-8 -Djava.io.tmpdir=" + str(temporary)})
    if args.run is not None:
        output = args.run.resolve(strict=True)
        command = [str(tools / "apache-maven-3.9.16/bin/mvn"), "-f", "business/pom.xml", "--batch-mode", "--no-transfer-progress",
            "-Dmaven.repo.local=" + str(ROOT / ".implementation-work/maven-repository"), "-pl", "ruoyi-admin", "-am",
            "-Dmedical.sourceUploadEvidence=" + str(output / "source-upload-result.json"),
            "-Dtest=" + ("SourceUploadHttpTest#acceptsTheOriginalSourceCompletionCommandAndReopensItsPendingOperation" if args.scope == "completion" else "SourceUploadHttpTest"),
            "-Dsurefire.failIfNoSpecifiedTests=false", "test"]
        started = time.time_ns()
        result = subprocess.run(command, cwd=ROOT, env=environment)
        saved = output / REPORT
        report_path = ROOT / "business/ruoyi-admin/target/surefire-reports" / REPORT
        if not report_path.is_file() or report_path.stat().st_mtime_ns < started:
            raise ValueError("SOURCE_HTTP_CURRENT_JUNIT_REQUIRED")
        shutil.copyfile(report_path, saved)
        report = ET.parse(saved).getroot()
        evidence = {"scope": "SOURCE_UPLOAD_CREATE_HTTP_TRANSACTION", "command": command,
            "totals": {name: int(report.attrib[name]) for name in ("tests", "failures", "errors", "skipped")},
            "junit_sha256": hashlib.sha256(saved.read_bytes()).hexdigest(), "customer_success_verified": False}
        if result.returncode == 0 and args.scope == "all":
            recovery = output / "source-upload-recovery-result.json"
            evidence["source_recovery"] = json.loads(recovery.read_bytes())
            evidence["source_recovery_sha256"] = hashlib.sha256(recovery.read_bytes()).hexdigest()
            capacity = output / "source-upload-capacity-result.json"
            evidence["source_capacity"] = json.loads(capacity.read_bytes())
            evidence["source_capacity_sha256"] = hashlib.sha256(capacity.read_bytes()).hexdigest()
            user_capacity = output / "source-upload-user-capacity-result.json"
            evidence["source_user_capacity"] = json.loads(user_capacity.read_bytes())
            evidence["source_user_capacity_sha256"] = hashlib.sha256(user_capacity.read_bytes()).hexdigest()
            deadline = output / "source-upload-deadline-result.json"
            evidence["source_deadline"] = json.loads(deadline.read_bytes())
            evidence["source_deadline_sha256"] = hashlib.sha256(deadline.read_bytes()).hexdigest()
        if result.returncode == 0:
            completion = output / "source-upload-completion-result.json"
            evidence["source_completion"] = json.loads(completion.read_bytes())
            evidence["source_completion_sha256"] = hashlib.sha256(completion.read_bytes()).hexdigest()
        evidence["scope"] = "SOURCE_UPLOAD_COMPLETION_COMMAND_HTTP" if args.scope == "completion" else "SOURCE_UPLOAD_CREATE_PART_AND_COMPLETION_HTTP"
        (output / "source-upload-http-check.json").write_text(json.dumps(evidence, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        raise SystemExit(result.returncode)
    if not args.execution or Path(args.execution).name != args.execution or args.execution in (".", ".."):
        raise ValueError("执行身份必须使用单一目录名称")
    output = ROOT / ".implementation-work" / ("IC-08-SOURCE-HTTP-" + args.execution)
    inputs = {"scripts/check_source_upload_http.py", "data-design/sample_dataset.json", "PRD.md", "LLD-后端.md", "LLD-数据.md", "backend-design/接口补充设计.md",
        "ui-delivery/assets/public-evidence/clinical-evaluation-2021.docx",
        ".implementation-work/runtime/service-identity.json", "contracts/catalogue.json", "data-design/core.sql",
        ".implementation-work/maven-repository/org/apache/httpcomponents/httpclient/4.5.13/httpclient-4.5.13.jar",
        ".implementation-work/maven-repository/org/apache/httpcomponents/httpcore/4.4.16/httpcore-4.4.16.jar",
        ".implementation-work/toolchains/toxiproxy_2.12.0_darwin_amd64.tar.gz",
        ".implementation-work/toolchains/toxiproxy-checksums-v2.12.0.txt",
        ".implementation-work/toolchains/toxiproxy-2.12.0/toxiproxy-server",
        ".implementation-work/maven-repository/org/awaitility/awaitility/4.2.2/awaitility-4.2.2.jar",
        ".implementation-work/maven-repository/org/hamcrest/hamcrest/3.0/hamcrest-3.0.jar",
        ".implementation-work/maven-repository/com/github/ben-manes/caffeine/caffeine/3.2.4/caffeine-3.2.4.jar",
        ".implementation-work/maven-repository/com/baomidou/mybatis-plus-jsqlparser/3.5.17/mybatis-plus-jsqlparser-3.5.17.jar",
        ".implementation-work/maven-repository/org/junit/jupiter/junit-jupiter-params/5.12.2/junit-jupiter-params-5.12.2.jar",
        ".implementation-work/runtime/object-storage/deployment.json"}
    inputs.update(str(path.relative_to(ROOT)) for path in (ROOT / "business").rglob("pom.xml") if "target" not in path.parts)
    for directory in ("medical-contracts", "medical-data", "medical-identity", "medical-persistence", "ruoyi-admin", "ruoyi-framework",
            "ruoyi-common", "ruoyi-system", "ruoyi-quartz", "ruoyi-generator"):
        inputs.update(str(path.relative_to(ROOT)) for path in (ROOT / "business" / directory / "src").rglob("*") if path.is_file())
    for entry in json.loads((ROOT / "contracts/catalogue.json").read_bytes())["schemas"]:
        inputs.add(entry["path"])
    command = [sys.executable, str(CAPTURE), "--output-dir", str(output), "--check-id", "IC-08-SOURCE-HTTP",
        "--kind", "INTEGRATION", "--baseline-id", "BACK-SOURCE-UPLOAD-01", "--cwd", str(ROOT), "--timeout", "600"]
    for path in sorted(inputs):
        command.extend(["--input", path])
    for filename in (REPORT, "source-upload-http-check.json"):
        command.extend(["--artifact", str(output / filename)])
    command.extend(["--", sys.executable, str(Path(__file__).resolve()), "--scope", args.scope, "--run", str(output)])
    raise SystemExit(subprocess.run(command, cwd=ROOT, env=environment).returncode)


if __name__ == "__main__":
    main()
