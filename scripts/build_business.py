import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import xml.etree.ElementTree as ET
from uuid import uuid4
from build_toolchains import resolve_toolchains


ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description="使用项目固定工具运行 Maven 原生构建")
    parser.add_argument("--versions", action="store_true")
    args = parser.parse_args()
    java_home, maven = resolve_toolchains(ROOT)
    environment = os.environ.copy()
    environment["JAVA_HOME"] = str(java_home)
    # 文件处理和依赖缓存使用项目目录，保持本地构建的资源归属。
    environment["MAVEN_OPTS"] = environment.get("MAVEN_OPTS", "-Xms128m -Xmx512m") + " -Dfile.encoding=UTF-8 -Djava.io.tmpdir=../.implementation-work/java-temp"
    (ROOT / ".implementation-work/java-temp").mkdir(parents=True, exist_ok=True)
    environment["TMPDIR"] = str(ROOT / ".implementation-work/java-temp")
    command = [str(maven)]
    if environment.get("MEDICAL_MAVEN_SETTINGS"):
        settings = Path(environment["MEDICAL_MAVEN_SETTINGS"]).resolve(strict=True)
        if not settings.is_file():
            raise ValueError("MAVEN_SETTINGS_NOT_FILE")
        command.extend(["--settings", str(settings)])
    if args.versions:
        command.append("--version")
    else:
        subprocess.run(["npm", "run", "typecheck:medical"], cwd=ROOT / "web", env=environment, check=True)
        subprocess.run(["npm", "run", "build:prod", "--", "--mode", "medical"], cwd=ROOT / "web", env=environment, check=True)
        runtime_checks = ROOT / ".implementation-work/java-runtime-checks" / str(uuid4())
        runtime_checks.mkdir(parents=True)
        command.extend([
            "--batch-mode",
            "--no-transfer-progress",
            "-Dmaven.repo.local=" + str(ROOT / ".implementation-work/maven-repository"),
            "-Dmedical.navigationEvidence=" + str(runtime_checks / "navigation-result.json"),
            "-Dmedical.httpEvidence=" + str(runtime_checks / "template-http-result.json"),
            "-Dmedical.methodSecurityEvidence=" + str(runtime_checks / "method-security-result.json"),
            "-Dmedical.accountActionsEvidence=" + str(runtime_checks / "account-actions-result.json"),
            "-Dmedical.managementDataEvidence=" + str(runtime_checks / "management-data-result.json"),
            "-Dmedical.managementServicesEvidence=" + str(runtime_checks / "management-services-result.json"),
            "-Dmedical.frontendEvidence=" + str(runtime_checks / "frontend-result.json"),
            "-Dmedical.taskListEvidence=" + str(runtime_checks / "task-list-result.json"),
            "-Dmedical.projectSimulationEvidence=" + str(runtime_checks / "project-result.json"),
            "-Dmedical.roleSimulationEvidence=" + str(runtime_checks / "role-result.json"),
            "-Dmedical.memberSimulationEvidence=" + str(runtime_checks / "member-result.json"),
            "-Dmedical.sourceUploadEvidence=" + str(runtime_checks / "source-upload-result.json"),
            "-Dmedical.serviceCertificateEvidence=" + str(runtime_checks / "service-certificate-result.json"),
            "clean",
            "verify",
        ])
    subprocess.run(command, cwd=ROOT / "business", env=environment, check=True)
    if not args.versions:
        # 保存实际JUnit原始结果，后续独立检查不会改变本次完整构建证据。
        artifacts = []
        for source in sorted((ROOT / "web/dist").rglob("*")):
            if source.is_file():
                target = runtime_checks / "frontend" / source.relative_to(ROOT / "web/dist")
                target.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(source, target)
                artifacts.append({"path": str(target), "sha256": hashlib.sha256(target.read_bytes()).hexdigest()})
        totals = {key: 0 for key in ("tests", "failures", "errors", "skipped")}
        for report in sorted((ROOT / "business").glob("*/target/surefire-reports/TEST-*.xml")):
            target = runtime_checks / "junit" / report.parents[2].name / report.name
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(report, target)
            suite = ET.parse(target).getroot()
            for key in totals:
                totals[key] += int(suite.attrib[key])
            artifacts.append({"path": str(target), "sha256": hashlib.sha256(target.read_bytes()).hexdigest()})
        if totals["tests"] <= 0 or any(totals[key] for key in ("failures", "errors", "skipped")):
            raise ValueError("全部Java检查必须执行且没有失败、错误或跳过")
        for name in ("navigation-result.json", "template-http-result.json", "method-security-result.json", "management-data-result.json",
                "management-services-result.json", "account-actions-result.json", "frontend-result.json",
                "task-list-result.json", "project-result.json", "role-result.json", "member-result.json", "source-upload-result.json",
                "source-upload-recovery-result.json", "source-upload-capacity-result.json", "source-upload-user-capacity-result.json",
                "source-upload-deadline-result.json", "source-upload-completion-result.json",
                "service-certificate-result.json", "service-certificate-servlet-result.json"):
            path = runtime_checks / name
            artifacts.append({"path": str(path), "sha256": hashlib.sha256(path.read_bytes()).hexdigest()})
        result = {"scope": "FULL_REGISTERED_JAVA_BUILD_AND_ORIGINAL_FRAMEWORK_HTTP", "command": command,
                  "totals": totals, "artifacts": artifacts, "frontend_build_verified": True, "customer_success_verified": False}
        report = runtime_checks / "summary.json"
        report.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        pointer = ROOT / ".implementation-work/java-runtime-checks/current.json"
        pointer.write_text(json.dumps({"report": str(report), "sha256": hashlib.sha256(report.read_bytes()).hexdigest()}), encoding="utf-8")
        print(json.dumps({"scope": result["scope"], "totals": totals, "report": str(report.relative_to(ROOT))}), flush=True)


if __name__ == "__main__":
    main()
