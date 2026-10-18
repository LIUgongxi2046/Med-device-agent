import asyncio
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import time
import unittest
from unittest.mock import patch
import uuid

from jsonschema import ValidationError
import rfc8785

from medical_contracts.catalogue import SchemaCatalogue
from medical_contracts.model_stream import ModelStreamBindings, RequestParameters
from medical_model_gateway.protocol import ExactInput, ProviderSettings
from medical_model_gateway.simulation import SimulationCase, SimulationProvider, content_hash
from medical_model_gateway.simulation_execution import (
    SimulationExecutionError, SimulationExecutionFinished, SimulationExecutor,
    SimulationFileStore, SimulationIdentity, _RecordDirectory,
)


ROOT = Path(__file__).resolve().parents[1]


class SimulationExecutionTests(unittest.IsolatedAsyncioTestCase):
    @classmethod
    def setUpClass(cls):
        cls.bindings = ModelStreamBindings(SchemaCatalogue(ROOT))

    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix='medical-simulation-')
        self.addCleanup(self.temporary.cleanup)
        self.directory = Path(self.temporary.name)
        self.store = SimulationFileStore(self.directory, maximum_record_bytes=1_048_576)
        self.executor = SimulationExecutor(self.store, self.bindings)
        self.identity = SimulationIdentity(**{field: str(uuid.uuid4()) for field in SimulationIdentity.model_fields})
        self.settings = ProviderSettings(model_id='SIMULATION:medical-agent', model_version='SIMULATION:1.0.0',
            max_output_tokens=2048, deadline_seconds=30, temperature_enabled=False, reasoning_efforts=['none'], stop_limit=0)
        self.parameters = RequestParameters(max_output_tokens=1024, temperature=None, reasoning_effort='none', stop=[], purpose='EVALUATION')
        self.exact_input = ExactInput(messages=[{'role': 'user', 'content': '【模拟数据】按固定输入生成测试结果。'}], tools=[])
        self.receipt = str(uuid.uuid4())
        self.case = self.make_case()

    def make_case(self, blocks=None, kind='stop', usage=None):
        return SimulationCase(schema_version='1.0.0', input_data_kind='SIMULATION', case_id=str(uuid.uuid4()),
            receipt_id=self.receipt, settings_sha256=content_hash(self.settings), input_sha256=content_hash(self.exact_input),
            parameters_sha256=content_hash(self.parameters),
            blocks=blocks or [{'type': 'text', 'text': '【模拟数据】型号 SIM-A；量程 0.001—99.999。'}],
            reason={'kind': kind, 'failure': None}, usage=usage)

    def stream(self, **changes):
        values = dict(identity=self.identity, case=self.case, settings=self.settings,
                      parameters=self.parameters, exact_input=self.exact_input)
        values.update(changes)
        return self.executor.stream(**values)

    async def execute(self, **changes):
        return [item async for item in self.stream(**changes)]

    def saved_directory(self):
        directories = list(self.directory.iterdir())
        self.assertEqual(len(directories), 1)
        return directories[0]

    async def test_actual_provider_saves_exact_bytes_and_mandatory_provenance_before_terminal(self):
        # These are denial assertions, not replacement model responses.
        with (patch('medical_model_gateway.credentials.read_key', side_effect=AssertionError('credential access')),
                patch('medical_model_gateway.provider.DeepSeekProvider.client', side_effect=AssertionError('external provider')),
                patch('socket.create_connection', side_effect=AssertionError('network'))):
            values = await self.execute()
        self.assertEqual([value.type for value in values[:-1]], ['block_start', 'text_delta', 'block_end'])
        finished = values[-1]
        self.assertIsInstance(finished, SimulationExecutionFinished)
        self.assertFalse(finished.replayed)
        saved = self.saved_directory()
        self.assertEqual(finished.result_bytes, (saved / 'result.json').read_bytes())
        self.assertEqual(finished.provenance_bytes, (saved / 'provenance.json').read_bytes())
        result = finished.read_result()
        proof = finished.read_provenance()
        self.assertEqual(result.blocks, self.case.blocks)
        self.assertEqual(result.provider_request_id, 'SIMULATION:' + self.case.case_id)
        self.assertEqual(proof.model_result_sha256, hashlib.sha256(finished.result_bytes).hexdigest())
        self.assertEqual(proof.settings_sha256, content_hash(self.settings))
        self.assertEqual(proof.input_sha256, content_hash(self.exact_input))
        self.assertEqual(proof.parameters_sha256, content_hash(self.parameters))
        self.assertEqual(proof.case_sha256, content_hash(self.case))
        self.assertEqual(proof.usage_origin, 'UNKNOWN')
        self.assertIsNone(result.usage)
        self.assertFalse(proof.external_request_sent)
        self.assertFalse(proof.production_eligible)
        self.assertEqual(proof.provider_charge_amount, '0.00000000')
        self.assertEqual(proof.execution_scope, 'LOCAL_COMPONENT')
        self.executor.provenance_validator.validate(json.loads(finished.provenance_bytes))
        self.assertEqual(self.bindings.bind(json.loads(finished.result_bytes), 'ModelResult').canonical_bytes, finished.result_bytes)
        self.assertNotIn('input_data_kind', json.loads(finished.result_bytes))

    async def test_new_executor_reuses_original_result_without_invoking_provider(self):
        original = await self.execute()
        snapshot = {item.name: item.read_bytes() for item in self.saved_directory().iterdir()}
        self.executor = SimulationExecutor(self.store, self.bindings)
        with patch.object(SimulationProvider, 'stream', side_effect=AssertionError('duplicate execution')):
            repeated = await self.execute()
        self.assertEqual(repeated[:-1], original[:-1])
        self.assertTrue(repeated[-1].replayed)
        self.assertEqual(repeated[-1].result_bytes, original[-1].result_bytes)
        self.assertEqual(repeated[-1].provenance_bytes, original[-1].provenance_bytes)
        self.assertEqual(snapshot, {item.name: item.read_bytes() for item in self.saved_directory().iterdir()})

    async def test_stopping_at_finished_releases_lock_for_fresh_and_replayed_streams(self):
        held_iterators = []
        try:
            for replayed in (False, True):
                iterator = self.stream()
                held_iterators.append(iterator)
                async for value in iterator:
                    if isinstance(value, SimulationExecutionFinished):
                        self.assertEqual(value.replayed, replayed)
                        break
                # Keep iterator referenced and suspended on its last yielded item.
                with patch.object(SimulationProvider, 'stream', side_effect=AssertionError('duplicate execution')):
                    self.assertTrue((await self.execute())[-1].replayed)
        finally:
            for iterator in held_iterators:
                await iterator.aclose()

    async def test_same_action_rejects_changed_case_input_parameters_config_or_identity(self):
        await self.execute()
        changed_input = ExactInput(messages=[{'role': 'user', 'content': 'changed'}], tools=[])
        changed_parameters = self.parameters.model_copy(update={'max_output_tokens': 1000})
        changed_settings = self.settings.model_copy(update={'max_output_tokens': 2000})
        variants = [dict(case=self.make_case()),
            dict(case=self.case.model_copy(update={'blocks': self.make_case([{'type': 'text', 'text': 'changed'}]).blocks})),
            dict(exact_input=changed_input, case=self.case.model_copy(update={'input_sha256': content_hash(changed_input)})),
            dict(parameters=changed_parameters, case=self.case.model_copy(update={'parameters_sha256': content_hash(changed_parameters)})),
            dict(settings=changed_settings, case=self.case.model_copy(update={'settings_sha256': content_hash(changed_settings)}))]
        variants.extend(dict(identity=self.identity.model_copy(update={name: str(uuid.uuid4())}))
                        for name in ('project_id', 'invocation_id', 'context_manifest_id', 'model_config_revision'))
        with patch.object(SimulationProvider, 'stream', side_effect=AssertionError('duplicate execution')):
            for variant in variants:
                with self.subTest(variant=list(variant)):
                    with self.assertRaisesRegex(SimulationExecutionError, 'SIMULATION_IDEMPOTENCY_CONFLICT'):
                        await self.execute(**variant)

    async def test_non_simulation_and_incorrect_hash_are_rejected_before_storage(self):
        with self.assertRaisesRegex(SimulationExecutionError, 'SIMULATION_MODEL_REQUIRED'):
            await self.execute(settings=self.settings.model_copy(update={'model_id': 'external-model'}))
        with self.assertRaisesRegex(SimulationExecutionError, 'SIMULATION_INPUT_MISMATCH'):
            await self.execute(case=self.case.model_copy(update={'input_sha256': '0' * 64}))
        self.assertEqual(list(self.directory.iterdir()), [])

    async def test_real_tool_arguments_validate_and_fixture_usage_stays_explicit(self):
        self.exact_input = ExactInput(messages=self.exact_input.messages, tools=[{'type': 'function', 'function': {
            'name': 'read_artifact', 'description': '读取模拟制品', 'parameters': {'type': 'object', 'additionalProperties': False,
                'required': ['artifact_id'], 'properties': {'artifact_id': {'type': 'string', 'format': 'uuid'}}}}}])
        arguments = json.dumps({'artifact_id': str(uuid.uuid4())})
        usage = dict(uncached_input_tokens=20, cache_read_tokens=0, cache_write_tokens=0, output_tokens=10,
                     total_tokens=30, reasoning_tokens=0, source_receipt_id=self.receipt)
        self.case = self.make_case([{'type': 'tool-call', 'id': 'simulation-call', 'name': 'read_artifact', 'arguments': arguments}],
                                   'tool-calls', usage)
        values = await self.execute()
        self.assertEqual(values[-1].read_result().blocks[0].arguments, arguments)
        self.assertEqual(values[-1].read_result().usage.total_tokens, 30)
        self.assertEqual(values[-1].read_provenance().usage_origin, 'SIMULATION_FIXTURE')
        self.assertEqual(values[-2].type, 'usage')

    async def test_bad_tool_arguments_and_duplicate_json_keys_cannot_execute(self):
        self.exact_input = ExactInput(messages=self.exact_input.messages, tools=[{'type': 'function', 'function': {
            'name': 'read_artifact', 'description': '读取模拟制品', 'parameters': {'type': 'object', 'additionalProperties': False,
                'required': ['artifact_id'], 'properties': {'artifact_id': {'type': 'string', 'format': 'uuid'}}}}}])
        for arguments in ('{}', '{"artifact_id":"not-a-uuid"}', '{"artifact_id":"a","artifact_id":"b"}'):
            self.case = self.make_case([{'type': 'tool-call', 'id': 'simulation-call', 'name': 'read_artifact', 'arguments': arguments}], 'tool-calls')
            with self.assertRaises((SimulationExecutionError, ValueError)):
                await self.execute()
        self.assertEqual(list(self.directory.iterdir()), [])

    async def test_consumer_interruption_requires_reconciliation_and_never_resends(self):
        iterator = self.stream()
        await anext(iterator)
        await iterator.aclose()
        self.assertFalse((self.saved_directory() / 'commit.json').exists())
        with patch.object(SimulationProvider, 'stream', side_effect=AssertionError('duplicate execution')):
            with self.assertRaisesRegex(SimulationExecutionError, 'SIMULATION_RECONCILIATION_REQUIRED'):
                await self.execute()

    async def test_interrupted_result_sidecar_and_commit_writes_never_return_success(self):
        original_install = _RecordDirectory.install
        for target in ('result.json', 'provenance.json', 'commit.json'):
            with self.subTest(target=target), tempfile.TemporaryDirectory(prefix='medical-simulation-write-') as root:
                self.store = SimulationFileStore(Path(root), maximum_record_bytes=1_048_576)
                self.executor = SimulationExecutor(self.store, self.bindings)
                observed = []

                def interrupted(directory, name, data):
                    if name == target:
                        raise OSError('simulated disk failure')
                    original_install(directory, name, data)

                with patch.object(_RecordDirectory, 'install', interrupted):
                    with self.assertRaisesRegex(OSError, 'simulated disk failure'):
                        async for event in self.stream():
                            observed.append(event)
                self.assertFalse(any(isinstance(item, SimulationExecutionFinished) for item in observed))
                with patch.object(SimulationProvider, 'stream', side_effect=AssertionError('duplicate execution')):
                    with self.assertRaisesRegex(SimulationExecutionError, 'SIMULATION_RECONCILIATION_REQUIRED'):
                        await self.execute()

    async def test_failed_reread_does_not_emit_success_and_committed_result_can_be_recovered(self):
        original_read = _RecordDirectory.read
        armed = False
        original_install = _RecordDirectory.install

        def installed(directory, name, data):
            nonlocal armed
            original_install(directory, name, data)
            if name == 'commit.json':
                armed = True

        def failure(directory, name):
            if armed and name == 'result.json':
                raise OSError('reread unavailable')
            return original_read(directory, name)

        observed = []
        with patch.object(_RecordDirectory, 'install', installed), patch.object(_RecordDirectory, 'read', failure):
            with self.assertRaisesRegex(OSError, 'reread unavailable'):
                async for event in self.stream():
                    observed.append(event)
        self.assertFalse(any(isinstance(item, SimulationExecutionFinished) for item in observed))
        with patch.object(SimulationProvider, 'stream', side_effect=AssertionError('duplicate execution')):
            recovered = await self.execute()
        self.assertTrue(recovered[-1].replayed)

    async def test_tampered_missing_sidecar_or_result_is_not_accepted(self):
        await self.execute()
        saved = self.saved_directory()
        for name in ('request.json', 'started.json', 'result.json', 'provenance.json', 'commit.json'):
            original = (saved / name).read_bytes()
            with self.subTest(name=name), patch.object(SimulationProvider, 'stream', side_effect=AssertionError('duplicate execution')):
                (saved / name).write_bytes(original + b' ')
                with self.assertRaises(SimulationExecutionError):
                    await self.execute()
                (saved / name).write_bytes(original)
        sidecar = (saved / 'provenance.json').read_bytes()
        (saved / 'provenance.json').unlink()
        with self.assertRaisesRegex(SimulationExecutionError, 'SIMULATION_CONTENT_MISMATCH'):
            await self.execute()
        (saved / 'provenance.json').write_bytes(sidecar)
        (saved / 'provenance.json').chmod(0o600)

    async def test_recomputed_tampered_result_hashes_cannot_change_case_output(self):
        await self.execute()
        saved = self.saved_directory()
        result = json.loads((saved / 'result.json').read_bytes())
        result['blocks'][0]['text'] = 'forged'
        result = rfc8785.dumps(result)
        proof = json.loads((saved / 'provenance.json').read_bytes())
        proof['model_result_sha256'] = hashlib.sha256(result).hexdigest()
        proof = rfc8785.dumps(proof)
        commit = json.loads((saved / 'commit.json').read_bytes())
        commit.update(result_sha256=hashlib.sha256(result).hexdigest(), provenance_sha256=hashlib.sha256(proof).hexdigest())
        (saved / 'result.json').write_bytes(result)
        (saved / 'provenance.json').write_bytes(proof)
        (saved / 'commit.json').write_bytes(rfc8785.dumps(commit))
        with self.assertRaisesRegex(SimulationExecutionError, 'SIMULATION_CONTENT_MISMATCH'):
            await self.execute()

    async def test_concurrent_same_action_is_busy_and_replay_after_finish_is_single_execution(self):
        first = self.stream()
        await anext(first)
        with self.assertRaisesRegex(SimulationExecutionError, 'SIMULATION_EXECUTION_BUSY'):
            await self.execute()
        result = [item async for item in first]
        self.assertIsInstance(result[-1], SimulationExecutionFinished)
        with patch.object(SimulationProvider, 'stream', side_effect=AssertionError('duplicate execution')):
            self.assertTrue((await self.execute())[-1].replayed)

    async def test_second_process_cannot_claim_an_active_action(self):
        iterator = self.stream()
        await anext(iterator)
        command = f'''from pathlib import Path
from medical_model_gateway.simulation_execution import SimulationFileStore, SimulationIdentity, SimulationExecutionError
store = SimulationFileStore(Path({str(self.directory)!r}), maximum_record_bytes=1048576)
try:
    with store.claim(SimulationIdentity(**{self.identity.model_dump(mode='json')!r})):
        pass
except SimulationExecutionError as error:
    raise SystemExit(0 if str(error) == 'SIMULATION_EXECUTION_BUSY' else 2)
raise SystemExit(1)
'''
        try:
            result = subprocess.run([sys.executable, '-c', command], capture_output=True, timeout=5)
            self.assertEqual(result.returncode, 0, result.stderr.decode())
            self.assertIsInstance([value async for value in iterator][-1], SimulationExecutionFinished)
        finally:
            await iterator.aclose()

    async def test_record_and_lock_modes_hardlinks_and_symlinks_are_rejected(self):
        await self.execute()
        directory = self.saved_directory()
        with patch.object(SimulationProvider, 'stream', side_effect=AssertionError('duplicate execution')):
            for name in ('result.json', '.lock'):
                path = directory / name
                with self.subTest(name=name, variant='mode'):
                    path.chmod(0o644)
                    with self.assertRaisesRegex(SimulationExecutionError, 'SIMULATION_STORAGE_NOT_PRIVATE'):
                        await self.execute()
                    path.chmod(0o600)
                with self.subTest(name=name, variant='hardlink'):
                    link = directory / 'unexpected-hardlink'
                    os.link(path, link)
                    with self.assertRaisesRegex(SimulationExecutionError, 'SIMULATION_STORAGE_NOT_PRIVATE'):
                        await self.execute()
                    link.unlink()
                with self.subTest(name=name, variant='symlink'):
                    original = path.read_bytes()
                    target = directory / 'symlink-target'
                    target.write_bytes(original)
                    target.chmod(0o600)
                    path.unlink()
                    path.symlink_to(target)
                    with self.assertRaises(OSError):
                        await self.execute()
                    path.unlink()
                    target.rename(path)

    async def test_timeout_preserves_incomplete_state_without_cancel_proof(self):
        original = SimulationProvider.stream

        async def delayed(provider, *args):
            async for event in original(provider, *args):
                yield event
                await asyncio.sleep(2)

        self.settings = self.settings.model_copy(update={'deadline_seconds': 1})
        self.case = self.make_case()
        with patch.object(SimulationProvider, 'stream', delayed):
            with self.assertRaises(TimeoutError):
                await self.execute()
        with self.assertRaisesRegex(SimulationExecutionError, 'SIMULATION_RECONCILIATION_REQUIRED'):
            await self.execute()
        self.assertFalse((self.saved_directory() / 'commit.json').exists())

    async def test_consumer_pause_is_not_cancelled_and_expired_stream_cannot_finish(self):
        self.settings = self.settings.model_copy(update={'deadline_seconds': 1})
        self.case = self.make_case()
        iterator = self.stream()
        await anext(iterator)
        # Deadline belongs to component advancement, not unrelated consumer work.
        await asyncio.sleep(1.1)
        with self.assertRaises(TimeoutError):
            await anext(iterator)
        self.assertFalse((self.saved_directory() / 'commit.json').exists())

    async def test_deadline_expiring_during_save_does_not_commit_or_return_success(self):
        self.settings = self.settings.model_copy(update={'deadline_seconds': 1})
        self.case = self.make_case()
        original = _RecordDirectory.install

        def delayed(directory, name, data):
            original(directory, name, data)
            if name == 'provenance.json':
                time.sleep(1.1)

        observed = []
        with patch.object(_RecordDirectory, 'install', delayed):
            with self.assertRaises(TimeoutError):
                async for item in self.stream():
                    observed.append(item)
        self.assertFalse(any(isinstance(item, SimulationExecutionFinished) for item in observed))
        self.assertFalse((self.saved_directory() / 'commit.json').exists())

    async def test_storage_permissions_symlinks_and_size_fail_closed(self):
        self.directory.chmod(0o755)
        with self.assertRaisesRegex(SimulationExecutionError, 'SIMULATION_STORAGE_NOT_PRIVATE'):
            await self.execute()
        self.directory.chmod(0o700)
        with tempfile.TemporaryDirectory() as other:
            link = Path(other) / 'store-link'
            link.symlink_to(self.directory, target_is_directory=True)
            self.executor = SimulationExecutor(SimulationFileStore(link, maximum_record_bytes=1_048_576), self.bindings)
            with self.assertRaises(OSError):
                await self.execute()
        self.executor = SimulationExecutor(SimulationFileStore(self.directory, maximum_record_bytes=1), self.bindings)
        with self.assertRaisesRegex(SimulationExecutionError, 'SIMULATION_STORAGE_SIZE_INVALID'):
            await self.execute()

    async def test_sidecar_cannot_claim_real_execution_or_approval(self):
        finished = (await self.execute())[-1]
        original = json.loads(finished.provenance_bytes)
        for key, value in (('input_data_kind', 'REAL'), ('production_eligible', True), ('external_request_sent', True),
                           ('provider_charge_amount', '1.00000000'), ('credential_reference_id', str(uuid.uuid4()))):
            with self.subTest(key=key), self.assertRaises(ValidationError):
                self.executor.provenance_validator.validate({**original, key: value})

    def test_fifo_record_is_rejected_without_blocking(self):
        os.mkfifo(self.directory / 'result.json', 0o600)
        command = f'''import os
from medical_model_gateway.simulation_execution import _RecordDirectory, SimulationExecutionError
fd = os.open({str(self.directory)!r}, os.O_RDONLY | os.O_DIRECTORY)
try:
    _RecordDirectory(fd, 1024).read('result.json')
except (OSError, SimulationExecutionError):
    raise SystemExit(0)
raise SystemExit(1)
'''
        result = subprocess.run([sys.executable, '-c', command], capture_output=True, timeout=5)
        self.assertEqual(result.returncode, 0, result.stderr.decode())

    def test_fifo_lock_is_rejected_without_blocking(self):
        with self.store.claim(self.identity):
            pass
        lock = self.saved_directory() / '.lock'
        lock.unlink()
        os.mkfifo(lock, 0o600)
        command = f'''from pathlib import Path
from medical_model_gateway.simulation_execution import SimulationFileStore, SimulationIdentity, SimulationExecutionError
store = SimulationFileStore(Path({str(self.directory)!r}), maximum_record_bytes=1024)
try:
    with store.claim(SimulationIdentity(**{self.identity.model_dump(mode='json')!r})):
        pass
except (OSError, SimulationExecutionError):
    raise SystemExit(0)
raise SystemExit(1)
'''
        result = subprocess.run([sys.executable, '-c', command], capture_output=True, timeout=5)
        self.assertEqual(result.returncode, 0, result.stderr.decode())

    def test_shared_catalogue_and_original_model_schema_are_unchanged(self):
        hashes = {'contracts/catalogue.json': 'dcd72acbb7a45775373662e0ffe4557030d4740b0afacdee8375e7768e761915',
            'agent-design/model-stream.schema.json': '9a379c8c9d292f8a24e2a7a153b91598aa00d96f024772829246192b96a5d378',
            'business/medical-persistence/src/main/resources/permission-decisions.schema.json': 'fca270edf7d9697f340333c15992ecaa77fff4a8f1ac7bc51d2ffa8d56094843',
            'contracts/extensions/evaluation-results.catalogue.json': 'a1b3dce03c553d0e9166d76c30e5af287c3bb06dfe90c9cc696632a1663c891d'}
        for path, expected in hashes.items():
            self.assertEqual(hashlib.sha256((ROOT / path).read_bytes()).hexdigest(), expected, path)


if __name__ == '__main__':
    unittest.main()
