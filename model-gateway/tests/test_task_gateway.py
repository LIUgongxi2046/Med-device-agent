"""Offline doubles at the provider/HTTP boundary; actual adapter, capture and journal run."""
import base64
import copy
from datetime import datetime, timedelta, timezone
import hashlib
import json
from pathlib import Path
import ssl
import tempfile
import unittest
from unittest.mock import patch

import httpx2
import rfc8785

from medical_contracts.model_stream import ModelResult
from medical_model_gateway.formal_invocation_adapter import (
    FormalInvocationAdapter, InvocationProfile, MutualTlsInvocationGateway,
)
from medical_model_gateway.formal_receipt_store import FormalReceiptStore
from medical_model_gateway.provider import ProviderCompletion
from medical_model_gateway.raw_response import CapturePolicy, CapturingTransport
from medical_model_gateway.task_gateway import TaskGatewayAdapter, configured, request

BASE = Path(__file__).resolve().parents[2]
FIXTURES = BASE / 'business/medical-persistence/src/main/resources/fixtures/provider-capture'
ID = '11111111-1111-4111-8111-111111111111'
OTHER = '22222222-2222-4222-8222-222222222222'


def action():
    return dict(run_id=ID, step_id=ID, action_id=ID, lease_generation='1',
                input_sha256='a'*64, arguments_sha256='b'*64, kind='MODEL_CALL',
                tool_version=ID, scope=dict(tenant_id=ID, project_id=ID,
                resource_kind='CONFIGURATION', resource_id=ID),
                action='model.send', purpose='RESEARCH_SYNTHESIS')


def dispatch(invocation=ID):
    return dict(schema_version='1.0.0', mode='DISPATCH', invocation_id=invocation,
                worker_action=action(), run_token='never-print-this-token')


