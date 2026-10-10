import json
from pathlib import Path
import time

from deepseek_harness import HarnessClient, HarnessConfig
from pydantic import BaseModel, ConfigDict


ROOT = Path(__file__).resolve().parents[1]


class ShutdownResponse(BaseModel):
    model_config = ConfigDict(extra='forbid')


def main():
    home = ROOT / '.build-work' / 'harness-carrier-home'
    home.mkdir(parents=True, exist_ok=True)
    started = time.monotonic()
    config = HarnessConfig(profile='sdk', dsh_home=str(home), cwd=str(ROOT),
        env={'TMPDIR': str(ROOT / '.build-work')}, request_timeout_seconds=45, shutdown_timeout_seconds=2)
    with HarnessClient(config) as client:
        client.request('shutdown', None, response_model=ShutdownResponse, timeout_seconds=45)
    record = {'carrier_profile': 'sdk', 'official_commit': '5badb15009ae1756c3afe0ae0cef1faafc290ccc',
        'public_sdk_rpc_verified': True, 'professional_initialize_verified': False,
        'elapsed_seconds': time.monotonic() - started}
    (ROOT / '.implementation-work' / 'harness-carrier-result.json').write_text(json.dumps(record), encoding='utf-8')
    print(json.dumps(record))


if __name__ == '__main__':
    main()
