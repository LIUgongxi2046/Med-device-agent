import hashlib
import json
import os
from pathlib import Path
import subprocess
import zipfile

import local_runtime as runtime


ROOT = Path(__file__).resolve().parents[1]


def main():
    deployment = runtime.verify_application_deployment()
    jar = ROOT / deployment["artifact"]
    libraries = ROOT / ".implementation-work/acceptance-seed-libraries" / deployment["sha256"]
    libraries.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(jar) as archive:
        names = [name for name in archive.namelist() if name.startswith("BOOT-INF/lib/") and name.endswith(".jar")]
        for name in names:
            if len(Path(name).parts) != 3:
                raise ValueError("ACCEPTANCE_DEPENDENCY_PATH_INVALID")
            target = libraries / Path(name).name
            content = archive.read(name)
            if target.exists():
                if hashlib.sha256(target.read_bytes()).digest() != hashlib.sha256(content).digest():
                    raise ValueError("ACCEPTANCE_DEPENDENCY_CHANGED")
            else:
                target.write_bytes(content)
    modules = ["ruoyi-admin", "medical-persistence", "medical-identity", "medical-contracts", "medical-workflow", "ruoyi-framework", "ruoyi-system", "ruoyi-common", "ruoyi-quartz", "ruoyi-generator"]
    project_libraries = {name + "-3.9.2.jar" for name in modules}
    classpath = [ROOT / "business/ruoyi-admin/target/test-classes", *[ROOT / "business" / name / "target/classes" for name in modules],
        *[path for path in sorted(libraries.glob("*.jar")) if path.name not in project_libraries]]
    values = runtime.settings(); management = runtime.management_secret(); invitation = runtime.invitation_secret(); token = runtime.ruoyi_token_secret()
    environment = runtime.environment()
    environment.update({"MEDICAL_LOCAL_DATABASE_PASSWORD": values["database_password"], "MEDICAL_LOCAL_REDIS_PASSWORD": values["redis_password"],
        "MEDICAL_LOCAL_MANAGEMENT_HMAC_KEY": management["key"], "MEDICAL_LOCAL_MANAGEMENT_HMAC_KEY_ID": management["key_id"],
        "MEDICAL_INVITATION_RESPONSE_KEY": invitation["key"], "MEDICAL_INVITATION_RESPONSE_KEY_ID": invitation["key_id"], "MEDICAL_RUOYI_TOKEN_SECRET": token["key"],
        "SPRING_CONFIG_ADDITIONAL_LOCATION": (ROOT / deployment["additional_configuration"]).as_uri()})
    classes = ROOT / ".implementation-work/acceptance-seed-classes"
    classes.mkdir(parents=True, exist_ok=True)
    subprocess.run([str(runtime.JAVA.with_name("javac")), "-encoding", "UTF-8", "--class-path", os.pathsep.join(str(path) for path in classpath), "-d", str(classes), str(ROOT / "scripts/SeedAcceptanceWorkspace.java")], cwd=ROOT, env=environment, check=True)
    subprocess.run([str(runtime.JAVA), "-Dfile.encoding=UTF-8", "-Dlogback.configurationFile=logback-medical.xml", "-Dlogging.config=classpath:logback-medical.xml", "-Dmedical.projectRoot=" + str(ROOT), "-Djava.io.tmpdir=" + str(runtime.RUNTIME / "tmp"),
        "--class-path", os.pathsep.join(str(path) for path in [classes, *classpath]), "com.ruoyi.medical.persistence.SeedAcceptanceWorkspace", str(ROOT)], cwd=ROOT, env=environment, check=True)


if __name__ == "__main__":
    main()
