#!/usr/bin/env bash
set -euo pipefail

project_directory="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
export JAVA_HOME="$project_directory/.implementation-work/toolchains/jdk-17.0.20.1+1/Contents/Home"
export TMPDIR="$project_directory/.implementation-work/migration-check-tmp"
export MAVEN_OPTS="-Xms128m -Xmx768m -Dfile.encoding=UTF-8 -Djava.io.tmpdir=$project_directory/.implementation-work/java-temp"

if (( $# == 0 )); then
  set -- "-Dmedical.testJvmMemory=-Xms128m -Xmx1024m" \
    "-Dtest=TemplateAcceptanceSchemaIT,VersionResourceMappingIT,SqlParseCacheTest,TenantAccessTest" \
    -Dsurefire.failIfNoSpecifiedTests=false
fi

exec "$project_directory/.implementation-work/toolchains/apache-maven-3.9.16/bin/mvn" \
  -f "$project_directory/business/pom.xml" --batch-mode --no-transfer-progress \
  "-Dmaven.repo.local=$project_directory/.implementation-work/maven-repository" \
  "-Dmedical.projectRoot=$project_directory" \
  "$@" -pl ruoyi-admin -am package
