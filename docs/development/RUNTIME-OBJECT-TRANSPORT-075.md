# Deployment object transport bridge

Use `new RuntimeObjectTransport(SourceUploadTransport, private0700Directory, maximumBytes)` from the actual deployment configuration. This uses the existing approved object store configuration and tenant/user concurrency permits. No extra credentials, grants, endpoint configuration, or SOURCE upload impersonation are introduced.

Apply only `source-runtime-object-transport.patch` to the shared SourceUploadTransport. The complete staged copy under d/java exists for isolated compilation; do not overwrite shared source with it. Existing writeSourceJob still admits only tenants/{tenant}/source-parsed/.

Both permitted RuntimeObjectAttempt implementations are private-constructor immutable ledger proofs. The D intent carries verified tenant, actor, and run; the E attempt carries verified tenant/actor/invocation/dispatch identity. Each computes its fixed object key from original IDs, rather than accepting a new key. The transport checks exact bytes/hash/size/media, rejects transactions across I/O, consumes initial-write mode once, and uses only recovery after an unknown initial send. The permit guard and original fresh domain guard run before and after actual transfer. Receipt registration remains in a separate freshly authenticated transaction.

Tests use actual adapter and actual TransferConcurrency.Lease.guard logic with mocked permit acquisition and S3 writer construction. They cover original SOURCE prefix positive/negative, scoped runtime routing, unknown-send recovery-only, wrong fixed key/hash, revocation before acquisition and after object readback, writer/permit closure, and private-file cleanup. These are deterministic boundary tests, not live Redis/S3 acceptance.

The E ledger changes in this increment are only the immutable tenant/actor fields, getters, expected key, and passing the already verified outcome identity into the private constructor.
