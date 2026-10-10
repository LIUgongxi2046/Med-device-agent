from dataclasses import dataclass
from types import MappingProxyType
from typing import Generic, Mapping, TypeVar

from pydantic import BaseModel, ConfigDict, TypeAdapter
import rfc8785

from .catalogue import SchemaCatalogue, ValidatedPayload


Payload = TypeVar("Payload")


@dataclass(frozen=True)
class TypedPayload(Generic[Payload]):
    _saved: ValidatedPayload
    _adapter: TypeAdapter[Payload]

    @property
    def schema_ref(self) -> str:
        return self._saved.schema_ref

    @property
    def schema_sha256(self) -> str:
        return self._saved.schema_sha256

    @property
    def canonical_bytes(self) -> bytes:
        return self._saved.canonical_bytes

    def read(self) -> Payload:
        # 每次读取从规范内容创建独立对象，成员数组不会共享已保存状态。
        return self._adapter.validate_python(self._saved.materialize(), strict=True)


class TypedSchemaBindings:
    def __init__(self, catalogue: SchemaCatalogue, name: str, definitions: Mapping[str, object], root_type=None):
        if set(definitions) != set(catalogue.schemas[name]["$defs"]):
            raise ValueError("Schema 定义与领域类型登记不一致")
        self.catalogue = catalogue
        self.name = name
        self.definitions = MappingProxyType(dict(definitions))
        self.root_type = root_type
        self._adapters = {definition: self._adapter(domain_type) for definition, domain_type in definitions.items()}
        if root_type is not None:
            self._adapters[None] = self._adapter(root_type)

    @staticmethod
    def _adapter(domain_type) -> TypeAdapter:
        if isinstance(domain_type, type) and issubclass(domain_type, BaseModel):
            return TypeAdapter(domain_type)
        return TypeAdapter(domain_type, config=ConfigDict(strict=True, hide_input_in_errors=True, allow_inf_nan=False))

    def bind(self, value, definition: str | None = None) -> TypedPayload:
        saved = self.catalogue.bind(self.name, value, definition)
        if definition not in self._adapters:
            raise ValueError("Schema 根类型未登记")
        adapter = self._adapters[definition]
        typed = adapter.validate_python(saved.materialize(), strict=True)
        restored = adapter.dump_python(typed, mode="json", by_alias=True, warnings="error")
        self.catalogue.validate(self.name, restored, definition)
        if rfc8785.dumps(restored) != saved.canonical_bytes:
            raise ValueError("领域类型序列化内容与原始载荷不一致")
        return TypedPayload(saved, adapter)
