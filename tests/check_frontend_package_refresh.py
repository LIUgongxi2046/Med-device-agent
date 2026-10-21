"""Offline packaging regression. Requires an existing web/dist and a populated Maven cache.

Runs two ordinary package lifecycles against a temporary dist, then restores the
normal package. No clean lifecycle, services, migrations, or credentials.
"""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]
ADMIN = ROOT / 'business/ruoyi-admin'


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def inventory(folder):
    return {p.relative_to(folder).as_posix(): digest(p)
            for p in sorted(folder.rglob('*')) if p.is_file()}


def verify(dist, evidence, label):
    expected = inventory(dist)
    generated = inventory(ADMIN / 'target/classes/static')
    jar = ADMIN / 'target/ruoyi-admin.jar'
    prefix = 'BOOT-INF/classes/static/'
    with zipfile.ZipFile(jar) as archive:
        actual = {name[len(prefix):]: hashlib.sha256(archive.read(name)).hexdigest()
                  for name in archive.namelist()
                  if name.startswith(prefix) and not name.endswith('/')}
    result = {'dist': expected, 'generated': generated, 'packaged': actual,
              'jar_sha256': digest(jar)}
    (evidence / (label + '-inventory.json')).write_text(json.dumps(result, indent=2) + '\n')
    assert expected == generated == actual, 'Exact static entry sets/bytes differ: ' + label
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--maven', required=True)
    parser.add_argument('--settings', required=True)
    parser.add_argument('--repository', required=True)
    parser.add_argument('--evidence', type=Path, required=True)
    args = parser.parse_args()
    evidence = args.evidence.resolve()
    evidence.mkdir(parents=True, exist_ok=False)
    dist = ROOT / 'web/dist'
    assert (dist / 'index.html').is_file(), 'Build web/dist first'
    resources = ADMIN / 'src/main/resources'
    authored_before = inventory(resources)
    # This module reserves the static output namespace for generated frontend bytes.
    assert not (resources / 'static').exists(), 'Authored static resources require an explicit ownership decision'
    sentinel = ADMIN / 'target/classes/frontend-cleanup-scope-check.txt'
    assert not sentinel.exists(), 'Regression sentinel already exists'
    command = [args.maven, '-o', '-s', args.settings, '-B',
               '-Dmaven.repo.local=' + args.repository, '-f', str(ROOT / 'business/pom.xml'),
               '-DskipTests', 'package']
    runs = []

    def package(label, frontend=None):
        actual = command + ([] if frontend is None else ['-Dmedical.frontendDirectory=' + str(frontend)])
        (evidence / (label + '-command.json')).write_text(json.dumps(actual, indent=2) + '\n')
        with (evidence / (label + '.log')).open('w') as log:
            result = subprocess.run(actual, cwd=ROOT, stdout=log, stderr=subprocess.STDOUT)
        runs.append({'label': label, 'exit_code': result.returncode})
        (evidence / 'results.json').write_text(json.dumps(runs, indent=2) + '\n')
        assert result.returncode == 0, 'Package failed: ' + label

    try:
        with tempfile.TemporaryDirectory(prefix='frontend-package-refresh-') as folder:
            fixture = Path(folder) / 'dist'
            shutil.copytree(dist, fixture)
            (fixture / 'regression-old.js').write_text('old hashed asset\n')
            (fixture / 'regression-removed.js').write_text('removed asset\n')
            sentinel.parent.mkdir(parents=True, exist_ok=True)
            sentinel.write_text('not owned by generated static cleanup\n')
            package('first', fixture)
            verify(fixture, evidence, 'first')
            assert sentinel.read_text() == 'not owned by generated static cleanup\n'
            (fixture / 'regression-old.js').rename(fixture / 'regression-new.js')
            (fixture / 'regression-removed.js').unlink()
            package('second', fixture)
            verify(fixture, evidence, 'second')
            assert sentinel.read_text() == 'not owned by generated static cleanup\n'
            assert inventory(resources) == authored_before, 'Authored resources changed'
    finally:
        sentinel.unlink(missing_ok=True)
        # Always restore a package using genuine web/dist, including on regression failure.
        package('final')
        final = verify(dist, evidence, 'final')
        assert inventory(resources) == authored_before, 'Authored resources changed'
        (evidence / 'authored-resources.json').write_text(json.dumps(authored_before, indent=2) + '\n')
    print(json.dumps({'status': 'PASS', 'final_static_files': len(final['dist']),
                      'final_jar_sha256': final['jar_sha256'], 'authored_resources': len(authored_before)}))


if __name__ == '__main__':
    main()
