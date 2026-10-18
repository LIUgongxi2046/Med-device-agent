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


def _merge_published_catalogue(base_bytes: bytes, extension_bytes: bytes, simulation_bytes: bytes | None = None, task_runtime_bytes: bytes | None = None, context_bytes: bytes | None = None):
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
