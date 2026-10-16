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
    parser = argparse.ArgumentParser(description="检查虚拟企业中的正式项目创建、修订与状态决定")
    parser.add_argument("--execution", required=True)
    parser.add_argument("--initial", action="store_true")
    parser.add_argument("--browser", action="store_true")
    parser.add_argument("--frontend-directory", type=Path)
    parser.add_argument("--timeout", type=int, default=1200)
    args = parser.parse_args()
    if Path(args.execution).name != args.execution or args.execution in (".", ".."):
        raise ValueError("执行身份必须使用单一目录名称")
    if args.timeout < 600 or args.timeout > 3600:
        raise ValueError("完整项目检查的总执行时限必须为600至3600秒")
    output = ROOT / ".implementation-work" / ("IC-04-PROJECT-SIMULATION-" + args.execution)
    if args.frontend_directory is not None and not args.browser:
        raise ValueError("前端制品需要浏览器检查")
    inputs = ["scripts/check_project_simulation.py", "business/pom.xml", "business/ruoyi-admin/pom.xml",
        "business/medical-persistence/pom.xml", "LLD-后端.md", "backend-design/接口补充设计.md",
        "contracts/catalogue.json", ".implementation-work/runtime/service-identity.json"]
    for directory in ("business/medical-contracts/src", "business/medical-data/src", "business/medical-identity/src",
            "business/medical-persistence/src", "business/ruoyi-admin/src", "business/ruoyi-framework/src",
            "business/ruoyi-system/src", "business/ruoyi-common/src"):
        inputs.extend(str(path.relative_to(ROOT)) for path in sorted((ROOT / directory).rglob("*")) if path.is_file())
    tools = ROOT / ".implementation-work/toolchains"
    temporary = ROOT / ".implementation-work/project-simulation-tmp"
    temporary.mkdir(parents=True, exist_ok=True)
    environment = os.environ.copy()
    environment.update({"JAVA_HOME": str(tools / "jdk-17.0.20.1+1/Contents/Home"), "TMPDIR": str(temporary),
        "MAVEN_OPTS": "-Xms128m -Xmx512m -Dfile.encoding=UTF-8 -Djava.io.tmpdir=" + str(temporary)})
    frontend = output / "production"
    if args.frontend_directory is not None:
        frontend = args.frontend_directory.resolve(strict=True)
        if not frontend.is_relative_to(ROOT / ".implementation-work") or not (frontend / "index.html").is_file():
            raise ValueError("前端制品需要使用项目检查目录的完整构建")
        inputs.extend(str(path.relative_to(ROOT)) for path in sorted(frontend.rglob("*")) if path.is_file())
    if args.browser:
        for directory in ("web/src", "web/vite", "web/public"):
            inputs.extend(str(path.relative_to(ROOT)) for path in sorted((ROOT / directory).rglob("*")) if path.is_file())
        inputs.extend(("tests/browser/project-simulation-checks.js", "tests/browser/package.json", "tests/browser/package-lock.json",
            "web/package.json", "web/package-lock.json", "web/vite.config.js", "web/tsconfig.medical.json", "web/.env.medical", "web/index.html"))
        if args.frontend_directory is None:
            output.mkdir(parents=True, exist_ok=True)
            subprocess.run(["npm", "run", "typecheck:medical"], cwd=ROOT / "web", env=environment, check=True)
            subprocess.run(["npm", "run", "build:prod", "--", "--mode", "medical", "--outDir", str(frontend)],
                cwd=ROOT / "web", env=environment, check=True)
    command = [sys.executable, str(CAPTURE), "--output-dir", str(output / "command" if args.browser else output), "--check-id", "IC-04-PROJECT-SIMULATION",
        "--kind", "INTEGRATION", "--baseline-id", "BIZ02-SIMULATION-01", "--cwd", str(ROOT), "--timeout", str(args.timeout)]
    for filename in sorted(set(inputs)):
        command.extend(["--input", filename])
    command.extend(["--artifact", "business/ruoyi-admin/target/surefire-reports/TEST-com.ruoyi.medical.runtime.ProjectSimulationHttpTest.xml"])
    if not args.initial:
        command.extend(["--artifact", str(output / "project-result.json")])
        if args.browser:
            command.extend(["--artifact", str(output / "project-browser-result.json"), "--artifact", str(output / "project-browser-output.log")])
    command.extend(["--", str(tools / "apache-maven-3.9.16/bin/mvn"), "-f", "business/pom.xml", "--batch-mode", "--no-transfer-progress",
        "-Dmaven.repo.local=" + str(ROOT / ".implementation-work/maven-repository"), "-pl", "ruoyi-admin", "-am",
        "-Dmedical.projectSimulationEvidence=" + str(output / "project-result.json"),
        "-Dmedical.projectSimulationBrowser=" + str(args.browser).lower(), "-Dmedical.projectSimulationFrontend=" + str(frontend),
        "-Dtest=ProjectSimulationHttpTest", "-Dsurefire.failIfNoSpecifiedTests=false", "test"])
    result = subprocess.run(command, cwd=ROOT, env=environment).returncode
    if result != 0 or not args.browser:
        raise SystemExit(result)
    original = output / 'command/receipt.json'
    receipt = json.loads(original.read_bytes())
    if receipt['exit_code'] != 0 or not receipt['inputs_unchanged']:
        raise ValueError('实际项目回执未通过')
    report = ROOT / 'business/ruoyi-admin/target/surefire-reports/TEST-com.ruoyi.medical.runtime.ProjectSimulationHttpTest.xml'
    references = [item for item in receipt['artifacts'] if Path(item['path']) == report]
    if len(references) != 1 or hashlib.sha256(report.read_bytes()).hexdigest() != references[0]['sha256']:
        raise ValueError('实际项目JUnit与执行回执不一致')
    fixed = output / report.name
    shutil.copyfile(report, fixed)
    if hashlib.sha256(fixed.read_bytes()).hexdigest() != references[0]['sha256']:
        raise ValueError('固定项目JUnit字节不一致')
    references[0]['path'] = str(fixed)
    for artifact in (original, output / 'project-result.json', output / 'project-browser-result.json', output / 'project-browser-output.log'):
        digest = hashlib.sha256(artifact.read_bytes()).hexdigest()
        existing = [item for item in receipt['artifacts'] if Path(item['path']) == artifact]
        if existing and (len(existing) != 1 or existing[0]['sha256'] != digest):
            raise ValueError('实际项目产物摘要不一致')
        if not existing:
            receipt['artifacts'].append({'path': str(artifact), 'sha256': digest})
    target = output / 'receipt.json'
    target.write_text(json.dumps(receipt, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps({'result': 'PASS', 'receipt': str(target.relative_to(ROOT)),
        'sha256': hashlib.sha256(target.read_bytes()).hexdigest(), 'inputs': len(receipt['inputs']),
        'artifacts': len(receipt['artifacts'])}))


if __name__ == "__main__":
    main()
