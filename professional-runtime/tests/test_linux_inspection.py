"""Unprivileged fixtures only: these tests do not provision or certify isolation."""
from contextlib import contextmanager
from dataclasses import FrozenInstanceError, replace
import hashlib
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from medical_professional_runtime.linux_inspection import (
    ExecutableProfile, InspectionBlocked, LinuxInspectionConfig, LinuxInspector,
    PinnedFile, _file_digest, _open_absolute_directory, _open_beneath, _start_ticks,
)

ROOT = Path('/sys/fs/cgroup/medical/session')
BOOT = '12345678-1234-4234-9234-123456789abc'
NAMESPACES = tuple((name, index + 101) for index, name in enumerate(
    ('cgroup', 'ipc', 'mnt', 'net', 'pid', 'user', 'uts')))
ZERO = '0000000000000000'


def digest(value):
    return hashlib.sha256(value).hexdigest()


def proc_stat(pid, start=200):
    return f'{pid} (runtime with ) spaces) S '.encode() + b' '.join(
        [b'0'] * 18 + [str(start).encode()] + [b'0'] * 30)


def proc_status(pid, task=None):
    return (f'Name:\truntime\nTgid:\t{pid}\nPid:\t{task or pid}\n'
            f'Uid:\t1000 1000 1000 1000\nGid:\t1000 1000 1000 1000\n'
            f'CapInh:\t{ZERO}\nCapPrm:\t{ZERO}\nCapEff:\t{ZERO}\n'
            f'CapBnd:\t{ZERO}\nCapAmb:\t{ZERO}\nNoNewPrivs:\t1\nSeccomp:\t2\n').encode()


class FixtureProcess:
    def __init__(self, pid=100, path=ROOT, command=b'runtime\x00'):
        self.pid = pid
        self.command = command
        self.path = path
        self.executable_value = (digest(b'runtime'), 8, 123)
        self.values = {'stat': proc_stat(pid), 'status': proc_status(pid),
                       'cgroup': b'0::' + ('/' + str(path.relative_to('/sys/fs/cgroup'))).encode() + b'\n',
                       'cmdline': command, 'attr/current': b'medical-runtime (enforce)\n'}
        self.task_values = {pid: {'status': proc_status(pid), 'cgroup': self.values['cgroup'],
                                 'children': b'', 'attr/current': self.values['attr/current']}}
        self.namespace_values = dict(NAMESPACES)
        self.thread_namespaces = {}
        self.file_values = {Path('/opt/runtime/package.py'): digest(b'runtime files'),
                            Path('/home/worker/profiles/profile.json'): digest(b'profile files')}
        self.on_read = None
        self.read_counts = {}
        self.on_executable = None
        self.executable_count = 0
        self.on_file = None
        self.file_count = 0
        self.is_alive = True

    def read(self, relative):
        self.read_counts[relative] = self.read_counts.get(relative, 0) + 1
        if self.on_read:
            self.on_read(self, relative, self.read_counts[relative])
        if relative.startswith('task/'):
            _, task, name = relative.split('/', 2)
            return self.task_values[int(task)][name]
        return self.values[relative]

    def alive(self):
        if not self.is_alive:
            raise InspectionBlocked('INSPECTION_PROCESS_DISAPPEARED')

    def namespace(self, name, task=None):
        return self.thread_namespaces.get(task, self.namespace_values)[name]

    def tasks(self):
        return tuple(sorted(self.task_values))

    def executable(self, maximum):
        self.executable_count += 1
        if self.on_executable:
            self.on_executable(self, self.executable_count)
        return self.executable_value

    def file(self, path, maximum):
        self.file_count += 1
        if self.on_file:
            self.on_file(self, path, self.file_count)
        return self.file_values[path]


