import hashlib
import json
import os
from pathlib import Path
import subprocess
import zipfile

import local_runtime as runtime


ROOT = Path(__file__).resolve().parents[1]


def main():
    artifact = ROOT / "business/ruoyi-admin/target/ruoyi-admin.jar"
    digest = hashlib.sha256(artifact.read_bytes()).hexdigest()
    libraries = ROOT / ".implementation-work/acceptance-seed-libraries" / digest
    libraries.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(artifact) as archive:
        for name in archive.namelist():
            if not name.startswith("BOOT-INF/lib/") or not name.endswith(".jar"):
                continue
            if len(Path(name).parts) != 3:
                raise ValueError("PUBLIC_ASSET_DEPENDENCY_PATH_INVALID")
            target, content = libraries / Path(name).name, archive.read(name)
            if target.exists() and hashlib.sha256(target.read_bytes()).digest() != hashlib.sha256(content).digest():
                raise ValueError("PUBLIC_ASSET_DEPENDENCY_CHANGED")
            if not target.exists():
                target.write_bytes(content)
    modules = ["ruoyi-admin", "medical-persistence", "medical-identity", "medical-contracts", "medical-workflow", "ruoyi-framework", "ruoyi-system", "ruoyi-common", "ruoyi-quartz", "ruoyi-generator"]
    project_libraries = {name + "-3.9.2.jar" for name in modules}
    classpath = [*[ROOT / "business" / name / "target/classes" for name in modules],
        *[path for path in sorted(libraries.glob("*.jar")) if path.name not in project_libraries]]
    classes = ROOT / ".implementation-work/acceptance-template-asset-classes"
    classes.mkdir(parents=True, exist_ok=True)
    environment = runtime.environment()
    environment["MEDICAL_LOCAL_DATABASE_PASSWORD"] = runtime.settings()["database_password"]
    subprocess.run([str(runtime.JAVA.with_name("javac")), "-J-Dfile.encoding=UTF-8", "-encoding", "UTF-8", "--class-path", os.pathsep.join(map(str, classpath)),
        "-d", str(classes), str(ROOT / "scripts/SeedAcceptanceTemplateAssets.java")], cwd=ROOT, env=environment, check=True)
    subprocess.run([str(runtime.JAVA), "-Xms128m", "-Xmx1024m", "-Dfile.encoding=UTF-8", "-Dlogback.configurationFile=logback-medical.xml", "-Djava.io.tmpdir=" + str(runtime.RUNTIME / "tmp"),
        "--class-path", os.pathsep.join(map(str, [classes, *classpath])), "com.ruoyi.medical.persistence.SeedAcceptanceTemplateAssets", str(ROOT)],
        cwd=ROOT, env=environment, check=True)
    print(json.dumps({"state": "REGISTERED", "data_kind": "SIMULATION", "artifact_sha256": digest}))


if __name__ == "__main__":
    main()
