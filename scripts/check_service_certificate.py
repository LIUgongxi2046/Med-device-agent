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
REPORT = "TEST-com.ruoyi.medical.identity.ServiceCertificateNativeTest.xml"


def main():
    parser = argparse.ArgumentParser(description="核验实际TLS服务证书与当前签名CRL")
    parser.add_argument("--execution")
    parser.add_argument("--scope", choices=("certificate", "identity"), default="certificate")
    parser.add_argument("--run", type=Path)
    args = parser.parse_args()
    tools = ROOT / ".implementation-work/toolchains"
    temporary = ROOT / ".implementation-work/service-certificate-tmp"
    temporary.mkdir(parents=True, exist_ok=True)
    environment = os.environ.copy()
    environment.update({"JAVA_HOME": str(tools / "jdk-17.0.20.1+1/Contents/Home"), "TMPDIR": str(temporary),
        "MAVEN_OPTS": "-Xms128m -Xmx512m -Dfile.encoding=UTF-8 -Djava.io.tmpdir=" + str(temporary)})
    if args.run is not None:
        output = args.run.resolve(strict=True)
        command = [str(tools / "apache-maven-3.9.16/bin/mvn"), "-f", "business/pom.xml", "--batch-mode", "--no-transfer-progress",
            "-Dmaven.repo.local=" + str(ROOT / ".implementation-work/maven-repository"), "-pl", "medical-identity", "-am",
            "-Dtest=" + ("*Test" if args.scope == "identity" else "ServiceCertificateNativeTest,ServiceCertificateServletTest,ServiceCertificateTrustStoreTest"), "-Dsurefire.failIfNoSpecifiedTests=false",
            "-Dmedical.serviceCertificateEvidence=" + str(output / "service-certificate-result.json"), "test"]
        started = time.time_ns()
        result = subprocess.run(command, cwd=ROOT, env=environment)
        report = ROOT / "business/medical-identity/target/surefire-reports" / REPORT
        if not report.is_file() or report.stat().st_mtime_ns < started:
            raise ValueError("SERVICE_CERTIFICATE_CURRENT_JUNIT_REQUIRED")
        saved = output / REPORT
        shutil.copyfile(report, saved)
        suite = ET.parse(saved).getroot()
        evidence = {"scope": "ACTUAL_TLS_SERVICE_CERTIFICATE_COMPONENT", "command": command,
            "totals": {name: int(suite.attrib[name]) for name in ("tests", "failures", "errors", "skipped")},
            "junit_sha256": hashlib.sha256(saved.read_bytes()).hexdigest(), "customer_success_verified": False}
        servlet_report = ROOT / "business/medical-identity/target/surefire-reports/TEST-com.ruoyi.medical.identity.ServiceCertificateServletTest.xml"
        if not servlet_report.is_file() or servlet_report.stat().st_mtime_ns < started:
            raise ValueError("SERVICE_CERTIFICATE_CURRENT_SERVLET_JUNIT_REQUIRED")
        servlet_saved = output / servlet_report.name
        shutil.copyfile(servlet_report, servlet_saved)
        evidence["servlet_junit_sha256"] = hashlib.sha256(servlet_saved.read_bytes()).hexdigest()
        servlet_suite = ET.parse(servlet_saved).getroot()
        for name in evidence["totals"]:
            evidence["totals"][name] += int(servlet_suite.attrib[name])
        trust_report = ROOT / "business/medical-identity/target/surefire-reports/TEST-com.ruoyi.medical.identity.ServiceCertificateTrustStoreTest.xml"
        if not trust_report.is_file() or trust_report.stat().st_mtime_ns < started:
            raise ValueError("SERVICE_CERTIFICATE_CURRENT_TRUST_JUNIT_REQUIRED")
        trust_saved = output / trust_report.name
        shutil.copyfile(trust_report, trust_saved)
        evidence["trust_store_junit_sha256"] = hashlib.sha256(trust_saved.read_bytes()).hexdigest()
        trust_suite = ET.parse(trust_saved).getroot()
        for name in evidence["totals"]:
            evidence["totals"][name] += int(trust_suite.attrib[name])
        if args.scope == "identity":
            totals = {name: 0 for name in ("tests", "failures", "errors", "skipped")}
            reports = []
            for module in ("medical-contracts", "medical-identity"):
                for source in sorted((ROOT / "business" / module / "target/surefire-reports").glob("TEST-*.xml")):
                    if source.stat().st_mtime_ns < started:
                        continue
                    target = output / "junit" / module / source.name
                    target.parent.mkdir(parents=True, exist_ok=True)
                    shutil.copyfile(source, target)
                    current = ET.parse(target).getroot()
                    for name in totals:
                        totals[name] += int(current.attrib[name])
                    reports.append({"path": str(target.relative_to(output)), "sha256": hashlib.sha256(target.read_bytes()).hexdigest()})
            evidence["affected_totals"] = totals
            evidence["affected_junit"] = reports
        if result.returncode == 0:
            raw = output / "service-certificate-result.json"
            evidence["result_sha256"] = hashlib.sha256(raw.read_bytes()).hexdigest()
            evidence["result"] = json.loads(raw.read_bytes())
            servlet_raw = output / "service-certificate-servlet-result.json"
            evidence["servlet_result_sha256"] = hashlib.sha256(servlet_raw.read_bytes()).hexdigest()
            evidence["servlet_result"] = json.loads(servlet_raw.read_bytes())
            public_evidence = []
            for label, actual in (("native", evidence["result"]), ("servlet", evidence["servlet_result"])):
                matches = [path.parent for path in (ROOT / ".implementation-work/service-certificates").glob("*/manifest.json")
                    if json.loads(path.read_bytes()) == actual["certificate_manifest"]]
                if len(matches) != 1 or (matches[0] / "private").exists():
                    raise ValueError("SERVICE_CERTIFICATE_CURRENT_PUBLIC_EVIDENCE_REQUIRED")
                for name, expected in actual["certificate_manifest"]["public_artifacts"].items():
                    source = matches[0] / name
                    if hashlib.sha256(source.read_bytes()).hexdigest() != expected:
                        raise ValueError("SERVICE_CERTIFICATE_PUBLIC_BYTES_REQUIRED")
                    target = output / "certificates" / label / name
                    target.parent.mkdir(parents=True, exist_ok=True)
                    shutil.copyfile(source, target)
                    public_evidence.append({"path": str(target.relative_to(output)), "sha256": expected})
            evidence["public_certificate_artifacts"] = public_evidence
        (output / "check.json").write_text(json.dumps(evidence, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        raise SystemExit(result.returncode)
    if not args.execution or Path(args.execution).name != args.execution or args.execution in (".", ".."):
        raise ValueError("SERVICE_CERTIFICATE_EXECUTION_REQUIRED")
    output = ROOT / ".implementation-work" / ("IC-04-SERVICE-CERTIFICATE-" + args.execution)
    inputs = {"scripts/check_service_certificate.py", "scripts/prepare_service_certificates.py", "scripts/requirements-native-security.txt", "PRD.md", "LLD-后端.md",
        "docs/development/IMP-04.md", "docs/development/index.md"}
    for module in ("medical-identity", "medical-contracts", "ruoyi-common", "ruoyi-system", "ruoyi-framework"):
        inputs.add("business/" + module + "/pom.xml")
        inputs.update(str(path.relative_to(ROOT)) for path in (ROOT / "business" / module / "src").rglob("*") if path.is_file())
    inputs.add("business/pom.xml")
    for group in ("com/github/jnr", "org/ow2/asm"):
        dependencies = ROOT / ".implementation-work/maven-repository" / group
        inputs.update(str(path.relative_to(ROOT)) for path in dependencies.rglob("*")
            if path.is_file() and path.suffix in (".jar", ".pom"))
    for name in ("cryptography", "cffi", "pycparser"):
        package = ROOT / ".implementation-work/document-venv/lib/python3.12/site-packages" / name
        inputs.update(str(path.relative_to(ROOT)) for path in package.rglob("*") if path.is_file() and "__pycache__" not in path.parts)
    command = [sys.executable, str(CAPTURE), "--output-dir", str(output), "--check-id", "IC-04-SERVICE-CERTIFICATE",
        "--kind", "INTEGRATION", "--baseline-id", "BACK-SERVICE-CERTIFICATE-01", "--cwd", str(ROOT), "--timeout", "300"]
    for path in sorted(inputs):
        command.extend(["--input", path])
    for filename in (REPORT, "TEST-com.ruoyi.medical.identity.ServiceCertificateServletTest.xml",
            "TEST-com.ruoyi.medical.identity.ServiceCertificateTrustStoreTest.xml", "check.json"):
        command.extend(["--artifact", str(output / filename)])
    command.extend(["--", sys.executable, str(Path(__file__).resolve()), "--scope", args.scope, "--run", str(output)])
    raise SystemExit(subprocess.run(command, cwd=ROOT, env=environment).returncode)


if __name__ == "__main__":
    main()
