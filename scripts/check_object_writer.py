import argparse
import os
from pathlib import Path
import subprocess
import sys


ROOT = Path(__file__).resolve().parents[1]
from check_support import capture_check_path
CAPTURE = capture_check_path()


def main():
    parser = argparse.ArgumentParser(description="核查真实私有对象写入、固定版本及原对象恢复")
    parser.add_argument("--execution", required=True)
    args = parser.parse_args()
    if Path(args.execution).name != args.execution or args.execution in (".", ".."):
        raise ValueError("执行身份必须使用单一目录名称")
    toolchains = ROOT / ".implementation-work/toolchains"
    java = (toolchains / "jdk-17.0.20.1+1/Contents/Home").resolve(strict=True)
    maven = (toolchains / "apache-maven-3.9.16/bin/mvn").resolve(strict=True)
    inputs = ["scripts/check_object_writer.py", "business/pom.xml", "business/medical-persistence/pom.xml",
              "backend-design/接口补充设计.md", "LLD-后端.md", "LLD-数据.md",
              ".implementation-work/runtime/object-storage/deployment.json", ".implementation-work/toolchains/seaweedfs-4.48/weed",
              ".implementation-work/IC-05-OBJECT-STORAGE-20261005-OBJECT-STORAGE-FINAL-01/objects.json",
              "ui-delivery/assets/public-evidence/clinical-evaluation-2021.docx",
              "ui-delivery/assets/public-evidence/safety-principles-2021.doc"]
    for module in ("medical-contracts", "medical-identity", "medical-persistence"):
        inputs.extend(str(path.relative_to(ROOT)) for path in sorted((ROOT / "business" / module / "src").rglob("*")) if path.is_file())
    capture = [sys.executable, str(CAPTURE), "--output-dir", ".implementation-work/IC-05-OBJECT-WRITE-" + args.execution,
               "--check-id", "IC-05-OBJECT-WRITE", "--kind", "INTEGRATION", "--baseline-id", "BACK-OBJECT-WRITE-01",
               "--cwd", str(ROOT / "business"), "--timeout", "900"]
    for path in inputs:
        capture.extend(["--input", str(ROOT / path)])
    capture.extend(["--artifact", str(ROOT / "business/medical-persistence/target/surefire-reports/TEST-com.ruoyi.medical.persistence.S3ObjectWriterTest.xml")])
    temporary = ROOT / ".implementation-work/object-writer-tmp"
    temporary.mkdir(parents=True, exist_ok=True)
    environment = os.environ.copy(); environment["JAVA_HOME"] = str(java); environment["TMPDIR"] = str(temporary)
    environment["MAVEN_OPTS"] = "-Dfile.encoding=UTF-8 -Djava.io.tmpdir=" + str(temporary)
    command = [str(maven), "--batch-mode", "--no-transfer-progress", "-Dmaven.repo.local=" + str(ROOT / ".implementation-work/maven-repository"),
               "-pl", "medical-persistence", "-am", "-Dtest=S3ObjectWriterTest", "-Dsurefire.failIfNoSpecifiedTests=false", "test"]
    subprocess.run([*capture, "--", *command], cwd=ROOT, env=environment, check=True)


if __name__ == "__main__":
    main()
