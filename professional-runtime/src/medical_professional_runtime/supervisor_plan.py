"""Independent prepared-plan reader and bounded pre-provisioned isolation pool.

This module creates no cgroups, namespaces, profiles, credentials or permissions.
A worker supplies only an original locator. The separately pinned backend returns
physically read075 bytes after current authorization; local policy is never HTTP data.
"""
import asyncio
import base64
import binascii
from dataclasses import dataclass, replace
from datetime import datetime, timezone
from pathlib import Path

from .host import Binding, ProfessionalHost, RuntimeBlocked, digest, file_bytes, profile_relative, sha, strict_json, uuid
from .linux_inspection import LinuxInspectionConfig, LinuxInspector, PinnedFile
from .supervisor import LaunchPolicy, SupervisorPlan, canonical


def binding_from_wire(value):
    fields={'tenant_id','run_id','step_id','action_id','lease_generation','input_sha256','step_envelope_sha256'}
    if not isinstance(value,dict) or set(value)!=fields:raise RuntimeBlocked('SUPERVISOR_PLAN_BINDING_INVALID')
    if type(value['lease_generation']) is not int or value['lease_generation']<1:raise RuntimeBlocked('SUPERVISOR_PLAN_BINDING_INVALID')
    return Binding(*(uuid(value[k]) for k in ('tenant_id','run_id','step_id','action_id')),value['lease_generation'],sha(value['input_sha256']),sha(value['step_envelope_sha256']))


def expiry(value):
    try:
        until=datetime.fromisoformat(value.replace('Z','+00:00'))
        if until.tzinfo is None or not 0<(until-datetime.now(timezone.utc)).total_seconds()<=5:raise RuntimeBlocked('SUPERVISOR_PLAN_EXPIRED')
        return until
    except (ValueError,TypeError,AttributeError):raise RuntimeBlocked('SUPERVISOR_PLAN_EXPIRED') from None


@dataclass(frozen=True)
class OperatorSlot:
    """One independently provisioned unit, reserved once per supervisor lifetime.
    Explicit lack of a free unit is a deployment blocker, never implicit setup.
    """
    deployment_bytes: bytes
    python: Path
    runner: Path
    runner_sha256: str
    inspection: LinuxInspectionConfig
    policy: LaunchPolicy

    def __post_init__(self):
        if type(self.deployment_bytes) is not bytes or not 0<len(self.deployment_bytes)<=1048576:raise RuntimeBlocked('PINNED_DEPLOYMENT_REQUIRED')
        value=strict_json(self.deployment_bytes)
        if not isinstance(value,dict) or not self.python.is_absolute() or not self.runner.is_absolute():raise RuntimeBlocked('PINNED_DEPLOYMENT_REQUIRED')
        self.inspection.validate();sha(self.runner_sha256)
        if not any(pin.path==self.runner and pin.sha256==self.runner_sha256 for pin in self.inspection.runtime_files):raise RuntimeBlocked('RUNNER_PROCESS_ROOT_PIN_REQUIRED')
        for profile in self.inspection.executable_profiles:
            if (profile.uid,profile.gid,profile.lsm_profile)!=(self.policy.uid,self.policy.gid,self.policy.lsm_profile):raise RuntimeBlocked('OPERATOR_SECURITY_POLICY_MISMATCH')


