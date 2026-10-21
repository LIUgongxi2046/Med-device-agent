"""Offline JSQLParser/sqlglot and mapper contracts; no MySQL/runtime acceptance claim."""
import hashlib
import json
import re
import unittest
from sqlglot import exp
from test_simulation_input_artifact_migration import ROOT, RESOURCES, inspect_migration, foreign_keys, assert_registered
from test_simulation_preparation_migration import mapper_statements, inserted_values, PERSISTENCE

MIGRATION = 'db/migration/066-gru-records.sql'
TABLES = {'gru_conversation_context', 'gru_message_record'}

class GruRecordsMigrationTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.parsed, cls.tables = inspect_migration(RESOURCES / MIGRATION)
        cls.mapper, cls.statements = mapper_statements('GruMapper')

    def test_additive_registration_and_exact_current_count(self):
        assert_registered(self, MIGRATION, 'db/migration/065-simulation-evaluation-preparation.sql', self.tables)
        self.assertEqual(TABLES, set(self.tables))
        self.assertEqual(2, len(self.parsed))
        self.assertTrue(all('create' in value for value in self.parsed))
        manifest = json.loads((RESOURCES / 'db/migration/coverage.json').read_text())
        self.assertEqual(526, sum(map(len, manifest['required_tables'].values())))
        self.assertEqual(565, sum(map(len, manifest['required_tables'].values())) + len(manifest['platform_tables']) + len(manifest['framework_tables']))
        self.assertNotRegex((RESOURCES / MIGRATION).read_text(), r'(?im)^\s*(ALTER|INSERT|UPDATE|DELETE|DROP|TRUNCATE|GRANT|REVOKE|REPLACE)\b')

    def test_001_through_065_bytes_frozen_against_part13_source_package(self):
        # Independently byte-compared with delivery/Med-device-agent-20261018-part13-source-and-validation.zip.
        paths = sorted(p for p in (RESOURCES / 'db/migration').iterdir()
                       if p.suffix in ('.sql', '.xml') and p.name[:3].isdigit() and int(p.name[:3]) <= 65)
        self.assertEqual(64, len(paths))
        digest = hashlib.sha256(b''.join(p.name.encode() + b'\0' + p.read_bytes() + b'\0' for p in paths)).hexdigest()
        self.assertEqual('a602b4a3faa10c2ded50edf30ef3f9fed12aefc10a5b5539236c0030c522482d', digest)

    def test_exact_tenant_project_context_and_message_foreign_keys(self):
        expected = {
          'gru_conversation_context': {
            ('operation_record', ('tenant_id','operation_id'), ('tenant_id','operation_id')),
            ('dependency_access_set', ('tenant_id','project_key','dependency_access_set_id'), ('tenant_id','project_key','access_set_id')),
            ('conversation', ('tenant_id','project_id','conversation_id'), ('tenant_id','project_id','conversation_id')),
            ('document_head', ('tenant_id','project_id','document_id'), ('tenant_id','project_id','document_id')),
            ('document_revision_meta', ('tenant_id','project_id','document_id','document_revision'), ('tenant_id','project_id','document_id','document_revision')),
            ('scope_snapshot', ('tenant_id','project_id','scope_snapshot_id'), ('tenant_id','project_id','snapshot_id')),
            ('tenant_member', ('tenant_id','created_by'), ('tenant_id','user_id'))},
          'gru_message_record': {
            ('conversation_message', ('tenant_id','project_id','conversation_id','message_id'), ('tenant_id','project_id','conversation_id','message_id')),
            ('tenant_member', ('tenant_id','created_by'), ('tenant_id','user_id'))}}
        for name, table in self.tables.items():
            self.assertEqual(expected[name], foreign_keys(table))
            primary = tuple(c.name for c in next(table.find_all(exp.PrimaryKey)).expressions)
            self.assertEqual(('tenant_id', 'conversation_id' if name.endswith('context') else 'message_id'), primary)
            columns = {c.name:c for c in table.this.expressions if isinstance(c, exp.ColumnDef)}
            for column, definition in columns.items():
                if column != 'project_key':
                    self.assertTrue(any(isinstance(c.kind, exp.NotNullColumnConstraint) for c in definition.constraints), column)
            self.assertFalse({'updated_at','revision'} & columns.keys())
            checks = ' AND '.join(c.this.sql(dialect='mysql') for c in table.find_all(exp.CheckColumnConstraint))
            for column in ('canonical_request_json','receipt_json'):
                self.assertIn('JSON_VALID('+column+')', checks)
            self.assertIn("schema_version = '1.0.0'", checks)
        checks = ' AND '.join(c.this.sql(dialect='mysql') for c in self.tables['gru_message_record'].find_all(exp.CheckColumnConstraint))
        self.assertIn("execution_state = 'NOT_EXECUTED'", checks)
        self.assertIn('message_sequence BETWEEN 2 AND 501', checks)
        self.assertIn("intent IN ('QUERY', 'RETRIEVE', 'CHECK', 'PROPOSE_EDIT')", checks)
        for name, expected_key in [('gru_conversation_context',('tenant_id','operation_id')),('gru_message_record',('tenant_id','conversation_id','message_sequence'))]:
            self.assertEqual({expected_key}, {tuple(c.name for c in key.this.expressions) for key in self.tables[name].find_all(exp.UniqueColumnConstraint)})

    def test_sidecar_insert_only_complete_columns_and_actor_scoped_read(self):
        java = (PERSISTENCE / 'java/com/ruoyi/medical/persistence/mapper/GruMapper.java').read_text()
        self.assertEqual(set(self.statements), set(re.findall(r'\b(\w+)\(Map<String,Object> values\)',java)))
        for name, (element, statement, text) in self.statements.items():
            if element.tag == 'select':
                self.assertEqual('false',element.attrib['useCache']); self.assertEqual('true',element.attrib['flushCache'])
            if isinstance(statement,exp.Insert):
                self.assertNotIn('tenant_id',inserted_values(statement))
            if isinstance(statement,exp.Update):
                self.assertEqual('advanceConversation',name); self.assertEqual('conversation',statement.this.name)
                for guard in ('owner_id=#{userId}',"status='ACTIVE'",'revision=#{expectedRevision}'):
                    self.assertIn(guard,text)
            self.assertNotIsInstance(statement,exp.Delete)
        for method, table in [('insertContext','gru_conversation_context'),('insertMessageRecord','gru_message_record')]:
            columns = {c.name for c in self.tables[table].this.expressions if isinstance(c,exp.ColumnDef)}
            self.assertEqual(columns-{'tenant_id','project_key'},set(inserted_values(self.statements[method][1])))
        for method in ('head','context','listed','messages'):
            self.assertIn('owner_id=#{userId}',self.statements[method][2])
        self.assertIn('subject_id=#{userId}',self.statements['operation'][2])
        self.assertIn('subject_id=#{creatorId}',self.statements['operationById'][2])
        self.assertIn('idempotency_key=#{key}',self.statements['operation'][2])
        self.assertIn('operation_id=#{operationId}',self.statements['operationById'][2])
        messages = self.statements['messages'][2]
        for column in ('tenant_id','project_id','conversation_id','message_id','schema_version','schema_manifest_sha256','created_by','created_at','classification'):
            self.assertIn('r.'+column+'=m.'+column,messages)
        self.assertIsInstance(inserted_values(self.statements['insertMessage'][1])['run_id'],exp.Null)
        self.assertEqual('COMPLETED',inserted_values(self.statements['saveOperation'][1])['state'].this)

    def test_list_counts_bind_owner_and_full_record_metadata_without_history_bodies(self):
        statement = self.statements['messageCounts'][1]
        text = self.statements['messageCounts'][2]
        self.assertEqual({'message_count', 'record_count'}, {node.alias for node in statement.expressions})
        self.assertEqual({'m.message_id','r.message_id'}, {node.this.sql() for node in statement.find_all(exp.Count)})
        self.assertEqual({'conversation','conversation_message','gru_message_record'}, {table.name for table in statement.find_all(exp.Table)})
        self.assertEqual(['LEFT','LEFT'], [join.side for join in statement.args['joins']])
        for column in ('tenant_id','conversation_id','project_id'):
            self.assertIn('m.'+column+'=c.'+column,text)
        for column in ('tenant_id','message_id','project_id','conversation_id','schema_version','schema_manifest_sha256','created_by','created_at','classification'):
            self.assertIn('r.'+column+'=m.'+column,text)
        self.assertIn('c.owner_id=#{userId}',text)
        self.assertIn('c.conversation_id=#{conversationId}',text)
        self.assertEqual(['c.conversation_id'], [node.sql() for node in statement.args['group'].expressions])

    def test_append_recovery_uses_actual_current_locked_actor_bound_reads(self):
        self.assertEqual(21, len(self.statements))
        cases = {
            'lockHead': ('conversation', {'owner_id': '#{userId}', 'conversation_id': '#{conversationId}'}),
            'recoveryOperation': ('operation_record', {'subject_id': '#{userId}', 'idempotency_key': '#{key}'})}
        for name, (table, parameters) in cases.items():
            element, statement, text = self.statements[name]
            self.assertEqual('select', element.tag)
            self.assertIsInstance(statement, exp.Select)
            self.assertEqual([table], [node.name for node in statement.find_all(exp.Table)])
            locks = statement.args.get('locks', [])
            self.assertEqual(1, len(locks), name)
            self.assertIsInstance(locks[0], exp.Lock)
            self.assertIs(locks[0].args.get('update'), True, name)
            self.assertFalse(locks[0].args.get('wait'), name)
            predicates = list(statement.args['where'].find_all(exp.EQ))
            expected_columns = set(parameters) | ({'operation_name'} if name == 'recoveryOperation' else set())
            self.assertEqual(expected_columns, {node.this.name for node in predicates})
            for column, parameter in parameters.items():
                self.assertIn(column+'='+parameter, text)
                predicate = next(node for node in predicates if node.this.name == column)
                self.assertIsInstance(predicate.expression, exp.Placeholder)
            self.assertFalse(list(statement.args['where'].find_all(exp.Or)))
        operation = self.statements['recoveryOperation'][1]
        name_predicate = next(node for node in operation.args['where'].find_all(exp.EQ) if node.this.name == 'operation_name')
        self.assertIsInstance(name_predicate.expression, exp.Literal)
        self.assertEqual('GRU-RECORD-APPEND', name_predicate.expression.this)

    def test_server_tenant_actor_and_immutable_registration(self):
        scope=(PERSISTENCE/'java/com/ruoyi/medical/persistence/TenantScope.java').read_text()
        self.assertIn('statementId.startsWith("com.ruoyi.medical.persistence.mapper.GruMapper.")',scope)
        interceptor=(PERSISTENCE/'java/com/ruoyi/medical/persistence/ProtectedTenantInterceptor.java').read_text()
        immutable=re.search(r'IMMUTABLE_TABLES\s*=\s*java.util.Set.of\((.*?)\);',interceptor,re.S).group(1)
        for table in TABLES:self.assertIn('"'+table+'"',immutable)
        self.assertIn('if (immutableTable(update.getTable().getName())) throw denied()',interceptor)
        self.assertIn('if (immutableTable(delete.getTable().getName())) throw denied()',interceptor)

if __name__=='__main__':unittest.main()
