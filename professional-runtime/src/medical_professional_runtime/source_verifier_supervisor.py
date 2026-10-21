"""Dedicated N05 prepared-ledger reader. Never relabels N02 object registration."""
import base64
import binascii
from .host import ProfessionalHost, RuntimeBlocked, digest, strict_json, uuid
from rfc8785 import dumps as canonical
from .supervisor_plan import BackendPlanLoader, expiry
from .supervisor_http import require_expiry


class SourceVerifierBackendPlanLoader(BackendPlanLoader):
    async def _read(self, binding, session):
        raw = await self.transport.request('POST', '/internal/v1/source-verifier-runtime/supervisor-plan', {'binding': binding.wire(), 'session_id': uuid(session)})
        reply = strict_json(raw)
        fields = {'binding','session_id','prepared_launch','prepared_envelope_base64','preparation_receipt','expires_at'}
        if not isinstance(reply, dict) or set(reply) != fields or reply['binding'] != binding.wire() or reply['session_id'] != session:
            raise RuntimeBlocked('N05_SUPERVISOR_PLAN_MISMATCH')
        expiry(reply['expires_at'])
        launch = reply['prepared_launch']
        ProfessionalHost._validate_launch(binding, launch)
        if launch.get('state') != 'PREPARE' or launch.get('session_id') != session or launch.get('purpose') != 'TASK' or launch.get('node_kind') != 'N05' or launch.get('role') != 'SourceVerifier':
            raise RuntimeBlocked('N05_SUPERVISOR_PLAN_MISMATCH')
        encoded, maximum = reply['prepared_envelope_base64'], self.transport.maximum
        if not isinstance(encoded, str) or not 0 < len(encoded) <= 4 * ((maximum + 2) // 3):
            raise RuntimeBlocked('N05_PREPARED_ENVELOPE_LIMIT')
        try:
            envelope = base64.b64decode(encoded, validate=True)
        except (ValueError, binascii.Error):
            raise RuntimeBlocked('N05_PREPARED_ENVELOPE_MISMATCH') from None
        if not 0 < len(envelope) <= maximum or digest(envelope) != binding.envelope_sha256:
            raise RuntimeBlocked('N05_PREPARED_ENVELOPE_MISMATCH')
        receipt = {'session_id': session, 'envelope_sha256': digest(envelope), 'launch_sha256': digest(canonical(launch))}
        if reply['preparation_receipt'] != receipt:
            raise RuntimeBlocked('N05_PREPARATION_RECEIPT_MISMATCH')
        value = strict_json(envelope)
        expected = {key: val for key, val in binding.wire().items() if key != 'step_envelope_sha256'} | {'session_id': session, 'execution_kind': 'TASK', 'node_kind': 'N05', 'compiled_prompt': launch['prompt'], 'prompt_sha256': launch['prompt_sha256'], 'prompt_provenance': launch['prompt_provenance']}
        if not isinstance(value, dict) or any(value.get(key) != val for key, val in expected.items()) or value.get('plan_revision') is None:
            raise RuntimeBlocked('N05_PREPARED_IDENTITY_MISMATCH')
        return reply, envelope


class SourceVerifierBackendAdmissionReader:
    def __init__(self, transport):
        self.transport = transport

    async def require(self, supervisor):
        value = {'binding': dict(supervisor.plan.binding), 'session_id': supervisor.plan.session_id, 'isolation_receipt_id': supervisor.admitted['receipt_id'], 'supervision_sha256': digest(canonical(supervisor.admitted)), 'process_unit_sha256': digest(canonical(supervisor.snapshot.wire())), 'supervisor_instance_id': supervisor.instance}
        reply = strict_json(await self.transport.request('POST', '/internal/v1/source-verifier-runtime/supervisor-admission', value))
        if not isinstance(reply, dict) or set(reply) != set(value) | {'admission','expires_at'} or any(reply.get(key) != val for key, val in (value | {'admission': 'CURRENT'}).items()):
            raise RuntimeBlocked('N05_ORIGINAL_BACKEND_ADMISSION_REQUIRED')
        require_expiry(reply['expires_at'])
