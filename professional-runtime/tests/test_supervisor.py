"""Real journals/orchestration with explicit process, inspection and kernel doubles.

These tests never launch a child, create a socket, ptrace, or claim live isolation.
"""
import asyncio
import base64
from dataclasses import replace
import os
from pathlib import Path
import select
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch
from uuid import uuid4

from medical_professional_runtime.host import RuntimeBlocked, digest, strict_json
from medical_professional_runtime.linux_inspection import (
    CgroupSnapshot, ExecutableProfile, InspectionBlocked, InspectionSnapshot,
    LinuxInspectionConfig, PinnedFile, ProcessSnapshot,
)
from medical_professional_runtime.supervisor import (
    LaunchPolicy, LinuxSupervisor, OwnedLinuxProcess, SupervisorPlan, canonical,
)


NAMESPACES = tuple((name, index + 101) for index, name in enumerate(
    ('cgroup', 'ipc', 'mnt', 'net', 'pid', 'user', 'uts')))
ZERO = '0000000000000000'
BOOT = '12345678-1234-4234-9234-123456789abc'
CGROUP = Path('/sys/fs/cgroup/medical/supervisor-test')


class InspectionDouble:
    """Independent inspection, never derived from the worker observation."""
    def __init__(self, events):
        self.events = events
        self.live = False
        self.failure = None
        self.unchanged_failure = None
        self.empty_failure = None
        launcher = ExecutableProfile('a' * 64, 8, 70, 'b' * 64, 1000, 1000,
                                     NAMESPACES, 'medical (enforce)', role='launcher')
        runtime = replace(launcher, exe_sha256='c' * 64, exe_inode=71,
                          cmdline_sha256='d' * 64, role='runtime')
        self.config = LinuxInspectionConfig(
            CGROUP, 99, (launcher, runtime),
            (PinnedFile(Path('/opt/runtime/runtime.py'), 'e' * 64),),
            (PinnedFile(Path('/opt/profile/profile.json'), 'f' * 64),), BOOT)
        root = ProcessSnapshot(
            100, 1001, launcher.exe_sha256, 8, 70, launcher.cmdline_sha256,
            1000, 1000, NAMESPACES, 1, 2, ZERO, 'medical (enforce)',
            str(CGROUP), (100,), (200,), ZERO, ZERO, ZERO, ZERO)
        child = replace(root, pid=200, start_ticks=2001,
                        exe_sha256=runtime.exe_sha256, exe_inode=71,
                        cmdline_sha256=runtime.cmdline_sha256,
                        task_ids=(200,), children=())
        self.value = InspectionSnapshot(
            BOOT, str(CGROUP), 99, (100, 200), (root, child),
            self.config.runtime_files, self.config.profile_files,
            (CgroupSnapshot(str(CGROUP), 99, (100, 200), (100, 200)),))

    def snapshot(self):
        self.events.append('snapshot')
        if self.failure:
            raise self.failure
        if not self.live:
            raise InspectionBlocked('INSPECTION_RUNTIME_REQUIRED')
        return self.value

    def assert_unchanged(self, expected):
        self.events.append('unchanged')
        if self.unchanged_failure:
            raise self.unchanged_failure
        actual = self.snapshot()
        if actual != expected:
            raise InspectionBlocked('INSPECTION_CHANGED')
        return actual

    def assert_empty(self):
        self.events.append('empty')
        if self.empty_failure:
            raise self.empty_failure
        if self.live:
            raise InspectionBlocked('INSPECTION_UNIT_NOT_EMPTY')

    def kill_unit(self):
        self.events.append('kill_unit')
        self.live = False


class TraceDouble:
    def __init__(self, events):
        self.events = events
        self.tids = {100, 200}
        self.failure = None
        self.root_terminal = None
        self.closed = False

    def check(self):
        self.events.append('trace_check')
        if self.failure:
            raise self.failure

    async def close(self):
        self.events.append('trace_close')
        self.closed = True


