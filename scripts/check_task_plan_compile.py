import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys


ROOT = Path(__file__).resolve().parents[1]
CAPTURE = Path("/Users/liuhaoxian/.agents/skills/haonan-s000-pmp/scripts/capture_check.py")


def main():
    parser = argparse.ArgumentParser(description="编译完整计划关系模块及原测试，保存实际class制品")
    parser.add_argument("--execution", required=True)
    args = parser.parse_args()
    if Path(args.execution).name != args.execution or args.execution in (".", ".."):
        raise ValueError("执行身份必须使用单一目录名称")
    toolchains = ROOT / ".implementation-work/toolchains"
    java = (toolchains / "jdk-17.0.20.1+1/Contents/Home").resolve(strict=True)
    maven = (toolchains / "apache-maven-3.9.16/bin/mvn").resolve(strict=True)
    inputs = [ROOT / "scripts/check_task_plan_compile.py", ROOT / "business/pom.xml", ROOT / "contracts/catalogue.json",
              ROOT / "LLD-后端.md", ROOT / "LLD-数据.md", ROOT / "backend-design/接口补充设计.md"]
    for module in ("medical-contracts", "medical-data", "ruoyi-common", "ruoyi-system", "ruoyi-framework", "medical-identity", "medical-persistence"):
        inputs.append(ROOT / "business" / module / "pom.xml")
        inputs.extend(path for path in sorted((ROOT / "business" / module / "src").rglob("*")) if path.is_file())
    output = ROOT / ".implementation-work" / ("IC-12-PLAN-COMPILE-" + args.execution)
    capture = [sys.executable, str(CAPTURE), "--output-dir", str(output), "--check-id", "IC-12-PLAN-COMPILE",
               "--kind", "STATIC", "--baseline-id", "BACK-TASK-PLAN-RELATIONS-01", "--cwd", str(ROOT / "business"), "--timeout", "600"]
    for path in sorted(set(inputs)):
        capture.extend(("--input", str(path)))
    directory = ROOT / "business/medical-persistence/target/classes/com/ruoyi/medical/persistence"
    names = ("TaskPlanProjection.class", "TaskPlanRelationStore.class", "TaskPlanWriteProtocol.class",
             "mapper/TaskPlanRelationMapper.class", "mapper/TaskPlanWriteMapper.class", "TenantScope.class")
    for name in names:
        capture.extend(("--artifact", str(directory / name)))
    temporary = ROOT / ".implementation-work/task-plan-compile-tmp"
    temporary.mkdir(parents=True, exist_ok=True)
    environment = os.environ.copy()
    environment["JAVA_HOME"] = str(java)
    environment["TMPDIR"] = str(temporary)
    environment["MAVEN_OPTS"] = "-Dfile.encoding=UTF-8 -Djava.io.tmpdir=" + str(temporary)
    command = [str(maven), "--offline", "--batch-mode", "--no-transfer-progress",
               "-Dmaven.repo.local=" + str(ROOT / ".implementation-work/maven-repository"),
               "-pl", "medical-persistence", "-am", "test-compile"]
    subprocess.run([*capture, "--", *command], cwd=ROOT, env=environment, check=True)
    saved = []
    for source in sorted(directory.rglob("*.class")):
        if source.name.startswith(("TaskPlan", "TenantScope")):
            target = output / "classes" / source.relative_to(directory)
            target.parent.mkdir(parents=True, exist_ok=True)
            original = hashlib.sha256(source.read_bytes()).hexdigest()
            shutil.copyfile(source, target)
            if hashlib.sha256(target.read_bytes()).hexdigest() != original:
                raise ValueError("独立class副本与实际编译制品不一致")
            saved.append({"path": str(target.relative_to(ROOT)), "sha256": original})
    result = {"scope": "TASK_PLAN_PUBLIC_JAVA_COMPILE_ONLY", "classes": saved,
              "receipt_sha256": hashlib.sha256((output / "receipt.json").read_bytes()).hexdigest(),
              "database_execution_verified": False, "public_task_write_verified": False}
    (output / "compiled.json").write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
