import argparse
import base64
import fcntl
import gzip
import hashlib
import json
import os
from pathlib import Path
import pwd
import re
import secrets
import shutil
import signal
import subprocess
import sys
import time
import uuid


ROOT = Path(__file__).resolve().parents[1]
RUNTIME = ROOT / ".implementation-work/runtime"
MYSQL = ROOT / ".implementation-work/toolchains/mysql-8.4.3-macos14-x86_64"
REDIS = ROOT / ".implementation-work/toolchains/redis-7.4.8/src"
JAVA = ROOT / ".implementation-work/toolchains/jdk-17.0.20.1+1/Contents/Home/bin/java"
SECRETS = RUNTIME / "service-secrets.json"


def environment():
    values = os.environ.copy()
    values["TMPDIR"] = str(ROOT / ".implementation-work/migration-check-tmp")
    values["LC_ALL"] = "en_US.UTF-8"
    return values


def settings():
    values = json.loads(SECRETS.read_bytes())
    if set(values) != {"root_password", "database_password", "redis_password"}:
        raise ValueError("LOCAL_SERVICE_SECRETS_INVALID")
    for value in values.values():
        if not isinstance(value, str) or not re.fullmatch("[a-f0-9]{64}", value):
            raise ValueError("LOCAL_SERVICE_SECRETS_INVALID")
    return values


def mysql(sql, root_password, socket=False, check=True):
    values = environment()
    values["MYSQL_PWD"] = root_password
    command = [str(MYSQL / "bin/mysql"), "--no-defaults", "--user=root", "--batch", "--raw", "--skip-column-names"]
    command.extend(["--protocol=SOCKET", "--socket=mysql-data/mysql.sock"] if socket else ["--protocol=TCP", "--host=127.0.0.1", "--port=13306"])
    return subprocess.run(command, input=sql, text=True, capture_output=True, check=check, cwd=RUNTIME, env=values)


def wait_mysql(password, socket=False):
    deadline = time.monotonic() + 30
    while time.monotonic() < deadline:
        result = mysql("SELECT 1", password, socket=socket, check=False)
        if result.returncode == 0 and result.stdout.strip() == "1":
            return
        time.sleep(0.5)
    raise RuntimeError("LOCAL_MYSQL_START_TIMEOUT")


def initialize():
    RUNTIME.mkdir(exist_ok=True, parents=True)
    RUNTIME.chmod(0o700)
    if SECRETS.exists():
        raise RuntimeError("LOCAL_SERVICES_ALREADY_INITIALIZED")
    datadir = RUNTIME / "mysql-data"
    if any(datadir.iterdir()):
        raise RuntimeError("LOCAL_MYSQL_DIRECTORY_NOT_EMPTY")
    subprocess.run([str(MYSQL / "bin/mysqld"), "--no-defaults", "--initialize-insecure", "--basedir=" + str(MYSQL),
                    "--datadir=" + str(datadir), "--log-error=" + str(RUNTIME / "mysql-initialize.log")],
                   check=True, env=environment())
    values = {name: secrets.token_hex(32) for name in ("root_password", "database_password", "redis_password")}
    with SECRETS.open("x", encoding="utf-8") as output:
        os.chmod(SECRETS, 0o600)
        json.dump(values, output)
    subprocess.run([str(MYSQL / "bin/mysqld"), "--no-defaults", "--basedir=" + str(MYSQL),
        "--datadir=" + str(datadir), "--skip-networking", "--socket=mysql.sock", "--mysqlx=OFF", "--daemonize",
        "--log-error=" + str(RUNTIME / "bootstrap-mysql.log"), "--pid-file=" + str(RUNTIME / "bootstrap-mysql.pid")],
        cwd=RUNTIME, env=environment(), check=True)
    wait_mysql("", socket=True)
    secure()
    print("LOCAL_SERVICES_INITIALIZED")


