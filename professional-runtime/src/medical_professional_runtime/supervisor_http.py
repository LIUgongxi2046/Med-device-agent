"""Bounded certificate-pinned independent supervisor protocol.

No endpoint accepts a PID, process flag, executable path, profile or exit boolean.
Plans are installed by trusted service wiring, not worker HTTP. There is no default
plan or permissive missing-service fallback.
"""
import asyncio
import base64
import binascii
import os
import re
from contextlib import asynccontextmanager
from dataclasses import dataclass
import hashlib
from datetime import datetime, timezone
import json
from pathlib import Path
import ssl
from urllib.parse import urlsplit
import aiohttp
from aiohttp import web
from .host import RuntimeBlocked, digest, sha, uuid, strict_json
from .supervisor import LinuxSupervisor, SupervisorPlan, canonical
from .private_bridge import PrivateBridgeServer
from .request_observation import SupervisorRequestRecorder

class _PinnedResponse(aiohttp.ClientResponse):
    """Retain the actual TLS peer before aiohttp releases a complete small body.
    This is observed on the connected transport, never read from HTTP headers.
    """
    async def start(self,connection):
        transport=connection.transport
        tls=transport.get_extra_info('ssl_object') if transport is not None else None
        if tls is None:raise RuntimeBlocked('SUPERVISOR_CERTIFICATE_MISMATCH')
        certificate=tls.getpeercert(binary_form=True)
        if not certificate:raise RuntimeBlocked('SUPERVISOR_CERTIFICATE_MISMATCH')
        self.medical_peer_sha256=digest(certificate)
        return await super().start(connection)

class _PinnedConnector(aiohttp.TCPConnector):
    """Pinned aiohttp3.14.4 hook checks peer before any HTTP body is sent.
    The supplied SSLContext still performs CA and hostname verification first.
    """
    def __init__(self,*,peer_sha256,**kwargs):
        self.medical_peer_sha256=sha(peer_sha256)
        super().__init__(**kwargs)
    async def _wrap_create_connection(self,*args,**kwargs):
        transport,protocol=await super()._wrap_create_connection(*args,**kwargs)
        try:
            tls=transport.get_extra_info('ssl_object')
            certificate=tls.getpeercert(binary_form=True) if tls is not None else None
            if not certificate or digest(certificate)!=self.medical_peer_sha256:raise RuntimeBlocked('SUPERVISOR_CERTIFICATE_MISMATCH')
            return transport,protocol
        except BaseException:
            transport.close()
            raise

class PinnedHttps:
    def __init__(self,origin,tls,peer_sha256,maximum=16777216):
        parts=urlsplit(origin)
        if parts.scheme!='https' or not parts.hostname or parts.username or parts.password or parts.query or parts.fragment or parts.path not in ('','/'):raise RuntimeBlocked('FIXED_SUPERVISOR_TLS_REQUIRED')
        if tls.verify_mode!=ssl.CERT_REQUIRED or not tls.check_hostname:raise RuntimeBlocked('VERIFIED_SUPERVISOR_TLS_REQUIRED')
        if not 0<maximum<=67108864:raise RuntimeBlocked('SUPERVISOR_LIMIT_INVALID')
        self.origin=origin.rstrip('/');self.tls=tls;self.pin=sha(peer_sha256);self.maximum=maximum;self.client=None
    async def __aenter__(self):
        self.client=aiohttp.ClientSession(connector=_PinnedConnector(ssl=self.tls,peer_sha256=self.pin),response_class=_PinnedResponse,trust_env=False,timeout=aiohttp.ClientTimeout(total=300,connect=5,sock_read=300))
        return self
    async def __aexit__(self,*_):await self.client.close()
    async def request(self,method,path,body=None):
        if self.client is None:raise RuntimeBlocked('SUPERVISOR_TRANSPORT_NOT_STARTED')
        raw=None if body is None else canonical(body)
        if raw is not None and len(raw)>self.maximum:raise RuntimeBlocked('SUPERVISOR_REQUEST_LIMIT')
        async with self.client.request(method,self.origin+path,data=raw,headers={'Content-Type':'application/json'},allow_redirects=False) as response:
            if getattr(response,'medical_peer_sha256',None)!=self.pin:raise RuntimeBlocked('SUPERVISOR_CERTIFICATE_MISMATCH')
            if response.status!=200:raise RuntimeBlocked('SUPERVISOR_REJECTED')
            result=bytearray()
            async for chunk in response.content.iter_chunked(65536):
                result.extend(chunk)
                if len(result)>self.maximum:raise RuntimeBlocked('SUPERVISOR_RESPONSE_LIMIT')
            return bytes(result)

