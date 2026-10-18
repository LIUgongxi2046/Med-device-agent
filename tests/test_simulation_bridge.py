import asyncio
import base64
import copy
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

import rfc8785

from medical_model_gateway.simulation_bridge import execute_pipe, SimulationBridgeError

ROOT = Path(__file__).resolve().parents[1]
FIXTURES = ROOT / 'business/medical-persistence/src/test/resources/simulation-ledger'


class SimulationBridgeTest(unittest.IsolatedAsyncioTestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.directory = Path(self.temp.name)
        self.addCleanup(self.temp.cleanup)
        self.request = json.loads((FIXTURES / 'admission.json').read_bytes())

    async def execute(self, request=None, **kwargs):
        return await execute_pipe(rfc8785.dumps(request or self.request), project_root=ROOT,
            store_directory=self.directory, maximum_record_bytes=kwargs.get('limit', 16_777_216))

    async def test_actual_local_execution_returns_original_canonical_bytes_without_network(self):
        with patch('socket.create_connection', side_effect=AssertionError('network')):
            value = json.loads(await self.execute())
        self.assertFalse(value['replayed'])
        self.assertEqual(base64.b64decode(value['result_base64']), (FIXTURES / 'result.json').read_bytes())
        self.assertEqual(base64.b64decode(value['provenance_base64']), (FIXTURES / 'provenance.json').read_bytes())
        self.assertEqual(set(value), {'schema_version', 'result_base64', 'provenance_base64', 'replayed'})

    async def test_component_reread_never_implies_dispatch_authority(self):
        first = json.loads(await self.execute())
        with patch('medical_model_gateway.simulation.SimulationProvider.stream', side_effect=AssertionError('repeat')):
            second = json.loads(await self.execute())
        self.assertTrue(second.pop('replayed'))
        first.pop('replayed')
        self.assertEqual(first, second)

    async def test_fixed_binding_mutation_rejected_before_storage(self):
        for field in ['local_request_sha256', 'case_sha256', 'settings_sha256', 'parameters_sha256']:
            request = copy.deepcopy(self.request)
            request['binding'][field] = 'f' * 64
            with self.assertRaises(SimulationBridgeError):
                await self.execute(request)
            self.assertEqual(list(self.directory.iterdir()), [])

    async def test_caps_checked_before_storage(self):
        request = copy.deepcopy(self.request)
        request['binding']['caps']['maximum_output_units'] = 1
        with self.assertRaises(SimulationBridgeError):
            await self.execute(request)
        self.assertEqual(list(self.directory.iterdir()), [])

    async def test_counter_bytes_and_fixture_receipt_checked(self):
        for target, key, value in [
            ('exact_input', 'units', 1),
            ('binding', 'case_receipt_id', '00000000-0000-0000-0000-000000000099'),
        ]:
            request = copy.deepcopy(self.request)
            container = request['binding']['exact_input'] if target == 'exact_input' else request['binding']
            container[key] = value
            with self.assertRaises((ValueError, RuntimeError)):
                await self.execute(request)
        self.assertEqual(list(self.directory.iterdir()), [])

    async def test_invalid_or_duplicate_json_rejected(self):
        for value in [b'', b'{} {}', b'{"a":1,"a":2}', b'\xff', b'x' * (16_777_216 + 1)]:
            with self.assertRaises(Exception):
                await execute_pipe(value, project_root=ROOT, store_directory=self.directory,
                    maximum_record_bytes=16_777_216)
        self.assertEqual(list(self.directory.iterdir()), [])

    async def test_nonprivate_storage_and_unbounded_record_limit_rejected(self):
        os.chmod(self.directory, 0o755)
        with self.assertRaises(Exception):
            await self.execute()
        os.chmod(self.directory, 0o700)
        for limit in [0, -1, True, 16_777_217]:
            with self.assertRaises(SimulationBridgeError):
                await self.execute(limit=limit)

    def test_cli_uses_only_pipe_and_redacted_errors(self):
        command = [sys.executable, '-I', '-m', 'medical_model_gateway.simulation_bridge',
            '--project-root', str(ROOT), '--store-directory', str(self.directory),
            '--maximum-record-bytes', '16777216']
        bad = subprocess.run(command, input=b'{"SECRET":"DO_NOT_PRINT"}', capture_output=True, timeout=30)
        self.assertEqual(bad.returncode, 2)
        self.assertEqual(bad.stdout, b'')
        self.assertEqual(bad.stderr, b'SIMULATION_BRIDGE_FAILED\n')
        good = subprocess.run(command, input=rfc8785.dumps(self.request), capture_output=True, timeout=30)
        self.assertEqual(good.returncode, 0, good.stderr)
        self.assertEqual(good.stderr, b'')
        self.assertFalse(json.loads(good.stdout)['replayed'])
