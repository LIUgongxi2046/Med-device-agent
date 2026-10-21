from copy import deepcopy
from contextlib import contextmanager
import hashlib
import json
from pathlib import Path
import shutil
from tempfile import TemporaryDirectory
import unittest
from urllib.parse import urljoin
from uuid import uuid4

from jsonschema import ValidationError
from referencing.exceptions import Unresolvable
import yaml

from medical_contracts import SchemaCatalogue
from medical_contracts.catalogue import _merge_published_catalogue
from medical_contracts.wire import code_point_quote, decimal_value, unsigned_revision, utc_time, uuid_value


ROOT = Path(__file__).resolve().parents[1]
EXTENSION_PATH = "contracts/extensions/evaluation-results.catalogue.json"


@contextmanager
def copied_publication():
    with TemporaryDirectory() as directory:
        root = Path(directory) / "project"
        # Explicit publication list: unknown extension files are never copied or trusted.
        manifests = ["contracts/catalogue.json", *[
            "contracts/extensions/" + name + ".catalogue.json" for name in (
                "evaluation-results", "simulation-execution", "task-runtime",
                "evaluation-context-compiler", "evaluation-planning-context-compiler",
                "task-plan-draft-package", "professional-review-packages", "source-evidence-v2",
                "document-evidence-v2", "template-application", "strict-field-evidence-v2",
                "document-operations-v2", "evaluation-business-v2", "task-tool-read",
                "task-professional-package", "task-professional-package-v2", "evaluation-stage-input")]]
        paths = list(manifests)
        for manifest in manifests:
            paths.extend(entry["path"] for entry in json.loads((ROOT / manifest).read_bytes())["schemas"])
        for relative in paths:
            target = root / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(ROOT / relative, target)
        yield root


