"""Bounded exact HTTP entity capture. It is evidence collection, never send authorization.

No request/response headers are persisted except a bounded actual x-request-id.
Body bytes are private and repr-redacted. EOF is not a successful generation or settlement.
"""
from __future__ import annotations
import asyncio
import base64
from dataclasses import dataclass
import hashlib
import json
import math
import re
import time
from uuid import UUID
import httpx2
import rfc8785
from .dispatch_guard import DurableDispatchGuard, DispatchGuardError


class CaptureError(RuntimeError):
    """Fixed, content-free local error code; never embeds SDK requests or credentials."""


@dataclass(frozen=True)
class CapturePolicy:
    enabled: bool = False
    maximum_request_bytes: int | None = None
    maximum_response_bytes: int | None = None
    maximum_seconds: float | None = None

    def require(self) -> None:
        if self.enabled is not True:
            raise CaptureError('PROVIDER_CAPTURE_DISABLED')
        for number in (self.maximum_request_bytes, self.maximum_response_bytes):
            if type(number) is not int or not 1 <= number <= 32 * 1024 * 1024:
                raise CaptureError('PROVIDER_CAPTURE_BYTE_CAP_REQUIRED')
        if type(self.maximum_seconds) not in (int, float) or not math.isfinite(self.maximum_seconds) or not 0 < self.maximum_seconds <= 300:
            raise CaptureError('PROVIDER_CAPTURE_TIME_CAP_REQUIRED')


def _id(value: str) -> str:
    try:
        if not isinstance(value, str) or str(UUID(value)) != value:
            raise ValueError()
    except (TypeError, ValueError, AttributeError):
        raise CaptureError('PROVIDER_CAPTURE_ID_INVALID') from None
    return value


def _unique(pairs: list[tuple[str, object]]) -> dict:
    result = {}
    for key, value in pairs:
        if key in result:
            raise CaptureError('PROVIDER_REQUEST_DUPLICATE_KEY')
        result[key] = value
    return result


