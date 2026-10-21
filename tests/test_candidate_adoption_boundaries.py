"""Static architectural regressions. Written for later centralized testing; not executed."""
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / 'business/medical-persistence/src/main/java/com/ruoyi/medical/persistence'

def test_confirmation_is_a_private_capability_and_atomic_consumption():
    source = (JAVA / 'DocumentCandidateAdoptionService.java').read_text()
    assert 'private Confirmed(' in source
    assert 'saved.requireFor(a,c)' in source
    assert 'reviews.enqueueSaved' in source
    assert "expires_at>?" in source
    assert 'document_adoption_completion' in source

def test_physical_originals_do_not_retain_old_transaction_authority():
    source = (JAVA / 'TaskCandidateAssemblyOriginalReader.java').read_text()
    assert 'public Loaded prepareHuman' in source
    assert 'new Verified(original,physical,load(original))' in source
    assert 'TransactionSynchronizationManager.isActualTransactionActive())bad()' in source

def test_candidate_comparison_physically_writes_then_recovers_original():
    source = (JAVA / 'CandidateComparisonService.java').read_text()
    assert 'transport.writeDocumentProjection' in source
    assert 'compareCandidate(request' in source
    assert 'candidate_comparison_completion' in source
    assert 'FIXED_DOCUMENT_STRUCTURE_CORE' not in source

def test_all_adoption_tables_have_tenant_metadata_and_immutable_originals():
    for filename in ['131-document-candidate-adoption.sql', '138-candidate-comparison.sql']:
        sql = (ROOT / 'business/medical-data/src/main/resources/db/migration' / filename).read_text()
        for table in sql.split('CREATE TABLE ')[1:]:
            definition = table.split(') ENGINE=')[0]
            for column in ['tenant_id', 'project_id', 'created_by', 'created_at', 'classification', 'schema_version', 'schema_manifest_sha256']:
                assert column in definition
        assert 'BEFORE UPDATE' in sql and 'BEFORE DELETE' in sql

def test_human_original_reader_separates_current_permissions_from_original_actor():
    source = (JAVA / 'TaskCandidateAssemblyHumanReadAuthority.java').read_text()
    assert 'a.subject()==null' in source
    assert '"task.read"' in source
    assert 'private Verified(' in source
    entry = (JAVA / 'DocumentCandidateAdoptionEntryService.java').read_text()
    assert 'originals.humanOriginal(a,ref)' in entry
    assert 'l.created_by=?' not in entry
    ledger = (JAVA / 'TaskCandidateAssemblyObjectLedger.java').read_text()
    assert 'new Attempt(row,true,original.admission().actorId())' in ledger
    assert 'original.actorId().equals(str(row,"created_by"))' in ledger
    assert '!fresh.actorId().equals(attempt.actorId())' in ledger
