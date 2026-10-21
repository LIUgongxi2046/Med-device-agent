"""N01 integrity only. Authority is the authenticated N01 prepared-ledger endpoint.

This module cannot turn a TASK envelope or a caller purpose label into PLAN_DRAFT.
"""
def require_prompt(prompt, expected_sha256, provenance, input_sha256):
    from .host import RuntimeBlocked, digest, sha, uuid, strict_json
    fields={'schema_version','compiler','input_artifact_id','input_sha256','input_receipt_sha256','launch_sha256','role_basis','prompt_basis','role_package','prompt_package','profile_package','adapter_artifact'}
    if not isinstance(prompt,str) or not prompt or not isinstance(provenance,dict) or set(provenance)!=fields:raise RuntimeBlocked('N01_COMPILED_PROMPT_REQUIRED')
    if provenance['schema_version']!='1.0.0' or provenance['compiler']!='N01_LITERAL_INSTRUCTIONS_AND_REGISTERED_INPUT_V1' or provenance['input_sha256']!=input_sha256 or provenance['launch_sha256']!=expected_sha256 or digest(prompt.encode())!=sha(expected_sha256):raise RuntimeBlocked('N01_COMPILED_PROMPT_MISMATCH')
    uuid(provenance['input_artifact_id']);sha(provenance['input_receipt_sha256'])
    for key in ('role_basis','prompt_basis','role_package','prompt_package','profile_package','adapter_artifact'):
        if not isinstance(provenance[key],dict) or not provenance[key]:raise RuntimeBlocked('N01_PACKAGE_PINS_REQUIRED')
    document=strict_json(prompt.encode())
    if not isinstance(document,dict) or set(document)!={'schema_version','node_kind','instructions','plan_draft_input'} or document['schema_version']!='1.0.0' or document['node_kind']!='N01' or not isinstance(document['instructions'],str) or not document['instructions'].strip():raise RuntimeBlocked('N01_PROMPT_DOCUMENT_INVALID')
    source=document['plan_draft_input']
    if not isinstance(source,dict) or source.get('node_kind')!='N01' or source.get('execution_kind')!='SYSTEM_JOB' or source.get('job_type')!='PLAN_DRAFT' or 'plan_revision' not in source or source['plan_revision'] is not None:raise RuntimeBlocked('N01_INPUT_IDENTITY_INVALID')
    from .supervisor import canonical
    if digest(canonical(source))!=input_sha256:raise RuntimeBlocked('N01_REGISTERED_INPUT_MISMATCH')
