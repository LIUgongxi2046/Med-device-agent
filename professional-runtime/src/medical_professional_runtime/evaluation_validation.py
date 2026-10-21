"""EVAL-only integrity checks. Authorization stays with the private backend."""
from .host import RuntimeBlocked, digest, strict_json, uuid, sha

ROLES = {'N01': 'Planner', 'N02': 'Researcher', 'N03': 'FieldVerifier', 'N04': 'SectionWriter', 'N05': 'SourceVerifier', 'N06': 'Assembler', 'N07': 'SourceVerifier'}


def require_prompt(prompt, expected, provenance, input_sha256):
    fields = {'schema_version','compiler','node_kind','input_artifact_id','input_sha256','launch_sha256','original_compiler'}
    if not isinstance(prompt, str) or not isinstance(provenance, dict) or set(provenance) != fields or provenance['schema_version'] != '1.0.0' or provenance['compiler'] != 'EVALUATION_REGISTERED_EXACT_INPUT_V1' or provenance['node_kind'] not in ROLES:
        raise RuntimeBlocked('EVALUATION_EXACT_INPUT_REQUIRED')
    if digest(prompt.encode()) != sha(expected) or expected != input_sha256 or provenance['input_sha256'] != expected or provenance['launch_sha256'] != expected:
        raise RuntimeBlocked('EVALUATION_EXACT_INPUT_MISMATCH')
    uuid(provenance['input_artifact_id'])
    exact = strict_json(prompt)
    if not isinstance(exact, dict) or set(exact) != {'messages','tools'} or not isinstance(exact['messages'], list) or not isinstance(exact['tools'], list):
        raise RuntimeBlocked('EVALUATION_EXACT_INPUT_INVALID')
    user = False
    for message in exact['messages']:
        if not isinstance(message, dict) or set(message) != {'role','content'} or user or message['role'] not in ('system','user') or not isinstance(message['content'], str):
            raise RuntimeBlocked('EVALUATION_MESSAGE_ORDER_INVALID')
        user = message['role'] == 'user'
    if not user: raise RuntimeBlocked('EVALUATION_USER_MESSAGE_REQUIRED')
    for tool in exact['tools']:
        if not isinstance(tool, dict) or set(tool) != {'type','function'} or tool['type'] != 'function' or not isinstance(tool['function'], dict): raise RuntimeBlocked('EVALUATION_TOOLS_INVALID')
    return exact


def validate_launch(binding, launch):
    fields = {'state','purpose','job_type','session_id','node_kind','role','runtime_manifest_sha256','deployment_sha256','job_directory','prompt','prompt_sha256','binding','prompt_provenance','sdk','profile_files','expected_inventory','role_version','profile_artifact'}
    if not isinstance(launch, dict) or set(launch) != fields or launch['binding'] != binding.wire() or launch['state'] != 'PREPARE' or launch['purpose'] != 'EVALUATION_VERIFICATION' or launch['job_type'] != 'EVALUATION_VERIFICATION' or ROLES.get(launch['node_kind']) != launch['role']:
        raise RuntimeBlocked('EVALUATION_LAUNCH_MISMATCH')
    uuid(launch['session_id']); sha(launch['deployment_sha256']); sha(launch['runtime_manifest_sha256'])
    exact = require_prompt(launch['prompt'], launch['prompt_sha256'], launch['prompt_provenance'], binding.input_sha256)
    if launch['prompt_provenance']['node_kind'] != launch['node_kind']: raise RuntimeBlocked('EVALUATION_NODE_MISMATCH')
    sdk = launch['sdk']
    if set(sdk) != {'model','reasoning_effort','max_tokens','initialize_timeout_seconds','request_timeout_seconds','shutdown_timeout_seconds','dsh_bin','sdk_version','runtime_version'} or not sdk['model'] or sdk['model'] == 'SIMULATION' or sdk['sdk_version'] != sdk['runtime_version'] or sdk['sdk_version'] == '0.0.0.dev0': raise RuntimeBlocked('EVALUATION_RUNTIME_UNAPPROVED')
    for key in ('max_tokens','initialize_timeout_seconds','request_timeout_seconds','shutdown_timeout_seconds'):
        if type(sdk[key]) not in (int,float) or not 0 < sdk[key] < float('inf'): raise RuntimeBlocked('EVALUATION_RUNTIME_LIMIT')
    inventory = launch['expected_inventory']
    if not inventory['plugin_names'] or not inventory['models'] or inventory['providers'] != [{'id':'enterprise-model','name':'企业模型接口'}] or not launch['profile_files']: raise RuntimeBlocked('EVALUATION_INVENTORY_REQUIRED')
    from .supervisor import canonical
    tools = {tool['function']['name']: digest(canonical(tool['function']['parameters'])) for tool in exact['tools']}
    if len(tools) != len(exact['tools']) or tools != inventory['tool_schema_hashes']: raise RuntimeBlocked('EVALUATION_TOOL_INVENTORY_MISMATCH')
