import argparse
import json
import os
from pathlib import Path
import subprocess
import sys


ROOT = Path(__file__).resolve().parents[1]
from check_support import capture_check_path
CAPTURE = capture_check_path()


def main():
    parser = argparse.ArgumentParser(description="核查固定任务读取、实际查询和企业准入")
    parser.add_argument("--execution", required=True)
    parser.add_argument("--check", choices=("native", "runtime"), default="native")
    args = parser.parse_args()
    if Path(args.execution).name != args.execution or args.execution in (".", ".."):
        raise ValueError("执行身份必须使用单一目录名称")
    tools = ROOT / ".implementation-work/toolchains"
    inputs = ["scripts/check_task_read.py", "business/pom.xml", "business/medical-persistence/pom.xml",
              "LLD-后端.md", "backend-design/接口补充设计.md", ".implementation-work/runtime/service-identity.json"]
    for directory in ("business/medical-contracts/src", "business/medical-persistence/src", "business/medical-data/src"):
        inputs.extend(str(path.relative_to(ROOT)) for path in sorted((ROOT / directory).rglob("*")) if path.is_file())
    temporary = ROOT / ".implementation-work/task-read-tmp"; temporary.mkdir(parents=True, exist_ok=True)
    environment = os.environ.copy()
    environment.update({"JAVA_HOME": str(tools / "jdk-17.0.20.1+1/Contents/Home"), "TMPDIR": str(temporary),
                        "MAVEN_OPTS": "-Dfile.encoding=UTF-8 -Djava.io.tmpdir=" + str(temporary)})
    action = [str(tools / "apache-maven-3.9.16/bin/mvn"), "-f", "business/pom.xml", "--batch-mode", "--no-transfer-progress",
              "-Dmaven.repo.local=" + str(ROOT / ".implementation-work/maven-repository"), "-pl", "medical-persistence", "-am",
              "-Dtest=TaskReadTest", "-Dsurefire.failIfNoSpecifiedTests=false", "test"]
    check_id = "IC-12-READ"
    artifacts = ["business/medical-persistence/target/surefire-reports/TEST-com.ruoyi.medical.persistence.TaskReadTest.xml"]
    if args.check == "runtime":
        check_id = "IC-12-READ-HTTP"
        inputs.extend(("tests/test_task_read_runtime.py", "tests/test_local_identity_runtime.py", "scripts/local_runtime.py",
            ".implementation-work/runtime/application-deployment.json", "business/ruoyi-admin/target/ruoyi-admin.jar"))
        deployment = json.loads((ROOT / ".implementation-work/runtime/application-deployment.json").read_bytes())
        artifact = Path(deployment["artifact"])
        if artifact.is_absolute() or ".." in artifact.parts or not artifact.is_relative_to(".implementation-work/runtime/applications"):
            raise ValueError("部署制品必须属于当前项目")
        inputs.append(str(artifact))
        artifacts = [".implementation-work/runtime/task-read-rejection-receipt.json"]
        action = [sys.executable, "tests/test_task_read_runtime.py"]
    command = [sys.executable, str(CAPTURE), "--output-dir", ".implementation-work/" + check_id + "-" + args.execution,
               "--check-id", check_id, "--kind", "INTEGRATION", "--baseline-id", "BACK-TASK-READ-01",
               "--cwd", str(ROOT), "--timeout", "900"]
    for path in inputs:
        command.extend(["--input", path])
    for path in artifacts:
        command.extend(["--artifact", path])
    subprocess.run([*command, "--", *action], cwd=ROOT, env=environment, check=True)


if __name__ == "__main__":
    main()
