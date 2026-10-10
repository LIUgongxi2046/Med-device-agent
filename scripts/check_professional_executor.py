import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys


ROOT = Path(__file__).resolve().parents[1]
CAPTURE = Path('/Users/liuhaoxian/.agents/skills/haonan-s000-pmp/scripts/capture_check.py')


def suite():
    environment = os.environ.copy()
    environment['TMPDIR'] = str(ROOT / '.build-work')
    package = ROOT / 'professional-executor'
    for command in (['npm', 'run', 'build'], ['npm', 'test']):
        subprocess.run(command, cwd=package, env=environment, check=True, timeout=90)
    subprocess.run([sys.executable, str(ROOT / 'scripts/check_professional_initialize.py')],
        cwd=ROOT, env=environment, check=True, timeout=90)
    bridge = json.loads((ROOT / '.implementation-work/professional-bridge-result.json').read_bytes())
    if bridge['dispatches'] != 1 or bridge['input_data_kind'] != 'SIMULATION' or not bridge['original_result_restored']:
        raise ValueError('原模型调用恢复检查未通过')


def main():
    parser = argparse.ArgumentParser(description='验证专业运行配置、完整模型协议、实际工具清单和原调用恢复')
    parser.add_argument('--execution')
    parser.add_argument('--suite', action='store_true')
    args = parser.parse_args()
    if args.suite:
        suite()
        return
    if args.execution is None or Path(args.execution).name != args.execution or args.execution in ('.', '..'):
        raise ValueError('执行身份必须使用单一目录名称')
    output = ROOT / '.implementation-work' / ('IC-14-PROFESSIONAL-' + args.execution)
    inputs = {ROOT / 'scripts/check_professional_executor.py', ROOT / 'scripts/check_professional_initialize.py',
        ROOT / 'LLD-Agent.md', ROOT / 'contracts/catalogue.json'}
    for directory in ('professional-executor/src', 'professional-executor/tests', 'contracts/typescript/src', 'agent-design'):
        inputs.update(path for path in (ROOT / directory).rglob('*') if path.is_file())
    for path in ('professional-executor/package.json', 'professional-executor/package-lock.json', 'professional-executor/tsconfig.json',
        'professional-executor/medical.patch.yml', 'professional-executor/profiles/medical-agent/package.json',
        'professional-executor/profiles/medical-agent/pnpm-workspace.yaml', 'professional-executor/profiles/medical-agent/pnpm-lock.yaml',
        'professional-executor/profiles/medical-agent/cordis.patch.yml', 'contracts/typescript/package.json',
        'contracts/typescript/tsconfig.build.json'):
        inputs.add(ROOT / path)
    manifest = json.loads((ROOT / 'contracts/catalogue.json').read_bytes())
    inputs.update(ROOT / entry['path'] for entry in manifest['schemas'])
    command = [sys.executable, str(CAPTURE), '--output-dir', str(output), '--check-id', 'IC-14-PROFESSIONAL',
        '--kind', 'INTEGRATION', '--baseline-id', 'HARNESS-MEDICAL-AGENT-01', '--cwd', str(ROOT), '--timeout', '240']
    for path in sorted(inputs):
        command.extend(('--input', str(path)))
    for path in ('professional-initialize-result.json', 'professional-bridge-result.json'):
        command.extend(('--artifact', str(ROOT / '.implementation-work' / path)))
    command.extend(('--artifact', str(ROOT / '.build-work/professional-inventory.json')))
    command.extend(('--', sys.executable, str(ROOT / 'scripts/check_professional_executor.py'), '--suite'))
    result = subprocess.run(command, cwd=ROOT, check=False)
    receipt = output / 'receipt.json'
    print(json.dumps({'receipt_sha256': hashlib.sha256(receipt.read_bytes()).hexdigest(), 'input_data_kind': 'SIMULATION',
        'customer_acceptance_verified': False}))
    if result.returncode:
        raise SystemExit(result.returncode)


if __name__ == '__main__':
    main()
