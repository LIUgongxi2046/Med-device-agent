import argparse
import datetime
import hashlib
import importlib.metadata
import json
import os
from pathlib import Path
import subprocess
import sys

from redis import Redis

from check_local_browser import browser_identity
from local_runtime import settings, verify_application_deployment


ROOT = Path(__file__).resolve().parents[1]
CAPTURE = Path('/Users/liuhaoxian/.agents/skills/haonan-s000-pmp/scripts/capture_check.py')


def read_metrics(output):
    if importlib.metadata.version('redis') != '6.4.0':
        raise ValueError('Redis检查组件版本无效')
    with Redis(host='127.0.0.1', port=16379, password=settings()['redis_password'],
               decode_responses=True, socket_timeout=5, socket_connect_timeout=5) as client:
        info = client.info()
        commands = client.info('commandstats')
    if info['redis_version'] != '7.4.8' or not commands:
        raise ValueError('实际Redis版本或命令统计无效')
    result = {
        'redis_version': info['redis_version'], 'redis_run_id': info['run_id'],
        'captured_at': datetime.datetime.now(datetime.timezone.utc).isoformat(),
        'source': '127.0.0.1:16379 Redis INFO / INFO commandstats',
        'command_statistics': commands,
        'cache': {'info': {'used_memory_human': info['used_memory_human']},
                  'commandStats': [{'name': name.removeprefix('cmdstat_'), 'value': value['calls']}
                                   for name, value in sorted(commands.items())]}
    }
    (output / 'redis-metrics.json').write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n')


def run_checks(output):
    before = verify_application_deployment()
    read_metrics(output)
    subprocess.run(['npm', 'run', 'build:prod', '--', '--outDir', str(output / 'production')],
                   cwd=ROOT / 'web', check=True)
    subprocess.run(['/usr/local/bin/node', 'tests/browser/web-compatibility-checks.js', str(output)],
                   cwd=ROOT, check=True)
    result = json.loads((output / 'compatibility-result.json').read_bytes())
    if result['check_count'] != 6 or result['browser_version'] != browser_identity()[0]:
        raise ValueError('正式组件检查数量或浏览器版本无效')
    if before != verify_application_deployment():
        raise ValueError('组件检查期间本地应用程序发生变化')
    print('WEB_COMPATIBILITY PASS 6/6')


def inputs():
    values = ['scripts/check_web.py', 'scripts/check_local_browser.py', 'scripts/local_runtime.py',
              'tests/web-requirements.lock', 'tests/browser/web-compatibility-checks.js',
              'tests/browser/package.json', 'tests/browser/package-lock.json',
              'web/package.json', 'web/package-lock.json', 'web/vite.config.js', 'web/index.html',
              'web/.env.development', 'web/.env.production', 'web/.env.staging', 'web/LICENSE',
              '.implementation-work/runtime/service-identity.json',
              '.implementation-work/runtime/application-deployment.json']
    values.append(verify_application_deployment()['artifact'])
    for directory in ['web/src', 'web/vite', 'web/tests/engineering']:
        values.extend(str(path.relative_to(ROOT)) for path in sorted((ROOT / directory).rglob('*')) if path.is_file())
    for package in ['vite', 'vue', 'element-plus', 'echarts', '@spiriit/vite-plugin-svg-spritemap',
                    'svgo', '@xmldom/xmldom']:
        directory = ROOT / 'web/node_modules' / package
        values.append(str(directory / 'package.json'))
    values.extend(['web/node_modules/@spiriit/vite-plugin-svg-spritemap/dist/index.mjs',
                   'web/node_modules/vite/dist/node/index.js', 'web/node_modules/echarts/dist/echarts.js',
                   'web/node_modules/echarts/theme/v5.js', 'tests/browser/node_modules/playwright/index.mjs',
                   'tests/browser/node_modules/playwright-core/lib/coreBundle.js'])
    distribution = importlib.metadata.distribution('redis')
    values.extend(str(distribution.locate_file(item)) for item in distribution.files
                  if str(item).endswith('.py') or str(item).endswith('METADATA'))
    values.extend(browser_identity()[1])
    return values


def main():
    parser = argparse.ArgumentParser(description='验证正式前端构建、真实图标与Redis图表')
    parser.add_argument('--execution')
    parser.add_argument('--run', type=Path)
    args = parser.parse_args()
    if args.run is not None:
        if args.execution is not None:
            raise ValueError('执行身份只能在登记入口指定')
        run_checks(args.run.resolve(strict=True))
        return
    if not args.execution or Path(args.execution).name != args.execution or args.execution in ('.', '..'):
        raise ValueError('执行身份必须为单一目录名称')
    output = ROOT / '.implementation-work' / ('IC-01-WEB-COMPAT-' + args.execution)
    if output.exists():
        raise ValueError('检查目录必须全新建立')
    command = [sys.executable, str(CAPTURE), '--output-dir', str(output / 'command'),
               '--check-id', 'IC-01-WEB-COMPAT', '--kind', 'INTEGRATION',
               '--baseline-id', 'WEB-DEPENDENCY-01', '--cwd', str(ROOT), '--timeout', '300']
    for filename in inputs():
        command.extend(['--input', filename])
    environment = os.environ.copy()
    environment['TMPDIR'] = str(ROOT / '.implementation-work/migration-check-tmp')
    subprocess.run([*command, '--', sys.executable, 'scripts/check_web.py', '--run', str(output)],
                   cwd=ROOT, env=environment, check=True)
    receipt_path = output / 'command/receipt.json'
    receipt = json.loads(receipt_path.read_bytes())
    # 原始执行回执保持独立，最终回执另外绑定全部构建文件。
    receipt['captured_by'] = 'project-web-check/v1'
    receipt['artifacts'].append({'path': str(receipt_path), 'sha256': hashlib.sha256(receipt_path.read_bytes()).hexdigest()})
    for filename in ['redis-metrics.json', 'compatibility-result.json', 'build-artifacts.json']:
        artifact = output / filename
        receipt['artifacts'].append({'path': str(artifact), 'sha256': hashlib.sha256(artifact.read_bytes()).hexdigest()})
    for item in json.loads((output / 'build-artifacts.json').read_bytes()):
        if hashlib.sha256(Path(item['path']).read_bytes()).hexdigest() != item['sha256']:
            raise ValueError('前端构建输出已经变化')
        receipt['artifacts'].append(item)
    target = output / 'receipt.json'
    target.write_text(json.dumps(receipt, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps({'receipt_path': str(target), 'receipt_sha256': hashlib.sha256(target.read_bytes()).hexdigest(),
                      'inputs': len(receipt['inputs']), 'artifacts': len(receipt['artifacts']),
                      'exit_code': receipt['exit_code'], 'inputs_unchanged': receipt['inputs_unchanged']}))


if __name__ == '__main__':
    main()
