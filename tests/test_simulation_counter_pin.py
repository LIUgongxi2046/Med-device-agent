"""Published JVM counter pin must describe actual Python source + measurement, not approval."""
from pathlib import Path
import hashlib
import json
import unittest
from medical_model_gateway.simulation_control import current_counter
ROOT=Path(__file__).resolve().parents[1]

class SimulationCounterPinTest(unittest.TestCase):
    def test_published_identity_matches_actual_counter_and_source_bytes(self):
        actual=current_counter().model_dump(mode='json')
        source=ROOT/'model-gateway/src/medical_model_gateway/simulation_control.py'
        self.assertEqual(actual['implementation_sha256'],hashlib.sha256(source.read_bytes()).hexdigest())
        pin=json.loads((ROOT/'business/medical-persistence/src/main/resources/simulation-counter-identity.json').read_text())
        self.assertEqual(pin,actual)
        self.assertEqual(pin['unit'],'CANONICAL_UTF8_BYTE')
        self.assertEqual(set(pin),{'unit','revision','implementation_sha256','measurement_schema_sha256'})

if __name__=='__main__':unittest.main()
