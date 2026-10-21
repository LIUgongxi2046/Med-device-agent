# EVAL per-call exact input producer (149)

Implementation-only delivery; tests, database migrations, provider calls and runtime verification are deferred.

`FormalEvaluationInvocationObjects` accepts only `FormalEvaluationInvocationService.PreparedContent`, whose constructor is private to the invocation service. The service must mint it from current EVAL session authority, an independently observed actual SDK request and verified immutable ordered model/tool history. No caller byte-array write entry exists. This producer is independent of TASK and document-review authority.

Each call has a distinct immutable intent keyed by run, session, observed request identifier, ordinal and projection hash. Database uniqueness independently prevents reusing an ordinal or request with different content. The stored transcript cannot reuse the initial 089 artifact when later messages exist. Schema-validated canonical bytes and full provenance are compared on every resume.

The intent/attempt transaction precedes external transfer. `RuntimeObjectTransport.transferVerified` provides a single conditional PUT and complete original readback/hash verification. Any persisted attempt, repeated in-memory transfer, or explicit `recover` is original-object GET only. Recovery never creates missing intent or attempt, never refreshes a key, and never resends UNKNOWN bytes. The service must commit intent/attempt before physical I/O and reacquire current PreparedContent in each transaction.

Registration requires the sealed attempt and its exact `VerifiedTransfer` identity, then writes actual artifact metadata, dependency access manifest/members, source lineage and immutable registration/audit/outbox evidence. Registered reads revalidate these records against the original canonical bytes. PROJECT resource ownership and dedicated schema/kind distinguish EVAL from simulation and TASK inputs. Generic ArtifactRegistry reads deny the dedicated owner even if kind/schema metadata is relabelled; intended consumers use the current EVAL invocation service.

149 adds immutable intent/attempt/registration tables with EVAL job/node/session ownership, per-call uniqueness, actor foreign keys, mandatory classification/schema metadata and update/delete rejection triggers. It does not create provider results, tool receipts, billing evidence, or grant model-send permissions.