class SharedContractChecks(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.catalogue = SchemaCatalogue(ROOT)
        cls.dataset = json.loads((ROOT / "data-design/sample_dataset.json").read_bytes())

    def test_n01_package_extension_is_present_in_copied_publication(self):
        with copied_publication() as root:
            catalogue = SchemaCatalogue(root)
            self.assertEqual("urn:medical:task-plan-draft-package:1.0.0#/$defs/RolePackage", catalogue.schema_ref("task-plan-draft-package", "RolePackage"))

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
        self.assertEqual(set(self.catalogue.schemas), {"dataset", "interfaces", "resource-details", "artifacts", "model-stream", "templates", "template-operations", "tasks", "collections", "rule-expressions", "template-applicability", "agent-release", "task-plan-view", "evaluation-results", "simulation-execution", "local-simulation-provenance", "replay-manifest", "runtime-governance", "agent-execution-policy", "runtime-retention", "run-token-authority", "projects", "budgets", "formal-evaluation-input", "evaluation-context-compiler", "evaluation-planning-context-compiler", "strict-field-typed-v2", "task-section-writer-package-v2", "artifacts-v2", "document-review-package-v2", "task-source-verifier-package", "tool-inputs-v2", "template-application", "task-source-verifier-package-v2", "task-strict-field-input-v2", "task-plan-draft-package", "document-review-package", "task-tool-read-success-v2", "source-evidence-v2", "tool-success-v2", "evaluation-stage-input-v2", "document-body-v2", "task-section-writer-package", "strict-field-comparison-v2", "document-export-command-v2", "tool-inputs", "task-tool-read-success", "task-professional-package", "task-professional-package-v2", "evaluation-stage-input", "evaluation-business-output"})
        self.assertGreater(self.catalogue.reference_count, 0)
        for name, schema in self.catalogue.schemas.items():
            self.catalogue.registry.resolver().lookup(self.catalogue.schema_ref(name))
            for definition in schema.get("$defs", {}):
                self.catalogue.registry.resolver().lookup(
                    self.catalogue.schema_ref(name, definition)
                )
        with self.assertRaises(ValueError):
            self.catalogue.validate("https://unregistered.invalid/schema", self.dataset)

    def test_pinned_extension_preserves_every_legacy_identity_and_digest(self):
        base_bytes = (ROOT / "contracts/catalogue.json").read_bytes()
        self.assertEqual(hashlib.sha256(base_bytes).hexdigest(),
                         "dcd72acbb7a45775373662e0ffe4557030d4740b0afacdee8375e7768e761915")
        base = json.loads(base_bytes)
        self.assertEqual(self.catalogue.manifest["base_uri"], base["base_uri"])
        self.assertEqual(self.catalogue.manifest["schemas"][:len(base["schemas"])], base["schemas"])
        for entry in base["schemas"]:
            with self.subTest(name=entry["name"]):
                raw = (ROOT / entry["path"]).read_bytes()
                document = yaml.safe_load(raw) if entry["path"].endswith(".yaml") else json.loads(raw)
                self.assertEqual(self.catalogue.schemas[entry["name"]], document)
                self.assertEqual(self.catalogue.schema_ref(entry["name"]),
                                 document.get("$id", urljoin(base["base_uri"], entry["path"])))
                self.assertEqual(self.catalogue.digests[entry["name"]], entry["sha256"])
        self.assertEqual(self.catalogue.schema_ref("evaluation-results"), "urn:medical:evaluation-results:1.0.0")
        self.assertEqual(sum(len(self.catalogue.schemas[entry["name"]].get("$defs", {})) for entry in self.catalogue.manifest["schemas"][:26]), 522)

    def test_pinned_extension_rejects_changed_raw_bytes_and_wrong_base(self):
        base = (ROOT / "contracts/catalogue.json").read_bytes()
        extension = (ROOT / EXTENSION_PATH).read_bytes()
        with self.assertRaisesRegex(ValueError, "扩展清单摘要"):
            _merge_published_catalogue(base, extension + b"\n")
        with self.assertRaisesRegex(ValueError, "基础清单摘要"):
            _merge_published_catalogue(base + b"\n", extension)
        with copied_publication() as root:
            (root / EXTENSION_PATH).write_bytes(extension + b"\n")
            with self.assertRaisesRegex(ValueError, "扩展清单摘要"):
                SchemaCatalogue(root)

    def test_pinned_extension_rejects_name_uri_base_and_path_overrides(self):
        base = (ROOT / "contracts/catalogue.json").read_bytes()
        original = json.loads((ROOT / EXTENSION_PATH).read_bytes())
        legacy = json.loads(base)["schemas"][0]
        mutations = []
        duplicate_name = deepcopy(original)
        duplicate_name["schemas"][0]["name"] = legacy["name"]
        mutations.append(duplicate_name)
        duplicate_uri = deepcopy(original)
        duplicate_uri["schemas"][0].update(path=legacy["path"], sha256=legacy["sha256"])
        mutations.append(duplicate_uri)
        replacement_base = {**original, "base_uri": "https://unregistered.invalid/"}
        mutations.append(replacement_base)
        for path in ("../outside.json", "/outside.json", "https://unregistered.invalid/schema.json"):
            changed = deepcopy(original)
            changed["schemas"][0]["path"] = path
            mutations.append(changed)
        for changed in mutations:
            with self.subTest(changed=changed), self.assertRaisesRegex(ValueError, "扩展清单摘要"):
                _merge_published_catalogue(base, json.dumps(changed).encode("utf-8"))

    def test_only_fixed_extension_is_loaded_and_all_files_stay_inside_root(self):
        with copied_publication() as root:
            (root / "contracts/extensions/untrusted.catalogue.json").write_text("invalid JSON", encoding="utf-8")
            self.assertEqual(SchemaCatalogue(root).schema_ref("evaluation-results"),
                             self.catalogue.schema_ref("evaluation-results"))
        extension = json.loads((ROOT / EXTENSION_PATH).read_bytes())
        for relative in ("contracts/catalogue.json", EXTENSION_PATH, extension["schemas"][0]["path"], "contracts/extensions/task-plan-draft-package.catalogue.json", "business/medical-persistence/src/main/resources/task-plan-draft-package.schema.json"):
            with self.subTest(path=relative), copied_publication() as root:
                path = root / relative
                outside = root.parent / "outside.json"
                path.rename(outside)
                path.symlink_to(outside)
                with self.assertRaisesRegex(ValueError, "路径超出登记目录"):
                    SchemaCatalogue(root)

    def test_unregistered_remote_reference_cannot_be_retrieved(self):
        with self.assertRaises(Unresolvable):
            self.catalogue.registry.resolver().lookup("https://unregistered.invalid/schema.json")

    def test_binding_preserves_the_actual_dataset_and_does_not_share_mutable_state(self):
        bound = self.catalogue.bind("dataset", self.dataset)
        self.assertEqual(bound.materialize(), self.dataset)
        changed = bound.materialize()
        changed["dataset_version"] += "-changed"
        self.assertEqual(bound.materialize(), self.dataset)

    def test_template_publication_uses_the_declared_identity_and_all_original_definitions(self):
        for name in ("templates", "template-operations", "tasks", "collections", "agent-release", "task-plan-view", "evaluation-results"):
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

class EvaluationContextCatalogueParityTests(unittest.TestCase):
    def parts(self):
        return [(ROOT / p).read_bytes() for p in ['contracts/catalogue.json', EXTENSION_PATH, 'contracts/extensions/simulation-execution.catalogue.json', 'contracts/extensions/task-runtime.catalogue.json', 'contracts/extensions/evaluation-context-compiler.catalogue.json']]

    def test_independent_context_catalogue_has_exact_full_reference_set(self):
        manifest = _merge_published_catalogue(*self.parts())
        self.assertEqual(25, len(manifest['schemas']))
        catalogue = SchemaCatalogue(ROOT)
        legacy = [catalogue.schemas[entry['name']] for entry in catalogue.manifest['schemas'][:26]]
        self.assertEqual(522, sum(len(s.get('$defs', {})) for s in legacy))
        self.assertEqual(548, sum(1 + len(s.get('$defs', {})) for s in legacy))
        self.assertEqual('urn:medical:evaluation-context-compiler:1.0.0', catalogue.uris['evaluation-context-compiler'])
        self.assertEqual('urn:medical:formal-evaluation-input:1.0.0', catalogue.uris['formal-evaluation-input'])

    def test_context_extension_raw_tampering_rejected(self):
        parts = self.parts()
        parts[-1] += b'\n'
        with self.assertRaises(ValueError):
            _merge_published_catalogue(*parts)

class EvaluationPlanningCatalogueParityTests(unittest.TestCase):
    def test_sixth_planning_extension_preserves_fifth_and_rejects_raw_tampering(self):
        parts = EvaluationContextCatalogueParityTests().parts()
        planning = (ROOT / 'contracts/extensions/evaluation-planning-context-compiler.catalogue.json').read_bytes()
        original = _merge_published_catalogue(*parts)
        full = _merge_published_catalogue(*parts, planning)
        self.assertEqual(original['schemas'], full['schemas'][:25])
        self.assertEqual(26, len(full['schemas']))
        self.assertEqual('urn:medical:evaluation-planning-context-compiler:1.0.0', SchemaCatalogue(ROOT).uris['evaluation-planning-context-compiler'])
        with self.assertRaises(ValueError):
            _merge_published_catalogue(*parts, planning + b'\n')


class FullPublicationParityTests(unittest.TestCase):
    extensions = ("task-tool-read", "task-professional-package", "task-professional-package-v2", "evaluation-stage-input")

    def test_all_added_publications_keep_pinned_bytes_and_resolve_offline(self):
        catalogue = SchemaCatalogue(ROOT)
        self.assertEqual(51, len(catalogue.schemas))
        self.assertEqual(919, sum(len(schema.get("$defs", {})) for schema in catalogue.schemas.values()))
        for name in self.extensions:
            manifest = json.loads((ROOT / ("contracts/extensions/" + name + ".catalogue.json")).read_bytes())
            for entry in manifest["schemas"]:
                self.assertEqual(entry["sha256"], catalogue.digests[entry["name"]])
                self.assertEqual(json.loads((ROOT / entry["path"]).read_bytes()), catalogue.schemas[entry["name"]])
                catalogue.registry.resolver().lookup(catalogue.schema_ref(entry["name"]))
                for definition in catalogue.schemas[entry["name"]].get("$defs", {}):
                    catalogue.registry.resolver().lookup(catalogue.schema_ref(entry["name"], definition))

    def test_each_added_extension_rejects_tampered_raw_bytes(self):
        for name in self.extensions:
            with self.subTest(name=name), copied_publication() as root:
                path = root / ("contracts/extensions/" + name + ".catalogue.json")
                path.write_bytes(path.read_bytes() + b"\n")
                with self.assertRaisesRegex(ValueError, "extension digest mismatch"):
                    SchemaCatalogue(root)

    def test_each_added_extension_rejects_external_symlink(self):
        for name in self.extensions:
            with self.subTest(name=name), copied_publication() as root:
                path = root / ("contracts/extensions/" + name + ".catalogue.json")
                outside = root.parent / "outside.json"
                path.rename(outside)
                path.symlink_to(outside)
                with self.assertRaisesRegex(ValueError, "路径超出登记目录"):
                    SchemaCatalogue(root)
