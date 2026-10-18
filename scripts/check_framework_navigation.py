import argparse
import fcntl
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys

from check_local_browser import browser_identity
from check_medical_account_browser import verify_mutations


ROOT = Path(__file__).resolve().parents[1]
CAPTURE = Path("/Users/liuhaoxian/.agents/skills/haonan-s000-pmp/scripts/capture_check.py")


def reports(complete):
    result = [("ruoyi-admin", "runtime", "FrameworkNavigationHttpTest")]
    if complete:
        result.append(("ruoyi-admin", "runtime", "FrontendEntryHttpTest"))
        result.append(("ruoyi-admin", "runtime", "RuoYiManagementServicesTest"))
        result.append(("ruoyi-admin", "runtime", "RuoYiManagementMapperTest"))
        result.append(("ruoyi-admin", "runtime", "FrameworkMethodSecurityHttpTest"))
        result.append(("ruoyi-admin", "runtime", "FrameworkAccountMutationHttpTest"))
        result.append(("ruoyi-admin", "runtime", "TemplateApplicabilityHttpTest"))
        result.append(("ruoyi-admin", "runtime", "TaskListHttpTest"))
        result.append(("ruoyi-admin", "runtime", "ProjectSimulationHttpTest"))
        result.append(("ruoyi-admin", "runtime", "EnterpriseRoleSimulationHttpTest"))
        result.append(("ruoyi-admin", "runtime", "MemberGovernanceSimulationHttpTest"))
        for name in ("TemplateApplicabilityTest", "TemplateDefinitionSchemaTest", "TemplateApprovalSchemaTest",
                "TemplateOperationQueriesTest", "TemplateOperationSchemaTest", "TaskInputSchemaTest", "TaskReadTest", "TaskListSchemaTest",
                "ResourceReadAuthorityTest", "ArtifactRegistryReadTest", "SourceReadAuthorityTest", "PermissionStructureTest",
                "ProjectSchemaTest", "ProjectPaginationTest", "EnterpriseRoleSchemaTest"):
            result.append(("medical-persistence", "persistence", name))
        for module, namespace in (("medical-contracts", "contracts"), ("medical-identity", "identity")):
            directory = ROOT / "business" / module / "src/test/java/com/ruoyi/medical" / namespace
            result.extend((module, namespace, path.stem) for path in sorted(directory.glob("*Test.java")))
    return result


