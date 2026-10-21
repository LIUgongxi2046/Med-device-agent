"""Test-only, content-addressed reuse of successful native parser JSON.

A hit is explicitly reused parse evidence, not a fresh Java execution. All Python
schema/guard assertions still run. Set MEDICAL_NATIVE_PARSE_CACHE=off for a fresh
parse and cross-check. No pickle, failed process, or malformed result is cached.
"""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
from functools import lru_cache
import importlib.metadata

ROOT = Path(__file__).resolve().parents[1]
FORMAT = 'medical-native-parser-cache-v1'


def digest(data):
    return hashlib.sha256(data).hexdigest()


@lru_cache(maxsize=4096)
def _file_digest(path, size, mtime_ns, ctime_ns, inode):
    return digest(Path(path).read_bytes())


def file_digest(path):
    path = Path(path).resolve(strict=True)
    stat = path.stat()
    return _file_digest(str(path), stat.st_size, stat.st_mtime_ns, stat.st_ctime_ns, stat.st_ino)


def identity(java, libraries, parser, source_bytes, mode):
    # Raw master/manifest bytes preserve include order, not merely table sets.
    resources = ROOT / 'business/medical-data/src/main/resources/db'
    files = [ROOT / 'data-design/core.sql', *resources.rglob('*')]
    files += list((ROOT / 'tests').glob('*.py')) + list((ROOT / 'tests').glob('*.java'))
    files += list((ROOT / 'scripts').glob('*.py'))
    java = Path(java).resolve(strict=True)
    jdk = java.parent.parent
    dependencies = [*libraries, java, jdk / 'release', jdk / 'lib/modules']
    return {'format': FORMAT, 'source_sha256': digest(source_bytes), 'mode': mode,
            'parser': str(Path(parser).resolve()), 'python': sys.version,
            'versions': {p: importlib.metadata.version(p) for p in ('sqlglot', 'sqlparse')},
            'inputs': {str(p.relative_to(ROOT)): file_digest(p) for p in sorted(set(files)) if p.is_file()},
            'dependencies': {str(Path(p).resolve()): file_digest(p) for p in dependencies},
            'java_config': {name: os.environ.get(name) for name in ('JAVA_TOOL_OPTIONS', 'JDK_JAVA_OPTIONS', '_JAVA_OPTIONS', 'LANG', 'LC_ALL')}}


def _valid(value, expected_count):
    return (isinstance(value, list) and all(isinstance(item, dict) for item in value)
            and (expected_count is None or len(value) == expected_count))


def run_json(command, *, source_bytes, libraries, parser, mode, expected_count=None, **kwargs):
    enabled = os.environ.get('MEDICAL_NATIVE_PARSE_CACHE', 'on') != 'off'
    metadata = identity(command[0], libraries, parser, source_bytes, {'name': mode, 'command': [str(x) for x in command[:-1]], 'expected_count': expected_count}) if enabled else None
    key = digest(json.dumps(metadata, sort_keys=True, separators=(',', ':')).encode()) if enabled else None
    directory = Path(os.environ.get('MEDICAL_NATIVE_PARSE_CACHE_DIR', ROOT / '.implementation-work/native-parser-cache'))
    entry = directory / (str(key) + '.json')
    if enabled and entry.is_file():
        try:
            record = json.loads(entry.read_text())
            output = record['output']
            if (record['identity'] == metadata and record['key'] == key
                    and record['output_sha256'] == digest(output.encode())):
                result = json.loads(output)
                if _valid(result, expected_count):
                    print('NATIVE_PARSE_CACHE_REUSED ' + key, file=sys.stderr)
                    return result
        except (ValueError, KeyError, TypeError, OSError):
            pass
    result = subprocess.run(command, **kwargs)
    # Caller uses check=True, but enforce this invariant independently.
    if result.returncode != 0:
        raise subprocess.CalledProcessError(result.returncode, command, result.stdout, result.stderr)
    parsed = json.loads(result.stdout)
    if not _valid(parsed, expected_count):
        raise ValueError('NATIVE_PARSER_RESULT_SHAPE_OR_COUNT_MISMATCH')
    if enabled and metadata != identity(command[0], libraries, parser, source_bytes, {'name': mode, 'command': [str(x) for x in command[:-1]], 'expected_count': expected_count}):
        raise RuntimeError('NATIVE_PARSER_INPUTS_CHANGED_DURING_EXECUTION')
    if enabled:
        directory.mkdir(parents=True, exist_ok=True)
        record = {'key': key, 'identity': metadata, 'output': result.stdout,
                  'output_sha256': digest(result.stdout.encode()), 'evidence': 'successful actual native parser execution'}
        with tempfile.NamedTemporaryFile(mode='w', dir=directory, prefix='.pending-', delete=False) as stream:
            temporary = Path(stream.name)
            json.dump(record, stream, sort_keys=True)
        temporary.replace(entry)
        print('NATIVE_PARSE_CACHE_FRESH ' + key, file=sys.stderr)
    else:
        print('NATIVE_PARSE_CACHE_DISABLED_FRESH', file=sys.stderr)
    return parsed
