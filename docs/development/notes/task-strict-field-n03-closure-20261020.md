# A5 N03 / AT-05 code handoff

Status: CODE_COMPLETE; tests and integrated acceptance intentionally deferred. No provider, permission, approval, storage credentials, or production execution is granted by these files.

## Exact execution path

`TaskStrictFieldExecutionBridge.execute(http, reference, "N03", commandId)` first reads the original command. An existing reservation, even an unclaimed/partially completed one, is query-only. A new command materializes the unique currently approved target, commits a private reservation, claims through `RunTokenAuthority.claimStrictFieldReserved`, atomically binds the actual TOOL action, issues the existing real token, and executes `TaskStrictFieldNode`. There is no model/C07 placeholder, professional host, or second lease generation on retry.

`recover` and `originalCommands` only read genuine immutable command/results. They never call claim, issue, object transfer, source production, or execute. An uncertain partial outcome remains pending rather than becoming success or authorizing a replacement command.

N03 input is its own closed schema. It remains distinct from N02 inputs and IA-04 DOCUMENT/CANDIDATE checks. Input binds the fixed run/graph/plan/template/snapshot, real registered STRICT_FIELD_TARGET, actual original input approval, approved policy versions, source contexts, original source selections, tool release, and predecessor receipts. Zero or multiple approved fixed-scope targets/policies/contexts are refused; latest-row guessing is prohibited.

## Typed author path and method coverage

The original096 TEXT producer and human restrictions remain. The separate104 family supports TEXT and exact DECIMAL candidate/source values, each backed by real SourceExact or A4 numeric evidence and independently approved source context originals. Typed target authoring and decisions use the normal human service/controller paths. Independent author/approver identities and their exact original current grants are rechecked.

`TaskStrictFieldTypedProtocol.Author.requirements` explicitly maps real confirmed-plan requirement node IDs. FIELD_ONLY requires a real field occurrence; METHOD lists actual field-definition IDs interpreted as indicators and method evidence. Each method cites an approved context revision plus exact Unicode-codepoint method and standard-designation spans from that original source. This classification is explicitly authored and independently approved, never inferred from template titles/free text. No separate approval is synthesized.

For METHOD, the resolved target enumerates each actual scoped indicator × model × condition combination, merges repeated field occurrences without dropping combinations, and includes only method evidence whose independently approved source context covers that combination. No matching evidence yields INCOMPLETE and the real requirement node ID in missing requirements. Unresolved/global method scopes cannot be turned into invented model/condition UUIDs. Omitted requirement mappings remain missing, not implicitly FIELD_ONLY. Supported fully configured scoped combinations can produce COMPLETE coverage; this is method-evidence coverage, not a statement that a trial passed or a professional approved the device.

The deterministic runtime freshly reads and verifies the original and parsed physical objects for every method source context. Immutable method-read receipts bind the actual N03 input/action/lease and are included in output intent/registration verification.

## Actual output, governance and content

The internal `task_strict_field_result` row records complete comparison calculation and original source receipts. Its result ID is not an artifact ID.

`TaskStrictFieldOutputProducer` performs a real versioned object transfer through a privately minted committed `TaskStrictFieldOutputLedger.Attempt`. A persisted previous attempt permits GET recovery only. `FIELD_CHECK_RESULT` is then registered as an actual ARTIFACT with exact ObjectRef and published `FieldCheckResult` schema binding. The public payload preserves both original values, precision/units/context, actual rule IDs and evidence, method coverage and missing requirement IDs. Absent source values use the published explicit EMPTY representation, never a fabricated value.

Public `rule_revision` identifies an actual immutable `STRICT_FIELD_RULE_SET` registry row. Its canonical manifest binds every policy revision/digest, decision ID/digest, definition and installed comparison implementation. Exact policy/decision lineage is persisted and reverified. The normal typed VersionResource owner is PROJECT with `task.strict_field_rule_set.read`; it is not a Boolean RuleVersion or a source grant.

Content access manifests contain only actual source-derived versions with real source rights. Governance versions and target approvals are independently bound in immutable originals, not mislabeled as sources. Current downstream reads recheck the complete rule-set governance graph with VersionResourceAuthority, current exact approvals, target authority and all source rights.

The final producer receipt is `STRICT_FIELD_OUTPUT_REGISTRATION`: original ResultReceipt fields plus artifact_id, object_ref, intent_sha256, rule_revision, rule_sha256 and nested result_receipt. Its audit/event IDs are the actual output registration journal. The 13-field V2 completion stays an internal result locator; producerReceiptSha256 hashes this final immutable output receipt. AT-05 artifact_id/resource_revision identify the real output artifact.

