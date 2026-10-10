import argparse
import os
from pathlib import Path
import subprocess
import sys
import xml.etree.ElementTree as ElementTree


ROOT = Path(__file__).resolve().parents[1]
CAPTURE = Path('/Users/liuhaoxian/.agents/skills/haonan-s000-pmp/scripts/capture_check.py')


def main():
    parser = argparse.ArgumentParser(description='验证浏览器完整表格编辑与Java正文消费者的结构一致性')
    parser.add_argument('--execution', required=True)
    args = parser.parse_args()
    if Path(args.execution).name != args.execution or args.execution in ('.', '..'):
        raise ValueError('执行身份必须使用单一目录名称')
    output = ROOT / '.implementation-work' / ('IC-16-DOCUMENT-STRUCTURE-' + args.execution)
    temporary = ROOT / '.implementation-work/document-structure-temp'
    temporary.mkdir(parents=True, exist_ok=True)
    tools = ROOT / '.implementation-work/toolchains'
    environment = os.environ.copy()
    environment.update({'TMPDIR': str(temporary), 'JAVA_HOME': str(tools / 'jdk-17.0.20.1+1/Contents/Home'),
        'MAVEN_OPTS': '-Dfile.encoding=UTF-8 -Djava.io.tmpdir=' + str(temporary)})
    subprocess.run(['/usr/local/bin/node', 'scripts/check_document_frontend.mjs'], cwd=ROOT, env=environment, check=True)
    inputs = {ROOT / 'scripts/check_document_structure.py', ROOT / 'scripts/check_document_frontend.mjs', ROOT / 'business/pom.xml',
        ROOT / 'web/package.json', ROOT / 'web/package-lock.json', ROOT / 'web/tsconfig.medical.json',
        ROOT / '.implementation-work/document-frontend-structure.json', ROOT / 'backend-design/接口补充设计.md'}
    for module in ('medical-contracts', 'medical-identity', 'medical-persistence'):
        inputs.add(ROOT / 'business' / module / 'pom.xml')
        inputs.update(path for path in (ROOT / 'business' / module / 'src').rglob('*') if path.is_file())
    for directory in ('web/src/api/medical', 'web/src/views/medical'):
        inputs.update(path for path in (ROOT / directory).rglob('*') if path.is_file())
    report = ROOT / 'business/medical-persistence/target/surefire-reports/TEST-com.ruoyi.medical.persistence.DocumentFrontendStructureTest.xml'
    capture = [sys.executable, str(CAPTURE), '--output-dir', str(output), '--check-id', 'IC-16-DOCUMENT-STRUCTURE',
        '--kind', 'INTEGRATION', '--baseline-id', 'BACK-DOCUMENT-BODY-01', '--cwd', str(ROOT / 'business'), '--timeout', '180']
    for path in sorted(inputs):
        capture.extend(('--input', str(path)))
    capture.extend(('--artifact', str(report)))
    command = [str(tools / 'apache-maven-3.9.16/bin/mvn'), '--offline', '--batch-mode', '--no-transfer-progress',
        '-Dmaven.repo.local=' + str(ROOT / '.implementation-work/maven-repository'), '-pl', 'medical-persistence', '-am',
        '-Dtest=DocumentFrontendStructureTest', '-Dsurefire.failIfNoSpecifiedTests=false', 'test']
    subprocess.run([*capture, '--', *command], cwd=ROOT, env=environment, check=True)
    result = ElementTree.parse(report).getroot()
    if int(result.attrib['tests']) != 1 or any(int(result.attrib.get(value, '0')) != 0 for value in ('failures', 'errors', 'skipped')):
        raise RuntimeError('当前正文消费者缺少完整编辑成功报告')


if __name__ == '__main__':
    main()
