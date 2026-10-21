import argparse
import hashlib
from importlib.metadata import version
from importlib.resources import files
import json
import os
from pathlib import Path
import subprocess
import sys
import unittest


ROOT = Path(__file__).resolve().parents[1]
from check_support import capture_check_path
CAPTURE = capture_check_path()


def main():
    parser = argparse.ArgumentParser(description='核验固定正文的实际DOCX、PDF及内部Markdown文件')
    parser.add_argument('--execution')
    parser.add_argument('--run', type=Path)
    parser.add_argument('--installed', action='store_true')
    args = parser.parse_args()
    if args.run is not None:
        output = args.run.resolve(strict=True)
        (output / 'files').mkdir(mode=0o700)
        package_files = {}
        if args.installed:
            package = files('medical_document')
            for source in sorted((ROOT / 'document/src/medical_document').glob('*.py')):
                actual = package.joinpath(source.name).read_bytes()
                if actual != source.read_bytes():
                    raise ValueError('已安装文档组件与当前源码不一致')
                package_files[source.name] = hashlib.sha256(actual).hexdigest()
        result = unittest.TextTestRunner(verbosity=2).run(unittest.defaultTestLoader.discover(str(ROOT / 'tests'), pattern='test_document_export.py'))
        values = {'input_data_kind': 'SIMULATION', 'tests_run': result.testsRun, 'failures': len(result.failures),
            'errors': len(result.errors), 'skipped': len(result.skipped), 'successful': result.wasSuccessful(),
            'packages': {name: version(name) for name in ('python-docx', 'pypdf', 'latex2mathml', 'mathml2omml', 'fonttools', 'lxml')},
            'customer_acceptance_verified': False, 'public_export_verified': False, 'visual_check': 'NOT_RUN',
            'installed_package_verified': args.installed, 'package_files': package_files,
            'files': {str(path.relative_to(ROOT)): {'sha256': hashlib.sha256(path.read_bytes()).hexdigest(), 'size_bytes': path.stat().st_size}
                for path in sorted((output / 'files').rglob('*')) if path.is_file() and path.suffix in {'.docx', '.pdf', '.md', '.png', '.json'}}}
        (output / 'export-result.json').write_text(json.dumps(values, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
        raise SystemExit(0 if result.wasSuccessful() else 1)
    if not args.execution or Path(args.execution).name != args.execution or args.execution in {'.', '..'}:
        raise ValueError('执行身份必须使用单一目录名称')
    output = ROOT / '.implementation-work' / ('IC-26-EXPORT-' + args.execution)
    if output.exists():
        raise FileExistsError(output)
    temporary = ROOT / '.implementation-work/export-temp'
    temporary.mkdir(parents=True, exist_ok=True)
    environment = os.environ.copy()
    environment.update({'TMPDIR': str(temporary), 'PYTHONPATH': '' if args.installed else str(ROOT / 'document/src') + ':' + str(ROOT / 'contracts/python/src'),
        'MEDICAL_EXPORT_TEST_ROOT': str(output / 'files')})
    inputs = {'scripts/check_document_export.py', 'tests/test_document_export.py', 'document/pyproject.toml',
        'contracts/catalogue.json', 'contracts/extensions/evaluation-results.catalogue.json', 'business/medical-persistence/src/main/resources/evaluation-results.schema.json', 'LLD-后端.md', 'LLD-数据.md'}
    for directory in ('document/src', 'contracts/python/src'):
        inputs.update(str(path.relative_to(ROOT)) for path in (ROOT / directory).rglob('*.py'))
    inputs.update(str(path.relative_to(ROOT)) for path in (ROOT / 'contracts').glob('*.json'))
    command = [sys.executable, str(CAPTURE), '--output-dir', str(output), '--check-id', 'IC-26-EXPORT', '--kind', 'INTEGRATION',
        '--baseline-id', 'DOCUMENT-FIXED-EXPORT-01', '--cwd', str(ROOT), '--timeout', '180']
    for value in sorted(inputs):
        command.extend(('--input', value))
    command.extend(('--artifact', str(output / 'export-result.json'), '--', sys.executable, str(Path(__file__).resolve()), '--run', str(output)))
    if args.installed:
        command.append('--installed')
    result = subprocess.run(command, cwd=ROOT, env=environment)
    if result.returncode != 0:
        raise SystemExit(result.returncode)
    receipt = output / 'receipt.json'
    print(json.dumps({'receipt_sha256': hashlib.sha256(receipt.read_bytes()).hexdigest(), 'input_data_kind': 'SIMULATION'}))
    raise SystemExit(result.returncode)


if __name__ == '__main__':
    main()
