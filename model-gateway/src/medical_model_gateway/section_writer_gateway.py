"""Deployment-selected TASK/N04 adapter; recovery only records original held076 bytes."""
import asyncio
from .task_gateway import TaskGatewayAdapter, CONFIG_KEYS, MAXIMUM_OPERATION_SECONDS, request
from .formal_invocation_adapter import InvocationProfile, _uuid
from .formal_verification_worker import configured_components, private_json


class SectionWriterGatewayAdapter(TaskGatewayAdapter):
    def __init__(self, adapter, tenant_id, current_config):
        if adapter._profile is not InvocationProfile.SECTION_WRITER_N04_V1:
            raise ValueError('N04_GATEWAY_PROFILE_REQUIRED')
        self._adapter, self._tenant, self._current = adapter, _uuid(tenant_id), current_config


def configured(path):
    config = private_json(path)
    if not isinstance(config, dict) or set(config) != CONFIG_KEYS | {'node_kind','model_tool_mode'} or config['schema_version'] != '1.0.0' or config['execution_kind'] != 'TASK' or config['node_kind'] != 'N04' or config['model_tool_mode'] != 'SECTION_WRITER_N04_V1':
        raise ValueError('N04_GATEWAY_CONFIG_INVALID')
    _uuid(config['tenant_id'])
    if type(config['maximum_seconds']) not in (int, float) or not 0 < config['maximum_seconds'] <= MAXIMUM_OPERATION_SECONDS:
        raise ValueError('N04_GATEWAY_TIME_BOUND')
    _, adapter, current = configured_components(path, config, InvocationProfile.SECTION_WRITER_N04_V1)
    return SectionWriterGatewayAdapter(adapter, config['tenant_id'], current), config


async def run(path, raw):
    request(raw)
    adapter, config = configured(path)
    async with asyncio.timeout(config['maximum_seconds']):
        return await adapter.execute(raw)
