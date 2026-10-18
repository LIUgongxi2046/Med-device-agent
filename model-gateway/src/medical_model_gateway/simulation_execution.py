"""Local SIMULATION execution component; not a C07 service or authorization gate.

Only the explicit SimulationProvider can execute here. A successful terminal item
requires immutable local bytes, a provenance sidecar, and a verified reread.
The local commit is not an artifact-registry, budget, release or workflow receipt.
"""

import asyncio
from collections.abc import AsyncIterator
from contextlib import contextmanager
from dataclasses import dataclass
import fcntl
import hashlib
from importlib.resources import files
import json
import os
from pathlib import Path
import stat
from typing import Literal
import uuid

from jsonschema import Draft202012Validator, FormatChecker
from referencing import Registry
import rfc8785

from medical_contracts.domain import DomainModel, Hash, WireId
from medical_contracts.model_stream import (
    BlockEnd, BlockStart, ModelResult, ModelStreamBindings, RequestParameters,
    StreamPayload, TextBlock, TextDelta, ToolDelta, UsageEvent,
)

from .protocol import ExactInput, ProviderSettings
from .simulation import SimulationCase, SimulationCompletion, SimulationProvider, content_hash


class SimulationExecutionError(ValueError):
    """Stable component errors deliberately omit input and stored content."""


class SimulationIdentity(DomainModel):
    tenant_id: WireId
    project_id: WireId | None
    run_id: WireId
    step_id: WireId
    action_id: WireId
    invocation_id: WireId
    context_manifest_id: WireId
    model_config_revision: WireId


class SimulationProvenance(SimulationIdentity):
    schema_version: Literal['1.0.0']
    input_data_kind: Literal['SIMULATION']
    execution_scope: Literal['LOCAL_COMPONENT']
    case_id: WireId
    receipt_id: WireId
    request_sha256: Hash
    case_sha256: Hash
    settings_sha256: Hash
    input_sha256: Hash
    parameters_sha256: Hash
    model_result_schema_ref: str
    model_result_schema_sha256: Hash
    model_result_sha256: Hash
    external_request_sent: Literal[False]
    provider_charge_amount: Literal['0.00000000']
    charge_basis: Literal['LOCAL_SIMULATION_NO_PROVIDER_REQUEST']
    usage_origin: Literal['UNKNOWN', 'SIMULATION_FIXTURE']
    production_eligible: Literal[False]


@dataclass(frozen=True, repr=False)
class SimulationExecutionFinished:
    """Verified local completion, deliberately not a backend FinishEvent."""

    result_bytes: bytes
    provenance_bytes: bytes
    replayed: bool
    input_data_kind: Literal['SIMULATION'] = 'SIMULATION'

    def read_result(self) -> ModelResult:
        return ModelResult.model_validate(_read_json(self.result_bytes))

    def read_provenance(self) -> SimulationProvenance:
        return SimulationProvenance.model_validate(_read_json(self.provenance_bytes))


