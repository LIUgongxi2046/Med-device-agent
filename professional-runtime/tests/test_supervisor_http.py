"""Exact TLS, admission and relay protocol tests using explicit in-memory doubles.

No test opens a listener/socket, starts a process, reads kernel process state, or
claims production isolation. Only request/response objects and trusted service
wiring are exercised; the supervisor and bridge are never instantiated here.
"""
import asyncio
import base64
from contextlib import asynccontextmanager
from copy import deepcopy
from datetime import datetime, timedelta, timezone
from pathlib import Path
import ssl
from types import SimpleNamespace
import unittest
from unittest.mock import AsyncMock, patch

from aiohttp import web
from medical_professional_runtime.host import Binding, RuntimeBlocked, digest, strict_json
from medical_professional_runtime.supervisor import canonical
from medical_professional_runtime.supervisor_plan import BackendPlanLoader
from medical_professional_runtime.supervisor_http import (
    BackendAdmissionReader, HostRelayService, HttpHostRelay, HttpSupervisorClient,
    PinnedHttps, RemoteSession, SupervisorHttpService, _PinnedConnector, _PinnedResponse,
)


MODULE = 'medical_professional_runtime.supervisor_http'
SESSION = '00000000-0000-4000-8000-000000000001'
RECEIPT = '00000000-0000-4000-8000-000000000002'
OTHER = '00000000-0000-4000-8000-000000000003'
BINDING = Binding(
    '00000000-0000-4000-8000-000000000004',
    '00000000-0000-4000-8000-000000000005',
    '00000000-0000-4000-8000-000000000006',
    '00000000-0000-4000-8000-000000000007', 3, 'a' * 64, 'b' * 64)
CERT = b'exact-supervisor-leaf-DER-fixture'
WORKER_CERT = b'exact-worker-leaf-DER-fixture'
BACKEND_CERT = b'exact-backend-leaf-DER-fixture'
NOW = datetime(2026, 10, 20, 2, 0, tzinfo=timezone.utc)


class FixedClock(datetime):
    @classmethod
    def now(cls, tz=None):
        return NOW if tz else NOW.replace(tzinfo=None)


class TlsDouble:
    def __init__(self, certificate=CERT):
        self.certificate = certificate
        self.calls = []

    def getpeercert(self, binary_form=False):
        self.calls.append(binary_form)
        return self.certificate


class TransportDouble:
    def __init__(self, tls):
        self.tls = tls
        self.calls = []
        self.closed = False

    def get_extra_info(self, name):
        self.calls.append(name)
        return self.tls if name == 'ssl_object' else None

    def close(self):
        self.closed = True


class ContentDouble:
    def __init__(self, chunks=()):
        self.chunks = list(chunks)
        self.read_sizes = []
        self.yielded = []

    async def iter_chunked(self, size):
        self.read_sizes.append(size)
        for chunk in self.chunks:
            self.yielded.append(chunk)
            yield chunk


class ResponseDouble:
    def __init__(self, chunks=(b'{}',), status=200, certificate=CERT,
                 content_type='application/json'):
        self.status = status
        self.content = ContentDouble(chunks)
        self.headers = {'Content-Type': content_type}
        self.tls = TlsDouble(certificate)
        self.medical_peer_sha256 = digest(certificate)
        self.connection = SimpleNamespace(transport=TransportDouble(self.tls))
        self.entered = self.exited = 0

    async def __aenter__(self):
        self.entered += 1
        return self

    async def __aexit__(self, *_):
        self.exited += 1


class SessionDouble:
    """Synchronous context-manager factory, matching aiohttp.ClientSession."""
    def __init__(self, response=None):
        self.response = response or ResponseDouble()
        self.calls = []
        self.closed = False

    def request(self, method, url, **kwargs):
        self.calls.append((method, url, kwargs))
        return self.response

    def post(self, url, **kwargs):
        return self.request('POST', url, **kwargs)

    async def close(self):
        self.closed = True


class ProtocolTransportDouble:
    """Already pinned channel: bytes only, no client-side process authority."""
    def __init__(self, reply=None, callback=None):
        self.reply = reply
        self.callback = callback
        self.calls = []

    async def request(self, method, path, body=None):
        self.calls.append((method, path, deepcopy(body)))
        result = self.callback(body) if self.callback else self.reply
        if isinstance(result, Exception):
            raise result
        return result if isinstance(result, bytes) else canonical(result)


class RequestDouble:
    def __init__(self, value=None, *, raw=None, certificate=CERT, method='POST',
                 operation='release', session=SESSION, headers=None, query=''):
        self.raw = canonical(value) if raw is None else raw
        self.transport = TransportDouble(TlsDouble(certificate))
        self.method = method
        self.match_info = {'session': session, 'operation': operation}
        self.headers = headers or {}
        self.query_string = query
        self.read_count = 0

    async def read(self):
        self.read_count += 1
        return self.raw


class StreamResponseDouble:
    instances = []

    def __init__(self, *, headers):
        self.headers = headers
        self.request = None
        self.chunks = []
        self.eof = False
        self.instances.append(self)

    async def prepare(self, request):
        self.request = request

    async def write(self, chunk):
        self.chunks.append(chunk)

    async def write_eof(self):
        self.eof = True


class RelayControlDouble:
    def __init__(self, response=None):
        self.response = response or ResponseDouble()
        self.calls = []
        self.exited = False

    @asynccontextmanager
    async def forward(self, *args):
        self.calls.append(deepcopy(args))
        try:
            yield self.response
        finally:
            self.exited = True


def verified_tls():
    return SimpleNamespace(verify_mode=ssl.CERT_REQUIRED, check_hostname=True)


def pinned(response=None, maximum=1024):
    transport = PinnedHttps('https://supervisor.example:8443/', verified_tls(),
                            digest(CERT), maximum)
    transport.client = SessionDouble(response)
    return transport


def supervisor_double(events=None):
    events = events if events is not None else []
    snapshot = {'processes': [{'pid': 321, 'start_ticks': 654, 'exe_sha256': 'c' * 64}],
                'boot_id': OTHER, 'cgroup_inode': 19}
    value = SimpleNamespace(
        plan=SimpleNamespace(binding=BINDING.wire(), session_id=SESSION,
                             payload=b'trusted supervisor payload', job=Path('/trusted/job')),
        admitted={'receipt_id': RECEIPT, 'session_id': SESSION, 'binding': BINDING.wire()},
        snapshot=SimpleNamespace(wire=lambda: deepcopy(snapshot)),
        instance=OTHER, state='ADMITTED', snapshot_value=snapshot)

    def release():
        events.append('release')
        value.state = 'RUNNING'
    value.release = release
    value.current = lambda: deepcopy(value.admitted)
    return value


def approved_reply(body):
    return dict(body, admission='CURRENT', expires_at=(NOW + timedelta(seconds=3)).isoformat())


