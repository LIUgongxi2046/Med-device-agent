import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

import native_parser_cache as cache


class NativeParserCacheTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.env = patch.dict('os.environ', {'MEDICAL_NATIVE_PARSE_CACHE': 'on', 'MEDICAL_NATIVE_PARSE_CACHE_DIR': self.temp.name})
        self.env.start()
        self.addCleanup(self.env.stop)
        self.identity = {'raw_source': 'a', 'manifest_order': ['a', 'b'], 'parser': 'p', 'jdk': 'j', 'library': 'l', 'config': 'c'}
        self.ident = patch.object(cache, 'identity', side_effect=lambda *a: dict(self.identity))
        self.ident.start()
        self.addCleanup(self.ident.stop)
        self.native = patch.object(cache.subprocess, 'run', return_value=subprocess.CompletedProcess(['java'], 0, '[{"create":"valid"}]', ''))
        self.run = self.native.start()
        self.addCleanup(self.native.stop)

    def parse(self):
        return cache.run_json(['java', 'parser', 'input'], source_bytes=b'raw', libraries=[], parser='parser', mode='ALTER', expected_count=1, check=True)

    def test_hit_reuses_only_identical_successful_evidence(self):
        self.assertEqual(self.parse(), self.parse())
        self.assertEqual(1, self.run.call_count)

    def test_every_identity_component_invalidates(self):
        self.parse()
        for key in list(self.identity):
            self.identity[key] = str(self.identity[key]) + 'changed'
            self.parse()
        self.assertEqual(7, self.run.call_count)

    def test_failed_or_malformed_native_result_never_cached(self):
        for value in [subprocess.CompletedProcess([], 2, '[]', 'error'), subprocess.CompletedProcess([], 0, '{}', ''), subprocess.CompletedProcess([], 0, '[]', ''), subprocess.CompletedProcess([], 0, 'invalid', '')]:
            self.run.return_value = value
            with self.assertRaises((ValueError, subprocess.CalledProcessError)):
                self.parse()
            self.assertEqual([], list(Path(self.temp.name).glob('*.json')))

    def test_corruption_is_reparsed_not_accepted(self):
        self.parse()
        entry = next(Path(self.temp.name).glob('*.json'))
        record = json.loads(entry.read_text()); record['output'] = '[{"create":"corrupt"}]'
        entry.write_text(json.dumps(record))
        self.assertEqual([{'create':'valid'}], self.parse())
        self.assertEqual(2, self.run.call_count)

    def test_no_cache_mode_always_executes_and_preserves_existing_evidence(self):
        self.parse()
        entry = next(Path(self.temp.name).glob('*.json')); before = entry.read_bytes()
        with patch.dict('os.environ', {'MEDICAL_NATIVE_PARSE_CACHE':'off'}):
            self.parse(); self.parse()
        self.assertEqual(3, self.run.call_count)
        self.assertEqual(before, entry.read_bytes())

    def test_mid_execution_input_change_fails_without_cache(self):
        self.run.side_effect = lambda *a, **k: (self.identity.update(parser='changed') or subprocess.CompletedProcess([], 0, '[{}]', ''))
        with self.assertRaisesRegex(RuntimeError, 'INPUTS_CHANGED'):
            self.parse()
        self.assertEqual([], list(Path(self.temp.name).glob('*.json')))

    def test_file_hash_tracks_modified_bytes(self):
        p = Path(self.temp.name) / 'input';p.write_bytes(b'first');a=cache.file_digest(p);p.write_bytes(b'other');self.assertNotEqual(a,cache.file_digest(p))
