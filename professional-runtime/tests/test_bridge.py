"""Real Unix socket HTTP tests with a bounded upstream fixture, no provider calls."""
from contextlib import asynccontextmanager
from pathlib import Path
import tempfile
import unittest
from uuid import uuid4
import aiohttp
from medical_professional_runtime.private_bridge import PrivateBridgeServer
from medical_professional_runtime.host import Binding

class Content:
    async def iter_chunked(self,n): yield b'{"fixture":true}'
class Upstream:
    status=200; headers={'Content-Type':'application/json'}; content=Content()

class BridgeTest(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self):
        self.temp=tempfile.TemporaryDirectory(prefix='jbridge-'); self.root=Path(self.temp.name)
        self.calls=[]
        @asynccontextmanager
        async def forward(*args): self.calls.append(args); yield Upstream()
        self.binding=Binding(*[str(uuid4()) for _ in range(4)],1,'a'*64,'b'*64)
        self.bridge=await PrivateBridgeServer(self.root,self.binding,str(uuid4()),forward,10000).start()
        self.bridge.guard=lambda: None  # Explicit bounded test-only inventory fixture
        self.client=aiohttp.ClientSession(connector=aiohttp.UnixConnector(path=str(self.bridge.socket)))
    async def asyncTearDown(self):
        await self.client.close(); await self.bridge.close(); self.temp.cleanup()
    async def request(self,path,body=b'{}',headers=None,method='POST'):
        async with self.client.request(method,'http://localhost'+path,data=body,headers=headers or {'Authorization':'Bearer '+self.bridge.token,'Content-Type':'application/json'}) as response:
            return response.status,await response.read()
    async def test_actual_socket_0600(self):
        self.assertEqual(self.bridge.socket.stat().st_mode&0o777,0o600)
        self.assertEqual(self.bridge.token_path.stat().st_mode&0o777,0o600)
        status,body=await self.request('/v1/model-contexts'); self.assertEqual(status,200); self.assertEqual(body,b'{"fixture":true}'); self.assertEqual(self.calls[0][0],self.binding)
    async def test_missing_auth(self):
        status,_=await self.request('/v1/model-contexts',headers={'Content-Type':'application/json'}); self.assertEqual(status,401); self.assertFalse(self.calls)
    async def test_unknown_route(self):
        status,_=await self.request('/v1/tools/AT-11'); self.assertEqual(status,404); self.assertFalse(self.calls)
    async def test_duplicate_json(self):
        status,_=await self.request('/v1/model-contexts',b'{"x":1,"x":2}'); self.assertEqual(status,400); self.assertFalse(self.calls)
    async def test_stream_requires_original_action(self):
        status,_=await self.request('/v1/model-streams'); self.assertEqual(status,400); self.assertFalse(self.calls)
    async def test_stream_original_key_forwarded(self):
        key=str(uuid4()); status,_=await self.request('/v1/model-streams',headers={'Authorization':'Bearer '+self.bridge.token,'Content-Type':'application/json','Idempotency-Key':key}); self.assertEqual(status,200); self.assertEqual(self.calls[0][-1],key)
    async def test_replay_headers_rejected(self):
        status,_=await self.request('/v1/model-contexts',headers={'Authorization':'Bearer '+self.bridge.token,'Content-Type':'application/json','Last-Event-ID':'old'}); self.assertEqual(status,400)
    async def test_query_rejected(self):
        status,_=await self.request('/v1/model-contexts?tenant=override'); self.assertEqual(status,400)
    async def test_arbitrary_result_path_rejected(self):
        status,_=await self.request('/v1/model-results/not-an-id',method='GET',body=b''); self.assertEqual(status,400)
    async def test_close_removes_token(self):
        await self.bridge.close(); self.assertFalse(self.bridge.token_path.exists())
if __name__=='__main__': unittest.main()
