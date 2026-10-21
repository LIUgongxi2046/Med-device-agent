"""Dedicated EVAL runner; pinned EVAL SDK adapter projects exact input and real history."""
import importlib.metadata
import json
import os
import sys

def main():
    os.umask(0o077)
    raw = sys.stdin.buffer.read(16 * 1024 * 1024 + 1)
    if len(raw) > 16 * 1024 * 1024: raise ValueError('INPUT_LIMIT')
    spec = json.loads(raw)
    import hashlib
    provenance = spec['prompt_provenance']
    if provenance.get('compiler') != 'EVALUATION_REGISTERED_EXACT_INPUT_V1' or hashlib.sha256(spec['prompt'].encode()).hexdigest() != spec['prompt_sha256'] or spec['binding']['input_sha256'] != spec['prompt_sha256']:
        raise ValueError('EVALUATION_EXACT_INPUT_MISMATCH')
    exact = json.loads(spec['prompt'])
    if set(exact) != {'messages', 'tools'}: raise ValueError('EVALUATION_EXACT_INPUT_REQUIRED')
    # Match paired installed wheels before any SDK process or model call.
    for name, key in [('deepseek-harness-sdk','sdk_version'), ('deepseek-harness-runtime-bin','runtime_version')]:
        if importlib.metadata.version(name) != spec[key]: raise ValueError('RUNTIME_VERSION_MISMATCH')
    from deepseek_harness import DeepSeekHarness
    forbidden = False
    def observe(notification):
        nonlocal forbidden
        if notification.method in ('subagent.started', 'subagent.finished'):
            forbidden = True
            raise ValueError('SUBAGENT_FORBIDDEN')
    with DeepSeekHarness(provider='enterprise-model', model=spec['model'], reasoning_effort=spec['reasoning_effort'], max_tokens=spec['max_tokens'], cwd=spec['workspace'], runtime_cwd=spec['workspace'], dsh_home=spec['home'], dsh_bin=spec['dsh_bin'], profile='medical-agent', initialize_timeout_seconds=spec['initialize_timeout_seconds'], request_timeout_seconds=spec['request_timeout_seconds'], shutdown_timeout_seconds=spec['shutdown_timeout_seconds']) as harness:
        # Initialization has created the actual SDK/runtime before the independent
        # supervisor inspects it. This byte is only a readiness hint, never a proof.
        ready = int(os.environ['MEDICAL_READY_FD']); gate = int(os.environ['MEDICAL_ADMISSION_FD'])
        os.write(ready, b'SDK_READY\n'); os.close(ready)
        if os.read(gate, 1) != b'1': raise ValueError('SUPERVISOR_ADMISSION_REQUIRED')
        os.close(gate)
        result = harness.run(spec['prompt'], session_id=spec['session_id'], on_notification=observe)
        if forbidden or result.session_id != spec['session_id']: raise ValueError('SESSION_MISMATCH')
    # Context-manager exit waits for the actual SDK process. Never copy arbitrary
    # notifications, chain-of-thought, errors, credentials or model usage assertions.
    sys.stdout.write(json.dumps({'session_id':result.session_id, 'binding':spec['binding'], 'finish_reason':result.finish_reason, 'final_response':result.final_response}, ensure_ascii=False, separators=(',',':')))

if __name__ == '__main__':
    try: main()
    except BaseException:
        # Sensitive SDK diagnostics are retained by approved job storage, not leaked
        # into activity error messages or Temporal history.
        sys.exit(1)
