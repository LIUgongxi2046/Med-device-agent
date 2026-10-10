import json
from pathlib import Path
import sys
import unittest


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
from local_runtime import mysql, settings


class MemberInvitationRuntimeChecks(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        actual = json.loads(mysql("SELECT JSON_OBJECT('version', VERSION(), 'database', 'medical_local', 'server_uuid', @@server_uuid)",
                                  settings()["root_password"]).stdout.strip())
        (ROOT / ".implementation-work/runtime/member-invitation-receipt.json").write_text(
            json.dumps({"scope": "ACTUAL_MEMBER_INVITATION_DATABASE_METADATA", "database": actual}), encoding="utf-8")

    def query(self, statement):
        return json.loads(mysql(statement, settings()["root_password"]).stdout.strip())

    def test_actual_database_matches_registered_identity(self):
        actual = self.query("SELECT JSON_OBJECT('version', VERSION(), 'database', 'medical_local', 'server_uuid', @@server_uuid)")
        self.assertEqual(actual, json.loads((ROOT / ".implementation-work/runtime/service-identity.json").read_bytes()))

    def test_role_version_retains_approval_definition_validity_and_enterprise_relations(self):
        fields = self.query("""
            SELECT COALESCE(JSON_OBJECTAGG(COLUMN_NAME, COLUMN_TYPE),JSON_OBJECT()) FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA='medical_local' AND TABLE_NAME='enterprise_role_version'
            """)
        self.assertEqual(set(fields), {"tenant_id", "version_id", "role_id", "definition", "status", "approved_by", "approved_at",
                                      "valid_from", "valid_to", "schema_version", "schema_manifest_sha256", "created_by", "created_at",
                                      "classification", "revision", "updated_at"})
        self.assertEqual(fields["definition"], "json")
        checks = self.query("""
            SELECT COALESCE(JSON_OBJECTAGG(CONSTRAINT_NAME,ENFORCED),JSON_OBJECT()) FROM information_schema.TABLE_CONSTRAINTS
            WHERE TABLE_SCHEMA='medical_local' AND TABLE_NAME='enterprise_role_version' AND CONSTRAINT_TYPE='CHECK'
            """)
        self.assertEqual(checks, {"ck_role_status": "YES", "ck_role_definition": "YES", "ck_role_validity": "YES",
                                 "ck_role_approval": "YES", "ck_role_revision": "YES"})
        keys = self.query("""
            SELECT COALESCE(JSON_ARRAYAGG(JSON_OBJECT('name',CONSTRAINT_NAME,'column',COLUMN_NAME,'target',REFERENCED_TABLE_NAME,
                'ordinal',ORDINAL_POSITION)),JSON_ARRAY()) FROM information_schema.KEY_COLUMN_USAGE
            WHERE TABLE_SCHEMA='medical_local' AND TABLE_NAME='enterprise_role_version' AND REFERENCED_TABLE_NAME IS NOT NULL
            """)
        expected = {"fk_role_tenant": ("tenant", ["tenant_id"]),
                    "fk_role_version": ("version_registry", ["tenant_id", "version_id"]),
                    "fk_role_approver": ("tenant_member", ["tenant_id", "approved_by"])}
        for name, (table, columns) in expected.items():
            actual = sorted((item for item in keys if item["name"] == name), key=lambda item: item["ordinal"])
            self.assertEqual([item["column"] for item in actual], columns)
            self.assertTrue(all(item["target"] == table for item in actual))
        path = ROOT / ".implementation-work/runtime/member-invitation-receipt.json"
        receipt = json.loads(path.read_bytes())
        receipt["role_version"] = {"fields": fields, "checks": checks, "keys": keys}
        path.write_text(json.dumps(receipt), encoding="utf-8")

    def test_invitation_retains_identity_authorization_expiry_and_secret_digest(self):
        columns = self.query("""
            SELECT COALESCE(JSON_OBJECTAGG(COLUMN_NAME, JSON_OBJECT('nullable', IS_NULLABLE,
                'type', COLUMN_TYPE, 'collation', COLLATION_NAME, 'generated', EXTRA)), JSON_OBJECT())
              FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA='medical_local' AND TABLE_NAME='member_invitation'
            """)
        required = {"tenant_id", "invitation_id", "user_id", "identity_id", "roles", "scope",
                    "authorization_manifest", "basis_version", "credential_sha256", "expires_at", "status",
                    "accepted_by", "accepted_at", "terminal_at", "terminal_reason", "pending_user_id",
                    "schema_version", "schema_manifest_sha256", "created_by", "created_at", "classification",
                    "revision", "updated_at"}
        self.assertEqual(set(columns), required, "邀请必须保存完整身份、授权、期限和结果")
        for name in required - {"accepted_by", "accepted_at", "terminal_at", "terminal_reason", "pending_user_id"}:
            self.assertEqual(columns[name]["nullable"], "NO", name)
        for name in ("roles", "scope", "authorization_manifest"):
            self.assertEqual(columns[name]["type"], "json")
        self.assertEqual(columns["credential_sha256"]["collation"], "ascii_bin")
        self.assertIn("STORED GENERATED", columns["pending_user_id"]["generated"])
        self.assertEqual(columns["revision"]["type"], "bigint unsigned")
        path = ROOT / ".implementation-work/runtime/member-invitation-receipt.json"
        receipt = json.loads(path.read_bytes())
        receipt["columns"] = columns
        path.write_text(json.dumps(receipt), encoding="utf-8")

    def test_invitation_binds_enterprise_member_identity_and_immutable_basis(self):
        keys = self.query("""
            SELECT COALESCE(JSON_ARRAYAGG(JSON_OBJECT('name', CONSTRAINT_NAME, 'column', COLUMN_NAME,
                'target', REFERENCED_TABLE_NAME, 'target_column', REFERENCED_COLUMN_NAME)), JSON_ARRAY())
              FROM information_schema.KEY_COLUMN_USAGE
             WHERE TABLE_SCHEMA='medical_local' AND TABLE_NAME='member_invitation'
            """)
        self.assertIsInstance(keys, list, "邀请关联必须在真实数据库建立")
        expected = {"fk_invitation_tenant": ("tenant", ["tenant_id"]),
                    "fk_invitation_identity": ("identity_binding", ["tenant_id", "identity_id"]),
                    "fk_invitation_member": ("tenant_member", ["tenant_id", "user_id"]),
                    "fk_invitation_basis": ("version_registry", ["tenant_id", "basis_version"]),
                    "fk_invitation_acceptor": ("tenant_member", ["tenant_id", "accepted_by"])}
        for name, (target, columns) in expected.items():
            actual = [entry for entry in keys if entry["name"] == name]
            self.assertEqual([entry["column"] for entry in actual], columns)
            self.assertTrue(all(entry["target"] == target for entry in actual))
        self.assertEqual([entry["column"] for entry in keys if entry["name"] == "uq_invitation_pending"],
                         ["tenant_id", "pending_user_id"])
        self.assertEqual([entry["column"] for entry in keys if entry["name"] == "uq_invitation_subject"],
                         ["tenant_id", "invitation_id", "user_id"])
        path = ROOT / ".implementation-work/runtime/member-invitation-receipt.json"
        receipt = json.loads(path.read_bytes())
        receipt["keys"] = keys
        path.write_text(json.dumps(receipt), encoding="utf-8")

    def test_database_enforces_pending_accepted_revoked_and_expired_results(self):
        checks = self.query("""
            SELECT COALESCE(JSON_OBJECTAGG(c.CONSTRAINT_NAME, JSON_OBJECT('clause', c.CHECK_CLAUSE,
                'enforced', t.ENFORCED)), JSON_OBJECT())
              FROM information_schema.CHECK_CONSTRAINTS c JOIN information_schema.TABLE_CONSTRAINTS t
                ON c.CONSTRAINT_SCHEMA=t.CONSTRAINT_SCHEMA AND c.CONSTRAINT_NAME=t.CONSTRAINT_NAME
             WHERE t.TABLE_SCHEMA='medical_local' AND t.TABLE_NAME='member_invitation'
            """)
        expected = {"ck_invitation_status", "ck_invitation_expiry", "ck_invitation_roles", "ck_invitation_scope",
                    "ck_invitation_authorizations", "ck_invitation_credential", "ck_invitation_result", "ck_invitation_revision"}
        self.assertEqual(set(checks), expected)
        self.assertTrue(all(entry["enforced"] == "YES" for entry in checks.values()))
        for name in ("accepted_by", "accepted_at", "terminal_at", "terminal_reason", "user_id", "expires_at"):
            self.assertIn(name, checks["ck_invitation_result"]["clause"])
        path = ROOT / ".implementation-work/runtime/member-invitation-receipt.json"
        receipt = json.loads(path.read_bytes())
        receipt["checks"] = checks
        path.write_text(json.dumps(receipt), encoding="utf-8")

    def test_acceptance_route_preserves_complete_target_subject_association(self):
        route = self.query("""
            SELECT COALESCE(JSON_ARRAYAGG(JSON_OBJECT('column', COLUMN_NAME, 'target', REFERENCED_TABLE_NAME,
                'target_column', REFERENCED_COLUMN_NAME, 'ordinal', ORDINAL_POSITION)), JSON_ARRAY())
              FROM information_schema.KEY_COLUMN_USAGE
             WHERE TABLE_SCHEMA='medical_local' AND TABLE_NAME='platform_invitation_route'
               AND CONSTRAINT_NAME='fk_invitation_route'
            """)
        route = sorted(route, key=lambda entry: entry["ordinal"])
        self.assertEqual(route, [{"column": "tenant_id", "target": "member_invitation", "target_column": "tenant_id", "ordinal": 1},
                                 {"column": "invitation_id", "target": "member_invitation", "target_column": "invitation_id", "ordinal": 2},
                                 {"column": "user_id", "target": "member_invitation", "target_column": "user_id", "ordinal": 3}])
        path = ROOT / ".implementation-work/runtime/member-invitation-receipt.json"
        receipt = json.loads(path.read_bytes())
        receipt["route"] = route
        path.write_text(json.dumps(receipt), encoding="utf-8")

    def test_migration_does_not_create_customer_business_records(self):
        counts = self.query("""
            SELECT JSON_OBJECT('tenants', (SELECT COUNT(*) FROM medical_local.tenant),
                'members', (SELECT COUNT(*) FROM medical_local.tenant_member),
                'bindings', (SELECT COUNT(*) FROM medical_local.identity_binding),
                'invitations', (SELECT COUNT(*) FROM medical_local.member_invitation),
                'routes', (SELECT COUNT(*) FROM medical_local.platform_invitation_route),
                'roles', (SELECT COUNT(*) FROM medical_local.enterprise_role_version))
            """)
        self.assertEqual(counts, {"tenants": 0, "members": 0, "bindings": 0, "invitations": 0, "routes": 0, "roles": 0})
        path = ROOT / ".implementation-work/runtime/member-invitation-receipt.json"
        receipt = json.loads(path.read_bytes())
        receipt["business_records"] = counts
        path.write_text(json.dumps(receipt), encoding="utf-8")


if __name__ == "__main__":
    unittest.main(verbosity=2)
