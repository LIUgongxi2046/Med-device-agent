"""Durable no-retry marker for one already server-admitted provider attempt.

This file is not authorization, a provider receipt, or evidence of a charge. Once a
marker exists, all later processes must reconcile the original operation, not send.
"""
from __future__ import annotations
import json
import os
from pathlib import Path
import re
import stat
from uuid import UUID
import rfc8785

class DispatchGuardError(RuntimeError):
    """Fixed content-free dispatch-state error."""

class DurableDispatchGuard:
    def __init_subclass__(cls, **kwargs):
        raise TypeError('DISPATCH_GUARD_SEALED')

    def __init__(self, directory: Path):
        self._fd = None
        try:
            fd = os.open(directory, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW | os.O_CLOEXEC)
            info = os.fstat(fd)
            if not stat.S_ISDIR(info.st_mode) or info.st_uid != os.geteuid() or stat.S_IMODE(info.st_mode) & 0o077:
                os.close(fd)
                raise DispatchGuardError('DISPATCH_DIRECTORY_NOT_PRIVATE')
            self._fd = fd
        except DispatchGuardError:
            raise
        except OSError:
            raise DispatchGuardError('DISPATCH_DIRECTORY_UNAVAILABLE') from None

    def __repr__(self):
        return '<DurableDispatchGuard redacted>'

    def close(self):
        if self._fd is not None:
            fd,self._fd=self._fd,None
            os.close(fd)

    def __enter__(self):
        return self

    def __exit__(self, *args):
        self.close()

    def _require_open(self):
        if self._fd is None:
            raise DispatchGuardError('DISPATCH_GUARD_CLOSED')
        try:
            info=os.fstat(self._fd)
        except OSError:
            raise DispatchGuardError('DISPATCH_DIRECTORY_UNAVAILABLE') from None
        if not stat.S_ISDIR(info.st_mode) or info.st_uid!=os.geteuid() or stat.S_IMODE(info.st_mode)&0o077:
            raise DispatchGuardError('DISPATCH_DIRECTORY_NOT_PRIVATE')

    @staticmethod
    def _record(invocation_id: str, attempt_id: str, receipt_id: str, canonical_request_sha256: str) -> bytes:
        try:
            if any(not isinstance(v,str) or str(UUID(v)) != v for v in (invocation_id,attempt_id,receipt_id)):
                raise ValueError()
        except (ValueError,AttributeError,TypeError):
            raise DispatchGuardError('DISPATCH_IDENTITY_INVALID') from None
        if not isinstance(canonical_request_sha256,str) or not re.fullmatch('[0-9a-f]{64}',canonical_request_sha256):
            raise DispatchGuardError('DISPATCH_REQUEST_HASH_INVALID')
        return rfc8785.dumps(dict(schema_version='1.0.0',state='DISPATCH_RESERVED_NO_RETRY',invocation_id=invocation_id,
            dispatch_attempt_id=attempt_id,receipt_id=receipt_id,canonical_request_body_sha256=canonical_request_sha256))

    def claim(self, invocation_id: str, attempt_id: str, receipt_id: str, canonical_request_sha256: str) -> None:
        raw=self._record(invocation_id,attempt_id,receipt_id,canonical_request_sha256)
        self._require_open()
        fd=None
        try:
            fd=os.open('dispatch-'+attempt_id+'.json',os.O_WRONLY|os.O_CREAT|os.O_EXCL|os.O_NOFOLLOW|os.O_CLOEXEC,0o600,dir_fd=self._fd)
            written=0
            while written<len(raw):
                count=os.write(fd,raw[written:])
                if count<=0:
                    raise OSError('zero write')
                written+=count
            os.fsync(fd)
            os.fsync(self._fd)
        except FileExistsError:
            raise DispatchGuardError('DISPATCH_ALREADY_RESERVED_RECONCILE_ONLY') from None
        except OSError:
            # Never delete/repair an uncertain marker. No subsequent send may reinterpret it.
            raise DispatchGuardError('DISPATCH_MARKER_UNCERTAIN_RECONCILE_ONLY') from None
        finally:
            if fd is not None:
                os.close(fd)

    def read(self, invocation_id: str, attempt_id: str, receipt_id: str, canonical_request_sha256: str) -> dict | None:
        expected=self._record(invocation_id,attempt_id,receipt_id,canonical_request_sha256)
        self._require_open()
        fd=None
        try:
            fd=os.open('dispatch-'+attempt_id+'.json',os.O_RDONLY|os.O_NOFOLLOW|os.O_CLOEXEC,dir_fd=self._fd)
            info=os.fstat(fd)
            if not stat.S_ISREG(info.st_mode) or info.st_uid!=os.geteuid() or stat.S_IMODE(info.st_mode)&0o077 or info.st_nlink!=1 or info.st_size>4096:
                raise DispatchGuardError('DISPATCH_MARKER_INVALID')
            data=os.read(fd,4097)
            if data!=expected:
                raise DispatchGuardError('DISPATCH_MARKER_IDENTITY_MISMATCH')
            return json.loads(data)
        except FileNotFoundError:
            return None
        except OSError:
            raise DispatchGuardError('DISPATCH_MARKER_UNAVAILABLE') from None
        finally:
            if fd is not None:
                os.close(fd)
