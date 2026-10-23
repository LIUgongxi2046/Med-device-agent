import json
import socket
import subprocess
from datetime import datetime, timezone

import local_runtime as runtime


def main():
    entries = []
    for name, port in (("mysql.pid", 13306), ("redis.pid", 16379)):
        path = runtime.RUNTIME / name
        if not path.exists():
            continue
        pid = int(path.read_text(encoding="ascii"))
        if pid <= 1 or path.is_symlink():
            raise RuntimeError("LOCAL_SERVICE_PID_INVALID")
        result = subprocess.run(["/bin/ps", "-p", str(pid), "-o", "pid="], capture_output=True, text=True, check=False, timeout=10)
        if result.returncode != 1 or result.stdout.strip():
            raise RuntimeError("LOCAL_SERVICE_PROCESS_STILL_PRESENT")
        with socket.socket() as probe:
            if probe.connect_ex(("127.0.0.1", port)) == 0:
                raise RuntimeError("LOCAL_SERVICE_PORT_STILL_PRESENT")
        entries.append((path, pid))
    stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    for path, pid in entries:
        target = path.with_name("terminated-" + stamp + "-" + str(pid) + "-" + path.name)
        if target.exists():
            raise RuntimeError("LOCAL_SERVICE_ARCHIVE_ALREADY_PRESENT")
        path.rename(target)
    print(json.dumps({"archived": len(entries)}))


if __name__ == "__main__":
    main()
