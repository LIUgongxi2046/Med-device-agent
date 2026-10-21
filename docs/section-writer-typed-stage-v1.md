# N04 typed stage extension

Implementation status: code complete; integration, replay and runtime tests are deferred.

The original V1 protocol and sixteen-field WaitingWorkflowDeploymentV2 bytes remain unchanged. SectionWriterWorkflowExtensionV1 is an independently pinned launch memo and version marker. Only its explicit worker factory schedules this branch; absent or unaccepted side-journal bindings fail closed.

Execution path:

1. The original N03 typed COMMIT must be SATISFIED. PREPARE invokes the actual C04 initial-document and per-chapter input materializer, with immutable object intents and real transfer/read-back registrations.
2. INVENTORY discovers materialized N04 assignments, original reservation commands (including partial claims), original physical envelope dispatches and verified commits. The original graph's full required chapter set is carried separately, so partial materialization never satisfies N04.
3. DISPATCH uses the actual writer reservation/claim/bootstrap/object journal. The Python activity is medical.task.section-writer.execute.v1 and receives the exact resulting WorkflowProtocol.Dispatch. It returns the closed 16-field original AT06 READY receipt, not WorkflowProtocol.Completion or an N02 EvidenceBundle.
4. COMMIT uses current FormalTaskNodeAuthority plus a separate StageFacts capability. It rechecks actual original reservation, physical envelope, AT06 submission, candidate/body registrations, invocation settlements and independent owned-process closure. It writes an immutable per-chapter journal and marks only that run_step SUCCEEDED with no fabricated artifact output.
5. A lost DISPATCH, model or COMMIT response enters RECOVER against the original command. Recovery is query-only. Partial reservations or uncertain non-produced outcomes remain pending; they never create another lease, command, model run or AT06 submission.
6. N04 is satisfied only when every chapter in the original required set has its exact commit. This does not fulfill N05/N06, adopt a candidate, mutate a document head or make the run successful.

Lifecycle uses a dedicated writer invocation reader and original N04 session/supervisor witnesses. It does not pass writer sessions through the N02 deployment/dispatch reader. Cancellation can use the original writer dispatch after genuine terminal-invocation and owned-process-exit facts. A missing closure or uncertain lease remains blocked rather than being inferred complete.

Validation performed: focused javac of workflow protocol/scheduler/client and backend stage/current/lifecycle dependency slice. No tests, providers, Temporal runtime, network execution or deployment were run. Added protocol tests cover partial assignment, partial reservation, immutable command, exact AT06 wire shape and rejection of synthetic recovery results.
