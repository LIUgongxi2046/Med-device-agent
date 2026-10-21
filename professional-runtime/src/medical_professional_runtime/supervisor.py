"""Dedicated Linux supervisor, not the worker's assertion or a deployment smoke test.

This service must run as its separately authenticated identity with an operator-owned
plan source and pre-provisioned namespace/cgroup/LSM/seccomp policy. Missing setup is
an explicit blocker. It never provisions isolation, grants permissions or calls a model.
A readiness byte only requests inspection: actual runtime descendants must exist before
ADMITTED. Receipt bytes and exit observations are owned by this service.
"""
from __future__ import annotations
import asyncio
import base64
import ctypes
from dataclasses import dataclass
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import select
import signal
import stat
import subprocess
import struct
from types import MappingProxyType
from uuid import uuid4

from .host import RuntimeBlocked, digest, file_bytes, private_directory, strict_json, uuid, sha
from .linux_inspection import LinuxInspector, InspectionBlocked
from .trace_guard import LinuxTraceGuard


def canonical(value):
    return json.dumps(value, sort_keys=True, separators=(',', ':'), ensure_ascii=False, allow_nan=False).encode()


@dataclass(frozen=True)
class LaunchPolicy:
    """Operator-owned Linux resources. Never populated from an HTTP worker request."""
    namespaces: tuple[tuple[str, Path], ...]
    uid: int
    gid: int
    lsm_profile: str
    seccomp_program: Path
    seccomp_sha256: str

    def prepare(self):
        if self.uid <= 0 or self.gid <= 0 or not self.lsm_profile or 'unconfined' in self.lsm_profile:
            raise RuntimeBlocked('SUPERVISOR_POLICY_REQUIRED')
        # The compiled filter and LSM policy must be reviewed/provisioned independently.
        program = file_bytes(self.seccomp_program, 32768, self.seccomp_sha256)
        if not program or len(program) % 8: raise RuntimeBlocked('SECCOMP_PROGRAM_INVALID')
        descriptors=[]
        try:
            for name, path in self.namespaces:
                if name not in ('mnt','net','ipc','uts','cgroup','user','pid') or not path.is_absolute(): raise RuntimeBlocked('NAMESPACE_POLICY_INVALID')
                descriptors.append((name,os.open(path,os.O_RDONLY|os.O_CLOEXEC)))
            if {name for name,_ in descriptors} != {'mnt','net','ipc','uts','cgroup','user','pid'}: raise RuntimeBlocked('NAMESPACE_POLICY_INCOMPLETE')
            return program,descriptors
        except BaseException:
            for _,fd in descriptors: os.close(fd)
            raise


@dataclass(frozen=True)
class SupervisorPlan:
    session_id: str
    binding: dict
    deployment_revision: str
    deployment_sha256: str
    runtime_manifest_sha256: str
    job: Path
    python: Path
    runner: Path
    runner_sha256: str
    payload: bytes
    environment: dict
    inspector: LinuxInspector
    policy: LaunchPolicy
    registered_envelope: bytes
    maximum_bytes: int = 16 * 1024 * 1024
    ready_seconds: float = 30
    shutdown_seconds: float = 3

    def __post_init__(self):
        uuid(self.session_id);uuid(self.deployment_revision);sha(self.deployment_sha256);sha(self.runtime_manifest_sha256)
        object.__setattr__(self,'binding',MappingProxyType(dict(self.binding)))
        object.__setattr__(self,'environment',MappingProxyType(dict(self.environment)))
        if not self.job.is_absolute() or self.job.resolve()!=self.job or not self.python.is_absolute() or not self.runner.is_absolute(): raise RuntimeBlocked('PINNED_LAUNCH_REQUIRED')
        if not 0 < self.maximum_bytes <= 67108864 or not 0 < self.ready_seconds <= 300 or not 0 < self.shutdown_seconds <= 10: raise RuntimeBlocked('SUPERVISOR_LIMIT_INVALID')
        spec=strict_json(self.payload)
        if spec.get('session_id')!=self.session_id or spec.get('binding')!=dict(self.binding) or len(self.payload)>self.maximum_bytes: raise RuntimeBlocked('SUPERVISOR_PLAN_MISMATCH')
        from .host import require_prompt
        require_prompt(spec.get('prompt'),spec.get('prompt_sha256'),spec.get('prompt_provenance'),self.binding.get('input_sha256'))
        # Loaded by the independent approved-plan source, never accepted from a
        # worker request. The final075 envelope binds the compiled package pins.
        if not isinstance(self.registered_envelope,bytes) or not 0<len(self.registered_envelope)<=self.maximum_bytes or digest(self.registered_envelope)!=self.binding.get('step_envelope_sha256'):raise RuntimeBlocked('REGISTERED_ENVELOPE_MISMATCH')
        envelope=strict_json(self.registered_envelope)
        expected={key:value for key,value in self.binding.items() if key!='step_envelope_sha256'}|{'session_id':self.session_id,'deployment_revision':self.deployment_revision,'deployment_sha256':self.deployment_sha256,'runtime_manifest_sha256':self.runtime_manifest_sha256,'compiled_prompt':spec['prompt'],'prompt_sha256':spec['prompt_sha256'],'prompt_provenance':spec['prompt_provenance']}
        if not isinstance(envelope,dict) or any(envelope.get(key)!=value for key,value in expected.items()):raise RuntimeBlocked('REGISTERED_ENVELOPE_MISMATCH')
        file_bytes(self.runner,1048576,self.runner_sha256)
        if not any(str(pin.path)==str(self.runner) and pin.sha256==self.runner_sha256 for pin in self.inspector.config.runtime_files):raise RuntimeBlocked('RUNNER_PROCESS_ROOT_PIN_REQUIRED')


