"""Independent EVAL prepared-ledger loader and kernel-owned process plan."""
import asyncio
import base64
import binascii
from dataclasses import replace
from pathlib import Path
from types import MappingProxyType
from .host import RuntimeBlocked, digest, file_bytes, profile_relative, strict_json, sha, uuid
from .supervisor import SupervisorPlan, canonical
from .supervisor_plan import BackendPlanLoader, binding_from_wire, expiry
from .supervisor_http import require_expiry
from .linux_inspection import LinuxInspector, PinnedFile
from .evaluation_validation import validate_launch, require_prompt


class EvaluationSupervisorPlan(SupervisorPlan):
    def __post_init__(self):
        # Separate envelope ABI. Never relabel as TASK or weaken TASK validation.
        uuid(self.session_id); uuid(self.deployment_revision); sha(self.deployment_sha256); sha(self.runtime_manifest_sha256)
        object.__setattr__(self, 'binding', MappingProxyType(dict(self.binding)))
        object.__setattr__(self, 'environment', MappingProxyType(dict(self.environment)))
        if not self.job.is_absolute() or self.job.resolve() != self.job or not self.python.is_absolute() or not self.runner.is_absolute(): raise RuntimeBlocked('PINNED_LAUNCH_REQUIRED')
        if not 0 < self.maximum_bytes <= 67108864 or not 0 < self.ready_seconds <= 300 or not 0 < self.shutdown_seconds <= 10: raise RuntimeBlocked('SUPERVISOR_LIMIT_INVALID')
        spec = strict_json(self.payload)
        if spec.get('session_id') != self.session_id or spec.get('binding') != dict(self.binding) or len(self.payload) > self.maximum_bytes: raise RuntimeBlocked('EVALUATION_PLAN_MISMATCH')
        require_prompt(spec.get('prompt'), spec.get('prompt_sha256'), spec.get('prompt_provenance'), self.binding.get('input_sha256'))
        if type(self.registered_envelope) is not bytes or not 0 < len(self.registered_envelope) <= self.maximum_bytes or digest(self.registered_envelope) != self.binding.get('step_envelope_sha256'): raise RuntimeBlocked('EVALUATION_ENVELOPE_MISMATCH')
        envelope = strict_json(self.registered_envelope)
        expected = {key: value for key,value in self.binding.items() if key != 'step_envelope_sha256'} | {'session_id':self.session_id,'deployment_revision':self.deployment_revision,'deployment_sha256':self.deployment_sha256,'runtime_manifest_sha256':self.runtime_manifest_sha256,'execution_kind':'SYSTEM_JOB','job_type':'EVALUATION_VERIFICATION','node_kind':spec['prompt_provenance']['node_kind']}
        if not isinstance(envelope, dict) or any(envelope.get(key) != value for key,value in expected.items()): raise RuntimeBlocked('EVALUATION_ENVELOPE_MISMATCH')
        file_bytes(self.runner, 1048576, self.runner_sha256)
        if not any(pin.path == self.runner and pin.sha256 == self.runner_sha256 for pin in self.inspector.config.runtime_files): raise RuntimeBlocked('RUNNER_PROCESS_ROOT_PIN_REQUIRED')


