package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import java.util.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.function.ThrowingSupplier;

/** Original physical production, with guard reacquisition and recover-only retry after persisted intent. */
public final class TaskStrictFieldTypedTargetProducer {
    public record Produce(String schemaVersion,String targetRevision,String contentSha256){public Produce{if(!Set.of("1.0.0","2.0.0").contains(schemaVersion))throw new IllegalArgumentException();WireValues.uuid(targetRevision);WireValues.hash(contentSha256);}}
    private final EnterpriseAccess access;private final TaskStrictFieldTypedTargetService targets;private final TaskStrictFieldTypedTargetLedger ledger;private final RuntimeObjectTransport transport;private final StrictComparisonCodec codec;private final ResourceReadAuthority resources;
    TaskStrictFieldTypedTargetProducer(EnterpriseAccess access,TaskStrictFieldTypedTargetService targets,TaskStrictFieldTypedTargetLedger ledger,RuntimeObjectTransport transport,StrictComparisonCodec codec,ResourceReadAuthority resources){this.access=access;this.targets=targets;this.ledger=ledger;this.transport=transport;this.codec=codec;this.resources=resources;}
    public JsonNode produce(ApplicationSessionIdentity.Verified subject,String context,String project,String key,byte[] bytes)throws Exception{
        if(TransactionSynchronizationManager.isActualTransactionActive())throw new TenantAccessException(ErrorCode.ACCESS_DENIED);if(transport==null)throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);var request=codec.bind(bytes,Produce.class);var storage=transport.storageBinding();
        var attempt=access.execute(subject,a->checked(()->ledger.prepare(current(a,context,project,request),key,storage)));
        var transferred=transport.transferVerified(attempt,()->access.execute(subject,a->checked(()->{transport.requireStorageBinding(storage);ledger.require(current(a,context,project,request),attempt,storage);return null;})));
        return access.execute(subject,a->checked(()->{transport.requireStorageBinding(storage);return ledger.register(current(a,context,project,request),attempt,transferred,storage);}));
    }
    private TaskStrictFieldTypedTargetService.Verified current(EnterpriseAccess.Admission a,String context,String project,Produce request)throws Exception{StrictComparisonAuthority.human(a,context);if(a.planDraftWorker())throw new TenantAccessException(ErrorCode.ACCESS_DENIED);resources.require(a,new PermissionProtocol.Scope(a.context().tenant_id(),project,"PROJECT",project),"task.strict_field_typed.produce",StrictComparisonAuthority.PURPOSE);var target=targets.requireHuman(a,project,request.targetRevision(),request.contentSha256());if(!request.schemaVersion().equals(target.request().schemaVersion()))throw new TenantAccessException(ErrorCode.SCHEMA_UNSUPPORTED);return target;}
    private static <T>T checked(ThrowingSupplier<T> action){return action.get();}
}
