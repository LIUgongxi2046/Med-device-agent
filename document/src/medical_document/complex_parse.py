"""Offline, release-pinned Docling structure and Tesseract OCR integration.

Only called after the original has passed the existing malware scanner. No model
installation, download, remote service, or external data destination is supported.
"""
import hashlib
from importlib.metadata import version
from io import BytesIO
import os
from pathlib import Path
import stat
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field
import rfc8785


class Asset(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True)
    path: str
    sha256: str = Field(pattern=r"^[0-9a-f]{64}$")


class ComplexRelease(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True)
    schema_version: Literal["1.0.0"]
    engine: Literal["DOCLING_TESSERACT_LOCAL"]
    network_enabled: Literal[False]
    docling_version: Literal["2.73.1"]
    packages: dict[str, str]
    tesseract: Asset
    artifacts_directory: str
    tessdata_directory: str
    assets: list[Asset] = Field(min_length=1, max_length=10000)
    languages: list[str] = Field(min_length=1, max_length=16)
    timeout_seconds: int = Field(ge=1, le=600)


class ComplexParser:
    def __init__(self, release_bytes: bytes, expected_hash: str):
        if hashlib.sha256(release_bytes).hexdigest() != expected_hash:
            raise ValueError("OCR_RELEASE_CHANGED")
        self.release = ComplexRelease.model_validate_json(release_bytes)
        if rfc8785.dumps(self.release.model_dump()) != release_bytes:
            raise ValueError("OCR_RELEASE_NOT_CANONICAL")
        self.release_sha256 = expected_hash
        self.verify()

    def verify(self):
        release = self.release
        if release.packages.get("docling") != release.docling_version or not {"docling", "docling-core", "docling-parse", "torch", "transformers"} <= release.packages.keys():
            raise ValueError("OCR_PACKAGES_NOT_PINNED")
        for name, expected in release.packages.items():
            if version(name) != expected:
                raise ValueError("OCR_PACKAGE_CHANGED")
        roots = [Path(release.artifacts_directory), Path(release.tessdata_directory)]
        for root in roots:
            if not root.is_absolute() or root.resolve(strict=True) != root or not root.is_dir():
                raise ValueError("OCR_ASSET_DIRECTORY_INVALID")
        expected_paths = set()
        for asset in [release.tesseract, *release.assets]:
            path = Path(asset.path)
            if not path.is_absolute() or path.resolve(strict=True) != path or not path.is_file() or path.stat().st_mode & (stat.S_IWGRP | stat.S_IWOTH):
                raise ValueError("OCR_ASSET_PATH_INVALID")
            if asset is not release.tesseract:
                if not any(path.is_relative_to(root) for root in roots) or str(path) in expected_paths:
                    raise ValueError("OCR_ASSET_SCOPE_INVALID")
                expected_paths.add(str(path))
            with path.open("rb") as stream:
                if hashlib.file_digest(stream, "sha256").hexdigest() != asset.sha256:
                    raise ValueError("OCR_ASSET_CHANGED")
        actual = {str(path) for root in roots for path in root.rglob("*") if path.is_file()}
        if actual != expected_paths or not os.access(release.tesseract.path, os.X_OK):
            raise ValueError("OCR_ASSET_MANIFEST_INCOMPLETE")
        if any(not language.replace("_", "").isalnum() or str(roots[1] / (language + ".traineddata")) not in expected_paths for language in release.languages):
            raise ValueError("OCR_LANGUAGE_NOT_PINNED")

    def convert(self, content: bytes, name: str):
        from docling.datamodel.base_models import ConversionStatus, InputFormat
        from docling.datamodel.pipeline_options import PdfPipelineOptions, TesseractCliOcrOptions, TableFormerMode
        from docling.document_converter import DocumentConverter, PdfFormatOption
        from docling_core.types.io import DocumentStream
        self.verify()
        # Images are converted locally to a PDF processing representation; original
        # bytes and IMAGE locators remain authoritative. Never label this as an original PDF.
        if not content.startswith(b"%PDF-"):
            from PIL import Image
            with Image.open(BytesIO(content)) as image:
                if getattr(image, "n_frames", 1) != 1 or image.width * image.height > 100000000:
                    raise ValueError("IMAGE_FRAME_OR_PIXEL_LIMIT")
                stream = BytesIO()
                image.convert("RGB").save(stream, format="PDF", resolution=72.0)
                content = stream.getvalue()
                name = "image-processing-representation.pdf"
        # Worker has an empty environment; only local approved assets may be used.
        os.environ["HF_HUB_OFFLINE"] = "1"
        os.environ["TRANSFORMERS_OFFLINE"] = "1"
        os.environ["TESSDATA_PREFIX"] = self.release.tessdata_directory
        options = PdfPipelineOptions(artifacts_path=Path(self.release.artifacts_directory),
            enable_remote_services=False, allow_external_plugins=False, do_ocr=True,
            do_table_structure=True, document_timeout=self.release.timeout_seconds)
        options.ocr_options = TesseractCliOcrOptions(lang=self.release.languages,
            tesseract_cmd=self.release.tesseract.path, force_full_page_ocr=False)
        options.table_structure_options.mode = TableFormerMode.ACCURATE
        options.table_structure_options.do_cell_matching = True
        converter = DocumentConverter(allowed_formats=[InputFormat.PDF], format_options={
            InputFormat.PDF: PdfFormatOption(pipeline_options=options)})
        result = converter.convert(DocumentStream(name=name, stream=BytesIO(content)),
            raises_on_error=True, max_num_pages=1000, max_file_size=104857600)
        self.verify()
        if result.status != ConversionStatus.SUCCESS:
            raise ValueError("OCR_CONVERSION_INCOMPLETE")
        return result.document

    def parse_pdf(self, content, context):
        document = self.convert(content, context.request.path.name)
        context.manifest["complex_engine_release_sha256"] = self.release_sha256
        context.manifest["complex_engine"] = self.release.engine
        context.manifest["recognition_accuracy"] = "UNKNOWN"
        context.manifest["human_verification"] = "NOT_PERFORMED"
        context.manifest["extraction_basis"] = "LOCAL_LAYOUT_OCR_PIPELINE"
        context.manifest["complex_structure"] = []
        for item, depth in document.iterate_items():
            provenance = getattr(item, "prov", [])
            cells = getattr(getattr(item, "data", None), "table_cells", None)
            if cells is not None:
                for index, cell in enumerate(cells):
                    if not cell.text:
                        continue
                    if len(provenance) != 1 or cell.bbox is None:
                        context.issue("TABLE_CELL_LOCATION_UNKNOWN", None)
                        continue
                    locator = self.locator(document, provenance[0].page_no, cell.bbox, context)
                    context.node(cell.text, locator)
                    context.manifest["complex_structure"].append({"node_id": context.nodes[-1].node_id,
                        "table_ref": item.self_ref, "cell_index": index, "depth": depth,
                        "row_start": cell.start_row_offset_idx, "row_end": cell.end_row_offset_idx,
                        "column_start": cell.start_col_offset_idx, "column_end": cell.end_col_offset_idx,
                        "row_span": cell.row_span, "column_span": cell.col_span})
            elif getattr(item, "text", None):
                if len(provenance) != 1:
                    context.issue("OCR_LOCATION_UNKNOWN", None)
                    continue
                context.node(item.text, self.locator(document, provenance[0].page_no, provenance[0].bbox, context))
        context.manifest["complex_structure_sha256"] = hashlib.sha256(rfc8785.dumps(context.manifest["complex_structure"])).hexdigest()

    @staticmethod
    def locator(document, page_no, bbox, context):
        page = document.pages[page_no]
        box = bbox.to_top_left_origin(page_height=page.size.height)
        coordinates = [box.l / page.size.width, box.t / page.size.height, box.r / page.size.width, box.b / page.size.height]
        if not all(0 <= value <= 1 for value in coordinates) or coordinates[0] >= coordinates[2] or coordinates[1] >= coordinates[3]:
            raise ValueError("OCR_LOCATION_INVALID")
        if context.request.format == "IMAGE":
            return {"schema_version": "1.0.0", "kind": "IMAGE", "frame_index": page_no - 1,
                "original_sha256": context.request.sha256, "width": int(page.size.width), "height": int(page.size.height),
                **dict(zip(("x0", "y0", "x1", "y1"), coordinates)),
                "coordinate_system": "ORIGINAL_TOP_LEFT_NORMALIZED"}
        return {"kind": "PDF", "page_index": page_no - 1, "page_basis": context.request.page_basis,
            "printed_label": None, "region": {"kind": "BOX", **dict(zip(("x0", "y0", "x1", "y1"), coordinates)),
                "coordinate_system": "CROPBOX_TOP_LEFT_NORMALIZED", "rotation": 0}}
