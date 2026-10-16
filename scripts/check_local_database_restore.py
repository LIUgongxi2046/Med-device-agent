import argparse
import csv
from datetime import datetime, timezone
import gzip
import hashlib
import io
import json
import os
from pathlib import Path
import re
import secrets
import shutil
import socket
import subprocess
import time
import uuid

import local_runtime as runtime


ROOT = Path(__file__).resolve().parents[1]


def mysql(directory, port, password, statement, check=True):
    environment = {**runtime.environment(), "MYSQL_PWD": password, "TMPDIR": str(directory / "temporary")}
    return subprocess.run([str(runtime.MYSQL / "bin/mysql"), "--no-defaults", "--user=root", "--batch", "--raw",
        "--skip-column-names", "--host=127.0.0.1", "--port=" + str(port), "--connect-timeout=5"],
        input=statement, capture_output=True, text=True, check=check, timeout=60, cwd=directory, env=environment)


def checksums(directory, port, password, tables):
    if not tables or any(re.fullmatch("[a-zA-Z0-9_]+", table) is None for table in tables):
        raise ValueError("RESTORE_TABLE_IDENTITIES_INVALID")
    result = mysql(directory, port, password, "CHECKSUM TABLE " + ",".join("medical_local.`" + table + "`" for table in tables) + " EXTENDED")
    rows = list(csv.reader(io.StringIO(result.stdout), delimiter="\t"))
    if len(rows) != len(tables) or any(len(row) != 2 or not row[1].isdigit() for row in rows):
        raise ValueError("RESTORE_TABLE_CHECKSUMS_UNAVAILABLE")
    return {row[0]: row[1] for row in rows}


