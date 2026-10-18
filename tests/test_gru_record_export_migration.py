"""070 real-parser storage/mapper contracts. Offline, not live MySQL acceptance."""
import json
import re
import unittest

import sqlglot
from sqlglot import exp

from test_document_comparison_migration import bound_ast, java_method, predicate
from test_migration_bundle import sqlglot_migration_source
from test_simulation_input_artifact_migration import (
    ROOT, RESOURCES, assert_registered, foreign_keys, inspect_migration,
)
from test_simulation_preparation_migration import mapper_statements


MIGRATION = 'db/migration/070-gru-record-export.sql'
JAVA = ROOT / 'business/medical-persistence/src/main/java/com/ruoyi/medical/persistence'
INTENT = 'gru_record_export_intent'
ATTEMPT = 'gru_record_export_attempt'
COMPLETED = 'gru_record_export'
TABLES = {INTENT, ATTEMPT, COMPLETED}
FIXED = {'mode': 'RECORD_ONLY', 'execution_state': 'NOT_EXECUTED',
         'format': 'JSON', 'media_type': 'application/json'}
RECORD_COLUMNS = {
    INTENT: '''tenant_id project_id export_id conversation_id owner_id conversation_revision
        document_id document_revision scope_snapshot_id canonical_request request_body_sha256
        operation_request_sha256 operation_id idempotency_key session_id context_json basis_json
        message_manifest_json message_manifest_sha256 message_count canonical_content content_sha256
        size_bytes write_intent_json artifact_id dependency_access_set_id dependency_manifest_sha256
        schema_version schema_manifest_sha256 created_by created_at deadline classification'''.split(),
    COMPLETED: '''tenant_id project_id export_id operation_id artifact_id dependency_access_set_id
        dependency_manifest_sha256 object_reference content_sha256 size_bytes receipt_json audit_event_id
        outbox_event_id schema_version schema_manifest_sha256 created_by created_at classification'''.split(),
    ATTEMPT: '''tenant_id project_id export_id operation_id created_by attempted_at
        schema_version schema_manifest_sha256 classification created_at'''.split(),
}
JSON_COLUMNS = {
    INTENT: '''canonical_request context_json basis_json message_manifest_json canonical_content
        write_intent_json'''.split(),
    COMPLETED: ['object_reference', 'receipt_json'],
    ATTEMPT: [],
}
INTEGER_COLUMNS = {'conversation_revision', 'message_count', 'size_bytes'}
DATE_COLUMNS = {'created_at', 'deadline', 'attempted_at'}
ATTEMPT_KEY = ('tenant_id', 'project_id', 'export_id', 'operation_id')
BINDING_KEY = ATTEMPT_KEY + ('artifact_id', 'dependency_access_set_id', 'content_sha256')
MEMBER_KEY = ('tenant_id', 'created_by')
MEMBER_REFERENCE = ('tenant_id', 'user_id')
OPERATION_KEY = ('tenant_id', 'operation_id')
OWNER = 'i.owner_id=#{userId} AND i.created_by=#{userId}'
CONVERSATION_JOIN = ('JOIN conversation c ON c.tenant_id=i.tenant_id AND c.project_id=i.project_id '
                     'AND c.conversation_id=i.conversation_id')
OPERATION_COLUMNS = 'operation_id request_sha256 state http_status response_ref target_id schema_version schema_manifest_sha256'.split()
OPERATION_IDENTITY = ("operation_id=#{operationId} AND subject_id=#{userId} AND created_by=#{userId} "
                      "AND operation_name='GRU-RECORD-EXPORT' AND idempotency_key=#{key} "
                      'AND request_sha256=#{requestHash} AND target_id=#{exportId}')
INTENT_OPERATION_MATCH = (
    'i.tenant_id=operation_record.tenant_id AND i.operation_id=operation_record.operation_id '
    'AND i.project_id=#{projectId} AND i.export_id=operation_record.target_id '
    'AND i.owner_id=operation_record.subject_id AND i.created_by=operation_record.created_by '
    'AND i.idempotency_key=operation_record.idempotency_key '
    'AND i.operation_request_sha256=operation_record.request_sha256 '
    'AND i.schema_manifest_sha256=operation_record.schema_manifest_sha256 '
    'AND i.created_at=operation_record.created_at AND i.classification=operation_record.classification')


def property_name(column):
    special = {'scope_snapshot_id': 'snapshotId', 'dependency_access_set_id': 'accessSetId',
               'schema_manifest_sha256': 'schemaManifest'}
    first, *rest = column.split('_')
    return special.get(column, first + ''.join(word.title() for word in rest))


def projection(table, alias):
    return ','.join(alias + '.' + column for column in RECORD_COLUMNS[table])


def columns(table):
    return {column.name: column for column in table.this.expressions if isinstance(column, exp.ColumnDef)}


