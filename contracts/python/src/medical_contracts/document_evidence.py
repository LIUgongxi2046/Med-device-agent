"""Document body v2 binds the exact new publication; legacy interfaces stay strict."""
from typing import Literal
from . import interfaces as v1
from .source_evidence import EvidenceRefV2
from .typed import TypedSchemaBindings

class EvidenceLinkV2(v1.EvidenceLink):
    evidence: EvidenceRefV2
class DocumentBodyV2(v1.DocumentBody):
    schema_version: Literal["2.0.0"]
    evidence_links: list[EvidenceLinkV2]
class DocumentFieldProjectionV2(v1.DocumentFieldProjection):
    schema_version: Literal["2.0.0"]
class DocumentEvidenceProjectionV2(v1.DocumentEvidenceProjection):
    schema_version: Literal["2.0.0"]
    evidence_links: list[EvidenceLinkV2]
class BodyPageV2(v1.BodyPage):
    evidence_links: list[EvidenceLinkV2]
class UploadRequestV2(v1.UploadRequest):
    schema_ref: Literal["https://schemas.medical-agent.invalid/2.0.0/document-body#/$defs/DocumentBody"]
class DocumentEvidenceBindings(TypedSchemaBindings):
    def __init__(self, catalogue):
        definitions = dict(v1.InterfaceBindings(catalogue).definitions)
        definitions.update(DocumentBody=DocumentBodyV2, EvidenceLink=EvidenceLinkV2,
                           DocumentFieldProjection=DocumentFieldProjectionV2,
                           DocumentEvidenceProjection=DocumentEvidenceProjectionV2,
                           BodyPage=BodyPageV2, UploadRequest=UploadRequestV2)
        required = catalogue.schemas["document-body-v2"]["$defs"]
        super().__init__(catalogue, "document-body-v2", {key: definitions[key] for key in required}, DocumentBodyV2)
