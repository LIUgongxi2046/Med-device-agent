"""One pinned 076 ALTER uses SQLGlot; all original CREATE bytes also use JSQLParser.
This is parser compatibility only. It executes neither SQL nor a database migration.
"""
import hashlib
from pathlib import Path
from tempfile import TemporaryDirectory
import sqlglot
from sqlglot import exp

SHA256='0059bc71ed9179fb0cadfcbb2432fa36032284ec29d4d1c4a4da5f22b07b5d57'
NEW_JOBS=('SIMULATION_EVALUATION','DOCUMENT_TECHNICAL_CHECK','MODEL_VERIFICATION')
OLD_JOBS=('SIMULATION_EVALUATION','DOCUMENT_TECHNICAL_CHECK')


def expected_alter(jobs):
    values=','.join("'"+v+"'" for v in jobs)
    return sqlglot.parse_one("ALTER TABLE system_job_run DROP CHECK ck_system_simulation_workflow, "
        "ADD CONSTRAINT ck_system_simulation_workflow CHECK ((job_type IN ("+values+") AND workflow_id IS NULL) "
        "OR (job_type NOT IN ("+values+") AND workflow_id IS NOT NULL))",read='mysql')


def parse_076_native_statements(path):
    from test_migration_bundle import normalize_liquibase_comments
    from test_simulation_input_artifact_migration import inspect_migration
    path=Path(path)
    if path.name!='076-formal-model-invocation.sql':
        raise ValueError('076_COMPATIBILITY_SCOPE_ONLY')
    raw=path.read_bytes()
    if hashlib.sha256(raw).hexdigest()!=SHA256:
        raise ValueError('076_ORIGINAL_BYTES_CHANGED')
    statements=sqlglot.parse(normalize_liquibase_comments(raw.decode('utf-8')),read='mysql')
    if len(statements)!=12 or not isinstance(statements[0],exp.Alter) or not all(
            isinstance(s,exp.Create) and s.kind=='TABLE' for s in statements[1:]):
        raise ValueError('076_COMPLETE_STATEMENT_SHAPE_REQUIRED')
    if statements[0].sql(comments=False)!=expected_alter(NEW_JOBS).sql(comments=False):
        raise ValueError('076_EXACT_WORKFLOW_REPLACEMENT_REQUIRED')
    previous=path.with_name('068-document-technical-check.sql')
    old=sqlglot.parse(normalize_liquibase_comments(previous.read_text()),read='mysql')[0]
    if old.sql(comments=False)!=expected_alter(OLD_JOBS).sql(comments=False):
        raise ValueError('076_PRIOR_WORKFLOW_CHECK_CHANGED')
    marker=b'--changeset medical-agent:076-02-formal-model-manifest'
    if raw.count(marker)!=1:raise ValueError('076_CREATE_BOUNDARY_REQUIRED')
    _,suffix=raw.split(marker)
    # Retain actual original CREATE bytes, never a rewritten/relaxed projection.
    with TemporaryDirectory(prefix='medical-076-parser-') as folder:
        creates=Path(folder)/'076-original-create-statements.sql'
        creates.write_bytes(marker+suffix)
        native,tables=inspect_migration(creates)
    original_names={s.this.this.name for s in statements[1:]}
    if len(native)!=11 or set(tables)!=original_names or not all(set(s)=={'create'} for s in native):
        raise ValueError('076_ALL_CREATES_REQUIRE_NATIVE_PARSER')
    # Return complete original ALTER AST plus native-parsed CREATE definitions;
    # the bundle retains all normal FK/type/check inspection on these tables.
    return [statements[0],*(tables[name] for name in (s.this.this.name for s in statements[1:]))]
