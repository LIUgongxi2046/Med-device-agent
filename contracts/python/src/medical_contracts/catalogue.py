from copy import deepcopy
from dataclasses import dataclass
import hashlib
import json
from pathlib import Path
from urllib.parse import urljoin, urlsplit

from jsonschema import Draft202012Validator, FormatChecker
from referencing import Registry, Resource
import rfc8785
import yaml

from .errors import SchemaValidationError, UnsupportedSchemaError


_TASK_TOOL_READ_EXTENSION_PATH = "contracts/extensions/task-tool-read.catalogue.json"
_TASK_TOOL_READ_EXTENSION_SHA256 = "d5401ee6c04ded4543b96bb7ec3c1fe57ef39ee3fa2cc9a6ddca191671c93cb1"

_TASK_PACKAGE_EXTENSION_PATH = "contracts/extensions/task-professional-package.catalogue.json"
_TASK_PACKAGE_EXTENSION_SHA256 = "f1b0d3877e11eb4d87eb69dd809f8c1c2a285bd56a05b2cb24d8ae9ebab370fc"

_TASK_PACKAGE_V2_EXTENSION_PATH = "contracts/extensions/task-professional-package-v2.catalogue.json"
_TASK_PACKAGE_V2_EXTENSION_SHA256 = "71e97a233df6b0bc91e8ead87db7ca5cd5a19fd970964cbf00b22810f6f5e89b"

_EVALUATION_STAGE_EXTENSION_PATH = "contracts/extensions/evaluation-stage-input.catalogue.json"
_EVALUATION_STAGE_EXTENSION_SHA256 = "37218c998d05b9f8001b36edfa385dfae41598832cb297ec4e1865a3d6580d8a"

_TEMPLATE_APPLICATION_EXTENSION_PATH = "contracts/extensions/template-application.catalogue.json"
_TEMPLATE_APPLICATION_EXTENSION_SHA256 = "dca6a68792e6f93fbcb476faacb55df75acf1a51e67eb55e52478a4c27134a1c"

_DOCUMENT_OPERATIONS_V2_EXTENSION_PATH = "contracts/extensions/document-operations-v2.catalogue.json"
_DOCUMENT_OPERATIONS_V2_EXTENSION_SHA256 = "137165db0c075aeab30799a63acef93c58a106d3e5041c4661787b9f1153f1d3"
_EVALUATION_BUSINESS_V2_EXTENSION_PATH = "contracts/extensions/evaluation-business-v2.catalogue.json"
_EVALUATION_BUSINESS_V2_EXTENSION_SHA256 = "8dab2002a36b6b1783be70fb2a56c81496e4ea83aee979977c48ef4329440c63"
_STRICT_FIELD_EVIDENCE_V2_EXTENSION_PATH = "contracts/extensions/strict-field-evidence-v2.catalogue.json"
_STRICT_FIELD_EVIDENCE_V2_EXTENSION_SHA256 = "1833b4c7d4984c51166d1e34249f35fa92c50310c9c3f6565040b2e34d3589d9"
_DOCUMENT_EVIDENCE_V2_EXTENSION_PATH = "contracts/extensions/document-evidence-v2.catalogue.json"
_DOCUMENT_EVIDENCE_V2_EXTENSION_SHA256 = "5f79cae29d60d5e28dd36c2b795c8ae7bb6cdf2ae1665e7342f6bab3bf0d5af1"

_SOURCE_EVIDENCE_V2_EXTENSION_PATH = "contracts/extensions/source-evidence-v2.catalogue.json"
_SOURCE_EVIDENCE_V2_EXTENSION_SHA256 = "745d5f62b3ff70d9ca11610e11b263226339e71f6c14f9325e160f08468c13aa"

_REVIEW_PACKAGES_EXTENSION_PATH = "contracts/extensions/professional-review-packages.catalogue.json"
_REVIEW_PACKAGES_EXTENSION_SHA256 = "abf2fd93515e4022975f16a6dc2938d17d6d855ee13e5f961f5a7a4dcd335420"

_PLAN_DRAFT_PACKAGE_EXTENSION_PATH = "contracts/extensions/task-plan-draft-package.catalogue.json"
_PLAN_DRAFT_PACKAGE_EXTENSION_SHA256 = "58c11567e757b6da79337a05b44d42ab2d1bacd38ea81960b661922d6be60632"

_PLANNING_EXTENSION_PATH = "contracts/extensions/evaluation-planning-context-compiler.catalogue.json"
_PLANNING_EXTENSION_SHA256 = "c091484892de819a9aa63ef7d75a3e33dc73a7a0b17af4bb16d8250da267e6c7"

