"""Private, bounded original receipt journal. No overwrite and no send authority is produced by recovery."""
from __future__ import annotations
import os, stat, json, hashlib, base64
from pathlib import Path
from uuid import UUID, uuid4
import rfc8785
from jsonschema import Draft202012Validator
from medical_contracts.model_stream import ModelResult
from .dispatch_guard import DurableDispatchGuard

SCHEMA_SHA = '99e4a445fac2018857fd989ba2e0d5df1605d31fce28add1865c1498990c935d'

def _json(raw):
    def unique(pairs):
        result = {}
        for k,v in pairs:
            if k in result: raise ValueError('RECEIPT_DUPLICATE_KEY')
            result[k]=v
        return result
    return json.loads(raw,object_pairs_hook=unique,parse_constant=lambda _: (_ for _ in ()).throw(ValueError('RECEIPT_NONFINITE')))

def _id(v):
    if not isinstance(v,str) or str(UUID(v)) != v:raise ValueError('RECEIPT_ID_INVALID')
    return v

class FormalReceiptStore:
    def __init__(self,directory:Path,maximum_bytes:int):
        if type(maximum_bytes) is not int or not 1<=maximum_bytes<=16*1024*1024:raise ValueError('RECEIPT_CAP_REQUIRED')
        self.directory,self.maximum=directory,maximum_bytes
        raw=Path(__file__).with_name('provider-response-capture.schema.json').read_bytes()
        if hashlib.sha256(raw).hexdigest()!=SCHEMA_SHA:raise ValueError('RECEIPT_SCHEMA_MISMATCH')
        schema=_json(raw)
        self.validator=Draft202012Validator(dict(schema, **{'$ref':'#/$defs/CaptureArtifact'}))
    def _open(self):
        fd=os.open(self.directory,os.O_RDONLY|os.O_DIRECTORY|os.O_NOFOLLOW|os.O_CLOEXEC)
        try:self._private(os.fstat(fd),directory=True)
        except BaseException:os.close(fd);raise
        return fd
    @staticmethod
    def _private(info,directory=False):
        if (not (stat.S_ISDIR(info.st_mode) if directory else stat.S_ISREG(info.st_mode)) or info.st_uid!=os.geteuid() or stat.S_IMODE(info.st_mode)!=(0o700 if directory else 0o600) or not directory and info.st_nlink!=1):raise ValueError('RECEIPT_NOT_PRIVATE')
    def _read(self,fd,name):
        f=os.open(name,os.O_RDONLY|os.O_NOFOLLOW|os.O_CLOEXEC,dir_fd=fd)
        try:
            info=os.fstat(f);self._private(info)
            if info.st_size<1 or info.st_size>self.maximum:raise ValueError('RECEIPT_SIZE_INVALID')
            chunks=[];size=0
            while True:
                chunk=os.read(f,min(65536,self.maximum+1-size))
                if not chunk:break
                chunks.append(chunk);size+=len(chunk)
                if size>self.maximum:raise ValueError('RECEIPT_SIZE_INVALID')
            raw=b''.join(chunks)
            after=os.fstat(f)
            stable=lambda x:(x.st_dev,x.st_ino,x.st_size,x.st_mtime_ns,x.st_ctime_ns,x.st_mode,x.st_uid,x.st_nlink)
            if len(raw)!=info.st_size or stable(after)!=stable(info):raise ValueError('RECEIPT_CHANGED')
            return raw
        finally:os.close(f)
    def _publish(self,fd,name,raw):
        if not raw or len(raw)>self.maximum:raise ValueError('RECEIPT_SIZE_INVALID')
        try:
            old=self._read(fd,name)
        except FileNotFoundError:old=None
        if old is not None:
            if old!=raw:raise ValueError('RECEIPT_ORIGINAL_MISMATCH')
            return
        temporary='receipt-pending-'+str(uuid4())
        output=os.open(temporary,os.O_WRONLY|os.O_CREAT|os.O_EXCL|os.O_NOFOLLOW|os.O_CLOEXEC,0o600,dir_fd=fd)
        try:
            position=0
            while position<len(raw):
                written=os.write(output,raw[position:])
                if written<1:raise OSError('RECEIPT_WRITE_UNKNOWN')
                position+=written
            os.fsync(output)
        finally:os.close(output)
        try:
            os.link(temporary,name,src_dir_fd=fd,dst_dir_fd=fd,follow_symlinks=False)
        except FileExistsError:
            # Another writer may have published; retain the temporary file on uncertainty.
            if self._read(fd,name)!=raw:raise ValueError('RECEIPT_ORIGINAL_MISMATCH')
        os.unlink(temporary,dir_fd=fd);os.fsync(fd)
    def _validate(self,request):
        if set(request)!={'schema_version','tenant_id','original_actor_id','model_result','provider_capture'} or request['schema_version']!='1.0.0':raise ValueError('RECEIPT_REQUEST_INVALID')
        _id(request['tenant_id']);_id(request['original_actor_id'])
        result=ModelResult.model_validate(request['model_result']);capture=request['provider_capture'];self.validator.validate(capture);m=capture['metadata']
        if result.invocation_id!=m['invocation_id']:raise ValueError('RECEIPT_INVOCATION_MISMATCH')
        if result.usage is not None and result.usage.source_receipt_id!=m['receipt_id']:raise ValueError('RECEIPT_USAGE_ID_MISMATCH')
        raw=base64.b64decode(capture['body_base64'],validate=True)
        if base64.b64encode(raw).decode()!=capture['body_base64'] or hashlib.sha256(raw).hexdigest()!=m['sha256'] or str(len(raw))!=m['size_bytes']:raise ValueError('RECEIPT_RAW_MISMATCH')
        for key in ('invocation_id','dispatch_attempt_id','receipt_id'):_id(m[key])
        with DurableDispatchGuard(self.directory) as guard:
            if guard.read(m['invocation_id'],m['dispatch_attempt_id'],m['receipt_id'],m['canonical_request_body_sha256']) is None:raise ValueError('RECEIPT_HAS_NO_ORIGINAL_DISPATCH')
        return m
    def save(self,request:dict)->bytes:
        if len(rfc8785.dumps(request))>self.maximum:raise ValueError('RECEIPT_SIZE_INVALID')
        m=self._validate(request);attempt=m['dispatch_attempt_id'];model=rfc8785.dumps(request['model_result']);capture=rfc8785.dumps(request['provider_capture'])
        manifest=dict(schema_version='1.0.0',tenant_id=request['tenant_id'],original_actor_id=request['original_actor_id'],invocation_id=m['invocation_id'],dispatch_attempt_id=attempt,receipt_id=m['receipt_id'],canonical_request_body_sha256=m['canonical_request_body_sha256'],model_result_sha256=hashlib.sha256(model).hexdigest(),provider_capture_sha256=hashlib.sha256(capture).hexdigest(),request_sha256=hashlib.sha256(rfc8785.dumps(request)).hexdigest())
        fd=self._open()
        try:
            self._publish(fd,'receipt-'+attempt+'-model.json',model);self._publish(fd,'receipt-'+attempt+'-capture.json',capture)
            self._publish(fd,'receipt-'+attempt+'-manifest.json',rfc8785.dumps(manifest))
        finally:os.close(fd)
        return self.read(m['invocation_id'],attempt)
    def read(self,invocation:str,attempt:str)->bytes:
        _id(invocation);_id(attempt);fd=self._open()
        try:
            raw_manifest=self._read(fd,'receipt-'+attempt+'-manifest.json');manifest=_json(raw_manifest)
            expected={'schema_version','tenant_id','original_actor_id','invocation_id','dispatch_attempt_id','receipt_id','canonical_request_body_sha256','model_result_sha256','provider_capture_sha256','request_sha256'}
            if set(manifest)!=expected or manifest['schema_version']!='1.0.0' or rfc8785.dumps(manifest)!=raw_manifest or manifest['invocation_id']!=invocation or manifest['dispatch_attempt_id']!=attempt:raise ValueError('RECEIPT_MANIFEST_INVALID')
            model=self._read(fd,'receipt-'+attempt+'-model.json');capture=self._read(fd,'receipt-'+attempt+'-capture.json')
            if hashlib.sha256(model).hexdigest()!=manifest['model_result_sha256'] or hashlib.sha256(capture).hexdigest()!=manifest['provider_capture_sha256']:raise ValueError('RECEIPT_ORIGINAL_MISMATCH')
            request=dict(schema_version='1.0.0',tenant_id=manifest['tenant_id'],original_actor_id=manifest['original_actor_id'],model_result=_json(model),provider_capture=_json(capture));canonical=rfc8785.dumps(request)
            if rfc8785.dumps(request['model_result'])!=model or rfc8785.dumps(request['provider_capture'])!=capture or hashlib.sha256(canonical).hexdigest()!=manifest['request_sha256']:raise ValueError('RECEIPT_REQUEST_MISMATCH')
            if len(canonical)>self.maximum:raise ValueError('RECEIPT_SIZE_INVALID')
            m=self._validate(request)
            if m['receipt_id']!=manifest['receipt_id'] or m['canonical_request_body_sha256']!=manifest['canonical_request_body_sha256']:raise ValueError('RECEIPT_ORIGINAL_IDENTITY_MISMATCH')
            return canonical
        finally:os.close(fd)
