import hashlib
from importlib.metadata import version
import json
from pathlib import Path
import platform
import subprocess

from medical_contracts import DatasetBindings, ErrorCode, ModelStreamBindings, SchemaCatalogue
from medical_contracts.wire import code_point_quote, decimal_value, unsigned_revision, utc_time, uuid_value
from error_design import published_error_codes


ROOT = Path(__file__).resolve().parents[1]


def main():
    pointer = json.loads((ROOT / ".implementation-work/IC-03-JAVA-SNAPSHOT/current.json").read_bytes())
    java_path = (ROOT / pointer["report_path"]).resolve(strict=True)
    if not java_path.is_relative_to(ROOT / ".implementation-work/IC-03-JAVA-SNAPSHOT/executions"):
        raise ValueError("Java 执行报告路径不在登记目录")
    java_bytes = java_path.read_bytes()
    if hashlib.sha256(java_bytes).hexdigest() != pointer["sha256"]:
        raise ValueError("Java 执行报告摘要不一致")
    java = json.loads(java_bytes)
    if java_path.parent.name != java["execution_id"]:
        raise ValueError("Java 执行身份与报告目录不一致")
    for path, expected in java["inputs"].items():
        if hashlib.sha256((ROOT / path).read_bytes()).hexdigest() != expected:
            raise ValueError("Java 证据输入已经变化")
    execution_id = java["execution_id"]
    error_codes = sorted(code.value for code in ErrorCode)
    if error_codes != published_error_codes(ROOT):
        raise ValueError("错误枚举与发布设计不一致")
    catalogue = SchemaCatalogue(ROOT)
    dataset = json.loads((ROOT / "data-design/sample_dataset.json").read_bytes())
    dataset_bindings = DatasetBindings(catalogue)
    model_bindings = ModelStreamBindings(catalogue)
    bound = dataset_bindings.bind(dataset)
    if bound.read().model_dump(mode="json") != dataset:
        raise ValueError("Python JSON 往返改变内容")
    sources = {source["source_id"]: source for source in dataset["sources"]}
    source_digests = {}
    scalar_digests = {}
    for identity, source in sources.items():
        data = (ROOT / source["source_ref"]).read_bytes()
        digest = hashlib.sha256(data).hexdigest()
        if digest != source["sha256"] or len(data) != source["size_bytes"]:
            raise ValueError("原件摘要或长度不一致")
        source_digests[identity] = digest
        for field, definition in (("source_id", "Text"), ("sha256", "Hash"), ("size_bytes", "TokenCount"), ("parent_source_id", "NullableText")):
            scalar_digests[identity + "/" + field] = hashlib.sha256(model_bindings.bind(source[field], definition).canonical_bytes).hexdigest()
    locators = {}
    text_ranges = {}
    for record in dataset["records"]:
        scalar_digests[record["record_id"] + "/text"] = hashlib.sha256(model_bindings.bind(record["text"], "Text").canonical_bytes).hexdigest()
        locator = record["locator"]
        locators[record["record_id"]] = hashlib.sha256(dataset_bindings.bind(locator, "locator").canonical_bytes).hexdigest()
        if locator["kind"] != "TXT":
            continue
        original = (ROOT / sources[record["source_id"]]["source_ref"]).read_bytes().decode("utf-8")
        quote = code_point_quote(original, locator["char_start"], locator["char_end"], record["quote_hash"])
        text_ranges[record["record_id"]] = hashlib.sha256(quote.encode("utf-8")).hexdigest()
    python = {
        "execution_id": execution_id,
        "dataset_sha256": hashlib.sha256(bound.canonical_bytes).hexdigest(),
        "source_sha256": source_digests,
        "locator_sha256": locators,
        "text_ranges": text_ranges,
        "schema_scalar_sha256": scalar_digests,
        "definition_count": sum(len(schema.get("$defs", {})) for schema in catalogue.schemas.values()),
        "error_codes": error_codes,
        "technical_wire": {
            "execution_uuid": str(uuid_value(execution_id)),
            "unsigned_max": str(unsigned_revision(str((1 << 64) - 1))),
            "money_boundary": format(decimal_value("0.00000001", 24, 8), "f"),
            "measurement_boundary": format(decimal_value("0.000000000001", 38, 12), "f"),
            "utc_time": utc_time(dataset["created_at"]).isoformat(timespec="microseconds").replace("+00:00", "Z"),
        },
    }
    node = subprocess.run(
        ["node", "--import", "tsx", "src/inspect.ts", execution_id],
        cwd=ROOT / "contracts/typescript", stdout=subprocess.PIPE, check=True,
    )
    typescript = json.loads(node.stdout)
    for key, expected in python.items():
        if java[key] != expected or typescript[key] != expected:
            raise ValueError(f"跨语言表示不一致：{key}")
    print(json.dumps({
        "result": "PASS",
        "execution_id": execution_id,
        "definition_count": python["definition_count"],
        "error_code_count": len(error_codes),
        "sources": len(source_digests),
        "locators": len(locators),
        "reopened_text_ranges": len(text_ranges),
        "schema_scalar_values": len(scalar_digests),
        "canonical_dataset_sha256": python["dataset_sha256"],
        "java_report_sha256": hashlib.sha256(java_path.read_bytes()).hexdigest(),
        "java_report_path": str(java_path.relative_to(ROOT)),
        "python": platform.python_version(),
        "libraries": {name: version(name) for name in ("jsonschema", "referencing", "PyYAML", "rfc8785", "pydantic", "pydantic-core", "markdown-it-py", "mdurl")},
        "scope": "LOCAL_SCHEMA_AND_WIRE_CHECK",
    }, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
