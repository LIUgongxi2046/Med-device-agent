"""Bounded MySQL source compatibility, not database migration verification.

Only CREATE TRIGGER ... BEFORE ... FOR EACH ROW with SIGNAL/BEGIN/IF/ELSE
is supported. Every condition has a real SQLGlot AST. Unknown procedural SQL
fails closed. Normal SQL still goes through the existing native parser.
"""
import re
import sqlglot
from sqlglot import exp
import sqlparse


def normalize_comments(source):
    return re.sub(r'(?m)^--(?=(?:liquibase formatted sql|changeset [^\r\n]+)\r?$)', '-- ', source)


def parse_trigger(source):
    tokens = sqlglot.tokenize(source, read='mysql')
    index = 0
    def take(value):
        nonlocal index
        if index >= len(tokens) or tokens[index].text.upper() != value:
            raise ValueError('TRIGGER_EXPECTED_' + value)
        index += 1
    def identifier():
        nonlocal index
        if index >= len(tokens) or not re.fullmatch(r'[A-Za-z_][A-Za-z_0-9]*', tokens[index].text):
            raise ValueError('TRIGGER_IDENTIFIER_REQUIRED')
        value = tokens[index].text
        index += 1
        return value
    def statements(stops, allow_elseif=False):
        nonlocal index
        result = []
        while index < len(tokens) and tokens[index].text.upper() not in stops:
            word = tokens[index].text.upper()
            if word == 'SIGNAL':
                take('SIGNAL'); take('SQLSTATE')
                token = tokens[index]; index += 1
                if source[token.start:token.end + 1] != "'45000'":
                    raise ValueError('TRIGGER_SIGNAL_45000_REQUIRED')
                take('SET'); take('MESSAGE_TEXT'); take('=')
                token = tokens[index]; index += 1
                message = sqlglot.parse_one(source[token.start:token.end + 1], read='mysql')
                if not isinstance(message, exp.Literal) or not message.is_string:
                    raise ValueError('TRIGGER_LITERAL_MESSAGE_REQUIRED')
                take(';')
                result.append({'signal': '45000', 'message': message.this})
            elif word == 'IF' or (word == 'ELSEIF' and allow_elseif):
                take(word); start = index; depth = 0
                while index < len(tokens):
                    value = tokens[index].text.upper()
                    if value == 'THEN' and depth == 0: break
                    depth += (value == '(') - (value == ')')
                    if depth < 0: raise ValueError('TRIGGER_UNBALANCED_CONDITION')
                    index += 1
                if index == start or index >= len(tokens) or depth:
                    raise ValueError('TRIGGER_CONDITION_REQUIRED')
                condition = source[tokens[start].start:tokens[index - 1].end + 1]
                parsed = sqlglot.parse('SELECT 1 WHERE ' + condition, read='mysql')
                if len(parsed) != 1 or not isinstance(parsed[0], exp.Select) or not parsed[0].args.get('where') or list(parsed[0].find_all(exp.Command)) or any(value for key, value in parsed[0].args.items() if key not in ('expressions', 'where')):
                    raise ValueError('TRIGGER_COMPLETE_CONDITION_AST_REQUIRED')
                take('THEN'); yes = statements({'ELSE', 'ELSEIF', 'END'}); no = []
                elseif = index < len(tokens) and tokens[index].text.upper() == 'ELSEIF'
                if elseif:
                    no = statements({'END'}, allow_elseif=True)
                if not elseif and index < len(tokens) and tokens[index].text.upper() == 'ELSE':
                    take('ELSE'); no = statements({'END'})
                if not elseif:
                    take('END'); take('IF'); take(';')
                if not yes: raise ValueError('TRIGGER_EMPTY_IF_NOT_SUPPORTED')
                result.append({'condition': condition, 'then': yes, 'else': no})
                if word == 'ELSEIF': return result
            else:
                raise ValueError('TRIGGER_UNSUPPORTED_BODY: ' + word)
        return result
    take('CREATE'); take('TRIGGER'); name = identifier(); take('BEFORE')
    event = identifier().upper()
    if event not in ('INSERT', 'UPDATE', 'DELETE'): raise ValueError('TRIGGER_EVENT_UNSUPPORTED')
    take('ON'); table = identifier(); take('FOR'); take('EACH'); take('ROW')
    if index < len(tokens) and tokens[index].text.upper() == 'BEGIN':
        take('BEGIN'); body = statements({'END'}); take('END'); take(';')
    else:
        body = statements(set())
        if len(body) != 1 or 'signal' not in body[0]: raise ValueError('TRIGGER_SINGLE_SIGNAL_REQUIRED')
    if index != len(tokens) or not body: raise ValueError('TRIGGER_COMPLETE_BODY_REQUIRED')
    return {'name': name, 'table': table, 'timing': 'BEFORE', 'event': event, 'body': body, 'source': source}