def secure():
    values = settings()
    identity = json.loads(mysql("SELECT JSON_OBJECT('port',@@port,'server_uuid',@@server_uuid,'version',VERSION())", "", socket=True).stdout)
    if identity["port"] != 0 or not identity["version"].startswith("8.4."):
        raise RuntimeError("LOCAL_BOOTSTRAP_DATABASE_INVALID")
    mysql("ALTER USER 'root'@'localhost' IDENTIFIED BY '" + values["root_password"] + "';\n"
          "CREATE DATABASE medical_local CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;\n"
          "CREATE USER 'medical_runtime'@'127.0.0.1' IDENTIFIED BY '" + values["database_password"] + "';\n"
          "GRANT SELECT,INSERT,UPDATE,DELETE ON medical_local.* TO 'medical_runtime'@'127.0.0.1';\n", "", socket=True)
    mysql("SHUTDOWN", values["root_password"], socket=True)
    deadline = time.monotonic() + 30
    while (RUNTIME / "bootstrap-mysql.pid").exists() and time.monotonic() < deadline:
        time.sleep(0.5)
    if (RUNTIME / "bootstrap-mysql.pid").exists():
        raise RuntimeError("LOCAL_MYSQL_INITIALIZATION_STOP_FAILED")
    (RUNTIME / "initialization-receipt.json").write_text(json.dumps({**identity, "state": "SECURED"}), encoding="utf-8")
    print("LOCAL_SERVICE_CREDENTIALS_CONFIGURED")


def start():
    values = settings()
    subprocess.run([str(MYSQL / "bin/mysqld"), "--no-defaults", "--basedir=" + str(MYSQL),
        "--datadir=" + str(RUNTIME / "mysql-data"), "--bind-address=127.0.0.1", "--port=13306", "--socket=mysql.sock",
        "--mysqlx=OFF", "--daemonize", "--pid-file=" + str(RUNTIME / "mysql.pid"),
        "--log-error=" + str(RUNTIME / "mysql.log")], cwd=RUNTIME, check=True, env=environment())
    wait_mysql(values["root_password"])
    configuration = "\n".join(["bind 127.0.0.1", "port 16379", "protected-mode yes", "daemonize yes", "locale-collate C",
        "dir " + json.dumps(str(RUNTIME / "redis-data"), ensure_ascii=False), "appendonly yes", "appendfsync everysec",
        "pidfile " + json.dumps(str(RUNTIME / "redis.pid"), ensure_ascii=False),
        "logfile " + json.dumps(str(RUNTIME / "redis.log"), ensure_ascii=False), "requirepass " + values["redis_password"]]) + "\n"
    subprocess.run([str(REDIS / "redis-server"), "-"], input=configuration, text=True, check=True, env=environment())
    redis_environment = environment()
    redis_environment["REDISCLI_AUTH"] = values["redis_password"]
    wait_redis(redis_environment)
    identity = json.loads(mysql("SELECT JSON_OBJECT('version',VERSION(),'server_uuid',@@server_uuid,'database','medical_local')",
                               values["root_password"]).stdout)
    (RUNTIME / "service-identity.json").write_text(json.dumps(identity, ensure_ascii=False), encoding="utf-8")
    print("LOCAL_SERVICES_READY " + json.dumps(identity, ensure_ascii=False))


def wait_redis(redis_environment):
    deadline = time.monotonic() + 30
    while time.monotonic() < deadline:
        result = subprocess.run([str(REDIS / "redis-cli"), "-h", "127.0.0.1", "-p", "16379", "PING"],
                                capture_output=True, text=True, check=True, timeout=5, env=redis_environment)
        response = result.stdout.strip()
        if response == "PONG":
            return
        if not response.startswith("LOADING "):
            raise RuntimeError("LOCAL_REDIS_NOT_READY")
        time.sleep(0.1)
    raise RuntimeError("LOCAL_REDIS_START_TIMEOUT")


