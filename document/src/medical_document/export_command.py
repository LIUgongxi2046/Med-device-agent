from pathlib import Path
import json
import sys

from fontTools.ttLib import TTFont
import rfc8785

from medical_contracts.catalogue import SchemaCatalogue
from medical_document.export import ConverterBinding, ExportInput, ExportRenderer, FixedAsset, FontBinding, FontResource


def required(value: dict, name: str):
    if name not in value:
        raise ValueError('导出命令缺少字段: ' + name)
    return value[name]


def font_family(path: Path) -> str:
    with TTFont(path, lazy=False) as font:
        preferred = sorted({item.toUnicode() for item in font['name'].names if item.nameID == 16})
        families = preferred or sorted({item.toUnicode() for item in font['name'].names if item.nameID == 1})
    if not families:
        raise ValueError('字体文件缺少名称')
    return families[0]


def execute(request_path: Path, result_path: Path) -> None:
    request = json.loads(request_path.read_bytes())
    if required(request, 'schema_version') != '1.0.0':
        raise ValueError('导出命令版本无效')
    fonts = required(request, 'fonts')
    if not fonts:
        raise ValueError('导出命令缺少字体')
    primary_path = Path(required(fonts[0], 'path')).resolve(strict=True)
    primary_family = font_family(primary_path)
    additional = tuple(FontResource(Path(required(item, 'path')).resolve(strict=True), required(item, 'sha256'),
        font_family(Path(required(item, 'path')).resolve(strict=True))) for item in fonts[1:])
    font = FontBinding(required(request, 'font_manifest_revision'), primary_path, required(fonts[0], 'sha256'),
        primary_family, additional)
    converter_data = required(request, 'converter')
    converter = ConverterBinding(required(converter_data, 'revision'), Path(required(converter_data, 'path')).resolve(strict=True),
        required(converter_data, 'version'), required(converter_data, 'timeout_seconds'), required(converter_data, 'sha256'))
    plan = ExportInput(required(request, 'tenant_id'), required(request, 'project_id'), required(request, 'document_revision'),
        required(request, 'attempt_id'), required(request, 'template_version'), required(request, 'body_sha256'),
        required(request, 'template_sha256'), required(request, 'purpose'), required(request, 'input_data_kind'), font, converter)
    assets = tuple(FixedAsset(required(item, 'artifact_id'), required(item, 'sha256'), required(item, 'media_type'),
        Path(required(item, 'path')).resolve(strict=True).read_bytes()) for item in required(request, 'assets'))
    renderer = ExportRenderer(SchemaCatalogue(Path(required(request, 'catalogue_root')).resolve(strict=True)))
    body = Path(required(request, 'body_path')).resolve(strict=True).read_bytes()
    template = Path(required(request, 'template_path')).resolve(strict=True).read_bytes()
    output = Path(required(request, 'output_directory')).resolve(strict=True)
    format_name = required(request, 'format')
    if format_name == 'DOCX':
        exported = renderer.docx(plan, body, template, assets, output / 'docx')
    elif format_name == 'PDF':
        source = renderer.docx(plan, body, template, assets, output / 'docx')
        exported = renderer.pdf(plan, source, output / 'pdf')
    elif format_name == 'MARKDOWN':
        exported = renderer.markdown(plan, body, assets, output / 'markdown')
    else:
        raise ValueError('导出格式无效')
    validation = {
        'schema_version': '1.0.0',
        'format': exported.format,
        'sha256': exported.sha256,
        'size_bytes': str(exported.size_bytes),
        'input_sha256': exported.input_sha256,
        'verified_node_ids': list(exported.verified_node_ids),
        'plan_sha256': exported.plan_sha256,
        'channel_check': exported.channel_check,
        'visual_check': exported.visual_check,
        'converter_revision': converter.revision,
        'font_manifest_revision': font.manifest_revision,
    }
    validation_path = output / 'validation.json'
    validation_path.write_bytes(rfc8785.dumps(validation))
    result_path.write_text(json.dumps({
        'schema_version': '1.0.0',
        'output_path': str(exported.path.resolve(strict=True)),
        'validation_path': str(validation_path.resolve(strict=True)),
        'validation': validation,
    }, ensure_ascii=False, separators=(',', ':')), encoding='utf-8')


if __name__ == '__main__':
    if len(sys.argv) != 3:
        raise ValueError('导出命令参数数量无效')
    execute(Path(sys.argv[1]), Path(sys.argv[2]))
