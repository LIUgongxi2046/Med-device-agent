import json
import os
from pathlib import Path
import secrets
import subprocess

import local_runtime as runtime


ROOT = Path(__file__).resolve().parents[1]


def main():
    if os.environ.get("MEDICAL_MIGRATION_APPROVAL_REF") != "WORKSPACE-ACCEPTANCE-20261022-WA-03":
        raise ValueError("ACCEPTANCE_DATA_AUTHORIZATION_REQUIRED")
    runtime.migrate()
    private = runtime.RUNTIME / "acceptance-native-settings.json"
    if private.exists():
        settings = json.loads(private.read_bytes())
    else:
        settings = {"initial_password": secrets.token_urlsafe(24), "data_kind": "SIMULATION"}
        runtime.save_private_json(private, settings)
    environment = runtime.environment()
    environment["MEDICAL_ROOT_PASSWORD"] = runtime.settings()["root_password"]
    environment["MEDICAL_ACCEPTANCE_INITIAL_PASSWORD"] = settings["initial_password"]
    libraries = [ROOT / ".implementation-work/maven-repository/com/github/jsqlparser/jsqlparser/5.2/jsqlparser-5.2.jar",
                 ROOT / ".implementation-work/maven-repository/com/mysql/mysql-connector-j/9.7.0/mysql-connector-j-9.7.0.jar"]
    result = subprocess.run([str(runtime.JAVA), "-Dfile.encoding=UTF-8", "-Djava.io.tmpdir=" + str(runtime.RUNTIME / "tmp"),
        "--class-path", os.pathsep.join(str(path) for path in libraries), str(ROOT / "scripts/SeedAcceptanceFramework.java"),
        str(ROOT / "business/sql/ry_20260417.sql")], cwd=ROOT, env=environment, capture_output=True, text=True, check=True)
    receipt = {"data_kind": "SIMULATION", "scope": "LOCAL_NATIVE_ACCEPTANCE_DATA", "reference": "business/sql/ry_20260417.sql",
        "output": result.stdout, "existing_data_preserved": True}
    runtime.save_private_json(runtime.RUNTIME / "acceptance-native-seed.json", receipt, replace=True)
    print(json.dumps(receipt, ensure_ascii=False))


if __name__ == "__main__":
    main()
