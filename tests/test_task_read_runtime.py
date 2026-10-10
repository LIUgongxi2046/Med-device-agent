import json
from pathlib import Path
import sys
import unittest


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
sys.path.insert(0, str(ROOT / "tests"))
from local_runtime import mysql, settings, verify_application_deployment
from test_local_identity_runtime import ActualBrowserSession, ORIGIN


class TaskReadRuntimeChecks(unittest.TestCase):
    def setUp(self):
        self.deployment = verify_application_deployment()
        owner = json.loads((ROOT / ".implementation-work/runtime/owner-credentials.json").read_bytes())
        self.browser = ActualBrowserSession()
        status, _, _ = self.browser.request("POST", "/auth/login", owner, ORIGIN, self.browser.csrf())
        self.assertEqual(status, 302)
        status, _, content = self.browser.request("GET", "/api/session", schema="1.0.0")
        self.assertEqual(status, 200)
        self.session = json.loads(content)["data"]
        self.assertIsNone(self.session["current_context"])
        self.assertEqual(self.session["memberships"], [])
        self.task_id = self.session["user"]["user_id"]

    def tearDown(self):
        status, _, _ = self.browser.request("POST", "/auth/logout", origin=ORIGIN, csrf=self.browser.csrf())
        self.assertEqual(status, 204)

    def read(self, suffix="", **options):
        arguments = {"schema": "1.0.0", "context": "0"}
        arguments.update(options)
        return self.browser.request("GET", "/api/tasks/" + self.task_id + suffix, **arguments)

    def test_actual_owner_without_enterprise_cannot_read_task_contents(self):
        status, _, content = self.read()
        self.assertEqual(status, 403)
        error = json.loads(content)
        self.assertEqual(error["code"], "ACCESS_DENIED")
        self.assertIsNone(error["resource_revision"])
        self.assertEqual(error["allowed_actions"], [])
        actual = json.loads(mysql("SELECT JSON_OBJECT('tasks',(SELECT COUNT(*) FROM medical_local.task_head),"
            "'inputs',(SELECT COUNT(*) FROM medical_local.task_input_revision),"
            "'operations',(SELECT COUNT(*) FROM medical_local.operation_record))", settings()["root_password"]).stdout)
        self.assertEqual(actual, {"tasks": 0, "inputs": 0, "operations": 0})
        receipt = {"scope": "TASK_READ_CURRENT_OWNER_WITHOUT_ENTERPRISE", "deployment": self.deployment,
            "user_id": self.task_id, "path": "/api/tasks/" + self.task_id, "status": status,
            "code": error["code"], "actual_database_counts": actual, "customer_success_verified": False}
        (ROOT / ".implementation-work/runtime/task-read-rejection-receipt.json").write_text(json.dumps(receipt), encoding="utf-8")

    def test_task_read_requires_current_schema_and_context_headers(self):
        for options, code in (({"schema": None}, "SCHEMA_UNSUPPORTED"), ({"context": None}, "SCHEMA_INVALID")):
            with self.subTest(options=options):
                status, _, content = self.read(**options)
                self.assertEqual(status, 422)
                self.assertEqual(json.loads(content)["code"], code)

    def test_task_read_rejects_unpublished_query_parameters(self):
        status, _, content = self.read("?owner_id=" + self.task_id)
        self.assertEqual(status, 422)
        self.assertEqual(json.loads(content)["code"], "SCHEMA_INVALID")

    def test_task_read_rejects_malformed_resource_identity(self):
        status, _, content = self.browser.request("GET", "/api/tasks/invalid", schema="1.0.0", context="0")
        self.assertEqual(status, 422)
        self.assertEqual(json.loads(content)["code"], "SCHEMA_INVALID")

    def test_task_read_requires_framework_authentication(self):
        status, _, content = ActualBrowserSession().request("GET", "/api/tasks/" + self.task_id, schema="1.0.0", context="0")
        self.assertEqual(status, 401)
        self.assertEqual(json.loads(content)["code"], "AUTHENTICATION_REQUIRED")


if __name__ == "__main__":
    unittest.main(verbosity=2)
