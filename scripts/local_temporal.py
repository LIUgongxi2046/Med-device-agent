import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import signal
import socket
import stat
import subprocess
import time
import uuid


ROOT = Path(__file__).resolve().parents[1]
DIRECTORY = ROOT / ".implementation-work/runtime/temporal-dev"
EXECUTABLE = ROOT / ".implementation-work/toolchains/temporal-cli-1.9.1/temporal"
STATE = DIRECTORY / "deployment.json"
VERSION = "temporal version 1.9.1 (Server 1.32.0, UI 2.54.1)"
FLAGS = ["--disable-config-env", "--disable-config-file"]


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def private_directory():
    DIRECTORY.mkdir(mode=0o700, parents=True, exist_ok=True)
    metadata = DIRECTORY.lstat()
    if not stat.S_ISDIR(metadata.st_mode) or stat.S_IMODE(metadata.st_mode) != 0o700 or metadata.st_uid != os.getuid():
        raise RuntimeError("TEMPORAL_PRIVATE_DIRECTORY_REQUIRED")
    temporary = DIRECTORY / "temporary"
    temporary.mkdir(mode=0o700, exist_ok=True)
    metadata = temporary.lstat()
    if not stat.S_ISDIR(metadata.st_mode) or stat.S_IMODE(metadata.st_mode) != 0o700 or metadata.st_uid != os.getuid():
        raise RuntimeError("TEMPORAL_PRIVATE_TEMPORARY_REQUIRED")
    return {"PATH": os.defpath, "LANG": "en_US.UTF-8", "LC_ALL": "en_US.UTF-8", "TMPDIR": str(temporary)}


def installed():
    if EXECUTABLE.is_symlink() or not EXECUTABLE.is_file():
        raise RuntimeError("TEMPORAL_OFFICIAL_EXECUTABLE_REQUIRED")
    result = subprocess.run([str(EXECUTABLE), *FLAGS, "--version"], env=private_directory(),
        check=True, capture_output=True, text=True, timeout=10)
    if result.stdout.strip() != VERSION:
        raise RuntimeError("TEMPORAL_FIXED_VERSION_REQUIRED")
    return digest(EXECUTABLE)


def process_field(pid, field):
    return subprocess.run(["/bin/ps", "-ww", "-p", str(pid), "-o", field + "="],
        capture_output=True, text=True, check=True, timeout=5, env=private_directory()).stdout.strip()


def record():
    metadata = STATE.lstat()
    if not stat.S_ISREG(metadata.st_mode) or stat.S_IMODE(metadata.st_mode) != 0o600 or metadata.st_uid != os.getuid():
        raise RuntimeError("TEMPORAL_PRIVATE_STATE_REQUIRED")
    state = json.loads(STATE.read_bytes())
    if state["os_uid"] != os.getuid() or state["executable"] != str(EXECUTABLE.relative_to(ROOT)):
        raise RuntimeError("TEMPORAL_DEPLOYMENT_IDENTITY_CHANGED")
    if installed() != state["executable_sha256"]:
        raise RuntimeError("TEMPORAL_EXECUTABLE_CHANGED")
    return state


def verify():
    state = record()
    pid = state["pid"]
    if process_field(pid, "uid") != str(state["os_uid"]) or process_field(pid, "lstart") != state["process_started"]:
        raise RuntimeError("TEMPORAL_PROCESS_IDENTITY_CHANGED")
    command = process_field(pid, "command")
    if command != " ".join([str(EXECUTABLE), *state["arguments"]]):
        raise RuntimeError("TEMPORAL_PROCESS_COMMAND_CHANGED")
    return state


def client(state, action, check=True):
    return subprocess.run([str(EXECUTABLE), *FLAGS, "--address", state["address"], "--namespace", state["namespace"],
        "--command-timeout", "5s", "--client-connect-timeout", "2s", "--output", "json", *action],
        env=private_directory(), check=check, capture_output=True, text=True, timeout=8)


def status():
    state = verify()
    health = json.loads(client(state, ["operator", "cluster", "health"]).stdout)
    namespace = json.loads(client(state, ["operator", "namespace", "describe"]).stdout)
    database = DIRECTORY / "server.db"
    metadata = database.lstat()
    if not stat.S_ISREG(metadata.st_mode) or stat.S_IMODE(metadata.st_mode) != 0o600 or metadata.st_uid != os.getuid():
        raise RuntimeError("TEMPORAL_PRIVATE_DATABASE_REQUIRED")
    return {"scope": "ACTUAL_LOCAL_DEVELOPMENT_SERVICE", "deployment": state, "health": health,
        "namespace": namespace, "database_bytes": metadata.st_size, "production_compatible": False}


