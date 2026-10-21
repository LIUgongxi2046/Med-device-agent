package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Data narrowing only; no test manufactures a current business approval. */
class TaskModelNodePermissionsTest {
 private final JsonMapper json=JsonMapper.builder().build();
 private final PermissionProtocol.Basis model=new PermissionProtocol.Basis("model-version","a".repeat(64));
 private final PermissionProtocol.Scope owner=new PermissionProtocol.Scope("tenant","project","CONFIGURATION","model-owner");
 private ObjectNode witness(String... purposes){
  var root=json.createObjectNode();var operations=root.putObject("rule").putObject("request").putArray("operations");
  for(var purpose:purposes){var op=operations.addObject().put("selector","MODEL_CONFIG").put("action","model.send").put("purpose",purpose);
   op.putObject("model_version").put("version_id",model.version_id()).put("content_sha256",model.content_sha256());}
  return root;
 }
 private ObjectNode operation(ObjectNode root){return (ObjectNode)root.path("rule").path("request").path("operations").get(0);}
 @Test void exactModelRetainsOnlyOriginalExplicitPurposes(){
  var result=TaskModelNodePermissions.declared(model,owner,witness("TASK_EXECUTION","RESEARCH"));
  assertEquals(List.of(new RunTokenAuthoritySchema.Permission(owner,"model.send","TASK_EXECUTION"),new RunTokenAuthoritySchema.Permission(owner,"model.send","RESEARCH")),result);
  assertThrows(UnsupportedOperationException.class,()->result.clear());
 }
 @Test void wrongModelIdOrHashCannotAuthorizeSelectedModel(){
  for(var field:List.of("version_id","content_sha256")){
   var w=witness("RESEARCH");((ObjectNode)operation(w).path("model_version")).put(field,"other");
   assertThrows(TenantAccessException.class,()->TaskModelNodePermissions.declared(model,owner,w));
  }
 }
 @Test void noOperationOrWrongSelectorOrActionFailsClosed(){
  assertThrows(TenantAccessException.class,()->TaskModelNodePermissions.declared(model,owner,witness()));
  for(var field:List.of("selector","action")){
   var w=witness("RESEARCH");operation(w).put(field,"other");
   assertThrows(TenantAccessException.class,()->TaskModelNodePermissions.declared(model,owner,w));
  }
 }
 @Test void duplicateOrMissingPurposeCannotInventAuthority(){
  assertThrows(TenantAccessException.class,()->TaskModelNodePermissions.declared(model,owner,witness("RESEARCH","RESEARCH")));
  var w=witness("RESEARCH");operation(w).remove("purpose");
  assertThrows(TenantAccessException.class,()->TaskModelNodePermissions.declared(model,owner,w));
 }
 @Test void wrongOwnerKindCannotBecomeModelScope(){
  assertThrows(TenantAccessException.class,()->TaskModelNodePermissions.declared(model,new PermissionProtocol.Scope("tenant","project","PROJECT","project"),witness("RESEARCH")));
 }
 @Test void unrelatedModelOperationDoesNotBroadenExactSelection(){
  var w=witness("RESEARCH","OTHER_PURPOSE");var second=(ObjectNode)w.path("rule").path("request").path("operations").get(1);
  ((ObjectNode)second.path("model_version")).put("version_id","other-model");
  assertEquals(1,TaskModelNodePermissions.declared(model,owner,w).size());
 }
}