class PinnedHttpsTest(unittest.IsolatedAsyncioTestCase):
    def test_requires_fixed_https_origin_without_userinfo_paths_or_queries(self):
        for origin in ('http://example.test', 'https://', 'https://user@example.test',
                       'https://user:pass@example.test', 'https://example.test/path',
                       'https://example.test/?query=yes', 'https://example.test/#fragment',
                       '//example.test'):
            with self.subTest(origin=origin), self.assertRaisesRegex(
                    RuntimeBlocked, 'FIXED_SUPERVISOR_TLS_REQUIRED'):
                PinnedHttps(origin, verified_tls(), digest(CERT))

    def test_requires_certificate_verification_and_hostname_checking(self):
        for tls in (SimpleNamespace(verify_mode=ssl.CERT_NONE, check_hostname=False),
                    SimpleNamespace(verify_mode=ssl.CERT_OPTIONAL, check_hostname=True),
                    SimpleNamespace(verify_mode=ssl.CERT_REQUIRED, check_hostname=False)):
            with self.subTest(tls=tls), self.assertRaisesRegex(
                    RuntimeBlocked, 'VERIFIED_SUPERVISOR_TLS_REQUIRED'):
                PinnedHttps('https://example.test', tls, digest(CERT))

    def test_rejects_invalid_pin_and_limits(self):
        for pin in ('', 'f' * 63, 'A' * 64, 'g' * 64):
            with self.subTest(pin=pin), self.assertRaises(RuntimeBlocked):
                PinnedHttps('https://example.test', verified_tls(), pin)
        for maximum in (0, -1, 67108865):
            with self.subTest(maximum=maximum), self.assertRaisesRegex(
                    RuntimeBlocked, 'SUPERVISOR_LIMIT_INVALID'):
                PinnedHttps('https://example.test', verified_tls(), digest(CERT), maximum)

    async def test_session_uses_operator_tls_no_environment_proxies_and_bounded_timeouts(self):
        transport = PinnedHttps('https://supervisor.example/', verified_tls(), digest(CERT))
        session = SessionDouble()
        connector = object()
        with patch(MODULE + '._PinnedConnector', return_value=connector) as connector_factory, \
             patch(MODULE + '.aiohttp.ClientSession', return_value=session) as session_factory:
            async with transport as entered:
                self.assertIs(entered, transport)
                self.assertIs(transport.client, session)
            connector_factory.assert_called_once_with(ssl=transport.tls, peer_sha256=transport.pin)
            kwargs = session_factory.call_args.kwargs
            self.assertIs(kwargs['connector'], connector)
            self.assertIs(kwargs['response_class'], _PinnedResponse)
            self.assertIs(kwargs['trust_env'], False)
            self.assertEqual((kwargs['timeout'].total, kwargs['timeout'].connect,
                              kwargs['timeout'].sock_read), (300, 5, 300))
            self.assertTrue(session.closed)

    async def test_unstarted_transport_never_attempts_request(self):
        transport = PinnedHttps('https://example.test', verified_tls(), digest(CERT))
        with self.assertRaisesRegex(RuntimeBlocked, 'SUPERVISOR_TRANSPORT_NOT_STARTED'):
            await transport.request('GET', '/fixed')

    async def test_exact_bytes_pin_canonical_request_and_redirects_disabled(self):
        response = ResponseDouble((b'{', b'"ok":true', b'}'))
        transport = pinned(response)
        body = {'z': 2, 'a': 'original'}
        self.assertEqual(await transport.request('POST', '/fixed', body), b'{"ok":true}')
        self.assertEqual(transport.client.calls, [('POST', 'https://supervisor.example:8443/fixed',
            {'data': canonical(body), 'headers': {'Content-Type': 'application/json'},
             'allow_redirects': False})])
        self.assertEqual(response.medical_peer_sha256, digest(CERT))
        self.assertEqual(response.content.read_sizes, [65536])
        self.assertEqual((response.entered, response.exited), (1, 1))

    async def test_request_limit_is_checked_before_network_double(self):
        body = {'v': 'x' * 10}
        raw = canonical(body)
        transport = pinned(maximum=len(raw) - 1)
        with self.assertRaisesRegex(RuntimeBlocked, 'SUPERVISOR_REQUEST_LIMIT'):
            await transport.request('POST', '/fixed', body)
        self.assertEqual(transport.client.calls, [])
        transport.maximum = len(raw)
        await transport.request('POST', '/fixed', body)
        self.assertEqual(transport.client.calls[0][2]['data'], raw)

    async def test_no_body_preserves_none(self):
        transport = pinned()
        await transport.request('GET', '/fixed')
        self.assertIsNone(transport.client.calls[0][2]['data'])

    async def test_wrong_certificate_blocks_before_status_or_body_consumption(self):
        response = ResponseDouble((b'not trusted',), status=500, certificate=b'another leaf')
        with self.assertRaisesRegex(RuntimeBlocked, 'SUPERVISOR_CERTIFICATE_MISMATCH'):
            await pinned(response).request('GET', '/fixed')
        self.assertEqual(response.content.yielded, [])
        self.assertEqual(response.exited, 1)

    async def test_missing_retained_peer_never_trusts_response_header_claim(self):
        response = ResponseDouble()
        del response.medical_peer_sha256
        response.headers['X-Medical-Peer-Sha256'] = digest(CERT)
        with self.assertRaisesRegex(RuntimeBlocked, 'SUPERVISOR_CERTIFICATE_MISMATCH'):
            await pinned(response).request('GET', '/fixed')
        self.assertEqual(response.content.yielded, [])

    async def test_early_released_connection_keeps_verified_peer_evidence(self):
        response = ResponseDouble()
        response.connection = None
        self.assertEqual(await pinned(response).request('GET', '/fixed'), b'{}')

    async def test_non_200_status_rejected_without_consuming_payload(self):
        for status in (201, 204, 301, 302, 307, 400, 401, 500):
            response = ResponseDouble(status=status)
            with self.subTest(status=status), self.assertRaisesRegex(RuntimeBlocked, 'SUPERVISOR_REJECTED'):
                await pinned(response).request('GET', '/fixed')
            self.assertEqual(response.content.yielded, [])

    async def test_response_limit_is_incremental_and_exact_boundary_allowed(self):
        response = ResponseDouble((b'ab', b'cd', b'e', b'never-read'))
        with self.assertRaisesRegex(RuntimeBlocked, 'SUPERVISOR_RESPONSE_LIMIT'):
            await pinned(response, maximum=4).request('GET', '/fixed')
        self.assertEqual(response.content.yielded, [b'ab', b'cd', b'e'])
        self.assertEqual(response.exited, 1)
        self.assertEqual(await pinned(ResponseDouble((b'ab', b'cd')), 4).request('GET', '/fixed'), b'abcd')


