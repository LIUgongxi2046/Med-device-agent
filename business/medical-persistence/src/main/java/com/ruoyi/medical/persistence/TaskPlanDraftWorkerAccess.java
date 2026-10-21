package com.ruoyi.medical.persistence;
import com.ruoyi.medical.contracts.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Objects;
import org.springframework.util.function.ThrowingSupplier;
/** Worker gateway uses the actual signed token, mTLS, bounded action and private current
 * N01 bootstrap in the same tenant transaction. It never rebrands control reads as model.send. */
public final class TaskPlanDraftWorkerAccess {
    private final FormalWorkerAccess workers;private final RunTokenAuthority tokens;
    public TaskPlanDraftWorkerAccess(FormalWorkerAccess workers,RunTokenAuthority tokens){this.workers=Objects.requireNonNull(workers);this.tokens=Objects.requireNonNull(tokens);}
    public <T>T withModelAction(HttpServletRequest http,String token,RunTokenAuthority.ActionRequest action,Operation<T> operation)throws Exception {
        if(!"MODEL_CALL".equals(action.kind())||!"model.send".equals(action.action()))deny();return withAction(http,token,action,operation);
    }
    public <T>T withAction(HttpServletRequest http,String token,RunTokenAuthority.ActionRequest action,Operation<T> operation)throws Exception {
        Objects.requireNonNull(operation);return workers.executeAction(http,token,action,(a,admitted)->ThrowingSupplier.of(()->{
            if(!a.formalWorker()||!"PLAN_DRAFT".equals(a.formalProof().identity().executionKind()))deny();
            var proof=tokens.planDraftRuntime(action);proof.requireUnchanged();var result=operation.apply(a,admitted,proof);proof.requireUnchanged();return result;
        }).get());
    }
    public <T>T withToolAction(HttpServletRequest http,String token,RunTokenAuthority.ActionRequest request,ToolOperation<T> operation)throws Exception {
        if(!"TOOL_CALL".equals(request.kind()))deny();
        return withAction(http,token,request,(a,t,r)->{var action=new Action(a,t,r);try{return operation.apply(action);}finally{action.active=false;}});
    }
    public final class Action {
        private final EnterpriseAccess.Admission admission;private final RunTokenAuthority.Admission token;private final TaskPlanDraftRuntimeAuthority.Verified runtime;
        private final Thread thread=Thread.currentThread();private boolean active=true;
        private Action(EnterpriseAccess.Admission admission,RunTokenAuthority.Admission token,TaskPlanDraftRuntimeAuthority.Verified runtime){this.admission=admission;this.token=token;this.runtime=runtime;}
        public void requireActive()throws Exception {if(!active||thread!=Thread.currentThread())deny();runtime.requireUnchanged();}
        public EnterpriseAccess.Admission admission()throws Exception{requireActive();return admission;}
        public RunTokenAuthority.Admission token()throws Exception{requireActive();return token;}
        public TaskPlanDraftRuntimeAuthority.Verified runtime()throws Exception{requireActive();return runtime;}
    }
    <T>T requireRuntime(EnterpriseAccess.Admission admission,RunTokenAuthority.Admission token,RunTokenAuthority.ActionRequest action,RuntimeOperation<T> operation)throws Exception {
        if(!admission.formalWorker()||!"PLAN_DRAFT".equals(admission.formalProof().identity().executionKind())||!token.actionId().equals(action.actionId())||!token.initiatorId().equals(admission.actorId()))deny();
        var proof=tokens.planDraftRuntime(action);proof.requireUnchanged();var result=operation.apply(proof);proof.requireUnchanged();return result;
    }
    @FunctionalInterface interface RuntimeOperation<T>{T apply(TaskPlanDraftRuntimeAuthority.Verified runtime)throws Exception;}
    @FunctionalInterface public interface ToolOperation<T>{T apply(Action action)throws Exception;}
    @FunctionalInterface public interface Operation<T>{T apply(EnterpriseAccess.Admission admission,RunTokenAuthority.Admission token,TaskPlanDraftRuntimeAuthority.Verified runtime)throws Exception;}
    private static void deny(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}
}
