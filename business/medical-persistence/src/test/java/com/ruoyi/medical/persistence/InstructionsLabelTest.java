package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.ruoyi.medical.persistence.InstructionsLabelSchema.*;

/** Deferred synthetic contract tests. No fixture is actual product data, regulatory rule or approval. */
class InstructionsLabelTest {
 private String id(int n){return String.format("00000000-0000-4000-8000-%012d",n);}
 private Source source(String key,String literal,String nature,int n){var evidence=new DatasetProtocol.EvidenceRef("1.0.0",id(90),id(91),id(5),id(n),id(2),"synthetic-chunk",DatasetProtocol.LocatorKind.TXT,new DatasetProtocol.TextLocator("TXT","UTF-8","PRESERVE",0,1,1,1),"a".repeat(64),id(3));return new Source(key,new PermissionProtocol.Basis(id(n),"a".repeat(64)),id(4),evidence,literal,nature);}
 private Selection fixture(String stage,String layout,List<Field> fields,List<Rule> rules)throws Exception{
  var schema=new InstructionsLabelSchema();var empty=new Fact("",List.of());var basis=new Fact("synthetic",List.of("basis"));
  var ifuRules=new Rules("IFU",stage,"synthetic-product","FULL",rules,empty,basis);var labelRules=new Rules("LABEL",stage,"synthetic-product",layout,List.of(),basis,basis);
  return new Selection("1.0.0",KIND,List.of(id(20),id(21)),List.of(source("ifu_rules",schema.tree(ifuRules).toString(),"APPROVED_RULES",1),source("label_rules",schema.tree(labelRules).toString(),"APPROVED_RULES",6),source("basis","synthetic","BASIS",7)),List.of(new Document("IFU","ifu-v1",stage,"synthetic-product","FULL","ifu_rules",basis,fields),new Document("LABEL","label-v1",stage,"synthetic-product",layout,"label_rules",basis,List.of())),List.of(),List.of(),"UNKNOWN",empty,empty,empty,empty,List.of());
 }
 private JsonNode plan(Selection selection)throws Exception{return InstructionsLabelPipeline.plan(new InstructionsLabelSchema().tree(Map.of("selection",selection)));}
 private void blocked(JsonNode result,String reason){assertTrue(result.required("blocked").asBoolean());assertTrue(result.required("unknown_reasons").toString().contains(reason));}
 @Test void incompleteSourcesRemainUnknown()throws Exception{var r=plan(fixture("APPLICATION_DRAFT","FULL",List.of(),List.of()));blocked(r,"RULE_CATALOGUE_INCOMPLETE:IFU");blocked(r,"RULE_CATALOGUE_INCOMPLETE:LABEL");blocked(r,"IMPORT_STATUS_UNKNOWN");assertFalse(r.required("regulatory_approval").asBoolean());}
 @Test void limitedLabelCannotInventMinimumContent()throws Exception{blocked(plan(fixture("APPLICATION_DRAFT","SPACE_LIMITED",List.of(),List.of())),"LABEL_MINIMUM_CONTENT_NOT_APPROVED");}
 @Test void unknownFieldCannotBeConfirmed()throws Exception{var empty=new Fact("",List.of());var field=new Field("name","IFU-01","UNKNOWN",empty,empty);blocked(plan(fixture("APPLICATION_DRAFT","FULL",List.of(field),List.of(new Rule("IFU-01",List.of("name"),true,true,false)))),"FIELD_APPLICABILITY_UNKNOWN:IFU:name");}
 @Test void marketVersionCannotDeferRegistration()throws Exception{var empty=new Fact("",List.of());var field=new Field("registration","IFU-04","PENDING_REGISTRATION",empty,empty);blocked(plan(fixture("APPROVED_MARKET","FULL",List.of(field),List.of(new Rule("IFU-04",List.of("registration"),true,true,true)))),"REGISTRATION_DEFERRAL_NOT_ALLOWED:IFU:registration");}
 @Test void applicationDeferralNeverInventsNumber()throws Exception{var empty=new Fact("",List.of());var field=new Field("registration","IFU-04","PENDING_REGISTRATION",empty,new Fact("synthetic",List.of("basis")));blocked(plan(fixture("APPLICATION_DRAFT","FULL",List.of(field),List.of(new Rule("IFU-04",List.of("registration"),true,true,true)))),"REGISTRATION_DEFERRAL_NOT_ALLOWED:IFU:registration");}
 @Test void ordinarySourceDoesNotCreateWarningOrProductApproval()throws Exception{var basis=new Fact("synthetic",List.of("basis"));var field=new Field("warning","IFU-07","REQUIRED",basis,basis);var r=plan(fixture("APPLICATION_DRAFT","FULL",List.of(field),List.of(new Rule("IFU-07",List.of("warning"),true,true,false))));blocked(r,"ACTUAL_APPROVED_SOURCE_MISSING:IFU:warning");blocked(r,"PRODUCT_FACT_COMPARISON_MISSING:IFU:warning");blocked(r,"DECISION_UNREADABLE:IFU:warning");}
 @Test void independentDocumentVersionsArePreserved()throws Exception{var schema=new InstructionsLabelSchema();var original=fixture("APPLICATION_DRAFT","FULL",List.of(),List.of());var restored=schema.definition(schema.definition(schema.tree(original)));assertEquals("ifu-v1",restored.documents().get(0).version());assertEquals("label-v1",restored.documents().get(1).version());}
 @Test void injectedApprovalIsRejected()throws Exception{var schema=new InstructionsLabelSchema();var value=(ObjectNode)schema.tree(fixture("APPLICATION_DRAFT","FULL",List.of(),List.of()));value.put("regulatory_approved",true);assertThrows(Exception.class,()->schema.read(value));}
 @Test void changedStageChangesImmutableSelection()throws Exception{var schema=new InstructionsLabelSchema();assertNotEquals(schema.definition(schema.tree(fixture("APPLICATION_DRAFT","FULL",List.of(),List.of()))),schema.definition(schema.tree(fixture("APPROVED_MARKET","FULL",List.of(),List.of()))));}
 @Test void sourceFreeDraftIsUnknownNotFabricated()throws Exception{var empty=new Fact("",List.of());var s=new Selection("1.0.0",KIND,List.of(id(20)),List.of(),List.of(new Document("IFU","pending-ifu","APPLICATION_DRAFT","pending-product","FULL","",empty,List.of()),new Document("LABEL","pending-label","APPLICATION_DRAFT","pending-product","FULL","",empty,List.of())),List.of(),List.of(),"UNKNOWN",empty,empty,empty,empty,List.of());blocked(plan(s),"APPROVED_RULES_MISSING:IFU");blocked(plan(s),"APPROVED_RULES_MISSING:LABEL");}

}