class FixtureGroup:
    def __init__(self):
        self.initial = 0
        self.request = 0
        self.writes = []
        self.freeze_effective = True
        self.nodes = {'': {'inode': 70, 'members': (100,), 'threads': (100,), 'type': b'domain'}}
        self.events_override = {}
        self.extra_reads = {}
        self.on_read = None
        self.read_counts = {}
        self.fail_freeze_once = False
        self.fail_restore = False
        self.kill_count = 0
        self.kill_error = None

    def kill(self):
        self.kill_count += 1
        if self.kill_error:
            raise self.kill_error

    def inode(self, relative=''):
        return self.nodes[relative]['inode']

    def directories(self, relative):
        return sorted(Path(path).name for path in self.nodes
                      if path and str(Path(path).parent).replace('.', '') == relative)

    def freeze(self, value):
        self.writes.append(value)
        self.request = value
        if value and self.fail_freeze_once:
            self.fail_freeze_once = False
            raise OSError('uncertain write')
        if not value and self.fail_restore:
            raise PermissionError('cannot restore')

    def read(self, relative):
        self.read_counts[relative] = self.read_counts.get(relative, 0) + 1
        if self.on_read:
            self.on_read(self, relative, self.read_counts[relative])
        if relative in self.extra_reads:
            value = self.extra_reads[relative]
            if isinstance(value, Exception):
                raise value
            return value
        prefix, _, name = relative.rpartition('/')
        node = self.nodes[prefix]
        if name == 'cgroup.freeze':
            return str(self.request).encode()
        if name == 'cgroup.type':
            return node['type'] + b'\n'
        if name == 'cgroup.events':
            populated = int(any(item['members'] for path, item in self.nodes.items()
                                if path == prefix or not prefix or path.startswith(prefix + '/')))
            frozen = int(bool(self.request) and self.freeze_effective)
            return self.events_override.get(prefix, f'populated {populated}\nfrozen {frozen}\n'.encode())
        if name in ('cgroup.procs', 'cgroup.threads'):
            values = node['members' if name == 'cgroup.procs' else 'threads']
            return ''.join(f'{value}\n' for value in values).encode()
        raise FileNotFoundError(relative)


class FixtureFS:
    def __init__(self):
        self.boot = BOOT
        self.group = FixtureGroup()
        self.processes = {100: FixtureProcess()}
        self.opened = []
        self.closed = []

    def boot_id(self):
        return self.boot

    @contextmanager
    def cgroup(self, config):
        yield self.group

    @contextmanager
    def process(self, pid):
        self.opened.append(pid)
        try:
            yield self.processes[pid]
        finally:
            self.closed.append(pid)