def migrate(migration_id=None):
    values = settings()
    identity = json.loads((RUNTIME / "service-identity.json").read_bytes())
    backup_directory = RUNTIME / "migration-backups"
    backup_directory.mkdir(mode=0o700, exist_ok=True)
    backup_id = str(uuid.uuid4()) if migration_id is None else migration_id
    if str(uuid.UUID(backup_id)) != backup_id:
        raise ValueError("迁移身份必须使用标准UUID")
    backup = backup_directory / (backup_id + ".sql.gz")
    backup_receipt = backup_directory / (backup_id + ".json")
    if backup.exists() or backup_receipt.exists():
        raise RuntimeError("LOCAL_MIGRATION_BACKUP_ALREADY_PRESENT")
    dump_environment = environment()
    dump_environment["MYSQL_PWD"] = values["root_password"]
    result = subprocess.run([str(MYSQL / "bin/mysqldump"), "--no-defaults", "--host=127.0.0.1", "--port=13306",
        "--user=root", "--single-transaction", "--routines", "--events", "--databases", "medical_local"],
        capture_output=True, check=True, env=dump_environment)
    with gzip.open(backup, "wb") as output:
        output.write(result.stdout)
    backup.chmod(0o600)
    backup_receipt.write_text(json.dumps({"database": identity["database"], "server_uuid": identity["server_uuid"],
        "path": str(backup.relative_to(ROOT)), "sha256": hashlib.sha256(backup.read_bytes()).hexdigest()}), encoding="utf-8")
    backup_receipt.chmod(0o600)
    jar = ROOT / "business/medical-data/target/medical-data-3.9.2-cli.jar"
    migration_environment = environment()
    migration_environment.update({"LIQUIBASE_COMMAND_URL": "jdbc:mysql://127.0.0.1:13306/medical_local?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
        "LIQUIBASE_COMMAND_USERNAME": "root", "LIQUIBASE_COMMAND_PASSWORD": values["root_password"],
        "MEDICAL_MIGRATION_JAR_SHA256": hashlib.sha256(jar.read_bytes()).hexdigest(),
        "MEDICAL_MIGRATION_LIBRARY_SHA256": hashlib.sha256((ROOT / "business/medical-data/target/medical-data-3.9.2.jar").read_bytes()).hexdigest(),
        "MEDICAL_MIGRATION_DATABASE": identity["database"], "MEDICAL_MIGRATION_SERVER_UUID": identity["server_uuid"],
        "MEDICAL_MIGRATION_APPROVAL_REF": "BACK-LOCAL-01", "MEDICAL_MIGRATION_BACKUP_RECEIPT_REF": str(backup_receipt.relative_to(ROOT))})
    with (RUNTIME / "migration.log").open("wb") as output:
        subprocess.run([sys.executable, str(ROOT / "scripts/run_migration.py"), "update"], env=migration_environment,
                       cwd=ROOT, check=True, stdout=output, stderr=subprocess.STDOUT)
    print("LOCAL_MIGRATION_APPLIED")


