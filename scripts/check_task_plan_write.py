import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import time
import xml.etree.ElementTree as ElementTree


ROOT = Path(__file__).resolve().parents[1]
CAPTURE = Path("/Users/liuhaoxian/.agents/skills/haonan-s000-pmp/scripts/capture_check.py")


def main():
    parser = argparse.ArgumentParser(description="验证任务计划公开入口、完整输入及关联代码")
    parser.add_argument("--execution", required=True)
    parser.add_argument("--red-only", action="store_true")
    parser.add_argument("--decision-only", action="store_true")
    parser.add_argument("--decision-http", action="store_true")
    parser.add_argument("--body-only", action="store_true")
    parser.add_argument("--upload-http", action="store_true")
    parser.add_argument("--document-http", action="store_true")
    parser.add_argument("--entry-only", action="store_true")
    parser.add_argument("--route-only", action="store_true")
    parser.add_argument("--revision-http", action="store_true")
    args = parser.parse_args()
    if args.entry_only and not args.document_http:
        raise ValueError("入口复验必须明确正文HTTP范围")
    if args.route_only and (not args.document_http or args.entry_only):
        raise ValueError("独立路由检查必须明确正文HTTP范围")
    if args.revision_http and (not args.document_http or args.entry_only or args.route_only):
        raise ValueError("正文保存业务检查必须明确独立HTTP范围")
    if sum((args.red_only, args.decision_only, args.decision_http, args.body_only, args.upload_http, args.document_http)) > 1:
        raise ValueError("每次执行需要明确单一检查范围")
    if Path(args.execution).name != args.execution or args.execution in (".", ".."):
        raise ValueError("执行身份必须使用单一目录名称")
    output = ROOT / ".implementation-work" / ("IC-12-PLAN-WRITE-" + args.execution)
    inputs = {ROOT / "scripts/check_task_plan_write.py", ROOT / "business/pom.xml", ROOT / "contracts/catalogue.json",
        ROOT / "business/medical-data/src/main/resources/db/migration/coverage.json",
        ROOT / "docs/development/IMP-12.md", ROOT / "docs/development/IMP-29.md"}
    if args.body_only:
        inputs.add(ROOT / "docs/development/IMP-16.md")
        inputs.add(ROOT / "backend-design/接口补充设计.md")
        inputs.update(path for path in (ROOT / "docs/development").rglob("*") if path.is_file())
    modules = ["medical-contracts", "medical-data", "ruoyi-common", "ruoyi-system", "ruoyi-framework", "medical-identity", "medical-persistence"]
    if args.decision_http or args.upload_http or args.document_http:
        modules.extend(("ruoyi-quartz", "ruoyi-generator", "ruoyi-admin"))
        inputs.add(ROOT / "docs/development/IMP-16.md")
        inputs.add(ROOT / "backend-design/接口补充设计.md")
    if args.upload_http:
        inputs.add(ROOT / "ui-delivery/assets/public-evidence/clinical-evaluation-2021.docx")
        inputs.add(ROOT / "data-design/sample_dataset.json")
        inputs.add(ROOT / ".implementation-work/runtime/object-storage/deployment.json")
    for module in modules:
        inputs.add(ROOT / "business" / module / "pom.xml")
        inputs.update(path for path in (ROOT / "business" / module / "src").rglob("*") if path.is_file())
    for entry in json.loads((ROOT / "contracts/catalogue.json").read_bytes())["schemas"]:
        inputs.add(ROOT / entry["path"])
    capture = [sys.executable, str(CAPTURE), "--output-dir", str(output), "--check-id", "IC-12-PLAN-WRITE",
        "--kind", "INTEGRATION" if args.decision_http or args.upload_http or args.document_http else "UNIT", "--baseline-id",
        "BACK-DOCUMENT-BODY-01" if args.body_only or args.upload_http or args.document_http else "BACK-TASK-PLAN-RETURN-01" if args.decision_only or args.decision_http else "BACK-TASK-PLAN-WRITE-01",
        "--cwd", str(ROOT / "business"), "--timeout", "420" if args.revision_http else "300"]
    for path in sorted(inputs):
        capture.extend(("--input", str(path)))
    temporary = ROOT / ".implementation-work/task-plan-write-tmp"
    temporary.mkdir(parents=True, exist_ok=True)
    tools = ROOT / ".implementation-work/toolchains"
    environment = os.environ.copy()
    environment.update({"JAVA_HOME": str(tools / "jdk-17.0.20.1+1/Contents/Home"), "TMPDIR": str(temporary),
        "MAVEN_OPTS": "-Dfile.encoding=UTF-8 -Djava.io.tmpdir=" + str(temporary)})
    selection = "TaskPlanWriteEntryTest" if args.red_only else "TaskPlanWriteEntryTest,TaskPlanGraphTest,TaskInputSchemaTest,TenantAccessTest,PublishedSchemaReferencesTest,ArtifactBindingsTest,SharedContractTest"
    if args.decision_only:
        selection = "TaskPlanDecisionEntryTest"
    if args.decision_http:
        selection = "TaskPlanReturnHttpTest,TaskPlanDecisionEntryTest,TaskPlanGraphTest,TaskPlanWriteEntryTest,TemplateErrorResponseTest"
    if args.body_only:
        selection = "DocumentBodySemanticsTest,UploadAuthorizationTest,DocumentUploadBasisTest,DocumentUploadPreparationTest,DocumentUploadIssuanceTest"
    if args.upload_http:
        selection = "SourceUploadHttpTest#persistsCompleteSourceDeclarationsAndReplaysTheOriginalTransaction+recoversOriginalPartAfterActualS3ResponseInterruption+expiresStoppedActualInputAndResumesTheOriginalOperation"
    if args.document_http:
        selection = "TaskPlanReturnHttpTest,InterfaceBindingsTest,BusinessSessionSchemaTest" if args.entry_only else "TaskPlanReturnHttpTest,DocumentUploadIssuanceTest,TenantAccessTest,PublishedSchemaReferencesTest,ArtifactBindingsTest,SharedContractTest,InterfaceBindingsTest,BusinessSessionSchemaTest"
        if args.route_only:
            selection = "TaskPlanReturnHttpTest"
        if args.revision_http:
            selection = "DocumentRevisionHttpTest"
    command = [str(tools / "apache-maven-3.9.16/bin/mvn"), "--offline", "--batch-mode", "--no-transfer-progress",
        "-Dmaven.repo.local=" + str(ROOT / ".implementation-work/maven-repository"), "-pl", "ruoyi-admin" if args.decision_http or args.upload_http or args.document_http else "medical-persistence", "-am",
        "-Dtest=" + selection, "-Dsurefire.failIfNoSpecifiedTests=false", "test"]
    if args.upload_http:
        command.insert(-1, "-Dmedical.sourceUploadEvidence=" + str(output / "source-upload-result.json"))
    started = time.time_ns()
    result = subprocess.run([*capture, "--", *command], cwd=ROOT, env=environment)
    verified = set()
    verified_methods = set()
    for source in sorted((ROOT / "business").glob("*/target/surefire-reports/TEST-*.xml")):
        if source.stat().st_mtime_ns >= started:
            target = output / source.parent.parent.parent.name / source.name
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(source, target)
            report = ElementTree.parse(target).getroot()
            name = report.attrib["name"].rsplit(".", 1)[-1]
            if int(report.attrib["tests"]) > 0 and all(int(report.attrib.get(item, "0")) == 0 for item in ("failures", "errors", "skipped")):
                verified.add(name)
                verified_methods.update(name + "#" + item.attrib["name"] for item in report.findall("testcase"))
    print(json.dumps({"receipt_sha256": hashlib.sha256((output / "receipt.json").read_bytes()).hexdigest(),
        "full_plan_write_business_verified": False, "customer_success_verified": False}))
    required = {item.split("#", 1)[0] for item in selection.split(",")}
    if result.returncode == 0 and not required.issubset(verified):
        raise RuntimeError("当前执行缺少所选检查的成功报告：" + ", ".join(sorted(required - verified)))
    required_methods = set()
    for item in selection.split(","):
        if "#" in item:
            name, methods = item.split("#", 1)
            required_methods.update(name + "#" + method for method in methods.split("+"))
    if result.returncode == 0 and not required_methods.issubset(verified_methods):
        raise RuntimeError("当前执行缺少所选行为的成功报告：" + ", ".join(sorted(required_methods - verified_methods)))
    raise SystemExit(result.returncode)


if __name__ == "__main__":
    main()
