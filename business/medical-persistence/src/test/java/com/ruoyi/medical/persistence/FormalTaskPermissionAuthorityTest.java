package com.ruoyi.medical.persistence;
import com.ruoyi.medical.contracts.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Pure narrowing tests do not assert that a publication or delegation was approved. */
class FormalTaskPermissionAuthorityTest {
 private static final String TENANT="00000000-0000-4000-8000-000000000001", PROJECT="00000000-0000-4000-8000-000000000002", TASK="00000000-0000-4000-8000-000000000003";
 private static RunTokenAuthoritySchema.Permission permission(String kind,String id,String action,String purpose){
  return new RunTokenAuthoritySchema.Permission(new PermissionProtocol.Scope(TENANT,PROJECT,kind,id),action,purpose);
 }
 @Test void exactSubsetDoesNotIncludeUnrequestedRolePermissions(){
  var read=permission("TASK",TASK,"task.read","TASK_EXECUTION");var run=permission("TASK",TASK,"task.run","TASK_EXECUTION");
  assertEquals(List.of(read),FormalTaskPermissionAuthority.narrow(List.of(read,run),List.of(read)));
 }
 @Test void projectScopeIsNotAResourceWildcard(){
  var parent=permission("PROJECT",PROJECT,"task.run","TASK_EXECUTION");var task=permission("TASK",TASK,"task.run","TASK_EXECUTION");
  assertEquals(ErrorCode.ACCESS_DENIED,assertThrows(TenantAccessException.class,()->FormalTaskPermissionAuthority.narrow(List.of(parent),List.of(task))).code());
 }
 @Test void actionDoesNotErasePurposeOrOwner(){
  var read=permission("TASK",TASK,"task.read","TASK_EXECUTION");
  assertThrows(TenantAccessException.class,()->FormalTaskPermissionAuthority.narrow(List.of(read),List.of(permission("TASK",TASK,"task.read","TASK_READING"))));
  assertThrows(TenantAccessException.class,()->FormalTaskPermissionAuthority.narrow(List.of(read),List.of(permission("TASK",PROJECT,"task.read","TASK_EXECUTION"))));
 }
 @Test void emptyAndDuplicateSetsCannotProduceAuthority(){
  var read=permission("TASK",TASK,"task.read","TASK_EXECUTION");
  assertThrows(TenantAccessException.class,()->FormalTaskPermissionAuthority.narrow(List.of(read),List.of()));
  assertThrows(TenantAccessException.class,()->FormalTaskPermissionAuthority.narrow(List.of(read,read),List.of(read)));
  assertThrows(TenantAccessException.class,()->FormalTaskPermissionAuthority.narrow(List.of(read),List.of(read,read)));
 }
 @Test void registeredArtifactSelectorGrantsNoPrincipalOrTokenAction(){
  var permissions=FormalTaskPermissionAuthority.registeredArtifactTokenPermissions();
  assertTrue(permissions.isEmpty());
  var read=permission("TASK",TASK,"task.read","TASK_EXECUTION");
  var principal=new java.util.ArrayList<>(List.of(read));principal.addAll(permissions);
  assertTrue(principal.stream().noneMatch(p->"ARTIFACT".equals(p.scope().resource_kind())));
  assertThrows(TenantAccessException.class,()->RuntimeNodePermissions.intersect(principal,List.of(read),List.of("artifact.read")));
 }
}
