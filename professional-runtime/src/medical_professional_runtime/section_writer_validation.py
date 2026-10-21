"""N04 prompt integrity. Authenticated Java authority owns registration and rights."""
from .host import RuntimeBlocked, digest, sha, uuid, strict_json
from rfc8785 import dumps as canonical

COMPILER = 'N04_LITERAL_INSTRUCTIONS_AND_REGISTERED_INPUT_V1'


def require_prompt(prompt, expected_sha256, provenance, input_sha256):
    fields = {'schema_version','compiler','input_artifact_id','input_sha256','input_receipt_sha256','launch_sha256','role_basis','prompt_basis','role_package','prompt_package','profile_package','adapter_artifact','writer_content'}
    if not isinstance(prompt, str) or not prompt or not isinstance(provenance, dict) or set(provenance) != fields:
        raise RuntimeBlocked('N04_COMPILED_PROMPT_REQUIRED')
    if provenance['schema_version'] != '1.0.0' or provenance['compiler'] != COMPILER or provenance['input_sha256'] != input_sha256 or provenance['launch_sha256'] != expected_sha256 or digest(prompt.encode()) != sha(expected_sha256):
        raise RuntimeBlocked('N04_COMPILED_PROMPT_MISMATCH')
    uuid(provenance['input_artifact_id']); sha(provenance['input_receipt_sha256'])
    for key in ('role_basis','prompt_basis','role_package','prompt_package','profile_package','adapter_artifact'):
        if not isinstance(provenance[key], dict) or not provenance[key]:
            raise RuntimeBlocked('N04_PACKAGE_PINS_REQUIRED')
    document = strict_json(prompt)
    if not isinstance(document, dict) or set(document) != {'schema_version','node_kind','instructions','section_writer_input','writer_content'} or document['schema_version'] != '1.0.0' or document['node_kind'] != 'N04' or not isinstance(document['instructions'], str) or not document['instructions'].strip():
        raise RuntimeBlocked('N04_PROMPT_DOCUMENT_INVALID')
    source = document['section_writer_input']
    if not isinstance(source, dict) or source.get('node_kind') != 'N04' or source.get('plan_revision') is None or digest(canonical(source)) != input_sha256:
        raise RuntimeBlocked('N04_REGISTERED_INPUT_MISMATCH')
    content, pins = document['writer_content'], provenance['writer_content']
    pin_fields = {'schema_version','kind','input_id','input_sha256','input_receipt_sha256','content_sha256','objects','sources'}
    if not isinstance(content, dict) or set(content) != {'artifacts','source_chunks'} or not isinstance(pins, dict) or set(pins) != pin_fields or pins['schema_version'] != '1.0.0' or pins['kind'] != 'N04_PHYSICAL_CONTEXT' or pins['input_id'] != provenance['input_artifact_id'] or pins['input_sha256'] != input_sha256 or pins['input_receipt_sha256'] != provenance['input_receipt_sha256'] or pins['content_sha256'] != digest(canonical(content)):
        raise RuntimeBlocked('N04_PHYSICAL_CONTENT_MISMATCH')
    artifacts, objects, chunks, sources = content['artifacts'], pins['objects'], content['source_chunks'], pins['sources']
    if not isinstance(artifacts, list) or not isinstance(objects, list) or not 0 < len(artifacts) == len(objects) <= 514 or not isinstance(chunks, list) or not isinstance(sources, list) or not 0 <= len(chunks) == len(sources) <= 512:
        raise RuntimeBlocked('N04_PHYSICAL_CONTENT_BOUND')
    seen = set()
    for artifact, pin in zip(artifacts, objects):
        if not isinstance(artifact, dict) or set(artifact) != {'artifact_id','sha256','payload'} or not isinstance(pin, dict) or set(pin) != {'artifact_id','object_ref'} or artifact['artifact_id'] != pin['artifact_id'] or artifact['artifact_id'] in seen:
            raise RuntimeBlocked('N04_COMPANION_PINS_REQUIRED')
        seen.add(uuid(artifact['artifact_id']))
        ref = _object_ref(pin['object_ref'], source.get('tenant_id'))
        raw = canonical(artifact['payload'])
        if ref['media_type'] != 'application/json' or ref['sha256'] != digest(raw) or artifact['sha256'] != ref['sha256'] or ref['size_bytes'] != str(len(raw)):
            raise RuntimeBlocked('N04_COMPANION_BYTES_MISMATCH')
    seen_sources = set()
    for chunk, pin in zip(chunks, sources):
        if not isinstance(chunk, dict) or set(chunk) != {'raw_text','evidence_ref'} or not isinstance(chunk['raw_text'], str) or not isinstance(pin, dict) or set(pin) != {'evidence_ref','processing_sha256','original_object','parsed_object'} or chunk['evidence_ref'] != pin['evidence_ref'] or not isinstance(pin['evidence_ref'], dict):
            raise RuntimeBlocked('N04_SOURCE_PINS_REQUIRED')
        key = canonical(pin['evidence_ref'])
        if key in seen_sources:
            raise RuntimeBlocked('N04_SOURCE_DUPLICATE')
        seen_sources.add(key)
        sha(pin['processing_sha256'])
        _object_ref(pin['original_object'], source.get('tenant_id'))
        _object_ref(pin['parsed_object'], source.get('tenant_id'))


def _object_ref(ref, tenant):
    if not isinstance(ref, dict) or set(ref) != {'object_key','object_version','sha256','size_bytes','media_type'} or not isinstance(ref['object_key'], str) or not ref['object_key'].startswith('tenants/' + uuid(tenant) + '/') or '..' in ref['object_key'] or not isinstance(ref['object_version'], str) or not ref['object_version'] or not isinstance(ref['size_bytes'], str) or not ref['size_bytes'].isdigit() or not 0 < int(ref['size_bytes']) <= 67108864 or not isinstance(ref['media_type'], str) or not ref['media_type']:
        raise RuntimeBlocked('N04_OBJECT_PIN_INVALID')
    sha(ref['sha256'])
    return ref
