"""069 real JSQLParser/SQLGlot storage and mapper contracts, entirely offline.

The service phase checks below are source contracts, not a live concurrency test.
Passing these checks does not establish MySQL or tenant-interceptor execution.
"""
import hashlib
import json
import re
import unittest

import sqlglot
from sqlglot import exp

from test_simulation_input_artifact_migration import (
    ROOT, RESOURCES, assert_registered, foreign_keys, inspect_migration,
)
from test_simulation_preparation_migration import mapper_statements, inserted_values


MIGRATION = 'db/migration/069-document-comparison.sql'
JAVA = ROOT / 'business/medical-persistence/src/main/java/com/ruoyi/medical/persistence'
INTENT = 'document_comparison_intent'
REGISTRATION = 'document_comparison_registration'
ATTEMPT = 'document_comparison_attempt'
TABLES = {INTENT, REGISTRATION, ATTEMPT}
COLUMNS = {
    INTENT: '''tenant_id project_id comparison_id operation_id manifest_id access_set_id
        artifact_id write_id start_event_id document_id left_revision right_revision
        request_context request_session idempotency_key canonical_request request_body_sha256
        operation_request_sha256 input_manifest input_sha256 dependency_manifest
        dependency_sha256 canonical_core core_sha256 schema_version schema_manifest_sha256
        created_by created_at deadline classification project_key'''.split(),
    REGISTRATION: '''tenant_id project_id comparison_id operation_id artifact_id intent_sha256
        reference receipt_json core_sha256 schema_version schema_manifest_sha256
        created_by created_at classification project_key'''.split(),
    ATTEMPT: '''tenant_id project_id comparison_id operation_id write_id intent_sha256
        schema_version schema_manifest_sha256 created_by created_at classification'''.split(),
}
JSON_COLUMNS = {
    INTENT: ('canonical_request', 'input_manifest', 'dependency_manifest', 'canonical_core'),
    REGISTRATION: ('reference', 'receipt_json'),
    ATTEMPT: (),
}
UNIQUE_KEYS = {
    INTENT: {
        ('tenant_id', 'operation_id'), ('tenant_id', 'artifact_id'),
        ('tenant_id', 'created_by', 'idempotency_key'),
        ('tenant_id', 'write_id'), ('tenant_id', 'start_event_id'),
        ('tenant_id', 'project_id', 'comparison_id', 'operation_id', 'write_id'),
        ('tenant_id', 'project_id', 'comparison_id', 'operation_id', 'artifact_id', 'core_sha256'),
    },
    REGISTRATION: {('tenant_id', 'operation_id'), ('tenant_id', 'artifact_id')},
    ATTEMPT: {('tenant_id', 'write_id')},
}
OPERATION_COLUMNS = ('operation_id,request_sha256,state,http_status,response_ref,target_id,'
                     'schema_version,schema_manifest_sha256')
OPERATION_IDENTITY = ("operation_id=#{operationId} AND subject_id=#{userId} "
                      "AND operation_name='DOCUMENT-COMPARISON' AND idempotency_key=#{key} "
                      "AND request_sha256=#{requestHash} AND target_id=#{comparisonId}")
OPERATION_CAS = OPERATION_IDENTITY + " AND state='PROCESSING' AND revision=#{operationRevision}"


def bound_ast(source):
    """Keep parameter identities while parsing; plain '?' would hide actor/hash swaps."""
    return sqlglot.parse_one(re.sub(r'#\{([^}]+)\}', lambda m: "'@" + m[1] + "'", source),
                             read='mysql')


def predicate(source):
    return sqlglot.parse_one('SELECT 1 WHERE ' + source, read='mysql').args['where'].this


def property_name(column):
    if column == 'schema_manifest_sha256':
        return 'schemaManifest'
    first, *rest = column.split('_')
    return first + ''.join(word.title() for word in rest)


