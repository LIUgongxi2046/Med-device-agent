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
    parser = argparse.ArgumentParser(description="检查项目任务列表的真实SQL、输入及当前应用准入")
    parser.add_argument("--execution", required=True)
    parser.add_argument("--red", action="store_true")
    parser.add_argument("--browser", action="store_true")
    parser.add_argument("--frontend-directory", type=Path)
    args = parser.parse_args()
    if Path(args.execution).name != args.execution or args.execution in (".", ".."):
        raise ValueError("执行身份必须使用单一目录名称")
    if args.frontend_directory is not None and not args.browser:
        raise ValueError("前端制品需要浏览器检查")
    output = ROOT / ".implementation-work" / ("IC-12-LIST-" + args.execution)
    selected = [("ruoyi-admin", "runtime", "TaskListHttpTest")]
    if not args.red:
        selected.extend(("medical-persistence", "persistence", name) for name in ("TaskReadTest", "TaskListSchemaTest", "TaskInputSchemaTest"))
        directory = ROOT / "business/medical-contracts/src/test/java/com/ruoyi/medical/contracts"
        selected.extend(("medical-contracts", "contracts", path.stem) for path in sorted(directory.glob("*Test.java")))
    inputs = ["scripts/check_task_list.py", "business/pom.xml", "business/ruoyi-admin/pom.xml", "business/medical-persistence/pom.xml",
        "LLD-后端.md", "backend-design/接口补充设计.md", "contracts/catalogue.json", ".implementation-work/runtime/service-identity.json"]
    for directory in ("business/medical-contracts/src", "business/medical-data/src", "business/medical-identity/src",
            "business/medical-persistence/src", "business/ruoyi-admin/src", "business/ruoyi-framework/src", "business/ruoyi-system/src", "business/ruoyi-common/src"):
        inputs.extend(str(path.relative_to(ROOT)) for path in sorted((ROOT / directory).rglob("*")) if path.is_file())
    frontend = output / "production"
    if args.frontend_directory is not None:
        frontend = args.frontend_directory.resolve(strict=True)
        if not frontend.is_relative_to(ROOT / ".implementation-work") or not (frontend / "index.html").is_file():
            raise ValueError("前端制品需要使用项目检查目录的完整构建")
        inputs.extend(str(path.relative_to(ROOT)) for path in sorted(frontend.rglob("*")) if path.is_file())
    if args.browser:
        for directory in ("web/src", "web/vite", "web/public"):
            inputs.extend(str(path.relative_to(ROOT)) for path in sorted((ROOT / directory).rglob("*")) if path.is_file())
        inputs.extend(("tests/browser/task-simulation-checks.js", "tests/browser/task-detail-simulation-checks.js", "tests/browser/package.json", "tests/browser/package-lock.json",
            "web/package.json", "web/package-lock.json", "web/vite.config.js", "web/tsconfig.medical.json", "web/.env.medical", "web/index.html"))
    tools = ROOT / ".implementation-work/toolchains"
    temporary = ROOT / ".implementation-work/task-list-tmp"; temporary.mkdir(parents=True, exist_ok=True)
    environment = os.environ.copy()
    environment.update({"JAVA_HOME": str(tools / "jdk-17.0.20.1+1/Contents/Home"), "TMPDIR": str(temporary),
        "MAVEN_OPTS": "-Xms128m -Xmx512m -Dfile.encoding=UTF-8 -Djava.io.tmpdir=" + str(temporary)})
    if args.browser and args.frontend_directory is None:
        output.mkdir(parents=True, exist_ok=True)
        subprocess.run(["npm", "run", "typecheck:medical"], cwd=ROOT / "web", env=environment, check=True)
        subprocess.run(["npm", "run", "build:prod", "--", "--mode", "medical", "--outDir", str(output / "production")],
            cwd=ROOT / "web", env=environment, check=True)
    command = [sys.executable, str(CAPTURE), "--output-dir", str(output / "command" if args.browser else output), "--check-id", "IC-12-LIST",
        "--kind", "INTEGRATION", "--baseline-id", "BACK-TASK-LIST-01", "--cwd", str(ROOT), "--timeout", "900"]
    for filename in sorted(set(inputs)):
        command.extend(["--input", filename])
    for module, namespace, name in selected:
        command.extend(["--artifact", "business/" + module + "/target/surefire-reports/TEST-com.ruoyi.medical." + namespace + "." + name + ".xml"])
    if not args.red:
        command.extend(["--artifact", str(output / "task-list-result.json")])
        if args.browser:
            command.extend(["--artifact", str(output / "task-browser-result.json"), "--artifact", str(output / "task-browser-output.log")])
    action = [str(tools / "apache-maven-3.9.16/bin/mvn"), "-f", "business/pom.xml", "--batch-mode", "--no-transfer-progress",
        "-Dmaven.repo.local=" + str(ROOT / ".implementation-work/maven-repository"), "-pl", "ruoyi-admin", "-am",
        "-Dmedical.taskListEvidence=" + str(output / "task-list-result.json"),
        "-Dmedical.taskListBrowser=" + str(args.browser).lower(),
        "-Dmedical.taskListFrontend=" + str(frontend),
        "-Dtest=" + ",".join(name for module, namespace, name in selected), "-Dsurefire.failIfNoSpecifiedTests=false", "test"]
    result = subprocess.run([*command, "--", *action], cwd=ROOT, env=environment).returncode
    if result != 0 or not args.browser:
        raise SystemExit(result)
    receipt_path = output / "command/receipt.json"
    receipt = json.loads(receipt_path.read_bytes())
    # 保存原始JUnit，后续构建不改变本次任务业务检查证据。
    for item in receipt["artifacts"]:
        source = Path(item["path"])
        if source.name.startswith("TEST-") and source.suffix == ".xml":
            target = output / "junit" / source.parents[2].name / source.name
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(source, target)
            if hashlib.sha256(target.read_bytes()).hexdigest() != item["sha256"]:
                raise ValueError("TASK_JUNIT_ARCHIVE_MISMATCH")
            item["path"] = str(target)
    artifacts = [receipt_path]
    artifacts.extend(path for path in sorted(frontend.rglob("*")) if path.is_file())
    for path in artifacts:
        receipt["artifacts"].append({"path": str(path), "sha256": hashlib.sha256(path.read_bytes()).hexdigest()})
    target = output / "receipt.json"
    with target.open("x", encoding="utf-8") as saved:
        json.dump(receipt, saved, ensure_ascii=False, indent=2)
    print(json.dumps({"result": "PASS", "receipt": str(target.relative_to(ROOT)),
        "sha256": hashlib.sha256(target.read_bytes()).hexdigest(), "inputs": len(receipt["inputs"]),
        "artifacts": len(receipt["artifacts"])}))


if __name__ == "__main__":
    main()
