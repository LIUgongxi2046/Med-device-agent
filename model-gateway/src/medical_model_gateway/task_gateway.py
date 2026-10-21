"""Bounded TASK process protocol, reusing original actual076 capture and receipt storage.

This module does not listen on a socket, reserve verification jobs, create credentials,
or accept provider bytes from its caller. Only begin-send returns provider request bytes.
"""
from __future__ import annotations

import asyncio
import re
from pathlib import Path

import rfc8785

from .formal_invocation_adapter import FormalInvocationAdapter, InvocationProfile, HeldReceipt, _parse, _uuid
from .formal_verification_worker import configured_components, private_json

MAXIMUM_STDIN_BYTES = 65536
MAXIMUM_STDOUT_BYTES = 4096
MAXIMUM_STDERR_BYTES = 128
MAXIMUM_ARGV_BYTES = 8192
MAXIMUM_OPERATION_SECONDS = 300
FAILURE_MESSAGE = b'TASK_GATEWAY_STOPPED_REVIEW_ORIGINAL_OPERATION\n'
CONFIG_KEYS = {
    'schema_version', 'execution_kind', 'tenant_id', 'origin', 'ca_file',
    'client_certificate_file', 'client_private_key_file', 'credential_bindings_file',
    'durable_directory', 'maximum_http_bytes', 'maximum_request_bytes',
    'maximum_response_bytes', 'maximum_seconds',
}
ACTION_KEYS = {
    'run_id', 'step_id', 'action_id', 'lease_generation', 'input_sha256',
    'arguments_sha256', 'kind', 'tool_version', 'scope', 'action', 'purpose',
}


def request(raw: bytes) -> dict:
    """Closed input grammar. Token is stdin-only; it is never included in an exception."""
    if not isinstance(raw, bytes) or not 0 < len(raw) <= MAXIMUM_STDIN_BYTES:
        raise ValueError('TASK_GATEWAY_INPUT_BOUND')
    value = _parse(raw)
    if not isinstance(value, dict) or value.get('schema_version') != '1.0.0':
        raise ValueError('TASK_GATEWAY_INPUT_INVALID')
    mode = value.get('mode')
    expected = {'schema_version', 'mode', 'invocation_id'}
    if mode == 'DISPATCH':
        expected |= {'worker_action', 'run_token'}
    elif mode == 'RECOVER':
        expected |= {'dispatch_attempt_id'}
    else:
        raise ValueError('TASK_GATEWAY_MODE_INVALID')
    if set(value) != expected:
        raise ValueError('TASK_GATEWAY_INPUT_INVALID')
    _uuid(value['invocation_id'])
    if mode == 'RECOVER':
        _uuid(value['dispatch_attempt_id'])
        return value
    token = value['run_token']
    if not isinstance(token, str) or not 1 <= len(token) <= 16384 or not re.fullmatch(r'[\x21-\x7e]+', token):
        raise ValueError('TASK_GATEWAY_TOKEN_INVALID')
    action = value['worker_action']
    if not isinstance(action, dict) or set(action) != ACTION_KEYS:
        raise ValueError('TASK_GATEWAY_ACTION_INVALID')
    for key in ('run_id', 'step_id', 'action_id', 'tool_version'):
        _uuid(action[key])
    for key in ('input_sha256', 'arguments_sha256'):
        if not isinstance(action[key], str) or not re.fullmatch('[0-9a-f]{64}', action[key]):
            raise ValueError('TASK_GATEWAY_ACTION_INVALID')
    generation = action['lease_generation']
    if not isinstance(generation, str) or not re.fullmatch('[1-9][0-9]{0,19}', generation) or int(generation) > 2**64-1:
        raise ValueError('TASK_GATEWAY_ACTION_INVALID')
    purpose = action['purpose']
    if (action['kind'] != 'MODEL_CALL' or action['action'] != 'model.send'
            or not isinstance(purpose, str) or not re.fullmatch('[A-Z][A-Z0-9_]{0,63}', purpose)
            or purpose in {'MODEL_VERIFICATION', 'VERIFICATION', 'MODEL_EVALUATION', 'EVALUATION'}):
        raise ValueError('TASK_GATEWAY_ACTION_PURPOSE_INVALID')
    scope = action['scope']
    if not isinstance(scope, dict) or set(scope) != {'tenant_id', 'project_id', 'resource_kind', 'resource_id'} or scope['resource_kind'] != 'CONFIGURATION':
        raise ValueError('TASK_GATEWAY_ACTION_SCOPE_INVALID')
    for key in ('tenant_id', 'project_id', 'resource_id'):
        _uuid(scope[key])
    return value


class TaskGatewayAdapter:
    """One process operation; a recovery can only record held originals, never dispatch."""
    def __init__(self, adapter: FormalInvocationAdapter, tenant_id: str, current_config):
        if adapter._profile is not InvocationProfile.TASK_V1:
            raise ValueError('TASK_GATEWAY_PROFILE_REQUIRED')
        self._adapter, self._tenant, self._current = adapter, _uuid(tenant_id), current_config

    async def execute(self, raw: bytes) -> bytes:
        value = request(raw)
        invocation, mode = value['invocation_id'], value['mode']
        self._current()
        if mode == 'DISPATCH':
            if value['worker_action']['scope']['tenant_id'] != self._tenant:
                raise ValueError('TASK_GATEWAY_TENANT_MISMATCH')
            held = await self._adapter.dispatch(invocation, value['worker_action'], value['run_token'])
            if held is None:
                return self._output(mode, invocation, None, False)
            original = _parse(held.body)
            attempt = _uuid(original['provider_capture']['metadata']['dispatch_attempt_id'])
        else:
            attempt = value['dispatch_attempt_id']
            # read() verifies the immutable original manifest, hashes, actual076 marker,
            # capture and result. No caller path or substitute receipt bytes are accepted.
            body = self._adapter._receipts.read(invocation, attempt)
            original, held = _parse(body), HeldReceipt(invocation, body)
        if original['tenant_id'] != self._tenant:
            raise ValueError('TASK_GATEWAY_TENANT_MISMATCH')
        self._current()
        await self._adapter.record(held)
        return self._output(mode, invocation, attempt, True)

    @staticmethod
    def _output(mode, invocation, attempt, recorded):
        raw = rfc8785.dumps({
            'schema_version': '1.0.0', 'mode': mode, 'invocation_id': invocation,
            'dispatch_attempt_id': attempt, 'recorded': recorded,
        }) + b'\n'
        if len(raw) > MAXIMUM_STDOUT_BYTES:
            raise ValueError('TASK_GATEWAY_OUTPUT_BOUND')
        return raw


def configured(path: Path):
    config = private_json(path)
    if not isinstance(config, dict) or set(config) != CONFIG_KEYS or config['schema_version'] != '1.0.0' or config['execution_kind'] != 'TASK':
        raise ValueError('TASK_GATEWAY_CONFIG_INVALID')
    _uuid(config['tenant_id'])
    if type(config['maximum_seconds']) not in (int, float) or not 0 < config['maximum_seconds'] <= MAXIMUM_OPERATION_SECONDS:
        raise ValueError('TASK_GATEWAY_TIME_BOUND')
    _, adapter, current = configured_components(path, config, InvocationProfile.TASK_V1)
    return TaskGatewayAdapter(adapter, config['tenant_id'], current), config


async def run(path: Path, raw: bytes) -> bytes:
    # Reject hostile stdin before opening a deployment file or initializing mTLS.
    request(raw)
    adapter, config = configured(path)
    async with asyncio.timeout(config['maximum_seconds']):
        return await adapter.execute(raw)