_CONTEXT_EXTENSION_PATH = "contracts/extensions/evaluation-context-compiler.catalogue.json"
_CONTEXT_EXTENSION_SHA256 = "2dcccbadff66b247c8025c781fb1fafc2a167d28c5d45ac0b808fb66611b5000"

_TASK_RUNTIME_EXTENSION_PATH = "contracts/extensions/task-runtime.catalogue.json"
_TASK_RUNTIME_EXTENSION_SHA256 = "044860cfe13730927433a208daf56e2adbb89c1d9e8ef3d61937424d812fcb6c"

_SIMULATION_EXTENSION_PATH = "contracts/extensions/simulation-execution.catalogue.json"
_SIMULATION_EXTENSION_SHA256 = "c3837a5c8de536a6504bbb296cd63389fac07ced1eda93ba721d94fcdad8446a"
_EXTENSION_PATH = "contracts/extensions/evaluation-results.catalogue.json"
_EXTENSION_SHA256 = "a1b3dce03c553d0e9166d76c30e5af287c3bb06dfe90c9cc696632a1663c891d"


def _check_schema_path(path):
    if (not isinstance(path, str) or any(char in path for char in "\\:%?#\0")
            or any(part in {"", ".", ".."} for part in path.split("/"))):
        raise ValueError("Schema 路径必须使用固定相对路径")


def _registered_path(root: Path, relative: str) -> Path:
    _check_schema_path(relative)
    path = (root / relative).resolve(strict=True)
    if not path.is_relative_to(root):
        raise ValueError("Schema 路径超出登记目录")
    return path


