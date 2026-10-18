"""Actual CP09 adapter. Durable attempt admission precedes all upstream bytes; recovery only re-submits held originals."""
from __future__ import annotations
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from uuid import UUID, uuid4
import hashlib, json, ssl, math
import httpx2, rfc8785
from medical_contracts.model_stream import RequestParameters, ModelResult
from .approved_credentials import ApprovedCredentialResolver
from .protocol import ProviderSettings, ExactInput
from .provider import ProviderCompletion
from .raw_response import CapturePolicy, ProviderResponseCapture
from .dispatch_guard import DurableDispatchGuard
from .formal_receipt_store import FormalReceiptStore


def _uuid(value):
    if not isinstance(value, str) or str(UUID(value)) != value:
        raise ValueError('FORMAL_ID_INVALID')
    return value


def _parse(raw: bytes):
    def unique(pairs):
        result = {}
        for key, value in pairs:
            if key in result:
                raise ValueError('FORMAL_DUPLICATE_JSON_KEY')
            result[key] = value
        return result
    return json.loads(raw, object_pairs_hook=unique, parse_constant=lambda _: (_ for _ in ()).throw(ValueError('FORMAL_NONFINITE_JSON')))


class MutualTlsInvocationGateway:
    """Deployment supplies an existing client-certificate SSLContext; no credentials are created here."""
    def __init__(self, origin: str, context: ssl.SSLContext, maximum_bytes: int, timeout_seconds: float):
        url = httpx2.URL(origin)
        if url.scheme != 'https' or url.username or url.password or url.query or url.fragment or url.path not in ('', '/'):
            raise ValueError('FORMAL_GATEWAY_ORIGIN_INVALID')
        if context.verify_mode != ssl.CERT_REQUIRED or not context.check_hostname:
            raise ValueError('FORMAL_GATEWAY_TLS_VERIFICATION_REQUIRED')
        if type(maximum_bytes) is not int or not 1 <= maximum_bytes <= 16*1024*1024 or not 0 < timeout_seconds <= 300:
            raise ValueError('FORMAL_GATEWAY_BOUNDS_REQUIRED')
        self._origin, self._context, self._maximum, self._timeout = origin.rstrip('/'), context, maximum_bytes, timeout_seconds

    async def post(self, path: str, body: dict, token: str | None = None):
        allowed = path in ('/internal/v1/invocations','/internal/v1/model-verification-workers/claim','/internal/v1/model-verification-workers/issue','/internal/v1/model-verification-workers/status')
        parts = path.split('/')
        if len(parts) == 6 and parts[:4] == ['', 'internal', 'v1', 'invocations'] and parts[5] in ('begin-send', 'receipts', 'reconciliations'):
            _uuid(parts[4]); allowed = True
        if not allowed:
            raise ValueError('FORMAL_GATEWAY_ROUTE_INVALID')
        raw = rfc8785.dumps(body)
        if len(raw) > self._maximum:
            raise ValueError('FORMAL_GATEWAY_BODY_LIMIT')
        headers = {'X-Schema-Version': '1.0.0', 'Content-Type': 'application/json'}
        if token is not None:
            if not isinstance(token, str) or not token or '\r' in token or '\n' in token:
                raise ValueError('FORMAL_RUN_TOKEN_INVALID')
            headers['X-Run-Token'] = token
        transport = httpx2.AsyncHTTPTransport(verify=self._context, retries=0, trust_env=False)
        async with httpx2.AsyncClient(transport=transport, trust_env=False, follow_redirects=False, timeout=self._timeout) as client:
            async with client.stream('POST', self._origin+path, content=raw, headers=headers) as response:
                if response.status_code != 200 or response.headers.get('x-schema-version') != '1.0.0':
                    raise RuntimeError('FORMAL_GATEWAY_REJECTED')
                chunks, size = [], 0
                async for chunk in response.aiter_bytes():
                    size += len(chunk)
                    if size > self._maximum:
                        raise ValueError('FORMAL_GATEWAY_RESPONSE_LIMIT')
                    chunks.append(chunk)
        value = _parse(b''.join(chunks))
        if set(value) != {'schema_version','request_id','resource_revision','data'} or value['schema_version'] != '1.0.0':
            raise ValueError('FORMAL_GATEWAY_ENVELOPE_INVALID')
        _uuid(value['request_id'])
        return value['data']


@dataclass(frozen=True, repr=False)
class HeldReceipt:
    """Read-back original bytes from the durable receipt journal."""
    invocation_id: str
    body: bytes = field(repr=False)