def application():
    if (RUNTIME / "application.pid").exists():
        raise RuntimeError("LOCAL_APPLICATION_PID_ALREADY_PRESENT")
    values = settings()
    application_environment = environment()
    management = management_secret()
    invitation = invitation_secret()
    framework_token = ruoyi_token_secret()
    application_environment.update({"MEDICAL_LOCAL_DATABASE_PASSWORD": values["database_password"],
                                     "MEDICAL_LOCAL_REDIS_PASSWORD": values["redis_password"],
                                     "MEDICAL_LOCAL_MANAGEMENT_HMAC_KEY": management["key"],
                                     "MEDICAL_LOCAL_MANAGEMENT_HMAC_KEY_ID": management["key_id"],
                                     "MEDICAL_INVITATION_RESPONSE_KEY": invitation["key"],
                                     "MEDICAL_INVITATION_RESPONSE_KEY_ID": invitation["key_id"],
                                     "MEDICAL_RUOYI_TOKEN_SECRET": framework_token["key"]})
    source = ROOT / "business/ruoyi-admin/target/ruoyi-admin.jar"
    digest = hashlib.sha256(source.read_bytes()).hexdigest()
    directory = RUNTIME / "applications" / digest
    directory.mkdir(parents=True, exist_ok=True, mode=0o700)
    directory.chmod(0o700)
    artifact = directory / "ruoyi-admin.jar"
    if not artifact.exists():
        with source.open("rb") as original, artifact.open("xb") as output:
            shutil.copyfileobj(original, output)
            output.flush()
            os.fsync(output.fileno())
        artifact.chmod(0o400)
    if artifact.is_symlink() or artifact.stat().st_uid != os.getuid() or artifact.stat().st_mode & 0o777 != 0o400:
        raise RuntimeError("LOCAL_APPLICATION_ARTIFACT_PERMISSIONS_INVALID")
    if hashlib.sha256(artifact.read_bytes()).hexdigest() != digest or hashlib.sha256(source.read_bytes()).hexdigest() != digest:
        raise RuntimeError("LOCAL_APPLICATION_ARTIFACT_CHANGED")
    with (RUNTIME / "application.log").open("ab") as output:
        process = subprocess.Popen([str(JAVA), "-Dfile.encoding=UTF-8", "-Dlogback.configurationFile=logback-medical.xml", "-Dmedical.projectRoot=" + str(ROOT), "-Djava.io.tmpdir=" + str(ROOT / ".implementation-work/java-temp"),
            "-jar", str(artifact), "--spring.config.name=application-medical-local"],
            cwd=ROOT, env=application_environment, stdout=output, stderr=output, start_new_session=True)
    deployment = {"deployment_id": str(uuid.uuid4()), "pid": process.pid, "os_uid": os.getuid(),
                  "artifact": str(artifact.relative_to(ROOT)), "sha256": digest,
                  "source": str(source.relative_to(ROOT)), "scope": "LOCAL_APPLICATION"}
    history = RUNTIME / ("application-deployment-" + deployment["deployment_id"] + ".json")
    with history.open("x", encoding="utf-8") as output:
        json.dump(deployment, output, ensure_ascii=False)
        output.flush()
        os.fsync(output.fileno())
    (RUNTIME / "application-deployment.json").write_text(json.dumps(deployment, ensure_ascii=False), encoding="utf-8")
    (RUNTIME / "application.pid").write_text(str(process.pid), encoding="ascii")
    synchronize_runtime_directory()
    print("LOCAL_APPLICATION_PROCESS " + str(process.pid))


def verify_application_deployment():
    deployment = json.loads((RUNTIME / "application-deployment.json").read_bytes())
    pid = int((RUNTIME / "application.pid").read_text(encoding="ascii"))
    digest = deployment["sha256"]
    if not re.fullmatch("[a-f0-9]{64}", digest) or deployment["pid"] != pid or deployment["os_uid"] != os.getuid():
        raise RuntimeError("LOCAL_APPLICATION_DEPLOYMENT_INVALID")
    artifact = RUNTIME / "applications" / digest / "ruoyi-admin.jar"
    if deployment["artifact"] != str(artifact.relative_to(ROOT)) or artifact.is_symlink():
        raise RuntimeError("LOCAL_APPLICATION_ARTIFACT_IDENTITY_CHANGED")
    if hashlib.sha256(artifact.read_bytes()).hexdigest() != digest:
        raise RuntimeError("LOCAL_APPLICATION_ARTIFACT_CHANGED")
    process = subprocess.run(["/bin/ps", "-p", str(pid), "-o", "command="], capture_output=True,
                             text=True, check=True, env=environment())
    expected = "-jar " + str(artifact) + " --spring.config.name=application-medical-local"
    if expected not in process.stdout or not process.stdout.strip().startswith(str(JAVA)):
        raise RuntimeError("LOCAL_APPLICATION_PROCESS_IDENTITY_CHANGED")
    return deployment