class EvaluationBackendPlanLoader(BackendPlanLoader):
    async def _read(self, binding, session):
        reply = strict_json(await self.transport.request('POST', '/internal/v1/evaluation-runtime/supervisor-plan', {'binding':binding.wire(),'session_id':uuid(session)}))
        fields = {'binding','session_id','prepared_launch','prepared_envelope_base64','preparation_receipt','expires_at'}
        if not isinstance(reply, dict) or set(reply) != fields or reply['binding'] != binding.wire() or reply['session_id'] != session: raise RuntimeBlocked('EVALUATION_SUPERVISOR_PLAN_MISMATCH')
        expiry(reply['expires_at']); launch = reply['prepared_launch']; validate_launch(binding, launch)
        if launch['session_id'] != session: raise RuntimeBlocked('EVALUATION_SESSION_MISMATCH')
        encoded = reply['prepared_envelope_base64']
        if not isinstance(encoded,str) or not 0 < len(encoded) <= 4*((self.transport.maximum+2)//3): raise RuntimeBlocked('EVALUATION_ENVELOPE_LIMIT')
        try: envelope = base64.b64decode(encoded, validate=True)
        except (ValueError,binascii.Error): raise RuntimeBlocked('EVALUATION_ENVELOPE_INVALID') from None
        if not 0 < len(envelope) <= self.transport.maximum or digest(envelope) != binding.envelope_sha256: raise RuntimeBlocked('EVALUATION_ENVELOPE_MISMATCH')
        if reply['preparation_receipt'] != {'session_id':session,'envelope_sha256':digest(envelope),'launch_sha256':digest(canonical(launch))}: raise RuntimeBlocked('EVALUATION_PREPARATION_MISMATCH')
        return reply, envelope

    async def load(self,session,binding_value,socket_root):
        binding=binding_from_wire(binding_value);uuid(session)
        async with self.lock:
            if session in self.assigned:raise RuntimeBlocked('ORIGINAL_SUPERVISOR_RECOVERY_REQUIRED')
            reply,envelope=await self._read(binding,session);launch=reply['prepared_launch']
            candidates=[(index,slot) for index,slot in enumerate(self.slots) if index not in self.used and digest(slot.deployment_bytes)==launch.get('deployment_sha256')]
            if not candidates:raise RuntimeBlocked('APPROVED_ISOLATION_SLOT_UNAVAILABLE')
            index,slot=candidates[0];deployment=strict_json(slot.deployment_bytes)
            if deployment.get('model_tool_mode')!='EVALUATION_NODE_V1' or deployment.get('node_kind')!=launch['node_kind']:raise RuntimeBlocked('EVALUATION_DEPLOYMENT_KIND_MISMATCH')
            for field in ('sdk','profile_files','expected_inventory','role_version','profile_artifact'):
                if launch.get(field)!=deployment.get(field):raise RuntimeBlocked('PINNED_DEPLOYMENT_MISMATCH')
            if launch.get('role')!=deployment.get('role_name') or launch.get('runtime_manifest_sha256')!=deployment.get('runtime_manifest_sha256'):raise RuntimeBlocked('PINNED_DEPLOYMENT_MISMATCH')
            root=Path(deployment['jobs_root']);job=root/binding.tenant/binding.run/binding.step/str(binding.generation)
            if not root.is_absolute() or root.resolve()!=root or launch['job_directory']!=str(job) or not isinstance(socket_root,Path) or not socket_root.is_absolute() or socket_root.resolve()!=socket_root:raise RuntimeBlocked('SUPERVISOR_JOB_PATH_MISMATCH')
            maximum=deployment['maximum_object_bytes']
            if type(maximum) is not int or not 0<maximum<=self.transport.maximum:raise RuntimeBlocked('SUPERVISOR_LIMIT_INVALID')
            bridge=socket_root/session.replace('-','');pins=[];total=0
            replacements={b'@@MEDICAL_SOCKET_PATH@@':str(bridge/'s').encode(),b'@@MEDICAL_TOKEN_PATH@@':str(bridge/'t').encode(),b'@@MEDICAL_INVENTORY_PATH@@':str(bridge/'inventory.json').encode()}
            for item in deployment['profile_files']:
                relative=profile_relative(item['relative']);source=Path(item['source'])
                if not source.is_absolute():raise RuntimeBlocked('PROFILE_PATH_INVALID')
                value=file_bytes(source,maximum,item['sha256']);total+=len(value)
                if total>maximum:raise RuntimeBlocked('SUPERVISOR_PROFILE_LIMIT')
                for marker,replacement in replacements.items():value=value.replace(marker,replacement)
                pins.append(PinnedFile(job/'home'/relative,digest(value)))
            spec={**deployment['sdk'],'session_id':session,'home':str(job/'home'),'workspace':str(job/'workspace'),'binding':binding.wire(),'prompt':launch['prompt'],'prompt_sha256':launch['prompt_sha256'],'prompt_provenance':launch['prompt_provenance']}
            environment={'HOME':str(job/'home'),'TMPDIR':str(job/'tmp'),'PATH':str(slot.python.parent)+':/usr/bin:/bin','LANG':'C.UTF-8','LC_ALL':'C.UTF-8'}
            inspection=LinuxInspector(replace(slot.inspection,profile_files=tuple(pins)))
            plan=EvaluationSupervisorPlan(session,binding.wire(),deployment['deployment_revision'],digest(slot.deployment_bytes),deployment['runtime_manifest_sha256'],job,slot.python,slot.runner,slot.runner_sha256,canonical(spec),environment,inspection,slot.policy,envelope,maximum_bytes=maximum)
            expiry(reply['expires_at'])
            self.used.add(index);self.assigned[session]=plan;self.originals[session]=canonical({key:value for key,value in reply.items() if key!='expires_at'})
            return plan

    async def require_current(self,plan):
        if self.assigned.get(plan.session_id) is not plan:raise RuntimeBlocked('ORIGINAL_SUPERVISOR_PLAN_REQUIRED')
        reply,envelope=await self._read(binding_from_wire(dict(plan.binding)),plan.session_id)
        if envelope!=plan.registered_envelope or canonical({key:value for key,value in reply.items() if key!='expires_at'})!=self.originals[plan.session_id]:raise RuntimeBlocked('SUPERVISOR_PLAN_CHANGED')
        expiry(reply['expires_at'])


class EvaluationBackendAdmissionReader:
    def __init__(self, transport): self.transport = transport
    async def require(self, supervisor):
        value = {'binding':dict(supervisor.plan.binding),'session_id':supervisor.plan.session_id,'isolation_receipt_id':supervisor.admitted['receipt_id'],'supervision_sha256':digest(canonical(supervisor.admitted)),'process_unit_sha256':digest(canonical(supervisor.snapshot.wire())),'supervisor_instance_id':supervisor.instance}
        reply = strict_json(await self.transport.request('POST','/internal/v1/evaluation-runtime/supervisor-admission',value))
        if not isinstance(reply,dict) or set(reply)!=set(value)|{'admission','expires_at'} or any(reply.get(key)!=val for key,val in (value|{'admission':'CURRENT'}).items()): raise RuntimeBlocked('EVALUATION_CURRENT_ADMISSION_REQUIRED')
        require_expiry(reply['expires_at'])
