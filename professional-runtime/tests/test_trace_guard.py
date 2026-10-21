"""Mock kernel event tests. No privileged ptrace, deployment, or model calls."""
import asyncio
from collections import defaultdict, deque
import ctypes
from dataclasses import replace
import os
from pathlib import Path
import signal
from types import SimpleNamespace
import unittest

from medical_professional_runtime.host import RuntimeBlocked
from medical_professional_runtime.linux_inspection import (
    CgroupSnapshot, InspectionBlocked, InspectionSnapshot, PinnedFile, ProcessSnapshot,
)
from medical_professional_runtime.trace_guard import LinuxTraceGuard

ROOT = '/sys/fs/cgroup/test/session'
NS = tuple((name, index + 1) for index, name in enumerate(('cgroup', 'ipc', 'mnt', 'net', 'pid', 'user', 'uts')))
ZERO = '0000000000000000'


def snapshot(root_threads=(100,), with_child=False):
    process = ProcessSnapshot(
        pid=100, start_ticks=123, exe_sha256='a' * 64, exe_device=8, exe_inode=90,
        cmdline_sha256='b' * 64, uid=1000, gid=1000, namespaces=NS,
        no_new_privs=1, seccomp=2, cap_eff=ZERO, lsm_profile='medical (enforce)',
        cgroup_path=ROOT, task_ids=root_threads, children=(200,) if with_child else (),
        cap_inh=ZERO, cap_prm=ZERO, cap_bnd=ZERO, cap_amb=ZERO)
    processes = (process, replace(process, pid=200, task_ids=(200,), children=())) if with_child else (process,)
    members = tuple(item.pid for item in processes)
    threads = tuple(sorted(tid for item in processes for tid in item.task_ids))
    return InspectionSnapshot('12345678-1234-4234-9234-123456789abc', ROOT, 70,
                              members, processes,
                              (PinnedFile(Path('/runtime'), 'c' * 64),),
                              (PinnedFile(Path('/profile'), 'd' * 64),),
                              (CgroupSnapshot(ROOT, 70, members, threads),))


def event(tid, kind, status):
    return SimpleNamespace(si_pid=tid, si_code=kind, si_status=status)


def trapped(tid, code=0, signum=signal.SIGTRAP):
    return event(tid, os.CLD_TRAPPED, (code << 8) | signum)


class KernelDouble:
    def __init__(self):
        self.events = defaultdict(deque)
        self.calls = []
        self.waits = []
        self.messages = {}
        self.statuses = {}
        self.denied = None
        self.on_cont = None
        self.wait_error = None

    def ptrace(self, operation, tid, address, data):
        value = data.value if isinstance(data, ctypes.c_void_p) else data
        self.calls.append((operation, tid, value))
        if self.denied == (operation, tid):
            ctypes.set_errno(1)
            return -1
        if operation == LinuxTraceGuard.GETEVENTMSG:
            ctypes.cast(data, ctypes.POINTER(ctypes.c_ulong))[0] = self.messages[tid]
        if operation == LinuxTraceGuard.CONT and self.on_cont:
            self.on_cont(tid)
        return 0

    def waitid(self, kind, identity, flags):
        self.waits.append((kind, identity, flags))
        tid = 100 if kind == os.P_PIDFD and identity == 999 else identity
        if self.wait_error:
            raise self.wait_error
        if self.events[tid]:
            return self.events[tid].popleft()
        return None

    def status(self, tid):
        return self.statuses.get(tid, f'Tgid:\t100\nPid:\t{tid}\n'.encode())


