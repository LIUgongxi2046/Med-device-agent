package com.ruoyi.medical.persistence;
import com.ruoyi.medical.contracts.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;
class PublishedRunCreateAdmissionTest {
 private PublishedRunCreateAdmission absent(){var beans=new StaticListableBeanFactory();return new PublishedRunCreateAdmission(
  beans.getBeanProvider(AgentPublicationAuthority.class),beans.getBeanProvider(RuntimeRetentionAuthority.class),null,null,null,null,null,null);}
 @Test void missingPublicationCannotBecomeAQueuedRun(){assertEquals(ErrorCode.DEPENDENCY_UNAVAILABLE,
  assertThrows(TenantAccessException.class,()->absent().prepare(null,"1",null,null)).code());}
 @Test void unpreparedConsumerCannotPerformObjectIoInsideTransaction(){assertEquals(ErrorCode.DEPENDENCY_UNAVAILABLE,
  assertThrows(TenantAccessException.class,()->absent().require(null,null,null,null,null,null,null)).code());}
 @Test void physicalPreparationRejectsAnActiveTransaction(){TransactionSynchronizationManager.setActualTransactionActive(true);try{
  assertEquals(ErrorCode.ACCESS_DENIED,assertThrows(TenantAccessException.class,()->absent().prepare(null,"1",null,null)).code());
 }finally{TransactionSynchronizationManager.setActualTransactionActive(false);}}
 @Test void legacyConsumerDoesNotGetImplicitPreparationAuthority(){RunCreateAdmission old=(a,t,p,s,r,run,principal)->{throw new AssertionError();};
  assertEquals(ErrorCode.DEPENDENCY_UNAVAILABLE,assertThrows(TenantAccessException.class,()->old.prepare(null,"1",null,null)).code());}
 @Test void additionalAssemblyWitnessPreservesExistingDelegations(){
  var mapper=new com.fasterxml.jackson.databind.ObjectMapper();
  var prior=mapper.createObjectNode();prior.putObject("N04").put("decision","original");
  var merged=PublishedRunCreateAdmission.mergeAuthorizations(prior,mapper.createObjectNode().put("status","NOT_REQUIRED"));
  assertEquals(prior.get("N04"),merged.get("N04"));assertFalse(prior.has("candidate_assembly_cross_file_inputs"));
  assertEquals("NOT_REQUIRED",merged.path("candidate_assembly_cross_file_inputs").path("status").asText());
 }
 @Test void assemblyCannotOverwriteAnExistingWitness(){
  var mapper=new com.fasterxml.jackson.databind.ObjectMapper();var prior=mapper.createObjectNode();prior.putObject("candidate_assembly_cross_file_inputs");
  assertEquals(ErrorCode.CONTENT_MISMATCH,assertThrows(TenantAccessException.class,()->PublishedRunCreateAdmission.mergeAuthorizations(prior,mapper.createObjectNode())).code());
 }
 @Test void dependencyUnionDeduplicatesOnlyExactVersionHashes(){
  var first=new com.ruoyi.medical.persistence.PermissionProtocol.Basis("00000000-0000-4000-8000-000000000001","a".repeat(64));
  var changed=new com.ruoyi.medical.persistence.PermissionProtocol.Basis(first.version_id(),"b".repeat(64));
  assertEquals(java.util.List.of(first),PublishedRunCreateAdmission.mergeVersions(java.util.List.of(first),java.util.List.of(first)));
  assertEquals(ErrorCode.CONTENT_MISMATCH,assertThrows(TenantAccessException.class,()->PublishedRunCreateAdmission.mergeVersions(java.util.List.of(first),java.util.List.of(changed))).code());
 }
}
