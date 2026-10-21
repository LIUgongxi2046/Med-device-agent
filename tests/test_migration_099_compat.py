import hashlib,itertools,re,unittest
from pathlib import Path
from tempfile import TemporaryDirectory
from unittest.mock import patch
import sqlglot
from sqlglot import exp
import migration_099_compat as compat
from test_migration_bundle import normalize_liquibase_comments
ROOT=Path(__file__).resolve().parents[1]
MIGRATIONS=ROOT/'business/medical-data/src/main/resources/db/migration'
CURRENT=MIGRATIONS/'099-task-professional-runtime.sql'
PREVIOUS=MIGRATIONS/'075-persistent-run-runtime.sql'

def evaluate(node,row):
    if isinstance(node,exp.Paren):return evaluate(node.this,row)
    if isinstance(node,exp.And):return evaluate(node.this,row) and evaluate(node.expression,row)
    if isinstance(node,exp.Or):return evaluate(node.this,row) or evaluate(node.expression,row)
    if isinstance(node,exp.EQ):return evaluate(node.this,row)==evaluate(node.expression,row)
    if isinstance(node,exp.GT):return evaluate(node.this,row)>evaluate(node.expression,row)
    if isinstance(node,exp.In):return evaluate(node.this,row) in [evaluate(x,row) for x in node.expressions]
    if isinstance(node,exp.RegexpLike):return re.search(evaluate(node.expression,row),evaluate(node.this,row)) is not None
    if isinstance(node,exp.Column):return row[node.name]
    if isinstance(node,exp.Literal):return node.this if node.is_string else int(node.this)
    raise AssertionError('Uninspected predicate '+type(node).__name__)