class OwnedLinuxProcess:
    """Owns the direct child and pidfds. PID disappearance is never an exit receipt."""
    def __init__(self, plan):
        self.plan=plan;self.process=None;self.gate=None;self.pidfds={};self.root_identity=None;self.observed={};self.output_task=None;self.exit_task=None;self.root_pidfd=None;self.trace=LinuxTraceGuard();self.reader=None

    async def launch_ready(self):
        if not hasattr(os,'pidfd_open') or not hasattr(os,'setns') or os.name!='posix': raise RuntimeBlocked('LINUX_INSTRUMENTATION_REQUIRED')
        plan=self.plan;program,namespaces=plan.policy.prepare()
        ready_read=ready_write=gate_read=gate_write=membership_fd=group_fd=None
        cgroup=plan.inspector.config.cgroup_path
        # Hold the pinned cgroup directory across namespace entry. A path in the
        # worker mount namespace is not the supervisor's original cgroup authority.
        try:
            ready_read,ready_write=os.pipe2(os.O_CLOEXEC);gate_read,gate_write=os.pipe2(os.O_CLOEXEC)
            plan.inspector.verify_unit_identity()
            group_fd=os.open(cgroup,os.O_RDONLY|os.O_DIRECTORY|os.O_NOFOLLOW)
            if os.fstat(group_fd).st_ino!=plan.inspector.config.cgroup_inode:raise RuntimeBlocked('CGROUP_REPLACED')
            membership_fd=os.open('cgroup.procs',os.O_WRONLY|os.O_NOFOLLOW,dir_fd=group_fd)
        except BaseException:
            for fd in (ready_read,ready_write,gate_read,gate_write,membership_fd):
                if fd is not None:os.close(fd)
            for _,fd in namespaces:os.close(fd)
            raise
        finally:
            if group_fd is not None:os.close(group_fd)
        def enter():
            # This code does not create resources or relax a policy. Any setup failure
            # aborts exec. All descendants inherit the actual confinement.
            for name,fd in namespaces:
                if name=='pid':
                    # setns(pid) affects future children, not this executable. This
                    # bounded launcher requires its dedicated service already in the
                    # operator-provisioned PID namespace; it never falsely attests entry.
                    if os.fstat(fd).st_ino!=os.stat('/proc/self/ns/pid').st_ino:raise RuntimeBlocked('SUPERVISOR_PID_NAMESPACE_REQUIRED')
                else:os.setns(fd,0)
            os.write(membership_fd,str(os.getpid()).encode());os.close(membership_fd)
            label=plan.policy.lsm_profile.removesuffix(' (enforce)')
            with open('/proc/self/attr/exec','w') as target:target.write('exec '+label)
            libc=ctypes.CDLL(None,use_errno=True)
            # setuid alone does not clear the capability bounding set.
            for capability in range(64):
                ctypes.set_errno(0);present=libc.prctl(23,capability,0,0,0)
                if present<0:
                    if ctypes.get_errno()==22:break
                    raise OSError(ctypes.get_errno(),'CAPABILITY_INSPECTION_FAILED')
                if present and libc.prctl(24,capability,0,0,0)!=0:raise OSError(ctypes.get_errno(),'CAPABILITY_DROP_FAILED')
            if libc.prctl(47,4,0,0,0)!=0:raise OSError(ctypes.get_errno(),'AMBIENT_CAPABILITY_DROP_FAILED')
            os.setgroups([]);os.setgid(plan.policy.gid);os.setuid(plan.policy.uid)
            class CapHeader(ctypes.Structure):_fields_=[('version',ctypes.c_uint32),('pid',ctypes.c_int)]
            class CapData(ctypes.Structure):_fields_=[('effective',ctypes.c_uint32),('permitted',ctypes.c_uint32),('inheritable',ctypes.c_uint32)]
            capabilities=(CapData*2)();header=CapHeader(0x20080522,0)
            if libc.capset(ctypes.byref(header),capabilities)!=0:raise OSError(ctypes.get_errno(),'CAPABILITY_CLEAR_FAILED')
            for _,fd in namespaces:os.close(fd)
            if libc.prctl(38,1,0,0,0)!=0: raise OSError(ctypes.get_errno(),'NO_NEW_PRIVILEGES_REQUIRED')
            class Filter(ctypes.Structure): _fields_=[('code',ctypes.c_ushort),('jt',ctypes.c_ubyte),('jf',ctypes.c_ubyte),('k',ctypes.c_uint)]
            class Program(ctypes.Structure): _fields_=[('len',ctypes.c_ushort),('filter',ctypes.POINTER(Filter))]
            filters=(Filter*(len(program)//8))(*(Filter(*struct.unpack_from('HBBI',program,offset)) for offset in range(0,len(program),8)))
            compiled=Program(len(filters),filters)
            if libc.prctl(22,2,ctypes.byref(compiled),0,0)!=0: raise OSError(ctypes.get_errno(),'SECCOMP_REQUIRED')
        try:
            env=dict(plan.environment);env['MEDICAL_READY_FD']=str(ready_write);env['MEDICAL_ADMISSION_FD']=str(gate_read)
            self.process=subprocess.Popen([str(plan.python),'-I',str(plan.runner)],cwd=plan.job/'workspace',env=env,stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.DEVNULL,pass_fds=(ready_write,gate_read,membership_fd,*(fd for _,fd in namespaces)),preexec_fn=enter,start_new_session=True)
            self.root_pidfd=os.pidfd_open(self.process.pid,0)
            self.reader=asyncio.StreamReader();protocol=asyncio.StreamReaderProtocol(self.reader)
            await asyncio.get_running_loop().connect_read_pipe(lambda:protocol,self.process.stdout)
            self.gate=gate_write;gate_write=None
            self.output_task=asyncio.create_task(self._read_bounded())
            self.input_task=asyncio.create_task(self._write_input(plan.payload))
        except BaseException:
            os.close(ready_read)
            raise
        finally:
            for _,fd in namespaces: os.close(fd)
            os.close(membership_fd)
            os.close(ready_write);os.close(gate_read)
            if gate_write is not None: os.close(gate_write)
        try:
            # Readiness is untrusted. It does not become admission until LinuxInspector
            # sees the actual required runtime executable and every descendant.
            ready=await asyncio.wait_for(self._read_ready(ready_read),timeout=plan.ready_seconds)
            await asyncio.wait_for(self.input_task,timeout=plan.ready_seconds)
            poller=select.poll();poller.register(self.root_pidfd,select.POLLIN)
            if ready!=b'SDK_READY\n' or poller.poll(0): raise RuntimeBlocked('SDK_READINESS_MISSING')
        finally: os.close(ready_read)

    def pin(self, snapshot):
        opened={}
        try:
            for item in snapshot.wire()['processes']:
                key=(item['pid'],item['start_ticks']);opened[key]=os.pidfd_open(item['pid'],0)
            self.plan.inspector.assert_unchanged(snapshot)
            if self.process.pid not in {pid for pid,_ in opened}: raise RuntimeBlocked('OWNED_ROOT_NOT_INSPECTED')
            self.admission_snapshot=snapshot
            final=self.plan.inspector.under_frozen(lambda value:self.trace.install(value,self._validate_thread))
            if final.wire()!=snapshot.wire(): raise RuntimeBlocked('PROCESS_CHANGED_BEFORE_TRACE')
            self.pidfds=opened;self.root_identity=next(key for key in opened if key[0]==self.process.pid)
            self.trace.start(self.process.pid,self.root_pidfd)
            self.exit_task=asyncio.create_task(self.trace.wait_root())
        except BaseException:
            for fd in opened.values(): os.close(fd)
            self.pidfds={}
            raise

    def release(self):
        if self.gate is None or not self.pidfds: raise RuntimeBlocked('SUPERVISED_GATE_REQUIRED')
        self.trace.check()
        os.write(self.gate,b'1');os.close(self.gate);self.gate=None

    def observe_exits(self):
        for key,fd in self.pidfds.items():
            poller=select.poll();poller.register(fd,select.POLLIN)
            events=poller.poll(0)
            for _,mask in events:
                if mask&(select.POLLNVAL|select.POLLERR):raise RuntimeBlocked('PIDFD_EXIT_UNKNOWN')
                if mask&select.POLLIN:self.observed[key]={'pid':key[0],'start_ticks':key[1],'kind':'PIDFD_EXIT'}
        return len(self.observed)==len(self.pidfds) and bool(self.pidfds)

    async def wait(self):
        # Owned waitid(P_PIDFD, WEXITED) never consumes ptrace stops and cannot
        # confuse a reused numeric PID with the original supervised child.
        if self.exit_task is None: raise RuntimeBlocked('OWNED_EXIT_UNKNOWN')
        done,_=await asyncio.wait({self.exit_task,self.output_task},return_when=asyncio.FIRST_COMPLETED)
        if self.output_task in done and self.output_task.exception() is not None:raise self.output_task.exception()
        status=await self.exit_task
        terminal=self.trace.root_terminal
        if not isinstance(terminal,dict) or terminal.get('code') not in ('EXITED','KILLED','DUMPED') or type(terminal.get('status')) is not int or not 0<=terminal['status']<=255 or status!=(terminal['status'] if terminal['code']=='EXITED' else -terminal['status']):raise RuntimeBlocked('OWNED_EXIT_UNKNOWN')
        self.process.returncode=status
        output=await asyncio.wait_for(self.output_task,self.plan.shutdown_seconds)
        self.trace.check()
        if not self.observe_exits(): raise RuntimeBlocked('DESCENDANT_EXIT_UNKNOWN')
        self.plan.inspector.assert_empty()
        return status,output

    async def abort(self):
        if self.gate is not None: os.close(self.gate);self.gate=None
        if self.process is None: return
        # No killpg(PID): reused numeric process groups are not authority to signal.
        # cgroup.kill is the exact independently pinned isolation unit.
        self.plan.inspector.kill_unit()
        if self.trace.tids or self.trace.root_exit is not None:
            if self.trace.monitor is None:self.trace.start(self.process.pid,self.root_pidfd)
            exit_wait=self.trace.wait_root(allow_violation=True)
        else:exit_wait=self._wait_untraced()
        try: self.process.returncode=await asyncio.wait_for(exit_wait,self.plan.shutdown_seconds)
        except asyncio.TimeoutError: raise RuntimeBlocked('OWNED_EXIT_UNKNOWN') from None
        self.observe_exits();self.plan.inspector.assert_empty()

    async def _read_bounded(self):
        output=bytearray()
        while chunk:=await self.reader.read(65536):
            output.extend(chunk)
            if len(output)>self.plan.maximum_bytes: raise RuntimeBlocked('OBSERVATION_LIMIT')
        return bytes(output)

    async def _read_ready(self,fd):
        future=asyncio.get_running_loop().create_future()
        def readable():
            if not future.done():
                try: future.set_result(os.read(fd,16))
                except BaseException as error: future.set_exception(error)
        loop=asyncio.get_running_loop();loop.add_reader(fd,readable)
        try:return await future
        finally:loop.remove_reader(fd)

    async def _wait_untraced(self):
        while True:
            # If pidfd_open itself failed during startup, this still-unreaped
            # direct child is owned by us and cannot have its PID reused. This
            # cleanup wait is not an admission or independent descendant proof.
            kind,identifier=(os.P_PID,self.process.pid) if self.root_pidfd is None else (os.P_PIDFD,self.root_pidfd)
            event=os.waitid(kind,identifier,os.WEXITED|os.WNOHANG)
            if event is not None:
                if event.si_pid!=self.process.pid or event.si_code not in (os.CLD_EXITED,os.CLD_KILLED,os.CLD_DUMPED):raise RuntimeBlocked('OWNED_EXIT_UNKNOWN')
                self.process.returncode=event.si_status if event.si_code==os.CLD_EXITED else -event.si_status
                return self.process.returncode
            await asyncio.sleep(.01)

    async def _write_input(self,payload):
        fd=self.process.stdin.fileno();os.set_blocking(fd,False);offset=0;loop=asyncio.get_running_loop()
        try:
            while offset<len(payload):
                try:offset+=os.write(fd,payload[offset:offset+65536])
                except BlockingIOError:
                    ready=loop.create_future()
                    def writable():
                        if not ready.done():ready.set_result(None)
                    loop.add_writer(fd,writable)
                    try:await ready
                    finally:loop.remove_writer(fd)
        finally:self.process.stdin.close()

    @staticmethod
    def _process_identity(snapshot):
        value=snapshot.wire()
        # Threads may be created by the initialized SDK, but only through the
        # kernel-stop guard and complete independent inspection before continuation.
        for process in value['processes']:process.pop('task_ids',None)
        for group in value.get('cgroups',[]):group.pop('threads',None)
        return value

    def _validate_thread(self,parent,child):
        actual=self.plan.inspector.snapshot()
        if self._process_identity(actual)!=self._process_identity(self.admission_snapshot):raise RuntimeBlocked('PROCESS_IDENTITY_CHANGED')
        return actual

    def require_current(self):
        self.trace.check();actual=self.plan.inspector.snapshot()
        if self._process_identity(actual)!=self._process_identity(self.admission_snapshot):raise RuntimeBlocked('PROCESS_IDENTITY_CHANGED')
        if not all(tid in self.trace.tids for process in actual.wire()['processes'] for tid in process['task_ids']):raise RuntimeBlocked('UNTRACED_THREAD')
        self.trace.check()

    def require_trace(self):self.trace.check()

    def close_handles(self):
        if self.root_pidfd is not None:os.close(self.root_pidfd);self.root_pidfd=None
        for fd in self.pidfds.values(): os.close(fd)
        self.pidfds={}


class LinuxSupervisor:
    """One owner per original session. Re-instantiation cannot resume an unknown exit.

    Plan source is operator/backend authenticated deployment wiring, not a request
    callback that turns caller fields into an attestation. A separate mTLS service
    adapter exposes only session-addressed operations to the host.
    """
    def __init__(self, plan:SupervisorPlan, receipt_root:Path):
        private_directory(receipt_root)
        self.plan=plan;self.root=receipt_root/plan.session_id;self.instance=str(uuid4());self.state='NEW'
        self.process=OwnedLinuxProcess(plan);self.snapshot=None;self.admitted=None;self.closed=None;self.terminated=None;self.export=None;self._abort_task=None;self.gate_timeout=None

    @classmethod
    def recover_closed(cls,plan,receipt_root):
        """Only a fully fsynced original closure can survive service restart.
        Missing/partial/blocked records never infer exit from an empty cgroup."""
        value=cls(plan,receipt_root)
        private_directory(value.root)
        if (value.root/'blocked.json').exists():raise RuntimeBlocked('ORIGINAL_EXIT_UNKNOWN')
        owner=strict_json(file_bytes(value.root/'owner.json',plan.maximum_bytes))
        admitted=strict_json(file_bytes(value.root/'admitted.json',plan.maximum_bytes))
        closed=strict_json(file_bytes(value.root/'closed.json',plan.maximum_bytes))
        if owner.get('session_id')!=plan.session_id or owner.get('binding')!=dict(plan.binding):raise RuntimeBlocked('ORIGINAL_SUPERVISOR_MISMATCH')
        for evidence,phase in ((admitted,'ADMITTED'),(closed,'CLOSED')):
            if evidence.get('phase')!=phase or evidence.get('session_id')!=plan.session_id or evidence.get('binding')!=dict(plan.binding) or evidence.get('deployment_sha256')!=plan.deployment_sha256 or evidence.get('deployment_revision')!=plan.deployment_revision or evidence.get('runtime_manifest_sha256')!=plan.runtime_manifest_sha256 or evidence.get('supervisor_instance_id')!=owner.get('supervisor_instance_id'):raise RuntimeBlocked('ORIGINAL_SUPERVISOR_MISMATCH')
        if closed.get('admitted_receipt_id')!=admitted.get('receipt_id') or closed.get('admitted_sha256')!=digest(canonical(admitted)) or closed.get('process_unit')!=admitted.get('process_unit'):raise RuntimeBlocked('ORIGINAL_SUPERVISOR_MISMATCH')
        exit=closed.get('exit',{});unit=admitted.get('process_unit',{})
        config=plan.inspector.config
        if unit.get('cgroup_path')!=str(config.cgroup_path) or unit.get('cgroup_inode')!=config.cgroup_inode or unit.get('boot_id')!=config.boot_id or unit.get('runtime_files')!=[p.wire() for p in config.runtime_files] or unit.get('profile_files')!=[p.wire() for p in config.profile_files]:raise RuntimeBlocked('ORIGINAL_SUPERVISOR_MISMATCH')
        launcher_pids=[]
        for observed in unit.get('processes',[]):
            matches=[]
            for profile in config.executable_profiles:
                fields=('exe_sha256','exe_device','exe_inode','cmdline_sha256','uid','gid','no_new_privs','seccomp','cap_eff','cap_inh','cap_prm','cap_bnd','cap_amb','lsm_profile')
                if all(observed.get(field)==getattr(profile,field) for field in fields) and observed.get('namespaces')==dict(profile.namespaces):matches.append(profile)
            if len(matches)!=1:raise RuntimeBlocked('ORIGINAL_SUPERVISOR_MISMATCH')
            if matches[0].role=='launcher':launcher_pids.append(observed.get('pid'))
        expected={(p['pid'],p['start_ticks']) for p in unit.get('processes',[])}
        observed=exit.get('observed_exits',[])
        if (not expected or len(observed)!=len(expected) or {(p.get('pid'),p.get('start_ticks')) for p in observed}!=expected or any(p.get('kind')!='PIDFD_EXIT' for p in observed) or exit.get('kind')!='OWNED_WAITID' or exit.get('unit_state')!='EMPTY' or exit.get('boot_id')!=unit.get('boot_id') or len(launcher_pids)!=1 or exit.get('root_pid')!=launcher_pids[0] or exit.get('process_unit_sha256')!=digest(canonical(unit)) or exit.get('code') not in ('EXITED','KILLED','DUMPED') or type(exit.get('status')) is not int or not 0<=exit['status']<=255):raise RuntimeBlocked('ORIGINAL_EXIT_UNKNOWN')
        value.export=file_bytes(value.root/'session-export.json',plan.maximum_bytes,closed['session_export']['sha256'])
        if len(value.export)!=closed['session_export']['size_bytes']:raise RuntimeBlocked('ORIGINAL_EXPORT_MISMATCH')
        plan.inspector.assert_empty()
        value.instance=owner['supervisor_instance_id'];value.admitted=admitted;value.closed=closed;value.state='CLOSED'
        return value

    @classmethod
    def recover_terminated(cls,plan,receipt_root):
        """Only a fully fsynced original closure can survive service restart.
        Missing/partial/blocked records never infer exit from an empty cgroup."""
        value=cls(plan,receipt_root)
        private_directory(value.root)
        owner=strict_json(file_bytes(value.root/'owner.json',plan.maximum_bytes))
        admitted=strict_json(file_bytes(value.root/'admitted.json',plan.maximum_bytes))
        closed=strict_json(file_bytes(value.root/'terminated.json',plan.maximum_bytes))
        if owner.get('session_id')!=plan.session_id or owner.get('binding')!=dict(plan.binding):raise RuntimeBlocked('ORIGINAL_SUPERVISOR_MISMATCH')
        for evidence,phase in ((admitted,'ADMITTED'),(closed,'TERMINATED')):
            if evidence.get('phase')!=phase or evidence.get('session_id')!=plan.session_id or evidence.get('binding')!=dict(plan.binding) or evidence.get('deployment_sha256')!=plan.deployment_sha256 or evidence.get('deployment_revision')!=plan.deployment_revision or evidence.get('runtime_manifest_sha256')!=plan.runtime_manifest_sha256 or evidence.get('supervisor_instance_id')!=owner.get('supervisor_instance_id'):raise RuntimeBlocked('ORIGINAL_SUPERVISOR_MISMATCH')
        if closed.get('admitted_receipt_id')!=admitted.get('receipt_id') or closed.get('admitted_sha256')!=digest(canonical(admitted)) or closed.get('process_unit')!=admitted.get('process_unit'):raise RuntimeBlocked('ORIGINAL_SUPERVISOR_MISMATCH')
        exit=closed.get('exit',{});unit=admitted.get('process_unit',{})
        config=plan.inspector.config
        if unit.get('cgroup_path')!=str(config.cgroup_path) or unit.get('cgroup_inode')!=config.cgroup_inode or unit.get('boot_id')!=config.boot_id or unit.get('runtime_files')!=[p.wire() for p in config.runtime_files] or unit.get('profile_files')!=[p.wire() for p in config.profile_files]:raise RuntimeBlocked('ORIGINAL_SUPERVISOR_MISMATCH')
        launcher_pids=[]
        for observed in unit.get('processes',[]):
            matches=[]
            for profile in config.executable_profiles:
                fields=('exe_sha256','exe_device','exe_inode','cmdline_sha256','uid','gid','no_new_privs','seccomp','cap_eff','cap_inh','cap_prm','cap_bnd','cap_amb','lsm_profile')
                if all(observed.get(field)==getattr(profile,field) for field in fields) and observed.get('namespaces')==dict(profile.namespaces):matches.append(profile)
            if len(matches)!=1:raise RuntimeBlocked('ORIGINAL_SUPERVISOR_MISMATCH')
            if matches[0].role=='launcher':launcher_pids.append(observed.get('pid'))
        expected={(p['pid'],p['start_ticks']) for p in unit.get('processes',[])}
        observed=exit.get('observed_exits',[])
        if (not expected or len(observed)!=len(expected) or {(p.get('pid'),p.get('start_ticks')) for p in observed}!=expected or any(p.get('kind')!='PIDFD_EXIT' for p in observed) or exit.get('kind')!='OWNED_WAITID' or exit.get('unit_state')!='EMPTY' or exit.get('boot_id')!=unit.get('boot_id') or len(launcher_pids)!=1 or exit.get('root_pid')!=launcher_pids[0] or exit.get('process_unit_sha256')!=digest(canonical(unit)) or exit.get('code') not in ('EXITED','KILLED','DUMPED') or type(exit.get('status')) is not int or not 0<=exit['status']<=255):raise RuntimeBlocked('ORIGINAL_EXIT_UNKNOWN')
        plan.inspector.assert_empty()
        value.instance=owner['supervisor_instance_id'];value.admitted=admitted;value.terminated=closed;value.state='TERMINATED'
        return value

    @classmethod
    def _testing(cls,plan,receipt_root,process_double):
        # Explicit test seam: tests never claim live isolation acceptance.
        value=cls(plan,receipt_root);value.process=process_double;return value

    async def prepare(self):
        if self.state!='NEW' or self.root.exists(): raise RuntimeBlocked('ORIGINAL_SUPERVISOR_RECOVERY_REQUIRED')
        self.root.mkdir(mode=0o700)
        self._persist('owner.json',canonical({'session_id':self.plan.session_id,'supervisor_instance_id':self.instance,'state':'PREPARING','binding':dict(self.plan.binding)}))
        self.state='PREPARING'
        try:
            self.plan.inspector.assert_empty()
            await self.process.launch_ready()
            self.snapshot=self.plan.inspector.snapshot()
            self.process.pin(self.snapshot)
            self.plan.inspector.assert_unchanged(self.snapshot)
            self.admitted=self._evidence('ADMITTED')|{'gate':'SDK_READY_LOCKED'}
            self._persist('admitted.json',canonical(self.admitted));self.state='ADMITTED'
            self.gate_timeout=asyncio.create_task(self._expire_locked_gate())
            self.gate_timeout.add_done_callback(lambda task:task.exception() if not task.cancelled() else None)
            return strict_json(canonical(self.admitted))
        except BaseException:
            self.state='UNKNOWN'
            try: await self.abort()
            finally:
                if not (self.root/'blocked.json').exists():self._persist('blocked.json',canonical({'state':'UNKNOWN','reason':'PREPARATION_NOT_ADMITTED'}))
            raise

    def current(self):
        if self.state not in ('ADMITTED','RUNNING'): raise RuntimeBlocked('SUPERVISION_NOT_CURRENT')
        try:
            self.process.require_trace()
            self.process.require_current()
            self.process.require_trace()
            if self.process.observe_exits(): raise RuntimeBlocked('SUPERVISED_PROCESS_EXITED')
            return self._reread('admitted.json',self.admitted)
        except BaseException:
            self.invalidate('LIVE_SUPERVISION_LOST')
            raise

    def invalidate(self,reason):
        if self.state in ('CLOSED','TERMINATED'):return
        self.state='UNKNOWN'
        try:
            if self.root.exists() and not (self.root/'blocked.json').exists():self._persist('blocked.json',canonical({'state':'UNKNOWN','reason':reason}))
        finally:
            # Storage failure must not prevent exact-unit cleanup. abort() owns a
            # single shared attempt; repeated requests never reissue an uncertain kill.
            self.cleanup=asyncio.create_task(self.abort())
            self.cleanup.add_done_callback(lambda task:task.exception() if not task.cancelled() else None)

    def release(self):
        if self.state!='ADMITTED': raise RuntimeBlocked('SUPERVISOR_GATE_NOT_LOCKED')
        try:
            self.current();self.process.release();self.state='RUNNING'
            if self.gate_timeout is not None:self.gate_timeout.cancel()
        except BaseException:
            self.invalidate('SUPERVISED_GATE_RELEASE_UNKNOWN')
            raise

    async def _expire_locked_gate(self):
        await asyncio.sleep(self.plan.ready_seconds)
        if self.state=='ADMITTED':self.invalidate('SUPERVISED_GATE_TIMEOUT')

    async def finish(self):
        if self.state!='RUNNING': raise RuntimeBlocked('SUPERVISOR_SESSION_NOT_RUNNING')
        try:
            status,raw=await self.process.wait()
            observation=strict_json(raw)
            if not isinstance(observation,dict) or observation.get('session_id')!=self.plan.session_id or observation.get('binding')!=dict(self.plan.binding): raise RuntimeBlocked('SUPERVISOR_OBSERVATION_MISMATCH')
            self.plan.inspector.assert_empty()
            archive=self._collect_archive(observation);self.export=canonical(archive)
            if len(self.export)>self.plan.maximum_bytes: raise RuntimeBlocked('SUPERVISOR_ARCHIVE_LIMIT')
            self._persist('session-export.json',self.export)
            evidence=self._evidence('CLOSED')
            evidence.update(admitted_receipt_id=self.admitted['receipt_id'],admitted_sha256=digest(canonical(self.admitted)),exit={'kind':'OWNED_WAITID','boot_id':self.snapshot.wire()['boot_id'],'process_unit_sha256':digest(canonical(self.snapshot.wire())),'root_pid':self.process.root_identity[0],'code':self.process.trace.root_terminal['code'],'status':self.process.trace.root_terminal['status'],'unit_state':'EMPTY','observed_exits':sorted(self.process.observed.values(),key=lambda item:item['pid'])},session_export={'sha256':digest(self.export),'size_bytes':len(self.export)})
            self._persist('closed.json',canonical(evidence));self.closed=evidence;self.state='CLOSED';await self.process.trace.close();self.process.close_handles()
            return raw
        except BaseException as failure:
            self.state='UNKNOWN'
            try:
                await self.abort()
            except BaseException as cleanup_failure:
                # Preserve the original failed safety check; cleanup remains
                # failed and its evidence is retained as the exception cause.
                raise failure from cleanup_failure
            finally:
                if not (self.root/'blocked.json').exists():self._persist('blocked.json',canonical({'state':'UNKNOWN','reason':'EXIT_OR_EXPORT_NOT_PROVEN'}))
            raise

    async def abort(self):
        if self.state in ('CLOSED','TERMINATED'):return
        self.state='UNKNOWN'
        if self.gate_timeout is not None and self.gate_timeout is not asyncio.current_task():self.gate_timeout.cancel()
        if self._abort_task is None:self._abort_task=asyncio.create_task(self._abort_owned())
        return await asyncio.shield(self._abort_task)

    async def _abort_owned(self):
        try:
            await self.process.abort()
            if self.admitted is not None and self.snapshot is not None and self.process.trace.root_terminal is not None:
                self.plan.inspector.assert_empty()
                expected={(p['pid'],p['start_ticks']) for p in self.snapshot.wire()['processes']}
                if set(self.process.observed)!=expected:raise RuntimeBlocked('OWNED_EXIT_UNKNOWN')
                evidence=self._evidence('TERMINATED')
                evidence.update(admitted_receipt_id=self.admitted['receipt_id'],admitted_sha256=digest(canonical(self.admitted)),exit={'kind':'OWNED_WAITID','boot_id':self.snapshot.wire()['boot_id'],'process_unit_sha256':digest(canonical(self.snapshot.wire())),'root_pid':self.process.root_identity[0],'code':self.process.trace.root_terminal['code'],'status':self.process.trace.root_terminal['status'],'unit_state':'EMPTY','observed_exits':sorted(self.process.observed.values(),key=lambda item:item['pid'])})
                self._persist('terminated.json',canonical(evidence));self.terminated=evidence;self.state='TERMINATED'
        finally:
            # Failed termination evidence must not leak the owned trace or
            # handles. Neither cleanup nor an empty unit proves termination.
            try:
                if not isinstance(self.process.trace,LinuxTraceGuard) or self.process.trace.owner_thread is not None:await self.process.trace.close()
            finally:
                try:
                    self.process.close_handles()
                finally:
                    if self.root.exists() and not (self.root/'blocked.json').exists(): self._persist('blocked.json',canonical({'state':'UNKNOWN','reason':'ADMISSION_ABORTED'}))
        # Failure is not a successful CLOSED receipt, even if cleanup killed a process.

    async def terminate(self):
        if self.terminated is not None:return self.receipt('TERMINATED')
        if self.state=='CLOSED':
            # Preserve the actual original waitid facts, without claiming a second
            # business completion or inventing an export for an aborted process.
            evidence=dict(self.closed);evidence.pop('session_export')
            evidence.update(phase='TERMINATED',receipt_id=str(uuid4()),inspected_at=datetime.now(timezone.utc).isoformat(timespec='microseconds').replace('+00:00','Z'))
            self.plan.inspector.assert_empty();self._persist('terminated.json',canonical(evidence));self.terminated=evidence
        else:await self.abort()
        return self.receipt('TERMINATED')

    def receipt(self,phase):
        if phase=='ADMITTED_ORIGINAL':
            if self.admitted is None:raise RuntimeBlocked('ADMISSION_ORIGINAL_MISSING')
            return self._reread('admitted.json',self.admitted)
        if phase=='TERMINATED':
            if self.state not in ('CLOSED','TERMINATED') or self.terminated is None:raise RuntimeBlocked('TERMINATION_EVIDENCE_MISSING')
            self.plan.inspector.assert_empty();return self._reread('terminated.json',self.terminated)
        if phase=='ADMITTED': return self.current()
        if phase!='CLOSED' or self.state!='CLOSED': raise RuntimeBlocked('CLOSURE_EVIDENCE_MISSING')
        self.plan.inspector.assert_empty();self.session_export();return self._reread('closed.json',self.closed)

    def session_export(self):
        if self.state!='CLOSED' or self.closed is None: raise RuntimeBlocked('CLOSURE_EVIDENCE_MISSING')
        return file_bytes(self.root/'session-export.json',self.plan.maximum_bytes,self.closed['session_export']['sha256'])

    def _evidence(self,phase):
        return {'schema_version':'1.0.0','receipt_id':str(uuid4()),'phase':phase,'session_id':self.plan.session_id,'binding':dict(self.plan.binding),'deployment_revision':self.plan.deployment_revision,'deployment_sha256':self.plan.deployment_sha256,'runtime_manifest_sha256':self.plan.runtime_manifest_sha256,'supervisor_instance_id':self.instance,'inspected_at':datetime.now(timezone.utc).isoformat(timespec='microseconds').replace('+00:00','Z'),'process_unit':self.snapshot.wire()}

    def _persist(self,name,raw):
        fd=os.open(self.root/name,os.O_WRONLY|os.O_CREAT|os.O_EXCL|os.O_NOFOLLOW,0o600)
        with os.fdopen(fd,'wb') as target:target.write(raw);target.flush();os.fsync(target.fileno())
        fd=os.open(self.root,os.O_RDONLY|os.O_DIRECTORY)
        try:os.fsync(fd)
        finally:os.close(fd)

    def _reread(self,name,expected):
        raw=file_bytes(self.root/name,self.plan.maximum_bytes,digest(canonical(expected)))
        return strict_json(raw)

    def _collect_archive(self,observation):
        files=[];total=0
        private_directory(self.plan.job)
        for path in sorted(self.plan.job.rglob('*')):
            if path.is_symlink(): raise RuntimeBlocked('ARCHIVE_SYMLINK_FORBIDDEN')
            info=path.lstat()
            if stat.S_ISDIR(info.st_mode):continue
            if not stat.S_ISREG(info.st_mode):raise RuntimeBlocked('ARCHIVE_SPECIAL_FILE_FORBIDDEN')
            raw=file_bytes(path,self.plan.maximum_bytes);total+=len(raw)
            if total>self.plan.maximum_bytes or len(files)>=256:raise RuntimeBlocked('SUPERVISOR_ARCHIVE_LIMIT')
            relative=str(path.relative_to(self.plan.job))
            # Authentication material lives outside the job and is never archived.
            files.append({'path':relative,'sha256':digest(raw),'size_bytes':str(len(raw)),'content_base64':base64.b64encode(raw).decode('ascii')})
        if not files:raise RuntimeBlocked('SESSION_ARCHIVE_MISSING')
        return {'files':files,'observation':observation}
