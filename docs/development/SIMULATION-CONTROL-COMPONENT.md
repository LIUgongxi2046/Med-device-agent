# SIMULATION control policy component

Status: local source component, not persisted C07 admission or a public/internal API.

This slice implements deterministic measurement and immutable transition proposals in
`model-gateway/src/medical_model_gateway/simulation_control.py`. It does not change
`simulation_execution.py`, the published base catalogue, configuration status,
RunTokenAuthority, C06, CP-02, RN-01, SQL, grants, or quota approvals. It never starts
a provider, database, server, socket, or workflow.

## Measurement contract

The unit is **CANONICAL_UTF8_BYTE**. Input is the complete validated `ExactInput`;
output is the complete validated `ModelResult`, including its normal identity and
usage fields. Measurement serializes the value using RFC8785, encodes UTF-8, and
counts those bytes. It adds no newline or BOM and performs no Unicode normalization.
This is bounded local data processing, not a tokenizer for any paid model.

Each measurement records its content hash and a counter identity containing:

- `LOCAL_SIMULATION_CANONICAL_UTF8_BYTE_V1` revision;
- SHA-256 of the actual `simulation_control.py` source bytes;
- SHA-256 of the RFC8785 canonical measurement schema, including the projection
  schemas and the algorithm description.

The source must remain readable in an installed wheel. Changing the source or
measurement schema causes existing snapshots to fail closed. Future persistence
must retain the original implementation/contract or introduce an explicit migration;
it must not silently recalculate historical measurements with newer code.

`maximum_input_units` and `maximum_output_units` are explicit, finite positive
integers. Neither `context_window`, `tokens_per_minute`, `max_output_tokens`, nor
provider usage is interpreted as a byte count. Arithmetic is bounded to exact JSON
integers, at most 2^53−1. Canonical result bytes exceeding the output cap are rejected
without consuming or releasing the existing reservation.

Provider usage remains in `fixture_usage` with origin `SIMULATION_FIXTURE`, or null
with origin `UNKNOWN`. Null token fields remain null. Known zero provider charge has
its own `LOCAL_SIMULATION_NO_PROVIDER_REQUEST` basis; it does not establish measured
provider tokens or a financial settlement receipt.

## Pure reservation and state transitions

`SimulationSnapshot` contains a parent-run identity, two distinct quota-account
snapshots and immutable invocation records. Account totals may include other runs.
These are caller-supplied accounting inputs, not approved quotas or authenticated
facts. The persistent adapter must validate their ownership, grants and revisions.

`reserve` requires the actual validated ExactInput, recomputes its canonical
measurement, and rejects any mismatched declared count/hash. It proposes reservation
of one call plus exact input units and the maximum
output units against both the enterprise and parent account. Failure in either
account returns no modified snapshot. Identical action bindings replay the original
state; differing bindings conflict. Unknown or completed actions never obtain a
new reservation by replaying the same identity.

The core lifecycle is:

- RESERVED → DISPATCHING: `begin_send` proposes the only dispatch generation, 1.
- DISPATCHING → IN_FLIGHT: an observation only, never a second send decision.
- DISPATCHING/IN_FLIGHT → UNKNOWN: preserve the entire reservation; no redispatch.
- DISPATCHING/IN_FLIGHT/UNKNOWN/CANCEL_REQUESTED → SUCCEEDED or FAILED: exact fixed
  local result and provenance evidence reconcile the original invocation.
- RESERVED → CANCELLED: a never-dispatched reservation can be released.
- After dispatch, cancellation only records CANCEL_REQUESTED. It does not prove
  process termination or release quota. A late original result remains recordable.

`record_result` requires canonical result/provenance bytes, correct shared result
schema, original invocation/context/configuration identities, matching input,
parameters and local-request hashes, and the original receipt. It reconstructs the
fixed SimulationCase from the supplied result and checks the case hash, rather than
accepting a changed result with an updated sidecar. An `aborted` fixture cannot prove
cancellation. Source receipt usage is preserved without reinterpretation.

On a valid result the proposal consumes exact input + result byte units, releases
the unused output allowance and consumes one local call on both accounts. Identical
late receipt replay does not charge twice. Conflicting evidence is rejected. This
does not settle a real provider bill or create an artifact-registry receipt.

## Exactly-once boundary: mandatory caller transaction

