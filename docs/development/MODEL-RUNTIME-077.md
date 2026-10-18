# 077 model runtime eligibility and connection preparation (staging)

Status: independent schema/credential/SQL pieces are implemented; full producer integration is awaiting the real E076 FormalInvocationService source for compilation and joint runtime tests. Do not publish or claim actual provider connectivity from this document. No secrets, grants, deployed properties or paid requests were created.

## Actual HTTP surface

Both model-runtime and formal-invocation feature flags must be true for the configuration/service/controller. Authenticated GET `/api/model-runtime-availability` exists independently and reports actual ModelRuntimeController bean presence in `ModelRuntimeAvailability`, with current_context and model_runtime_enabled; response revision is current context. No grant is inferred.

Normal responses use schema_version1.0.0 envelope revision1. Schema/context/CSRF headers retain existing protection; Idempotency-Key is printable ASCII16–128. All creating controllers authenticate before reading bounded64KiB request bytes. No query is accepted. Explicit protocol error handlers preserve medical envelopes.

- POST `/api/model-connection-verifications` HTTP201: exact E `formal-invocation.schema.json#/$defs/VerificationRequest`. Immutable original intent is committed first, then actual E startVerification runs outside the tenant transaction for object/tokenizer I/O, then actual E run/step/action is bound. No provider send occurs in this B request. Returns ConnectionStarted or the existing exact original result.
- GET `/api/model-connection-verification-operations`: original key header, no body, readonly ConnectionResult. NOT_FOUND means no original intent; UNKNOWN means original intent exists without the required persisted E/B binding. Neither permits replacing a key or inventing invocation success. The endpoint does not bind, prepare, dispatch or complete anything.
- POST `/api/model-connection-verification-completions`: original key header, no body, actual private E SettledProof required. It verifies invocation/usage/receipt artifact/price/currency/settled amount and creates a PASSED receipt only after actual ledger evidence exists.
- POST `/api/model-eligibilities` HTTP201: exact ModelEnableRequest. Requires model.enable/MODEL_ENABLEMENT on actual CONFIGURATION owner scope, current passed verification, matching actual pricing version, and current approved data-policy Source evidence. Registers the real MODEL_ELIGIBILITY fixed version and independent operation/audit/outbox.
- GET `/api/model-eligibility-operations`: readonly original key, ModelEnableRecovery FOUND|NOT_FOUND.

Runtime governance schema: `urn:medical:runtime-governance:1.0.0`; current resource is b/resources/runtime-governance.schema.json. ModelEligibilityBinding is the strict safe replay type, excluding auth_ref, secret paths and key material. Retention uses the independent runtime-retention URI, not old unused retention definitions in this file.

## Current transport limitations and real inputs

E first executable provider is DeepSeek with explicit request_parameters.reasoning_effort='none'. Other provider/reasoning modes remain rejected; raw internal reasoning is never retained. E requires a real project plus fixed scope snapshot, real externally permitted MODEL_SEND Source members, explicit request.business_purpose DOCUMENT_BODY or MODEL_VERIFICATION, fixed input/role/prompt/rules/tokenizer/budgets and actual token counting. No empty rules or fabricated token estimates.

007 ownership invariant: connection_verification must have the model configuration's real owner, and its receipt_artifact FK requires that same owner. Project-owned E receipts cannot be attached to enterprise-owned configurations. E confirmed its receipts are project-owned. Both B and E reject enterprise or wrong-project model configurations before intent/job creation; first slice supports only the actual PROJECT configuration in the verification project. Availability explicitly reports PROJECT/DEEPSEEK/none limits. No old migration or owner bypass is permitted.

Mandatory non-secret deployment input keys are medical.model-runtime.approved-credential-reference-file and medical.model-runtime.maximum-connection-age-seconds, behind both medical.model-runtime.enabled and medical.formal-invocation.enabled. The reference file contains only approved reference metadata; secure provider key bytes are resolved separately behind actual E transport.

ConfiguredCredentialDirectory reads a bounded, exact closed deployment manifest with actual tenant/reference/revision/provider/endpoint/approval identifiers, no secret values. It enforces owner-only regular-file path and reread identity constraints. Python ApprovedCredentialResolver consumes only trusted deployment bindings and current-check callback; each acquisition reuses existing read_key secure reader and real DeepSeekProvider, with current checks both before and after read, zero automatic retries, no proxy environment or redirects. D worker code receives no key/path/provider factory.

## Durable evidence and consumers

B intent records real model/config/credential revision, entire E original request/hash, actor/key/operation and exact model.verify grant. Its entire normalized immutable content is independently SHA-anchored by requested audit/outbox before the transaction commits. E consumes a private ConnectionVerificationIntentProof and rechecks it after real input I/O. B start binding refers only to actually persisted E run/step/action; invocation_id remains null until real lease/reserve creates an invocation. No made-up lease generation or invocation ID.

PASSED stores actual receipt artifact and full completed sidecar; audit/outbox independently SHA-anchor the complete completion metadata. Current consumer checks the real E ledger again, exact model/credential/pricing/receipt and bounded age from the immutable dispatch admitted_at timestamp exposed by E verifiedAt (a conservative freshness anchor, not a claimed provider completion timestamp). Neither delayed receipt nor B completion refreshes this age. E settledAt is the actual settlement insertion time and must fall after dispatch admission and before B completion. Existing connection_verification.verified_at stores that conservative anchor in this slice; the actual provider completion time is unknown. It does not accept caller-declared successful JSON.

ModelEligibilityAuthority exposes a privately constructed Verified proof and requireUnchanged; parameters are checked against the actual fixed model's request_parameter_schema. safeReplayBinding has only approved public business metadata. dependencyVersions contains actual registered model, price and data-policy versions only; credentials and connection IDs are never inserted as fake version_registry lineage.

The current model eligibility author creates one immutable enabled decision per model version. There is no renew/disable author in this slice. Revocation/current credential/connection expiry prevents use; a new model version and real approval is required for fresh eligibility. Do not claim this is a complete ongoing model governance UI.

## Verification achieved so far

- 3 ApprovedCredentialAuthority Java tests on actual authority/directory with metadata-only temporary files.
- 5 Python resolver tests with mocked secure-read boundary and actual transport inheritance; no provider/key I/O.
- 3 actual E VerificationRequest/local response schema tests.
- 1 actual MyBatis/XML/JSqlParser test covering27 statements, bounded SELECT/timeouts, INSERT arity, original operation subject_id and config/credential/creator CAS.
- Full connection/eligibility tests await real E service. No stub is substituted, and no Maven/global build was run.

## 2026-10-18 actual integration checkpoint

All B Java sources, the narrow ModelConfigurationService patch, and the actual A AgentExecutionPolicyModelAdapter compile under JDK 17 against the actual E FormalInvocationService and D worker classes. No placeholder model proof or service was used. E has not yet frozen its complete outcome/reconciliation path; compilation is not a live connection acceptance result.

ConnectionVerificationLedgerTest adds 10 distinct JUnit tests (also run by its standalone runner) against the actual B service and private ledger verifier with persistence/access boundary doubles. They cover whole-intent audit/outbox anchors, altered private grant, missing event, original request mutation, fake PENDING receipt/time, RUNNING without binding, COMPLETED without completion, read-only NOT_FOUND without starting E, changed context before lookup, and equivalent database JSON/time formatting. Witness normalization uses actual snake_case serialized fields. Standalone output: tests/connection-ledger-results.txt. No database/provider/credential deployment was exercised.
