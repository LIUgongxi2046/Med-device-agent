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
        self.manifest = json.loads((self.root / "contracts/catalogue.json").read_bytes())
        if self.manifest["schema_version"] != "1.0.0":
            raise UnsupportedSchemaError()
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
            path = (self.root / entry["path"]).resolve(strict=True)
            if not path.is_relative_to(self.root):
                raise ValueError("Schema 路径超出登记目录")
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
