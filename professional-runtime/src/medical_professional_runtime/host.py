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

def private_directory(path: Path):
    info = path.lstat()
    if not stat.S_ISDIR(info.st_mode) or info.st_uid != os.getuid() or info.st_mode & 0o077:
        raise RuntimeBlocked('PRIVATE_DIRECTORY_REQUIRED')
    if path.resolve() != path: raise RuntimeBlocked('SYMLINK_DIRECTORY_FORBIDDEN')

def file_bytes(path: Path, maximum: int, expected: str | None = None):
    fd = os.open(path, os.O_RDONLY | os.O_NOFOLLOW)
    try:
        info = os.fstat(fd)
        if not stat.S_ISREG(info.st_mode) or info.st_size > maximum: raise RuntimeBlocked('INVALID_FILE')
        with os.fdopen(fd, 'rb', closefd=False) as stream: value = stream.read(maximum + 1)
        if len(value) > maximum or expected is not None and digest(value) != sha(expected): raise RuntimeBlocked('FILE_HASH_MISMATCH')
        return value
    finally: os.close(fd)

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
    async def heartbeat(self, binding: Binding, session: str) -> None: ...
    async def close_admission(self, binding: Binding, session: str, reason: str) -> None: ...
    async def finish(self, binding: Binding, session: str, observation: dict, files: list[dict]) -> dict: ...
    async def open_bridge(self, binding: Binding, session: str, job: Path): ...
    async def failed(self, binding: Binding, session: str, reason: str, process_exited: bool) -> None: ...

