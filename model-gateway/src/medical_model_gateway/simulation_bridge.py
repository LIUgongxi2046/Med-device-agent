"""Private local pipe adapter. Authority is enforced by the Java dispatch owner.

This module is not a server or an authorization endpoint. A valid JSON document
does not grant permission to invoke it. The parent must consume a committed,
single-use DispatchPermit before starting this process. No retries are performed.
Only fixed SIMULATION cases are supported; no provider credentials or network.
"""
import argparse
import asyncio
import base64
import hashlib
from pathlib import Path
import sys

import rfc8785

from medical_contracts.catalogue import SchemaCatalogue
from medical_contracts.model_stream import ModelResult, ModelStreamBindings, RequestParameters
from .protocol import ExactInput, ProviderSettings
from .simulation import SimulationCase, content_hash
from .simulation_control import SimulationBinding, measure_input
from .simulation_execution import (
    SimulationExecutor, SimulationExecutionFinished, SimulationFileStore, _read_json,
)

MAX_INPUT_BYTES = 16_777_216


class SimulationBridgeError(ValueError):
    pass


async def execute_pipe(data: bytes, *, project_root: Path, store_directory: Path,
                       maximum_record_bytes: int, reconcile_only: bool = False) -> bytes:
    """Execute one already-authorized dispatch and return exact local fact bytes.

    No SQL status, reservation, artifact, evaluation score or production eligibility
    is changed here. Invalid requests are rejected before opening the local store.
    """
    if not data or len(data) > MAX_INPUT_BYTES:
        raise SimulationBridgeError('SIMULATION_BRIDGE_INPUT_INVALID')
    if type(maximum_record_bytes) is not int or not 1 <= maximum_record_bytes <= MAX_INPUT_BYTES:
        raise SimulationBridgeError('SIMULATION_BRIDGE_LIMIT_INVALID')
    catalogue = SchemaCatalogue(project_root)
    request = _read_json(data)
    catalogue.validate('simulation-execution', request, 'SimulationInvocationAdmission')
    binding = SimulationBinding.model_validate(request['binding'])
    exact_input = ExactInput.model_validate(request['exact_input'])
    parameters = RequestParameters.model_validate(request['request_parameters'])
    settings = ProviderSettings.model_validate(request['settings'])
    case = SimulationCase.model_validate(request['simulation_case'])
    if (binding.exact_input != measure_input(exact_input)
            or binding.case_id != case.case_id or binding.case_receipt_id != case.receipt_id
            or binding.case_sha256 != content_hash(case)
            or binding.settings_sha256 != content_hash(settings)
            or binding.parameters_sha256 != content_hash(parameters)):
        raise SimulationBridgeError('SIMULATION_BRIDGE_BINDING_MISMATCH')
    executor = SimulationExecutor(SimulationFileStore(store_directory,
        maximum_record_bytes=maximum_record_bytes), ModelStreamBindings(catalogue))
    local_request = rfc8785.dumps({
        'identity': binding.identity.model_dump(mode='json'), 'case': case.model_dump(mode='json'),
        'settings': settings.model_dump(mode='json'), 'parameters': parameters.model_dump(mode='json'),
        'exact_input': exact_input.model_dump(mode='json'),
        'provenance_schema_sha256': executor.provenance_schema_sha256,
        'model_result_schema_sha256': catalogue.digests['model-stream'],
    })
    if hashlib.sha256(local_request).hexdigest() != binding.local_request_sha256:
        raise SimulationBridgeError('SIMULATION_BRIDGE_BINDING_MISMATCH')
    expected = ModelResult(schema_version='1.0.0', invocation_id=binding.identity.invocation_id,
        context_manifest_id=binding.identity.context_manifest_id,
        model_config_revision=binding.identity.model_config_revision,
        provider_request_id='SIMULATION:' + case.case_id, blocks=case.blocks,
        usage=case.usage, reason=case.reason, replay_state_id=None)
    if len(rfc8785.dumps(expected.model_dump(mode='json'))) > binding.caps.maximum_output_units:
        raise SimulationBridgeError('SIMULATION_BRIDGE_OUTPUT_LIMIT')
    if reconcile_only:
        finished = executor.reconcile(binding.identity, case, settings, parameters, exact_input)
    else:
        finished = None
        async for item in executor.stream(binding.identity, case, settings, parameters, exact_input):
            if isinstance(item, SimulationExecutionFinished):
                finished = item
    if finished is None:
        raise SimulationBridgeError('SIMULATION_BRIDGE_RESULT_MISSING')
    return rfc8785.dumps({
        'schema_version': '1.0.0',
        'result_base64': base64.b64encode(finished.result_bytes).decode('ascii'),
        'provenance_base64': base64.b64encode(finished.provenance_bytes).decode('ascii'),
        'replayed': finished.replayed,
    })


def main() -> int:
    parser = argparse.ArgumentParser(description='Private local SIMULATION pipe adapter')
    parser.add_argument('--project-root', type=Path, required=True)
    parser.add_argument('--store-directory', type=Path, required=True)
    parser.add_argument('--maximum-record-bytes', type=int, required=True)
    parser.add_argument('--reconcile-only', action='store_true')
    args = parser.parse_args()
    try:
        result = asyncio.run(execute_pipe(sys.stdin.buffer.read(MAX_INPUT_BYTES + 1),
            project_root=args.project_root, store_directory=args.store_directory,
            maximum_record_bytes=args.maximum_record_bytes, reconcile_only=args.reconcile_only))
        sys.stdout.buffer.write(result)
        sys.stdout.buffer.flush()
        return 0
    except Exception:
        # Never log request content, credentials, paths, or arbitrary exception text.
        sys.stderr.write('SIMULATION_BRIDGE_FAILED\n')
        return 2


if __name__ == '__main__':
    raise SystemExit(main())
