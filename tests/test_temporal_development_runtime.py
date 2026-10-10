import argparse
import hashlib
import json
from pathlib import Path
import sqlite3
import sys
import unittest


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
import local_temporal


class TemporalDevelopmentRuntimeTest(unittest.TestCase):
    def test_actual_persistent_namespace_survives_verified_process_restart(self):
        before = local_temporal.status()
        self.assertEqual(before["health"]["status"], "SERVING")
        self.assertEqual(before["namespace"]["namespaceInfo"]["name"], "medical-local")
        self.assertEqual(before["namespace"]["namespaceInfo"]["state"], "NAMESPACE_STATE_REGISTERED")
        self.assertFalse(before["production_compatible"])
        stopped = local_temporal.stop()
        self.assertEqual(stopped["status"], "STOPPED")
        self.assertTrue(stopped["database_preserved"])
        self.assertEqual(stopped["original_pid"], before["deployment"]["pid"])
        database = local_temporal.DIRECTORY / "server.db"
        with sqlite3.connect(database.as_uri() + "?mode=ro", uri=True) as connection:
            integrity = connection.execute("PRAGMA integrity_check").fetchall()
        self.assertEqual(integrity, [("ok",)])
        self.assertFalse(local_temporal.STATE.exists())
        previous = before["deployment"]
        after = local_temporal.start(argparse.Namespace(port=int(previous["address"].rsplit(":", 1)[1]),
            http_port=previous["http_port"], metrics_port=previous["metrics_port"], namespace=previous["namespace"]))
        self.assertEqual(after["health"]["status"], "SERVING")
        self.assertEqual(after["namespace"]["namespaceInfo"]["id"], before["namespace"]["namespaceInfo"]["id"])
        self.assertEqual(after["namespace"]["config"], before["namespace"]["config"])
        self.assertNotEqual(after["deployment"]["deployment_id"], previous["deployment_id"])
        self.assertEqual(after["deployment"]["executable_sha256"], previous["executable_sha256"])
        self.assertEqual(after["deployment"]["database"], previous["database"])
        self.assertIn("--headless", after["deployment"]["arguments"])
        self.assertIn("127.0.0.1", after["deployment"]["arguments"])
        stopped_path = ROOT / stopped["receipt"]
        evidence = {"scope": "ACTUAL_TEMPORAL_DEVELOPMENT_SERVICE_RESTART", "status": "PASS",
            "before": before, "stopped": stopped, "after": after, "sqlite_integrity": "ok",
            "stop_receipt_sha256": hashlib.sha256(stopped_path.read_bytes()).hexdigest(),
            "business_workflow_verified": False, "production_compatible": False}
        with (OUTPUT / "temporal-result.json").open("x", encoding="utf-8") as saved:
            json.dump(evidence, saved, ensure_ascii=False, indent=2)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="验证实际Temporal本地服务正常关闭与持久Namespace重新读取")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    OUTPUT = args.output.resolve(strict=True)
    if not OUTPUT.is_relative_to(ROOT / ".implementation-work"):
        raise ValueError("TEMPORAL_PROJECT_EVIDENCE_DIRECTORY_REQUIRED")
    local_temporal.os.umask(0o077)
    unittest.main(argv=[sys.argv[0]], verbosity=2)
