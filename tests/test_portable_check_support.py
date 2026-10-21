import argparse
import importlib.util
import json
from pathlib import Path
import sys
from tempfile import TemporaryDirectory
import unittest

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'scripts'))
from capture_check import run_check


class CaptureTests(unittest.TestCase):
    def capture(self, directory, code, artifacts=()):
        directory = Path(directory)
        source = directory / 'source.txt'
        source.write_text('original')
        args = argparse.Namespace(cwd=directory, output_dir=directory/'evidence', check_id='TARGETED',
            kind='UNIT', baseline_id='portability', timeout=5, input=[source], artifact=list(artifacts),
            command=[sys.executable, '-c', code])
        result = run_check(args)
        return result, json.loads((args.output_dir/'receipt.json').read_text()), args

    def test_success_records_hashes_and_refuses_reuse(self):
        with TemporaryDirectory() as directory:
            result, receipt, args = self.capture(directory, 'print("actual command")')
            self.assertEqual(0, result)
            self.assertTrue(receipt['inputs_unchanged'])
            self.assertTrue(receipt['inputs'])
            with self.assertRaises(FileExistsError): run_check(args)

    def test_command_failure_is_not_pass(self):
        with TemporaryDirectory() as directory:
            result, receipt, _ = self.capture(directory, 'raise SystemExit(7)')
            self.assertEqual(7, result)
            self.assertEqual(7, receipt['command_exit_code'])

    def test_changed_input_fails(self):
        with TemporaryDirectory() as directory:
            result, receipt, _ = self.capture(directory, 'from pathlib import Path; Path("source.txt").write_text("changed")')
            self.assertNotEqual(0, result)
            self.assertFalse(receipt['inputs_unchanged'])

    def test_missing_required_artifact_fails(self):
        with TemporaryDirectory() as directory:
            result, receipt, _ = self.capture(directory, 'pass', ['missing.json'])
            self.assertNotEqual(0, result)
            self.assertIn('FileNotFoundError', receipt['error'])


if __name__ == '__main__': unittest.main()