class PinnedAiohttpHookTest(unittest.IsolatedAsyncioTestCase):
    def connector(self):
        # Bypass resource allocation entirely. Only the production hook is called.
        connector = object.__new__(_PinnedConnector)
        connector.medical_peer_sha256 = digest(CERT)
        return connector

    async def test_connector_verifies_actual_tls_before_returning_usable_transport(self):
        transport = TransportDouble(TlsDouble())
        protocol = object()
        base = AsyncMock(return_value=(transport, protocol))
        with patch(MODULE + '.aiohttp.TCPConnector._wrap_create_connection', base):
            result = await self.connector()._wrap_create_connection('factory', timeout='bounded')
        self.assertEqual(result, (transport, protocol))
        base.assert_awaited_once_with('factory', timeout='bounded')
        self.assertEqual(transport.calls, ['ssl_object'])
        self.assertEqual(transport.tls.calls, [True])
        self.assertFalse(transport.closed)

    async def test_connector_closes_untrusted_transport_before_http_can_use_it(self):
        for tls in (None, TlsDouble(b''), TlsDouble(b'wrong-leaf')):
            transport = TransportDouble(tls)
            with patch(MODULE + '.aiohttp.TCPConnector._wrap_create_connection',
                       AsyncMock(return_value=(transport, object()))):
                with self.subTest(tls=tls), self.assertRaisesRegex(RuntimeBlocked, 'SUPERVISOR_CERTIFICATE_MISMATCH'):
                    await self.connector()._wrap_create_connection()
            self.assertTrue(transport.closed)

    async def test_connector_also_closes_on_certificate_inspection_failure(self):
        tls = TlsDouble()
        tls.getpeercert = lambda **_: (_ for _ in ()).throw(ValueError('invalid peer certificate'))
        transport = TransportDouble(tls)
        with patch(MODULE + '.aiohttp.TCPConnector._wrap_create_connection',
                   AsyncMock(return_value=(transport, object()))):
            with self.assertRaisesRegex(ValueError, 'invalid peer certificate'):
                await self.connector()._wrap_create_connection()
        self.assertTrue(transport.closed)

    async def test_failed_ca_or_hostname_handshake_is_not_bypassed(self):
        base = AsyncMock(side_effect=ssl.SSLCertVerificationError('certificate verify failed'))
        with patch(MODULE + '.aiohttp.TCPConnector._wrap_create_connection', base):
            with self.assertRaises(ssl.SSLCertVerificationError):
                await self.connector()._wrap_create_connection()
        base.assert_awaited_once()

    def test_connector_constructor_preserves_operator_context_and_validates_pin(self):
        tls = verified_tls()
        with patch(MODULE + '.aiohttp.TCPConnector.__init__', return_value=None) as initialize:
            connector = _PinnedConnector(peer_sha256=digest(CERT), ssl=tls)
            self.assertEqual(connector.medical_peer_sha256, digest(CERT))
            initialize.assert_called_once_with(ssl=tls)
        with patch(MODULE + '.aiohttp.TCPConnector.__init__', return_value=None) as initialize:
            with self.assertRaisesRegex(RuntimeBlocked, 'INVALID_HASH'):
                _PinnedConnector(peer_sha256='wrong', ssl=tls)
            initialize.assert_not_called()

    async def test_response_captures_transport_peer_before_super_start_releases_connection(self):
        response = object.__new__(_PinnedResponse)
        tls = TlsDouble()
        connection = SimpleNamespace(transport=TransportDouble(tls))
        seen = []

        async def complete_start(actual_connection):
            seen.append((response.medical_peer_sha256, actual_connection))
            response._connection = None  # What aiohttp does for a completed small body.
            return response
        with patch(MODULE + '.aiohttp.ClientResponse.start', AsyncMock(side_effect=complete_start)) as base:
            self.assertIs(await response.start(connection), response)
        base.assert_awaited_once_with(connection)
        self.assertEqual(seen, [(digest(CERT), connection)])
        self.assertEqual(tls.calls, [True])
        self.assertIsNone(response.connection)
        self.assertEqual(response.medical_peer_sha256, digest(CERT))

    async def test_response_rejects_missing_tls_or_empty_certificate_before_super_start(self):
        for transport in (None, TransportDouble(None), TransportDouble(TlsDouble(b''))):
            response = object.__new__(_PinnedResponse)
            with patch(MODULE + '.aiohttp.ClientResponse.start', AsyncMock()) as base:
                with self.subTest(transport=transport), self.assertRaisesRegex(RuntimeBlocked, 'SUPERVISOR_CERTIFICATE_MISMATCH'):
                    await response.start(SimpleNamespace(transport=transport))
                base.assert_not_awaited()


