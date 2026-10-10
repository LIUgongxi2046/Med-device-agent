import json
from pathlib import Path
import sys
import unittest


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
sys.path.insert(0, str(ROOT / "tests"))
from local_runtime import mysql, settings
from test_local_identity_runtime import ActualBrowserSession, ORIGIN


class EnterpriseRoleRuntimeChecks(unittest.TestCase):
    def setUp(self):
        self.owner = json.loads((ROOT / ".implementation-work/runtime/owner-credentials.json").read_bytes())
        self.browser = ActualBrowserSession()
        status, _, _ = self.browser.request("POST", "/auth/login", self.owner, ORIGIN, self.browser.csrf())
        self.assertEqual(status, 302)
        status, _, content = self.browser.request("GET", "/api/session", schema="1.0.0")
        self.assertEqual(status, 200)
        self.session = json.loads(content)["data"]
        self.assertIsNone(self.session["current_context"])
        self.assertEqual(self.session["memberships"], [])
        self.role_id = self.session["user"]["user_id"]
        self.base = "/api/enterprise-roles/" + self.role_id + "/versions"

    def tearDown(self):
        status, _, _ = self.browser.request("POST", "/auth/logout", origin=ORIGIN, csrf=self.browser.csrf())
        self.assertEqual(status, 204)

    def call(self, method, path, **overrides):
        options = {"schema": "1.0.0", "context": "0"}
        if method == "POST":
            options.update(json_body={}, origin=ORIGIN, csrf=self.session["csrf"], key=self.role_id)
        options.update(overrides)
        return self.browser.request(method, path, **options)

    def test_current_platform_identity_cannot_read_or_change_enterprise_roles(self):
        for method, path in (("GET", "/api/enterprise-roles"), ("GET", self.base + "/" + self.role_id),
                             ("POST", self.base), ("POST", self.base + "/" + self.role_id + "/revocations")):
            status, _, content = self.call(method, path)
            self.assertEqual(status, 403)
            result = json.loads(content)
            self.assertEqual(result["code"], "ACCESS_DENIED")
            self.assertEqual(result["allowed_actions"], [])
            self.assertIsNone(result["resource_revision"])

    def test_missing_schema_and_context_headers_are_rejected(self):
        for method, path in (("GET", "/api/enterprise-roles"), ("POST", self.base)):
            status, _, content = self.call(method, path, schema=None)
            self.assertEqual(status, 422)
            self.assertEqual(json.loads(content)["code"], "SCHEMA_UNSUPPORTED")
            for context in (None, "-1", "18446744073709551616"):
                status, _, content = self.call(method, path, context=context)
                self.assertEqual(status, 422)
                self.assertEqual(json.loads(content)["code"], "SCHEMA_INVALID")

    def test_query_and_path_inputs_remain_closed(self):
        for path in ("/api/enterprise-roles?unknown=1", "/api/enterprise-roles?limit=0",
                     "/api/enterprise-roles?limit=101", "/api/enterprise-roles?limit=1&limit=2",
                     "/api/enterprise-roles?after=unapproved", self.base.replace(self.role_id, self.role_id.upper())):
            status, _, content = self.call("POST" if "/versions" in path else "GET", path)
            self.assertEqual(status, 422)
            self.assertEqual(json.loads(content)["code"], "SCHEMA_INVALID")

    def test_role_writes_require_idempotency_csrf_and_origin(self):
        for overrides in ({"key": None}, {"key": ""}):
            status, _, content = self.call("POST", self.base, **overrides)
            self.assertEqual(status, 422)
            self.assertEqual(json.loads(content)["code"], "SCHEMA_INVALID")
        for overrides in ({"csrf": None}, {"origin": "null"}):
            status, _, _ = self.call("POST", self.base, **overrides)
            self.assertEqual(status, 403)

    def test_repeated_denials_preserve_current_database(self):
        for _ in range(2):
            status, _, _ = self.call("POST", self.base)
            self.assertEqual(status, 403)
        actual = json.loads(mysql("""
            SELECT JSON_OBJECT('tenants',(SELECT COUNT(*) FROM medical_local.tenant),
                'roles',(SELECT COUNT(*) FROM medical_local.enterprise_role_version),
                'heads',(SELECT COUNT(*) FROM medical_local.enterprise_role_head),
                'publications',(SELECT COUNT(*) FROM medical_local.enterprise_role_publication),
                'origins',(SELECT COUNT(*) FROM medical_local.member_role_grant),
                'invitations',(SELECT COUNT(*) FROM medical_local.member_invitation),
                'grants',(SELECT COUNT(*) FROM medical_local.permission_grant),
                'operations',(SELECT COUNT(*) FROM medical_local.operation_record))
            """, settings()["root_password"]).stdout.strip())
        self.assertEqual(actual, {name: 0 for name in ("tenants", "roles", "heads", "publications", "origins", "invitations", "grants", "operations")})

    def test_actual_role_tables_keep_complete_enterprise_foreign_keys(self):
        result = mysql("""
            SELECT JSON_ARRAYAGG(JSON_OBJECT('table_name',TABLE_NAME,'constraint_name',CONSTRAINT_NAME,
                'column_name',COLUMN_NAME,'target_table',REFERENCED_TABLE_NAME,'target_column',REFERENCED_COLUMN_NAME,
                'ordinal',ORDINAL_POSITION))
            FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA='medical_local'
                AND TABLE_NAME IN ('enterprise_role_head','enterprise_role_publication','member_role_grant')
                AND REFERENCED_TABLE_NAME IS NOT NULL
            """, settings()["root_password"])
        actual = json.loads(result.stdout)
        expected = {
            "fk_role_head_tenant": ("tenant", ["tenant_id"], ["tenant_id"]),
            "fk_role_head_version": ("enterprise_role_version", ["tenant_id", "current_version"], ["tenant_id", "version_id"]),
            "fk_role_publication_tenant": ("tenant", ["tenant_id"], ["tenant_id"]),
            "fk_role_publication_version": ("enterprise_role_version", ["tenant_id", "version_id"], ["tenant_id", "version_id"]),
            "fk_role_publication_basis": ("version_registry", ["tenant_id", "approval_basis"], ["tenant_id", "version_id"]),
            "fk_role_publication_authority": ("permission_grant", ["tenant_id", "authority_grant"], ["tenant_id", "grant_id"]),
            "fk_role_grant_tenant": ("tenant", ["tenant_id"], ["tenant_id"]),
            "fk_role_grant_permission": ("permission_grant", ["tenant_id", "grant_id"], ["tenant_id", "grant_id"]),
            "fk_role_grant_version": ("enterprise_role_version", ["tenant_id", "role_version"], ["tenant_id", "version_id"]),
            "fk_role_grant_invitation": ("member_invitation", ["tenant_id", "invitation_id"], ["tenant_id", "invitation_id"]),
        }
        self.assertEqual({item["constraint_name"] for item in actual}, set(expected))
        for name, (target, columns, targets) in expected.items():
            entries = sorted((item for item in actual if item["constraint_name"] == name), key=lambda item: item["ordinal"])
            self.assertEqual([item["column_name"] for item in entries], columns)
            self.assertEqual([item["target_column"] for item in entries], targets)
            self.assertTrue(all(item["target_table"] == target for item in entries))
        print("ROLE_DATABASE_FOREIGN_KEYS_CHECK constraints=10 source=ACTUAL_MYSQL")


if __name__ == "__main__":
    unittest.main(verbosity=2)
