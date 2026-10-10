import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys


ROOT = Path(__file__).resolve().parents[1]
CAPTURE = Path("/Users/liuhaoxian/.agents/skills/haonan-s000-pmp/scripts/capture_check.py")


def main():
    parser = argparse.ArgumentParser(description="核查固定模板适用、原表达式框架及实际数据库查询")
    parser.add_argument("--execution", required=True)
    parser.add_argument("--suite", choices=("applicability", "templates"), default="applicability")
    args = parser.parse_args()
    if Path(args.execution).name != args.execution or args.execution in (".", ".."):
        raise ValueError("执行身份必须使用单一目录名称")
    tools = ROOT / ".implementation-work/toolchains"
    temporary = ROOT / ".implementation-work/template-applicability-tmp"
    temporary.mkdir(parents=True, exist_ok=True)
    environment = os.environ.copy()
    environment.update({"JAVA_HOME": str(tools / "jdk-17.0.20.1+1/Contents/Home"), "TMPDIR": str(temporary),
        "MAVEN_OPTS": "-Dfile.encoding=UTF-8 -Djava.io.tmpdir=" + str(temporary)})
    inputs = ["scripts/check_template_applicability.py", "business/pom.xml", "business/medical-persistence/pom.xml",
        "business/medical-contracts/pom.xml", "contracts/catalogue.json", "backend-design/接口补充设计.md", "LLD-后端.md",
        ".implementation-work/runtime/service-identity.json"]
    for directory in ("business/medical-contracts/src", "business/medical-data/src", "business/medical-persistence/src", "business/medical-identity/src"):
        inputs.extend(str(path.relative_to(ROOT)) for path in sorted((ROOT / directory).rglob("*")) if path.is_file())
    reports = ["TemplateApplicabilityTest"]
    if args.suite == "templates":
        reports.extend(("TemplateDefinitionSchemaTest", "TemplateApprovalSchemaTest", "TemplateOperationQueriesTest",
            "TemplateOperationSchemaTest", "TaskInputSchemaTest", "ProjectSchemaTest"))
        inputs.extend(("tests/check_project_sql_columns.py", "business/ruoyi-admin/pom.xml"))
        for directory in ("business/ruoyi-admin/src", "business/ruoyi-common/src", "business/ruoyi-system/src", "business/ruoyi-framework/src"):
            inputs.extend(str(path.relative_to(ROOT)) for path in sorted((ROOT / directory).rglob("*")) if path.is_file())
    report_paths = [("medical-persistence", "persistence", name) for name in reports]
    if args.suite == "templates":
        for module, namespace in (("medical-contracts", "contracts"), ("medical-identity", "identity")):
            directory = ROOT / "business" / module / "src/test/java/com/ruoyi/medical" / namespace
            for path in sorted(directory.glob("*Test.java")):
                reports.append(path.stem); report_paths.append((module, namespace, path.stem))
        reports.append("TemplateApplicabilityHttpTest"); report_paths.append(("ruoyi-admin", "runtime", "TemplateApplicabilityHttpTest"))
    capture = [sys.executable, str(CAPTURE), "--output-dir", ".implementation-work/IC-11-APPLICABILITY-" + args.execution,
        "--check-id", "IC-11-APPLICABILITY", "--kind", "INTEGRATION", "--baseline-id", "BACK-TEMPLATE-APPLICABILITY-01",
        "--cwd", str(ROOT), "--timeout", "900"]
    for path in inputs:
        capture.extend(["--input", path])
    for module, namespace, name in report_paths:
        capture.extend(["--artifact", "business/" + module + "/target/surefire-reports/TEST-com.ruoyi.medical." + namespace + "." + name + ".xml"])
    http_evidence = ROOT / ".implementation-work" / ("IC-11-APPLICABILITY-" + args.execution) / "http-result.json"
    if args.suite == "templates": capture.extend(["--artifact", str(http_evidence)])
    capture.extend(["--", str(tools / "apache-maven-3.9.16/bin/mvn"), "-f", "business/pom.xml", "--offline", "--batch-mode", "--no-transfer-progress",
        "-Dmaven.repo.local=" + str(ROOT / ".implementation-work/maven-repository"), "-pl", "ruoyi-admin" if args.suite == "templates" else "medical-persistence", "-am",
        "-Dmedical.httpEvidence=" + str(http_evidence),
        "-Dtest=" + ",".join(reports), "-Dsurefire.failIfNoSpecifiedTests=false", "test"])
    result = subprocess.run(capture, cwd=ROOT, env=environment, check=False)
    if result.returncode == 0:
        output = ROOT / ".implementation-work" / ("IC-11-APPLICABILITY-" + args.execution)
        receipt = json.loads((output / "receipt.json").read_bytes())
        if not receipt["inputs_unchanged"]:
            raise ValueError("模板适用检查期间输入发生变化")
        saved = []
        for module, namespace, name in report_paths:
            source = ROOT / "business" / module / "target/surefire-reports" / ("TEST-com.ruoyi.medical." + namespace + "." + name + ".xml")
            target = output / "reports" / module / source.name
            target.parent.mkdir(parents=True, exist_ok=True)
            digest = hashlib.sha256(source.read_bytes()).hexdigest()
            shutil.copyfile(source, target)
            if hashlib.sha256(target.read_bytes()).hexdigest() != digest:
                raise ValueError("独立JUnit副本与实际检查结果不一致")
            saved.append({"path": str(target.relative_to(ROOT)), "sha256": digest})
        (output / "reports.json").write_text(json.dumps({"receipt_sha256": hashlib.sha256((output / "receipt.json").read_bytes()).hexdigest(),
            "reports": saved, "public_task_write_verified": False, "customer_success_verified": False}, indent=2) + "\n", encoding="utf-8")
    raise SystemExit(result.returncode)


if __name__ == "__main__":
    main()