def _hash(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def _read_json(data: bytes):
    def pairs(items):
        value = {}
        for key, item in items:
            if key in value:
                raise SimulationExecutionError('SIMULATION_CONTENT_INVALID')
            value[key] = item
        return value

    def constant(_):
        raise SimulationExecutionError('SIMULATION_CONTENT_INVALID')

    try:
        return json.loads(data.decode('utf-8', errors='strict'), object_pairs_hook=pairs, parse_constant=constant)
    except (UnicodeError, json.JSONDecodeError) as error:
        raise SimulationExecutionError('SIMULATION_CONTENT_INVALID') from error


def _private(fd: int, directory: bool = False):
    value = os.fstat(fd)
    if ((not stat.S_ISDIR(value.st_mode) if directory else not stat.S_ISREG(value.st_mode))
            or value.st_uid != os.getuid() or stat.S_IMODE(value.st_mode) & 0o077
            or not directory and value.st_nlink != 1):
        raise SimulationExecutionError('SIMULATION_STORAGE_NOT_PRIVATE')


class _RecordDirectory:
    def __init__(self, descriptor: int, maximum_bytes: int, *, readonly: bool = False):
        self.descriptor = descriptor
        self.maximum_bytes = maximum_bytes
        self.readonly = readonly

    def read(self, name: str) -> bytes | None:
        try:
            descriptor = os.open(name, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK, dir_fd=self.descriptor)
        except FileNotFoundError:
            return None
        with os.fdopen(descriptor, 'rb') as source:
            _private(source.fileno())
            data = source.read(self.maximum_bytes + 1)
            if not data or len(data) > self.maximum_bytes:
                raise SimulationExecutionError('SIMULATION_STORAGE_SIZE_INVALID')
            return data

    def install(self, name: str, data: bytes):
        if self.readonly:
            raise SimulationExecutionError('SIMULATION_RECONCILIATION_READ_ONLY')
        if not data or len(data) > self.maximum_bytes:
            raise SimulationExecutionError('SIMULATION_STORAGE_SIZE_INVALID')
        temporary = '.writing-' + str(uuid.uuid4())
        descriptor = os.open(temporary, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW,
                             0o600, dir_fd=self.descriptor)
        try:
            with os.fdopen(descriptor, 'wb') as target:
                target.write(data)
                target.flush()
                os.fsync(target.fileno())
            # Link is atomic and refuses replacement of an existing immutable item.
            os.link(temporary, name, src_dir_fd=self.descriptor, dst_dir_fd=self.descriptor,
                    follow_symlinks=False)
            os.fsync(self.descriptor)
        finally:
            os.unlink(temporary, dir_fd=self.descriptor)
            os.fsync(self.descriptor)


class SimulationFileStore:
    """Explicit private local test/component store; no implicit runtime directory."""

    def __init__(self, directory: Path, *, maximum_record_bytes: int):
        if type(maximum_record_bytes) is not int or maximum_record_bytes < 1:
            raise ValueError('SIMULATION_STORAGE_LIMIT_REQUIRED')
        self.directory = Path(directory)
        self.maximum_record_bytes = maximum_record_bytes

    @contextmanager
    def existing(self, identity: SimulationIdentity):
        """Lock and read an existing journal; never create directories or lock files."""
        key = _hash(rfc8785.dumps({name: getattr(identity, name) for name in
                                  ('tenant_id', 'run_id', 'step_id', 'action_id')}))
        root = child = lock = None
        try:
            root = os.open(self.directory, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW)
            _private(root, directory=True)
            child = os.open(key, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW, dir_fd=root)
            _private(child, directory=True)
            lock = os.open('.lock', os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK, dir_fd=child)
            _private(lock)
            try:
                fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
            except BlockingIOError as error:
                raise SimulationExecutionError('SIMULATION_EXECUTION_BUSY') from error
            yield _RecordDirectory(child, self.maximum_record_bytes, readonly=True)
        except FileNotFoundError as error:
            raise SimulationExecutionError('SIMULATION_RECONCILIATION_REQUIRED') from error
        finally:
            for descriptor in (lock, child, root):
                if descriptor is not None:
                    os.close(descriptor)

    @contextmanager
    def claim(self, identity: SimulationIdentity):
        key = _hash(rfc8785.dumps({key: getattr(identity, key) for key in
                                  ('tenant_id', 'run_id', 'step_id', 'action_id')}))
        root = os.open(self.directory, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW)
        child = lock = None
        try:
            _private(root, directory=True)
            try:
                os.mkdir(key, mode=0o700, dir_fd=root)
                os.fsync(root)
            except FileExistsError:
                pass
            child = os.open(key, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW, dir_fd=root)
            _private(child, directory=True)
            lock = os.open('.lock', os.O_WRONLY | os.O_CREAT | os.O_NOFOLLOW | os.O_NONBLOCK, 0o600, dir_fd=child)
            _private(lock)
            try:
                fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
            except BlockingIOError as error:
                raise SimulationExecutionError('SIMULATION_EXECUTION_BUSY') from error
            yield _RecordDirectory(child, self.maximum_record_bytes)
        finally:
            if lock is not None:
                os.close(lock)
            if child is not None:
                os.close(child)
            os.close(root)


class SimulationExecutor:
    def __init__(self, store: SimulationFileStore, model_bindings: ModelStreamBindings):
        self.store = store
        self.models = model_bindings
        definition = files('medical_model_gateway').joinpath('simulation-execution.schema.json').read_bytes()
        self.provenance_schema_sha256 = _hash(definition)
        schema = _read_json(definition)
        Draft202012Validator.check_schema(schema)
        self.provenance_validator = Draft202012Validator(schema, format_checker=FormatChecker(), registry=Registry())

    def reconcile(self, identity: SimulationIdentity, case: SimulationCase, settings: ProviderSettings,
                  parameters: RequestParameters, exact_input: ExactInput) -> SimulationExecutionFinished:
        """Read verified committed local facts only; grants no dispatch or settlement authority."""
        identity = SimulationIdentity.model_validate(identity.model_dump(mode='python'))
        case = SimulationCase.model_validate(case.model_dump(mode='python'))
        settings = ProviderSettings.model_validate(settings.model_dump(mode='python'))
        parameters = RequestParameters.model_validate(parameters.model_dump(mode='python'))
        exact_input = ExactInput.model_validate(exact_input.model_dump(mode='python'))
        self._validate(case, settings, parameters, exact_input)
        request = rfc8785.dumps({'identity': identity.model_dump(mode='json'), 'case': case.model_dump(mode='json'),
            'settings': settings.model_dump(mode='json'), 'parameters': parameters.model_dump(mode='json'),
            'exact_input': exact_input.model_dump(mode='json'), 'provenance_schema_sha256': self.provenance_schema_sha256,
            'model_result_schema_sha256': self.models.catalogue.digests['model-stream']})
        with self.store.existing(identity) as directory:
            if directory.read('commit.json') is None:
                raise SimulationExecutionError('SIMULATION_RECONCILIATION_REQUIRED')
            return self._reread(directory, request, identity, case, self._result(identity, case), replayed=True)

    async def stream(self, identity: SimulationIdentity, case: SimulationCase, settings: ProviderSettings,
                     parameters: RequestParameters, exact_input: ExactInput
                     ) -> AsyncIterator[StreamPayload | SimulationExecutionFinished]:
        finished = None
        execution = self._stream_locked(identity, case, settings, parameters, exact_input)
        try:
            async for value in execution:
                if isinstance(value, SimulationExecutionFinished):
                    finished = value
                else:
                    yield value
        finally:
            await execution.aclose()
        # The caller may stop at this terminal item without asking for another
        # item or explicitly closing the iterator. All storage locks are released.
        if finished is None:
            raise SimulationExecutionError('SIMULATION_PROVIDER_RESULT_MISSING')
        yield finished

    async def _stream_locked(self, identity: SimulationIdentity, case: SimulationCase, settings: ProviderSettings,
                             parameters: RequestParameters, exact_input: ExactInput
                             ) -> AsyncIterator[StreamPayload | SimulationExecutionFinished]:
        # Clone nested containers: caller mutation after admission cannot change this execution.
        identity = SimulationIdentity.model_validate(identity.model_dump(mode='python'))
        case = SimulationCase.model_validate(case.model_dump(mode='python'))
        settings = ProviderSettings.model_validate(settings.model_dump(mode='python'))
        parameters = RequestParameters.model_validate(parameters.model_dump(mode='python'))
        exact_input = ExactInput.model_validate(exact_input.model_dump(mode='python'))
        self._validate(case, settings, parameters, exact_input)
        request = rfc8785.dumps({'identity': identity.model_dump(mode='json'), 'case': case.model_dump(mode='json'),
            'settings': settings.model_dump(mode='json'), 'parameters': parameters.model_dump(mode='json'),
            'exact_input': exact_input.model_dump(mode='json'), 'provenance_schema_sha256': self.provenance_schema_sha256,
            'model_result_schema_sha256': self.models.catalogue.digests['model-stream']})
        expected = self._result(identity, case)
        events = list(self._events(expected))
        for event in events:
            self.models.bind(event.model_dump(mode='json'), type(event).__name__)
        with self.store.claim(identity) as directory:
            original = directory.read('request.json')
            if original is not None and original != request:
                raise SimulationExecutionError('SIMULATION_IDEMPOTENCY_CONFLICT')
            if original is None:
                if any(directory.read(name) is not None for name in ('started.json', 'result.json', 'provenance.json', 'commit.json')):
                    raise SimulationExecutionError('SIMULATION_CONTENT_MISMATCH')
                directory.install('request.json', request)
            if directory.read('commit.json') is not None:
                saved = self._reread(directory, request, identity, case, expected, replayed=True)
                for event in events:
                    yield event
                yield saved
                return
            if directory.read('started.json') is not None:
                raise SimulationExecutionError('SIMULATION_RECONCILIATION_REQUIRED')
            if directory.read('result.json') is not None or directory.read('provenance.json') is not None:
                raise SimulationExecutionError('SIMULATION_CONTENT_MISMATCH')
            directory.install('started.json', rfc8785.dumps({'request_sha256': _hash(request)}))
            completed = None
            index = 0
            deadline = asyncio.get_running_loop().time() + settings.deadline_seconds
            provider = SimulationProvider(case).stream(settings, parameters, exact_input, case.receipt_id)
            try:
                while True:
                    # A timeout must never remain active while yielding to caller code.
                    try:
                        async with asyncio.timeout(self._remaining(deadline)):
                            value = await anext(provider)
                    except StopAsyncIteration:
                        break
                    self._remaining(deadline)
                    if isinstance(value, SimulationCompletion):
                        if (completed is not None or index != len(events) or value.case_id != case.case_id
                                or value.input_data_kind != 'SIMULATION' or value.response_model != settings.model_version
                                or value.provider_request_id != expected.provider_request_id
                                or value.blocks != expected.blocks or value.usage != expected.usage or value.reason != expected.reason):
                            raise SimulationExecutionError('SIMULATION_PROVIDER_RESULT_MISMATCH')
                        completed = value
                    else:
                        if completed is not None or index >= len(events) or value != events[index]:
                            raise SimulationExecutionError('SIMULATION_PROVIDER_STREAM_MISMATCH')
                        index += 1
                        yield value
                if completed is None:
                    raise SimulationExecutionError('SIMULATION_PROVIDER_RESULT_MISSING')
                self._remaining(deadline)
                result = self.models.bind(expected.model_dump(mode='json'), 'ModelResult').canonical_bytes
                provenance = self._provenance(identity, case, request, result)
                directory.install('result.json', result)
                directory.install('provenance.json', provenance)
                self._remaining(deadline)
                directory.install('commit.json', rfc8785.dumps({'request_sha256': _hash(request),
                    'result_sha256': _hash(result), 'provenance_sha256': _hash(provenance)}))
                # No success is returned on a partial write, missing sidecar or failed reread.
                self._remaining(deadline)
                finished = self._reread(directory, request, identity, case, expected, replayed=False)
                self._remaining(deadline)
                yield finished
            finally:
                await provider.aclose()

    @staticmethod
    def _remaining(deadline: float) -> float:
        remaining = deadline - asyncio.get_running_loop().time()
        if remaining <= 0:
            raise TimeoutError('SIMULATION_DEADLINE_EXCEEDED')
        return remaining

    def _validate(self, case, settings, parameters, exact_input):
        if not settings.model_id.startswith('SIMULATION:') or not settings.model_version.startswith('SIMULATION:'):
            raise SimulationExecutionError('SIMULATION_MODEL_REQUIRED')
        settings.request_options(parameters, exact_input)
        if (case.settings_sha256 != content_hash(settings) or case.input_sha256 != content_hash(exact_input)
                or case.parameters_sha256 != content_hash(parameters)):
            raise SimulationExecutionError('SIMULATION_INPUT_MISMATCH')
        definitions = {tool.function.name: tool.function.parameters for tool in exact_input.tools}
        for block in case.blocks:
            if not isinstance(block, TextBlock):
                if block.name not in definitions:
                    raise SimulationExecutionError('SIMULATION_TOOL_NOT_DECLARED')
                arguments = _read_json(block.arguments.encode('utf-8'))
                validator = Draft202012Validator(definitions[block.name], format_checker=FormatChecker(), registry=Registry())
                if not isinstance(arguments, dict) or not validator.is_valid(arguments):
                    raise SimulationExecutionError('SIMULATION_TOOL_ARGUMENTS_INVALID')

    @staticmethod
    def _result(identity, case):
        return ModelResult(schema_version='1.0.0', invocation_id=identity.invocation_id,
            context_manifest_id=identity.context_manifest_id, model_config_revision=identity.model_config_revision,
            provider_request_id='SIMULATION:' + case.case_id, blocks=case.blocks, usage=case.usage,
            reason=case.reason, replay_state_id=None)

    @staticmethod
    def _events(result):
        for index, block in enumerate(result.blocks):
            if isinstance(block, TextBlock):
                yield BlockStart(type='block_start', index=index, block_type='text')
                yield TextDelta(type='text_delta', index=index, text=block.text)
            else:
                yield BlockStart(type='block_start', index=index, block_type='tool-call')
                yield ToolDelta(type='tool_call_delta', index=index, call_id=block.id, name=block.name, arguments_delta=block.arguments)
            yield BlockEnd(type='block_end', index=index, block=block)
        if result.usage is not None:
            yield UsageEvent(type='usage', usage=result.usage)

    def _provenance(self, identity, case, request, result):
        value = SimulationProvenance(**identity.model_dump(mode='python'), schema_version='1.0.0',
            input_data_kind='SIMULATION', execution_scope='LOCAL_COMPONENT', case_id=case.case_id, receipt_id=case.receipt_id,
            request_sha256=_hash(request), case_sha256=content_hash(case), settings_sha256=case.settings_sha256,
            input_sha256=case.input_sha256, parameters_sha256=case.parameters_sha256,
            model_result_schema_ref=self.models.catalogue.schema_ref('model-stream', 'ModelResult'),
            model_result_schema_sha256=self.models.catalogue.digests['model-stream'], model_result_sha256=_hash(result),
            external_request_sent=False, provider_charge_amount='0.00000000', charge_basis='LOCAL_SIMULATION_NO_PROVIDER_REQUEST',
            usage_origin='UNKNOWN' if case.usage is None else 'SIMULATION_FIXTURE', production_eligible=False)
        plain = value.model_dump(mode='json')
        self.provenance_validator.validate(plain)
        return rfc8785.dumps(plain)

    def _reread(self, directory, request, identity, case, expected, *, replayed):
        result = directory.read('result.json')
        provenance = directory.read('provenance.json')
        commit = directory.read('commit.json')
        if result is None or provenance is None or commit is None:
            raise SimulationExecutionError('SIMULATION_CONTENT_MISMATCH')
        if (directory.read('request.json') != request
                or directory.read('started.json') != rfc8785.dumps({'request_sha256': _hash(request)})
                or commit != rfc8785.dumps({'request_sha256': _hash(request), 'result_sha256': _hash(result),
                                          'provenance_sha256': _hash(provenance)})):
            raise SimulationExecutionError('SIMULATION_CONTENT_MISMATCH')
        bound = self.models.bind(_read_json(result), 'ModelResult')
        if (bound.canonical_bytes != result or bound.read() != expected
                or provenance != self._provenance(identity, case, request, result)):
            raise SimulationExecutionError('SIMULATION_CONTENT_MISMATCH')
        self.provenance_validator.validate(_read_json(provenance))
        return SimulationExecutionFinished(result, provenance, replayed)
