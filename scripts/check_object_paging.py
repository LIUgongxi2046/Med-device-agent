import argparse
import os
from pathlib import Path
import subprocess
import sys


ROOT = Path(__file__).resolve().parents[1]
from check_support import capture_check_path
CAPTURE = capture_check_path()


def main():
    parser = argparse.ArgumentParser(description="保存真实原件分页、加密游标及完整内容消费的原生检查")
    parser.add_argument("--execution", required=True)
    args = parser.parse_args()
    if Path(args.execution).name != args.execution or args.execution in (".", ".."):
        raise ValueError("执行身份必须使用单一目录名称")
    toolchains = ROOT / ".implementation-work/toolchains"
    java_home = (toolchains / "jdk-17.0.20.1+1/Contents/Home").resolve(strict=True)
    maven = (toolchains / "apache-maven-3.9.16/bin/mvn").resolve(strict=True)
    inputs = ["scripts/check_object_paging.py", "business/pom.xml", "business/medical-persistence/pom.xml",
              "business/medical-identity/pom.xml", "business/medical-contracts/pom.xml", "contracts/catalogue.json", "contracts/extensions/evaluation-results.catalogue.json", "business/medical-persistence/src/main/resources/evaluation-results.schema.json",
              "backend-design/interfaces.schema.yaml", "backend-design/接口补充设计.md", "LLD-后端.md",
              ".implementation-work/runtime/object-storage/deployment.json",
              ".implementation-work/toolchains/seaweedfs-4.48/weed",
              ".implementation-work/IC-05-OBJECT-STORAGE-20261005-OBJECT-STORAGE-FINAL-01/objects.json",
              "ui-delivery/assets/public-evidence/clinical-evaluation-2021.docx",
              "ui-delivery/assets/public-evidence/safety-principles-2021.doc"]
    for module in ("medical-contracts", "medical-identity", "medical-persistence"):
        inputs.extend(str(path.relative_to(ROOT)) for path in sorted((ROOT / "business" / module / "src").rglob("*")) if path.is_file())
    command = [str(maven), "--batch-mode", "--no-transfer-progress",
               "-Dmaven.repo.local=" + str(ROOT / ".implementation-work/maven-repository"),
               "-pl", "medical-persistence", "-am", "-Dtest=ObjectContentPagingTest",
               "-Dsurefire.failIfNoSpecifiedTests=false", "test"]
    capture = [sys.executable, str(CAPTURE), "--output-dir", ".implementation-work/IC-05-OBJECT-PAGING-" + args.execution,
               "--check-id", "IC-05-OBJECT-PAGING", "--kind", "INTEGRATION",
               "--baseline-id", "local-private-storage-public-originals", "--cwd", str(ROOT / "business"), "--timeout", "900"]
    for path in inputs:
        capture.extend(["--input", str(ROOT / path)])
    capture.extend(["--artifact", str(ROOT / "business/medical-persistence/target/surefire-reports/TEST-com.ruoyi.medical.persistence.ObjectContentPagingTest.xml")])
    environment = os.environ.copy()
    environment["JAVA_HOME"] = str(java_home)
    environment["MAVEN_OPTS"] = "-Dfile.encoding=UTF-8 -Djava.io.tmpdir=" + str(ROOT / ".implementation-work/java-temp")
    temporary = ROOT / ".implementation-work/object-paging-tmp"
    temporary.mkdir(parents=True, exist_ok=True)
    environment["TMPDIR"] = str(temporary)
    subprocess.run([*capture, "--", *command], cwd=ROOT, env=environment, check=True)


if __name__ == "__main__":
    main()
