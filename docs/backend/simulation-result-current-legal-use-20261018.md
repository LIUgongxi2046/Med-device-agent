# Historical SIM result current legal use

Settled MODEL_RESULT and SIMULATION_PROVENANCE reads now share `SimulationEvaluationLegalUseAuthority` with evaluation definition consumption. A verified fixed definition must still be all-SIM, its registered authorized-use document must reconstruct against current business-source rights and the opaque private approval witness, and its fixed rule must still satisfy the existing strict APPROVED rule decision/operation/audit/outbox checks. These are current read checks and create no authorization.

The helper accepts `EvaluationProtocol.Create`; it deliberately cannot inspect evaluation lifecycle or score/result references. Completed evaluations remain readable while current legal use and independent result/context/source/retention permissions remain valid. `SimulationArtifactReadGuard` retains the full stored result, fixed definition, candidate, execution context, original object and current read checks. It does not invoke `EvaluationService.load` or `checkReferences`.

Rule dependencies may contain older settled artifacts. Such dependency reads use the same guard. The existing thread-local active artifact set rejects repeated artifact identity and limits nested reads to 32. Failure unwinds the set in `finally`; no cycle is ignored or treated as authorized. The bound remains separate from database lineage size limits because nested domain reads can cross stored graph boundaries.

Scope limits remain: authorization preparation is enterprise-only; generic dataset versions remain supported when their actual registered closure reaches a real authorized SOURCE. An isolated version without source ancestry cannot satisfy a source-rights dependency manifest. No worker purpose whitelist changes.

Regression coverage: guard orchestration tests include terminal success, witness denial, current-rule denial, and same-result rule dependency recursion followed by successful clean retry. The opaque-witness tests compose the shared entry point with real current rights/witness validators; document-registration and mapper boundaries remain explicit doubles. No database or end-to-end production claim follows from these tests.