class ProfessionalHost:
    def __init__(self, deployment: Deployment, control: Control):
        deployment.validate(); self.deployment = deployment; self.control = control

    async def execute(self, reference: dict, dispatch: dict, heartbeat=lambda _: None) -> dict:
        binding = Binding.from_workflow(reference, dispatch)
        deadline = reference['deadlineEpochMillis'] / 1000
        if deadline <= time.time(): raise RuntimeBlocked('DEADLINE_EXCEEDED')
        launch = await self.control.open(binding, reference, dispatch)
        self._validate_launch(binding, launch)
        session = uuid(launch['session_id'])
        job = self.deployment.jobs / binding.tenant / binding.run / binding.step / str(binding.generation)
        # A reused directory/session never triggers a second model execution.
        # Recovery must query the original registered result through control.open.
        if launch['state'] == 'COMPLETED': return self._completion(binding, launch['completion'])
        owns_job = False
        process = None
        bridge = None
        observation = None
        try:
            for directory in (job.parent.parent.parent, job.parent.parent, job.parent):
                directory.mkdir(mode=0o700, exist_ok=True); private_directory(directory)
            job.mkdir(mode=0o700, exist_ok=False)
            owns_job = True
            for name in ('home', 'workspace', 'tmp'): (job / name).mkdir(mode=0o700)
            # Profile bundle and private bridge are prepared by the isolated deployment agent,
            # never by model-controlled paths or an npm install at activity time.
            if Path(launch['job_directory']) != job: raise RuntimeBlocked('JOB_DIRECTORY_MISMATCH')
            await self.control.heartbeat(binding, session)
            bridge = await self.control.open_bridge(binding, session, job)
            bridge.guard = lambda: self._require_inventory(launch, strict_json(file_bytes(bridge.inventory_path, self.deployment.maximum_observation_bytes)))
            spec = {**launch['sdk'], 'session_id': session, 'home': str(job / 'home'), 'workspace': str(job / 'workspace'), 'binding': binding.wire()}
            # Only immutable reviewed profile files are copied. All paths stay below home.
            for item in launch['profile_files']:
                source = Path(item['source']); relative = Path(item['relative'])
                if relative.is_absolute() or '..' in relative.parts or not relative.parts or relative.parts[0] != 'profiles': raise RuntimeBlocked('PROFILE_PATH_INVALID')
                value = file_bytes(source, self.deployment.maximum_observation_bytes, item['sha256'])
                for marker, replacement in {b'@@MEDICAL_SOCKET_PATH@@': str(bridge.socket).encode(), b'@@MEDICAL_TOKEN_PATH@@': str(bridge.token_path).encode(), b'@@MEDICAL_INVENTORY_PATH@@': str(bridge.inventory_path).encode()}.items():
                    value = value.replace(marker, replacement)
                target = job / 'home' / relative
                target.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
                with open(target, 'xb') as stream: stream.write(value)
                target.chmod(0o400)
            spec['prompt'] = launch['prompt']
            payload = json.dumps(spec, separators=(',', ':'), ensure_ascii=False).encode()
            if len(payload) > self.deployment.maximum_observation_bytes: raise RuntimeBlocked('INPUT_LIMIT')
            environment = {'HOME': str(job / 'home'), 'TMPDIR': str(job / 'tmp'), 'PATH': str(self.deployment.python.parent) + ':/usr/bin:/bin', 'LANG': 'C.UTF-8', 'LC_ALL': 'C.UTF-8'}
            process = await asyncio.create_subprocess_exec(str(self.deployment.python), '-I', str(self.deployment.runner), cwd=job / 'workspace', env=environment, stdin=asyncio.subprocess.PIPE, stdout=asyncio.subprocess.PIPE, stderr=asyncio.subprocess.DEVNULL, start_new_session=True)
            process.stdin.write(payload); await process.stdin.drain(); process.stdin.close()
            read_task = asyncio.create_task(self._read_bounded(process.stdout))
            exit_task = asyncio.create_task(process.wait())
            try:
                while not exit_task.done():
                    remaining = deadline - time.time()
                    if remaining <= 0: raise RuntimeBlocked('DEADLINE_EXCEEDED')
                    await self.control.heartbeat(binding, session); heartbeat(binding.wire())
                    await asyncio.wait({exit_task, read_task}, timeout=min(self.deployment.heartbeat_seconds, remaining), return_when=asyncio.FIRST_COMPLETED)
                    if read_task.done() and read_task.exception() is not None: raise read_task.exception()
                    if read_task.done() and not exit_task.done():
                        # EOF alone is not exit; avoid polling an already completed reader.
                        await asyncio.wait({exit_task}, timeout=min(self.deployment.heartbeat_seconds, max(0, deadline-time.time())))
                output = await asyncio.wait_for(read_task, timeout=self.deployment.shutdown_seconds)
                if process.returncode != 0: raise RuntimeBlocked('HARNESS_PROCESS_FAILED')
                observation = strict_json(output)
            finally:
                await self._stop(process)
                for task in (read_task, exit_task):
                    if not task.done(): task.cancel()
                await asyncio.gather(read_task, exit_task, return_exceptions=True)
            if not isinstance(observation, dict) or observation.get('session_id') != session or observation.get('binding') != binding.wire(): raise RuntimeBlocked('SESSION_BINDING_MISMATCH')
            if observation.get('finish_reason') not in ('completed', 'max-tokens', 'error', 'aborted'): raise RuntimeBlocked('HARNESS_TERMINAL_MISSING')
            actual_inventory = file_bytes(bridge.inventory_path, self.deployment.maximum_observation_bytes)
            actual_inventory_value = strict_json(actual_inventory)
            self._require_inventory(launch, actual_inventory_value)
            with open(job / 'runtime-inventory.json', 'xb') as inventory_file: inventory_file.write(actual_inventory)
            (job / 'runtime-inventory.json').chmod(0o600)
            await bridge.close()
            await self.control.heartbeat(binding, session)
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
                        if process is not None: await self._stop(process)
                    finally:
                        await self.control.failed(binding, session, reason, process is None or process.returncode is not None)
            raise
        # No implicit cleanup: session/input bytes follow the registered retention policy.

    async def _read_bounded(self, stream):
        result = bytearray()
        while chunk := await stream.read(65536):
            result.extend(chunk)
            if len(result) > self.deployment.maximum_observation_bytes: raise RuntimeBlocked('OBSERVATION_LIMIT')
        return bytes(result)

    async def _stop(self, process):
        # Kill the entire owned process group, including an orphaned SDK child. This is
        # local exit only; it never asserts a provider invocation was cancelled.
        try: os.killpg(process.pid, signal.SIGTERM)
        except ProcessLookupError: pass
        try: await asyncio.wait_for(process.wait(), timeout=self.deployment.shutdown_seconds)
        except asyncio.TimeoutError:
            try: os.killpg(process.pid, signal.SIGKILL)
            except ProcessLookupError: pass
            await process.wait()
        try: os.killpg(process.pid, signal.SIGKILL)
        except ProcessLookupError: pass

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
        if launch.get('binding') != binding.wire() or launch.get('purpose') not in ('TASK','EVALUATION_VERIFICATION') or launch.get('state') not in ('START','COMPLETED'): raise RuntimeBlocked('LAUNCH_BINDING_MISMATCH')
        uuid(launch['session_id'])
        if launch['state'] == 'COMPLETED': return
        roles = {'N01': ('Planner',), 'N02': ('Researcher', 'NetworkResearcher'), 'N03': ('FieldVerifier',),
                 'N04': ('SectionWriter',), 'N05': ('SourceVerifier',), 'N06': ('Assembler',), 'N07': ('SourceVerifier',)}
        if launch.get('node_kind') not in roles or launch.get('role') not in roles[launch['node_kind']]:
            raise RuntimeBlocked('PROFESSIONAL_NODE_ROLE_MISMATCH')
        # N00 admission, N08 deterministic export, N09 recorded adoption/evaluation
        # have dedicated business handlers and must never start a model session.
        uuid(launch['isolation_receipt_id']); sha(launch['runtime_manifest_sha256'])
        expected = launch['expected_inventory']
        if not expected.get('plugin_names') or not expected.get('tool_schema_hashes') or not expected.get('models') or expected.get('providers') != [{'id': 'enterprise-model', 'name': '企业模型接口'}]: raise RuntimeBlocked('RUNTIME_INVENTORY_REQUIRED')
        for value in expected['tool_schema_hashes'].values(): sha(value)
        sdk = launch['sdk']
        if set(sdk) != {'model','reasoning_effort','max_tokens','initialize_timeout_seconds','request_timeout_seconds','shutdown_timeout_seconds','dsh_bin','sdk_version','runtime_version'} or sdk['model'] == 'SIMULATION' or not sdk['model'] or sdk['sdk_version'] != sdk['runtime_version'] or sdk['sdk_version'] == '0.0.0.dev0': raise RuntimeBlocked('UNAPPROVED_RUNTIME')
        for key in ('max_tokens','initialize_timeout_seconds','request_timeout_seconds','shutdown_timeout_seconds'):
            if type(sdk[key]) not in (int,float) or not 0 < sdk[key] < float('inf'): raise RuntimeBlocked('LIMIT_INVALID')
        if not isinstance(launch['prompt'],str) or not launch['prompt'] or not launch['profile_files']: raise RuntimeBlocked('INPUT_MISSING')
