"""Fail-closed Linux observations from operator pins, never worker assertions.

This module does not provision isolation, launch processes, or attest process exit.
It requires an existing cgroup-v2 domain, freezes it only while reading, and
restores its original freezer request. The supervisor-only kill_unit operation
writes cgroup.kill through a pinned directory descriptor; it never proves exit. A snapshot is an observation at a point in
time, not proof that no exec/fork occurred between two observations. The owning
supervisor must retain pidfds and gate admission for the complete lifecycle.
"""
from __future__ import annotations

from contextlib import contextmanager, ExitStack
from dataclasses import dataclass, fields
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import select
import stat
import threading
import time
from uuid import UUID

_NAMESPACES = ('cgroup', 'ipc', 'mnt', 'net', 'pid', 'user', 'uts')
_CAPABILITIES = ('CapInh', 'CapPrm', 'CapEff', 'CapBnd', 'CapAmb')
_ZERO_CAPS = '0000000000000000'
_CGROUP_ROOT = Path('/sys/fs/cgroup')


class InspectionBlocked(RuntimeError):
    """An absent, inconsistent, or unpinned observation is not valid evidence."""


def _require(condition, reason):
    if not condition:
        raise InspectionBlocked(reason)


def _sha256(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def _valid_hash(value):
    return isinstance(value, str) and re.fullmatch('[0-9a-f]{64}', value) is not None


def _path(value):
    _require(isinstance(value, Path) and value.is_absolute() and '..' not in value.parts
             and str(value) == str(PurePosixPath(value)) and '\x00' not in str(value),
             'INSPECTION_PIN_PATH_INVALID')


def _positive(value):
    return type(value) is int and value > 0


@dataclass(frozen=True, slots=True)
class PinnedFile:
    """Absolute canonical path as visible through each inspected process root."""
    path: Path
    sha256: str

    def wire(self):
        return {'path': str(self.path), 'sha256': self.sha256}


@dataclass(frozen=True, slots=True)
class ExecutableProfile:
    exe_sha256: str
    exe_device: int
    exe_inode: int
    cmdline_sha256: str
    uid: int
    gid: int
    namespaces: tuple[tuple[str, int], ...]
    lsm_profile: str
    role: str = 'runtime'
    required: bool = True
    no_new_privs: int = 1
    seccomp: int = 2
    cap_eff: str = _ZERO_CAPS
    cap_inh: str = _ZERO_CAPS
    cap_prm: str = _ZERO_CAPS
    cap_bnd: str = _ZERO_CAPS
    cap_amb: str = _ZERO_CAPS


@dataclass(frozen=True, slots=True)
class LinuxInspectionConfig:
    cgroup_path: Path
    cgroup_inode: int
    executable_profiles: tuple[ExecutableProfile, ...]
    runtime_files: tuple[PinnedFile, ...]
    profile_files: tuple[PinnedFile, ...]
    boot_id: str
    maximum_file_bytes: int = 256 * 1024 * 1024
    maximum_processes: int = 128
    maximum_cgroups: int = 128
    maximum_threads: int = 1024
    freeze_timeout_seconds: float = 2.0

    def validate(self):
        _path(self.cgroup_path)
        _require(_CGROUP_ROOT in self.cgroup_path.parents and _positive(self.cgroup_inode),
                 'INSPECTION_CGROUP_PIN_INVALID')
        try:
            _require(str(UUID(self.boot_id)) == self.boot_id, 'INSPECTION_BOOT_PIN_INVALID')
        except (ValueError, TypeError, AttributeError):
            raise InspectionBlocked('INSPECTION_BOOT_PIN_INVALID') from None
        for value, maximum in ((self.maximum_file_bytes, 1024 * 1024 * 1024),
                               (self.maximum_processes, 4096), (self.maximum_cgroups, 4096),
                               (self.maximum_threads, 16384)):
            _require(_positive(value) and value <= maximum, 'INSPECTION_LIMIT_INVALID')
        _require(type(self.freeze_timeout_seconds) in (int, float)
                 and 0 < self.freeze_timeout_seconds <= 10, 'INSPECTION_LIMIT_INVALID')
        for files in (self.runtime_files, self.profile_files):
            _require(type(files) is tuple and files, 'INSPECTION_FILE_PINS_REQUIRED')
            _require(all(type(item) is PinnedFile for item in files), 'INSPECTION_FILE_PIN_INVALID')
            _require(len({item.path for item in files}) == len(files), 'INSPECTION_DUPLICATE_FILE_PIN')
            for item in files:
                _path(item.path)
                _require(_valid_hash(item.sha256), 'INSPECTION_FILE_PIN_INVALID')
        _require(not {item.path for item in self.runtime_files}.intersection(
            item.path for item in self.profile_files), 'INSPECTION_DUPLICATE_FILE_PIN')
        _require(type(self.executable_profiles) is tuple and self.executable_profiles,
                 'INSPECTION_EXECUTABLE_PINS_REQUIRED')
        for profile in self.executable_profiles:
            _require(type(profile) is ExecutableProfile, 'INSPECTION_EXECUTABLE_PIN_INVALID')
            _require(_valid_hash(profile.exe_sha256) and _valid_hash(profile.cmdline_sha256)
                     and type(profile.exe_device) is int and profile.exe_device >= 0
                     and _positive(profile.exe_inode), 'INSPECTION_EXECUTABLE_PIN_INVALID')
            _require(type(profile.uid) is int and profile.uid > 0 and type(profile.gid) is int
                     and profile.gid > 0, 'INSPECTION_UNPRIVILEGED_IDS_REQUIRED')
            _require(type(profile.namespaces) is tuple and len(profile.namespaces) == len(_NAMESPACES)
                     and all(type(pair) is tuple and len(pair) == 2 for pair in profile.namespaces)
                     and sorted(name for name, _ in profile.namespaces) == list(_NAMESPACES)
                     and all(_positive(inode) for _, inode in profile.namespaces),
                     'INSPECTION_NAMESPACE_PINS_REQUIRED')
            _require(profile.role in ('launcher', 'runtime') and type(profile.required) is bool,
                     'INSPECTION_EXECUTABLE_ROLE_INVALID')
            _require(type(profile.no_new_privs) is int and profile.no_new_privs == 1
                     and type(profile.seccomp) is int and profile.seccomp == 2,
                     'INSPECTION_HARDENING_REQUIRED')
            _require(isinstance(profile.lsm_profile, str) and profile.lsm_profile.strip() == profile.lsm_profile
                     and profile.lsm_profile not in ('', 'unconfined') and '\x00' not in profile.lsm_profile
                     and re.fullmatch(r'[^()\n\x00]+ \(enforce\)', profile.lsm_profile) is not None
                     and profile.lsm_profile != 'unconfined (enforce)',
                     'INSPECTION_ENFORCED_APPARMOR_PIN_REQUIRED')
            for capability in (profile.cap_eff, profile.cap_inh, profile.cap_prm,
                               profile.cap_bnd, profile.cap_amb):
                _require(capability == _ZERO_CAPS, 'INSPECTION_CAPABILITIES_MUST_BE_DROPPED')
        runtime = {item.exe_sha256 for item in self.executable_profiles
                   if item.role == 'runtime' and item.required}
        launcher = {item.exe_sha256 for item in self.executable_profiles if item.role == 'launcher'}
        _require(runtime and not runtime.intersection(launcher), 'INSPECTION_DISTINCT_RUNTIME_REQUIRED')
        _require(len(set(self.executable_profiles)) == len(self.executable_profiles),
                 'INSPECTION_DUPLICATE_EXECUTABLE_PIN')


@dataclass(frozen=True, slots=True)
class ProcessSnapshot:
    pid: int
    start_ticks: int
    exe_sha256: str
    exe_device: int
    exe_inode: int
    cmdline_sha256: str
    uid: int
    gid: int
    namespaces: tuple[tuple[str, int], ...]
    no_new_privs: int
    seccomp: int
    cap_eff: str
    lsm_profile: str
    cgroup_path: str
    task_ids: tuple[int, ...]
    children: tuple[int, ...]
    cap_inh: str
    cap_prm: str
    cap_bnd: str
    cap_amb: str

    def wire(self):
        return {name: dict(value) if name == 'namespaces' else list(value)
                if name in ('task_ids', 'children') else value
                for name, value in ((field.name, getattr(self, field.name)) for field in fields(self))}


@dataclass(frozen=True, slots=True)
class CgroupSnapshot:
    path: str
    inode: int
    members: tuple[int, ...]
    threads: tuple[int, ...]

    def wire(self):
        return {'path': self.path, 'inode': self.inode,
                'members': list(self.members), 'threads': list(self.threads)}


@dataclass(frozen=True, slots=True)
class InspectionSnapshot:
    boot_id: str
    cgroup_path: str
    cgroup_inode: int
    cgroup_members: tuple[int, ...]
    processes: tuple[ProcessSnapshot, ...]
    runtime_files: tuple[PinnedFile, ...]
    profile_files: tuple[PinnedFile, ...]
    cgroups: tuple[CgroupSnapshot, ...]

    def wire(self):
        """A fresh deterministic wire value; no timestamps or self-referential hash."""
        return {'boot_id': self.boot_id, 'cgroup_path': self.cgroup_path,
                'cgroup_inode': self.cgroup_inode, 'cgroup_members': list(self.cgroup_members),
                'processes': [item.wire() for item in self.processes],
                'runtime_files': [item.wire() for item in self.runtime_files],
                'profile_files': [item.wire() for item in self.profile_files],
                'cgroups': [item.wire() for item in self.cgroups]}

    @property
    def sha256(self):
        return _sha256(json.dumps(self.wire(), sort_keys=True, separators=(',', ':'),
                                  ensure_ascii=False, allow_nan=False).encode('utf-8'))


def _read_fd(fd, maximum):
    result = bytearray()
    while chunk := os.read(fd, min(65536, maximum + 1 - len(result))):
        result.extend(chunk)
        _require(len(result) <= maximum, 'INSPECTION_READ_LIMIT')
    return bytes(result)


def _relative_parts(path):
    value = PurePosixPath(path)
    _require(not value.is_absolute() and '..' not in value.parts,
             'INSPECTION_RELATIVE_PATH_INVALID')
    return value.parts


@contextmanager
def _open_beneath(directory_fd, path, flags=os.O_RDONLY | os.O_NONBLOCK):
    """No path component may redirect inspection outside its pinned directory."""
    parts = _relative_parts(path)
    _require(parts, 'INSPECTION_RELATIVE_PATH_INVALID')
    fd = os.dup(directory_fd)
    try:
        for index, part in enumerate(parts):
            final = index == len(parts) - 1
            next_fd = os.open(part, (flags if final else os.O_RDONLY | os.O_DIRECTORY)
                              | os.O_NOFOLLOW | os.O_CLOEXEC, dir_fd=fd)
            os.close(fd)
            fd = next_fd
        yield fd
    finally:
        os.close(fd)


@contextmanager
def _open_absolute_directory(path):
    fd = os.open('/', os.O_RDONLY | os.O_DIRECTORY | os.O_CLOEXEC)
    try:
        with _open_beneath(fd, str(path).lstrip('/'), os.O_RDONLY | os.O_DIRECTORY) as result:
            yield result
    finally:
        os.close(fd)


def _file_digest(fd, maximum):
    before = os.fstat(fd)
    _require(stat.S_ISREG(before.st_mode) and 0 <= before.st_size <= maximum,
             'INSPECTION_REGULAR_FILE_REQUIRED')
    value = hashlib.sha256()
    size = 0
    while chunk := os.read(fd, min(65536, maximum + 1 - size)):
        size += len(chunk)
        _require(size <= maximum, 'INSPECTION_READ_LIMIT')
        value.update(chunk)
    after = os.fstat(fd)
    fields = ('st_dev', 'st_ino', 'st_size', 'st_mtime_ns', 'st_ctime_ns')
    _require(size == before.st_size and all(getattr(before, field) == getattr(after, field)
                                          for field in fields), 'INSPECTION_FILE_CHANGED')
    return value.hexdigest(), before.st_dev, before.st_ino


class _CgroupView:
    def __init__(self, fd):
        self.fd = fd

    def inode(self, relative=''):
        if not relative:
            return os.fstat(self.fd).st_ino
        with _open_beneath(self.fd, relative, os.O_RDONLY | os.O_DIRECTORY) as fd:
            return os.fstat(fd).st_ino

    def read(self, relative):
        with _open_beneath(self.fd, relative) as fd:
            return _read_fd(fd, 1024 * 1024)

    def freeze(self, value):
        with _open_beneath(self.fd, 'cgroup.freeze', os.O_WRONLY) as fd:
            _require(os.write(fd, str(value).encode('ascii')) == 1, 'INSPECTION_FREEZER_WRITE_FAILED')

    def kill(self):
        with _open_beneath(self.fd, 'cgroup.kill', os.O_WRONLY) as fd:
            _require(os.write(fd, b'1') == 1, 'INSPECTION_CGROUP_KILL_WRITE_FAILED')

    def directories(self, relative):
        @contextmanager
        def directory():
            if relative:
                with _open_beneath(self.fd, relative, os.O_RDONLY | os.O_DIRECTORY) as fd:
                    yield fd
            else:
                yield self.fd
        with directory() as fd:
            result = []
            for name in os.listdir(fd):
                info = os.stat(name, dir_fd=fd, follow_symlinks=False)
                _require(not stat.S_ISLNK(info.st_mode), 'INSPECTION_CGROUP_SYMLINK')
                if stat.S_ISDIR(info.st_mode):
                    result.append(name)
            return sorted(result)


class _ProcView:
    def __init__(self, fd, pidfd):
        self.fd, self.pidfd = fd, pidfd

    def alive(self):
        poller = select.poll()
        poller.register(self.pidfd, select.POLLIN | select.POLLHUP | select.POLLERR)
        _require(not poller.poll(0), 'INSPECTION_PROCESS_DISAPPEARED')

    def read(self, relative):
        with _open_beneath(self.fd, relative) as fd:
            return _read_fd(fd, 1024 * 1024)

    def namespace(self, name, task=None):
        prefix = f'task/{task}/' if task is not None else ''
        # Only kernel-provided namespace magic symlinks are followed/read.
        value = os.readlink(prefix + 'ns/' + name, dir_fd=self.fd)
        match = re.fullmatch(re.escape(name) + r':\[([1-9][0-9]*)\]', value)
        _require(match is not None, 'INSPECTION_NAMESPACE_INVALID')
        return int(match.group(1))

    def tasks(self):
        with _open_beneath(self.fd, 'task', os.O_RDONLY | os.O_DIRECTORY) as fd:
            names = os.listdir(fd)
            _require(all(re.fullmatch('[1-9][0-9]*', name) for name in names),
                     'INSPECTION_TASK_INVALID')
            return tuple(sorted(int(name) for name in names))

    def executable(self, maximum):
        fd = os.open('exe', os.O_RDONLY | os.O_CLOEXEC, dir_fd=self.fd)
        try:
            return _file_digest(fd, maximum)
        finally:
            os.close(fd)

    def file(self, path, maximum):
        # /proc/PID/root includes that process's root and mount view, unlike a
        # host-path hash. Components below it cannot be symlinks or escape root.
        root_fd = os.open('root', os.O_RDONLY | os.O_DIRECTORY | os.O_CLOEXEC, dir_fd=self.fd)
        try:
            with _open_beneath(root_fd, str(path).lstrip('/')) as fd:
                return _file_digest(fd, maximum)[0]
        finally:
            os.close(root_fd)


class _KernelFS:
    def boot_id(self):
        with open('/proc/sys/kernel/random/boot_id', 'rb') as stream:
            return stream.read(128).decode('ascii').strip()

    @contextmanager
    def cgroup(self, config):
        # Refuse cgroup-v1, remapped roots, and a nested mount that could hide the
        # inspected subtree. Standard /sys/fs/cgroup with root / is required.
        with open('/proc/self/mountinfo', 'rb') as stream:
            mountinfo = stream.read(1024 * 1024 + 1)
        _require(len(mountinfo) <= 1024 * 1024, 'INSPECTION_MOUNTINFO_LIMIT')
        mounts = []
        for line in mountinfo.decode('utf-8', errors='strict').splitlines():
            left, separator, right = line.partition(' - ')
            _require(bool(separator), 'INSPECTION_MOUNTINFO_INVALID')
            fields, filesystem = left.split(), right.split()
            _require(len(fields) >= 6 and len(filesystem) >= 3, 'INSPECTION_MOUNTINFO_INVALID')
            path = Path(re.sub(r'\\([0-7]{3})', lambda item: chr(int(item[1], 8)), fields[4]))
            if path == config.cgroup_path or path in config.cgroup_path.parents:
                mounts.append((path, fields[3], filesystem[0]))
            elif config.cgroup_path in path.parents:
                raise InspectionBlocked('INSPECTION_NESTED_CGROUP_MOUNT')
        _require(mounts, 'INSPECTION_CGROUP2_REQUIRED')
        mount = max(mounts, key=lambda item: len(item[0].parts))
        _require(mount == (_CGROUP_ROOT, '/', 'cgroup2'), 'INSPECTION_CGROUP2_REQUIRED')
        with _open_absolute_directory(config.cgroup_path) as fd:
            _require(os.fstat(fd).st_ino == config.cgroup_inode, 'INSPECTION_CGROUP_REPLACED')
            yield _CgroupView(fd)
            with _open_absolute_directory(config.cgroup_path) as check_fd:
                _require(os.fstat(check_fd).st_ino == config.cgroup_inode,
                         'INSPECTION_CGROUP_REPLACED')

    @contextmanager
    def process(self, pid):
        _require(hasattr(os, 'pidfd_open'), 'INSPECTION_PIDFD_REQUIRED')
        pidfd = os.pidfd_open(pid, 0)
        try:
            with _open_absolute_directory(Path('/proc') / str(pid)) as fd:
                view = _ProcView(fd, pidfd)
                view.alive()
                yield view
                view.alive()
        finally:
            os.close(pidfd)


def _key_values(raw, separator=None):
    values = {}
    for line in raw.decode('ascii', errors='strict').splitlines():
        if separator:
            key, found, value = line.partition(separator)
            _require(bool(found), 'INSPECTION_FIELDS_INVALID')
        else:
            parts = line.split()
            _require(len(parts) == 2, 'INSPECTION_FIELDS_INVALID')
            key, value = parts
        _require(key not in values, 'INSPECTION_DUPLICATE_FIELD')
        values[key] = value.strip()
    return values


def _ids(raw, maximum):
    parts = raw.decode('ascii', errors='strict').split()
    _require(len(parts) <= maximum and all(re.fullmatch('[1-9][0-9]*', part) for part in parts),
             'INSPECTION_MEMBERS_INVALID')
    values = tuple(sorted(int(part) for part in parts))
    _require(len(set(values)) == len(values), 'INSPECTION_MEMBERS_CHANGED')
    return values


def _start_ticks(raw, pid):
    # comm may contain spaces and closing parentheses; field 22 is offset 19
    # after the final ') ', not a whitespace split over the complete stat line.
    prefix, found, suffix = raw.rpartition(b') ')
    _require(bool(found) and prefix.startswith(str(pid).encode('ascii') + b' ('),
             'INSPECTION_STAT_INVALID')
    fields = suffix.split()
    _require(len(fields) >= 20 and fields[0] not in (b'Z', b'X', b'x')
             and fields[19].isdigit() and int(fields[19]) > 0, 'INSPECTION_STAT_INVALID')
    return int(fields[19])


class LinuxInspector:
    """Only operator-reviewed immutable pins enter the production constructor."""
    def __init__(self, config: LinuxInspectionConfig):
        _require(type(config) is LinuxInspectionConfig, 'INSPECTION_CONFIG_REQUIRED')
        config.validate()
        self.config = config
        self._fs = _KernelFS()
        self._lock = threading.Lock()

    @classmethod
    def _for_test(cls, config, adapter):
        """Package-private fixture seam; never selected by worker launch input."""
        result = cls(config)
        result._fs = adapter
        return result

    def _boot(self):
        _require(self._fs.boot_id() == self.config.boot_id, 'INSPECTION_BOOT_CHANGED')

    def _events(self, group, relative=''):
        values = _key_values(group.read(str(PurePosixPath(relative) / 'cgroup.events')))
        _require(values.get('populated') in ('0', '1') and values.get('frozen') in ('0', '1'),
                 'INSPECTION_EVENTS_REQUIRED')
        return values

    @contextmanager
    def _frozen(self):
        _require(self._lock.acquire(blocking=False), 'INSPECTION_ALREADY_RUNNING')
        try:
            self._boot()
            with self._fs.cgroup(self.config) as group:
                _require(group.inode() == self.config.cgroup_inode, 'INSPECTION_CGROUP_REPLACED')
                original = group.read('cgroup.freeze').strip()
                _require(original in (b'0', b'1'), 'INSPECTION_FREEZER_REQUIRED')
                initially_frozen = self._events(group)['frozen']
                changed = False
                try:
                    if original == b'0':
                        # Set before the write: even an uncertain write must be
                        # followed by restoring the original request.
                        changed = True
                        group.freeze(1)
                    until = time.monotonic() + self.config.freeze_timeout_seconds
                    while self._events(group)['frozen'] != '1':
                        _require(time.monotonic() < until, 'INSPECTION_FREEZE_TIMEOUT')
                        time.sleep(0.005)
                    self._boot()
                    yield group
                    _require(self._events(group)['frozen'] == '1', 'INSPECTION_FREEZE_LOST')
                    self._boot()
                finally:
                    if changed:
                        group.freeze(0)
                        _require(group.read('cgroup.freeze').strip() == b'0',
                                 'INSPECTION_FREEZER_RESTORE_FAILED')
                        # An ancestor may already have frozen this unit. Restore
                        # the request without incorrectly thawing its ancestor.
                        if initially_frozen == '0':
                            until = time.monotonic() + self.config.freeze_timeout_seconds
                            while self._events(group)['frozen'] != '0':
                                _require(time.monotonic() < until,
                                         'INSPECTION_FREEZER_RESTORE_TIMEOUT')
                                time.sleep(0.005)
        except InspectionBlocked:
            raise
        except (OSError, ValueError, UnicodeError, KeyError, TypeError, OverflowError) as error:
            raise InspectionBlocked('INSPECTION_OBSERVATION_UNAVAILABLE') from error
        finally:
            self._lock.release()

    def _groups(self, group):
        result = []
        pending = ['']
        while pending:
            relative = pending.pop()
            _require(len(result) < self.config.maximum_cgroups, 'INSPECTION_CGROUP_LIMIT')
            prefix = PurePosixPath(relative)
            _require(group.read(str(prefix / 'cgroup.type')).strip() == b'domain',
                     'INSPECTION_DOMAIN_CGROUP_REQUIRED')
            event = self._events(group, relative)
            _require(event['frozen'] == '1', 'INSPECTION_FREEZE_LOST')
            members = _ids(group.read(str(prefix / 'cgroup.procs')), self.config.maximum_processes)
            threads = _ids(group.read(str(prefix / 'cgroup.threads')), self.config.maximum_threads)
            result.append(CgroupSnapshot(str(self.config.cgroup_path / relative),
                                         group.inode(relative), members, threads))
            pending.extend(str(prefix / name) for name in reversed(group.directories(relative)))
        return tuple(sorted(result, key=lambda item: item.path))

    def _security(self, process, pid, task=None):
        prefix = f'task/{task}/' if task is not None else ''
        values = _key_values(process.read(prefix + 'status'), ':')
        _require(values.get('Tgid') == str(pid) and values.get('Pid') == str(task or pid),
                 'INSPECTION_TASK_IDENTITY_CHANGED')
        ids = []
        for name in ('Uid', 'Gid'):
            fields = values[name].split()
            _require(len(fields) == 4 and all(value.isascii() and value.isdigit() for value in fields)
                     and len(set(fields)) == 1, 'INSPECTION_CREDENTIALS_INVALID')
            ids.append(int(fields[0]))
        _require(values['NoNewPrivs'] == '1' and values['Seccomp'] == '2',
                 'INSPECTION_HARDENING_REQUIRED')
        capabilities = tuple(values[name] for name in _CAPABILITIES)
        _require(all(value == _ZERO_CAPS for value in capabilities),
                 'INSPECTION_CAPABILITIES_CHANGED')
        namespaces = tuple((name, process.namespace(name, task)) for name in _NAMESPACES)
        lsm = process.read(prefix + 'attr/current').decode('utf-8', errors='strict').rstrip('\n')
        _require(re.fullmatch(r'[^()\n\x00]+ \(enforce\)', lsm) is not None
                 and lsm != 'unconfined (enforce)', 'INSPECTION_LSM_REQUIRED')
        return (*ids, namespaces, 1, 2, *capabilities, lsm)

    def _process(self, process, pid, cgroup_path, members):
        process.alive()
        start = _start_ticks(process.read('stat'), pid)
        expected = '/' + str(Path(cgroup_path).relative_to(_CGROUP_ROOT))
        _require(process.read('cgroup').decode('utf-8').strip() == '0::' + expected,
                 'INSPECTION_PROCESS_CGROUP_CHANGED')
        security = self._security(process, pid)
        uid, gid, namespaces, nnp, seccomp, inh, prm, eff, bnd, amb, lsm = security
        tasks = process.tasks()
        _require(pid in tasks and len(tasks) <= self.config.maximum_threads,
                 'INSPECTION_TASKS_REQUIRED')
        children = []
        for task in tasks:
            _require(self._security(process, pid, task) == security,
                     'INSPECTION_THREAD_SECURITY_CHANGED')
            _require(process.read(f'task/{task}/cgroup').decode('utf-8').strip() == '0::' + expected,
                     'INSPECTION_THREAD_CGROUP_CHANGED')
            children.extend(_ids(process.read(f'task/{task}/children'), self.config.maximum_processes))
        _require(len(set(children)) == len(children) and set(children).issubset(members),
                 'INSPECTION_CHILD_ESCAPED')
        executable, device, inode = process.executable(self.config.maximum_file_bytes)
        command = process.read('cmdline')
        _require(bool(command) and command.endswith(b'\x00'), 'INSPECTION_CMDLINE_REQUIRED')
        snapshot = ProcessSnapshot(pid, start, executable, device, inode, _sha256(command), uid,
                                   gid, namespaces, nnp, seccomp, eff, lsm, cgroup_path,
                                   tasks, tuple(sorted(children)), inh, prm, bnd, amb)
        matched = [profile for profile in self.config.executable_profiles
                   if all(getattr(profile, field) == getattr(snapshot, field)
                          for field in ('exe_sha256', 'exe_device', 'exe_inode', 'cmdline_sha256',
                                        'uid', 'gid', 'no_new_privs', 'seccomp', 'cap_eff', 'cap_inh',
                                        'cap_prm', 'cap_bnd', 'cap_amb', 'lsm_profile'))
                   and tuple(sorted(profile.namespaces)) == snapshot.namespaces]
        _require(len(matched) == 1, 'INSPECTION_EXECUTABLE_PROFILE_MISMATCH')
        for pinned in self.config.runtime_files + self.config.profile_files:
            _require(process.file(pinned.path, self.config.maximum_file_bytes) == pinned.sha256,
                     'INSPECTION_PROCESS_FILE_MISMATCH')
        _require(_start_ticks(process.read('stat'), pid) == start, 'INSPECTION_PID_REUSED')
        process.alive()
        return snapshot, matched[0]

    def _snapshot(self, group):
        with ExitStack() as handles:
            groups = self._groups(group)
            membership = [(pid, item.path) for item in groups for pid in item.members]
            members = tuple(sorted(pid for pid, _ in membership))
            _require(members and len(members) <= self.config.maximum_processes
                     and len(set(members)) == len(members), 'INSPECTION_EXACT_MEMBERS_REQUIRED')
            _require(self._events(group)['populated'] == '1', 'INSPECTION_POPULATED_MISMATCH')
            views = {pid: handles.enter_context(self._fs.process(pid)) for pid in members}
            first = [self._process(views[pid], pid, path, set(members))
                     for pid, path in sorted(membership)]
            threads = tuple(sorted(task for item, _ in first for task in item.task_ids))
            actual_threads = tuple(sorted(task for item in groups for task in item.threads))
            _require(threads == actual_threads and len(set(threads)) == len(threads)
                     and len(threads) <= self.config.maximum_threads,
                     'INSPECTION_EXACT_THREADS_REQUIRED')
            _require(all(not profile.required or profile in [matched for _, matched in first]
                         for profile in self.config.executable_profiles), 'INSPECTION_REQUIRED_RUNTIME_MISSING')
            # Freezing does not prevent external migration, SIGKILL, or cgroup
            # directory changes. A complete second read must agree, including
            # process-root files, threads, child edges, and executable identity.
            second = [self._process(views[pid], pid, path, set(members))
                      for pid, path in sorted(membership)]
            _require(first == second and groups == self._groups(group),
                     'INSPECTION_SNAPSHOT_CHANGED')
            return InspectionSnapshot(self.config.boot_id, str(self.config.cgroup_path),
                                      self.config.cgroup_inode, members,
                                      tuple(item for item, _ in first),
                                      tuple(sorted(self.config.runtime_files, key=lambda item: str(item.path))),
                                      tuple(sorted(self.config.profile_files, key=lambda item: str(item.path))), groups)

    def snapshot(self):
        with self._frozen() as group:
            return self._snapshot(group)

    def under_frozen(self, callback):
        """Install a supervisor-owned guard between two stable frozen reads.

        callback receives the verified immutable snapshot. It must not call
        snapshot recursively or unfreeze/migrate the unit. Its return value is
        ignored; this method returns only the final verified snapshot.
        """
        _require(callable(callback), 'INSPECTION_CALLBACK_REQUIRED')
        with self._frozen() as group:
            before = self._snapshot(group)
            callback(before)
            _require(self._events(group)['frozen'] == '1', 'INSPECTION_FREEZE_LOST')
            after = self._snapshot(group)
            _require(before == after, 'INSPECTION_SNAPSHOT_CHANGED')
            return after

    def verify_unit_identity(self):
        """Read-only identity check, not authority for a later path-based write.

        A caller writing cgroup.kill must independently hold a no-symlink-opened
        directory fd, compare fstat().st_ino with the operator pin, and open the
        control file relative to that fd. This check cannot remove that TOCTOU.
        """
        try:
            self._boot()
            with self._fs.cgroup(self.config) as group:
                _require(group.inode() == self.config.cgroup_inode, 'INSPECTION_CGROUP_REPLACED')
                _require(group.read('cgroup.type').strip() == b'domain',
                         'INSPECTION_DOMAIN_CGROUP_REQUIRED')
                self._boot()
        except InspectionBlocked:
            raise
        except (OSError, ValueError, UnicodeError, KeyError, TypeError, OverflowError) as error:
            raise InspectionBlocked('INSPECTION_OBSERVATION_UNAVAILABLE') from error

    def kill_unit(self):
        """Supervisor cleanup only. Sending kill is not evidence of process exit.

        The cgroup fd is opened without symlink traversal, verified against the
        operator inode and filesystem pins, and held across the relative open of
        cgroup.kill. No caller-selected PID or cgroup path is accepted here.
        """
        _require(self._lock.acquire(blocking=False), 'INSPECTION_ALREADY_RUNNING')
        try:
            self._boot()
            with self._fs.cgroup(self.config) as group:
                _require(group.inode() == self.config.cgroup_inode, 'INSPECTION_CGROUP_REPLACED')
                _require(group.read('cgroup.type').strip() == b'domain',
                         'INSPECTION_DOMAIN_CGROUP_REQUIRED')
                group.kill()
                self._boot()
        except InspectionBlocked:
            raise
        except (OSError, ValueError, UnicodeError, KeyError, TypeError, OverflowError) as error:
            raise InspectionBlocked('INSPECTION_KILL_OUTCOME_UNCERTAIN') from error
        finally:
            self._lock.release()

    def assert_unchanged(self, previous: InspectionSnapshot):
        _require(type(previous) is InspectionSnapshot, 'INSPECTION_BASELINE_REQUIRED')
        current = self.snapshot()
        _require(current == previous, 'INSPECTION_SNAPSHOT_CHANGED')
        return current

    def assert_empty(self):
        """Proves only current cgroup emptiness, never an owned process exit."""
        with self._frozen() as group:
            first = self._groups(group)
            _require(not any(item.members or item.threads for item in first)
                     and all(self._events(group, str(Path(item.path).relative_to(self.config.cgroup_path)))[
                         'populated'] == '0' for item in first), 'INSPECTION_CGROUP_NOT_EMPTY')
            _require(first == self._groups(group) and self._events(group)['populated'] == '0',
                     'INSPECTION_SNAPSHOT_CHANGED')
