import argparse
import os
from pathlib import Path
import subprocess
import sys


ROOT = Path(__file__).resolve().parents[1]
CAPTURE = Path("/Users/liuhaoxian/.agents/skills/haonan-s000-pmp/scripts/capture_check.py")


def main():
    parser = argparse.ArgumentParser(description="核查固定发布Schema引用及实际完整UTF8内容")
    parser.add_argument("--execution", required=True)
    args = parser.parse_args()
    if Path(args.execution).name != args.execution or args.execution in (".", ".."):
        raise ValueError("执行身份必须使用单一目录名称")
    toolchains = ROOT / ".implementation-work/toolchains"
    java_home = (toolchains / "jdk-17.0.20.1+1/Contents/Home").resolve(strict=True)
    maven = (toolchains / "apache-maven-3.9.16/bin/mvn").resolve(strict=True)
    inputs = ["scripts/check_published_schema_references.py", "business/pom.xml", "business/medical-contracts/pom.xml",
              "contracts/catalogue.json", "contracts/extensions/evaluation-results.catalogue.json", "business/medical-persistence/src/main/resources/evaluation-results.schema.json", "data-design/dataset.schema.json", "data-design/sample_dataset.json",
              "backend-design/interfaces.schema.yaml", "backend-design/resource-details.schema.yaml",
              "agent-design/artifacts.schema.json", "agent-design/model-stream.schema.json",
              "backend-design/接口补充设计.md", "LLD-后端.md"]
    inputs.extend(str(path.relative_to(ROOT)) for path in sorted((ROOT / "business/medical-contracts/src").rglob("*.java")))
    command = [str(maven), "--batch-mode", "--no-transfer-progress",
               "-Dmaven.repo.local=" + str(ROOT / ".implementation-work/maven-repository"),
               "-pl", "medical-contracts", "-am", "-Dtest=PublishedSchemaReferencesTest",
               "-Dsurefire.failIfNoSpecifiedTests=false", "test"]
    capture = [sys.executable, str(CAPTURE), "--output-dir", ".implementation-work/IC-05-PUBLISHED-SCHEMA-" + args.execution,
               "--check-id", "IC-05-PUBLISHED-SCHEMA", "--kind", "UNIT",
               "--baseline-id", "published-schema-and-actual-design-dataset", "--cwd", str(ROOT / "business"), "--timeout", "900"]
    for path in inputs:
        capture.extend(["--input", str(ROOT / path)])
    capture.extend(["--artifact", str(ROOT / "business/medical-contracts/target/surefire-reports/TEST-com.ruoyi.medical.contracts.PublishedSchemaReferencesTest.xml")])
    environment = os.environ.copy()
    environment["JAVA_HOME"] = str(java_home)
    environment["MAVEN_OPTS"] = "-Dfile.encoding=UTF-8 -Djava.io.tmpdir=" + str(ROOT / ".implementation-work/java-temp")
    temporary = ROOT / ".implementation-work/published-schema-tmp"
    temporary.mkdir(parents=True, exist_ok=True)
    environment["TMPDIR"] = str(temporary)
    subprocess.run([*capture, "--", *command], cwd=ROOT, env=environment, check=True)


if __name__ == "__main__":
    main()
