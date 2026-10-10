import json
from pathlib import Path
import time

from deepseek_harness import HarnessClient, HarnessConfig


ROOT = Path(__file__).resolve().parents[1]


def main():
    started = time.monotonic()
    config = HarnessConfig(profile='medical-agent', dsh_home=str(ROOT / 'professional-executor'), cwd=str(ROOT),
        env={'TMPDIR': str(ROOT / '.build-work')}, initialize_timeout_seconds=45, request_timeout_seconds=45, shutdown_timeout_seconds=2)
    with HarnessClient(config) as client:
        result = client.initialize(cwd=str(ROOT), provider='enterprise-model', model='SIMULATION')
        if result.serverInfo is None or result.serverInfo.name != 'deepseek-harness-sdk-runtime':
            raise ValueError('专业运行配置未返回官方SDK身份')
        inventory = json.loads((ROOT / '.build-work/professional-inventory.json').read_bytes())
        expected = {'read_artifact', 'load_skill', 'ask_user_question', 'submit_plan_candidate'}
        if inventory['role'] != 'Planner' or {tool['name'] for tool in inventory['tools']} != expected:
            raise ValueError('实际工具清单与Planner角色不一致')
        if inventory['providers'] != [{'id': 'enterprise-model', 'name': '企业模型接口'}]:
            raise ValueError('实际模型清单包含未发布路由')
        if len(inventory['plugins']) != 13 or any(len(tool['parameters_sha256']) != 64 for tool in inventory['tools']):
            raise ValueError('实际专业插件或Schema清单不完整')
    record = {'profile': 'medical-agent', 'input_data_kind': 'SIMULATION', 'public_initialize_verified': True,
        'runtime_inventory': inventory, 'elapsed_seconds': time.monotonic() - started}
    (ROOT / '.implementation-work' / 'professional-initialize-result.json').write_text(json.dumps(record), encoding='utf-8')
    print(json.dumps(record))


if __name__ == '__main__':
    main()