class BackendPlanLoader:
    def __init__(self,transport,slots):
        if not isinstance(slots,tuple) or not 0<len(slots)<=256 or any(type(slot) is not OperatorSlot for slot in slots):raise RuntimeBlocked('OPERATOR_ISOLATION_POOL_REQUIRED')
        units={(str(slot.inspection.cgroup_path),slot.inspection.cgroup_inode) for slot in slots}
        if len(units)!=len(slots):raise RuntimeBlocked('DUPLICATE_OPERATOR_ISOLATION_UNIT')
        self.transport=transport;self.slots=slots;self.lock=asyncio.Lock();self.assigned={};self.used=set();self.originals={}

    async def _read(self,binding,session):
        raw=await self.transport.request('POST','/internal/v1/professional-runtime/supervisor-plan',{'binding':binding.wire(),'session_id':uuid(session)})
        reply=strict_json(raw)
        fields={'binding','session_id','prepared_launch','registered_envelope_base64','object_ref','registration_receipt_sha256','expires_at'}
        if not isinstance(reply,dict) or set(reply)!=fields or reply['binding']!=binding.wire() or reply['session_id']!=session:raise RuntimeBlocked('SUPERVISOR_PLAN_MISMATCH')
        expiry(reply['expires_at']);sha(reply['registration_receipt_sha256'])
        launch=reply['prepared_launch']
        launch_fields={'state','purpose','node_kind','role','session_id','binding','deployment_sha256','runtime_manifest_sha256','job_directory','prompt','prompt_sha256','prompt_provenance','sdk','profile_files','expected_inventory','role_version','profile_artifact'}
        if not isinstance(launch,dict) or set(launch)!=launch_fields:raise RuntimeBlocked('SUPERVISOR_PLAN_MISMATCH')
        ProfessionalHost._validate_launch(binding,launch)
        if launch['state']!='PREPARE' or launch['session_id']!=session or launch['purpose']!='TASK' or launch['node_kind']!='N02':raise RuntimeBlocked('SUPERVISOR_PLAN_MISMATCH')
        maximum=self.transport.maximum
        encoded=reply['registered_envelope_base64']
        if not isinstance(encoded,str) or not 0<len(encoded)<=4*((maximum+2)//3):raise RuntimeBlocked('SUPERVISOR_ENVELOPE_LIMIT')
        try:envelope=base64.b64decode(encoded,validate=True)
        except (ValueError,binascii.Error):raise RuntimeBlocked('REGISTERED_ENVELOPE_MISMATCH') from None
        ref=reply['object_ref']
        if not isinstance(ref,dict) or set(ref)!={'objectKey','objectVersion','sha256','sizeBytes','mediaType'} or not isinstance(ref['objectKey'],str) or not ref['objectKey'] or not isinstance(ref['objectVersion'],str) or not ref['objectVersion'] or ref['mediaType']!='application/json' or ref['sha256']!=binding.envelope_sha256 or ref['sizeBytes']!=str(len(envelope)) or digest(envelope)!=binding.envelope_sha256 or not 0<len(envelope)<=maximum:raise RuntimeBlocked('REGISTERED_ENVELOPE_MISMATCH')
        return reply,envelope

    async def load(self,session,binding_value,socket_root):
        binding=binding_from_wire(binding_value);uuid(session)
        async with self.lock:
            if session in self.assigned:raise RuntimeBlocked('ORIGINAL_SUPERVISOR_RECOVERY_REQUIRED')
            reply,envelope=await self._read(binding,session);launch=reply['prepared_launch']
            candidates=[(index,slot) for index,slot in enumerate(self.slots) if index not in self.used and digest(slot.deployment_bytes)==launch.get('deployment_sha256')]
            if not candidates:raise RuntimeBlocked('APPROVED_ISOLATION_SLOT_UNAVAILABLE')
            index,slot=candidates[0];deployment=strict_json(slot.deployment_bytes)
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
            plan=SupervisorPlan(session,binding.wire(),deployment['deployment_revision'],digest(slot.deployment_bytes),deployment['runtime_manifest_sha256'],job,slot.python,slot.runner,slot.runner_sha256,canonical(spec),environment,inspection,slot.policy,envelope,maximum_bytes=maximum)
            expiry(reply['expires_at'])
            self.used.add(index);self.assigned[session]=plan;self.originals[session]=canonical({key:value for key,value in reply.items() if key!='expires_at'})
            return plan

    async def require_current(self,plan):
        if self.assigned.get(plan.session_id) is not plan:raise RuntimeBlocked('ORIGINAL_SUPERVISOR_PLAN_REQUIRED')
        reply,envelope=await self._read(binding_from_wire(dict(plan.binding)),plan.session_id)
        if envelope!=plan.registered_envelope or canonical({key:value for key,value in reply.items() if key!='expires_at'})!=self.originals[plan.session_id]:raise RuntimeBlocked('SUPERVISOR_PLAN_CHANGED')
        expiry(reply['expires_at'])

class PlanDraftBackendPlanLoader(BackendPlanLoader):
    """Separate N01 SQL-preparation ledger source; never claims an object PUT/GET.

    The backend independently rechecks current N01 authority and original audit/
    outbox pins. Its pinned mTLS identity is the authority, not this JSON receipt.
    The inherited loader still requires operator-pinned isolation and profile bytes.
    """
    async def _read(self,binding,session):
        raw=await self.transport.request('POST','/internal/v1/plan-draft-runtime/supervisor-plan',{'binding':binding.wire(),'session_id':uuid(session)})
        reply=strict_json(raw)
        fields={'binding','session_id','prepared_launch','prepared_envelope_base64','preparation_receipt','expires_at'}
        if not isinstance(reply,dict) or set(reply)!=fields or reply['binding']!=binding.wire() or reply['session_id']!=session:raise RuntimeBlocked('N01_SUPERVISOR_PLAN_MISMATCH')
        expiry(reply['expires_at']);launch=reply['prepared_launch'];ProfessionalHost._validate_launch(binding,launch)
        if launch.get('state')!='PREPARE' or launch.get('session_id')!=session or launch.get('purpose')!='PLAN_DRAFT' or launch.get('node_kind')!='N01':raise RuntimeBlocked('N01_SUPERVISOR_PLAN_MISMATCH')
        encoded=reply['prepared_envelope_base64'];maximum=self.transport.maximum
        if not isinstance(encoded,str) or not 0<len(encoded)<=4*((maximum+2)//3):raise RuntimeBlocked('SUPERVISOR_ENVELOPE_LIMIT')
        try:envelope=base64.b64decode(encoded,validate=True)
        except (ValueError,binascii.Error):raise RuntimeBlocked('N01_PREPARED_ENVELOPE_MISMATCH') from None
        if not 0<len(envelope)<=maximum or digest(envelope)!=binding.envelope_sha256:raise RuntimeBlocked('N01_PREPARED_ENVELOPE_MISMATCH')
        value=strict_json(envelope);receipt=reply['preparation_receipt']
        if not isinstance(receipt,dict) or set(receipt)!={'session_id','envelope_sha256','launch_sha256'} or receipt!={'session_id':session,'envelope_sha256':digest(envelope),'launch_sha256':digest(canonical(launch))}:raise RuntimeBlocked('N01_PREPARATION_RECEIPT_MISMATCH')
        if value.get('execution_kind')!='SYSTEM_JOB' or value.get('job_type')!='PLAN_DRAFT' or value.get('node_kind')!='N01' or 'plan_revision' not in value or value['plan_revision'] is not None:raise RuntimeBlocked('N01_PREPARED_IDENTITY_MISMATCH')
        return reply,envelope
