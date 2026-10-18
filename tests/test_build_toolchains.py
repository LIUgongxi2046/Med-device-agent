import importlib.util
from pathlib import Path
import tempfile
import unittest

SPEC = importlib.util.spec_from_file_location("build_toolchains", Path(__file__).resolve().parents[1] / "scripts/build_toolchains.py")
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class BuildToolchainsTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.tools = self.root / ".implementation-work/toolchains"

    def executable(self, path):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text("#!/bin/sh\nexit 0\n")
        path.chmod(0o700)
        return path

    def jdk(self, suffix=""):
        home = self.tools / "jdk-17.0.20.1+1" / suffix
        for name in ("java", "javac"):
            self.executable(home / "bin" / name)
        return home

    def maven(self):
        return self.executable(self.tools / "apache-maven-3.9.16/bin/mvn")

    def test_linux_layout(self):
        home, maven = self.jdk(), self.maven()
        self.assertEqual(MODULE.resolve_toolchains(self.root, {}), (home, maven))

    def test_original_macos_layout(self):
        home, maven = self.jdk("Contents/Home"), self.maven()
        self.assertEqual(MODULE.resolve_toolchains(self.root, {}), (home, maven))

    def test_explicit_paths(self):
        home, maven = self.jdk(), self.maven()
        self.assertEqual(MODULE.resolve_toolchains(self.root, {"MEDICAL_JAVA_HOME": str(home), "MEDICAL_MAVEN": str(maven)}), (home, maven))

    def test_missing_explicit_java_does_not_fall_back(self):
        self.jdk(); self.maven()
        with self.assertRaisesRegex(ValueError, "JDK_NOT_FOUND"):
            MODULE.resolve_toolchains(self.root, {"MEDICAL_JAVA_HOME": str(self.root / "missing")})

    def test_runtime_without_compiler_is_rejected(self):
        home = self.jdk(); self.maven()
        (home / "bin/javac").unlink()
        with self.assertRaisesRegex(ValueError, "JDK_NOT_FOUND"):
            MODULE.resolve_toolchains(self.root, {})

    def test_missing_explicit_maven_does_not_fall_back(self):
        self.jdk(); self.maven()
        with self.assertRaisesRegex(ValueError, "MAVEN_NOT_FOUND"):
            MODULE.resolve_toolchains(self.root, {"MEDICAL_MAVEN": str(self.root / "missing")})

    def test_non_executable_tools_rejected(self):
        self.jdk(); maven = self.maven(); maven.chmod(0o600)
        with self.assertRaisesRegex(ValueError, "MAVEN_NOT_FOUND"):
            MODULE.resolve_toolchains(self.root, {})


if __name__ == "__main__":
    unittest.main()
