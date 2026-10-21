"""Offline real-parser constraints, not MySQL execution or business acceptance."""
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import unittest
from xml.etree import ElementTree

import sqlglot
from sqlglot import exp

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'scripts'))
from run_migration import resolve_java
RESOURCES = ROOT / 'business/medical-data/src/main/resources'
MIGRATION = 'db/migration/063-simulation-input-artifact.sql'


def inspect_migration(path):
    repo = ROOT / '.implementation-work/maven-repository'
    jars = [repo / p for p in (
        'com/github/jsqlparser/jsqlparser/5.2/jsqlparser-5.2.jar',
        'com/fasterxml/jackson/core/jackson-databind/2.21.4/jackson-databind-2.21.4.jar',
        'com/fasterxml/jackson/core/jackson-core/2.21.4/jackson-core-2.21.4.jar',
        'com/fasterxml/jackson/core/jackson-annotations/2.21/jackson-annotations-2.21.jar')]
    from migration_source_parser import split_native_source
    from tempfile import TemporaryDirectory
    normal, triggers = split_native_source(Path(path).read_text())
    # JSQLParser 5.2 hangs on MySQL DROP CHECK. Keep that exact, bounded
    # grammar as a complete SQLGlot AST; all other original SQL stays native.
    from migration_source_parser import bounded_drop_check
    drops = {index: bounded_drop_check(statement) for index, statement in enumerate(normal)}
    native = [statement for index, statement in enumerate(normal) if drops[index] is None]
    parsed_native = []
    if native:
        with TemporaryDirectory(prefix="medical-source-parser-") as temporary:
            native_path = Path(temporary) / "source.sql"
            native_path.write_text("\n".join(native))
            try:
                from native_parser_cache import run_json
                parsed_native = run_json([str(resolve_java(ROOT)), '-XX:ActiveProcessorCount=2', '-XX:-UsePerfData',
                    '--class-path', os.pathsep.join(map(str, jars)), str(ROOT / 'tests/InspectAlterMigrations.java'),
                    str(native_path)], source_bytes=Path(path).read_bytes(), libraries=jars,
                    parser=ROOT / 'tests/InspectAlterMigrations.java', mode='ALTER', expected_count=len(native),
                    cwd=ROOT, capture_output=True, text=True, timeout=60, check=True)
            except (subprocess.CalledProcessError, subprocess.TimeoutExpired) as error:
                raise RuntimeError('NATIVE_MIGRATION_PARSE_FAILED: ' + str(path)) from error
    if len(parsed_native) != len(native):
        raise ValueError('NATIVE_STATEMENT_COUNT_MISMATCH: ' + str(path))
    native_iter = iter(parsed_native)
    parsed = [drops[index] if drops[index] is not None else next(native_iter) for index in range(len(normal))]
    parsed.extend({"trigger": trigger} for trigger in triggers)
    tables = {}
    for item in parsed:
        if 'create' in item:
            table = sqlglot.parse_one(item['create'], read='mysql')
            tables[table.this.this.name] = table
    return parsed, tables


def foreign_keys(table):
    return {(fk.args['reference'].this.this.name,
             tuple(c.name for c in fk.expressions),
             tuple(c.name for c in fk.args['reference'].this.expressions))
            for fk in table.find_all(exp.ForeignKey)}


def assert_registered(test, migration, predecessor, tables):
    entries = ElementTree.parse(RESOURCES / 'db/changelog/master.xml').findall(
        '{http://www.liquibase.org/xml/ns/dbchangelog}include')
    files = [e.attrib['file'] for e in entries]
    test.assertEqual(1, files.count(migration))
    test.assertEqual(predecessor, files[files.index(migration) - 1])
    coverage = json.loads((RESOURCES / 'db/migration/coverage.json').read_text())
    test.assertEqual(1, coverage['native_sql_migrations'].count(migration))
    registered = {name for names in coverage['required_tables'].values() for name in names}
    test.assertLessEqual(set(tables), registered)


class SimulationInputArtifactMigrationTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.sql = (RESOURCES / MIGRATION).read_text()
        cls.parsed, cls.tables = inspect_migration(RESOURCES / MIGRATION)

    def test_registration_and_no_seed_grants_or_mutation(self):
        assert_registered(self, MIGRATION, 'db/migration/062-simulation-result-settlement.sql', self.tables)
        self.assertEqual({'simulation_fixture_input_artifact', 'simulation_input_artifact_operation'}, set(self.tables))
        self.assertNotRegex(self.sql, r'(?im)^\s*(INSERT|UPDATE|DELETE|REPLACE|TRUNCATE|DROP|GRANT|REVOKE)\b')
        self.assertFalse(any('data_fill' in item for item in self.parsed))
        alters = [item for item in self.parsed if 'table' in item]
        self.assertEqual(1, len(alters))
        self.assertEqual('simulation_fixture_registration', alters[0]['table'])
        self.assertEqual('ADD', alters[0]['operation'])
        self.assertIn('UNIQUE', alters[0]['definition'])
        self.assertIn('(tenant_id, project_key, case_id, model_config_revision, input_source_version, input_rights_revision)', alters[0]['definition'])

    def test_001_through_062_bytes_frozen_against_part11(self):
        # Digest independently calculated after byte-for-byte comparison with the part11 delivery ZIP.
        paths = sorted(p for p in (RESOURCES / 'db/migration').iterdir()
                       if p.suffix in ('.sql', '.xml') and p.name[:3].isdigit() and int(p.name[:3]) <= 62)
        self.assertEqual(61, len(paths))
        digest = hashlib.sha256(b''.join(p.name.encode() + b'\0' + p.read_bytes() + b'\0' for p in paths)).hexdigest()
        self.assertEqual('2d397c29d7cd21da85187ffd3473d2f4eef452b404a04f50a3716daa4eed60bf', digest)

    def test_strict_fixture_source_rights_artifact_scope(self):
        table = self.tables['simulation_fixture_input_artifact']
        keys = foreign_keys(table)
        self.assertIn(('simulation_fixture_registration',
            ('tenant_id', 'project_key', 'case_id', 'model_config_revision', 'input_source_version', 'input_rights_revision'),
            ('tenant_id', 'project_key', 'case_id', 'model_config_revision', 'input_source_version', 'input_rights_revision')), keys)
        self.assertIn(('rights_revision', ('tenant_id', 'input_source_version', 'input_rights_revision'),
                       ('tenant_id', 'source_version', 'rights_revision')), keys)
        self.assertIn(('artifact', ('tenant_id', 'project_key', 'artifact_id'),
                       ('tenant_id', 'project_key', 'artifact_id')), keys)
        self.assertIn('artifact_id <> input_source_version', self.sql)
        for table in self.tables.values():
            for _, local, remote in foreign_keys(table):
                self.assertEqual('tenant_id', local[0])
                self.assertEqual('tenant_id', remote[0])

    def test_project_key_nonnull_unique_and_stored_primary_key(self):
        table = self.tables['simulation_fixture_input_artifact']
        columns = {c.name: c for c in table.this.expressions if isinstance(c, exp.ColumnDef)}
        project = columns['project_key'].sql(dialect='mysql').upper()
        self.assertIn("COALESCE(PROJECT_ID, 'ENTERPRISE')", project)
        unique = {tuple(c.name for c in u.this.expressions) for u in table.find_all(exp.UniqueColumnConstraint)}
        self.assertIn(('tenant_id', 'artifact_id'), unique)
        primary = next(table.find_all(exp.PrimaryKey))
        # MySQL 8.0 permits STORED generated primary keys (bugs.mysql.com/bug.php?id=80211).
        self.assertIn('STORED', project)
        self.assertEqual(('tenant_id', 'project_key', 'case_id', 'model_config_revision'),
                         tuple(c.name for c in primary.expressions))

    def test_operation_identity_and_receipt_integrity(self):
        table = self.tables['simulation_input_artifact_operation']
        self.assertEqual(('tenant_id', 'actor_id', 'idempotency_key'), tuple(c.name for c in next(table.find_all(exp.PrimaryKey)).expressions))
        for token in ("JSON_TYPE(receipt_json)='OBJECT'", "request_sha256 REGEXP '^[a-f0-9]{64}$'",
                      "schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'", 'CHAR_LENGTH(idempotency_key) BETWEEN 16 AND 128'):
            self.assertIn(token, self.sql)

    def test_canonical_original_request_and_body_hash_are_closed_and_nonnull(self):
        table = self.tables['simulation_input_artifact_operation']
        columns = {c.name: c for c in table.this.expressions if isinstance(c, exp.ColumnDef)}
        for name in ('canonical_request_json', 'request_body_sha256', 'request_sha256'):
            self.assertTrue(any(isinstance(c.kind, exp.NotNullColumnConstraint) for c in columns[name].constraints))
        self.assertEqual('JSON', columns['canonical_request_json'].kind.sql(dialect='mysql'))
        checks = ' AND '.join(c.this.sql(dialect='mysql') for c in table.find_all(exp.CheckColumnConstraint))
        self.assertIn(('request_body_sha256', '^[a-f0-9]{64}$'),
                      {(node.this.name, node.expression.this) for node in table.find_all(exp.RegexpLike)})
        for token in ("JSON_TYPE(canonical_request_json)='OBJECT'", 'JSON_LENGTH(canonical_request_json)=2',
                      "JSON_CONTAINS_PATH(canonical_request_json,'all','$.case_id','$.request')",
                      "JSON_TYPE(JSON_EXTRACT(canonical_request_json,'$.case_id'))='STRING'",
                      "JSON_TYPE(JSON_EXTRACT(canonical_request_json,'$.request'))='OBJECT'"):
            self.assertIn(token, self.sql)
        self.assertIn('created_by = actor_id', checks)
        self.assertIn("schema_version = '1.0.0'", checks)

    def test_input_mapper_preserves_original_hashes_and_configuration_scope(self):
        from test_simulation_preparation_migration import mapper_statements, inserted_values
        mapper, statements = mapper_statements('SimulationInputArtifactMapper')
        self.assertTrue(all(isinstance(statement, (exp.Select, exp.Insert)) for _, statement, _ in statements.values()))
        operation = statements['operation'][1]
        selected = {c.name for c in operation.expressions}
        self.assertLessEqual({'canonical_request_json', 'request_body_sha256', 'request_sha256', 'receipt_json'}, selected)
        constructor = mapper.find("resultMap[@id='operationRow']/constructor")
        self.assertEqual(selected, {arg.attrib['column'] for arg in constructor})
        written = inserted_values(statements['insertOperation'][1])
        self.assertLessEqual({'canonical_request_json', 'request_body_sha256', 'request_sha256', 'receipt_json'}, written.keys())
        for parameter in ('#{canonicalRequest}', '#{bodyHash}', '#{requestHash}', '#{receipt}'):
            self.assertIn(parameter, statements['insertOperation'][2])
        self.assertIn('actor_id=#{userId}', statements['operation'][2])
        self.assertIn('idempotency_key=#{key}', statements['operation'][2])
        binding = inserted_values(statements['insertBinding'][1])
        self.assertEqual('CONFIGURATION', binding['resource_kind'].this)
        self.assertIsInstance(binding['project_id'], exp.Placeholder)
        self.assertIn('#{configId}', statements['insertBinding'][2])
        self.assertIn('project_id <=> #{projectId}', statements['link'][2])
        self.assertIn('project_id <=> #{projectId}', statements['lockFixture'][2])
        link = inserted_values(statements['insertLink'][1])
        self.assertLessEqual({'project_id', 'case_id', 'model_config_revision', 'input_source_version', 'input_rights_revision', 'artifact_id'}, link.keys())


if __name__ == '__main__':
    unittest.main()
