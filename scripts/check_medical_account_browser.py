import argparse
import fcntl
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import uuid

from check_local_browser import browser_identity
from check_medical_session_browser import counts
from local_runtime import mysql, settings, verify_application_deployment


ROOT = Path(__file__).resolve().parents[1]
CAPTURE = Path('/Users/liuhaoxian/.agents/skills/haonan-s000-pmp/scripts/capture_check.py')


def verify_mutations(output):
    mutations = []
    for mode in ('development', 'production'):
        path = output / ('medical-account-result-' + mode + '.json')
        result = json.loads(path.read_bytes())
        if result['browser_version'] != browser_identity()[0] or len(result['checks']) != 6:
            raise ValueError('本人账户浏览器身份或者断言范围不匹配')
        profile = result['checks'][1]
        if profile['name'] != 'native_profile_save_offline_refresh_replay_and_reread':
            raise ValueError('原个人资料操作检查缺少')
        profile_actor = str(uuid.UUID(profile['actor']))
        profile_key = str(uuid.UUID(profile['key']))
        profile_operation = str(uuid.UUID(profile['envelope']['data']['operation_id']))
        profile_persisted = json.loads(mysql("SELECT JSON_OBJECT('count',COUNT(*),'response',MIN(CAST(response_ref AS CHAR)),"
            "'audits',(SELECT COUNT(*) FROM medical_local.platform_local_audit WHERE operation_id='" + profile_operation + "')) "
            "FROM medical_local.platform_local_operation WHERE subject_id='" + profile_actor + "' AND idempotency_key='" + profile_key + "' "
            "AND operation_name='LOCAL-07' AND operation_id='" + profile_operation + "'", settings()['root_password']).stdout)
        if (profile_persisted['count'] != 1 or profile_persisted['audits'] != 1
                or json.loads(profile_persisted['response']) != profile['envelope']
                or profile['envelope']['data']['reauthentication_required'] is not False
                or profile['envelope']['data']['account']['revision'] != str(int(profile['previous_revision']) + 1)):
            raise ValueError('原个人资料请求必须对应唯一操作、原响应、版本及同步审计')
        result['profile_database'] = {'source': 'ACTUAL_MYSQL', 'operation_count': 1, 'audit_count': 1, 'response_matches': True}
        check = result['checks'][-1]
        actor = str(uuid.UUID(check['actor']))
        key = str(uuid.UUID(check['key']))
        operation = str(uuid.UUID(check['envelope']['data']['operation_id']))
        persisted = json.loads(mysql("SELECT JSON_OBJECT('count',COUNT(*),'response',MIN(CAST(response_ref AS CHAR)),"
            "'audit',(SELECT resource_ref FROM medical_local.platform_local_audit WHERE operation_id='" + operation + "'),"
            "'audits',(SELECT COUNT(*) FROM medical_local.platform_local_audit WHERE operation_id='" + operation + "')) "
            "FROM medical_local.platform_local_operation WHERE subject_id='" + actor + "' AND idempotency_key='" + key + "' "
            "AND operation_name='LOCAL-06' AND operation_id='" + operation + "'", settings()['root_password']).stdout)
        if persisted['count'] != 1 or persisted['audits'] != 1 or json.loads(persisted['response']) != check['envelope']:
            raise ValueError('原请求重放必须对应唯一数据库操作、原响应及审计')
        account = check['envelope']['data']['account']
        mutations.append(account)
        audit = {'schema_version': '1.0.0', 'resource_kind': 'LOCAL_ACCOUNT', 'user_id': actor,
                 'revision': account['revision'], 'credential_revision': account['credential_revision'], 'status': 'ACTIVE'}
        if persisted['audit'] != audit or account['revision'] != str(int(check['previous_revision']) + 1):
            raise ValueError('浏览器原响应、账户版本及审计字段不一致')
        result['database'] = {'source': 'ACTUAL_MYSQL', 'operation_count': persisted['count'],
                              'audit_count': persisted['audits'], 'response_matches': True}
        path.write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n')
    if mutations[0]['user_id'] != mutations[1]['user_id'] or int(mutations[1]['credential_revision']) != int(mutations[0]['credential_revision']) + 1:
        raise ValueError('两种入口必须分别更新同一真实账户的凭据版本一次')
    actor = str(uuid.UUID(mutations[-1]['user_id']))
    current = json.loads(mysql("SELECT JSON_OBJECT('credential_revision',CAST(a.credential_revision AS CHAR),'status',a.status,"
        "'revision',CAST(a.revision AS CHAR),'failed_attempts',a.failed_attempts,'locked_until',a.locked_until,"
        "'authentication_sessions',(SELECT COUNT(*) FROM medical_local.platform_local_session s "
        "WHERE s.user_id=a.user_id AND s.credential_revision=a.credential_revision)) "
        "FROM medical_local.platform_local_account a WHERE a.user_id='" + actor + "'", settings()['root_password']).stdout)
    # 每次真实登录创建绑定并增加账户版本，完整关联检查包含后续HTTP登录。
    sessions = current['authentication_sessions']
    if (current['status'] != 'ACTIVE' or current['credential_revision'] != mutations[-1]['credential_revision']
            or sessions < 1 or current['failed_attempts'] != 0 or current['locked_until'] is not None
            or int(current['revision']) != int(mutations[-1]['revision']) + sessions):
        raise ValueError('当前账户、凭据及实际登录绑定的完整版本变化不一致')
    target = output / 'medical-account-result-production.json'
    result = json.loads(target.read_bytes())
    result['database']['current_account'] = current
    result['database']['authentication_revisions_match'] = True
    target.write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n')


