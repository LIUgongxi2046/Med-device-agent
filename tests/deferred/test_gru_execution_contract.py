"""Deferred B12 source/contract guard tests. Not a DB/provider/runtime verification."""
import json
import pathlib
import unittest
ROOT = pathlib.Path(__file__).resolve().parents[2]
JAVA = ROOT / 'business/medical-persistence/src/main/java/com/ruoyi/medical/persistence'
class GruExecutionContractTests(unittest.TestCase):
    def test_closed_confirmation(self):
        contract = json.loads((ROOT / 'business/medical-persistence/src/main/resources/gru-execution.schema.json').read_text())
        confirmation = contract['$defs']['Confirmation']
        self.assertFalse(confirmation['additionalProperties'])
        self.assertEqual(confirmation['properties']['confirm'], {'const': True})
        self.assertEqual(set(confirmation['required']), {'schema_version','confirm','request_sha256','target_version','parameters'})
    def test_no_project_mutation_or_fake_model_receipt(self):
        service = (JAVA / 'GruExecutionService.java').read_text()
        self.assertIn('starts.create(user,context,key', service)
        self.assertIn('cancellations.cancel(user,context', service)
        self.assertIn('required(adoption).confirm', service)
        self.assertNotIn('UPDATE document_head', service)
        self.assertNotIn('INSERT INTO invocation', service)
        self.assertNotIn('INSERT INTO permission', service)
        self.assertIn('task.input().goal()', service)
    def test_legacy_record_only_unchanged(self):
        service = (JAVA / 'GruService.java').read_text()
        self.assertIn('same("USER",row.senderKind())', service)
        self.assertIn('same("NOT_EXECUTED",row.executionState())', service)
    def test_migration_one_use_and_metadata(self):
        sql = (ROOT / 'business/medical-data/src/main/resources/db/migration/162-gru-execution.sql').read_text()
        self.assertEqual(sql.count('CREATE TABLE '), 4)
        self.assertEqual(sql.count('created_by CHAR(36)'), 4)
        self.assertIn('NEW.revision<>OLD.revision+1', sql)
        self.assertIn('OLD.confirmation_json IS NOT NULL', sql)
        self.assertIn('IMMUTABLE_GRU_EXECUTION_MESSAGE', sql)
    def test_owner_memory_has_current_consumer_and_removal(self):
        memory = (JAVA / 'GruPresentationMemory.java').read_text()
        self.assertIn("status='ACTIVE' AND revision=? AND presentation_sha256=?", memory)
        self.assertIn("SET status='REMOVED',revision=revision+1", memory)
        self.assertIn('GRU_RESULT_WRAPPER_ONLY', memory)
        self.assertIn('canonical_result_unchanged', memory)
        self.assertIn('source_refs_sha256', memory)
        execution = (JAVA / 'GruExecutionService.java').read_text()
        self.assertIn('preferences.capture(a,proof,action)', execution)
        self.assertIn('preferences.present(a,proof,action', execution)
if __name__ == '__main__':
    unittest.main()
