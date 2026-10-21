"""Pinned standalone entrypoint: python -I <this-file> --config <path> --package-root <path>.

The Java process owner verifies the executable, this entrypoint and complete application
package manifest before launch. No token, provider content or credential belongs in argv.
This file uses only the standard library before selecting the exact package root.
"""
from __future__ import annotations

import asyncio
import logging
import os
from pathlib import Path
import signal
import stat
import sys

FAILURE = b'TASK_GATEWAY_STOPPED_REVIEW_ORIGINAL_OPERATION\n'


def _timeout(signum, frame):
    raise TimeoutError('TASK_GATEWAY_TIME_BOUND')


def _paths(argv):
    if not sys.flags.isolated or len(argv) != 5 or argv[1] != '--config' or argv[3] != '--package-root':
        raise ValueError('TASK_GATEWAY_ARGV_INVALID')
    if sum(len(arg.encode('utf-8')) + 1 for arg in argv) > 8192:
        raise ValueError('TASK_GATEWAY_ARGV_BOUND')
    config, root = Path(argv[2]), Path(argv[4])
    for path in (config, root):
        if not path.is_absolute() or path.resolve(strict=True) != path:
            raise ValueError('TASK_GATEWAY_ABSOLUTE_NONSYMLINK_PATH_REQUIRED')
    if not stat.S_ISDIR(root.stat().st_mode):
        raise ValueError('TASK_GATEWAY_PACKAGE_ROOT_REQUIRED')
    for name in ('medical_model_gateway', 'medical_contracts'):
        package = root / name
        if not package.is_dir() or package.resolve(strict=True) != package or not (package / '__init__.py').is_file():
            raise ValueError('TASK_GATEWAY_PACKAGE_ROOT_REQUIRED')
    return config, root


def main() -> int:
    output_fd, error_fd = os.dup(1), os.dup(2)
    try:
        # Never forward imported-library logs, tracebacks, prints, token or model bytes.
        # The private journal is the sole destination for original model content.
        with open(os.devnull, 'wb', buffering=0) as sink:
            os.dup2(sink.fileno(), 1)
            os.dup2(sink.fileno(), 2)
            logging.disable(logging.CRITICAL)
            signal.signal(signal.SIGALRM, _timeout)
            signal.alarm(10)
            config, root = _paths(sys.argv)
            sys.dont_write_bytecode = True
            sys.path.insert(0, str(root))
            from medical_model_gateway.task_gateway import run
            raw = sys.stdin.buffer.read(65537)
            signal.alarm(310)
            output = asyncio.run(run(config, raw))
            if not isinstance(output, bytes) or not 0 < len(output) <= 4096:
                raise ValueError('TASK_GATEWAY_OUTPUT_BOUND')
            os.write(output_fd, output)
            signal.alarm(0)
        return 0
    except BaseException:
        signal.alarm(0)
        os.write(error_fd, FAILURE)
        return 1
    finally:
        os.close(output_fd)
        os.close(error_fd)


if __name__ == '__main__':
    raise SystemExit(main())
