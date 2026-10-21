"""Unrun static regressions for genuine N07 physical reads and immutable facts."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "business/medical-persistence/src/main/java/com/ruoyi/medical/persistence"


def test_n07_physical_context_is_not_candidate_or_metadata_only():
    source = (JAVA / "DocumentReviewContextReads.java").read_text()
    assert "document.objectReference()" in source
    assert "consume(original,physical.original(),false,guard)" in source
    assert "consume(original,physical.parsed(),true,guard)" in source
    assert "current.requireCurrent();int count=" in source
    assert "offset+=count;current.requireCurrent();" in source
    assert "literals.reviewLiteral" in source
    assert "SourceReadProtocol.Use.MODEL_SEND" in source
    assert "writerSessionIds()" in source
    assert "TASK_SOURCE_VERIFIER_INPUT" not in source
    assert "candidateArtifactId()" not in source
    assert "requireStoredFacts" in source


def test_context_contract_has_actual_saved_body_identity():
    schema = json.loads((ROOT / "business/medical-persistence/src/main/resources/document-review-context-read.schema.json").read_text())
    assert schema["properties"]["kind"]["const"] == "N07_PHYSICAL_CONTEXT"
    assert {"baseline_sha256", "writer_session_ids", "content_members"} <= set(schema["required"])
    assert "input_receipt_sha256" not in schema["properties"]
    assert "input_id" not in schema["properties"]
    assert schema["additionalProperties"] is False


def test_read_journal_is_additive_immutable_and_not_task_run_fk():
    sql = (ROOT / "business/medical-data/src/main/resources/db/migration/127-document-review-context-read.sql").read_text()
    assert "CREATE TABLE document_review_context_read" in sql
    assert "CREATE TABLE document_review_independent_read" in sql
    assert "REFERENCES task_run" not in sql
    assert "BEFORE UPDATE ON document_review_context_read" in sql
    assert "BEFORE DELETE ON document_review_independent_read" in sql
    assert "baseline_sha256" in sql