def _merge_published_catalogue(base_bytes: bytes, extension_bytes: bytes, simulation_bytes: bytes | None = None, task_runtime_bytes: bytes | None = None, context_bytes: bytes | None = None, planning_bytes: bytes | None = None, plan_draft_package_bytes: bytes | None = None, review_packages_bytes: bytes | None = None, source_evidence_v2_bytes: bytes | None = None, document_evidence_v2_bytes: bytes | None = None, template_application_bytes: bytes | None = None, strict_field_evidence_v2_bytes: bytes | None = None, document_operations_v2_bytes: bytes | None = None, evaluation_business_v2_bytes: bytes | None = None, task_tool_read_bytes: bytes | None = None, task_package_bytes: bytes | None = None, task_package_v2_bytes: bytes | None = None, evaluation_stage_bytes: bytes | None = None):
    # 扩展清单本身固定在代码信任边界内，调用方不能替换可信摘要。
    if hashlib.sha256(extension_bytes).hexdigest() != _EXTENSION_SHA256:
        raise ValueError("Schema 扩展清单摘要不一致")
    extension = json.loads(extension_bytes)
    if set(extension) != {"schema_version", "base_catalogue_sha256", "schemas"}:
        raise ValueError("Schema 扩展清单字段不受支持")
    if hashlib.sha256(base_bytes).hexdigest() != extension["base_catalogue_sha256"]:
        raise ValueError("Schema 基础清单摘要不一致")
    base = json.loads(base_bytes)
    if base["schema_version"] != "1.0.0" or extension["schema_version"] != "1.0.0":
        raise UnsupportedSchemaError()
    names, uris = set(), set()
    schemas = [*base["schemas"], *extension["schemas"]]
    if simulation_bytes is not None:
        if hashlib.sha256(simulation_bytes).hexdigest() != _SIMULATION_EXTENSION_SHA256:
            raise ValueError("Schema 模拟扩展清单摘要不一致")
        simulation = json.loads(simulation_bytes)
        if (set(simulation) != {"schema_version", "base_catalogue_sha256", "schemas"}
                or simulation["schema_version"] != "1.0.0"
                or simulation["base_catalogue_sha256"] != hashlib.sha256(base_bytes).hexdigest()):
            raise ValueError("Schema 模拟扩展基础身份不一致")
        schemas.extend(simulation["schemas"])
    if task_runtime_bytes is not None:
        if hashlib.sha256(task_runtime_bytes).hexdigest() != _TASK_RUNTIME_EXTENSION_SHA256:
            raise ValueError("Schema 任务运行扩展清单摘要不一致")
        task_runtime = json.loads(task_runtime_bytes)
        if (set(task_runtime) != {"schema_version", "base_catalogue_sha256", "schemas"}
                or task_runtime["schema_version"] != "1.0.0"
                or task_runtime["base_catalogue_sha256"] != hashlib.sha256(base_bytes).hexdigest()):
            raise ValueError("Schema 任务运行扩展基础身份不一致")
        schemas.extend(task_runtime["schemas"])
    if context_bytes is not None:
        if hashlib.sha256(context_bytes).hexdigest() != _CONTEXT_EXTENSION_SHA256:
            raise ValueError("Schema context extension digest mismatch")
        context = json.loads(context_bytes)
        if (set(context) != {"schema_version", "base_catalogue_sha256", "schemas"}
                or context["schema_version"] != "1.0.0"
                or context["base_catalogue_sha256"] != hashlib.sha256(base_bytes).hexdigest()):
            raise ValueError("Schema context extension base mismatch")
        schemas.extend(context["schemas"])
    if planning_bytes is not None:
        if hashlib.sha256(planning_bytes).hexdigest() != _PLANNING_EXTENSION_SHA256:
            raise ValueError("Schema planning extension digest mismatch")
        planning = json.loads(planning_bytes)
        if (set(planning) != {"schema_version", "base_catalogue_sha256", "schemas"}
                or planning["schema_version"] != "1.0.0"
                or planning["base_catalogue_sha256"] != hashlib.sha256(base_bytes).hexdigest()):
            raise ValueError("Schema planning extension base mismatch")
        schemas.extend(planning["schemas"])
    if plan_draft_package_bytes is not None:
        if hashlib.sha256(plan_draft_package_bytes).hexdigest() != _PLAN_DRAFT_PACKAGE_EXTENSION_SHA256:
            raise ValueError("Plan draft package extension digest mismatch")
        extra = json.loads(plan_draft_package_bytes)
        if (set(extra) != {"schema_version", "base_catalogue_sha256", "schemas"}
                or extra["schema_version"] != "1.0.0"
                or extra["base_catalogue_sha256"] != hashlib.sha256(base_bytes).hexdigest()):
            raise ValueError("Plan draft package extension base mismatch")
        schemas.extend(extra["schemas"])
    if review_packages_bytes is not None:
        if hashlib.sha256(review_packages_bytes).hexdigest() != _REVIEW_PACKAGES_EXTENSION_SHA256:
            raise ValueError("Review packages extension digest mismatch")
        extra = json.loads(review_packages_bytes)
        if (set(extra) != {"schema_version", "base_catalogue_sha256", "schemas"}
                or extra["schema_version"] != "1.0.0"
                or extra["base_catalogue_sha256"] != hashlib.sha256(base_bytes).hexdigest()):
            raise ValueError("Review packages extension base mismatch")
        schemas.extend(extra["schemas"])
    if source_evidence_v2_bytes is not None:
        if hashlib.sha256(source_evidence_v2_bytes).hexdigest() != _SOURCE_EVIDENCE_V2_EXTENSION_SHA256:
            raise ValueError("Source evidence V2 extension digest mismatch")
        extra = json.loads(source_evidence_v2_bytes)
        if (set(extra) != {"schema_version", "base_catalogue_sha256", "schemas"}
                or extra["schema_version"] != "1.0.0"
                or extra["base_catalogue_sha256"] != hashlib.sha256(base_bytes).hexdigest()):
            raise ValueError("Source evidence V2 extension base mismatch")
        schemas.extend(extra["schemas"])
    if document_operations_v2_bytes is not None:
        if hashlib.sha256(document_operations_v2_bytes).hexdigest() != _DOCUMENT_OPERATIONS_V2_EXTENSION_SHA256:
            raise ValueError("Document operations V2 extension digest mismatch")
        extra = json.loads(document_operations_v2_bytes)
        if (set(extra) != {"schema_version", "base_catalogue_sha256", "schemas"}
                or extra["schema_version"] != "1.0.0"
                or extra["base_catalogue_sha256"] != hashlib.sha256(base_bytes).hexdigest()):
            raise ValueError("Document operations V2 extension base mismatch")
        schemas.extend(extra["schemas"])
    if evaluation_business_v2_bytes is not None:
        if hashlib.sha256(evaluation_business_v2_bytes).hexdigest() != _EVALUATION_BUSINESS_V2_EXTENSION_SHA256:
            raise ValueError("Source evidence V2 extension digest mismatch")
        extra = json.loads(evaluation_business_v2_bytes)
        if (set(extra) != {"schema_version", "base_catalogue_sha256", "schemas"}
                or extra["schema_version"] != "1.0.0"
                or extra["base_catalogue_sha256"] != hashlib.sha256(base_bytes).hexdigest()):
            raise ValueError("Source evidence V2 extension base mismatch")
        schemas.extend(extra["schemas"])
    if strict_field_evidence_v2_bytes is not None:
        if hashlib.sha256(strict_field_evidence_v2_bytes).hexdigest() != _STRICT_FIELD_EVIDENCE_V2_EXTENSION_SHA256:
            raise ValueError("Source evidence V2 extension digest mismatch")
        extra = json.loads(strict_field_evidence_v2_bytes)
        if (set(extra) != {"schema_version", "base_catalogue_sha256", "schemas"}
                or extra["schema_version"] != "1.0.0"
                or extra["base_catalogue_sha256"] != hashlib.sha256(base_bytes).hexdigest()):
            raise ValueError("Source evidence V2 extension base mismatch")
        schemas.extend(extra["schemas"])
    if document_evidence_v2_bytes is not None:
        if hashlib.sha256(document_evidence_v2_bytes).hexdigest() != _DOCUMENT_EVIDENCE_V2_EXTENSION_SHA256:
            raise ValueError("Source evidence V2 extension digest mismatch")
        extra = json.loads(document_evidence_v2_bytes)
        if (set(extra) != {"schema_version", "base_catalogue_sha256", "schemas"}
                or extra["schema_version"] != "1.0.0"
                or extra["base_catalogue_sha256"] != hashlib.sha256(base_bytes).hexdigest()):
            raise ValueError("Source evidence V2 extension base mismatch")
        schemas.extend(extra["schemas"])
    if template_application_bytes is not None:
        if hashlib.sha256(template_application_bytes).hexdigest() != _TEMPLATE_APPLICATION_EXTENSION_SHA256:
            raise ValueError("Template application extension digest mismatch")
        extra = json.loads(template_application_bytes)
        if (set(extra) != {"schema_version", "base_catalogue_sha256", "schemas"}
                or extra["schema_version"] != "1.0.0"
                or extra["base_catalogue_sha256"] != hashlib.sha256(base_bytes).hexdigest()):
            raise ValueError("Template application extension base mismatch")
        schemas.extend(extra["schemas"])
    for content, expected_digest, label in (
        (task_tool_read_bytes, _TASK_TOOL_READ_EXTENSION_SHA256, "Task tool read"),
        (task_package_bytes, _TASK_PACKAGE_EXTENSION_SHA256, "Task package"),
        (task_package_v2_bytes, _TASK_PACKAGE_V2_EXTENSION_SHA256, "Task package V2"),
        (evaluation_stage_bytes, _EVALUATION_STAGE_EXTENSION_SHA256, "Evaluation stage"),
    ):
        if content is None:
            continue
        if hashlib.sha256(content).hexdigest() != expected_digest:
            raise ValueError(label + " extension digest mismatch")
        extra = json.loads(content)
        if (set(extra) != {"schema_version", "base_catalogue_sha256", "schemas"}
                or extra["schema_version"] != "1.0.0"
                or extra["base_catalogue_sha256"] != hashlib.sha256(base_bytes).hexdigest()):
            raise ValueError(label + " extension base mismatch")
        schemas.extend(extra["schemas"])
    for entry in schemas:
        if set(entry) != {"name", "path", "sha256"}:
            raise ValueError("Schema 登记字段不受支持")
        _check_schema_path(entry["path"])
        if entry["name"] in names:
            raise ValueError("Schema 名称重复")
        uri = urljoin(base["base_uri"], entry["path"])
        if uri in uris:
            raise ValueError("Schema 发布引用重复")
        names.add(entry["name"])
        uris.add(uri)
    return {**base, "schemas": schemas}


