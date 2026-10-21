import argparse
import os
from pathlib import Path
import subprocess
import sys


ROOT = Path(__file__).resolve().parents[1]
from check_support import capture_check_path
CAPTURE = capture_check_path()


def main():
    parser = argparse.ArgumentParser(description="仅编译上传组件及原测试，不连接外部服务")
    parser.add_argument("--execution", required=True)
    args = parser.parse_args()
    if Path(args.execution).name != args.execution or args.execution in (".", ".."):
        raise ValueError("执行身份必须使用单一目录名称")
    java = (ROOT / ".implementation-work/toolchains/jdk-17.0.20.1+1/Contents/Home").resolve(strict=True)
    maven = (ROOT / ".implementation-work/toolchains/apache-maven-3.9.16/bin/mvn").resolve(strict=True)
    inputs = [ROOT / "scripts/check_upload_compile.py", ROOT / "business/pom.xml",
        ROOT / ".implementation-work/maven-repository/org/redisson/redisson/3.52.0/redisson-3.52.0.jar"]
    for module in ("medical-contracts", "medical-identity", "medical-persistence", "medical-data"):
        inputs.append(ROOT / "business" / module / "pom.xml")
        inputs.extend(path for path in sorted((ROOT / "business" / module / "src").rglob("*")) if path.is_file())
    inputs.extend((ROOT / "LLD-后端.md", ROOT / "backend-design/接口补充设计.md"))
    output = ROOT / ".implementation-work" / ("IC-05-UPLOAD-COMPILE-" + args.execution)
    capture = [sys.executable, str(CAPTURE), "--output-dir", str(output),
        "--check-id", "IC-05-UPLOAD-COMPILE", "--kind", "STATIC", "--baseline-id", "BACK-UPLOAD-PERMIT-DEADLINE-01",
        "--cwd", str(ROOT / "business"), "--timeout", "600"]
    for path in inputs:
        capture.extend(("--input", str(path)))
    artifacts = [ROOT / "business/medical-persistence/target/classes/com/ruoyi/medical/persistence/TransferConcurrency.class",
        ROOT / "business/medical-persistence/target/test-classes/com/ruoyi/medical/persistence/TransferConcurrencyTest.class"]
    for path in artifacts:
        capture.extend(("--artifact", str(path)))
    temporary = ROOT / ".implementation-work/upload-compile-tmp"
    temporary.mkdir(parents=True, exist_ok=True)
    environment = os.environ.copy()
    environment["JAVA_HOME"] = str(java)
    environment["TMPDIR"] = str(temporary)
    environment["MAVEN_OPTS"] = "-Dfile.encoding=UTF-8 -Djava.io.tmpdir=" + str(temporary)
    command = [str(maven), "--offline", "--batch-mode", "--no-transfer-progress",
        "-Dmaven.repo.local=" + str(ROOT / ".implementation-work/maven-repository"),
        "-pl", "medical-persistence", "-am", "test-compile"]
    subprocess.run([*capture, "--", *command], cwd=ROOT, env=environment, check=True)


if __name__ == "__main__":
    main()
