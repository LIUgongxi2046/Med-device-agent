from copy import deepcopy
import hashlib
from importlib.metadata import version
import json
from pathlib import Path
from typing import Annotated, get_args, get_origin, get_type_hints
import unittest
from urllib.parse import urljoin
from uuid import uuid4

from jsonschema import ValidationError as SchemaError
from pydantic import BaseModel, JsonValue, TypeAdapter, ValidationError
import rfc8785

import medical_contracts
from medical_contracts import SchemaCatalogue
from medical_contracts.wire import code_point_quote


ROOT = Path(__file__).resolve().parents[1]


class PythonDomainChecks(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.catalogue = SchemaCatalogue(ROOT)
        cls.dataset = json.loads((ROOT / "data-design/sample_dataset.json").read_bytes())

    def dataset_bindings(self):
        self.assertTrue(hasattr(medical_contracts, "DatasetBindings"), "登记资料缺少 Pydantic 类型绑定")
        return medical_contracts.DatasetBindings(self.catalogue)

    def assert_object(self, model, schema):
        self.assertTrue(issubclass(model, BaseModel))
        fields = {field.alias or name: field for name, field in model.model_fields.items()}
        self.assertEqual(set(fields), set(schema["properties"]))
        self.assertEqual({name for name, field in fields.items() if field.is_required()}, set(schema["required"]))
        self.assertEqual(model.model_config["extra"], "forbid" if schema.get("additionalProperties") is False or schema.get("unevaluatedProperties") is False else "allow")
        self.assertTrue(model.model_config["strict"])
        self.assertTrue(model.model_config["frozen"])
        for name, definition in schema["properties"].items():
            if isinstance(definition, dict) and ("enum" in definition or "const" in definition):
                identifier = next(identifier for identifier, field in model.model_fields.items() if (field.alias or identifier) == name)
                annotation = get_type_hints(model, include_extras=True)[identifier]
                if get_origin(annotation) is Annotated:
                    annotation = get_args(annotation)[0]
                expected = definition["enum"] if "enum" in definition else [definition["const"]]
                self.assertEqual(set(get_args(annotation)), set(expected), name)

    def assert_field_type(self, annotation, schema, refs, base_uri):
        if schema is True:
            self.assertIs(annotation, JsonValue)
            return
        if "$ref" in schema:
            expected = refs[urljoin(base_uri, schema["$ref"])]
            if annotation != expected:
                self.assertEqual(self.base_type(annotation), self.base_type(expected))
            return
        annotation = self.base_type(annotation)
        if "enum" in schema or "const" in schema:
            self.assertEqual(set(get_args(annotation)), set(schema["enum"] if "enum" in schema else [schema["const"]]))
        elif "oneOf" in schema or "anyOf" in schema:
            members = get_args(annotation)
            branches = schema["oneOf"] if "oneOf" in schema else schema["anyOf"]
            self.assertEqual(len(members), len(branches))
            for member, branch in zip(members, branches, strict=True):
                self.assert_field_type(member, branch, refs, base_uri)
        elif schema["type"] == "array":
            self.assertIs(get_origin(annotation), list)
            self.assert_field_type(get_args(annotation)[0], schema["items"], refs, base_uri)
        elif schema["type"] == "object":
            self.assert_object(annotation, schema)
            for name, field in schema["properties"].items():
                self.assert_field_type(get_type_hints(annotation, include_extras=True)[name], field, refs, base_uri)
        elif isinstance(schema["type"], list):
            expected = {"string": str, "null": type(None), "integer": int, "number": float, "boolean": bool, "object": dict}
            actual = {self.base_type(item) for item in get_args(annotation)}
            self.assertEqual(actual, {expected[item] for item in schema["type"]})
        else:
            expected = {"string": str, "null": type(None), "integer": int, "number": float, "boolean": bool}
            self.assertIs(annotation, expected[schema["type"]])

    @staticmethod
    def base_type(annotation):
        return get_args(annotation)[0] if get_origin(annotation) is Annotated else annotation

    def assert_all_fields(self, bindings, extra_refs=None):
        refs = {self.catalogue.schema_ref(bindings.name, name): domain_type for name, domain_type in bindings.definitions.items()}
        if extra_refs is not None:
            refs.update(extra_refs)
        for name, schema in self.catalogue.schemas[bindings.name]["$defs"].items():
            if schema.get("type") == "object":
                hints = get_type_hints(bindings.definitions[name], include_extras=True)
                for field, definition in schema["properties"].items():
                    with self.subTest(definition=name, field=field):
                        if definition.get("type") == ["object", "null"]:
                            cell = next(member for member in get_args(hints[field]) if member is not type(None))
                            self.assert_object(cell, definition)
                        else:
                            self.assert_field_type(hints[field], definition, refs, self.catalogue.uris[bindings.name])

    def test_fixed_pydantic_dependency_is_installed(self):
        expected = {"pydantic": "2.13.5", "pydantic-core": "2.46.5", "annotated-types": "0.8.0", "typing-inspection": "0.4.4"}
        for name, release in expected.items():
            self.assertEqual(version(name), release)

    def test_actual_dataset_has_complete_typed_binding(self):
        bindings = self.dataset_bindings()
        original = self.catalogue.schemas["dataset"]
        self.assertEqual(set(bindings.definitions), set(original["$defs"]))
        self.assertEqual(len(bindings.definitions), 13)
        self.assert_all_fields(bindings)
        self.assert_object(bindings.root_type, original)
        for name, definition in original["$defs"].items():
            if definition.get("type") == "object":
                self.assert_object(bindings.definitions[name], definition)
        bound = bindings.bind(self.dataset)
        read = bound.read()
        self.assertIsInstance(read, bindings.root_type)
        self.assertEqual(read.model_dump(mode="json"), self.dataset)
        self.assertEqual(bound.schema_ref, self.catalogue.schema_ref("dataset"))
        self.assertEqual(bound.schema_sha256, self.catalogue.digests["dataset"])
        self.assertEqual(bound.canonical_bytes, rfc8785.dumps(self.dataset))
        self.assertEqual(len(read.sources), 11)
        self.assertEqual(len(read.records), 12)
        self.assertEqual({source.production_status for source in read.sources}, {"PENDING_APPROVAL"})
        self.assertEqual({use for source in read.sources for use in source.allowed_use}, {"LOCAL_DESIGN_REVIEW", "LOCAL_PARSER_CHECK"})
        self.assertTrue(all(record.formal_evidence_eligible is False for record in read.records))

    def test_actual_members_preserve_original_identity_and_locators(self):
        bindings = self.dataset_bindings()
        for source in self.dataset["sources"]:
            read = bindings.bind(source, "source").read()
            self.assertEqual(read.source_id, source["source_id"])
            self.assertEqual(read.model_dump(mode="json"), source)
        sources = {entry["source_id"]: entry for entry in self.dataset["sources"]}
        checked = 0
        for record in self.dataset["records"]:
            read = bindings.bind(record, "record").read()
            self.assertEqual(read.model_dump(mode="json"), record)
            self.assertEqual(bindings.bind(record["locator"], "locator").read().model_dump(mode="json"), record["locator"])
            self.assertEqual(bindings.bind(record["source_id"], "id").read(), record["source_id"])
            self.assertEqual(bindings.bind(record["quote_hash"], "hash").read(), record["quote_hash"])
            if read.locator.kind in {"TXT", "MARKDOWN"}:
                original = (ROOT / sources[read.source_id]["source_ref"]).read_bytes().decode("utf-8")
                if read.locator.kind == "TXT":
                    self.assertEqual(code_point_quote(original, read.locator.char_start, read.locator.char_end, read.quote_hash), read.text)
                    checked += 1
        self.assertEqual(checked, 5)

    def test_binding_reads_have_independent_mutable_members(self):
        bindings = self.dataset_bindings()
        bound = bindings.bind(self.dataset)
        read = bound.read()
        read.sources.clear()
        read.records[0].checks.clear()
        self.assertEqual(bound.read().model_dump(mode="json"), self.dataset)
        self.assertEqual(hashlib.sha256(bound.canonical_bytes).hexdigest(), "67560cf1a91e7464bab061fb8b13bf2432439f6984a623a9968e2c9bcc90e359")
        with self.assertRaises(ValidationError):
            read.dataset_version = self.dataset["dataset_version"] + "-changed"

    def test_actual_dataset_rejects_unknown_missing_and_wrong_primitive_fields(self):
        bindings = self.dataset_bindings()
        changed = deepcopy(self.dataset)
        changed["production_approved"] = True
        with self.assertRaises(SchemaError):
            bindings.bind(changed)
        changed = deepcopy(self.dataset)
        del changed["sources"][0]["official_url"]
        with self.assertRaises(SchemaError):
            bindings.bind(changed)
        source = self.dataset["sources"][0]
        changed = deepcopy(source)
        changed["allowed_use"].append(changed["allowed_use"][0])
        with self.assertRaises(ValidationError):
            bindings.definitions["source"].model_validate(changed, strict=True)
        for invalid in [str(source["size_bytes"]), True, None]:
            changed = deepcopy(source)
            changed["size_bytes"] = invalid
            with self.subTest(invalid=invalid), self.assertRaises(ValidationError):
                bindings.definitions["source"].model_validate(changed, strict=True)
        with self.assertRaises(ValueError):
            bindings.bind(self.dataset, "UNREGISTERED")

    def test_seven_locator_variants_and_inline_types_match_original_schema(self):
        bindings = self.dataset_bindings()
        schemas = self.catalogue.schemas["dataset"]["$defs"]
        locator = bindings.definitions["locator"]
        self.assertIs(get_origin(locator), Annotated)
        variants = get_args(get_args(locator)[0])
        self.assertEqual({get_args(get_type_hints(model)["kind"])[0] for model in variants}, {"PDF", "DOCX", "XLSX", "CSV", "TXT", "MARKDOWN", "PPTX"})
        pairs = [("pdf", "region", "oneOf"), ("docx", "address", "oneOf")]
        for name, field, branches in pairs:
            annotation = get_type_hints(bindings.definitions[name], include_extras=True)[field]
            members = get_args(get_args(annotation)[0])
            definitions = schemas[name]["properties"][field][branches]
            self.assertEqual(len(members), len(definitions))
            for model, definition in zip(members, definitions, strict=True):
                self.assert_object(model, definition)
        cell = next(item for item in get_args(get_type_hints(bindings.definitions["pptx"])["table_cell"]) if item is not type(None))
        self.assert_object(cell, schemas["pptx"]["properties"]["table_cell"])
        for record in self.dataset["records"]:
            locator = record["locator"]
            names = {"PDF": "pdf", "DOCX": "docx", "XLSX": "xlsx", "CSV": "csv", "TXT": "text", "MARKDOWN": "markdown", "PPTX": "pptx"}
            name = names[locator["kind"]]
            model = bindings.definitions[name]
            for nullable, definition in schemas[name]["properties"].items():
                if "null" in definition.get("type", []):
                    changed = deepcopy(locator)
                    del changed[nullable]
                    with self.subTest(kind=locator["kind"], field=nullable), self.assertRaises(ValidationError):
                        model.model_validate(changed, strict=True)

    def model_bindings(self):
        self.assertTrue(hasattr(medical_contracts, "ModelStreamBindings"), "模型协议缺少 Pydantic 类型绑定")
        return medical_contracts.ModelStreamBindings(self.catalogue)

    def test_model_protocol_has_all_original_definitions_and_object_fields(self):
        bindings = self.model_bindings()
        original = self.catalogue.schemas["model-stream"]["$defs"]
        self.assertEqual(set(bindings.definitions), set(original))
        self.assertEqual(len(bindings.definitions), 25)
        self.assert_all_fields(bindings)
        checked = 0
        for name, schema in original.items():
            if schema.get("type") == "object":
                self.assert_object(bindings.definitions[name], schema)
                checked += 1
        self.assertEqual(checked, 19)
        for name in ("StreamRequest", "StreamEvent", "ModelResult", "InvocationStatus", "CancelRequest"):
            with self.subTest(name=name), self.assertRaises(SchemaError):
                bindings.bind(self.dataset, name)

    def test_model_scalar_binding_preserves_actual_values_and_technical_boundaries(self):
        bindings = self.model_bindings()
        execution_id = str(uuid4())
        self.assertEqual(bindings.bind(execution_id, "Id").read(), execution_id)
        for source in self.dataset["sources"]:
            self.assertEqual(bindings.bind(source["owner"], "Text").read(), source["owner"])
            self.assertEqual(bindings.bind(source["sha256"], "Hash").read(), source["sha256"])
            self.assertEqual(bindings.bind(source["size_bytes"], "TokenCount").read(), source["size_bytes"])
        self.assertIsNone(bindings.bind(None, "TokenCount").read())
        self.assertIsNone(bindings.bind(None, "NullableText").read())
        for invalid in (execution_id.upper(), self.dataset["sources"][0]["source_id"]):
            with self.assertRaises((SchemaError, ValidationError)):
                bindings.bind(invalid, "Id")
        for invalid in (True, -1, str(self.dataset["sources"][0]["size_bytes"])):
            with self.assertRaises((SchemaError, ValidationError)):
                bindings.bind(invalid, "TokenCount")

    def test_model_block_event_and_root_unions_match_original_branches(self):
        bindings = self.model_bindings()
        block = bindings.definitions["Block"]
        variants = get_args(get_args(block)[0])
        self.assertEqual({model for model in variants}, {bindings.definitions["TextBlock"], bindings.definitions["ToolBlock"]})
        payload = get_type_hints(bindings.definitions["StreamEvent"], include_extras=True)["payload"]
        variants = get_args(get_args(payload)[0])
        names = ["Accepted", "BlockStart", "TextDelta", "ToolDelta", "BlockEnd", "UsageEvent", "FinishEvent", "ErrorEvent"]
        self.assertEqual(set(variants), {bindings.definitions[name] for name in names})
        self.assertEqual(set(get_args(bindings.root_type)), {bindings.definitions[name] for name in ("StreamRequest", "StreamEvent", "ModelResult", "InvocationStatus", "CancelRequest")})
        original = self.catalogue.schemas["model-stream"]["$defs"]
        nullable = [("InvocationStatus", "result_artifact_id"), ("StreamEvent", "emitted_at"), ("ModelResult", "replay_state_id")]
        for name, field in nullable:
            annotation = get_type_hints(bindings.definitions[name], include_extras=True)[field]
            adapter = TypeAdapter(annotation)
            if field == "emitted_at":
                actual = self.dataset["created_at"]
                self.assertEqual(adapter.validate_python(actual, strict=True), actual)
                with self.assertRaises(ValidationError):
                    adapter.validate_python(actual.replace("+00:00", "+08:00"), strict=True)
            else:
                self.assertIsNone(adapter.validate_python(None, strict=True))
                with self.assertRaises(ValidationError):
                    adapter.validate_python(self.dataset["sources"][0]["source_id"], strict=True)
            self.assertIn(field, original[name]["required"])

    def artifact_bindings(self):
        self.assertTrue(hasattr(medical_contracts, "ArtifactBindings"), "专业产物缺少 Pydantic 类型绑定")
        return medical_contracts.ArtifactBindings(self.catalogue)

    def test_artifact_types_preserve_all_definitions_fields_and_evidence_identity(self):
        bindings = self.artifact_bindings()
        original = self.catalogue.schemas["artifacts"]
        self.assertEqual(set(bindings.definitions), set(original["$defs"]))
        self.assertEqual(len(bindings.definitions), 49)
        self.assert_object(bindings.root_type, original)
        dataset = self.dataset_bindings()
        self.assertIs(bindings.definitions["EvidenceRef"], dataset.definitions["EvidenceRef"])
        refs = {self.catalogue.schema_ref("dataset", name): value for name, value in dataset.definitions.items()}
        self.assert_all_fields(bindings, refs)
        refs.update({self.catalogue.schema_ref("artifacts", name): value for name, value in bindings.definitions.items()})
        for field, definition in original["properties"].items():
            self.assert_field_type(get_type_hints(bindings.root_type, include_extras=True)[field], definition, refs, self.catalogue.uris["artifacts"])
        objects = 1
        for name, definition in original["$defs"].items():
            if definition.get("type") == "object":
                self.assert_object(bindings.definitions[name], definition)
                objects += 1
                with self.subTest(name=name), self.assertRaises(SchemaError):
                    bindings.bind(self.dataset, name)
        self.assertEqual(objects, 37)
        with self.assertRaises(SchemaError):
            bindings.bind(self.dataset)

    def test_artifact_outputs_anchors_and_wire_annotations_match_original_schema(self):
        bindings = self.artifact_bindings()
        for name in ("ArtifactOutput", "Anchor"):
            annotation = bindings.definitions[name]
            members = get_args(get_args(annotation)[0])
            original = self.catalogue.schemas["artifacts"]["$defs"][name]["oneOf"]
            refs = {self.catalogue.schema_ref("artifacts", name): value for name, value in bindings.definitions.items()}
            self.assertEqual(set(members), {refs[urljoin(self.catalogue.uris["artifacts"], item["$ref"])] for item in original})
        evidence_hints = get_type_hints(bindings.definitions["EvidenceRef"], include_extras=True)
        attachment_hints = get_type_hints(bindings.definitions["AttachmentBinding"], include_extras=True)
        actual = self.dataset["records"][0]
        self.assertEqual(TypeAdapter(evidence_hints["source_version"]).validate_python(actual["source_version"], strict=True), actual["source_version"])
        with self.assertRaises(ValidationError):
            TypeAdapter(attachment_hints["source_version"]).validate_python(actual["source_version"], strict=True)
        emitted = get_type_hints(bindings.definitions["HandoffRecord"], include_extras=True)["created_at"]
        self.assertEqual(TypeAdapter(emitted).validate_python(self.dataset["created_at"], strict=True), self.dataset["created_at"])
        with self.assertRaises(ValidationError):
            TypeAdapter(emitted).validate_python(self.dataset["created_at"].replace("+00:00", "+08:00"), strict=True)

    def test_artifact_scalars_preserve_actual_values_and_decimal_precision(self):
        bindings = self.artifact_bindings()
        execution_id = str(uuid4())
        self.assertEqual(bindings.bind(execution_id, "Id").read(), execution_id)
        self.assertEqual(bindings.bind([execution_id], "Ids").read(), [execution_id])
        self.assertIsNone(bindings.bind(None, "NullableId").read())
        for source in self.dataset["sources"]:
            self.assertEqual(bindings.bind(source["sha256"], "Hash").read(), source["sha256"])
            self.assertEqual(bindings.bind(str(source["size_bytes"]), "Decimal").read(), str(source["size_bytes"]))
            self.assertEqual(bindings.bind(source["owner"], "Text").read(), source["owner"])
            self.assertEqual(bindings.bind(source["allowed_use"][0], "Key").read(), source["allowed_use"][0])
        self.assertEqual(bindings.bind("0.000000000001", "Decimal").read(), "0.000000000001")
        for invalid in ("0.0000000000001", "1" + "0" * 26, "1e-12", 1):
            with self.assertRaises((SchemaError, ValidationError)):
                bindings.bind(invalid, "Decimal")
        money = get_type_hints(bindings.definitions["RemainingLimits"], include_extras=True)["cost_amount"]
        adapter = TypeAdapter(money)
        self.assertEqual(adapter.validate_python("0.00000001", strict=True), "0.00000001")
        for invalid in ("0.000000001", "10000000000000000", "-1", 1):
            with self.assertRaises(ValidationError):
                adapter.validate_python(invalid, strict=True)

    def interface_bindings(self):
        self.assertTrue(hasattr(medical_contracts, "InterfaceBindings"), "完整接口缺少 Pydantic 类型绑定")
        return medical_contracts.InterfaceBindings(self.catalogue)

    def inherited_interface_schema(self, name):
        schema = deepcopy(self.catalogue.schemas["interfaces"]["$defs"][name])
        for parent in schema.get("allOf", []):
            if "$ref" in parent:
                inherited = self.inherited_interface_schema(parent["$ref"].removeprefix("#/$defs/"))
                schema["properties"] = inherited["properties"] | schema["properties"]
                schema["required"] = inherited["required"] + schema["required"]
        return schema

    def test_interfaces_preserve_all_definitions_inherited_fields_and_refs(self):
        bindings = self.interface_bindings()
        original = self.catalogue.schemas["interfaces"]["$defs"]
        self.assertEqual(set(bindings.definitions), set(original))
        self.assertEqual(len(bindings.definitions), 104)
        refs = {self.catalogue.schema_ref("interfaces", name): value for name, value in bindings.definitions.items()}
        refs.update({self.catalogue.schema_ref("dataset", name): value for name, value in self.dataset_bindings().definitions.items()})
        for name, value in self.resource_bindings().common_definitions.items():
            self.assertIs(bindings.definitions[name], value)
        objects = 0
        for name, definition in original.items():
            if definition.get("type") == "object":
                schema = self.inherited_interface_schema(name)
                model = bindings.definitions[name]
                self.assert_object(model, schema)
                hints = get_type_hints(model, include_extras=True)
                for identifier, field in model.model_fields.items():
                    wire_name = field.alias or identifier
                    if name != "PublicEvent" or wire_name != "payload":
                        with self.subTest(definition=name, field=wire_name):
                            self.assert_field_type(hints[identifier], schema["properties"][wire_name], refs, self.catalogue.uris["interfaces"])
                objects += 1
                with self.subTest(definition=name), self.assertRaises(SchemaError):
                    bindings.bind(self.dataset, name)
        self.assertEqual(objects, 95)

    def test_interface_envelope_reads_actual_registered_data_independently(self):
        bindings = self.interface_bindings()
        execution_id = str(uuid4())
        envelope = {"schema_version": "1.0.0", "request_id": execution_id, "resource_revision": None, "data": self.dataset}
        bound = bindings.bind(envelope, "Envelope")
        read = bound.read()
        self.assertEqual(read.data, self.dataset)
        self.assertEqual(read.request_id, execution_id)
        self.assertIsNone(read.resource_revision)
        self.assertEqual(bound.canonical_bytes, rfc8785.dumps(envelope))
        read.data["sources"].clear()
        self.assertEqual(bound.read().data, self.dataset)
        for source in self.dataset["sources"]:
            envelope["data"] = source["size_bytes"]
            self.assertEqual(bindings.bind(envelope, "Envelope").read().data, source["size_bytes"])
        envelope["data"] = None
        self.assertIsNone(bindings.bind(envelope, "Envelope").read().data)
        envelope["resource_revision"] = str(1 << 64)
        with self.assertRaises(ValidationError):
            bindings.bind(envelope, "Envelope")
        self.assertIs(get_type_hints(bindings.definitions["Envelope"])["data"], JsonValue)

    def test_interface_event_types_and_secret_metadata_preserve_original_constraints(self):
        bindings = self.interface_bindings()
        original = self.catalogue.schemas["interfaces"]["$defs"]
        event = bindings.definitions["PublicEvent"]
        branches = original["PublicEvent"]["oneOf"]
        expected = {}
        payload_types = set()
        for branch in branches:
            fields = branch["properties"]
            payload_name = fields["payload"]["$ref"].removeprefix("#/$defs/")
            payload = fields["payload"].get("properties", {}).get("read_operation_id")
            operation = payload["const"] if payload is not None else original[payload_name]["properties"]["read_operation_id"]["const"]
            expected[fields["event_type"]["const"]] = (fields["resource_kind"]["const"], bindings.definitions[payload_name], operation)
            payload_types.add(bindings.definitions[payload_name])
        self.assertEqual(dict(event.event_mapping), expected)
        self.assertEqual(set(get_args(get_type_hints(event)["payload"])), payload_types)
        self.assertEqual(len(expected), 9)
        node = get_type_hints(bindings.definitions["Node"], include_extras=True)["content"]
        branches = original["Node"]["properties"]["content"]["oneOf"]
        self.assertEqual(set(get_args(self.base_type(node))), {bindings.definitions[branch["$ref"].removeprefix("#/$defs/")] for branch in branches})
        secret = bindings.definitions["CredentialWrite"].model_fields["secret_value"]
        self.assertFalse(secret.repr)
        self.assertIs(secret.json_schema_extra["writeOnly"], True)

    def test_interface_scalar_annotations_preserve_actual_text_time_and_precision(self):
        bindings = self.interface_bindings()
        source = self.dataset["sources"][0]
        original = self.catalogue.schemas["interfaces"]["$defs"]
        for name, field in (("OperationLookup", "operation_id"), ("ManagementOperation", "operation_id"), ("ContextRequest", "tokenizer_revision")):
            adapter = TypeAdapter(get_type_hints(bindings.definitions[name], include_extras=True)[field])
            self.assertEqual(adapter.validate_python(source["source_id"], strict=True), source["source_id"])
            self.assertEqual(original[name]["properties"][field]["type"], "string")
        model = bindings.definitions["FieldValue"]
        adapter = TypeAdapter(get_type_hints(model, include_extras=True)["decimal_value"])
        self.assertEqual(adapter.validate_python("0.000000000001", strict=True), "0.000000000001")
        for invalid in ("0.0000000000001", "1" + "0" * 26, "1e-12", 1):
            with self.assertRaises(ValidationError):
                adapter.validate_python(invalid, strict=True)
        report = bindings.definitions["ReportFilters"]
        self.assertEqual(report.model_fields["from_"].alias, "from")
        timestamp = TypeAdapter(get_type_hints(report, include_extras=True)["from_"])
        self.assertEqual(timestamp.validate_python(self.dataset["created_at"], strict=True), self.dataset["created_at"])
        with self.assertRaises(ValidationError):
            timestamp.validate_python(self.dataset["created_at"].replace("+00:00", "+08:00"), strict=True)
        for name in ("StepIdentity", "ActionIdentity", "ReviewBaseline"):
            model = bindings.definitions[name]
            self.assertEqual(model.model_config["extra"], "allow")
            self.assertEqual(get_type_hints(model)["__pydantic_extra__"], dict[str, JsonValue])
        for name in ("InputReadRequest", "PlanCandidateRequest", "EvidenceDecisionBatch", "ReviewBatch"):
            self.assertEqual(bindings.definitions[name].model_config["extra"], "forbid")

    def resource_bindings(self):
        self.assertTrue(hasattr(medical_contracts, "ResourceBindings"), "资源详情缺少 Pydantic 类型绑定")
        return medical_contracts.ResourceBindings(self.catalogue)

    def test_resource_definitions_and_common_types_preserve_original_fields_and_refs(self):
        bindings = self.resource_bindings()
        self.assertEqual(set(bindings.definitions), set(self.catalogue.schemas["resource-details"]["$defs"]))
        self.assertEqual(len(bindings.definitions), 27)
        self.assertEqual(len(bindings.common_definitions), 17)
        refs = {self.catalogue.schema_ref("interfaces", name): value for name, value in bindings.common_definitions.items()}
        self.assert_all_fields(bindings, refs)
        objects = 0
        for name, definition in self.catalogue.schemas["resource-details"]["$defs"].items():
            if definition.get("type") == "object":
                self.assert_object(bindings.definitions[name], definition)
                objects += 1
        self.assertEqual(objects, 19)
        for name, domain_type in bindings.common_definitions.items():
            original = self.catalogue.schemas["interfaces"]["$defs"][name]
            if original.get("type") == "object":
                self.assert_object(domain_type, original)
                for field, definition in original["properties"].items():
                    self.assert_field_type(get_type_hints(domain_type, include_extras=True)[field], definition, refs, self.catalogue.uris["interfaces"])

    def test_resource_primitive_binding_reads_actual_hash_text_and_unsigned_revision(self):
        bindings = self.resource_bindings()
        execution_id = str(uuid4())
        self.assertEqual(bindings.bind(execution_id, "Id").read(), execution_id)
        self.assertEqual(bindings.bind([execution_id], "Ids").read(), [execution_id])
        self.assertIsNone(bindings.bind(None, "NullableId").read())
        maximum = str((1 << 64) - 1)
        self.assertEqual(bindings.bind(maximum, "Revision").read(), maximum)
        actual = self.dataset["records"][0]["checks"]
        bound = bindings.bind(actual, "Texts")
        bound.read().clear()
        self.assertEqual(bound.read(), actual)
        for invalid in (maximum + "0", "01", 1, True):
            with self.assertRaises((SchemaError, ValidationError)):
                bindings.bind(invalid, "Revision")
        with self.assertRaises(SchemaError):
            bindings.bind([execution_id, execution_id], "Ids")
        for name, definition in self.catalogue.schemas["resource-details"]["$defs"].items():
            if definition.get("type") == "object":
                with self.subTest(name=name), self.assertRaises(SchemaError):
                    bindings.bind(self.dataset, name)


if __name__ == "__main__":
    unittest.main()
