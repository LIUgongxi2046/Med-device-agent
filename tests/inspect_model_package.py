import importlib.util
from pathlib import Path
import sys
import unittest


ROOT = Path(__file__).resolve().parents[1]
target = Path(sys.argv[1]).resolve(strict=True)
sys.path.insert(0, str(target))

from medical_model_gateway import credentials, pricing, protocol, provider, simulation


for module in (credentials, pricing, protocol, provider, simulation):
    if not Path(module.__file__).resolve(strict=True).is_relative_to(target):
        raise ValueError("检查没有使用实际安装的wheel")
suite = unittest.TestSuite()
for name in ("test_model_gateway", "test_model_simulation"):
    specification = importlib.util.spec_from_file_location(name, ROOT / "tests" / (name + ".py"))
    module = importlib.util.module_from_spec(specification)
    specification.loader.exec_module(module)
    suite.addTests(unittest.defaultTestLoader.loadTestsFromModule(module))
result = unittest.TextTestRunner(verbosity=2).run(suite)
if not result.wasSuccessful():
    raise ValueError("实际wheel的适配器检查没有通过")
