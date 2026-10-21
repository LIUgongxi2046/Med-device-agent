package com.ruoyi.medical.persistence;

import com.ruoyi.medical.workflow.WorkflowProtocol.Reference;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import static com.ruoyi.medical.persistence.TaskRunLifecycleAuthority.Operation.REQUEST_CANCEL;

/** Original-only process reconciliation after dispatch has closed. Each independent
 * HTTPS read is outside SQL; fresh current authority and the original session are
 * rechecked before its owned waitid evidence is recorded. No launch/redispatch. */
public final class TaskRunTerminationReconciler {
    private final TaskRunLifecycleAuthority authority;
    private final TaskRunTerminationFacts facts;
    private final TaskProfessionalSupervisor research;
    private final TaskProfessionalRuntimeService.Deployment researchDeployment;
    private final ObjectProvider<TaskSectionWriterSupervisor> writers;
    private final ObjectProvider<TaskSectionWriterDeployment> writerDeployment;
    private final ObjectProvider<TaskSourceVerifierSupervisor> verifiers;
    private final ObjectProvider<TaskSourceVerifierDeployment> verifierDeployment;
    public TaskRunTerminationReconciler(TaskRunLifecycleAuthority authority,TaskRunTerminationFacts facts,
            TaskProfessionalSupervisor research,TaskProfessionalRuntimeService.Deployment researchDeployment,
            ObjectProvider<TaskSectionWriterSupervisor> writers,ObjectProvider<TaskSectionWriterDeployment> writerDeployment,
            ObjectProvider<TaskSourceVerifierSupervisor> verifiers,ObjectProvider<TaskSourceVerifierDeployment> verifierDeployment){
        this.authority=authority;this.facts=facts;this.research=research;this.researchDeployment=researchDeployment;
        this.writers=writers;this.writerDeployment=writerDeployment;this.verifiers=verifiers;this.verifierDeployment=verifierDeployment;
    }
    void reconcile(HttpServletRequest http,Reference reference)throws Exception {
        var sessions=authority.execute(http,reference,REQUEST_CANCEL,p->facts.pendingClosures(p).stream().map(TaskRunTerminationFacts.PendingClosure::session).toList());
        for(String session:sessions){
            var request=authority.execute(http,reference,REQUEST_CANCEL,p->{
                var original=find(facts.pendingClosures(p),session);if(original==null)return null;
                return switch(original.start().required("node_kind").asText()){
                    case "N02" -> research.snapshotTermination(original,researchDeployment);
                    case "N04" -> writers.getObject().snapshotTermination(original,writerDeployment.getObject());
                    case "N05" -> verifiers.getObject().snapshotTermination(original,verifierDeployment.getObject());
                    default -> throw new IllegalStateException("UNSUPPORTED_ORIGINAL_PROCESS_KIND");
                };
            });
            if(request==null)continue;
            final Object inspected;
            try{
                if(request instanceof TaskProfessionalSupervisor.Request r)inspected=research.inspect(r);
                else if(request instanceof TaskSectionWriterSupervisor.Request r)inspected=writers.getObject().inspect(r);
                else inspected=verifiers.getObject().inspect((TaskSourceVerifierSupervisor.Request)request);
            }catch(java.io.IOException unavailable){continue;} // Missing CLOSED is pending, never process exit.
            authority.execute(http,reference,REQUEST_CANCEL,p->{
                var original=find(facts.pendingClosures(p),session);if(original==null)return null;
                if(inspected instanceof TaskProfessionalSupervisor.Inspection i)research.recordTermination(original,researchDeployment,i);
                else if(inspected instanceof TaskSectionWriterSupervisor.Inspection i)writers.getObject().recordTermination(original,writerDeployment.getObject(),i);
                else verifiers.getObject().recordTermination(original,verifierDeployment.getObject(),(TaskSourceVerifierSupervisor.Inspection)inspected);
                return null;
            });
        }
        var pending=authority.execute(http,reference,REQUEST_CANCEL,p->facts.pendingLaunches(p).stream().map(TaskRunTerminationFacts.PendingLaunch::session).toList());
        for(String session:pending){
            var requests=authority.execute(http,reference,REQUEST_CANCEL,p->{var original=facts.pendingLaunches(p).stream().filter(v->v.session().equals(session)).findFirst().orElse(null);if(original==null||facts.pendingTermination(original).isPresent())return null;return switch(original.kind()){
                case "N02"->List.of(research.snapshotPending(original,researchDeployment,"ADMITTED"),research.snapshotPending(original,researchDeployment,"TERMINATED"));
                case "N04"->List.of(writers.getObject().snapshotPending(original,writerDeployment.getObject(),"ADMITTED"),writers.getObject().snapshotPending(original,writerDeployment.getObject(),"TERMINATED"));
                case "N05"->List.of(verifiers.getObject().snapshotPending(original,verifierDeployment.getObject(),"ADMITTED"),verifiers.getObject().snapshotPending(original,verifierDeployment.getObject(),"TERMINATED"));default->throw new IllegalStateException();};});
            if(requests==null)continue;final Object admission,termination;
            try{admission=inspectPending(requests.get(0),true);termination=inspectPending(requests.get(1),false);}catch(java.io.IOException unavailable){continue;}
            authority.execute(http,reference,REQUEST_CANCEL,p->{var original=facts.pendingLaunches(p).stream().filter(v->v.session().equals(session)).findFirst().orElse(null);if(original!=null){
                if(admission instanceof TaskProfessionalSupervisor.Inspection a)research.recordPending(original,researchDeployment,a,(TaskProfessionalSupervisor.Inspection)termination);
                else if(admission instanceof TaskSectionWriterSupervisor.Inspection a)writers.getObject().recordPending(original,writerDeployment.getObject(),a,(TaskSectionWriterSupervisor.Inspection)termination);
                else verifiers.getObject().recordPending(original,verifierDeployment.getObject(),(TaskSourceVerifierSupervisor.Inspection)admission,(TaskSourceVerifierSupervisor.Inspection)termination);
            }return null;});

        }

    }
    private Object inspectPending(Object request,boolean admission)throws Exception{if(request instanceof TaskProfessionalSupervisor.Request r)return admission?research.inspectPendingAdmission(r):research.inspect(r);if(request instanceof TaskSectionWriterSupervisor.Request r)return admission?writers.getObject().inspectPendingAdmission(r):writers.getObject().inspect(r);var r=(TaskSourceVerifierSupervisor.Request)request;return admission?verifiers.getObject().inspectPendingAdmission(r):verifiers.getObject().inspect(r);}
    private static TaskRunTerminationFacts.PendingClosure find(List<TaskRunTerminationFacts.PendingClosure> values,String session){return values.stream().filter(v->session.equals(v.session())).findFirst().orElse(null);}
}
