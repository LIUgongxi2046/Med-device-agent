import argparse
from concurrent.futures import ThreadPoolExecutor
import hashlib
import json
from pathlib import Path
import subprocess
from urllib.parse import quote


ROOT = Path(__file__).resolve().parents[1]


def download(artifact):
    destination, url, expected = artifact
    if not destination.exists():
        subprocess.run(['curl', '--fail', '--location', '--connect-timeout', '15', '--max-time', '180',
            '--output', str(destination), url], check=True, capture_output=True)
    actual = hashlib.sha256(destination.read_bytes()).hexdigest()
    if actual != expected:
        raise ValueError('Harness 官方资源摘要不一致：' + destination.name)
    return {'sha256': actual, 'size_bytes': destination.stat().st_size}


def main():
    parser = argparse.ArgumentParser(description='取得并核验 Harness 官方构建要求的完整资源')
    parser.add_argument('--source', required=True, type=Path)
    parser.add_argument('--target', required=True)
    args = parser.parse_args()
    source = args.source.resolve()
    lock = json.loads((source / 'scripts/primary-runtime/lock.json').read_bytes())
    target = lock['targets'][args.target]
    cache = ROOT / '.build-work/dsh-primary-runtime-downloads'
    cache.mkdir(parents=True, exist_ok=True)
    node = 'node-v' + lock['nodeVersion'] + '-' + target['nodeArchive']
    python = 'cpython-' + lock['pythonVersion'] + '+' + lock['pythonRelease'] + '-' + target['pythonTarget'] + '-install_only_stripped.tar.gz'
    artifacts = [(cache / target['nodeSha256'], 'https://nodejs.org/dist/v' + lock['nodeVersion'] + '/' + node, target['nodeSha256']),
        (cache / target['pythonSha256'], 'https://github.com/astral-sh/python-build-standalone/releases/download/' + lock['pythonRelease'] + '/' + quote(python, safe=''), target['pythonSha256'])]
    artifacts.extend((cache / item['sha256'], item['url'], item['sha256']) for item in target['wheels'] + lock['wheels'])
    with ThreadPoolExecutor(max_workers=4) as downloads:
        receipts = list(downloads.map(download, artifacts))
    result = {'source_lock_sha256': hashlib.sha256((source / 'scripts/primary-runtime/lock.json').read_bytes()).hexdigest(),
        'target': args.target, 'complete_asset_count': len(receipts), 'assets': receipts}
    (ROOT / '.implementation-work/harness-download-result.json').write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps({'complete_asset_count': len(receipts), 'all_sha256_verified': True}))


if __name__ == '__main__':
    main()