Every transition carries `expected_snapshot_sha256`, `next_snapshot` and
`requires_atomic_commit=true`. `dispatch_candidate=true` means only that a proposed
RESERVED → DISPATCHING transition would be eligible after a successful commit.

**A pure function cannot grant exactly-once execution.** Calling `begin_send` twice
with the same old snapshot produces the same proposal. The repository must:

1. Authenticate the explicit simulation execution purpose and revalidate current
   input/configuration/dependency permission before dispatch.
2. Lock or CAS both authoritative account rows and the invocation/action identity,
   validating their complete expected values in one transaction.
3. Persist the winning transition and associated audit/outbox facts atomically.
4. Give execution permission only to that transaction's winner, never merely to a
   reader of an already committed DISPATCHING state.

The snapshot hash is an expected-state fingerprint, not a signature or an authority
token. A fabricated transition object is not trusted. Shared enterprise accounting
must be serialized across different parent runs. The test CAS harness demonstrates
one winning proposal; it is not a database or process-concurrency implementation.

A lost commit response is ambiguous. The caller must reread and reconcile; it must
not infer a right to execute from DISPATCHING, IN_FLIGHT or UNKNOWN. The component
provides no age-based reset, quota release, generation rollover or retry path for
such records. A later run must not create a new action to disguise retry of an
unknown origin; origin/retry authorization remains a persistence responsibility.

## Evidence and integration gaps

The component validates bytes supplied by its caller. It does not establish that
those bytes were durably committed, came from an authenticated worker, remain
readable under current permissions, or were registered as backend artifacts.
`LOCAL_COMPONENT` provenance is preserved and never upgraded into backend proof.

Still required for persisted C07 and IA-06/IN-08–11:

- Explicit authenticated evaluation job creation and scoped permission evidence;
- mode-safe execution/context/invocation schema migrations and immutable binding;
- authorized finite simulation quota configuration and atomic shared-account CAS;
- trusted lease/service identity and durable once-only dispatch ownership;
- backend artifact/object persistence, complete reread, and receipt/operation/audit
  transactions before any successful C07 finish;
- result/status APIs, source/dependency revalidation, restricted read/replay, and
  independent late-receipt ingestion after revocation;
- original-action recovery, process-termination evidence and retention policy;
- actual database/process integration and concurrency/crash tests.

Production RunTokenAuthority's PUBLISHED ROLE requirement remains unchanged. This
module cannot create grants, SYSTEM_JOB approval, a PUBLISHED release, a real
connection verification, or production eligibility.

## Standalone schemas and tests

`agent-design/simulation-byte-measurement.schema.json` and
`agent-design/simulation-control-component.schema.json` are standalone structural
schemas. They are deliberately not registered in the frozen production catalogue.
Python domain validation additionally enforces cross-field state, hash, cap and
accounting invariants; JSON Schema validation alone is insufficient.

Focused verification:

```sh
PYTHONPATH=model-gateway/src:contracts/python/src \
  .implementation-work/python-venv/bin/python -m unittest discover \
  -s tests -p test_simulation_control.py -v
```

Tests cover real local SimulationExecutor receipt bytes, canonical Unicode byte
measurement, counter digests, strict integer/cap/overflow rejection, dual-account
reservation, action idempotency, the mandatory CAS boundary, unknown/cancel recovery,
late receipt deduplication, fixed-case integrity, nullable fixture usage and
standalone schema parity. They do not constitute persisted C07 or production acceptance.

Verified on 2026-10-18:

- 86 tests passed across the existing gateway, SimulationProvider, local executor
  and the new 32-test policy suite.
- The built wheel passed all 32 policy tests under Python `-I`. Its installed
  source bytes, counter implementation digest and generated standalone schemas
  matched the reviewed source/exported schemas.
- Independent review found and verified the fix for understated input measurement:
  reservation now requires and measures actual ExactInput. Send/cancel and
  receipt/cancel CAS race orders, lost commit acknowledgement, held UNKNOWN quota
  and aggregate integer overflow have permanent regression tests.

Installed-wheel verification uses the new read-only entry point:

```sh
.implementation-work/python-venv/bin/python -I \
  tests/inspect_simulation_control_package.py PATH_TO_INSTALLED_WHEEL_DIRECTORY
```