def main():
    parser = argparse.ArgumentParser(description="核查原RuoYi菜单、Vue会话及当前模板和身份检查")
    parser.add_argument("--execution")
    parser.add_argument("--run", type=Path)
    parser.add_argument("--red", action="store_true")
    parser.add_argument("--browser", action="store_true")
    parser.add_argument("--password-browser", action="store_true")
    parser.add_argument("--complete", action="store_true")
    parser.add_argument("--method-security", action="store_true")
    parser.add_argument("--native-services", action="store_true")
    parser.add_argument("--account-actions", action="store_true")
    parser.add_argument("--login-entry", action="store_true")
    args = parser.parse_args()
    if args.password_browser and not args.browser:
        raise ValueError("原密码组件检查需要实际浏览器及当前构建")
    tools = ROOT / ".implementation-work/toolchains"
    temporary = ROOT / ".implementation-work/framework-navigation-tmp"
    temporary.mkdir(parents=True, exist_ok=True)
    environment = os.environ.copy()
    environment.update({"JAVA_HOME": str(tools / "jdk-17.0.20.1+1/Contents/Home"), "TMPDIR": str(temporary),
        "MAVEN_OPTS": "-Xms128m -Xmx512m -Dfile.encoding=UTF-8 -Djava.io.tmpdir=" + str(temporary)})
    selected = reports(args.complete)
    if args.login_entry:
        if args.complete or args.method_security or args.native_services or args.account_actions or args.password_browser:
            raise ValueError("原登录发布检查使用独立Servlet与浏览器范围")
        selected = [("ruoyi-admin", "runtime", "FrontendEntryHttpTest"),
            ("ruoyi-admin", "runtime", "FrameworkMethodSecurityHttpTest"),
            ("ruoyi-admin", "runtime", "RuoYiManagementServicesTest")]
    if args.method_security:
        if args.browser or args.complete:
            raise ValueError("独立方法权限检查不能同时指定完整导航或浏览器检查")
        selected = [("ruoyi-admin", "runtime", "FrameworkMethodSecurityHttpTest")]
    if args.native_services:
        if args.browser or args.complete or args.method_security:
            raise ValueError("原服务检查必须使用独立执行范围")
        selected = [("ruoyi-admin", "runtime", "RuoYiManagementServicesTest")]
    if args.account_actions:
        if args.browser or args.complete or args.method_security or args.native_services:
            raise ValueError("原账户动作检查必须使用独立执行范围")
        selected = [("ruoyi-admin", "runtime", "FrameworkAccountMutationHttpTest")]
    if args.run is not None:
        output = args.run.resolve(strict=True)
        if args.browser:
            subprocess.run(["npm", "run", "typecheck:medical"], cwd=ROOT / "web", env=environment, check=True)
            subprocess.run(["npm", "run", "build:prod", "--", "--mode", "medical", "--outDir", str(output / "production")],
                           cwd=ROOT / "web", env=environment, check=True)
        command = [str(tools / "apache-maven-3.9.16/bin/mvn"), "-f", "business/pom.xml", "--batch-mode", "--no-transfer-progress",
            "-Dmaven.repo.local=" + str(ROOT / ".implementation-work/maven-repository"), "-pl", "ruoyi-admin", "-am",
            "-Dmedical.navigationEvidence=" + str(output / "navigation-result.json"),
            "-Dmedical.frontendEvidence=" + str(output / "frontend-result.json"),
            "-Dmedical.frontendBrowser=" + str((args.login_entry or args.complete) and args.browser).lower(),
            "-Dmedical.frontendDirectory=" + str(output / "production" if args.browser else ROOT / "web/dist"),
            "-Dmedical.httpEvidence=" + str(output / "template-http-result.json"),
            "-Dmedical.methodSecurityEvidence=" + str(output / "method-security-result.json"),
            "-Dmedical.accountActionsEvidence=" + str(output / "account-actions-result.json"),
            "-Dmedical.managementServicesEvidence=" + str(output / "management-services-result.json"),
            "-Dmedical.managementDataEvidence=" + str(output / "management-data-result.json"),
            "-Dmedical.taskListEvidence=" + str(output / "task-list-result.json"),
            "-Dmedical.taskListBrowser=" + str(args.complete and args.browser).lower(),
            "-Dmedical.taskListFrontend=" + str(output / "production" if args.browser else ROOT / "web/dist"),
            "-Dmedical.projectSimulationEvidence=" + str(output / "project-result.json"),
            "-Dmedical.projectSimulationBrowser=" + str(args.complete and args.browser).lower(),
            "-Dmedical.projectSimulationFrontend=" + str(output / "production" if args.browser else ROOT / "web/dist"),
            "-Dmedical.roleSimulationEvidence=" + str(output / "role-result.json"),
            "-Dmedical.roleSimulationBrowser=" + str(args.complete and args.browser).lower(),
            "-Dmedical.roleSimulationFrontend=" + str(output / "production" if args.browser else ROOT / "web/dist"),
            "-Dmedical.memberSimulationEvidence=" + str(output / "member-result.json"),
            "-Dmedical.memberSimulationBrowser=" + str(args.complete and args.browser).lower(),
            "-Dmedical.memberSimulationFrontend=" + str(output / "production" if args.browser else ROOT / "web/dist"),
            "-Dmedical.navigationOutput=" + str(output), "-Dmedical.navigationBrowser=" + str(args.browser).lower(),
            "-Dmedical.passwordBrowser=" + str(args.password_browser).lower(),
            "-Dtest=" + ",".join(name for module, namespace, name in selected), "-Dsurefire.runOrder=alphabetical",
            "-Dsurefire.failIfNoSpecifiedTests=false", "test"]
        with (ROOT / ".implementation-work/runtime/owner-recovery.lock").open("a") as lock:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
            result = subprocess.run(command, cwd=ROOT, env=environment).returncode
            if result == 0 and args.password_browser:
                verify_mutations(output)
        raise SystemExit(result)
    if not args.execution or Path(args.execution).name != args.execution or args.execution in (".", ".."):
        raise ValueError("执行身份必须使用单一目录名称")
    inputs = ["scripts/check_framework_navigation.py", "business/pom.xml", "business/ruoyi-admin/pom.xml",
        "business/medical-persistence/pom.xml", "business/medical-contracts/pom.xml", "business/medical-identity/pom.xml",
        "business/ruoyi-common/pom.xml", "business/ruoyi-system/pom.xml", "business/ruoyi-framework/pom.xml",
        "business/ruoyi-quartz/pom.xml", "business/ruoyi-generator/pom.xml", "contracts/catalogue.json", "contracts/extensions/evaluation-results.catalogue.json", "business/medical-persistence/src/main/resources/evaluation-results.schema.json",
        "backend-design/接口补充设计.md", "LLD-前端.md", ".implementation-work/runtime/service-identity.json",
        "scripts/local_runtime.py", ".implementation-work/runtime/ruoyi-token-configuration-receipt.json"]
    for directory in ("business/medical-contracts/src", "business/medical-data/src", "business/medical-persistence/src",
            "business/medical-identity/src", "business/ruoyi-admin/src", "business/ruoyi-common/src",
            "business/ruoyi-system/src", "business/ruoyi-framework/src", "business/ruoyi-quartz/src", "business/ruoyi-generator/src"):
        inputs.extend(str(path.relative_to(ROOT)) for path in sorted((ROOT / directory).rglob("*")) if path.is_file())
    output = ROOT / ".implementation-work" / ("IC-06-FRAMEWORK-NAVIGATION-" + args.execution)
    if args.complete:
        inputs.append("scripts/check_member_simulation.py")
        inputs.append("tests/check_project_sql_columns.py")
        inputs.append("tests/check_permission_sql_columns.py")
        inputs.append("tests/check_role_sql_columns.py")
        if args.browser:
            inputs.extend(("tests/browser/project-simulation-checks.js", "tests/browser/task-simulation-checks.js", "tests/browser/role-simulation-checks.js", "tests/browser/member-acceptance-checks.js"))
    if args.browser:
        for directory in ("web/src", "web/vite", "web/public", "tests/browser/node_modules"):
            inputs.extend(str(path.relative_to(ROOT)) for path in sorted((ROOT / directory).rglob("*"))
                          if path.is_file() and ("node_modules" not in path.parts or path.suffix in (".js", ".json", ".node")))
        inputs.extend(("web/index.html", "web/package.json", "web/package-lock.json", "web/vite.config.js", "web/tsconfig.medical.json", "web/.env.medical",
            "scripts/check_local_browser.py", "scripts/local_runtime.py", "backend-design/interfaces.schema.yaml", "contracts/typescript/src/errors.ts",
            "backend-design/resource-details.schema.yaml", "agent-design/artifacts.schema.json", "backend-design/interfaces.openapi.yaml",
            "tests/browser/framework-navigation-checks.js", "tests/browser/package.json", "tests/browser/package-lock.json"))
        if args.login_entry or args.complete:
            inputs.append("tests/browser/frontend-entry-checks.js")
        for package in ("vue", "element-plus", "axios", "ajv", "ajv-formats", "yaml", "lossless-json", "jsonc-parser", "json-custom-numbers",
                "json-canonicalize", "@js-temporal/polyfill", "jsbi", "vue-cropper", "vite", "vue-tsc", "typescript"):
            directory = ROOT / "web/node_modules" / package
            inputs.extend(str(path.relative_to(ROOT)) for path in sorted(directory.rglob("*")) if path.is_file())
        inputs.extend(browser_identity()[1])
        if args.password_browser:
            inputs.extend(("tests/browser/medical-account-checks.js", "scripts/check_medical_account_browser.py",
                "scripts/check_medical_session_browser.py"))
    command = [sys.executable, str(CAPTURE), "--output-dir", str(output / "command"), "--check-id", "IC-06-FRAMEWORK-NAVIGATION",
        "--kind", "INTEGRATION", "--baseline-id", "BACK-RUOYI-MANAGEMENT-SERVICES-01" if args.native_services else
            ("BACK-RUOYI-ACCOUNT-ACTIONS-01" if args.account_actions else
                ("BACK-RUOYI-METHOD-SECURITY-01" if args.method_security else "BACK-RUOYI-NAVIGATION-01")),
        "--cwd", str(ROOT), "--timeout", "900"]
    for path in sorted(set(inputs)):
        command.extend(["--input", path])
    for module, namespace, name in selected:
        command.extend(["--artifact", "business/" + module + "/target/surefire-reports/TEST-com.ruoyi.medical." + namespace + "." + name + ".xml"])
    if not args.red:
        if args.login_entry:
            command.extend(["--artifact", str(output / "frontend-result.json")])
            command.extend(["--artifact", str(output / "method-security-result.json"),
                "--artifact", str(output / "management-services-result.json")])
            if args.browser:
                command.extend(["--artifact", str(output / "frontend-browser-output.log")])
        elif args.native_services:
            command.extend(["--artifact", str(output / "management-services-result.json")])
        elif not args.method_security and not args.account_actions:
            command.extend(["--artifact", str(output / "navigation-result.json")])
        if args.method_security or args.complete:
            command.extend(["--artifact", str(output / "method-security-result.json")])
        if args.account_actions or args.complete:
            command.extend(["--artifact", str(output / "account-actions-result.json")])
        if args.complete:
            command.extend(["--artifact", str(output / "frontend-result.json")])
            if args.browser:
                command.extend(["--artifact", str(output / "frontend-browser-output.log")])
            command.extend(["--artifact", str(output / "template-http-result.json")])
            command.extend(["--artifact", str(output / "task-list-result.json")])
            command.extend(["--artifact", str(output / "project-result.json")])
            command.extend(["--artifact", str(output / "role-result.json")])
            command.extend(["--artifact", str(output / "member-result.json")])
            if args.browser:
                command.extend(["--artifact", str(output / "project-browser-result.json"), "--artifact", str(output / "project-browser-output.log"),
                    "--artifact", str(output / "task-browser-result.json"), "--artifact", str(output / "task-browser-output.log"),
                    "--artifact", str(output / "role-browser-result.json"), "--artifact", str(output / "role-browser-output.log")])
                command.extend(["--artifact", str(output / "member-browser-result.json"), "--artifact", str(output / "member-browser-output.log")])
            command.extend(["--artifact", str(output / "management-services-result.json"),
                "--artifact", str(output / "management-data-result.json")])
            command.extend(["--artifact", ".implementation-work/project-query-columns.json",
                "--artifact", ".implementation-work/project-query-columns-output.log"])
            command.extend(["--artifact", ".implementation-work/permission-query-columns.json"])
            command.extend(["--artifact", ".implementation-work/role-query-columns.json"])
        if args.browser and not args.login_entry:
            command.extend(["--artifact", str(output / "browser-output.log")])
            if args.password_browser:
                command.extend(["--artifact", str(output / "password-browser-output.log")])
    command.extend(["--", sys.executable, "scripts/check_framework_navigation.py", "--run", str(output)])
    if args.browser:
        command.append("--browser")
    if args.password_browser:
        command.append("--password-browser")
    if args.complete:
        command.append("--complete")
    if args.method_security:
        command.append("--method-security")
    if args.native_services:
        command.append("--native-services")
    if args.account_actions:
        command.append("--account-actions")
    if args.login_entry:
        command.append("--login-entry")
    result = subprocess.run(command, cwd=ROOT, env=environment)
    if result.returncode != 0:
        raise SystemExit(result.returncode)
    receipt_path = output / "command/receipt.json"
    receipt = json.loads(receipt_path.read_bytes())
    # 每次执行保存原始JUnit副本，使后续构建能够独立核验本次结果。
    for item in receipt["artifacts"]:
        source = Path(item["path"])
        if source.name.startswith("TEST-") and source.suffix == ".xml":
            target = output / "junit" / source.parents[2].name / source.name
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(source, target)
            if hashlib.sha256(target.read_bytes()).hexdigest() != item["sha256"]:
                raise ValueError("FRAMEWORK_JUNIT_ARCHIVE_MISMATCH")
            item["path"] = str(target)
    artifacts = [receipt_path]
    if args.browser:
        if args.login_entry:
            artifacts.append(output / "frontend-browser-result.json")
        else:
            artifacts.extend(output / ("navigation-browser-" + mode + ".json") for mode in ("development", "production"))
            if args.complete:
                artifacts.append(output / "frontend-browser-result.json")
        if args.password_browser:
            artifacts.extend(output / ("medical-account-result-" + mode + ".json") for mode in ("development", "production"))
        artifacts.extend(path for path in sorted((output / "production").rglob("*")) if path.is_file())
    for path in artifacts:
        receipt["artifacts"].append({"path": str(path), "sha256": hashlib.sha256(path.read_bytes()).hexdigest()})
    target = output / "receipt.json"
    target.write_text(json.dumps(receipt, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"result": "PASS", "receipt": str(target.relative_to(ROOT)),
        "sha256": hashlib.sha256(target.read_bytes()).hexdigest(), "inputs": len(receipt["inputs"]), "artifacts": len(receipt["artifacts"])}))


if __name__ == "__main__":
    main()
