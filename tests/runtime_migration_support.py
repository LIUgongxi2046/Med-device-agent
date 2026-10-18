"""Offline MySQL SQLGlot/JSQLParser contracts. Never connects to or migrates a DB."""
import os
from pathlib import Path
import re
import sys
from xml.etree import ElementTree as ET
import json
import sqlglot
from sqlglot import exp


def project_root():
    override = os.environ.get('MEDICAL_PROJECT_ROOT')
    if override:
        root = Path(override).resolve()
        if not (root / 'contracts/catalogue.json').is_file():
            raise RuntimeError('MEDICAL_PROJECT_ROOT must name the actual repository')
        return root
    for origin in (Path(__file__).resolve().parent, Path.cwd().resolve()):
        for root in (origin, *origin.parents):
            if (root / 'contracts/catalogue.json').is_file():
                return root
    raise RuntimeError('Run from the repository or set MEDICAL_PROJECT_ROOT')


ROOT = project_root()
sys.path.insert(0, str(ROOT / 'tests'))
from test_migration_bundle import normalize_liquibase_comments
from test_simulation_input_artifact_migration import inspect_migration, foreign_keys
RESOURCES = ROOT / 'business/medical-data/src/main/resources'
JAVA = ROOT / 'business/medical-persistence/src/main/java/com/ruoyi/medical/persistence'
MAPPERS = ROOT / 'business/medical-persistence/src/main/resources/medical-mappers'


def ast_tables(path):
    statements = sqlglot.parse(normalize_liquibase_comments(path.read_text()), read='mysql')
    tables = {s.this.this.name: s for s in statements if isinstance(s, exp.Create) and isinstance(s.this, exp.Schema)}
    return statements, tables


def columns(table):
    return {c.name: c for c in table.this.expressions if isinstance(c, exp.ColumnDef)}


def unique_keys(table):
    return {tuple(c.name for c in k.expressions) for k in table.find_all(exp.PrimaryKey)} | {
        tuple(c.name for c in k.this.expressions) for k in table.find_all(exp.UniqueColumnConstraint)}


def registered(test, filename, expected):
    path = 'db/migration/' + filename
    entries = ET.parse(RESOURCES / 'db/changelog/master.xml').findall('{http://www.liquibase.org/xml/ns/dbchangelog}include')
    files = [e.attrib['file'] for e in entries]
    test.assertEqual(1, files.count(path))
    coverage = json.loads((RESOURCES / 'db/migration/coverage.json').read_text())
    test.assertEqual(1, coverage['native_sql_migrations'].count(path))
    actual = {t for group in coverage['required_tables'].values() for t in group}
    test.assertLessEqual(set(expected), actual)
    sequence = ['074-runtime-retention.sql', '075-persistent-run-runtime.sql', '076-formal-model-invocation.sql',
                '077-model-eligibility-connection.sql', '078-agent-execution-policy.sql',
                '079-formal-evaluation-input.sql', '082-formal-capability-cancellation.sql']
    # Gaps 080/081 are legitimate reserved migrations, not a fixed-tail assertion.
    indexes = [files.index('db/migration/' + item) for item in sequence]
    test.assertEqual(sorted(indexes), indexes)


def tenant_catalogues(test, tables, mutable=()):
    protection = (JAVA / 'ProtectedTenantInterceptor.java').read_text()
    test.assertIn('boolean ignoreTable(String table) { return false; }', protection)
    block = protection.split('IMMUTABLE_TABLES =', 1)[1].split(';', 1)[0]
    for table in tables:
        if table in mutable:
            test.assertNotIn('"' + table + '"', block)
        else:
            test.assertIn('"' + table + '"', block)


def standard_metadata(test, tables, project_via_parent=()):
    required = {'tenant_id', 'project_id', 'created_by', 'created_at', 'schema_version', 'schema_manifest_sha256', 'classification'}
    for name, table in tables.items():
        expected = required - {'project_id'} if name in project_via_parent else required
        test.assertLessEqual(expected, set(columns(table)), name)
        for target, local, remote in foreign_keys(table):
            test.assertEqual('tenant_id', local[0], (name, target, local))
            test.assertEqual('tenant_id', remote[0], (name, target, remote))
            test.assertEqual(len(local), len(remote))


def mapper_prefix(test, mapper):
    namespace = 'com.ruoyi.medical.persistence.mapper.' + mapper
    scope = (JAVA / 'TenantScope.java').read_text()
    test.assertIn('statementId.startsWith("' + namespace + '.")', scope)
    tree = ET.parse(MAPPERS / (mapper + '.xml')).getroot()
    test.assertEqual(namespace, tree.attrib['namespace'])
    statements = [n for n in tree if n.tag in {'select', 'insert', 'update', 'delete'}]
    test.assertTrue(statements)
    ids = [n.attrib['id'] for n in statements]
    test.assertEqual(len(ids), len(set(ids)))
    for node in statements:
        sql = ''.join(node.itertext())
        test.assertNotIn('${', sql, node.attrib['id'])
    return {n.attrib['id']: n for n in statements}


def parent_contracts(test, tables):
    parents = dict(tables)
    paths = [ROOT / 'data-design/core.sql']
    paths.extend(RESOURCES / ('db/migration/' + name) for name in [
        '002-identity-sources.sql', '004-task-execution.sql', '005-document-evidence.sql',
        '007-capability-model-budget.sql', '008-governance-evaluation-connector.sql', '009-conversation-events-receipts.sql'])
    for path in paths:
        _, created = ast_tables(path)
        parents.update(created)
    # This real historical ALTER adds the digest key referenced by formal manifests.
    altered, _ = ast_tables(RESOURCES / 'db/migration/027-template-definition.sql')
    for statement in altered:
        if isinstance(statement, exp.Alter) and statement.this.name == 'version_registry':
            for action in statement.args.get('actions', []):
                for key in action.find_all(exp.UniqueColumnConstraint):
                    parents['version_registry'].this.append('expressions', key.copy())
    for name, table in tables.items():
        child = columns(table)
        for target, local, remote in foreign_keys(table):
            test.assertIn(target, set(parents), (name, target))
            parent = parents[target]
            test.assertIn(remote, unique_keys(parent), (name, target, remote))
            for left, right in zip(local, remote):
                test.assertEqual(child[left].kind, columns(parent)[right].kind, (name, left, target, right))
