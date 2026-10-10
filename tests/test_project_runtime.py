import json
from pathlib import Path
import sys
import unittest


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
sys.path.insert(0, str(ROOT / "tests"))
from local_runtime import mysql, settings
from test_local_identity_runtime import ActualBrowserSession, ORIGIN


class ProjectRuntimeChecks(unittest.TestCase):
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
        self.absent_id = self.session["user"]["user_id"]
        self.base = "/api/projects/" + self.absent_id

    def tearDown(self):
        status, _, _ = self.browser.request("POST", "/auth/logout", origin=ORIGIN, csrf=self.browser.csrf())
        self.assertEqual(status, 204)

    def call(self, method, path, **overrides):
        options = {"schema": "1.0.0", "context": "0"}
        if method == "POST":
            options.update(json_body={}, origin=ORIGIN, csrf=self.session["csrf"], key=self.absent_id)
        options.update(overrides)
        return self.browser.request(method, path, **options)

    def test_all_project_routes_require_current_enterprise_membership(self):
        for method, path in (("GET", "/api/projects"), ("POST", "/api/projects"), ("GET", self.base),
                             ("POST", self.base + "/profile-revisions"), ("POST", self.base + "/state-decisions"),
                             ("GET", "/api/work-items")):
            status, _, content = self.call(method, path)
            self.assertEqual(status, 403)
            result = json.loads(content)
            self.assertEqual(result["code"], "ACCESS_DENIED")
            self.assertEqual(result["allowed_actions"], [])
            self.assertIsNone(result["resource_revision"])

    def test_project_queries_and_paths_are_closed_and_bounded(self):
        for path in ("/api/projects?limit=0", "/api/projects?limit=101", "/api/projects?status=UNKNOWN",
                     "/api/projects?limit=1&limit=2", "/api/projects?tenant_id=" + self.absent_id,
                     "/api/projects?after=unapproved", self.base.replace(self.absent_id, self.absent_id.upper()),
                     "/api/work-items?unknown=1", "/api/work-items?project_id=unapproved"):
            status, _, content = self.call("GET", path)
            self.assertEqual(status, 422)
            self.assertEqual(json.loads(content)["code"], "SCHEMA_INVALID")

    def test_schema_context_and_idempotency_headers_are_required(self):
        for method, path in (("GET", "/api/projects"), ("GET", "/api/work-items"), ("POST", "/api/projects")):
            status, _, content = self.call(method, path, schema=None)
            self.assertEqual(status, 422)
            self.assertEqual(json.loads(content)["code"], "SCHEMA_UNSUPPORTED")
            for context in (None, "-1", "18446744073709551616"):
                status, _, content = self.call(method, path, context=context)
                self.assertEqual(status, 422)
                self.assertEqual(json.loads(content)["code"], "SCHEMA_INVALID")
        status, _, content = self.call("POST", "/api/projects", key=None)
        self.assertEqual(status, 422)
        self.assertEqual(json.loads(content)["code"], "SCHEMA_INVALID")

    def test_project_writes_require_csrf_and_origin(self):
        for path in ("/api/projects", self.base + "/profile-revisions", self.base + "/state-decisions"):
            for overrides in ({"csrf": None}, {"origin": "null"}):
                status, _, _ = self.call("POST", path, **overrides)
                self.assertEqual(status, 403)

    def test_repeated_denials_do_not_create_customer_projects_or_operations(self):
        for _ in range(2):
            status, _, _ = self.call("POST", "/api/projects")
            self.assertEqual(status, 403)
        result = mysql("""
            SELECT JSON_OBJECT('projects',(SELECT COUNT(*) FROM medical_local.project),
                'profiles',(SELECT COUNT(*) FROM medical_local.project_profile_revision),
                'operations',(SELECT COUNT(*) FROM medical_local.operation_record),
                'events',(SELECT COUNT(*) FROM medical_local.outbox_event))
            """, settings()["root_password"])
        self.assertEqual(json.loads(result.stdout), {name: 0 for name in ("projects", "profiles", "operations", "events")})

    def test_actual_project_governance_foreign_keys_preserve_complete_identity(self):
        result = mysql("""
            SELECT JSON_ARRAYAGG(JSON_OBJECT('constraint_name',CONSTRAINT_NAME,'column_name',COLUMN_NAME,
                'target_table',REFERENCED_TABLE_NAME,'target_column',REFERENCED_COLUMN_NAME,'ordinal',ORDINAL_POSITION))
            FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA='medical_local'
                AND TABLE_NAME IN ('project_record','project_state_decision') AND REFERENCED_TABLE_NAME IS NOT NULL
            """, settings()["root_password"])
        actual = json.loads(result.stdout)
        expected = {
            "fk_project_record_project": ("project", ["tenant_id", "project_id"], ["tenant_id", "project_id"]),
            "fk_project_record_profile": ("project_profile_revision", ["tenant_id", "project_id", "current_profile_revision"], ["tenant_id", "project_id", "profile_revision"]),
            "fk_project_record_responsible": ("tenant_member", ["tenant_id", "responsible_user_id"], ["tenant_id", "user_id"]),
            "fk_project_record_basis": ("version_registry", ["tenant_id", "creation_basis"], ["tenant_id", "version_id"]),
            "fk_project_record_authority": ("permission_grant", ["tenant_id", "authority_grant"], ["tenant_id", "grant_id"]),
            "fk_project_state_project": ("project", ["tenant_id", "project_id"], ["tenant_id", "project_id"]),
            "fk_project_state_profile": ("project_profile_revision", ["tenant_id", "project_id", "profile_revision"], ["tenant_id", "project_id", "profile_revision"]),
            "fk_project_state_operation": ("operation_record", ["tenant_id", "operation_id"], ["tenant_id", "operation_id"]),
            "fk_project_state_authority": ("permission_grant", ["tenant_id", "authority_grant"], ["tenant_id", "grant_id"]),
        }
        self.assertEqual({item["constraint_name"] for item in actual}, set(expected))
        for name, (target, columns, targets) in expected.items():
            entries = sorted((item for item in actual if item["constraint_name"] == name), key=lambda item: item["ordinal"])
            self.assertEqual([item["column_name"] for item in entries], columns)
            self.assertEqual([item["target_column"] for item in entries], targets)
            self.assertTrue(all(item["target_table"] == target for item in entries))
        print("PROJECT_DATABASE_FOREIGN_KEYS_CHECK constraints=9 source=ACTUAL_MYSQL")


if __name__ == "__main__":
    unittest.main(verbosity=2)
