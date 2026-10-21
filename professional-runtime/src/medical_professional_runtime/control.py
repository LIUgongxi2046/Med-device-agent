"""Concrete bounded mTLS transport for the closed professional-runtime control API.

This API is an integration boundary requiring server-side original-ledger proofs;
absence is a deployment blocker, never a local success fallback.
"""
from contextlib import asynccontextmanager
import json
from pathlib import Path
import ssl
from urllib.parse import urlsplit
import aiohttp
from .host import RuntimeBlocked, strict_json, private_directory, uuid

class HttpControl:
    def __init__(self, origin: str, tls: ssl.SSLContext, sockets: Path, maximum_bytes=16*1024*1024):
        parts=urlsplit(origin)
        if parts.scheme!='https' or not parts.hostname or parts.username or parts.password or parts.path not in ('','/') or parts.query or parts.fragment: raise RuntimeBlocked('FIXED_TLS_ORIGIN_REQUIRED')
        if tls.verify_mode != ssl.CERT_REQUIRED or not tls.check_hostname: raise RuntimeBlocked('VERIFIED_TLS_REQUIRED')
        private_directory(sockets)
        self.origin=origin.rstrip('/'); self.tls=tls; self.sockets=sockets; self.maximum_bytes=maximum_bytes; self.client=None

    async def __aenter__(self):
        self.client=aiohttp.ClientSession(connector=aiohttp.TCPConnector(ssl=self.tls),trust_env=False,
            timeout=aiohttp.ClientTimeout(total=10,connect=5),raise_for_status=False)
        return self
    async def __aexit__(self,*_): await self.client.close()

    async def _request(self, operation, value):
        if self.client is None: raise RuntimeBlocked('CONTROL_NOT_STARTED')
        payload=json.dumps(value,separators=(',',':'),ensure_ascii=False).encode()
        if len(payload)>self.maximum_bytes: raise RuntimeBlocked('CONTROL_REQUEST_LIMIT')
        # No redirects, credential forwarding, automatic application retry or SDK retry.
        async with self.client.post(self.origin+'/internal/v1/professional-runtime/'+operation,data=payload,
                headers={'Content-Type':'application/json'},allow_redirects=False) as response:
            if response.status != 200: raise RuntimeBlocked('CONTROL_REJECTED')
            raw=bytearray()
            async for chunk in response.content.iter_chunked(65536):
                raw.extend(chunk)
                if len(raw)>self.maximum_bytes: raise RuntimeBlocked('CONTROL_RESPONSE_LIMIT')
            return strict_json(bytes(raw))

    async def open(self,binding,reference,dispatch):
        return await self._request('open',{'binding':binding.wire(),'reference':reference,'dispatch':dispatch})
    async def admit(self,binding,session):
        return await self._request('admit',{'binding':binding.wire(),'session_id':session})
    async def heartbeat(self,binding,session):
        reply=await self._request('heartbeat',{'binding':binding.wire(),'session_id':session})
        if reply != {'binding':binding.wire(),'session_id':session,'admission':'CURRENT'}: raise RuntimeBlocked('LEASE_NOT_CURRENT')
    async def close_admission(self,binding,session,reason):
        reply=await self._request('close-admission',{'binding':binding.wire(),'session_id':session,'reason':reason})
        if reply != {'binding':binding.wire(),'session_id':session,'admission':'CLOSED'}: raise RuntimeBlocked('ADMISSION_CLOSURE_UNCONFIRMED')
    async def finish(self,binding,session,observation,files):
        return await self._request('finish',{'binding':binding.wire(),'session_id':session,'observation':observation,'files':files})
    async def failed(self,binding,session,reason,process_exited):
        await self._request('failure',{'binding':binding.wire(),'session_id':session,'reason':reason,'host_process_exited':process_exited})
    async def open_bridge(self,binding,session,job):
        raise RuntimeBlocked('INDEPENDENT_SUPERVISOR_BRIDGE_REQUIRED')

    @asynccontextmanager
    async def forward(self,binding,session,method,route,body,key,observed=None):
        if self.client is None: raise RuntimeBlocked('CONTROL_NOT_STARTED')
        await self.heartbeat(binding,session)
        # The private path is closed by PrivateBridgeServer; the model supplies no
        # tenant, run, step, lease, auth token, provider endpoint or HTTP header.
        payload={'binding':binding.wire(),'session_id':session,'method':method,'route':route,'body':None if not body else strict_json(body),'idempotency_key':key,'model_request_observation':observed}
        async with self.client.post(self.origin+'/internal/v1/professional-runtime/bridge',json=payload,
                timeout=aiohttp.ClientTimeout(total=360 if route == '/v1/tools/AT-09' else 300,connect=5,sock_read=360 if route == '/v1/tools/AT-09' else 30),allow_redirects=False) as response:
            yield response