class ProcessDouble(OwnedLinuxProcess):
    """Production wait/current/exit observation with explicitly synthetic handles."""
    def __init__(self, plan, events):
        super().__init__(plan)
        self.events = events
        self.trace = TraceDouble(events)
        self.launch_failure = None
        self.pin_failure = None
        self.wait_failure = None
        self.abort_failure = None
        self.exit_status = 0
        self.exit_code = None
        self.exit_masks = {}
        self.exited = False
        self.released = False
        self.aborted = False
        self.handles_closed = False
        self.raw = canonical({'session_id': plan.session_id,
                              'binding': dict(plan.binding),
                              'status': 'completed', 'claimed_files': ['not-authority']})

    async def launch_ready(self):
        self.events.append('launch_ready')
        if self.launch_failure:
            raise self.launch_failure
        self.process = SimpleNamespace(pid=100)
        self.plan.inspector.live = True
        self.events.append('sdk_ready_locked')

    def pin(self, snapshot):
        self.events.append('pin')
        if self.pin_failure:
            raise self.pin_failure
        self.admission_snapshot = snapshot
        self.pidfds = {(item.pid, item.start_ticks): 900 + index
                       for index, item in enumerate(snapshot.processes)}
        self.root_identity = (100, 1001)
        loop = asyncio.get_running_loop()
        self.exit_task = loop.create_future()
        self.output_task = loop.create_future()

    def release(self):
        self.events.append('release')
        self.trace.check()
        self.released = True

    def observe_exits(self):
        process = self

        class PollDouble:
            def register(self, fd, mask):
                self.fd = fd

            def poll(self, timeout):
                mask = process.exit_masks.get(self.fd, select.POLLIN if process.exited else 0)
                return [(self.fd, mask)] if mask else []

        with patch('medical_professional_runtime.supervisor.select.poll', PollDouble):
            return super().observe_exits()

    async def wait(self):
        self.events.append('owned_wait')
        if self.wait_failure:
            raise self.wait_failure
        self.exited = True
        self.trace.root_terminal={'code':self.exit_code or ('EXITED' if self.exit_status>=0 else 'KILLED'),'status':abs(self.exit_status)}
        self.plan.inspector.live = False
        self.exit_task.set_result(self.exit_status)
        self.output_task.set_result(self.raw)
        return await super().wait()

    async def abort(self):
        self.events.append('abort')
        self.aborted = True
        self.plan.inspector.live = False
        for future in (self.exit_task, self.output_task):
            if future is not None and not future.done():
                future.cancel()
        if self.abort_failure:
            raise self.abort_failure

    def close_handles(self):
        self.events.append('close_handles')
        self.handles_closed = True
        self.pidfds = {}


def make_fixture(directory):
    """Shared file fixture for recorder tests; it has no privileged operations."""
    root = Path(directory)
    receipts = root / 'receipts'
    receipts.mkdir(mode=0o700)
    job = root / 'job'
    job.mkdir(mode=0o700)
    workspace = job / 'workspace'
    workspace.mkdir(mode=0o700)
    (workspace / 'result.txt').write_bytes('独立归档\n'.encode())
    runner = root / 'runner.py'
    runner.write_bytes(b'# pinned fixture; never executed\n')
    events = []
    inspector = InspectionDouble(events)
    inspector.config = replace(
        inspector.config,
        runtime_files=inspector.config.runtime_files + (PinnedFile(runner, digest(runner.read_bytes())),))
    inspector.value = replace(inspector.value, runtime_files=inspector.config.runtime_files)
    session = str(uuid4())
    binding = {'tenant_id': str(uuid4()), 'run_id': str(uuid4()),
               'step_id': str(uuid4()), 'action_id': str(uuid4()),
               'lease_generation': 1, 'input_sha256': '1' * 64,
               'step_envelope_sha256': '2' * 64}
    # Explicit compiled-package doubles exercise integrity, not package authority.
    # No fixture is obtained from a production loader or used for live execution.
    prompt = 'Literal fixture instructions.\nRegistered fixture input.\n'
    prompt_sha256 = digest(prompt.encode('utf-8'))
    provenance = {
        'schema_version': '1.0.0',
        'compiler': 'N02_LITERAL_INSTRUCTIONS_AND_REGISTERED_INPUT_V1',
        'input_artifact_id': str(uuid4()), 'input_sha256': binding['input_sha256'],
        'input_receipt_sha256': '6' * 64, 'launch_sha256': prompt_sha256,
        **{key: {'fixture_package': key, 'artifact_id': str(uuid4()),
                 'sha256': digest(key.encode())}
           for key in ('role_basis', 'prompt_basis', 'role_package', 'prompt_package',
                       'profile_package', 'adapter_artifact')}}
    revision = str(uuid4())
    envelope = canonical(
        {key: value for key, value in binding.items() if key != 'step_envelope_sha256'} |
        {'session_id': session, 'deployment_revision': revision, 'deployment_sha256': '3' * 64,
         'runtime_manifest_sha256': '4' * 64, 'compiled_prompt':prompt, 'prompt_sha256': prompt_sha256,
         'prompt_provenance': provenance})
    binding['step_envelope_sha256'] = digest(envelope)
    payload = canonical({'session_id': session, 'binding': binding, 'prompt': prompt,
                         'prompt_sha256': prompt_sha256, 'prompt_provenance': provenance})
    plan = SupervisorPlan(
        session, binding, revision, '3' * 64, '4' * 64,
        job, Path('/operator/pinned/python'), runner, digest(runner.read_bytes()),
        payload, {}, inspector,
        LaunchPolicy((), 1000, 1000, 'medical (enforce)', root / 'seccomp', '5' * 64),
        registered_envelope=envelope, maximum_bytes=1024 * 1024)
    process = ProcessDouble(plan, events)
    supervisor = LinuxSupervisor._testing(plan, receipts, process)
    return SimpleNamespace(plan=plan, receipts=receipts, job=job, inspector=inspector,
                           process=process, supervisor=supervisor, events=events)


