"""Source-only EVAL regressions. Intentionally not executed during development."""
import ast
import inspect
from pathlib import Path
from uuid import uuid4
import pytest
from medical_professional_runtime.evaluation_validation import require_prompt
from medical_professional_runtime.evaluation_activity import node_receipt, route, EvaluationActivities
from medical_professional_runtime.evaluation_deployment import require_protocol
from medical_professional_runtime.host import RuntimeBlocked, digest
from medical_professional_runtime.supervisor import canonical


def exact():
    text=canonical({'messages':[{'role':'system','content':'批准系统指令'},{'role':'user','content':'原始输入'}],'tools':[]}).decode()
    pin=digest(text.encode())
    provenance={'schema_version':'1.0.0','compiler':'EVALUATION_REGISTERED_EXACT_INPUT_V1','node_kind':'N04','input_artifact_id':str(uuid4()),'input_sha256':pin,'launch_sha256':pin,'original_compiler':{'source':'fixture-only'}}
    return text,pin,provenance


def test_eval_n04_is_not_task_n04():
    text,pin,provenance=exact()
    assert require_prompt(text,pin,provenance,pin)['messages'][0]['role']=='system'
    provenance['compiler']='N04_LITERAL_INSTRUCTIONS_AND_REGISTERED_INPUT_V1'
    with pytest.raises(RuntimeBlocked):require_prompt(text,pin,provenance,pin)


def test_exact_bytes_and_system_user_order_are_bound():
    text,pin,provenance=exact()
    with pytest.raises(RuntimeBlocked):require_prompt(text+' ',pin,provenance,pin)
    changed=canonical({'messages':[{'role':'user','content':'x'},{'role':'system','content':'y'}],'tools':[]}).decode()
    pin=digest(changed.encode());provenance.update(input_sha256=pin,launch_sha256=pin)
    with pytest.raises(RuntimeBlocked):require_prompt(changed,pin,provenance,pin)


def test_recovery_activity_cannot_call_execute_or_open():
    source=inspect.getsource(EvaluationActivities.recover)
    assert "'recover-node'" in source
    assert "'open'" not in source and '.execute(' not in source and '.admit(' not in source


def test_no_task_reference_or_unowned_receipt():
    with pytest.raises(RuntimeBlocked):route({'schemaVersion':'1.0.0','executionKind':'TASK','jobType':'EVALUATION_VERIFICATION'})
    ref={'runId':str(uuid4())};step={'stepId':str(uuid4()),'nodeSha256':'a'*64}
    receipt={'schemaVersion':'1.0.0','runId':str(uuid4()),'stepId':step['stepId'],'nodeSha256':'a'*64,'state':'SUCCEEDED','receiptId':str(uuid4()),'receiptSha256':'b'*64}
    with pytest.raises(RuntimeBlocked):node_receipt(receipt,ref,step)


def test_pinned_workflow_and_separate_queues():
    p={'schema_version':'1.0.0','revision':str(uuid4()),'namespace':'fixture','control_task_queue':'eval-control','professional_task_queue':'eval-professional','workflow_type':'medical.system.formal-evaluation.v1','execution_kind':'SYSTEM_JOB','job_type':'EVALUATION_VERIFICATION','model_tool_mode':'EVALUATION_VERIFICATION_V1'}
    assert require_protocol(p,digest(canonical(p)),'fixture')==p
    p['professional_task_queue']=p['control_task_queue']
    with pytest.raises(RuntimeBlocked):require_protocol(p,digest(canonical(p)),'fixture')


def test_host_has_one_open_and_no_claimed_completion():
    path=Path(__file__).parents[1]/'src/medical_professional_runtime/evaluation_activity.py'
    module=ast.parse(path.read_text())
    host=next(node for node in module.body if isinstance(node,ast.ClassDef) and node.name=='EvaluationHost')
    opens=[node for node in ast.walk(host) if isinstance(node,ast.Call) and node.args and isinstance(node.args[0],ast.Constant) and node.args[0].value=='open']
    assert len(opens)==1
    source=ast.get_source_segment(path.read_text(),host)
    assert "'host_process_exited'" not in source and "'observation':" not in source


