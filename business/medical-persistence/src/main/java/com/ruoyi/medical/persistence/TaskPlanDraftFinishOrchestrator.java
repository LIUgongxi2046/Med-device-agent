package com.ruoyi.medical.persistence;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.List;
/** Actual independent closure + settled model bytes + normalized output + exact object
 * readback + server check + signed host AT10. This stops before human confirmation. */
public final class TaskPlanDraftFinishOrchestrator {
 private final TaskPlanDraftRuntimeAdapter runtime;private final TaskPlanDraftSupervisor supervisor;private final TaskPlanDraftInvocationService invocations;private final TaskPlanDraftObjectLedger ledger;private final RuntimeObjectTransport transport;private final TaskPlanDraftCandidateRegistration candidates;private final TaskPlanDraftIndependentCheck checks;private final TaskPlanDraftSaveAction actions;private final TaskPlanDraftSaveReceipt saves;private final RunTokenAuthority tokens;private final TaskPlanDraftWorkerAccess workers;private final TaskPlanDraftDeployment deployment;
 public TaskPlanDraftFinishOrchestrator(TaskPlanDraftRuntimeAdapter runtime,TaskPlanDraftSupervisor supervisor,TaskPlanDraftInvocationService invocations,TaskPlanDraftObjectLedger ledger,RuntimeObjectTransport transport,TaskPlanDraftCandidateRegistration candidates,TaskPlanDraftIndependentCheck checks,TaskPlanDraftSaveAction actions,TaskPlanDraftSaveReceipt saves,RunTokenAuthority tokens,TaskPlanDraftWorkerAccess workers,TaskPlanDraftDeployment deployment){this.runtime=runtime;this.supervisor=supervisor;this.invocations=invocations;this.ledger=ledger;this.transport=transport;this.candidates=candidates;this.checks=checks;this.actions=actions;this.saves=saves;this.tokens=tokens;this.workers=workers;this.deployment=deployment;}
 public TaskPlanDraftSaveReceipt.Saved save(HttpServletRequest http,TaskPlanDraftHostAccess.Handle host)throws Exception {
  var snapshot=host.execute(supervisor::snapshotClosure);var actual=supervisor.inspect(snapshot);
  host.execute(s->{runtime.close(s,supervisor.close(s,actual));return null;});
  var intent=host.execute(s->ledger.prepareCandidate(produce(s,actual)));
  var attempt=host.execute(s->ledger.attemptCandidate(produce(s,actual),intent));
  var physical=transport.transferVerified(attempt,()->org.springframework.util.function.ThrowingSupplier.of(()->host.execute(s->{ledger.requireCandidateAttempt(produce(s,actual),intent);return null;})).get());
  var checked=host.execute(s->{var registered=candidates.register(produce(s,actual),host.input(s),attempt,physical);var result=checks.check(registered);return new CheckedCall(result.receipt().required("check_id").textValue(),actions.bind(result));});
  var bound=checked.bound();var j=bound.job();var action=bound.actionRequest();var token=tokens.issue(http,new RunTokenAuthority.IssueRequest(j.tenant(),j.actor(),j.run(),j.step(),action.generation(),List.of("plan.write"),Duration.ofSeconds(deployment.value().required("token_lifetime_seconds").longValue())));
  return workers.withToolAction(http,token.token(),action,authenticated->{var session=host.sessionFor(authenticated.runtime());var candidate=candidates.register(produce(session,actual),host.input(session),attempt,physical);var check=checks.read(candidate,checked.checkId());return saves.save(authenticated,bound,check);});
 }
 private TaskPlanDraftRuntimeAdapter.Produced produce(TaskPlanDraftRuntimeAdapter.Session session,TaskPlanDraftSupervisor.Inspection actual)throws Exception {var closure=supervisor.requireClosed(session,actual);return runtime.produce(session,closure,invocations.settledForSession(session,closure));}
 private record CheckedCall(String checkId,TaskPlanDraftSaveAction.Bound bound){}
}