def split_native_source(source):
    if re.search(r'/\*[!+]', source):
        raise ValueError('EXECUTABLE_SQL_COMMENTS_NOT_SUPPORTED')
    normal, triggers = [], []
    # Liquibase custom delimiters are metadata, not SQL. Normalize only the
    # terminal delimiter of a declared changeset, never tokens inside literals.
    blocks = re.split(r'(?m)(?=^-- ?changeset )', source)
    for i, block in enumerate(blocks):
        header = block.split('\n', 1)[0]
        delimiter = re.search(r'\bendDelimiter:(\S+)', header)
        if delimiter:
            if delimiter.group(1) != '$$' or not block.rstrip().endswith('END$$'):
                raise ValueError('UNSUPPORTED_CHANGESET_DELIMITER')
            blocks[i] = block.rstrip()[:-2] + ';\n'
    source = ''.join(blocks)
    for statement in sqlparse.split(normalize_comments(source)):
        clean = sqlparse.format(statement, strip_comments=True).strip()
        if not clean: continue
        if re.match(r'CREATE\s+TRIGGER\b', clean, re.I):
            triggers.append(parse_trigger(clean))
            continue
        # Split only top-level ALTER actions. Parenthesized predicates, keys,
        # strings and quoted identifiers remain original source fragments.
        tokens = sqlglot.tokenize(clean, read='mysql')
        if len(tokens) >= 4 and [t.text.upper() for t in tokens[:2]] == ['ALTER', 'TABLE']:
            header = clean[:tokens[2].end + 1]
            start = tokens[3].start; depth = 0
            for token in tokens[3:]:
                from sqlglot.tokens import TokenType
                if token.token_type == TokenType.L_PAREN: depth += 1
                elif token.token_type == TokenType.R_PAREN: depth -= 1
                elif token.token_type == TokenType.COMMA and depth == 0:
                    normal.append(header + ' ' + clean[start:token.start].strip() + ';')
                    start = token.end + 1
                if depth < 0: raise ValueError('ALTER_UNBALANCED_ACTION')
            if depth: raise ValueError('ALTER_UNBALANCED_ACTION')
            normal.append(header + ' ' + clean[start:].strip().rstrip(';') + ';')
        else:
            normal.append(statement)
    return normal, triggers


def parse_schema_source(source):
    """Strict source ASTs for column/key checks, not an executable migration plan.

    MODIFY COLUMN is projected as an ALTER column definition, preserving every
    type/constraint token; DROP CHECK has an explicit structured drop action.
    Trigger bodies are fully validated separately by the bounded source parser.
    Unknown commands or extra statements are rejected, never silently omitted.
    """
    statements, triggers = split_native_source(source)
    parsed = []
    identifier = r'(?:`[A-Za-z_][A-Za-z_0-9]*`|[A-Za-z_][A-Za-z_0-9]*)'
    for statement in statements:
        clean = sqlparse.format(statement, strip_comments=True).strip()
        modify = re.fullmatch(r'ALTER\s+TABLE\s+(' + identifier + r')\s+MODIFY\s+(?:COLUMN\s+)?(.+?)\s*;?', clean, re.I | re.S)
        drop = re.fullmatch(r'ALTER\s+TABLE\s+(' + identifier + r')\s+DROP\s+CHECK\s+(' + identifier + r')\s*;?', clean, re.I | re.S)
        if modify:
            definitions = sqlglot.parse('CREATE TABLE ' + modify[1] + ' (' + modify[2].rstrip(';') + ')', read='mysql')
            if len(definitions) != 1 or not isinstance(definitions[0], exp.Create) or not isinstance(definitions[0].this, exp.Schema):
                raise ValueError('MODIFY_COMPLETE_COLUMN_AST_REQUIRED')
            schema = definitions[0].this
            if len(schema.expressions) != 1 or not isinstance(schema.expressions[0], exp.ColumnDef):
                raise ValueError('MODIFY_SINGLE_COLUMN_REQUIRED')
            ast = exp.Alter(this=schema.this, actions=schema.expressions)
            ast.meta['source_operation'] = 'MODIFY'
        elif drop:
            ast = exp.Alter(this=exp.to_table(drop[1]), actions=[exp.Drop(this=exp.to_identifier(drop[2].strip('`')), kind='CHECK')])
        else:
            values = sqlglot.parse(clean, read='mysql')
            if len(values) != 1:
                raise ValueError('SINGLE_SCHEMA_STATEMENT_REQUIRED')
            ast = values[0]
        if ast is None or list(ast.find_all(exp.Command)) or not isinstance(ast, (exp.Create, exp.Alter, exp.Insert, exp.Update)):
            raise ValueError('UNSUPPORTED_SCHEMA_STATEMENT: ' + clean[:100])
        if isinstance(ast, exp.Create) and (ast.kind != 'TABLE' or not isinstance(ast.this, exp.Schema)):
            raise ValueError('CREATE_TABLE_SCHEMA_REQUIRED')
        if isinstance(ast, exp.Alter):
            if not ast.args.get('actions') or ast.args.get('options'):
                raise ValueError('COMPLETE_SCHEMA_ALTER_ACTION_REQUIRED')
            for action in ast.args.get('actions', []):
                if not isinstance(action, (exp.ColumnDef, exp.AddConstraint, exp.AlterColumn, exp.Drop)):
                    raise ValueError('UNSUPPORTED_SCHEMA_ALTER_ACTION')
                if isinstance(action, exp.Drop) and action.kind not in ('CHECK', 'CONSTRAINT'):
                    raise ValueError('UNSUPPORTED_SCHEMA_DROP_ACTION')
        parsed.append(ast)
    return parsed, triggers


def bounded_drop_check(statement):
    """Return a complete source-backed DROP CHECK record, or defer to native SQL."""
    clean = sqlparse.format(statement, strip_comments=True).strip()
    identifier = r'(?:`[A-Za-z_][A-Za-z_0-9]*`|[A-Za-z_][A-Za-z_0-9]*)'
    match = re.fullmatch(r'ALTER\s+TABLE\s+(' + identifier + r')\s+DROP\s+CHECK\s+(' + identifier + r')\s*;?', clean, re.I | re.S)
    if not match:
        return None
    ast, triggers = parse_schema_source(clean)
    if len(ast) != 1 or triggers or len(ast[0].args['actions']) != 1 or ast[0].args['actions'][0].kind != 'CHECK':
        raise ValueError('COMPLETE_DROP_CHECK_AST_REQUIRED')
    return {'table': ast[0].this.name, 'operation': 'DROP', 'definition': clean,
            'drop_constraint': ast[0].args['actions'][0].this.name, 'source_drop_kind': 'CHECK'}
