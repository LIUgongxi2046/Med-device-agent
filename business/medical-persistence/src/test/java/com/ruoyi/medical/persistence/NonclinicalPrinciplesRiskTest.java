package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.ruoyi.medical.persistence.NonclinicalPrinciplesRiskSchema.*;

/** Deferred synthetic contract tests. Not executed and not clinical, product or regulatory evidence. */
class NonclinicalPrinciplesRiskTest {
 private String id(int n){return String.format("00000000-0000-4000-8000-%012d",n);}
 private Selection selection(String status,Fact result,String nature){
  var evidence=new DatasetProtocol.EvidenceRef("1.0.0",id(90),id(91),id(5),id(1),id(2),"synthetic-chunk",DatasetProtocol.LocatorKind.TXT,new DatasetProtocol.TextLocator("TXT","UTF-8","PRESERVE",0,1,1,1),"a".repeat(64),id(3));
  var src=new Source("s",new PermissionProtocol.Basis(id(1),"a".repeat(64)),id(4),evidence,"synthetic",nature,"synthetic document","synthetic number","synthetic page");
  var fact=new Fact("synthetic",List.of("s"));var empty=new Fact("",List.of());
  var study=new Study("study","MATERIAL","synthetic-v1",status,fact,fact,fact,fact,fact,fact,fact,fact,empty,result,empty,result,empty,empty,List.of(),List.of());
  var applicability=GROUPS.stream().sorted().map(g->new Applicability(g,"MATERIAL".equals(g)?"REQUIRED":"NOT_APPLICABLE",fact)).toList();
  return new Selection("1.0.0",KIND,List.of(id(20)),List.of(src),"s",applicability,List.of(study),List.of(),List.of(),new RiskSummary(empty,empty,empty,empty,empty,empty,empty),List.of(),List.of(),List.of());
 }
 private com.fasterxml.jackson.databind.JsonNode plan(Selection s)throws Exception{var schema=new NonclinicalPrinciplesRiskSchema();return NonclinicalPrinciplesRiskPipeline.plan(schema.tree(Map.of("selection",s)));}
 @Test void planCannotBecomeCompletedResearch()throws Exception{var result=plan(selection("PLAN_ONLY",new Fact("synthetic",List.of("s")),"PLAN"));assertTrue(result.required("blocked").asBoolean());assertTrue(result.required("unknown_reasons").toString().contains("PLAN_CANNOT_DECLARE_ACTUAL_RESULTS:study"));}
 @Test void declaredCompletionNeedsActualDataAndReport()throws Exception{var result=plan(selection("COMPLETED",new Fact("synthetic",List.of("s")),"PLAN"));assertTrue(result.required("unknown_reasons").toString().contains("ACTUAL_DATA_REPORT_CONCLUSION_MISSING:study"));}
 @Test void allConditionalResearchGroupsMustBeExplicit()throws Exception{var schema=new NonclinicalPrinciplesRiskSchema();var value=(com.fasterxml.jackson.databind.node.ObjectNode)schema.tree(selection("PLAN_ONLY",new Fact("",List.of()),"PLAN"));value.putArray("applicability");assertThrows(SchemaRejection.class,()->schema.read(value));}
 @Test void unknownFieldsCannotSmuggleApproval()throws Exception{var schema=new NonclinicalPrinciplesRiskSchema();var value=(com.fasterxml.jackson.databind.node.ObjectNode)schema.tree(selection("PLAN_ONLY",new Fact("",List.of()),"PLAN"));value.put("regulatory_approved",true);assertThrows(Exception.class,()->schema.read(value));}
 @Test void changingEvidenceChangesFixedSelectionBytes()throws Exception{var schema=new NonclinicalPrinciplesRiskSchema();var first=schema.definition(schema.tree(selection("PLAN_ONLY",new Fact("",List.of()),"PLAN")));var second=schema.definition(schema.tree(selection("COMPLETED",new Fact("",List.of()),"PLAN")));assertNotEquals(first,second);}
}
