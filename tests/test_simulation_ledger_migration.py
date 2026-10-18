"""Offline SQL AST verification. No database connections or DDL execution."""
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
MIGRATION = 'db/migration/060-simulation-invocation-ledger.sql'
TABLES = {'simulation_quota_account', 'simulation_execution_authority',
          'simulation_invocation_provenance', 'simulation_quota_reservation', 'simulation_invocation_fact', 'simulation_fixture_registration', 'simulation_retention_policy'}

class SimulationLedgerMigrationTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.sql = (RESOURCES / MIGRATION).read_text()
        repo = ROOT / '.implementation-work/maven-repository'
        jars = [repo / name for name in (
            'com/github/jsqlparser/jsqlparser/5.2/jsqlparser-5.2.jar',
            'com/fasterxml/jackson/core/jackson-databind/2.21.4/jackson-databind-2.21.4.jar',
            'com/fasterxml/jackson/core/jackson-core/2.21.4/jackson-core-2.21.4.jar',
            'com/fasterxml/jackson/core/jackson-annotations/2.21/jackson-annotations-2.21.jar')]
        result = subprocess.run([str(resolve_java(ROOT)), '--class-path', os.pathsep.join(map(str,jars)),
            str(ROOT / 'tests/InspectAlterMigrations.java'), str(RESOURCES / MIGRATION)],
            cwd=ROOT,capture_output=True,text=True,timeout=30,check=True)
        cls.parsed=json.loads(result.stdout)
        cls.tables={}
        for entry in cls.parsed:
            if 'create' in entry:
                table=sqlglot.parse_one(entry['create'],read='mysql')
                cls.tables[table.this.this.name]=table

    def test_registration_exact_tables_and_no_authority_seed(self):
        entries=ElementTree.parse(RESOURCES/'db/changelog/master.xml').findall('{http://www.liquibase.org/xml/ns/dbchangelog}include')
        self.assertLess([e.attrib['file'] for e in entries].index(MIGRATION),
                        [e.attrib['file'] for e in entries].index('db/migration/061-simulation-evaluation-job.sql'))
        self.assertEqual(1,sum(e.attrib['file']==MIGRATION for e in entries))
        coverage=json.loads((RESOURCES/'db/migration/coverage.json').read_text())
        self.assertEqual(TABLES,set(coverage['required_tables']['simulation_ledger']))
        self.assertEqual(TABLES,set(self.tables))
        self.assertEqual(281,sum(map(len,coverage['required_tables'].values())))
        self.assertEqual(1,coverage['native_sql_migrations'].count(MIGRATION))
        self.assertFalse(any('data_fill' in e for e in self.parsed))
        self.assertNotRegex(self.sql,r'(?im)^\s*(INSERT|UPDATE|DELETE|DROP|TRUNCATE)\b')

    def test_historical_migrations_unchanged(self):
        expected={'005-document-evidence.sql':'965055255fe7f7b267b4845eb741291bf59996ddd3e5bbadf8f89f582d0a2118',
            '007-capability-model-budget.sql':'1a434e6b364ab44c354b2411dd6bac762fe60bce5d5ff2d51ae0d6099602234f',
            '057-simulation-model-configuration.sql':'c724890977456b9cad939a20a316d3f9847282d3af106e7b9fc24bcd0df089be',
            '059-annotation-anchor-lifecycle.sql':'25350941613a4219897da78683c2883a536b8736dd65d40e89298b666cecfff6'}
        for name,digest in expected.items():
            self.assertEqual(digest,hashlib.sha256((RESOURCES/'db/migration'/name).read_bytes()).hexdigest())

    def test_mode_alters_keep_canonical_identity_and_external_requirements(self):
        altered={e['table'] for e in self.parsed if 'table' in e}
        self.assertEqual({'context_manifest','model_config_revision','invocation','evaluation_batch','simulation_execution_authority','principal_token'},altered)
        self.assertEqual(2,self.sql.count("execution_mode VARCHAR(16) NOT NULL DEFAULT 'EXTERNAL'"))
        for field in ['credential_reference_id','credential_revision','verification_id','price_version','currency']:
            self.assertIn(field+' IS NOT NULL',self.sql)
            self.assertIn(field+' IS NULL',self.sql)
        self.assertIn('REFERENCES model_config_revision (tenant_id,config_id,model_config_revision,execution_mode)',self.sql)
        self.assertIn('REFERENCES context_manifest (tenant_id,project_key,context_manifest_id,execution_mode)',self.sql)
        self.assertIn('tokenizer_revision IS NULL AND token_count IS NULL',self.sql)
        self.assertIn("settlement_status='PENDING'",self.sql)
        self.assertNotIn('ALTER TABLE budget_reservation',self.sql)

    def test_real_authority_and_simulation_only_children(self):
        def targets(name):
            return {fk.args['reference'].this.this.name for fk in self.tables[name].find_all(exp.ForeignKey)}
        self.assertEqual({'step_action_binding','execution_registry','run_principal','permission_grant','evaluation_batch'},targets('simulation_execution_authority'))
        for name in ['simulation_invocation_provenance','simulation_quota_reservation','simulation_invocation_fact']:
            self.assertIn('invocation',targets(name))
            references=[fk for fk in self.tables[name].find_all(exp.ForeignKey) if fk.args['reference'].this.this.name=='invocation']
            self.assertEqual(['tenant_id','project_key','invocation_id','execution_mode'],[x.name for x in references[0].expressions])
        self.assertIn('expires_at>created_at',self.sql)
        self.assertIn('retention_deadline>created_at',self.sql)
        self.assertNotIn('PUBLISHED',self.sql)

    def test_retention_requires_real_scoped_approval_without_source_policy_reuse(self):
        table=self.tables['simulation_retention_policy']
        refs={(fk.args['reference'].this.this.name, tuple(c.name for c in fk.expressions))
              for fk in table.find_all(exp.ForeignKey)}
        self.assertIn(('evaluation_batch',('tenant_id','project_key','evaluation_id')),refs)
        self.assertIn(('permission_grant',('tenant_id','approval_grant_id')),refs)
        self.assertIn(('tenant_member',('tenant_id','created_by')),refs)
        self.assertIn('FOREIGN KEY (tenant_id,project_key,evaluation_id,retention_policy_id)',self.sql)
        self.assertIn('maximum_record_bytes BETWEEN 1 AND 9007199254740991',self.sql)
        self.assertIn('retain_until>created_at',self.sql)
        self.assertNotIn('REFERENCES retention_policy_revision',self.sql)
        self.assertIn('REFERENCES principal_token (tenant_id,project_key,principal_id,run_id,step_id,jti,service_uri,lease_generation)',self.sql)
        self.assertIn('ADD COLUMN token_jti CHAR(36) CHARACTER SET ascii NOT NULL',self.sql)

    def test_fixture_registration_binds_real_source_rights_and_case_identity(self):
        table=self.tables['simulation_fixture_registration']
        refs={(fk.args['reference'].this.this.name, tuple(c.name for c in fk.expressions))
              for fk in table.find_all(exp.ForeignKey)}
        for prefix in ['case','input']:
            self.assertIn(('rights_revision',('tenant_id',prefix+'_source_version',prefix+'_rights_revision')),refs)
        self.assertIn(('model_config_revision',('tenant_id','model_config_revision')),refs)
        self.assertIn('uq_simulation_fixture_case (tenant_id,project_key,case_id)',self.sql)
        self.assertIn('PRIMARY KEY (tenant_id,case_receipt_id)',self.sql)
        self.assertIn('these JSON values do not establish source/fixture authorization',self.sql)

    def test_independent_finite_quota_and_idempotent_facts(self):
        account=self.tables['simulation_quota_account']
        cols={c.name for c in account.this.expressions if isinstance(c,exp.ColumnDef)}
        self.assertTrue({'counter','counter_sha256','approval_grant_id','approval_grant_revision','parent_run_key'}<=cols)
        self.assertFalse({'currency','upper_tokens','settled_amount','price_version'}&cols)
        self.assertIn('max_units BETWEEN 1 AND 9007199254740991',self.sql)
        self.assertIn('reserved_units<=max_units-spent_units',self.sql)
        self.assertIn('reserved_calls<=max_calls-spent_calls',self.sql)
        self.assertIn('PRIMARY KEY (tenant_id,invocation_id,account_id)',self.sql)
        self.assertIn('uq_simulation_fact_content (tenant_id,invocation_id,kind,payload_sha256)',self.sql)
        self.assertIn("status='HELD' AND charged_units IS NULL",self.sql)
        self.assertIn("status='RELEASED' AND charged_units IS NOT NULL AND charged_units=0",self.sql)
        self.assertIn('settings JSON NOT NULL',self.sql)

if __name__=='__main__': unittest.main(verbosity=2)