def stop_application():
    path = RUNTIME / "application.pid"
    pid = verify_application_deployment()["pid"]
    os.kill(pid, signal.SIGTERM)
    deadline = time.monotonic() + 30
    while time.monotonic() < deadline:
        result = subprocess.run(["/bin/ps", "-p", str(pid), "-o", "pid="], capture_output=True,
                                text=True, check=False, env=environment())
        if result.returncode != 0:
            path.unlink()
            print("LOCAL_APPLICATION_STOPPED")
            return
        time.sleep(0.5)
    raise RuntimeError("LOCAL_APPLICATION_STOP_TIMEOUT")


def recover_owner():
    owner = pwd.getpwuid(os.getuid())
    path = RUNTIME / "owner-credentials.json"
    if RUNTIME.stat().st_uid != owner.pw_uid or RUNTIME.stat().st_mode & 0o777 != 0o700:
        raise RuntimeError("LOCAL_RUNTIME_PERMISSIONS_INVALID")
    with (RUNTIME / "owner-recovery.lock").open("a", encoding="utf-8") as lock:
        os.chmod(lock.fileno(), 0o600)
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        display_name = owner.pw_name
        if path.exists():
            if path.is_symlink() or path.stat().st_uid != owner.pw_uid or path.stat().st_mode & 0o777 != 0o600:
                raise RuntimeError("LOCAL_OWNER_CREDENTIAL_PERMISSIONS_INVALID")
            previous = json.loads(path.read_bytes())
            if previous["username"] != owner.pw_name:
                raise RuntimeError("LOCAL_OWNER_IDENTITY_CHANGED")
            display_name = previous["display_name"]
        credentials = {"username": owner.pw_name, "password": secrets.token_hex(24), "display_name": display_name}
        candidate = RUNTIME / ("owner-credentials-" + str(uuid.uuid4()) + ".json")
        with candidate.open("x", encoding="utf-8") as output:
            os.chmod(output.fileno(), 0o600)
            json.dump(credentials, output, ensure_ascii=False)
            output.flush()
            os.fsync(output.fileno())
        synchronize_runtime_directory()
        update_owner_credential(owner, credentials)
        os.replace(candidate, path)
        synchronize_runtime_directory()
        (RUNTIME / "owner-credential-receipt.json").write_text(json.dumps({"scope": "LOCAL_OWNER_CREDENTIAL_RECOVERY",
            "source": "operating_system_account", "os_uid": owner.pw_uid, "username": owner.pw_name,
            "credential_file": str(path.relative_to(ROOT)), "state": "UPDATED"}), encoding="utf-8")


def management_secret():
    path = RUNTIME / "local-management-secret.json"
    if RUNTIME.is_symlink() or RUNTIME.stat().st_uid != os.getuid() or RUNTIME.stat().st_mode & 0o777 != 0o700:
        raise RuntimeError("LOCAL_RUNTIME_PERMISSIONS_INVALID")
    if not path.exists():
        count = mysql("SELECT COUNT(*) FROM medical_local.platform_local_operation", settings()["root_password"]).stdout.strip()
        if int(count) != 0:
            raise RuntimeError("LOCAL_MANAGEMENT_SECRET_REQUIRED_FOR_EXISTING_OPERATIONS")
        value = {"key_id": str(uuid.uuid4()), "key": secrets.token_hex(32)}
        with path.open("x", encoding="utf-8") as output:
            os.fchmod(output.fileno(), 0o600)
            json.dump(value, output)
            output.flush()
            os.fsync(output.fileno())
        synchronize_runtime_directory()
    if path.is_symlink() or path.stat().st_uid != os.getuid() or path.stat().st_mode & 0o777 != 0o600:
        raise RuntimeError("LOCAL_MANAGEMENT_SECRET_PERMISSIONS_INVALID")
    value = json.loads(path.read_text(encoding="utf-8"))
    if set(value) != {"key_id", "key"} or not isinstance(value["key"], str) or not re.fullmatch("[0-9a-f]{64}", value["key"]):
        raise RuntimeError("LOCAL_MANAGEMENT_SECRET_INVALID")
    if not isinstance(value["key_id"], str) or str(uuid.UUID(value["key_id"])) != value["key_id"]:
        raise RuntimeError("LOCAL_MANAGEMENT_SECRET_INVALID")
    return value


