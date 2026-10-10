import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
from urllib.request import urlopen
import unittest
from uuid import uuid4
from zipfile import ZipFile


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
from local_runtime import mysql, settings, verify_application_deployment


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


class ApplicationDeploymentRuntimeChecks(unittest.TestCase):
    def test_current_build_and_packaged_frontend_are_actually_served(self):
        receipt_path = Path(os.environ["MEDICAL_APPLICATION_BUILD_RECEIPT"]).resolve(strict=True)
        self.assertTrue(receipt_path.is_relative_to(ROOT / ".implementation-work"))
        receipt = json.loads(receipt_path.read_bytes())
        self.assertEqual(receipt["check_id"], "IC-03-JAVA")
        self.assertEqual(receipt["exit_code"], 0)
        self.assertIs(receipt["inputs_unchanged"], True)
        for item in receipt["inputs"] + receipt["artifacts"]:
            self.assertEqual(digest(Path(item["path"])), item["sha256"], item["path"])
        deployment = verify_application_deployment()
        process_uid = subprocess.run(["/bin/ps", "-p", str(deployment["pid"]), "-o", "uid="],
            capture_output=True, text=True, check=True, timeout=10).stdout.strip()
        self.assertEqual(process_uid, str(os.getuid()))
        source = ROOT / deployment["source"]
        artifact = ROOT / deployment["artifact"]
        self.assertEqual(digest(source), deployment["sha256"])
        self.assertEqual(digest(artifact), deployment["sha256"])
        build_artifact = next(item for item in receipt["artifacts"] if Path(item["path"]) == source)
        self.assertEqual(build_artifact["sha256"], deployment["sha256"])
        pointer = ROOT / ".implementation-work/java-runtime-checks/current.json"
        summary_ref = json.loads(pointer.read_bytes())
        summary_path = Path(summary_ref["report"]).resolve(strict=True)
        self.assertEqual(digest(summary_path), summary_ref["sha256"])
        summary = json.loads(summary_path.read_bytes())
        self.assertGreater(summary["totals"]["tests"], 0)
        for name in ("failures", "errors", "skipped"):
            self.assertEqual(summary["totals"][name], 0)
        self.assertIs(summary["frontend_build_verified"], True)
        for item in summary["artifacts"]:
            self.assertEqual(digest(Path(item["path"])), item["sha256"], item["path"])
        frontend = summary_path.parent / "frontend"
        files = sorted(path for path in frontend.rglob("*") if path.is_file())
        self.assertGreater(len(files), 1)
        observations = []
        with ZipFile(artifact) as package:
            entries = {name for name in package.namelist() if name.startswith("BOOT-INF/classes/static/") and not name.endswith("/")}
            expected_entries = {"BOOT-INF/classes/static/" + path.relative_to(frontend).as_posix() for path in files}
            self.assertEqual(entries, expected_entries)
            for path in files:
                relative = path.relative_to(frontend).as_posix()
                original = path.read_bytes()
                self.assertEqual(package.read("BOOT-INF/classes/static/" + relative), original)
                # 当前运行服务必须提供相同发布制品的完整字节。
                with urlopen("http://127.0.0.1:18080/" + relative, timeout=20) as response:
                    self.assertEqual(response.status, 200)
                    self.assertEqual(response.read(), original, relative)
                observations.append({"path": relative, "sha256": digest(path)})
        index = (frontend / "index.html").read_bytes()
        project_uploads = "/projects/" + str(uuid4()) + "/uploads"
        task_configuration = "/projects/" + str(uuid4()) + "/tasks/" + str(uuid4()) + "/config"
        routes = ("/auth/login", "/enterprises/members", "/enterprises/roles", "/projects", "/access", "/projects/" + str(uuid4()) + "/templates", project_uploads, task_configuration)
        for route in routes:
            with urlopen("http://127.0.0.1:18080" + route, timeout=20) as response:
                self.assertEqual(response.status, 200)
                self.assertEqual(response.read(), index, route)
        with urlopen("http://127.0.0.1:18080" + project_uploads, timeout=20) as response:
            self.assertEqual(response.status, 200)
            self.assertEqual(response.read(), index)
        with urlopen("http://127.0.0.1:18080" + task_configuration, timeout=20) as response:
            self.assertEqual(response.status, 200)
            self.assertEqual(response.read(), index)
        with urlopen("http://127.0.0.1:19340/status", timeout=20) as response:
            self.assertEqual(response.status, 200)
            capacity = json.load(response)
        self.assertTrue(capacity["Volumes"])
        self.assertTrue(all(volume["ReadOnly"] is False for volume in capacity["Volumes"]))
        counts = json.loads(mysql("SELECT JSON_OBJECT('enterprises',(SELECT COUNT(*) FROM medical_local.tenant),"
            "'members',(SELECT COUNT(*) FROM medical_local.tenant_member),'projects',(SELECT COUNT(*) FROM medical_local.project),"
            "'tasks',(SELECT COUNT(*) FROM medical_local.task_head),'operations',(SELECT COUNT(*) FROM medical_local.operation_record),"
            "'migrations',(SELECT COUNT(*) FROM medical_local.DATABASECHANGELOG))", settings()["root_password"]).stdout)
        self.assertEqual(deployment, verify_application_deployment())
        result = {"scope": "CURRENT_LOCAL_APPLICATION_BUILD_PROCESS_AND_ALL_STATIC_BYTES",
            "deployment": deployment, "build_receipt": str(receipt_path), "build_receipt_sha256": digest(receipt_path),
            "java": summary["totals"], "static_files": observations, "page_routes": list(routes),
            "project_upload_direct_entry_and_refresh_verified": True,
            "task_configuration_direct_entry_and_refresh_verified": True,
            "capacity": capacity, "counts": counts,
            "new_source_deployed": True, "customer_success_verified": False}
        with Path(os.environ["MEDICAL_APPLICATION_DEPLOYMENT_RESULT"]).open("x", encoding="utf-8") as output:
            json.dump(result, output, ensure_ascii=False, indent=2)


if __name__ == "__main__":
    unittest.main(verbosity=2)