class HttpSupervisorClientTest(unittest.IsolatedAsyncioTestCase):
    def setUp(self):
        self.payload = b'operator-pinned payload'
        self.job = Path('/trusted/job')
        self.receipt = {'receipt_id': RECEIPT, 'binding': BINDING.wire(),
                        'session_id': SESSION, 'processes': [{'pid': 321, 'start_ticks': 654}]}
        self.remote = RemoteSession(BINDING, SESSION, RECEIPT, digest(canonical(self.receipt)))

    def plan_reply(self):
        return {'session_id': SESSION, 'binding': BINDING.wire(),
                'payload_sha256': digest(self.payload), 'job_directory': str(self.job),
                'receipt_id': RECEIPT, 'receipt_sha256': self.remote.receipt_sha256}

    def bridge_reply(self):
        return {'session_id': SESSION, 'binding': BINDING.wire(), 'socket': '/trusted/private.sock',
                'token_path': '/trusted/token', 'inventory_path': '/trusted/inventory.json'}

    async def test_prepare_sends_binding_only_never_payload_environment_job_or_pid(self):
        transport = ProtocolTransportDouble(self.plan_reply())
        remote = await HttpSupervisorClient(transport).prepare(
            BINDING, SESSION, self.job, self.payload,
            {'UNTRUSTED_PID': '321', 'PATH': '/worker/override', 'SECRET': 'not-for-supervisor'})
        self.assertEqual(remote, self.remote)
        self.assertEqual(transport.calls, [('POST', '/internal/v1/supervision/' + SESSION + '/prepare',
                                            {'binding': BINDING.wire()})])

    async def test_prepare_checks_exact_plan_and_rejects_worker_process_claim_fields(self):
        mutations = ({'session_id': OTHER}, {'binding': dict(BINDING.wire(), lease_generation=4)},
                     {'payload_sha256': 'f' * 64}, {'job_directory': '/worker/job'},
                     {'pid': 321}, {'process_exited': True}, {'payload': 'replacement'})
        for mutation in mutations:
            reply = self.plan_reply() | mutation
            with self.subTest(mutation=mutation), self.assertRaisesRegex(RuntimeBlocked, 'SUPERVISOR_PLAN_MISMATCH'):
                await HttpSupervisorClient(ProtocolTransportDouble(reply)).prepare(
                    BINDING, SESSION, self.job, self.payload, {})

    async def test_prepare_validates_receipt_identifiers_and_digest(self):
        for mutation in ({'receipt_id': 'not-a-uuid'}, {'receipt_sha256': 'not-a-digest'}):
            with self.subTest(mutation=mutation), self.assertRaises((RuntimeBlocked, ValueError)):
                await HttpSupervisorClient(ProtocolTransportDouble(self.plan_reply() | mutation)).prepare(
                    BINDING, SESSION, self.job, self.payload, {})

    async def test_bridge_uses_binding_only_and_keeps_supervisor_owned_paths(self):
        transport = ProtocolTransportDouble(self.bridge_reply())
        bridge = await HttpSupervisorClient(transport).open_bridge(BINDING, SESSION, self.job)
        self.assertEqual((bridge.socket, bridge.token_path, bridge.inventory_path),
                         (Path('/trusted/private.sock'), Path('/trusted/token'), Path('/trusted/inventory.json')))
        self.assertEqual(transport.calls[0][2], {'binding': BINDING.wire()})
        self.assertIsNone(bridge.guard)

    async def test_bridge_rejects_relative_or_parent_traversal_paths(self):
        for field in ('socket', 'token_path', 'inventory_path'):
            for path in ('relative', '/trusted/../outside'):
                with self.subTest(field=field, path=path), self.assertRaisesRegex(
                        RuntimeBlocked, 'SUPERVISOR_BRIDGE_PATH_INVALID'):
                    await HttpSupervisorClient(ProtocolTransportDouble(self.bridge_reply() | {field: path})).open_bridge(
                        BINDING, SESSION, self.job)

    async def test_bridge_rejects_changed_scope_and_extra_claims(self):
        for mutation in ({'session_id': OTHER}, {'binding': {}}, {'pid': 321}):
            with self.subTest(mutation=mutation), self.assertRaisesRegex(RuntimeBlocked, 'SUPERVISOR_BRIDGE_MISMATCH'):
                await HttpSupervisorClient(ProtocolTransportDouble(self.bridge_reply() | mutation)).open_bridge(
                    BINDING, SESSION, self.job)

    async def test_current_requires_original_complete_receipt_digest(self):
        transport = ProtocolTransportDouble(self.receipt)
        await HttpSupervisorClient(transport).current(self.remote)
        self.assertEqual(transport.calls[0][2], {'binding': BINDING.wire()})
        for mutation in ({'session_id': OTHER}, {'receipt_id': OTHER}, {'binding': {}},
                         {'processes': [{'pid': 321, 'start_ticks': 655}]}, {'process_exited': True}):
            with self.subTest(mutation=mutation), self.assertRaisesRegex(RuntimeBlocked, 'SUPERVISION_CHANGED'):
                await HttpSupervisorClient(ProtocolTransportDouble(self.receipt | mutation)).current(self.remote)

    async def test_release_requires_matching_admission_before_request(self):
        transport = ProtocolTransportDouble({'session_id': SESSION, 'state': 'RUNNING'})
        client = HttpSupervisorClient(transport)
        admission = {'isolation_receipt_id': RECEIPT, 'supervision_sha256': self.remote.receipt_sha256}
        for mutation in ({'isolation_receipt_id': OTHER}, {'supervision_sha256': 'f' * 64}):
            with self.subTest(mutation=mutation), self.assertRaisesRegex(RuntimeBlocked, 'ADMISSION_RECEIPT_MISMATCH'):
                await client.release(self.remote, admission | mutation)
        self.assertEqual(transport.calls, [])
        await client.release(self.remote, admission)
        self.assertEqual(transport.calls[0][2], {'binding': BINDING.wire()})

    async def test_release_requires_exact_running_reply_not_exit_or_pid_claims(self):
        admission = {'isolation_receipt_id': RECEIPT, 'supervision_sha256': self.remote.receipt_sha256}
        for reply in ({'session_id': OTHER, 'state': 'RUNNING'},
                      {'session_id': SESSION, 'state': 'ADMITTED'},
                      {'session_id': SESSION, 'state': 'RUNNING', 'pid': 321}):
            with self.subTest(reply=reply), self.assertRaisesRegex(RuntimeBlocked, 'SUPERVISOR_GATE_NOT_RELEASED'):
                await HttpSupervisorClient(ProtocolTransportDouble(reply)).release(self.remote, admission)

    async def test_wait_returns_original_bytes_without_trusting_exit_claims(self):
        raw = b'{"process_exited":true, "pid":321, "status":"completed"}'
        transport = ProtocolTransportDouble(raw)
        self.assertEqual(await HttpSupervisorClient(transport).wait(self.remote), raw)
        self.assertEqual(transport.calls[0][2], {'binding': BINDING.wire()})

    async def test_abort_only_accepts_closed_or_explicit_unknown(self):
        for state in ('CLOSED', 'UNKNOWN'):
            await HttpSupervisorClient(ProtocolTransportDouble({'session_id': SESSION, 'state': state})).abort(self.remote)
        for reply in ({'session_id': SESSION, 'state': 'RUNNING'},
                      {'session_id': OTHER, 'state': 'CLOSED'},
                      {'session_id': SESSION, 'state': 'CLOSED', 'process_exited': True}, []):
            with self.subTest(reply=reply), self.assertRaisesRegex(RuntimeBlocked, 'SUPERVISOR_ABORT_UNCONFIRMED'):
                await HttpSupervisorClient(ProtocolTransportDouble(reply)).abort(self.remote)

    async def test_bridge_close_requires_exact_confirmation(self):
        transport = ProtocolTransportDouble(self.bridge_reply())
        bridge = await HttpSupervisorClient(transport).open_bridge(BINDING, SESSION, self.job)
        transport.reply = {'session_id': SESSION, 'bridge': 'CLOSED'}
        await bridge.close()
        self.assertEqual(transport.calls[-1], ('POST', '/internal/v1/supervision/' + SESSION + '/close-bridge',
                                             {'binding': BINDING.wire()}))
        transport.reply = {'session_id': SESSION, 'bridge': 'CLOSED', 'pid': 321}
        with self.assertRaisesRegex(RuntimeBlocked, 'SUPERVISOR_BRIDGE_CLOSURE_UNKNOWN'):
            await bridge.close()

    async def test_duplicate_reply_json_keys_are_never_accepted(self):
        transport = ProtocolTransportDouble(b'{"session_id":"a","session_id":"b"}')
        with self.assertRaisesRegex(RuntimeBlocked, 'DUPLICATE_JSON_KEY'):
            await HttpSupervisorClient(transport).prepare(BINDING, SESSION, self.job, self.payload, {})


