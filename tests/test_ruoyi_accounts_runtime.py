import json
import os
import subprocess
import unittest
from pathlib import Path

import sqlglot
import sqlparse
from sqlglot import exp

from test_local_identity_runtime import ActualBrowserSession, LocalIdentityRuntimeChecks, ORIGIN, REDIS, RUNTIME, mysql, recover_owner, settings, verify_application_deployment


class RuoYiAccountRuntimeChecks(LocalIdentityRuntimeChecks):
    def test_original_password_service_controls_lock_and_clear(self):
        root = Path(__file__).resolve().parents[1]
        deployment = verify_application_deployment()
        repository = root / ".implementation-work/maven-repository"
        libraries = [repository / path for path in (
            "org/junit/jupiter/junit-jupiter-api/5.12.2/junit-jupiter-api-5.12.2.jar",
            "org/junit/platform/junit-platform-commons/1.12.2/junit-platform-commons-1.12.2.jar",
            "org/opentest4j/opentest4j/1.3.0/opentest4j-1.3.0.jar",
            "org/apiguardian/apiguardian-api/1.1.2/apiguardian-api-1.1.2.jar")]
        output = RUNTIME / "ruoyi-password-authority-receipt.json"
        command = [str(root / ".implementation-work/toolchains/jdk-17.0.20.1+1/Contents/Home/bin/java"),
            "-Djava.io.tmpdir=" + str(root / ".implementation-work/java-temp"),
            "-Dfile.encoding=UTF-8", "-Dlogback.configurationFile=logback-medical.xml",
            "-Dmedical.project-root=" + str(root), "-Dloader.main=com.ruoyi.medical.identity.RuoYiPasswordRetryIT",
            "-Dloader.path=" + ",".join(str(path.resolve(strict=True)) for path in
                [root / "business/medical-identity/target/test-classes", *libraries]),
            "-cp", str(root / deployment["artifact"]), "org.springframework.boot.loader.launch.PropertiesLauncher", str(output)]
        result = subprocess.run(command, cwd=root, check=False, timeout=60)
        if result.returncode != 0:
            # 使用已有本机管理员恢复流程恢复实际账户；失败结果仍然保留。
            recover_owner()
            type(self).owner = json.loads((RUNTIME / "owner-credentials.json").read_bytes())
        self.assertEqual(result.returncode, 0, "原密码服务解除锁定后正确凭据必须能够登录")
        receipt = json.loads(output.read_bytes())
        self.assertEqual(receipt["user_id"], self.user_id)
        self.assertEqual(receipt["native_retry_limit"], 5)
        self.assertEqual(receipt["limit_http_status"], 401)
        self.assertEqual(receipt["correct_password_http_status"], 302)
        self.assertEqual(receipt["account_http_status"], 200)
        for key in ("audit_snapshot_locked_before_clear", "native_retry_cache_cleared",
                    "audit_snapshot_reset_after_login", "http_application_closed"):
            self.assertIs(receipt[key], True)
        self.assertEqual(verify_application_deployment(), deployment)

    def test_current_identity_uses_original_framework_security_and_uuid_session_index(self):
        root = Path(__file__).resolve().parents[1]
        deployment = verify_application_deployment()
        browser = self.login()
        status, _, _ = browser.request("GET", "/api/framework-integration/data-scope")
        self.assertEqual(status, 404, "工程检查Controller只能存在于检查应用中")
        java = root / ".implementation-work/toolchains/jdk-17.0.20.1+1/Contents/Home/bin/java"
        repository = root / ".implementation-work/maven-repository"
        libraries = [repository / path for path in (
            "org/junit/jupiter/junit-jupiter-api/5.12.2/junit-jupiter-api-5.12.2.jar",
            "org/junit/platform/junit-platform-commons/1.12.2/junit-platform-commons-1.12.2.jar",
            "org/opentest4j/opentest4j/1.3.0/opentest4j-1.3.0.jar",
            "org/apiguardian/apiguardian-api/1.1.2/apiguardian-api-1.1.2.jar")]
        output = RUNTIME / "ruoyi-native-identity-receipt.json"
        command = [str(java), "-Djava.io.tmpdir=" + str(root / ".implementation-work/java-temp"),
            "-Dfile.encoding=UTF-8", "-Dlogback.configurationFile=logback-medical.xml",
            "-Dmedical.project-root=" + str(root), "-Dloader.main=com.ruoyi.medical.identity.RuoYiNativeIdentityIT",
            "-Dloader.path=" + ",".join(str(path.resolve(strict=True)) for path in
                [root / "business/medical-identity/target/test-classes", *libraries]),
            "-cp", str(root / deployment["artifact"]), "org.springframework.boot.loader.launch.PropertiesLauncher",
            "identity", str(output)]
        result = subprocess.run(command, cwd=root, check=False, timeout=60)
        self.assertEqual(result.returncode, 0, "原身份及数据范围集成检查必须成功")
        receipt = json.loads(output.read_bytes())
        self.assertEqual(receipt["user_id"], self.user_id)
        self.assertEqual(receipt["ruoyi_user_id"], mysql("SELECT ruoyi_user_id FROM medical_local.platform_local_identity_metadata",
            settings()["root_password"]).stdout.strip())
        self.assertEqual(receipt["username"], self.owner["username"])
        self.assertIs(receipt["original_data_scope_applied"], True)
        self.assertIs(receipt["client_data_scope_cleared"], True)
        self.assertIs(receipt["internal_user_scope_denied"], True)
        self.assertEqual(receipt["native_user_list_count"], 0)
        self.assertEqual(receipt["native_department_list_count"], 0)
        self.assertEqual(receipt["native_role_list_count"], 0)
        self.assertIs(receipt["data_scope_http_application_closed"], True)
        self.assertGreater(receipt["data_scope_http_port"], 0)
        self.assertEqual(verify_application_deployment(), deployment)

    def test_framework_authentication_tables_use_complete_original_columns(self):
        root = Path(__file__).resolve().parents[1]
        statements = sqlparse.parse((root / "business/sql/ry_20260417.sql").read_text(encoding="utf-8"))
        definitions = {}
        for statement in statements:
            if statement.get_type() == "CREATE" and any(isinstance(token, sqlparse.sql.Identifier)
                    and token.get_real_name() in {"sys_menu", "sys_role_menu"} for token in statement.tokens):
                definition = sqlglot.parse_one(str(statement), read="mysql")
                self.assertIsInstance(definition, exp.Create)
                self.assertIsInstance(definition.this, exp.Schema)
                self.assertNotIn(definition.this.this.name, definitions)
                definitions[definition.this.this.name] = definition.this
        self.assertEqual(set(definitions), {"sys_menu", "sys_role_menu"})
        proof = {}
        for name in ("sys_menu", "sys_role_menu"):
            columns = [column for column in definitions[name].expressions if isinstance(column, exp.ColumnDef)]
            actual = [json.loads(line) for line in mysql("SELECT JSON_OBJECT('name',COLUMN_NAME,'type',COLUMN_TYPE) "
                "FROM information_schema.columns WHERE TABLE_SCHEMA='medical_local' AND TABLE_NAME='" + name +
                "' ORDER BY ORDINAL_POSITION", settings()["root_password"]).stdout.splitlines()]
            self.assertEqual([item["name"] for item in actual], [column.name for column in columns])
            for item, column in zip(actual, columns, strict=True):
                original_type = column.kind.copy()
                if original_type.this in (exp.DataType.Type.BIGINT, exp.DataType.Type.INT):
                    original_type.set("expressions", [])
                self.assertEqual(sqlglot.parse_one(item["type"], into=exp.DataType, dialect="mysql").sql(dialect="mysql"),
                    original_type.sql(dialect="mysql"))
            self.assertEqual(int(mysql("SELECT COUNT(*) FROM medical_local." + name, settings()["root_password"]).stdout), 0)
            proof[name] = actual
        (RUNTIME / "ruoyi-authentication-schema-receipt.json").write_text(json.dumps({
            "scope": "ACTUAL_ORIGINAL_FRAMEWORK_AUTHENTICATION_TABLES", "tables": proof,
            "deployment": verify_application_deployment()}), encoding="utf-8")

    def test_password_failure_and_success_use_framework_retry_cache(self):
        self.login()
        changed = dict(self.owner)
        changed["password"] = ("a" if self.owner["password"][0] != "a" else "b") + self.owner["password"][1:]
        browser = ActualBrowserSession()
        status, headers, content = browser.request("POST", "/auth/login", changed, ORIGIN, browser.csrf())
        self.assertEqual(status, 401)
        self.assertEqual(json.loads(content)["code"], "AUTHENTICATION_FAILED")
        native_environment = os.environ.copy()
        native_environment["REDISCLI_AUTH"] = settings()["redis_password"]
        command = [str(REDIS / "redis-cli"), "-h", "127.0.0.1", "-p", "16379", "--json"]
        key = "pwd_err_cnt:" + self.owner["username"]
        stored = json.loads(subprocess.run([*command, "GET", key], env=native_environment,
            capture_output=True, text=True, check=True, timeout=15).stdout)
        self.assertIsNotNone(stored)
        self.assertEqual(json.loads(stored), 1)
        snapshot = int(mysql("SELECT failed_attempts FROM medical_local.platform_local_account",
                             settings()["root_password"]).stdout)
        self.assertEqual(snapshot, json.loads(stored))
        remaining = json.loads(subprocess.run([*command, "TTL", key], env=native_environment,
            capture_output=True, text=True, check=True, timeout=15).stdout)
        self.assertGreater(remaining, 0)
        self.assertLessEqual(remaining, 600)
        authenticated = self.login()
        status, headers, content = authenticated.request("GET", "/auth/account")
        self.assertEqual(status, 200)
        self.assertEqual(json.loads(content)["user_id"], self.user_id)
        cleared = json.loads(subprocess.run([*command, "GET", key], env=native_environment,
            capture_output=True, text=True, check=True, timeout=15).stdout)
        self.assertIsNone(cleared)
        (RUNTIME / "ruoyi-password-receipt.json").write_text(json.dumps({
            "scope": "ACTUAL_OWNER_FRAMEWORK_PASSWORD", "user_id": self.user_id,
            "failure_http_status": 401, "native_retry_count": json.loads(stored),
            "persisted_retry_snapshot": snapshot,
            "native_remaining_seconds": remaining, "cache_cleared_after_login": True,
            "account_http_status": status, "deployment": verify_application_deployment()}), encoding="utf-8")

    def test_framework_account_is_the_only_profile_and_password_source(self):
        deployment = verify_application_deployment()
        tables = mysql("SELECT table_name FROM information_schema.tables WHERE table_schema='medical_local' "
                       "AND table_name IN ('sys_user','platform_local_identity_metadata') AND table_type='BASE TABLE'",
                       settings()["root_password"]).stdout.splitlines()
        self.assertEqual(set(tables), {"sys_user", "platform_local_identity_metadata"})
        actual = json.loads(mysql("""
            SELECT JSON_OBJECT('accounts',(SELECT COUNT(*) FROM medical_local.sys_user),
              'bindings',(SELECT COUNT(*) FROM medical_local.platform_local_identity_metadata),
              'complete',(SELECT COUNT(*) FROM medical_local.platform_local_identity_metadata m
                JOIN medical_local.sys_user u ON u.user_id=m.ruoyi_user_id
                JOIN medical_local.platform_local_account a ON a.user_id=m.user_id
                WHERE a.username=u.user_name AND a.display_name=u.nick_name AND a.password_hash=u.password
                  AND a.status='ACTIVE' AND u.status='0' AND u.del_flag='0'),
              'profile_columns',(SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='medical_local'
                AND table_name='platform_local_identity_metadata' AND column_name IN ('username','display_name','password_hash','status')),
              'directory_type',(SELECT table_type FROM information_schema.tables WHERE table_schema='medical_local'
                AND table_name='platform_local_account'))
            """, settings()["root_password"]).stdout)
        self.assertEqual(actual, {"accounts": 1, "bindings": 1, "complete": 1, "profile_columns": 0, "directory_type": "VIEW"})
        browser = self.login()
        status, headers, content = browser.request("GET", "/auth/account")
        self.assertEqual(status, 200)
        self.assertEqual(json.loads(content)["user_id"], self.user_id)
        self.assertEqual(json.loads(content)["username"], self.owner["username"])
        (RUNTIME / "ruoyi-account-receipt.json").write_text(json.dumps({"scope": "ACTUAL_OWNER_FRAMEWORK_ACCOUNT",
            "user_id": self.user_id, "storage": actual, "deployment": deployment,
            "login_http_status": 302, "account_http_status": status}), encoding="utf-8")


if __name__ == "__main__":
    unittest.main(verbosity=2)
