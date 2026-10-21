import json
from pathlib import Path
import time

from deepseek_harness import HarnessClient, HarnessConfig


ROOT = Path(__file__).resolve().parents[1]


def main():
    started = time.monotonic()
    workspace = ROOT / '.build-work/professional-workspace'
    workspace.mkdir(parents=True, exist_ok=True, mode=0o700)
    config = HarnessConfig(profile='medical-agent', dsh_home=str(ROOT / 'professional-executor'), cwd=str(workspace),
        env={'TMPDIR': str(ROOT / '.build-work')}, initialize_timeout_seconds=45, request_timeout_seconds=45, shutdown_timeout_seconds=2)
    with HarnessClient(config) as client:
        result = client.initialize(cwd=str(workspace), provider='enterprise-model', model='SIMULATION')
        if result.serverInfo is None or result.serverInfo.name != 'deepseek-harness-sdk-runtime':
            raise ValueError('专业运行配置未返回官方SDK身份')
        inventory = json.loads((ROOT / '.build-work/professional-inventory.json').read_bytes())
        expected = {'read_artifact', 'load_skill', 'ask_user_question', 'submit_plan_candidate'}
        if inventory['role'] != 'Planner' or {tool['name'] for tool in inventory['tools']} != expected:
            raise ValueError('实际工具清单与Planner角色不一致')
        if inventory['providers'] != [{'id': 'enterprise-model', 'name': '企业模型接口'}]:
            raise ValueError('实际模型清单包含未发布路由')
        expected_plugins = {
            'cordis:include', '@medical-agent/professional-executor',
            '@deepseek-ai/dsh-sdk-app', '@deepseek-ai/dsh-sdk-jsonrpc-server',
            '@deepseek-ai/cordis-plugin-timer', '@deepseek-ai/dsh-llm',
            '@deepseek-ai/dsh-session', '@deepseek-ai/dsh-session-projection',
            '@deepseek-ai/dsh-fs-local', '@deepseek-ai/dsh-working-directory',
            '@deepseek-ai/dsh-system-prompt', '@deepseek-ai/dsh-tools',
            '@deepseek-ai/dsh-agent', '@deepseek-ai/dsh-agent-loop',
            '@deepseek-ai/dsh-session-persistence-jsonl',
        }
        actual_plugins = [plugin['name'] for plugin in inventory['plugins']]
        if len(actual_plugins) != len(expected_plugins) or set(actual_plugins) != expected_plugins or any(len(tool['parameters_sha256']) != 64 for tool in inventory['tools']):
            raise ValueError('实际专业插件或Schema清单不完整')
    record = {'profile': 'medical-agent', 'input_data_kind': 'SIMULATION', 'public_initialize_verified': True,
        'runtime_inventory': inventory, 'elapsed_seconds': time.monotonic() - started}
    (ROOT / '.implementation-work' / 'professional-initialize-result.json').write_text(json.dumps(record), encoding='utf-8')
    print(json.dumps(record))


if __name__ == '__main__':
    main()
