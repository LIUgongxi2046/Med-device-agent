import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import xml.etree.ElementTree as ElementTree


ROOT = Path(__file__).resolve().parents[1]
from check_support import capture_check_path
CAPTURE = capture_check_path()


def main():
    parser = argparse.ArgumentParser(description='验证运行持久快照、实际HTTP及当前授权')
    parser.add_argument('--execution', required=True)
    parser.add_argument('--http', action='store_true')
    args = parser.parse_args()
    if Path(args.execution).name != args.execution or args.execution in ('.', '..'):
        raise ValueError('执行身份必须使用单一目录名称')
    temporary = ROOT / '.implementation-work/run-read-temp'
    temporary.mkdir(parents=True, exist_ok=True)
    tools = ROOT / '.implementation-work/toolchains'
    environment = os.environ.copy()
    environment.update({'TMPDIR': str(temporary), 'JAVA_HOME': str(tools / 'jdk-17.0.20.1+1/Contents/Home'),
        'MAVEN_OPTS': '-Dfile.encoding=UTF-8 -Djava.io.tmpdir=' + str(temporary)})
    modules = ['medical-contracts', 'medical-identity', 'medical-persistence']
    if args.http:
        modules.extend(['ruoyi-common', 'ruoyi-system', 'ruoyi-framework', 'ruoyi-quartz', 'ruoyi-generator', 'ruoyi-admin'])
    inputs = {ROOT / 'scripts/check_run_read.py', ROOT / 'business/pom.xml', ROOT / 'LLD-后端.md'}
    for module in modules:
        inputs.add(ROOT / 'business' / module / 'pom.xml')
        inputs.update(path for path in (ROOT / 'business' / module / 'src').rglob('*') if path.is_file())
    output = ROOT / '.implementation-work' / ('IC-13-RUN-READ-' + args.execution)
    capture = [sys.executable, str(CAPTURE), '--output-dir', str(output), '--check-id', 'IC-13-RUN-READ',
        '--kind', 'INTEGRATION', '--baseline-id', 'BACK-RUN-READ-01', '--cwd', str(ROOT / 'business'), '--timeout', '420']
    for path in sorted(inputs):
        capture.extend(('--input', str(path)))
    selected = 'RunReadHttpTest' if args.http else 'RunReadTest'
    report = ROOT / ('business/' + ('ruoyi-admin' if args.http else 'medical-persistence') + '/target/surefire-reports/TEST-com.ruoyi.medical.' + ('runtime' if args.http else 'persistence') + '.' + selected + '.xml')
    capture.extend(('--artifact', str(report)))
    if args.http:
        capture.extend(('--artifact', str(ROOT / '.implementation-work/run-read-http-result.json')))
    command = [str(tools / 'apache-maven-3.9.16/bin/mvn'), '--offline', '--batch-mode', '--no-transfer-progress',
        '-Dmaven.repo.local=' + str(ROOT / '.implementation-work/maven-repository'), '-pl', 'ruoyi-admin' if args.http else 'medical-persistence', '-am',
        '-Dtest=' + selected, '-Dsurefire.failIfNoSpecifiedTests=false', 'test']
    result = subprocess.run([*capture, '--', *command], cwd=ROOT, env=environment)
    receipt = output / 'receipt.json'
    print(json.dumps({'receipt_sha256': hashlib.sha256(receipt.read_bytes()).hexdigest(), 'input_data_kind': 'SIMULATION'}))
    if result.returncode:
        raise SystemExit(result.returncode)
    recorded = ElementTree.parse(report).getroot()
    if int(recorded.attrib['tests']) != (1 if args.http else 2) or any(int(recorded.attrib.get(value, '0')) for value in ('errors', 'failures', 'skipped')):
        raise RuntimeError('运行读取的完整检查未通过')


if __name__ == '__main__':
    main()
