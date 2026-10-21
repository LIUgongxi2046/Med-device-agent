"""Actual MySQL SQLGlot and JSQLParser shape checks. Never executes a database migration."""
import hashlib
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
FIXTURES = ROOT / 'tests/fixtures/evaluation-exact-input-object'
SQL_FIXTURE = FIXTURES / 'actual-sql.json'
sys.path.insert(0, str(ROOT / 'tests'))
import sqlglot
from sqlglot import exp
from migration_source_parser import parse_schema_source
from test_migration_bundle import sqlglot_migration_source
from test_simulation_input_artifact_migration import inspect_migration, foreign_keys

DDL = RESOURCES / 'db/migration/089-formal-evaluation-exact-input-object.sql'
EXPECTED = {'formal_evaluation_exact_input_intent', 'formal_evaluation_exact_input_attempt',
            'formal_evaluation_exact_input_registered'}

class Migration089(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.parsed, cls.tables = inspect_migration(DDL)
        cls.all_tables, cls.keys, cls.columns = {}, {}, {}
        paths = [ROOT / 'data-design/core.sql'] + sorted((RESOURCES / 'db/migration').glob('*.sql'))
        if paths.count(DDL) != 1:
            raise AssertionError('089 must occur once in the real migration bundle')
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

    def test_migration_is_registered_once_after_088(self):
        from xml.etree import ElementTree as ET
        entries = ET.parse(RESOURCES / 'db/changelog/master.xml').findall(
            '{http://www.liquibase.org/xml/ns/dbchangelog}include')
        files = [entry.attrib['file'] for entry in entries]
        name = 'db/migration/' + DDL.name
        self.assertEqual(1, files.count(name))
        self.assertEqual('db/migration/088-formal-evaluation-context.sql', files[files.index(name) - 1])
        coverage = json.loads((RESOURCES / 'db/migration/coverage.json').read_text())
        self.assertEqual(1, coverage['native_sql_migrations'].count(name))
        self.assertEqual(EXPECTED, set(coverage['required_tables']['formal_evaluation_exact_input_object']))

    def test_checked_in_sql_fixture_matches_recorded_actual_producer_sources(self):
        provenance = json.loads((FIXTURES / 'provenance.json').read_text())
        self.assertEqual('evaluation-exact-input-sql-fixture-provenance-v1', provenance['format'])
        self.assertEqual('tests/fixtures/evaluation-exact-input-object/actual-sql.json', provenance['fixture_path'])
        self.assertEqual('com.ruoyi.medical.persistence.FormalEvaluationExactInputObjectRegistrationTest'
                         '#actualEmittedSqlParsesAndCanBeCheckedAgainstTheCompleteMigrationColumns',
                         provenance['producer_test'])
        self.assertEqual('medical.exactInputSqlEvidence', provenance['system_property'])
        self.assertEqual(provenance['fixture_sha256'], hashlib.sha256(SQL_FIXTURE.read_bytes()).hexdigest())
        for source in provenance['sources']:
            self.assertEqual(source['sha256'], hashlib.sha256((ROOT / source['path']).read_bytes()).hexdigest(),
                             source['path'])
        # CI may require parity with SQL freshly emitted by the real Java test. An explicitly
        # supplied missing/mismatching result fails; it never falls back to the checked-in fixture.
        runtime = os.environ.get('MEDICAL_EVAL_EXACT_INPUT_SQL_EVIDENCE')
        if runtime is not None:
            current = Path(runtime)
            self.assertTrue(current.is_file(), 'Fresh Java SQL evidence is required at ' + runtime)
            self.assertEqual(provenance['fixture_sha256'], hashlib.sha256(current.read_bytes()).hexdigest())

    def test_three_append_only_tables_without_seeds_or_shared_mutations(self):
        self.assertEqual(EXPECTED, set(self.tables))
        self.assertEqual(3, len(self.parsed))
        self.assertTrue(all(set(s) == {'create'} for s in self.parsed))
        self.assertNotRegex(DDL.read_text(), r'(?im)^\s*(ALTER|INSERT|UPDATE|DELETE|DROP|GRANT|REVOKE|REPLACE)\b')

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
        keys = self.keys['formal_evaluation_exact_input_intent']
        for expected in [('tenant_id', 'created_by', 'idempotency_key'), ('tenant_id', 'artifact_id'),
                         ('tenant_id', 'execution_id', 'sample_id', 'repeat_index', 'node_kind'),
                         ('tenant_id', 'operation_id')]:
            self.assertIn(expected, keys)
        self.assertIn(('tenant_id', 'write_id'), self.keys['formal_evaluation_exact_input_attempt'])
        self.assertIn(('tenant_id', 'write_id'), self.keys['formal_evaluation_exact_input_registered'])

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
        self.assertLessEqual(EXPECTED | {'artifact', 'dependency_access_member', 'operation_record', 'audit_event', 'outbox_event'}, checked)

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

    def test_actual_ownership_mapper_uses_exact_indexed_key(self):
        xml = (ROOT / 'business/medical-persistence/src/main/resources/medical-mappers/ArtifactRegistryMapper.xml').read_text()
        self.assertIn('SELECT write_id FROM formal_evaluation_exact_input_intent i WHERE artifact_id=#{id} LIMIT 2', xml)
        self.assertIn(('tenant_id', 'artifact_id'), self.keys['formal_evaluation_exact_input_intent'])

if __name__ == '__main__':
    unittest.main(verbosity=2)
