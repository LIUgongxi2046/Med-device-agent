"""Offline N07 contract assertions. Deliberately authored, not executed in the code-only phase."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "business/medical-persistence/src/main/java/com/ruoyi/medical/persistence"
SQL = ROOT / "business/medical-data/src/main/resources/db/migration/122-document-professional-review.sql"


class SavedDocumentReviewContracts(unittest.TestCase):
    def test_original_operation_and_outputs_are_immutable(self):
        sql = SQL.read_text()
        for name in ("document_review_intent", "document_review_object_intent", "document_review_object_attempt", "document_review_object", "document_review_result"):
            self.assertIn("CREATE TABLE " + name, sql)
            self.assertIn("BEFORE UPDATE ON " + name, sql)
            self.assertIn("BEFORE DELETE ON " + name, sql)
        self.assertIn("uq_document_review_origin(tenant_id,operation_id)", sql)

    def test_direct_save_enqueues_exact_document_job(self):
        source = (JAVA / "DocumentRevisionService.java").read_text()
        self.assertIn("professionalReviews.enqueueSaved(admission, documentId, command.documentRevision(), operation.operationId())", source)
        self.assertNotIn('"document.check.requested"', source)
        producer = (JAVA / "DocumentReviewJobService.java").read_text()
        for pin in ("original_idempotency_key", "original_request_sha256", "document_revision", "body_sha256", "CHECK_DOCUMENT", "DRAFT_CHECK_PENDING"):
            self.assertIn(pin, producer)
        self.assertNotIn("UPDATE task_run", producer)

    def test_actual_body_proof_not_candidate_pass(self):
        source = (JAVA / "DocumentReviewAuthority.java").read_text()
        self.assertIn("documents.readFixed", source)
        self.assertIn("proof.bodyBytes()", source)
        self.assertIn('"document.check"', source)
        self.assertNotIn("candidate", source.replace("A candidate receipt", ""))
        result = (JAVA / "DocumentReviewResultService.java").read_text()
        self.assertIn("normalizeDocument", result)
        self.assertIn('put("professional_approval",false)', result)
        self.assertIn("DocumentReviewObjectLedger.Registered", result)

    def test_new_revision_invalidates_existing_professional_checks(self):
        mapper = (ROOT / "business/medical-persistence/src/main/resources/medical-mappers/DocumentRevisionMapper.xml").read_text()
        self.assertIn("DOCUMENT_REVISION_CHANGED", mapper)
        self.assertIn("document_revision=#{input.baseRevision}", mapper)

    def test_real_transfer_and_unknown_outcome_recovery(self):
        source = (JAVA / "DocumentReviewObjectLedger.java").read_text()
        self.assertIn("RuntimeObjectTransport.VerifiedTransfer", source)
        self.assertIn("physical.originalAttempt()!=attempt", source)
        self.assertIn("afterCommit()", source)
        self.assertIn("TransferMode.RECOVER_ONLY", source)
        self.assertIn('"SAVED_DOCUMENT_REVIEW_INPUT"', source)
