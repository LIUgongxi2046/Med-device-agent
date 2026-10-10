import argparse
import os
from pathlib import Path
import shutil
import subprocess
import sys


ROOT = Path(__file__).resolve().parents[1]
CAPTURE = Path("/Users/liuhaoxian/.agents/skills/haonan-s000-pmp/scripts/capture_check.py")
REPORT = "TEST-com.ruoyi.medical.identity.RunTokenPolicyStoreTest.xml"


def main():
    parser = argparse.ArgumentParser(description="验证当前受保护配置和实际RSA签名消费者")
    parser.add_argument("--execution", required=True)
    parser.add_argument("--positive-only", action="store_true")
    args = parser.parse_args()
    if Path(args.execution).name != args.execution or args.execution in (".", ".."):
        raise ValueError("执行身份必须为单一目录名称")
    java = ROOT / ".implementation-work/toolchains/jdk-17.0.20.1+1/Contents/Home"
    maven = ROOT / ".implementation-work/toolchains/apache-maven-3.9.16/bin/mvn"
    output = ROOT / ".implementation-work" / ("IC-04-RUN-TOKEN-POLICY-" + args.execution)
    inputs = [Path(__file__).resolve(), ROOT / "business/pom.xml", ROOT / "docs/development/IMP-04.md", ROOT / "LLD-后端.md",
        java / "bin/java", java / "lib/modules",
        ROOT / ".implementation-work/maven-repository/com/nimbusds/nimbus-jose-jwt/9.37.4/nimbus-jose-jwt-9.37.4.jar",
        ROOT / ".implementation-work/maven-repository/com/networknt/json-schema-validator/1.5.9/json-schema-validator-1.5.9.jar"]
    for module in ("medical-contracts", "medical-identity", "ruoyi-common", "ruoyi-system", "ruoyi-framework"):
        inputs.append(ROOT / "business" / module / "pom.xml")
        inputs.extend(path for path in sorted((ROOT / "business" / module / "src").rglob("*")) if path.is_file())
    for group in ("com/github/jnr", "org/ow2/asm"):
        inputs.extend(path for path in sorted((ROOT / ".implementation-work/maven-repository" / group).rglob("*"))
            if path.is_file() and path.suffix in (".jar", ".pom"))
    capture = [sys.executable, str(CAPTURE), "--output-dir", str(output), "--check-id", "IC-04-RUN-TOKEN-POLICY",
        "--kind", "UNIT", "--baseline-id", "BACK-CURRENT-RUN-TOKEN-POLICY-01", "--cwd", str(ROOT / "business"), "--timeout", "600"]
    for path in inputs:
        capture.extend(("--input", str(path)))
    original = ROOT / "business/medical-identity/target/surefire-reports" / REPORT
    capture.extend(("--artifact", str(original)))
    temporary = ROOT / ".implementation-work/run-token-policy-tmp"
    temporary.mkdir(parents=True, exist_ok=True)
    environment = os.environ.copy()
    environment.update({"JAVA_HOME": str(java), "TMPDIR": str(temporary),
        "MAVEN_OPTS": "-Dfile.encoding=UTF-8 -Djava.io.tmpdir=" + str(temporary)})
    selected = "RunTokenPolicyStoreTest#readsCurrentConfigurationAndVerifiesActualSignedToken" if args.positive_only else "RunTokenPolicyStoreTest,RunTokenSignatureTest"
    command = [str(maven), "--offline", "--batch-mode", "--no-transfer-progress",
        "-Dmaven.repo.local=" + str(ROOT / ".implementation-work/maven-repository"), "-pl", "medical-identity", "-am",
        "-Dtest=" + selected, "-Dsurefire.failIfNoSpecifiedTests=false", "test"]
    result = subprocess.run([*capture, "--", *command], cwd=ROOT, env=environment)
    shutil.copyfile(original, output / REPORT)
    if not args.positive_only:
        signature = original.with_name("TEST-com.ruoyi.medical.identity.RunTokenSignatureTest.xml")
        shutil.copyfile(signature, output / signature.name)
    raise SystemExit(result.returncode)


if __name__ == "__main__":
    main()
