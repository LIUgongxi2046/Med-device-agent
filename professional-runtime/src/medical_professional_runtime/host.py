"""One isolated process/session per server-owned step lease. No business success inference.

The control service owns current authorization, input loading and registered results.
SDK completion is only an observation submitted after local process exit.
"""
from __future__ import annotations
import asyncio
import hashlib
import json
import os
from pathlib import Path
import signal
import stat
import time
from dataclasses import dataclass
from typing import Protocol
from uuid import UUID

class RuntimeBlocked(RuntimeError):
    pass

def strict_json(raw: bytes):
    def pairs(values):
        result = {}
        for key, value in values:
            if key in result: raise RuntimeBlocked('DUPLICATE_JSON_KEY')
            result[key] = value
        return result
    try:
        if isinstance(raw, bytes): raw = raw.decode('utf-8')
        return json.loads(raw, object_pairs_hook=pairs, parse_constant=lambda _: (_ for _ in ()).throw(ValueError()))
    except (ValueError, UnicodeError) as error:
        raise RuntimeBlocked('INVALID_JSON') from error

def digest(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()

def uuid(value):
    if not isinstance(value, str) or str(UUID(value)) != value: raise RuntimeBlocked('INVALID_ID')
    return value

def sha(value):
    if not isinstance(value, str) or len(value) != 64 or any(c not in '0123456789abcdef' for c in value): raise RuntimeBlocked('INVALID_HASH')
    return value

def require_prompt(prompt, expected_sha256, provenance, input_sha256):
    """Integrity checks only; package authority comes from the authenticated backend.
    The supervisor additionally matches these pins to the physically loaded075 bytes.
    """
    if isinstance(provenance,dict) and provenance.get('compiler')=='N07_LITERAL_INSTRUCTIONS_AND_SAVED_DOCUMENT_V1':
        from .document_review_validation import require_prompt as require_n07_prompt
        return require_n07_prompt(prompt,expected_sha256,provenance,input_sha256)
    if isinstance(provenance,dict) and provenance.get('compiler')=='N05_LITERAL_INSTRUCTIONS_AND_REGISTERED_INPUT_V1':
        from .source_verifier_validation import require_prompt as require_n05_prompt
        return require_n05_prompt(prompt,expected_sha256,provenance,input_sha256)
    if isinstance(provenance,dict) and provenance.get('compiler')=='N04_LITERAL_INSTRUCTIONS_AND_REGISTERED_INPUT_V1':
        from .section_writer_validation import require_prompt as require_n04_prompt
        return require_n04_prompt(prompt,expected_sha256,provenance,input_sha256)
    if isinstance(provenance,dict) and provenance.get('compiler')=='N01_LITERAL_INSTRUCTIONS_AND_REGISTERED_INPUT_V1':
        from .plan_draft_validation import require_prompt as require_n01_prompt
        return require_n01_prompt(prompt,expected_sha256,provenance,input_sha256)
    if isinstance(provenance,dict) and provenance.get('compiler')=='N02_LITERAL_INSTRUCTIONS_AND_REGISTERED_PREDECESSOR_EVIDENCE_V2':
        return _require_prompt_v2(prompt,expected_sha256,provenance,input_sha256)
    fields={'schema_version','compiler','input_artifact_id','input_sha256','input_receipt_sha256','launch_sha256','role_basis','prompt_basis','role_package','prompt_package','profile_package','adapter_artifact'}
    if not isinstance(prompt,str) or not prompt or not isinstance(provenance,dict) or set(provenance)!=fields:raise RuntimeBlocked('COMPILED_PROMPT_REQUIRED')
    if provenance.get('schema_version')!='1.0.0' or provenance.get('compiler')!='N02_LITERAL_INSTRUCTIONS_AND_REGISTERED_INPUT_V1' or provenance.get('input_sha256')!=input_sha256 or provenance.get('launch_sha256')!=expected_sha256 or digest(prompt.encode('utf-8'))!=sha(expected_sha256):raise RuntimeBlocked('COMPILED_PROMPT_MISMATCH')
    uuid(provenance['input_artifact_id']);sha(provenance['input_receipt_sha256'])
    for key in ('role_basis','prompt_basis','role_package','prompt_package','profile_package','adapter_artifact'):
        if not isinstance(provenance[key],dict) or not provenance[key]:raise RuntimeBlocked('COMPILED_PROMPT_REQUIRED')


def _require_prompt_v2(prompt,expected_sha256,provenance,input_sha256):
    # Additive ABI, never selected by a caller flag or by merely having predecessors.
    # These are integrity checks, not registration, source-rights or send authority.
    common={'schema_version','compiler','input_artifact_id','input_sha256','input_receipt_sha256','launch_sha256','role_basis','prompt_basis','role_package','prompt_package','profile_package','adapter_artifact'}
    if not isinstance(prompt,str) or not prompt or set(provenance)!=common|{'predecessor_evidence'}:raise RuntimeBlocked('COMPILED_PROMPT_REQUIRED')
    if provenance['schema_version']!='2.0.0' or provenance['input_sha256']!=input_sha256 or provenance['launch_sha256']!=expected_sha256 or digest(prompt.encode('utf-8'))!=sha(expected_sha256):raise RuntimeBlocked('COMPILED_PROMPT_MISMATCH')
    try:
        uuid(provenance['input_artifact_id']);sha(provenance['input_receipt_sha256'])
        for key in ('role_basis','prompt_basis','adapter_artifact'):
            if not isinstance(provenance[key],dict) or not provenance[key]:raise RuntimeBlocked('COMPILED_PROMPT_REQUIRED')
        package_fields={'artifact','object_ref','schema_ref','schema_sha256','authorization_sha256','content_members'}
        for key,ref in (('role_package','urn:medical:task-professional-package:1.0.0#/$defs/RolePackage'),('prompt_package','urn:medical:task-professional-package:2.0.0#/$defs/PromptPackage'),('profile_package','urn:medical:task-professional-package:2.0.0#/$defs/ProfilePackage')):
            package=provenance[key]
            if not isinstance(package,dict) or set(package)!=package_fields or package['schema_ref']!=ref:raise RuntimeBlocked('COMPILED_PROMPT_V2_PACKAGE_MISMATCH')
            sha(package['schema_sha256']);sha(package['authorization_sha256'])
            if not isinstance(package['artifact'],dict) or not isinstance(package['object_ref'],dict) or not isinstance(package['content_members'],list):raise RuntimeBlocked('COMPILED_PROMPT_V2_PACKAGE_MISMATCH')
        if provenance['prompt_package']['schema_sha256']!=provenance['profile_package']['schema_sha256']:raise RuntimeBlocked('COMPILED_PROMPT_V2_PACKAGE_MISMATCH')
        document=strict_json(prompt)
        if not isinstance(document,dict) or set(document)!={'schema_version','node_kind','instructions','task_node_input','predecessor_evidence'} or document['schema_version']!='2.0.0' or document['node_kind']!='N02' or not isinstance(document['instructions'],str) or not document['instructions'].strip():raise RuntimeBlocked('COMPILED_PROMPT_V2_DOCUMENT_INVALID')
        node=document['task_node_input'];entries=document['predecessor_evidence'];pins=provenance['predecessor_evidence']
        if not isinstance(node,dict) or node.get('schema_version')!='1.0.0' or node.get('node_kind')!='N02' or not isinstance(node.get('predecessors'),list) or not isinstance(entries,list) or not isinstance(pins,list) or len(entries)!=len(pins) or len(entries)!=len(node['predecessors']) or len(entries)>512:raise RuntimeBlocked('COMPILED_PROMPT_V2_PREDECESSORS_INVALID')
        predecessor_fields={'step_id','result_artifact_id','result_sha256','result_object_version','result_receipt_sha256','evidence_artifact_id','evidence_sha256','evidence_object_version','evidence_receipt_sha256','completion_receipt_sha256'}
        pin_fields={'predecessor','object_ref','schema_ref','schema_sha256','content_roots','content_members'}
        bundle_fields={'retrieval_ids','question_coverage','selected_refs','conflict_refs','missing_requirement_ids','read_receipt_ids'}
        steps=set();artifacts=set()
        for position,(entry,pin) in enumerate(zip(entries,pins)):
            if not isinstance(entry,dict) or set(entry)!={'predecessor','evidence_bundle'} or not isinstance(pin,dict) or set(pin)!=pin_fields:raise RuntimeBlocked('COMPILED_PROMPT_V2_PREDECESSORS_INVALID')
            predecessor=entry['predecessor']
            if not isinstance(predecessor,dict) or set(predecessor)!=predecessor_fields or predecessor!=pin['predecessor'] or predecessor!=node['predecessors'][position]:raise RuntimeBlocked('COMPILED_PROMPT_V2_PREDECESSORS_INVALID')
            for key,value in predecessor.items():
                if key.endswith('_id'):uuid(value)
                elif key.endswith('sha256'):sha(value)
                elif not isinstance(value,str) or not value.strip() or len(value)>256:raise RuntimeBlocked('COMPILED_PROMPT_V2_PREDECESSORS_INVALID')
            if predecessor['step_id']==node.get('step_id') or predecessor['step_id'] in steps:raise RuntimeBlocked('COMPILED_PROMPT_V2_PREDECESSORS_INVALID')
            steps.add(predecessor['step_id'])
            for key in ('result_artifact_id','evidence_artifact_id'):
                if predecessor[key] in artifacts:raise RuntimeBlocked('COMPILED_PROMPT_V2_PREDECESSORS_INVALID')
                artifacts.add(predecessor[key])
            ref=pin['object_ref']
            if not isinstance(ref,dict) or set(ref)!={'objectKey','objectVersion','sha256','sizeBytes','mediaType'} or ref['objectVersion']!=predecessor['evidence_object_version'] or ref['sha256']!=predecessor['evidence_sha256'] or ref['mediaType']!='application/json' or not isinstance(ref['objectKey'],str) or not ref['objectKey'].startswith('tenants/'+node.get('tenant_id','')+'/') or '..' in ref['objectKey'] or not isinstance(ref['sizeBytes'],str) or not ref['sizeBytes'].isdigit() or not 0<int(ref['sizeBytes'])<=67108864:raise RuntimeBlocked('COMPILED_PROMPT_V2_OBJECT_MISMATCH')
            if pin['schema_ref']!='https://schemas.medical-agent.invalid/1.0.0/agent-design/artifacts.schema.json#/$defs/EvidenceBundle':raise RuntimeBlocked('COMPILED_PROMPT_V2_PREDECESSORS_INVALID')
            sha(pin['schema_sha256'])
            if not isinstance(pin['content_roots'],list) or not isinstance(pin['content_members'],list):raise RuntimeBlocked('COMPILED_PROMPT_V2_PREDECESSORS_INVALID')
            for root in pin['content_roots']:uuid(root)
            for member in pin['content_members']:
                if not isinstance(member,dict) or set(member)!={'upstream_version_id','rights_revision'}:raise RuntimeBlocked('COMPILED_PROMPT_V2_PREDECESSORS_INVALID')
                uuid(member['upstream_version_id']);uuid(member['rights_revision'])
            bundle=entry['evidence_bundle']
            if not isinstance(bundle,dict) or set(bundle)!=bundle_fields or any(not isinstance(bundle[key],list) for key in bundle_fields):raise RuntimeBlocked('COMPILED_PROMPT_V2_EVIDENCE_INVALID')
    except (KeyError,TypeError,ValueError,AttributeError):raise RuntimeBlocked('COMPILED_PROMPT_V2_INVALID') from None

def private_directory(path: Path):
    info = path.lstat()
    if not stat.S_ISDIR(info.st_mode) or info.st_uid != os.getuid() or info.st_mode & 0o077:
        raise RuntimeBlocked('PRIVATE_DIRECTORY_REQUIRED')
    if path.resolve() != path: raise RuntimeBlocked('SYMLINK_DIRECTORY_FORBIDDEN')

def profile_relative(value):
    # Validate the original spelling, before pathlib collapses empty or dot segments.
    if (not isinstance(value, str) or not value.startswith('profiles/') or '\\' in value
            or '%' in value or any(ord(c) < 32 or 127 <= ord(c) <= 159 for c in value)):
        raise RuntimeBlocked('PROFILE_PATH_INVALID')
    parts = value.split('/')
    if any(part in ('', '.', '..') for part in parts): raise RuntimeBlocked('PROFILE_PATH_INVALID')
    return Path(*parts)


def _directory_chain(path: Path):
    if not isinstance(path, Path) or not path.is_absolute() or '..' in path.parts:
        raise RuntimeBlocked('ABSOLUTE_NOFOLLOW_PATH_REQUIRED')
    flags = os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW | os.O_CLOEXEC | os.O_NONBLOCK
    descriptors = []
    try:
        descriptors.append((None, os.open('/', flags)))
        for part in path.parts[1:]: descriptors.append((part, os.open(part, flags, dir_fd=descriptors[-1][1])))
        return descriptors
    except BaseException:
        for _, fd in reversed(descriptors): os.close(fd)
        raise


def _bound_directories(descriptors):
    for index in range(1, len(descriptors)):
        name, fd = descriptors[index]
        actual = os.fstat(fd); current = os.stat(name, dir_fd=descriptors[index - 1][1], follow_symlinks=False)
        if not stat.S_ISDIR(current.st_mode) or (actual.st_dev, actual.st_ino) != (current.st_dev, current.st_ino):
            raise RuntimeBlocked('DIRECTORY_IDENTITY_CHANGED')


def _file_identity(info):
    return (info.st_dev, info.st_ino, info.st_mode, info.st_uid, info.st_gid, info.st_nlink,
            info.st_size, info.st_mtime_ns, info.st_ctime_ns)


def file_bytes(path: Path, maximum: int, expected: str | None = None):
    if type(maximum) is not int or not 0 < maximum <= 67108864: raise RuntimeBlocked('INVALID_FILE_LIMIT')
    if not isinstance(path, Path) or not path.is_absolute() or path.name in ('', '.', '..'): raise RuntimeBlocked('ABSOLUTE_NOFOLLOW_PATH_REQUIRED')
    directories = _directory_chain(path.parent)
    fd = None
    try:
        fd = os.open(path.name, os.O_RDONLY | os.O_NOFOLLOW | os.O_CLOEXEC | os.O_NONBLOCK, dir_fd=directories[-1][1])
        info = os.fstat(fd)
        if not stat.S_ISREG(info.st_mode) or not 0 <= info.st_size <= maximum: raise RuntimeBlocked('INVALID_FILE')
        with os.fdopen(fd, 'rb', closefd=False) as stream: value = stream.read(maximum + 1)
        if len(value) != info.st_size or _file_identity(info) != _file_identity(os.fstat(fd)):
            raise RuntimeBlocked('FILE_IDENTITY_CHANGED')
        current = os.stat(path.name, dir_fd=directories[-1][1], follow_symlinks=False)
        if _file_identity(info) != _file_identity(current): raise RuntimeBlocked('FILE_IDENTITY_CHANGED')
        _bound_directories(directories)
        if len(value) > maximum or expected is not None and digest(value) != sha(expected): raise RuntimeBlocked('FILE_HASH_MISMATCH')
        return value
    finally:
        if fd is not None: os.close(fd)
        for _, directory in reversed(directories): os.close(directory)


def _private_descriptor(fd):
    info = os.fstat(fd)
    if not stat.S_ISDIR(info.st_mode) or info.st_uid != os.getuid() or info.st_mode & 0o077:
        raise RuntimeBlocked('PRIVATE_DIRECTORY_REQUIRED')


def prepare_job(jobs: Path, components):
    # The names come from the validated immutable workflow binding, never an SDK path.
    if len(components) != 4 or any(not isinstance(part, str) or not part or part in ('.', '..')
            or '/' in part or '\\' in part or '%' in part or any(ord(c) < 32 or 127 <= ord(c) <= 159 for c in part) for part in components):
        raise RuntimeBlocked('JOB_DIRECTORY_INVALID')
    directories = _directory_chain(jobs)
    try:
        _private_descriptor(directories[-1][1])
        for index, part in enumerate(components):
            if index == 3: os.mkdir(part, 0o700, dir_fd=directories[-1][1])
            else:
                try: os.mkdir(part, 0o700, dir_fd=directories[-1][1])
                except FileExistsError: pass
            fd = os.open(part, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW | os.O_CLOEXEC, dir_fd=directories[-1][1])
            directories.append((part, fd)); _private_descriptor(fd)
        for name in ('home', 'workspace', 'tmp'): os.mkdir(name, 0o700, dir_fd=directories[-1][1])
        _bound_directories(directories)
    finally:
        for _, directory in reversed(directories): os.close(directory)


def _write_private_file(root: Path, parts, value: bytes):
    directories = _directory_chain(root)
    fd = None
    try:
        _private_descriptor(directories[-1][1])
        for part in parts[:-1]:
            try: os.mkdir(part, 0o700, dir_fd=directories[-1][1])
            except FileExistsError: pass
            directory = os.open(part, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW | os.O_CLOEXEC, dir_fd=directories[-1][1])
            directories.append((part, directory)); _private_descriptor(directory)
        fd = os.open(parts[-1], os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW | os.O_CLOEXEC, 0o400, dir_fd=directories[-1][1])
        with os.fdopen(fd, 'wb', closefd=False) as stream: stream.write(value); stream.flush()
        os.fchmod(fd, 0o400); _bound_directories(directories)
        if os.fstat(fd).st_size != len(value) or _file_identity(os.fstat(fd)) != _file_identity(os.stat(parts[-1], dir_fd=directories[-1][1], follow_symlinks=False)): raise RuntimeBlocked('PROFILE_WRITE_CHANGED')
    finally:
        if fd is not None: os.close(fd)
        for _, directory in reversed(directories): os.close(directory)


def write_profile(home: Path, relative: str, value: bytes):
    _write_private_file(home, profile_relative(relative).parts, value)


def write_inventory(job: Path, value: bytes):
    _write_private_file(job, ('runtime-inventory.json',), value)

@dataclass(frozen=True)
class Binding:
    tenant: str
    run: str
    step: str
    action: str
    generation: int
    input_sha256: str
    envelope_sha256: str
    @classmethod
    def from_workflow(cls, reference: dict, dispatch: dict):
        if reference['runId'] != dispatch['runId']: raise RuntimeBlocked('WORKFLOW_DISPATCH_MISMATCH')
        result = cls(uuid(reference['tenantId']), uuid(dispatch['runId']), uuid(dispatch['stepId']), uuid(dispatch['actionId']), dispatch['leaseGeneration'], sha(dispatch['inputSha256']), sha(dispatch['stepEnvelopeSha256']))
        if type(result.generation) is not int or result.generation < 1: raise RuntimeBlocked('INVALID_GENERATION')
        return result
    def wire(self):
        return {'tenant_id': self.tenant, 'run_id': self.run, 'step_id': self.step, 'action_id': self.action, 'lease_generation': self.generation, 'input_sha256': self.input_sha256, 'step_envelope_sha256': self.envelope_sha256}

@dataclass(frozen=True)
class Deployment:
    python: Path
    runner: Path
    runner_sha256: str
    jobs: Path
    maximum_observation_bytes: int = 16 * 1024 * 1024
    heartbeat_seconds: float = 15
    shutdown_seconds: float = 3
    def validate(self):
        if not self.python.is_absolute() or not self.python.is_file(): raise RuntimeBlocked('PYTHON_REQUIRED')
        private_directory(self.jobs)
        file_bytes(self.runner, 1024 * 1024, self.runner_sha256)
        if not 0 < self.heartbeat_seconds <= 15 or not 0 < self.shutdown_seconds <= 10 or not 0 < self.maximum_observation_bytes <= 64 * 1024 * 1024: raise RuntimeBlocked('LIMIT_INVALID')

class Control(Protocol):
    async def open(self, binding: Binding, reference: dict, dispatch: dict) -> dict: ...
    async def admit(self, binding: Binding, session: str) -> dict: ...
    async def heartbeat(self, binding: Binding, session: str) -> None: ...
    async def close_admission(self, binding: Binding, session: str, reason: str) -> None: ...
    async def finish(self, binding: Binding, session: str, observation: dict, files: list[dict]) -> dict: ...
    async def open_bridge(self, binding: Binding, session: str, job: Path): ...
    async def failed(self, binding: Binding, session: str, reason: str, process_exited: bool) -> None: ...

class ProfessionalHost:
    def __init__(self, deployment: Deployment, control: Control, supervisor=None):
        deployment.validate(); self.deployment = deployment; self.control = control
        if supervisor is None: raise RuntimeBlocked('INDEPENDENT_SUPERVISOR_REQUIRED')
        self.supervisor=supervisor

    async def execute(self, reference: dict, dispatch: dict, heartbeat=lambda _: None) -> dict:
        binding = Binding.from_workflow(reference, dispatch)
        deadline = reference['deadlineEpochMillis'] / 1000
        if deadline <= time.time(): raise RuntimeBlocked('DEADLINE_EXCEEDED')
        launch = await self.control.open(binding, reference, dispatch)
        return await self._execute_launch(binding,launch,deadline,heartbeat)

    async def _execute_launch(self,binding,launch,deadline,heartbeat):
        self._validate_launch(binding, launch)
        session = uuid(launch['session_id'])
        job = self.deployment.jobs / binding.tenant / binding.run / binding.step / str(binding.generation)
        # A reused directory/session never triggers a second model execution.
        # Recovery must query the original registered result through control.open.
        if launch['state'] == 'COMPLETED': return self._completion(binding, launch['completion'])
        owns_job = False
        process = None
        admitted = False
        bridge = None
        observation = None
        try:
            prepare_job(self.deployment.jobs, (binding.tenant, binding.run, binding.step, str(binding.generation)))
            owns_job = True
            # Profile bundle and private bridge are prepared by the isolated deployment agent,
            # never by model-controlled paths or an npm install at activity time.
            if Path(launch['job_directory']) != job: raise RuntimeBlocked('JOB_DIRECTORY_MISMATCH')
            bridge = await self.supervisor.open_bridge(binding, session, job)
            # Keep the bridge closed until independent process admission and083 exist.
            bridge.guard = None
            spec = {**launch['sdk'], 'session_id': session, 'home': str(job / 'home'), 'workspace': str(job / 'workspace'), 'binding': binding.wire()}
            # Only immutable reviewed profile files are copied. All paths stay below home.
            for item in launch['profile_files']:
                source = Path(item['source']); relative = profile_relative(item['relative'])
                value = file_bytes(source, self.deployment.maximum_observation_bytes, item['sha256'])
                for marker, replacement in {b'@@MEDICAL_SOCKET_PATH@@': str(bridge.socket).encode(), b'@@MEDICAL_TOKEN_PATH@@': str(bridge.token_path).encode(), b'@@MEDICAL_INVENTORY_PATH@@': str(bridge.inventory_path).encode()}.items():
                    value = value.replace(marker, replacement)
                write_profile(job / 'home', item['relative'], value)
            spec['prompt'] = launch['prompt']
            spec['prompt_sha256'] = launch['prompt_sha256']
            spec['prompt_provenance'] = launch['prompt_provenance']
            payload = json.dumps(spec, sort_keys=True, separators=(',', ':'), ensure_ascii=False).encode()
            if len(payload) > self.deployment.maximum_observation_bytes: raise RuntimeBlocked('INPUT_LIMIT')
            environment = {'HOME': str(job / 'home'), 'TMPDIR': str(job / 'tmp'), 'PATH': str(self.deployment.python.parent) + ':/usr/bin:/bin', 'LANG': 'C.UTF-8', 'LC_ALL': 'C.UTF-8'}
            # The dedicated service independently loads the approved plan. It never
            # turns our payload, PID or a worker boolean into an admission receipt.
            process = await self.supervisor.prepare(binding, session, job, payload, environment)
            response = await self.control.admit(binding, session)
            expected = {'binding': binding.wire(), 'session_id': session, 'admission': 'CURRENT'}
            if any(response.get(k) != v for k,v in expected.items()): raise RuntimeBlocked('SUPERVISOR_ADMISSION_MISMATCH')
            uuid(response['isolation_receipt_id']); sha(response['supervision_sha256'])
            actual_inventory = file_bytes(bridge.inventory_path, self.deployment.maximum_observation_bytes)
            self._require_inventory(launch, strict_json(actual_inventory))
            write_inventory(job, actual_inventory)
            async def guard():
                if not admitted: raise RuntimeBlocked('SUPERVISOR_ADMISSION_REQUIRED')
                await self.supervisor.current(process)
                self._require_inventory(launch, strict_json(file_bytes(bridge.inventory_path, self.deployment.maximum_observation_bytes)))
            bridge.guard = guard
            # Release itself rechecks083 through the supervisor's independent control
            # channel. A host response/boolean cannot release an unadmitted SDK.
            admitted = True
            await self.supervisor.release(process, response)
            exit_task = asyncio.create_task(self.supervisor.wait(process))
            try:
                while not exit_task.done():
                    remaining = deadline - time.time()
                    if remaining <= 0: raise RuntimeBlocked('DEADLINE_EXCEEDED')
                    await asyncio.wait({exit_task}, timeout=min(self.deployment.heartbeat_seconds, remaining))
                    if not exit_task.done():
                        await self.control.heartbeat(binding, session); heartbeat(binding.wire())
                output = await exit_task
                observation = strict_json(output)
            finally:
                if not exit_task.done():
                    exit_task.cancel();await asyncio.gather(exit_task, return_exceptions=True)
            if not isinstance(observation, dict) or observation.get('session_id') != session or observation.get('binding') != binding.wire(): raise RuntimeBlocked('SESSION_BINDING_MISMATCH')
            if observation.get('finish_reason') not in ('completed', 'max-tokens', 'error', 'aborted'): raise RuntimeBlocked('HARNESS_TERMINAL_MISSING')
            self._require_inventory(launch, strict_json(file_bytes(job / 'runtime-inventory.json', self.deployment.maximum_observation_bytes)))
            await bridge.close()
            files = self._inventory(job)
            # Backend re-reads real actions, invocation terminal/settlement and artifacts;
            # neither completed nor final_response grants SUCCEEDED here.
            result = await self.control.finish(binding, session, observation, files)
            return self._completion(binding, result)
        except BaseException as error:
            if not owns_job:
                raise RuntimeBlocked('ORIGINAL_SESSION_RECONCILIATION_REQUIRED') from None
            reason = str(error) if isinstance(error, RuntimeBlocked) else 'PROFESSIONAL_EXECUTION_INTERRUPTED'
            # Stop admission before killing so a still-running process cannot create actions.
            try:
                await self.control.close_admission(binding, session, reason)
            finally:
                try:
                    if bridge is not None: await bridge.close()
                finally:
                    try:
                        if process is not None: await self.supervisor.abort(process)
                        else: await self.supervisor.abort_session(binding,session)
                    finally:
                        await self.control.failed(binding, session, reason, False)
            raise
        # No implicit cleanup: session/input bytes follow the registered retention policy.

    async def _read_bounded(self, stream):
        result = bytearray()
        while chunk := await stream.read(65536):
            result.extend(chunk)
            if len(result) > self.deployment.maximum_observation_bytes: raise RuntimeBlocked('OBSERVATION_LIMIT')
        return bytes(result)

    def _inventory(self, job):
        result = []
        total = 0
        for path in sorted(job.rglob('*')):
            if path.is_symlink(): raise RuntimeBlocked('JOB_SYMLINK_FORBIDDEN')
            if path.is_file():
                value = file_bytes(path, self.deployment.maximum_observation_bytes)
                total += len(value)
                if total > self.deployment.maximum_observation_bytes or len(result) >= 10000: raise RuntimeBlocked('JOB_RETAINED_FILES_LIMIT')
                result.append({'path': str(path.relative_to(job)), 'sha256': digest(value), 'size_bytes': len(value)})
        return result

    @staticmethod
    def _require_inventory(launch, inventory):
        expected = launch['expected_inventory']
        if not isinstance(inventory, dict) or inventory.get('role') != launch['role']:
            raise RuntimeBlocked('RUNTIME_INVENTORY_MISMATCH')
        plugins = inventory.get('plugins', [])
        tools = inventory.get('tools', [])
        if (not all(isinstance(p, dict) and isinstance(p.get('name'), str) for p in plugins)
                or not all(isinstance(t, dict) and isinstance(t.get('name'), str) for t in tools)):
            raise RuntimeBlocked('RUNTIME_INVENTORY_MISMATCH')
        names = [p['name'] for p in plugins]
        tool_names = [t['name'] for t in tools]
        if (len(names) != len(set(names)) or len(tool_names) != len(set(tool_names))
                or sorted(names) != sorted(expected['plugin_names'])
                or {t['name']: t.get('parameters_sha256') for t in tools} != expected['tool_schema_hashes']
                or inventory.get('providers') != expected['providers']
                or inventory.get('models') != expected['models']
                or inventory.get('model_stream_schema_ref') != expected['model_stream_schema_ref']):
            raise RuntimeBlocked('RUNTIME_INVENTORY_MISMATCH')
        sha(inventory.get('configuration_sha256'))

    @staticmethod
    def _completion(binding, result):
        required = {'runId','stepId','inputSha256','leaseGeneration','status','resultArtifactId','resultSha256'}
        if not isinstance(result, dict) or set(result) != required or result['runId'] != binding.run or result['stepId'] != binding.step or result['inputSha256'] != binding.input_sha256 or result['leaseGeneration'] != binding.generation or result['status'] not in ('SUCCEEDED','FAILED','WAITING_INPUT'): raise RuntimeBlocked('REGISTERED_COMPLETION_MISMATCH')
        uuid(result['resultArtifactId']); sha(result['resultSha256']); return result

    @staticmethod
    def _validate_launch(binding, launch):
        if launch.get('binding') != binding.wire() or launch.get('purpose') not in ('TASK','EVALUATION_VERIFICATION','PLAN_DRAFT','SYSTEM_JOB') or launch.get('state') not in ('PREPARE','COMPLETED'): raise RuntimeBlocked('LAUNCH_BINDING_MISMATCH')
        if launch.get('purpose')=='SYSTEM_JOB' and (launch.get('node_kind')!='N07' or launch.get('role')!='SourceVerifier' or launch.get('prompt_provenance',{}).get('compiler')!='N07_LITERAL_INSTRUCTIONS_AND_SAVED_DOCUMENT_V1'): raise RuntimeBlocked('N07_LAUNCH_IDENTITY_MISMATCH')
        if launch.get('prompt_provenance',{}).get('compiler')=='N07_LITERAL_INSTRUCTIONS_AND_SAVED_DOCUMENT_V1' and (launch.get('purpose')!='SYSTEM_JOB' or launch.get('node_kind')!='N07'): raise RuntimeBlocked('N07_LAUNCH_IDENTITY_MISMATCH')
        if launch.get('purpose')=='PLAN_DRAFT' and (launch.get('node_kind')!='N01' or launch.get('role')!='Planner' or launch.get('prompt_provenance',{}).get('compiler')!='N01_LITERAL_INSTRUCTIONS_AND_REGISTERED_INPUT_V1'): raise RuntimeBlocked('N01_LAUNCH_IDENTITY_MISMATCH')
        if launch.get('purpose')!='PLAN_DRAFT' and launch.get('prompt_provenance',{}).get('compiler')=='N01_LITERAL_INSTRUCTIONS_AND_REGISTERED_INPUT_V1': raise RuntimeBlocked('N01_LAUNCH_IDENTITY_MISMATCH')
        if launch.get('node_kind')=='N04' and (launch.get('purpose')!='TASK' or launch.get('role')!='SectionWriter' or launch.get('prompt_provenance',{}).get('compiler')!='N04_LITERAL_INSTRUCTIONS_AND_REGISTERED_INPUT_V1'): raise RuntimeBlocked('N04_LAUNCH_IDENTITY_MISMATCH')
        if launch.get('node_kind')!='N04' and launch.get('prompt_provenance',{}).get('compiler')=='N04_LITERAL_INSTRUCTIONS_AND_REGISTERED_INPUT_V1': raise RuntimeBlocked('N04_LAUNCH_IDENTITY_MISMATCH')
        if launch.get('node_kind')=='N05' and (launch.get('purpose')!='TASK' or launch.get('role')!='SourceVerifier' or launch.get('prompt_provenance',{}).get('compiler')!='N05_LITERAL_INSTRUCTIONS_AND_REGISTERED_INPUT_V1'): raise RuntimeBlocked('N05_LAUNCH_IDENTITY_MISMATCH')
        if launch.get('node_kind')!='N05' and launch.get('prompt_provenance',{}).get('compiler')=='N05_LITERAL_INSTRUCTIONS_AND_REGISTERED_INPUT_V1': raise RuntimeBlocked('N05_LAUNCH_IDENTITY_MISMATCH')
        uuid(launch['session_id'])
        if launch['state'] == 'COMPLETED': return
        roles = {'N01': ('Planner',), 'N02': ('Researcher', 'NetworkResearcher'), 'N03': ('FieldVerifier',),
                 'N04': ('SectionWriter',), 'N05': ('SourceVerifier',), 'N06': ('Assembler',), 'N07': ('SourceVerifier',)}
        if launch.get('node_kind') not in roles or launch.get('role') not in roles[launch['node_kind']]:
            raise RuntimeBlocked('PROFESSIONAL_NODE_ROLE_MISMATCH')
        # N00 admission, N08 deterministic export, N09 recorded adoption/evaluation
        # have dedicated business handlers and must never start a model session.
        if 'isolation_receipt_id' in launch: raise RuntimeBlocked('PREMATURE_ISOLATION_RECEIPT')
        sha(launch['runtime_manifest_sha256'])
        expected = launch['expected_inventory']
        text_only = launch.get('purpose')=='PLAN_DRAFT' or launch.get('node_kind') in ('N04','N05','N07')
        if not expected.get('plugin_names') or (not expected.get('tool_schema_hashes') and not text_only) or (text_only and expected.get('tool_schema_hashes')!={}) or not expected.get('models') or expected.get('providers') != [{'id': 'enterprise-model', 'name': '企业模型接口'}]: raise RuntimeBlocked('RUNTIME_INVENTORY_REQUIRED')
        for value in expected['tool_schema_hashes'].values(): sha(value)
        sdk = launch['sdk']
        if set(sdk) != {'model','reasoning_effort','max_tokens','initialize_timeout_seconds','request_timeout_seconds','shutdown_timeout_seconds','dsh_bin','sdk_version','runtime_version'} or sdk['model'] == 'SIMULATION' or not sdk['model'] or sdk['sdk_version'] != sdk['runtime_version'] or sdk['sdk_version'] == '0.0.0.dev0': raise RuntimeBlocked('UNAPPROVED_RUNTIME')
        for key in ('max_tokens','initialize_timeout_seconds','request_timeout_seconds','shutdown_timeout_seconds'):
            if type(sdk[key]) not in (int,float) or not 0 < sdk[key] < float('inf'): raise RuntimeBlocked('LIMIT_INVALID')
        if not isinstance(launch['prompt'],str) or not launch['prompt'] or not launch['profile_files']: raise RuntimeBlocked('INPUT_MISSING')
        require_prompt(launch['prompt'],launch.get('prompt_sha256'),launch.get('prompt_provenance'),binding.input_sha256)
        if launch.get('node_kind')=='N05' and launch['prompt_provenance']['review_content']['session_id'] != launch['session_id']: raise RuntimeBlocked('N05_INDEPENDENT_SESSION_MISMATCH')
        if launch.get('node_kind')=='N07' and launch['prompt_provenance']['review_content']['session_id'] != launch['session_id']: raise RuntimeBlocked('N07_INDEPENDENT_SESSION_MISMATCH')
