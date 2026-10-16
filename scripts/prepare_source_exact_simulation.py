import argparse
import hashlib
from pathlib import Path

import rfc8785
from medical_contracts.catalogue import SchemaCatalogue
from medical_document.native import NativeParser, SourceInput


def main():
    parser = argparse.ArgumentParser(description='从获授权模拟TXT原件取得真实解析节点')
    parser.add_argument('--root', type=Path, required=True)
    parser.add_argument('--source', type=Path, required=True)
    parser.add_argument('--source-version', required=True)
    parser.add_argument('--parse-version', required=True)
    parser.add_argument('--sha256', required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    root = args.root.resolve(strict=True)
    source = args.source.resolve(strict=True)
    source.relative_to(root / '.implementation-work')
    args.output.parent.resolve(strict=True).relative_to(root / '.implementation-work')
    native = NativeParser(SchemaCatalogue(root))
    result = native.parse(SourceInput(source, 'TXT', args.source_version, args.parse_version, args.sha256, 'SIMULATION'))
    if result.status != 'COMPLETE' or len(result.nodes) != 1 or result.issues:
        raise ValueError('SIMULATION_TXT_PARSE_INCOMPLETE')
    node = result.nodes[0]
    payload = {'data_kind': 'SIMULATION', 'source_version': result.source_version, 'parse_version': result.parse_version,
        'original_sha256': result.original_sha256, 'status': result.status, 'processor_manifest': dict(result.processor_manifest),
        'node_id': node.node_id, 'raw_text': node.raw_text, 'quote_hash': node.quote_hash,
        'locator': node.locator.model_dump(mode='json'), 'heading_path': list(node.heading_path)}
    payload['node_hash'] = hashlib.sha256(rfc8785.dumps(payload)).hexdigest()
    with args.output.open('xb') as target:
        target.write(rfc8785.dumps(payload))
    args.output.chmod(0o600)


if __name__ == '__main__':
    main()
