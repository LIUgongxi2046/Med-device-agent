"""DDL/source contract checks only, not proof of MySQL execution or business approval."""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
DDL = ROOT / 'business/medical-data/src/main/resources/db/migration/102-strict-comparison-governance.sql'
JAVA = ROOT / 'business/medical-persistence/src/main/java/com/ruoyi/medical/persistence'


def test_separate_immutable_family_and_cas_state():
    sql = DDL.read_text()
    names = set(re.findall(r'CREATE TABLE (\w+)', sql))
    assert names == {'strict_comparison_policy_revision', 'strict_comparison_policy_decision',
                     'strict_decimal_evidence_revision', 'strict_comparison_policy_state'}
    assert 'INSERT INTO permission_grant' not in sql
    assert 'INSERT INTO rule_version' not in sql
    assert 'REFERENCES strict_comparison_policy_decision (tenant_id,project_id,parent_id,revision_id)' in sql
    service = (JAVA / 'StrictComparisonPolicyService.java').read_text()
    assert 'UPDATE strict_comparison_policy_state' in service
    assert 'UPDATE strict_comparison_policy_revision' not in service
    assert 'UPDATE rule_version' not in service


def test_numeric_source_is_physical_read_not_candidate_input():
    code = (JAVA / 'StrictDecimalEvidenceService.java').read_text()
    assert 'readVerified(physical.original()' in code
    assert 'readVerified(physical.parsed()' in code
    assert 'processing.verify(physical.command(),result,parsed)' in code
    assert 'sources.require(a,requirement)' in code
    assert 'class Verified' in code and 'private Verified(' in code
    assert 'candidateValue' not in code and 'sourceObservation' not in code
    assert 'UnitBasis.UNKNOWN' in code


def test_fixed_running_proof_does_not_weaken_existing_ready_guard():
    run = (JAVA / 'StrictComparisonRunScope.java').read_text()
    assert 'require(TaskNodeProductionAuthority.Verified production)' in run
    assert 'production.requireUnchanged()' in run
    assert 'plans.readFixed' in run and 'templates.requireTarget' in run
    human = (JAVA / 'TaskFieldTargetBasis.java').read_text()
    assert 'admission.formalWorker()' in human
    plans = (JAVA / 'TaskPlanService.java').read_text()
    execution = plans.split('Confirmation execution(', 1)[1].split('Confirmation executionContinuation(', 1)[0]
    assert 'if (!"READY".equals(fixed.head().status())) fail(ErrorCode.PLAN_STALE);' in execution
    assert execution.index('fail(ErrorCode.PLAN_STALE)') < execution.index('executionConfirmed(admission, fixed, plan)')


def test_policy_state_preserves_original_audit_metadata():
    sql = DDL.read_text().split('CREATE TABLE strict_comparison_policy_state', 1)[1]
    for field in ('created_by', 'created_at', 'classification', 'schema_version', 'schema_manifest_sha256'):
        assert re.search(r'\b' + field + r'\s+(?:CHAR|VARCHAR|DATETIME)', sql)
    code = (JAVA / 'StrictComparisonPolicyService.java').read_text()
    assert "SELECT tenant_id,project_id,revision_id,1,'DRAFT',NULL,schema_version,schema_manifest_sha256,created_by,created_at,classification" in code
    for field in ('created_by', 'created_at', 'classification', 'schema_version', 'schema_manifest_sha256'):
        assert f'p.{field}=s.{field}' in code
