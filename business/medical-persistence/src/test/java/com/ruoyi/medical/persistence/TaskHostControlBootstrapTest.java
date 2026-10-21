package com.ruoyi.medical.persistence;
import static org.junit.jupiter.api.Assertions.*;
import java.math.BigInteger;
import java.util.List;
import org.junit.jupiter.api.Test;
class TaskHostControlBootstrapTest {
 private final String id="11111111-1111-4111-8111-111111111111";
 private RunTokenAuthoritySchema.Permission permission(String action){return new RunTokenAuthoritySchema.Permission(new PermissionProtocol.Scope(id,id,"TASK",id),action,"TASK_EXECUTION");}
 @Test void hostTokenCannotCarrySendOrWriteEvenWhenNodeDeclaresThem(){assertEquals(List.of("artifact.read","task.run"),TaskHostControlBootstrap.hostActions(List.of(permission("model.send"),permission("artifact.register"),permission("artifact.read"),permission("task.run"))));}
 @Test void taskRunIsNotInferredFromReadOrModelAuthority(){assertThrows(TenantAccessException.class,()->TaskHostControlBootstrap.hostActions(List.of(permission("model.send"),permission("task.read"))));}
 @Test void originalBindingIdentityChangesWithActualLease(){assertEquals(TaskHostControlBootstrap.actionId(id,id,id,BigInteger.ONE),TaskHostControlBootstrap.actionId(id,id,id,BigInteger.ONE));assertNotEquals(TaskHostControlBootstrap.actionId(id,id,id,BigInteger.ONE),TaskHostControlBootstrap.actionId(id,id,id,BigInteger.TWO));assertThrows(TenantAccessException.class,()->TaskHostControlBootstrap.actionId(id,id,id,BigInteger.ZERO));}
}
