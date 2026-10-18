import asyncio
import copy
import fcntl
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import rfc8785
from medical_contracts.catalogue import SchemaCatalogue
from medical_contracts.model_stream import ModelStreamBindings, RequestParameters
from medical_model_gateway.protocol import ExactInput, ProviderSettings
from medical_model_gateway.simulation import SimulationCase
from medical_model_gateway.simulation_execution import SimulationExecutor, SimulationFileStore, SimulationIdentity, SimulationExecutionError
from medical_model_gateway.simulation_bridge import execute_pipe

ROOT = Path(__file__).resolve().parents[1]
FIXTURES = ROOT / 'business/medical-persistence/src/test/resources/simulation-ledger'


class SimulationReconciliationTests(unittest.IsolatedAsyncioTestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.directory = Path(self.temp.name)
        self.request = json.loads((FIXTURES / 'admission.json').read_bytes())
        self.identity = SimulationIdentity.model_validate(self.request['binding']['identity'])
        self.arguments = dict(identity=self.identity, case=SimulationCase.model_validate(self.request['simulation_case']),
            settings=ProviderSettings.model_validate(self.request['settings']),
            parameters=RequestParameters.model_validate(self.request['request_parameters']),
            exact_input=ExactInput.model_validate(self.request['exact_input']))
        self.store = SimulationFileStore(self.directory, maximum_record_bytes=16_777_216)
        self.executor = SimulationExecutor(self.store, ModelStreamBindings(SchemaCatalogue(ROOT)))

    async def execute(self):
        return [x async for x in self.executor.stream(**self.arguments)][-1]

    def snapshot(self):
        return {str(p.relative_to(self.directory)): (p.read_bytes(), p.stat().st_mtime_ns)
                for p in self.directory.rglob('*') if p.is_file()}

    async def test_committed_journal_reconciles_without_execution_or_writes(self):
        original = await self.execute()
        before = self.snapshot()
        with (patch('medical_model_gateway.simulation.SimulationProvider.stream', side_effect=AssertionError('provider')),
              patch('os.mkdir', side_effect=AssertionError('mkdir')),
              patch('medical_model_gateway.simulation_execution._RecordDirectory.install', side_effect=AssertionError('write'))):
            recovered = self.executor.reconcile(**self.arguments)
        self.assertTrue(recovered.replayed)
        self.assertEqual(original.result_bytes, recovered.result_bytes)
        self.assertEqual(original.provenance_bytes, recovered.provenance_bytes)
        self.assertEqual(before, self.snapshot())

    async def test_missing_journal_does_not_create_root_child_or_lock(self):
        with self.assertRaisesRegex(SimulationExecutionError, 'RECONCILIATION_REQUIRED'):
            self.executor.reconcile(**self.arguments)
        self.assertEqual([], list(self.directory.iterdir()))
        missing = SimulationExecutor(SimulationFileStore(self.directory / 'absent', maximum_record_bytes=4096), self.executor.models)
        with self.assertRaisesRegex(SimulationExecutionError, 'RECONCILIATION_REQUIRED'):
            missing.reconcile(**self.arguments)
        self.assertFalse((self.directory / 'absent').exists())

    async def test_partial_commit_and_missing_lock_remain_unknown_without_repair(self):
        await self.execute()
        child = next(self.directory.iterdir())
        for name in ('commit.json', '.lock'):
            original = (child / name).read_bytes()
            (child / name).unlink()
            before = self.snapshot()
            with self.assertRaisesRegex(SimulationExecutionError, 'RECONCILIATION_REQUIRED'):
                self.executor.reconcile(**self.arguments)
            self.assertEqual(before, self.snapshot())
            (child / name).write_bytes(original); (child / name).chmod(0o600)

    async def test_tampered_result_is_not_repaired_or_trusted(self):
        await self.execute()
        child = next(self.directory.iterdir())
        (child / 'result.json').write_bytes(b'{}')
        before = self.snapshot()
        with self.assertRaisesRegex(SimulationExecutionError, 'CONTENT_MISMATCH'):
            self.executor.reconcile(**self.arguments)
        self.assertEqual(before, self.snapshot())

    async def test_existing_busy_lock_is_not_replaced(self):
        await self.execute()
        child = next(self.directory.iterdir())
        with (child / '.lock').open('rb') as lock:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
            before = self.snapshot()
            with self.assertRaisesRegex(SimulationExecutionError, 'EXECUTION_BUSY'):
                self.executor.reconcile(**self.arguments)
            self.assertEqual(before, self.snapshot())

    async def test_readonly_directory_handle_cannot_install(self):
        await self.execute()
        with self.store.existing(self.identity) as directory:
            with self.assertRaisesRegex(SimulationExecutionError, 'READ_ONLY'):
                directory.install('unexpected.json', b'{}')
        self.assertFalse(any(p.name == 'unexpected.json' for p in self.directory.rglob('*')))

    async def test_pipe_reconciliation_uses_existing_commit_without_provider(self):
        await self.execute()
        before = self.snapshot()
        with patch('medical_model_gateway.simulation.SimulationProvider.stream', side_effect=AssertionError('provider')):
            value = json.loads(await execute_pipe(rfc8785.dumps(self.request), project_root=ROOT,
                store_directory=self.directory, maximum_record_bytes=16_777_216, reconcile_only=True))
        self.assertTrue(value['replayed']); self.assertEqual(before, self.snapshot())

    async def test_altered_identity_cannot_reuse_an_existing_commit(self):
        await self.execute()
        changed = copy.deepcopy(self.arguments)
        changed['identity'] = self.identity.model_copy(update={'invocation_id': '00000000-0000-0000-0000-000000000099'})
        before = self.snapshot()
        with self.assertRaisesRegex(SimulationExecutionError, 'CONTENT_MISMATCH'):
            self.executor.reconcile(**changed)
        self.assertEqual(before, self.snapshot())
