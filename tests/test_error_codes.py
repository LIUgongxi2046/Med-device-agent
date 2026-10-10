from copy import deepcopy
import json
from pathlib import Path
import unittest

from jsonschema import ValidationError

import medical_contracts
from medical_contracts import SchemaCatalogue
from error_design import published_error_codes


ROOT = Path(__file__).resolve().parents[1]


class ErrorCodeChecks(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.catalogue = SchemaCatalogue(ROOT)
        cls.dataset = json.loads((ROOT / "data-design/sample_dataset.json").read_bytes())

    def test_codes_cover_the_published_backend_design(self):
        self.assertTrue(hasattr(medical_contracts, "ErrorCode"), "共享库必须发布 ErrorCode")
        self.assertEqual(sorted(code.value for code in medical_contracts.ErrorCode), published_error_codes(ROOT))

    def test_invalid_actual_dataset_has_a_safe_machine_code(self):
        changed = deepcopy(self.dataset)
        changed["schema_version"] += "-changed"
        with self.assertRaises(ValidationError) as raised:
            self.catalogue.validate("dataset", changed)
        error = raised.exception
        self.assertEqual(getattr(error, "code", None), "SCHEMA_INVALID")
        self.assertEqual(error.message, "SCHEMA_VALIDATION_FAILED")
        self.assertIsNone(error.__cause__)
        self.assertNotIn(changed["schema_version"], str(error))
        with self.assertRaises(AttributeError):
            error.code = "INTERNAL_ERROR"

    def test_unregistered_actual_source_identity_has_a_safe_machine_code(self):
        source_id = self.dataset["sources"][0]["source_id"]
        with self.assertRaises(ValueError) as raised:
            self.catalogue.schema_ref(source_id)
        self.assertEqual(getattr(raised.exception, "code", None), "SCHEMA_UNSUPPORTED")
        self.assertEqual(str(raised.exception), "SCHEMA_UNSUPPORTED")
        self.assertIsNone(raised.exception.__cause__)
        with self.assertRaises(AttributeError):
            raised.exception.code = "INTERNAL_ERROR"

    def test_unknown_codes_and_types_are_rejected_without_input_disclosure(self):
        self.assertTrue(hasattr(medical_contracts, "parse_error_code"), "共享库必须提供严格错误代码读取")
        for value in (self.dataset["sources"][0]["source_id"], None, self.dataset["sources"]):
            with self.subTest(value_type=type(value).__name__), self.assertRaises(ValueError) as raised:
                medical_contracts.parse_error_code(value)
            self.assertEqual(raised.exception.code, "SCHEMA_UNSUPPORTED")
            self.assertEqual(str(raised.exception), "SCHEMA_UNSUPPORTED")
        for value in published_error_codes(ROOT):
            self.assertEqual(medical_contracts.parse_error_code(value).value, value)
