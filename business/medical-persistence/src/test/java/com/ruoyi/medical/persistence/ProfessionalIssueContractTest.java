package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** Deferred pure contract/coverage source. Does not claim database, physical object, or revocation certification. */
class ProfessionalIssueContractTest {
 private static String id(int n){return String.format("00000000-0000-4000-8000-%012d",n);}
 private ObjectNode request(String decision)throws Exception{
  return (ObjectNode)new TaskSourceVerifierSchema().tree(new LinkedHashMap<String,Object>(){{put("schema_version","1.0.0");put("decision",decision);put("document_revision",id(1));put("issue_revision","1");put("evidence_watermark","2");put("issue_watermark","3");put("assignee_id",null);put("new_check_id",null);put("reason","Verified corresponding result.");}});
 }
 @Test void resolveRequiresNewCheckAndRejectsClientSuccessClaims()throws Exception{
  var schema=new ProfessionalIssueSchema();var n=request("RESOLVE");assertThrows(SchemaRejection.class,()->schema.validate(n));n.put("new_check_id",id(2));assertDoesNotThrow(()->schema.validate(n));n.put("professional_approval",true);assertThrows(SchemaRejection.class,()->schema.validate(n));
 }
 @Test void assignRequiresAssigneeAndForbidsBorrowedCheck()throws Exception{
  var schema=new ProfessionalIssueSchema();var n=request("ASSIGN");assertThrows(SchemaRejection.class,()->schema.validate(n));n.put("assignee_id",id(3));assertDoesNotThrow(()->schema.validate(n));n.put("new_check_id",id(2));assertThrows(SchemaRejection.class,()->schema.validate(n));
 }
 @Test void reopenCannotSupplyAResolutionOrSilentWhitespaceReason()throws Exception{
  var schema=new ProfessionalIssueSchema();var n=request("REOPEN");assertDoesNotThrow(()->schema.validate(n));n.put("reason","  ");assertThrows(SchemaRejection.class,()->schema.validate(n));n.put("reason","Reproduced finding.").put("new_check_id",id(4));assertThrows(SchemaRejection.class,()->schema.validate(n));
 }
 @Test void completeCannotOmitAnyRequiredClaimOrRule(){
  var f=new ArtifactProtocol.ReviewFindings(ArtifactProtocol.TargetKind.DOCUMENT,id(1),"a".repeat(64),id(2),List.of(id(3)),List.of(id(4)),List.of(),List.of(),List.of(),true);
  assertThrows(TenantAccessException.class,()->ProfessionalCheckService.checkCoverage(f));
 }
 @Test void explicitlyIncompleteCoverageNeverGetsUpgradedByConsumer(){
  var f=new ArtifactProtocol.ReviewFindings(ArtifactProtocol.TargetKind.DOCUMENT,id(1),"a".repeat(64),id(2),List.of(id(3)),List.of(id(4)),List.of(),List.of(),List.of(),false);
  assertDoesNotThrow(()->ProfessionalCheckService.checkCoverage(f));assertFalse(f.coverageComplete());
 }
 @Test void duplicateChecksCannotSimulateCoverage(){
  var c=new ArtifactProtocol.ClaimCheck(id(3),ArtifactProtocol.AccessCheck.PASS,ArtifactProtocol.LocatorCheck.PASS,ArtifactProtocol.SupportCheck.SUPPORTED,List.of(id(4)),List.of(id(5)),"Exact original read.");
  var f=new ArtifactProtocol.ReviewFindings(ArtifactProtocol.TargetKind.DOCUMENT,id(1),"a".repeat(64),id(2),List.of(id(3)),List.of(id(4)),List.of(c,c),List.of(),List.of(),false);
  assertThrows(TenantAccessException.class,()->ProfessionalCheckService.checkCoverage(f));
 }
 @Test void checkProofCannotBeCreatedThroughPublicConstructor(){assertEquals(0,ProfessionalCheckService.Verified.class.getConstructors().length);}
}
