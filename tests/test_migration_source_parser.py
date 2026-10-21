import unittest
from migration_source_parser import parse_trigger, split_native_source, parse_schema_source, bounded_drop_check
import sqlglot
from sqlglot import exp


class BoundedMigrationParserTests(unittest.TestCase):
    prefix = 'CREATE TRIGGER protect BEFORE UPDATE ON evidence FOR EACH ROW '
    signal = "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='immutable';"

    def test_simple_signal_is_structured(self):
        parsed = parse_trigger(self.prefix + self.signal)
        self.assertEqual('evidence', parsed['table'])
        self.assertEqual([{'signal': '45000', 'message': 'immutable'}], parsed['body'])

    def test_nested_predicates_and_else_are_preserved(self):
        parsed = parse_trigger(self.prefix + 'BEGIN IF NEW.id <> OLD.id THEN IF EXISTS(SELECT 1 FROM audit) THEN '
            + self.signal + ' END IF; ELSE ' + self.signal + ' END IF; END;')
        self.assertEqual('NEW.id <> OLD.id', parsed['body'][0]['condition'])
        self.assertEqual('EXISTS(SELECT 1 FROM audit)', parsed['body'][0]['then'][0]['condition'])
        self.assertEqual(1, len(parsed['body'][0]['else']))

    def test_unknown_procedural_statements_fail_closed(self):
        for body in ('BEGIN DELETE FROM evidence; END;', 'BEGIN SET NEW.id=1; END;',
                     'BEGIN WHILE TRUE DO ' + self.signal + ' END WHILE; END;', self.signal + 'SELECT 1;',
                     "SIGNAL SQLSTATE '01000' SET MESSAGE_TEXT='warning';"):
            with self.subTest(body=body), self.assertRaises(ValueError):
                parse_trigger(self.prefix + body)

    def test_elseif_retains_following_sibling(self):
        source = self.prefix + 'BEGIN IF NEW.id=1 THEN ' + self.signal + ' ELSEIF NEW.id=2 THEN ' + self.signal + ' ELSE ' + self.signal + ' END IF; ' + self.signal + ' END;'
        parsed = parse_trigger(source)
        self.assertEqual(2, len(parsed['body']))
        self.assertEqual('NEW.id=2', parsed['body'][0]['else'][0]['condition'])

    def test_extra_condition_clause_and_executable_comment_rejected(self):
        with self.assertRaises(ValueError):
            parse_trigger(self.prefix + 'BEGIN IF NEW.id=1 LIMIT 1 THEN ' + self.signal + ' END IF; END;')
        with self.assertRaises(ValueError):
            split_native_source('/*! DELETE FROM evidence */;')

    def test_declared_custom_delimiter_is_bounded(self):
        source = '--changeset test:1 dbms:mysql endDelimiter:$$\n' + self.prefix + 'BEGIN ' + self.signal + ' END$$\n'
        normal, triggers = split_native_source(source)
        self.assertFalse(normal); self.assertEqual(1, len(triggers))
        with self.assertRaises(ValueError): split_native_source(source.replace('endDelimiter:$$', 'endDelimiter://'))

    def test_compound_alter_preserves_literal_and_predicate_commas(self):
        normal, triggers = split_native_source("ALTER TABLE evidence ADD COLUMN tags VARCHAR(40) DEFAULT 'a,b', "
            "ADD CONSTRAINT ck_tags CHECK(tags IN ('a,b','c'));")
        self.assertFalse(triggers)
        self.assertEqual(2, len(normal))
        self.assertIn("DEFAULT 'a,b'", normal[0])
        self.assertIn("IN ('a,b','c')", normal[1])

    def test_trigger_and_create_both_retained(self):
        normal, triggers = split_native_source('CREATE TABLE evidence(id INT);\n' + self.prefix + self.signal)
        self.assertEqual(1, len(normal)); self.assertEqual(1, len(triggers))

    def test_schema_projection_preserves_modified_column_and_drop_check(self):
        ast, triggers = parse_schema_source("ALTER TABLE evidence MODIFY COLUMN value VARCHAR(40) COLLATE utf8mb4_bin NOT NULL; ALTER TABLE evidence DROP CHECK ck_value;")
        self.assertFalse(triggers)
        column = next(ast[0].find_all(exp.ColumnDef))
        self.assertEqual('value', column.name)
        self.assertIn('VARCHAR(40)', column.sql('mysql'))
        self.assertIn('COLLATE utf8mb4_bin', column.sql('mysql'))
        self.assertIn('NOT NULL', column.sql('mysql'))
        self.assertEqual('MODIFY', ast[0].meta['source_operation'])
        self.assertEqual('CHECK', ast[1].args['actions'][0].kind)
        self.assertEqual('ck_value', ast[1].args['actions'][0].this.name)

    def test_schema_projection_rejects_unknown_or_injected_actions(self):
        for source in ('ALTER TABLE evidence RENAME TO hidden;',
                       'ALTER TABLE evidence MODIFY COLUMN value INT; DELETE FROM evidence;',
                       'ALTER TABLE evidence MODIFY COLUMN value INT, unknown INT;',
                       'CREATE VIEW hidden AS SELECT 1;',
                       'ALTER TABLE evidence DROP CHECK ck_value CASCADE;'):
            with self.subTest(source=source), self.assertRaises((ValueError, sqlglot.errors.ParseError)):
                parse_schema_source(source)

    def test_native_drop_check_compatibility_retains_original_and_rejects_suffix(self):
        original = 'ALTER TABLE evidence DROP CHECK ck_value;'
        record = bounded_drop_check(original)
        self.assertEqual(original, record['definition'])
        self.assertEqual('evidence', record['table'])
        self.assertEqual('ck_value', record['drop_constraint'])
        self.assertEqual('CHECK', record['source_drop_kind'])
        self.assertIsNone(bounded_drop_check(original + ' DELETE FROM evidence;'))
        self.assertIsNone(bounded_drop_check('ALTER TABLE evidence DROP CHECK ck_value CASCADE;'))


    def test_signing_compound_trigger_delimiters_preserve_original_guards(self):
        from pathlib import Path
        import hashlib, json, re
        path = Path(__file__).resolve().parents[1] / 'business/medical-data/src/main/resources/db/migration/140-document-signing.sql'
        source = path.read_text()
        self.assertNotIn('END$$', source)
        for identity in ('140-40-receipt-consume', '140-44-challenge-transition'):
            self.assertRegex(source, r'(?m)^--changeset medical-agent:' + identity + r' [^\n]*splitStatements:false$')
        triggers = {t['name']: t for t in split_native_source(source)[1]}
        expected = {'b10_receipt_consume': '95aaea1d427269b9da39cb29979a853d5a51e111bf9abc33a06f837e32ad8484', 'b10_challenge_transition': '4eea75b6bdbacd092de5c7c6631bc982957553bd4659c3c32fa0ffaa00e5dad5'}
        for name, digest in expected.items():
            self.assertEqual(digest, hashlib.sha256(json.dumps(triggers[name]['body'], sort_keys=True).encode()).hexdigest())


    def test_author_precondition_decodes_only_quoted_metadata_and_keeps_exact_guard(self):
        from pathlib import Path
        import hashlib
        path = Path(__file__).resolve().parents[1] / 'business/medical-data/src/main/resources/db/migration/166-scenario-large-author-contract.sql'
        source = path.read_text()
        self.assertIn('--preconditions onFail:HALT onError:HALT', source)
        self.assertIn('--precondition-sql-check expectedResult:1', source)
        self.assertIn("tc.TABLE_SCHEMA=DATABASE() AND tc.TABLE_NAME='scenario_record'", source)
        self.assertIn("tc.CONSTRAINT_TYPE='CHECK' AND tc.CONSTRAINT_NAME='scenario_record_chk_3'", source)
        self.assertIn('REPLACE(LOWER(cc.CHECK_CLAUSE),CONCAT(CHAR(92),CHAR(39)),CHAR(39))', source)
        self.assertEqual("'schema_version=''1.0.0''andschema_manifest_sha256regexp''^[a-f0-9]{64}$''','schema_version=''1.0.0''andregexp_likeschema_manifest_sha256,''^[a-f0-9]{64}$''')", source.split(' IN (', 1)[1].split('\n', 1)[0])
        self.assertEqual('994634a94cf67a8a5a5b4e288e4a874dd241567c891f0bf9ffad5e1a689f2df9', hashlib.sha256(source.split('ALTER TABLE', 1)[1].encode()).hexdigest())

if __name__ == '__main__': unittest.main()