class BackendAdmissionReaderTest(unittest.IsolatedAsyncioTestCase):
    def setUp(self):
        self.clock = patch(MODULE + '.datetime', FixedClock)
        self.clock.start()
        self.addCleanup(self.clock.stop)
        self.supervisor = supervisor_double()

    async def test_admission_binds_original_receipt_and_process_pid_start_snapshot(self):
        transport = ProtocolTransportDouble(callback=approved_reply)
        await BackendAdmissionReader(transport).require(self.supervisor)
        self.assertEqual(transport.calls, [('POST', '/internal/v1/professional-runtime/supervisor-admission',
            {'binding': BINDING.wire(), 'session_id': SESSION, 'isolation_receipt_id': RECEIPT,
             'supervision_sha256': digest(canonical(self.supervisor.admitted)),
             'process_unit_sha256': digest(canonical(self.supervisor.snapshot.wire())),
             'supervisor_instance_id': OTHER})])

    async def test_same_pid_with_changed_start_ticks_requires_new_backend_process_hash(self):
        first = ProtocolTransportDouble(callback=approved_reply)
        await BackendAdmissionReader(first).require(self.supervisor)
        old_reply = approved_reply(first.calls[0][2])
        self.supervisor.snapshot_value['processes'][0]['start_ticks'] += 1
        transport = ProtocolTransportDouble(old_reply)
        with self.assertRaisesRegex(RuntimeBlocked, 'ORIGINAL_BACKEND_ADMISSION_REQUIRED'):
            await BackendAdmissionReader(transport).require(self.supervisor)
        self.assertNotEqual(first.calls[0][2]['process_unit_sha256'], transport.calls[0][2]['process_unit_sha256'])

    async def test_each_scope_field_must_match_exactly(self):
        changes = {'binding': dict(BINDING.wire(), lease_generation=4), 'session_id': OTHER,
                   'isolation_receipt_id': OTHER, 'supervision_sha256': 'f' * 64,
                   'process_unit_sha256': 'e' * 64, 'supervisor_instance_id': SESSION,
                   'admission': 'REVOKED'}
        for key, wrong in changes.items():
            transport = ProtocolTransportDouble(callback=lambda body, key=key, wrong=wrong:
                                                  approved_reply(body) | {key: wrong})
            with self.subTest(field=key), self.assertRaisesRegex(RuntimeBlocked, 'ORIGINAL_BACKEND_ADMISSION_REQUIRED'):
                await BackendAdmissionReader(transport).require(self.supervisor)

    async def test_reply_has_exact_shape_and_no_extra_worker_authority(self):
        callbacks = (lambda body: approved_reply(body) | {'pid': 321},
                     lambda body: {k: v for k, v in approved_reply(body).items() if k != 'expires_at'},
                     lambda body: [], lambda body: True)
        for callback in callbacks:
            with self.subTest(callback=callback), self.assertRaisesRegex(RuntimeBlocked, 'ORIGINAL_BACKEND_ADMISSION_REQUIRED'):
                await BackendAdmissionReader(ProtocolTransportDouble(callback=callback)).require(self.supervisor)

    async def test_admission_validity_is_strictly_positive_and_at_most_five_seconds(self):
        for seconds in (-1, 0, 5.000001, 300):
            callback = lambda body, seconds=seconds: approved_reply(body) | {
                'expires_at': (NOW + timedelta(seconds=seconds)).isoformat()}
            with self.subTest(seconds=seconds), self.assertRaisesRegex(RuntimeBlocked, 'BACKEND_ADMISSION_EXPIRED'):
                await BackendAdmissionReader(ProtocolTransportDouble(callback=callback)).require(self.supervisor)
        for seconds in (.000001, 5):
            callback = lambda body, seconds=seconds: approved_reply(body) | {
                'expires_at': (NOW + timedelta(seconds=seconds)).isoformat()}
            await BackendAdmissionReader(ProtocolTransportDouble(callback=callback)).require(self.supervisor)

    async def test_timezone_is_required_and_utc_z_is_supported(self):
        callback = lambda body: approved_reply(body) | {'expires_at': '2026-10-20T02:00:03'}
        with self.assertRaisesRegex(RuntimeBlocked, 'BACKEND_ADMISSION_EXPIRED'):
            await BackendAdmissionReader(ProtocolTransportDouble(callback=callback)).require(self.supervisor)
        callback = lambda body: approved_reply(body) | {'expires_at': '2026-10-20T02:00:03Z'}
        await BackendAdmissionReader(ProtocolTransportDouble(callback=callback)).require(self.supervisor)

    async def test_malformed_expiry_is_controlled_admission_rejection(self):
        for expires in ('invalid', '', None, 3, True, [], {}, '2026-99-20T02:00:03Z'):
            callback = lambda body, expires=expires: approved_reply(body) | {'expires_at': expires}
            with self.subTest(expires=expires), self.assertRaisesRegex(RuntimeBlocked, 'BACKEND_ADMISSION_EXPIRED'):
                await BackendAdmissionReader(ProtocolTransportDouble(callback=callback)).require(self.supervisor)

    async def test_transport_denial_is_not_an_admission_fallback(self):
        transport = ProtocolTransportDouble(RuntimeBlocked('SUPERVISOR_REJECTED'))
        with self.assertRaisesRegex(RuntimeBlocked, 'SUPERVISOR_REJECTED'):
            await BackendAdmissionReader(transport).require(self.supervisor)