class Migration099CompatibilityTests(unittest.TestCase):
    def source(self):return compat._first_statement(normalize_liquibase_comments(CURRENT.read_text()))[0]
    def test_original_files_and_complete_drop_add_ast(self):
        self.assertEqual(compat.SHA256,hashlib.sha256(CURRENT.read_bytes()).hexdigest())
        self.assertEqual(compat.PRIOR_SHA256,hashlib.sha256(PREVIOUS.read_bytes()).hexdigest())
        tree=compat.parse_runtime_alter(self.source())
        self.assertEqual([exp.Drop,exp.AddConstraint],list(map(type,tree.args['actions'])))
        self.assertEqual('CHECK',tree.args['actions'][0].args['kind'])
        self.assertEqual('runtime_object_intent_chk_3',tree.args['actions'][0].this.name)
        self.assertFalse(list(tree.find_all(exp.Command)))
        self.assertEqual(1,len(list(tree.find_all(exp.CheckColumnConstraint))))
    def test_all_original_create_bytes_reach_native_parser(self):
        marker=b'--changeset medical-agent:099-02-professional-supervision';expected=marker+CURRENT.read_bytes().split(marker)[1]
        def native(path):
            self.assertEqual(expected,Path(path).read_bytes())
            tables=sqlglot.parse(normalize_liquibase_comments(expected.decode()),read='mysql')
            return ([{'create':t.sql(dialect='mysql')}for t in tables],{t.this.this.name:t for t in tables})
        with patch('test_simulation_input_artifact_migration.inspect_migration',side_effect=native) as called:
            result=compat.parse_099_native_statements(CURRENT);called.assert_called_once()
        self.assertEqual(3,len(result));self.assertFalse(any(list(t.find_all(exp.Command))for t in result))
    def test_actual_native_parser_of_both_create_tables(self):
        result=compat.parse_099_native_statements(CURRENT)
        self.assertEqual(['professional_runtime_supervision','runtime_professional_object_binding'],[s.this.this.name for s in result[1:]])
        self.assertTrue(all(list(s.find_all(exp.ForeignKey))for s in result[1:]))
    def test_wrong_drop_identity_grammar_comments_and_named_add_rejected(self):
        for before,after in [('runtime_object_intent','other_table'),('DROP CHECK runtime_object_intent_chk_3','DROP CHECK wrong'),('DROP CHECK','DROP CONSTRAINT'),('DROP CHECK','DROP CHECK IF EXISTS'),('DROP CHECK','DROP /* hidden */ CHECK'),('DROP CHECK',"DROP 'CHECK'"),('ck_runtime_object_typed_version','wrong_constraint')]:
            with self.subTest(after=after),self.assertRaises(ValueError):compat.parse_runtime_alter(self.source().replace(before,after,1))
    def test_injected_actions_and_statements_rejected(self):
        for suffix in [', ADD COLUMN injected INT;', '; DROP TABLE runtime_object_intent;', ', ALGORITHM=INPLACE;']:
            with self.subTest(suffix=suffix),self.assertRaises((ValueError,sqlglot.errors.ParseError)):
                compat.parse_runtime_alter(self.source().rstrip().removesuffix(';')+suffix)
    def test_whitespace_preserves_all_predicates(self):
        actual=self.source();self.assertEqual(compat.parse_runtime_alter(actual),compat.parse_runtime_alter(actual[actual.index('ALTER TABLE'):].replace('\n','  ').replace(',',',  ')))
    def test_changed_predicate_is_not_hidden_as_opaque_success(self):
        original=compat.parse_runtime_alter(self.source())
        for before,after in [("schema_version='2.0.0'","schema_version='3.0.0'"),('size_bytes>0','size_bytes>=0'),("write_kind='TASK_EXACT_INPUT'","write_kind='INVENTED'"),("StepResult'","OtherResult'")]:
            with self.subTest(after=after):
                altered=compat.parse_runtime_alter(self.source().replace(before,after));self.assertNotEqual(original,altered);self.assertFalse(list(altered.find_all(exp.Command)))
    def test_changed_current_or_prior_bytes_rejected_before_native_parser(self):
        for current_delta,prior_delta in [(b'\n',b''),(b'',b'\n')]:
            with TemporaryDirectory()as folder:
                path=Path(folder)/CURRENT.name;path.write_bytes(CURRENT.read_bytes()+current_delta);path.with_name(PREVIOUS.name).write_bytes(PREVIOUS.read_bytes()+prior_delta)
                with patch('test_simulation_input_artifact_migration.inspect_migration')as native,self.assertRaisesRegex(ValueError,'ORIGINAL_BYTES_CHANGED'):compat.parse_099_native_statements(path)
                native.assert_not_called()
    def test_wrong_file_and_missing_prior_are_rejected(self):
        with self.assertRaisesRegex(ValueError,'SCOPE_ONLY'):compat.parse_099_native_statements(PREVIOUS)
        with TemporaryDirectory()as folder:
            path=Path(folder)/CURRENT.name;path.write_bytes(CURRENT.read_bytes())
            with self.assertRaisesRegex(ValueError,'PRIOR_ORIGINAL_BYTES_REQUIRED'):compat.parse_099_native_statements(path)
    def test_v1_preservation_and_closed_v2_mapping_for_2016_cases(self):
        new=next(compat.parse_runtime_alter(self.source()).find_all(exp.CheckColumnConstraint)).this
        old_table=next(s for s in sqlglot.parse(normalize_liquibase_comments(PREVIOUS.read_text()),read='mysql')if isinstance(s,exp.Create)and s.this.this.name=='runtime_object_intent')
        old_checks=list(old_table.find_all(exp.CheckColumnConstraint));self.assertEqual(3,len(old_checks));old=old_checks[2].this
        count=0
        for version,kind,ref,size,object_ok,write_ok,payload_ok in itertools.product(['1.0.0','2.0.0','3.0.0'],['REPLAY_MANIFEST',*compat.V2_REFS,'UNKNOWN'],[*compat.V2_REFS.values(),'other'],[0,1],[False,True],[False,True],[False,True]):
            row={'schema_version':version,'write_kind':kind,'schema_ref':ref,'size_bytes':size,'object_sha256':'a'*64 if object_ok else 'BAD','write_key':'a'*64 if write_ok else 'BAD','payload_schema_sha256':'a'*64 if payload_ok else 'BAD'}
            actual=evaluate(new,row)
            if version=='1.0.0':self.assertEqual(evaluate(old,row),actual,row)
            expected=size>0 and object_ok and write_ok and payload_ok and ((version=='1.0.0'and kind in ['REPLAY_MANIFEST','STEP_ENVELOPE','STEP_RESULT'])or(version=='2.0.0'and compat.V2_REFS.get(kind)==ref))
            self.assertEqual(expected,actual,row);count+=1
        self.assertEqual(2016,count)

if __name__=='__main__':unittest.main()