def start(args):
    environment = private_directory()
    if STATE.exists() or STATE.is_symlink():
        raise RuntimeError("TEMPORAL_EXISTING_DEPLOYMENT_REQUIRES_VERIFICATION")
    executable_sha256 = installed()
    if len({args.port, args.http_port, args.metrics_port}) != 3:
        raise ValueError("TEMPORAL_DISTINCT_PORTS_REQUIRED")
    for port in (args.port, args.http_port, args.metrics_port):
        if not 1024 <= port <= 65535:
            raise ValueError("TEMPORAL_LOCAL_PORT_REQUIRED")
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as probe:
            probe.bind(("127.0.0.1", port))
    if args.namespace != "medical-local":
        raise ValueError("TEMPORAL_LOCAL_NAMESPACE_REQUIRED")
    deployment_id = str(uuid.uuid4())
    log = DIRECTORY / (deployment_id + ".log")
    arguments = [*FLAGS, "--log-format", "json", "server", "start-dev",
        "--headless", "--ip", "127.0.0.1", "--port", str(args.port), "--http-port", str(args.http_port),
        "--metrics-port", str(args.metrics_port), "--namespace", args.namespace, "--db-filename", str(DIRECTORY / "server.db")]
    with log.open("xb") as output:
        process = subprocess.Popen([str(EXECUTABLE), *arguments], cwd=ROOT, env=environment, stdin=subprocess.DEVNULL,
            stdout=output, stderr=subprocess.STDOUT, start_new_session=True)
    state = {"deployment_id": deployment_id, "scope": "LOCAL_DEVELOPMENT_ONLY", "pid": process.pid,
        "os_uid": os.getuid(), "process_started": process_field(process.pid, "lstart"),
        "created_at": datetime.now(timezone.utc).isoformat(), "executable": str(EXECUTABLE.relative_to(ROOT)),
        "executable_sha256": executable_sha256, "version": VERSION, "arguments": arguments,
        "address": "127.0.0.1:" + str(args.port), "http_port": args.http_port, "metrics_port": args.metrics_port,
        "namespace": args.namespace, "database": str((DIRECTORY / "server.db").relative_to(ROOT)), "log": str(log.relative_to(ROOT))}
    # 启动身份立即保存；就绪失败保留原进程与日志供核查。
    with STATE.open("x", encoding="utf-8") as saved:
        json.dump(state, saved, ensure_ascii=False, indent=2)
    deadline = time.monotonic() + 60
    while time.monotonic() < deadline:
        if process.poll() is not None:
            raise RuntimeError("TEMPORAL_ORIGINAL_PROCESS_TERMINATED:" + str(process.returncode))
        if client(state, ["operator", "cluster", "health"], check=False).returncode == 0:
            return status()
        time.sleep(0.5)
    raise RuntimeError("TEMPORAL_ORIGINAL_PROCESS_NOT_READY")


def stop():
    state = verify()
    pid = state["pid"]
    os.kill(pid, signal.SIGTERM)
    deadline = time.monotonic() + 30
    while time.monotonic() < deadline:
        result = subprocess.run(["/bin/ps", "-p", str(pid), "-o", "stat="], capture_output=True,
            text=True, check=False, timeout=5, env=private_directory())
        if result.returncode != 0 or result.stdout.strip().startswith("Z"):
            target = DIRECTORY / (state["deployment_id"] + "-stopped.json")
            state["stopped_at"] = datetime.now(timezone.utc).isoformat()
            with target.open("x", encoding="utf-8") as saved:
                json.dump(state, saved, ensure_ascii=False, indent=2)
            STATE.unlink()
            return {"status": "STOPPED", "original_pid": pid, "deployment_id": state["deployment_id"],
                "database_preserved": (DIRECTORY / "server.db").is_file(), "receipt": str(target.relative_to(ROOT))}
        time.sleep(0.5)
    raise RuntimeError("TEMPORAL_ORIGINAL_PROCESS_STOP_TIMEOUT")


def archive_terminated():
    state = record()
    actual = subprocess.run(["/bin/ps", "-p", str(state["pid"]), "-o", "pid="], capture_output=True,
        text=True, check=False, timeout=5, env=private_directory())
    if actual.returncode != 1 or actual.stdout.strip():
        raise RuntimeError("TEMPORAL_ORIGINAL_PROCESS_ABSENCE_REQUIRED")
    target = DIRECTORY / (state["deployment_id"] + "-terminated.json")
    state["absence_verified_at"] = datetime.now(timezone.utc).isoformat()
    with target.open("x", encoding="utf-8") as saved:
        json.dump(state, saved, ensure_ascii=False, indent=2)
    STATE.unlink()
    return {"status": "TERMINATED_PROCESS_ARCHIVED", "original_pid": state["pid"],
        "receipt": str(target.relative_to(ROOT))}


def main():
    parser = argparse.ArgumentParser(description="管理官方Temporal本地持久开发服务")
    parser.add_argument("command", choices=("start", "status", "stop", "archive-terminated"))
    parser.add_argument("--port", type=int)
    parser.add_argument("--http-port", type=int)
    parser.add_argument("--metrics-port", type=int)
    parser.add_argument("--namespace")
    args = parser.parse_args()
    os.umask(0o077)
    if args.command == "start":
        if any(value is None for value in (args.port, args.http_port, args.metrics_port, args.namespace)):
            parser.error("启动需要明确Namespace及全部端口")
        result = start(args)
    else:
        if any(value is not None for value in (args.port, args.http_port, args.metrics_port, args.namespace)):
            parser.error("状态和关闭使用原部署配置")
        actions = {"status": status, "stop": stop, "archive-terminated": archive_terminated}
        result = actions[args.command]()
    print(json.dumps(result, ensure_ascii=False))


if __name__ == "__main__":
    main()
