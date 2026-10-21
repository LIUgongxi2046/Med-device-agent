import argparse
import os
from pathlib import Path
import subprocess
import sys


ROOT = Path(__file__).resolve().parents[1]
from check_support import capture_check_path
CAPTURE = capture_check_path()


def main():
    parser = argparse.ArgumentParser(description="检查虚拟企业中的岗位发布、历史版本和撤销")
    parser.add_argument("--execution", required=True)
    parser.add_argument("--initial", action="store_true")
    parser.add_argument("--browser", action="store_true")
    parser.add_argument("--frontend-directory", type=Path)
    args = parser.parse_args()
    if Path(args.execution).name != args.execution or args.execution in (".", ".."):
        raise ValueError("执行身份必须使用单一目录名称")
    output = ROOT / ".implementation-work" / ("IC-04-ROLE-SIMULATION-" + args.execution)
    inputs = ["scripts/check_role_simulation.py", "business/pom.xml", "business/ruoyi-admin/pom.xml", "business/medical-persistence/pom.xml",
        "PRD.md", "LLD-后端.md", "LLD-前端.md", "backend-design/接口补充设计.md", "contracts/catalogue.json", "contracts/extensions/evaluation-results.catalogue.json", "business/medical-persistence/src/main/resources/evaluation-results.schema.json",
        ".implementation-work/runtime/service-identity.json"]
    for directory in ("business/medical-contracts/src", "business/medical-data/src", "business/medical-identity/src",
            "business/medical-persistence/src", "business/ruoyi-admin/src", "business/ruoyi-framework/src", "business/ruoyi-system/src", "business/ruoyi-common/src"):
        inputs.extend(str(path.relative_to(ROOT)) for path in sorted((ROOT / directory).rglob("*")) if path.is_file())
    tools = ROOT / ".implementation-work/toolchains"; temporary = ROOT / ".implementation-work/role-simulation-tmp"
    temporary.mkdir(parents=True, exist_ok=True)
    environment = os.environ.copy()
    environment.update({"JAVA_HOME": str(tools / "jdk-17.0.20.1+1/Contents/Home"), "TMPDIR": str(temporary),
        "MAVEN_OPTS": "-Xms128m -Xmx512m -Dfile.encoding=UTF-8 -Djava.io.tmpdir=" + str(temporary)})
    frontend = output / "production"
    if args.frontend_directory is not None:
        if not args.browser:
            raise ValueError("前端制品需要浏览器检查")
        frontend = args.frontend_directory.resolve(strict=True)
        if not frontend.is_relative_to(ROOT / ".implementation-work") or not (frontend / "index.html").is_file():
            raise ValueError("前端制品需要使用项目检查目录的完整构建")
    if args.browser:
        for directory in ("web/src", "web/vite", "web/public"):
            inputs.extend(str(path.relative_to(ROOT)) for path in sorted((ROOT / directory).rglob("*")) if path.is_file())
        inputs.extend(("tests/browser/role-simulation-checks.js", "tests/browser/package.json", "tests/browser/package-lock.json",
            "web/package.json", "web/package-lock.json", "web/vite.config.js", "web/tsconfig.medical.json", "web/.env.medical", "web/index.html"))
        output.mkdir(parents=True, exist_ok=False)
        subprocess.run(["npm", "run", "typecheck:medical"], cwd=ROOT / "web", env=environment, check=True)
        if args.frontend_directory is None:
            subprocess.run(["npm", "run", "build:prod", "--", "--mode", "medical", "--outDir", str(frontend)], cwd=ROOT / "web", env=environment, check=True)
        inputs.extend(str(path.relative_to(ROOT)) for path in sorted(frontend.rglob("*")) if path.is_file())
    command = [sys.executable, str(CAPTURE), "--output-dir", str(output / "command" if args.browser else output),
        "--check-id", "IC-04-ROLE-SIMULATION", "--kind", "INTEGRATION", "--baseline-id", "BIZ02-ROLE-SIMULATION-01",
        "--cwd", str(ROOT), "--timeout", "600"]
    for filename in sorted(set(inputs)):
        command.extend(["--input", filename])
    command.extend(["--artifact", "business/ruoyi-admin/target/surefire-reports/TEST-com.ruoyi.medical.runtime.EnterpriseRoleSimulationHttpTest.xml"])
    if not args.initial:
        command.extend(["--artifact", str(output / "role-result.json")])
        if args.browser:
            command.extend(["--artifact", str(output / "role-browser-result.json"), "--artifact", str(output / "role-browser-output.log")])
    command.extend(["--", str(tools / "apache-maven-3.9.16/bin/mvn"), "-f", "business/pom.xml", "--batch-mode", "--no-transfer-progress",
        "-Dmaven.repo.local=" + str(ROOT / ".implementation-work/maven-repository"), "-pl", "ruoyi-admin", "-am",
        "-Dmedical.roleSimulationEvidence=" + str(output / "role-result.json"), "-Dmedical.roleSimulationBrowser=" + str(args.browser).lower(),
        "-Dmedical.roleSimulationFrontend=" + str(frontend), "-Dtest=EnterpriseRoleSimulationHttpTest", "-Dsurefire.failIfNoSpecifiedTests=false", "test"])
    raise SystemExit(subprocess.run(command, cwd=ROOT, env=environment).returncode)


if __name__ == "__main__":
    main()
