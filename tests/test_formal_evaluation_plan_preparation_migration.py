"""Actual MySQL SQLGlot and JSQLParser shape checks. Never executes a database migration."""
import json
import os
import re
import sys
import unittest
from pathlib import Path

ROOT = Path(os.environ.get('MEDICAL_PROJECT_ROOT', Path(__file__).resolve().parents[1])).resolve()
if not (ROOT / 'contracts/catalogue.json').is_file():
    raise RuntimeError('MEDICAL_PROJECT_ROOT must identify the repository')
RESOURCES = ROOT / 'business/medical-data/src/main/resources'
# A stage override changes only these three known files; repository schema helpers
# and original parent migrations continue to come from MEDICAL_PROJECT_ROOT.
STAGE = Path(os.environ['MEDICAL_PLAN_STAGE']).resolve() if 'MEDICAL_PLAN_STAGE' in os.environ else None
DDL = (STAGE / 'sql/090-formal-evaluation-plan-preparation.sql' if STAGE else
       RESOURCES / 'db/migration/090-formal-evaluation-plan-preparation.sql')
SQL_FIXTURE = (STAGE / 'actual-sql.json' if STAGE else ROOT /
               'business/medical-persistence/src/test/resources/formal-evaluation-plan-preparation/actual-sql.json')
MAPPER_XML = (STAGE / 'shared/ArtifactRegistryMapper.xml' if STAGE else ROOT /
              'business/medical-persistence/src/main/resources/medical-mappers/ArtifactRegistryMapper.xml')
for required_path in (DDL, SQL_FIXTURE, MAPPER_XML):
    if not required_path.is_file():
        raise RuntimeError('Required migration input is missing: ' + str(required_path))
sys.path.insert(0, str(ROOT / 'tests'))
import sqlglot
from sqlglot import exp
from migration_source_parser import parse_schema_source
from test_migration_bundle import sqlglot_migration_source
from test_simulation_input_artifact_migration import inspect_migration, foreign_keys

EXPECTED = {'formal_evaluation_plan_preparation_intent', 'formal_evaluation_plan_preparation_attempt',
            'formal_evaluation_plan_preparation_registered'}

