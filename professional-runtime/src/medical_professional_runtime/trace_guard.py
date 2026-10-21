"""One kernel wait dispatcher for ptrace stops AND terminal observations.

PTRACE_SEIZE/EXITKILL is mandatory; no policy is relaxed to obtain it. The owner
retains a pidfd for its direct child and passes it to start(). Only this dispatcher
may consume that child's wait events. Linux reports ptrace stops even to callers
requesting WEXITED alone, so a second exit watcher is not safe.
"""
from __future__ import annotations
import asyncio
import ctypes
import os
from pathlib import Path
import signal
import threading

from .host import RuntimeBlocked
from .linux_inspection import InspectionSnapshot


class LinuxTraceGuard:
    SEIZE = 0x4206
    CONT = 7
    LISTEN = 0x4208
    GETEVENTMSG = 0x4201
    TRACEFORK = 0x2
    TRACEVFORK = 0x4
    TRACECLONE = 0x8
    TRACEEXEC = 0x10
    TRACEEXIT = 0x40
    EXITKILL = 0x100000
    OPTIONS = TRACEFORK | TRACEVFORK | TRACECLONE | TRACEEXEC | TRACEEXIT | EXITKILL
    WALL = 0x40000000
    _STOP_SIGNALS = {signal.SIGSTOP, signal.SIGTSTP, signal.SIGTTIN, signal.SIGTTOU}

    def __init__(self):
        self.libc = ctypes.CDLL(None, use_errno=True)
        self.libc.ptrace.restype = ctypes.c_long
        self.tids = set()
        self.owners = {}
        self.exiting = set()
        self.terminal = {}
        self.pending_clones = {}
        self.violation = None
        self.monitor = None
        self.failure = None
        self.root_exit = None
        self.root_terminal = None
        self.root_pid = None
        self.root_pidfd = None
        self.stopped = False
        self.installed = False
        self.baseline = None
        self.thread_validator = None
        self.owner_thread = None
        self._waitid = os.waitid
        self._read_status = lambda tid: (Path('/proc') / str(tid) / 'status').read_bytes()

    @classmethod
    def _testing(cls, kernel, waitid=None, read_status=None):
        """Only explicit test fixtures replace kernel observations."""
        guard = cls()
        guard.libc = kernel
        if waitid is not None:
            guard._waitid = waitid
        if read_status is not None:
            guard._read_status = read_status
        return guard

    def _fail(self, reason):
        if self.violation is None:
            self.violation = reason
        if self.failure is not None and not self.failure.done():
            self.failure.set_result(self.violation)

    def _same_thread(self):
        if self.owner_thread != threading.get_native_id():
            raise RuntimeBlocked('TRACE_OWNER_THREAD_CHANGED')

    def _ptrace(self, operation, tid, data=0):
        ctypes.set_errno(0)
        argument = ctypes.c_void_p(data) if isinstance(data, int) else data
        if self.libc.ptrace(operation, tid, None, argument) == -1:
            raise RuntimeBlocked('KERNEL_TRACE_INSTRUMENTATION_REQUIRED')

    def install(self, snapshot, thread_validator=None):
        if self.installed or self.tids:
            raise RuntimeBlocked('TRACE_ALREADY_INSTALLED')
        if type(snapshot) is not InspectionSnapshot:
            raise RuntimeBlocked('TRACE_SNAPSHOT_REQUIRED')
        if thread_validator is not None and not callable(thread_validator):
            raise RuntimeBlocked('TRACE_THREAD_VALIDATOR_INVALID')
        self.owner_thread = threading.get_native_id()
        owners = {tid: process.pid for process in snapshot.processes for tid in process.task_ids}
        if not owners or len(owners) != sum(len(process.task_ids) for process in snapshot.processes):
            raise RuntimeBlocked('TRACE_THREADS_MISSING')
        self.baseline = snapshot
        self.thread_validator = thread_validator
        try:
            for tid in sorted(owners):
                self._ptrace(self.SEIZE, tid, self.OPTIONS)
                self.tids.add(tid)
                self.owners[tid] = owners[tid]
            self.installed = True
        except BaseException:
            self._fail('TRACE_INSTALLATION_FAILED')
            # Keep every successful attachment tracked for exact-unit cleanup.
            raise

    def start(self, root_pid, root_pidfd=None):
        self._same_thread()
        if self.monitor is not None or (not self.installed and not self.tids):
            raise RuntimeBlocked('TRACE_NOT_INSTALLED')
        if root_pid not in self.tids:
            if self.violation is None:
                raise RuntimeBlocked('TRACE_ROOT_MUST_BE_INSPECTED')
            # Partial installation still needs one owner for the direct child's
            # terminal wait during cleanup, even if its attach failed.
            self.tids.add(root_pid)
            self.owners[root_pid] = root_pid
        self.installed = True
        if type(root_pidfd) is not int or root_pidfd < 0:
            raise RuntimeBlocked('TRACE_OWNED_PIDFD_REQUIRED')
        if self.owners[root_pid] != root_pid:
            raise RuntimeBlocked('TRACE_ROOT_MUST_BE_PROCESS_LEADER')
        self.root_pid, self.root_pidfd = root_pid, root_pidfd
        loop = asyncio.get_running_loop()
        self.root_exit, self.failure = loop.create_future(), loop.create_future()
        if self.violation is not None:
            self.failure.set_result(self.violation)
        self.monitor = asyncio.create_task(self._monitor())

    def _event_message(self, tid):
        value = ctypes.c_ulong()
        self._ptrace(self.GETEVENTMSG, tid, ctypes.byref(value))
        if value.value <= 0 or value.value in self.tids:
            raise RuntimeBlocked('TRACE_CHILD_IDENTITY_INVALID')
        return value.value

    def _thread_group(self, tid):
        raw = self._read_status(tid)
        if len(raw) > 1024 * 1024:
            raise RuntimeBlocked('TRACE_THREAD_STATUS_INVALID')
        values = {}
        for line in raw.decode('ascii').splitlines():
            key, separator, value = line.partition(':')
            if not separator or key in values:
                raise RuntimeBlocked('TRACE_THREAD_STATUS_INVALID')
            values[key] = value.strip()
        if values.get('Pid') != str(tid) or not values.get('Tgid', '').isdigit():
            raise RuntimeBlocked('TRACE_THREAD_STATUS_INVALID')
        return int(values['Tgid'])

    @staticmethod
    def _without_thread_sets(snapshot):
        wire = snapshot.wire()
        for process in wire['processes']:
            process.pop('task_ids')
        for group in wire['cgroups']:
            group.pop('threads')
        return wire

    def _accept_thread(self, child):
        parent, owner = self.pending_clones[child]
        if self.thread_validator is None or self._thread_group(child) != owner:
            raise RuntimeBlocked('SUPERVISED_EXEC_OR_CHILD_CHANGE')
        # The callback must independently freeze/read the exact unit and validate
        # every new thread's credentials/namespaces/profile, not trust this event.
        updated = self.thread_validator(parent, child)
        if type(updated) is not InspectionSnapshot:
            raise RuntimeBlocked('TRACE_THREAD_SNAPSHOT_REQUIRED')
        expected = set(self.tids)
        observed = {tid for process in updated.processes for tid in process.task_ids}
        if (observed != expected or self._without_thread_sets(updated) != self._without_thread_sets(self.baseline)
                or sum(child in process.task_ids and process.pid == owner for process in updated.processes) != 1):
            raise RuntimeBlocked('TRACE_THREAD_SNAPSHOT_CHANGED')
        self.baseline = updated
        self.pending_clones.pop(child)
        self._ptrace(self.CONT, child)
        self._ptrace(self.CONT, parent)

    def _handle(self, tid, event):
        if event.si_pid != tid:
            raise RuntimeBlocked('TRACE_IDENTITY_LOST')
        if event.si_code in (os.CLD_EXITED, os.CLD_KILLED, os.CLD_DUMPED):
            status = event.si_status if event.si_code == os.CLD_EXITED else -event.si_status
            self.terminal[tid] = status
            self.tids.remove(tid)
            self.exiting.discard(tid)
            self.pending_clones.pop(tid, None)
            if tid == self.root_pid and not self.root_exit.done():
                self.root_terminal={'code':{os.CLD_EXITED:'EXITED',os.CLD_KILLED:'KILLED',os.CLD_DUMPED:'DUMPED'}[event.si_code],'status':event.si_status}
                self.root_exit.set_result(status)
            # Consuming non-root terminal waits is essential: it forwards exit
            # notification to that tracee's real parent (the runner/SDK).
            return
        if event.si_code != os.CLD_TRAPPED:
            raise RuntimeBlocked('UNEXPECTED_TRACE_EVENT')
        trace_event = event.si_status >> 8
        stop_signal = event.si_status & 0xff
        if trace_event == 6:
            self.exiting.add(tid)
            # EXIT is irreversible. Continuing it is safe even after a violation
            # and allows supervisor cgroup.kill cleanup to finish.
            self._ptrace(self.CONT, tid)
            return
        if self.violation is not None:
            return  # Never release a changed executable/child after a failure.
        if trace_event in (1, 2, 4):
            if trace_event in (1, 2):
                child = self._event_message(tid)
                self.tids.add(child)
                self.owners[child] = child
            raise RuntimeBlocked('SUPERVISED_EXEC_OR_CHILD_CHANGE')
        if trace_event == 3:
            child = self._event_message(tid)
            self.tids.add(child)
            self.owners[child] = self.owners[tid]
            self.pending_clones[child] = (tid, self.owners[tid])
            if self.thread_validator is None:
                raise RuntimeBlocked('SUPERVISED_EXEC_OR_CHILD_CHANGE')
            return  # Both tasks remain kernel-stopped until the child stop arrives.
        if tid in self.pending_clones:
            if trace_event != 128 or stop_signal != signal.SIGTRAP:
                raise RuntimeBlocked('TRACE_CHILD_STOP_REQUIRED')
            self._accept_thread(tid)
            return
        if trace_event == 128 and stop_signal in self._STOP_SIGNALS:
            self._ptrace(self.LISTEN, tid)
            return
        if trace_event != 0 or stop_signal == signal.SIGTRAP:
            raise RuntimeBlocked('UNEXPECTED_TRACE_TRAP')
        self._ptrace(self.CONT, tid, stop_signal)

    def _dispatch(self):
        self._same_thread()
        if not self.installed or self.root_pid is None:
            raise RuntimeBlocked('TRACE_NOT_STARTED')
        # Drain new auto-attached children as well as already-known tids. Bound a
        # pathological event stream without dropping or silently ignoring it.
        processed = 0
        while processed < 16384:
            progressed = False
            for tid in sorted(tuple(self.tids)):
                kind, identity = (os.P_PIDFD, self.root_pidfd) if tid == self.root_pid else (os.P_PID, tid)
                try:
                    event = self._waitid(kind, identity, os.WSTOPPED | os.WEXITED | os.WNOHANG | self.WALL)
                    if event is None:
                        continue
                    progressed = True
                    processed += 1
                    if processed >= 16384:
                        self._fail('TRACE_EVENT_LIMIT')
                    self._handle(tid, event)
                    if processed >= 16384:
                        return
                except ChildProcessError:
                    self._fail('TRACE_IDENTITY_LOST')
                except Exception as error:
                    self._fail(str(error) if isinstance(error, RuntimeBlocked) else 'TRACE_OBSERVATION_UNAVAILABLE')
            if not progressed:
                return
        self._fail('TRACE_EVENT_LIMIT')

    def check(self):
        self._dispatch()
        if self.violation:
            raise RuntimeBlocked(self.violation)

    async def wait_root(self, allow_violation=False):
        if self.root_exit is None:
            raise RuntimeBlocked('TRACE_NOT_STARTED')
        if allow_violation:
            # Cleanup obtains a factual wait result but can never turn a failed
            # guard back into valid admission/completion evidence.
            return await asyncio.shield(self.root_exit)
        if self.violation:
            raise RuntimeBlocked(self.violation)
        await asyncio.wait((self.root_exit, self.failure), return_when=asyncio.FIRST_COMPLETED)
        if self.violation:
            raise RuntimeBlocked(self.violation)
        return self.root_exit.result()

    async def _monitor(self):
        try:
            while not self.stopped:
                self._dispatch()
                await asyncio.sleep(.005)
        except asyncio.CancelledError:
            raise
        except BaseException:
            self._fail('TRACE_DISPATCHER_FAILED')

    async def close(self):
        self._same_thread()
        if self.installed and self.root_pid is not None:
            self._dispatch()
        if self.tids:
            raise RuntimeBlocked('TRACE_LIVE_TASKS_REMAIN')
        self.stopped = True
        if self.monitor is not None:
            self.monitor.cancel()
            await asyncio.gather(self.monitor, return_exceptions=True)
        # Never PTRACE_DETACH a live task. The supervisor owns the pidfd lifetime.
