"""Real JSqlParser/SQLGlot checks for additive immutable SIM rule-purpose bindings."""
import unittest
from sqlglot import exp
from test_simulation_input_artifact_migration import RESOURCES, inspect_migration, foreign_keys, assert_registered

MIGRATION = 'db/migration/064-simulation-rule-binding.sql'


class SimulationRuleBindingMigrationTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.sql = (RESOURCES / MIGRATION).read_text()
        cls.parsed, cls.tables = inspect_migration(RESOURCES / MIGRATION)

    def test_registration_additive_only_no_seed_grant_or_rule_rewrite(self):
        assert_registered(self, MIGRATION, 'db/migration/063-simulation-input-artifact.sql', self.tables)
        self.assertEqual({'simulation_rule_binding', 'simulation_rule_approval_binding'}, set(self.tables))
        self.assertTrue(all('create' in item for item in self.parsed))
        self.assertNotRegex(self.sql, r'(?im)^\s*(INSERT|UPDATE|DELETE|REPLACE|ALTER|DROP|TRUNCATE|GRANT|REVOKE)\b')

    def test_immutable_rule_content_and_tenant_identity(self):
        table = self.tables['simulation_rule_binding']
        self.assertEqual(('tenant_id', 'rule_version'), tuple(c.name for c in next(table.find_all(exp.PrimaryKey)).expressions))
        self.assertIn(('rule_version', ('tenant_id', 'rule_version', 'content_sha256'),
                       ('tenant_id', 'rule_version', 'content_sha256')), foreign_keys(table))
        unique = {tuple(c.name for c in u.this.expressions) for u in table.find_all(exp.UniqueColumnConstraint)}
        self.assertIn(('tenant_id', 'rule_version', 'binding_sha256'), unique)
        columns = {c.name for c in table.this.expressions if isinstance(c, exp.ColumnDef)}
        self.assertFalse({'updated_at', 'revision', 'status', 'production_eligible'} & columns)
        for table in self.tables.values():
            for _, local, remote in foreign_keys(table):
                self.assertEqual('tenant_id', local[0])
                self.assertEqual('tenant_id', remote[0])

    def test_purpose_and_closed_json_bound_to_exact_rule_and_false_production(self):
        for token in ("dependency_use='SIMULATION_EVALUATION' AND schema_version='1.0.0'",
                      "JSON_TYPE(binding_json)='OBJECT'", 'JSON_LENGTH(binding_json)=6',
                      "JSON_TYPE(JSON_EXTRACT(binding_json,'$.production_eligible'))='BOOLEAN'",
                      "JSON_EXTRACT(binding_json,'$.production_eligible')=CAST('false' AS JSON)"):
            self.assertIn(token, self.sql)
        for field in ('schema_version', 'tenant_id', 'rule_version', 'content_sha256', 'dependency_use'):
            self.assertIn("JSON_UNQUOTE(JSON_EXTRACT(binding_json,'$." + field + "')) <=> " + field, self.sql)
        for field in ('content_sha256', 'binding_sha256', 'schema_manifest_sha256'):
            self.assertIn(field + " REGEXP '^[a-f0-9]{64}$'", self.sql)
        self.assertIn("JSON_CONTAINS_PATH(binding_json,'all','$.schema_version','$.tenant_id','$.rule_version','$.content_sha256','$.dependency_use','$.production_eligible')", self.sql)

    def test_approval_pins_existing_decision_and_exact_binding_digest(self):
        table = self.tables['simulation_rule_approval_binding']
        self.assertEqual(('tenant_id', 'decision_id'), tuple(c.name for c in next(table.find_all(exp.PrimaryKey)).expressions))
        keys = foreign_keys(table)
        self.assertIn(('rule_approval_decision', ('tenant_id', 'decision_id'), ('tenant_id', 'decision_id')), keys)
        self.assertIn(('simulation_rule_binding', ('tenant_id', 'rule_version', 'binding_sha256'),
                       ('tenant_id', 'rule_version', 'binding_sha256')), keys)
        self.assertIn("binding_sha256 REGEXP '^[a-f0-9]{64}$'", self.sql)


if __name__ == '__main__':
    unittest.main()
