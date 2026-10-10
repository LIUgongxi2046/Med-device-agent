import argparse
import json
import os
from pathlib import Path
import subprocess
import sys
from uuid import uuid4

import sqlglot
import sqlparse


ROOT = Path(__file__).resolve().parents[1]
CAPTURE = Path("/Users/liuhaoxian/.agents/skills/haonan-s000-pmp/scripts/capture_check.py")


def main():
    parser = argparse.ArgumentParser(description="保存框架用户服务与真实本机身份检查")
    parser.add_argument("--execution", required=True)
    parser.add_argument("--check", choices=("runtime", "migrate", "password-authority"), default="runtime")
    args = parser.parse_args()
    if Path(args.execution).name != args.execution or args.execution in (".", ".."):
        raise ValueError("执行身份必须为单一目录名称")
    check_id = {"runtime": "IC-04-RUOYI", "migrate": "IC-03-RUOYI-MIGRATION",
                "password-authority": "IC-04-RUOYI-PASSWORD-AUTHORITY"}[args.check]
    command = [sys.executable, str(CAPTURE), "--output-dir", ".implementation-work/" + check_id + "-" + args.execution,
               "--check-id", check_id, "--kind", "INTEGRATION", "--baseline-id", "BACK-RUOYI-AUTHENTICATION-01",
               "--cwd", str(ROOT), "--timeout", "600"]
    inputs = ["scripts/check_ruoyi_accounts.py", "scripts/local_runtime.py", "scripts/RecoverLocalOwner.java",
              "tests/test_ruoyi_accounts_runtime.py", "tests/test_local_identity_runtime.py", "HLD.md",
              "backend-design/接口补充设计.md", "business/medical-identity/pom.xml",
              "business/sql/ry_20260417.sql", "data-design/requirements.txt", str(Path(sqlglot.__file__).resolve(strict=True)),
              str(Path(sqlparse.__file__).resolve(strict=True)),
              ".implementation-work/runtime/service-identity.json"]
    if args.check != "migrate":
        deployment = json.loads((ROOT / ".implementation-work/runtime/application-deployment.json").read_bytes())
        artifact = Path(deployment["artifact"])
        if artifact.is_absolute() or ".." in artifact.parts or not artifact.is_relative_to(".implementation-work/runtime/applications"):
            raise ValueError("运行制品必须位于本项目版本目录")
        inputs.extend((".implementation-work/runtime/application-deployment.json", str(artifact)))
        if args.check == "runtime":
            for artifact in ("ruoyi-account-receipt.json", "ruoyi-password-receipt.json", "ruoyi-authentication-schema-receipt.json",
                             "ruoyi-native-identity-receipt.json", "ruoyi-password-authority-receipt.json"):
                command.extend(["--artifact", ".implementation-work/runtime/" + artifact])
        inputs.extend(sorted(str(path.relative_to(ROOT)) for path in
            (ROOT / "business/medical-identity/target/test-classes").rglob("RuoYiNativeIdentityIT*.class")))
        inputs.extend(sorted(str(path.relative_to(ROOT)) for path in
            (ROOT / "business/medical-identity/target/test-classes").rglob("RuoYiPasswordRetryIT*.class")))
        inputs.append(".implementation-work/toolchains/jdk-17.0.20.1+1/Contents/Home/bin/java")
        for dependency in ("org/junit/jupiter/junit-jupiter-api/5.12.2/junit-jupiter-api-5.12.2.jar",
                "org/junit/platform/junit-platform-commons/1.12.2/junit-platform-commons-1.12.2.jar",
                "org/opentest4j/opentest4j/1.3.0/opentest4j-1.3.0.jar",
                "org/apiguardian/apiguardian-api/1.1.2/apiguardian-api-1.1.2.jar"):
            inputs.append(".implementation-work/maven-repository/" + dependency)
        action = [sys.executable, "tests/test_ruoyi_accounts_runtime.py",
            "RuoYiAccountRuntimeChecks.test_framework_account_is_the_only_profile_and_password_source",
            "RuoYiAccountRuntimeChecks.test_password_failure_and_success_use_framework_retry_cache",
            "RuoYiAccountRuntimeChecks.test_framework_authentication_tables_use_complete_original_columns",
            "RuoYiAccountRuntimeChecks.test_current_identity_uses_original_framework_security_and_uuid_session_index",
            "RuoYiAccountRuntimeChecks.test_original_password_service_controls_lock_and_clear"]
        if args.check == "password-authority":
            action = [sys.executable, "tests/test_ruoyi_accounts_runtime.py",
                      "RuoYiAccountRuntimeChecks.test_original_password_service_controls_lock_and_clear"]
    else:
        migration_id = str(uuid4())
        inputs.extend(("scripts/run_migration.py", "business/medical-data/target/medical-data-3.9.2-cli.jar",
            "business/medical-data/target/medical-data-3.9.2.jar"))
        for path in ("migration.log", "migration-backups/" + migration_id + ".sql.gz", "migration-backups/" + migration_id + ".json"):
            command.extend(["--artifact", ".implementation-work/runtime/" + path])
        action = [sys.executable, "scripts/local_runtime.py", "migrate", "--migration-id", migration_id]
    for directory in ("business/medical-identity/src", "business/medical-data/src",
                      "business/ruoyi-system/src", "business/ruoyi-common/src", "business/ruoyi-framework/src"):
        inputs.extend(sorted(str(path.relative_to(ROOT)) for path in (ROOT / directory).rglob("*") if path.is_file()))
    for path in inputs:
        command.extend(["--input", path])
    environment = os.environ.copy()
    environment["TMPDIR"] = str(ROOT / ".implementation-work/migration-check-tmp")
    subprocess.run([*command, "--", *action],
                   cwd=ROOT, env=environment, check=True)


if __name__ == "__main__":
    main()
