import argparse
import os
from pathlib import Path
import subprocess
import sys


ROOT = Path(__file__).resolve().parents[1]
from check_support import capture_check_path
CAPTURE = capture_check_path()


def main():
    parser = argparse.ArgumentParser(description="核查派生版本的实际资源归属及企业事务保护")
    parser.add_argument("--execution", required=True)
    args = parser.parse_args()
    if Path(args.execution).name != args.execution or args.execution in (".", ".."):
        raise ValueError("执行身份必须使用单一目录名称")
    toolchains = ROOT / ".implementation-work/toolchains"
    java = (toolchains / "jdk-17.0.20.1+1/Contents/Home").resolve(strict=True)
    maven = (toolchains / "apache-maven-3.9.16/bin/mvn").resolve(strict=True)
    inputs = [ROOT / "scripts/check_version_resource_authority.py", ROOT / "business/pom.xml",
              ROOT / "business/medical-persistence/pom.xml", ROOT / "LLD-数据.md", ROOT / "LLD-后端.md",
              ROOT / "backend-design/接口补充设计.md", ROOT / ".implementation-work/runtime/service-identity.json"]
    for directory in ("business/medical-contracts/src", "business/medical-persistence/src", "business/medical-data/src"):
        inputs.extend(path for path in sorted((ROOT / directory).rglob("*")) if path.is_file())
    capture = [sys.executable, str(CAPTURE), "--output-dir", ".implementation-work/IC-05-VERSION-AUTHORITY-" + args.execution,
               "--check-id", "IC-05-VERSION-AUTHORITY", "--kind", "INTEGRATION", "--baseline-id", "BACK-VERSION-RESOURCE-AUTHORITY-01",
               "--cwd", str(ROOT / "business"), "--timeout", "900"]
    for path in inputs:
        capture.extend(["--input", str(path)])
    capture.extend(["--artifact", str(ROOT / "business/medical-persistence/target/surefire-reports/TEST-com.ruoyi.medical.persistence.VersionResourceAuthorityTest.xml")])
    temporary = ROOT / ".implementation-work/version-authority-tmp"
    temporary.mkdir(parents=True, exist_ok=True)
    environment = os.environ.copy(); environment["JAVA_HOME"] = str(java); environment["TMPDIR"] = str(temporary)
    environment["MAVEN_OPTS"] = "-Dfile.encoding=UTF-8 -Djava.io.tmpdir=" + str(temporary)
    command = [str(maven), "--batch-mode", "--no-transfer-progress", "-Dmaven.repo.local=" + str(ROOT / ".implementation-work/maven-repository"),
               "-pl", "medical-persistence", "-am", "-Dtest=VersionResourceAuthorityTest", "-Dsurefire.failIfNoSpecifiedTests=false", "test"]
    subprocess.run([*capture, "--", *command], cwd=ROOT, env=environment, check=True)


if __name__ == "__main__":
    main()
