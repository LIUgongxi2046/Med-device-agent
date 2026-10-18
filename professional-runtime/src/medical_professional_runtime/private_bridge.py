"""Authenticated UDS server for the checked-in TypeScript PrivateBridge routes.

Forwards only fixed protocol routes under an immutable host binding. It is not a
business authorization decision: the server rechecks lease/grants per request.
"""
import asyncio
import hmac
import os
from pathlib import Path
import re
import secrets
from aiohttp import web
from .host import RuntimeBlocked, private_directory, strict_json, uuid

class PrivateBridgeServer:
    def __init__(self, socket_root: Path, binding, session: str, forward, maximum_bytes: int):
        private_directory(socket_root)
        self.binding=binding; self.session=uuid(session); self.forward=forward; self.maximum_bytes=maximum_bytes
        self.directory=socket_root / self.session.replace('-','')
        self.directory.mkdir(mode=0o700, exist_ok=False)
        self.socket=self.directory/'s'; self.token_path=self.directory/'t'; self.inventory_path=self.directory/'inventory.json'
        if len(os.fsencode(self.socket)) >= 104: raise RuntimeBlocked('SOCKET_PATH_LIMIT')
        self.token=secrets.token_urlsafe(48)
        fd=os.open(self.token_path,os.O_WRONLY|os.O_CREAT|os.O_EXCL|os.O_NOFOLLOW,0o600)
        with os.fdopen(fd,'w') as stream: stream.write(self.token)
        self.runner=None; self.closed=False; self.active=set(); self.guard=None

    async def start(self):
        app=web.Application(client_max_size=self.maximum_bytes)
        app.router.add_route('*','/{tail:.*}',self.handle)
        self.runner=web.AppRunner(app,access_log=None,shutdown_timeout=3)
        try:
            await self.runner.setup()
            await web.UnixSite(self.runner,str(self.socket)).start()
            self.socket.chmod(0o600)
            return self
        except BaseException:
            await self.close()
            raise

    async def close(self):
        self.closed=True
        for task in list(self.active): task.cancel()
        if self.runner is not None: await self.runner.cleanup()
        # Token/socket are ephemeral auth transport, never retained professional content.
        for path in (self.token_path,self.socket):
            try: path.unlink()
            except FileNotFoundError: pass

    async def handle(self, request):
        if self.closed or not hmac.compare_digest(request.headers.get('Authorization',''),'Bearer '+self.token):
            raise web.HTTPUnauthorized()
        if request.query_string or request.headers.get('Last-Event-ID') is not None: raise web.HTTPBadRequest()
        route=request.path
        if request.method=='POST' and route in ('/v1/model-contexts','/v1/model-streams','/v1/model-cancellations'):
            pass
        elif request.method=='POST' and re.fullmatch(r'/v1/tools/AT-(?:0[1-9]|10)',route):
            pass
        elif request.method=='GET' and route.startswith('/v1/model-results/'):
            try: uuid(route.removeprefix('/v1/model-results/'))
            except (ValueError,RuntimeBlocked): raise web.HTTPBadRequest()
        else: raise web.HTTPNotFound()
        body=await request.read()
        if request.method=='POST':
            if request.content_type!='application/json': raise web.HTTPUnsupportedMediaType()
            try: strict_json(body)
            except RuntimeBlocked: raise web.HTTPBadRequest()
        elif body: raise web.HTTPBadRequest()
        key=request.headers.get('Idempotency-Key')
        if route in ('/v1/model-streams','/v1/model-cancellations'):
            try: uuid(key)
            except (ValueError,TypeError,AttributeError,RuntimeBlocked): raise web.HTTPBadRequest()
        if self.guard is None: raise web.HTTPServiceUnavailable()
        try: self.guard()
        except Exception: raise web.HTTPServiceUnavailable() from None
        task=asyncio.current_task(); self.active.add(task)
        try:
            # Forward is a context-managed, one-shot mTLS request, not a retry loop.
            async with self.forward(self.binding,self.session,request.method,route,body,key) as source:
                if source.status != 200: raise web.HTTPBadGateway()
                content_type=source.headers.get('Content-Type','')
                if content_type.split(';')[0].strip() not in ('application/json','text/event-stream'): raise web.HTTPBadGateway()
                if content_type.startswith('text/event-stream') and (route!='/v1/model-streams' or source.headers.get('Cache-Control')!='no-store'): raise web.HTTPBadGateway()
                response=web.StreamResponse(status=200,headers={'Content-Type':content_type,'Cache-Control':'no-store'})
                await response.prepare(request)
                count=0
                async for chunk in source.content.iter_chunked(65536):
                    count+=len(chunk)
                    if count>self.maximum_bytes: raise RuntimeBlocked('BRIDGE_RESPONSE_LIMIT')
                    await response.write(chunk)
                await response.write_eof()
                return response
        finally: self.active.discard(task)
