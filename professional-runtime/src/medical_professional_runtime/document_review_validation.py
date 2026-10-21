"""N07 byte integrity only. Current authority comes from the independently authenticated Java ledger."""
from .host import RuntimeBlocked, digest, sha, uuid, strict_json
from rfc8785 import dumps as canonical

COMPILER = 'N07_LITERAL_INSTRUCTIONS_AND_SAVED_DOCUMENT_V1'


def require_prompt(prompt, expected_sha256, provenance, input_sha256):
    fields = {'schema_version','compiler','input_sha256','launch_sha256','role_basis','prompt_basis','role_package','prompt_package','profile_package','adapter_artifact','review_content'}
    if not isinstance(prompt, str) or not prompt or not isinstance(provenance, dict) or set(provenance) != fields:
        raise RuntimeBlocked('N07_COMPILED_PROMPT_REQUIRED')
    if provenance['schema_version'] != '1.0.0' or provenance['compiler'] != COMPILER or provenance['input_sha256'] != input_sha256 or provenance['launch_sha256'] != expected_sha256 or digest(prompt.encode()) != sha(expected_sha256):
        raise RuntimeBlocked('N07_COMPILED_PROMPT_MISMATCH')
    for key in ('role_basis','prompt_basis','role_package','prompt_package','profile_package','adapter_artifact'):
        if not isinstance(provenance[key], dict) or not provenance[key]:
            raise RuntimeBlocked('N07_PACKAGE_PINS_REQUIRED')
    document = strict_json(prompt)
    if not isinstance(document, dict) or set(document) != {'schema_version','node_kind','instructions','document_review_input','review_content'} or document['schema_version'] != '1.0.0' or document['node_kind'] != 'N07' or not isinstance(document['instructions'], str) or not document['instructions'].strip():
        raise RuntimeBlocked('N07_PROMPT_DOCUMENT_INVALID')
    source = document['document_review_input']
    source_fields = {'schema_version','execution_kind','job_type','node_kind','tenant_id','project_id','run_id','step_id','intent_id','document_id','document_revision','body_sha256','baseline_sha256','rules_sha256','document_body','document_baseline','rules_manifest','task_relation'}
    if not isinstance(source, dict) or set(source) != source_fields or source.get('node_kind') != 'N07' or source.get('execution_kind') != 'SYSTEM_JOB' or source.get('job_type') != 'CHECK_DOCUMENT' or digest(canonical(source)) != input_sha256:
        raise RuntimeBlocked('N07_SAVED_INPUT_MISMATCH')
    for key in ('tenant_id','project_id','run_id','step_id','intent_id','document_id','document_revision'):
        uuid(source[key])
    baseline, body = source['document_baseline'], source['document_body']
    if not isinstance(baseline, dict) or baseline.get('target_kind') != 'DOCUMENT' or baseline.get('node_kind') != 'N07' or baseline.get('document_revision') != source['document_revision'] or baseline.get('document_id') != source['document_id'] or digest(canonical(baseline)) != sha(source['baseline_sha256']) or digest(canonical(body)) != sha(source['body_sha256']) or baseline.get('body_sha256') != source['body_sha256']:
        raise RuntimeBlocked('N07_SAVED_BODY_BASELINE_MISMATCH')
    content, pins = document['review_content'], provenance['review_content']
    pin_fields = {'schema_version','kind','session_id','input_sha256','baseline_sha256','content_sha256','rules_sha256','rule_authorizations_sha256','writer_session_ids','content_members','rule_ids','rule_reads','objects','sources'}
    if not isinstance(content, dict) or set(content) != {'session_id','rules','rule_read_receipts','artifacts','source_chunks'} or not isinstance(pins, dict) or set(pins) != pin_fields or pins['schema_version'] != '1.0.0' or pins['kind'] != 'N07_PHYSICAL_CONTEXT' or pins['session_id'] != content['session_id'] or pins['input_sha256'] != input_sha256 or pins['baseline_sha256'] != source['baseline_sha256'] or pins['content_sha256'] != digest(canonical(content)):
        raise RuntimeBlocked('N07_PHYSICAL_CONTENT_MISMATCH')
    uuid(pins['session_id']); sha(pins['rule_authorizations_sha256'])
    writers = pins['writer_session_ids']
    if not isinstance(writers, list) or len(set(writers)) != len(writers) or pins['session_id'] in writers:
        raise RuntimeBlocked('N07_INDEPENDENT_SESSION_REQUIRED')
    for writer in writers:
        uuid(writer)
    if not isinstance(pins['content_members'], list) or not pins['content_members']:
        raise RuntimeBlocked('N07_CONTENT_LINEAGE_REQUIRED')
    rules = content['rules']
    if not isinstance(rules, dict) or rules != source['rules_manifest'] or rules.get('node_kind') != 'N07' or rules.get('target_kind') != 'DOCUMENT' or rules.get('document_revision') != source['document_revision'] or rules.get('target_sha256') != source['body_sha256'] or pins['rules_sha256'] != digest(canonical(rules)) or pins['rules_sha256'] != source['rules_sha256'] or pins['rule_authorizations_sha256'] != digest(canonical(rules.get('authorizations'))):
        raise RuntimeBlocked('N07_RULE_MANIFEST_MISMATCH')
    required = rules.get('rules')
    if not isinstance(required, list) or not 0 < len(required) <= 10000 or not isinstance(pins['rule_reads'], list) or len(pins['rule_reads']) != len(required) or not isinstance(content['rule_read_receipts'], list) or len(content['rule_read_receipts']) != len(required):
        raise RuntimeBlocked('N07_COMPLETE_RULE_CONTEXT_REQUIRED')
    expected = {uuid(item['rule_id']): sha(item['sha256']) for item in required}
    if len(expected) != len(required) or pins['rule_ids'] != sorted(expected):
        raise RuntimeBlocked('N07_COMPLETE_RULE_CONTEXT_REQUIRED')
    seen = set(); receipts = set()
    for rule, read, receipt in zip(required, pins['rule_reads'], content['rule_read_receipts']):
        if not isinstance(rule, dict) or set(rule) != {'rule_id','sha256','content'} or rule['rule_id'] in seen or expected.get(rule['rule_id']) != rule['sha256'] or not isinstance(rule['content'], dict) or not isinstance(read, dict) or set(read) != {'rule_id','sha256','receipt_id','complete'} or read['rule_id'] != rule['rule_id'] or read['sha256'] != rule['sha256'] or read['complete'] is not True or receipt != {'rule_id':rule['rule_id'],'receipt_id':read['receipt_id']}:
            raise RuntimeBlocked('N07_COMPLETE_RULE_CONTEXT_REQUIRED')
        seen.add(rule['rule_id']); receipts.add(uuid(read['receipt_id']))
    if len(receipts) != len(required):
        raise RuntimeBlocked('N07_DUPLICATE_READ_RECEIPT')
    artifacts, objects, chunks, sources = content['artifacts'], pins['objects'], content['source_chunks'], pins['sources']
    if not isinstance(artifacts, list) or not isinstance(objects, list) or len(artifacts) != 1 or len(objects) != 1 or not isinstance(chunks, list) or not isinstance(sources, list) or not 0 <= len(chunks) == len(sources) <= 512:
        raise RuntimeBlocked('N07_PHYSICAL_CONTENT_BOUND')
    artifact, pin = artifacts[0], objects[0]
    if not isinstance(artifact, dict) or set(artifact) != {'artifact_id','sha256','payload'} or not isinstance(pin, dict) or set(pin) != {'artifact_id','object_ref'} or artifact['artifact_id'] != pin['artifact_id'] or artifact['artifact_id'] != baseline.get('body_artifact_id') or artifact['payload'] != body:
        raise RuntimeBlocked('N07_TARGET_PINS_REQUIRED')
    ref = _object_ref(pin['object_ref'], source['tenant_id']); raw = canonical(body)
    if ref['media_type'] != 'application/json' or ref['sha256'] != digest(raw) or artifact['sha256'] != ref['sha256'] or ref['size_bytes'] != str(len(raw)) or ref['object_version'] != baseline.get('object_version'):
        raise RuntimeBlocked('N07_TARGET_BYTES_MISMATCH')
    evidence = baseline.get('evidence_refs')
    if not isinstance(evidence, list):
        raise RuntimeBlocked('N07_COMPLETE_SOURCE_CONTEXT_REQUIRED')
    expected_sources = {canonical(ref) for ref in evidence}
    seen_sources = set()
    for chunk, pin in zip(chunks, sources):
        if not isinstance(chunk, dict) or set(chunk) != {'raw_text','evidence_ref','receipt_id','located','read_status'} or not isinstance(chunk['raw_text'], str) or not isinstance(pin, dict) or set(pin) != {'evidence_ref','receipt_id','access','located','complete','literal_sha256','processing_sha256','original_object','parsed_object'} or chunk['evidence_ref'] != pin['evidence_ref'] or not isinstance(pin['evidence_ref'], dict) or chunk['receipt_id'] != pin['receipt_id'] or pin['access'] is not True or type(pin['located']) is not bool or pin['complete'] is not pin['located'] or chunk['located'] is not pin['located'] or chunk['read_status'] != ('COMPLETE' if pin['located'] else 'LOCATOR_MISMATCH') or (not pin['located'] and chunk['raw_text'] != '') or pin['literal_sha256'] != digest(chunk['raw_text'].encode()):
            raise RuntimeBlocked('N07_COMPLETE_SOURCE_CONTEXT_REQUIRED')
        key = canonical(pin['evidence_ref']); receipt = uuid(pin['receipt_id'])
        if key in seen_sources or receipt in receipts:
            raise RuntimeBlocked('N07_SOURCE_DUPLICATE')
        seen_sources.add(key); receipts.add(receipt); sha(pin['processing_sha256'])
        _object_ref(pin['original_object'], source.get('tenant_id')); _object_ref(pin['parsed_object'], source.get('tenant_id'))

    if seen_sources != expected_sources:
        raise RuntimeBlocked('N07_COMPLETE_SOURCE_CONTEXT_REQUIRED')


def _object_ref(ref, tenant):
    if not isinstance(ref, dict) or set(ref) != {'object_key','object_version','sha256','size_bytes','media_type'} or not isinstance(ref['object_key'], str) or not ref['object_key'].startswith('tenants/' + uuid(tenant) + '/') or '..' in ref['object_key'] or not isinstance(ref['object_version'], str) or not ref['object_version'] or not isinstance(ref['size_bytes'], str) or not ref['size_bytes'].isdigit() or not 0 < int(ref['size_bytes']) <= 67108864 or not isinstance(ref['media_type'], str) or not ref['media_type']:
        raise RuntimeBlocked('N07_OBJECT_PIN_INVALID')
    sha(ref['sha256'])
    return ref
