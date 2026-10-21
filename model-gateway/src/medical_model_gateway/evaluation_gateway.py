"""Independent SYSTEM_JOB/EVALUATION_VERIFICATION C07 adapter; never a TASK purpose conversion."""
import asyncio
import rfc8785
from .formal_invocation_adapter import InvocationProfile, HeldReceipt, _uuid, _parse
from .formal_verification_worker import configured_components, private_json
from .task_gateway import CONFIG_KEYS, MAXIMUM_OPERATION_SECONDS


def request(raw):
    if not isinstance(raw, bytes) or not 0 < len(raw) <= 32768:
        raise ValueError('EVALUATION_GATEWAY_INPUT_BOUND')
    value = _parse(raw)
    if not isinstance(value, dict) or value.get('schema_version') != '1.0.0':
        raise ValueError('EVALUATION_GATEWAY_INPUT_INVALID')
    mode = value.get('mode')
    extra = {'evaluation_action'} if mode == 'DISPATCH' else {'dispatch_attempt_id'} if mode == 'RECOVER' else None
    if extra is None or set(value) != {'schema_version', 'mode', 'invocation_id'} | extra:
        raise ValueError('EVALUATION_GATEWAY_INPUT_INVALID')
    _uuid(value['invocation_id'])
    if mode == 'RECOVER':
        _uuid(value['dispatch_attempt_id'])
    else:
        action = value['evaluation_action']
        if not isinstance(action, dict) or set(action) != {'reference', 'step', 'session_id', 'request_id'}:
            raise ValueError('EVALUATION_GATEWAY_ACTION_INVALID')
        for key in ('session_id', 'request_id'):
            _uuid(action[key])
        if not isinstance(action['reference'], dict) or action['reference'].get('execution_kind') != 'SYSTEM_JOB' or action['reference'].get('job_type') != 'EVALUATION_VERIFICATION':
            raise ValueError('EVALUATION_GATEWAY_JOB_INVALID')
        _uuid(action['reference']['tenant_id'])
    return value


class EvaluationGatewayAdapter:
    def __init__(self, adapter, tenant, current):
        if adapter._profile is not InvocationProfile.EVALUATION_VERIFICATION_V1:
            raise ValueError('EVALUATION_GATEWAY_PROFILE_REQUIRED')
        self._adapter, self._tenant, self._current = adapter, _uuid(tenant), current

    async def execute(self, raw):
        value = request(raw)
        self._current()
        invocation, mode = value['invocation_id'], value['mode']
        if mode == 'DISPATCH':
            if value['evaluation_action']['reference']['tenant_id'] != self._tenant:
                raise ValueError('EVALUATION_GATEWAY_TENANT_MISMATCH')
            # mTLS plus server-owned independent lease/session/observation admission.
            # A user-session token or generic TASK run token is deliberately not accepted.
            held = await self._adapter.dispatch(invocation, value['evaluation_action'], None)
            if held is None:
                return rfc8785.dumps({'schema_version':'1.0.0','mode':mode,'invocation_id':invocation,'dispatch_attempt_id':None,'recorded':False})
            attempt = _uuid(_parse(held.body)['provider_capture']['metadata']['dispatch_attempt_id'])
        else:
            attempt = value['dispatch_attempt_id']
            held = HeldReceipt(invocation, self._adapter._receipts.read(invocation, attempt))
        if _parse(held.body)['tenant_id'] != self._tenant:
            raise ValueError('EVALUATION_GATEWAY_TENANT_MISMATCH')
        self._current()
        receipt = await self._adapter.record(held)
        if receipt.get('invocation_id') != invocation or receipt.get('attempt_id') != attempt:
            raise ValueError('EVALUATION_GATEWAY_RECEIPT_MISMATCH')
        return rfc8785.dumps({'schema_version':'1.0.0','mode':mode,'invocation_id':invocation,'dispatch_attempt_id':attempt,'recorded':True})


def configured(path):
    config = private_json(path)
    if not isinstance(config, dict) or set(config) != CONFIG_KEYS | {'job_type','model_tool_mode'} or config['schema_version'] != '1.0.0' or config['execution_kind'] != 'SYSTEM_JOB' or config['job_type'] != 'EVALUATION_VERIFICATION' or config['model_tool_mode'] != 'EVALUATION_VERIFICATION_V1':
        raise ValueError('EVALUATION_GATEWAY_CONFIG_INVALID')
    _uuid(config['tenant_id'])
    if type(config['maximum_seconds']) not in (int, float) or not 0 < config['maximum_seconds'] <= MAXIMUM_OPERATION_SECONDS:
        raise ValueError('EVALUATION_GATEWAY_TIME_BOUND')
    _, adapter, current = configured_components(path, config, InvocationProfile.EVALUATION_VERIFICATION_V1)
    return EvaluationGatewayAdapter(adapter, config['tenant_id'], current), config


async def run(path, raw):
    request(raw)
    adapter, config = configured(path)
    async with asyncio.timeout(config['maximum_seconds']):
        return await adapter.execute(raw)
