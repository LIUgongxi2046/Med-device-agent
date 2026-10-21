import argparse
import os
from pathlib import Path
import subprocess
import sys


ROOT = Path(__file__).resolve().parents[1]
from check_support import capture_check_path
CAPTURE = capture_check_path()
TESTS = ("S3ObjectWriterTest", "UploadTransferTest", "UploadAuthorizationTest", "DocumentUploadBasisTest", "DocumentUploadPreparationTest", "DocumentUploadIssuanceTest", "IncomingUploadPartTest", "TransferConcurrencyTest", "ServletUploadInputTest")


def main():
    parser = argparse.ArgumentParser(description="核查真实上传分片重组、原写入恢复及当前传输授权")
    parser.add_argument("--execution", required=True)
    parser.add_argument("--test", choices=TESTS, action="append")
    args = parser.parse_args()
    tests = TESTS if args.test is None else tuple(args.test)
    if Path(args.execution).name != args.execution or args.execution in (".", ".."):
        raise ValueError("执行身份必须使用单一目录名称")
    toolchains = ROOT / ".implementation-work/toolchains"
    java = (toolchains / "jdk-17.0.20.1+1/Contents/Home").resolve(strict=True)
    maven = (toolchains / "apache-maven-3.9.16/bin/mvn").resolve(strict=True)
    inputs = ["scripts/check_upload_io.py", "business/pom.xml", "business/medical-persistence/pom.xml",
        "backend-design/接口补充设计.md", "LLD-后端.md", "LLD-数据.md",
        ".implementation-work/runtime/service-identity.json", ".implementation-work/runtime/object-storage/deployment.json",
        ".implementation-work/maven-repository/org/redisson/redisson/3.52.0/redisson-3.52.0.jar",
        ".implementation-work/maven-repository/software/amazon/awssdk/sdk-core/2.55.11/sdk-core-2.55.11.jar",
        ".implementation-work/maven-repository/software/amazon/awssdk/apache-client/2.55.11/apache-client-2.55.11.jar",
        ".implementation-work/maven-repository/org/springframework/spring-web/6.2.19/spring-web-6.2.19.jar",
        ".implementation-work/maven-repository/org/springframework/spring-core/6.2.19/spring-core-6.2.19.jar",
        ".implementation-work/maven-repository/io/projectreactor/reactor-core/3.7.19/reactor-core-3.7.19.jar",
        ".implementation-work/maven-repository/org/reactivestreams/reactive-streams/1.0.4/reactive-streams-1.0.4.jar",
        ".implementation-work/maven-repository/org/apache/tomcat/embed/tomcat-embed-core/10.1.55/tomcat-embed-core-10.1.55.jar",
        ".implementation-work/maven-repository/jakarta/servlet/jakarta.servlet-api/6.0.0/jakarta.servlet-api-6.0.0.jar",
        ".implementation-work/maven-repository/org/awaitility/awaitility/4.2.2/awaitility-4.2.2.jar",
        ".implementation-work/maven-repository/org/hamcrest/hamcrest/3.0/hamcrest-3.0.jar",
        ".implementation-work/toolchains/seaweedfs-4.48/weed",
        ".implementation-work/IC-05-OBJECT-STORAGE-20261005-OBJECT-STORAGE-FINAL-01/objects.json",
        "ui-delivery/assets/public-evidence/clinical-evaluation-2021.docx", "ui-delivery/assets/public-evidence/safety-principles-2021.doc"]
    for module in ("medical-contracts", "medical-identity", "medical-persistence", "medical-data"):
        inputs.extend(str(path.relative_to(ROOT)) for path in sorted((ROOT / "business" / module / "src").rglob("*")) if path.is_file())
    capture = [sys.executable, str(CAPTURE), "--output-dir", ".implementation-work/IC-05-UPLOAD-IO-" + args.execution,
        "--check-id", "IC-05-UPLOAD-IO", "--kind", "INTEGRATION", "--baseline-id", "BACK-UPLOAD-IO-01",
        "--cwd", str(ROOT / "business"), "--timeout", "900"]
    for path in inputs:
        capture.extend(["--input", str(ROOT / path)])
    for name in tests:
        capture.extend(["--artifact", str(ROOT / "business/medical-persistence/target/surefire-reports" / ("TEST-com.ruoyi.medical.persistence." + name + ".xml"))])
    temporary = ROOT / ".implementation-work/upload-io-tmp"
    temporary.mkdir(parents=True, exist_ok=True)
    environment = os.environ.copy(); environment["JAVA_HOME"] = str(java); environment["TMPDIR"] = str(temporary)
    environment["MAVEN_OPTS"] = "-Dfile.encoding=UTF-8 -Djava.io.tmpdir=" + str(temporary)
    command = [str(maven), "--batch-mode", "--no-transfer-progress", "-Dmaven.repo.local=" + str(ROOT / ".implementation-work/maven-repository"),
        "-pl", "medical-persistence", "-am", "-Dtest=" + ",".join(tests),
        "-Dsurefire.failIfNoSpecifiedTests=false", "test"]
    subprocess.run([*capture, "--", *command], cwd=ROOT, env=environment, check=True)


if __name__ == "__main__":
    main()