class SupervisorLifecycleTest(unittest.IsolatedAsyncioTestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.fixture = make_fixture(self.temporary.name)
        self.supervisor = self.fixture.supervisor
        self.process = self.fixture.process

    async def asyncTearDown(self):
        cleanup = getattr(self.supervisor, 'cleanup', None)
        if cleanup is not None:
            await asyncio.gather(cleanup, return_exceptions=True)
        for future in (self.process.exit_task, self.process.output_task):
            if future is not None and not future.done():
                future.cancel()

    async def close_original(self):
        await self.supervisor.prepare()
        self.supervisor.release()
        return await self.supervisor.finish()

    def assert_unknown(self, *, terminated=False):
        self.assertEqual(self.supervisor.state, 'TERMINATED' if terminated else 'UNKNOWN')
        self.assertTrue(self.process.trace.closed)
        self.assertTrue(self.process.handles_closed)
        if terminated:
            self.assertEqual(self.supervisor.receipt('TERMINATED')['phase'], 'TERMINATED')
        else:
            with self.assertRaisesRegex(RuntimeBlocked, 'TERMINATION_EVIDENCE_MISSING'):
                self.supervisor.receipt('TERMINATED')
        self.assertTrue(self.process.aborted)
        self.assertTrue((self.supervisor.root / 'blocked.json').is_file())
        self.assertFalse((self.supervisor.root / 'closed.json').exists())
        with self.assertRaisesRegex(RuntimeBlocked, 'CLOSURE_EVIDENCE_MISSING'):
            self.supervisor.receipt('CLOSED')

    async def test_readiness_inspection_durable_admission_release_owned_exit_archive(self):
        original_fsync = os.fsync
        with patch('medical_professional_runtime.supervisor.os.fsync', wraps=original_fsync) as sync:
            admitted = await self.supervisor.prepare()
            self.assertGreaterEqual(sync.call_count, 4)
        self.assertEqual(self.supervisor.state, 'ADMITTED')
        self.assertFalse(self.process.released)
        self.assertEqual(admitted['gate'], 'SDK_READY_LOCKED')
        self.assertEqual(admitted['process_unit'], self.fixture.inspector.value.wire())
        self.assertEqual((self.supervisor.root / 'admitted.json').read_bytes(), canonical(admitted))
        self.assertEqual(self.fixture.events[:5],
                         ['empty', 'launch_ready', 'sdk_ready_locked', 'snapshot', 'pin'])
        self.assertLess(self.fixture.events.index('pin'), self.fixture.events.index('unchanged'))
        self.supervisor.release()
        self.assertTrue(self.process.released)
        self.assertEqual(self.supervisor.state, 'RUNNING')
        raw = await self.supervisor.finish()
        self.assertEqual(raw, self.process.raw)
        self.assertEqual(self.supervisor.state, 'CLOSED')
        closed = self.supervisor.receipt('CLOSED')
        self.assertEqual(closed['admitted_sha256'], digest(canonical(admitted)))
        self.assertEqual(closed['exit']['kind'], 'OWNED_WAITID')
        self.assertEqual(closed['exit']['observed_exits'], [
            {'pid': 100, 'start_ticks': 1001, 'kind': 'PIDFD_EXIT'},
            {'pid': 200, 'start_ticks': 2001, 'kind': 'PIDFD_EXIT'}])
        archive = strict_json(self.supervisor.session_export())
        self.assertEqual([item['path'] for item in archive['files']], ['workspace/result.txt'])
        self.assertEqual(base64.b64decode(archive['files'][0]['content_base64']),
                         (self.fixture.job / 'workspace/result.txt').read_bytes())
        self.assertEqual(archive['observation'], strict_json(raw))
        self.assertTrue(self.process.trace.closed)
        self.assertTrue(self.process.handles_closed)

    async def test_missing_actual_sdk_readiness_never_admits(self):
        self.process.launch_failure = RuntimeBlocked('SDK_READINESS_MISSING')
        with self.assertRaisesRegex(RuntimeBlocked, 'SDK_READINESS_MISSING'):
            await self.supervisor.prepare()
        self.assert_unknown()
        self.assertFalse((self.supervisor.root / 'admitted.json').exists())
        self.assertNotIn('snapshot', self.fixture.events)

    async def test_missing_independent_inspection_never_admits(self):
        self.fixture.inspector.failure = InspectionBlocked('INSPECTION_RUNTIME_REQUIRED')
        with self.assertRaisesRegex(InspectionBlocked, 'RUNTIME_REQUIRED'):
            await self.supervisor.prepare()
        self.assert_unknown()
        self.assertFalse(self.process.released)

    async def test_trace_install_failure_never_admits(self):
        self.process.pin_failure = RuntimeBlocked('PTRACE_REQUIRED')
        with self.assertRaisesRegex(RuntimeBlocked, 'PTRACE_REQUIRED'):
            await self.supervisor.prepare()
        self.assert_unknown()

    async def test_post_pin_change_never_admits(self):
        self.fixture.inspector.unchanged_failure = InspectionBlocked('PROFILE_CHANGED')
        with self.assertRaisesRegex(InspectionBlocked, 'PROFILE_CHANGED'):
            await self.supervisor.prepare()
        self.assert_unknown()

    async def test_durable_admission_failure_keeps_gate_locked(self):
        persist = self.supervisor._persist

        def fail_admission(name, raw):
            if name == 'admitted.json':
                raise OSError('simulated fsync failure')
            return persist(name, raw)

        with patch.object(self.supervisor, '_persist', side_effect=fail_admission):
            with self.assertRaisesRegex(OSError, 'fsync failure'):
                await self.supervisor.prepare()
        self.assert_unknown()
        self.assertFalse(self.process.released)

    async def test_owned_wait_unknown_never_infers_exit_from_empty_unit(self):
        await self.supervisor.prepare()
        self.supervisor.release()
        self.fixture.inspector.live = False
        self.process.wait_failure = RuntimeBlocked('OWNED_EXIT_UNKNOWN')
        with self.assertRaisesRegex(RuntimeBlocked, 'OWNED_EXIT_UNKNOWN'):
            await self.supervisor.finish()
        self.assert_unknown()

    async def test_missing_one_descendant_pidfd_exit_never_closes(self):
        await self.supervisor.prepare()
        self.supervisor.release()
        self.process.exit_masks[901] = 0
        with self.assertRaisesRegex(RuntimeBlocked, 'DESCENDANT_EXIT_UNKNOWN'):
            await self.supervisor.finish()
        self.assert_unknown()

    async def test_invalid_pidfd_poll_is_not_exit(self):
        await self.supervisor.prepare()
        self.supervisor.release()
        self.process.exit_masks[901] = select.POLLNVAL
        with self.assertRaisesRegex(RuntimeBlocked, 'PIDFD_EXIT_UNKNOWN'):
            await self.supervisor.finish()
        self.assert_unknown()

    async def test_trace_violation_at_exit_never_closes(self):
        await self.supervisor.prepare()
        self.supervisor.release()
        self.process.trace.failure = RuntimeBlocked('SUPERVISED_EXEC_OR_CHILD_CHANGE')
        with self.assertRaisesRegex(RuntimeBlocked, 'SUPERVISED_EXEC_OR_CHILD_CHANGE'):
            await self.supervisor.finish()
        self.assert_unknown()

    async def test_cancelled_finish_cleans_up_without_closure(self):
        await self.supervisor.prepare()
        self.supervisor.release()
        self.process.wait_failure = asyncio.CancelledError()
        with self.assertRaises(asyncio.CancelledError):
            await self.supervisor.finish()
        self.assert_unknown()

    async def test_wrong_worker_binding_is_not_an_observation(self):
        self.process.raw = canonical({'session_id': self.fixture.plan.session_id, 'binding': {}})
        with self.assertRaisesRegex(RuntimeBlocked, 'SUPERVISOR_OBSERVATION_MISMATCH'):
            await self.close_original()
        self.assert_unknown(terminated=True)

    async def test_signalled_owned_child_records_signal_not_success(self):
        self.process.exit_status = -9
        await self.close_original()
        evidence = self.supervisor.receipt('CLOSED')['exit']
        self.assertEqual((evidence['code'], evidence['status']), ('KILLED', 9))

    async def test_lost_admission_response_cannot_leave_gate_locked_forever(self):
        self.supervisor.plan=replace(self.supervisor.plan,ready_seconds=.001)
        await self.supervisor.prepare()
        await asyncio.sleep(.02)
        await self.supervisor.cleanup
        self.assertEqual(self.supervisor.state,'UNKNOWN')
        self.assertFalse(self.process.released)
        self.assertTrue(self.process.aborted)
        self.assertFalse((self.supervisor.root/'closed.json').exists())

    async def test_release_cancels_locked_gate_timeout(self):
        self.supervisor.plan=replace(self.supervisor.plan,ready_seconds=.001)
        await self.supervisor.prepare()
        self.supervisor.release()
        await asyncio.sleep(.02)
        self.assertEqual(self.supervisor.state,'RUNNING')
        self.assertFalse(self.process.aborted)

    async def test_dumped_owned_child_preserves_original_waitid_code(self):
        self.process.exit_status=-11
        self.process.exit_code='DUMPED'
        await self.close_original()
        evidence=self.supervisor.receipt('CLOSED')['exit']
        self.assertEqual((evidence['code'],evidence['status']),('DUMPED',11))

    async def test_archive_symlink_is_rejected_not_followed(self):
        secret = Path(self.temporary.name) / 'outside-secret'
        secret.write_bytes(b'must not enter archive')
        (self.fixture.job / 'workspace/link').symlink_to(secret)
        with self.assertRaisesRegex(RuntimeBlocked, 'ARCHIVE_SYMLINK_FORBIDDEN'):
            await self.close_original()
        self.assert_unknown(terminated=True)
        self.assertFalse((self.supervisor.root / 'session-export.json').exists())

    async def test_archive_is_independent_of_later_worker_file_mutation(self):
        await self.close_original()
        original = self.supervisor.session_export()
        (self.fixture.job / 'workspace/result.txt').write_bytes(b'changed after closure')
        self.assertEqual(self.supervisor.session_export(), original)

    async def test_archive_mutation_invalidates_closed_readback(self):
        await self.close_original()
        (self.supervisor.root / 'session-export.json').write_bytes(b'{}')
        with self.assertRaisesRegex(RuntimeBlocked, 'FILE_HASH_MISMATCH'):
            self.supervisor.receipt('CLOSED')

    async def test_admitted_file_mutation_prevents_release(self):
        await self.supervisor.prepare()
        (self.supervisor.root / 'admitted.json').write_bytes(b'{}')
        with self.assertRaisesRegex(RuntimeBlocked, 'FILE_HASH_MISMATCH'):
            self.supervisor.release()
        self.assertFalse(self.process.released)
        await self.supervisor.cleanup
        self.assert_unknown()

    async def test_current_snapshot_change_prevents_release(self):
        await self.supervisor.prepare()
        self.fixture.inspector.value = replace(
            self.fixture.inspector.value,
            profile_files=(PinnedFile(Path('/opt/profile/profile.json'), '0' * 64),))
        with self.assertRaisesRegex(RuntimeBlocked, 'PROCESS_IDENTITY_CHANGED'):
            self.supervisor.release()
        self.assertFalse(self.process.released)
        await self.supervisor.cleanup
        self.assert_unknown()

    async def test_untraced_thread_prevents_release(self):
        await self.supervisor.prepare()
        old = self.fixture.inspector.value
        child = replace(old.processes[1], task_ids=(200, 201))
        self.fixture.inspector.value = replace(old, processes=(old.processes[0], child))
        with self.assertRaisesRegex(RuntimeBlocked, 'UNTRACED_THREAD'):
            self.supervisor.release()
        self.assertFalse(self.process.released)
        await self.supervisor.cleanup
        self.assert_unknown()

    async def test_release_failure_after_last_current_check_invalidates_and_cleans_up(self):
        await self.supervisor.prepare()
        with patch.object(self.process, 'release', side_effect=OSError('synthetic gate failure')):
            with self.assertRaisesRegex(OSError, 'gate failure'):
                self.supervisor.release()
        self.assertEqual(self.supervisor.state, 'UNKNOWN')
        await self.supervisor.cleanup
        self.assert_unknown()

    async def test_failed_blocked_journal_does_not_prevent_owned_cleanup(self):
        await self.supervisor.prepare()
        self.process.trace.failure = RuntimeBlocked('TRACE_NOT_CURRENT')
        original = self.supervisor._persist

        def failed_blocked(name, raw):
            if name == 'blocked.json':
                raise OSError('synthetic blocked journal failure')
            return original(name, raw)

        with patch.object(self.supervisor, '_persist', side_effect=failed_blocked):
            with self.assertRaises((RuntimeBlocked, OSError)):
                self.supervisor.current()
        self.assertEqual(self.supervisor.state, 'UNKNOWN')
        self.assertIsNotNone(getattr(self.supervisor, 'cleanup', None))
        await self.supervisor.cleanup
        self.assert_unknown()

    async def test_uncertain_closed_write_is_blocked_not_restart_recoverable(self):
        persist = self.supervisor._persist

        def uncertain_closed(name, raw):
            persist(name, raw)
            if name == 'closed.json':
                raise OSError('synthetic closed fsync failure')

        with patch.object(self.supervisor, '_persist', side_effect=uncertain_closed):
            with self.assertRaisesRegex(OSError, 'closed fsync failure'):
                await self.close_original()
        self.assertEqual(self.supervisor.state, 'TERMINATED')
        self.assertEqual(self.supervisor.receipt('TERMINATED')['phase'], 'TERMINATED')
        self.assertTrue(self.process.aborted)
        self.assertTrue(self.process.handles_closed)
        with self.assertRaisesRegex(RuntimeBlocked, 'CLOSURE_EVIDENCE_MISSING'):
            self.supervisor.receipt('CLOSED')
        with self.assertRaisesRegex(RuntimeBlocked, 'ORIGINAL_EXIT_UNKNOWN'):
            LinuxSupervisor.recover_closed(self.fixture.plan, self.fixture.receipts)

    async def test_prepare_return_does_not_alias_original_evidence(self):
        evidence = await self.supervisor.prepare()
        evidence['binding']['lease_generation'] = 999
        evidence['process_unit']['processes'][0]['pid'] = 999
        current = self.supervisor.current()
        self.assertEqual(current['binding']['lease_generation'], 1)
        self.assertEqual(current['process_unit']['processes'][0]['pid'], 100)

    async def test_duplicate_prepare_never_launches_new_process(self):
        await self.supervisor.prepare()
        restarted = LinuxSupervisor._testing(self.fixture.plan, self.fixture.receipts, self.process)
        with self.assertRaisesRegex(RuntimeBlocked, 'ORIGINAL_SUPERVISOR_RECOVERY_REQUIRED'):
            await restarted.prepare()
        self.assertEqual(self.fixture.events.count('launch_ready'), 1)

    async def test_partial_journal_cannot_recover_from_empty_cgroup(self):
        await self.supervisor.prepare()
        self.fixture.inspector.live = False
        with self.assertRaises((RuntimeBlocked, OSError)):
            LinuxSupervisor.recover_closed(self.fixture.plan, self.fixture.receipts)

    async def test_blocked_journal_cannot_recover_even_if_other_files_complete(self):
        await self.close_original()
        self.supervisor._persist('blocked.json', canonical({'state': 'UNKNOWN'}))
        with self.assertRaisesRegex(RuntimeBlocked, 'ORIGINAL_EXIT_UNKNOWN'):
            LinuxSupervisor.recover_closed(self.fixture.plan, self.fixture.receipts)

    async def test_complete_original_closure_recovers_without_process_launch(self):
        await self.close_original()
        recovered = LinuxSupervisor.recover_closed(self.fixture.plan, self.fixture.receipts)
        self.assertEqual(recovered.state, 'CLOSED')
        self.assertEqual(recovered.instance, self.supervisor.instance)
        self.assertEqual(recovered.receipt('CLOSED'), self.supervisor.receipt('CLOSED'))
        self.assertEqual(recovered.session_export(), self.supervisor.session_export())
        self.assertEqual(self.fixture.events.count('launch_ready'), 1)

    async def test_restart_rejects_changed_manifest_or_deployment_revision(self):
        await self.close_original()
        for change in ({'runtime_manifest_sha256': '9' * 64},
                       {'deployment_revision': str(uuid4())},
                       {'deployment_sha256': '8' * 64}):
            with self.subTest(change=change):
                with self.assertRaises(RuntimeBlocked):
                    LinuxSupervisor.recover_closed(replace(self.fixture.plan, **change),
                                                   self.fixture.receipts)

    async def test_restart_rejects_changed_operator_profile_pin(self):
        await self.close_original()
        self.fixture.inspector.config = replace(
            self.fixture.inspector.config,
            profile_files=(PinnedFile(Path('/opt/profile/profile.json'), '0' * 64),))
        with self.assertRaises(RuntimeBlocked):
            LinuxSupervisor.recover_closed(self.fixture.plan, self.fixture.receipts)

    async def test_restart_rejects_changed_executable_identity_and_confinement_pins(self):
        await self.close_original()
        config = self.fixture.inspector.config
        runtime = config.executable_profiles[1]
        changes = ({'uid': 1001}, {'gid': 1001}, {'exe_device': 9}, {'exe_inode': 90},
                   {'exe_sha256': '9' * 64}, {'cmdline_sha256': '8' * 64},
                   {'no_new_privs': 0}, {'seccomp': 0}, {'lsm_profile': 'other (enforce)'},
                   {'namespaces': tuple((name, inode + 1) for name, inode in NAMESPACES)},
                   *({field: '0000000000000001'} for field in
                     ('cap_eff', 'cap_inh', 'cap_prm', 'cap_bnd', 'cap_amb')))
        for change in changes:
            with self.subTest(change=change):
                self.fixture.inspector.config = replace(
                    config, executable_profiles=(config.executable_profiles[0], replace(runtime, **change)))
                with self.assertRaises(RuntimeBlocked):
                    LinuxSupervisor.recover_closed(self.fixture.plan, self.fixture.receipts)
        self.fixture.inspector.config = config

    async def test_restart_rejects_incomplete_original_exit_set(self):
        await self.close_original()
        closed = strict_json((self.supervisor.root / 'closed.json').read_bytes())
        closed['exit']['observed_exits'].pop()
        (self.supervisor.root / 'closed.json').write_bytes(canonical(closed))
        with self.assertRaisesRegex(RuntimeBlocked, 'ORIGINAL_EXIT_UNKNOWN'):
            LinuxSupervisor.recover_closed(self.fixture.plan, self.fixture.receipts)

    async def test_restart_rejects_exit_from_wrong_boot_or_uninspected_root(self):
        await self.close_original()
        path = self.supervisor.root / 'closed.json'
        original = path.read_bytes()
        for change in ({'boot_id': str(uuid4())}, {'root_pid': 999}):
            with self.subTest(change=change):
                closed = strict_json(original)
                closed['exit'].update(change)
                path.write_bytes(canonical(closed))
                with self.assertRaisesRegex(RuntimeBlocked, 'ORIGINAL_EXIT_UNKNOWN'):
                    LinuxSupervisor.recover_closed(self.fixture.plan, self.fixture.receipts)
        path.write_bytes(original)

    async def test_restart_rejects_changed_admission_chain(self):
        await self.close_original()
        admitted = strict_json((self.supervisor.root / 'admitted.json').read_bytes())
        admitted['gate'] = 'CHANGED'
        (self.supervisor.root / 'admitted.json').write_bytes(canonical(admitted))
        with self.assertRaisesRegex(RuntimeBlocked, 'ORIGINAL_SUPERVISOR_MISMATCH'):
            LinuxSupervisor.recover_closed(self.fixture.plan, self.fixture.receipts)

    async def test_receipts_are_exclusive_and_private_files(self):
        await self.supervisor.prepare()
        path = self.supervisor.root / 'admitted.json'
        original = path.read_bytes()
        self.assertEqual(path.stat().st_mode & 0o777, 0o600)
        self.assertEqual(self.supervisor.root.stat().st_mode & 0o777, 0o700)
        with self.assertRaises(FileExistsError):
            self.supervisor._persist('admitted.json', b'overwrite')
        self.assertEqual(path.read_bytes(), original)

    async def test_abort_failure_still_closes_owned_trace_and_handles(self):
        await self.supervisor.prepare()
        self.process.abort_failure = RuntimeBlocked('OWNED_EXIT_UNKNOWN')
        with self.assertRaisesRegex(RuntimeBlocked, 'OWNED_EXIT_UNKNOWN'):
            await self.supervisor.abort()
        self.assert_unknown()
        self.assertFalse((self.supervisor.root / 'terminated.json').exists())

    async def test_trace_close_failure_still_closes_handles_and_blocks(self):
        await self.supervisor.prepare()
        async def failed_close():
            raise RuntimeBlocked('TRACE_CLOSE_FAILED')
        self.process.trace.close = failed_close
        with self.assertRaisesRegex(RuntimeBlocked, 'TRACE_CLOSE_FAILED'):
            await self.supervisor.abort()
        self.assertEqual(self.supervisor.state, 'UNKNOWN')
        self.assertTrue(self.process.handles_closed)
        self.assertTrue((self.supervisor.root / 'blocked.json').is_file())
        self.assertFalse((self.supervisor.root / 'closed.json').exists())
        self.assertFalse((self.supervisor.root / 'terminated.json').exists())

    async def test_finish_preserves_failure_with_cleanup_error_as_cause(self):
        await self.supervisor.prepare()
        self.supervisor.release()
        self.process.wait_failure = RuntimeBlocked('ORIGINAL_WAIT_FAILURE')
        self.process.abort_failure = RuntimeBlocked('CLEANUP_WAIT_FAILURE')
        with self.assertRaisesRegex(RuntimeBlocked, 'ORIGINAL_WAIT_FAILURE') as raised:
            await self.supervisor.finish()
        self.assertEqual(str(raised.exception.__cause__), 'CLEANUP_WAIT_FAILURE')
        self.assert_unknown()

    async def test_abort_never_creates_successful_closure(self):
        await self.supervisor.prepare()
        await self.supervisor.abort()
        self.assert_unknown()

    async def test_prepare_failure_closes_trace_and_owned_handles(self):
        self.fixture.inspector.unchanged_failure = InspectionBlocked('INSPECTION_CHANGED')
        with self.assertRaises(InspectionBlocked):
            await self.supervisor.prepare()
        self.assertTrue(self.process.trace.closed)
        self.assertTrue(self.process.handles_closed)


class SupervisorPromptPlanTest(unittest.TestCase):
    """Compiled prompt pins must match physically loaded final-envelope bytes."""
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.fixture = make_fixture(self.temporary.name)
        self.plan = self.fixture.plan

    def replace_payload(self, value):
        return replace(self.plan, payload=canonical(value))

    def replace_envelope_and_byte_pin(self, raw):
        binding = dict(self.plan.binding) | {'step_envelope_sha256': digest(raw)}
        payload = strict_json(self.plan.payload)
        payload['binding'] = binding
        return replace(self.plan, binding=binding, payload=canonical(payload), registered_envelope=raw)

    def test_fixture_prompt_and_final_envelope_are_exactly_pinned(self):
        payload = strict_json(self.plan.payload)
        envelope = strict_json(self.plan.registered_envelope)
        self.assertEqual(digest(self.plan.registered_envelope), self.plan.binding['step_envelope_sha256'])
        self.assertEqual(digest(payload['prompt'].encode()), payload['prompt_sha256'])
        self.assertEqual(envelope['prompt_sha256'], payload['prompt_sha256'])
        self.assertEqual(envelope['prompt_provenance'], payload['prompt_provenance'])
        self.assertEqual(envelope['session_id'], self.plan.session_id)
        self.assertEqual(envelope['deployment_revision'], self.plan.deployment_revision)
        self.assertEqual(envelope['runtime_manifest_sha256'], self.plan.runtime_manifest_sha256)

    def test_missing_prompt_hash_or_provenance_fails_before_process_launch(self):
        for field in ('prompt', 'prompt_sha256', 'prompt_provenance'):
            with self.subTest(field=field):
                payload = strict_json(self.plan.payload)
                payload.pop(field)
                with self.assertRaises(RuntimeBlocked):
                    self.replace_payload(payload)
        self.assertEqual(self.fixture.events, [])

    def test_changed_prompt_bytes_cannot_reuse_compiled_hash(self):
        for prompt in ('', 'Different instructions', strict_json(self.plan.payload)['prompt'] + ' '):
            with self.subTest(prompt=prompt):
                payload = strict_json(self.plan.payload)
                payload['prompt'] = prompt
                with self.assertRaisesRegex(RuntimeBlocked, 'COMPILED_PROMPT_'):
                    self.replace_payload(payload)

    def test_wrong_compiler_input_or_launch_pin_is_rejected(self):
        for field, value in (('compiler', 'UNTRUSTED_COMPILER'), ('schema_version', '2.0.0'),
                             ('input_sha256', '7' * 64), ('launch_sha256', '8' * 64)):
            with self.subTest(field=field):
                payload = strict_json(self.plan.payload)
                payload['prompt_provenance'][field] = value
                with self.assertRaisesRegex(RuntimeBlocked, 'COMPILED_PROMPT_MISMATCH'):
                    self.replace_payload(payload)

    def test_prompt_provenance_is_closed_and_all_package_pins_are_required(self):
        original = strict_json(self.plan.payload)
        for field in original['prompt_provenance']:
            with self.subTest(missing=field):
                payload = strict_json(self.plan.payload)
                payload['prompt_provenance'].pop(field)
                with self.assertRaisesRegex(RuntimeBlocked, 'COMPILED_PROMPT_REQUIRED'):
                    self.replace_payload(payload)
        payload = strict_json(self.plan.payload)
        payload['prompt_provenance']['caller_approved'] = True
        with self.assertRaisesRegex(RuntimeBlocked, 'COMPILED_PROMPT_REQUIRED'):
            self.replace_payload(payload)
        for field in ('role_basis', 'prompt_basis', 'role_package', 'prompt_package',
                      'profile_package', 'adapter_artifact'):
            with self.subTest(empty=field):
                payload = strict_json(self.plan.payload)
                payload['prompt_provenance'][field] = {}
                with self.assertRaisesRegex(RuntimeBlocked, 'COMPILED_PROMPT_REQUIRED'):
                    self.replace_payload(payload)

    def test_package_pin_change_must_match_registered_envelope_not_only_prompt_hash(self):
        for field in ('role_basis', 'prompt_basis', 'role_package', 'prompt_package',
                      'profile_package', 'adapter_artifact'):
            with self.subTest(field=field):
                payload = strict_json(self.plan.payload)
                payload['prompt_provenance'][field]['sha256'] = '9' * 64
                with self.assertRaisesRegex(RuntimeBlocked, 'REGISTERED_ENVELOPE_MISMATCH'):
                    self.replace_payload(payload)

    def test_rehashed_changed_prompt_still_requires_registered_envelope_pin(self):
        payload = strict_json(self.plan.payload)
        payload['prompt'] = 'Changed but self-consistently rehashed instructions'
        payload['prompt_sha256'] = digest(payload['prompt'].encode())
        payload['prompt_provenance']['launch_sha256'] = payload['prompt_sha256']
        with self.assertRaisesRegex(RuntimeBlocked, 'REGISTERED_ENVELOPE_MISMATCH'):
            self.replace_payload(payload)

    def test_absent_nonbytes_and_modified_registered_envelope_are_rejected(self):
        for raw in (None, b'', bytearray(self.plan.registered_envelope),
                    self.plan.registered_envelope + b'\n', b'{}'):
            with self.subTest(value_type=type(raw).__name__):
                with self.assertRaisesRegex(RuntimeBlocked, 'REGISTERED_ENVELOPE_MISMATCH'):
                    replace(self.plan, registered_envelope=raw)

    def test_rehashed_envelope_cannot_rebind_session_binding_deployment_or_prompt(self):
        changes = {'session_id': str(uuid4()), 'tenant_id': str(uuid4()), 'run_id': str(uuid4()),
                   'step_id': str(uuid4()), 'action_id': str(uuid4()), 'lease_generation': 2,
                   'input_sha256': '7' * 64, 'deployment_revision': str(uuid4()),
                   'deployment_sha256': '8' * 64, 'runtime_manifest_sha256': '9' * 64,
                   'prompt_sha256': '0' * 64, 'prompt_provenance': {'caller_assertion': True}}
        for field, value in changes.items():
            with self.subTest(field=field):
                envelope = strict_json(self.plan.registered_envelope)
                envelope[field] = value
                with self.assertRaisesRegex(RuntimeBlocked, 'REGISTERED_ENVELOPE_MISMATCH'):
                    self.replace_envelope_and_byte_pin(canonical(envelope))

    def test_duplicate_registered_envelope_fields_rejected_even_with_matching_byte_hash(self):
        raw = self.plan.registered_envelope
        raw = b'{"session_id":"' + self.plan.session_id.encode() + b'",' + raw[1:]
        with self.assertRaisesRegex(RuntimeBlocked, 'DUPLICATE_JSON_KEY'):
            self.replace_envelope_and_byte_pin(raw)


if __name__ == '__main__':
    unittest.main()
