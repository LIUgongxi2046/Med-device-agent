import argparse
import hashlib
import os
from pathlib import Path
import re
import subprocess
from uuid import UUID


ROOT = Path(__file__).resolve().parents[1]
JAR = ROOT / "business/medical-data/target/medical-data-3.9.2-cli.jar"
LIBRARY = ROOT / "business/medical-data/target/medical-data-3.9.2.jar"
DATABASE_COMMANDS = ("validate", "status", "update-sql", "update")


def required_value(environment, name):
    value = environment.get(name)
    if not value or not value.strip():
        raise ValueError("缺少迁移参数：" + name)
    return value


def main():
    parser = argparse.ArgumentParser(description="运行固定 Liquibase 迁移制品")
    parser.add_argument("command", choices=("version", "help", *DATABASE_COMMANDS))
    parser.add_argument("--for-command", choices=DATABASE_COMMANDS)
    args = parser.parse_args()
    if args.for_command and args.command != "help":
        parser.error("--for-command 仅用于原生命令帮助")
    java = (ROOT / ".implementation-work/toolchains/jdk-17.0.20.1+1/Contents/Home/bin/java").resolve(strict=True)
    jar = JAR.resolve(strict=True)
    environment = os.environ.copy()
    temporary = ROOT / ".implementation-work/java-temp"
    temporary.mkdir(parents=True, exist_ok=True)
    environment["TMPDIR"] = str(temporary)
    command = [str(java), "-Dfile.encoding=UTF-8", "-Djava.io.tmpdir=" + str(temporary), "-jar", str(jar),
               "--analytics-enabled=false", "--strict=true", "--changelog-parse-mode=STRICT"]

    if args.command in DATABASE_COMMANDS:
        # 目标与制品必须由实际发布输入固定，秘密仅由环境注入。
        expected_hash = required_value(environment, "MEDICAL_MIGRATION_JAR_SHA256")
        if not re.fullmatch("[a-f0-9]{64}", expected_hash):
            raise ValueError("迁移制品摘要必须使用小写 SHA-256")
        with jar.open("rb") as stream:
            if hashlib.file_digest(stream, "sha256").hexdigest() != expected_hash:
                raise ValueError("迁移制品摘要不匹配")
        for name in ("LIQUIBASE_COMMAND_URL", "LIQUIBASE_COMMAND_USERNAME", "LIQUIBASE_COMMAND_PASSWORD"):
            required_value(environment, name)
        expected_library_hash = required_value(environment, "MEDICAL_MIGRATION_LIBRARY_SHA256")
        if not re.fullmatch("[a-f0-9]{64}", expected_library_hash):
            raise ValueError("迁移资源制品摘要必须使用小写 SHA-256")
        if hashlib.sha256(LIBRARY.resolve(strict=True).read_bytes()).hexdigest() != expected_library_hash:
            raise ValueError("迁移资源制品摘要不匹配")
        if not environment["LIQUIBASE_COMMAND_URL"].startswith("jdbc:mysql:"):
            raise ValueError("迁移连接必须使用 MySQL JDBC，完整地址由 Connector/J 校验")
        database = required_value(environment, "MEDICAL_MIGRATION_DATABASE")
        server = required_value(environment, "MEDICAL_MIGRATION_SERVER_UUID")
        if str(UUID(server)) != server:
            raise ValueError("MySQL server_uuid 必须使用小写标准 UUID")
        required_value(environment, "MEDICAL_MIGRATION_APPROVAL_REF")
        if args.command == "update":
            required_value(environment, "MEDICAL_MIGRATION_BACKUP_RECEIPT_REF")
        command.append("--search-path=" + str(LIBRARY))
        command.extend([
            args.command,
            "--changelog-file=db/changelog/master.xml",
            "--driver=com.mysql.cj.jdbc.Driver",
            "-DmedicalMigrationDatabase=" + database,
            "-DmedicalMigrationServerUuid=" + server,
        ])
    else:
        if args.for_command:
            command.append(args.for_command)
        command.append("--version" if args.command == "version" else "--help")

    subprocess.run(command, cwd=ROOT, env=environment, check=True)


if __name__ == "__main__":
    main()
