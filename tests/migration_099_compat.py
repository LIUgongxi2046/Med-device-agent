"""Pinned099 DROP CHECK compatibility. Parse every original action and CREATE.
No SQL execution and no changes to075/099 migration bytes.
"""
import hashlib
from pathlib import Path
from tempfile import TemporaryDirectory
import sqlglot
from sqlglot import exp
from sqlglot.tokens import TokenType

SHA256 = 'c0b10a5cc84fd0a3f18bc632a7f5eddf4314d07412779981efb3dc34fda3791a'
PRIOR_SHA256 = '42a95302147e3ac6e250397a925f4b83d7a2e3d7cce6b4e842ccedc5eae5cf8e'
V2_REFS = {
    'STEP_ENVELOPE': 'urn:medical:task-research-step:1.0.0#/$defs/StepEnvelope',
    'STEP_RESULT': 'urn:medical:task-research-step:1.0.0#/$defs/StepResult',
    'TASK_EXACT_INPUT': 'urn:medical:task-invocation-context:1.0.0#/$defs/ExactInput',
    'SESSION_EXPORT': 'urn:medical:task-research-step:1.0.0#/$defs/SessionExport',
    'EVIDENCE_BUNDLE': 'https://schemas.medical-agent.invalid/1.0.0/agent-design/artifacts.schema.json#/$defs/EvidenceBundle',
}

def _first_statement(source):
    for token in sqlglot.tokenize(source, read='mysql'):
        if token.token_type == TokenType.SEMICOLON:
            return source[:token.end + 1], source[token.end + 1:]
    raise ValueError('099_ALTER_STATEMENT_BOUNDARY_REQUIRED')

def parse_runtime_alter(source):
    tokens = sqlglot.tokenize(source, read='mysql')
    if any(token.comments for token in tokens[1:]):
        raise ValueError('099_ACTION_COMMENTS_NOT_SUPPORTED')
    expected = ((TokenType.ALTER, 'ALTER'), (TokenType.TABLE, 'TABLE'),
                (TokenType.VAR, 'runtime_object_intent'), (TokenType.DROP, 'DROP'),
                (TokenType.VAR, 'CHECK'), (TokenType.VAR, 'runtime_object_intent_chk_3'),
                (TokenType.COMMA, ','), (TokenType.VAR, 'ADD'))
    if len(tokens) < len(expected) or any(t.token_type != kind or t.text != value for t, (kind, value) in zip(tokens, expected)):
        raise ValueError('099_EXACT_DROP_CHECK_GRAMMAR_REQUIRED')
    # Preserve the exact original ADD action, including every predicate expression.
    parsed = sqlglot.parse(source[tokens[0].start:tokens[2].end + 1] + ' ' + source[tokens[7].start:], read='mysql')
    if (len(parsed) != 1 or not isinstance(parsed[0], exp.Alter) or parsed[0].args.get('options')
            or len(parsed[0].args.get('actions', [])) != 1
            or not isinstance(parsed[0].args['actions'][0], exp.AddConstraint)
            or list(parsed[0].find_all(exp.Command))):
        raise ValueError('099_COMPLETE_ADD_CHECK_AST_REQUIRED')
    alter = parsed[0]
    constraints = list(alter.find_all(exp.Constraint))
    if len(constraints) != 1 or constraints[0].this.name != 'ck_runtime_object_typed_version' or len(list(alter.find_all(exp.CheckColumnConstraint))) != 1:
        raise ValueError('099_EXACT_NAMED_CHECK_REQUIRED')
    drop = exp.Drop(this=exp.Table(this=exp.to_identifier(tokens[5].text)), kind='CHECK')
    alter.set('actions', [drop, *alter.args['actions']])
    return alter

def parse_099_native_statements(path):
    from test_migration_bundle import normalize_liquibase_comments
    from test_simulation_input_artifact_migration import inspect_migration
    path = Path(path)
    if path.name != '099-task-professional-runtime.sql':
        raise ValueError('099_COMPATIBILITY_SCOPE_ONLY')
    raw = path.read_bytes()
    if hashlib.sha256(raw).hexdigest() != SHA256:
        raise ValueError('099_ORIGINAL_BYTES_CHANGED')
    previous = path.with_name('075-persistent-run-runtime.sql')
    if not previous.is_file():
        raise ValueError('099_PRIOR_ORIGINAL_BYTES_REQUIRED')
    prior = previous.read_bytes()
    if hashlib.sha256(prior).hexdigest() != PRIOR_SHA256:
        raise ValueError('099_PRIOR_ORIGINAL_BYTES_CHANGED')
    old_tables = sqlglot.parse(normalize_liquibase_comments(prior.decode('utf8')), read='mysql')
    intent = next(s for s in old_tables if isinstance(s, exp.Create) and s.this.this.name == 'runtime_object_intent')
    if len(list(intent.find_all(exp.CheckColumnConstraint))) != 3:
        raise ValueError('099_PRIOR_DROP_ORDINAL_CHANGED')
    first, suffix = _first_statement(normalize_liquibase_comments(raw.decode('utf8')))
    statements = [parse_runtime_alter(first), *sqlglot.parse(suffix, read='mysql')]
    names = ['professional_runtime_supervision', 'runtime_professional_object_binding']
    if (len(statements) != 3 or any(list(s.find_all(exp.Command)) for s in statements)
            or not all(isinstance(s, exp.Create) and s.kind == 'TABLE' for s in statements[1:])
            or [s.this.this.name for s in statements[1:]] != names):
        raise ValueError('099_COMPLETE_STATEMENT_SHAPE_REQUIRED')
    marker = b'--changeset medical-agent:099-02-professional-supervision'
    if raw.count(marker) != 1:
        raise ValueError('099_CREATE_BOUNDARY_REQUIRED')
    # Native parser sees actual original CREATE bytes, not a rewritten SQL projection.
    with TemporaryDirectory(prefix='medical-099-parser-') as folder:
        creates = Path(folder) / '099-original-create-statements.sql'
        creates.write_bytes(marker + raw.split(marker)[1])
        native, tables = inspect_migration(creates)
    if len(native) != 2 or set(tables) != set(names) or not all(set(s) == {'create'} for s in native):
        raise ValueError('099_ALL_CREATES_REQUIRE_NATIVE_PARSER')
    return [statements[0], *(tables[name] for name in names)]
