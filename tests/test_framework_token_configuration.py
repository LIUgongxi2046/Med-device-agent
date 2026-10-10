import hashlib
import json
import os
from pathlib import Path
import sys
import unittest


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
from local_runtime import ruoyi_token_secret


class FrameworkTokenConfigurationChecks(unittest.TestCase):
    def test_existing_actual_key_is_read_without_rotation_or_rewriting(self):
        path = ROOT / ".implementation-work/runtime/ruoyi-token-secret.json"
        self.assertTrue(path.is_file())
        before = (hashlib.sha256(path.read_bytes()).hexdigest(), path.stat().st_mtime_ns)
        first = ruoyi_token_secret()
        second = ruoyi_token_secret()
        self.assertTrue(first == second, "读取实际框架配置必须使用同一密钥身份")
        self.assertEqual(before, (hashlib.sha256(path.read_bytes()).hexdigest(), path.stat().st_mtime_ns))

    def test_actual_configuration_receipt_binds_the_private_owned_key(self):
        path = ROOT / ".implementation-work/runtime/ruoyi-token-secret.json"
        receipt = json.loads((ROOT / ".implementation-work/runtime/ruoyi-token-configuration-receipt.json").read_bytes())
        value = ruoyi_token_secret()
        self.assertEqual(receipt["key_id"], value["key_id"])
        self.assertEqual(receipt["purpose"], value["purpose"])
        self.assertEqual(receipt["key_file"], str(path.relative_to(ROOT)))
        self.assertEqual(receipt["key_file_sha256"], hashlib.sha256(path.read_bytes()).hexdigest())
        self.assertEqual(path.stat().st_uid, os.getuid())
        self.assertEqual(path.stat().st_mode & 0o777, 0o600)
        self.assertEqual(path.parent.stat().st_mode & 0o777, 0o700)


if __name__ == "__main__":
    unittest.main(verbosity=2)
