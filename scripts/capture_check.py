"""Capture actual command evidence; missing inputs/artifacts and changed inputs fail closed.

This records source/command evidence only, never claims database or deployment
verification unless the supplied command actually performs those checks.
"""
import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import signal
import subprocess
import sys


def timestamp():
    return datetime.now(timezone.utc).isoformat()


def snapshot(paths, cwd):
    result = []
    for value in sorted(set(map(str, paths))):
        path = Path(value)
        path = (cwd / path if not path.is_absolute() else path).resolve(strict=True)
        if not path.is_file():
            raise ValueError('EVIDENCE_FILE_REQUIRED: ' + str(path))
        result.append({'path': str(path), 'sha256': hashlib.sha256(path.read_bytes()).hexdigest()})
    return sorted({item['path']: item for item in result}.values(), key=lambda item: item['path'])


def run_check(args):
    cwd = Path(args.cwd).resolve(strict=True)
    output = Path(args.output_dir)
    output = (cwd / output if not output.is_absolute() else output).resolve()
    output.mkdir(parents=True, exist_ok=True)
    # Refuse re-use, including interrupted or failed executions.
    with (output / 'execution.lock').open('x') as lock:
        lock.write(timestamp())
    command = list(args.command)
    if command and command[0] == '--':
        command = command[1:]
    if not command or args.timeout <= 0:
        raise ValueError('COMMAND_AND_POSITIVE_TIMEOUT_REQUIRED')
    inputs = snapshot([*args.input, Path(__file__), Path(__file__).with_name('check_support.py')], cwd)
    receipt = {'captured_by': 'repository-capture-check/v1', 'check_id': args.check_id,
        'kind': args.kind, 'baseline_id': args.baseline_id, 'cwd': str(cwd),
        'command': command, 'started_at': timestamp(), 'inputs': inputs,
        'artifacts': [], 'exit_code': 1, 'inputs_unchanged': False}
    result = 1
    try:
        with (output / 'stdout.log').open('wb') as stdout, (output / 'stderr.log').open('wb') as stderr:
            process = subprocess.Popen(command, cwd=cwd, stdout=stdout, stderr=stderr, start_new_session=True)
            try:
                result = process.wait(timeout=args.timeout)
            except subprocess.TimeoutExpired:
                os.killpg(process.pid, signal.SIGKILL)
                process.wait()
                result = 124
                receipt['error'] = 'COMMAND_TIMEOUT'
        receipt['command_exit_code'] = result
        receipt['inputs_unchanged'] = snapshot([item['path'] for item in inputs], cwd) == inputs
        receipt['artifacts'] = snapshot(args.artifact, cwd)
        if not receipt['inputs_unchanged']:
            receipt['error'] = 'INPUTS_CHANGED_DURING_CHECK'
            result = result or 1
    except Exception as error:
        receipt['error'] = type(error).__name__ + ': ' + str(error)
        result = result or 1
    receipt['exit_code'] = result
    receipt['ended_at'] = timestamp()
    with (output / 'receipt.json').open('x', encoding='utf-8') as target:
        json.dump(receipt, target, ensure_ascii=False, indent=2)
        target.write('\n')
    print(json.dumps({'check_id': args.check_id, 'exit_code': result, 'receipt': str(output / 'receipt.json')}))
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('output-dir', 'check-id', 'kind', 'baseline-id', 'cwd'):
        parser.add_argument('--' + name, required=True)
    parser.add_argument('--timeout', type=float, required=True)
    parser.add_argument('--input', action='append', default=[])
    parser.add_argument('--artifact', action='append', default=[])
    parser.add_argument('command', nargs=argparse.REMAINDER)
    return run_check(parser.parse_args())


if __name__ == '__main__':
    sys.exit(main())
