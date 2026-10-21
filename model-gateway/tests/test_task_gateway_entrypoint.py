"""Standard-library-only boundary checks, not claims about an installed provider SDK."""
import ast
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

GATEWAY = Path(__file__).resolve().parents[1]
ENTRYPOINT = GATEWAY/'task_gateway_entrypoint.py'
FAILURE = b'TASK_GATEWAY_STOPPED_REVIEW_ORIGINAL_OPERATION\n'


class EntrypointTests(unittest.TestCase):
    def call(self, arguments, *, isolated=True, data=b''):
        return subprocess.run([sys.executable, *(['-I'] if isolated else []),
            str(ENTRYPOINT), *arguments], input=data, capture_output=True, timeout=15)

    def rejected(self, result):
        self.assertEqual(result.returncode, 1)
        self.assertEqual(result.stdout, b'')
        self.assertEqual(result.stderr, FAILURE)

    def test_requires_isolated_interpreter_and_closed_argv(self):
        self.rejected(self.call([], isolated=False))
        self.rejected(self.call(['--run-token', 'secret-must-not-echo']))
        self.rejected(self.call(['--config', '/absent', '--package-root', '/absent',
                                 '--provider-body', 'secret-must-not-echo']))

    def test_bounds_argv_and_does_not_echo_hostile_values(self):
        self.rejected(self.call(['--config', '/'+('x'*8192), '--package-root', '/absent']))

    def test_missing_configuration_or_package_fails_closed(self):
        self.rejected(self.call(['--config', '/absent-task-gateway-config',
                                 '--package-root', str(GATEWAY/'src')]))
        with tempfile.TemporaryDirectory() as temporary:
            config = Path(temporary)/'config.json'
            config.write_text('{}')
            config.chmod(0o600)
            self.rejected(self.call(['--config', str(config), '--package-root', str(GATEWAY/'src')]))

    def test_symlink_or_relative_configuration_is_rejected(self):
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary)
            (path/'config.json').write_text('{}')
            (path/'config-link.json').symlink_to(path/'config.json')
            for config in (str(path/'config-link.json'), 'relative-config.json'):
                self.rejected(self.call(['--config', config, '--package-root', str(GATEWAY/'src')]))

    def test_import_failure_is_sanitized_without_sdk_substitutes(self):
        # Empty package directories exercise only the pinned launcher failure boundary.
        # They are not fake SDKs and no successful gateway outcome is asserted.
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            config = root/'config.json'
            config.write_text('{}')
            for name in ('medical_model_gateway', 'medical_contracts'):
                (root/name).mkdir()
                (root/name/'__init__.py').write_text('')
            self.rejected(self.call(['--config', str(config), '--package-root', str(root)],
                                    data=b'{"run_token":"secret-never-echo"}'))

    def test_all_changed_python_sources_parse_without_importing_dependencies(self):
        paths = [ENTRYPOINT, GATEWAY/'src/medical_model_gateway/task_gateway.py',
                 GATEWAY/'src/medical_model_gateway/formal_invocation_adapter.py',
                 GATEWAY/'src/medical_model_gateway/formal_verification_worker.py',
                 GATEWAY/'tests/test_task_gateway.py', Path(__file__)]
        for path in paths:
            with self.subTest(path=path.name):
                ast.parse(path.read_bytes(), filename=str(path))


if __name__ == '__main__':
    unittest.main()
