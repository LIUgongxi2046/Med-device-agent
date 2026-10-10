import json
from pathlib import Path
import sys
import unittest


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
sys.path.insert(0, str(ROOT / "tests"))
from local_runtime import mysql, settings
from test_local_identity_runtime import ActualBrowserSession, ORIGIN


class OperationRuntimeChecks(unittest.TestCase):
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
        self.owner_id = self.session["user"]["user_id"]
        self.path = "/api/operations/" + self.owner_id
        self.lookup = {"operation_id": "PJ-02", "idempotency_key": self.owner_id}

    def tearDown(self):
        status, _, _ = self.browser.request("POST", "/auth/logout", origin=ORIGIN, csrf=self.browser.csrf())
        self.assertEqual(status, 204)

    def call(self, method, path, **overrides):
        options = {"schema": "1.0.0", "context": "0"}
        if method == "POST":
            options.update(json_body=self.lookup, origin=ORIGIN, csrf=self.session["csrf"])
        options.update(overrides)
        return self.browser.request(method, path, **options)

    def test_lookup_and_status_require_current_enterprise_membership(self):
        for method, path in (("POST", "/api/operation-lookups"), ("GET", self.path)):
            status, headers, content = self.call(method, path)
            self.assertEqual(status, 403)
            result = json.loads(content)
            self.assertEqual(result["code"], "ACCESS_DENIED")
            self.assertEqual(result["allowed_actions"], [])
            self.assertIsNone(result["resource_revision"])
            self.assertEqual(headers["Cache-Control"], "no-store")

    def test_queries_paths_and_lookup_bodies_are_closed(self):
        for method, path in (("POST", "/api/operation-lookups?tenant_id=" + self.owner_id),
                             ("GET", self.path + "?subject_id=" + self.owner_id),
                             ("GET", "/api/operations/unapproved"), ("GET", self.path.upper().replace("/API/OPERATIONS/", "/api/operations/"))):
            status, _, content = self.call(method, path)
            self.assertEqual(status, 422)
            self.assertEqual(json.loads(content)["code"], "SCHEMA_INVALID")
        for body in ({}, {**self.lookup, "tenant_id": self.owner_id}, {**self.lookup, "operation_id": ""},
                     {**self.lookup, "idempotency_key": ""}, {**self.lookup, "idempotency_key": self.owner_id + " "},
                     {**self.lookup, "idempotency_key": self.owner_id * 4}):
            status, _, content = self.call("POST", "/api/operation-lookups", json_body=body)
            self.assertEqual(status, 422)
            self.assertEqual(json.loads(content)["code"], "SCHEMA_INVALID")

    def test_schema_and_current_context_headers_are_required(self):
        for method, path in (("POST", "/api/operation-lookups"), ("GET", self.path)):
            status, _, content = self.call(method, path, schema=None)
            self.assertEqual(status, 422)
            self.assertEqual(json.loads(content)["code"], "SCHEMA_UNSUPPORTED")
            for context in (None, "-1", "18446744073709551616"):
                status, _, content = self.call(method, path, context=context)
                self.assertEqual(status, 422)
                self.assertEqual(json.loads(content)["code"], "SCHEMA_INVALID")

    def test_lookup_requires_csrf_and_origin(self):
        for options in ({"csrf": None}, {"origin": "null"}):
            status, _, _ = self.call("POST", "/api/operation-lookups", **options)
            self.assertEqual(status, 403)

    def test_repeated_reads_do_not_create_customer_records(self):
        for _ in range(2):
            for method, path in (("POST", "/api/operation-lookups"), ("GET", self.path)):
                status, _, _ = self.call(method, path)
                self.assertEqual(status, 403)
        result = mysql("""
            SELECT JSON_OBJECT('tenants',(SELECT COUNT(*) FROM medical_local.tenant),
                'members',(SELECT COUNT(*) FROM medical_local.tenant_member),
                'projects',(SELECT COUNT(*) FROM medical_local.project),
                'operations',(SELECT COUNT(*) FROM medical_local.operation_record),
                'events',(SELECT COUNT(*) FROM medical_local.outbox_event))
            """, settings()["root_password"])
        self.assertEqual(json.loads(result.stdout), {name: 0 for name in ("tenants", "members", "projects", "operations", "events")})

    def test_actual_retention_columns_and_policy_foreign_key_preserve_enterprise_identity(self):
        result = mysql("""
            SELECT JSON_OBJECT('columns',(SELECT JSON_ARRAYAGG(JSON_OBJECT('name',COLUMN_NAME,'nullable',IS_NULLABLE,'type',COLUMN_TYPE))
                FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='medical_local' AND TABLE_NAME='operation_record'
                  AND COLUMN_NAME IN ('dedup_expires_at','dedup_policy_revision')),
                'foreign_key',(SELECT JSON_ARRAYAGG(JSON_OBJECT('column',COLUMN_NAME,'target_table',REFERENCED_TABLE_NAME,
                  'target_column',REFERENCED_COLUMN_NAME,'ordinal',ORDINAL_POSITION)) FROM information_schema.KEY_COLUMN_USAGE
                  WHERE TABLE_SCHEMA='medical_local' AND TABLE_NAME='operation_record' AND CONSTRAINT_NAME='fk_operation_dedup_policy'),
                'check_count',(SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA='medical_local'
                  AND TABLE_NAME='operation_record' AND CONSTRAINT_NAME='ck_operation_dedup_policy' AND CONSTRAINT_TYPE='CHECK' AND ENFORCED='YES'))
            """, settings()["root_password"])
        actual = json.loads(result.stdout)
        columns = {item["name"]: item for item in actual["columns"]}
        self.assertEqual(columns, {"dedup_expires_at": {"name": "dedup_expires_at", "nullable": "YES", "type": "datetime(6)"},
                                   "dedup_policy_revision": {"name": "dedup_policy_revision", "nullable": "YES", "type": "char(36)"}})
        entries = sorted(actual["foreign_key"], key=lambda item: item["ordinal"])
        self.assertEqual([item["column"] for item in entries], ["tenant_id", "dedup_policy_revision"])
        self.assertEqual([item["target_column"] for item in entries], ["tenant_id", "version_id"])
        self.assertTrue(all(item["target_table"] == "version_registry" for item in entries))
        self.assertEqual(actual["check_count"], 1)
        print("OPERATION_RETENTION_CHECK source=ACTUAL_MYSQL columns=2 foreign_keys=1 enforced_checks=1")


if __name__ == "__main__":
    unittest.main(verbosity=2)
