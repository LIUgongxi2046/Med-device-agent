"""Offline mature-parser checks for preparation storage and mapper contract.

No MySQL execution, socket access or claim of runtime tenant-interceptor acceptance.
"""
import json
import re
import unittest
from xml.etree import ElementTree

import sqlglot
from sqlglot import exp
from test_simulation_input_artifact_migration import ROOT, RESOURCES, inspect_migration, foreign_keys, assert_registered

MIGRATION = 'db/migration/065-simulation-evaluation-preparation.sql'
PERSISTENCE = ROOT / 'business/medical-persistence/src/main'
TABLE = 'simulation_evaluation_preparation'


def mapper_statements(name):
    root = ElementTree.parse(PERSISTENCE / 'resources/medical-mappers' / (name + '.xml')).getroot()
    statements = {}
    for item in root:
        if item.tag in ('select', 'insert', 'update', 'delete'):
            # Real MyBatis parameters become parser placeholders, never SQL string interpolation.
            text = ''.join(item.itertext()).strip()
            if '${' in text:
                raise AssertionError('Unbounded SQL interpolation: ' + item.attrib['id'])
            source = re.sub(r'#\{[^}]+\}', '?', text)
            statements[item.attrib['id']] = (item, sqlglot.parse_one(source, read='mysql'), text)
    return root, statements


def inserted_values(statement):
    return dict(zip((column.name for column in statement.this.expressions),
                    statement.expression.expressions[0].expressions, strict=True))


class SimulationPreparationMigrationTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.sql = (RESOURCES / MIGRATION).read_text()
        cls.parsed, cls.tables = inspect_migration(RESOURCES / MIGRATION)
        cls.table = cls.tables[TABLE]
        cls.columns = {c.name: c for c in cls.table.this.expressions if isinstance(c, exp.ColumnDef)}
        cls.mapper, cls.statements = mapper_statements('SimulationEvaluationPreparationMapper')

    def test_exact_registration_after_064_and_additive_immutable_table(self):
        assert_registered(self, MIGRATION, 'db/migration/064-simulation-rule-binding.sql', self.tables)
        self.assertEqual({TABLE}, set(self.tables))
        self.assertEqual(1, len(self.parsed))
        self.assertIn('create', self.parsed[0])
        coverage = json.loads((RESOURCES / 'db/migration/coverage.json').read_text())
        self.assertEqual([TABLE], coverage['required_tables']['simulation_evaluation_preparation'])
        self.assertEqual(526, sum(map(len, coverage['required_tables'].values())))
        self.assertEqual(565, sum(map(len, coverage['required_tables'].values()))
                         + len(coverage['platform_tables']) + len(coverage['framework_tables']))
        self.assertNotRegex(self.sql, r'(?im)^\s*(INSERT|UPDATE|DELETE|REPLACE|ALTER|DROP|TRUNCATE|GRANT|REVOKE)\b')
        identities = re.findall(r'^--changeset (\S+) dbms:mysql runInTransaction:false failOnError:true$', self.sql, re.M)
        self.assertEqual(['medical-agent:065-01-simulation-evaluation-preparation'], identities)

    def test_tenant_primary_unique_operation_and_exact_source_rights_artifact_fks(self):
        self.assertEqual(('tenant_id', 'artifact_id'), tuple(c.name for c in next(self.table.find_all(exp.PrimaryKey)).expressions))
        unique = {tuple(c.name for c in key.this.expressions) for key in self.table.find_all(exp.UniqueColumnConstraint)}
        self.assertEqual({('tenant_id', 'operation_id')}, unique)
        self.assertEqual({
            ('artifact', ('tenant_id', 'artifact_id'), ('tenant_id', 'artifact_id')),
            ('source_version', ('tenant_id', 'source_version'), ('tenant_id', 'source_version')),
            ('rights_revision', ('tenant_id', 'source_version', 'rights_revision'), ('tenant_id', 'source_version', 'rights_revision')),
            ('tenant_member', ('tenant_id', 'created_by'), ('tenant_id', 'user_id')),
        }, foreign_keys(self.table))
        self.assertNotIn('project_id', self.columns)  # Producer is enterprise-only; mapper pins its artifacts below.
        self.assertFalse({'revision', 'updated_at', 'status'} & self.columns.keys())

    def test_full_original_documents_operation_and_body_hashes_are_nonnull(self):
        self.assertEqual({'tenant_id', 'artifact_id', 'kind', 'source_version', 'rights_revision',
            'canonical_document', 'document_sha256', 'canonical_request_json', 'request_body_sha256',
            'operation_request_sha256', 'receipt_json', 'operation_id', 'schema_version',
            'schema_manifest_sha256', 'created_by', 'created_at', 'classification'}, set(self.columns))
        for name, column in self.columns.items():
            with self.subTest(column=name):
                self.assertTrue(any(isinstance(c.kind, exp.NotNullColumnConstraint) for c in column.constraints))
        checks = ' AND '.join(c.this.sql(dialect='mysql') for c in self.table.find_all(exp.CheckColumnConstraint))
        for column in ('canonical_document', 'canonical_request_json', 'receipt_json'):
            self.assertIn('JSON_VALID(' + column + ')', checks)
            self.assertEqual('LONGTEXT', self.columns[column].kind.sql(dialect='mysql'))
        hash_guards = {(node.this.name, node.expression.this) for node in self.table.find_all(exp.RegexpLike)}
        self.assertEqual({(column, '^[a-f0-9]{64}$') for column in
            ('document_sha256', 'request_body_sha256', 'operation_request_sha256', 'schema_manifest_sha256')}, hash_guards)
        self.assertIn("kind IN ('EXPECTED_ITEMS', 'SAMPLE_MANIFEST', 'AUTHORIZED_USE_EVIDENCE')", checks)
        self.assertIn("schema_version = '1.0.0'", checks)
        self.assertIn("classification IN ('PUBLIC', 'INTERNAL', 'RESTRICTED', 'PERSONAL_HEALTH')", checks)

    def test_mapper_persists_and_reads_complete_registration_without_mutation(self):
        self.assertEqual('com.ruoyi.medical.persistence.mapper.SimulationEvaluationPreparationMapper', self.mapper.attrib['namespace'])
        expected = {'registration', 'lockSource', 'insertRegistration', 'insertArtifact', 'insertBinding', 'operation', 'operationById', 'saveOperation', 'audit', 'event'}
        self.assertEqual(expected, set(self.statements))
        java = (PERSISTENCE / 'java/com/ruoyi/medical/persistence/mapper/SimulationEvaluationPreparationMapper.java').read_text()
        self.assertEqual(expected, set(re.findall(r'\b(\w+)\(Map<String,Object> values\)', java)))
        for name, (element, statement, _) in self.statements.items():
            self.assertIsInstance(statement, (exp.Select, exp.Insert), name)
            if element.tag == 'select':
                self.assertEqual('false', element.attrib['useCache'])
                self.assertEqual('true', element.attrib['flushCache'])
        read = self.statements['registration'][1]
        self.assertEqual(set(self.columns), {c.name for c in read.expressions})
        result = self.mapper.find("resultMap[@id='registrationRow']/constructor")
        self.assertEqual(list(self.columns), [arg.attrib['column'] for arg in result])
        insert = self.statements['insertRegistration'][1]
        self.assertEqual(TABLE, insert.this.this.name)
        self.assertEqual(set(self.columns) - {'tenant_id'}, set(inserted_values(insert)))
        self.assertTrue(all(isinstance(value, exp.Placeholder) for value in inserted_values(insert).values()))
        self.assertIn('FOR UPDATE', self.statements['lockSource'][2])

    def test_mapper_artifacts_are_enterprise_source_bound_and_actor_scoped(self):
        artifact = inserted_values(self.statements['insertArtifact'][1])
        binding = inserted_values(self.statements['insertBinding'][1])
        self.assertIsInstance(artifact['project_id'], exp.Null)
        self.assertIsInstance(binding['project_id'], exp.Null)
        self.assertEqual('SOURCE', binding['resource_kind'].this)
        self.assertIn('#{sourceId}', self.statements['insertBinding'][2])
        for name, subject in [('operation', '#{userId}'), ('operationById', '#{creatorId}')]:
            text = self.statements[name][2]
            self.assertIn('subject_id=' + subject, text)
            self.assertIn("operation_name='SIM-EVALUATION-PREPARE'", text)
        self.assertIn('idempotency_key=#{key}', self.statements['operation'][2])
        self.assertIn('operation_id=#{operationId}', self.statements['operationById'][2])
        operation = inserted_values(self.statements['saveOperation'][1])
        self.assertEqual('SIM-EVALUATION-PREPARE', operation['operation_name'].this)
        self.assertEqual('201', operation['http_status'].this)
        self.assertEqual('COMPLETED', operation['state'].this)

    def test_mapper_reaches_existing_server_tenant_and_immutable_guards(self):
        scope = (PERSISTENCE / 'java/com/ruoyi/medical/persistence/TenantScope.java').read_text()
        self.assertIn('statementId.startsWith("com.ruoyi.medical.persistence.mapper.SimulationEvaluationPreparationMapper.")', scope)
        guard = (PERSISTENCE / 'java/com/ruoyi/medical/persistence/ProtectedTenantInterceptor.java').read_text()
        immutable = re.search(r'IMMUTABLE_TABLES\s*=\s*java.util.Set.of\((.*?)\);', guard, re.S).group(1)
        self.assertIn('"' + TABLE + '"', immutable)
        self.assertIn('if (immutableTable(update.getTable().getName())) throw denied()', guard)
        self.assertIn('if (immutableTable(delete.getTable().getName())) throw denied()', guard)
        # Mapper omits tenant values by design: the existing server-owned interceptor supplies them.
        for _, statement, _ in self.statements.values():
            if isinstance(statement, exp.Insert):
                self.assertNotIn('tenant_id', inserted_values(statement))


if __name__ == '__main__':
    unittest.main()