def java_method(source, name):
    """Extract a declared method, ignoring braces in strings/comments while scanning."""
    declaration = re.search(r'(?:public|private)\s+[^;{}\n]*\b' + name +
                            r'\([^;{}]*?\)\s*(?:throws\s+\w+\s*)?\{', source)
    if declaration is None:
        raise AssertionError('Missing Java method: ' + name)
    start = declaration.end()
    depth = 1
    tokens = re.compile(r'"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|//[^\n]*|/\*.*?\*/|[{}]', re.S)
    for token in tokens.finditer(source, start):
        if token[0] == '{':
            depth += 1
        elif token[0] == '}':
            depth -= 1
            if depth == 0:
                return source[start:token.start()]
    raise AssertionError('Unclosed Java method: ' + name)


class DocumentComparisonMigrationTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.sql = (RESOURCES / MIGRATION).read_text()
        # 069 must pass the ordinary mature Java parser; no 068 compatibility route.
        cls.parsed, cls.tables = inspect_migration(RESOURCES / MIGRATION)
        cls.mapper, cls.statements = mapper_statements('DocumentComparisonMapper')
        cls.mapper_java = (JAVA / 'mapper/DocumentComparisonMapper.java').read_text()
        cls.service_java = (JAVA / 'DocumentComparisonService.java').read_text()

    def assert_sql(self, statement_id, expected):
        self.assertEqual(bound_ast(expected), bound_ast(self.statements[statement_id][2]),
                         statement_id)

    def assert_insert(self, statement_id, table, values):
        expected = 'INSERT INTO ' + table + '(' + ','.join(values) + ') VALUES(' + ','.join(values.values()) + ')'
        self.assert_sql(statement_id, expected)

    def assert_ordered(self, source, *fragments):
        offset = 0
        for fragment in fragments:
            index = source.find(fragment, offset)
            self.assertNotEqual(-1, index, fragment)
            offset = index + len(fragment)

    def test_registration_exact_three_additive_tables_and_counts(self):
        assert_registered(self, MIGRATION, 'db/migration/068-document-technical-check.sql', self.tables)
        self.assertEqual(TABLES, set(self.tables))
        self.assertEqual(3, len(self.parsed))
        self.assertTrue(all(set(item) == {'create'} for item in self.parsed))
        self.assertTrue(all(isinstance(table, exp.Create) and table.kind == 'TABLE'
                            for table in self.tables.values()))
        self.assertEqual([
            'medical-agent:069-01-document-comparison-intent',
            'medical-agent:069-02-document-comparison-registration',
            'medical-agent:069-03-document-comparison-attempt',
        ], re.findall(r'(?m)^--changeset (\S+) dbms:mysql runInTransaction:false failOnError:true$', self.sql))
        self.assertNotRegex(self.sql, r'(?im)^\s*(ALTER|INSERT|UPDATE|DELETE|REPLACE|TRUNCATE|DROP|GRANT|REVOKE)\b')
        coverage = json.loads((RESOURCES / 'db/migration/coverage.json').read_text())
        self.assertEqual(TABLES, set(coverage['required_tables']['document_comparison']))
        business = sum(map(len, coverage['required_tables'].values()))
        self.assertEqual(281, business)
        self.assertEqual(318, business + len(coverage['platform_tables']) + len(coverage['framework_tables']))

    def test_068_parser_compatibility_does_not_accept_069(self):
        from test_migration_bundle import parse_068_native_statements
        with self.assertRaisesRegex(ValueError, '^068_COMPATIBILITY_SCOPE_ONLY$'):
            parse_068_native_statements(RESOURCES / MIGRATION)

    def test_001_through_068_frozen_against_part17(self):
        # Digest supplied after an independent byte-for-byte part17 archive comparison.
        paths = sorted(path for path in (RESOURCES / 'db/migration').iterdir()
                       if path.suffix in ('.sql', '.xml') and path.name[:3].isdigit()
                       and int(path.name[:3]) <= 68)
        self.assertEqual(67, len(paths))
        digest = hashlib.sha256(b''.join(path.name.encode() + b'\0' + path.read_bytes() + b'\0'
                                         for path in paths)).hexdigest()
        self.assertEqual('aef81c64a51c1096ede9dc4c2c651d4f344887992f9037ce9af6df646722ed0c', digest)

    def test_exact_columns_types_not_null_and_created_metadata(self):
        for name, table in self.tables.items():
            with self.subTest(table=name):
                columns = {column.name: column for column in table.this.expressions
                           if isinstance(column, exp.ColumnDef)}
                self.assertEqual(COLUMNS[name], list(columns))
                self.assertFalse({'updated_at', 'revision', 'status'} & columns.keys())
                for key, column in columns.items():
                    expected_type = ('LONGTEXT' if key in JSON_COLUMNS[name] else
                                     'DATETIME(6)' if key in ('created_at', 'deadline') else
                                     'VARCHAR(20)' if key == 'request_context' else
                                     'VARCHAR(128)' if key == 'idempotency_key' else
                                     'VARCHAR(16)' if key == 'schema_version' else
                                     'VARCHAR(24)' if key == 'classification' else
                                     'VARCHAR(36)' if key == 'project_key' else
                                     'CHAR(64)' if key.endswith('sha256') else 'CHAR(36)')
                    self.assertEqual(expected_type, column.kind.sql(dialect='mysql'), key)
                    if key != 'project_key':
                        self.assertTrue(any(isinstance(c.kind, exp.NotNullColumnConstraint)
                                            for c in column.constraints), key)
                    if expected_type.startswith(('CHAR(', 'VARCHAR(')) and key != 'classification':
                        charsets = list(column.find_all(exp.CharacterSetColumnConstraint))
                        self.assertEqual(['ascii'], [c.this.name for c in charsets], key)

    def test_generated_project_scope_and_storage_engine(self):
        for name, table in self.tables.items():
            computed = list(table.find_all(exp.ComputedColumnConstraint))
            self.assertEqual(0 if name == ATTEMPT else 1, len(computed), name)
            if computed:
                self.assertEqual(exp.column('project_id'), computed[0].this)
                self.assertTrue(computed[0].args['persisted'])
            self.assertEqual({'ENGINE=InnoDB', 'DEFAULT CHARACTER SET=utf8mb4',
                              'COLLATE=utf8mb4_0900_as_cs'},
                             {p.sql(dialect='mysql') for p in table.args['properties'].expressions})

    def test_exact_primary_and_unique_keys(self):
        for name, table in self.tables.items():
            primary = list(table.find_all(exp.PrimaryKey))
            self.assertEqual(1, len(primary), name)
            self.assertEqual(('tenant_id', 'comparison_id'), tuple(c.name for c in primary[0].expressions))
            unique = list(table.find_all(exp.UniqueColumnConstraint))
            self.assertEqual(len(UNIQUE_KEYS[name]), len(unique), name)
            self.assertEqual(UNIQUE_KEYS[name], {tuple(c.name for c in key.this.expressions)
                                               for key in unique}, name)

    def test_intent_full_fixed_document_and_dependency_foreign_keys(self):
        self.assertEqual({
            ('document_revision_meta', ('tenant_id', 'project_id', 'document_id', 'left_revision'),
             ('tenant_id', 'project_id', 'document_id', 'document_revision')),
            ('document_revision_meta', ('tenant_id', 'project_id', 'document_id', 'right_revision'),
             ('tenant_id', 'project_id', 'document_id', 'document_revision')),
            ('version_registry', ('tenant_id', 'manifest_id', 'input_sha256'),
             ('tenant_id', 'version_id', 'content_sha256')),
            ('dependency_access_manifest', ('tenant_id', 'project_key', 'access_set_id', 'dependency_sha256'),
             ('tenant_id', 'project_key', 'access_set_id', 'manifest_sha256')),
            ('operation_record', ('tenant_id', 'operation_id'), ('tenant_id', 'operation_id')),
            ('tenant_member', ('tenant_id', 'created_by'), ('tenant_id', 'user_id')),
        }, foreign_keys(self.tables[INTENT]))

    def test_registration_and_attempt_full_parent_and_artifact_foreign_keys(self):
        complete = ('tenant_id', 'project_id', 'comparison_id', 'operation_id', 'artifact_id', 'core_sha256')
        attempt = ('tenant_id', 'project_id', 'comparison_id', 'operation_id', 'write_id')
        self.assertEqual({
            (INTENT, complete, complete),
            ('artifact', ('tenant_id', 'project_key', 'artifact_id', 'core_sha256'),
             ('tenant_id', 'project_key', 'artifact_id', 'sha256')),
            ('operation_record', ('tenant_id', 'operation_id'), ('tenant_id', 'operation_id')),
            ('tenant_member', ('tenant_id', 'created_by'), ('tenant_id', 'user_id')),
        }, foreign_keys(self.tables[REGISTRATION]))
        self.assertEqual({
            (INTENT, attempt, attempt),
            ('tenant_member', ('tenant_id', 'created_by'), ('tenant_id', 'user_id')),
        }, foreign_keys(self.tables[ATTEMPT]))
        actual_parent_unique = {tuple(c.name for c in key.this.expressions)
                                for key in self.tables[INTENT].find_all(exp.UniqueColumnConstraint)}
        for name, table in self.tables.items():
            keys = foreign_keys(table)
            self.assertEqual(len(keys), len(list(table.find_all(exp.ForeignKey))), name)
            for parent, local, remote in keys:
                self.assertEqual('tenant_id', local[0])
                self.assertEqual('tenant_id', remote[0])
                self.assertEqual(len(local), len(remote))
                if parent == INTENT:
                    self.assertIn(remote, actual_parent_unique)
                    self.assertEqual(local, remote)

    def test_exact_schema_classification_json_hash_and_deadline_checks(self):
        for name, table in self.tables.items():
            hashes = [column for column in COLUMNS[name] if column.endswith('sha256')]
            expected = ["schema_version='1.0.0'",
                        "classification IN ('PUBLIC','INTERNAL','RESTRICTED','PERSONAL_HEALTH')",
                        ' AND '.join(column + " REGEXP '^[a-f0-9]{64}$'" for column in hashes)]
            if JSON_COLUMNS[name]:
                expected.append(' AND '.join('JSON_VALID(' + column + ')' for column in JSON_COLUMNS[name]))
            if name == INTENT:
                expected.extend(['deadline>created_at', "request_context REGEXP '^[0-9]+$'", "CHAR_LENGTH(idempotency_key) BETWEEN 16 AND 128 AND idempotency_key REGEXP '^[!-~]+$'"])
            checks = list(table.find_all(exp.CheckColumnConstraint))
            self.assertEqual(len(expected), len(checks), name)
            self.assertEqual({predicate(check) for check in expected}, {check.this for check in checks}, name)

    def test_mapper_exact_twenty_six_ids_interface_and_uncached_reads(self):
        expected = {
            'intent', 'intentByOperation', 'intentByKey', 'registration', 'operation', 'operationById',
            'operationRevision', 'startAudit', 'startEvent', 'finalAudit', 'finalEvent',
            'artifactOwner', 'attempt', 'insertVersion', 'insertAccessSet',
            'insertAccessManifest', 'insertAccessMember', 'insertLineage', 'insertIntent',
            'insertRegistration', 'startOperation', 'updateCommand', 'completeOperation',
            'insertArtifact', 'insertBinding', 'insertAttempt',
        }
        self.assertEqual(26, len(self.statements))
        self.assertEqual(expected, set(self.statements))
        self.assertEqual(expected, set(re.findall(r'\b(\w+)\(Map<String,\s*Object> values\)', self.mapper_java)))
        self.assertEqual('com.ruoyi.medical.persistence.mapper.DocumentComparisonMapper', self.mapper.attrib['namespace'])
        self.assertIsNone(self.mapper.find('cache'))
        self.assertIsNone(self.mapper.find('cache-ref'))
        for name, (element, statement, _) in self.statements.items():
            if element.tag == 'select':
                self.assertEqual('false', element.attrib['useCache'], name)
                self.assertEqual('true', element.attrib['flushCache'], name)
            if isinstance(statement, exp.Insert):
                self.assertNotIn('tenant_id', inserted_values(statement), name)
                self.assertNotIn('project_key', inserted_values(statement), name)
            if isinstance(statement, (exp.Update, exp.Delete)):
                self.assertEqual('operation_record', statement.this.name, name)
                self.assertIsInstance(statement, exp.Update)

    def test_original_key_lookup_is_independent_actor_bound_and_case_sensitive(self):
        self.assert_sql('intentByKey', 'SELECT * FROM document_comparison_intent WHERE created_by=#{userId} AND idempotency_key=#{key}')
        self.assertEqual('Intent', self.statements['intentByKey'][0].attrib['resultMap'])
        self.assertRegex(self.sql, r'idempotency_key VARCHAR\(128\) CHARACTER SET ascii COLLATE ascii_bin NOT NULL')
        lookup = java_method(self.service_java, 'lookup')
        self.assert_ordered(lookup, 'mapper.intentByKey(v)', 'mapper.operation(v)')
        self.assertIn('if(intents.isEmpty()&&operations.isEmpty())return null;', lookup)
        for fragment in ('same(a.actorId(),row.createdBy())', 'same(key,row.idempotencyKey())', 'same(row.operationId(),operation.operationId())', 'same(row.comparisonId(),operation.targetId())', 'same(row.operationRequestSha256(),operation.requestSha256())'):
            self.assertIn(fragment, lookup)
        self.assertIn('lookup(a,key)', java_method(self.service_java, 'create'))
        self.assertIn('lookup(a,key)', java_method(self.service_java, 'recovery'))
        self.assertIn('lookup(a,key)', java_method(self.service_java, 'start'))
        self.assertIn('.put("idempotency_key",key)', java_method(self.service_java, 'input'))

    def test_immutable_record_insert_columns_and_parameter_binding_are_complete(self):
        for name, table in (('insertIntent', INTENT), ('insertRegistration', REGISTRATION),
                            ('insertAttempt', ATTEMPT)):
            values = {column: '#{row.' + property_name(column) + '}' for column in COLUMNS[table]
                      if column not in ('tenant_id', 'project_key')}
            self.assert_insert(name, table, values)

    def test_result_constructors_match_all_persisted_metadata_and_java_records(self):
        for result_name, table in (('Intent', INTENT), ('Registration', REGISTRATION), ('Attempt', ATTEMPT)):
            result = self.mapper.find("resultMap[@id='" + result_name + "']")
            self.assertEqual('com.ruoyi.medical.persistence.mapper.DocumentComparisonMapper$' + result_name,
                             result.attrib['type'])
            args = list(result.find('constructor'))
            columns = [column for column in COLUMNS[table] if column != 'project_key']
            self.assertEqual(columns, [arg.attrib['column'] for arg in args])
            java_fields = re.search(r'\brecord ' + result_name + r'\(([^)]*)\)', self.mapper_java)[1]
            expected_fields = []
            for column, arg in zip(columns, args, strict=True):
                date = column in ('created_at', 'deadline')
                self.assertEqual('java.time.LocalDateTime' if date else 'java.lang.String', arg.attrib['javaType'])
                expected_fields.append(('LocalDateTime' if date else 'String', property_name(column)))
            self.assertEqual(expected_fields, [tuple(field.strip().split()) for field in java_fields.split(',')])

    def test_immutable_record_readback_identity_and_attempt_read_only(self):
        for name, table, identity, parameter, result in (
            ('intent', INTENT, 'comparison_id', 'comparisonId', 'Intent'),
            ('intentByOperation', INTENT, 'operation_id', 'operationId', 'Intent'),
            ('registration', REGISTRATION, 'comparison_id', 'comparisonId', 'Registration'),
            ('attempt', ATTEMPT, 'comparison_id', 'comparisonId', 'Attempt'),
        ):
            self.assert_sql(name, 'SELECT * FROM ' + table + ' WHERE ' + identity + '=#{' + parameter + '}')
            self.assertEqual(result, self.statements[name][0].attrib['resultMap'])
        self.assert_sql('artifactOwner', 'SELECT document_revision,run_id FROM artifact WHERE artifact_id=#{artifactId}')
        # Attempt is immutable and serialized by the operation lock, not a mutable lock row.
        self.assertEqual(['attempt', 'insertAttempt'], [name for name, (_, statement, _) in self.statements.items()
                          if any(table.name == ATTEMPT for table in statement.find_all(exp.Table))])

    def test_operation_reads_bind_actor_and_lock_exact_identity(self):
        self.assert_sql('operation', 'SELECT ' + OPERATION_COLUMNS + " FROM operation_record WHERE "
                        "operation_name='DOCUMENT-COMPARISON' AND subject_id=#{userId} AND idempotency_key=#{key} FOR UPDATE")
        self.assert_sql('operationById', 'SELECT ' + OPERATION_COLUMNS + " FROM operation_record WHERE "
                        "operation_name='DOCUMENT-COMPARISON' AND subject_id=#{creatorId} AND operation_id=#{operationId}")
        self.assert_sql('operationRevision', 'SELECT revision FROM operation_record WHERE ' + OPERATION_IDENTITY + ' FOR UPDATE')
        self.assertEqual('java.math.BigInteger', self.statements['operationRevision'][0].attrib['resultType'])
        for name in ('operation', 'operationRevision'):
            locks = self.statements[name][1].args.get('locks', [])
            self.assertEqual(1, len(locks), name)
            self.assertTrue(locks[0].args.get('update'), name)

    def test_operation_initial_state_and_revision_compare_and_swap(self):
        self.assert_insert('startOperation', 'operation_record', {
            'operation_id': '#{operationId}', 'subject_id': '#{userId}',
            'operation_name': "'DOCUMENT-COMPARISON'", 'idempotency_key': '#{key}',
            'request_sha256': '#{requestHash}', 'state': "'PROCESSING'", 'response_ref': '#{commandJson}',
            'target_id': '#{comparisonId}', 'schema_version': "'1.0.0'", 'schema_manifest_sha256': '#{manifest}',
            'created_by': '#{userId}', 'created_at': '#{now}', 'classification': '#{classification}',
            'revision': '1', 'updated_at': '#{now}',
        })
        self.assert_sql('updateCommand', 'UPDATE operation_record SET response_ref=#{commandJson},'
                        'revision=revision+1,updated_at=#{now} WHERE ' + OPERATION_CAS)
        self.assert_sql('completeOperation', "UPDATE operation_record SET state='COMPLETED',http_status=200,"
                        'response_ref=#{response},revision=revision+1,updated_at=#{now} WHERE ' + OPERATION_CAS)

    def test_artifact_and_document_binding_insert_all_columns_without_fabricated_run(self):
        self.assert_insert('insertArtifact', 'artifact', {
            'project_id': '#{projectId}', 'artifact_id': '#{artifactId}', 'run_id': 'NULL',
            'document_revision': '#{rightRevision}', 'artifact_kind': "'DOCUMENT_COMPARISON'",
            'object_ref': '#{storedObject}', 'object_key': '#{reference.objectKey}',
            'object_version': '#{reference.objectVersion}', 'sha256': '#{reference.sha256}',
            'size_bytes': '#{reference.sizeBytes}', 'media_type': '#{reference.mediaType}',
            'dependency_access_set_id': '#{accessSetId}', 'content_sha256': '#{reference.sha256}',
            'schema_version': "'1.0.0'", 'schema_manifest_sha256': '#{manifest}',
            'created_by': '#{userId}', 'created_at': '#{now}', 'classification': '#{classification}',
        })
        self.assert_insert('insertBinding', 'artifact_content_binding', {
            'project_id': '#{projectId}', 'artifact_id': '#{artifactId}', 'resource_kind': "'DOCUMENT'",
            'resource_id': '#{documentId}', 'schema_ref': '#{schemaRef}', 'schema_document_sha256': '#{schemaHash}',
            'dependency_access_set_id': '#{accessSetId}', 'dependency_manifest_sha256': '#{dependencyHash}',
            'created_by': '#{userId}', 'created_at': '#{now}', 'classification': '#{classification}',
        })

    def test_version_access_and_lineage_inserts_bind_complete_hashes_and_actor(self):
        self.assert_insert('insertVersion', 'version_registry', {
            'version_id': '#{versionId}', 'owner_project_id': '#{projectId}',
            'entity_kind': '#{versionKind}', 'content_sha256': '#{versionHash}',
        })
        self.assert_insert('insertAccessSet', 'dependency_access_set', {
            'project_id': '#{projectId}', 'access_set_id': '#{accessSetId}',
            'manifest_sha256': '#{dependencyHash}', 'content_sha256': '#{dependencyHash}',
            'created_by': '#{userId}', 'created_at': '#{now}', 'classification': '#{classification}',
        })
        self.assert_insert('insertAccessManifest', 'dependency_access_manifest', {
            'project_id': '#{projectId}', 'access_set_id': '#{accessSetId}', 'manifest_json': '#{dependencyJson}',
            'manifest_sha256': '#{dependencyHash}', 'content_sha256': '#{dependencyHash}', 'member_count': '#{memberCount}',
            'schema_version': "'1.0.0'", 'schema_manifest_sha256': '#{dependencySchema}',
            'created_by': '#{userId}', 'created_at': '#{now}', 'classification': '#{classification}',
        })
        self.assert_insert('insertAccessMember', 'dependency_access_member', {
            'access_set_id': '#{accessSetId}', 'upstream_version_id': '#{member.upstreamVersionId}',
            'rights_revision': '#{member.rightsRevision}', 'created_by': '#{userId}',
            'created_at': '#{now}', 'classification': '#{classification}',
        })
        self.assert_insert('insertLineage', 'lineage_edge', {
            'consumer_version_id': '#{consumerId}', 'upstream_version_id': '#{upstreamId}',
            'relation_kind': "'DOCUMENT_COMPARISON_INPUT'", 'actor_id': '#{userId}',
            'decision_ref': 'NULL', 'created_at': '#{now}',
        })

    def test_audit_and_event_readback_scopes_started_and_completed_phases(self):
        for prefix, phase, revision, event_id in (('start', 'started', 1, 'startEventId'),
                                                  ('final', 'completed', 2, 'operationId')):
            self.assert_sql(prefix + 'Audit', 'SELECT resource_ref FROM audit_event WHERE '
                            'project_id=#{projectId} AND subject_id=#{creatorId} AND created_by=#{creatorId} '
                            'AND request_id=#{operationId} AND trace_id=#{operationId} '
                            "AND action='document.comparison." + phase + "' AND result='SUCCESS' "
                            "AND reason_code IS NULL AND schema_version='1.0.0' "
                            'AND schema_manifest_sha256=#{manifest} AND classification=#{classification}')
            self.assert_sql(prefix + 'Event', 'SELECT payload FROM outbox_event WHERE event_id=#{' + event_id + '}'
                            ' AND aggregate_id=#{comparisonId} AND aggregate_revision=' + str(revision) +
                            " AND event_type='document.comparison." + phase + "'")

    def test_service_attempt_phase_source_orders_lock_insert_cas_and_readback(self):
        command = java_method(self.service_java, 'command')
        self.assert_ordered(command, 'mapper.operation(v)', 'mapper.operationRevision(v)', 'mapper.attempt(v)')
        self.assertIn('if(command.attempted())verifyAttempt(a,row);else if(!attempts.isEmpty())bad();', command)
        mark = java_method(self.service_java, 'markAttempt')
        self.assert_ordered(mark, 'same(previous,command(a,row,key))', 'mapper.operationRevision(v)',
                            'count(mapper.insertAttempt(v))', 'count(mapper.updateCommand(v))',
                            'same(next,command(a,row,key))')
        self.assertIn('if(previous.attempted()||previous.reference()!=null||!next.attempted()||next.reference()!=null)bad();', mark)
        self.assertNotIn('tx(', mark)
        create = java_method(self.service_java, 'create')
        self.assertRegex(create, r'tx\(subject,a->\{[^\n]*if\(!recover\)markAttempt\(a,intent,key,command,attempted\);'
                                r'else same\(command,command\(a,intent,key\)\);return null;\}\);')
        self.assert_ordered(create, 'markAttempt(a,intent,key,command,attempted)', 'transport.writeDocumentProjection(',
                            'advance(a,intent,key,attempted,stored)')
        self.assertIn('access.execute(subject,a->ThrowingSupplier.of(()->work.apply(a)).get())',
                      java_method(self.service_java, 'tx'))
        access = (JAVA / 'EnterpriseAccess.java').read_text()
        self.assertRegex(access, r'return transactions\.execute\(watermarks -> \{[\s\S]*return operation\.apply\(')

    def test_attempt_readback_verifies_immutable_scope_intent_hash_and_metadata(self):
        verify = java_method(self.service_java, 'verifyAttempt')
        self.assertIn('one(mapper.attempt(v))', verify)
        for field in ('tenantId', 'projectId', 'comparisonId', 'operationId', 'writeId',
                      'schemaManifest', 'createdBy', 'classification'):
            self.assertIn('same(row.' + field + '(),attempt.' + field + '())', verify)
        self.assertIn('same(schema.hash(row),attempt.intentSha256())', verify)
        self.assertIn('same("1.0.0",attempt.schemaVersion())', verify)
        self.assertIn('attempt.createdAt()==null', verify)
        self.assertIn('attempt.createdAt().isBefore(row.createdAt())', verify)
        self.assertIn('!attempt.createdAt().isBefore(row.deadline())', verify)

    def test_server_immutable_registration_and_required_current_actor(self):
        interceptor = (JAVA / 'ProtectedTenantInterceptor.java').read_text()
        immutable = re.search(r'IMMUTABLE_TABLES\s*=\s*java.util.Set.of\((.*?)\);', interceptor, re.S)[1]
        for table in TABLES:
            self.assertIn('"' + table + '"', immutable)
        self.assertIn('if (immutableTable(update.getTable().getName())) throw denied();', interceptor)
        self.assertIn('if (immutableTable(delete.getTable().getName())) throw denied();', interceptor)
        self.assertIn('TenantScope.requireSubjectParameter(statement.getId(), parameter);', interceptor)
        scope = (JAVA / 'TenantScope.java').read_text()
        self.assertRegex(scope, r'statementId\.startsWith\("com\.ruoyi\.medical\.persistence\.mapper\.'
                               r'DocumentComparisonMapper\."\)[^{}]*\{\s*requireSubject\(parameter\); return;')
        self.assertIn('values.put("userId",a.actorId())', java_method(self.service_java, 'values'))
        self.assertNotIn('"userId"', java_method(self.service_java, 'base'))


if __name__ == '__main__':
    unittest.main()
