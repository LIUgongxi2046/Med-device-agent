import json
from pathlib import Path
import sys
import unittest


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
sys.path.insert(0, str(ROOT / "tests"))
from local_runtime import mysql, settings, verify_application_deployment
from test_local_identity_runtime import ActualBrowserSession, ORIGIN


class CollectionRuntimeChecks(unittest.TestCase):
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
        self.collection_id = self.session["user"]["user_id"]

    def tearDown(self):
        status, _, _ = self.browser.request("POST", "/auth/logout", origin=ORIGIN, csrf=self.browser.csrf())
        self.assertEqual(status, 204)

    def read(self, suffix="", **options):
        arguments = {"schema": "1.0.0", "context": "0"}
        arguments.update(options)
        return self.browser.request("GET", "/api/collections/" + self.collection_id + "/versions" + suffix, **arguments)

    def write(self, path, **options):
        arguments = {"origin": ORIGIN, "csrf": self.browser.csrf(), "schema": "1.0.0", "context": "0",
                     "key": self.collection_id, "json_body": {}}
        arguments.update(options)
        return self.browser.request("POST", path, **arguments)

    def error(self, response, status, code):
        self.assertEqual(response[0], status)
        value = json.loads(response[2])
        self.assertEqual(value["code"], code)
        self.assertIsNone(value["resource_revision"])
        self.assertEqual(value["allowed_actions"], [])
        self.assertFalse(value["retryable"])

    def test_current_owner_requires_actual_enterprise_admission(self):
        result = self.read()
        self.error(result, 403, "ACCESS_DENIED")
        actual = json.loads(mysql("SELECT JSON_OBJECT('collections',(SELECT COUNT(*) FROM medical_local.collection_head),"
            "'versions',(SELECT COUNT(*) FROM medical_local.collection_version),"
            "'members',(SELECT COUNT(*) FROM medical_local.collection_member),"
            "'tasks',(SELECT COUNT(*) FROM medical_local.task_head),"
            "'operations',(SELECT COUNT(*) FROM medical_local.operation_record))", settings()["root_password"]).stdout)
        self.assertEqual(actual, {"collections": 0, "versions": 0, "members": 0, "tasks": 0, "operations": 0})
        receipt = {"scope": "COLLECTION_CURRENT_OWNER_WITHOUT_ENTERPRISE", "deployment": self.deployment,
            "user_id": self.collection_id, "path": "/api/collections/" + self.collection_id + "/versions", "status": result[0],
            "code": json.loads(result[2])["code"], "actual_database_counts": actual, "customer_success_verified": False}
        (ROOT / ".implementation-work/runtime/collection-rejection-receipt.json").write_text(json.dumps(receipt), encoding="utf-8")

    def test_read_requires_current_schema_and_context(self):
        for options, code in (({"schema": None}, "SCHEMA_UNSUPPORTED"), ({"context": None}, "SCHEMA_INVALID")):
            with self.subTest(options=options):
                self.error(self.read(**options), 422, code)

    def test_pagination_rejects_invalid_or_unpublished_parameters(self):
        for query in ("?page=0", "?page=2", "?page_size=101", "?page=1&page=2", "?owner_id=" + self.collection_id,
                      "?dataset_watermark=18446744073709551616"):
            with self.subTest(query=query):
                self.error(self.read(query), 422, "SCHEMA_INVALID")

    def test_collection_paths_require_complete_uuid(self):
        self.error(self.browser.request("GET", "/api/collections/invalid/versions", schema="1.0.0", context="0"), 422, "SCHEMA_INVALID")
        self.error(self.write("/api/collections/invalid/versions"), 422, "SCHEMA_INVALID")

    def test_create_and_revision_require_complete_business_bodies(self):
        for path in ("/api/collections", "/api/collections/" + self.collection_id + "/versions"):
            with self.subTest(path=path):
                self.error(self.write(path), 422, "SCHEMA_INVALID")
                self.error(self.write(path, schema=None), 422, "SCHEMA_UNSUPPORTED")
                self.error(self.write(path, key=None), 422, "SCHEMA_INVALID")
                self.error(self.write(path + "?owner_id=" + self.collection_id), 422, "SCHEMA_INVALID")

    def test_framework_csrf_protects_collection_mutations(self):
        for path in ("/api/collections", "/api/collections/" + self.collection_id + "/versions"):
            with self.subTest(path=path):
                self.error(self.write(path, csrf=None), 403, "ACCESS_DENIED")

    def test_framework_authentication_protects_history(self):
        self.error(ActualBrowserSession().request("GET", "/api/collections/" + self.collection_id + "/versions",
                                                 schema="1.0.0", context="0"), 401, "AUTHENTICATION_REQUIRED")


if __name__ == "__main__":
    unittest.main(verbosity=2)
