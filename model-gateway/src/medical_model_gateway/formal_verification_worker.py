"""Deployment entry: python -m medical_model_gateway.formal_verification_worker --config <existing-private-json> [--once].
No keys, grants, certificates or worker identities are created by this command.
"""
from __future__ import annotations
import argparse,asyncio,json,os,ssl,stat
from pathlib import Path
from .formal_invocation_adapter import MutualTlsInvocationGateway,FormalInvocationAdapter,FormalVerificationWorker,InvocationProfile,_parse,_uuid
from .approved_credentials import ApprovedCredentialBinding,ApprovedCredentialResolver
from .raw_response import CapturePolicy


def private_json(path:Path):
    if not path.is_absolute():raise ValueError('WORKER_CONFIG_ABSOLUTE_REQUIRED')
    parent=os.open(path.parent,os.O_RDONLY|os.O_DIRECTORY|os.O_NOFOLLOW|os.O_CLOEXEC)
    try:
        d=os.fstat(parent)
        if d.st_uid!=os.geteuid() or stat.S_IMODE(d.st_mode)!=0o700:raise ValueError('WORKER_CONFIG_DIRECTORY_PRIVATE_REQUIRED')
        fd=os.open(path.name,os.O_RDONLY|os.O_NOFOLLOW|os.O_CLOEXEC,dir_fd=parent)
        try:
            before=os.fstat(fd)
            if not stat.S_ISREG(before.st_mode) or before.st_uid!=os.geteuid() or stat.S_IMODE(before.st_mode)!=0o600 or before.st_nlink!=1 or not 0<before.st_size<=1048576:raise ValueError('WORKER_CONFIG_PRIVATE_REQUIRED')
            data=b''
            while len(data)<=1048576:
                part=os.read(fd,65536)
                if not part:break
                data+=part
            after=os.fstat(fd)
            if len(data)!=before.st_size or (before.st_ino,before.st_size,before.st_mtime_ns,before.st_ctime_ns)!=(after.st_ino,after.st_size,after.st_mtime_ns,after.st_ctime_ns):raise ValueError('WORKER_CONFIG_CHANGED')
            return _parse(data)
        finally:os.close(fd)
    finally:os.close(parent)


def configured(path:Path):
    config=private_json(path)
    keys={'schema_version','tenant_id','origin','ca_file','client_certificate_file','client_private_key_file','credential_bindings_file','durable_directory','maximum_http_bytes','maximum_request_bytes','maximum_response_bytes','maximum_seconds','poll_seconds'}
    if set(config)!=keys or config['schema_version']!='1.0.0':raise ValueError('WORKER_CONFIG_INVALID')
    _uuid(config['tenant_id'])
    if type(config['poll_seconds']) is not int or not 2<=config['poll_seconds']<=300:raise ValueError('WORKER_POLL_BOUND_REQUIRED')
    gateway,adapter,current_config=configured_components(path,config,InvocationProfile.MODEL_VERIFICATION_V1)
    return FormalVerificationWorker(gateway,adapter),config,current_config


def configured_components(path:Path,config:dict,profile:InvocationProfile):
    """Shared deployment loading, not an HTTP app. Callers validate a closed config first."""
    if type(profile) is not InvocationProfile:raise ValueError('FORMAL_INVOCATION_PROFILE_INVALID')
    for key in ('ca_file','client_certificate_file','client_private_key_file','credential_bindings_file','durable_directory'):
        if not Path(config[key]).is_absolute():raise ValueError('WORKER_DEPLOYMENT_PATH_REQUIRED')
    original=config.copy()
    def current_config():
        if private_json(path)!=original:raise ValueError('WORKER_CONFIG_CHANGED_RESTART_REQUIRED')
    bindings_path=Path(config['credential_bindings_file'])
    def rows():
        current_config();data=private_json(bindings_path)
        if set(data)!= {'schema_version','bindings'} or data['schema_version']!='1.0.0' or not isinstance(data['bindings'],list) or not 1<=len(data['bindings'])<=128:raise ValueError('WORKER_CREDENTIAL_DIRECTORY_INVALID')
        result=[]
        for row in data['bindings']:
            if set(row)!={'tenant_id','reference_id','revision','provider','endpoint','path','active'} or row['active'] is not True:raise ValueError('WORKER_APPROVED_CREDENTIAL_REQUIRED')
            _uuid(row['tenant_id']);_uuid(row['reference_id'])
            result.append(ApprovedCredentialBinding(row['tenant_id'],row['reference_id'],row['revision'],row['provider'],row['endpoint'],Path(row['path'])))
        return tuple(result)
    initial=rows()
    resolver=ApprovedCredentialResolver(initial,lambda binding:sum(item==binding for item in rows())==1)
    context=ssl.create_default_context(cafile=config['ca_file']);context.minimum_version=ssl.TLSVersion.TLSv1_3
    context.load_cert_chain(config['client_certificate_file'],config['client_private_key_file'])
    gateway=MutualTlsInvocationGateway(config['origin'],context,config['maximum_http_bytes'],config['maximum_seconds'],profile=profile)
    policy=CapturePolicy(True,config['maximum_request_bytes'],config['maximum_response_bytes'],config['maximum_seconds'])
    adapter=FormalInvocationAdapter(gateway,resolver,Path(config['durable_directory']),policy,config['maximum_http_bytes'],profile=profile)
    return gateway,adapter,current_config

async def run(path:Path,once:bool,recover_manifest:str|None=None):
    worker,config,current=configured(path)
    if recover_manifest is not None:
        import re
        if not re.fullmatch(r'receipt-[0-9a-f-]{36}-manifest\.json',recover_manifest):raise ValueError('WORKER_ORIGINAL_MANIFEST_REQUIRED')
        manifest=private_json(Path(config['durable_directory'])/recover_manifest)
        await worker.adapter.recover(manifest['invocation_id'],manifest['dispatch_attempt_id'])
        return
    while True:
        current()
        await worker.run_once(config['tenant_id'])
        if once:return
        await asyncio.sleep(config['poll_seconds'])

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--config',type=Path,required=True);parser.add_argument('--once',action='store_true');parser.add_argument('--recover-manifest');args=parser.parse_args()
    # Any uncertainty terminates this process. The supervisor must use original receipt recovery,
    # never invent another send. No sensitive provider content or token is printed.
    try:asyncio.run(run(args.config,args.once,args.recover_manifest))
    except KeyboardInterrupt:return
    except Exception:raise SystemExit('FORMAL_WORKER_STOPPED_REVIEW_ORIGINAL_OPERATION') from None

if __name__=='__main__':main()