def ruoyi_token_secret():
    path = RUNTIME / "ruoyi-token-secret.json"
    if RUNTIME.is_symlink() or RUNTIME.stat().st_uid != os.getuid() or RUNTIME.stat().st_mode & 0o777 != 0o700:
        raise RuntimeError("LOCAL_RUNTIME_PERMISSIONS_INVALID")
    if not path.exists():
        values = environment()
        values["REDISCLI_AUTH"] = settings()["redis_password"]
        existing = subprocess.run([str(REDIS / "redis-cli"), "-h", "127.0.0.1", "-p", "16379", "--scan",
                                  "--pattern", "login_tokens:*"], capture_output=True, text=True, check=True, env=values)
        if existing.stdout.strip():
            raise RuntimeError("RUOYI_TOKEN_SECRET_REQUIRED_FOR_EXISTING_TOKENS")
        value = {"purpose": "RUOYI_TOKEN_SIGNING", "key_id": str(uuid.uuid4()),
                 "key": base64.b64encode(secrets.token_bytes(64)).decode("ascii")}
        with path.open("x", encoding="utf-8") as output:
            os.fchmod(output.fileno(), 0o600)
            json.dump(value, output)
            output.flush()
            os.fsync(output.fileno())
        synchronize_runtime_directory()
    if path.is_symlink() or path.stat().st_uid != os.getuid() or path.stat().st_mode & 0o777 != 0o600:
        raise RuntimeError("RUOYI_TOKEN_SECRET_PERMISSIONS_INVALID")
    value = json.loads(path.read_bytes())
    if set(value) != {"purpose", "key_id", "key"} or value["purpose"] != "RUOYI_TOKEN_SIGNING" or not isinstance(value["key"], str):
        raise RuntimeError("RUOYI_TOKEN_SECRET_INVALID")
    key = base64.b64decode(value["key"], validate=True)
    if len(key) != 64 or base64.b64encode(key).decode("ascii") != value["key"]:
        raise RuntimeError("RUOYI_TOKEN_SECRET_INVALID")
    if not isinstance(value["key_id"], str) or str(uuid.UUID(value["key_id"])) != value["key_id"]:
        raise RuntimeError("RUOYI_TOKEN_SECRET_INVALID")
    return value


def configure_framework_token():
    value = ruoyi_token_secret()
    path = RUNTIME / "ruoyi-token-secret.json"
    receipt = {"scope": "LOCAL_RUOYI_TOKEN_CONFIGURATION", "purpose": value["purpose"], "key_id": value["key_id"],
               "key_file": str(path.relative_to(ROOT)), "key_file_sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
               "algorithm": "HS512", "key_bits": 512, "key_file_mode": "0600", "runtime_directory_mode": "0700"}
    (RUNTIME / "ruoyi-token-configuration-receipt.json").write_text(json.dumps(receipt, ensure_ascii=False), encoding="utf-8")
    print(json.dumps(receipt, ensure_ascii=False))