@dataclass(frozen=True)
class RemoteSession:
    binding: object
    session_id:str
    receipt_id:str
    receipt_sha256:str

class HttpSupervisorClient:
    def __init__(self,transport:PinnedHttps):self.transport=transport
    async def open_bridge(self,binding,session,job):
        reply=strict_json(await self.transport.request('POST','/internal/v1/supervision/'+uuid(session)+'/bridge',{'binding':binding.wire()}))
        if set(reply)!={'session_id','binding','socket','token_path','inventory_path'} or reply['session_id']!=session or reply['binding']!=binding.wire():raise RuntimeBlocked('SUPERVISOR_BRIDGE_MISMATCH')
        for key in ('socket','token_path','inventory_path'):
            path=Path(reply[key])
            if not path.is_absolute() or '..' in path.parts:raise RuntimeBlocked('SUPERVISOR_BRIDGE_PATH_INVALID')
        return RemoteBridge(self,binding,session,reply)
    async def prepare(self,binding,session,job,payload,environment):
        reply=strict_json(await self.transport.request('POST','/internal/v1/supervision/'+uuid(session)+'/prepare',{'binding':binding.wire()}))
        if set(reply)!={'session_id','binding','payload_sha256','job_directory','receipt_id','receipt_sha256'} or reply['session_id']!=session or reply['binding']!=binding.wire() or reply['payload_sha256']!=digest(payload) or reply['job_directory']!=str(job):raise RuntimeBlocked('SUPERVISOR_PLAN_MISMATCH')
        return RemoteSession(binding,session,uuid(reply['receipt_id']),sha(reply['receipt_sha256']))
    async def current(self,session):
        reply=strict_json(await self.transport.request('POST','/internal/v1/supervision/'+session.session_id+'/current',{'binding':session.binding.wire()}))
        if reply.get('binding')!=session.binding.wire() or reply.get('session_id')!=session.session_id or reply.get('receipt_id')!=session.receipt_id or digest(canonical(reply))!=session.receipt_sha256:raise RuntimeBlocked('SUPERVISION_CHANGED')
    async def release(self,session,response):
        if response.get('isolation_receipt_id')!=session.receipt_id or response.get('supervision_sha256')!=session.receipt_sha256:raise RuntimeBlocked('ADMISSION_RECEIPT_MISMATCH')
        reply=strict_json(await self.transport.request('POST','/internal/v1/supervision/'+session.session_id+'/release',{'binding':session.binding.wire()}))
        if reply!={'session_id':session.session_id,'state':'RUNNING'}:raise RuntimeBlocked('SUPERVISOR_GATE_NOT_RELEASED')
    async def wait(self,session):
        return await self.transport.request('POST','/internal/v1/supervision/'+session.session_id+'/wait',{'binding':session.binding.wire()})
    async def abort(self,session):
        return await self.abort_session(session.binding,session.session_id)
    async def abort_session(self,binding,session):
        # Exact locator cleanup also handles a lost prepare response. Only the
        # service owns the process; it shares one abort attempt for this session.
        reply=strict_json(await self.transport.request('POST','/internal/v1/supervision/'+uuid(session)+'/abort',{'binding':binding.wire()}))
        if not isinstance(reply,dict) or set(reply)!={'session_id','state'} or reply['session_id']!=session or reply['state'] not in ('UNKNOWN','CLOSED','TERMINATED'):raise RuntimeBlocked('SUPERVISOR_ABORT_UNCONFIRMED')

class RemoteBridge:
    def __init__(self,owner,binding,session,reply):
        self.owner=owner;self.binding=binding;self.session=session
        self.socket=Path(reply['socket']);self.token_path=Path(reply['token_path']);self.inventory_path=Path(reply['inventory_path']);self.guard=None
    async def close(self):
        reply=strict_json(await self.owner.transport.request('POST','/internal/v1/supervision/'+self.session+'/close-bridge',{'binding':self.binding.wire()}))
        if reply!={'session_id':self.session,'bridge':'CLOSED'}:raise RuntimeBlocked('SUPERVISOR_BRIDGE_CLOSURE_UNKNOWN')

