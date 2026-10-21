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
from check_support import capture_check_path
CAPTURE = capture_check_path()


def business_counts():
    value = counts()
    result = mysql("SELECT JSON_OBJECT('templates',(SELECT COUNT(*) FROM medical_local.template_version),"
                   "'decisions',(SELECT COUNT(*) FROM medical_local.template_approval_decision),"
                   "'events',(SELECT COUNT(*) FROM medical_local.outbox_event))", settings()['root_password'])
    value.update(json.loads(result.stdout))
    if any(value.values()):
        raise ValueError('当前检查需要实际无企业所有者；客户记录需要对应成功验收')
    return value


def main():
    parser = argparse.ArgumentParser(description='验证正式模板创建和审查页面、当前企业权限及无业务写入')
    parser.add_argument('--execution')
    parser.add_argument('--run', type=Path)
    args = parser.parse_args()
    deployment = verify_application_deployment()
    if args.run is not None:
        before = business_counts()
        subprocess.run(['npm', 'run', 'typecheck:medical'], cwd=ROOT / 'web', check=True)
        subprocess.run(['npm', 'run', 'build:prod', '--', '--mode', 'medical', '--outDir', str(args.run.resolve(strict=True) / 'production')], cwd=ROOT / 'web', check=True)
        subprocess.run(['/usr/local/bin/node', 'tests/browser/template-approval-checks.js', str(args.run.resolve(strict=True))], cwd=ROOT, check=True)
        if before != business_counts() or deployment != verify_application_deployment():
            raise ValueError('模板页面检查期间业务数据或部署发生变化')
        for mode in ('development', 'production'):
            result = json.loads((args.run / ('template-approval-result-' + mode + '.json')).read_bytes())
            if result['browser_version'] != browser_identity()[0] or len(result['checks']) != 7:
                raise ValueError('实际浏览器或模板页面检查范围不一致')
        print(json.dumps({'source': 'ACTUAL_MYSQL', 'before': before, 'after': business_counts(), 'result': 'PASS'}))
        return
    if not args.execution or Path(args.execution).name != args.execution or args.execution in ('.', '..'):
        raise ValueError('执行身份必须为单一目录名称')
    output = ROOT / '.implementation-work' / ('IC-11-APPROVAL-BROWSER-' + args.execution)
    inputs = ['scripts/check_template_approval_browser.py', 'scripts/check_medical_session_browser.py', 'scripts/check_local_browser.py',
              'scripts/local_runtime.py', 'tests/browser/template-approval-checks.js', 'tests/browser/package.json', 'tests/browser/package-lock.json',
              'web/package.json', 'web/package-lock.json', 'web/vite.config.js', 'web/index.html', 'web/tsconfig.medical.json', 'web/.env.medical',
              'backend-design/interfaces.schema.yaml', 'backend-design/接口补充设计.md', 'contracts/typescript/src/errors.ts',
              'LLD-前端.md', 'ui-delivery/tokens.json', '.implementation-work/runtime/application-deployment.json',
              '.implementation-work/runtime/service-identity.json', deployment['artifact']]
    for directory in ('web/src', 'web/vite', 'business/medical-identity/src', 'business/medical-persistence/src', 'tests/browser/node_modules'):
        inputs.extend(str(path.relative_to(ROOT)) for path in sorted((ROOT / directory).rglob('*'))
                      if path.is_file() and ('node_modules' not in path.parts or path.suffix in ('.js', '.json', '.node')))
    inputs.extend(browser_identity()[1])
    for package in ('vue', 'element-plus', 'axios', 'ajv', 'ajv-formats', 'yaml', 'lossless-json', 'jsonc-parser', 'json-custom-numbers', 'json-canonicalize', 'vite', 'vue-tsc', 'typescript'):
        directory = ROOT / 'web/node_modules' / package
        inputs.extend(str(path.relative_to(ROOT)) for path in sorted(directory.rglob('*')) if path.is_file())
    command = [sys.executable, str(CAPTURE), '--output-dir', str(output / 'command'), '--check-id', 'IC-11-APPROVAL-BROWSER',
               '--kind', 'INTEGRATION', '--baseline-id', 'FRONT-TEMPLATE-APPROVAL-01', '--cwd', str(ROOT), '--timeout', '300']
    for filename in inputs:
        command.extend(['--input', filename])
    environment = os.environ.copy()
    environment['TMPDIR'] = str(ROOT / '.implementation-work/migration-check-tmp')
    result = subprocess.run([*command, '--', sys.executable, 'scripts/check_template_approval_browser.py', '--run', str(output)],
                            cwd=ROOT, env=environment)
    if result.returncode != 0:
        raise SystemExit(result.returncode)
    receipt_path = output / 'command/receipt.json'
    receipt = json.loads(receipt_path.read_bytes())
    artifacts = [receipt_path, output / 'template-approval-result-development.json', output / 'template-approval-result-production.json']
    artifacts.extend(path for path in sorted((output / 'production').rglob('*')) if path.is_file())
    for filename in artifacts:
        receipt['artifacts'].append({'path': str(filename), 'sha256': hashlib.sha256(filename.read_bytes()).hexdigest()})
    target = output / 'receipt.json'
    target.write_text(json.dumps(receipt, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps({'result': 'PASS', 'receipt': str(target.relative_to(ROOT)), 'sha256': hashlib.sha256(target.read_bytes()).hexdigest(),
                      'inputs': len(receipt['inputs']), 'artifacts': len(receipt['artifacts'])}))


if __name__ == '__main__':
    main()