def invitation_secret():
    path = RUNTIME / "invitation-response-secret.json"
    if RUNTIME.is_symlink() or RUNTIME.stat().st_uid != os.getuid() or RUNTIME.stat().st_mode & 0o777 != 0o700:
        raise RuntimeError("LOCAL_RUNTIME_PERMISSIONS_INVALID")
    if not path.exists():
        count = mysql("SELECT COUNT(*) FROM medical_local.operation_record WHERE operation_name IN ('ID-07','ID-08')",
                      settings()["root_password"]).stdout.strip()
        if int(count) != 0:
            raise RuntimeError("INVITATION_RESPONSE_SECRET_REQUIRED_FOR_EXISTING_OPERATIONS")
        value = {"purpose": "INVITATION_RESPONSE", "key_id": str(uuid.uuid4()), "key": secrets.token_hex(32)}
        with path.open("x", encoding="utf-8") as output:
            os.fchmod(output.fileno(), 0o600)
            json.dump(value, output)
            output.flush()
            os.fsync(output.fileno())
        synchronize_runtime_directory()
    if path.is_symlink() or path.stat().st_uid != os.getuid() or path.stat().st_mode & 0o777 != 0o600:
        raise RuntimeError("INVITATION_RESPONSE_SECRET_PERMISSIONS_INVALID")
    value = json.loads(path.read_bytes())
    if set(value) != {"purpose", "key_id", "key"} or value["purpose"] != "INVITATION_RESPONSE" or not isinstance(value["key"], str) or not re.fullmatch("[0-9a-f]{64}", value["key"]):
        raise RuntimeError("INVITATION_RESPONSE_SECRET_INVALID")
    if not isinstance(value["key_id"], str) or str(uuid.UUID(value["key_id"])) != value["key_id"]:
        raise RuntimeError("INVITATION_RESPONSE_SECRET_INVALID")
    return value


def synchronize_runtime_directory():
    descriptor = os.open(RUNTIME, os.O_RDONLY)
    try:
        os.fsync(descriptor)
    finally:
        os.close(descriptor)


def update_owner_credential(owner, credentials):
    values = environment()
    values.update({"MEDICAL_OWNER_USERNAME": owner.pw_name, "MEDICAL_OWNER_PASSWORD": credentials["password"],
                   "MEDICAL_ROOT_PASSWORD": settings()["root_password"]})
    libraries = [
        ".implementation-work/maven-repository/org/springframework/security/spring-security-crypto/6.5.11/spring-security-crypto-6.5.11.jar",
        ".implementation-work/maven-repository/org/springframework/spring-jcl/6.2.19/spring-jcl-6.2.19.jar",
        ".implementation-work/maven-repository/com/mysql/mysql-connector-j/9.7.0/mysql-connector-j-9.7.0.jar",
    ]
    subprocess.run([str(JAVA), "-Djava.io.tmpdir=" + str(ROOT / ".implementation-work/java-temp"),
                    "--class-path", os.pathsep.join(str(ROOT / name) for name in libraries),
                    str(ROOT / "scripts/RecoverLocalOwner.java")], env=values, cwd=ROOT, check=True)
    values["REDISCLI_AUTH"] = settings()["redis_password"]
    cleared = subprocess.run([str(REDIS / "redis-cli"), "-h", "127.0.0.1", "-p", "16379", "--json",
        "DEL", "pwd_err_cnt:" + owner.pw_name], env=values, cwd=ROOT, capture_output=True, text=True, check=True, timeout=15)
    if json.loads(cleared.stdout) not in (0, 1):
        raise RuntimeError("FRAMEWORK_LOGIN_RETRY_CLEAR_FAILED")


def main():
    parser = argparse.ArgumentParser(description="运行本项目独立的真实本地服务")
    parser.add_argument("command", choices=("initialize", "secure", "start", "migrate", "application", "stop-application", "recover-owner", "framework-token"))
    parser.add_argument("--migration-id")
    args = parser.parse_args()
    if args.migration_id is not None and args.command != "migrate":
        parser.error("迁移身份只用于migrate")
    if args.command == "migrate":
        migrate(args.migration_id)
        return
    actions = {"initialize": initialize, "secure": secure, "start": start, "migrate": migrate,
               "application": application, "stop-application": stop_application, "recover-owner": recover_owner,
               "framework-token": configure_framework_token}
    actions[args.command]()


if __name__ == "__main__":
    main()