def main():
    parser = argparse.ArgumentParser(description="使用真实本地数据库备份核验隔离恢复")
    parser.add_argument("--port", type=int, required=True)
    args = parser.parse_args()
    if not 1024 <= args.port <= 65535 or args.port in (13306, 16379, 16333, 16334, 17233, 17234, 17235, 18080, 18333):
        raise ValueError("RESTORE_ISOLATED_PORT_REQUIRED")
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as probe:
        probe.bind(("127.0.0.1", args.port))
    os.umask(0o077)
    execution = str(uuid.uuid4())
    directory = ROOT / ".implementation-work/restore-checks" / execution
    directory.mkdir(mode=0o700, parents=True)
    for name in ("data", "temporary"):
        (directory / name).mkdir(mode=0o700)
    original_password = runtime.settings()["root_password"]
    identity = json.loads(mysql(directory,13306,original_password,
        "SELECT JSON_OBJECT('server_uuid',@@server_uuid,'database','medical_local','version',VERSION())").stdout)
    tables = sorted(json.loads(mysql(directory,13306,original_password,
        "SELECT JSON_ARRAYAGG(TABLE_NAME) FROM information_schema.TABLES WHERE TABLE_SCHEMA='medical_local' AND TABLE_TYPE='BASE TABLE'").stdout))
    before = checksums(directory,13306,original_password,tables)
    backup = directory / "database.sql.gz"
    dump_environment = {**runtime.environment(), "MYSQL_PWD": original_password, "TMPDIR": str(directory / "temporary")}
    with (directory / "dump.log").open("xb") as errors, gzip.open(backup,"wb") as output:
        dump = subprocess.Popen([str(runtime.MYSQL / "bin/mysqldump"), "--no-defaults", "--host=127.0.0.1", "--port=13306",
            "--user=root", "--single-transaction", "--routines", "--events", "--set-gtid-purged=OFF", "--databases", "medical_local"],
            stdout=subprocess.PIPE, stderr=errors, cwd=directory, env=dump_environment)
        shutil.copyfileobj(dump.stdout, output, length=1048576)
        if dump.wait(timeout=60) != 0:
            raise RuntimeError("RESTORE_ORIGINAL_BACKUP_FAILED")
    if checksums(directory,13306,original_password,tables) != before:
        raise ValueError("RESTORE_ORIGINAL_DATABASE_CHANGED_DURING_BACKUP")
    environment = {**runtime.environment(), "TMPDIR": str(directory / "temporary")}
    initialized = time.monotonic()
    subprocess.run([str(runtime.MYSQL / "bin/mysqld"), "--no-defaults", "--initialize-insecure", "--basedir=" + str(runtime.MYSQL),
        "--datadir=" + str(directory / "data"), "--log-error=" + str(directory / "initialize.log")],
        check=True, timeout=60, cwd=directory, env=environment)
    subprocess.run([str(runtime.MYSQL / "bin/mysqld"), "--no-defaults", "--basedir=" + str(runtime.MYSQL),
        "--datadir=" + str(directory / "data"), "--bind-address=127.0.0.1", "--port=" + str(args.port),
        "--socket=mysql.sock", "--mysqlx=OFF", "--daemonize", "--pid-file=" + str(directory / "mysql.pid"),
        "--log-error=" + str(directory / "mysql.log")], check=True, timeout=30, cwd=directory, env=environment)
    deadline = time.monotonic() + 30
    while time.monotonic() < deadline:
        if mysql(directory,args.port,"","SELECT 1",check=False).returncode == 0:
            break
        time.sleep(0.2)
    else:
        raise TimeoutError("RESTORE_ISOLATED_DATABASE_NOT_READY")
    password = secrets.token_hex(32)
    mysql(directory,args.port,"","ALTER USER 'root'@'localhost' IDENTIFIED BY '" + password + "'")
    with (directory / "credentials.json").open("x",encoding="utf-8") as output:
        json.dump({"root_password":password},output)
    restored_identity = json.loads(mysql(directory,args.port,password,
        "SELECT JSON_OBJECT('server_uuid',@@server_uuid,'version',VERSION())").stdout)
    if restored_identity["server_uuid"] == identity["server_uuid"] or restored_identity["version"] != identity["version"]:
        raise ValueError("RESTORE_DATABASE_ISOLATION_INVALID")
    with gzip.open(backup,"rb") as source, (directory / "restore.log").open("xb") as errors:
        restore = subprocess.Popen([str(runtime.MYSQL / "bin/mysql"), "--no-defaults", "--user=root", "--host=127.0.0.1",
            "--port=" + str(args.port), "--binary-mode"], stdin=subprocess.PIPE, stdout=errors, stderr=errors,
            cwd=directory, env={**environment,"MYSQL_PWD":password})
        shutil.copyfileobj(source,restore.stdin,length=1048576)
        restore.stdin.close()
        if restore.wait(timeout=60) != 0:
            raise RuntimeError("RESTORE_ACTUAL_DATABASE_IMPORT_FAILED")
    after = checksums(directory,args.port,password,tables)
    if after != before:
        raise ValueError("RESTORE_FULL_TABLE_CONTENT_MISMATCH")
    pid = int((directory / "mysql.pid").read_text().strip())
    process = subprocess.run(["/bin/ps","-ww","-p",str(pid),"-o","args="], capture_output=True,
        text=True,check=True,timeout=10,env=environment).stdout.strip()
    if "--datadir=" + str(directory / "data") not in process or "--port=" + str(args.port) not in process:
        raise ValueError("RESTORE_PROCESS_IDENTITY_CHANGED")
    elapsed = time.monotonic() - initialized
    mysql(directory,args.port,password,"SHUTDOWN")
    deadline = time.monotonic() + 30
    while (directory / "mysql.pid").exists() and time.monotonic() < deadline:
        time.sleep(0.2)
    if (directory / "mysql.pid").exists():
        raise TimeoutError("RESTORE_ISOLATED_DATABASE_STOP_TIMEOUT")
    shutil.rmtree(directory / "data")
    (directory / "credentials.json").unlink()
    with backup.open("rb") as source:
        backup_sha256 = hashlib.file_digest(source,"sha256").hexdigest()
    receipt = {"scope":"ACTUAL_ISOLATED_DATABASE_RESTORE", "execution_id":execution,
        "verified_at":datetime.now(timezone.utc).isoformat(), "original_identity":identity,
        "restored_identity":restored_identity,"port":args.port,"backup_sha256":backup_sha256,
        "tables":after,"table_count":len(after),"restore_seconds":elapsed,"restored_process_stopped":True,
        "restored_data_removed":True,"original_database_modified":False,"approved_rto_met":None,
        "business_recovery_verified":False}
    with (directory / "receipt.json").open("x",encoding="utf-8") as output:
        json.dump(receipt,output,ensure_ascii=False,indent=2)
    print("LOCAL_DATABASE_RESTORE_VERIFIED " + json.dumps({"receipt":str((directory / "receipt.json").relative_to(ROOT)),
        "table_count":len(after),"restore_seconds":elapsed,"business_recovery_verified":False}))


if __name__ == "__main__":
    main()
