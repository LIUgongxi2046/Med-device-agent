import argparse
import json
import os
from pathlib import Path
import subprocess
import sys


ROOT = Path(__file__).resolve().parents[1]
CAPTURE = Path("/Users/liuhaoxian/.agents/skills/haonan-s000-pmp/scripts/capture_check.py")


def main():
    parser = argparse.ArgumentParser(description="保存迁移制品与共享表示的真实检查证据")
    parser.add_argument("--check", choices=("build", "coverage", "cross", "entrypoint"), required=True)
    parser.add_argument("--execution", required=True)
    args = parser.parse_args()
    execution = Path(args.execution)
    if execution.name != args.execution or args.execution in (".", ".."):
        raise ValueError("执行身份必须为单一目录名称")

    schemas = [
        "contracts/catalogue.json", "contracts/extensions/evaluation-results.catalogue.json", "data-design/dataset.schema.json", "data-design/sample_dataset.json",
        "backend-design/interfaces.schema.yaml", "backend-design/resource-details.schema.yaml",
        "agent-design/artifacts.schema.json", "agent-design/model-stream.schema.json",
        "business/medical-persistence/src/main/resources/templates.schema.json",
        "business/medical-persistence/src/main/resources/template-operations.schema.json",
        "business/medical-persistence/src/main/resources/tasks.schema.json",
        "business/medical-persistence/src/main/resources/collections.schema.json",
        "business/medical-persistence/src/main/resources/rule-expressions.schema.json",
        "business/medical-persistence/src/main/resources/template-applicability.schema.json",
        "business/medical-persistence/src/main/resources/evaluation-results.schema.json",
        ".implementation-work/IC-05-OBJECT-STORAGE-20261005-OBJECT-STORAGE-FINAL-01/objects.json",
        "ui-delivery/assets/public-evidence/clinical-evaluation-2021.docx",
        "ui-delivery/assets/public-evidence/safety-principles-2021.doc",
    ]
    migration = sorted(str(path.relative_to(ROOT)) for path in (ROOT / "business/medical-data/src").rglob("*") if path.is_file())
    java = sorted(str(path.relative_to(ROOT)) for path in (ROOT / "business/medical-contracts/src").rglob("*.java"))
    persistence = sorted(str(path.relative_to(ROOT)) for path in (ROOT / "business/medical-persistence/src").rglob("*") if path.is_file())
    identity = sorted(str(path.relative_to(ROOT)) for path in (ROOT / "business/medical-identity/src").rglob("*") if path.is_file())
    runtime = sorted(str(path.relative_to(ROOT)) for path in (ROOT / "business/ruoyi-admin/src/main/java/com/ruoyi/medical").rglob("*") if path.is_file())
    runtime.append("business/ruoyi-admin/src/main/resources/application-medical-local.yml")
    runtime.append("business/ruoyi-admin/src/main/resources/logback-medical.xml")
    runtime.append("business/ruoyi-admin/src/main/resources/i18n/messages.properties")
    common = ["scripts/check_migration.py", "LLD-后端.md", "backend-design/接口补充设计.md", "backend-design/interfaces.openapi.yaml", *schemas]
    artifacts = []
    if args.check == "build":
        check_id, kind, timeout = "IC-03-JAVA", "UNIT", 1800
        inputs = [*common, *java, *migration, *persistence, *identity, *runtime, "business/pom.xml", "business/ruoyi-admin/pom.xml",
                  "business/medical-contracts/pom.xml", "business/medical-data/pom.xml", "scripts/build_business.py", "scripts/check_collections.py",
                  "scripts/prepare_service_certificates.py", "scripts/requirements-native-security.txt",
                  "tests/check_permission_sql_columns.py", "tests/check_invitation_sql_columns.py", "tests/check_role_sql_columns.py", "tests/check_project_sql_columns.py", "tests/check_operation_sql_columns.py", "tests/check_provisioning_sql_columns.py", "tests/check_budget_sql_columns.py", "tests/test_migration_bundle.py", "tests/InspectAlterMigrations.java", "tests/InspectXmlMigrations.java", "tests/InspectFrameworkTables.java"]
        inputs.extend(("business/medical-persistence/pom.xml", "business/medical-persistence/.gitignore",
                       "business/medical-identity/pom.xml", "business/medical-identity/.gitignore",
                       "business/ruoyi-framework/src/main/java/com/ruoyi/framework/config/MyBatisConfig.java",
                       "business/ruoyi-framework/src/main/java/com/ruoyi/framework/config/ApplicationConfig.java"))
        for directory in ("business/ruoyi-system/src", "business/ruoyi-common/src", "business/ruoyi-framework/src",
                "business/ruoyi-quartz/src", "business/ruoyi-generator/src"):
            inputs.extend(sorted(str(path.relative_to(ROOT)) for path in (ROOT / directory).rglob("*") if path.is_file()))
        inputs.extend(sorted(str(path.relative_to(ROOT)) for path in (ROOT / "business/ruoyi-admin/src/test").rglob("*") if path.is_file()))
        inputs.extend(sorted(str(path.relative_to(ROOT)) for path in (ROOT / "business/ruoyi-admin/src/main/java/com/ruoyi/web").rglob("*") if path.is_file()))
        inputs.extend(("business/ruoyi-system/pom.xml", "business/ruoyi-common/pom.xml", "business/ruoyi-framework/pom.xml",
            "business/ruoyi-quartz/pom.xml", "business/ruoyi-generator/pom.xml", "business/sql/ry_20260417.sql"))
        inputs.extend(("web/index.html", "web/package.json", "web/package-lock.json", "web/vite.config.js",
            "web/tsconfig.medical.json", "web/.env.medical"))
        for directory in ("web/src", "web/vite", "web/public"):
            inputs.extend(sorted(str(path.relative_to(ROOT)) for path in (ROOT / directory).rglob("*") if path.is_file()))
        artifacts = [".implementation-work/IC-03-JAVA-SNAPSHOT/current.json",
                     ".implementation-work/java-runtime-checks/current.json",
                     "business/medical-contracts/target/medical-contracts-3.9.2.jar",
                     "business/medical-data/target/medical-data-3.9.2.jar",
                     "business/medical-persistence/target/medical-persistence-3.9.2.jar",
                     "business/medical-identity/target/medical-identity-3.9.2.jar",
                     "business/medical-data/target/medical-data-3.9.2-cli.jar", "business/ruoyi-admin/target/ruoyi-admin.jar"]
        command = [sys.executable, "scripts/build_business.py"]
    elif args.check == "coverage":
        check_id, kind, timeout = "IC-03-MIGRATION-COVERAGE", "STATIC", 600
        inputs = [*common, *migration, "data-design/core.sql", "tests/test_migration_bundle.py",
                  "tests/InspectFrameworkTables.java", "business/sql/ry_20260417.sql"]
        inputs.extend(["tests/InspectAlterMigrations.java", "tests/InspectXmlMigrations.java",
            ".implementation-work/maven-repository/com/github/jsqlparser/jsqlparser/5.2/jsqlparser-5.2.jar",
            ".implementation-work/maven-repository/com/fasterxml/jackson/core/jackson-databind/2.21.4/jackson-databind-2.21.4.jar",
            ".implementation-work/maven-repository/com/fasterxml/jackson/core/jackson-core/2.21.4/jackson-core-2.21.4.jar",
            ".implementation-work/maven-repository/com/fasterxml/jackson/core/jackson-annotations/2.21/jackson-annotations-2.21.jar",
            ".implementation-work/toolchains/jdk-17.0.20.1+1/Contents/Home/bin/java"])
        command = [sys.executable, "tests/test_migration_bundle.py"]
    elif args.check == "entrypoint":
        check_id, kind, timeout = "IC-03-MIGRATION-CLI", "UNIT", 600
        inputs = [*common, *migration, "data-design/core.sql", "business/medical-data/pom.xml",
                  "scripts/run_migration.py", "tests/test_migration_cli.py",
                  "business/medical-data/target/medical-data-3.9.2.jar",
                  "business/medical-data/target/medical-data-3.9.2-cli.jar"]
        command = [sys.executable, "tests/test_migration_cli.py"]
    else:
        check_id, kind, timeout = "IC-03-CROSS", "INTEGRATION", 600
        pointer = ".implementation-work/IC-03-JAVA-SNAPSHOT/current.json"
        report = json.loads((ROOT / pointer).read_bytes())["report_path"]
        report_path = (ROOT / report).resolve(strict=True)
        report_path.relative_to((ROOT / ".implementation-work/IC-03-JAVA-SNAPSHOT/executions").resolve(strict=True))
        report = str(report_path.relative_to(ROOT))
        typescript = sorted(str(path.relative_to(ROOT)) for path in (ROOT / "contracts/typescript/src").rglob("*") if path.is_file())
        python = sorted(str(path.relative_to(ROOT)) for path in (ROOT / "contracts/python/src").rglob("*.py"))
        inputs = [*common, *java, *typescript, *python, "business/pom.xml", "business/medical-contracts/pom.xml",
                  "tests/check_cross_language.py", "tests/error_design.py", "contracts/typescript/package.json", "contracts/typescript/package-lock.json",
                  "contracts/python/pyproject.toml", "contracts/python/requirements.lock", pointer, report]
        artifacts = [report]
        command = [sys.executable, "tests/check_cross_language.py"]
    if args.check == "build":
        inputs.append("data-design/core.sql")
        for group in ("com/github/jnr", "org/ow2/asm"):
            dependencies = ROOT / ".implementation-work/maven-repository" / group
            inputs.extend(str(path.relative_to(ROOT)) for path in sorted(dependencies.rglob("*"))
                if path.is_file() and path.suffix in (".jar", ".pom"))
        inputs.extend(("PRD.md", "data-design/sample_dataset.json", "scripts/check_template_directory.py",
                       "ui-delivery/document-reading-design.md",
                       ".implementation-work/maven-repository/com/networknt/json-schema-validator/1.5.9/json-schema-validator-1.5.9.jar",
                       "ui-delivery/research/official/registration-requirements-2021-121.txt",
                       ".implementation-work/maven-repository/org/jgrapht/jgrapht-core/1.5.2/jgrapht-core-1.5.2.jar"))
        inputs.extend((".implementation-work/runtime/object-storage/deployment.json",
                       ".implementation-work/maven-repository/org/redisson/redisson/3.52.0/redisson-3.52.0.jar",
                       ".implementation-work/maven-repository/com/github/ben-manes/caffeine/caffeine/3.2.4/caffeine-3.2.4.jar",
                       ".implementation-work/maven-repository/com/baomidou/mybatis-plus-jsqlparser/3.5.17/mybatis-plus-jsqlparser-3.5.17.jar",
                       ".implementation-work/maven-repository/software/amazon/awssdk/sdk-core/2.55.11/sdk-core-2.55.11.jar",
                       ".implementation-work/maven-repository/software/amazon/awssdk/apache-client/2.55.11/apache-client-2.55.11.jar",
                       ".implementation-work/maven-repository/org/apache/httpcomponents/httpclient/4.5.13/httpclient-4.5.13.jar",
                       ".implementation-work/maven-repository/org/apache/httpcomponents/httpcore/4.4.16/httpcore-4.4.16.jar",
                       ".implementation-work/maven-repository/org/springframework/spring-web/6.2.19/spring-web-6.2.19.jar",
                       ".implementation-work/maven-repository/org/springframework/spring-core/6.2.19/spring-core-6.2.19.jar",
                       ".implementation-work/maven-repository/io/projectreactor/reactor-core/3.7.19/reactor-core-3.7.19.jar",
                       ".implementation-work/maven-repository/org/reactivestreams/reactive-streams/1.0.4/reactive-streams-1.0.4.jar",
                       ".implementation-work/maven-repository/org/apache/tomcat/embed/tomcat-embed-core/10.1.55/tomcat-embed-core-10.1.55.jar",
                       ".implementation-work/maven-repository/jakarta/servlet/jakarta.servlet-api/6.0.0/jakarta.servlet-api-6.0.0.jar",
                       ".implementation-work/maven-repository/org/awaitility/awaitility/4.2.2/awaitility-4.2.2.jar",
                       ".implementation-work/maven-repository/org/hamcrest/hamcrest/3.0/hamcrest-3.0.jar",
                       ".implementation-work/maven-repository/org/junit/jupiter/junit-jupiter-params/5.12.2/junit-jupiter-params-5.12.2.jar",
                       ".implementation-work/toolchains/seaweedfs-4.48/weed",
                       ".implementation-work/toolchains/toxiproxy_2.12.0_darwin_amd64.tar.gz",
                       ".implementation-work/toolchains/toxiproxy-checksums-v2.12.0.txt",
                       ".implementation-work/toolchains/toxiproxy-2.12.0/toxiproxy-server",
                       ".implementation-work/runtime/service-identity.json",
                       ".implementation-work/maven-repository/com/mysql/mysql-connector-j/9.7.0/mysql-connector-j-9.7.0.jar"))
    output = ".implementation-work/" + check_id + "-" + args.execution
    capture = [sys.executable, str(CAPTURE), "--output-dir", output, "--check-id", check_id,
               "--kind", kind, "--baseline-id", "shared-schemas-and-migration-1.0.0",
               "--cwd", str(ROOT), "--timeout", str(timeout)]
    for path in inputs:
        capture.extend(["--input", path])
    for path in artifacts:
        capture.extend(["--artifact", path])
    environment = os.environ.copy()
    environment["PYTHONPATH"] = str(ROOT / "contracts/python/src")
    temporary = ROOT / ".implementation-work/migration-check-tmp"
    temporary.mkdir(parents=True, exist_ok=True)
    environment["TMPDIR"] = str(temporary)
    subprocess.run([*capture, "--", *command], cwd=ROOT, env=environment, check=True)


if __name__ == "__main__":
    main()
