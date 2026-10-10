import argparse
import os
from pathlib import Path
import subprocess
import sys


ROOT = Path(__file__).resolve().parents[1]
CAPTURE = Path("/Users/liuhaoxian/.agents/skills/haonan-s000-pmp/scripts/capture_check.py")


def main():
    parser = argparse.ArgumentParser(description="保存实际MySQL对象引用表示及固定原件读取检查")
    parser.add_argument("--execution", required=True)
    args = parser.parse_args()
    if Path(args.execution).name != args.execution or args.execution in (".", ".."):
        raise ValueError("执行身份必须使用单一目录名称")
    toolchains = ROOT / ".implementation-work/toolchains"
    java_home = (toolchains / "jdk-17.0.20.1+1/Contents/Home").resolve(strict=True)
    maven = (toolchains / "apache-maven-3.9.16/bin/mvn").resolve(strict=True)
    inputs = ["scripts/check_stored_object_references.py", "business/pom.xml", "business/medical-persistence/pom.xml",
              "business/medical-identity/pom.xml", "business/medical-contracts/pom.xml", "contracts/catalogue.json",
              "backend-design/interfaces.schema.yaml", "backend-design/接口补充设计.md", "LLD-后端.md", "data-design/core.sql",
              "business/medical-data/src/main/resources/db/migration/005-document-evidence.sql",
              ".implementation-work/runtime/service-identity.json", ".implementation-work/runtime/object-storage/deployment.json",
              ".implementation-work/toolchains/seaweedfs-4.48/weed",
              ".implementation-work/maven-repository/com/mysql/mysql-connector-j/9.7.0/mysql-connector-j-9.7.0.jar",
              ".implementation-work/IC-05-OBJECT-STORAGE-20261005-OBJECT-STORAGE-FINAL-01/objects.json",
              "ui-delivery/assets/public-evidence/clinical-evaluation-2021.docx",
              "ui-delivery/assets/public-evidence/safety-principles-2021.doc"]
    for module in ("medical-contracts", "medical-identity", "medical-persistence"):
        inputs.extend(str(path.relative_to(ROOT)) for path in sorted((ROOT / "business" / module / "src").rglob("*")) if path.is_file())
    command = [str(maven), "--batch-mode", "--no-transfer-progress",
               "-Dmaven.repo.local=" + str(ROOT / ".implementation-work/maven-repository"),
               "-pl", "medical-persistence", "-am", "-Dtest=StoredObjectReferencesTest", "-Dsurefire.failIfNoSpecifiedTests=false", "test"]
    capture = [sys.executable, str(CAPTURE), "--output-dir", ".implementation-work/IC-05-OBJECT-PERSISTENCE-" + args.execution,
               "--check-id", "IC-05-OBJECT-PERSISTENCE", "--kind", "INTEGRATION",
               "--baseline-id", "local-database-and-private-storage-public-originals", "--cwd", str(ROOT / "business"), "--timeout", "900"]
    for path in inputs:
        capture.extend(["--input", str(ROOT / path)])
    capture.extend(["--artifact", str(ROOT / "business/medical-persistence/target/surefire-reports/TEST-com.ruoyi.medical.persistence.StoredObjectReferencesTest.xml")])
    environment = os.environ.copy()
    environment["JAVA_HOME"] = str(java_home)
    environment["MAVEN_OPTS"] = "-Dfile.encoding=UTF-8 -Djava.io.tmpdir=" + str(ROOT / ".implementation-work/java-temp")
    temporary = ROOT / ".implementation-work/stored-object-reference-tmp"
    temporary.mkdir(parents=True, exist_ok=True)
    environment["TMPDIR"] = str(temporary)
    subprocess.run([*capture, "--", *command], cwd=ROOT, env=environment, check=True)


if __name__ == "__main__":
    main()
