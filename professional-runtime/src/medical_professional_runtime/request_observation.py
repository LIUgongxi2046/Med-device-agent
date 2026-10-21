"""Supervisor-owned UDS origin records, not host-submitted request attestations."""
import asyncio
import base64
import os
import socket
import struct
from importlib.resources import files
from jsonschema import Draft202012Validator, FormatChecker
from uuid import uuid4
from .host import RuntimeBlocked, file_bytes, strict_json
from .supervisor import canonical, digest

PROJECTION_REF='urn:medical:task-invocation-context:1.0.0#/$defs/SdkProjection'
PROJECTION_VERSION='1.0.0'
PROJECTION_SCHEMA_SHA256='30378eaad8c98486077b47d995a4da5cd218d57eab20d86d69ddabda1139a01b'

class SupervisorRequestRecorder:
    def __init__(self,supervisor,maximum_request_bytes=1048576,maximum_requests=256,maximum_retained_bytes=16777216):
        if not 0<maximum_request_bytes<=16777216 or not 0<maximum_requests<=10000 or not maximum_request_bytes<=maximum_retained_bytes<=67108864:raise RuntimeBlocked('REQUEST_OBSERVATION_LIMIT_INVALID')
        self.supervisor=supervisor;self.maximum=maximum_request_bytes;self.count_limit=maximum_requests;self.total_limit=maximum_retained_bytes
        # Select only from the independent authenticated prepared envelope, never
        # from the SDK request body or a worker-supplied purpose flag.
        envelope=strict_json(supervisor.plan.registered_envelope)
        n01=envelope.get('execution_kind')=='SYSTEM_JOB' and envelope.get('job_type')=='PLAN_DRAFT' and envelope.get('node_kind')=='N01'
        self.projection_ref='urn:medical:task-plan-draft-invocation:1.0.0#/$defs/SdkProjection' if n01 else PROJECTION_REF
        self.projection_sha256='95e321f8044094fe78aece3e0ef20c76d8e77b5776992d4fec539e7d6406313f' if n01 else PROJECTION_SCHEMA_SHA256
        schema_name='task-plan-draft-invocation.schema.json' if n01 else 'task-invocation-context.schema.json'
        n04=envelope.get('execution_kind')=='TASK' and envelope.get('node_kind')=='N04'
        if n04:
            self.projection_ref='urn:medical:task-section-writer-invocation:1.0.0#/$defs/SdkProjection'
            self.projection_sha256='36d8017f5797985092bae6e6a9f9e23254b23a31975aedbb757bfd8fee96650b'
            schema_name='task-section-writer-invocation.schema.json'
        if envelope.get('execution_kind')=='TASK' and envelope.get('node_kind')=='N05':
            self.projection_ref='urn:medical:task-source-verifier-invocation:1.0.0#/$defs/SdkProjection'
            self.projection_sha256='769dfe349a15bc44e820c21911923332af9a3bdc5aaa03b0bc8bef13dca8e4d1'
            schema_name='task-source-verifier-invocation.schema.json'
        if envelope.get('execution_kind')=='SYSTEM_JOB' and envelope.get('job_type')=='CHECK_DOCUMENT' and envelope.get('node_kind')=='N07':
            self.projection_ref='urn:medical:document-review-invocation:1.0.0#/$defs/SdkProjection'
            self.projection_sha256='ba630ee8c7455645c80f5542c476684083a9026935d63c3159b599065d5af364'
            schema_name='document-review-invocation.schema.json'
        if envelope.get('execution_kind')=='SYSTEM_JOB' and envelope.get('job_type')=='EVALUATION_VERIFICATION':
            self.projection_ref='urn:medical:formal-evaluation-invocation:1.0.0#/$defs/SdkProjection'
            self.projection_sha256='b20e37fb356d49acf4137a26e54d7089630076d9f13870e0e43b8548db656f56'
            schema_name='formal-evaluation-invocation.schema.json'
        schema_bytes=files('medical_professional_runtime').joinpath('schemas/'+schema_name).read_bytes()
        if digest(schema_bytes)!=self.projection_sha256:raise RuntimeBlocked('SDK_PROJECTION_SCHEMA_MISMATCH')
        document=strict_json(schema_bytes)
        self.schema=Draft202012Validator({'$schema':'https://json-schema.org/draft/2020-12/schema','$ref':'#/$defs/SdkProjection','$defs':document['$defs']},format_checker=FormatChecker())
        self.ordinal=0;self.total=0;self.lock=asyncio.Lock();self.records={};self.failed=False

    async def capture(self,request,body):
        # PrivateBridgeServer checks framing before reading. Enforce again before any
        # base64 expansion, canonicalization or persistent allocation.
        if request.method!='POST' or request.path!='/v1/model-contexts' or type(body) is not bytes or not 0<len(body)<=self.maximum:raise RuntimeBlocked('SDK_REQUEST_OBSERVATION_INVALID')
        if request.content_length is None or request.content_length!=len(body) or request.headers.get('Transfer-Encoding') is not None or request.headers.get('Content-Encoding') is not None:raise RuntimeBlocked('SDK_REQUEST_FRAMING_REQUIRED')
        transport=request.transport;sock=transport.get_extra_info('socket') if transport else None
        if sock is None:raise RuntimeBlocked('SDK_PEER_CREDENTIALS_REQUIRED')
        raw=sock.getsockopt(socket.SOL_SOCKET,socket.SO_PEERCRED,struct.calcsize('3i'))
        if len(raw)!=struct.calcsize('3i'):raise RuntimeBlocked('SDK_PEER_CREDENTIALS_REQUIRED')
        pid,uid,gid=struct.unpack('3i',raw)
        async with self.lock:
            if self.failed:raise RuntimeBlocked('REQUEST_OBSERVATION_RECOVERY_REQUIRED')
            reserved=4*((len(body)+2)//3)+4096
            if self.ordinal>=self.count_limit or self.total+reserved>self.total_limit:raise RuntimeBlocked('REQUEST_OBSERVATION_RETENTION_LIMIT')
            supervisor=self.supervisor;admitted=supervisor.current()
            processes=supervisor.plan.inspector.snapshot().wire()['processes']
            peers=[p for p in processes if p['pid']==pid and p['uid']==uid and p['gid']==gid]
            runtime_hashes={p.exe_sha256 for p in supervisor.plan.inspector.config.executable_profiles if p.role=='runtime'}
            if len(peers)!=1 or peers[0]['exe_sha256'] not in runtime_hashes:raise RuntimeBlocked('SDK_PEER_NOT_INSPECTED_RUNTIME')
            peer=peers[0]
            # Keep the exact on-wire canonical projection. Parsing rejects duplicate
            # keys/non-JSON; it does not interpret or approve model/source semantics.
            self.schema.validate(strict_json(body))  # Preserve every declared dispatch field and exact bytes.
            request_id=str(uuid4());ordinal=self.ordinal+1
            evidence={'schema_version':'1.0.0','receipt_id':str(uuid4()),'request_id':request_id,'ordinal':ordinal,'session_id':supervisor.plan.session_id,'binding':dict(supervisor.plan.binding),'deployment_sha256':supervisor.plan.deployment_sha256,'supervision_receipt_id':admitted['receipt_id'],'supervision_sha256':digest(canonical(admitted)),'method':'POST','route':'/v1/model-contexts','raw_body_sha256':digest(body),'raw_size_bytes':len(body),'projection_schema_ref':self.projection_ref,'projection_schema_version':PROJECTION_VERSION,'projection_schema_sha256':self.projection_sha256,'body_sha256':digest(body),'body_base64':base64.b64encode(body).decode('ascii'),'peer_pid':pid,'peer_start_ticks':peer['start_ticks'],'peer_exe_sha256':peer['exe_sha256']}
            encoded=canonical(evidence)
            if len(encoded)>supervisor.plan.maximum_bytes:raise RuntimeBlocked('REQUEST_OBSERVATION_LIMIT')
            # Reserve sequence/bytes BEFORE IO. An uncertain write consumes its slot;
            # it is never retried with another ID or silently omitted from recovery.
            self.ordinal=ordinal;self.total+=len(encoded)
            try:
                supervisor._persist('request-'+request_id+'.json',encoded)
                supervisor.current()
            except BaseException:
                self.failed=True;supervisor.invalidate('SDK_REQUEST_OBSERVATION_UNCERTAIN')
                raise
            self.records[request_id]=digest(encoded)
            return {'request_id':request_id,'ordinal':ordinal,'receipt_id':evidence['receipt_id'],'sha256':digest(encoded)}

    def read(self,request_id):
        self.supervisor.current()
        expected=self.records.get(request_id)
        if expected is None:raise RuntimeBlocked('SDK_REQUEST_ORIGINAL_MISSING')
        return strict_json(file_bytes(self.supervisor.root/('request-'+request_id+'.json'),self.supervisor.plan.maximum_bytes,expected))
