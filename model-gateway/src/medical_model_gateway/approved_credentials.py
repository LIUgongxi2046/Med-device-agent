"""Read only deployment-supplied bindings. Never accepts a request-supplied path or creates secrets."""
from dataclasses import dataclass, field
from pathlib import Path
from typing import Callable
from medical_model_gateway.credentials import read_key

@dataclass(frozen=True, repr=False)
class ApprovedCredentialBinding:
    tenant_id: str
    reference_id: str
    revision: str
    provider: str
    endpoint: str
    path: Path = field(repr=False)

class ApprovedCredentialResolver:
    def __init__(self, bindings: tuple[ApprovedCredentialBinding, ...], current: Callable[[ApprovedCredentialBinding], bool]):
        self._bindings = {}
        self._current = current
        for binding in bindings:
            if binding.provider != 'DEEPSEEK' or binding.endpoint != 'https://api.deepseek.com':
                raise ValueError('Unsupported approved provider endpoint')
            key = (binding.tenant_id, binding.reference_id, binding.revision)
            if key in self._bindings or not binding.path.is_absolute():
                raise ValueError('Ambiguous or relative credential binding')
            self._bindings[key] = binding

    def require(self, tenant_id: str, reference_id: str, revision: str, provider: str, endpoint: str):
        binding = self._bindings.get((tenant_id, reference_id, revision))
        if binding is None or (binding.provider, binding.endpoint) != (provider, endpoint) or not self._current(binding):
            raise PermissionError('Credential binding unavailable')
        # Existing read_key performs nofollow, owner/mode/link checks. No secret is returned by repr/log output.
        secret = read_key(binding.path)
        if not self._current(binding):
            raise PermissionError('Credential binding changed')
        return secret

    def provider(self, tenant_id: str, reference_id: str, revision: str, provider: str, endpoint: str):
        """Reuse real transport while resolving the exact approved binding on EVERY client acquisition."""
        from medical_model_gateway.provider import DeepSeekProvider
        resolver = self
        class BoundProvider(DeepSeekProvider):
            def __init__(self):
                pass
            def client(self, capture=None):
                import logging
                import httpx2
                from openai import AsyncOpenAI
                from medical_model_gateway.raw_response import CapturingTransport
                if capture is not None:
                    capture.require_durable_guard()
                key = resolver.require(tenant_id, reference_id, revision, provider, endpoint)
                for name in ('openai', 'openai._base_client', 'httpx2', 'httpcore2'):
                    logging.getLogger(name).setLevel(logging.CRITICAL + 1)
                raw_transport = None if capture is None else CapturingTransport(
                    httpx2.AsyncHTTPTransport(trust_env=False, retries=0), capture)
                transport = httpx2.AsyncClient(trust_env=False, follow_redirects=False, transport=raw_transport,
                    timeout=httpx2.Timeout(60, connect=5, write=60, pool=5))
                return AsyncOpenAI(api_key=key.get_secret_value(), base_url=DeepSeekProvider.BASE_URL,
                    max_retries=0, http_client=transport)
        return BoundProvider()
