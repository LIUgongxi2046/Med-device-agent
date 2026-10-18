"""Offline parser/constraint tests only. These are not evidence of a migrated database."""
import hashlib
import json
from pathlib import Path
import re
import unittest
from xml.etree import ElementTree

import sqlglot
from sqlglot import exp

ROOT = Path(__file__).resolve().parents[1]
RESOURCES = ROOT / 'business/medical-data/src/main/resources'
MIGRATION = 'db/migration/061-simulation-evaluation-job.sql'


class SimulationEvaluationJobMigrationTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.sql = (RESOURCES / MIGRATION).read_text()
        source = re.sub(r'(?m)^--.*$', '', cls.sql)
        # SQLGlot parses CREATE constraints; mixed MySQL ALTER is covered by the full bundle's JSQLParser.
        cls.tables = {}
        for statement in source.split(';'):
            if re.search(r'(?m)^CREATE TABLE ', statement):
                parsed = sqlglot.parse_one(statement, read='mysql')
                cls.tables[parsed.this.this.name] = parsed

    def test_registration_no_seed_or_destructive_change(self):
        entries = ElementTree.parse(RESOURCES / 'db/changelog/master.xml').findall('{http://www.liquibase.org/xml/ns/dbchangelog}include')
        self.assertLess([e.attrib['file'] for e in entries].index(MIGRATION),
                        [e.attrib['file'] for e in entries].index('db/migration/062-simulation-result-settlement.sql'))
        self.assertEqual(1, sum(e.attrib['file'] == MIGRATION for e in entries))
        coverage = json.loads((RESOURCES / 'db/migration/coverage.json').read_text())
        self.assertEqual(1, coverage['native_sql_migrations'].count(MIGRATION))
        self.assertEqual({'simulation_scope_manifest', 'simulation_evaluation_job', 'simulation_context_dependency_manifest'}, set(self.tables))
        self.assertEqual(set(self.tables), set(coverage['required_tables']['simulation_evaluation_job']))
        self.assertNotRegex(self.sql, r'(?im)^\s*(INSERT|UPDATE|DELETE|DROP|TRUNCATE)\b')
        identities = []
        for path in (RESOURCES / 'db/migration').glob('*.sql'):
            identities += re.findall(r'^--changeset (\S+)', path.read_text(), re.M)
        self.assertEqual(len(identities), len(set(identities)))

    def test_all_prior_migration_bytes_are_preserved(self):
        paths = sorted(p for p in (RESOURCES / 'db/migration').iterdir()
                       if p.suffix in ('.sql', '.xml') and p.name[:3].isdigit() and int(p.name[:3]) <= 60)
        self.assertEqual(59, len(paths))
        digest = hashlib.sha256(b''.join(p.name.encode() + b'\0' + p.read_bytes() + b'\0' for p in paths)).hexdigest()
        self.assertEqual('76d4338e055ffb1887752f2c1d7e74248c73df299be7df351543a4b84c3c8b64', digest)

    def test_workflow_nullable_conversion_preserves_old_requirement_atomically(self):
        alter = next(s for s in self.sql.split(';') if 'ALTER TABLE system_job_run' in s)
        self.assertIn('MODIFY COLUMN workflow_id VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NULL', alter)
        self.assertIn("job_type='SIMULATION_EVALUATION' AND workflow_id IS NULL", alter)
        self.assertIn("job_type<>'SIMULATION_EVALUATION' AND workflow_id IS NOT NULL", alter)
        self.assertNotIn('ALTER TABLE task_run', self.sql)

    def test_unique_cell_and_all_real_identity_bindings(self):
        table = self.tables['simulation_evaluation_job']
        keys = {tuple(c.name for c in key.this.expressions) for key in table.find_all(exp.UniqueColumnConstraint)}
        self.assertIn(('tenant_id', 'evaluation_id', 'sample_id', 'agent_version_id', 'repeat_index'), keys)
        self.assertIn(('tenant_id', 'run_id'), keys)
        self.assertIn(('tenant_id', 'invocation_id'), keys)
        targets = {fk.args['reference'].this.this.name for fk in table.find_all(exp.ForeignKey)}
        self.assertEqual({'evaluation_batch', 'evaluation_definition', 'agent_version', 'system_job_run',
            'run_principal', 'step_action_binding', 'context_manifest', 'simulation_scope_manifest',
            'capability_version', 'version_registry', 'simulation_fixture_registration', 'model_config_revision',
            'simulation_quota_account', 'simulation_retention_policy', 'permission_grant',
            'simulation_execution_authority', 'tenant_member'}, targets)
        self.assertNotIn('invocation', targets)  # Planned ID is not a fictional admitted/executed invocation.
        for fk in table.find_all(exp.ForeignKey):
            self.assertEqual('tenant_id', fk.expressions[0].name)
        self.assertIn('REFERENCES simulation_quota_account (tenant_id,project_key,account_id,scope,parent_run_id)', self.sql)

    def test_real_scope_content_version_hash_and_context_mode(self):
        self.assertIn('REFERENCES version_registry (tenant_id,version_id,content_sha256)', self.sql)
        self.assertIn("JSON_TYPE(manifest)='OBJECT'", self.sql)
        self.assertIn('REFERENCES context_manifest (tenant_id,project_key,context_manifest_id,execution_mode)', self.sql)
        self.assertIn("execution_mode='SIMULATION' AND job_type='SIMULATION_EVALUATION'", self.sql)

    def test_dependency_manifest_and_runtime_role_are_real_registered_versions(self):
        table = self.tables['simulation_context_dependency_manifest']
        targets = {fk.args['reference'].this.this.name for fk in table.find_all(exp.ForeignKey)}
        self.assertEqual({'context_manifest', 'version_registry', 'tenant_member'}, targets)
        self.assertIn('REFERENCES capability_version (tenant_id,capability_version,kind)', self.sql)
        self.assertIn("role_kind='ROLE'", self.sql)
        self.assertNotIn('enterprise_role_version', self.sql)
        self.assertIn('REFERENCES simulation_quota_account (tenant_id,account_id,scope)', self.sql)
        self.assertIn("enterprise_account_scope='ENTERPRISE'", self.sql)
        self.assertIn('REFERENCES simulation_execution_authority (tenant_id,project_key,authority_id,run_id,step_id,action_id,principal_id,evaluation_id)', self.sql)

    def test_status_and_revision_cannot_claim_execution_completion(self):
        self.assertIn("status='READY' AND revision=1 AND authority_id IS NULL AND admission_json IS NULL", self.sql)
        self.assertIn("status='CLAIMED' AND revision=2 AND authority_id IS NULL AND admission_json IS NULL", self.sql)
        self.assertIn("status='ISSUED' AND revision=3 AND authority_id IS NOT NULL AND admission_json IS NOT NULL", self.sql)
        self.assertNotIn("'COMPLETED'", self.sql)
        for field in ('start_request', 'source_binding', 'prepared_payload'):
            self.assertIn("JSON_TYPE(" + field + ")='OBJECT'", self.sql)
            self.assertIn(field + "_sha256 REGEXP '^[a-f0-9]{64}$'", self.sql)
        for prohibited in ('credential_reference_id', 'provider_request_id', 'token_jti', 'access_token'):
            self.assertNotIn(prohibited, self.sql)


if __name__ == '__main__':
    unittest.main()
