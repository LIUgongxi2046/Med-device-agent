"""N00 technical input check. It does not grant authority or complete a TASK.

The host supplies already authenticated, version-pinned bytes and performs current
lease/permission checks before and after this function. No model/network execution.
"""
from __future__ import annotations
import hashlib
import json
import math
from datetime import datetime, timezone
from pathlib import Path
from jsonschema import Draft202012Validator, FormatChecker

_SCHEMA = json.loads((Path(__file__).resolve().with_name('runtime-step.schema.json')).read_bytes())

def strict_json(raw: bytes, maximum_bytes: int):
    if not isinstance(raw, bytes) or not 0 < len(raw) <= maximum_bytes:
        raise ValueError('CONTENT_LIMIT')
    def pairs(items):
        result = {}
        for key, value in items:
            if key in result: raise ValueError('DUPLICATE_JSON_KEY')
            result[key] = value
        return result
    def number(value):
        result = int(value)
        if abs(result) > 9007199254740991: raise ValueError('UNSAFE_INTEGER')
        return result
    def decimal(value):
        result=float(value)
        if not math.isfinite(result) or abs(result)>9007199254740991: raise ValueError('UNSAFE_NUMBER')
        return result
    return json.loads(raw.decode('utf-8', errors='strict'), object_pairs_hook=pairs,
                      parse_int=number, parse_float=decimal, parse_constant=lambda _: (_ for _ in ()).throw(ValueError('NONFINITE_NUMBER')))


def validate_envelope(raw: bytes, maximum_bytes: int):
    envelope = strict_json(raw, maximum_bytes)
    validator = Draft202012Validator({**_SCHEMA, '$ref': '#/$defs/StepEnvelope'}, format_checker=FormatChecker())
    validator.validate(envelope)
    if (envelope['input']['artifact_id'] != envelope['input_manifest_id']
            or envelope['input']['sha256'] != envelope['input_manifest_sha256']):
        raise ValueError('CONTENT_MISMATCH')
    return envelope


def check_input(envelope_bytes: bytes, envelope_sha256: str, input_bytes: bytes,
                published_validator: Draft202012Validator, published_schema_ref: str,
                runtime_manifest_sha256: str, now: datetime, maximum_bytes: int):
    """Raw object SHA is never replaced with a reserialized/canonical hash.

    published_validator is selected from the host's fixed published catalogue,
    never built from caller JSON or a remotely resolved schema reference.
    """
    if hashlib.sha256(envelope_bytes).hexdigest() != envelope_sha256: raise ValueError('CONTENT_MISMATCH')
    envelope = validate_envelope(envelope_bytes, maximum_bytes)
    if now.tzinfo is None or now.utcoffset() is None: raise ValueError('UTC_CLOCK_REQUIRED')
    if now.astimezone(timezone.utc) >= datetime.fromisoformat(envelope['deadline_at'].replace('Z', '+00:00')):
        raise ValueError('DEADLINE_EXCEEDED')
    if (envelope['input']['schema_ref'] != published_schema_ref
            or envelope['runtime_manifest_sha256'] != runtime_manifest_sha256
            or hashlib.sha256(input_bytes).hexdigest() != envelope['input_manifest_sha256']):
        raise ValueError('CONTENT_MISMATCH')
    value = strict_json(input_bytes, maximum_bytes)
    published_validator.validate(value)
    for name in ('tenant_id','project_id','initiator_id','principal_id','task_id','task_revision','run_id',
                 'plan_revision','snapshot_id','budget_revision','agent_release_id'):
        if value.get(name) != envelope[name]: raise ValueError('INPUT_OWNER_MISMATCH')
    return {'schema_version':'1.0.0','run_id':envelope['run_id'],'step_id':envelope['step_id'],
            'input_manifest_sha256':envelope['input_manifest_sha256'],
            'lease_generation':envelope['lease_generation'],'outcome':'SUCCEEDED',
            'artifact_ids':[],'candidate_ids':[],'invocation_ids':[],'missing_requirements':[],
            'error_code':None,'technical_only':True,'node_kind':'N00_INPUT_INTEGRITY',
            'checked_input_sha256':hashlib.sha256(input_bytes).hexdigest(),
            'runtime_manifest_sha256':runtime_manifest_sha256}