## Commit and downstream seams

- `TaskStrictFieldCompletionAuthority.requireProduced(realNode, resultId)` consumes the current genuine node proof and complete output registration. `requireStageSatisfied` requires every expected entry COMPUTED/MATCH, no waiting or missing requirement and no incomplete method coverage. CHECKED alone is never satisfaction.
- `requireOriginal(recoveryAdmission, completion)`, `requireLifecycleOriginal(lifecycleProof, completion)` and `requireConsumerOriginal(productionProof, completion)` verify original command, input/action, full immutable result/source/policy/output journals and independently recompute the calculation. They do not borrow the old producer's current lease or identity.
- `TaskStrictFieldWriterReadAuthority.requireForWriter(production, artifactId, receiptSha256)` returns private `ForWriter`: content, targetArtifactId, outputArtifactId, outputObject, outputReceiptSha256, input and requireUnchanged. It consumes genuine B01 `TaskRunStageCommitServiceV2.ForConsumer` and requires SATISFIED. The graph is acyclic: writer authority depends on both commit and factual output ledger; the output ledger does not depend on commit.
- N04 must still perform its own authorized physical read using the returned exact ObjectRef; the canonical content proof is not a substitute for the downstream physical-read receipt.

## Deployment and dependencies

Default off: `medical.task-strict-field.enabled`. All runtime capacities are explicit. New required positive settings are `medical.task-strict-field.lease-lifetime-seconds` and `medical.task-strict-field.token-lifetime-seconds`; token lifetime cannot exceed lease lifetime. Existing qualified taskFieldTargetTransport is reused only as a real configured object transport. Missing configuration fails closed.

Required genuine dependencies: A4 approved typed comparison/numeric services and current fixed-run scope; B01 production/current protocol/graph/control/recovery/typed commit; original096 target/capability authority; C12 source processing and physical reader; actual approved role/tool/release node permissions; real RuntimeObjectTransport and artifact/dependency registry. New custom read permissions and published artifact profiles need real deployment governance; code does not seed grants.

Migration order:103 (three internal N03 tables),104 (eight context/typed-target tables),107 (four rule-set/output/method-read tables). All-domain VersionResource queries require the full migration set including107. Root owns master, business coverage and protected immutable-table registration.104 state rows preserve actual immutable revision creator/time/classification/schema pins.

## Verification and remaining acceptance

Focused source tests cover deterministic policy/coverage behavior, unsupported/missing outcomes, private capability constructors, preserved096 entry boundaries, exact context spans and typed method-mapping rejection. They have not been executed. Necessary Java17 slice diagnostics passed during implementation; the final combined compile-only diagnostic passed275 current sources including B01/N04 dependencies. No Maven, full tests, provider launch, live object storage operation, credential setup, commit or external push was performed by this worker.

Unified acceptance must exercise SQL migrations/FKs and immutable guards, actual full fixed-run admission, exact-one selection, independent approval revocation, source/right changes, duplicate commands, partial claims/transfers, output receipts/lineage, method combination omissions/duplicates, decimal precision/unit mismatches, committed N04 reads, cancellation and original-only recovery. CODE_COMPLETE is not test acceptance or runtime authorization.


## Committed output read correction (2026-10-20)

The downstream writer no longer requests a newly produced N03 artifact through the replay-only generic control read boundary. Its unchanged `requireForWriter` entry point first authenticates the exact physical output registration and B01 SATISFIED stage COMMIT, then privately mints `CommittedRead`. A narrow `ResourceReadAuthority.requireStrictFieldOutput` overload admits only that exact output artifact/action/purpose while retaining the existing actual resource owner and current grant lookup. Generic control scopes and generic read methods are unchanged.

The separate `VersionResourceAuthority.requireStrictFieldOutput` overload accepts only the original rule-set graph root. Non-replay version pins are confined to the exact rule-set ID/hash, fixed N03 input policies (also currently independently approved), and original rule-set decision snapshots. Prior decisions are followed only through their authenticated immutable decision baseline, bounded and required to belong to the same policy; every row and original lineage/journal is checked by `StrictComparisonStore`. Other ancestors must still match original replay version IDs and hashes. Actual domain ownership, current resource grants, complete lineage and fixed-source rights checks remain in force. This does not authorize arbitrary lineage ancestors, create a grant, or reuse the old N03 RUNNING lease.

Focused test source now covers the private proof constructor and refusal of null proofs at both overloads. Tests remain deferred to the unified phase.
