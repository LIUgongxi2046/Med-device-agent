"""Explicit N05 worker and independently authenticated supervisor deployment wiring.

Operators supply existing mTLS identities, isolation slots and immutable manifests.
Nothing here creates credentials, namespaces, cgroups, runtime packages or permissions.
N05 registration never modifies the N01/N02 worker registration or queue protocol.
"""
from .host import RuntimeBlocked, digest, sha, uuid
from .supervisor import canonical
from .supervisor_http import PinnedHttps, HttpSupervisorClient, SupervisorHttpService, HttpHostRelay, HostRelayService
from .source_verifier_supervisor import SourceVerifierBackendPlanLoader, SourceVerifierBackendAdmissionReader
from .source_verifier_control import SourceVerifierHttpControl


def require_deployment(protocol, expected_sha256, namespace, queue):
    fields = {'schema_version','revision','namespace','control_task_queue','source_verifier_task_queue','workflow_type','activity','execution_kind','node_kind','model_tool_mode'}
    if not isinstance(protocol, dict) or set(protocol) != fields or any(not isinstance(protocol[key], str) or not protocol[key] for key in fields):
        raise RuntimeBlocked('N05_EXPLICIT_DEPLOYMENT_REQUIRED')
    uuid(protocol['revision'])
    if digest(canonical(protocol)) != sha(expected_sha256) or protocol['schema_version'] != '1.0.0' or protocol['namespace'] != namespace or protocol['source_verifier_task_queue'] != queue or queue == protocol['control_task_queue'] or protocol['workflow_type'] != 'medical.task.run' or protocol['activity'] != 'medical.task.source-verifier.execute.v1' or protocol['execution_kind'] != 'TASK' or protocol['node_kind'] != 'N05' or protocol['model_tool_mode'] != 'SOURCE_VERIFIER_N05_V1':
        raise RuntimeBlocked('N05_EXPLICIT_DEPLOYMENT_REQUIRED')
    return protocol


def require_workflow_extension(protocol, extension, expected_sha256):
    """Cross-pin the distinct Java memo extension, not a renamed host protocol."""
    fields = {'schema_version','revision','base_protocol_sha256','section_writer_extension_sha256','professional_task_queue','execute_activity','prepare_activity','inventory_activity','dispatch_activity','commit_activity','recover_activity','version_marker'}
    if not isinstance(extension, dict) or set(extension) != fields or digest(canonical(extension)) != sha(expected_sha256):
        raise RuntimeBlocked('N05_ORIGINAL_WORKFLOW_EXTENSION_REQUIRED')
    uuid(extension['revision']); sha(extension['base_protocol_sha256']); sha(extension['section_writer_extension_sha256'])
    expected = {'schema_version':'1.0.0', 'professional_task_queue':protocol['source_verifier_task_queue'], 'execute_activity':protocol['activity'], 'version_marker':'task-source-verifier-stage-v1'}
    expected.update({name+'_activity':'medical.run.source-verifier.'+name+'.v1' for name in ('prepare','inventory','dispatch','commit','recover')})
    if any(extension.get(key) != value for key, value in expected.items()):
        raise RuntimeBlocked('N05_ORIGINAL_WORKFLOW_EXTENSION_REQUIRED')
    return extension


def supervisor_service(*, backend_transport, relay_transport, slots, receipt_root, socket_root, worker_certificate_sha256, backend_certificate_sha256):
    """Return a ready aiohttp application owner, with only authenticated lazy plans.

    Transport contexts must remain open for the service lifetime. HTTP server TLS
    must independently require client certificates. Per-request pins still apply.
    """
    if type(backend_transport) is not PinnedHttps or type(relay_transport) is not PinnedHttps or backend_transport is relay_transport:
        raise RuntimeBlocked('N05_DISTINCT_PINNED_TRANSPORTS_REQUIRED')
    loader = SourceVerifierBackendPlanLoader(backend_transport, slots)
    admission = SourceVerifierBackendAdmissionReader(backend_transport)
    return SupervisorHttpService({}, receipt_root, worker_certificate_sha256, backend_certificate_sha256, admission, socket_root, HttpHostRelay(relay_transport), loader)


