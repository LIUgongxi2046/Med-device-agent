"""Dedicated TASK/N05 endpoint selection. No caller-controlled base path."""
from contextlib import asynccontextmanager
import json
import aiohttp
from .control import HttpControl
from .host import RuntimeBlocked, strict_json

class SourceVerifierHttpControl(HttpControl):
    async def _request(self, operation, value):
        if self.client is None: raise RuntimeBlocked('CONTROL_NOT_STARTED')
        payload=json.dumps(value,separators=(',',':'),ensure_ascii=False).encode()
        if len(payload)>self.maximum_bytes: raise RuntimeBlocked('CONTROL_REQUEST_LIMIT')
        # No redirects, credential forwarding, automatic application retry or SDK retry.
        async with self.client.post(self.origin+'/internal/v1/source-verifier-runtime/'+operation,data=payload,
                headers={'Content-Type':'application/json'},allow_redirects=False) as response:
            if response.status != 200: raise RuntimeBlocked('CONTROL_REJECTED')
            raw=bytearray()
            async for chunk in response.content.iter_chunked(65536):
                raw.extend(chunk)
                if len(raw)>self.maximum_bytes: raise RuntimeBlocked('CONTROL_RESPONSE_LIMIT')
            return strict_json(bytes(raw))

    async def open_workflow(self,reference,dispatch):
        return await self._request('open-workflow',{'reference':reference,'dispatch':dispatch})
    @asynccontextmanager
    async def forward(self,binding,session,method,route,body,key,observed=None):
        if self.client is None: raise RuntimeBlocked('CONTROL_NOT_STARTED')
        if route.startswith('/v1/tools/'):
            raise RuntimeBlocked('N05_MODEL_TOOLS_FORBIDDEN')
        await self.heartbeat(binding,session)
        # The private path is closed by PrivateBridgeServer; the model supplies no
        # tenant, run, step, lease, auth token, provider endpoint or HTTP header.
        payload={'binding':binding.wire(),'session_id':session,'method':method,'route':route,'body':None if not body else strict_json(body),'idempotency_key':key,'model_request_observation':observed}
        async with self.client.post(self.origin+'/internal/v1/source-verifier-runtime/bridge',json=payload,
                timeout=aiohttp.ClientTimeout(total=300,connect=5,sock_read=30),allow_redirects=False) as response:
            yield response
