"""Socket-free denial-path tests. These do not replace live UDS transport tests."""
from pathlib import Path
import tempfile
import unittest
from uuid import uuid4
from aiohttp import web
from aiohttp.test_utils import make_mocked_request
from medical_professional_runtime.private_bridge import PrivateBridgeServer
from medical_professional_runtime.host import Binding

class RouteTest(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self):
        self.temp=tempfile.TemporaryDirectory(prefix='jroute-')
        async def forbidden(*_): self.fail('Denial must not reach upstream')
        self.bridge=PrivateBridgeServer(Path(self.temp.name),Binding(*[str(uuid4()) for _ in range(4)],1,'a'*64,'b'*64),str(uuid4()),forbidden,1024)
    async def asyncTearDown(self): await self.bridge.close(); self.temp.cleanup()
    async def denied(self,path,expected,method='POST',extra=None,authenticated=True):
        headers={'Authorization':'Bearer '+self.bridge.token} if authenticated else {}
        headers.update(extra or {})
        with self.assertRaises(expected): await self.bridge.handle(make_mocked_request(method,path,headers=headers))
    async def test_no_auth(self): await self.denied('/v1/model-contexts',web.HTTPUnauthorized,authenticated=False)
    async def test_unknown_tool(self): await self.denied('/v1/tools/AT-11',web.HTTPNotFound)
    async def test_wrong_method(self): await self.denied('/v1/model-contexts',web.HTTPNotFound,'PUT')
    async def test_query(self): await self.denied('/v1/model-contexts?tenant=other',web.HTTPBadRequest)
    async def test_replay(self): await self.denied('/v1/model-streams',web.HTTPBadRequest,extra={'Last-Event-ID':'old'})
    async def test_invalid_artifact(self): await self.denied('/v1/model-results/secret',web.HTTPBadRequest,'GET')
    async def test_closed(self):
        await self.bridge.close(); await self.denied('/v1/model-contexts',web.HTTPUnauthorized)
    async def test_private_token_file_and_cleanup(self):
        self.assertEqual(self.bridge.token_path.stat().st_mode&0o777,0o600)
        await self.bridge.close(); self.assertFalse(self.bridge.token_path.exists())
if __name__=='__main__': unittest.main()
