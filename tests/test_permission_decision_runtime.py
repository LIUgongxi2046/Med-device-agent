import json
from pathlib import Path
import sys
import unittest


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
sys.path.insert(0, str(ROOT / "tests"))
from local_runtime import mysql, settings
from test_local_identity_runtime import ActualBrowserSession, ORIGIN


class PermissionDecisionRuntimeChecks(unittest.TestCase):
    def setUp(self):
        self.owner = json.loads((ROOT / ".implementation-work/runtime/owner-credentials.json").read_bytes())
        self.browser = ActualBrowserSession()
        csrf = self.browser.csrf()
        status, _, _ = self.browser.request("POST", "/auth/login", self.owner, ORIGIN, csrf)
        self.assertEqual(status, 302)
        status, _, content = self.browser.request("GET", "/api/session", schema="1.0.0")
        self.assertEqual(status, 200)
        self.session = json.loads(content)["data"]
        self.assertIsNone(self.session["current_context"])
        self.assertEqual(self.session["memberships"], [])
        self.assertEqual(self.session["user"]["platform_role"], "PLATFORM_ADMIN")

    def tearDown(self):
        csrf = self.browser.csrf()
        status, _, _ = self.browser.request("POST", "/auth/logout", origin=ORIGIN, csrf=csrf)
        self.assertEqual(status, 204)

    def submit(self, **overrides):
        options = {"json_body": {}, "origin": ORIGIN, "csrf": self.session["csrf"], "schema": "1.0.0",
                   "context": "0", "key": self.session["user"]["user_id"]}
        options.update(overrides)
        return self.browser.request("POST", "/api/permission-decisions", **options)

    def test_platform_administrator_without_enterprise_cannot_decide_permissions(self):
        status, _, content = self.submit()
        self.assertEqual(status, 403)
        result = json.loads(content)
        self.assertEqual(result["code"], "ACCESS_DENIED")
        self.assertIsNone(result["resource_revision"])
        self.assertEqual(result["allowed_actions"], [])

    def test_repeated_rejection_does_not_create_grants_operations_or_enterprises(self):
        for _ in range(2):
            status, _, content = self.submit()
            self.assertEqual(status, 403)
            self.assertEqual(json.loads(content)["code"], "ACCESS_DENIED")
        actual = json.loads(mysql("""
            SELECT JSON_OBJECT('tenants',(SELECT COUNT(*) FROM medical_local.tenant),
                'members',(SELECT COUNT(*) FROM medical_local.tenant_member),
                'grants',(SELECT COUNT(*) FROM medical_local.permission_grant),
                'operations',(SELECT COUNT(*) FROM medical_local.operation_record),
                'events',(SELECT COUNT(*) FROM medical_local.outbox_event))
            """, settings()["root_password"]).stdout.strip())
        self.assertEqual(actual, {"tenants": 0, "members": 0, "grants": 0, "operations": 0, "events": 0})

    def test_missing_schema_version_is_rejected(self):
        status, _, content = self.submit(schema=None)
        self.assertEqual(status, 422)
        self.assertEqual(json.loads(content)["code"], "SCHEMA_UNSUPPORTED")

    def test_invalid_context_and_idempotency_headers_are_rejected(self):
        for options in ({"context": None}, {"context": "-1"}, {"key": None}, {"key": ""}):
            status, _, content = self.submit(**options)
            self.assertEqual(status, 422)
            self.assertEqual(json.loads(content)["code"], "SCHEMA_INVALID")

    def test_csrf_and_origin_remain_required(self):
        for options in ({"csrf": None}, {"origin": "null"}):
            status, _, _ = self.submit(**options)
            self.assertEqual(status, 403)


if __name__ == "__main__":
    unittest.main(verbosity=2)
