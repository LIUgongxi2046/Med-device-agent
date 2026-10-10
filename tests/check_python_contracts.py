from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
loader = unittest.TestLoader()
suite = unittest.TestSuite()
for pattern in ("test_shared_contracts.py", "test_python_domain.py", "test_error_codes.py"):
    suite.addTests(loader.discover(str(ROOT / "tests"), pattern=pattern))
result = unittest.TextTestRunner(verbosity=2).run(suite)
if not result.wasSuccessful():
    raise SystemExit(1)
