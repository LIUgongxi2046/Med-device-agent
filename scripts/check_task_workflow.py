import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import xml.etree.ElementTree as ElementTree


ROOT = Path(__file__).resolve().parents[1]
CAPTURE = Path('/Users/liuhaoxian/.agents/skills/haonan-s000-pmp/scripts/capture_check.py')


def main():
    parser = argparse.ArgumentParser(description='验证固定运行输入、完整步骤依赖和取消证明')
    parser.add_argument('--execution', required=True)
    parser.add_argument('--delivery', action='store_true')
    args = parser.parse_args()
    if Path(args.execution).name != args.execution or args.execution in ('.', '..'):
        raise ValueError('执行身份必须使用单一目录名称')
    output = ROOT / '.implementation-work' / ('IC-13-WORKFLOW-' + args.execution)
    temporary = ROOT / '.implementation-work/workflow-temp'
    temporary.mkdir(parents=True, exist_ok=True)
    tools = ROOT / '.implementation-work/toolchains'
    environment = os.environ.copy()
    environment.update({'TMPDIR': str(temporary), 'JAVA_HOME': str(tools / 'jdk-17.0.20.1+1/Contents/Home'),
        'MAVEN_OPTS': '-Dfile.encoding=UTF-8 -Djava.io.tmpdir=' + str(temporary)})
    inputs = {ROOT / 'scripts/check_task_workflow.py', ROOT / 'business/pom.xml', ROOT / 'LLD-后端.md'}
    for module in ('medical-contracts', 'medical-workflow'):
        inputs.add(ROOT / 'business' / module / 'pom.xml')
        inputs.update(path for path in (ROOT / 'business' / module / 'src').rglob('*') if path.is_file())
    selected = 'WorkflowDeliveryTest' if args.delivery else 'WorkflowPlanTest'
    report = ROOT / ('business/medical-workflow/target/surefire-reports/TEST-com.ruoyi.medical.workflow.' + selected + '.xml')
    capture = [sys.executable, str(CAPTURE), '--output-dir', str(output), '--check-id', 'IC-13-WORKFLOW',
        '--kind', 'INTEGRATION', '--baseline-id', 'BACK-TASK-WORKFLOW-01', '--cwd', str(ROOT / 'business'), '--timeout', '180']
    for path in sorted(inputs):
        capture.extend(('--input', str(path)))
    capture.extend(('--artifact', str(report)))
    command = [str(tools / 'apache-maven-3.9.16/bin/mvn'), '--offline', '--batch-mode', '--no-transfer-progress',
        '-Dmaven.repo.local=' + str(ROOT / '.implementation-work/maven-repository'), '-pl', 'medical-workflow', '-am',
        '-Dtest=' + selected, '-Dsurefire.failIfNoSpecifiedTests=false', 'test']
    result = subprocess.run([*capture, '--', *command], cwd=ROOT, env=environment)
    receipt = output / 'receipt.json'
    print(json.dumps({'receipt_sha256': hashlib.sha256(receipt.read_bytes()).hexdigest(),
        'input_data_kind': 'SIMULATION', 'business_workflow_verified': False, 'customer_acceptance_verified': False}))
    if result.returncode:
        raise SystemExit(result.returncode)
    recorded = ElementTree.parse(report).getroot()
    if int(recorded.attrib['tests']) != (1 if args.delivery else 5) or any(int(recorded.attrib.get(value, '0')) for value in ('errors', 'failures', 'skipped')):
        raise RuntimeError('完整步骤依赖和取消证明检查未通过')


if __name__ == '__main__':
    main()
