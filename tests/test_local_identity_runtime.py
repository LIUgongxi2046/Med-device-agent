import base64
import fcntl
import hashlib
import hmac
import http.client
from http.cookies import SimpleCookie
import json
import os
from pathlib import Path
import pwd
import rfc8785
import secrets
import sys
import subprocess
import unittest
import uuid
from urllib.parse import urlencode


ROOT = Path(__file__).resolve().parents[1]
RUNTIME = ROOT / ".implementation-work/runtime"
ORIGIN = "http://127.0.0.1:18080"
sys.path.insert(0, str(ROOT / "scripts"))
from local_runtime import REDIS, environment, mysql, recover_owner, settings, synchronize_runtime_directory, verify_application_deployment


class ActualBrowserSession:
    def __init__(self):
        self.cookies = SimpleCookie()

    def request(self, method, path, body=None, origin=None, csrf=None, schema=None, json_body=None, context=None, key=None):
        connection = http.client.HTTPConnection("127.0.0.1", 18080, timeout=15)
        headers = {}
        if schema is not None:
            headers["X-Schema-Version"] = schema
        if context is not None:
            headers["X-Context-Revision"] = context
        if key is not None:
            headers["Idempotency-Key"] = key
        if self.cookies:
            headers["Cookie"] = "; ".join(cookie.OutputString(attrs=[]) for cookie in self.cookies.values())
        if origin is not None:
            headers["Origin"] = origin
        if body is not None:
            if json_body is not None:
                raise ValueError("请求只能使用一种正文格式")
            headers["Content-Type"] = "application/x-www-form-urlencoded"
            body = urlencode(body)
        if json_body is not None:
            headers["Content-Type"] = "application/json"
            body = json.dumps(json_body)
        if csrf is not None:
            headers[csrf["header_name"]] = csrf["token"]
        connection.request(method, path, body=body, headers=headers)
        response = connection.getresponse()
        content = response.read()
        metadata = dict(response.getheaders())
        for name, value in response.getheaders():
            if name.lower() == "set-cookie":
                self.cookies.load(value)
        connection.close()
        return response.status, metadata, content

    def csrf(self):
        status, metadata, content = self.request("GET", "/auth/csrf")
        if status != 200 or metadata.get("Cache-Control") != "no-store":
            raise RuntimeError("LOCAL_CSRF_READ_FAILED")
        return json.loads(content)


def provision_owner():
    path = RUNTIME / "owner-credentials.json"
    if path.exists():
        values = json.loads(path.read_bytes())
        if values["username"] != pwd.getpwuid(os.getuid()).pw_name:
            raise RuntimeError("LOCAL_OWNER_IDENTITY_CHANGED")
        return values
    browser = ActualBrowserSession()
    status, headers, content = browser.request("GET", "/auth/setup")
    if status != 200 or "设置首次管理员" not in content.decode("utf-8"):
        raise RuntimeError("LOCAL_BOOTSTRAP_NOT_AVAILABLE")
    owner = pwd.getpwuid(os.getuid())
    values = {"username": owner.pw_name, "password": secrets.token_hex(24), "display_name": owner.pw_name}
    # 凭据必须先保存，才能提交首次账户设置。
    with path.open("x", encoding="utf-8") as output:
        os.chmod(path, 0o600)
        json.dump(values, output, ensure_ascii=False)
    csrf = browser.csrf()
    status, headers, content = browser.request("POST", "/auth/setup", values, ORIGIN, csrf)
    if status != 302 or headers.get("Location") not in ("/auth/login", ORIGIN + "/auth/login"):
        raise RuntimeError("LOCAL_OWNER_CREATION_FAILED status=" + str(status) + " location=" + str(headers.get("Location")))
    receipt = {"scope": "LOCAL_OWNER_BOOTSTRAP", "source": "operating_system_account", "os_uid": owner.pw_uid,
               "username": owner.pw_name, "method": "POST", "path": "/auth/setup", "status": status}
    (RUNTIME / "owner-setup-receipt.json").write_text(json.dumps(receipt), encoding="utf-8")
    return values