@dataclass(frozen=True)
class ValidatedPayload:
    schema_ref: str
    schema_sha256: str
    canonical_bytes: bytes

    def materialize(self):
        return json.loads(self.canonical_bytes)


class SchemaCatalogue:
    def __init__(self, project_root: Path):
        self.root = project_root.resolve(strict=True)
        self.manifest = _merge_published_catalogue(
            _registered_path(self.root, "contracts/catalogue.json").read_bytes(),
            _registered_path(self.root, _EXTENSION_PATH).read_bytes(),
            _registered_path(self.root, _SIMULATION_EXTENSION_PATH).read_bytes(),
            _registered_path(self.root, _TASK_RUNTIME_EXTENSION_PATH).read_bytes(),
            _registered_path(self.root, _CONTEXT_EXTENSION_PATH).read_bytes(),
            _registered_path(self.root, _PLANNING_EXTENSION_PATH).read_bytes(),
            _registered_path(self.root, _PLAN_DRAFT_PACKAGE_EXTENSION_PATH).read_bytes(),
            _registered_path(self.root, _REVIEW_PACKAGES_EXTENSION_PATH).read_bytes(),
            _registered_path(self.root, _SOURCE_EVIDENCE_V2_EXTENSION_PATH).read_bytes(),
            _registered_path(self.root, _DOCUMENT_EVIDENCE_V2_EXTENSION_PATH).read_bytes(),
            _registered_path(self.root, _TEMPLATE_APPLICATION_EXTENSION_PATH).read_bytes(),
            _registered_path(self.root, _STRICT_FIELD_EVIDENCE_V2_EXTENSION_PATH).read_bytes(),
            _registered_path(self.root, _DOCUMENT_OPERATIONS_V2_EXTENSION_PATH).read_bytes(),
            _registered_path(self.root, _EVALUATION_BUSINESS_V2_EXTENSION_PATH).read_bytes(),
            _registered_path(self.root, _TASK_TOOL_READ_EXTENSION_PATH).read_bytes(),
            _registered_path(self.root, _TASK_PACKAGE_EXTENSION_PATH).read_bytes(),
            _registered_path(self.root, _TASK_PACKAGE_V2_EXTENSION_PATH).read_bytes(),
            _registered_path(self.root, _EVALUATION_STAGE_EXTENSION_PATH).read_bytes(),
        )
        self.schemas = {}
        self.uris = {}
        self.digests = {}
        self.validators = {}
        self.formats = FormatChecker()
        self.registry = Registry()
        for entry in self.manifest["schemas"]:
            name = entry["name"]
            if name in self.schemas:
                raise ValueError("Schema 名称重复")
            path = _registered_path(self.root, entry["path"])
            data = path.read_bytes()
            if hashlib.sha256(data).hexdigest() != entry["sha256"]:
                raise ValueError("Schema 摘要不一致")
            schema = yaml.safe_load(data) if path.suffix == ".yaml" else json.loads(data)
            Draft202012Validator.check_schema(schema)
            if "$id" in schema:
                uri = schema["$id"]
                declared = urlsplit(uri)
                if not declared.scheme or declared.fragment or declared.query:
                    raise ValueError("Schema 声明身份必须使用完整固定引用")
            else:
                uri = urljoin(self.manifest["base_uri"], entry["path"])
            if uri in self.uris.values():
                raise ValueError("Schema 发布引用重复")
            self.schemas[name] = schema
            self.uris[name] = uri
            self.digests[name] = entry["sha256"]
            self.registry = self.registry.with_resource(uri, Resource.from_contents(schema))
        self.reference_count = 0
        for name, schema in self.schemas.items():
            self._check_references(schema, self.registry.resolver(self.uris[name]))

    def _check_references(self, node, resolver):
        if isinstance(node, dict):
            if "$ref" in node:
                # Registry 只登记当前发布集合，不配置网络读取能力。
                resolver.lookup(node["$ref"])
                self.reference_count += 1
            if isinstance(node.get("format"), str) and node["format"] not in self.formats.checkers:
                raise ValueError("Schema format 未取得校验组件")
            for child in node.values():
                self._check_references(child, resolver)
        elif isinstance(node, list):
            for child in node:
                self._check_references(child, resolver)

    def schema_ref(self, name: str, definition: str | None = None) -> str:
        if name not in self.schemas:
            raise UnsupportedSchemaError()
        uri = self.uris[name]
        if definition is not None:
            if definition not in self.schemas[name].get("$defs", {}):
                raise UnsupportedSchemaError()
            uri += "#/$defs/" + definition
        return uri

    def validate(self, name: str, value, definition: str | None = None):
        uri = self.schema_ref(name, definition)
        if uri not in self.validators:
            self.validators[uri] = Draft202012Validator(
                {"$ref": uri}, registry=self.registry, format_checker=self.formats
            )
        error = next(self.validators[uri].iter_errors(value), None)
        if error is not None:
            # 错误保留位置与规则；异常文字不携带受限载荷。
            raise SchemaValidationError(validator=error.validator, path=error.path)

    def bind(self, name: str, value, definition: str | None = None) -> ValidatedPayload:
        saved = deepcopy(value)
        self.validate(name, saved, definition)
        return ValidatedPayload(
            self.schema_ref(name, definition), self.digests[name], rfc8785.dumps(saved)
        )
