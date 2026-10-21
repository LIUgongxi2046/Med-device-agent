"""Explicit EVAL host/supervisor listeners and Temporal registration; no provisioning."""
import ipaddress
import ssl
import re
from pathlib import Path
from aiohttp import web
from .host import RuntimeBlocked, digest, sha, uuid, file_bytes
from .supervisor import canonical
from .supervisor_http import PinnedHttps, HttpSupervisorClient, SupervisorHttpService, HttpHostRelay, HostRelayService
from .evaluation_control import EvaluationControl
from .evaluation_supervisor import EvaluationBackendPlanLoader, EvaluationBackendAdmissionReader


def require_protocol(protocol, expected_sha256, namespace):
    fields={'schema_version','revision','namespace','control_task_queue','professional_task_queue','workflow_type','execution_kind','job_type','model_tool_mode'}
    if not isinstance(protocol,dict) or set(protocol)!=fields or any(not isinstance(value,str) or not value for value in protocol.values()): raise RuntimeBlocked('EVALUATION_PROTOCOL_REQUIRED')
    uuid(protocol['revision'])
    if any(re.fullmatch(r'[A-Za-z0-9_.-]{1,128}',protocol[key]) is None for key in ('control_task_queue','professional_task_queue')): raise RuntimeBlocked('EVALUATION_QUEUE_INVALID')
    if digest(canonical(protocol))!=sha(expected_sha256) or protocol['namespace']!=namespace or protocol['schema_version']!='1.0.0' or protocol['execution_kind']!='SYSTEM_JOB' or protocol['job_type']!='EVALUATION_VERIFICATION' or protocol['model_tool_mode']!='EVALUATION_VERIFICATION_V1' or protocol['workflow_type']!='medical.system.formal-evaluation.v1' or protocol['control_task_queue']==protocol['professional_task_queue']: raise RuntimeBlocked('EVALUATION_PROTOCOL_MISMATCH')
    return protocol


def require_runner(deployment):
    # A different runner cannot interpret an exact-input document as a TASK prompt.
    approved=Path(__file__).with_name('evaluation_runner.py')
    if file_bytes(deployment.runner,1048576,deployment.runner_sha256)!=file_bytes(approved,1048576): raise RuntimeBlocked('EVALUATION_PINNED_RUNNER_REQUIRED')


def supervisor_service(*,backend_transport,relay_transport,slots,receipt_root,socket_root,worker_certificate_sha256,backend_certificate_sha256):
    if type(backend_transport) is not PinnedHttps or type(relay_transport) is not PinnedHttps or backend_transport is relay_transport: raise RuntimeBlocked('EVALUATION_DISTINCT_CONTROL_TRANSPORTS_REQUIRED')
    approved=file_bytes(Path(__file__).with_name('evaluation_runner.py'),1048576)
    for slot in slots:
        if file_bytes(slot.runner,1048576,slot.runner_sha256)!=approved: raise RuntimeBlocked('EVALUATION_PINNED_RUNNER_REQUIRED')
    loader=EvaluationBackendPlanLoader(backend_transport,slots)
    return SupervisorHttpService({},receipt_root,worker_certificate_sha256,backend_certificate_sha256,EvaluationBackendAdmissionReader(backend_transport),socket_root,HttpHostRelay(relay_transport),loader)


class EvaluationRelayService(HostRelayService):
    def register(self,binding,session):
        uuid(session)
        if session in self.bindings and self.bindings[session]!=binding: raise RuntimeBlocked('EVALUATION_RELAY_OWNER_CHANGED')
        self.bindings[session]=binding


async def serve_worker_with_relay(client,deployments,control_transport,supervisor_transport,stop,concurrency,*,enabled,protocol,protocol_sha256,relay_tls,relay_host,relay_port,supervisor_certificate_sha256):
    from .evaluation_activity import EvaluationHost,serve
    if enabled is not True or type(control_transport) is not PinnedHttps or type(supervisor_transport) is not PinnedHttps or control_transport is supervisor_transport or type(concurrency) is not int or not 1<=concurrency<=256: raise RuntimeBlocked('EVALUATION_EXPLICIT_DEPLOYMENT_REQUIRED')
    require_protocol(protocol,protocol_sha256,client.namespace)
    if not isinstance(relay_tls,ssl.SSLContext) or relay_tls.protocol!=ssl.PROTOCOL_TLS_SERVER or relay_tls.verify_mode!=ssl.CERT_REQUIRED or type(relay_port) is not int or not 1<=relay_port<=65535: raise RuntimeBlocked('EVALUATION_MTLS_LISTENER_REQUIRED')
    ipaddress.ip_address(relay_host)
    for deployment in deployments.values(): require_runner(deployment)
    control=EvaluationControl(control_transport);relay=EvaluationRelayService(control,{},supervisor_certificate_sha256)
    runner=web.AppRunner(relay.application(),access_log=None)
    await runner.setup()
    try:
        async with control_transport,supervisor_transport:
            await web.TCPSite(runner,host=relay_host,port=relay_port,ssl_context=relay_tls).start()
            host=EvaluationHost(deployments,control,HttpSupervisorClient(supervisor_transport),relay)
            await serve(client,protocol,control,host,stop,concurrency)
    finally: await runner.cleanup()
