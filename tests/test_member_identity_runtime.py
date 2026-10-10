import json
from pathlib import Path
import sys
import unittest


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
from local_runtime import mysql, settings


class MemberIdentityRuntimeChecks(unittest.TestCase):
    def query(self, statement):
        return json.loads(mysql(statement, settings()["root_password"]).stdout.strip())

    def test_actual_database_matches_registered_service_identity(self):
        actual = self.query("SELECT JSON_OBJECT('version', VERSION(), 'database', 'medical_local', 'server_uuid', @@server_uuid)")
        expected = json.loads((ROOT / ".implementation-work/runtime/service-identity.json").read_bytes())
        self.assertEqual(actual, expected)

    def test_enterprise_identity_can_store_explicit_local_binding(self):
        columns = self.query("""
            SELECT JSON_OBJECTAGG(COLUMN_NAME, JSON_OBJECT('nullable', IS_NULLABLE,
                'collation', COLLATION_NAME, 'default', COLUMN_DEFAULT))
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA='medical_local' AND TABLE_NAME='identity_binding'
            """)
        self.assertIn("mode", columns, "企业身份必须保存明确的认证方式")
        self.assertIn("local_user_id", columns, "本地身份必须关联真实本地账户")
        self.assertEqual(columns["mode"]["nullable"], "NO")
        self.assertIsNone(columns["mode"]["default"])
        self.assertEqual(columns["issuer"]["nullable"], "YES")
        self.assertEqual(columns["subject"]["nullable"], "YES")
        self.assertEqual(columns["subject"]["collation"], "utf8mb4_0900_bin")
        (ROOT / ".implementation-work/runtime/member-identity-receipt.json").write_text(
            json.dumps({"scope": "ACTUAL_MEMBER_IDENTITY_DATABASE_METADATA", "columns": columns}), encoding="utf-8")

    def test_local_binding_foreign_key_and_uniqueness_are_enforced(self):
        constraints = self.query("""
            SELECT JSON_ARRAYAGG(JSON_OBJECT('name', CONSTRAINT_NAME, 'column', COLUMN_NAME,
                'target', REFERENCED_TABLE_NAME, 'target_column', REFERENCED_COLUMN_NAME))
            FROM information_schema.KEY_COLUMN_USAGE
            WHERE TABLE_SCHEMA='medical_local' AND TABLE_NAME='identity_binding'
            """)
        self.assertIn({"name": "fk_identity_local_account", "column": "local_user_id",
                       "target": "platform_local_identity_metadata", "target_column": "user_id"}, constraints)
        unique_columns = [item["column"] for item in constraints if item["name"] == "uq_identity_local_account"]
        self.assertEqual(set(unique_columns), {"tenant_id", "local_user_id"})
        checks = self.query("""
            SELECT JSON_OBJECTAGG(c.CONSTRAINT_NAME, JSON_OBJECT('clause', c.CHECK_CLAUSE, 'enforced', t.ENFORCED))
            FROM information_schema.CHECK_CONSTRAINTS c JOIN information_schema.TABLE_CONSTRAINTS t
              ON c.CONSTRAINT_SCHEMA=t.CONSTRAINT_SCHEMA AND c.CONSTRAINT_NAME=t.CONSTRAINT_NAME
            WHERE t.TABLE_SCHEMA='medical_local' AND t.TABLE_NAME='identity_binding'
            """)
        self.assertIn("ck_identity_binding_mode", checks)
        self.assertEqual(checks["ck_identity_binding_mode"]["enforced"], "YES")
        for field in ("mode", "user_id", "local_user_id", "issuer", "subject"):
            self.assertIn(field, checks["ck_identity_binding_mode"]["clause"])
        path = ROOT / ".implementation-work/runtime/member-identity-receipt.json"
        receipt = json.loads(path.read_bytes())
        receipt.update({"constraints": constraints, "checks": checks})
        path.write_text(json.dumps(receipt), encoding="utf-8")

    def test_migration_does_not_create_enterprises_or_member_identities(self):
        counts = self.query("""
            SELECT JSON_OBJECT('tenants', (SELECT COUNT(*) FROM medical_local.tenant),
                'members', (SELECT COUNT(*) FROM medical_local.tenant_member),
                'bindings', (SELECT COUNT(*) FROM medical_local.identity_binding))
            """)
        self.assertEqual(counts, {"tenants": 0, "members": 0, "bindings": 0})


if __name__ == "__main__":
    unittest.main(verbosity=2)
