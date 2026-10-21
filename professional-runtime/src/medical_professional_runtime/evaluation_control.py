"""Pinned service-only EVAL channel. No retry or caller-supplied output evidence."""
import re
from contextlib import asynccontextmanager
from .host import RuntimeBlocked, strict_json, uuid
from .supervisor import canonical
from .supervisor_http import PinnedHttps

PREFIX = '/internal/v1/evaluation-runtime/'


def snake(value):
    if isinstance(value, list): return [snake(item) for item in value]
    if isinstance(value, dict): return {re.sub(r'(?<!^)(?=[A-Z])', '_', key).lower():snake(item) for key,item in value.items()}
    return value


def camel(value):
    if isinstance(value, list): return [camel(item) for item in value]
    if isinstance(value, dict): return {re.sub(r'_([a-z])', lambda match:match[1].upper(), key):camel(item) for key,item in value.items()}
    return value


class JsonResponse:
    status = 200
    headers = {'Content-Type':'application/json','Cache-Control':'no-store'}
    def __init__(self, value): self.raw = canonical(value); self.content = self
    async def iter_chunked(self, size):
        for offset in range(0,len(self.raw),size): yield self.raw[offset:offset+size]


class EvaluationControl:
    def __init__(self, transport):
        if type(transport) is not PinnedHttps: raise RuntimeBlocked('EVALUATION_PINNED_CONTROL_REQUIRED')
        self.transport = transport; self.routes = {}

    async def request(self, operation, value):
        if operation not in ('await-launch','open','admit','heartbeat','finish','completed-original','recover-node','complete-cell','cancel-plan','cancel-cell','close-admission'): raise RuntimeBlocked('EVALUATION_OPERATION_INVALID')
        return await self._request(operation, value)

    async def _request(self, operation, value):
        reply = strict_json(await self.transport.request('POST', PREFIX+operation, value))
        if not isinstance(reply,dict) or set(reply) != {'schema_version','request_id','resource_revision','data'} or reply['schema_version'] != '1.0.0': raise RuntimeBlocked('EVALUATION_RESPONSE_ENVELOPE_INVALID')
        uuid(reply['request_id'])
        return reply['data']

    def register(self, binding, session, reference, step):
        route = {'schema_version':'1.0.0','reference':snake(reference),'step':snake(step),'session_id':uuid(session)}
        old = self.routes.get(session)
        if old is not None and old != (binding,route): raise RuntimeBlocked('EVALUATION_SESSION_ROUTE_CHANGED')
        self.routes[session] = (binding,route)

    def route(self, binding, session):
        original = self.routes.get(session)
        if original is None or original[0] != binding: raise RuntimeBlocked('EVALUATION_ORIGINAL_ROUTE_REQUIRED')
        return dict(original[1])

    async def heartbeat(self,binding,session):
        value = await self.request('heartbeat', self.route(binding,session))
        if value != {'session_id':session,'state':'CURRENT'}: raise RuntimeBlocked('EVALUATION_LEASE_NOT_CURRENT')

    @asynccontextmanager
    async def forward(self,binding,session,method,route,body,key,observed=None):
        if method != 'POST' or key is not None: raise RuntimeBlocked('EVALUATION_BRIDGE_ROUTE_INVALID')
        await self.heartbeat(binding,session)
        request = self.route(binding,session)
        if route == '/v1/model-contexts':
            # Only supervisor-owned SO_PEERCRED observation identifies SDK bytes.
            # Body is not sent as a claim; backend retrieves the original receipt.
            if not isinstance(observed,dict) or set(observed) != {'request_id','ordinal','receipt_id','sha256'}: raise RuntimeBlocked('EVALUATION_OBSERVATION_REQUIRED')
            request['request_id'] = uuid(observed['request_id'])
            response = await self._request('model',request)
        elif re.fullmatch(r'/v1/tools/AT-(?:0[1-9]|10)',route) and observed is None:
            request['tool_id'] = route.rsplit('/',1)[1]; request['request'] = strict_json(body)
            response = await self._request('tool',request)
        else: raise RuntimeBlocked('EVALUATION_BRIDGE_ROUTE_INVALID')
        yield JsonResponse(response)