class FormalInvocationAdapter:
    def __init__(self, gateway: MutualTlsInvocationGateway, credentials: ApprovedCredentialResolver,
                 durable_directory: Path, capture_policy: CapturePolicy, maximum_artifact_bytes: int):
        capture_policy.require()
        if type(maximum_artifact_bytes) is not int or not 1 <= maximum_artifact_bytes <= 16*1024*1024:
            raise ValueError('FORMAL_ARTIFACT_CAP_REQUIRED')
        self._gateway, self._credentials = gateway, credentials
        self._directory, self._policy, self._maximum = durable_directory, capture_policy, maximum_artifact_bytes
        self._receipts = FormalReceiptStore(durable_directory, maximum_artifact_bytes)

    async def dispatch(self, invocation_id: str, worker_action: dict, token: str) -> HeldReceipt | None:
        _uuid(invocation_id)
        admitted = await self._gateway.post('/internal/v1/invocations/'+invocation_id+'/begin-send',
            {'schema_version':'1.0.0','worker_action':worker_action,'expected_dispatch_generation':'0'}, token)
        expected = {'invocation_id','new_send_admission','tenant_id','original_actor_id','context_manifest_id','model_config_revision','dispatch_attempt_id','dispatch_generation','service_uri','deadline_at','credential_reference_id','credential_revision','canonical_provider_body_sha256','provider_body'}
        if set(admitted) != expected or admitted['invocation_id'] != invocation_id or admitted['dispatch_generation'] != '1':
            raise ValueError('FORMAL_SEND_ADMISSION_INVALID')
        if admitted['new_send_admission'] is False:
            if any(admitted[k] is not None for k in expected-{'invocation_id','new_send_admission','dispatch_generation'}):
                raise ValueError('FORMAL_REPEATED_ADMISSION_EXPOSES_CONTENT')
            return None
        if admitted['new_send_admission'] is not True:
            raise ValueError('FORMAL_SEND_ADMISSION_INVALID')
        for key in ('tenant_id','original_actor_id','context_manifest_id','model_config_revision','dispatch_attempt_id','credential_reference_id'):
            _uuid(admitted[key])
        deadline = datetime.fromisoformat(admitted['deadline_at'].replace('Z','+00:00'))
        seconds = math.floor((deadline-datetime.now(timezone.utc)).total_seconds())
        if seconds < 1:
            raise ValueError('FORMAL_SEND_DEADLINE_EXPIRED')
        body = admitted['provider_body']
        digest = hashlib.sha256(rfc8785.dumps(body)).hexdigest()
        if digest != admitted['canonical_provider_body_sha256'] or body.get('reasoning_effort') != 'none':
            raise ValueError('FORMAL_PROVIDER_BODY_MISMATCH')
        exact = ExactInput.model_validate({'messages':body['messages'],'tools':body.get('tools',[])})
        parameters = RequestParameters(max_output_tokens=body['max_tokens'],temperature=body.get('temperature'),reasoning_effort='none',stop=body.get('stop',[]),purpose='VERIFICATION')
        settings = ProviderSettings(model_id=body['model'],model_version=body['model'],max_output_tokens=body['max_tokens'],deadline_seconds=min(300,seconds),temperature_enabled='temperature' in body,reasoning_efforts=['none'],stop_limit=len(body.get('stop',[])))
        if rfc8785.dumps(settings.request_options(parameters,exact)) != rfc8785.dumps(body):
            raise ValueError('FORMAL_PROVIDER_REQUEST_RECONSTRUCTION_MISMATCH')
        receipt_id = str(uuid4())
        try:
            with DurableDispatchGuard(self._directory) as guard:
                capture = ProviderResponseCapture(self._policy,invocation_id,admitted['dispatch_attempt_id'],receipt_id,digest,dispatch_guard=guard)
                provider = self._credentials.provider(admitted['tenant_id'],admitted['credential_reference_id'],admitted['credential_revision'],'DEEPSEEK','https://api.deepseek.com')
                completion = None
                async for event in provider.stream(settings,parameters,exact,receipt_id,capture=capture):
                    if isinstance(event,ProviderCompletion):
                        if completion is not None:
                            raise ValueError('FORMAL_DUPLICATE_COMPLETION')
                        completion = event
                    # Never emit host finish or speculative blocks before persisted result confirmation.
                if completion is None or completion.raw_response is None:
                    raise RuntimeError('FORMAL_PROVIDER_OUTCOME_UNKNOWN')
                result = ModelResult(schema_version='1.0.0',invocation_id=invocation_id,context_manifest_id=admitted['context_manifest_id'],model_config_revision=admitted['model_config_revision'],provider_request_id=completion.provider_request_id,blocks=completion.blocks,usage=completion.usage,reason=completion.reason,replay_state_id=None)
                original_capture = _parse(completion.raw_response.artifact(self._maximum))
                payload = rfc8785.dumps({'schema_version':'1.0.0','tenant_id':admitted['tenant_id'],'original_actor_id':admitted['original_actor_id'],'model_result':result.model_dump(mode='json'),'provider_capture':original_capture})
                if len(payload) > 16*1024*1024:
                    raise ValueError('FORMAL_RECEIPT_BODY_LIMIT')
                return HeldReceipt(invocation_id,self._receipts.save(_parse(payload)))
        except Exception:
            # No bill or completion is invented. If IN11 is also unavailable, the server
            # remains DISPATCHING/PENDING and the durable marker still forbids a resend.
            try:
                await self._gateway.post('/internal/v1/invocations/'+invocation_id+'/reconciliations',
                    {'schema_version':'1.0.0','tenant_id':admitted['tenant_id'],
                     'original_actor_id':admitted['original_actor_id'],
                     'dispatch_attempt_id':admitted['dispatch_attempt_id'],'kind':'TRANSPORT_OUTCOME_UNKNOWN'})
            except Exception:
                pass
            raise

    async def record(self, held: HeldReceipt):
        """Retry only identical held receipt bytes. This method never calls a model or begin-send."""
        return await self._gateway.post('/internal/v1/invocations/'+_uuid(held.invocation_id)+'/receipts',_parse(held.body))

    async def recover(self, invocation_id: str, dispatch_attempt_id: str):
        """After restart, read exact saved originals and retry IN10 only. No new IDs or provider call."""
        original = self._receipts.read(_uuid(invocation_id), _uuid(dispatch_attempt_id))
        return await self.record(HeldReceipt(invocation_id, original))