class SourceVerifierRelayService(HostRelayService):
    def register(self, binding, session):
        uuid(session)
        original = self.bindings.get(session)
        if original is not None and original != binding:
            raise RuntimeBlocked('N05_RELAY_BINDING_CHANGED')
        self.bindings[session] = binding


def host_relay(control, bindings, supervisor_certificate_sha256):
    if type(control) is not SourceVerifierHttpControl:
        raise RuntimeBlocked('N05_CONTROL_REQUIRED')
    return SourceVerifierRelayService(control, bindings, supervisor_certificate_sha256)


async def serve_worker(client, deployment, control, supervisor_transport, relay, stop, concurrency, *, enabled, protocol, protocol_sha256, workflow_extension, workflow_extension_sha256):
    """Actual guarded worker entry. Contexts are already operator-configured, not mocks.

    Open control and supervisor transports here and keep them alive until shutdown.
    The independently deployed supervisor serves supervisor_service(...).application().
    """
    from .source_verifier_activity import SourceVerifierHost, serve
    if enabled is not True or type(control) is not SourceVerifierHttpControl or type(supervisor_transport) is not PinnedHttps:
        raise RuntimeBlocked('N05_EXPLICIT_DEPLOYMENT_REQUIRED')
    queue = protocol.get('source_verifier_task_queue') if isinstance(protocol, dict) else None
    require_deployment(protocol, protocol_sha256, client.namespace, queue)
    require_workflow_extension(protocol, workflow_extension, workflow_extension_sha256)
    async with control, supervisor_transport:
        host = SourceVerifierHost(deployment, control, HttpSupervisorClient(supervisor_transport), relay)
        await serve(client, queue, host, stop, concurrency, enabled=enabled, protocol=protocol, protocol_sha256=protocol_sha256)


async def serve_worker_with_relay(client, deployment, control, supervisor_transport, stop, concurrency, *, enabled, protocol, protocol_sha256, workflow_extension, workflow_extension_sha256, relay_tls, relay_host, relay_port, supervisor_certificate_sha256):
    """Runnable service entry: start the pinned supervisor relay and Temporal worker.

    The listener is bound only after explicit protocol validation. TLS and credentials
    must already be supplied by deployment; no certificates or grants are generated.
    """
    import ipaddress
    import ssl
    from aiohttp import web
    if enabled is not True or type(control) is not SourceVerifierHttpControl or type(supervisor_transport) is not PinnedHttps:
        raise RuntimeBlocked('N05_EXPLICIT_DEPLOYMENT_REQUIRED')
    queue = protocol.get('source_verifier_task_queue') if isinstance(protocol, dict) else None
    require_deployment(protocol, protocol_sha256, client.namespace, queue)
    require_workflow_extension(protocol, workflow_extension, workflow_extension_sha256)
    if not isinstance(relay_tls, ssl.SSLContext) or relay_tls.verify_mode != ssl.CERT_REQUIRED or relay_tls.protocol != ssl.PROTOCOL_TLS_SERVER or type(relay_port) is not int or not 1 <= relay_port <= 65535:
        raise RuntimeBlocked('N05_CLIENT_CERTIFICATE_LISTENER_REQUIRED')
    try:
        ipaddress.ip_address(relay_host)
    except (TypeError, ValueError):
        raise RuntimeBlocked('N05_FIXED_RELAY_ADDRESS_REQUIRED') from None
    relay = host_relay(control, {}, supervisor_certificate_sha256)
    runner = web.AppRunner(relay.application(), access_log=None)
    await runner.setup()
    try:
        await web.TCPSite(runner, host=relay_host, port=relay_port, ssl_context=relay_tls).start()
        await serve_worker(client, deployment, control, supervisor_transport, relay, stop, concurrency, enabled=enabled, protocol=protocol, protocol_sha256=protocol_sha256, workflow_extension=workflow_extension, workflow_extension_sha256=workflow_extension_sha256)
    finally:
        await runner.cleanup()
