import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys

from check_local_browser import browser_identity
from check_template_approval_browser import business_counts
from local_runtime import verify_application_deployment


ROOT = Path(__file__).resolve().parents[1]
CAPTURE = Path('/Users/liuhaoxian/.agents/skills/haonan-s000-pmp/scripts/capture_check.py')


def main():
    parser = argparse.ArgumentParser(description='检查任务当前状态页面的实际账户权限、刷新和身份清理')
    parser.add_argument('--execution')
    parser.add_argument('--run', type=Path)
    parser.add_argument('--development-only', action='store_true')
    args = parser.parse_args()
    deployment = verify_application_deployment()
    if args.run is not None:
        before = business_counts()
        if any(before.values()):
            raise ValueError('当前检查使用无企业账户，客户任务需要独立验收')
        output = args.run.resolve(strict=True)
        if not args.development_only:
            subprocess.run(['npm', 'run', 'typecheck:medical'], cwd=ROOT / 'web', check=True)
            subprocess.run(['npm', 'run', 'build:prod', '--', '--mode', 'medical', '--outDir', str(output / 'production')], cwd=ROOT / 'web', check=True)
        command = ['/usr/local/bin/node', 'tests/browser/task-detail-checks.js', str(output)]
        if args.development_only:
            command.append('--development-only')
        subprocess.run(command, cwd=ROOT, check=True)
        after = business_counts()
        if before != after or deployment != verify_application_deployment():
            raise ValueError('任务页面检查期间业务记录或部署变化')
        for mode in (('development',) if args.development_only else ('development', 'production')):
            result = json.loads((output / ('task-detail-result-' + mode + '.json')).read_bytes())
            if len(result['checks']) != 5 or result['browser_version'] != browser_identity()[0]:
                raise ValueError('任务页面实际检查范围或浏览器不一致')
        print(json.dumps({'source': 'ACTUAL_MYSQL', 'before': before, 'after': after, 'result': 'PASS'}))
        return
    if not args.execution or Path(args.execution).name != args.execution or args.execution in ('.', '..'):
        raise ValueError('执行身份必须为单一目录名称')
    output = ROOT / '.implementation-work' / ('IC-12-TASK-DETAIL-BROWSER-' + args.execution)
    inputs = ['scripts/check_task_detail_browser.py', 'scripts/check_template_approval_browser.py',
              'scripts/check_medical_session_browser.py', 'scripts/check_local_browser.py', 'scripts/local_runtime.py',
              'tests/browser/task-detail-checks.js', 'tests/browser/package.json', 'tests/browser/package-lock.json',
              'web/package.json', 'web/package-lock.json', 'web/vite.config.js', 'web/index.html', 'web/tsconfig.medical.json', 'web/.env.medical',
              'backend-design/interfaces.schema.yaml', 'backend-design/resource-details.schema.yaml', 'data-design/dataset.schema.json',
              'agent-design/artifacts.schema.json', 'contracts/typescript/src/errors.ts', 'LLD-前端.md', 'ui-delivery/tokens.json',
              '.implementation-work/runtime/application-deployment.json', '.implementation-work/runtime/service-identity.json', deployment['artifact']]
    for directory in ('web/src', 'web/vite', 'web/tests/source-file-upload', 'tests/browser/node_modules'):
        inputs.extend(str(path.relative_to(ROOT)) for path in sorted((ROOT / directory).rglob('*'))
                      if path.is_file() and ('node_modules' not in path.parts or path.suffix in ('.js', '.json', '.node')))
    for directory in ('business/medical-identity/src/main/resources', 'business/medical-persistence/src/main/resources'):
        inputs.extend(str(path.relative_to(ROOT)) for path in sorted((ROOT / directory).glob('*.schema.json')))
    inputs.extend(browser_identity()[1])
    for package in ('vue', 'element-plus', 'axios', 'ajv', 'ajv-formats', 'yaml', 'lossless-json', 'jsonc-parser', 'json-custom-numbers', 'json-canonicalize', 'vite', 'vue-tsc', 'typescript'):
        inputs.extend(str(path.relative_to(ROOT)) for path in sorted((ROOT / 'web/node_modules' / package).rglob('*')) if path.is_file())
    command = [sys.executable, str(CAPTURE), '--output-dir', str(output / 'command'), '--check-id', 'IC-12-TASK-DETAIL-BROWSER',
               '--kind', 'INTEGRATION', '--baseline-id', 'FRONT-TASK-DETAIL-01', '--cwd', str(ROOT), '--timeout', '300']
    for filename in inputs:
        command.extend(['--input', filename])
    environment = os.environ.copy()
    environment['TMPDIR'] = str(ROOT / '.implementation-work/migration-check-tmp')
    invocation = [sys.executable, 'scripts/check_task_detail_browser.py', '--run', str(output)]
    if args.development_only:
        invocation.append('--development-only')
    completed = subprocess.run([*command, '--', *invocation], cwd=ROOT, env=environment, check=False)
    if completed.returncode != 0:
        raise SystemExit(completed.returncode)
    receipt_path = output / 'command/receipt.json'
    receipt = json.loads(receipt_path.read_bytes())
    artifacts = [receipt_path, *sorted(output.glob('task-detail-result-*.json'))]
    artifacts.extend(path for path in sorted((output / 'production').rglob('*')) if path.is_file())
    for filename in artifacts:
        receipt['artifacts'].append({'path': str(filename), 'sha256': hashlib.sha256(filename.read_bytes()).hexdigest()})
    target = output / 'receipt.json'
    target.write_text(json.dumps(receipt, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps({'result': 'PASS', 'receipt': str(target.relative_to(ROOT)), 'sha256': hashlib.sha256(target.read_bytes()).hexdigest(),
                      'inputs': len(receipt['inputs']), 'artifacts': len(receipt['artifacts'])}))


if __name__ == '__main__':
    main()
