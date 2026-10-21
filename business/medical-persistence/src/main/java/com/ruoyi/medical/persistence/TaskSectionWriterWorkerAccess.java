package com.ruoyi.medical.persistence;
import com.ruoyi.medical.contracts.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Objects;
import org.springframework.util.function.ThrowingSupplier;
/** Worker gateway uses the actual signed token, mTLS, bounded action and private current
 * N04 bootstrap in the same tenant transaction. It never rebrands control reads as model.send. */
public final class TaskSectionWriterWorkerAccess {
    private final FormalWorkerAccess workers;private final TaskSectionWriterRuntimeAuthority authorities;
    public TaskSectionWriterWorkerAccess(FormalWorkerAccess workers,TaskSectionWriterRuntimeAuthority authorities){this.workers=Objects.requireNonNull(workers);this.authorities=Objects.requireNonNull(authorities);}
    public <T>T withModelAction(HttpServletRequest http,String token,RunTokenAuthority.ActionRequest action,Operation<T> operation)throws Exception {
        if(!"MODEL_CALL".equals(action.kind())||!"model.send".equals(action.action()))deny();return withAction(http,token,action,operation);
    }
    public <T>T withAction(HttpServletRequest http,String token,RunTokenAuthority.ActionRequest action,Operation<T> operation)throws Exception {
        Objects.requireNonNull(operation);return workers.executeAction(http,token,action,(a,admitted)->ThrowingSupplier.of(()->{
            if(!a.formalWorker()||!"TASK".equals(a.formalProof().identity().executionKind()))deny();
            var proof=authorities.requireLeased(a,admitted.currentWriterInput(),action.actionId(),action.generation(),admitted.serviceUri());proof.requireUnchanged();var result=operation.apply(a,admitted,proof);proof.requireUnchanged();return result;
        }).get());
    }
    public <T>T withToolAction(HttpServletRequest http,String token,RunTokenAuthority.ActionRequest request,ToolOperation<T> operation)throws Exception {
        if(!"TOOL".equals(request.kind()))deny();
        return withAction(http,token,request,(a,t,r)->{var action=new Action(a,t,r);try{return operation.apply(action);}finally{action.active=false;}});
    }
    public final class Action {
        private final EnterpriseAccess.Admission admission;private final RunTokenAuthority.Admission token;private final TaskSectionWriterRuntimeAuthority.Verified runtime;
        private final Thread thread=Thread.currentThread();private boolean active=true;
        private Action(EnterpriseAccess.Admission admission,RunTokenAuthority.Admission token,TaskSectionWriterRuntimeAuthority.Verified runtime){this.admission=admission;this.token=token;this.runtime=runtime;}
        public void requireActive()throws Exception {if(!active||thread!=Thread.currentThread())deny();runtime.requireUnchanged();}
        public EnterpriseAccess.Admission admission()throws Exception{requireActive();return admission;}
        public RunTokenAuthority.Admission token()throws Exception{requireActive();return token;}
        public TaskSectionWriterRuntimeAuthority.Verified runtime()throws Exception{requireActive();return runtime;}
    }
    <T>T requireRuntime(EnterpriseAccess.Admission admission,RunTokenAuthority.Admission token,RunTokenAuthority.ActionRequest action,RuntimeOperation<T> operation)throws Exception {
        if(!admission.formalWorker()||!"TASK".equals(admission.formalProof().identity().executionKind())||!token.actionId().equals(action.actionId())||!token.initiatorId().equals(admission.actorId()))deny();
        var proof=authorities.requireLeased(admission,token.currentWriterInput(),action.actionId(),action.generation(),token.serviceUri());proof.requireUnchanged();var result=operation.apply(proof);proof.requireUnchanged();return result;
    }
    @FunctionalInterface interface RuntimeOperation<T>{T apply(TaskSectionWriterRuntimeAuthority.Verified runtime)throws Exception;}
    @FunctionalInterface public interface ToolOperation<T>{T apply(Action action)throws Exception;}
    @FunctionalInterface public interface Operation<T>{T apply(EnterpriseAccess.Admission admission,RunTokenAuthority.Admission token,TaskSectionWriterRuntimeAuthority.Verified runtime)throws Exception;}
    private static void deny(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}
}
