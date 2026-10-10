import argparse
import os
from pathlib import Path
import subprocess
import sys


ROOT = Path(__file__).resolve().parents[1]
CAPTURE = Path("/Users/liuhaoxian/.agents/skills/haonan-s000-pmp/scripts/capture_check.py")


def main():
    parser = argparse.ArgumentParser(description="核查真实官方来源对应的完整首次注册目录")
    parser.add_argument("--execution", required=True)
    parser.add_argument("--test", choices=("RegistrationDirectoryTest", "TemplateDefinitionSchemaTest"))
    args = parser.parse_args()
    if Path(args.execution).name != args.execution or args.execution in (".", ".."):
        raise ValueError("执行身份必须使用单一目录名称")
    java = ROOT / ".implementation-work/toolchains/jdk-17.0.20.1+1/Contents/Home"
    maven = ROOT / ".implementation-work/toolchains/apache-maven-3.9.16/bin/mvn"
    inputs = ["scripts/check_template_directory.py", "business/pom.xml", "business/medical-persistence/pom.xml", "business/medical-contracts/pom.xml",
              "PRD.md", "LLD-数据.md", "LLD-后端.md", "data-design/sample_dataset.json",
              "backend-design/接口补充设计.md", "ui-delivery/document-reading-design.md",
              "ui-delivery/research/official/registration-requirements-2021-121.txt",
              ".implementation-work/maven-repository/com/networknt/json-schema-validator/1.5.9/json-schema-validator-1.5.9.jar",
              ".implementation-work/maven-repository/org/jgrapht/jgrapht-core/1.5.2/jgrapht-core-1.5.2.jar",
              ".implementation-work/maven-repository/com/fasterxml/jackson/core/jackson-databind/2.21.4/jackson-databind-2.21.4.jar"]
    for module in ("medical-contracts", "medical-identity", "medical-persistence", "medical-data"):
        inputs.extend(str(path.relative_to(ROOT)) for path in sorted((ROOT / "business" / module / "src").rglob("*")) if path.is_file())
    capture = [sys.executable, str(CAPTURE), "--output-dir", ".implementation-work/IC-11-DIRECTORY-" + args.execution,
               "--check-id", "IC-11-DIRECTORY", "--kind", "INTEGRATION", "--baseline-id", "BACK-REGISTRATION-DIRECTORY-01",
               "--cwd", str(ROOT / "business"), "--timeout", "900"]
    for path in inputs:
        capture.extend(["--input", str(ROOT / path)])
    tests = [args.test] if args.test else ["RegistrationDirectoryTest", "TemplateDefinitionSchemaTest"]
    for test in tests:
        capture.extend(["--artifact", str(ROOT / ("business/medical-persistence/target/surefire-reports/TEST-com.ruoyi.medical.persistence." + test + ".xml"))])
    temporary = ROOT / ".implementation-work/template-directory-tmp"
    temporary.mkdir(parents=True, exist_ok=True)
    environment = os.environ.copy()
    environment.update(JAVA_HOME=str(java), TMPDIR=str(temporary), MAVEN_OPTS="-Dfile.encoding=UTF-8 -Djava.io.tmpdir=" + str(temporary))
    command = [str(maven), "--batch-mode", "--no-transfer-progress", "-Dmaven.repo.local=" + str(ROOT / ".implementation-work/maven-repository"),
               "-pl", "medical-persistence", "-am", "-Dtest=" + ",".join(tests), "-Dsurefire.failIfNoSpecifiedTests=false", "test"]
    subprocess.run([*capture, "--", *command], cwd=ROOT, env=environment, check=True)


if __name__ == "__main__":
    main()