class HttpHostRelay:
    """Supervisor identity contacts the worker's bounded relay, never impersonates it."""
    def __init__(self,transport:PinnedHttps):self.transport=transport
    @asynccontextmanager
    async def forward(self,binding,session,method,route,body,key,observed):
        if self.transport.client is None:raise RuntimeBlocked('HOST_RELAY_NOT_STARTED')
        if type(body) is not bytes or len(body)>self.transport.maximum:raise RuntimeBlocked('HOST_RELAY_REQUEST_LIMIT')
        value={'binding':binding.wire(),'session_id':session,'method':method,'route':route,'body_base64':base64.b64encode(body).decode('ascii'),'idempotency_key':key,'model_request_observation':observed}
        encoded=canonical(value)
        if len(encoded)>self.transport.maximum:raise RuntimeBlocked('HOST_RELAY_REQUEST_LIMIT')
        async with self.transport.client.post(self.transport.origin+'/internal/v1/professional-runtime/relay',data=encoded,headers={'Content-Type':'application/json'},allow_redirects=False) as response:
            if getattr(response,'medical_peer_sha256',None)!=self.transport.pin:raise RuntimeBlocked('HOST_RELAY_CERTIFICATE_MISMATCH')
            yield response

class HostRelayService:
    """Separately authenticated supervisor-origin relay; C07 still owns authorization."""
    def __init__(self,control,bindings,supervisor_certificate_sha256,maximum=16777216):
        self.control=control;self.bindings=dict(bindings);self.pin=sha(supervisor_certificate_sha256);self.maximum=maximum
    def application(self):
        app=web.Application(client_max_size=self.maximum);app.router.add_post('/internal/v1/professional-runtime/relay',self.handle);return app
    async def handle(self,request):
        tls=request.transport.get_extra_info('ssl_object') if request.transport else None
        if tls is None or digest(tls.getpeercert(binary_form=True))!=self.pin or request.query_string or request.headers.get('Cookie') or request.headers.get('Origin'):raise web.HTTPUnauthorized()
        try:
            value=strict_json(await request.read())
            if not isinstance(value,dict) or set(value)!={'binding','session_id','method','route','body_base64','idempotency_key','model_request_observation'}:raise web.HTTPBadRequest()
            session=uuid(value['session_id'])
        except (ValueError,TypeError,RuntimeBlocked):raise web.HTTPBadRequest() from None
        binding=self.bindings.get(session)
        if binding is None or binding.wire()!=value['binding']:raise web.HTTPForbidden()
        route=value['route'];method=value['method']
        if not isinstance(route,str) or not isinstance(method,str):raise web.HTTPBadRequest()
        try:
            if not ((method=='POST' and (route in ('/v1/model-contexts','/v1/model-streams','/v1/model-cancellations') or re.fullmatch(r'/v1/tools/AT-(?:0[1-9]|10)',route))) or (method=='GET' and route.startswith('/v1/model-results/') and uuid(route.removeprefix('/v1/model-results/')))):raise web.HTTPBadRequest()
            if not isinstance(value['body_base64'],str) or len(value['body_base64'])>4*((self.maximum+2)//3):raise web.HTTPBadRequest()
            body=base64.b64decode(value['body_base64'],validate=True)
        except (ValueError,TypeError,binascii.Error,RuntimeBlocked):raise web.HTTPBadRequest() from None
        if len(body)>self.maximum:raise web.HTTPRequestEntityTooLarge(max_size=self.maximum,actual_size=len(body))
        async with self.control.forward(binding,session,method,route,body,value['idempotency_key'],value['model_request_observation']) as upstream:
            if upstream.status!=200:raise web.HTTPBadGateway()
            content_type=upstream.headers.get('Content-Type','')
            if content_type.split(';')[0] not in ('application/json','text/event-stream'):raise web.HTTPBadGateway()
            response=web.StreamResponse(headers={'Content-Type':content_type,'Cache-Control':'no-store'});await response.prepare(request);count=0
            async for chunk in upstream.content.iter_chunked(65536):
                count+=len(chunk)
                if count>self.maximum:raise RuntimeBlocked('HOST_RELAY_RESPONSE_LIMIT')
                await response.write(chunk)
            await response.write_eof();return response

class BackendAdmissionReader:
    """Reads actual083/current backend admission via a distinct pinned service channel."""
    def __init__(self,transport:PinnedHttps):self.transport=transport
    async def require(self,supervisor):
        value={'binding':dict(supervisor.plan.binding),'session_id':supervisor.plan.session_id,'isolation_receipt_id':supervisor.admitted['receipt_id'],'supervision_sha256':digest(canonical(supervisor.admitted)),'process_unit_sha256':digest(canonical(supervisor.snapshot.wire())),'supervisor_instance_id':supervisor.instance}
        reply=strict_json(await self.transport.request('POST','/internal/v1/professional-runtime/supervisor-admission',value))
        if not isinstance(reply,dict) or set(reply)!=set(value)|{'admission','expires_at'} or any(reply.get(k)!=v for k,v in (value|{'admission':'CURRENT'}).items()):raise RuntimeBlocked('ORIGINAL_BACKEND_ADMISSION_REQUIRED')
        require_expiry(reply['expires_at'])

class PlanDraftBackendAdmissionReader:
    """Reads actual083/current backend admission via a distinct pinned service channel."""
    def __init__(self,transport:PinnedHttps):self.transport=transport
    async def require(self,supervisor):
        value={'binding':dict(supervisor.plan.binding),'session_id':supervisor.plan.session_id,'isolation_receipt_id':supervisor.admitted['receipt_id'],'supervision_sha256':digest(canonical(supervisor.admitted)),'process_unit_sha256':digest(canonical(supervisor.snapshot.wire())),'supervisor_instance_id':supervisor.instance}
        reply=strict_json(await self.transport.request('POST','/internal/v1/plan-draft-runtime/supervisor-admission',value))
        if not isinstance(reply,dict) or set(reply)!=set(value)|{'admission','expires_at'} or any(reply.get(k)!=v for k,v in (value|{'admission':'CURRENT'}).items()):raise RuntimeBlocked('ORIGINAL_BACKEND_ADMISSION_REQUIRED')
        require_expiry(reply['expires_at'])

def require_expiry(value):
    try:
        until=datetime.fromisoformat(value.replace('Z','+00:00'));now=datetime.now(timezone.utc)
        if until.tzinfo is None or not 0<(until-now).total_seconds()<=5:raise RuntimeBlocked('BACKEND_ADMISSION_EXPIRED')
        return until
    except (TypeError,ValueError,AttributeError):raise RuntimeBlocked('BACKEND_ADMISSION_EXPIRED') from None

class SupervisorHttpService:
    def __init__(self,plans:dict[str,SupervisorPlan],receipt_root:Path,worker_certificate_sha256:str,backend_certificate_sha256:str,admission:BackendAdmissionReader,socket_root:Path|None=None,relay:HttpHostRelay|None=None,loader=None):
        from .evaluation_supervisor import EvaluationBackendAdmissionReader,EvaluationBackendPlanLoader
        from .document_review_supervisor import DocumentReviewBackendAdmissionReader,DocumentReviewBackendPlanLoader
        from .source_verifier_supervisor import SourceVerifierBackendAdmissionReader,SourceVerifierBackendPlanLoader
        from .section_writer_supervisor import SectionWriterBackendAdmissionReader,SectionWriterBackendPlanLoader
        if type(admission) not in (EvaluationBackendAdmissionReader,BackendAdmissionReader,PlanDraftBackendAdmissionReader,SectionWriterBackendAdmissionReader,SourceVerifierBackendAdmissionReader,DocumentReviewBackendAdmissionReader):raise RuntimeBlocked('INDEPENDENT_ADMISSION_READER_REQUIRED')
        from .supervisor_plan import BackendPlanLoader,PlanDraftBackendPlanLoader
        expected_loader=EvaluationBackendPlanLoader if type(admission) is EvaluationBackendAdmissionReader else DocumentReviewBackendPlanLoader if type(admission) is DocumentReviewBackendAdmissionReader else SourceVerifierBackendPlanLoader if type(admission) is SourceVerifierBackendAdmissionReader else SectionWriterBackendPlanLoader if type(admission) is SectionWriterBackendAdmissionReader else PlanDraftBackendPlanLoader if type(admission) is PlanDraftBackendAdmissionReader else BackendPlanLoader
        if loader is not None and type(loader) is not expected_loader:raise RuntimeBlocked('INDEPENDENT_PLAN_LOADER_REQUIRED')
        if type(admission) is PlanDraftBackendAdmissionReader and plans:raise RuntimeBlocked('N01_AUTHENTICATED_PLAN_LOADER_REQUIRED')
        if type(admission) is SectionWriterBackendAdmissionReader and (plans or loader is None):raise RuntimeBlocked('N04_AUTHENTICATED_PLAN_LOADER_REQUIRED')
        if type(admission) is SourceVerifierBackendAdmissionReader and (plans or loader is None):raise RuntimeBlocked('N05_AUTHENTICATED_PLAN_LOADER_REQUIRED')
        if type(admission) is DocumentReviewBackendAdmissionReader and (plans or loader is None):raise RuntimeBlocked('N07_AUTHENTICATED_PLAN_LOADER_REQUIRED')
        if type(admission) is EvaluationBackendAdmissionReader and (plans or loader is None):raise RuntimeBlocked('EVALUATION_AUTHENTICATED_PLAN_LOADER_REQUIRED')
        self.loader=loader
        self.plans=dict(plans);self.root=receipt_root;self.worker=sha(worker_certificate_sha256);self.backend=sha(backend_certificate_sha256);self.admission=admission
        if self.worker==self.backend:raise RuntimeBlocked('DISTINCT_CONTROL_IDENTITY_REQUIRED')
        self.sessions={};self.waits={};self.locks={};self.bridges={};self.recorders={};self.socket_root=socket_root;self.relay=relay
        for session,plan in self.plans.items():
            if session!=plan.session_id:raise RuntimeBlocked('SUPERVISOR_PLAN_MISMATCH')
    def application(self):
        app=web.Application(client_max_size=65536)
        app.router.add_route('*','/internal/v1/supervision/{session}/{operation}',self.handle)
        return app
    def _authenticate(self,request,pin):
        tls=request.transport.get_extra_info('ssl_object') if request.transport else None
        if tls is None or digest(tls.getpeercert(binary_form=True))!=pin or request.headers.get('Cookie') or request.headers.get('Origin'):raise web.HTTPUnauthorized()
    async def handle(self,request):
        session=uuid(request.match_info['session']);operation=request.match_info['operation']
        if request.query_string:raise web.HTTPBadRequest()
        reads=operation in ('ADMITTED','CLOSED','TERMINATED','export','admission-original') or operation.startswith('request-')
        self._authenticate(request,self.backend if reads or operation=='terminate' else self.worker)
        if session not in self.plans and (operation!='bridge' or self.loader is None):raise web.HTTPNotFound()
        if reads:
            if request.method!='GET' or await request.read():raise web.HTTPBadRequest()
        else:
            if request.method!='POST' or operation not in ('prepare','release','wait','abort','current','bridge','close-bridge','terminate'):raise web.HTTPNotFound()
            body=strict_json(await request.read())
            if not isinstance(body,dict) or set(body)!={'binding'} or session in self.plans and body!={'binding':dict(self.plans[session].binding)}:raise web.HTTPBadRequest()
        try:
            if operation=='bridge':
                lock=self.locks.setdefault(session,asyncio.Lock())
                async with lock:
                    if session in self.sessions or self.socket_root is None or type(self.relay) is not HttpHostRelay:raise RuntimeBlocked('SUPERVISOR_BRIDGE_WIRING_REQUIRED')
                    if session not in self.plans:self.plans[session]=await self.loader.load(session,body['binding'],self.socket_root)
                    supervised=LinuxSupervisor(self.plans[session],self.root);self.sessions[session]=supervised
                    from .host import Binding
                    b=supervised.plan.binding
                    binding=Binding(b['tenant_id'],b['run_id'],b['step_id'],b['action_id'],b['lease_generation'],b['input_sha256'],b['step_envelope_sha256'])
                    bridge=await PrivateBridgeServer(self.socket_root,binding,session,self.relay.forward,supervised.plan.maximum_bytes,runtime_gid=supervised.plan.policy.gid).start()
                    # The bridge is supervisor-owned. Worker can read its transport
                    # token, but SO_PEERCRED still rejects every non-runtime process.
                    for path in (bridge.directory,bridge.socket,bridge.token_path):os.chown(path,supervised.plan.policy.uid,supervised.plan.policy.gid)
                    def guard():
                        if supervised.state!='RUNNING':raise RuntimeBlocked('SUPERVISOR_GATE_NOT_RELEASED')
                        supervised.current()
                    bridge.guard=guard
                    # EVAL permits 256 recorded tool rounds plus its final model result.
                    from .evaluation_supervisor import EvaluationSupervisorPlan
                    recorder=SupervisorRequestRecorder(supervised,maximum_requests=257 if type(supervised.plan) is EvaluationSupervisorPlan else 256);bridge.model_observer=recorder
                    self.bridges[session]=bridge;self.recorders[session]=recorder
                    reply={'session_id':session,'binding':dict(supervised.plan.binding),'socket':str(bridge.socket),'token_path':str(bridge.token_path),'inventory_path':str(bridge.inventory_path)}
            elif operation=='prepare':
                lock=self.locks.setdefault(session,asyncio.Lock())
                async with lock:
                    supervised=self.sessions.get(session)
                    if supervised is None or supervised.state!='NEW' or session not in self.bridges:raise RuntimeBlocked('SUPERVISOR_BRIDGE_WIRING_REQUIRED')
                    if self.loader is None:raise RuntimeBlocked('INDEPENDENT_PLAN_LOADER_REQUIRED')
                    await self.loader.require_current(supervised.plan)
                    evidence=await supervised.prepare()
                    reply={'session_id':session,'binding':dict(supervised.plan.binding),'payload_sha256':digest(supervised.plan.payload),'job_directory':str(supervised.plan.job),'receipt_id':evidence['receipt_id'],'receipt_sha256':digest(canonical(evidence))}
            else:
                supervised=self.sessions.get(session)
                if supervised is None:
                    if operation not in ('CLOSED','TERMINATED','export','terminate','admission-original'):raise RuntimeBlocked('SUPERVISOR_RESTART_RECOVERY_REQUIRED')
                    recover_termination=operation in ('TERMINATED','terminate') or (operation=='admission-original' and (self.root/session/'terminated.json').exists())
                    supervised=(LinuxSupervisor.recover_terminated if recover_termination else LinuxSupervisor.recover_closed)(self.plans[session],self.root);self.sessions[session]=supervised
                if operation=='admission-original':
                    reply=supervised.receipt('ADMITTED_ORIGINAL')
                elif operation=='close-bridge':
                    if session not in self.bridges:raise RuntimeBlocked('SUPERVISOR_BRIDGE_MISSING')
                    await self.bridges[session].close();reply={'session_id':session,'bridge':'CLOSED'}
                elif operation.startswith('request-'):
                    recorder=self.recorders.get(session)
                    if recorder is None:raise RuntimeBlocked('SDK_REQUEST_ORIGINAL_MISSING')
                    reply=recorder.read(uuid(operation.removeprefix('request-')))
                elif operation=='release':
                    await self.admission.require(supervised);supervised.release();reply={'session_id':session,'state':'RUNNING'}
                elif operation=='current':reply=supervised.current()
                elif operation=='abort':await supervised.abort();reply={'session_id':session,'state':supervised.state}
                elif operation=='terminate':
                    # Serialize with preparation: never race the admission snapshot
                    # or allow a later prepare to start after backend termination.
                    lock=self.locks.setdefault(session,asyncio.Lock())
                    async with lock:
                        reply=await supervised.terminate()
                elif operation=='wait':
                    if session not in self.waits:self.waits[session]=asyncio.create_task(supervised.finish())
                    raw=await asyncio.shield(self.waits[session]);return web.Response(body=raw,content_type='application/json',headers={'Cache-Control':'no-store'})
                elif operation=='export':return web.Response(body=supervised.session_export(),content_type='application/json',headers={'Cache-Control':'no-store'})
                else:reply=supervised.receipt(operation)
            return web.Response(body=canonical(reply),content_type='application/json',headers={'Cache-Control':'no-store'})
        except (RuntimeBlocked,InspectionBlocked):raise web.HTTPConflict(text='SUPERVISION_UNAVAILABLE') from None

from .linux_inspection import InspectionBlocked