class Migration090(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.parsed, cls.tables = inspect_migration(DDL)
        cls.all_tables, cls.keys, cls.columns = {}, {}, {}
        paths = [ROOT / 'data-design/core.sql'] + [
            path for path in sorted((RESOURCES / 'db/migration').glob('*.sql')) if path.name != DDL.name
        ] + [DDL]
        paths = list(dict.fromkeys(path.resolve() for path in paths))
        if sum(path.name == DDL.name for path in paths) != 1:
            raise AssertionError('090 must be parsed exactly once, including after integration')
        for path in paths:
            for statement in parse_schema_source(path.read_text())[0]:
                if isinstance(statement, exp.Create) and isinstance(statement.this, exp.Schema):
                    name = statement.this.this.name
                    cls.all_tables[name] = statement
                    cls.keys[name] = {tuple(c.name for c in k.expressions) for k in statement.find_all(exp.PrimaryKey)} | {
                        tuple(c.name for c in k.this.expressions) for k in statement.find_all(exp.UniqueColumnConstraint)}
                    cls.columns[name] = {c.name: c for c in statement.this.expressions if isinstance(c, exp.ColumnDef)}
                elif isinstance(statement, exp.Alter):
                    name = statement.this.name
                    cls.keys.setdefault(name, set()).update(tuple(c.name for c in k.this.expressions) for k in statement.find_all(exp.UniqueColumnConstraint))
                    cls.columns.setdefault(name, {}).update({c.name: c for c in statement.find_all(exp.ColumnDef)})

    def test_three_append_only_tables_without_seeds_or_shared_mutations(self):
        self.assertEqual(EXPECTED, set(self.tables))
        self.assertEqual(3, len(self.parsed))
        self.assertTrue(all(set(s) == {'create'} for s in self.parsed))
        self.assertNotRegex(DDL.read_text(), r'(?im)^\s*(ALTER|INSERT|UPDATE|DELETE|DROP|GRANT|REVOKE|REPLACE)\b')

    def test_native_and_mysql_parsers_agree_without_fallback_commands(self):
        source = '\n'.join(line for line in sqlglot_migration_source(DDL).splitlines()
                           if not line.lstrip().startswith('--'))
        mysql = sqlglot.parse(source, read='mysql')
        self.assertEqual(3, len(mysql))
        self.assertTrue(all(isinstance(s, exp.Create) and isinstance(s.this, exp.Schema) for s in mysql))
        self.assertEqual(EXPECTED, {s.this.this.name for s in mysql})
        for table in mysql:
            native = self.tables[table.this.this.name]
            self.assertEqual(foreign_keys(table), foreign_keys(native))
            self.assertEqual({c.name for c in table.this.expressions if isinstance(c, exp.ColumnDef)},
                             {c.name for c in native.this.expressions if isinstance(c, exp.ColumnDef)})

    def test_all_parent_keys_exist_and_have_exact_column_types(self):
        for name, table in self.tables.items():
            for target, local, remote in foreign_keys(table):
                self.assertEqual('tenant_id', local[0], (name, target))
                self.assertEqual('tenant_id', remote[0], (name, target))
                self.assertIn(remote, self.keys[target], (name, target, remote))
                self.assertEqual(len(local), len(remote))
                for left, right in zip(local, remote):
                    self.assertEqual(self.columns[name][left].kind, self.columns[target][right].kind,
                                     (name, target, left, right))

    def test_every_table_has_own_nonnullable_standard_metadata(self):
        fields = {'tenant_id', 'project_id', 'created_by', 'created_at', 'schema_version', 'schema_manifest_sha256', 'classification'}
        for name in EXPECTED:
            self.assertLessEqual(fields, set(self.columns[name]))
            for field in fields:
                self.assertTrue(any(isinstance(c.kind, exp.NotNullColumnConstraint) for c in self.columns[name][field].constraints), (name, field))

    def test_original_key_node_operation_and_attempt_are_uniquely_bound(self):
        keys = self.keys['formal_evaluation_plan_preparation_intent']
        for expected in [('tenant_id', 'created_by', 'idempotency_key'), ('tenant_id', 'artifact_id'),
                         ('tenant_id', 'execution_id', 'sample_id', 'repeat_index', 'node_kind'),
                         ('tenant_id', 'operation_id')]:
            self.assertIn(expected, keys)
        self.assertIn(('tenant_id', 'write_id'), self.keys['formal_evaluation_plan_preparation_attempt'])
        self.assertIn(('tenant_id', 'write_id'), self.keys['formal_evaluation_plan_preparation_registered'])

    def test_no_task_or_model_verification_ownership_is_borrowed(self):
        text = DDL.read_text()
        for name in ['task_run', 'formal_model_verification_job', 'system_job_run']:
            self.assertNotIn('REFERENCES ' + name, text)
        self.assertIn('REFERENCES formal_evaluation_execution(', text)
        self.assertIn('REFERENCES formal_evaluation_execution_context(', text)
        self.assertIn("node_kind='N01'", text)

    def test_all_actual_executed_insert_columns_exist_in_complete_migrations(self):
        statements = json.loads(SQL_FIXTURE.read_text())
        checked = set()
        for raw in statements:
            tree = sqlglot.parse_one(raw, read='mysql')
            if isinstance(tree, exp.Insert):
                table = tree.this.this.name
                for column in tree.this.expressions:
                    self.assertIn(column.name, self.columns[table], (table, column.name, raw))
                self.assertEqual(len(tree.this.expressions), len(tree.expression.expressions[0].expressions), raw)
                checked.add(table)
        self.assertLessEqual(EXPECTED | {'artifact', 'artifact_content_binding', 'version_registry', 'dependency_access_set', 'dependency_access_manifest', 'dependency_access_member', 'lineage_edge', 'operation_record', 'audit_event', 'outbox_event'}, checked)

    def test_actual_new_table_inserts_cover_all_required_columns(self):
        statements = json.loads(SQL_FIXTURE.read_text())
        covered = set()
        for raw in statements:
            tree = sqlglot.parse_one(raw, read='mysql')
            if isinstance(tree, exp.Insert) and tree.this.this.name in EXPECTED:
                table = tree.this.this.name
                self.assertEqual(set(self.columns[table]), {column.name for column in tree.this.expressions}, raw)
                covered.add(table)
        self.assertEqual(EXPECTED, covered)

    def test_actual_select_update_columns_and_tenant_predicates_match_migrations(self):
        statements = json.loads(SQL_FIXTURE.read_text())
        for raw in statements:
            tree = sqlglot.parse_one(raw, read='mysql')
            tables = {table.name for table in tree.find_all(exp.Table)}
            if not tables:
                continue
            allowed = set().union(*(set(self.columns[table]) for table in tables))
            for column in tree.find_all(exp.Column):
                if column.name != '*':
                    self.assertIn(column.name, allowed, (column.name, raw))
            if isinstance(tree, (exp.Select, exp.Update)):
                self.assertRegex(raw, r'(?i)WHERE tenant_id=\?', raw)

    def test_actual_sql_never_updates_or_deletes_preparation_history(self):
        statements = json.loads(SQL_FIXTURE.read_text())
        for raw in statements:
            tree = sqlglot.parse_one(raw, read='mysql')
            self.assertIsInstance(tree, (exp.Select, exp.Insert, exp.Update), raw)
            if isinstance(tree, exp.Update):
                self.assertEqual('operation_record', tree.this.name, raw)
                self.assertIn('revision=', raw)

    def test_original_context_source_hash_and_registration_identity_are_foreign_keys(self):
        intent = foreign_keys(self.tables['formal_evaluation_plan_preparation_intent'])
        self.assertLessEqual({
            ('formal_evaluation_execution', ('tenant_id','project_id','execution_id'),
             ('tenant_id','project_id','execution_id')),
            ('formal_evaluation_execution_context', ('tenant_id','execution_id'), ('tenant_id','execution_id')),
            ('formal_evaluation_context_version', ('tenant_id','context_version','context_sha256'),
             ('tenant_id','version_id','content_sha256')),
            ('version_registry', ('tenant_id','source_input_version','source_input_sha256'),
             ('tenant_id','version_id','content_sha256')),
        }, intent)
        registered = foreign_keys(self.tables['formal_evaluation_plan_preparation_registered'])
        self.assertLessEqual({
            ('formal_evaluation_plan_preparation_intent', ('tenant_id','write_id','operation_id','artifact_id'),
             ('tenant_id','write_id','operation_id','artifact_id')),
            ('formal_evaluation_plan_preparation_attempt', ('tenant_id','project_id','write_id','attempt_id'),
             ('tenant_id','project_id','write_id','attempt_id')),
            ('version_registry', ('tenant_id','artifact_id','payload_sha256'), ('tenant_id','version_id','content_sha256')),
        }, registered)

    def test_original_payload_and_intent_are_nonnullable_and_json_checked(self):
        for field in ('canonical_intent', 'intent_sha256', 'canonical_payload', 'payload_sha256',
                      'request_sha256', 'context_version', 'context_sha256', 'source_input_version', 'source_input_sha256'):
            column = self.columns['formal_evaluation_plan_preparation_intent'][field]
            self.assertTrue(any(isinstance(c.kind, exp.NotNullColumnConstraint) for c in column.constraints), field)
        self.assertIn('JSON_VALID(canonical_intent) AND JSON_VALID(canonical_payload)', DDL.read_text())
        self.assertIn('JSON_VALID(receipt_json)', DDL.read_text())

    def test_actual_ownership_mapper_uses_exact_indexed_key(self):
        xml = MAPPER_XML.read_text()
        self.assertIn('SELECT write_id FROM formal_evaluation_plan_preparation_intent i WHERE artifact_id=#{id} LIMIT 2', xml)
        self.assertIn(('tenant_id', 'artifact_id'), self.keys['formal_evaluation_plan_preparation_intent'])

if __name__ == '__main__':
    unittest.main(verbosity=2)
