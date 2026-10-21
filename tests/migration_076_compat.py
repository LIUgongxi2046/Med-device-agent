"""One pinned 076 ALTER uses SQLGlot; all original CREATE bytes also use JSQLParser.
This is parser compatibility only. It executes neither SQL nor a database migration.
"""
import hashlib
from pathlib import Path
from tempfile import TemporaryDirectory
import sqlglot
from sqlglot import exp
from sqlglot.tokens import TokenType

SHA256='0059bc71ed9179fb0cadfcbb2432fa36032284ec29d4d1c4a4da5f22b07b5d57'
PRIOR_SHA256='488608fe321499210e309bec0cd7479f61dd488bc9ef28cf275cd22df96baec8'
NEW_JOBS=('SIMULATION_EVALUATION','DOCUMENT_TECHNICAL_CHECK','MODEL_VERIFICATION')
OLD_JOBS=('SIMULATION_EVALUATION','DOCUMENT_TECHNICAL_CHECK')


def _drop_check(name):
    return exp.Drop(this=exp.Table(this=exp.to_identifier(name)), kind='CHECK')


def expected_alter(jobs):
    values=','.join("'"+v+"'" for v in jobs)
    alter=sqlglot.parse_one("ALTER TABLE system_job_run "
        "ADD CONSTRAINT ck_system_simulation_workflow CHECK ((job_type IN ("+values+") AND workflow_id IS NULL) "
        "OR (job_type NOT IN ("+values+") AND workflow_id IS NOT NULL))",read='mysql')
    alter.set('actions', [_drop_check('ck_system_simulation_workflow'), *alter.args['actions']])
    return alter


def parse_workflow_alter(source):
    """Parse the exact narrow DROP CHECK grammar absent from SQLGlot 30.0.3.

    Token offsets preserve the original ADD action, including every expression.
    The DROP is fully represented, not retained as an opaque Command. This is
    deliberately not a general ALTER parser or a textual comparison fallback.
    """
    tokens=sqlglot.tokenize(source, read='mysql')
    if any(token.comments for token in tokens[1:]):
        raise ValueError('076_ACTION_COMMENTS_NOT_SUPPORTED')
    expected=(
        (TokenType.ALTER, 'ALTER'), (TokenType.TABLE, 'TABLE'),
        (TokenType.VAR, 'system_job_run'), (TokenType.DROP, 'DROP'),
        (TokenType.VAR, 'CHECK'), (TokenType.VAR, 'ck_system_simulation_workflow'),
        (TokenType.COMMA, ','), (TokenType.VAR, 'ADD'),
    )
    if len(tokens)<len(expected) or any(
            token.token_type != kind or token.text != text
            for token,(kind,text) in zip(tokens,expected)):
        raise ValueError('076_EXACT_DROP_CHECK_GRAMMAR_REQUIRED')
    # Only the header is repeated for SQLGlot; the ADD body is original source.
    parsed=sqlglot.parse(source[tokens[0].start:tokens[2].end+1] + ' ' +
                         source[tokens[7].start:], read='mysql')
    if (len(parsed)!=1 or not isinstance(parsed[0],exp.Alter)
            or parsed[0].args.get('options')
            or len(parsed[0].args.get('actions',[]))!=1
            or not isinstance(parsed[0].args['actions'][0],exp.AddConstraint)
            or any(isinstance(node,exp.Command) for node in parsed[0].walk())):
        raise ValueError('076_COMPLETE_ADD_CHECK_AST_REQUIRED')
    alter=parsed[0]
    alter.set('actions', [_drop_check(tokens[5].text), *alter.args['actions']])
    return alter


def _first_statement(source):
    for token in sqlglot.tokenize(source,read='mysql'):
        if token.token_type == TokenType.SEMICOLON:
            return source[:token.end+1], source[token.end+1:]
    raise ValueError('076_ALTER_STATEMENT_BOUNDARY_REQUIRED')


def parse_076_native_statements(path):
    from test_migration_bundle import normalize_liquibase_comments
    from test_simulation_input_artifact_migration import inspect_migration
    path=Path(path)
    if path.name!='076-formal-model-invocation.sql':
        raise ValueError('076_COMPATIBILITY_SCOPE_ONLY')
    raw=path.read_bytes()
    if hashlib.sha256(raw).hexdigest()!=SHA256:
        raise ValueError('076_ORIGINAL_BYTES_CHANGED')
    first,suffix=_first_statement(normalize_liquibase_comments(raw.decode('utf-8')))
    statements=[parse_workflow_alter(first), *sqlglot.parse(suffix,read='mysql')]
    if len(statements)!=12 or not isinstance(statements[0],exp.Alter) or not all(
            isinstance(s,exp.Create) and s.kind=='TABLE' for s in statements[1:]):
        raise ValueError('076_COMPLETE_STATEMENT_SHAPE_REQUIRED')
    if statements[0]!=expected_alter(NEW_JOBS):
        raise ValueError('076_EXACT_WORKFLOW_REPLACEMENT_REQUIRED')
    previous=path.with_name('068-document-technical-check.sql')
    previous_raw=previous.read_bytes()
    if hashlib.sha256(previous_raw).hexdigest()!=PRIOR_SHA256:
        raise ValueError('076_PRIOR_ORIGINAL_BYTES_CHANGED')
    old_source,_=_first_statement(normalize_liquibase_comments(previous_raw.decode('utf-8')))
    old=parse_workflow_alter(old_source)
    if old!=expected_alter(OLD_JOBS):
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
