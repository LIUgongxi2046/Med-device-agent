# Physical EVAL runtime objects (147)

`FormalEvaluationRuntimeObjects` is the dedicated producer for STEP_ENVELOPE, independently reread SESSION_EXPORT and checked STEP_RESULT. It does not use TASK 075 object journals or permit a caller to submit trusted bytes.

- Envelope bytes come only from the runtime's private production-ready Envelope.
- Export bytes come only from independent supervisor Closed proof, with successful owned process exit, exact session/binding and current lease revalidation.
- Result bytes and result roots come only from the private NodeOutcomes.Checked proof, not host FINISH or caller-provided status.
- A durable immutable intent fixes the original payload, schema digest, exact provenance, session, lease, full source-backed content ancestry and object key. A durable attempt precedes any object I/O.
- `RuntimeObjectTransport.transferVerified` runs outside SQL transactions. Each guard must reconstruct current EVAL admission, lease and original private proof. A repeated attempt uses recovery-only on the same key and cannot create a replacement send.
- Registration requires the original private Attempt and actual VerifiedTransfer identity, checks exact bytes/version, persists version/artifact/content-binding/complete dependency manifests and lineage plus audit/outbox, and rereads originals. Metadata-only receipts cannot substitute for physical transfer proof.
- Original reads verify fixed provenance, schemas, payload digests, registered object identity, manifest/lineage, real audit/outbox and original attempt. They expose stored metadata only; 144 execution evidence still performs full original object GET before scoring/acceptance.
- Generic artifact reads are gated by immutable 147 ownership, even after relabeling. A dedicated EVAL schema without its genuine original registration is rejected. Valid registered originals may be read under EVALUATION_RESULTS with the normal source/right/resource and artifact authorization chain.
- Three 147 tables have real run-step/session-preparation/supervisor/artifact/version/attempt/audit/event foreign keys, reserved metadata, uniqueness and update/delete rejection. No placeholder object, schema approval, completion or score history is inserted.

Configuration uses the existing opt-in `medical.formal-evaluation-execution.enabled` plus an explicit private `object-directory` for the bounded physical transport. This directory must already have the required private filesystem permissions; code does not silently create or weaken it.

Tests, service runs, object store calls and migration execution are deferred per user instruction. The later verification pass must check original-key recovery, concurrent intent/attempt identity, partial/unknown physical write, revoked source rights, cross-tenant/cross-cell output substitution, mismatched session closure, forged generic upload, relocated schema labels, altered payload/object version, corrupted manifests/audit/outbox, and registration followed by real original GET.

## Independent business output registration

`prepareOutput(Checked)` writes a distinct `BUSINESS_OUTPUT` original from the private checker’s normalized final output. Its schema reference and digest come from the current approved role and are validated against the published catalogue, not from a caller. The same sealed attempt, guarded physical transfer and immutable registration path is used. A model-result object or an embedded result document is never its replacement.

Order: register session export, obtain Checked, register BUSINESS_OUTPUT, then register STEP_RESULT. The latter requires that exact business original, adds `output_artifact`, and includes it as a content lineage root. Original reads recheck the exact output bytes, schema, closed session and artifact reference. The existing configured EVALUATION_RESULTS profile must authorize the published business schema for the EVALUATION owner; missing profiles remain fail-closed. This does not invent an N02 or other node checker: unsupported node outcomes remain unavailable until their actual checker is implemented.
