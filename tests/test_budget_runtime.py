import json
from pathlib import Path
import sys
import unittest


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
sys.path.insert(0, str(ROOT / "tests"))
from local_runtime import mysql, settings
from test_local_identity_runtime import ActualBrowserSession, ORIGIN


class BudgetRuntimeChecks(unittest.TestCase):
    def setUp(self):
        owner = json.loads((ROOT / ".implementation-work/runtime/owner-credentials.json").read_bytes())
        self.browser = ActualBrowserSession()
        status, _, _ = self.browser.request("POST", "/auth/login", owner, ORIGIN, self.browser.csrf())
        self.assertEqual(status, 302)
        status, _, content = self.browser.request("GET", "/api/session", schema="1.0.0")
        self.assertEqual(status, 200)
        self.session = json.loads(content)["data"]
        self.assertIsNone(self.session["current_context"])
        self.assertEqual(self.session["memberships"], [])
        self.identity = self.session["user"]["user_id"]
        self.base = "/api/budgets/" + self.identity

    def tearDown(self):
        status, _, _ = self.browser.request("POST", "/auth/logout", origin=ORIGIN, csrf=self.browser.csrf())
        self.assertEqual(status, 204)

    def call(self, method, path, **overrides):
        options = {"schema": "1.0.0", "context": "0"}
        if method == "POST":
            options.update(json_body={}, origin=ORIGIN, csrf=self.session["csrf"], key=self.identity)
        options.update(overrides)
        return self.browser.request(method, path, **options)

    def test_current_platform_owner_cannot_read_or_approve_enterprise_budgets(self):
        for method, path in (("GET", "/api/budgets"), ("POST", "/api/budgets"),
                             ("GET", self.base), ("POST", self.base + "/decisions"),
                             ("GET", self.base + "/revisions"),
                             ("GET", self.base + "/revisions/" + self.identity)):
            status, headers, content = self.call(method, path)
            self.assertEqual(status, 403)
            result = json.loads(content)
            self.assertEqual(headers.get("Cache-Control"), "no-store")
            self.assertEqual(headers.get("X-Schema-Version"), "1.0.0")
            self.assertEqual(headers.get("X-Request-Id"), result["request_id"])
            self.assertEqual(result["code"], "ACCESS_DENIED")
            self.assertEqual(result["allowed_actions"], [])
            self.assertIsNone(result["resource_revision"])

    def test_queries_paths_and_headers_reject_unapproved_input(self):
        for path in ("/api/budgets?limit=0", "/api/budgets?limit=101", "/api/budgets?limit=1&limit=2",
                     "/api/budgets?tenant_id=" + self.identity, "/api/budgets?after=unapproved",
                     "/api/budgets?project_id=unapproved", self.base + "?unknown=1",
                     self.base.replace(self.identity, self.identity.upper())):
            status, _, content = self.call("GET", path)
            self.assertEqual(status, 422)
            self.assertEqual(json.loads(content)["code"], "SCHEMA_INVALID")
        for method, path in (("GET", "/api/budgets"), ("POST", "/api/budgets")):
            status, _, content = self.call(method, path, schema=None)
            self.assertEqual(status, 422)
            self.assertEqual(json.loads(content)["code"], "SCHEMA_UNSUPPORTED")
            for context in (None, "-1", "18446744073709551616"):
                status, _, content = self.call(method, path, context=context)
                self.assertEqual(status, 422)
                self.assertEqual(json.loads(content)["code"], "SCHEMA_INVALID")
        status, _, content = self.call("POST", "/api/budgets", key=None)
        self.assertEqual(status, 422)
        self.assertEqual(json.loads(content)["code"], "SCHEMA_INVALID")

    def test_history_queries_paths_and_headers_are_closed(self):
        history = self.base + "/revisions"
        detail = history + "/" + self.identity
        for path in (history + "?limit=0", history + "?limit=101", history + "?limit=1&limit=2",
                     history + "?project_id=" + self.identity, history + "?after=unapproved",
                     history + "?after=" + self.identity + "&after=" + self.identity,
                     detail + "?limit=20", history + "/unapproved"):
            status, _, content = self.call("GET", path)
            self.assertEqual(status, 422)
            self.assertEqual(json.loads(content)["code"], "SCHEMA_INVALID")
        for path in (history, detail):
            status, _, content = self.call("GET", path, schema=None)
            self.assertEqual(status, 422)
            self.assertEqual(json.loads(content)["code"], "SCHEMA_UNSUPPORTED")
            for context in (None, "-1", "18446744073709551616"):
                status, _, content = self.call("GET", path, context=context)
                self.assertEqual(status, 422)
                self.assertEqual(json.loads(content)["code"], "SCHEMA_INVALID")

    def test_actual_csrf_and_origin_protect_budget_approval(self):
        for path in ("/api/budgets", self.base + "/decisions"):
            for overrides in ({"csrf": None}, {"origin": "null"}):
                status, _, _ = self.call("POST", path, **overrides)
                self.assertEqual(status, 403)

    def test_repeated_rejections_do_not_create_accounts_approvals_or_charges(self):
        for _ in range(2):
            status, _, _ = self.call("POST", "/api/budgets")
            self.assertEqual(status, 403)
        result = mysql("""
            SELECT JSON_OBJECT('accounts',(SELECT COUNT(*) FROM medical_local.budget_account),
                'approvals',(SELECT COUNT(*) FROM medical_local.budget_revision),
                'reservations',(SELECT COUNT(*) FROM medical_local.budget_reservation),
                'invocations',(SELECT COUNT(*) FROM medical_local.invocation),
                'operations',(SELECT COUNT(*) FROM medical_local.operation_record),
                'events',(SELECT COUNT(*) FROM medical_local.outbox_event))
            """, settings()["root_password"])
        self.assertEqual(json.loads(result.stdout), {name: 0 for name in
                         ("accounts", "approvals", "reservations", "invocations", "operations", "events")})


if __name__ == "__main__":
    unittest.main(verbosity=2)
