import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys

from check_local_browser import browser_identity
from check_medical_session_browser import counts
from local_runtime import mysql, settings, verify_application_deployment


ROOT = Path(__file__).resolve().parents[1]
CAPTURE = Path('/Users/liuhaoxian/.agents/skills/haonan-s000-pmp/scripts/capture_check.py')


def business_counts():
    result = counts()
    query = "SELECT JSON_OBJECT('collections',(SELECT COUNT(*) FROM medical_local.collection_head)," \
            "'collection_versions',(SELECT COUNT(*) FROM medical_local.collection_version)," \
            "'collection_members',(SELECT COUNT(*) FROM medical_local.collection_member)," \
            "'events',(SELECT COUNT(*) FROM medical_local.outbox_event))"
    result.update(json.loads(mysql(query, settings()['root_password']).stdout))
    if any(result.values()):
        raise ValueError('当前所有者检查需要无企业状态，获授权业务分别执行成功验收')
    return result


def main():
    parser = argparse.ArgumentParser(description='核查正式资料集页面及当前实际身份，不创建客户数据')
    parser.add_argument('--execution')
    parser.add_argument('--run', type=Path)
    parser.add_argument('--red', action='store_true')
    args = parser.parse_args()
    deployment = verify_application_deployment()
    if args.run is not None:
        output = args.run.resolve(strict=True)
        before = business_counts()
        if not args.red:
            subprocess.run(['npm', 'run', 'typecheck:medical'], cwd=ROOT / 'web', check=True)
            subprocess.run(['npm', 'run', 'build:prod', '--', '--mode', 'medical', '--outDir', str(output / 'production')], cwd=ROOT / 'web', check=True)
        subprocess.run(['/usr/local/bin/node', 'tests/browser/collection-checks.js', str(output)], cwd=ROOT, check=True)
        if before != business_counts() or deployment != verify_application_deployment():
            raise ValueError('检查期间业务数据或者当前部署发生变化')
        for mode in ('development', 'production'):
            result = json.loads((output / ('collection-result-' + mode + '.json')).read_bytes())
            if result['browser_version'] != browser_identity()[0] or len(result['checks']) != 5:
                raise ValueError('实际浏览器或者当前检查范围不一致')
        print(json.dumps({'source': 'ACTUAL_MYSQL', 'before': before, 'after': business_counts(), 'result': 'PASS'}))
        return
    if not args.execution or Path(args.execution).name != args.execution or args.execution in ('.', '..'):
        raise ValueError('执行身份必须使用单一目录名称')
    output = ROOT / '.implementation-work' / ('IC-10-COLLECTION-BROWSER-' + args.execution)
    inputs = ['scripts/check_collection_browser.py', 'scripts/check_medical_session_browser.py', 'scripts/check_local_browser.py',
              'scripts/local_runtime.py', 'tests/browser/collection-checks.js', 'tests/browser/package.json', 'tests/browser/package-lock.json',
              'web/package.json', 'web/package-lock.json', 'web/vite.config.js', 'web/index.html', 'web/tsconfig.medical.json', 'web/.env.medical',
              'backend-design/interfaces.schema.yaml', 'contracts/typescript/src/errors.ts', 'LLD-前端.md', '界面规范.md',
              '.implementation-work/runtime/application-deployment.json', '.implementation-work/runtime/service-identity.json', deployment['artifact']]
    for directory in ('web/src', 'web/vite', 'business/medical-identity/src', 'business/medical-persistence/src', 'tests/browser/node_modules'):
        inputs.extend(str(path.relative_to(ROOT)) for path in sorted((ROOT / directory).rglob('*'))
                      if path.is_file() and ('node_modules' not in path.parts or path.suffix in ('.js', '.json', '.node')))
    inputs.extend(browser_identity()[1])
    for package in ('vue', 'element-plus', 'axios', 'ajv', 'ajv-formats', 'yaml', 'lossless-json', 'jsonc-parser', 'json-custom-numbers', 'json-canonicalize', 'vite', 'vue-tsc', 'typescript'):
        inputs.extend(str(path.relative_to(ROOT)) for path in sorted((ROOT / 'web/node_modules' / package).rglob('*')) if path.is_file())
    command = [sys.executable, str(CAPTURE), '--output-dir', str(output / 'command'), '--check-id', 'IC-10-COLLECTION-BROWSER',
               '--kind', 'INTEGRATION', '--baseline-id', 'FRONT-COLLECTION-VERSIONS-01', '--cwd', str(ROOT), '--timeout', '300']
    for filename in inputs:
        command.extend(['--input', filename])
    environment = os.environ.copy(); environment['TMPDIR'] = str(ROOT / '.implementation-work/migration-check-tmp')
    action = [sys.executable, 'scripts/check_collection_browser.py', '--run', str(output)]
    if args.red:
        action.append('--red')
    result = subprocess.run([*command, '--', *action], cwd=ROOT, env=environment)
    if result.returncode != 0:
        raise SystemExit(result.returncode)
    receipt_path = output / 'command/receipt.json'
    receipt = json.loads(receipt_path.read_bytes())
    artifacts = [receipt_path, output / 'collection-result-development.json', output / 'collection-result-production.json']
    artifacts.extend(path for path in sorted((output / 'production').rglob('*')) if path.is_file())
    for filename in artifacts:
        receipt['artifacts'].append({'path': str(filename), 'sha256': hashlib.sha256(filename.read_bytes()).hexdigest()})
    target = output / 'receipt.json'; target.write_text(json.dumps(receipt, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps({'result': 'PASS', 'receipt': str(target.relative_to(ROOT)), 'sha256': hashlib.sha256(target.read_bytes()).hexdigest(),
                      'inputs': len(receipt['inputs']), 'artifacts': len(receipt['artifacts'])}))


if __name__ == '__main__':
    main()
