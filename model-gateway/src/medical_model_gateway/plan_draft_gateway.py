"""Explicit N01 process adapter. Deployment selects the branch, never stdin JSON.
The upstream model business-purpose is TASK; execution is SYSTEM_JOB/PLAN_DRAFT.
"""
import asyncio
from .task_gateway import TaskGatewayAdapter, CONFIG_KEYS, MAXIMUM_OPERATION_SECONDS, request
from .formal_invocation_adapter import InvocationProfile, _uuid
from .formal_verification_worker import configured_components, private_json

class PlanDraftGatewayAdapter(TaskGatewayAdapter):
    def __init__(self, adapter, tenant_id, current_config):
        if adapter._profile is not InvocationProfile.PLAN_DRAFT_V1:
            raise ValueError('PLAN_DRAFT_GATEWAY_PROFILE_REQUIRED')
        self._adapter,self._tenant,self._current=adapter,_uuid(tenant_id),current_config

def configured(path):
    config=private_json(path)
    if not isinstance(config,dict) or set(config)!=CONFIG_KEYS|{'job_type','node_kind'} or config['schema_version']!='1.0.0' or config['execution_kind']!='SYSTEM_JOB' or config['job_type']!='PLAN_DRAFT' or config['node_kind']!='N01':
        raise ValueError('PLAN_DRAFT_GATEWAY_CONFIG_INVALID')
    _uuid(config['tenant_id'])
    if type(config['maximum_seconds']) not in (int,float) or not 0<config['maximum_seconds']<=MAXIMUM_OPERATION_SECONDS:
        raise ValueError('PLAN_DRAFT_GATEWAY_TIME_BOUND')
    _,adapter,current=configured_components(path,config,InvocationProfile.PLAN_DRAFT_V1)
    return PlanDraftGatewayAdapter(adapter,config['tenant_id'],current),config

async def run(path,raw):
    request(raw)
    adapter,config=configured(path)
    async with asyncio.timeout(config['maximum_seconds']):
        return await adapter.execute(raw)
