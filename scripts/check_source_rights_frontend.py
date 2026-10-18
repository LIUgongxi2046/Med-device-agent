import argparse
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import shutil
import sys

from check_local_browser import browser_identity


ROOT = Path(__file__).resolve().parents[1]
CAPTURE = Path('/Users/liuhaoxian/.agents/skills/haonan-s000-pmp/scripts/capture_check.py')


def main():
    parser = argparse.ArgumentParser(description='使用真实服务和已授权模拟资料验证来源权利决定与浏览器恢复')
    parser.add_argument('--execution', required=True)
    parser.add_argument('--native-only', action='store_true')
    parser.add_argument('--frontend-directory', type=Path)
    parser.add_argument('--reuse-build-from', type=Path)
    parser.add_argument('--timeout', type=int, default=3600)
    args = parser.parse_args()
    if not 600 <= args.timeout <= 3600:
        raise ValueError('完整检查运行时限需要600至3600秒')
    if Path(args.execution).name != args.execution or args.execution in ('.', '..'):
        raise ValueError('执行身份必须使用单一目录名称')
    if args.native_only == (args.frontend_directory is not None):
        raise ValueError('原生检查使用native-only，完整浏览器检查需要当前完整前端制品')
    output = ROOT / '.implementation-work' / ('IC-10-SOURCE-RIGHTS-' + args.execution)
    output.mkdir(parents=True, exist_ok=False)
    build = output / 'maven'
    if args.reuse_build_from is not None:
        previous = args.reuse_build_from.resolve(strict=True)
        previous.relative_to(ROOT / '.implementation-work')
        if not previous.name.startswith('IC-10-SOURCE-RIGHTS-') or not (previous / 'maven').is_dir():
            raise ValueError('增量构建只能使用本项原独立检查目录')
        build = previous / 'maven'
    temporary = ROOT / '.implementation-work/source-rights-tmp'
    temporary.mkdir(parents=True, exist_ok=True)
    inputs = {ROOT / name for name in ('scripts/check_source_rights_frontend.py', 'scripts/check_local_browser.py',
        'business/pom.xml', 'business/ruoyi-admin/pom.xml', 'business/medical-persistence/pom.xml', 'contracts/catalogue.json', 'contracts/extensions/evaluation-results.catalogue.json', 'business/medical-persistence/src/main/resources/evaluation-results.schema.json')}
    inputs.add(CAPTURE)
    inputs.add(ROOT / 'scripts/prepare_source_exact_simulation.py')
    for directory in ('document/src', 'contracts/python/src'):
        inputs.update(path for path in (ROOT / directory).rglob('*') if path.is_file() and '__pycache__' not in path.parts)
    for module in ('medical-contracts', 'medical-data', 'medical-identity', 'medical-persistence', 'ruoyi-admin', 'ruoyi-framework', 'ruoyi-system', 'ruoyi-common'):
        inputs.update(path for path in (ROOT / 'business' / module / 'src').rglob('*') if path.is_file())
    for item in json.loads((ROOT / 'contracts/catalogue.json').read_bytes())['schemas']:
        inputs.add(ROOT / item['path'])
    frontend = output / 'unused-frontend'
    if args.frontend_directory is not None:
        frontend = args.frontend_directory.resolve(strict=True)
        frontend.relative_to(ROOT / '.implementation-work')
        if not (frontend / 'index.html').is_file():
            raise ValueError('完整前端制品缺少正式入口')
        inputs.update(path for path in frontend.rglob('*') if path.is_file())
        inputs.update(path for path in (ROOT / 'web/src').rglob('*') if path.is_file())
        inputs.update(ROOT / name for name in ('tests/browser/source-rights-checks.js', 'tests/browser/source-exact-actions.js', 'tests/browser/package.json', 'tests/browser/package-lock.json',
            'web/package.json', 'web/package-lock.json', 'web/vite.config.js', 'web/tsconfig.medical.json', 'web/.env.medical', 'web/index.html'))
        for package in ('playwright', 'playwright-core'):
            inputs.update(path for path in (ROOT / 'tests/browser/node_modules' / package).rglob('*') if path.is_file())
        inputs.update(Path(name) for name in browser_identity()[1])
    tools = ROOT / '.implementation-work/toolchains'
    os.environ['JAVA_HOME'] = str(tools / 'jdk-17.0.20.1+1/Contents/Home')
    os.environ['TMPDIR'] = str(temporary)
    os.environ['MAVEN_OPTS'] = '-Xms128m -Xmx512m -Dfile.encoding=UTF-8 -Djava.io.tmpdir=' + str(temporary)
    specification = importlib.util.spec_from_file_location('source_rights_capture', CAPTURE)
    if specification is None or specification.loader is None:
        raise ValueError('当前检查回执工具无法加载')
    capture = importlib.util.module_from_spec(specification)
    specification.loader.exec_module(capture)
    command = [str(tools / 'apache-maven-3.9.16/bin/mvn'), '-f', 'business/pom.xml', '--batch-mode', '--no-transfer-progress',
        '-Dmaven.repo.local=' + str(ROOT / '.implementation-work/maven-repository'), '-pl', 'ruoyi-admin', '-am',
        '-Dmedical.check.output-root=' + str(build),
        '-Dmedical.sourceRightsEvidence=' + str(output / 'result.json'), '-Dmedical.sourceRightsBrowser=' + str(not args.native_only).lower(),
        '-Dmedical.sourceRightsFrontend=' + str(frontend), '-Dtest=SourceRightsSimulationHttpTest', '-Dsurefire.failIfNoSpecifiedTests=false', 'test']
    result = capture.run_check(argparse.Namespace(output_dir=output / 'command', check_id='IC-10-SOURCE-RIGHTS', kind='INTEGRATION',
        baseline_id='FRONT-SOURCE-RIGHTS-01', cwd=ROOT, timeout=args.timeout, input=[str(path) for path in sorted(inputs)], artifact=[], command=command))
    if result != 0:
        raise SystemExit(result)
    original = output / 'command/receipt.json'
    receipt = json.loads(original.read_bytes())
    report = build / 'ruoyi-admin/surefire-reports/TEST-com.ruoyi.medical.runtime.SourceRightsSimulationHttpTest.xml'
    fixed = output / report.name
    shutil.copyfile(report, fixed)
    files = [original, fixed, output / 'result.json']
    if not args.native_only:
        for mode in ('development', 'production'):
            files.extend((output / ('browser-' + mode + '.json'), output / ('browser-' + mode + '.log')))
            browser = json.loads((output / ('browser-' + mode + '.json')).read_bytes())
            if browser['data_kind'] != 'SIMULATION' or browser['visual'] != 'NOT_RUN' or len(browser['checks']) != 8 or len(browser['upload_checks']) != 3 or len(browser['index_checks']) != 3 or len(browser['exact_checks']) != 11:
                raise ValueError('完整权利浏览器范围或身份不匹配')
    for path in files:
        receipt['artifacts'].append({'path': str(path), 'sha256': hashlib.sha256(path.read_bytes()).hexdigest()})
    target = output / 'receipt.json'
    target.write_text(json.dumps(receipt, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps({'result': 'PASS', 'receipt': str(target.relative_to(ROOT)), 'sha256': hashlib.sha256(target.read_bytes()).hexdigest(),
        'inputs': len(receipt['inputs']), 'artifacts': len(receipt['artifacts'])}))


if __name__ == '__main__':
    main()
