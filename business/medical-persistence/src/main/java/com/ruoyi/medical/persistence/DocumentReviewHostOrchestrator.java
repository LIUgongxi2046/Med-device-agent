package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
/** Physical supervision is always outside SQL. Every observation is then consumed
 * against freshly reacquired token/current source/lease/package authority. */
public final class DocumentReviewHostOrchestrator {
 private final DocumentReviewRuntimeAdapter runtime;private final DocumentReviewSupervisor supervisor;
 public DocumentReviewHostOrchestrator(DocumentReviewRuntimeAdapter runtime,DocumentReviewSupervisor supervisor){this.runtime=runtime;this.supervisor=supervisor;}
 public JsonNode prepare(DocumentReviewHostAccess.Handle host)throws Exception{return host.prepare(DocumentReviewRuntimeAdapter.Envelope::launch);}
 public JsonNode admit(HttpServletRequest http,DocumentReviewHostAccess.Handle host)throws Exception {
  var snapshot=host.prepare(supervisor::snapshotAdmission);var observed=supervisor.inspect(snapshot);
  return host.prepare(envelope->{var admitted=supervisor.admit(envelope,observed);return runtime.admit(http,envelope,admitted).launch();});
 }
 public JsonNode close(DocumentReviewHostAccess.Handle host)throws Exception {
  var snapshot=host.execute(supervisor::snapshotClosure);var observed=supervisor.inspect(snapshot);
  return host.execute(session->{var closure=supervisor.close(session,observed);return runtime.close(session,closure).launch();});
 }
}