def main():
    parser = argparse.ArgumentParser(description='核查正式Vue本人账户、密码变更及原响应审计')
    parser.add_argument('--execution')
    parser.add_argument('--run', type=Path)
    args = parser.parse_args()
    deployment = verify_application_deployment()
    if args.run is not None:
        before = counts()
        subprocess.run(['npm', 'run', 'typecheck:medical'], cwd=ROOT / 'web', check=True)
        subprocess.run(['npm', 'run', 'build:prod', '--', '--mode', 'medical', '--outDir', str(args.run.resolve(strict=True) / 'production')], cwd=ROOT / 'web', check=True)
        with (ROOT / '.implementation-work/runtime/owner-recovery.lock').open('a') as lock:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
            subprocess.run(['/usr/local/bin/node', 'tests/browser/medical-account-checks.js', str(args.run.resolve(strict=True)),
                            'http://127.0.0.1:18080'], cwd=ROOT, check=True)
            verify_mutations(args.run)
        if before != counts() or deployment != verify_application_deployment():
            raise ValueError('本人账户检查期间企业资料或者部署发生变化')
        print(json.dumps({'source': 'ACTUAL_MYSQL', 'before': before, 'after': counts(), 'result': 'PASS'}))
        return
    if not args.execution or Path(args.execution).name != args.execution or args.execution in ('.', '..'):
        raise ValueError('执行身份必须使用单一目录名称')
    output = ROOT / '.implementation-work' / ('IC-06-ACCOUNT-BROWSER-' + args.execution)
    inputs = ['scripts/check_medical_account_browser.py', 'scripts/check_medical_session_browser.py', 'scripts/check_local_browser.py',
              'scripts/local_runtime.py', 'tests/browser/medical-account-checks.js', 'tests/browser/package.json', 'tests/browser/package-lock.json',
              'web/package.json', 'web/package-lock.json', 'web/vite.config.js', 'web/index.html', 'web/tsconfig.medical.json', 'web/.env.medical',
              'backend-design/interfaces.schema.yaml', 'backend-design/接口补充设计.md', 'contracts/typescript/src/errors.ts',
              'LLD-前端.md', 'ui-delivery/tokens.json', '.implementation-work/runtime/application-deployment.json',
              '.implementation-work/runtime/service-identity.json', deployment['artifact']]
    for directory in ('web/src', 'web/vite', 'business/medical-identity/src', 'business/medical-persistence/src', 'tests/browser/node_modules'):
        inputs.extend(str(path.relative_to(ROOT)) for path in sorted((ROOT / directory).rglob('*'))
                      if path.is_file() and ('node_modules' not in path.parts or path.suffix in ('.js', '.json', '.node')))
    inputs.extend(browser_identity()[1])
    for package in ('vue', 'element-plus', 'axios', 'ajv', 'ajv-formats', 'yaml', 'lossless-json', 'jsonc-parser', 'json-custom-numbers', 'json-canonicalize', 'vue-cropper', 'vite', 'vue-tsc', 'typescript'):
        inputs.extend(str(path.relative_to(ROOT)) for path in sorted((ROOT / 'web/node_modules' / package).rglob('*')) if path.is_file())
    command = [sys.executable, str(CAPTURE), '--output-dir', str(output / 'command'), '--check-id', 'IC-06-ACCOUNT-BROWSER',
               '--kind', 'INTEGRATION', '--baseline-id', 'FRONT-ACCOUNT-01', '--cwd', str(ROOT), '--timeout', '300']
    for filename in inputs:
        command.extend(['--input', filename])
    environment = os.environ.copy()
    environment['TMPDIR'] = str(ROOT / '.implementation-work/migration-check-tmp')
    result = subprocess.run([*command, '--', sys.executable, 'scripts/check_medical_account_browser.py', '--run', str(output)], cwd=ROOT, env=environment)
    if result.returncode != 0:
        raise SystemExit(result.returncode)
    original = output / 'command/receipt.json'
    receipt = json.loads(original.read_bytes())
    artifacts = [original, output / 'medical-account-result-development.json', output / 'medical-account-result-production.json']
    artifacts.extend(path for path in sorted((output / 'production').rglob('*')) if path.is_file())
    for artifact in artifacts:
        receipt['artifacts'].append({'path': str(artifact), 'sha256': hashlib.sha256(artifact.read_bytes()).hexdigest()})
    target = output / 'receipt.json'
    target.write_text(json.dumps(receipt, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps({'result': 'PASS', 'receipt': str(target.relative_to(ROOT)), 'sha256': hashlib.sha256(target.read_bytes()).hexdigest(),
                      'inputs': len(receipt['inputs']), 'artifacts': len(receipt['artifacts'])}))


if __name__ == '__main__':
    main()