class TraceGuardTest(unittest.IsolatedAsyncioTestCase):
    def setUp(self):
        self.kernel = KernelDouble()
        self.guard = LinuxTraceGuard._testing(self.kernel, self.kernel.waitid, self.kernel.status)

    async def asyncTearDown(self):
        # Fixture teardown only: no live processes/ptrace targets exist here.
        if self.guard.monitor is not None:
            self.guard.monitor.cancel()
            await asyncio.gather(self.guard.monitor, return_exceptions=True)

    def start(self, value=None, validator=None):
        self.guard.install(value or snapshot(), validator)
        self.guard.start(100, 999)

    def assert_blocked(self, text, operation=None):
        with self.assertRaisesRegex(RuntimeBlocked, text):
            (operation or self.guard.check)()

    async def test_seize_every_thread_with_exitkill_and_all_lifecycle_events(self):
        self.start(snapshot((100, 101), with_child=True))
        self.assertEqual(self.guard.tids, {100, 101, 200})
        self.assertEqual(self.kernel.calls, [(LinuxTraceGuard.SEIZE, tid, LinuxTraceGuard.OPTIONS)
                                            for tid in (100, 101, 200)])

    async def test_single_dispatcher_consumes_root_stop_and_terminal_using_pidfd(self):
        self.start()
        self.kernel.events[100].extend([trapped(100, 6), event(100, os.CLD_EXITED, 0)])
        self.guard.check()
        self.assertEqual(await self.guard.wait_root(), 0)
        self.assertTrue(all(kind == os.P_PIDFD and identity == 999 for kind, identity, _ in self.kernel.waits))
        self.assertTrue(all(flags & os.WSTOPPED and flags & os.WEXITED for _, _, flags in self.kernel.waits))
        self.assertIn((LinuxTraceGuard.CONT, 100, None), self.kernel.calls)
        await self.guard.close()

    async def test_nonroot_final_status_is_reaped_before_parent_exit(self):
        self.start(snapshot(with_child=True))
        self.kernel.events[200].extend([trapped(200, 6), event(200, os.CLD_EXITED, 0)])
        self.guard.check()
        self.assertEqual(self.guard.terminal, {200: 0})
        self.assertEqual(self.guard.tids, {100})
        self.assertFalse(self.guard.root_exit.done())
        self.kernel.events[100].append(event(100, os.CLD_EXITED, 0))
        self.guard.check()
        self.assertEqual(await self.guard.wait_root(), 0)
        await self.guard.close()

    async def test_signal_terminal_status_is_not_confused_with_exit_code(self):
        self.start()
        self.kernel.events[100].append(event(100, os.CLD_KILLED, signal.SIGKILL))
        self.guard.check()
        self.assertEqual(await self.guard.wait_root(), -signal.SIGKILL)

    async def test_reexec_fails_before_any_continue(self):
        self.start()
        self.kernel.events[100].append(trapped(100, 4))
        self.assert_blocked('SUPERVISED_EXEC_OR_CHILD_CHANGE')
        self.assertFalse(any(call[0] == LinuxTraceGuard.CONT for call in self.kernel.calls))
        self.assertEqual(await self.guard.failure, 'SUPERVISED_EXEC_OR_CHILD_CHANGE')
        with self.assertRaisesRegex(RuntimeBlocked, 'SUPERVISED_EXEC_OR_CHILD_CHANGE'):
            await self.guard.wait_root()

    async def test_fork_child_is_tracked_but_never_resumed(self):
        self.start()
        self.kernel.messages[100] = 300
        self.kernel.events[100].append(trapped(100, 1))
        self.kernel.events[300].append(trapped(300, 128))
        self.assert_blocked('SUPERVISED_EXEC_OR_CHILD_CHANGE')
        self.assertEqual(self.guard.tids, {100, 300})
        self.assertFalse(any(call[0] == LinuxTraceGuard.CONT for call in self.kernel.calls))

    async def test_clone_is_rejected_without_independent_validator(self):
        self.start()
        self.kernel.messages[100] = 101
        self.kernel.events[100].append(trapped(100, 3))
        self.assert_blocked('SUPERVISED_EXEC_OR_CHILD_CHANGE')

    async def test_thread_stays_stopped_until_initial_child_stop_and_validation(self):
        validations = []
        def validate(parent, child):
            validations.append((parent, child))
            self.assertFalse(any(call[0] == LinuxTraceGuard.CONT for call in self.kernel.calls))
            return snapshot((100, 101))
        self.start(validator=validate)
        self.kernel.messages[100] = 101
        self.kernel.events[100].append(trapped(100, 3))
        self.guard.check()
        self.assertEqual(validations, [])
        self.assertEqual(self.guard.pending_clones, {101: (100, 100)})
        self.kernel.events[101].append(trapped(101, 128))
        self.guard.check()
        self.assertEqual(validations, [(100, 101)])
        self.assertEqual(self.guard.tids, {100, 101})
        self.assertEqual(self.kernel.calls[-2:], [(LinuxTraceGuard.CONT, 101, None), (LinuxTraceGuard.CONT, 100, None)])

    async def test_clone_nonthread_process_is_rejected_despite_validator(self):
        self.start(validator=lambda *_: snapshot((100, 101)))
        self.kernel.statuses[101] = b'Tgid:\t101\nPid:\t101\n'
        self.kernel.messages[100] = 101
        self.kernel.events[100].append(trapped(100, 3))
        self.kernel.events[101].append(trapped(101, 128))
        self.assert_blocked('SUPERVISED_EXEC_OR_CHILD_CHANGE')
        self.assertFalse(any(call[0] == LinuxTraceGuard.CONT for call in self.kernel.calls))

    async def test_new_thread_credential_or_executable_change_is_rejected(self):
        altered = snapshot((100, 101))
        altered = replace(altered, processes=(replace(altered.processes[0], uid=0),))
        self.start(validator=lambda *_: altered)
        self.kernel.messages[100] = 101
        self.kernel.events[100].append(trapped(100, 3))
        self.kernel.events[101].append(trapped(101, 128))
        self.assert_blocked('TRACE_THREAD_SNAPSHOT_CHANGED')

    async def test_validator_cannot_return_worker_dictionary(self):
        self.start(validator=lambda *_: {'trusted': True})
        self.kernel.messages[100] = 101
        self.kernel.events[100].append(trapped(100, 3))
        self.kernel.events[101].append(trapped(101, 128))
        self.assert_blocked('TRACE_THREAD_SNAPSHOT_REQUIRED')

    async def test_untracked_extra_thread_is_rejected(self):
        self.start(validator=lambda *_: snapshot((100, 101, 102)))
        self.kernel.messages[100] = 101
        self.kernel.events[100].append(trapped(100, 3))
        self.kernel.events[101].append(trapped(101, 128))
        self.assert_blocked('TRACE_THREAD_SNAPSHOT_CHANGED')

    async def test_ordinary_signal_is_preserved(self):
        self.start()
        self.kernel.events[100].append(trapped(100, signum=signal.SIGTERM))
        self.guard.check()
        self.assertEqual(self.kernel.calls[-1], (LinuxTraceGuard.CONT, 100, signal.SIGTERM))

    async def test_group_stop_is_listened_to_not_resumed(self):
        self.start()
        self.kernel.events[100].append(trapped(100, 128, signal.SIGSTOP))
        self.guard.check()
        self.assertEqual(self.kernel.calls[-1], (LinuxTraceGuard.LISTEN, 100, None))

    async def test_unexpected_sigtrap_is_fail_closed(self):
        self.start()
        self.kernel.events[100].append(trapped(100))
        self.assert_blocked('UNEXPECTED_TRACE_TRAP')

    async def test_cleanup_drains_terminal_after_violation_but_never_clears_it(self):
        self.start()
        self.kernel.events[100].append(trapped(100, 4))
        self.assert_blocked('SUPERVISED_EXEC_OR_CHILD_CHANGE')
        self.kernel.events[100].extend([trapped(100, 6), event(100, os.CLD_KILLED, signal.SIGKILL)])
        self.assert_blocked('SUPERVISED_EXEC_OR_CHILD_CHANGE')
        self.assertEqual(await self.guard.wait_root(allow_violation=True), -signal.SIGKILL)
        with self.assertRaisesRegex(RuntimeBlocked, 'SUPERVISED_EXEC_OR_CHILD_CHANGE'):
            await self.guard.wait_root()
        await self.guard.close()

    async def test_thread_exit_does_not_prevent_later_verified_thread(self):
        self.start(snapshot((100, 101)), lambda *_: snapshot((100, 102)))
        self.kernel.events[101].extend([trapped(101, 6), event(101, os.CLD_EXITED, 0)])
        self.guard.check()
        self.kernel.messages[100] = 102
        self.kernel.events[100].append(trapped(100, 3))
        self.kernel.events[102].append(trapped(102, 128))
        self.guard.check()
        self.assertEqual(self.guard.tids, {100, 102})

    async def test_root_future_cancellation_does_not_cancel_terminal_observation(self):
        self.start()
        pending = asyncio.create_task(self.guard.wait_root(allow_violation=True))
        await asyncio.sleep(0)
        pending.cancel()
        await asyncio.gather(pending, return_exceptions=True)
        self.assertFalse(self.guard.root_exit.cancelled())
        self.kernel.events[100].append(event(100, os.CLD_EXITED, 0))
        self.guard.check()
        self.assertEqual(await self.guard.wait_root(allow_violation=True), 0)

    async def test_partial_seize_failure_is_retained_for_cleanup(self):
        self.kernel.denied = (LinuxTraceGuard.SEIZE, 101)
        self.assert_blocked('KERNEL_TRACE_INSTRUMENTATION_REQUIRED',
                            lambda: self.guard.install(snapshot((100, 101))))
        self.assertEqual(self.guard.tids, {100})
        self.guard.start(100, 999)
        self.kernel.events[100].append(event(100, os.CLD_KILLED, signal.SIGKILL))
        self.assert_blocked('TRACE_INSTALLATION_FAILED')
        self.assertEqual(await self.guard.wait_root(allow_violation=True), -signal.SIGKILL)
        await self.guard.close()

    async def test_no_implicit_pidfd_or_unverified_install_input(self):
        self.assert_blocked('TRACE_SNAPSHOT_REQUIRED', lambda: self.guard.install({'processes': []}))
        self.guard.install(snapshot())
        self.assert_blocked('TRACE_OWNED_PIDFD_REQUIRED', lambda: self.guard.start(100))

    async def test_close_never_detaches_live_tasks(self):
        self.start()
        with self.assertRaisesRegex(RuntimeBlocked, 'TRACE_LIVE_TASKS_REMAIN'):
            await self.guard.close()
        self.assertFalse(self.guard.stopped)
        self.assertFalse(any(call[0] == 17 for call in self.kernel.calls))

    async def test_missing_wait_observation_is_failure_not_exit(self):
        self.start()
        self.kernel.wait_error = ChildProcessError()
        self.assert_blocked('TRACE_IDENTITY_LOST')
        self.assertFalse(self.guard.root_exit.done())

    async def test_inspection_failure_keeps_dispatcher_available_for_cleanup(self):
        def denied(*_):
            raise InspectionBlocked('INSPECTION_PROCESS_FILE_MISMATCH')
        self.start(validator=denied)
        self.kernel.messages[100] = 101
        self.kernel.events[100].append(trapped(100, 3))
        self.kernel.events[101].append(trapped(101, 128))
        self.assert_blocked('TRACE_OBSERVATION_UNAVAILABLE')
        self.kernel.events[101].extend([trapped(101, 6), event(101, os.CLD_KILLED, signal.SIGKILL)])
        self.kernel.events[100].extend([trapped(100, 6), event(100, os.CLD_KILLED, signal.SIGKILL)])
        self.assert_blocked('TRACE_OBSERVATION_UNAVAILABLE')
        self.assertEqual(await self.guard.wait_root(allow_violation=True), -signal.SIGKILL)
        await self.guard.close()

    async def test_multiple_pending_threads_are_all_inspected_before_release(self):
        self.start(snapshot((100, 102)), validator=lambda *_: snapshot((100, 101, 102, 103)))
        self.kernel.messages.update({100: 101, 102: 103})
        self.kernel.events[100].append(trapped(100, 3))
        self.kernel.events[102].append(trapped(102, 3))
        self.kernel.events[101].append(trapped(101, 128))
        self.kernel.events[103].append(trapped(103, 128))
        self.guard.check()
        self.assertEqual(self.guard.tids, {100, 101, 102, 103})
        self.assertFalse(self.guard.pending_clones)

    async def test_child_event_message_cannot_reuse_existing_tid(self):
        self.start()
        self.kernel.messages[100] = 100
        self.kernel.events[100].append(trapped(100, 1))
        self.assert_blocked('TRACE_CHILD_IDENTITY_INVALID')

    async def test_mismatched_wait_identity_is_not_accepted(self):
        self.start()
        self.kernel.events[100].append(event(999, os.CLD_EXITED, 0))
        self.assert_blocked('TRACE_IDENTITY_LOST')
        self.assertFalse(self.guard.root_exit.done())

    async def test_unexpected_child_stop_is_not_released(self):
        self.start(validator=lambda *_: snapshot((100, 101)))
        self.kernel.messages[100] = 101
        self.kernel.events[100].append(trapped(100, 3))
        self.kernel.events[101].append(trapped(101, 0, signal.SIGSTOP))
        self.assert_blocked('TRACE_CHILD_STOP_REQUIRED')

    async def test_waiter_cancellation_does_not_cancel_failure_monitor(self):
        self.start()
        waiter = asyncio.create_task(self.guard.wait_root())
        await asyncio.sleep(0)
        waiter.cancel()
        await asyncio.gather(waiter, return_exceptions=True)
        self.assertFalse(self.guard.root_exit.cancelled())
        self.assertFalse(self.guard.failure.cancelled())
        self.kernel.events[100].append(trapped(100, 4))
        self.assert_blocked('SUPERVISED_EXEC_OR_CHILD_CHANGE')

    async def test_monitor_publishes_failure_without_external_poll(self):
        self.start()
        self.kernel.events[100].append(trapped(100, 4))
        reason = await asyncio.wait_for(asyncio.shield(self.guard.failure), .2)
        self.assertEqual(reason, 'SUPERVISED_EXEC_OR_CHILD_CHANGE')


if __name__ == '__main__':
    unittest.main()
