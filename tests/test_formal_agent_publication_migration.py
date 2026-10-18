"""Portable 086 storage contracts using real parsers; no live MySQL claim."""
import unittest
from xml.etree import ElementTree as ET
import sqlglot
from sqlglot import exp
from test_simulation_input_artifact_migration import ROOT, RESOURCES, inspect_migration, foreign_keys
from test_migration_bundle import sqlglot_migration_source

MIGRATION = 'db/migration/086-agent-publication-lifecycle.sql'
TABLES = {'formal_agent_acceptance', 'formal_agent_publication_intent',
          'formal_agent_publication_attempt', 'formal_agent_publication_registration',
          'formal_agent_publication_disable'}
PERSISTENCE = ROOT / 'business/medical-persistence/src/main'


def columns(table):
    return {c.name: c for c in table.this.expressions if isinstance(c, exp.ColumnDef)}


def unique_keys(table):
    return ({tuple(c.name for c in k.expressions) for k in table.find_all(exp.PrimaryKey)} |
            {tuple(c.name for c in k.this.expressions) for k in table.find_all(exp.UniqueColumnConstraint)})


def charset(column):
    return tuple(c.sql(dialect='mysql') for c in column.constraints
                 if isinstance(c.kind, (exp.CharacterSetColumnConstraint, exp.CollateColumnConstraint)))


class FormalAgentPublicationMigrationTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.sql = (RESOURCES / MIGRATION).read_text()
        # 086 itself must pass real JSQLParser. No 068 compatibility exception here.
        cls.parsed, cls.tables = inspect_migration(RESOURCES / MIGRATION)
        cls.parent_keys, cls.parent_columns = {}, {}
        paths = [ROOT / 'data-design/core.sql'] + [RESOURCES / 'db/migration' / name for name in (
            '002-identity-sources.sql', '005-document-evidence.sql', '007-capability-model-budget.sql',
            '009-conversation-events-receipts.sql', '025-artifact-content-registry.sql',
            '027-template-definition.sql', '073-agent-publication.sql',
            '080-agent-publication.sql', '086-agent-publication-lifecycle.sql')]
        for path in paths:
            for statement in sqlglot.parse(sqlglot_migration_source(path), read='mysql'):
                if isinstance(statement, exp.Create) and isinstance(statement.this, exp.Schema):
                    name = statement.this.this.name
                    cls.parent_keys[name] = unique_keys(statement)
                    cls.parent_columns[name] = columns(statement)
                elif isinstance(statement, exp.Alter):
                    name = statement.this.name
                    cls.parent_keys.setdefault(name, set()).update(
                        tuple(c.name for c in k.this.expressions)
                        for k in statement.find_all(exp.UniqueColumnConstraint))
                    cls.parent_columns.setdefault(name, {}).update(
                        {c.name: c for c in statement.find_all(exp.ColumnDef)})

    def test_exact_five_additive_tables_and_no_seed_or_permission_mutation(self):
        self.assertEqual(TABLES, set(self.tables))
        self.assertEqual(5, len(self.parsed))
        self.assertTrue(all(set(s) == {'create'} for s in self.parsed))
        self.assertNotRegex(self.sql, r'(?im)^\s*(ALTER|INSERT|UPDATE|DELETE|DROP|GRANT|REVOKE|REPLACE|TRUNCATE)\b')

    def test_all_25_foreign_keys_have_exact_parent_unique_tuple_and_matching_types(self):
        count = 0
        for name, table in self.tables.items():
            for target, local, remote in foreign_keys(table):
                count += 1
                with self.subTest(table=name, parent=target, key=local):
                    self.assertEqual('tenant_id', local[0])
                    self.assertEqual('tenant_id', remote[0])
                    self.assertEqual(len(local), len(remote))
                    self.assertIn(remote, self.parent_keys[target])
                    for left, right in zip(local, remote):
                        child = self.parent_columns[name][left]
                        parent = self.parent_columns[target][right]
                        self.assertEqual(child.kind, parent.kind)
                        self.assertEqual(charset(child), charset(parent))
        self.assertEqual(25, count)

    def test_required_metadata_has_no_defaults_and_classification_is_frozen(self):
        for name, table in self.tables.items():
            with self.subTest(table=name):
                cols = columns(table)
                for key in ('tenant_id', 'schema_version', 'schema_manifest_sha256',
                            'classification', 'created_by', 'created_at'):
                    self.assertTrue(list(cols[key].find_all(exp.NotNullColumnConstraint)), key)
                    self.assertFalse(list(cols[key].find_all(exp.DefaultColumnConstraint)), key)
                checks = ' '.join(c.sql(dialect='mysql') for c in table.find_all(exp.CheckColumnConstraint))
                self.assertIn("schema_version = '1.0.0'", checks)
                self.assertIn("'PUBLIC', 'INTERNAL', 'RESTRICTED', 'PERSONAL_HEALTH'", checks)
                self.assertIn(('tenant_member', ('tenant_id', 'created_by'), ('tenant_id', 'user_id')), foreign_keys(table))

    def test_original_key_and_attempt_registration_owner_chain(self):
        intent = self.tables['formal_agent_publication_intent']
        self.assertIn(('tenant_id', 'created_by', 'idempotency_key'), unique_keys(intent))
        scoped = ('tenant_id', 'project_key', 'write_id', 'operation_id', 'release_id', 'created_by')
        self.assertIn(scoped, unique_keys(intent))
        for name in ('formal_agent_publication_attempt', 'formal_agent_publication_registration'):
            self.assertIn(('formal_agent_publication_intent', scoped, scoped), foreign_keys(self.tables[name]))
        registration = self.tables['formal_agent_publication_registration']
        self.assertIn(('formal_agent_publication_attempt', ('tenant_id', 'write_id'), ('tenant_id', 'write_id')), foreign_keys(registration))
        for name in TABLES - {'formal_agent_publication_disable'}:
            project = columns(self.tables[name])['project_key'].sql(dialect='mysql').upper()
            self.assertIn("COALESCE(PROJECT_ID, 'ENTERPRISE')", project)
            self.assertIn('STORED', project)
        self.assertIn('deadline > created_at', intent.sql(dialect='mysql'))

    def test_master_order_and_immutable_registration(self):
        entries = ET.parse(RESOURCES / 'db/changelog/master.xml').getroot()
        files = [e.attrib['file'] for e in entries if e.tag.endswith('include')]
        self.assertEqual(1, files.count(MIGRATION))
        self.assertTrue(files[files.index(MIGRATION) - 1].startswith('db/migration/085-'))
        self.assertTrue(files[files.index(MIGRATION) + 1].startswith('db/migration/087-'))
        guard = (PERSISTENCE / 'java/com/ruoyi/medical/persistence/ProtectedTenantInterceptor.java').read_text().split('private static boolean immutableTable', 1)[0]
        for name in TABLES:
            self.assertIn('"' + name + '"', guard)

    def test_exact_mapper_ids_and_current_subject_guard(self):
        root = ET.parse(PERSISTENCE / 'resources/medical-mappers/FormalAgentAcceptanceMapper.xml').getroot()
        self.assertEqual({'acceptance', 'insertAcceptance', 'decideCandidate'},
                         {e.attrib['id'] for e in root if e.tag in ('select', 'insert', 'update', 'delete')})
        scope = (PERSISTENCE / 'java/com/ruoyi/medical/persistence/TenantScope.java').read_text()
        self.assertIn('com.ruoyi.medical.persistence.mapper.FormalAgentAcceptanceMapper.', scope)


if __name__ == '__main__':
    unittest.main()
