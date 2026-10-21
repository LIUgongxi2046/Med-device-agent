"""N05 byte integrity, not read authorization. Java's original ledger grants rights.

Only a complete physically read independent context can enter the tool-free session.
Writer artifacts remain review targets, never independent read/review proof.
"""
from .host import RuntimeBlocked, digest, sha, uuid, strict_json
from rfc8785 import dumps as canonical

COMPILER = 'N05_LITERAL_INSTRUCTIONS_AND_REGISTERED_INPUT_V1'


def require_prompt(prompt, expected_sha256, provenance, input_sha256):
    fields = {'schema_version','compiler','input_artifact_id','input_sha256','input_receipt_sha256','launch_sha256','role_basis','prompt_basis','role_package','prompt_package','profile_package','adapter_artifact','review_content'}
    if not isinstance(prompt, str) or not prompt or not isinstance(provenance, dict) or set(provenance) != fields:
        raise RuntimeBlocked('N05_COMPILED_PROMPT_REQUIRED')
    if provenance['schema_version'] != '1.0.0' or provenance['compiler'] != COMPILER or provenance['input_sha256'] != input_sha256 or provenance['launch_sha256'] != expected_sha256 or digest(prompt.encode()) != sha(expected_sha256):
        raise RuntimeBlocked('N05_COMPILED_PROMPT_MISMATCH')
    uuid(provenance['input_artifact_id']); sha(provenance['input_receipt_sha256'])
    for key in ('role_basis','prompt_basis','role_package','prompt_package','profile_package','adapter_artifact'):
        if not isinstance(provenance[key], dict) or not provenance[key]:
            raise RuntimeBlocked('N05_PACKAGE_PINS_REQUIRED')
    document = strict_json(prompt)
    if not isinstance(document, dict) or set(document) != {'schema_version','node_kind','instructions','source_verifier_input','review_content'} or document['schema_version'] != '1.0.0' or document['node_kind'] != 'N05' or not isinstance(document['instructions'], str) or not document['instructions'].strip():
        raise RuntimeBlocked('N05_PROMPT_DOCUMENT_INVALID')
    source = document['source_verifier_input']
    if not isinstance(source, dict) or source.get('node_kind') != 'N05' or source.get('plan_revision') is None or source.get('writer_step_id') == source.get('step_id') or digest(canonical(source)) != input_sha256:
        raise RuntimeBlocked('N05_REGISTERED_INPUT_MISMATCH')
    content, pins = document['review_content'], provenance['review_content']
    pin_fields = {'schema_version','kind','session_id','input_id','input_sha256','input_receipt_sha256','content_sha256','rules_sha256','rule_authorizations_sha256','rule_ids','rule_reads','objects','sources'}
    if not isinstance(content, dict) or set(content) != {'session_id','rules','rule_read_receipts','artifacts','source_chunks'} or not isinstance(pins, dict) or set(pins) != pin_fields or pins['schema_version'] != '1.0.0' or pins['kind'] != 'N05_PHYSICAL_CONTEXT' or pins['session_id'] != content['session_id'] or pins['input_id'] != provenance['input_artifact_id'] or pins['input_sha256'] != input_sha256 or pins['input_receipt_sha256'] != provenance['input_receipt_sha256'] or pins['content_sha256'] != digest(canonical(content)):
        raise RuntimeBlocked('N05_PHYSICAL_CONTENT_MISMATCH')
    uuid(pins['session_id']); sha(pins['rule_authorizations_sha256'])
    rules = content['rules']
    if not isinstance(rules, dict) or set(rules) != {'schema_version','node_kind','revision_id','rules'} or rules['schema_version'] != '1.0.0' or rules['node_kind'] != 'N05' or rules['revision_id'] != source.get('rule_revision') or pins['rules_sha256'] != digest(canonical(rules)) or pins['rules_sha256'] != source.get('rule_manifest_sha256'):
        raise RuntimeBlocked('N05_RULE_MANIFEST_MISMATCH')
    required = source.get('required_rules')
    if not isinstance(required, list) or not 0 < len(required) <= 10000 or not isinstance(rules['rules'], list) or len(rules['rules']) != len(required) or not isinstance(pins['rule_reads'], list) or len(pins['rule_reads']) != len(required) or not isinstance(content['rule_read_receipts'], list) or len(content['rule_read_receipts']) != len(required):
        raise RuntimeBlocked('N05_COMPLETE_RULE_CONTEXT_REQUIRED')
    expected = {uuid(item['version_id']): sha(item['content_sha256']) for item in required}
    if len(expected) != len(required) or pins['rule_ids'] != sorted(expected):
        raise RuntimeBlocked('N05_COMPLETE_RULE_CONTEXT_REQUIRED')
    seen = set(); receipts = set()
    for rule, read, receipt in zip(rules['rules'], pins['rule_reads'], content['rule_read_receipts']):
        if not isinstance(rule, dict) or set(rule) != {'rule_id','sha256','content'} or rule['rule_id'] in seen or expected.get(rule['rule_id']) != rule['sha256'] or not isinstance(rule['content'], dict) or not isinstance(read, dict) or set(read) != {'rule_id','sha256','receipt_id','complete'} or read['rule_id'] != rule['rule_id'] or read['sha256'] != rule['sha256'] or read['complete'] is not True or receipt != {'rule_id':rule['rule_id'],'receipt_id':read['receipt_id']}:
            raise RuntimeBlocked('N05_COMPLETE_RULE_CONTEXT_REQUIRED')
        seen.add(rule['rule_id']); receipts.add(uuid(read['receipt_id']))
    if len(receipts) != len(required):
        raise RuntimeBlocked('N05_DUPLICATE_READ_RECEIPT')
    artifacts, objects, chunks, sources = content['artifacts'], pins['objects'], content['source_chunks'], pins['sources']
    if not isinstance(artifacts, list) or not isinstance(objects, list) or len(artifacts) != 2 or len(objects) != 2 or not isinstance(chunks, list) or not isinstance(sources, list) or not 0 <= len(chunks) == len(sources) <= 512:
        raise RuntimeBlocked('N05_PHYSICAL_CONTENT_BOUND')
    expected_objects = {source.get('candidate_artifact_id'): source.get('candidate_object'), source.get('body_artifact_id'): source.get('body_object')}
    seen = set()
    for artifact, pin in zip(artifacts, objects):
        if not isinstance(artifact, dict) or set(artifact) != {'artifact_id','sha256','payload'} or not isinstance(pin, dict) or set(pin) != {'artifact_id','object_ref'} or artifact['artifact_id'] != pin['artifact_id'] or artifact['artifact_id'] in seen or expected_objects.get(artifact['artifact_id']) != pin['object_ref']:
            raise RuntimeBlocked('N05_TARGET_PINS_REQUIRED')
        seen.add(uuid(artifact['artifact_id']))
        ref = _object_ref(pin['object_ref'], source.get('tenant_id')); raw = canonical(artifact['payload'])
        if ref['media_type'] != 'application/json' or ref['sha256'] != digest(raw) or artifact['sha256'] != ref['sha256'] or ref['size_bytes'] != str(len(raw)):
            raise RuntimeBlocked('N05_TARGET_BYTES_MISMATCH')
    candidate = next(a['payload'] for a in artifacts if a['artifact_id'] == source['candidate_artifact_id'])
    if not isinstance(candidate, dict) or not isinstance(candidate.get('output'), dict) or candidate['output'].get('artifact_kind') != 'SECTION_CANDIDATE' or not isinstance(candidate['output'].get('content'), dict):
        raise RuntimeBlocked('N05_TARGET_BYTES_MISMATCH')
    candidate = candidate['output']['content']
    if {claim['claim_id'] for claim in candidate.get('claim_records', [])} != set(source.get('required_claim_ids', [])):
        raise RuntimeBlocked('N05_COMPLETE_CLAIM_CONTEXT_REQUIRED')
    expected_sources = set()
    for claim in candidate.get('claim_records', []):
        for link in claim.get('evidence_links', []):
            expected_sources.add(canonical(link['evidence_ref']))
    for field in ('field_assignments', 'attachment_bindings'):
        for entry in candidate.get(field, []):
            for evidence in entry.get('evidence_refs', []):
                expected_sources.add(canonical(evidence))
    seen_sources = set()
    for chunk, pin in zip(chunks, sources):
        if not isinstance(chunk, dict) or set(chunk) != {'raw_text','evidence_ref','receipt_id','located','read_status'} or not isinstance(chunk['raw_text'], str) or not isinstance(pin, dict) or set(pin) != {'evidence_ref','receipt_id','access','located','complete','literal_sha256','processing_sha256','original_object','parsed_object'} or chunk['evidence_ref'] != pin['evidence_ref'] or not isinstance(pin['evidence_ref'], dict) or chunk['receipt_id'] != pin['receipt_id'] or pin['access'] is not True or type(pin['located']) is not bool or pin['complete'] is not pin['located'] or chunk['located'] is not pin['located'] or chunk['read_status'] != ('COMPLETE' if pin['located'] else 'LOCATOR_MISMATCH') or (not pin['located'] and chunk['raw_text'] != '') or pin['literal_sha256'] != digest(chunk['raw_text'].encode()):
            raise RuntimeBlocked('N05_COMPLETE_SOURCE_CONTEXT_REQUIRED')
        key = canonical(pin['evidence_ref']); receipt = uuid(pin['receipt_id'])
        if key in seen_sources or receipt in receipts:
            raise RuntimeBlocked('N05_SOURCE_DUPLICATE')
        seen_sources.add(key); receipts.add(receipt); sha(pin['processing_sha256'])
        _object_ref(pin['original_object'], source.get('tenant_id')); _object_ref(pin['parsed_object'], source.get('tenant_id'))

    if seen_sources != expected_sources:
        raise RuntimeBlocked('N05_COMPLETE_SOURCE_CONTEXT_REQUIRED')


def _object_ref(ref, tenant):
    if not isinstance(ref, dict) or set(ref) != {'object_key','object_version','sha256','size_bytes','media_type'} or not isinstance(ref['object_key'], str) or not ref['object_key'].startswith('tenants/' + uuid(tenant) + '/') or '..' in ref['object_key'] or not isinstance(ref['object_version'], str) or not ref['object_version'] or not isinstance(ref['size_bytes'], str) or not ref['size_bytes'].isdigit() or not 0 < int(ref['size_bytes']) <= 67108864 or not isinstance(ref['media_type'], str) or not ref['media_type']:
        raise RuntimeBlocked('N05_OBJECT_PIN_INVALID')
    sha(ref['sha256'])
    return ref
