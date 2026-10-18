"""Offline AST and constraint checks; no database, migration execution or object writes."""
import hashlib
import json
import os
from pathlib import Path
import re
import sqlite3
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
MIGRATION = 'db/migration/062-simulation-result-settlement.sql'


class SimulationResultSettlementMigrationTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.sql = (RESOURCES / MIGRATION).read_text()
        repo = ROOT / '.implementation-work/maven-repository'
        jars = [repo / path for path in (
            'com/github/jsqlparser/jsqlparser/5.2/jsqlparser-5.2.jar',
            'com/fasterxml/jackson/core/jackson-databind/2.21.4/jackson-databind-2.21.4.jar',
            'com/fasterxml/jackson/core/jackson-core/2.21.4/jackson-core-2.21.4.jar',
            'com/fasterxml/jackson/core/jackson-annotations/2.21/jackson-annotations-2.21.jar')]
        result = subprocess.run([str(resolve_java(ROOT)), '-XX:-UsePerfData', '--class-path', os.pathsep.join(map(str, jars)),
            str(ROOT / 'tests/InspectAlterMigrations.java'), str(RESOURCES / MIGRATION)],
            cwd=ROOT, capture_output=True, text=True, timeout=30, check=True)
        cls.parsed = json.loads(result.stdout)
        cls.tables = {}
        for item in cls.parsed:
            if 'create' in item:
                table = sqlglot.parse_one(item['create'], read='mysql')
                cls.tables[table.this.this.name] = table

    def test_registered_once_after_061_without_seed_or_financial_change(self):
        entries = ElementTree.parse(RESOURCES / 'db/changelog/master.xml').findall('{http://www.liquibase.org/xml/ns/dbchangelog}include')
        files = [e.attrib['file'] for e in entries]
        self.assertEqual(1, files.count(MIGRATION))
        self.assertEqual('db/migration/061-simulation-evaluation-job.sql', files[files.index(MIGRATION) - 1])
        coverage = json.loads((RESOURCES / 'db/migration/coverage.json').read_text())
        self.assertEqual(1, coverage['native_sql_migrations'].count(MIGRATION))
        self.assertEqual({'simulation_result_persistence', 'simulation_quota_settlement'}, set(self.tables))
        self.assertEqual(set(self.tables), set(coverage['required_tables']['simulation_result_settlement']))
        self.assertNotRegex(self.sql, r'(?im)^\s*(INSERT|UPDATE|DELETE|TRUNCATE|DROP TABLE)\b')
        self.assertNotIn('ALTER TABLE invocation', self.sql)
        ids = [identity for path in (RESOURCES / 'db/migration').glob('*.sql')
               for identity in re.findall(r'^--changeset (\S+)', path.read_text(), re.M)]
        self.assertEqual(len(ids), len(set(ids)))

    def test_all_001_through_061_bytes_frozen(self):
        paths = sorted(p for p in (RESOURCES / 'db/migration').iterdir()
                       if p.suffix in ('.sql', '.xml') and p.name[:3].isdigit() and int(p.name[:3]) <= 61)
        self.assertEqual(60, len(paths))
        digest = hashlib.sha256(b''.join(p.name.encode() + b'\0' + p.read_bytes() + b'\0' for p in paths)).hexdigest()
        self.assertEqual('5c59fc1066481fa630289a92c7bf7fac70d5062ec6053551c295431834388b88', digest)

    def test_original_invocation_fact_scope_and_hash_foreign_keys(self):
        for name, table in self.tables.items():
            with self.subTest(table=name):
                primary = next(table.find_all(exp.PrimaryKey))
                self.assertEqual(('tenant_id', 'invocation_id'), tuple(c.name for c in primary.expressions))
                foreigns = list(table.find_all(exp.ForeignKey))
                self.assertTrue(all(fk.expressions[0].name == 'tenant_id' for fk in foreigns))
                invocation = next(fk for fk in foreigns if fk.args['reference'].this.this.name == 'invocation')
                self.assertEqual(('tenant_id', 'project_key', 'invocation_id', 'execution_mode'),
                                 tuple(c.name for c in invocation.expressions))
                fact = next(fk for fk in foreigns if fk.args['reference'].this.this.name == 'simulation_invocation_fact')
                expected = ('tenant_id', 'project_key', 'invocation_id', 'fact_id')
                if name == 'simulation_result_persistence':
                    expected += ('payload_sha256',)
                self.assertEqual(expected, tuple(c.name for c in fact.expressions))
        self.assertIn('ADD UNIQUE KEY uq_simulation_result_fact_hash (tenant_id,project_key,invocation_id,fact_id,payload_sha256)', self.sql)

    def test_prepared_command_is_not_a_fictional_artifact(self):
        table = self.tables['simulation_result_persistence']
        targets = {fk.args['reference'].this.this.name for fk in table.find_all(exp.ForeignKey)}
        self.assertEqual({'invocation', 'simulation_invocation_fact', 'simulation_retention_policy', 'tenant_member'}, targets)
        self.assertIn("JSON_TYPE(command_json)='OBJECT'", self.sql)
        self.assertIn("status IN ('PREPARED','WRITING','VERIFIED','COMPLETED')", self.sql)
        self.assertIn('revision BETWEEN 1 AND 9007199254740991', self.sql)
        self.assertIn('retention_deadline>created_at', self.sql)
        self.assertIn("binding_sha256 REGEXP '^[a-f0-9]{64}$'", self.sql)

    def test_final_receipt_requires_both_real_same_scope_artifact_hashes(self):
        table = self.tables['simulation_quota_settlement']
        artifacts = [fk for fk in table.find_all(exp.ForeignKey) if fk.args['reference'].this.this.name == 'artifact']
        self.assertEqual(2, len(artifacts))
        self.assertEqual({('tenant_id', 'project_key', 'result_artifact_id', 'result_sha256'),
                          ('tenant_id', 'project_key', 'provenance_artifact_id', 'provenance_sha256')},
                         {tuple(c.name for c in fk.expressions) for fk in artifacts})
        self.assertTrue(all(tuple(c.name for c in fk.args['reference'].this.expressions) ==
                            ('tenant_id', 'project_key', 'artifact_id', 'sha256') for fk in artifacts))
        self.assertIn('result_artifact_id<>provenance_artifact_id', self.sql)
        # A fixture receipt is reusable; invocation is the deduplication key.
        self.assertEqual([], list(table.find_all(exp.UniqueColumnConstraint)))

    def test_no_provider_finance_or_production_claim_columns(self):
        table = self.tables['simulation_quota_settlement']
        columns = {c.name for c in table.this.expressions if isinstance(c, exp.ColumnDef)}
        self.assertTrue({'charged_units', 'charged_calls', 'counter', 'counter_sha256', 'fixture_usage', 'usage_origin'} <= columns)
        self.assertFalse({'currency', 'price_version', 'provider_request_id', 'usage_receipt_id', 'settled_amount', 'total_tokens'} & columns)
        self.assertIn("execution_mode='SIMULATION' AND production_eligible=FALSE", self.sql)

    def test_actual_charge_and_fixture_null_guards_reject_invalid_values(self):
        # Exercise the actual extracted CHECK expressions with an in-memory expression evaluator.
        # This is deliberately not a claim of MySQL DDL execution.
        checks = list(self.tables['simulation_quota_settlement'].find_all(exp.CheckColumnConstraint))
        selected = [check.this.sql(dialect='mysql') for check in checks
                    if any(token in check.this.sql(dialect='mysql') for token in ('charged_units', 'fixture_usage', 'charged_calls', 'production_eligible'))]
        db = sqlite3.connect(':memory:')
        db.create_function('JSON_TYPE', 1, lambda value: 'OBJECT' if value and isinstance(json.loads(value), dict) else 'NULL')
        def accepted(**overrides):
            values = dict(input_units=4, output_units=3, charged_units=7, charged_calls=1,
                          result_artifact_id='a', provenance_artifact_id='b', usage_origin='UNKNOWN', fixture_usage=None,
                          execution_mode='SIMULATION', production_eligible=0)
            values.update(overrides)
            sql = 'SELECT (' + ') AND ('.join(selected) + ') FROM (SELECT ' + ','.join(':'+k+' AS '+k for k in values) + ')'
            return db.execute(sql, values).fetchone()[0] == 1
        self.assertTrue(accepted())
        self.assertTrue(accepted(usage_origin='SIMULATION_FIXTURE', fixture_usage='{"tokens":4}'))
        for bad in (dict(production_eligible=1), dict(execution_mode='EXTERNAL'), dict(charged_units=8), dict(charged_calls=2), dict(result_artifact_id='b'),
                    dict(input_units=9007199254740992), dict(fixture_usage='{}'),
                    dict(usage_origin='SIMULATION_FIXTURE'), dict(usage_origin='PROVIDER'),
                    dict(usage_origin='SIMULATION_FIXTURE', fixture_usage='null')):
            with self.subTest(bad=bad):
                self.assertFalse(accepted(**bad))
        db.close()

    def test_terminal_replacement_preserves_old_states_and_other_checks(self):
        previous = (RESOURCES / 'db/migration/061-simulation-evaluation-job.sql').read_text()
        previous_table = sqlglot.parse_one(next(s for s in re.sub(r'(?m)^--.*$', '', previous).split(';')
                                                if 'CREATE TABLE simulation_evaluation_job' in s), read='mysql')
        checks = list(previous_table.find_all(exp.CheckColumnConstraint))
        self.assertIn("status = 'READY'", checks[3].this.sql())
        drop = [item for item in self.parsed if 'drop_constraint' in item]
        self.assertEqual(1, len(drop))
        self.assertEqual('simulation_evaluation_job_chk_4', drop[0]['drop_constraint'])
        alters = [s for s in self.sql.split(';') if 'ALTER TABLE simulation_evaluation_job' in s]
        self.assertEqual(1, len(alters))
        alter = alters[0]
        self.assertIn('DROP CONSTRAINT simulation_evaluation_job_chk_4', alter)
        self.assertIn('ADD CONSTRAINT ck_simulation_job_result_state CHECK', alter)
        for state, revision in [('READY', 1), ('CLAIMED', 2), ('ISSUED', 3)]:
            self.assertIn("status='" + state + "' AND revision=" + str(revision), alter)
        self.assertIn("status IN ('SUCCEEDED','FAILED') AND revision=4 AND authority_id IS NOT NULL AND admission_json IS NOT NULL", alter)
        self.assertNotIn('ALTER TABLE system_job_run', self.sql)
        self.assertNotIn('ALTER TABLE run_step', self.sql)


if __name__ == '__main__':
    unittest.main()
