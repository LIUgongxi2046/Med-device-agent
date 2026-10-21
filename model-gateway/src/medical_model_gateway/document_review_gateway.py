"""Deployment-selected SYSTEM_JOB/CHECK_DOCUMENT/N07 adapter; recovery only records original held076 bytes."""
import asyncio
from .task_gateway import TaskGatewayAdapter, CONFIG_KEYS, MAXIMUM_OPERATION_SECONDS, request
from .formal_invocation_adapter import InvocationProfile, _uuid
from .formal_verification_worker import configured_components, private_json


class DocumentReviewGatewayAdapter(TaskGatewayAdapter):
    def __init__(self, adapter, tenant_id, current_config):
        if adapter._profile is not InvocationProfile.DOCUMENT_REVIEW_N07_V1:
            raise ValueError('N07_GATEWAY_PROFILE_REQUIRED')
        self._adapter, self._tenant, self._current = adapter, _uuid(tenant_id), current_config


def configured(path):
    config = private_json(path)
    if not isinstance(config, dict) or set(config) != CONFIG_KEYS | {'job_type','node_kind','model_tool_mode'} or config['schema_version'] != '1.0.0' or config['execution_kind'] != 'SYSTEM_JOB' or config['job_type'] != 'CHECK_DOCUMENT' or config['node_kind'] != 'N07' or config['model_tool_mode'] != 'DOCUMENT_REVIEW_N07_V1':
        raise ValueError('N07_GATEWAY_CONFIG_INVALID')
    _uuid(config['tenant_id'])
    if type(config['maximum_seconds']) not in (int, float) or not 0 < config['maximum_seconds'] <= MAXIMUM_OPERATION_SECONDS:
        raise ValueError('N07_GATEWAY_TIME_BOUND')
    _, adapter, current = configured_components(path, config, InvocationProfile.DOCUMENT_REVIEW_N07_V1)
    return DocumentReviewGatewayAdapter(adapter, config['tenant_id'], current), config


async def run(path, raw):
    request(raw)
    adapter, config = configured(path)
    async with asyncio.timeout(config['maximum_seconds']):
        return await adapter.execute(raw)
