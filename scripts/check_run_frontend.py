import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys


ROOT = Path(__file__).resolve().parents[1]
from check_support import capture_check_path
CAPTURE = capture_check_path()


def main():
    parser = argparse.ArgumentParser(description='验证运行页面完整Schema、严格类型与实际构建')
    parser.add_argument('--execution')
    parser.add_argument('--run', type=Path)
    args = parser.parse_args()
    environment = os.environ.copy()
    environment['TMPDIR'] = str(ROOT / '.build-work')
    if args.run is not None:
        output = args.run.resolve(strict=True)
        if not output.is_relative_to(ROOT / '.implementation-work'):
            raise ValueError('构建目录必须位于项目中间结果目录')
        (output / 'frontend-result.json').write_text(json.dumps({'input_data_kind': 'SIMULATION', 'strict_types_verified': False,
            'production_build_verified': False, 'schema_definitions': 8, 'visual_verified': False}), encoding='utf-8')
        subprocess.run(['node', 'scripts/check_run_frontend.mjs'], cwd=ROOT, env=environment, check=True)
        subprocess.run(['npm', 'run', 'typecheck:medical'], cwd=ROOT / 'web', env=environment, check=True)
        subprocess.run(['npm', 'run', 'build:prod', '--', '--mode', 'medical', '--outDir', str(output / 'production')], cwd=ROOT / 'web', env=environment, check=True)
        (output / 'frontend-result.json').write_text(json.dumps({'input_data_kind': 'SIMULATION', 'strict_types_verified': True,
            'production_build_verified': True, 'schema_definitions': 8, 'visual_verified': False}), encoding='utf-8')
        return
    if args.execution is None or Path(args.execution).name != args.execution or args.execution in ('.', '..'):
        raise ValueError('执行身份必须使用单一目录名称')
    output = ROOT / '.implementation-work' / ('IC-27-RUN-FRONT-' + args.execution)
    inputs = {ROOT / 'scripts/check_run_frontend.py', ROOT / 'scripts/check_run_frontend.mjs', ROOT / 'LLD-前端.md',
        ROOT / 'business/medical-persistence/src/main/resources/run-view.schema.json',
        ROOT / 'business/medical-persistence/src/main/resources/run-control.schema.json'}
    for directory in ('web/src', 'contracts/typescript/src'):
        inputs.update(path for path in (ROOT / directory).rglob('*') if path.is_file())
    for name in ('package.json', 'package-lock.json', 'tsconfig.medical.json', 'vite.config.js'):
        inputs.add(ROOT / 'web' / name)
    command = [sys.executable, str(CAPTURE), '--output-dir', str(output), '--check-id', 'IC-27-RUN-FRONT',
        '--kind', 'INTEGRATION', '--baseline-id', 'FRONT-RUN-READ-01', '--cwd', str(ROOT), '--timeout', '420']
    for path in sorted(inputs):
        command.extend(('--input', str(path)))
    command.extend(('--artifact', str(output / 'frontend-result.json'), '--', sys.executable, str(ROOT / 'scripts/check_run_frontend.py'), '--run', str(output)))
    result = subprocess.run(command, cwd=ROOT, env=environment)
    receipt = output / 'receipt.json'
    print(json.dumps({'receipt_sha256': hashlib.sha256(receipt.read_bytes()).hexdigest()}))
    if result.returncode:
        raise SystemExit(result.returncode)


if __name__ == '__main__':
    main()
