import argparse
import json
import os
from pathlib import Path
import subprocess
import sys


ROOT = Path(__file__).resolve().parents[1]
CAPTURE = Path("/Users/liuhaoxian/.agents/skills/haonan-s000-pmp/scripts/capture_check.py")


def main():
    parser = argparse.ArgumentParser(description="核查资料集固定版本、真实查询与框架分页")
    parser.add_argument("--execution", required=True)
    parser.add_argument("--check", choices=("native", "runtime"), default="native")
    args = parser.parse_args()
    if Path(args.execution).name != args.execution or args.execution in (".", ".."):
        raise ValueError("执行身份必须使用单一目录名称")
    temporary = ROOT / ".implementation-work/collection-check-tmp"
    temporary.mkdir(parents=True, exist_ok=True)
    tools = ROOT / ".implementation-work/toolchains"
    environment = os.environ.copy()
    environment.update({"JAVA_HOME": str(tools / "jdk-17.0.20.1+1/Contents/Home"), "TMPDIR": str(temporary),
                        "MAVEN_OPTS": "-Dfile.encoding=UTF-8 -Djava.io.tmpdir=" + str(temporary)})
    inputs = ["scripts/check_collections.py", "business/pom.xml", "business/medical-persistence/pom.xml",
              "contracts/catalogue.json", "contracts/extensions/evaluation-results.catalogue.json", "business/medical-persistence/src/main/resources/evaluation-results.schema.json", "LLD-后端.md", "backend-design/接口补充设计.md", ".implementation-work/runtime/service-identity.json"]
    for directory in ("business/medical-contracts/src", "business/medical-persistence/src", "business/medical-data/src"):
        inputs.extend(str(path.relative_to(ROOT)) for path in sorted((ROOT / directory).rglob("*")) if path.is_file())
    check_id = "IC-10-COLLECTION"
    artifacts = ["business/medical-persistence/target/surefire-reports/TEST-com.ruoyi.medical.persistence.CollectionTest.xml"]
    action = [str(tools / "apache-maven-3.9.16/bin/mvn"), "-f", "business/pom.xml", "--batch-mode", "--no-transfer-progress",
              "-Dmaven.repo.local=" + str(ROOT / ".implementation-work/maven-repository"), "-pl", "medical-persistence", "-am",
              "-Dtest=CollectionTest", "-Dsurefire.failIfNoSpecifiedTests=false", "test"]
    if args.check == "runtime":
        check_id = "IC-10-COLLECTION-HTTP"
        inputs.extend(("tests/test_collection_runtime.py", "tests/test_local_identity_runtime.py", "scripts/local_runtime.py",
            "business/ruoyi-admin/src/main/resources/application-medical-local.yml",
            ".implementation-work/runtime/application-deployment.json", "business/ruoyi-admin/target/ruoyi-admin.jar"))
        deployment = json.loads((ROOT / ".implementation-work/runtime/application-deployment.json").read_bytes())
        artifact = Path(deployment["artifact"])
        if artifact.is_absolute() or ".." in artifact.parts or not artifact.is_relative_to(".implementation-work/runtime/applications"):
            raise ValueError("部署制品必须属于当前项目")
        inputs.append(str(artifact))
        artifacts = [".implementation-work/runtime/collection-rejection-receipt.json"]
        action = [sys.executable, "tests/test_collection_runtime.py"]
    command = [sys.executable, str(CAPTURE), "--output-dir", ".implementation-work/" + check_id + "-" + args.execution,
               "--check-id", check_id, "--kind", "INTEGRATION", "--baseline-id", "BACK-COLLECTION-VERSIONS-01",
               "--cwd", str(ROOT), "--timeout", "900"]
    for path in inputs:
        command.extend(["--input", path])
    for path in artifacts:
        command.extend(["--artifact", path])
    command.extend(["--", *action])
    result = subprocess.run(command, cwd=ROOT, env=environment, check=False)
    raise SystemExit(result.returncode)


if __name__ == "__main__":
    main()
