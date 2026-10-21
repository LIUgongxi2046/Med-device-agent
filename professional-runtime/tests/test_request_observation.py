"""SO_PEERCRED transport doubles and real immutable request journals, no sockets."""
import asyncio
import base64
from dataclasses import replace
from pathlib import Path
import socket
import struct
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch
from uuid import uuid4
from jsonschema import ValidationError

from medical_professional_runtime.host import RuntimeBlocked, digest, strict_json
from medical_professional_runtime.request_observation import (
    PROJECTION_REF, PROJECTION_SCHEMA_SHA256, PROJECTION_VERSION, SupervisorRequestRecorder,
)
from medical_professional_runtime.supervisor import canonical
from test_supervisor import make_fixture


def projection():
    return {'provider': 'enterprise-model', 'model': 'fixture',
            'messages': [{'role': 'user', 'content': [{'type': 'text', 'text': '你好'}]}],
            'system': None, 'tools': [], 'tool_history': None,
            'parameters': {'max_output_tokens': 100, 'temperature': 0.5,
                           'reasoning_effort': None, 'stop': [], 'purpose': 'TASK'},
            'session_id': None}


BODY = canonical(projection())


class SocketDouble:
    """The only source of peer credentials is getsockopt on request.transport."""
    def __init__(self, pid=200, uid=1000, gid=1000):
        self.raw = struct.pack('3i', pid, uid, gid)
        self.calls = []
        self.failure = None

    def getsockopt(self, level, option, size):
        self.calls.append((level, option, size))
        if self.failure:
            raise self.failure
        return self.raw


class TransportDouble:
    def __init__(self, sock):
        self.sock = sock
        self.calls = []

    def get_extra_info(self, name):
        self.calls.append(name)
        return self.sock if name == 'socket' else None


def request(body, peer=None, **changes):
    values = dict(method='POST', path='/v1/model-contexts', content_length=len(body),
                  headers={}, transport=TransportDouble(peer or SocketDouble()))
    values.update(changes)
    return SimpleNamespace(**values)


