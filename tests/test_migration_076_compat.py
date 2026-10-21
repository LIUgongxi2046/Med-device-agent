"""Scoped parser regressions. No migration, Maven, service, or credentials used."""
import hashlib
from pathlib import Path
import sys
from tempfile import TemporaryDirectory
import unittest
from unittest.mock import patch

import sqlglot
from sqlglot import exp

sys.path.insert(0, str(Path(__file__).resolve().parent))
import migration_076_compat as compat
from test_migration_bundle import normalize_liquibase_comments

MIGRATIONS=Path(__file__).resolve().parents[1]/'business/medical-data/src/main/resources/db/migration'
CURRENT=MIGRATIONS/'076-formal-model-invocation.sql'
PREVIOUS=MIGRATIONS/'068-document-technical-check.sql'


class Migration076CompatibilityTests(unittest.TestCase):
    def source(self,path=CURRENT):
        return compat._first_statement(normalize_liquibase_comments(path.read_text()))[0]

    def test_original_checksums_and_complete_ast(self):
        for path,checksum,jobs in ((CURRENT,compat.SHA256,compat.NEW_JOBS),
                                   (PREVIOUS,compat.PRIOR_SHA256,compat.OLD_JOBS)):
            self.assertEqual(checksum,hashlib.sha256(path.read_bytes()).hexdigest())
            tree=compat.parse_workflow_alter(self.source(path))
            self.assertEqual(compat.expected_alter(jobs),tree)
            self.assertEqual([exp.Drop,exp.AddConstraint],list(map(type,tree.args['actions'])))
            self.assertFalse(list(tree.find_all(exp.Command)))
            self.assertEqual('CHECK',tree.args['actions'][0].args['kind'])
            self.assertEqual(2,len(list(tree.find_all(exp.In))))

    def test_whitespace_does_not_change_semantics(self):
        self.assertEqual(compat.expected_alter(compat.NEW_JOBS),
                         compat.parse_workflow_alter(self.source()[self.source().index('ALTER TABLE'):].replace('\n','  ').replace(',',',  ')))

    def test_wrong_drop_table_constraint_or_grammar_rejected(self):
        for before,after in [('system_job_run','other_table'),
                             ('DROP CHECK ck_system_simulation_workflow','DROP CHECK other_check'),
                             ('DROP CHECK','DROP CONSTRAINT'),
                             ('DROP CHECK','DROP CHECK IF EXISTS'),
                             ('DROP CHECK','DROP /* ignored */ CHECK'),
                             ('DROP CHECK',"DROP 'CHECK'")]:
            with self.subTest(after=after), self.assertRaises(ValueError):
                compat.parse_workflow_alter(self.source().replace(before,after,1))

    def test_changed_add_constraint_job_set_and_predicate_fail_structural_gate(self):
        for before,after in [('ADD CONSTRAINT ck_system_simulation_workflow','ADD CONSTRAINT other_check'),
                             ('MODEL_VERIFICATION','OTHER_JOB'),('workflow_id IS NULL','workflow_id IS NOT NULL'),
                             ("'DOCUMENT_TECHNICAL_CHECK','MODEL_VERIFICATION'","'DOCUMENT_TECHNICAL_CHECK'")]:
            with self.subTest(after=after):
                tree=compat.parse_workflow_alter(self.source().replace(before,after))
                self.assertNotEqual(compat.expected_alter(compat.NEW_JOBS),tree)

    def test_injected_action_or_statement_rejected(self):
        for addition in [', ADD COLUMN injected INT;', '; DROP TABLE system_job_run;', ', ALGORITHM=INPLACE;']:
            with self.subTest(addition=addition), self.assertRaises((ValueError,sqlglot.errors.ParseError)):
                compat.parse_workflow_alter(self.source().rstrip().removesuffix(';')+addition)

    def test_mutated_original_bytes_rejected_before_native_parser(self):
        with TemporaryDirectory() as folder:
            path=Path(folder)/CURRENT.name
            path.write_bytes(CURRENT.read_bytes()+b'\n')
            with patch('test_simulation_input_artifact_migration.inspect_migration') as native:
                with self.assertRaisesRegex(ValueError,'076_ORIGINAL_BYTES_CHANGED'):
                    compat.parse_076_native_statements(path)
                native.assert_not_called()

    def test_prior_original_bytes_are_mandatory(self):
        with TemporaryDirectory() as folder:
            path=Path(folder)/CURRENT.name
            path.write_bytes(CURRENT.read_bytes())
            path.with_name(PREVIOUS.name).write_bytes(PREVIOUS.read_bytes()+b'\n')
            with self.assertRaisesRegex(ValueError,'076_PRIOR_ORIGINAL_BYTES_CHANGED'):
                compat.parse_076_native_statements(path)

    def test_all_original_create_bytes_sent_to_native_parser(self):
        marker=b'--changeset medical-agent:076-02-formal-model-manifest'
        expected=marker+CURRENT.read_bytes().split(marker)[1]
        def native(path):
            self.assertEqual(expected,Path(path).read_bytes())
            creates=sqlglot.parse(normalize_liquibase_comments(expected.decode()),read='mysql')
            return [{'create':tree.sql()} for tree in creates],{tree.this.this.name:tree for tree in creates}
        with patch('test_simulation_input_artifact_migration.inspect_migration',side_effect=native) as inspect:
            trees=compat.parse_076_native_statements(CURRENT)
            inspect.assert_called_once()
            self.assertEqual(12,len(trees))
            self.assertEqual(compat.expected_alter(compat.NEW_JOBS),trees[0])

    def test_missing_native_create_is_rejected(self):
        with patch('test_simulation_input_artifact_migration.inspect_migration',return_value=([],{})):
            with self.assertRaisesRegex(ValueError,'076_ALL_CREATES_REQUIRE_NATIVE_PARSER'):
                compat.parse_076_native_statements(CURRENT)


if __name__=='__main__':
    unittest.main()
