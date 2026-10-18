"""Resolve build tools without assuming the original developer's macOS layout."""
import os
from pathlib import Path
import shutil


def resolve_toolchains(root: Path, environment=None):
    env = os.environ if environment is None else environment
    toolchains = root / ".implementation-work/toolchains"
    configured_java = env.get("MEDICAL_JAVA_HOME") or env.get("JAVA_HOME")
    candidates = ([Path(configured_java)] if configured_java else [
        toolchains / "jdk-17.0.20.1+1/Contents/Home",
        toolchains / "jdk-17.0.20.1+1",
    ])
    java_home = next((p for p in candidates if all(
        (p / "bin" / name).is_file() and os.access(p / "bin" / name, os.X_OK)
        for name in ("java", "javac")
    )), None)
    if java_home is None:
        raise ValueError("JDK_NOT_FOUND: set MEDICAL_JAVA_HOME to a complete JDK (java and javac required)")
    configured_maven = env.get("MEDICAL_MAVEN")
    maven_candidates = ([Path(configured_maven)] if configured_maven else [
        toolchains / "apache-maven-3.9.16/bin/mvn",
        Path(shutil.which("mvn", path=env.get("PATH", "")) or "/nonexistent/mvn"),
    ])
    maven = next((p for p in maven_candidates if p.is_file() and os.access(p, os.X_OK)), None)
    if maven is None:
        raise ValueError("MAVEN_NOT_FOUND: set MEDICAL_MAVEN to the Maven executable")
    return java_home.resolve(), maven.resolve()
