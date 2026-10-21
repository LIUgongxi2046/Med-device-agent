import argparse
import os
from pathlib import Path
import subprocess
import sys


ROOT = Path(__file__).resolve().parents[1]
from check_support import capture_check_path
CAPTURE = capture_check_path()


def main():
    parser = argparse.ArgumentParser(description='核查预算历史和批准错误的实际序列化及共享Schema')
    parser.add_argument('--execution', required=True)
    args = parser.parse_args()
    if Path(args.execution).name != args.execution or args.execution in ('.', '..'):
        raise ValueError('执行身份必须为单一目录名称')
    toolchains = ROOT / '.implementation-work/toolchains'
    environment = os.environ.copy()
    environment['JAVA_HOME'] = str((toolchains / 'jdk-17.0.20.1+1/Contents/Home').resolve(strict=True))
    environment['MAVEN_OPTS'] = '-Dfile.encoding=UTF-8 -Djava.io.tmpdir=' + str(ROOT / '.implementation-work/java-temp')
    environment['TMPDIR'] = str(ROOT / '.implementation-work/migration-check-tmp')
    command = [sys.executable, str(CAPTURE), '--output-dir', str(ROOT / '.implementation-work' / ('IC-07-BUDGET-ERRORS-' + args.execution)),
               '--check-id', 'IC-07-BUDGET-ERRORS', '--kind', 'UNIT', '--baseline-id', 'BACK-BUDGET-HISTORY-01',
               '--cwd', str(ROOT / 'business'), '--timeout', '180']
    for path in ('scripts/check_budget_errors.py', 'business/pom.xml', 'business/medical-identity/pom.xml',
                 'business/medical-identity/src/main/java/com/ruoyi/medical/identity/IdentityErrorResponseWriter.java',
                 'business/medical-identity/src/test/java/com/ruoyi/medical/identity/OidcSecurityTest.java',
                 'backend-design/interfaces.schema.yaml', 'contracts/catalogue.json', 'contracts/extensions/evaluation-results.catalogue.json', 'business/medical-persistence/src/main/resources/evaluation-results.schema.json'):
        command.extend(['--input', str(ROOT / path)])
    subprocess.run([*command, '--', str((toolchains / 'apache-maven-3.9.16/bin/mvn').resolve(strict=True)),
                    '--batch-mode', '--no-transfer-progress', '-Dmaven.repo.local=' + str(ROOT / '.implementation-work/maven-repository'),
                    '-pl', 'medical-identity', '-am', '-Dtest=OidcSecurityTest', '-Dsurefire.failIfNoSpecifiedTests=false', 'test'],
                   cwd=ROOT, env=environment, check=True)


if __name__ == '__main__':
    main()
