import json
from pathlib import Path
import sys
import unittest


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
sys.path.insert(0, str(ROOT / "tests"))
from local_runtime import mysql, settings
from test_local_identity_runtime import ActualBrowserSession, ORIGIN


class TemplateRuntimeChecks(unittest.TestCase):
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
        self.key = self.session["user"]["user_id"]

    def tearDown(self):
        status, _, _ = self.browser.request("POST", "/auth/logout", origin=ORIGIN, csrf=self.browser.csrf())
        self.assertEqual(status, 204)

    def call(self, path="/api/template-versions", **overrides):
        options = {"schema": "1.0.0", "context": "0", "key": self.key,
                   "json_body": {}, "origin": ORIGIN, "csrf": self.session["csrf"]}
        options.update(overrides)
        return self.browser.request("POST", path, **options)

    def test_template_creation_requires_current_enterprise_membership(self):
        status, _, content = self.call()
        self.assertEqual(status, 403, "模板草稿入口必须核查实际企业身份")
        result = json.loads(content)
        self.assertEqual(result["code"], "ACCESS_DENIED")
        self.assertEqual(result["allowed_actions"], [])

    def test_schema_context_and_idempotency_headers_are_required(self):
        status, _, content = self.call(schema=None)
        self.assertEqual(status, 422)
        self.assertEqual(json.loads(content)["code"], "SCHEMA_UNSUPPORTED")
        for overrides in ({"context": None}, {"context": "-1"}, {"context": "18446744073709551616"},
                          {"key": None}, {"key": "short"}):
            status, _, content = self.call(**overrides)
            self.assertEqual(status, 422)
            self.assertEqual(json.loads(content)["code"], "SCHEMA_INVALID")

    def test_template_creation_rejects_query_parameters(self):
        for query in ("?tenant_id=" + self.key, "?unknown=1"):
            status, _, content = self.call("/api/template-versions" + query)
            self.assertEqual(status, 422)
            self.assertEqual(json.loads(content)["code"], "SCHEMA_INVALID")

    def test_template_creation_requires_csrf_and_origin(self):
        for overrides in ({"csrf": None}, {"origin": "null"}):
            status, _, _ = self.call(**overrides)
            self.assertEqual(status, 403)

    def test_repeated_denials_do_not_create_templates_or_side_effects(self):
        for _ in range(2):
            status, _, _ = self.call()
            self.assertEqual(status, 403)
        actual = json.loads(mysql("""
            SELECT JSON_OBJECT('templates',(SELECT COUNT(*) FROM medical_local.template_version),
                'definitions',(SELECT COUNT(*) FROM medical_local.template_definition),
                'operations',(SELECT COUNT(*) FROM medical_local.operation_record),
                'events',(SELECT COUNT(*) FROM medical_local.outbox_event))
            """, settings()["root_password"]).stdout)
        self.assertEqual(actual, {name: 0 for name in ("templates", "definitions", "operations", "events")})

    def test_template_approval_requires_current_enterprise_membership(self):
        path = "/api/template-versions/" + self.key + "/approval-decisions"
        for _ in range(2):
            status, _, content = self.call(path)
            self.assertEqual(status, 403, "模板核准入口必须核查实际企业身份")
            self.assertEqual(json.loads(content)["code"], "ACCESS_DENIED")

    def test_template_approval_preserves_schema_context_and_idempotency_requirements(self):
        path = "/api/template-versions/" + self.key + "/approval-decisions"
        status, _, content = self.call(path, schema=None)
        self.assertEqual(status, 422)
        self.assertEqual(json.loads(content)["code"], "SCHEMA_UNSUPPORTED")
        for overrides in ({"context": None}, {"key": None}):
            status, _, content = self.call(path, **overrides)
            self.assertEqual(status, 422)
            self.assertEqual(json.loads(content)["code"], "SCHEMA_INVALID")

    def test_template_approval_rejects_queries_and_requires_csrf(self):
        path = "/api/template-versions/" + self.key + "/approval-decisions"
        status, _, content = self.call(path + "?decision=APPROVE")
        self.assertEqual(status, 422)
        self.assertEqual(json.loads(content)["code"], "SCHEMA_INVALID")
        status, _, _ = self.call(path, csrf=None)
        self.assertEqual(status, 403)

    def test_template_approval_baseline_requires_current_enterprise_and_schema(self):
        path = "/api/template-versions/" + self.key + "/approval-baseline"
        status, _, content = self.browser.request("GET", path, schema="1.0.0", context="0")
        self.assertEqual(status, 403)
        self.assertEqual(json.loads(content)["code"], "ACCESS_DENIED")
        status, _, content = self.browser.request("GET", path, context="0")
        self.assertEqual(status, 422)
        self.assertEqual(json.loads(content)["code"], "SCHEMA_UNSUPPORTED")
        actual = json.loads(mysql("""
            SELECT JSON_OBJECT('decisions',(SELECT COUNT(*) FROM medical_local.template_approval_decision),
                'templates',(SELECT COUNT(*) FROM medical_local.template_version),
                'operations',(SELECT COUNT(*) FROM medical_local.operation_record),
                'events',(SELECT COUNT(*) FROM medical_local.outbox_event))
            """, settings()["root_password"]).stdout)
        self.assertEqual(actual, {name: 0 for name in ("decisions", "templates", "operations", "events")})


if __name__ == "__main__":
    unittest.main(verbosity=2)
