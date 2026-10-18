package com.ruoyi.medical.persistence;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RuntimeNodePermissionsTest {
 private RunTokenAuthoritySchema.Permission p(String owner,String kind,String id,String purpose){return new RunTokenAuthoritySchema.Permission(new PermissionProtocol.Scope("tenant",owner,kind,id),"artifact.read",purpose);}
 @Test void sameActionOtherNodeScopeIsExcluded(){var a=p("project","ARTIFACT","first","TASK_EXECUTION");var b=p("project","ARTIFACT","second","TASK_EXECUTION");assertEquals(List.of(a),RuntimeNodePermissions.intersect(List.of(a,b),List.of(a),List.of("artifact.read")));}
 @Test void scopeKindOwnerAndPurposeMustBeExact(){var original=p("project","ARTIFACT","first","TASK_EXECUTION");for(var changed:List.of(p("other","ARTIFACT","first","TASK_EXECUTION"),p("project","SOURCE","first","TASK_EXECUTION"),p("project","ARTIFACT","first","REVIEW")))assertThrows(TenantAccessException.class,()->RuntimeNodePermissions.intersect(List.of(original),List.of(changed),List.of("artifact.read")));}
 @Test void tokenCannotAddAnotherNodeAction(){var a=p("project","ARTIFACT","first","TASK_EXECUTION");assertThrows(TenantAccessException.class,()->RuntimeNodePermissions.intersect(List.of(a),List.of(a),List.of("artifact.read","artifact.register")));}
 @Test void resultIsImmutableAndEmptyOrDuplicatesRejected(){var a=p("project","ARTIFACT","first","TASK_EXECUTION");assertThrows(UnsupportedOperationException.class,()->RuntimeNodePermissions.intersect(List.of(a),List.of(a),List.of("artifact.read")).clear());assertThrows(TenantAccessException.class,()->RuntimeNodePermissions.intersect(List.of(a),List.of(),List.of("artifact.read")));assertThrows(TenantAccessException.class,()->RuntimeNodePermissions.intersect(List.of(a),List.of(a,a),List.of("artifact.read")));}
}