class SupervisorHttpServiceTest(unittest.IsolatedAsyncioTestCase):
    def setUp(self):
        self.clock = patch(MODULE + '.datetime', FixedClock)
        self.clock.start()
        self.addCleanup(self.clock.stop)
        self.events = []
        self.supervisor = supervisor_double(self.events)

        def approve(body):
            self.events.append('independent-backend-admission')
            return approved_reply(body)
        self.transport = ProtocolTransportDouble(callback=approve)
        self.admission = BackendAdmissionReader(self.transport)
        # Explicit in-memory loader double with the trusted concrete wiring type.
        # No slots, files, inspection, or loader I/O are initialized or exercised.
        self.loader = object.__new__(BackendPlanLoader)
        self.loader.require_current = AsyncMock(side_effect=lambda plan: self.events.append('fresh-backend-plan'))
        self.service = SupervisorHttpService({SESSION: self.supervisor.plan}, Path('/unused/receipts'),
            digest(WORKER_CERT), digest(BACKEND_CERT), self.admission, loader=self.loader)
        self.service.sessions[SESSION] = self.supervisor

    def incoming(self, operation='release', **kwargs):
        kwargs.setdefault('certificate', WORKER_CERT)
        return RequestDouble({'binding': BINDING.wire()}, operation=operation, **kwargs)

    def test_requires_exact_reader_type_and_distinct_worker_backend_certificates(self):
        with self.assertRaisesRegex(RuntimeBlocked, 'INDEPENDENT_ADMISSION_READER_REQUIRED'):
            SupervisorHttpService({}, Path('/unused'), digest(WORKER_CERT), digest(BACKEND_CERT), object())
        with self.assertRaisesRegex(RuntimeBlocked, 'DISTINCT_CONTROL_IDENTITY_REQUIRED'):
            SupervisorHttpService({}, Path('/unused'), digest(WORKER_CERT), digest(WORKER_CERT), self.admission)
        with self.assertRaisesRegex(RuntimeBlocked, 'SUPERVISOR_PLAN_MISMATCH'):
            SupervisorHttpService({OTHER: self.supervisor.plan}, Path('/unused'),
                                  digest(WORKER_CERT), digest(BACKEND_CERT), self.admission)

    async def test_release_queries_backend_before_opening_process_gate(self):
        response = await self.service.handle(self.incoming())
        self.assertEqual(self.events, ['independent-backend-admission', 'release'])
        self.assertEqual(strict_json(response.body), {'session_id': SESSION, 'state': 'RUNNING'})
        self.assertEqual(response.headers['Cache-Control'], 'no-store')

    async def test_rejected_backend_admission_never_releases(self):
        self.transport.callback = lambda body: approved_reply(body) | {'admission': 'REVOKED'}
        with self.assertRaises(web.HTTPConflict) as caught:
            await self.service.handle(self.incoming())
        self.assertEqual(caught.exception.text, 'SUPERVISION_UNAVAILABLE')
        self.assertNotIn('release', self.events)
        self.assertEqual(self.supervisor.state, 'ADMITTED')

    async def test_expired_backend_admission_never_releases(self):
        self.transport.callback = lambda body: approved_reply(body) | {'expires_at': NOW.isoformat()}
        with self.assertRaises(web.HTTPConflict):
            await self.service.handle(self.incoming())
        self.assertNotIn('release', self.events)

    async def test_malformed_backend_expiry_maps_to_conflict_without_releasing(self):
        self.transport.callback = lambda body: approved_reply(body) | {'expires_at': 'invalid'}
        with self.assertRaises(web.HTTPConflict):
            await self.service.handle(self.incoming())
        self.assertNotIn('release', self.events)
        self.assertEqual(self.supervisor.state, 'ADMITTED')

    async def test_backend_identity_cannot_issue_worker_release(self):
        request = self.incoming(certificate=BACKEND_CERT)
        with self.assertRaises(web.HTTPUnauthorized):
            await self.service.handle(request)
        self.assertEqual(request.read_count, 0)
        self.assertEqual(self.transport.calls, [])

    async def test_worker_cannot_read_backend_only_receipts(self):
        for operation in ('ADMITTED', 'CLOSED', 'export', 'request-' + RECEIPT):
            with self.subTest(operation=operation), self.assertRaises(web.HTTPUnauthorized):
                await self.service.handle(self.incoming(operation, method='GET', raw=b''))

    async def test_cookie_origin_query_wrong_scope_and_extra_pid_rejected_before_backend(self):
        cases = ((self.incoming(headers={'Cookie': 'a=b'}), web.HTTPUnauthorized),
                 (self.incoming(headers={'Origin': 'https://worker.example'}), web.HTTPUnauthorized),
                 (self.incoming(query='pid=321'), web.HTTPBadRequest),
                 (RequestDouble({'binding': BINDING.wire(), 'pid': 321}, certificate=WORKER_CERT), web.HTTPBadRequest),
                 (RequestDouble({'binding': dict(BINDING.wire(), lease_generation=4)}, certificate=WORKER_CERT), web.HTTPBadRequest))
        for request, error in cases:
            with self.subTest(request=request), self.assertRaises(error):
                await self.service.handle(request)
        self.assertEqual(self.transport.calls, [])
        self.assertNotIn('release', self.events)

    async def test_unknown_session_or_operation_does_not_create_supervisor(self):
        with self.assertRaises(web.HTTPNotFound):
            await self.service.handle(self.incoming(session=OTHER))
        with self.assertRaises(web.HTTPNotFound):
            await self.service.handle(self.incoming('inject-pid'))
        with self.assertRaises(web.HTTPNotFound):
            await self.service.handle(self.incoming(method='GET'))
        self.assertEqual(list(self.service.sessions), [SESSION])

    async def test_missing_prepared_supervisor_never_recovers_to_release(self):
        self.service.sessions.clear()
        with self.assertRaises(web.HTTPConflict):
            await self.service.handle(self.incoming())
        self.assertEqual(self.service.sessions, {})
        self.assertEqual(self.transport.calls, [])

    async def test_prepare_requires_new_state_and_existing_supervisor_owned_bridge(self):
        for state, has_bridge in (('ADMITTED', False), ('NEW', False), ('RUNNING', True)):
            self.supervisor.state = state
            self.service.bridges = {SESSION: object()} if has_bridge else {}
            with self.subTest(state=state, bridge=has_bridge), self.assertRaises(web.HTTPConflict):
                await self.service.handle(self.incoming('prepare'))

    async def test_prepare_uses_server_plan_not_worker_payload_or_pid(self):
        self.supervisor.state = 'NEW'
        self.service.bridges[SESSION] = object()

        async def prepare():
            self.events.append('prepare-owned-supervisor')
            return self.supervisor.admitted
        self.supervisor.prepare = prepare
        response = await self.service.handle(self.incoming('prepare'))
        reply = strict_json(response.body)
        self.assertEqual(reply['payload_sha256'], digest(self.supervisor.plan.payload))
        self.assertEqual(reply['job_directory'], str(self.supervisor.plan.job))
        self.assertEqual(reply['receipt_sha256'], digest(canonical(self.supervisor.admitted)))
        self.assertEqual(self.events, ['fresh-backend-plan', 'prepare-owned-supervisor'])
        self.loader.require_current.assert_awaited_once_with(self.supervisor.plan)

    def test_untrusted_plan_loader_type_is_not_allowed_at_service_construction(self):
        with self.assertRaisesRegex(RuntimeBlocked, 'INDEPENDENT_PLAN_LOADER_REQUIRED'):
            SupervisorHttpService({}, Path('/unused'), digest(WORKER_CERT), digest(BACKEND_CERT),
                                  self.admission, loader=SimpleNamespace(require_current=AsyncMock()))

    async def test_prepare_without_independent_loader_never_launches_static_plan(self):
        self.service.loader = None
        self.supervisor.state = 'NEW'
        self.service.bridges[SESSION] = object()
        self.supervisor.prepare = AsyncMock(return_value=self.supervisor.admitted)
        with self.assertRaises(web.HTTPConflict) as caught:
            await self.service.handle(self.incoming('prepare'))
        self.assertEqual(caught.exception.text, 'SUPERVISION_UNAVAILABLE')
        self.supervisor.prepare.assert_not_awaited()
        self.assertEqual(self.supervisor.state, 'NEW')

    async def test_changed_or_expired_plan_never_launches_even_with_existing_bridge(self):
        self.supervisor.state = 'NEW'
        self.service.bridges[SESSION] = object()
        self.supervisor.prepare = AsyncMock(return_value=self.supervisor.admitted)
        for reason in ('SUPERVISOR_PLAN_CHANGED', 'SUPERVISOR_PLAN_EXPIRED', 'ORIGINAL_SUPERVISOR_PLAN_REQUIRED'):
            self.loader.require_current = AsyncMock(side_effect=RuntimeBlocked(reason))
            with self.subTest(reason=reason), self.assertRaises(web.HTTPConflict):
                await self.service.handle(self.incoming('prepare'))
            self.loader.require_current.assert_awaited_once_with(self.supervisor.plan)
            self.supervisor.prepare.assert_not_awaited()
        self.assertEqual(self.supervisor.state, 'NEW')

    async def test_prepare_waits_for_fresh_backend_plan_read_before_owned_launch(self):
        self.supervisor.state = 'NEW'
        self.service.bridges[SESSION] = object()
        entered = asyncio.Event()
        permit = asyncio.Event()

        async def require_current(plan):
            self.assertIs(plan, self.supervisor.plan)
            self.events.append('fresh-plan-started')
            entered.set()
            await permit.wait()
            self.events.append('fresh-plan-confirmed')

        async def prepare():
            self.events.append('owned-launch')
            return self.supervisor.admitted
        self.loader.require_current = AsyncMock(side_effect=require_current)
        self.supervisor.prepare = AsyncMock(side_effect=prepare)
        pending = asyncio.create_task(self.service.handle(self.incoming('prepare')))
        try:
            await entered.wait()
            self.supervisor.prepare.assert_not_awaited()
            self.assertEqual(self.events, ['fresh-plan-started'])
            permit.set()
            await pending
        finally:
            if not pending.done():
                pending.cancel()
                await asyncio.gather(pending, return_exceptions=True)
        self.assertEqual(self.events, ['fresh-plan-started', 'fresh-plan-confirmed', 'owned-launch'])
        self.supervisor.prepare.assert_awaited_once_with()

    async def test_backend_read_requires_get_without_body(self):
        for method, raw in (('POST', b''), ('GET', b'{}')):
            with self.subTest(method=method, raw=raw), self.assertRaises(web.HTTPBadRequest):
                await self.service.handle(self.incoming('ADMITTED', method=method, raw=raw, certificate=BACKEND_CERT))

    async def test_backend_receipt_and_request_reads_use_trusted_sources(self):
        self.supervisor.receipt = lambda state: {'state': state, 'receipt_id': RECEIPT}
        response = await self.service.handle(self.incoming('ADMITTED', method='GET', raw=b'', certificate=BACKEND_CERT))
        self.assertEqual(strict_json(response.body), {'state': 'ADMITTED', 'receipt_id': RECEIPT})
        requested = []
        self.service.recorders[SESSION] = SimpleNamespace(read=lambda request_id:
            requested.append(request_id) or {'request_id': request_id, 'ordinal': 7})
        response = await self.service.handle(self.incoming('request-' + RECEIPT, method='GET', raw=b'', certificate=BACKEND_CERT))
        self.assertEqual(requested, [RECEIPT])
        self.assertEqual(strict_json(response.body)['ordinal'], 7)

    async def test_wait_uses_one_owned_completion_task_for_concurrent_callers(self):
        ready = asyncio.Event()
        finish_calls = []
        raw = b'{"original":"immutable completion"}'

        async def finish():
            finish_calls.append(True)
            await ready.wait()
            return raw
        self.supervisor.finish = finish
        first = asyncio.create_task(self.service.handle(self.incoming('wait')))
        second = asyncio.create_task(self.service.handle(self.incoming('wait')))
        await asyncio.sleep(0)
        ready.set()
        responses = await asyncio.gather(first, second)
        self.assertEqual(finish_calls, [True])
        self.assertEqual([response.body for response in responses], [raw, raw])
        self.assertTrue(all(response.headers['Cache-Control'] == 'no-store' for response in responses))


