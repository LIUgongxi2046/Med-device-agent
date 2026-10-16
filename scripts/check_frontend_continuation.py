import argparse
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import sys

from check_local_browser import browser_identity


ROOT = Path(__file__).resolve().parents[1]
CAPTURE = Path('/Users/liuhaoxian/.agents/skills/haonan-s000-pmp/scripts/capture_check.py')
SUITES = {
    'document-selection': ('tests/browser/document-selection-checks.js', 'FRONT-DOCUMENT-SELECTION-20261013'),
    'template-fields': ('tests/browser/template-fields-checks.js', 'FRONT-TEMPLATE-FIELDS-20261013'),
    'source-rights': ('tests/browser/source-rights-schema-checks.js', 'FRONT-SOURCE-RIGHTS-SCHEMA-20261014'),
}


def main():
    parser = argparse.ArgumentParser(description='验证剩余前端交互与完整医疗制品，并固定当前输入及产物身份')
    parser.add_argument('--suite', choices=(*SUITES, 'release'), required=True)
    parser.add_argument('--execution')
    parser.add_argument('--execute-checks', action='store_true')
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    temporary = ROOT / '.implementation-work/frontend-continuation-tmp'
    temporary.mkdir(parents=True, exist_ok=True)
    environment = os.environ.copy()
    environment['TMPDIR'] = str(temporary)
    if args.execute_checks:
        if args.execution is not None or args.output is None:
            raise ValueError('运行检查需要独立输出目录')
        output = args.output.resolve(strict=True)
        if not output.is_relative_to(ROOT / '.implementation-work'):
            raise ValueError('检查产物必须保存在项目专用目录')
        if args.suite in SUITES:
            subprocess.run(['node', SUITES[args.suite][0]], cwd=ROOT, env=environment, check=True)
        else:
            subprocess.run(['npm', 'run', 'typecheck:medical'], cwd=ROOT / 'web', env=environment, check=True)
            for script in ('check_task_plan_frontend.mjs', 'check_document_frontend.mjs', 'check_source_upload_frontend.mjs', 'check_source_index_frontend.mjs', 'check_source_exact_frontend.mjs'):
                subprocess.run(['node', str(ROOT / 'scripts' / script)], cwd=temporary, env=environment, check=True)
            subprocess.run(['npm', 'run', 'build:prod', '--', '--mode', 'medical', '--outDir', str(output / 'production')],
                           cwd=ROOT / 'web', env=environment, check=True)
        return
    if args.output is not None or not args.execution or Path(args.execution).name != args.execution or args.execution in ('.', '..'):
        raise ValueError('登记回执需要单一执行身份')
    output = ROOT / '.implementation-work' / ('IC-FRONT-CONTINUE-' + args.suite.upper() + '-' + args.execution)
    output.mkdir(parents=True, exist_ok=False)
    inputs = {ROOT / name for name in ('scripts/check_frontend_continuation.py', 'scripts/check_local_browser.py',
        'web/package.json', 'web/package-lock.json', 'web/tsconfig.medical.json', 'web/vite.config.js',
        'web/index.html', 'web/.env.medical', 'contracts/catalogue.json')}
    inputs.add(CAPTURE)
    for directory in ('web/src', 'web/vite', 'web/public', 'contracts/typescript/src'):
        inputs.update(path for path in (ROOT / directory).rglob('*') if path.is_file())
    for directory in ('business/medical-identity/src/main/resources', 'business/medical-persistence/src/main/resources'):
        inputs.update(path for path in (ROOT / directory).rglob('*.schema.json') if path.is_file())
    for package in ('vue', 'element-plus', 'axios', 'ajv', 'ajv-formats', 'json-canonicalize', 'jsonc-parser',
                    'lossless-json', 'json-custom-numbers', 'vite', 'vue-tsc', 'typescript'):
        inputs.update(path for path in (ROOT / 'web/node_modules' / package).rglob('*') if path.is_file())
    for entry in json.loads((ROOT / 'contracts/catalogue.json').read_bytes())['schemas']:
        inputs.add(ROOT / entry['path'])
    if args.suite in SUITES:
        script, fixture = SUITES[args.suite]
        inputs.add(ROOT / script)
        inputs.update(path for path in (ROOT / 'web/tests' / args.suite).rglob('*') if path.is_file())
        inputs.update(ROOT / name for name in ('tests/browser/package.json', 'tests/browser/package-lock.json'))
        for package in ('playwright', 'playwright-core'):
            inputs.update(path for path in (ROOT / 'tests/browser/node_modules' / package).rglob('*') if path.is_file())
        inputs.update(Path(name) for name in browser_identity()[1])
        artifact_directory = ROOT / '.implementation-work' / fixture
    else:
        inputs.update(ROOT / 'scripts' / name for name in ('check_task_plan_frontend.mjs', 'check_document_frontend.mjs', 'check_source_upload_frontend.mjs', 'check_source_index_frontend.mjs', 'check_source_exact_frontend.mjs'))
        inputs.update(path for path in (ROOT / 'web/tests').rglob('*') if path.is_file())
        artifact_directory = output
    specification = importlib.util.spec_from_file_location('frontend_capture_check', CAPTURE)
    if specification is None or specification.loader is None:
        raise ValueError('当前检查回执工具无法加载')
    capture = importlib.util.module_from_spec(specification)
    specification.loader.exec_module(capture)
    capture_args = argparse.Namespace(output_dir=output / 'command', check_id='IC-FRONT-CONTINUE', kind='INTEGRATION',
        baseline_id='FE-11-16-36-20261013', cwd=ROOT, timeout=3600, input=[str(filename) for filename in sorted(inputs)],
        artifact=[], command=[sys.executable, 'scripts/check_frontend_continuation.py', '--suite', args.suite,
            '--execute-checks', '--output', str(output)])
    result = capture.run_check(capture_args)
    if result != 0:
        raise SystemExit(result)
    receipt_path = output / 'command/receipt.json'
    receipt = json.loads(receipt_path.read_bytes())
    if receipt['exit_code'] != 0 or not receipt['inputs_unchanged']:
        raise ValueError('当前输入与完整检查结果没有保持一致')
    if args.suite in SUITES:
        checks_path = artifact_directory / 'checks.json'
        checks = json.loads(checks_path.read_bytes())
        count = {'document-selection': 12, 'template-fields': 10, 'source-rights': 12}[args.suite]
        if checks['data_kind'] != 'SIMULATION' or checks['visual'] != 'NOT_RUN' or len(checks['checks']) != count:
            raise ValueError('浏览器检查范围或输入身份不完整')
        if any(check['result'] != 'PASS' for check in checks['checks']):
            raise ValueError('浏览器检查没有全部通过')
        receipt['artifacts'].append({'path': str(checks_path), 'sha256': hashlib.sha256(checks_path.read_bytes()).hexdigest()})
    production = artifact_directory / 'production'
    if not (production / 'index.html').is_file() and args.suite == 'release':
        raise ValueError('完整医疗构建缺少正式入口')
    for filename in sorted(production.rglob('*')):
        if filename.is_file():
            receipt['artifacts'].append({'path': str(filename), 'sha256': hashlib.sha256(filename.read_bytes()).hexdigest()})
    target = output / 'receipt.json'
    target.write_text(json.dumps(receipt, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps({'result': 'PASS', 'suite': args.suite, 'receipt': str(target.relative_to(ROOT)),
        'sha256': hashlib.sha256(target.read_bytes()).hexdigest(), 'inputs': len(receipt['inputs']), 'artifacts': len(receipt['artifacts'])}))


if __name__ == '__main__':
    main()
