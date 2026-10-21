"""N07 contract tests. Do not execute as part of the development-only delivery."""
import uuid
import pytest
from medical_professional_runtime.document_review_deployment import require_deployment
from medical_professional_runtime.host import RuntimeBlocked, digest
from medical_professional_runtime.supervisor import canonical


def protocol():
    return {'schema_version':'1.0.0','revision':str(uuid.uuid4()),'namespace':'medical',
            'control_task_queue':'n07-control','document_review_task_queue':'n07-professional',
            'workflow_type':'medical.system.check-document.v1',
            'activity':'medical.system.check-document.execute.v1','execution_kind':'SYSTEM_JOB',
            'job_type':'CHECK_DOCUMENT','node_kind':'N07','model_tool_mode':'DOCUMENT_REVIEW_N07_V1'}


def test_distinct_document_job_protocol():
    p = protocol()
    assert require_deployment(p, digest(canonical(p)), 'medical', 'n07-professional') == p


@pytest.mark.parametrize('key,value', [('execution_kind','TASK'),('job_type','PLAN_DRAFT'),('node_kind','N05'),('model_tool_mode','SOURCE_VERIFIER_N05_V1'),('workflow_type','medical.task.run')])
def test_other_lanes_are_not_aliases(key, value):
    p = protocol(); p[key] = value
    with pytest.raises(RuntimeBlocked):
        require_deployment(p, digest(canonical(p)), 'medical', 'n07-professional')


def test_control_and_model_queues_are_distinct():
    p = protocol(); p['control_task_queue'] = p['document_review_task_queue']
    with pytest.raises(RuntimeBlocked):
        require_deployment(p, digest(canonical(p)), 'medical', 'n07-professional')
