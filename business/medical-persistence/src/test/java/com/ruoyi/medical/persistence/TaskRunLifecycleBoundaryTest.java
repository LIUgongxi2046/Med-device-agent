package com.ruoyi.medical.persistence;
import java.lang.reflect.Modifier;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class TaskRunLifecycleBoundaryTest {
    @Test void privateProofAndTransitionCannotBeMintedByController(){for(var type:List.of(TaskRunLifecycleAuthority.Proof.class,TaskRunLifecycleAuthority.Transition.class,TaskRunLifecycleService.ValidatedCancellation.class,TaskRunStageCommitServiceV2.ForConsumer.class))for(var ctor:type.getDeclaredConstructors())assertTrue(Modifier.isPrivate(ctor.getModifiers()),type.getName());}
    @Test void cancellationPurposeNeverAuthorizesFailureOrSuccess(){for(var op:TaskRunLifecycleAuthority.Operation.values()){boolean cancel=Set.of(TaskRunLifecycleAuthority.Operation.REQUEST_CANCEL,TaskRunLifecycleAuthority.Operation.CONSUME_CANCEL,TaskRunLifecycleAuthority.Operation.FINISH_CANCELLED).contains(op);assertEquals(cancel,op.cancellation());assertEquals(cancel?"RUN_CANCELLATION":"TASK_EXECUTION",op.purpose());}}
    @Test void controlReadersKeepTheirOperationScopedEntryPackagePrivate()throws Exception{var method=FormalRunControlAccess.class.getDeclaredMethod("withLifecycleReads",jakarta.servlet.http.HttpServletRequest.class,String.class,String.class,String.class,TaskRunLifecycleAuthority.Operation.class,java.util.function.Function.class);assertFalse(Modifier.isPublic(method.getModifiers()));for(var f:TaskRunTerminationFacts.class.getDeclaredFields())assertFalse(Set.of("RunTokenAuthority","TaskHostControlBootstrap","FormalInvocationService","TaskInvocationService").contains(f.getType().getSimpleName()));}
    @Test void immutableLifecycleManifestIsActualVersionedContract()throws Exception{try(var in=Objects.requireNonNull(getClass().getResourceAsStream("/task-run-lifecycle.schema.json"))){assertEquals(org.apache.commons.codec.digest.DigestUtils.sha256Hex(in.readAllBytes()),TaskRunLifecycleService.MANIFEST);}try(var in=Objects.requireNonNull(getClass().getResourceAsStream("/task-run-stage-commit-v2.schema.json"))){assertEquals(org.apache.commons.codec.digest.DigestUtils.sha256Hex(in.readAllBytes()),TaskRunStageCommitServiceV2.MANIFEST);}}
}