class HttpHostRelayTest(unittest.IsolatedAsyncioTestCase):
    async def test_unstarted_relay_never_forwards(self):
        transport = pinned()
        transport.client = None
        with self.assertRaisesRegex(RuntimeBlocked, 'HOST_RELAY_NOT_STARTED'):
            async with HttpHostRelay(transport).forward(BINDING, SESSION, 'POST', '/v1/model-contexts', b'{}', 'key', None):
                self.fail('unstarted relay yielded')

    async def test_exact_pin_and_original_bytes_observation_forwarded_without_impersonation(self):
        response = ResponseDouble()
        transport = pinned(response)
        observed = {'request_id': RECEIPT, 'ordinal': 4, 'sha256': 'd' * 64}
        raw = b'{ "original": 1e-7 }'
        async with HttpHostRelay(transport).forward(BINDING, SESSION, 'POST', '/v1/model-contexts', raw, 'key', observed) as received:
            self.assertIs(received, response)
        method, url, kwargs = transport.client.calls[0]
        self.assertEqual((method, url), ('POST', 'https://supervisor.example:8443/internal/v1/professional-runtime/relay'))
        self.assertIs(kwargs['allow_redirects'], False)
        self.assertEqual(kwargs['headers'], {'Content-Type': 'application/json'})
        self.assertEqual(strict_json(kwargs['data']), {
            'binding': BINDING.wire(), 'session_id': SESSION, 'method': 'POST', 'route': '/v1/model-contexts',
            'body_base64': base64.b64encode(raw).decode('ascii'), 'idempotency_key': 'key',
            'model_request_observation': observed})
        self.assertEqual(response.medical_peer_sha256, digest(CERT))
        self.assertEqual(response.exited, 1)

    async def test_raw_body_size_and_type_checked_before_post(self):
        for body in (b'12345', '1234', bytearray(b'1234'), None):
            transport = pinned(maximum=4)
            with self.subTest(body=body), self.assertRaisesRegex(RuntimeBlocked, 'HOST_RELAY_REQUEST_LIMIT'):
                async with HttpHostRelay(transport).forward(BINDING, SESSION, 'POST', '/v1/model-contexts', body, 'key', None):
                    self.fail('oversized or non-byte body yielded')
            self.assertEqual(transport.client.calls, [])

    async def test_encoded_envelope_limit_is_checked_even_when_raw_body_fits(self):
        transport = pinned(maximum=1024)
        raw = b'x' * 500
        self.assertLess(len(raw), transport.maximum)
        with self.assertRaisesRegex(RuntimeBlocked, 'HOST_RELAY_REQUEST_LIMIT'):
            async with HttpHostRelay(transport).forward(BINDING, SESSION, 'POST', '/v1/model-contexts', raw, 'key', None):
                self.fail('oversized envelope yielded')
        self.assertEqual(transport.client.calls, [])

    async def test_encoded_envelope_exact_boundary_is_permitted(self):
        raw = b'original bytes'
        encoded = canonical({'binding': BINDING.wire(), 'session_id': SESSION, 'method': 'POST',
                             'route': '/v1/model-contexts', 'body_base64': base64.b64encode(raw).decode('ascii'),
                             'idempotency_key': 'key', 'model_request_observation': None})
        transport = pinned(maximum=len(encoded))
        async with HttpHostRelay(transport).forward(BINDING, SESSION, 'POST', '/v1/model-contexts', raw, 'key', None):
            pass
        self.assertEqual(transport.client.calls[0][2]['data'], encoded)
        transport.client.calls.clear()
        transport.maximum -= 1
        with self.assertRaisesRegex(RuntimeBlocked, 'HOST_RELAY_REQUEST_LIMIT'):
            async with HttpHostRelay(transport).forward(BINDING, SESSION, 'POST', '/v1/model-contexts', raw, 'key', None):
                self.fail('envelope above exact limit yielded')
        self.assertEqual(transport.client.calls, [])

    async def test_wrong_or_missing_pin_rejected_before_yield(self):
        for response in (ResponseDouble(certificate=b'other'), ResponseDouble()):
            if response.tls.certificate == CERT:
                del response.medical_peer_sha256
            with self.subTest(response=response), self.assertRaisesRegex(RuntimeBlocked, 'HOST_RELAY_CERTIFICATE_MISMATCH'):
                async with HttpHostRelay(pinned(response)).forward(BINDING, SESSION, 'POST', '/v1/model-contexts', b'{}', 'key', None):
                    self.fail('untrusted relay response yielded')


