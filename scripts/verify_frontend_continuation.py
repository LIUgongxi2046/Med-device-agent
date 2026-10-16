import argparse
import hashlib
import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
WORK = (ROOT / '.implementation-work').resolve(strict=True)


def evidence_path(value):
    path = (ROOT / value).resolve(strict=True)
    path.relative_to(WORK)
    return path


def fixed_file(path):
    return {'path': str(path), 'sha256': hashlib.sha256(path.read_bytes()).hexdigest()}


def main():
    parser = argparse.ArgumentParser(description='核对当前前端组件、完整制品和真实项目浏览器结果的全部字节')
    parser.add_argument('--selection', required=True)
    parser.add_argument('--template', required=True)
    parser.add_argument('--release', required=True)
    parser.add_argument('--project', required=True, action='append')
    parser.add_argument('--output', required=True)
    args = parser.parse_args()
    files = []
    receipts = []
    expected_inputs = {}
    entries = [(role, getattr(args, role)) for role in ('selection', 'template', 'release')]
    entries.extend(('project', value) for value in args.project)
    if len({value for _, value in entries}) != len(entries):
        raise ValueError('实际检查回执重复')
    for role, value in entries:
        path = evidence_path(value)
        receipt = json.loads(path.read_bytes())
        if receipt['exit_code'] != 0 or not receipt['inputs_unchanged']:
            raise ValueError('检查回执未通过：' + role)
        for item in [*receipt['inputs'], *receipt['artifacts']]:
            actual = Path(item['path']).resolve(strict=True)
            if hashlib.sha256(actual.read_bytes()).hexdigest() != item['sha256']:
                raise ValueError('当前文件与检查回执不一致：' + str(actual))
        for item in receipt['inputs']:
            previous = expected_inputs.get(item['path'])
            if previous is not None and previous != item['sha256']:
                raise ValueError('检查集合采用了不同输入：' + item['path'])
            expected_inputs[item['path']] = item['sha256']
        receipts.append({'role': role, **fixed_file(path), 'inputs': len(receipt['inputs']),
                         'artifacts': len(receipt['artifacts']), 'status': 'PASS'})
    component_checks = 0
    for role, count in (('selection', 12), ('template', 10)):
        receipt = json.loads(evidence_path(getattr(args, role)).read_bytes())
        candidates = [Path(item['path']) for item in receipt['artifacts'] if Path(item['path']).name == 'checks.json']
        if len(candidates) != 1:
            raise ValueError('组件检查结果缺少或重复：' + role)
        result = json.loads(candidates[0].read_bytes())
        if result['data_kind'] != 'SIMULATION' or result['visual'] != 'NOT_RUN' or len(result['checks']) != count:
            raise ValueError('组件检查身份或范围不完整：' + role)
        if any(check['result'] != 'PASS' for check in result['checks']):
            raise ValueError('组件检查未全部通过：' + role)
        component_checks += count
    browser_results = []
    for value in args.project:
        project_directory = evidence_path(value).parent
        for name in ('project-result.json', 'project-browser-result.json', 'project-browser-output.log'):
            files.append(fixed_file(project_directory / name))
        project = json.loads((project_directory / 'project-result.json').read_bytes())
        if project['data_kind'] != 'SIMULATION' or project['project_actions'] != 6 or project['customer_success_verified']:
            raise ValueError('项目业务检查输入或范围不匹配')
        for key in ('create_replay_and_conflict_verified', 'revision_conflict_verified',
                    'status_replay_and_reopening_verified', 'audit_and_outbox_verified',
                    'simulation_browser_verified', 'project_counts_unchanged', 'temporary_application_closed'):
            if project[key] is not True:
                raise ValueError('项目业务检查未通过：' + key)
        browser = json.loads((project_directory / 'project-browser-result.json').read_bytes())
        if browser['customer_success_verified'] or not browser['results']:
            raise ValueError('项目浏览器检查身份或环境不完整')
        browser_results.extend(browser['results'])
    if {item['mode'] for item in browser_results} != {'development', 'production'}:
        raise ValueError('项目浏览器检查环境不完整')
    if len(browser_results) != 2:
        raise ValueError('项目浏览器检查环境重复')
    for item in browser_results:
        if item['data_kind'] != 'SIMULATION' or len(item['checks']) != 8 or len(set(item['checks'])) != 8:
            raise ValueError('项目浏览器检查范围不完整')
        if len(item['writes']) != 6 or len({write['key'] for write in item['writes']}) != 6:
            raise ValueError('项目浏览器原请求身份不完整')
        if any(write['authorization'] is not None for write in item['writes']):
            raise ValueError('项目浏览器采用了未经批准的认证方式')
    output = ROOT / args.output
    output.parent.resolve(strict=True).relative_to(WORK)
    result = {'scope': 'FRONTEND_CONTINUATION_CURRENT', 'status': 'PASS', 'data_kind': 'SIMULATION',
              'visual': 'NOT_RUN', 'customer_success_verified': False, 'receipts': receipts,
              'verification_script': fixed_file(Path(__file__).resolve(strict=True)),
              'result_files': files, 'component_checks': component_checks, 'project_browser_checks': 16,
              'project_browser_writes': 12, 'native_project_actions': 6 * len(args.project)}
    with output.open('x', encoding='utf-8') as target:
        json.dump(result, target, ensure_ascii=False, indent=2)
        target.write('\n')
    print(json.dumps({'result': 'PASS', 'output': str(output.relative_to(ROOT)),
                      'sha256': fixed_file(output)['sha256'], 'receipts': len(receipts)}))


if __name__ == '__main__':
    main()
