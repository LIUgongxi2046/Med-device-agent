package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
/** Physical supervision is always outside SQL. Every observation is then consumed
 * against freshly reacquired token/current source/lease/package authority. */
public final class TaskSourceVerifierHostOrchestrator {
 private final TaskSourceVerifierRuntimeAdapter runtime;private final TaskSourceVerifierSupervisor supervisor;
 public TaskSourceVerifierHostOrchestrator(TaskSourceVerifierRuntimeAdapter runtime,TaskSourceVerifierSupervisor supervisor){this.runtime=runtime;this.supervisor=supervisor;}
 public JsonNode prepare(TaskSourceVerifierHostAccess.Handle host)throws Exception{return host.prepare(TaskSourceVerifierRuntimeAdapter.Envelope::launch);}
 public JsonNode admit(HttpServletRequest http,TaskSourceVerifierHostAccess.Handle host)throws Exception {
  var snapshot=host.prepare(supervisor::snapshotAdmission);var observed=supervisor.inspect(snapshot);
  return host.prepare(envelope->{var admitted=supervisor.admit(envelope,observed);return runtime.admit(http,envelope,admitted).launch();});
 }
 public JsonNode close(TaskSourceVerifierHostAccess.Handle host)throws Exception {
  var snapshot=host.execute(supervisor::snapshotClosure);var observed=supervisor.inspect(snapshot);
  return host.execute(session->{var closure=supervisor.close(session,observed);return runtime.close(session,closure).launch();});
 }
}
