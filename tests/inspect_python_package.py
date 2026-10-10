import hashlib
from importlib.metadata import distribution, version
import json
from pathlib import Path
import platform
import sys

from medical_contracts import ArtifactBindings, DatasetBindings, ErrorCode, InterfaceBindings, ModelStreamBindings, ResourceBindings, SchemaCatalogue
import medical_contracts


ROOT = Path(__file__).resolve().parents[1]
wheel = Path(sys.argv[1]).resolve(strict=True)
installed = Path(medical_contracts.__file__).resolve(strict=True).parent
if not installed.is_relative_to(ROOT / ".lld-data-work/venv/lib/python3.12/site-packages"):
    raise ValueError("共享库没有从登记 Python 环境的安装制品读取")
package = distribution("medical-contracts")
if package.version != "1.0.0" or "pydantic==2.13.5" not in package.requires:
    raise ValueError("Python 安装制品身份或依赖不一致")
source = ROOT / "contracts/python/src/medical_contracts"
expected = {path.name for path in source.glob("*.py")}
actual = {path.name for path in installed.glob("*.py")}
if expected != actual:
    raise ValueError("Python 安装制品与正式源码的文件集合不一致")
digests = {}
for name in sorted(expected):
    digest = hashlib.sha256((source / name).read_bytes()).hexdigest()
    if hashlib.sha256((installed / name).read_bytes()).hexdigest() != digest:
        raise ValueError("Python 安装制品与正式源码的内容不一致")
    digests[name] = digest
catalogue = SchemaCatalogue(ROOT)
bindings = [DatasetBindings(catalogue), ModelStreamBindings(catalogue), ResourceBindings(catalogue), ArtifactBindings(catalogue), InterfaceBindings(catalogue)]
dataset = json.loads((ROOT / "data-design/sample_dataset.json").read_bytes())
if bindings[0].bind(dataset).read().model_dump(mode="json") != dataset:
    raise ValueError("安装制品读取真实资料后改变内容")
print(json.dumps({
    "result": "PASS", "scope": "LOCAL_PACKAGE_CHECK", "python": platform.python_version(),
    "package": package.version, "installed_from": str(installed),
    "artifact": str(wheel), "artifact_sha256": hashlib.sha256(wheel.read_bytes()).hexdigest(),
    "source_files": digests, "typed_definition_count": sum(len(item.definitions) for item in bindings),
    "common_definition_count": len(bindings[2].common_definitions),
    "error_code_count": len(ErrorCode),
    "libraries": {name: version(name) for name in ("setuptools", "pydantic", "pydantic-core", "annotated-types", "typing-inspection", "jsonschema", "referencing", "PyYAML", "rfc8785", "markdown-it-py", "mdurl")},
}, ensure_ascii=False, indent=2))
