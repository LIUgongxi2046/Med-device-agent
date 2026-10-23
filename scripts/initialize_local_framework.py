import gzip
import hashlib
import json
import os
from pathlib import Path
import subprocess
import uuid

import local_runtime as runtime

ROOT = Path(__file__).resolve().parents[1]
runtime.private_directory(runtime.RUNTIME)
owner_path = runtime.RUNTIME / 'owner-credentials.json'
if owner_path.is_symlink() or owner_path.stat().st_uid != os.getuid() or owner_path.stat().st_mode & 0o777 != 0o600:
    raise RuntimeError('LOCAL_OWNER_CREDENTIAL_PERMISSIONS_INVALID')
owner = json.loads(owner_path.read_bytes())
values = runtime.environment()
values.update(MYSQL_PWD=runtime.settings()['root_password'], MEDICAL_ROOT_PASSWORD=runtime.settings()['root_password'],
              MEDICAL_OWNER_USERNAME=owner['username'])
libraries = [ROOT / '.implementation-work/maven-repository/com/github/jsqlparser/jsqlparser/5.2/jsqlparser-5.2.jar',
             ROOT / '.implementation-work/maven-repository/com/mysql/mysql-connector-j/9.7.0/mysql-connector-j-9.7.0.jar']
command = [str(runtime.JAVA), '-Djava.io.tmpdir=' + str(runtime.RUNTIME / 'tmp'), '--class-path',
           os.pathsep.join(str(path) for path in libraries), str(ROOT / 'scripts/InitializeLocalFramework.java'),
           str(ROOT / 'business/sql/ry_20260417.sql')]
subprocess.run(command, cwd=ROOT, env=values, check=True)
directory = runtime.RUNTIME / 'framework-backups'
runtime.private_directory(directory, create=True)
backup_id = str(uuid.uuid4())
backup = directory / (backup_id + '.sql.gz')
dump = subprocess.run([str(runtime.MYSQL / 'bin/mysqldump'), '--no-defaults', '--no-login-paths', '--protocol=TCP',
    '--host=127.0.0.1', '--port=13306', '--user=root', '--single-transaction', '--routines', '--events',
    '--databases', 'medical_local'], env=values, capture_output=True, check=True)
with os.fdopen(os.open(backup, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600), 'wb') as output:
    with gzip.GzipFile(fileobj=output, mode='wb') as compressed:
        compressed.write(dump.stdout)
    output.flush()
    os.fsync(output.fileno())
runtime.save_private_json(directory / (backup_id + '.json'), {'database': 'medical_local', 'path': str(backup.relative_to(ROOT)),
    'sha256': hashlib.sha256(backup.read_bytes()).hexdigest(), 'authorization': 'HOME-ADMIN-RUOYI-20261021'})
subprocess.run([*command, '--apply'], cwd=ROOT, env=values, check=True)
