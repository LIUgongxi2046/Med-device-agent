import argparse
import json
import os
from pathlib import Path
import subprocess
import sys


ROOT = Path(__file__).resolve().parents[1]
from check_support import capture_check_path
CAPTURE = capture_check_path()


def main():
    parser = argparse.ArgumentParser(description='检查计划页面严格类型、原生Vite转换及实际Schema加载')
    parser.add_argument('--execution')
    parser.add_argument('--execute-checks', action='store_true')
    parser.add_argument('--timeout', type=int, default=120)
    args = parser.parse_args()
    if args.timeout <= 0:
        raise ValueError('检查期限必须为正数')
    temporary = ROOT / '.implementation-work/task-plan-frontend-tmp'
    temporary.mkdir(parents=True, exist_ok=True)
    environment = os.environ.copy()
    environment['TMPDIR'] = str(temporary)
    if args.execute_checks:
        if args.execution is not None:
            raise ValueError('原执行与回执登记需要分别指定')
        subprocess.run(['npm', 'run', 'typecheck:medical'], cwd=ROOT / 'web', env=environment, check=True)
        subprocess.run(['node', str(ROOT / 'scripts/check_task_plan_frontend.mjs')], cwd=temporary, env=environment, check=True)
        subprocess.run(['node', str(ROOT / 'scripts/check_document_frontend.mjs')], cwd=temporary, env=environment, check=True)
        return
    if not args.execution or Path(args.execution).name != args.execution or args.execution in ('.', '..'):
        raise ValueError('执行身份需要使用单一目录名称')
    inputs = {ROOT / name for name in ('scripts/check_task_plan_frontend.py', 'scripts/check_task_plan_frontend.mjs',
        'web/package.json', 'web/package-lock.json', 'web/tsconfig.medical.json', 'contracts/catalogue.json', 'contracts/extensions/evaluation-results.catalogue.json', 'business/medical-persistence/src/main/resources/evaluation-results.schema.json',
        'backend-design/interfaces.openapi.yaml', 'docs/development/IMP-12.md')}
    inputs.add(ROOT / 'business/medical-persistence/src/main/resources/task-plan-decision.schema.json')
    inputs.add(ROOT / 'business/medical-persistence/src/main/resources/task-create-draft.schema.json')
    inputs.add(ROOT / 'business/medical-persistence/src/main/resources/task-creation-selection.schema.json')
    inputs.add(ROOT / 'business/medical-persistence/src/main/resources/task-scope-selection.schema.json')
    inputs.add(ROOT / 'business/medical-persistence/src/main/resources/scenario-catalogue.schema.json')
    inputs.add(ROOT / 'docs/development/IMP-29.md')
    inputs.add(ROOT / 'docs/development/IMP-16.md')
    inputs.add(ROOT / 'scripts/check_document_frontend.mjs')
    inputs.add(ROOT / 'backend-design/接口补充设计.md')
    for directory in ('web/src', 'web/vite', 'contracts/typescript/src'):
        inputs.update(path for path in (ROOT / directory).rglob('*') if path.is_file())
    inputs.update((ROOT / 'web').glob('vite.config.*'))
    for entry in json.loads((ROOT / 'contracts/catalogue.json').read_bytes())['schemas']:
        inputs.add(ROOT / entry['path'])
    command = [sys.executable, str(CAPTURE), '--output-dir', str(ROOT / '.implementation-work' / ('IC-12-PLAN-FRONT-' + args.execution)),
        '--check-id', 'IC-12-PLAN-FRONT', '--kind', 'UNIT', '--baseline-id', 'FRONT-TASK-PLAN-WRITE-01',
        '--cwd', str(ROOT), '--timeout', str(args.timeout)]
    for path in sorted(inputs):
        command.extend(('--input', str(path)))
    subprocess.run([*command, '--', sys.executable, 'scripts/check_task_plan_frontend.py', '--execute-checks'],
        cwd=ROOT, env=environment, check=True)


if __name__ == '__main__':
    main()
