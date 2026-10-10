import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys

from check_local_browser import browser_identity
from local_runtime import mysql, settings, verify_application_deployment


ROOT = Path(__file__).resolve().parents[1]
CAPTURE = Path('/Users/liuhaoxian/.agents/skills/haonan-s000-pmp/scripts/capture_check.py')


def counts():
    result = mysql("SELECT JSON_OBJECT('tenants',(SELECT COUNT(*) FROM medical_local.tenant),"
                   "'members',(SELECT COUNT(*) FROM medical_local.tenant_member),"
                   "'projects',(SELECT COUNT(*) FROM medical_local.project),"
                   "'operations',(SELECT COUNT(*) FROM medical_local.operation_record),"
                   "'registrations',(SELECT COUNT(*) FROM medical_local.platform_enterprise_registration),"
                   "'registration_audits',(SELECT COUNT(*) FROM medical_local.platform_enterprise_registration_audit),"
                   "'registration_decisions',(SELECT COUNT(*) FROM medical_local.platform_enterprise_registration_decision))", settings()['root_password'])
    value = json.loads(result.stdout)
    if any(value.values()):
        raise ValueError('当前检查需要实际无企业账户；已有客户记录时使用合法成功验收')
    return value


def main():
    parser = argparse.ArgumentParser(description='检查正式Vue入口的实际Cookie会话及拒绝行为')
    parser.add_argument('--execution')
    parser.add_argument('--run', type=Path)
    args = parser.parse_args()
    deployment = verify_application_deployment()
    if args.run is not None:
        before = counts()
        subprocess.run(['npm', 'run', 'typecheck:medical'], cwd=ROOT / 'web', check=True)
        subprocess.run(['npm', 'run', 'build:prod', '--', '--mode', 'medical', '--outDir', str(args.run.resolve(strict=True) / 'production')], cwd=ROOT / 'web', check=True)
        subprocess.run(['/usr/local/bin/node', 'tests/browser/medical-session-checks.js', str(args.run.resolve(strict=True))], cwd=ROOT, check=True)
        if before != counts() or deployment != verify_application_deployment():
            raise ValueError('检查期间实际业务状态或部署发生变化')
        print(json.dumps({'source': 'ACTUAL_MYSQL', 'before': before, 'after': counts(), 'result': 'PASS'}))
        return
    if not args.execution or Path(args.execution).name != args.execution or args.execution in ('.', '..'):
        raise ValueError('执行身份必须使用单一目录名称')
    output = ROOT / '.implementation-work' / ('IC-06-SESSION-BROWSER-' + args.execution)
    inputs = ['scripts/check_medical_session_browser.py', 'scripts/check_local_browser.py', 'scripts/local_runtime.py',
              'tests/browser/medical-session-checks.js', 'tests/browser/package.json', 'tests/browser/package-lock.json',
              'web/package.json', 'web/package-lock.json', 'web/vite.config.js', 'web/index.html', 'web/tsconfig.medical.json', 'web/.env.medical',
              'backend-design/interfaces.schema.yaml', 'contracts/typescript/src/errors.ts',
              'LLD-前端.md', 'ui-delivery/tokens.json', '.implementation-work/runtime/application-deployment.json',
              '.implementation-work/runtime/service-identity.json', deployment['artifact']]
    for directory in ('web/src', 'web/vite', 'business/medical-identity/src', 'business/medical-persistence/src', 'tests/browser/node_modules'):
        inputs.extend(str(path.relative_to(ROOT)) for path in sorted((ROOT / directory).rglob('*'))
                      if path.is_file() and ('node_modules' not in path.parts or path.suffix in ('.js', '.json', '.node')))
    inputs.extend(browser_identity()[1])
    for package in ('vue', 'element-plus', 'axios', 'ajv', 'ajv-formats', 'yaml', 'lossless-json', 'jsonc-parser', 'json-custom-numbers', 'json-canonicalize', 'vite', 'vue-tsc', 'typescript'):
        directory = ROOT / 'web/node_modules' / package
        inputs.extend(str(path.relative_to(ROOT)) for path in sorted(directory.rglob('*')) if path.is_file())
    command = [sys.executable, str(CAPTURE), '--output-dir', str(output / 'command'), '--check-id', 'IC-06-SESSION-BROWSER',
               '--kind', 'INTEGRATION', '--baseline-id', 'FRONT-SESSION-01', '--cwd', str(ROOT), '--timeout', '300']
    for filename in inputs:
        command.extend(['--input', filename])
    environment = os.environ.copy()
    environment['TMPDIR'] = str(ROOT / '.implementation-work/migration-check-tmp')
    result = subprocess.run([*command, '--', sys.executable, 'scripts/check_medical_session_browser.py', '--run', str(output)],
                            cwd=ROOT, env=environment)
    if result.returncode != 0:
        raise SystemExit(result.returncode)
    receipt_path = output / 'command/receipt.json'
    receipt = json.loads(receipt_path.read_bytes())
    artifacts = [receipt_path, output / 'medical-session-result-development.json', output / 'medical-session-result-production.json']
    artifacts.extend(path for path in sorted((output / 'production').rglob('*')) if path.is_file())
    for filename in artifacts:
        receipt['artifacts'].append({'path': str(filename), 'sha256': hashlib.sha256(filename.read_bytes()).hexdigest()})
    target = output / 'receipt.json'
    target.write_text(json.dumps(receipt, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps({'result': 'PASS', 'receipt': str(target.relative_to(ROOT)), 'sha256': hashlib.sha256(target.read_bytes()).hexdigest(),
                      'inputs': len(receipt['inputs']), 'artifacts': len(receipt['artifacts'])}))


if __name__ == '__main__':
    main()
