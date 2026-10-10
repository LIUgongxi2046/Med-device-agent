import argparse
import os
from pathlib import Path
import subprocess
import sys


ROOT = Path(__file__).resolve().parents[1]
CAPTURE = Path("/Users/liuhaoxian/.agents/skills/haonan-s000-pmp/scripts/capture_check.py")


def main():
    parser = argparse.ArgumentParser(description="验证实际RSA运行令牌签名及封闭字段，不连接服务或数据库")
    parser.add_argument("--execution", required=True)
    parser.add_argument("--positive-only", action="store_true")
    args = parser.parse_args()
    if Path(args.execution).name != args.execution or args.execution in (".", ".."):
        raise ValueError("执行身份必须使用单一目录名称")
    java = (ROOT / ".implementation-work/toolchains/jdk-17.0.20.1+1/Contents/Home").resolve(strict=True)
    maven = (ROOT / ".implementation-work/toolchains/apache-maven-3.9.16/bin/mvn").resolve(strict=True)
    inputs = [ROOT / "scripts/check_run_token_signature.py", ROOT / "business/pom.xml", ROOT / "docs/development/IMP-04.md",
        ROOT / "LLD-后端.md", ROOT / "business/medical-data/src/main/resources/db/migration/002-identity-sources.sql",
        ROOT / "business/medical-data/src/main/resources/db/migration/004-task-execution.sql",
        ROOT / ".implementation-work/maven-repository/com/nimbusds/nimbus-jose-jwt/9.37.4/nimbus-jose-jwt-9.37.4.jar",
        java / "bin/java", java / "lib/modules"]
    for module in ("medical-contracts", "medical-identity", "ruoyi-common", "ruoyi-system", "ruoyi-framework"):
        inputs.append(ROOT / "business" / module / "pom.xml")
        inputs.extend(path for path in sorted((ROOT / "business" / module / "src").rglob("*")) if path.is_file())
    output = ROOT / ".implementation-work" / ("IC-04-RUN-TOKEN-" + args.execution)
    capture = [sys.executable, str(CAPTURE), "--output-dir", str(output), "--check-id", "IC-04-RUN-TOKEN",
        "--kind", "UNIT", "--baseline-id", "BACK-RUN-TOKEN-SIGNATURE-01", "--cwd", str(ROOT / "business"), "--timeout", "600"]
    for path in inputs:
        capture.extend(("--input", str(path)))
    capture.extend(("--artifact", str(ROOT / "business/medical-identity/target/surefire-reports/TEST-com.ruoyi.medical.identity.RunTokenSignatureTest.xml")))
    temporary = ROOT / ".implementation-work/run-token-check-tmp"
    temporary.mkdir(parents=True, exist_ok=True)
    environment = os.environ.copy(); environment["JAVA_HOME"] = str(java); environment["TMPDIR"] = str(temporary)
    environment["MAVEN_OPTS"] = "-Dfile.encoding=UTF-8 -Djava.io.tmpdir=" + str(temporary)
    selected = "RunTokenSignatureTest#actualSignedTokenPreservesCompleteClaims" if args.positive_only else "RunTokenSignatureTest"
    command = [str(maven), "--offline", "--batch-mode", "--no-transfer-progress",
        "-Dmaven.repo.local=" + str(ROOT / ".implementation-work/maven-repository"), "-pl", "medical-identity", "-am",
        "-Dtest=" + selected, "-Dsurefire.failIfNoSpecifiedTests=false", "test"]
    subprocess.run([*capture, "--", *command], cwd=ROOT, env=environment, check=True)


if __name__ == "__main__":
    main()