def test_backend_has_real_cancel_plan_and_cell_consumers():
    root = Path(__file__).resolve().parents[2]
    backend = root / 'business/medical-persistence/src/main/java/com/ruoyi/medical/persistence'
    controller = (backend / 'FormalEvaluationRuntimeController.java').read_text()
    service = (backend / 'FormalEvaluationRuntimeService.java').read_text()
    cells = (backend / 'FormalEvaluationCellCompletion.java').read_text()
    assert '/evaluation-runtime/cancel-plan' in controller
    assert '/evaluation-runtime/cancel-cell' in controller
    assert 'supervisor.recordCancellation' in service
    assert 'runtime.cancellationTerminations(proof)' in cells
    assert 'invocations.requireQuiescentOriginal' in cells
    assert 'cancelUnknown' in service


def test_backend_stage_recovery_never_recompiles_or_uploads_input():
    root = Path(__file__).resolve().parents[2]
    backend = root / 'business/medical-persistence/src/main/java/com/ruoyi/medical/persistence'
    source = (backend / 'FormalEvaluationNodeInputs.java').read_text()
    recovery = source.split('public Verified readOriginal(', 1)[1].split('private Verified stage(', 1)[0]
    assert 'stageObjects.readOriginal' in recovery and 'reader.readVerified' in recovery
    assert 'stages.prepare' not in recovery and 'transferVerified' not in recovery
    assert 'tokenizer' not in recovery


def test_temporal_cancel_waits_for_original_terminal_evidence():
    root = Path(__file__).resolve().parents[2]
    workflow = (root / 'business/medical-workflow/src/main/java/com/ruoyi/medical/workflow/FormalEvaluationWorkflowImpl.java').read_text()
    detached = workflow.split('Workflow.newDetachedCancellationScope(', 1)[1]
    assert '"CANCELLED".equals(result[0].state())' in detached
    assert '"UNKNOWN".equals(result[0].state())' in detached
    assert 'Workflow.sleep' in detached


def test_pure_n03_has_no_professional_session_or_provider_receipt():
    root = Path(__file__).resolve().parents[2]
    backend = root / 'business/medical-persistence/src/main/java/com/ruoyi/medical/persistence'
    producer = (backend / 'FormalEvaluationDeterministicRuntime.java').read_text()
    service = (backend / 'FormalEvaluationRuntimeService.java').read_text()
    assert 'checkDeterministic' in producer
    assert 'formal_evaluation_deterministic_outcome' in producer
    assert 'INSERT INTO professional_runtime_session' not in producer
    assert 'INSERT INTO invocation ' not in producer
    assert 'INSERT INTO check_run ' not in producer
    assert 'result.artifact(),null,List.of()' in producer
    assert 'deterministicObjects.register' in service
    assert 'runtime.requireOriginalCompleted' in service


def test_deterministic_original_artifacts_are_not_generic_unowned_json():
    root = Path(__file__).resolve().parents[2]
    mapper = (root / 'business/medical-persistence/src/main/resources/medical-mappers/ArtifactRegistryMapper.xml').read_text()
    backend = root / 'business/medical-persistence/src/main/java/com/ruoyi/medical/persistence'
    objects = (backend / 'FormalEvaluationRuntimeObjects.java').read_text()
    assert 'formal_evaluation_deterministic_object_intent' in mapper
    assert 'formal_evaluation_stage_input_intent' in mapper
    assert 'deterministic.readOriginalArtifact' in objects
    assert 'stageInputs.readOriginalArtifact' in objects


def test_pure_assembly_and_factual_reads_use_distinct_authorities():
    root = Path(__file__).resolve().parents[2]
    backend = root / 'business/medical-persistence/src/main/java/com/ruoyi/medical/persistence'
    protocol = (backend / 'FormalEvaluationExecutionProtocol.java').read_text()
    checks = (backend / 'FormalEvaluationBusinessChecks.java').read_text()
    fixed = (backend / 'FormalEvaluationFixedResults.java').read_text()
    evidence = (backend / 'FormalEvaluationExecutionEvidence.java').read_text()
    assert 'Set.of("N03","N06").contains(node.nodeKind())' in protocol
    assert 'checkDeterministic(String kind' in checks
    assert 'if(!projection.isNull())' in checks
    assert 'prepareFactual' in fixed and 'requireFactual' in fixed
    assert 'ORIGINAL_FACTS_ONLY' in fixed
    assert 'class FactualEvidence' in evidence
    assert 'requiredCompletionAuthority()' in evidence