def unique_keys(statement):
    keys = {tuple(column.name for column in key.this.expressions)
            for key in statement.find_all(exp.UniqueColumnConstraint)}
    keys.update(tuple(column.name for column in key.expressions) for key in statement.find_all(exp.PrimaryKey))
    return keys


class GruRecordExportMigrationTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.sql = (RESOURCES / MIGRATION).read_text()
        # Intentionally uses the ordinary JSQLParser path, never the 068 workaround.
        cls.parsed, cls.tables = inspect_migration(RESOURCES / MIGRATION)
        cls.mapper, cls.statements = mapper_statements('GruRecordExportMapper')
        cls.mapper_java = (JAVA / 'mapper/GruRecordExportMapper.java').read_text()
        cls.service_java = (JAVA / 'GruRecordExportService.java').read_text()

    def assert_sql(self, name, expected):
        self.assertEqual(bound_ast(expected), bound_ast(self.statements[name][2]), name)

    def assert_insert(self, name, table, values, source=None):
        head = 'INSERT INTO ' + table + '(' + ','.join(values) + ') '
        tail = ('VALUES(' + ','.join(values.values()) + ')' if source is None else
                'SELECT ' + ','.join(values.values()) + ' ' + source)
        self.assert_sql(name, head + tail)

    def test_actual_jsqlparser_three_additive_tables_and_registration(self):
        assert_registered(self, MIGRATION, 'db/migration/069-document-comparison.sql', self.tables)
        self.assertEqual(TABLES, set(self.tables))
        self.assertEqual(3, len(self.parsed))
        self.assertTrue(all(set(item) == {'create'} for item in self.parsed))
        self.assertEqual(['medical-agent:070-intent', 'medical-agent:070-attempt', 'medical-agent:070-completed'],
                         re.findall(r'(?m)^--changeset (\S+) dbms:mysql runInTransaction:false failOnError:true$', self.sql))
        coverage = json.loads((RESOURCES / 'db/migration/coverage.json').read_text())
        self.assertEqual(TABLES, set(coverage['required_tables']['gru_record_export']))
        self.assertNotRegex(self.sql, r'(?im)^\s*(ALTER|INSERT|UPDATE|DELETE|REPLACE|TRUNCATE|DROP|GRANT|REVOKE)\b')

    def test_exact_columns_and_complete_audit_schema_metadata(self):
        for name, table in self.tables.items():
            actual = columns(table)
            expected = set(RECORD_COLUMNS[name]) | (set(FIXED) | {'project_key'} if name != ATTEMPT else set())
            self.assertEqual(expected, set(actual), name)
            self.assertLessEqual({'created_by', 'created_at', 'classification', 'schema_version',
                                 'schema_manifest_sha256'}, actual.keys(), name)
            self.assertFalse({'updated_at', 'revision', 'status'} & actual.keys())
            for column, definition in actual.items():
                if column != 'project_key':
                    self.assertTrue(any(isinstance(c.kind, exp.NotNullColumnConstraint)
                                        for c in definition.constraints), (name, column))
                if column not in FIXED:
                    self.assertFalse(list(definition.find_all(exp.DefaultColumnConstraint)), (name, column))

    def test_exact_types_case_sensitive_keys_and_generated_scope(self):
        text_types = {'idempotency_key': 'VARCHAR(128)', 'session_id': 'VARCHAR(255)',
                      'schema_version': 'VARCHAR(32)', 'classification': 'VARCHAR(24)',
                      'project_key': 'VARCHAR(36)', 'mode': 'VARCHAR(24)', 'execution_state': 'VARCHAR(24)',
                      'format': 'VARCHAR(12)', 'media_type': 'VARCHAR(128)'}
        for name, table in self.tables.items():
            for column, definition in columns(table).items():
                expected = ('LONGTEXT' if column in JSON_COLUMNS[name] else
                            'BIGINT UNSIGNED' if column in INTEGER_COLUMNS else
                            'DATETIME(6)' if column in DATE_COLUMNS else
                            'VARCHAR(20)' if name == ATTEMPT and column == 'schema_version' else
                            'VARCHAR(32)' if name == ATTEMPT and column == 'classification' else
                            text_types.get(column, 'CHAR(64)' if column.endswith('sha256') else 'CHAR(36)'))
                self.assertEqual(expected, definition.kind.sql(dialect='mysql'), (name, column))
                if (expected.startswith('CHAR(') or column in ('project_key', 'idempotency_key', 'session_id')
                        or name == ATTEMPT and column in ('schema_version', 'classification')):
                    charsets = [c.this.name for c in definition.find_all(exp.CharacterSetColumnConstraint)]
                    self.assertEqual(['utf8mb4' if column == 'session_id' else 'ascii'], charsets)
            self.assertEqual({'ENGINE=InnoDB', 'DEFAULT CHARACTER SET=utf8mb4', 'COLLATE=utf8mb4_0900_as_cs'},
                             {p.sql(dialect='mysql') for p in table.args['properties'].expressions})
            if name != ATTEMPT:
                computed = list(columns(table)['project_key'].find_all(exp.ComputedColumnConstraint))
                self.assertEqual(1, len(computed))
                self.assertEqual(exp.column('project_id'), computed[0].this)
                self.assertTrue(computed[0].args['persisted'])
        for name, collation in (('idempotency_key', 'ascii_bin'), ('session_id', 'utf8mb4_0900_bin')):
            self.assertEqual([collation], [c.this.name for c in columns(self.tables[INTENT])[name]
                                          .find_all(exp.CollateColumnConstraint)])

    def test_primary_and_full_immutable_uniqueness(self):
        primary = ('tenant_id', 'project_id', 'export_id')
        expected = {
            INTENT: {primary, ('tenant_id', 'export_id'), ('tenant_id', 'owner_id', 'idempotency_key'),
                     OPERATION_KEY, ATTEMPT_KEY, BINDING_KEY},
            ATTEMPT: {primary, OPERATION_KEY, ATTEMPT_KEY},
            COMPLETED: {primary, OPERATION_KEY},
        }
        for name, table in self.tables.items():
            self.assertEqual([primary], [tuple(c.name for c in key.expressions)
                                        for key in table.find_all(exp.PrimaryKey)])
            self.assertEqual(expected[name], unique_keys(table), name)
            self.assertEqual(len(expected[name]) - 1, len(list(table.find_all(exp.UniqueColumnConstraint))), name)

    def test_intent_full_conversation_document_snapshot_dependency_and_actor_foreign_keys(self):
        self.assertEqual({
            ('conversation', ('tenant_id', 'project_id', 'conversation_id'), ('tenant_id', 'project_id', 'conversation_id')),
            ('gru_conversation_context', ('tenant_id', 'conversation_id'), ('tenant_id', 'conversation_id')),
            ('document_revision_meta', ('tenant_id', 'project_id', 'document_id', 'document_revision'),
             ('tenant_id', 'project_id', 'document_id', 'document_revision')),
            ('scope_snapshot', ('tenant_id', 'project_id', 'scope_snapshot_id'), ('tenant_id', 'project_id', 'snapshot_id')),
            ('tenant_member', ('tenant_id', 'owner_id'), MEMBER_REFERENCE),
            ('tenant_member', MEMBER_KEY, MEMBER_REFERENCE),
            ('operation_record', OPERATION_KEY, OPERATION_KEY),
            ('dependency_access_manifest', ('tenant_id', 'project_key', 'dependency_access_set_id', 'dependency_manifest_sha256'),
             ('tenant_id', 'project_key', 'access_set_id', 'manifest_sha256')),
        }, foreign_keys(self.tables[INTENT]))

    def test_attempt_and_completed_cannot_cross_intent_artifact_or_operation(self):
        self.assertEqual({(INTENT, ATTEMPT_KEY, ATTEMPT_KEY), ('tenant_member', MEMBER_KEY, MEMBER_REFERENCE)},
                         foreign_keys(self.tables[ATTEMPT]))
        self.assertEqual({
            (INTENT, BINDING_KEY, BINDING_KEY), (ATTEMPT, ATTEMPT_KEY, ATTEMPT_KEY),
            ('artifact', ('tenant_id', 'project_key', 'artifact_id', 'dependency_access_set_id'),
             ('tenant_id', 'project_key', 'artifact_id', 'dependency_access_set_id')),
            ('artifact', ('tenant_id', 'project_key', 'artifact_id', 'content_sha256'),
             ('tenant_id', 'project_key', 'artifact_id', 'sha256')),
            ('audit_event', ('tenant_id', 'audit_event_id'), ('tenant_id', 'audit_id')),
            ('outbox_event', ('tenant_id', 'outbox_event_id'), ('tenant_id', 'event_id')),
            ('operation_record', OPERATION_KEY, OPERATION_KEY),
            ('dependency_access_manifest', ('tenant_id', 'project_key', 'dependency_access_set_id', 'dependency_manifest_sha256'),
             ('tenant_id', 'project_key', 'access_set_id', 'manifest_sha256')),
            ('tenant_member', MEMBER_KEY, MEMBER_REFERENCE),
        }, foreign_keys(self.tables[COMPLETED]))

    def test_every_foreign_key_references_actual_complete_unique_parent_and_matching_types(self):
        # Only the real defining migrations and relevant additive parent keys are needed.
        # This does not run the global bundle or invoke its 068 compatibility parser.
        paths = [ROOT / 'data-design/core.sql'] + [RESOURCES / ('db/migration/' + name) for name in (
            '002-identity-sources.sql', '005-document-evidence.sql', '009-conversation-events-receipts.sql',
            '025-artifact-content-registry.sql', '066-gru-records.sql')]
        statements = [statement for path in paths for statement in sqlglot.parse(sqlglot_migration_source(path), read='mysql')]
        parents = {statement.this.this.name: statement for statement in statements
                   if isinstance(statement, exp.Create) and statement.kind == 'TABLE'}
        parents.update(self.tables)
        keys = {name: unique_keys(table) for name, table in parents.items()}
        for statement in statements:
            if isinstance(statement, exp.Alter):
                keys.setdefault(statement.this.name, set()).update(unique_keys(statement))
        count = 0
        for name, table in self.tables.items():
            references = foreign_keys(table)
            self.assertEqual(len(references), len(list(table.find_all(exp.ForeignKey))), name)
            for parent, local, remote in references:
                count += 1
                with self.subTest(table=name, parent=parent, local=local):
                    self.assertEqual('tenant_id', local[0])
                    self.assertEqual('tenant_id', remote[0])
                    self.assertIn(remote, keys[parent])
                    self.assertEqual(len(local), len(remote))
                    for left, right in zip(local, remote, strict=True):
                        a, b = columns(table)[left], columns(parents[parent])[right]
                        self.assertEqual(a.kind, b.kind)
                        for constraint in (exp.CharacterSetColumnConstraint, exp.CollateColumnConstraint):
                            self.assertEqual([c.this for c in a.find_all(constraint)], [c.this for c in b.find_all(constraint)])
        self.assertEqual(19, count)

    def test_closed_record_only_defaults_and_schema_json_hash_checks(self):
        for name in (INTENT, COMPLETED):
            table = self.tables[name]
            for column, value in FIXED.items():
                defaults = list(columns(table)[column].find_all(exp.DefaultColumnConstraint))
                self.assertEqual([exp.Literal.string(value)], [default.this for default in defaults])
            checks = {check.this for check in table.find_all(exp.CheckColumnConstraint)}
            expected = ["schema_version='1.0.0'", "classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH')",
                        "mode='RECORD_ONLY' AND execution_state='NOT_EXECUTED' AND format='JSON' AND media_type='application/json'",
                        'size_bytes > 0']
            expected += ['JSON_VALID(' + column + ')' for column in JSON_COLUMNS[name]]
            expected += [column + " REGEXP '^[a-f0-9]{64}$'" for column in RECORD_COLUMNS[name] if column.endswith('sha256')]
            if name == INTENT:
                expected += ['owner_id=created_by', 'CHAR_LENGTH(idempotency_key) BETWEEN 16 AND 128',
                             'message_count BETWEEN 0 AND 500', 'conversation_revision=message_count+1',
                             'deadline>created_at', "JSON_TYPE(CAST(message_manifest_json AS JSON))='ARRAY'",
                             'JSON_LENGTH(CAST(message_manifest_json AS JSON))=message_count',
                             'OCTET_LENGTH(canonical_content)=size_bytes']
            else:
                expected += ["JSON_UNQUOTE(JSON_EXTRACT(object_reference,'$.sha256')) <=> content_sha256",
                             "JSON_EXTRACT(object_reference,'$.size_bytes') <=> size_bytes",
                             "JSON_UNQUOTE(JSON_EXTRACT(object_reference,'$.media_type')) <=> media_type"]
            self.assertEqual(len(expected), len(list(table.find_all(exp.CheckColumnConstraint))), name)
            self.assertEqual({predicate(check) for check in expected}, checks, name)

    def test_attempt_schema_classification_and_creation_metadata_checks(self):
        checks = {check.this for check in self.tables[ATTEMPT].find_all(exp.CheckColumnConstraint)}
        self.assertEqual(4, len(list(self.tables[ATTEMPT].find_all(exp.CheckColumnConstraint))))
        self.assertEqual({predicate("schema_version='1.0.0'"),
                          predicate("schema_manifest_sha256 REGEXP '^[a-f0-9]{64}$'"),
                          predicate("classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH')"),
                          predicate('created_at=attempted_at')}, checks)

    def test_exact_twenty_three_mapper_ids_and_uncached_actor_registered_reads(self):
        expected = {'attempt', 'artifactOwner', 'intent', 'intentById', 'intentByKey', 'intentByOperation',
                    'registration', 'registrationByOperation', 'operation', 'operationById', 'operationRevision',
                    'audit', 'event', 'insertAttempt', 'insertBinding', 'insertIntent', 'insertRegistration',
                    'startOperation', 'markAttempted', 'completeOperation', 'insertVersion', 'insertArtifact', 'insertLineage'}
        self.assertEqual(23, len(self.statements))
        self.assertEqual(expected, set(self.statements))
        self.assertEqual(expected, set(re.findall(r'\b(\w+)\(Map<String,\s*Object> values\)', self.mapper_java)))
        self.assertEqual('com.ruoyi.medical.persistence.mapper.GruRecordExportMapper', self.mapper.attrib['namespace'])
        self.assertIsNone(self.mapper.find('cache'))
        self.assertIsNone(self.mapper.find('cache-ref'))
        for name, (element, statement, _) in self.statements.items():
            if isinstance(statement, exp.Select):
                self.assertEqual('false', element.attrib['useCache'], name)
                self.assertEqual('true', element.attrib['flushCache'], name)
            if isinstance(statement, exp.Insert):
                inserted = {column.name for column in statement.this.expressions}
                self.assertFalse({'tenant_id', 'project_key'} & inserted, name)
            if isinstance(statement, (exp.Update, exp.Delete)):
                self.assertIsInstance(statement, exp.Update)
                self.assertEqual('operation_record', statement.this.name, name)
                self.assertIn(name, ('markAttempted', 'completeOperation'))

    def test_complete_result_maps_match_java_record_constructor_types_and_order(self):
        for result_name, table in (('Intent', INTENT), ('Registration', COMPLETED), ('Attempt', ATTEMPT)):
            result = self.mapper.find("resultMap[@id='" + result_name + "']")
            self.assertEqual('com.ruoyi.medical.persistence.mapper.GruRecordExportMapper$' + result_name, result.attrib['type'])
            args = list(result.find('constructor'))
            self.assertEqual(RECORD_COLUMNS[table], [arg.attrib['column'] for arg in args])
            fields = re.search(r'\brecord ' + result_name + r'\(([^)]*)\)', self.mapper_java)[1]
            expected = []
            for column, arg in zip(RECORD_COLUMNS[table], args, strict=True):
                kind = ('LocalDateTime' if column in DATE_COLUMNS else 'BigInteger' if column in INTEGER_COLUMNS else 'String')
                prefix = {'LocalDateTime': 'java.time.', 'BigInteger': 'java.math.', 'String': 'java.lang.'}[kind]
                self.assertEqual(prefix + kind, arg.attrib['javaType'])
                expected.append((kind, property_name(column)))
            self.assertEqual(expected, [tuple(field.strip().split()) for field in fields.split(',')])

    def test_intent_reads_bind_owner_creator_and_current_conversation_owner(self):
        head = 'SELECT ' + projection(INTENT, 'i') + ' FROM ' + INTENT + ' i '
        for name, field, parameter in (('intent', 'export_id', 'exportId'), ('intentByOperation', 'operation_id', 'operationId')):
            self.assert_sql(name, head + CONVERSATION_JOIN + ' WHERE i.project_id=#{projectId} AND i.' + field +
                            '=#{' + parameter + '} AND ' + OWNER + ' AND c.owner_id=#{userId}')
        self.assert_sql('intentByKey', head + 'WHERE ' + OWNER + ' AND i.idempotency_key=#{key}')
        self.assert_sql('intentById', head + 'WHERE ' + OWNER + ' AND i.export_id=#{exportId}')

    def test_registration_and_attempt_reads_use_full_intent_join_and_owner(self):
        head = ('SELECT ' + projection(COMPLETED, 'r') + ' FROM ' + COMPLETED + ' r JOIN ' + INTENT + ' i '
                'ON i.tenant_id=r.tenant_id AND i.project_id=r.project_id AND i.export_id=r.export_id '
                'AND i.operation_id=r.operation_id ' + CONVERSATION_JOIN)
        for name, field, parameter in (('registration', 'export_id', 'exportId'),
                                       ('registrationByOperation', 'operation_id', 'operationId')):
            self.assert_sql(name, head + ' WHERE r.project_id=#{projectId} AND r.' + field + '=#{' + parameter + '}'
                            ' AND ' + OWNER + ' AND c.owner_id=#{userId}')
        self.assert_sql('attempt', 'SELECT ' + projection(ATTEMPT, 'a') + ' FROM ' + ATTEMPT + ' a JOIN ' + INTENT +
                        ' i ON i.tenant_id=a.tenant_id AND i.project_id=a.project_id AND i.export_id=a.export_id '
                        'AND i.operation_id=a.operation_id WHERE a.project_id=#{projectId} AND a.export_id=#{exportId} '
                        'AND a.operation_id=#{operationId} AND a.created_by=#{userId} AND ' + OWNER)

    def test_intent_insert_requires_active_fixed_conversation_and_matching_prepared_operation(self):
        values = {column: '#{row.' + property_name(column) + '}' for column in RECORD_COLUMNS[INTENT] if column != 'tenant_id'}
        self.assert_insert('insertIntent', INTENT, values,
            'FROM conversation c JOIN gru_conversation_context g ON g.tenant_id=c.tenant_id '
            'AND g.project_id=c.project_id AND g.conversation_id=c.conversation_id '
            'JOIN operation_record o ON o.tenant_id=c.tenant_id AND o.operation_id=#{row.operationId} '
            'WHERE c.project_id=#{row.projectId} AND c.conversation_id=#{row.conversationId} AND c.owner_id=#{userId} '
            "AND c.status='ACTIVE' AND c.revision=#{row.conversationRevision} AND g.document_id=#{row.documentId} "
            'AND g.document_revision=#{row.documentRevision} AND g.scope_snapshot_id=#{row.snapshotId} '
            'AND #{row.ownerId}=#{userId} AND #{row.createdBy}=#{userId} AND o.subject_id=#{userId} '
            "AND o.created_by=#{userId} AND o.operation_name='GRU-RECORD-EXPORT' AND o.idempotency_key=#{row.idempotencyKey} "
            "AND o.target_id=#{row.exportId} AND o.request_sha256=#{row.operationRequestSha256} AND o.state='PROCESSING' "
            'AND o.revision=1 AND o.created_at=#{row.createdAt} AND o.schema_manifest_sha256=#{row.schemaManifest} '
            'AND o.classification=#{row.classification}')

    def test_completed_insert_requires_attempt_matching_artifact_and_exact_reference(self):
        values = {column: '#{row.' + property_name(column) + '}' for column in RECORD_COLUMNS[COMPLETED] if column != 'tenant_id'}
        self.assert_insert('insertRegistration', COMPLETED, values,
            'FROM ' + INTENT + ' i JOIN ' + ATTEMPT + ' t ON t.tenant_id=i.tenant_id AND t.project_id=i.project_id '
            'AND t.export_id=i.export_id AND t.operation_id=i.operation_id JOIN artifact a ON a.tenant_id=i.tenant_id '
            'AND a.project_id=i.project_id AND a.artifact_id=i.artifact_id WHERE i.project_id=#{row.projectId} '
            'AND i.export_id=#{row.exportId} AND i.operation_id=#{row.operationId} AND ' + OWNER +
            ' AND t.created_by=#{userId} AND t.schema_version=i.schema_version '
            'AND t.schema_manifest_sha256=i.schema_manifest_sha256 AND t.classification=i.classification '
            'AND t.created_at=t.attempted_at AND #{row.createdBy}=#{userId} AND i.artifact_id=#{row.artifactId} '
            'AND i.dependency_access_set_id=#{row.accessSetId} AND i.dependency_manifest_sha256=#{row.dependencyManifestSha256} '
            'AND i.content_sha256=#{row.contentSha256} AND i.size_bytes=#{row.sizeBytes} '
            'AND i.schema_manifest_sha256=#{row.schemaManifest} AND i.classification=#{row.classification} '
            'AND a.sha256=i.content_sha256 AND a.size_bytes=i.size_bytes AND a.dependency_access_set_id=i.dependency_access_set_id '
            "AND a.document_revision=i.document_revision AND a.run_id IS NULL AND a.artifact_kind='GRU_RECORD_EXPORT' "
            'AND a.created_by=#{userId} AND a.object_ref=CAST(#{row.objectReference} AS JSON)')

    def test_operation_reads_bind_owner_creator_and_revision_locks_full_intent_identity(self):
        head = 'SELECT ' + ','.join('o.' + column for column in OPERATION_COLUMNS) + ' FROM operation_record o WHERE '
        scope = "o.subject_id=#{userId} AND o.created_by=#{userId} AND o.operation_name='GRU-RECORD-EXPORT' AND "
        self.assert_sql('operation', head + scope + 'o.idempotency_key=#{key}')
        self.assert_sql('operationById', head + scope + 'o.operation_id=#{operationId}')
        self.assert_sql('operationRevision', 'SELECT revision FROM operation_record WHERE ' + OPERATION_IDENTITY +
                        ' AND EXISTS(SELECT 1 FROM ' + INTENT + ' i WHERE ' + INTENT_OPERATION_MATCH + ') FOR UPDATE')
        locks = self.statements['operationRevision'][1].args.get('locks', [])
        self.assertEqual(1, len(locks))
        self.assertTrue(locks[0].args.get('update'))
        self.assertEqual('java.math.BigInteger', self.statements['operationRevision'][0].attrib['resultType'])

    def test_operation_insert_prepares_revision_one_without_write_claim(self):
        self.assert_insert('startOperation', 'operation_record', {
            'operation_id': '#{operationId}', 'subject_id': '#{userId}', 'operation_name': "'GRU-RECORD-EXPORT'",
            'idempotency_key': '#{key}', 'request_sha256': '#{requestHash}', 'state': "'PROCESSING'",
            'response_ref': '#{commandJson}', 'target_id': '#{exportId}', 'schema_version': "'1.0.0'",
            'schema_manifest_sha256': '#{manifest}', 'created_by': '#{userId}', 'created_at': '#{now}',
            'classification': '#{classification}', 'revision': '1', 'updated_at': '#{now}',
        })

    def test_attempt_insert_is_unique_owner_session_deadline_bound_first_write_authority(self):
        self.assert_insert('insertAttempt', ATTEMPT, {
            'project_id': 'i.project_id', 'export_id': 'i.export_id', 'operation_id': 'i.operation_id',
            'created_by': 'i.owner_id', 'attempted_at': '#{now}', 'schema_version': 'i.schema_version',
            'schema_manifest_sha256': 'i.schema_manifest_sha256', 'classification': 'i.classification', 'created_at': '#{now}',
        }, 'FROM ' + INTENT + ' i JOIN operation_record o ON o.tenant_id=i.tenant_id AND o.operation_id=i.operation_id '
            'AND o.subject_id=i.owner_id AND o.created_by=i.created_by AND o.idempotency_key=i.idempotency_key '
            'AND o.request_sha256=i.operation_request_sha256 AND o.target_id=i.export_id '
            'AND o.schema_manifest_sha256=i.schema_manifest_sha256 AND o.created_at=i.created_at AND o.classification=i.classification '
            'WHERE i.project_id=#{projectId} AND i.export_id=#{exportId} AND i.operation_id=#{operationId} AND ' + OWNER +
            ' AND i.session_id=#{sessionId} AND i.deadline > #{now} AND i.created_at <= #{now} '
            "AND o.operation_name='GRU-RECORD-EXPORT' AND o.idempotency_key=#{key} AND o.request_sha256=#{requestHash} "
            "AND o.state='PROCESSING' AND o.revision=1")

    def test_attempt_cas_requires_inserted_marker_and_never_rewrites_command_or_resets_revision(self):
        self.assert_sql('markAttempted', 'UPDATE operation_record SET revision=2,updated_at=#{now} WHERE ' +
                        OPERATION_IDENTITY + " AND state='PROCESSING' AND revision=1 AND EXISTS(SELECT 1 FROM " + INTENT +
                        ' i WHERE ' + INTENT_OPERATION_MATCH + ' AND i.session_id=#{sessionId} AND i.deadline > #{now}) '
                        'AND EXISTS(SELECT 1 FROM ' + ATTEMPT + ' a WHERE a.tenant_id=operation_record.tenant_id '
                        'AND a.project_id=#{projectId} AND a.export_id=operation_record.target_id '
                        'AND a.operation_id=operation_record.operation_id AND a.created_by=#{userId} AND a.attempted_at=#{now} '
                        'AND a.created_at=a.attempted_at AND a.schema_version=operation_record.schema_version '
                        'AND a.schema_manifest_sha256=operation_record.schema_manifest_sha256 '
                        'AND a.classification=operation_record.classification)')
        self.assert_sql('completeOperation', "UPDATE operation_record SET state='COMPLETED',http_status=201,"
                        'response_ref=#{response},revision=3,updated_at=#{now} WHERE ' + OPERATION_IDENTITY +
                        " AND state='PROCESSING' AND revision=2 AND EXISTS(SELECT 1 FROM " + INTENT + ' i JOIN ' + COMPLETED +
                        ' r ON r.tenant_id=i.tenant_id AND r.project_id=i.project_id AND r.export_id=i.export_id '
                        'AND r.operation_id=i.operation_id WHERE ' + INTENT_OPERATION_MATCH + ' AND r.receipt_json=#{response})')

    def test_artifact_binding_version_and_lineage_inserts_have_no_fabricated_execution(self):
        self.assert_insert('insertVersion', 'version_registry', {
            'version_id': '#{artifactId}', 'owner_project_id': '#{projectId}', 'entity_kind': "'ARTIFACT'", 'content_sha256': '#{contentSha256}',
        })
        self.assert_insert('insertArtifact', 'artifact', {
            'project_id': '#{projectId}', 'artifact_id': '#{artifactId}', 'run_id': 'NULL', 'document_revision': '#{documentRevision}',
            'artifact_kind': "'GRU_RECORD_EXPORT'", 'object_ref': '#{storedObject}', 'object_key': '#{reference.objectKey}',
            'object_version': '#{reference.objectVersion}', 'sha256': '#{reference.sha256}', 'size_bytes': '#{reference.sizeBytes}',
            'media_type': '#{reference.mediaType}', 'dependency_access_set_id': '#{accessSetId}', 'content_sha256': '#{reference.sha256}',
            'schema_version': "'1.0.0'", 'schema_manifest_sha256': '#{manifest}', 'created_by': '#{userId}',
            'created_at': '#{now}', 'classification': '#{classification}',
        })
        self.assert_insert('insertBinding', 'artifact_content_binding', {
            'project_id': '#{projectId}', 'artifact_id': '#{artifactId}', 'resource_kind': "'DOCUMENT'", 'resource_id': '#{documentId}',
            'schema_ref': '#{schemaRef}', 'schema_document_sha256': '#{schemaHash}', 'dependency_access_set_id': '#{accessSetId}',
            'dependency_manifest_sha256': '#{dependencyHash}', 'created_by': '#{userId}', 'created_at': '#{now}', 'classification': '#{classification}',
        })
        self.assert_insert('insertLineage', 'lineage_edge', {
            'consumer_version_id': '#{artifactId}', 'upstream_version_id': '#{upstreamId}', 'relation_kind': "'GRU_RECORD_EXPORT_INPUT'",
            'actor_id': '#{userId}', 'decision_ref': 'NULL', 'created_at': '#{now}',
        })

    def test_artifact_audit_event_readback_uses_exact_owner_ids_and_kind(self):
        self.assert_sql('artifactOwner', 'SELECT document_revision,run_id FROM artifact WHERE project_id=#{projectId} '
                        "AND artifact_id=#{artifactId} AND created_by=#{userId} AND artifact_kind='GRU_RECORD_EXPORT'")
        self.assert_sql('audit', 'SELECT resource_ref FROM audit_event WHERE audit_id=#{auditEventId} AND project_id=#{projectId} '
                        'AND subject_id=#{userId} AND created_by=#{userId} AND request_id=#{operationId} AND trace_id=#{operationId} '
                        "AND action='gru.record_export.completed' AND result='SUCCESS' AND reason_code IS NULL AND schema_version='1.0.0' "
                        'AND schema_manifest_sha256=#{manifest} AND classification=#{classification}')
        self.assert_sql('event', 'SELECT payload FROM outbox_event WHERE event_id=#{outboxEventId} AND aggregate_id=#{exportId} '
                        "AND aggregate_revision=1 AND event_type='gru.record_export.completed'")

    def test_service_source_commits_unique_attempt_with_cas_before_external_write(self):
        # Source-order/transaction contracts only; concurrency execution is covered elsewhere.
        create = java_method(self.service_java, 'create')
        claim = re.search(r'boolean recover=tx\(subject,a->\{(.*?)return false;\}\);', create, re.S)
        self.assertIsNotNone(claim)
        phase = claim[1]
        ordered = ['same(subject.sessionId(),intent.sessionId())', 'mapper.attempt(v)',
                   'same(BigInteger.ONE,one(mapper.operationRevision(v)))',
                   'count(mapper.insertAttempt(v))', 'count(mapper.markAttempted(v))']
        offset = 0
        for fragment in ordered:
            index = phase.find(fragment, offset)
            self.assertNotEqual(-1, index, fragment)
            offset = index + len(fragment)
        self.assertIn('return true;', phase)
        self.assertLess(claim.end(), create.index('transport.writeDocumentProjection('))
        self.assertIn('write,content,recover,policy.maximumBytes()', create)
        self.assertIn('access.execute(subject,a->ThrowingSupplier.of(()->work.run(a)).get())', java_method(self.service_java, 'tx'))
        verify = java_method(self.service_java, 'verifyAttempt')
        for field in ('tenantId', 'projectId', 'exportId', 'operationId', 'schemaVersion', 'schemaManifest', 'classification'):
            self.assertIn('same(i.' + field + '(),t.' + field + '())', verify)
        self.assertIn('same(i.ownerId(),t.createdBy())', verify)
        self.assertIn('same(t.attemptedAt(),t.createdAt())', verify)
        self.assertIn('t.attemptedAt().isAfter(now())', verify)
        self.assertIn('t.attemptedAt().isBefore(i.createdAt())', verify)
        self.assertIn('!t.attemptedAt().isBefore(i.deadline())', verify)

    def test_immutable_tables_and_tenant_scope_actor_guard_are_registered(self):
        interceptor = (JAVA / 'ProtectedTenantInterceptor.java').read_text()
        immutable = re.search(r'IMMUTABLE_TABLES\s*=\s*java.util.Set.of\((.*?)\);', interceptor, re.S)[1]
        for table in TABLES:
            self.assertIn('"' + table + '"', immutable)
        self.assertIn('if (immutableTable(update.getTable().getName())) throw denied();', interceptor)
        self.assertIn('if (immutableTable(delete.getTable().getName())) throw denied();', interceptor)
        self.assertIn('TenantScope.requireSubjectParameter(statement.getId(), parameter);', interceptor)
        scope = (JAVA / 'TenantScope.java').read_text()
        self.assertRegex(scope, r'statementId\.startsWith\("com\.ruoyi\.medical\.persistence\.mapper\.'
                               r'GruRecordExportMapper\."\)[^{}]*\{\s*requireSubject\(parameter\); return;')
        self.assertIn('v.put("userId",a.actorId())', self.service_java)


if __name__ == '__main__':
    unittest.main()