class FormalVerificationWorker:
    """Deployment-driven bounded poll. Server supplies every job/lease/action/token ID."""
    def __init__(self, gateway: MutualTlsInvocationGateway, adapter: FormalInvocationAdapter):
        self.gateway, self.adapter = gateway, adapter

    async def run_once(self, tenant_id: str):
        tenant_id = _uuid(tenant_id)
        claimed = await self.gateway.post('/internal/v1/model-verification-workers/claim',
            {'schema_version':'1.0.0','tenant_id':tenant_id})
        if set(claimed) != {'found','lease'} or type(claimed['found']) is not bool:
            raise ValueError('FORMAL_WORKER_CLAIM_INVALID')
        if not claimed['found']:
            if claimed['lease'] is not None: raise ValueError('FORMAL_WORKER_CLAIM_INVALID')
            return {'state':'IDLE'}
        lease = claimed['lease']
        verification = _uuid(lease['verification_id'])
        issued = await self.gateway.post('/internal/v1/model-verification-workers/issue',
            {'schema_version':'1.0.0','tenant_id':tenant_id,'verification_id':verification})
        if issued['verification_id'] != verification or issued['run_id'] != lease['run_id'] or issued['step_id'] != lease['step_id'] or issued['worker_action']['lease_generation'] != lease['lease_generation']:
            raise ValueError('FORMAL_WORKER_ISSUE_MISMATCH')
        reserved = await self.gateway.post('/internal/v1/invocations',
            {'schema_version':'1.0.0','worker_action':issued['worker_action'],
             'model_config_revision':issued['model_config_revision'],'context_manifest_id':issued['context_manifest_id'],
             'request_parameters':issued['request_parameters']}, issued['token'])
        if reserved['verification_id'] != verification:
            raise ValueError('FORMAL_WORKER_RESERVATION_MISMATCH')
        invocation = _uuid(reserved['invocation_id'])
        held = await self.adapter.dispatch(invocation, issued['worker_action'], issued['token'])
        if held is None:
            return await self.status(tenant_id, verification)
        return await self.adapter.record(held)

    async def status(self, tenant_id: str, verification_id: str):
        return await self.gateway.post('/internal/v1/model-verification-workers/status',
            {'schema_version':'1.0.0','tenant_id':_uuid(tenant_id),'verification_id':_uuid(verification_id)})

    async def recover(self, tenant_id: str, verification_id: str):
        status = await self.status(tenant_id, verification_id)
        if status['invocation_id'] is None or status['dispatch_attempt_id'] is None:
            return status
        return await self.adapter.recover(status['invocation_id'], status['dispatch_attempt_id'])
