from copy import deepcopy
import hashlib
import json
from pathlib import Path
import unittest
from uuid import uuid4

from jsonschema import ValidationError

from medical_contracts import SchemaCatalogue
from medical_contracts.wire import code_point_quote, decimal_value, unsigned_revision, utc_time, uuid_value


ROOT = Path(__file__).resolve().parents[1]


class SharedContractChecks(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.catalogue = SchemaCatalogue(ROOT)
        cls.dataset = json.loads((ROOT / "data-design/sample_dataset.json").read_bytes())

    def test_registered_actual_dataset_is_accepted(self):
        self.catalogue.validate("dataset", self.dataset)

    def test_undefined_fields_are_rejected_at_the_boundary(self):
        changed = deepcopy(self.dataset)
        changed["production_approved"] = True
        with self.assertRaises(ValidationError):
            self.catalogue.validate("dataset", changed)

    def test_damaged_schema_version_is_rejected(self):
        changed = deepcopy(self.dataset)
        changed["schema_version"] += "-changed"
        with self.assertRaises(ValidationError):
            self.catalogue.validate("dataset", changed)

    def test_malformed_actual_timestamp_is_rejected(self):
        changed = deepcopy(self.dataset)
        changed["created_at"] = changed["created_at"].replace("T", "X", 1)
        with self.assertRaises(ValidationError):
            self.catalogue.validate("dataset", changed)

    def test_registered_locators_are_validated_without_changing_source_identity(self):
        for record in self.dataset["records"]:
            with self.subTest(record_id=record["record_id"]):
                self.catalogue.validate("dataset", record["locator"], "locator")
                self.assertEqual(
                    hashlib.sha256(record["text"].encode("utf-8")).hexdigest(),
                    record["quote_hash"],
                )

    def test_all_registered_schema_references_resolve_offline(self):
        self.assertEqual(set(self.catalogue.schemas), {"dataset", "interfaces", "resource-details", "artifacts", "model-stream", "templates", "template-operations", "tasks", "collections", "rule-expressions", "template-applicability", "agent-release", "task-plan-view"})
        self.assertGreater(self.catalogue.reference_count, 0)
        for name, schema in self.catalogue.schemas.items():
            for definition in schema["$defs"]:
                self.catalogue.registry.resolver().lookup(
                    self.catalogue.schema_ref(name, definition)
                )
        with self.assertRaises(ValueError):
            self.catalogue.validate("https://unregistered.invalid/schema", self.dataset)

    def test_binding_preserves_the_actual_dataset_and_does_not_share_mutable_state(self):
        bound = self.catalogue.bind("dataset", self.dataset)
        self.assertEqual(bound.materialize(), self.dataset)
        changed = bound.materialize()
        changed["dataset_version"] += "-changed"
        self.assertEqual(bound.materialize(), self.dataset)

    def test_template_publication_uses_the_declared_identity_and_all_original_definitions(self):
        for name in ("templates", "template-operations", "tasks", "collections", "agent-release", "task-plan-view"):
            document = self.catalogue.schemas[name]
            self.assertEqual(self.catalogue.schema_ref(name), document["$id"])
            for definition, schema in document["$defs"].items():
                reference = self.catalogue.schema_ref(name, definition)
                self.assertEqual(reference, document["$id"] + "#/$defs/" + definition)
                self.assertEqual(self.catalogue.registry.resolver().lookup(reference).contents, schema)

    def test_template_hash_binding_preserves_actual_authorized_originals(self):
        for source in self.dataset["sources"]:
            bound = self.catalogue.bind("templates", source["sha256"], "Hash")
            self.assertEqual(bound.schema_ref, "urn:medical:templates:1.0.0#/$defs/Hash")
            self.assertEqual(bound.materialize(), source["sha256"])
            with self.assertRaises(ValidationError):
                self.catalogue.bind("templates", source, "Hash")
        with self.assertRaises(ValidationError):
            self.catalogue.bind("templates", self.dataset, "Content")
        with self.assertRaises(ValidationError):
            self.catalogue.bind("template-operations", self.dataset)

    def test_task_inputs_share_the_complete_source_and_plan_definitions(self):
        definitions = self.catalogue.schemas["tasks"]["$defs"]
        self.assertEqual(definitions["Create"]["properties"]["scope_selection"]["$ref"],
                         self.catalogue.schema_ref("interfaces", "SourceScope"))
        self.assertEqual(definitions["PlanVersionInput"]["properties"]["content"]["$ref"],
                         self.catalogue.schema_ref("artifacts", "PlanCandidate"))
        for definition in ("Create", "PlanVersionInput"):
            with self.assertRaises(ValidationError):
                self.catalogue.bind("tasks", self.dataset, definition)
        for mode in ("PROJECT_APPROVED", "COLLECTION_VERSIONS", "SOURCE_VERSIONS", "COMBINED"):
            scope = {"mode": mode, "collection_versions": [], "source_versions": [], "network_enabled": False,
                     "network_policy_revision": None, "approved_query_revision": None}
            self.assertEqual(self.catalogue.bind("interfaces", scope, "SourceScope").materialize(), scope)
            with self.assertRaises(ValidationError):
                self.catalogue.bind("interfaces", {**scope, "network_enabled": True}, "SourceScope")

    def test_text_ranges_reopen_actual_registered_source_bytes(self):
        sources = {entry["source_id"]: entry for entry in self.dataset["sources"]}
        checked = 0
        for record in self.dataset["records"]:
            locator = record["locator"]
            if locator["kind"] not in {"TXT", "MD"}:
                continue
            source = sources[record["source_id"]]
            original = (ROOT / source["source_ref"]).read_bytes().decode("utf-8")
            quote = code_point_quote(
                original, locator["char_start"], locator["char_end"], record["quote_hash"]
            )
            self.assertEqual(quote, record["text"])
            with self.assertRaises(ValueError):
                code_point_quote(original, locator["char_start"], locator["char_end"], source["sha256"])
            checked += 1
        self.assertGreater(checked, 0)

    def test_technical_wire_boundaries_do_not_create_business_records(self):
        execution_id = str(uuid4())
        self.assertEqual(str(uuid_value(execution_id)), execution_id)
        with self.assertRaises(ValueError):
            uuid_value(execution_id.upper())
        maximum = str((1 << 64) - 1)
        self.assertEqual(str(unsigned_revision(maximum)), maximum)
        for invalid in [maximum + "0", "01", "1.0", 1, True]:
            with self.subTest(invalid=invalid), self.assertRaises(ValueError):
                unsigned_revision(invalid)
        self.assertEqual(str(decimal_value("0.00000001", 24, 8)), "1E-8")
        self.assertEqual(format(decimal_value("0.000000000001", 38, 12), "f"), "0.000000000001")
        for invalid in ["0.000000001", "10000000000000000", "1e-8", "NaN", 0.1]:
            with self.subTest(invalid=invalid), self.assertRaises(ValueError):
                decimal_value(invalid, 24, 8)
        actual_time = self.dataset["created_at"]
        self.assertEqual(utc_time(actual_time).isoformat(), actual_time)
        for invalid in [actual_time.replace("+00:00", "+08:00"), actual_time[:-6], actual_time.replace("T", " ")]:
            with self.subTest(invalid=invalid), self.assertRaises(ValueError):
                utc_time(invalid)


if __name__ == "__main__":
    unittest.main(verbosity=2)