class RequestObservationTest(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.fixture = make_fixture(self.temporary.name)
        self.supervisor = self.fixture.supervisor
        await self.supervisor.prepare()
        self.recorder = SupervisorRequestRecorder(self.supervisor)

    async def asyncTearDown(self):
        cleanup = getattr(self.supervisor, 'cleanup', None)
        if cleanup is not None:
            await asyncio.gather(cleanup, return_exceptions=True)
        for future in (self.fixture.process.exit_task, self.fixture.process.output_task):
            if future is not None and not future.done():
                future.cancel()

    def request_files(self):
        return sorted(self.supervisor.root.glob('request-*.json'))

    async def test_actual_transport_peer_matches_current_runtime_and_original_bytes(self):
        body = BODY
        peer = SocketDouble()
        incoming = request(body, peer)
        reference = await self.recorder.capture(incoming, body)
        self.assertEqual(peer.calls, [(socket.SOL_SOCKET, socket.SO_PEERCRED, struct.calcsize('3i'))])
        self.assertEqual(incoming.transport.calls, ['socket'])
        self.assertEqual(reference['ordinal'], 1)
        record = self.recorder.read(reference['request_id'])
        self.assertEqual(record['peer_pid'], 200)
        self.assertEqual(record['peer_start_ticks'], 2001)
        self.assertEqual(record['peer_exe_sha256'], 'c' * 64)
        self.assertEqual(record['binding'], dict(self.fixture.plan.binding))
        self.assertEqual(record['session_id'], self.fixture.plan.session_id)
        self.assertEqual(record['supervision_receipt_id'], self.supervisor.admitted['receipt_id'])
        self.assertEqual(record['supervision_sha256'], digest(canonical(self.supervisor.admitted)))
        self.assertEqual(record['raw_body_sha256'], digest(body))
        self.assertEqual(record['body_sha256'], digest(body))
        self.assertEqual(record['raw_size_bytes'], len(body))
        self.assertEqual(record['projection_schema_ref'], PROJECTION_REF)
        self.assertEqual(record['projection_schema_version'], PROJECTION_VERSION)
        self.assertEqual(record['projection_schema_sha256'], PROJECTION_SCHEMA_SHA256)
        self.assertEqual(base64.b64decode(record['body_base64'], validate=True), body)
        path = self.request_files()[0]
        self.assertEqual(digest(path.read_bytes()), reference['sha256'])
        self.assertEqual(path.stat().st_mode & 0o777, 0o600)
        self.assertEqual(self.recorder.total, len(path.read_bytes()))

    async def test_preserves_jcs_number_spelling_instead_of_python_reencoding(self):
        value = projection()
        value['parameters']['temperature'] = 1e-7
        body = canonical(value).replace(b'1e-07', b'1e-7')
        self.assertNotEqual(canonical(strict_json(body)), body)
        reference = await self.recorder.capture(request(body), body)
        record = self.recorder.read(reference['request_id'])
        self.assertEqual(base64.b64decode(record['body_base64']), body)

    async def test_preserves_declared_tools_history_sources_and_parameters_without_projection_loss(self):
        value = projection()
        tool = {'name': 'lookup', 'description': 'fixture tool',
                'parameters': {'type': 'object', 'properties': {'key': {'type': 'string'}}}}
        value.update(system='system text', tools=[tool],
                     tool_history={'tools': [tool], 'updates': []}, session_id='runtime-session')
        value['messages'].append({
            'role': 'assistant', 'id': 'message-1', 'isError': False,
            'content': [{'type': 'tool-call', 'id': 'call-1', 'name': 'lookup', 'arguments': '{"key":"v"}'}],
            'source': {'kind': 'model', 'provider': 'enterprise-model', 'model': 'fixture',
                       'replayState': {'response': {'invocation_id': str(uuid4()),
                                                    'replay_state_id': 'replay-1'}}}})
        value['messages'].append({'role': 'tool', 'toolCallId': 'call-1',
                                  'content': [{'type': 'text', 'text': 'result'}],
                                  'source': {'kind': 'tool', 'callId': 'call-1'}})
        value['parameters'].update(reasoning_effort='high', stop=['STOP'], purpose='COMPACTION')
        body = canonical(value)
        reference = await self.recorder.capture(request(body), body)
        record = self.recorder.read(reference['request_id'])
        self.assertEqual(base64.b64decode(record['body_base64']), body)
        self.assertEqual(strict_json(base64.b64decode(record['body_base64'])), value)

    async def test_schema_rejects_undeclared_claims_missing_fields_and_unbounded_shapes(self):
        cases = []
        value = projection(); value['peer_pid'] = 200; cases.append(value)
        value = projection(); value.pop('parameters'); cases.append(value)
        value = projection(); value['parameters']['max_output_tokens'] = True; cases.append(value)
        value = projection(); value['parameters']['max_output_tokens'] = 9007199254740992; cases.append(value)
        value = projection(); value['messages'][0]['role'] = 'unsupported'; cases.append(value)
        value = projection(); value['messages'] *= 2049; cases.append(value)
        value = projection(); value['parameters']['stop'] = ['stop'] * 257; cases.append(value)
        for index, value in enumerate(cases):
            with self.subTest(case=index):
                body = canonical(value)
                with self.assertRaises((RuntimeBlocked, ValidationError)):
                    await self.recorder.capture(request(body), body)
        self.assertEqual(self.recorder.ordinal, 0)
        self.assertEqual(self.request_files(), [])

    async def test_host_supplied_peer_claim_never_overrides_kernel_credentials(self):
        body = BODY
        incoming = request(body, headers={'X-Peer-Pid': '100', 'X-Peer-Uid': '0'})
        reference = await self.recorder.capture(incoming, body)
        record = self.recorder.read(reference['request_id'])
        self.assertEqual((record['peer_pid'], record['peer_exe_sha256']), (200, 'c' * 64))
        self.assertEqual(base64.b64decode(record['body_base64']), body)

    async def test_same_body_is_distinct_sequenced_observation_not_deduplicated_assertion(self):
        body = BODY
        first = await self.recorder.capture(request(body), body)
        second = await self.recorder.capture(request(body), body)
        self.assertEqual((first['ordinal'], second['ordinal']), (1, 2))
        self.assertNotEqual(first['request_id'], second['request_id'])
        self.assertNotEqual(first['receipt_id'], second['receipt_id'])
        self.assertEqual(len(self.request_files()), 2)

    async def test_colliding_request_id_cannot_replace_original_immutable_record(self):
        reference = await self.recorder.capture(request(BODY), BODY)
        original = self.request_files()[0].read_bytes()
        with patch('medical_professional_runtime.request_observation.uuid4',
                   side_effect=[reference['request_id'], str(uuid4())]):
            with self.assertRaises(FileExistsError):
                await self.recorder.capture(request(BODY), BODY)
        self.assertEqual(len(self.request_files()), 1)
        self.assertEqual(self.request_files()[0].read_bytes(), original)
        self.assertEqual(self.supervisor.state, 'UNKNOWN')

    async def test_concurrent_capture_is_serialized_and_has_no_lost_ordinals(self):
        body = BODY
        results = await asyncio.gather(*(self.recorder.capture(request(body), body) for _ in range(8)))
        self.assertEqual(sorted(item['ordinal'] for item in results), list(range(1, 9)))
        self.assertEqual(len({item['request_id'] for item in results}), 8)
        self.assertEqual(self.recorder.total, sum(path.stat().st_size for path in self.request_files()))

    async def test_wrong_pid_uid_gid_and_launcher_are_not_runtime_peer(self):
        body = BODY
        for credentials in ((999, 1000, 1000), (200, 1001, 1000),
                            (200, 1000, 1001), (100, 1000, 1000)):
            with self.subTest(credentials=credentials):
                with self.assertRaisesRegex(RuntimeBlocked, 'SDK_PEER_NOT_INSPECTED_RUNTIME'):
                    await self.recorder.capture(request(body, SocketDouble(*credentials)), body)
        self.assertEqual(self.recorder.ordinal, 0)
        self.assertEqual(self.request_files(), [])

    async def test_runtime_role_must_match_operator_profile_not_any_inspected_executable(self):
        config = self.fixture.inspector.config
        self.fixture.inspector.config = replace(
            config, executable_profiles=tuple(replace(item, role='launcher')
                                              for item in config.executable_profiles))
        with self.assertRaisesRegex(RuntimeBlocked, 'SDK_PEER_NOT_INSPECTED_RUNTIME'):
            await self.recorder.capture(request(BODY), BODY)
        self.assertEqual(self.request_files(), [])

    async def test_missing_transport_or_socket_does_not_fall_back_to_asserted_headers(self):
        body = BODY
        for transport in (None, TransportDouble(None)):
            with self.subTest(transport=transport):
                incoming = request(body, transport=transport, headers={'X-Peer-Pid': '200'})
                with self.assertRaisesRegex(RuntimeBlocked, 'SDK_PEER_CREDENTIALS_REQUIRED'):
                    await self.recorder.capture(incoming, body)
        self.assertEqual(self.request_files(), [])

    async def test_missing_or_malformed_socket_credentials_fail_closed(self):
        for raw in (b'', b'\x00' * 11, b'\x00' * 13):
            with self.subTest(length=len(raw)):
                peer = SocketDouble()
                peer.raw = raw
                with self.assertRaisesRegex(RuntimeBlocked, 'SDK_PEER_CREDENTIALS_REQUIRED'):
                    await self.recorder.capture(request(BODY, peer), BODY)
        peer = SocketDouble()
        peer.failure = OSError('synthetic getsockopt failure')
        with self.assertRaisesRegex(OSError, 'getsockopt failure'):
            await self.recorder.capture(request(BODY, peer), BODY)
        self.assertEqual(self.request_files(), [])

    async def test_bad_framing_rejected_before_peer_inspection_or_persistence(self):
        body = BODY
        changes = ({'content_length': None}, {'content_length': 1},
                   {'headers': {'Transfer-Encoding': 'chunked'}},
                   {'headers': {'Content-Encoding': 'gzip'}})
        for change in changes:
            with self.subTest(change=change):
                peer = SocketDouble()
                with self.assertRaisesRegex(RuntimeBlocked, 'SDK_REQUEST_FRAMING_REQUIRED'):
                    await self.recorder.capture(request(body, peer, **change), body)
                self.assertEqual(peer.calls, [])
        self.assertEqual(self.recorder.ordinal, 0)

    async def test_invalid_method_route_empty_or_nonbytes_body_rejected_early(self):
        for body, change in ((BODY, {'method': 'GET'}),
                             (BODY, {'path': '/v1/model-streams'}),
                             (b'', {}), ('{}', {}), (bytearray(BODY), {})):
            with self.subTest(body=body, change=change):
                peer = SocketDouble()
                with self.assertRaisesRegex(RuntimeBlocked, 'SDK_REQUEST_OBSERVATION_INVALID'):
                    await self.recorder.capture(request(body, peer, **change), body)
                self.assertEqual(peer.calls, [])
        self.assertEqual(self.request_files(), [])

    async def test_duplicate_json_keys_and_invalid_json_are_not_observed(self):
        for body in (b'{"a":1,"a":2}', b'{"nested":{"a":1,"a":2}}',
                     b'{"a":NaN}', b'{"a":Infinity}', b'not json', b'{"a":"\xff"}'):
            with self.subTest(body=body):
                with self.assertRaises(RuntimeBlocked):
                    await self.recorder.capture(request(body), body)
        self.assertEqual(self.recorder.ordinal, 0)
        self.assertEqual(self.request_files(), [])

    async def test_utf16_utf32_and_bom_are_not_accepted_as_utf8_sdk_body(self):
        for body in ('{}'.encode('utf-16'), '{}'.encode('utf-32'), b'\xef\xbb\xbf{}'):
            with self.subTest(body=body):
                with self.assertRaises(RuntimeBlocked):
                    await self.recorder.capture(request(body), body)
        self.assertEqual(self.request_files(), [])

    async def test_per_request_limit_checked_before_peer_or_encoding(self):
        recorder = SupervisorRequestRecorder(self.supervisor, maximum_request_bytes=4)
        body = b'{"long":true}'
        peer = SocketDouble()
        with self.assertRaisesRegex(RuntimeBlocked, 'SDK_REQUEST_OBSERVATION_INVALID'):
            await recorder.capture(request(body, peer), body)
        self.assertEqual(peer.calls, [])
        self.assertEqual(recorder.ordinal, 0)

    async def test_request_count_limit_does_not_consume_another_slot(self):
        recorder = SupervisorRequestRecorder(self.supervisor, maximum_requests=1)
        await recorder.capture(request(BODY), BODY)
        with self.assertRaisesRegex(RuntimeBlocked, 'REQUEST_OBSERVATION_RETENTION_LIMIT'):
            await recorder.capture(request(BODY), BODY)
        self.assertEqual(recorder.ordinal, 1)
        self.assertEqual(len(self.request_files()), 1)

    async def test_aggregate_limit_counts_encoded_evidence_not_only_body_bytes(self):
        recorder = SupervisorRequestRecorder(self.supervisor, maximum_request_bytes=1024,
                                             maximum_retained_bytes=6000)
        await recorder.capture(request(BODY), BODY)
        self.assertGreater(recorder.total, len(BODY))
        self.assertEqual(recorder.total, self.request_files()[0].stat().st_size)
        with self.assertRaisesRegex(RuntimeBlocked, 'REQUEST_OBSERVATION_RETENTION_LIMIT'):
            await recorder.capture(request(BODY), BODY)
        self.assertEqual(recorder.ordinal, 1)

    async def test_persistent_record_limit_is_independent_of_body_limit(self):
        value = projection()
        value['messages'][0]['content'][0]['text'] = 'x' * 4000
        body = canonical(value)
        maximum = max(len(body), (self.supervisor.root / 'admitted.json').stat().st_size) + 1
        self.supervisor.plan = replace(self.fixture.plan, maximum_bytes=maximum)
        with self.assertRaisesRegex(RuntimeBlocked, '^REQUEST_OBSERVATION_LIMIT$'):
            await self.recorder.capture(request(body), body)
        self.assertEqual(self.recorder.ordinal, 0)
        self.assertEqual(self.request_files(), [])

    async def test_uncertain_write_consumes_sequence_and_poisoned_recorder_cannot_retry(self):
        persist = self.supervisor._persist

        def uncertain(name, raw):
            persist(name, raw)
            if name.startswith('request-'):
                raise OSError('synthetic uncertain fsync')

        with patch.object(self.supervisor, '_persist', side_effect=uncertain):
            with self.assertRaisesRegex(OSError, 'uncertain fsync'):
                await self.recorder.capture(request(BODY), BODY)
        self.assertEqual(self.recorder.ordinal, 1)
        self.assertGreater(self.recorder.total, 0)
        self.assertEqual(self.recorder.records, {})
        self.assertEqual(len(self.request_files()), 1)
        self.assertEqual(self.supervisor.state, 'UNKNOWN')
        with self.assertRaisesRegex(RuntimeBlocked, 'REQUEST_OBSERVATION_RECOVERY_REQUIRED'):
            await self.recorder.capture(request(BODY), BODY)
        self.assertEqual(len(self.request_files()), 1)

    async def test_lost_current_evidence_after_persist_does_not_publish_reference(self):
        original = self.supervisor.current
        count = 0

        def lose_after_write():
            nonlocal count
            count += 1
            if count == 2:
                raise RuntimeBlocked('SUPERVISION_LOST_AFTER_WRITE')
            return original()

        with patch.object(self.supervisor, 'current', side_effect=lose_after_write):
            with self.assertRaisesRegex(RuntimeBlocked, 'SUPERVISION_LOST_AFTER_WRITE'):
                await self.recorder.capture(request(BODY), BODY)
        self.assertEqual(self.recorder.ordinal, 1)
        self.assertEqual(self.recorder.records, {})
        self.assertEqual(self.supervisor.state, 'UNKNOWN')

    async def test_record_readback_detects_file_mutation(self):
        reference = await self.recorder.capture(request(BODY), BODY)
        self.request_files()[0].write_bytes(BODY)
        with self.assertRaisesRegex(RuntimeBlocked, 'FILE_HASH_MISMATCH'):
            self.recorder.read(reference['request_id'])

    async def test_record_readback_is_detached_and_unknown_ids_are_not_path_authority(self):
        reference = await self.recorder.capture(request(BODY), BODY)
        evidence = self.recorder.read(reference['request_id'])
        evidence['binding']['lease_generation'] = 999
        self.assertEqual(self.recorder.read(reference['request_id'])['binding']['lease_generation'], 1)
        for request_id in (str(uuid4()), '../admitted', str(self.supervisor.root / 'admitted')):
            with self.subTest(request_id=request_id):
                with self.assertRaisesRegex(RuntimeBlocked, 'SDK_REQUEST_ORIGINAL_MISSING'):
                    self.recorder.read(request_id)

    async def test_record_symlink_substitution_is_never_followed(self):
        reference = await self.recorder.capture(request(BODY), BODY)
        path = self.request_files()[0]
        outside = Path(self.temporary.name) / 'substitute'
        outside.write_bytes(path.read_bytes())
        path.unlink()
        path.symlink_to(outside)
        with self.assertRaises(OSError):
            self.recorder.read(reference['request_id'])

    async def test_live_trace_failure_blocks_request_without_new_record(self):
        self.fixture.process.trace.failure = RuntimeBlocked('TRACE_NOT_CURRENT')
        with self.assertRaisesRegex(RuntimeBlocked, 'TRACE_NOT_CURRENT'):
            await self.recorder.capture(request(BODY), BODY)
        self.assertEqual(self.request_files(), [])
        self.assertEqual(self.supervisor.state, 'UNKNOWN')

    async def test_closed_supervision_cannot_serve_live_request_evidence(self):
        reference = await self.recorder.capture(request(BODY), BODY)
        self.supervisor.release()
        await self.supervisor.finish()
        with self.assertRaisesRegex(RuntimeBlocked, 'SUPERVISION_NOT_CURRENT'):
            self.recorder.read(reference['request_id'])

    def test_invalid_limits_rejected_without_allocating_journal(self):
        for limits in ({'maximum_request_bytes': 0}, {'maximum_requests': 0},
                       {'maximum_requests': 10001}, {'maximum_retained_bytes': 1},
                       {'maximum_retained_bytes': 67108865}):
            with self.subTest(limits=limits):
                with self.assertRaisesRegex(RuntimeBlocked, 'REQUEST_OBSERVATION_LIMIT_INVALID'):
                    SupervisorRequestRecorder(self.supervisor, **limits)

    def test_packaged_schema_hash_mismatch_is_fail_closed(self):
        package = SimpleNamespace(joinpath=lambda path: SimpleNamespace(read_bytes=lambda: b'{}'))
        with patch('medical_professional_runtime.request_observation.files', return_value=package):
            with self.assertRaisesRegex(RuntimeBlocked, 'SDK_PROJECTION_SCHEMA_MISMATCH'):
                SupervisorRequestRecorder(self.supervisor)


if __name__ == '__main__':
    unittest.main()
