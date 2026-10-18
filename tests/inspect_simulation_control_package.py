"""Read-only installed-wheel/schema verification; run with Python -I."""

import hashlib
import importlib.util
import json
from pathlib import Path
import sys
import unittest


ROOT = Path(__file__).resolve().parents[1]
target = Path(sys.argv[1]).resolve(strict=True)
sys.path.insert(0, str(target))

from medical_model_gateway import simulation_control


module_path = Path(simulation_control.__file__).resolve(strict=True)
if not module_path.is_relative_to(target):
    raise ValueError('SIMULATION_CONTROL_NOT_INSTALLED_WHEEL')
source = ROOT / 'model-gateway/src/medical_model_gateway/simulation_control.py'
if source.read_bytes() != module_path.read_bytes():
    raise ValueError('SIMULATION_CONTROL_WHEEL_SOURCE_MISMATCH')
if simulation_control.current_counter().implementation_sha256 != hashlib.sha256(source.read_bytes()).hexdigest():
    raise ValueError('SIMULATION_CONTROL_WHEEL_DIGEST_MISMATCH')
for name, schema in (
    ('simulation-control-component.schema.json', simulation_control.control_schema()),
    ('simulation-byte-measurement.schema.json', simulation_control.measurement_schema()),
):
    if json.loads((ROOT / 'agent-design' / name).read_bytes()) != schema:
        raise ValueError('SIMULATION_CONTROL_WHEEL_SCHEMA_MISMATCH')

spec = importlib.util.spec_from_file_location('test_simulation_control', ROOT / 'tests/test_simulation_control.py')
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)
result = unittest.TextTestRunner(verbosity=2).run(unittest.defaultTestLoader.loadTestsFromModule(module))
if not result.wasSuccessful():
    raise ValueError('SIMULATION_CONTROL_WHEEL_TESTS_FAILED')