class HostRelayServiceTest(unittest.IsolatedAsyncioTestCase):
    def setUp(self):
        self.control = RelayControlDouble()
        self.service = HostRelayService(self.control, {SESSION: BINDING}, digest(CERT), maximum=1024)
        StreamResponseDouble.instances = []
        self.response_patch = patch(MODULE + '.web.StreamResponse', StreamResponseDouble)
        self.response_patch.start()
        self.addCleanup(self.response_patch.stop)

    def value(self, **changes):
        return {'binding': BINDING.wire(), 'session_id': SESSION, 'method': 'POST',
                'route': '/v1/model-contexts', 'body_base64': base64.b64encode(b'{"original":true}').decode('ascii'),
                'idempotency_key': 'key-1',
                'model_request_observation': {'request_id': RECEIPT, 'ordinal': 3, 'sha256': 'd' * 64}} | changes

    async def test_preserves_scoped_binding_bytes_idempotency_and_observation_sequence(self):
        self.control.response = ResponseDouble((b'data: one\n', b'data: two\n'), content_type='text/event-stream; charset=utf-8')
        request = RequestDouble(self.value())
        response = await self.service.handle(request)
        self.assertEqual(self.control.calls, [(BINDING, SESSION, 'POST', '/v1/model-contexts',
            b'{"original":true}', 'key-1', {'request_id': RECEIPT, 'ordinal': 3, 'sha256': 'd' * 64})])
        self.assertEqual(response.chunks, [b'data: one\n', b'data: two\n'])
        self.assertIs(response.request, request)
        self.assertTrue(response.eof)
        self.assertTrue(self.control.exited)
        self.assertEqual(response.headers, {'Content-Type': 'text/event-stream; charset=utf-8', 'Cache-Control': 'no-store'})

    async def test_model_observation_ordinal_is_not_invented_resequenced_or_deduplicated(self):
        for ordinal in (3, 4):
            observed = {'request_id': RECEIPT if ordinal == 3 else OTHER, 'ordinal': ordinal, 'sha256': 'd' * 64}
            await self.service.handle(RequestDouble(self.value(model_request_observation=observed)))
        self.assertEqual([args[-1]['ordinal'] for args in self.control.calls], [3, 4])
        self.assertEqual([args[-1]['request_id'] for args in self.control.calls], [RECEIPT, OTHER])

    async def test_certificate_cookie_origin_and_query_reject_before_body_read(self):
        requests = (RequestDouble(self.value(), certificate=WORKER_CERT),
                    RequestDouble(self.value(), headers={'Cookie': 'a=b'}),
                    RequestDouble(self.value(), headers={'Origin': 'https://worker.example'}),
                    RequestDouble(self.value(), query='route=/other'))
        for request in requests:
            with self.subTest(request=request), self.assertRaises(web.HTTPUnauthorized):
                await self.service.handle(request)
            self.assertEqual(request.read_count, 0)
        self.assertEqual(self.control.calls, [])

    async def test_missing_tls_never_forwards(self):
        for transport in (None, TransportDouble(None)):
            request = RequestDouble(self.value())
            request.transport = transport
            with self.subTest(transport=transport), self.assertRaises(web.HTTPUnauthorized):
                await self.service.handle(request)
        self.assertEqual(self.control.calls, [])

    async def test_exact_shape_rejects_pid_process_flags_and_missing_observation(self):
        values = (self.value(pid=321), self.value(process_exited=True),
                  {k: v for k, v in self.value().items() if k != 'model_request_observation'})
        for value in values:
            with self.subTest(value=value), self.assertRaises(web.HTTPBadRequest):
                await self.service.handle(RequestDouble(value))
        self.assertEqual(self.control.calls, [])

    async def test_malformed_json_and_non_object_roots_return_bad_request(self):
        for raw in (b'null', b'true', b'1', b'[]', b'"text"', b'{', b'\xff',
                    b'{"binding":{},"binding":{}}'):
            with self.subTest(raw=raw), self.assertRaises(web.HTTPBadRequest):
                await self.service.handle(RequestDouble(raw=raw))
        self.assertEqual(self.control.calls, [])

    async def test_malformed_session_route_method_and_base64_return_bad_request(self):
        changes = ({'session_id': 'invalid'}, {'session_id': None}, {'route': []}, {'route': None},
                   {'method': {}}, {'method': None}, {'method': 'GET', 'route': '/v1/model-results/not-a-uuid'},
                   {'method': 'GET', 'route': '/v1/model-results/'},
                   {'body_base64': '!!!'}, {'body_base64': 'YQ'}, {'body_base64': 'YQ==\n'},
                   {'body_base64': None}, {'body_base64': []}, {'body_base64': 1})
        for change in changes:
            with self.subTest(change=change), self.assertRaises(web.HTTPBadRequest):
                await self.service.handle(RequestDouble(self.value(**change)))
        self.assertEqual(self.control.calls, [])

    async def test_impossible_base64_size_is_rejected_before_decoding(self):
        self.service.maximum = 4
        value = self.value(body_base64='A' * 12)
        with patch(MODULE + '.base64.b64decode') as decode:
            with self.assertRaises(web.HTTPBadRequest):
                await self.service.handle(RequestDouble(value))
            decode.assert_not_called()
        self.assertEqual(self.control.calls, [])

    async def test_cross_session_and_stale_binding_do_not_reach_control(self):
        for value in (self.value(session_id=OTHER), self.value(binding=dict(BINDING.wire(), lease_generation=4))):
            with self.subTest(value=value), self.assertRaises(web.HTTPForbidden):
                await self.service.handle(RequestDouble(value))
        self.assertEqual(self.control.calls, [])

    async def test_only_allowlisted_model_and_at01_through_at10_routes_reach_control(self):
        routes = ['/v1/model-contexts', '/v1/model-streams', '/v1/model-cancellations']
        routes += ['/v1/tools/AT-' + str(number).zfill(2) for number in range(1, 11)]
        for route in routes:
            await self.service.handle(RequestDouble(self.value(route=route)))
        await self.service.handle(RequestDouble(self.value(method='GET', route='/v1/model-results/' + RECEIPT)))
        self.assertEqual([args[3] for args in self.control.calls], routes + ['/v1/model-results/' + RECEIPT])

    async def test_scope_escape_wrong_methods_and_suffixes_are_rejected(self):
        cases = [('POST', route) for route in (
            '/v1/tools/AT-00', '/v1/tools/AT-11', '/v1/tools/AT-1', '/v1/tools/AT-01/extra',
            '/v1/tools/AT-01?pid=321', '/v1/model-contexts/', '/internal/v1/admin',
            'https://another.example/v1/model-contexts', '/v1/../model-contexts')]
        cases += [('GET', '/v1/model-contexts'), ('DELETE', '/v1/model-contexts'),
                  ('POST', '/v1/model-results/' + RECEIPT)]
        for method, route in cases:
            with self.subTest(method=method, route=route), self.assertRaises(web.HTTPBadRequest):
                await self.service.handle(RequestDouble(self.value(method=method, route=route)))
        self.assertEqual(self.control.calls, [])

    async def test_decoded_body_size_not_base64_character_count_is_enforced(self):
        self.service.maximum = 4
        value = self.value(body_base64=base64.b64encode(b'12345').decode('ascii'))
        with self.assertRaises(web.HTTPRequestEntityTooLarge):
            await self.service.handle(RequestDouble(value))
        self.assertEqual(self.control.calls, [])
        await self.service.handle(RequestDouble(self.value(body_base64=base64.b64encode(b'1234').decode('ascii'))))
        self.assertEqual(self.control.calls[-1][4], b'1234')

    async def test_non_200_or_non_json_non_sse_upstream_is_rejected_before_stream(self):
        for status, content_type in ((201, 'application/json'), (500, 'application/json'),
                                     (200, 'text/html'), (200, 'application/octet-stream')):
            self.control.response = ResponseDouble(status=status, content_type=content_type)
            with self.subTest(status=status, content_type=content_type), self.assertRaises(web.HTTPBadGateway):
                await self.service.handle(RequestDouble(self.value()))
            self.assertEqual(self.control.response.content.yielded, [])
        self.assertEqual(StreamResponseDouble.instances, [])

    async def test_stream_limit_stops_before_writing_oversized_chunk(self):
        self.service.maximum = 4
        self.control.response = ResponseDouble((b'12', b'34', b'5', b'unread'))
        with self.assertRaisesRegex(RuntimeBlocked, 'HOST_RELAY_RESPONSE_LIMIT'):
            await self.service.handle(RequestDouble(self.value(body_base64='')))
        response = StreamResponseDouble.instances[-1]
        self.assertEqual(response.chunks, [b'12', b'34'])
        self.assertFalse(response.eof)
        self.assertTrue(self.control.exited)
        self.assertEqual(self.control.response.content.yielded, [b'12', b'34', b'5'])


if __name__ == '__main__':
    unittest.main()
