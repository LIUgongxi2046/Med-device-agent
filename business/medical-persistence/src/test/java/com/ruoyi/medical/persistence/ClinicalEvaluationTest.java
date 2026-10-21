package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.ruoyi.medical.persistence.ClinicalEvaluationSchema.*;

/** Deferred synthetic tests, not executed. Fixtures are not professional or clinical evidence. */
class ClinicalEvaluationTest {
 private String id(int n){return String.format("00000000-0000-4000-8000-%012d",n);}
 private Source source(String key,String literal,String nature,int n){
  var evidence=new DatasetProtocol.EvidenceRef("1.0.0",id(90),id(91),id(5),id(n),id(2),"synthetic-chunk",DatasetProtocol.LocatorKind.TXT,new DatasetProtocol.TextLocator("TXT","UTF-8","PRESERVE",0,1,1,1),"a".repeat(64),id(3));
  return new Source(key,new PermissionProtocol.Basis(id(n),"a".repeat(64)),id(4),evidence,literal,nature,"synthetic document","synthetic number","synthetic location");
 }
 private Selection selection(String path,String nature,List<String> components,List<ClinicalEvaluationSchema.Record> records)throws Exception{
  var schema=new ClinicalEvaluationSchema();String approval=schema.tree(new PathApproval(path,components,"APPROVED")).toString();
  return new Selection("1.0.0",KIND,List.of(id(20)),List.of(source("decision",approval,nature,1),source("ordinary","synthetic","BASIS",6)),path,components,new Fact(approval,List.of("decision")),records,List.of(),List.of());
 }
 private JsonNode plan(Selection s)throws Exception{var schema=new ClinicalEvaluationSchema();return ClinicalEvaluationPipeline.plan(schema.tree(Map.of("selection",s)));}
 private void reason(JsonNode result,String text){assertTrue(result.required("blocked").asBoolean());assertTrue(result.required("unknown_reasons").toString().contains(text));}
 @Test void unknownPathNeverConfersApproval()throws Exception{var result=plan(selection("UNKNOWN","BASIS",List.of(),List.of()));reason(result,"PROFESSIONAL_PATH_APPROVAL_MISSING");reason(result,"CLINICAL_PATH_UNKNOWN");assertFalse(result.required("clinical_approval").asBoolean());}
 @Test void exemptionNeedsCatalogueAndDomesticComparison()throws Exception{var result=plan(selection("EXEMPTION","APPROVED_DECISION",List.of(),List.of()));reason(result,"REQUIRED_CLINICAL_GROUP_MISSING:EXEMPTION");reason(result,"COMPARISON_ITEM_MISSING:EXEMPTION:CATALOGUE_ENTRY:PRINCIPLE");reason(result,"COMPARISON_ITEM_MISSING:EXEMPTION:DOMESTIC_DEVICE:PRINCIPLE");}
 @Test void combinedPathDoesNotDropTrialOrComparableData()throws Exception{var result=plan(selection("COMBINATION","APPROVED_DECISION",List.of("COMPARABLE_DEVICE","CLINICAL_TRIAL"),List.of()));reason(result,"REQUIRED_CLINICAL_GROUP_MISSING:TRIAL");reason(result,"REQUIRED_CLINICAL_GROUP_MISSING:LITERATURE");reason(result,"COMPARISON_ITEM_MISSING:COMPARABLE_DEVICE:DEVICE:INTENDED_USE");}
 @Test void sourceLabelCannotApproveDifferentPath()throws Exception{var schema=new ClinicalEvaluationSchema();var value=(ObjectNode)schema.tree(selection("EXEMPTION","APPROVED_DECISION",List.of(),List.of()));value.put("path","CLINICAL_TRIAL");reason(ClinicalEvaluationPipeline.plan(schema.tree(Map.of("selection",value))),"PROFESSIONAL_PATH_DECISION_MISMATCH");}
 @Test void trialPlanCannotStandInForActualResultsAndAttachments()throws Exception{
  var fact=new Fact("synthetic",List.of("ordinary"));var trial=new ClinicalEvaluationSchema.Record("trial","TRIAL","REQUIRED",fact,List.of(new Field("RESULTS",fact)),List.of("ordinary"));
  var result=plan(selection("CLINICAL_TRIAL","APPROVED_DECISION",List.of(),List.of(trial)));reason(result,"ACTUAL_TRIAL_ATTACHMENT_MISSING:trial:ETHICS_APPROVAL");reason(result,"ACTUAL_TRIAL_ATTACHMENT_MISSING:trial:ORIGINAL_DATABASE");reason(result,"ACTUAL_CLINICAL_SOURCE_MISSING:trial:RESULTS");
 }
 @Test void missingEquivalenceNeverPermitsExemption()throws Exception{
  var fact=new Fact("synthetic",List.of("ordinary"));var exemption=new ClinicalEvaluationSchema.Record("exemption","EXEMPTION","REQUIRED",fact,List.of(new Field("EQUIVALENCE_DECISION",fact)),List.of());
  reason(plan(selection("EXEMPTION","APPROVED_DECISION",List.of(),List.of(exemption))),"EXEMPTION_NOT_ESTABLISHED_CLINICAL_EVALUATION_REQUIRED");
 }
 @Test void injectedApprovalFieldIsRejected()throws Exception{var schema=new ClinicalEvaluationSchema();var value=(ObjectNode)schema.tree(selection("UNKNOWN","BASIS",List.of(),List.of()));value.put("clinical_approved",true);assertThrows(Exception.class,()->schema.read(value));}
 @Test void pathChangesCreateDifferentImmutableDefinition()throws Exception{var schema=new ClinicalEvaluationSchema();assertNotEquals(schema.definition(schema.tree(selection("EXEMPTION","APPROVED_DECISION",List.of(),List.of()))),schema.definition(schema.tree(selection("CLINICAL_TRIAL","APPROVED_DECISION",List.of(),List.of()))));}
 @Test void schemaRoundTripPreservesUnknownRatherThanInventingEvidence()throws Exception{var schema=new ClinicalEvaluationSchema();var s=new Selection("1.0.0",KIND,List.of(id(20)),List.of(),"UNKNOWN",List.of(),new Fact("",List.of()),List.of(),List.of(),List.of());assertEquals(s,schema.definition(schema.definition(schema.tree(s))));reason(plan(s),"PROFESSIONAL_PATH_APPROVAL_MISSING");}
 @Test void completeSyntheticTrialPlanStillRequiresProfessionalReview()throws Exception{
  var schema=new ClinicalEvaluationSchema();var sources=new ArrayList<Source>();var decision=schema.tree(new PathApproval("CLINICAL_TRIAL",List.of(),"APPROVED")).toString();sources.add(source("path",decision,"APPROVED_DECISION",1));
  var natures=List.of("APPROVED_DECISION","SIGNATURE","QUALIFICATION","ETHICS_APPROVAL","TRIAL_PLAN","CONSENT_SAMPLE","TRIAL_REPORT","ORIGINAL_DATABASE","ANALYSIS_CODE");for(int i=0;i<natures.size();i++)sources.add(source(natures.get(i),"synthetic",natures.get(i),30+i));
  var fields=new LinkedHashMap<String,List<String>>();
  fields.put("COVER",List.of("PRODUCT_NAME","MODELS","SIGNATURE","COMPLETION_DATE"));
  fields.put("PRODUCT",List.of("BASIC_INFORMATION","INTENDED_USE_POPULATION_SITE","CONTACT_MODE_DURATION","DISEASE_STAGE","USE_CONDITIONS_REUSE","CONTRAINDICATIONS_WARNINGS","DEVELOPMENT_BACKGROUND","PRINCIPLE","EXISTING_METHODS_EXPECTED_EFFECT"));
  fields.put("SCOPE",List.of("EVALUATION_SCOPE","COMPONENTS","EXCLUSIONS_AND_COMBINED_USE_BASIS"));
  fields.put("PATH",List.of("RATIONALE","PRODUCT_CHARACTERISTICS","EXISTING_EVIDENCE","ADDITIONAL_EVIDENCE"));
  fields.put("CONCLUSION",List.of("ESSENTIAL_PRINCIPLES","CLAIMS_SUPPORT","SAFETY_CLINICAL_PERFORMANCE","BENEFIT_RISK","POST_MARKET_STUDIES","LIMITATIONS_UNRESOLVED_GAPS"));
  fields.put("EVALUATOR",List.of("QUALIFICATIONS","EXPERIENCE","RESPONSIBILITIES"));fields.put("OTHER",List.of("SUPPLEMENTARY_INFORMATION_ATTACHMENTS"));
  fields.put("TRIAL",List.of("DESIGN_BASIS","INSTITUTION","TIME","ENDPOINTS","INCLUSION_EXCLUSION","SAMPLE_SIZE","FOLLOWUP","RESULTS","ANALYSIS_EXPLANATION"));
  var records=new ArrayList<ClinicalEvaluationSchema.Record>();var bodies=new ArrayList<Body>();var approved=new Fact("synthetic",List.of("APPROVED_DECISION"));int i=60;
  for(var e:fields.entrySet()){String key="record_"+e.getKey();var values=new ArrayList<Field>();for(String name:e.getValue()){String nature=switch(name){case "SIGNATURE" -> "SIGNATURE";case "QUALIFICATIONS" -> "QUALIFICATION";case "RESULTS" -> "TRIAL_REPORT";default -> "APPROVED_DECISION";};values.add(new Field(name,new Fact("synthetic",List.of(nature))));}records.add(new ClinicalEvaluationSchema.Record(key,e.getKey(),"REQUIRED",approved,values,"TRIAL".equals(e.getKey())?natures:List.of()));bodies.add(new Body(key,id(i++),id(20),e.getKey(),List.of(key),schema.tree(Map.of("kind","TEXT","text","synthetic")),List.of("APPROVED_DECISION")));}
  bodies.add(new Body("path_body",id(80),id(20),"PATH",List.of("PATH"),schema.tree(Map.of("kind","TEXT","text",decision)),List.of("path")));
  var result=plan(new Selection("1.0.0",KIND,List.of(id(20)),sources,"CLINICAL_TRIAL",List.of(),new Fact(decision,List.of("path")),records,List.of(),bodies));
  assertFalse(result.required("blocked").asBoolean());assertEquals("READY_FOR_PROFESSIONAL_REVIEW",result.required("status").asText());assertTrue(result.required("human_review_required").asBoolean());assertFalse(result.required("clinical_approval").asBoolean());
 }

}