class LocalIdentityRuntimeChecks(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.owner = provision_owner()
        cls.user_id = str(uuid.UUID(mysql("SELECT user_id FROM medical_local.platform_local_account",
                                         settings()["root_password"]).stdout.strip()))

    def login(self):
        browser = ActualBrowserSession()
        csrf = browser.csrf()
        status, metadata, content = browser.request("POST", "/auth/login", self.owner, ORIGIN, csrf)
        self.assertEqual(status, 302)
        self.assertIn(metadata["Location"], ("/local-auth/account.html", ORIGIN + "/local-auth/account.html"))
        return browser

    def test_actual_owner_can_login_and_read_persisted_account(self):
        browser = self.login()
        status, metadata, content = browser.request("GET", "/auth/account")
        self.assertEqual(status, 200)
        account = json.loads(content)
        self.assertEqual(account["username"], self.owner["username"])
        self.assertEqual(account["display_name"], self.owner["display_name"])
        self.assertEqual(account["platform_role"], "PLATFORM_ADMIN")
        self.assertEqual(set(account), {"user_id", "username", "display_name", "platform_role", "revision"})
        self.assertEqual(metadata["Cache-Control"], "no-store")
        cookie = browser.cookies["MEDICAL_SESSION"]
        self.assertTrue(cookie["httponly"])
        self.assertEqual(cookie["samesite"], "Lax")
        self.assertFalse(cookie["secure"])
        self.assertFalse(self.owner["password"].encode() in content)
        (RUNTIME / "owner-account-receipt.json").write_text(json.dumps(account), encoding="utf-8")

    def test_application_runs_recorded_program_version(self):
        deployment = verify_application_deployment()
        artifact = ROOT / deployment["artifact"]
        source = ROOT / deployment["source"]
        self.assertNotEqual(artifact, source)
        self.assertEqual(hashlib.sha256(source.read_bytes()).hexdigest(), deployment["sha256"])
        self.assertEqual(artifact.stat().st_mode & 0o777, 0o400)
        history = json.loads((RUNTIME / ("application-deployment-" + deployment["deployment_id"] + ".json")).read_bytes())
        self.assertEqual(history, deployment)

    def test_local_admin_reads_accounts_from_actual_directory(self):
        browser = self.login()
        status, metadata, content = browser.request("GET", "/api/local-accounts?limit=1", schema="1.0.0")
        self.assertEqual(status, 200)
        value = json.loads(content)
        self.assertEqual(set(value), {"schema_version", "request_id", "resource_revision", "data"})
        self.assertEqual(metadata["Cache-Control"], "no-store")
        page = value["data"]
        self.assertEqual(set(page), {"schema_version", "items", "next_cursor"})
        self.assertIsNone(page["next_cursor"])
        self.assertEqual(len(page["items"]), 1)
        account = page["items"][0]
        self.assertEqual(set(account), {"user_id", "username", "display_name", "status", "platform_role", "revision", "credential_revision"})
        self.assertEqual(account["user_id"], self.user_id)
        self.assertEqual(account["username"], self.owner["username"])
        self.assertEqual(account["status"], "ACTIVE")
        self.assertEqual(account["platform_role"], "PLATFORM_ADMIN")
        self.assertFalse(self.owner["password"].encode() in content)
        (RUNTIME / "local-account-management-receipt.json").write_text(json.dumps(value), encoding="utf-8")

    def test_protected_account_rejects_anonymous_request(self):
        status, metadata, content = ActualBrowserSession().request("GET", "/auth/account")
        self.assertEqual(status, 401)
        self.assertEqual(json.loads(content)["code"], "AUTHENTICATION_REQUIRED")

    def management_account(self, browser):
        status, metadata, content = browser.request("GET", "/api/local-accounts?limit=1", schema="1.0.0")
        self.assertEqual(status, 200)
        return json.loads(content)["data"]["items"][0]

    def management_change(self, browser, path, body, key=None, schema="1.0.0"):
        return browser.request("POST", path, origin=ORIGIN, csrf=browser.csrf(), schema=schema,
                               json_body=body, key=key if key is not None else str(uuid.uuid4()))

    def test_local_management_rejects_anonymous_and_invalid_queries(self):
        status, metadata, content = ActualBrowserSession().request("GET", "/api/local-accounts", schema="1.0.0")
        self.assertEqual(status, 401)
        browser = self.login()
        for query in ("?limit=0", "?limit=101", "?cursor=" + self.owner["username"], "?username=" + self.owner["username"], "?limit=1&limit=2"):
            with self.subTest(query=query):
                status, metadata, content = browser.request("GET", "/api/local-accounts" + query, schema="1.0.0")
                self.assertEqual(status, 422)
                self.assertEqual(json.loads(content)["code"], "SCHEMA_INVALID")
        status, metadata, content = browser.request("GET", "/api/local-accounts")
        self.assertEqual(status, 422)
        self.assertEqual(json.loads(content)["code"], "SCHEMA_UNSUPPORTED")

    def test_local_management_protects_last_active_administrator(self):
        browser = self.login()
        before = self.management_account(browser)
        for suffix, body in (("state-decisions", {"decision": "DISABLE", "expected_revision": before["revision"]}),
                             ("profile-revisions", {"display_name": before["display_name"], "platform_role": "USER", "expected_revision": before["revision"]})):
            with self.subTest(action=suffix):
                status, metadata, content = self.management_change(browser, "/api/local-accounts/" + self.user_id + "/" + suffix, body)
                self.assertEqual(status, 409)
                self.assertEqual(json.loads(content)["code"], "REVISION_CONFLICT")
                self.assertEqual(self.management_account(browser), before)

    def test_local_management_rejects_existing_username_and_self_reset(self):
        browser = self.login()
        before = self.management_account(browser)
        status, metadata, content = self.management_change(browser, "/api/local-accounts", {
            "username": self.owner["username"], "display_name": before["display_name"], "password": self.owner["password"], "platform_role": "PLATFORM_ADMIN"})
        self.assertEqual(status, 409)
        self.assertEqual(json.loads(content)["code"], "REVISION_CONFLICT")
        status, metadata, content = self.management_change(browser, "/api/local-accounts/" + self.user_id + "/credential-revisions", {
            "password": self.owner["password"], "expected_revision": before["revision"]})
        self.assertEqual(status, 403)
        self.assertEqual(self.management_account(browser), before)

    def test_local_management_rejects_wrong_password_and_stale_revision(self):
        browser = self.login()
        before = self.management_account(browser)
        changed = ("a" if self.owner["password"][0] != "a" else "b") + self.owner["password"][1:]
        body = {"current_password": changed, "password": self.owner["password"], "expected_revision": before["revision"]}
        status, metadata, content = self.management_change(browser, "/api/local-account/credential-revisions", body)
        self.assertEqual(status, 401)
        self.assertEqual(json.loads(content)["code"], "AUTHENTICATION_FAILED")
        body.update({"current_password": self.owner["password"], "expected_revision": str(int(before["revision"]) - 1)})
        status, metadata, content = self.management_change(browser, "/api/local-account/credential-revisions", body)
        self.assertEqual(status, 409)
        self.assertEqual(json.loads(content)["code"], "REVISION_CONFLICT")
        self.assertEqual(self.management_account(browser), before)

    def test_local_management_requires_schema_csrf_origin_and_sealed_body(self):
        browser = self.login()
        before = self.management_account(browser)
        body = {"current_password": self.owner["password"], "password": self.owner["password"], "expected_revision": before["revision"]}
        path = "/api/local-account/credential-revisions"
        status, metadata, content = browser.request("POST", path, origin=ORIGIN, schema="1.0.0", json_body=body, key=str(uuid.uuid4()))
        self.assertEqual(status, 403)
        status, metadata, content = browser.request("POST", path, csrf=browser.csrf(), schema="1.0.0", json_body=body, key=str(uuid.uuid4()))
        self.assertEqual(status, 403)
        status, metadata, content = self.management_change(browser, path, body, schema=None)
        self.assertEqual(status, 422)
        self.assertEqual(json.loads(content)["code"], "SCHEMA_UNSUPPORTED")
        status, metadata, content = self.management_change(browser, path, {**body, "user_id": self.user_id})
        self.assertEqual(status, 422)
        status, metadata, content = self.management_change(browser, path, body, key="invalid")
        self.assertEqual(status, 422)
        self.assertEqual(self.management_account(browser), before)

    def test_local_owner_changes_password_and_replays_original_receipt(self):
        browser = self.login()
        other = self.login()
        before = self.management_account(browser)
        path = "/api/local-account/credential-revisions"
        key = str(uuid.uuid4())
        credentials = {**self.owner, "password": secrets.token_hex(24)}
        body = {"current_password": self.owner["password"], "password": credentials["password"], "expected_revision": before["revision"]}
        candidate = RUNTIME / ("owner-credentials-management-" + str(uuid.uuid4()) + ".json")
        with (RUNTIME / "owner-recovery.lock").open("a", encoding="utf-8") as lock:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
            with candidate.open("x", encoding="utf-8") as output:
                os.fchmod(output.fileno(), 0o600)
                json.dump(credentials, output)
                output.flush()
                os.fsync(output.fileno())
            synchronize_runtime_directory()
            status, metadata, content = self.management_change(browser, path, body, key=key)
            self.assertEqual(status, 200)
            result = json.loads(content)
            self.assertEqual(result["data"]["account"]["credential_revision"], str(int(before["credential_revision"]) + 1))
            self.assertEqual(result["data"]["account"]["revision"], str(int(before["revision"]) + 1))
            self.assertEqual(result["data"]["account"]["user_id"], self.user_id)
            self.assertTrue(result["data"]["reauthentication_required"])
            self.assertEqual(metadata["X-Request-Id"], result["request_id"])
            self.assertEqual(metadata["Cache-Control"], "no-store")
            os.replace(candidate, RUNTIME / "owner-credentials.json")
            synchronize_runtime_directory()
            type(self).owner = credentials
        for session in (browser, other):
            status, metadata, content = session.request("GET", "/auth/account")
            self.assertEqual(status, 403)
            self.assertEqual(json.loads(content)["code"], "ACCESS_DENIED")
        authenticated = self.login()
        current = self.management_account(authenticated)
        status, metadata, content = self.management_change(authenticated, path, body, key=key)
        self.assertEqual(status, 200)
        self.assertEqual(json.loads(content), result)
        self.assertEqual(metadata["X-Request-Id"], result["request_id"])
        self.assertEqual(self.management_account(authenticated), current)
        changed = {**body, "password": ("a" if credentials["password"][0] != "a" else "b") + credentials["password"][1:]}
        status, metadata, content = self.management_change(authenticated, path, changed, key=key)
        self.assertEqual(status, 409)
        self.assertEqual(json.loads(content)["code"], "IDEMPOTENCY_CONFLICT")
        self.assertEqual(self.management_account(authenticated), current)
        operation = str(uuid.UUID(result["data"]["operation_id"]))
        persisted = json.loads(mysql("SELECT JSON_OBJECT('operation_count',COUNT(*),'audit_count',"
            "(SELECT COUNT(*) FROM medical_local.platform_local_audit WHERE operation_id='" + operation + "'),"
            "'audit',(SELECT resource_ref FROM medical_local.platform_local_audit WHERE operation_id='" + operation + "'),"
            "'hmac_length',MIN(CHAR_LENGTH(request_hmac_sha256)),'hmac',MIN(request_hmac_sha256),'key_id',MIN(hmac_key_id),'response',MIN(CAST(response_ref AS CHAR))) "
            "FROM medical_local.platform_local_operation WHERE operation_id='" + operation + "'", settings()["root_password"]).stdout)
        self.assertEqual(persisted["operation_count"], 1)
        self.assertEqual(persisted["audit_count"], 1)
        self.assertEqual(persisted["hmac_length"], 64)
        secret = json.loads((RUNTIME / "local-management-secret.json").read_bytes())
        self.assertEqual(persisted["key_id"], secret["key_id"])
        canonical = rfc8785.dumps({"method": "POST", "path": path, "schema_version": "1.0.0", "body": body})
        expected_hmac = hmac.new(bytes.fromhex(secret["key"]), canonical, hashlib.sha256).hexdigest()
        self.assertTrue(hmac.compare_digest(persisted["hmac"], expected_hmac))
        self.assertEqual(persisted["audit"], {"schema_version": "1.0.0", "resource_kind": "LOCAL_ACCOUNT", "user_id": self.user_id,
            "revision": result["resource_revision"], "credential_revision": result["data"]["account"]["credential_revision"], "status": "ACTIVE"})
        self.assertEqual(json.loads(persisted["response"]), result)
        self.assertNotIn(body["current_password"], persisted["response"])
        self.assertNotIn(body["password"], persisted["response"])
        self.assertEqual((RUNTIME / "local-management-secret.json").stat().st_mode & 0o777, 0o600)
        (RUNTIME / "local-account-credential-receipt.json").write_text(json.dumps({"scope": "LOCAL_OWNER_CREDENTIAL_MANAGEMENT", "result": result,
            "previous_credential_revision": before["credential_revision"], "old_session_status": 403, "new_login_status": 302,
            "replay_status": 200, "changed_request_status": 409, "operation_count": 1, "audit_count": 1, "hmac_verified": True,
            "audit": persisted["audit"], "hmac_key_id": secret["key_id"]}), encoding="utf-8")

    def test_local_account_pages_provide_credential_and_management_controls(self):
        browser = self.login()
        for path, controls in (("/local-auth/account.html", ("data-credential", "current_password", "data-management")),
                               ("/local-auth/management.html", ("data-create", "data-accounts", "data-management-status"))):
            status, metadata, content = browser.request("GET", path)
            self.assertEqual(status, 200)
            for control in controls:
                self.assertIn(control, content.decode("utf-8"))

    def test_actual_owner_reads_business_session_without_enterprise_permissions(self):
        browser = self.login()
        status, metadata, content = browser.request("GET", "/api/session", schema="1.0.0")
        self.assertEqual(status, 200)
        envelope = json.loads(content)
        self.assertEqual(set(envelope), {"schema_version", "request_id", "resource_revision", "data"})
        self.assertEqual(envelope["schema_version"], "1.0.0")
        self.assertEqual(str(uuid.UUID(envelope["request_id"])), envelope["request_id"])
        self.assertEqual(metadata["X-Request-Id"], envelope["request_id"])
        self.assertIsNone(envelope["resource_revision"])
        session = envelope["data"]
        self.assertEqual(set(session), {"schema_version", "user", "memberships", "current_context", "csrf",
                                        "action_summary", "invitation_entry"})
        self.assertEqual(session["schema_version"], "1.0.0")
        self.assertEqual(session["user"]["username"], self.owner["username"])
        self.assertEqual(session["user"]["platform_role"], "PLATFORM_ADMIN")
        self.assertEqual(session["memberships"], [])
        self.assertIsNone(session["current_context"])
        self.assertEqual(session["action_summary"], [])
        self.assertEqual(session["csrf"]["header_name"], "X-CSRF-TOKEN")
        current_csrf = browser.csrf()
        self.assertEqual(set(session["csrf"]), {"header_name", "token"})
        self.assertEqual(session["csrf"]["header_name"], current_csrf["header_name"])
        self.assertTrue(session["csrf"]["token"] == current_csrf["token"], "业务会话与原生 CSRF 必须使用同一 Token")
        self.assertEqual(metadata["Cache-Control"], "no-store")
        self.assertEqual(metadata["X-Schema-Version"], "1.0.0")
        self.assertFalse(self.owner["password"].encode() in content)
        self.assertEqual(int(mysql("SELECT COUNT(*) FROM medical_local.tenant", settings()["root_password"]).stdout), 0)
        repeated_status, repeated_metadata, repeated_content = browser.request("GET", "/api/session", schema="1.0.0")
        self.assertEqual(repeated_status, 200)
        self.assertTrue(json.loads(repeated_content)["data"] == session, "重复读取必须保留当前会话字段")
        session_id = str(uuid.UUID(base64.b64decode(browser.cookies["MEDICAL_SESSION"].value, validate=True).decode("ascii")))
        internal_id = mysql("SELECT context_id FROM medical_local.platform_session_context WHERE session_id='" + session_id + "'",
                            settings()["root_password"]).stdout.strip()
        self.assertEqual(str(uuid.UUID(internal_id)), internal_id)
        final_status, final_metadata, final_content = browser.request("GET", "/api/session", schema="1.0.0")
        self.assertEqual(final_status, 200)
        self.assertTrue(json.loads(final_content)["data"] == session, "再次读取必须保持当前会话字段")
        self.assertEqual(mysql("SELECT context_id FROM medical_local.platform_session_context WHERE session_id='" + session_id + "'",
                               settings()["root_password"]).stdout.strip(), internal_id)
        context = json.loads(mysql("SELECT JSON_OBJECT('user_id',user_id,'tenant_id',tenant_id,"
                                   "'context_revision',context_revision,'auth_epoch',auth_epoch,'governance_epoch',governance_epoch) "
                                   "FROM medical_local.platform_session_context WHERE session_id='" + session_id + "'",
                                   settings()["root_password"]).stdout)
        self.assertEqual(context, {"user_id": session["user"]["user_id"], "tenant_id": None, "context_revision": 0,
                                   "auth_epoch": None, "governance_epoch": None})
        receipt = {name: value for name, value in session.items() if name != "csrf"}
        receipt.update({"scope": "LOCAL_BUSINESS_SESSION", "http_status": status, "context_record": context,
                        "internal_context_id": internal_id,
                        "request_id": envelope["request_id"], "resource_revision": envelope["resource_revision"],
                        "csrf_header": session["csrf"]["header_name"],
                        "csrf_sha256": hashlib.sha256(session["csrf"]["token"].encode("utf-8")).hexdigest()})
        (RUNTIME / "business-session-receipt.json").write_text(json.dumps(receipt), encoding="utf-8")
        logout_status, logout_metadata, logout_content = browser.request("POST", "/auth/logout", {}, ORIGIN, session["csrf"])
        self.assertEqual(logout_status, 204)
        denied_status, denied_metadata, denied_content = browser.request("GET", "/api/session", schema="1.0.0")
        self.assertEqual(denied_status, 401)
        self.assertEqual(json.loads(denied_content)["code"], "AUTHENTICATION_REQUIRED")

    def test_business_session_requires_explicit_schema_version(self):
        browser = self.login()
        status, metadata, content = browser.request("GET", "/api/session")
        self.assertEqual(status, 422)
        self.assertEqual(json.loads(content)["code"], "SCHEMA_UNSUPPORTED")

    def test_current_session_returns_prebound_local_identity(self):
        browser = self.login()
        path = "/api/session/identity-binding"
        status, metadata, content = browser.request("GET", path, schema="1.0.0")
        self.assertEqual(status, 200)
        result = json.loads(content)
        self.assertEqual(set(result), {"schema_version", "request_id", "resource_revision", "data"})
        self.assertEqual(result["schema_version"], "1.0.0")
        self.assertEqual(str(uuid.UUID(result["request_id"])), result["request_id"])
        self.assertEqual(metadata["X-Request-Id"], result["request_id"])
        self.assertEqual(metadata["X-Schema-Version"], "1.0.0")
        self.assertEqual(metadata["Cache-Control"], "no-store")
        self.assertIsNone(result["resource_revision"])
        binding = {"mode": "local", "user_id": self.user_id, "issuer": None, "subject": None}
        self.assertEqual(result["data"], binding)
        self.assertFalse(self.owner["password"].encode() in content)
        repeated_status, repeated_headers, repeated_content = browser.request("GET", path, schema="1.0.0")
        self.assertEqual(repeated_status, 200)
        self.assertEqual(json.loads(repeated_content)["data"], binding)
        self.assertNotEqual(json.loads(repeated_content)["request_id"], result["request_id"])
        missing_status, missing_headers, missing_content = browser.request("GET", path)
        self.assertEqual(missing_status, 422)
        self.assertEqual(json.loads(missing_content)["code"], "SCHEMA_UNSUPPORTED")
        query_status, query_headers, query_content = browser.request("GET", path + "?user_id=" + self.user_id, schema="1.0.0")
        self.assertEqual(query_status, 422)
        self.assertEqual(json.loads(query_content)["code"], "SCHEMA_INVALID")
        anonymous_status, anonymous_headers, anonymous_content = ActualBrowserSession().request("GET", path, schema="1.0.0")
        self.assertEqual(anonymous_status, 401)
        self.assertEqual(json.loads(anonymous_content)["code"], "AUTHENTICATION_REQUIRED")
        self.assertEqual(int(mysql("SELECT COUNT(*) FROM medical_local.tenant", settings()["root_password"]).stdout), 0)
        self.assertEqual(int(mysql("SELECT COUNT(*) FROM medical_local.tenant_member", settings()["root_password"]).stdout), 0)
        (RUNTIME / "current-identity-binding-receipt.json").write_text(json.dumps({"scope": "CURRENT_AUTHENTICATED_LOCAL_IDENTITY",
            "result": result, "repeat_status": 200, "missing_schema_status": 422, "query_status": 422,
            "anonymous_status": 401, "tenant_count": 0, "member_count": 0}), encoding="utf-8")

    def test_business_session_rejects_anonymous_request(self):
        status, metadata, content = ActualBrowserSession().request("GET", "/api/session", schema="1.0.0")
        self.assertEqual(status, 401)
        self.assertEqual(json.loads(content)["code"], "AUTHENTICATION_REQUIRED")

    def selection(self, browser, csrf, origin=ORIGIN, context="0", key=None, extra=None):
        body = {"tenant_id": self.user_id, "expected_context_revision": "0"}
        if extra is not None:
            body.update(extra)
        return browser.request("POST", "/api/session/tenant-selections", origin=origin, csrf=csrf, schema="1.0.0",
                               json_body=body, context=context, key=key)

    def test_tenant_selection_rejects_unjoined_owner_without_context_update(self):
        browser = self.login()
        status, metadata, content = browser.request("GET", "/api/session", schema="1.0.0")
        self.assertEqual(status, 200)
        session = json.loads(content)["data"]
        self.assertEqual(session["memberships"], [])
        self.assertIsNone(session["current_context"])
        self.assertEqual(int(mysql("SELECT COUNT(*) FROM medical_local.tenant", settings()["root_password"]).stdout), 0)
        key = secrets.token_urlsafe(24)
        # 使用真实用户 ID 请求未登记的企业，不创建企业或成员样本。
        status, metadata, content = self.selection(browser, session["csrf"], key=key)
        self.assertEqual(status, 403)
        self.assertEqual(json.loads(content)["code"], "ACCESS_DENIED")
        repeated_status, repeated_metadata, repeated_content = self.selection(browser, session["csrf"], key=key)
        self.assertEqual(repeated_status, 403)
        self.assertEqual(json.loads(repeated_content)["code"], "ACCESS_DENIED")
        session_id = str(uuid.UUID(base64.b64decode(browser.cookies["MEDICAL_SESSION"].value, validate=True).decode("ascii")))
        context = json.loads(mysql("SELECT JSON_OBJECT('tenant_id',tenant_id,'context_revision',context_revision,"
                                   "'auth_epoch',auth_epoch,'governance_epoch',governance_epoch) "
                                   "FROM medical_local.platform_session_context WHERE session_id='" + session_id + "'",
                                   settings()["root_password"]).stdout)
        self.assertEqual(context, {"tenant_id": None, "context_revision": 0, "auth_epoch": None, "governance_epoch": None})
        for table in ("operation_record", "audit_event", "outbox_event"):
            self.assertEqual(int(mysql("SELECT COUNT(*) FROM medical_local." + table, settings()["root_password"]).stdout), 0)
        (RUNTIME / "tenant-selection-rejection-receipt.json").write_text(json.dumps({
            "scope": "LOCAL_UNJOINED_TENANT_SELECTION", "subject_id": self.user_id, "target_id_source": "actual_user_id",
            "http_status": status, "repeated_status": repeated_status, "code": "ACCESS_DENIED", "context_record": context,
            "request_id": metadata["X-Request-Id"], "business_records_created": 0}), encoding="utf-8")

    def test_tenant_selection_rejects_anonymous_request(self):
        browser = ActualBrowserSession()
        status, metadata, content = self.selection(browser, browser.csrf(), key=secrets.token_urlsafe(24))
        self.assertEqual(status, 401)
        self.assertEqual(json.loads(content)["code"], "AUTHENTICATION_REQUIRED")

    def test_tenant_selection_requires_actual_csrf(self):
        browser = self.login()
        status, metadata, content = self.selection(browser, None, key=secrets.token_urlsafe(24))
        self.assertEqual(status, 403)
        self.assertEqual(json.loads(content)["code"], "ACCESS_DENIED")

    def test_tenant_selection_requires_exact_registered_origin(self):
        browser = self.login()
        status, metadata, content = self.selection(browser, browser.csrf(), origin="http://localhost:18080", key=secrets.token_urlsafe(24))
        self.assertEqual(status, 403)
        self.assertEqual(json.loads(content)["code"], "ACCESS_DENIED")

    def test_tenant_selection_rejects_unknown_fields(self):
        browser = self.login()
        status, metadata, content = self.selection(browser, browser.csrf(), key=secrets.token_urlsafe(24), extra={"user_id": self.user_id})
        self.assertEqual(status, 422)
        self.assertEqual(json.loads(content)["code"], "SCHEMA_INVALID")

    def test_tenant_selection_requires_context_and_idempotency_headers(self):
        browser = self.login()
        csrf = browser.csrf()
        for context, key in ((None, secrets.token_urlsafe(24)), ("0", None)):
            status, metadata, content = self.selection(browser, csrf, context=context, key=key)
            self.assertEqual(status, 422)
            self.assertEqual(json.loads(content)["code"], "SCHEMA_INVALID")

    def test_tenant_selection_rejects_mismatching_context_header(self):
        browser = self.login()
        status, metadata, content = self.selection(browser, browser.csrf(), context="1", key=secrets.token_urlsafe(24))
        self.assertEqual(status, 409)
        self.assertEqual(json.loads(content)["code"], "CONTEXT_CHANGED")

    def test_login_requires_actual_csrf(self):
        browser = ActualBrowserSession()
        browser.csrf()
        status, metadata, content = browser.request("POST", "/auth/login", self.owner, ORIGIN)
        self.assertEqual(status, 403)

    def test_login_requires_exact_registered_origin(self):
        browser = ActualBrowserSession()
        csrf = browser.csrf()
        status, metadata, content = browser.request("POST", "/auth/login", self.owner, "http://localhost:18080", csrf)
        self.assertEqual(status, 403)
        self.assertEqual(json.loads(content)["code"], "ACCESS_DENIED")

    def test_actual_account_rejects_changed_password(self):
        browser = ActualBrowserSession()
        csrf = browser.csrf()
        changed = dict(self.owner)
        changed["password"] = ("a" if self.owner["password"][0] != "a" else "b") + self.owner["password"][1:]
        status, metadata, content = browser.request("POST", "/auth/login", changed, ORIGIN, csrf)
        self.assertEqual(status, 401)
        self.assertEqual(json.loads(content)["code"], "AUTHENTICATION_FAILED")

    def test_logout_revokes_actual_session(self):
        browser = self.login()
        csrf = browser.csrf()
        before = browser.cookies["MEDICAL_SESSION"].value
        status, metadata, content = browser.request("POST", "/auth/logout", {}, ORIGIN, csrf)
        self.assertEqual(status, 204)
        self.assertEqual(content, b"")
        session_id = str(uuid.UUID(base64.b64decode(before, validate=True).decode("ascii")))
        native_environment = environment()
        native_environment["REDISCLI_AUTH"] = settings()["redis_password"]
        deleted = subprocess.run([str(REDIS / "redis-cli"), "-h", "127.0.0.1", "-p", "16379",
                                  "EXISTS", "medical:local:session:sessions:expires:" + session_id],
                                 capture_output=True, text=True, check=True, env=native_environment)
        self.assertEqual(deleted.stdout.strip(), "0")
        # indexed repository 保留短期事件元数据，活动期限标记必须已经删除。
        expiry = subprocess.run([str(REDIS / "redis-cli"), "-h", "127.0.0.1", "-p", "16379",
                                 "TTL", "medical:local:session:sessions:" + session_id],
                                capture_output=True, text=True, check=True, env=native_environment)
        self.assertGreaterEqual(int(expiry.stdout.strip()), 0)
        self.assertLessEqual(int(expiry.stdout.strip()), 300)
        revoked = json.loads(mysql("SELECT JSON_OBJECT('status',status,'has_revoked_at',revoked_at IS NOT NULL) "
                                   "FROM medical_local.platform_local_session WHERE session_id='" + session_id + "'",
                                   settings()["root_password"]).stdout)
        self.assertEqual(revoked, {"status": "REVOKED", "has_revoked_at": 1})
        browser.cookies["MEDICAL_SESSION"] = before
        status, metadata, content = browser.request("GET", "/auth/account")
        self.assertEqual(status, 401)
        self.assertEqual(json.loads(content)["code"], "AUTHENTICATION_REQUIRED")

    def test_first_administrator_setup_cannot_be_repeated(self):
        browser = self.login()
        csrf = browser.csrf()
        status, metadata, content = browser.request("POST", "/auth/setup", self.owner, ORIGIN, csrf)
        self.assertEqual(status, 403)

    def test_existing_owner_credential_recovery_revokes_previous_session(self):
        browser = self.login()
        previous = int(mysql("SELECT credential_revision FROM medical_local.platform_local_account",
                             settings()["root_password"]).stdout)
        recover_owner()
        type(self).owner = provision_owner()
        current = int(mysql("SELECT credential_revision FROM medical_local.platform_local_account",
                            settings()["root_password"]).stdout)
        self.assertEqual(current, previous + 1)
        status, metadata, content = browser.request("GET", "/auth/account")
        self.assertEqual(status, 403)
        self.assertEqual(json.loads(content)["code"], "ACCESS_DENIED")
        authenticated = self.login()
        status, metadata, content = authenticated.request("GET", "/auth/account")
        self.assertEqual(status, 200)
        self.assertEqual(json.loads(content)["username"], self.owner["username"])
        self.assertEqual((RUNTIME / "owner-credentials.json").stat().st_mode & 0o777, 0o600)
        (RUNTIME / "owner-account-receipt.json").write_bytes(content)

    def test_recovery_rejects_concurrent_operator_before_database_update(self):
        previous = int(mysql("SELECT credential_revision FROM medical_local.platform_local_account",
                             settings()["root_password"]).stdout)
        with (RUNTIME / "owner-recovery.lock").open("a", encoding="utf-8") as lock:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
            with self.assertRaises(BlockingIOError):
                recover_owner()
        current = int(mysql("SELECT credential_revision FROM medical_local.platform_local_account",
                            settings()["root_password"]).stdout)
        self.assertEqual(current, previous)

    def test_z_actual_account_locks_after_five_failures_and_recovers(self):
        previous_browser = self.login()
        previous = int(mysql("SELECT credential_revision FROM medical_local.platform_local_account",
                             settings()["root_password"]).stdout)
        changed = dict(self.owner)
        changed["password"] = ("a" if self.owner["password"][0] != "a" else "b") + self.owner["password"][1:]
        for count in range(1, 6):
            browser = ActualBrowserSession()
            status, metadata, content = browser.request("POST", "/auth/login", changed, ORIGIN, browser.csrf())
            self.assertEqual(status, 401)
            self.assertEqual(json.loads(content)["code"], "AUTHENTICATION_FAILED")
            actual = int(mysql("SELECT failed_attempts FROM medical_local.platform_local_account",
                               settings()["root_password"]).stdout)
            self.assertEqual(actual, count)
        locked = json.loads(mysql("SELECT JSON_OBJECT('failed_attempts',failed_attempts,"
                                  "'remaining_seconds',TIMESTAMPDIFF(SECOND,UTC_TIMESTAMP(6),locked_until)) "
                                  "FROM medical_local.platform_local_account", settings()["root_password"]).stdout)
        self.assertEqual(locked["failed_attempts"], 5)
        self.assertGreater(locked["remaining_seconds"], 0)
        self.assertLessEqual(locked["remaining_seconds"], 600)
        browser = ActualBrowserSession()
        status, metadata, content = browser.request("POST", "/auth/login", self.owner, ORIGIN, browser.csrf())
        self.assertEqual(status, 401)
        self.assertEqual(json.loads(content)["code"], "AUTHENTICATION_FAILED")
        recover_owner()
        type(self).owner = provision_owner()
        restored = json.loads(mysql("SELECT JSON_OBJECT('failed_attempts',failed_attempts,"
                                    "'unlocked',locked_until IS NULL,'credential_revision',credential_revision) "
                                    "FROM medical_local.platform_local_account", settings()["root_password"]).stdout)
        self.assertEqual(restored, {"failed_attempts": 0, "unlocked": 1, "credential_revision": previous + 1})
        status, metadata, content = previous_browser.request("GET", "/auth/account")
        self.assertEqual(status, 403)
        self.assertEqual(json.loads(content)["code"], "ACCESS_DENIED")
        authenticated = self.login()
        status, metadata, content = authenticated.request("GET", "/auth/account")
        self.assertEqual(status, 200)
        (RUNTIME / "owner-account-receipt.json").write_bytes(content)
        (RUNTIME / "owner-lockout-receipt.json").write_text(json.dumps({"scope": "LOCAL_OWNER_LOCKOUT",
            "username": self.owner["username"], "locked": locked, "restored": restored,
            "old_session_status": 403, "new_login_status": 302}), encoding="utf-8")

    def test_database_persists_one_actual_owner_and_complete_migration(self):
        identity = json.loads(mysql("""
            USE medical_local;
            SELECT JSON_OBJECT('server_uuid',@@server_uuid,'database',DATABASE(),'version',VERSION(),
              'account_count',(SELECT COUNT(*) FROM medical_local.platform_local_account),
              'active_admin_count',(SELECT COUNT(*) FROM medical_local.platform_local_account WHERE status='ACTIVE' AND platform_role='PLATFORM_ADMIN'),
              'username',(SELECT username FROM medical_local.platform_local_account),
              'password_hash_valid',(SELECT LENGTH(password_hash)=60 AND LEFT(password_hash,7)='$2a$12$' FROM medical_local.platform_local_account),
              'business_and_platform_tables',(SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='medical_local' AND table_name NOT IN ('DATABASECHANGELOG','DATABASECHANGELOGLOCK')),
              'changesets',(SELECT COUNT(*) FROM medical_local.DATABASECHANGELOG),
              'context_rows',(SELECT COUNT(*) FROM medical_local.platform_session_context),
              'context_unique_ids',(SELECT COUNT(DISTINCT context_id) FROM medical_local.platform_session_context),
              'context_invalid_ids',(SELECT COUNT(*) FROM medical_local.platform_session_context WHERE context_id IS NULL OR IS_UUID(context_id)=0),
              'context_column_valid',(SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='medical_local'
                AND table_name='platform_session_context' AND column_name='context_id' AND column_type='char(36)'
                AND character_set_name='ascii' AND is_nullable='NO'),
              'context_unique_constraint',(SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema='medical_local'
                AND table_name='platform_session_context' AND index_name='uq_platform_context_identity'
                AND column_name='context_id' AND non_unique=0),
              'revoked_sessions',(SELECT COUNT(*) FROM medical_local.platform_local_session WHERE status='REVOKED' AND revoked_at IS NOT NULL))
            """, settings()["root_password"]).stdout)
        expected = json.loads((RUNTIME / "service-identity.json").read_bytes())
        self.assertEqual(identity["server_uuid"], expected["server_uuid"])
        self.assertEqual(identity["database"], expected["database"])
        self.assertTrue(identity["version"].startswith("8.4."))
        self.assertEqual(identity["account_count"], 1)
        self.assertEqual(identity["active_admin_count"], 1)
        self.assertEqual(identity["username"], self.owner["username"])
        self.assertEqual(identity["password_hash_valid"], 1)
        self.assertEqual(identity["business_and_platform_tables"], 180)
        self.assertEqual(identity["changesets"], 226)
        self.assertGreater(identity["context_rows"], 0)
        self.assertEqual(identity["context_unique_ids"], identity["context_rows"])
        self.assertEqual(identity["context_invalid_ids"], 0)
        self.assertEqual(identity["context_column_valid"], 1)
        self.assertEqual(identity["context_unique_constraint"], 1)
        self.assertGreaterEqual(identity["revoked_sessions"], 1)
        (RUNTIME / "database-audit-receipt.json").write_text(json.dumps(identity), encoding="utf-8")


if __name__ == "__main__":
    unittest.main(verbosity=2)
