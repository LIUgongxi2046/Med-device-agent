package com.ruoyi.medical.persistence;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.function.ThrowingSupplier;
/** Independent F2 + original settled076 + actual registered saved-body findings/coverage. */
public final class DocumentReviewFinishOrchestrator {
 private final DocumentReviewCompletionLedger completions;private final DocumentReviewRuntimeAdapter runtime;private final DocumentReviewSupervisor supervisor;private final DocumentReviewInvocationService invocations;private final DocumentReviewObjectLedger objects;private final RuntimeObjectTransport transport;private final DocumentReviewResultService results;
 public DocumentReviewFinishOrchestrator(DocumentReviewRuntimeAdapter runtime,DocumentReviewSupervisor supervisor,DocumentReviewInvocationService invocations,DocumentReviewObjectLedger objects,RuntimeObjectTransport transport,DocumentReviewResultService results,DocumentReviewCompletionLedger completions){this.completions=completions;this.runtime=runtime;this.supervisor=supervisor;this.invocations=invocations;this.objects=objects;this.transport=transport;this.results=results;}
 public void save(HttpServletRequest http,DocumentReviewHostAccess.Handle host)throws Exception {
  var request=host.execute(supervisor::snapshotClosure);var actual=supervisor.inspect(request);
  host.execute(s->{runtime.close(s,supervisor.close(s,actual));return null;});
  var findings=host.execute(s->objects.prepare(s.admission(),produce(s,actual),"REVIEW_FINDINGS"));
  var findingBytes=transport.transferVerified(findings,()->ThrowingSupplier.of(()->host.execute(s->{objects.requireTransfer(s.admission(),produce(s,actual),"REVIEW_FINDINGS",findings);return null;})).get());
  var coverage=host.execute(s->objects.prepare(s.admission(),produce(s,actual),"REVIEW_COVERAGE"));
  var coverageBytes=transport.transferVerified(coverage,()->ThrowingSupplier.of(()->host.execute(s->{objects.requireTransfer(s.admission(),produce(s,actual),"REVIEW_COVERAGE",coverage);return null;})).get());
  host.execute(s->{var produced=produce(s,actual);var a=s.admission();var f=objects.register(a,produced,"REVIEW_FINDINGS",findings,findingBytes);var c=objects.register(a,produced,"REVIEW_COVERAGE",coverage,coverageBytes);var job=s.authority().job();var completed=results.submitSaved(a,produced,f,c);completions.finish(a,job,completed);return null;});
 }
 private DocumentReviewResultService.Produced produce(DocumentReviewRuntimeAdapter.Session s,DocumentReviewSupervisor.Inspection actual)throws Exception{return results.produce(s.admission(),invocations.settledForSession(s,supervisor.requireClosed(s,actual)));}
}
