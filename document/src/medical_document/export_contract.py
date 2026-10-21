"""Internal v2 export authority: authenticated command, never body-driven dispatch."""
from dataclasses import dataclass
import hashlib
import hmac
import json

import rfc8785
from medical_contracts.catalogue import SchemaCatalogue

V2_BODY_REF = 'https://schemas.medical-agent.invalid/2.0.0/document-body#/$defs/DocumentBody'

@dataclass(frozen=True)
class AuthenticatedBodyContract:
    schema_ref: str
    schema_sha256: str
    body_sha256: str
    attempt_id: str
    command_sha256: str


def verify_authentication(command_bytes: bytes, authentication: bytes):
    # The backend supplies a fresh secret and tag over a private child-process pipe.
    # Neither is accepted from request JSON or persisted in renderer output.
    if len(authentication) != 129 or authentication[64:65] != b':':
        raise ValueError('导出命令认证缺失')
    key = bytes.fromhex(authentication[:64].decode('ascii'))
    tag = authentication[65:].decode('ascii')
    if not hmac.compare_digest(hmac.new(key, command_bytes, hashlib.sha256).hexdigest(), tag):
        raise ValueError('导出命令认证失败')


def authenticate(command_bytes: bytes, authentication: bytes, catalogue: SchemaCatalogue):
    verify_authentication(command_bytes, authentication)
    request = json.loads(command_bytes)
    if rfc8785.dumps(request) != command_bytes:
        raise ValueError('导出命令必须使用固定规范字节')
    catalogue.validate('document-export-command-v2', request)
    # The catalogue loader has already checked the pinned publication bytes.
    registered = catalogue.digests['document-body-v2']
    if request['body_schema_ref'] != V2_BODY_REF or request['body_schema_sha256'] != registered:
        raise ValueError('导出正文登记 Schema 不一致')
    return request, AuthenticatedBodyContract(V2_BODY_REF, registered, request['body_sha256'],
        request['attempt_id'], hashlib.sha256(command_bytes).hexdigest())