@dataclass(frozen=True, repr=False)
class CapturedResponse:
    invocation_id: str
    dispatch_attempt_id: str
    receipt_id: str
    body: bytes
    sha256: str
    observed_bytes: int
    canonical_request_body_sha256: str | None
    request_body_sha256: str | None
    provider_request_id: str | None
    http_status: int | None
    transport_terminal: str
    capture_complete: bool
    provider_finish_reason: str | None
    provider_response_id: str | None
    response_model: str | None
    response_eof_observed: bool
    content_policy_status: str

    def descriptor(self) -> dict:
        return dict(schema_version='1.0.0', profile='PROVIDER_HTTP_RESPONSE_ENTITY',
                    invocation_id=self.invocation_id, dispatch_attempt_id=self.dispatch_attempt_id,
                    receipt_id=self.receipt_id, sha256=self.sha256, size_bytes=str(len(self.body)),
                    observed_bytes=str(self.observed_bytes),
                    canonical_request_body_sha256=self.canonical_request_body_sha256,
                    request_body_sha256=self.request_body_sha256,
                    provider_request_id=self.provider_request_id,
                    request_id_source='HTTP_X_REQUEST_ID' if self.provider_request_id is not None else None,
                    http_status=self.http_status, transport_terminal=self.transport_terminal,
                    capture_complete=self.capture_complete, provider_finish_reason=self.provider_finish_reason,
                    provider_response_id=self.provider_response_id, response_model=self.response_model,
                    response_eof_observed=self.response_eof_observed, content_policy_status=self.content_policy_status)

    def artifact(self, maximum_bytes: int) -> bytes:
        if type(maximum_bytes) is not int or not 1 <= maximum_bytes <= 64 * 1024 * 1024:
            raise CaptureError("PROVIDER_CAPTURE_ARTIFACT_CAP_REQUIRED")
        if 4 * ((len(self.body) + 2) // 3) > maximum_bytes:
            raise CaptureError("PROVIDER_CAPTURE_ARTIFACT_BYTE_LIMIT")
        value = rfc8785.dumps(dict(schema_version="1.0.0", media_type="application/octet-stream", encoding="base64", metadata=self.descriptor(), body_base64=base64.b64encode(self.body).decode("ascii")))
        if len(value) > maximum_bytes:
            raise CaptureError("PROVIDER_CAPTURE_ARTIFACT_BYTE_LIMIT")
        return value


class ProviderResponseCapture:
    """One server-bound dispatch attempt, one HTTP request, no automatic retries."""
    ENDPOINT = 'https://api.deepseek.com/chat/completions'

    def __init__(self, policy: CapturePolicy, invocation_id: str, dispatch_attempt_id: str,
                 receipt_id: str, approved_canonical_request_sha256: str, *, dispatch_guard: DurableDispatchGuard | None = None):
        policy.require()
        self.policy = policy
        self.invocation_id, self.dispatch_attempt_id, self.receipt_id = map(_id, (invocation_id, dispatch_attempt_id, receipt_id))
        if not isinstance(approved_canonical_request_sha256, str) or not re.fullmatch(r'[0-9a-f]{64}', approved_canonical_request_sha256):
            raise CaptureError('PROVIDER_APPROVED_REQUEST_HASH_REQUIRED')
        if dispatch_guard is not None and type(dispatch_guard) is not DurableDispatchGuard:
            raise CaptureError("PROVIDER_DISPATCH_GUARD_INVALID")
        self._dispatch_guard = dispatch_guard
        self._guard_blocked = False
        self._approved = approved_canonical_request_sha256
        self._claimed = False
        self._response_started = False
        self._body = bytearray()
        self._observed = 0
        self._request_hash = None
        self._canonical_hash = None
        self._request_id = None
        self._status = None
        self._terminal = None
        self._deadline = None
        self._allow_drain = False
        self._cached_result = None
        self._semantic_terminal = None
        self._response_eof = False
        self._content_type = None

    def __repr__(self):
        return '<ProviderResponseCapture redacted>'

    def require_durable_guard(self) -> None:
        if self._dispatch_guard is None:
            raise CaptureError("PROVIDER_DURABLE_DISPATCH_GUARD_REQUIRED")

    def require_receipt(self, receipt_id: str) -> None:
        if _id(receipt_id) != self.receipt_id:
            raise CaptureError('PROVIDER_CAPTURE_RECEIPT_MISMATCH')

    def _claim(self, request: httpx2.Request) -> None:
        if self._claimed or self._cached_result is not None:
            raise CaptureError('PROVIDER_CAPTURE_ATTEMPT_ALREADY_USED')
        if request.method != 'POST' or str(request.url) != self.ENDPOINT:
            raise CaptureError('PROVIDER_CAPTURE_ENDPOINT_DENIED')
        # SDK JSON requests must already be bounded bytes, not an arbitrary caller stream.
        try:
            raw = request.content
        except httpx2.RequestNotRead:
            raise CaptureError('PROVIDER_REQUEST_NOT_BUFFERED') from None
        if not isinstance(raw, bytes) or not raw or len(raw) > self.policy.maximum_request_bytes:
            raise CaptureError('PROVIDER_REQUEST_BYTE_LIMIT')
        try:
            value = json.loads(raw.decode('utf-8'), object_pairs_hook=_unique,
                               parse_constant=lambda _: (_ for _ in ()).throw(ValueError()))
            if not isinstance(value, dict):
                raise ValueError()
            actual = hashlib.sha256(rfc8785.dumps(value)).hexdigest()
        except CaptureError:
            raise
        except (ValueError, UnicodeError, RecursionError, OverflowError):
            raise CaptureError('PROVIDER_REQUEST_JSON_INVALID') from None
        if actual != self._approved:
            raise CaptureError('PROVIDER_REQUEST_NOT_APPROVED')
        if value.get('reasoning_effort') != 'none':
            raise CaptureError('PROVIDER_CAPTURE_REASONING_MODE_UNSUPPORTED')
        self._canonical_hash = actual
        self._request_hash = hashlib.sha256(raw).hexdigest()
        self._claimed = True  # Commit before invoking transport; a failure does not permit a retry.
        self._deadline = time.monotonic() + self.policy.maximum_seconds
        if self._dispatch_guard is not None:
            try:
                self._dispatch_guard.claim(self.invocation_id,self.dispatch_attempt_id,self.receipt_id,self._approved)
            except DispatchGuardError:
                self._guard_blocked = True
                raise CaptureError("PROVIDER_DISPATCH_REQUIRES_RECONCILIATION") from None
            if time.monotonic() >= self._deadline:
                self._finish("TIME_LIMIT")
                raise CaptureError("PROVIDER_RESPONSE_TIME_LIMIT")

    def _start(self, response: httpx2.Response) -> None:
        if self._response_started:
            raise CaptureError('PROVIDER_RESPONSE_ALREADY_STARTED')
        self._response_started = True
        self._status = response.status_code
        self._content_type = response.headers.get('content-type', '').split(';', 1)[0].strip().lower()
        if response.is_stream_consumed or response.is_closed:
            self._finish('TRANSPORT_ERROR')
            raise CaptureError('PROVIDER_RESPONSE_ALREADY_CONSUMED')
        ids = response.headers.get_list('x-request-id')
        if len(ids) == 1 and re.fullmatch(r'[\x21-\x7e]{1,256}', ids[0]):
            self._request_id = ids[0]
        # Prevent decompression bombs and ambiguity between stored and SDK-decoded entities.
        encodings = response.headers.get_list('content-encoding')
        if encodings and (len(encodings) != 1 or encodings[0].strip().lower() != 'identity'):
            self._finish('UNSUPPORTED_ENCODING')
            raise CaptureError('PROVIDER_RESPONSE_ENCODING_UNSUPPORTED')

    def _append(self, chunk: bytes) -> None:
        if self._terminal is not None or not isinstance(chunk, bytes):
            self._finish('TRANSPORT_ERROR')
            raise CaptureError('PROVIDER_RESPONSE_STREAM_INVALID')
        self._observed += len(chunk)
        remaining = self.policy.maximum_response_bytes - len(self._body)
        self._body.extend(chunk[:remaining])
        if len(chunk) > remaining:
            self._finish('BYTE_LIMIT')
            raise CaptureError('PROVIDER_RESPONSE_BYTE_LIMIT')

    def _finish(self, terminal: str) -> None:
        if terminal == "COMPLETE_EOF":
            self._response_eof = True
        if self._terminal is None:
            self._terminal = terminal

    def allow_terminal_drain(self, reason: str, response_id: str, model: str) -> None:
        """Only the adapter may call this after validating a provider finish-reason chunk.

        The SDK closes its HTTP stream upon [DONE] before observing transport EOF. A bounded
        same-response tail drain establishes complete bytes, never another model request.
        Cancellation still closes immediately; no drain occurs for unfinished generation.
        """
        if reason not in {'stop', 'length', 'tool_calls', 'content_filter', 'insufficient_system_resource', 'aborted'} or not response_id or not model:
            raise CaptureError('PROVIDER_TERMINAL_INVALID')
        if len(response_id) > 256 or len(model) > 256 or any(ord(c) < 32 or ord(c) == 127 for c in response_id + model):
            raise CaptureError('PROVIDER_TERMINAL_ID_INVALID')
        value = (reason, response_id, model)
        if self._semantic_terminal is not None and self._semantic_terminal != value:
            raise CaptureError('PROVIDER_TERMINAL_CHANGED')
        self._semantic_terminal = value
        if self._claimed and self._terminal is None:
            self._allow_drain = True

    def reject_reasoning_content(self) -> None:
        # Project contract forbids preserving internal reasoning. No original-response proof is issued.
        self._body.clear()
        self._terminal = 'FORBIDDEN_PROVIDER_CONTENT'
        self._cached_result = None
        self._allow_drain = False
        raise CaptureError('PROVIDER_INTERNAL_REASONING_NOT_RETAINED')

    def result(self) -> CapturedResponse:
        if self._guard_blocked:
            raise CaptureError("PROVIDER_DISPATCH_REQUIRES_RECONCILIATION")
        if self._claimed and self._terminal is None:
            raise CaptureError('PROVIDER_CAPTURE_STILL_RUNNING')
        if self._cached_result is None:
            status = 'INCOMPLETE_RESPONSE'
            body = bytes(self._body)
            if self._terminal == 'COMPLETE_EOF':
                status = _retention_policy(body, self._content_type)
            elif self._terminal == 'FORBIDDEN_PROVIDER_CONTENT':
                status = 'FORBIDDEN_INTERNAL_REASONING'
            if status != 'VERIFIED_NO_INTERNAL_REASONING':
                self._body.clear()
                body = b''
            complete = self._terminal == 'COMPLETE_EOF' and status == 'VERIFIED_NO_INTERNAL_REASONING'
            self._cached_result = CapturedResponse(self.invocation_id, self.dispatch_attempt_id,
                self.receipt_id, body, hashlib.sha256(body).hexdigest(), self._observed,
                self._canonical_hash, self._request_hash, self._request_id, self._status,
                self._terminal or 'NOT_STARTED', complete,
                *(self._semantic_terminal or (None, None, None)), self._response_eof, status)
        return self._cached_result


def _retention_policy(body: bytes, content_type: str | None) -> str:
    """Do not persist incomplete/unparseable responses that could conceal reasoning fields."""
    def check(value):
        if isinstance(value, dict):
            for key, child in value.items():
                if key == 'reasoning_content' and child not in (None, ''):
                    raise CaptureError('INTERNAL_REASONING')
                check(child)
        elif isinstance(value, list):
            for child in value:
                check(child)
    try:
        text = body.decode('utf-8')
        if content_type == 'text/event-stream':
            # Exact stored bytes remain unchanged; only this validation view normalizes SSE line endings.
            blocks = text.replace('\r\n', '\n').replace('\r', '\n').split('\n\n')
            parsed = 0
            for block in blocks:
                if not block.strip():
                    continue
                lines = block.split('\n')
                # Unknown comments/fields have no reviewed content-retention contract.
                if any(line and not line.startswith('data:') for line in lines):
                    return 'UNVERIFIABLE_RESPONSE'
                payload = '\n'.join(line[5:].removeprefix(' ') for line in lines if line.startswith('data:'))
                if payload == '[DONE]':
                    continue
                check(json.loads(payload, object_pairs_hook=_unique, parse_constant=lambda _: (_ for _ in ()).throw(ValueError())))
                parsed += 1
            if not parsed:
                return 'UNVERIFIABLE_RESPONSE'
        elif content_type == 'application/json':
            check(json.loads(text, object_pairs_hook=_unique, parse_constant=lambda _: (_ for _ in ()).throw(ValueError())))
        else:
            return 'UNVERIFIABLE_RESPONSE'
    except CaptureError as failure:
        return 'FORBIDDEN_INTERNAL_REASONING' if str(failure) == 'INTERNAL_REASONING' else 'UNVERIFIABLE_RESPONSE'
    except (ValueError, UnicodeError, RecursionError):
        return 'UNVERIFIABLE_RESPONSE'
    return 'VERIFIED_NO_INTERNAL_REASONING'


class CapturingTransport(httpx2.AsyncBaseTransport):
    def __init__(self, upstream: httpx2.AsyncBaseTransport, capture: ProviderResponseCapture):
        self.upstream, self.capture = upstream, capture

    async def handle_async_request(self, request: httpx2.Request) -> httpx2.Response:
        self.capture._claim(request)
        # Identity is a transport control, not a credential or an additional approved body field.
        request.headers['accept-encoding'] = 'identity'
        try:
            async with asyncio.timeout_at(self.capture._deadline):
                response = await self.upstream.handle_async_request(request)
            try:
                self.capture._start(response)
            except BaseException:
                await response.aclose()
                raise
            if not isinstance(response.stream, httpx2.AsyncByteStream):
                self.capture._finish('TRANSPORT_ERROR')
                await response.aclose()
                raise CaptureError('PROVIDER_ASYNC_RESPONSE_REQUIRED')
            response.stream = _CapturedStream(response.stream, self.capture)
            return response
        except TimeoutError:
            self.capture._finish('TIME_LIMIT')
            raise CaptureError('PROVIDER_RESPONSE_TIME_LIMIT') from None
        except asyncio.CancelledError:
            self.capture._finish('CANCELLED')
            raise
        except BaseException:
            self.capture._finish('TRANSPORT_ERROR')
            raise

    async def aclose(self) -> None:
        await self.upstream.aclose()


class _CapturedStream(httpx2.AsyncByteStream):
    def __init__(self, stream: httpx2.AsyncByteStream, capture: ProviderResponseCapture):
        self._stream, self._iterator, self._capture = stream, stream.__aiter__(), capture
        self._closed = False
        self._closing = False

    def __aiter__(self):
        return self

    async def __anext__(self):
        if self._closed:
            raise StopAsyncIteration
        try:
            if time.monotonic() >= self._capture._deadline:
                raise TimeoutError()
            async with asyncio.timeout_at(self._capture._deadline):
                chunk = await anext(self._iterator)
            if time.monotonic() >= self._capture._deadline:
                raise TimeoutError()
            self._capture._append(chunk)
            return chunk
        except StopAsyncIteration:
            self._capture._finish('COMPLETE_EOF')
            raise
        except TimeoutError:
            self._capture._finish('TIME_LIMIT')
            raise CaptureError('PROVIDER_RESPONSE_TIME_LIMIT') from None
        except asyncio.CancelledError:
            self._capture._finish('CANCELLED')
            raise
        except BaseException:
            self._capture._finish('TRANSPORT_ERROR')
            raise

    async def aclose(self):
        if self._closed or self._closing:
            return
        self._closing = True
        try:
            cancelling = asyncio.current_task() is not None and asyncio.current_task().cancelling() > 0
            if cancelling:
                self._capture._finish('CANCELLED')
            elif self._capture._allow_drain and self._capture._terminal is None:
                try:
                    while True:
                        await self.__anext__()
                except StopAsyncIteration:
                    pass
            self._capture._finish('EARLY_CLOSE')
        finally:
            self._closed = True
            await self._stream.aclose()
