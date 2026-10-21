package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
/** Physical supervision is always outside SQL. Every observation is then consumed
 * against freshly reacquired token/current source/lease/package authority. */
public final class TaskSectionWriterHostOrchestrator {
 private final TaskSectionWriterRuntimeAdapter runtime;private final TaskSectionWriterSupervisor supervisor;
 public TaskSectionWriterHostOrchestrator(TaskSectionWriterRuntimeAdapter runtime,TaskSectionWriterSupervisor supervisor){this.runtime=runtime;this.supervisor=supervisor;}
 public JsonNode prepare(TaskSectionWriterHostAccess.Handle host)throws Exception{return host.prepare(TaskSectionWriterRuntimeAdapter.Envelope::launch);}
 public JsonNode admit(HttpServletRequest http,TaskSectionWriterHostAccess.Handle host)throws Exception {
  var snapshot=host.prepare(supervisor::snapshotAdmission);var observed=supervisor.inspect(snapshot);
  return host.prepare(envelope->{var admitted=supervisor.admit(envelope,observed);return runtime.admit(http,envelope,admitted).launch();});
 }
 public JsonNode close(TaskSectionWriterHostAccess.Handle host)throws Exception {
  var snapshot=host.execute(supervisor::snapshotClosure);var observed=supervisor.inspect(snapshot);
  return host.execute(session->{var closure=supervisor.close(session,observed);return runtime.close(session,closure).launch();});
 }
}