class LinuxInspectionTest(unittest.TestCase):
    def setUp(self):
        self.profile = ExecutableProfile(digest(b'runtime'), 8, 123, digest(b'runtime\x00'),
                                         1000, 1000, NAMESPACES, 'medical-runtime (enforce)')
        self.config = LinuxInspectionConfig(
            ROOT, 70, (self.profile,),
            (PinnedFile(Path('/opt/runtime/package.py'), digest(b'runtime files')),),
            (PinnedFile(Path('/home/worker/profiles/profile.json'), digest(b'profile files')),), BOOT)
        self.fs = FixtureFS()
        self.inspector = LinuxInspector._for_test(self.config, self.fs)

    def blocked(self, expected='INSPECTION', operation=None):
        with self.assertRaisesRegex(InspectionBlocked, expected):
            (operation or self.inspector.snapshot)()

    def test_real_constructor_accepts_only_operator_config(self):
        with self.assertRaises(TypeError):
            LinuxInspector(self.config, self.fs)
        with self.assertRaisesRegex(InspectionBlocked, 'CONFIG_REQUIRED'):
            LinuxInspector({'worker_assertion': True})

    def test_deterministic_complete_frozen_snapshot(self):
        snapshot = self.inspector.snapshot()
        self.assertEqual(snapshot.cgroup_members, (100,))
        self.assertEqual(snapshot.processes[0].start_ticks, 200)
        self.assertEqual(snapshot.processes[0].exe_inode, 123)
        self.assertEqual(snapshot.processes[0].namespaces, NAMESPACES)
        self.assertEqual(snapshot.wire()['runtime_files'], [self.config.runtime_files[0].wire()])
        self.assertEqual(snapshot.wire()['processes'][0]['cap_amb'], ZERO)
        self.assertEqual(self.fs.group.writes, [1, 0])
        self.assertEqual(self.fs.opened, self.fs.closed)
        self.assertEqual(snapshot, self.inspector.assert_unchanged(snapshot))
        expected = digest(json.dumps(snapshot.wire(), sort_keys=True, separators=(',', ':'),
                                     ensure_ascii=False).encode())
        self.assertEqual(snapshot.sha256, expected)

    def test_snapshot_is_deeply_immutable_and_wire_is_detached(self):
        snapshot = self.inspector.snapshot()
        with self.assertRaises(FrozenInstanceError):
            snapshot.processes[0].uid = 0
        with self.assertRaises(AttributeError):
            snapshot.processes[0].__dict__['uid'] = 0
        wire = snapshot.wire()
        wire['processes'][0]['namespaces']['net'] = 9
        wire['cgroup_members'].append(777)
        self.assertEqual(snapshot.cgroup_members, (100,))
        self.assertEqual(dict(snapshot.processes[0].namespaces)['net'], 104)

    def test_initially_frozen_state_is_preserved(self):
        self.fs.group.request = 1
        self.inspector.snapshot()
        self.assertEqual(self.fs.group.request, 1)
        self.assertEqual(self.fs.group.writes, [])

    def test_descendant_members_and_child_edges_are_included(self):
        child = FixtureProcess(200, ROOT / 'child')
        self.fs.processes[200] = child
        self.fs.group.nodes['child'] = {'inode': 71, 'members': (200,), 'threads': (200,), 'type': b'domain'}
        self.fs.processes[100].task_values[100]['children'] = b'200\n'
        snapshot = self.inspector.snapshot()
        self.assertEqual(snapshot.cgroup_members, (100, 200))
        self.assertEqual(snapshot.processes[0].children, (200,))
        self.assertEqual(snapshot.cgroups[1].path, str(ROOT / 'child'))

    def test_unknown_executable_in_nested_cgroup_is_rejected(self):
        self.fs.group.nodes['child'] = {'inode': 71, 'members': (200,), 'threads': (200,), 'type': b'domain'}
        child = FixtureProcess(200, ROOT / 'child')
        child.executable_value = (digest(b'unapproved'), 8, 456)
        self.fs.processes[200] = child
        self.blocked('EXECUTABLE_PROFILE_MISMATCH')
        self.assertEqual(self.fs.group.writes, [1, 0])

    def test_required_runtime_cannot_be_replaced_by_launcher_only(self):
        launcher = replace(self.profile, exe_sha256=digest(b'launcher'), exe_inode=55, role='launcher', required=False)
        config = replace(self.config, executable_profiles=(launcher, self.profile))
        self.fs.processes[100].executable_value = (launcher.exe_sha256, 8, 55)
        inspector = LinuxInspector._for_test(config, self.fs)
        self.blocked('REQUIRED_RUNTIME_MISSING', inspector.snapshot)

    def test_launcher_and_runtime_must_be_distinct_executables(self):
        launcher = replace(self.profile, role='launcher', required=False)
        self.blocked('DISTINCT_RUNTIME_REQUIRED', lambda: LinuxInspector(
            replace(self.config, executable_profiles=(launcher, self.profile))))

    def test_runtime_profile_must_be_required(self):
        self.blocked('DISTINCT_RUNTIME_REQUIRED', lambda: LinuxInspector(
            replace(self.config, executable_profiles=(replace(self.profile, required=False),))))

    def test_empty_group_is_not_an_admission_snapshot(self):
        self.fs.group.nodes['']['members'] = ()
        self.fs.group.nodes['']['threads'] = ()
        self.blocked('EXACT_MEMBERS_REQUIRED')

    def test_empty_assertion_never_claims_or_queries_process_exit(self):
        self.fs.group.nodes['']['members'] = ()
        self.fs.group.nodes['']['threads'] = ()
        self.assertIsNone(self.inspector.assert_empty())
        self.assertEqual(self.fs.opened, [])
        self.assertEqual(self.fs.group.writes, [1, 0])

    def test_empty_assertion_rejects_descendant_process(self):
        self.fs.group.nodes['']['members'] = ()
        self.fs.group.nodes['']['threads'] = ()
        self.fs.group.nodes['child'] = {'inode': 71, 'members': (200,), 'threads': (200,), 'type': b'domain'}
        self.blocked('CGROUP_NOT_EMPTY', self.inspector.assert_empty)

    def test_empty_assertion_requires_populated_zero(self):
        self.fs.group.nodes['']['members'] = ()
        self.fs.group.nodes['']['threads'] = ()
        self.fs.group.events_override[''] = b'populated 1\nfrozen 1\n'
        self.blocked('CGROUP_NOT_EMPTY', self.inspector.assert_empty)

    def test_empty_assertion_detects_directory_change(self):
        self.fs.group.nodes['']['members'] = ()
        self.fs.group.nodes['']['threads'] = ()
        def change(group, path, count):
            if path == 'cgroup.type' and count == 2:
                group.nodes['child'] = {'inode': 80, 'members': (), 'threads': (), 'type': b'domain'}
        self.fs.group.on_read = change
        self.blocked('SNAPSHOT_CHANGED', self.inspector.assert_empty)

    def test_missing_proc_observation_fails_closed_and_restores_freezer(self):
        del self.fs.processes[100].values['status']
        self.blocked('OBSERVATION_UNAVAILABLE')
        self.assertEqual(self.fs.group.writes, [1, 0])
        self.assertEqual(self.fs.opened, self.fs.closed)

    def test_missing_security_field_never_defaults_to_secure(self):
        for field in (b'NoNewPrivs', b'Seccomp', b'CapAmb', b'CapBnd', b'Uid', b'Gid'):
            with self.subTest(field=field):
                process = FixtureProcess()
                process.values['status'] = b'\n'.join(line for line in process.values['status'].splitlines()
                                                     if not line.startswith(field + b':'))
                self.fs.processes[100] = process
                self.blocked('OBSERVATION_UNAVAILABLE')

    def test_effective_saved_and_filesystem_ids_must_all_match(self):
        self.fs.processes[100].values['status'] = proc_status(100).replace(
            b'1000 1000 1000 1000', b'1000 1000 0 1000', 1)
        self.blocked('CREDENTIALS_INVALID')

    def test_capability_change_is_rejected(self):
        for field in (b'CapInh', b'CapPrm', b'CapEff', b'CapBnd', b'CapAmb'):
            with self.subTest(field=field):
                self.fs.processes[100] = FixtureProcess()
                self.fs.processes[100].values['status'] = proc_status(100).replace(
                    field + b':\t' + ZERO.encode(), field + b':\t0000000000000001')
                self.blocked('CAPABILITIES_CHANGED')

    def test_missing_lsm_and_namespace_are_rejected(self):
        self.fs.processes[100].values['attr/current'] = b'unconfined\n'
        self.blocked('LSM_REQUIRED')
        self.fs.processes[100] = FixtureProcess()
        del self.fs.processes[100].namespace_values['net']
        self.blocked('OBSERVATION_UNAVAILABLE')

    def test_changed_lsm_policy_is_rejected(self):
        process = self.fs.processes[100]
        process.values['attr/current'] = b'other-policy (enforce)\n'
        process.task_values[100]['attr/current'] = process.values['attr/current']
        self.blocked('EXECUTABLE_PROFILE_MISMATCH')

    def test_namespace_change_is_rejected(self):
        self.fs.processes[100].namespace_values['net'] = 999
        self.blocked('EXECUTABLE_PROFILE_MISMATCH')

    def test_every_thread_is_checked_for_weaker_security(self):
        process = self.fs.processes[100]
        process.task_values[101] = dict(process.task_values[100], status=proc_status(100, 101).replace(
            b'NoNewPrivs:\t1', b'NoNewPrivs:\t0'))
        self.fs.group.nodes['']['threads'] = (100, 101)
        self.blocked('HARDENING_REQUIRED')

    def test_every_thread_namespace_is_checked(self):
        process = self.fs.processes[100]
        process.task_values[101] = dict(process.task_values[100], status=proc_status(100, 101))
        process.thread_namespaces[101] = dict(NAMESPACES, net=999)
        self.fs.group.nodes['']['threads'] = (100, 101)
        self.blocked('THREAD_SECURITY_CHANGED')

    def test_unlisted_thread_in_cgroup_is_rejected(self):
        self.fs.group.nodes['']['threads'] = (100, 101)
        self.blocked('EXACT_THREADS_REQUIRED')

    def test_escaped_child_is_rejected(self):
        self.fs.processes[100].task_values[100]['children'] = b'200\n'
        self.blocked('CHILD_ESCAPED')

    def test_process_cgroup_mismatch_is_rejected(self):
        self.fs.processes[100].values['cgroup'] = b'0::/outside\n'
        self.blocked('PROCESS_CGROUP_CHANGED')

    def test_threaded_cgroup_is_not_silently_accepted(self):
        self.fs.group.nodes['']['type'] = b'domain threaded'
        self.blocked('DOMAIN_CGROUP_REQUIRED')

    def test_pid_reuse_during_snapshot_is_rejected(self):
        def change(process, path, count):
            if path == 'stat' and count == 2:
                process.values['stat'] = proc_stat(100, 500)
        self.fs.processes[100].on_read = change
        self.blocked('PID_REUSED')

    def test_pidfd_disappearance_is_not_exit_evidence(self):
        self.fs.processes[100].is_alive = False
        self.blocked('PROCESS_DISAPPEARED')

    def test_pid_reuse_between_snapshots_is_rejected(self):
        before = self.inspector.snapshot()
        self.fs.processes[100].values['stat'] = proc_stat(100, 300)
        self.blocked('SNAPSHOT_CHANGED', lambda: self.inspector.assert_unchanged(before))

    def test_exec_change_between_passes_is_rejected_even_when_both_profiles_allowed(self):
        alternate = replace(self.profile, exe_sha256=digest(b'alternate'), exe_inode=456, required=False)
        inspector = LinuxInspector._for_test(replace(self.config, executable_profiles=(self.profile, alternate)), self.fs)
        def change(process, count):
            if count == 2:
                process.executable_value = (alternate.exe_sha256, 8, 456)
        self.fs.processes[100].on_executable = change
        self.blocked('SNAPSHOT_CHANGED', inspector.snapshot)

    def test_exec_same_hash_different_inode_is_rejected(self):
        self.fs.processes[100].executable_value = (self.profile.exe_sha256, 8, 999)
        self.blocked('EXECUTABLE_PROFILE_MISMATCH')

    def test_command_line_changes_are_rejected(self):
        self.fs.processes[100].values['cmdline'] = b'runtime\x00--unsafe\x00'
        self.blocked('EXECUTABLE_PROFILE_MISMATCH')

    def test_every_process_root_file_is_checked_not_supervisor_file(self):
        for item in self.config.runtime_files + self.config.profile_files:
            with self.subTest(path=item.path):
                self.fs.processes[100] = FixtureProcess()
                self.fs.processes[100].file_values[item.path] = digest(b'changed inside sandbox only')
                self.blocked('PROCESS_FILE_MISMATCH')

    def test_process_root_files_are_rechecked_in_second_pass(self):
        def change(process, path, count):
            if count == 3:
                process.file_values[path] = digest(b'changed')
        self.fs.processes[100].on_file = change
        self.blocked('PROCESS_FILE_MISMATCH')

    def test_duplicate_cgroup_pid_is_not_deduplicated(self):
        self.fs.group.nodes['']['members'] = (100, 100)
        self.blocked('MEMBERS_CHANGED')

    def test_membership_change_during_freeze_is_rejected(self):
        def change(group, path, count):
            if path == 'cgroup.procs' and count == 2:
                group.nodes['']['members'] = (100, 200)
        self.fs.group.on_read = change
        self.blocked('SNAPSHOT_CHANGED')

    def test_freeze_timeout_attempts_restore(self):
        self.fs.group.freeze_effective = False
        inspector = LinuxInspector._for_test(replace(self.config, freeze_timeout_seconds=.001), self.fs)
        self.blocked('FREEZE_TIMEOUT', inspector.snapshot)
        self.assertEqual(self.fs.group.writes, [1, 0])

    def test_uncertain_freeze_write_attempts_restore(self):
        self.fs.group.fail_freeze_once = True
        self.blocked('OBSERVATION_UNAVAILABLE')
        self.assertEqual(self.fs.group.writes, [1, 0])

    def test_restore_failure_cannot_return_valid_snapshot(self):
        self.fs.group.fail_restore = True
        self.blocked('OBSERVATION_UNAVAILABLE')

    def test_missing_freezer_or_populated_attribute_is_rejected(self):
        self.fs.group.events_override[''] = b'populated 1\n'
        self.blocked('EVENTS_REQUIRED')
        self.fs.group.events_override[''] = b'frozen 1\n'
        self.blocked('EVENTS_REQUIRED')

    def test_boot_id_and_inode_are_pinned(self):
        self.fs.boot = '22345678-1234-4234-9234-123456789abc'
        self.blocked('BOOT_CHANGED')
        self.assertEqual(self.fs.group.writes, [])
        self.fs.boot = BOOT
        self.fs.group.nodes['']['inode'] = 71
        self.blocked('CGROUP_REPLACED')

    def test_new_boot_during_snapshot_is_rejected(self):
        def change(process, path, count):
            if path == 'stat' and count == 4:
                self.fs.boot = '22345678-1234-4234-9234-123456789abc'
        self.fs.processes[100].on_read = change
        self.blocked('BOOT_CHANGED')

    def test_limits_are_enforced_without_truncating(self):
        self.fs.group.nodes['child'] = {'inode': 71, 'members': (), 'threads': (), 'type': b'domain'}
        inspector = LinuxInspector._for_test(replace(self.config, maximum_cgroups=1), self.fs)
        self.blocked('CGROUP_LIMIT', inspector.snapshot)

    def test_malformed_or_mutable_operator_config_is_rejected(self):
        changes = [dict(cgroup_path=Path('/tmp/task')), dict(cgroup_inode=True),
                   dict(executable_profiles=[self.profile]), dict(runtime_files=()),
                   dict(boot_id='bad'), dict(freeze_timeout_seconds=float('nan')),
                   dict(runtime_files=(PinnedFile(Path('/opt/../escape'), 'a' * 64),))]
        for values in changes:
            with self.subTest(values=values):
                self.blocked(operation=lambda: LinuxInspector(replace(self.config, **values)))
        for profile in (replace(self.profile, uid=0), replace(self.profile, seccomp=0),
                        replace(self.profile, cap_bnd='0000000000000001'),
                        replace(self.profile, namespaces=list(NAMESPACES)),
                        replace(self.profile, lsm_profile='unconfined')):
            with self.subTest(profile=profile):
                self.blocked(operation=lambda: LinuxInspector(replace(self.config, executable_profiles=(profile,))))

    def test_baseline_requires_typed_immutable_snapshot(self):
        self.blocked('BASELINE_REQUIRED', lambda: self.inspector.assert_unchanged({'worker': 'evidence'}))

    def test_under_frozen_installs_guard_between_stable_reads(self):
        observed = []
        def install(snapshot):
            self.assertEqual(self.fs.group.request, 1)
            observed.append(snapshot)
        result = self.inspector.under_frozen(install)
        self.assertEqual(observed, [result])
        self.assertEqual(self.fs.group.writes, [1, 0])

    def test_under_frozen_rejects_mutation_during_guard_install(self):
        def install(snapshot):
            self.fs.processes[100].values['stat'] = proc_stat(100, 999)
        self.blocked('SNAPSHOT_CHANGED', lambda: self.inspector.under_frozen(install))
        self.assertEqual(self.fs.group.writes, [1, 0])

    def test_under_frozen_refuses_callback_thaw(self):
        def install(snapshot):
            self.fs.group.request = 0
        self.blocked('FREEZE_LOST', lambda: self.inspector.under_frozen(install))

    def test_under_frozen_restores_after_callback_failure(self):
        def install(snapshot):
            raise InspectionBlocked('GUARD_INSTALL_FAILED')
        self.blocked('GUARD_INSTALL_FAILED', lambda: self.inspector.under_frozen(install))
        self.assertEqual(self.fs.group.writes, [1, 0])

    def test_read_only_identity_check_does_not_freeze(self):
        self.assertIsNone(self.inspector.verify_unit_identity())
        self.assertEqual(self.fs.group.writes, [])
        self.fs.group.nodes['']['inode'] = 999
        self.blocked('CGROUP_REPLACED', self.inspector.verify_unit_identity)

    def test_under_frozen_rejects_reentrant_snapshot_without_deadlock(self):
        self.blocked('ALREADY_RUNNING', lambda: self.inspector.under_frozen(
            lambda snapshot: self.inspector.snapshot()))
        self.assertEqual(self.fs.group.writes, [1, 0])

    def test_total_thread_limit_is_enforced(self):
        process = self.fs.processes[100]
        process.task_values[101] = dict(process.task_values[100], status=proc_status(100, 101))
        self.fs.group.nodes['']['threads'] = (100, 101)
        inspector = LinuxInspector._for_test(replace(self.config, maximum_threads=1), self.fs)
        self.blocked('MEMBERS_INVALID', inspector.snapshot)

    def test_secure_file_reader_does_not_block_on_fifo(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            os.mkfifo(root / 'pipe')
            with _open_absolute_directory(root) as root_fd:
                with _open_beneath(root_fd, 'pipe') as fd:
                    self.blocked('REGULAR_FILE_REQUIRED', lambda: _file_digest(fd, 100))

    def test_kill_uses_pinned_unit_without_claiming_exit(self):
        self.assertIsNone(self.inspector.kill_unit())
        self.assertEqual(self.fs.group.kill_count, 1)
        self.assertEqual(self.fs.group.writes, [])
        self.assertEqual(self.fs.group.nodes['']['members'], (100,))
        self.assertEqual(self.fs.opened, [])

    def test_kill_rejects_changed_boot_or_unit(self):
        self.fs.boot = '22345678-1234-4234-9234-123456789abc'
        self.blocked('BOOT_CHANGED', self.inspector.kill_unit)
        self.assertEqual(self.fs.group.kill_count, 0)
        self.fs.boot = BOOT
        self.fs.group.nodes['']['inode'] = 999
        self.blocked('CGROUP_REPLACED', self.inspector.kill_unit)
        self.assertEqual(self.fs.group.kill_count, 0)

    def test_failed_kill_is_not_retried_or_promoted_to_exit(self):
        self.fs.group.kill_error = PermissionError('denied')
        self.blocked('KILL_OUTCOME_UNCERTAIN', self.inspector.kill_unit)
        self.assertEqual(self.fs.group.kill_count, 1)
        self.assertEqual(self.fs.group.nodes['']['members'], (100,))

    def test_complain_or_unverified_lsm_cannot_be_pinned(self):
        for label in ('medical-runtime (complain)', 'medical-runtime',
                      'system_u:system_r:container_t:s0', 'unconfined (enforce)'):
            with self.subTest(label=label):
                self.blocked('ENFORCED_APPARMOR_PIN_REQUIRED', lambda: LinuxInspector(
                    replace(self.config, executable_profiles=(replace(self.profile, lsm_profile=label),))))

    def test_stat_parser_handles_parentheses_and_rejects_zombies(self):
        self.assertEqual(_start_ticks(proc_stat(100), 100), 200)
        for raw in (proc_stat(101), proc_stat(100).replace(b') S ', b') Z '), b'invalid'):
            self.blocked('STAT_INVALID', lambda: _start_ticks(raw, 100))

    def test_secure_file_reader_refuses_symlink_and_non_regular_file(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'real').write_bytes(b'file bytes')
            (root / 'link').symlink_to(root / 'real')
            (root / 'nested').mkdir()
            (root / 'alias').symlink_to(root / 'nested', target_is_directory=True)
            with _open_absolute_directory(root) as root_fd:
                with _open_beneath(root_fd, 'real') as fd:
                    value, _, _ = _file_digest(fd, 100)
                    self.assertEqual(value, digest(b'file bytes'))
                for path in ('link', 'alias/missing'):
                    with self.assertRaises(OSError):
                        with _open_beneath(root_fd, path):
                            self.fail('symlink followed')
                with _open_beneath(root_fd, 'nested') as fd:
                    self.blocked('REGULAR_FILE_REQUIRED', lambda: _file_digest(fd, 100))
                self.blocked('RELATIVE_PATH_INVALID', lambda: _open_beneath(root_fd, '../escape').__enter__())

    def test_secure_file_reader_enforces_size(self):
        with tempfile.TemporaryFile() as file:
            file.write(b'too large')
            file.seek(0)
            self.blocked('REGULAR_FILE_REQUIRED', lambda: _file_digest(file.fileno(), 1))


if __name__ == '__main__':
    unittest.main()
