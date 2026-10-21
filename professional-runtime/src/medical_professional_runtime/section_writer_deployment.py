"""Explicit N04 worker and independently authenticated supervisor deployment wiring.

Operators supply existing mTLS identities, isolation slots and immutable manifests.
Nothing here creates credentials, namespaces, cgroups, runtime packages or permissions.
N04 registration never modifies the N01/N02 worker registration or queue protocol.
"""
from .host import RuntimeBlocked, digest, sha, uuid
from .supervisor import canonical
from .supervisor_http import PinnedHttps, HttpSupervisorClient, SupervisorHttpService, HttpHostRelay, HostRelayService
from .section_writer_supervisor import SectionWriterBackendPlanLoader, SectionWriterBackendAdmissionReader
from .section_writer_control import SectionWriterHttpControl


def require_deployment(protocol, expected_sha256, namespace, queue):
    fields = {'schema_version','revision','namespace','control_task_queue','section_writer_task_queue','workflow_type','activity','execution_kind','node_kind','model_tool_mode'}
    if not isinstance(protocol, dict) or set(protocol) != fields or any(not isinstance(protocol[key], str) or not protocol[key] for key in fields):
        raise RuntimeBlocked('N04_EXPLICIT_DEPLOYMENT_REQUIRED')
    uuid(protocol['revision'])
    if digest(canonical(protocol)) != sha(expected_sha256) or protocol['schema_version'] != '1.0.0' or protocol['namespace'] != namespace or protocol['section_writer_task_queue'] != queue or queue == protocol['control_task_queue'] or protocol['workflow_type'] != 'medical.task.run' or protocol['activity'] != 'medical.task.section-writer.execute.v1' or protocol['execution_kind'] != 'TASK' or protocol['node_kind'] != 'N04' or protocol['model_tool_mode'] != 'SECTION_WRITER_N04_V1':
        raise RuntimeBlocked('N04_EXPLICIT_DEPLOYMENT_REQUIRED')
    return protocol


def supervisor_service(*, backend_transport, relay_transport, slots, receipt_root, socket_root, worker_certificate_sha256, backend_certificate_sha256):
    """Return a ready aiohttp application owner, with only authenticated lazy plans.

    Transport contexts must remain open for the service lifetime. HTTP server TLS
    must independently require client certificates. Per-request pins still apply.
    """
    if type(backend_transport) is not PinnedHttps or type(relay_transport) is not PinnedHttps or backend_transport is relay_transport:
        raise RuntimeBlocked('N04_DISTINCT_PINNED_TRANSPORTS_REQUIRED')
    loader = SectionWriterBackendPlanLoader(backend_transport, slots)
    admission = SectionWriterBackendAdmissionReader(backend_transport)
    return SupervisorHttpService({}, receipt_root, worker_certificate_sha256, backend_certificate_sha256, admission, socket_root, HttpHostRelay(relay_transport), loader)


class SectionWriterRelayService(HostRelayService):
    def register(self, binding, session):
        uuid(session)
        original = self.bindings.get(session)
        if original is not None and original != binding:
            raise RuntimeBlocked('N04_RELAY_BINDING_CHANGED')
        self.bindings[session] = binding


def host_relay(control, bindings, supervisor_certificate_sha256):
    if type(control) is not SectionWriterHttpControl:
        raise RuntimeBlocked('N04_CONTROL_REQUIRED')
    return SectionWriterRelayService(control, bindings, supervisor_certificate_sha256)


async def serve_worker(client, deployment, control, supervisor_transport, relay, stop, concurrency, *, enabled, protocol, protocol_sha256):
    """Actual guarded worker entry. Contexts are already operator-configured, not mocks.

    Open control and supervisor transports here and keep them alive until shutdown.
    The independently deployed supervisor serves supervisor_service(...).application().
    """
    from .section_writer_activity import SectionWriterHost, serve
    if enabled is not True or type(control) is not SectionWriterHttpControl or type(supervisor_transport) is not PinnedHttps:
        raise RuntimeBlocked('N04_EXPLICIT_DEPLOYMENT_REQUIRED')
    queue = protocol.get('section_writer_task_queue') if isinstance(protocol, dict) else None
    require_deployment(protocol, protocol_sha256, client.namespace, queue)
    async with control, supervisor_transport:
        host = SectionWriterHost(deployment, control, HttpSupervisorClient(supervisor_transport), relay)
        await serve(client, queue, host, stop, concurrency, enabled=enabled, protocol=protocol, protocol_sha256=protocol_sha256)


async def serve_worker_with_relay(client, deployment, control, supervisor_transport, stop, concurrency, *, enabled, protocol, protocol_sha256, relay_tls, relay_host, relay_port, supervisor_certificate_sha256):
    """Runnable service entry: start the pinned supervisor relay and Temporal worker.

    The listener is bound only after explicit protocol validation. TLS and credentials
    must already be supplied by deployment; no certificates or grants are generated.
    """
    import ipaddress
    import ssl
    from aiohttp import web
    if enabled is not True or type(control) is not SectionWriterHttpControl or type(supervisor_transport) is not PinnedHttps:
        raise RuntimeBlocked('N04_EXPLICIT_DEPLOYMENT_REQUIRED')
    queue = protocol.get('section_writer_task_queue') if isinstance(protocol, dict) else None
    require_deployment(protocol, protocol_sha256, client.namespace, queue)
    if not isinstance(relay_tls, ssl.SSLContext) or relay_tls.verify_mode != ssl.CERT_REQUIRED or relay_tls.protocol != ssl.PROTOCOL_TLS_SERVER or type(relay_port) is not int or not 1 <= relay_port <= 65535:
        raise RuntimeBlocked('N04_CLIENT_CERTIFICATE_LISTENER_REQUIRED')
    try:
        ipaddress.ip_address(relay_host)
    except (TypeError, ValueError):
        raise RuntimeBlocked('N04_FIXED_RELAY_ADDRESS_REQUIRED') from None
    relay = host_relay(control, {}, supervisor_certificate_sha256)
    runner = web.AppRunner(relay.application(), access_log=None)
    await runner.setup()
    try:
        await web.TCPSite(runner, host=relay_host, port=relay_port, ssl_context=relay_tls).start()
        await serve_worker(client, deployment, control, supervisor_transport, relay, stop, concurrency, enabled=enabled, protocol=protocol, protocol_sha256=protocol_sha256)
    finally:
        await runner.cleanup()
