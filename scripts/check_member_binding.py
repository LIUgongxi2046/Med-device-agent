import argparse
import os
from pathlib import Path
import subprocess
import sys

from local_runtime import verify_application_deployment


ROOT = Path(__file__).resolve().parents[1]
CAPTURE = Path("/Users/liuhaoxian/.agents/skills/haonan-s000-pmp/scripts/capture_check.py")


def main():
    parser = argparse.ArgumentParser(description="运行真实认证身份的Java邀请绑定检查")
    parser.add_argument("--execution", required=True)
    args = parser.parse_args()
    if Path(args.execution).name != args.execution or args.execution in (".", ".."):
        raise ValueError("执行身份必须为单一目录名称")
    deployment = verify_application_deployment()
    repository = ROOT / ".implementation-work/maven-repository"
    dependencies = [
        "org/junit/jupiter/junit-jupiter-api/5.12.2/junit-jupiter-api-5.12.2.jar",
        "org/junit/platform/junit-platform-commons/1.12.2/junit-platform-commons-1.12.2.jar",
        "org/opentest4j/opentest4j/1.3.0/opentest4j-1.3.0.jar",
        "org/apiguardian/apiguardian-api/1.1.2/apiguardian-api-1.1.2.jar",
    ]
    libraries = [repository / path for path in dependencies]
    output = ROOT / (".implementation-work/IC-04-MEMBER-BINDING-" + args.execution)
    java = ROOT / ".implementation-work/toolchains/jdk-17.0.20.1+1/Contents/Home/bin/java"
    jar = ROOT / deployment["artifact"]
    classes = ROOT / "business/medical-identity/target/test-classes"
    source = "business/medical-identity/src/test/java/com/ruoyi/medical/identity/MemberInvitationBindingIT.java"
    inputs = ["scripts/check_member_binding.py", "scripts/local_runtime.py", source,
              ".implementation-work/runtime/application-deployment.json", str(jar.relative_to(ROOT)),
              "backend-design/接口补充设计.md", str(java.relative_to(ROOT))]
    inputs.extend(str(path.relative_to(ROOT)) for path in libraries)
    inputs.extend(sorted(str(path.relative_to(ROOT)) for path in classes.rglob("MemberInvitationBindingIT*.class")))
    inputs.extend(["business/medical-identity/src/main/java/com/ruoyi/medical/identity/IdentityBindingSchema.java",
                   "business/medical-identity/src/main/java/com/ruoyi/medical/identity/AuthenticatedIdentityBinding.java",
                   "business/medical-identity/src/main/resources/identity-binding.schema.json",
                   "business/medical-identity/src/main/resources/identity-binding-manifest.json"])
    command = [sys.executable, str(CAPTURE), "--output-dir", str(output), "--check-id", "IC-04-MEMBER-BINDING",
               "--kind", "INTEGRATION", "--baseline-id", "BACK-MEMBER-IDENTITY-01", "--cwd", str(ROOT), "--timeout", "120"]
    for path in inputs:
        command.extend(["--input", path])
    command.extend(["--artifact", str(output / "binding.json")])
    environment = os.environ.copy(); environment["TMPDIR"] = str(ROOT / ".implementation-work/java-temp")
    check = [str(java), "-Djava.io.tmpdir=" + str(ROOT / ".implementation-work/java-temp"),
             "-Dmedical.project-root=" + str(ROOT),
             "-Dloader.main=com.ruoyi.medical.identity.MemberInvitationBindingIT",
             "-Dloader.path=" + ",".join(str(path.resolve(strict=True)) for path in [classes, *libraries]),
             "-cp", str(jar), "org.springframework.boot.loader.launch.PropertiesLauncher", str(output / "binding.json")]
    subprocess.run([*command, "--", *check], cwd=ROOT, env=environment, check=True)
    if verify_application_deployment() != deployment:
        raise RuntimeError("LOCAL_APPLICATION_DEPLOYMENT_CHANGED")


if __name__ == "__main__":
    main()
