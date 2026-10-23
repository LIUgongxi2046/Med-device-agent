import json
import hashlib
import subprocess
import zipfile
from datetime import datetime, timezone
from urllib.request import urlopen

import local_runtime as runtime


def main():
    deployment = runtime.verify_application_deployment()
    frontend = runtime.ROOT / "web/dist"
    files = {path.relative_to(frontend).as_posix(): path for path in frontend.rglob("*") if path.is_file()}
    prefix = "BOOT-INF/classes/static/"
    with zipfile.ZipFile(runtime.ROOT / deployment["artifact"]) as archive:
        names = [item.filename for item in archive.infolist() if item.filename.startswith(prefix) and not item.is_dir()]
        if not files or len(names) != len(set(names)) or {name[len(prefix):] for name in names} != set(files):
            raise RuntimeError("ACCEPTANCE_FRONTEND_FILES_MISMATCH")
        for name, path in files.items():
            if hashlib.sha256(path.read_bytes()).digest() != hashlib.sha256(archive.read(prefix + name)).digest():
                raise RuntimeError("ACCEPTANCE_FRONTEND_BYTES_MISMATCH")
    environment = runtime.environment()
    environment["REDISCLI_AUTH"] = runtime.settings()["redis_password"]
    response = subprocess.run([str(runtime.REDIS / "redis-cli"), "-h", "127.0.0.1", "-p", "16379", "PING"],
        env=environment, capture_output=True, text=True, check=True, timeout=10)
    if response.stdout.strip() != "PONG":
        raise RuntimeError("ACCEPTANCE_REDIS_NOT_READY")
    if runtime.mysql("SELECT 1", runtime.settings()["root_password"]).stdout.strip() != "1":
        raise RuntimeError("ACCEPTANCE_DATABASE_NOT_READY")
    for port in (18080, 18083):
        with urlopen("http://127.0.0.1:" + str(port) + "/auth/csrf", timeout=30) as response:
            if response.status != 200 or "application/json" not in response.headers["Content-Type"]:
                raise RuntimeError("ACCEPTANCE_HTTP_NOT_READY")
            value = json.load(response)
            if not value.get("header_name") or not value.get("token"):
                raise RuntimeError("ACCEPTANCE_CSRF_NOT_READY")
    print(json.dumps({"state": "READY", "verified_at": datetime.now(timezone.utc).isoformat(), "pid": deployment["pid"], "artifact_sha256": deployment["sha256"],
        "ports": [18080, 18083], "redis": "PONG", "database": "SELECT_1", "static_files": len(files), "static_files_match": True}))


if __name__ == "__main__":
    main()