class ProtocolTests(unittest.TestCase):
    def test_exact_dispatch_and_recovery_grammar(self):
        for value in (dispatch(), dict(schema_version='1.0.0', mode='RECOVER',
                                      invocation_id=ID, dispatch_attempt_id=OTHER)):
            self.assertEqual(request(rfc8785.dumps(value)), value)

    def test_duplicate_unknown_oversize_or_cross_purpose_rejected(self):
        cases = [b'{"mode":"DISPATCH","mode":"RECOVER"}', b' '*65537, b'[]',
                 b'{"schema_version":"1.0.0","mode":"POLL"}']
        for key, replacement in [('purpose', 'MODEL_VERIFICATION'), ('purpose', 'EVALUATION'),
                                 ('kind', 'HOST_CONTROL'), ('lease_generation', 1)]:
            value = dispatch()
            value['worker_action'][key] = replacement
            cases.append(rfc8785.dumps(value))
        value = dispatch()
        value['provider_body'] = {'messages': []}
        cases.append(rfc8785.dumps(value))
        value = dispatch()
        value['run_token'] += '\n'
        cases.append(rfc8785.dumps(value))
        for raw in cases:
            with self.subTest(raw=raw[:80]), self.assertRaises((ValueError, TypeError)):
                request(raw)

    def test_missing_mtls_or_credential_configuration_fails_closed(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            config = directory / 'config.json'
            values = dict(schema_version='1.0.0', execution_kind='TASK', tenant_id=ID,
                          origin='https://gateway.invalid', ca_file=str(directory/'absent-ca'),
                          client_certificate_file=str(directory/'absent-cert'),
                          client_private_key_file=str(directory/'absent-key'),
                          credential_bindings_file=str(directory/'bindings.json'),
                          durable_directory=str(directory), maximum_http_bytes=1048576,
                          maximum_request_bytes=1048576, maximum_response_bytes=1048576,
                          maximum_seconds=30)
            config.write_bytes(rfc8785.dumps(values))
            config.chmod(0o600)
            with self.assertRaises(FileNotFoundError):
                configured(config)
            binding = directory/'bindings.json'
            binding.write_bytes(rfc8785.dumps(dict(schema_version='1.0.0', bindings=[])))
            binding.chmod(0o600)
            with self.assertRaisesRegex(ValueError, 'CREDENTIAL_DIRECTORY'):
                configured(config)
            binding.write_bytes(rfc8785.dumps(dict(schema_version='1.0.0', bindings=[
                dict(tenant_id=ID, reference_id=ID, revision='fixed', provider='DEEPSEEK',
                     endpoint='https://api.deepseek.com', path=str(directory/'absent-secret'), active=True)])))
            with self.assertRaises(FileNotFoundError):
                configured(config)


class BytesStream(httpx2.AsyncByteStream):
    def __init__(self, body):
        self.body = body

    async def __aiter__(self):
        for position in range(0, len(self.body), 17):
            yield self.body[position:position+17]


class TaskOrchestrationTests(unittest.IsolatedAsyncioTestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.directory = Path(self.temporary.name)
        self.capture = json.loads((FIXTURES/'actual-sdk-capture.json').read_bytes())
        self.model = ModelResult.model_validate_json((FIXTURES/'actual-sdk-model-result.json').read_bytes())
        self.body = json.loads((FIXTURES/'approved-provider-body.json').read_bytes())
        self.metadata = self.capture['metadata']
        self.invocation, self.attempt, self.receipt = (self.metadata[k] for k in
                                                    ('invocation_id', 'dispatch_attempt_id', 'receipt_id'))
        self.calls, self.provider_calls, self.recorded = [], [], []
        self.repeat = self.receipt_failure = self.provider_failure = False
        self.admitted = dict(
            invocation_id=self.invocation, new_send_admission=True, tenant_id=ID,
            original_actor_id=OTHER, context_manifest_id=self.model.context_manifest_id,
            model_config_revision=self.model.model_config_revision, dispatch_attempt_id=self.attempt,
            dispatch_generation='1', service_uri='spiffe://test/task-gateway',
            deadline_at=(datetime.now(timezone.utc)+timedelta(seconds=120)).isoformat(),
            credential_reference_id=ID, credential_revision='fixed',
            canonical_provider_body_sha256=hashlib.sha256(rfc8785.dumps(self.body)).hexdigest(),
            provider_body=self.body)
        test = self

        class Provider:
            async def stream(self, settings, parameters, exact, receipt, *, capture):
                test.provider_calls.append(parameters.purpose)
                test.assertEqual(parameters.purpose, 'TASK')
                test.assertEqual(receipt, test.receipt)
                def upstream(sent):
                    # Real capture has durably claimed before invoking the explicit double.
                    test.assertTrue(any(test.directory.glob('dispatch-*.json')))
                    if test.provider_failure:
                        raise httpx2.ConnectError('private provider failure must not escape')
                    return httpx2.Response(200, headers={'content-type': 'text/event-stream',
                        'x-request-id': 'actual-http-request'}, stream=BytesStream(
                            base64.b64decode(test.capture['body_base64'])))
                transport = CapturingTransport(httpx2.MockTransport(upstream), capture)
                response = await transport.handle_async_request(httpx2.Request('POST',
                    capture.ENDPOINT, content=rfc8785.dumps(settings.request_options(parameters, exact))))
                capture.allow_terminal_drain('stop', test.model.provider_request_id, 'deepseek-chat')
                await response.aread()
                await response.aclose()
                yield ProviderCompletion(test.model.provider_request_id, 'deepseek-chat',
                    test.model.blocks, test.model.usage, test.model.reason, raw_response=capture.result())

        class Credentials:
            def provider(self, tenant, reference, revision, provider, endpoint):
                test.assertEqual((tenant, reference, revision, provider, endpoint),
                                 (ID, ID, 'fixed', 'DEEPSEEK', 'https://api.deepseek.com'))
                return Provider()

        self.gateway = MutualTlsInvocationGateway('https://gateway.test.invalid',
            ssl.create_default_context(), 16777216, 10, profile=InvocationProfile.TASK_V1)
        self.adapter = FormalInvocationAdapter(self.gateway, Credentials(), self.directory,
            CapturePolicy(True, 1048576, 1048576, 120), 16777216, profile=InvocationProfile.TASK_V1)
        self.runner = TaskGatewayAdapter(self.adapter, ID, lambda: None)
        self.http_patch = patch('medical_model_gateway.formal_invocation_adapter.httpx2.AsyncHTTPTransport',
                               side_effect=lambda **_: httpx2.MockTransport(self.gateway_transport))
        self.id_patch = patch('medical_model_gateway.formal_invocation_adapter.uuid4', return_value=self.receipt)
        self.http_patch.start()
        self.id_patch.start()

    def tearDown(self):
        self.id_patch.stop()
        self.http_patch.stop()
        self.temporary.cleanup()

    def gateway_transport(self, sent):
        path = sent.url.path
        self.calls.append(path)
        self.assertTrue(path.startswith('/internal/v1/task-invocations/'+self.invocation+'/'))
        self.assertEqual(sent.headers['x-schema-version'], '1.0.0')
        payload = json.loads(sent.content)
        if path.endswith('/begin-send'):
            self.assertEqual(sent.headers['x-run-token'], 'never-print-this-token')
            self.assertEqual(payload, dict(schema_version='1.0.0', worker_action=action(),
                                           expected_dispatch_generation='0'))
            data = copy.deepcopy(self.admitted)
            if self.repeat:
                data = {key: value if key in {'invocation_id', 'dispatch_generation'} else None
                        for key, value in data.items()}
                data['new_send_admission'] = False
        elif path.endswith('/cancellation-status'):
            self.assertEqual(payload, dict(schema_version='1.0.0', tenant_id=ID,
                original_actor_id=ID, dispatch_attempt_id=self.attempt))
            data = dict(schema_version='1.0.0', invocation_id=self.invocation,
                dispatch_attempt_id=self.attempt, stop_requested=False,
                strategy='TRANSPORT_ABORT', provider_terminal=False)
        elif path.endswith('/receipts'):
            self.assertNotIn('x-run-token', sent.headers)
            self.recorded.append(sent.content)
            if self.receipt_failure:
                raise httpx2.ReadError('private receipt failure must not escape')
            data = {'receipt_received': True}
        elif path.endswith('/reconciliations'):
            self.assertEqual(payload['kind'], 'TRANSPORT_OUTCOME_UNKNOWN')
            data = {'state': 'PENDING'}
        else:
            self.fail(path)
        return httpx2.Response(200, headers={'x-schema-version': '1.0.0'}, json=dict(
            schema_version='1.0.0', request_id=ID, resource_revision='1', data=data))

    async def execute_dispatch(self):
        return json.loads(await self.runner.execute(rfc8785.dumps(dispatch(self.invocation))))

    async def test_dispatch_captures_persists_and_records_before_bounded_output(self):
        result = await self.execute_dispatch()
        self.assertEqual(result, dict(schema_version='1.0.0', mode='DISPATCH',
                         invocation_id=self.invocation, dispatch_attempt_id=self.attempt, recorded=True))
        self.assertEqual(self.provider_calls, ['TASK'])
        original = FormalReceiptStore(self.directory, 16777216).read(self.invocation, self.attempt)
        self.assertEqual(self.recorded, [original])
        self.assertEqual([path.rsplit('/', 1)[-1] for path in self.calls],
                         ['begin-send', 'cancellation-status', 'receipts'])
        self.assertNotIn('never-print', json.dumps(result))

    async def test_repeated_admission_has_no_provider_or_receipt(self):
        self.repeat = True
        result = await self.execute_dispatch()
        self.assertFalse(result['recorded'])
        self.assertIsNone(result['dispatch_attempt_id'])
        self.assertEqual(self.provider_calls, [])
        self.assertEqual(self.recorded, [])

    async def test_recovery_records_original_after_uncertain_record_without_resend(self):
        self.receipt_failure = True
        with self.assertRaises(httpx2.ReadError):
            await self.execute_dispatch()
        original = self.recorded[0]
        self.receipt_failure = False
        self.calls.clear()
        response = await self.runner.execute(rfc8785.dumps(dict(schema_version='1.0.0',
            mode='RECOVER', invocation_id=self.invocation, dispatch_attempt_id=self.attempt)))
        self.assertTrue(json.loads(response)['recorded'])
        self.assertEqual(self.provider_calls, ['TASK'])
        self.assertEqual(self.recorded, [original, original])
        self.assertEqual(self.calls, ['/internal/v1/task-invocations/'+self.invocation+'/receipts'])

    async def test_missing_or_tampered_original_never_re_dispatches(self):
        recover = rfc8785.dumps(dict(schema_version='1.0.0', mode='RECOVER',
                               invocation_id=self.invocation, dispatch_attempt_id=self.attempt))
        with self.assertRaises(FileNotFoundError):
            await self.runner.execute(recover)
        self.assertEqual(self.calls, [])
        await self.execute_dispatch()
        self.calls.clear()
        original = self.directory/('receipt-'+self.attempt+'-model.json')
        original.write_bytes(original.read_bytes()+b' ')
        with self.assertRaises(ValueError):
            await self.runner.execute(recover)
        self.assertEqual(self.calls, [])
        self.assertEqual(self.provider_calls, ['TASK'])

    async def test_unknown_provider_outcome_records_reconciliation_only(self):
        self.provider_failure = True
        with self.assertRaises(httpx2.ConnectError):
            await self.execute_dispatch()
        self.assertEqual(self.recorded, [])
        self.assertTrue(self.calls[-1].endswith('/reconciliations'))
        self.assertEqual(self.provider_calls, ['TASK'])
        self.repeat = True
        self.assertFalse((await self.execute_dispatch())['recorded'])
        self.assertEqual(self.provider_calls, ['TASK'])

    async def test_cross_profile_routes_and_constructor_strings_rejected_before_transport(self):
        verification = MutualTlsInvocationGateway('https://gateway.test.invalid',
            ssl.create_default_context(), 1048576, 10)
        for gateway, path in [
            (self.gateway, '/internal/v1/invocations/'+ID+'/begin-send'),
            (self.gateway, '/internal/v1/model-verification-workers/claim'),
            (verification, '/internal/v1/task-invocations/'+ID+'/receipts')]:
            with self.assertRaisesRegex(ValueError, 'ROUTE_INVALID'):
                await gateway.post(path, {})
        self.assertEqual(self.calls, [])
        with self.assertRaisesRegex(ValueError, 'PROFILE_INVALID'):
            MutualTlsInvocationGateway('https://gateway.test.invalid', ssl.create_default_context(),
                                       1024, 1, profile='TASK')
        with self.assertRaisesRegex(ValueError, 'PROFILE_MISMATCH'):
            FormalInvocationAdapter(verification, object(), self.directory,
                                    CapturePolicy(True, 1024, 1024, 1), 1024,
                                    profile=InvocationProfile.TASK_V1)


if __name__ == '__main__':
    unittest.main()
