"""Run the bounded MyBatis/TenantScope probe with existing compiled dependencies.
No Maven build, dependency installation, database connection or deployment is performed.
Example: python tests/run_source_original_parse_probe.py --classpath "$EXISTING_CP" --java-home "$JAVA_HOME" --output-dir /tmp/source-parse-evidence
"""
import argparse
import os
from pathlib import Path
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[1]

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--classpath', required=True, help='Existing compiled application classes and dependency jars, separated by os.pathsep')
    parser.add_argument('--java-home', default=os.environ.get('JAVA_HOME'))
    parser.add_argument('--output-dir', type=Path, required=True)
    args = parser.parse_args()
    def tool(name):
        found = str(Path(args.java_home) / 'bin' / name) if args.java_home else shutil.which(name)
        if not found or not Path(found).is_file():
            raise SystemExit(f'An existing JDK with {name} is required; nothing will be installed.')
        return found
    output = args.output_dir.resolve()
    output.mkdir(parents=True, exist_ok=True)
    classes = output / 'classes'
    classes.mkdir(exist_ok=True)
    quiet_logging = output / 'logback-empty.xml'
    quiet_logging.write_text('<configuration/>\n', encoding='utf-8')
    cp = args.classpath
    subprocess.run([tool('javac'), '-cp', cp, '-d', str(classes),
                    str(ROOT / 'tests/SourceOriginalParseMapperProbe.java')], cwd=ROOT, check=True)
    command = [tool('java'), '-Dlogback.configurationFile=' + str(quiet_logging),
               '-cp', str(classes) + os.pathsep + cp,
               'com.ruoyi.medical.persistence.SourceOriginalParseMapperProbe',
               str(ROOT / 'business/medical-persistence/src/main/resources/medical-mappers/SourceGovernanceMapper.xml'),
               str(output / 'source-mybatis-intercepted.txt')]
    result = subprocess.run(command, cwd=ROOT, check=True, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    (output / 'probe.log').write_text(result.stdout, encoding='utf-8')
    print(result.stdout, end='')

if __name__ == '__main__':
    main()
