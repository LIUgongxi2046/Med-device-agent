import argparse
import json
import os
from pathlib import Path
import subprocess
import sys


ROOT = Path(__file__).resolve().parents[1]
CAPTURE = Path("/Users/liuhaoxian/.agents/skills/haonan-s000-pmp/scripts/capture_check.py")


def main():
    parser = argparse.ArgumentParser(description="执行计划结构与依赖规则的原生检查，保存实际源码和结果")
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--execution")
    mode.add_argument("--run", action="store_true")
    args = parser.parse_args()
    environment = os.environ.copy()
    tools = ROOT / ".implementation-work/toolchains"
    environment["JAVA_HOME"] = str((tools / "jdk-17.0.20.1+1/Contents/Home").resolve(strict=True))
    temporary = ROOT / ".implementation-work/java-temp"
    temporary.mkdir(parents=True, exist_ok=True)
    environment["MAVEN_OPTS"] = "-Dfile.encoding=UTF-8 -Djava.io.tmpdir=" + str(temporary)
    environment["TMPDIR"] = str(temporary)
    if args.run:
        command = [str((tools / "apache-maven-3.9.16/bin/mvn").resolve(strict=True)), "--batch-mode",
                   "--no-transfer-progress", "--offline", "-Dmaven.repo.local=" + str(ROOT / ".implementation-work/maven-repository"),
                   "-pl", "medical-persistence", "-am", "-Dtest=TaskPlanGraphTest,TaskInputSchemaTest",
                   "-Dsurefire.failIfNoSpecifiedTests=false", "test"]
        completed = subprocess.run(command, cwd=ROOT / "business", env=environment, check=False)
        raise SystemExit(completed.returncode)
    if Path(args.execution).name != args.execution or args.execution in (".", ".."):
        raise ValueError("执行身份必须使用独立目录名称")
    inputs = ["scripts/check_task_plan.py", "contracts/catalogue.json", "contracts/extensions/evaluation-results.catalogue.json", "business/medical-persistence/src/main/resources/evaluation-results.schema.json", "LLD-后端.md", "LLD-Agent.md",
              "backend-design/接口补充设计.md", "PRD.md", "实施任务计划.md", "docs/development/IMP-12.md"]
    catalogue = json.loads((ROOT / "contracts/catalogue.json").read_bytes())
    inputs.extend(item["path"] for item in catalogue["schemas"])
    for module in ("medical-contracts", "medical-data", "ruoyi-common", "ruoyi-system", "ruoyi-framework", "medical-identity", "medical-persistence"):
        inputs.append("business/" + module + "/pom.xml")
        inputs.extend(str(path.relative_to(ROOT)) for path in sorted((ROOT / "business" / module / "src").rglob("*")) if path.is_file())
    inputs.append("business/pom.xml")
    command = [sys.executable, str(CAPTURE), "--output-dir", ".implementation-work/IC-12-PLAN-" + args.execution,
               "--check-id", "IC-12-PLAN", "--kind", "UNIT", "--baseline-id", "task-plan-fixed-input-1.0.0",
               "--cwd", str(ROOT), "--timeout", "600"]
    for value in sorted(set(inputs)):
        command.extend(["--input", value])
    command.extend(["--artifact", "business/medical-persistence/target/surefire-reports/TEST-com.ruoyi.medical.persistence.TaskPlanGraphTest.xml",
                    "--artifact", "business/medical-persistence/target/surefire-reports/TEST-com.ruoyi.medical.persistence.TaskInputSchemaTest.xml",
                    "--", sys.executable, "scripts/check_task_plan.py", "--run"])
    completed = subprocess.run(command, cwd=ROOT, env=environment, check=False)
    raise SystemExit(completed.returncode)


if __name__ == "__main__":
    main()
