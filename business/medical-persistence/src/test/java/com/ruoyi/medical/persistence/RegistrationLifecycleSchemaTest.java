package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** Deferred synthetic contract checks only. NOT_RUN; no clinical or regulatory acceptance evidence. */
class RegistrationLifecycleSchemaTest {
 private final RegistrationLifecycleSchema schema=new RegistrationLifecycleSchema();
 private ObjectNode selection(String kind)throws Exception{
  var n=new PermissionSchema().object().put("schema_version","1.0.0").put("scenario_kind",kind).put("product_category","SYNTHETIC").put("catalogue_source_key","");
  n.putArray("section_ids").add("11111111-1111-4111-8111-111111111111");n.putArray("sources");n.putObject("template_version").put("version_id","22222222-2222-4222-8222-222222222222").put("content_sha256","a".repeat(64));
  fact(n,"path_decision");n.putArray("directory");n.putArray("body");n.putArray("consistency");
  if(RegistrationLifecycleSchema.IVD.equals(kind)){fact(n,"intended_use");fact(n,"reagent_classification");n.putArray("analytical_evidence_source_keys");n.putArray("clinical_evidence_source_keys");}
  else{var h=n.putObject("history").put("certificate_number","").put("original_source_key","");h.putArray("change_source_keys");h.putArray("current_source_keys");n.put("history_source_key","");n.putArray(RegistrationLifecycleSchema.CHANGE.equals(kind)?"changes":"conditions");}
  return n;
 }
 private static void fact(ObjectNode n,String key){n.putObject(key).put("text","").putArray("source_keys");}
 @Test void distinctRecordsDoNotCollapseIntoGenericTemplate()throws Exception{assertInstanceOf(RegistrationLifecycleSchema.IvdSelection.class,schema.read(selection(RegistrationLifecycleSchema.IVD)));assertInstanceOf(RegistrationLifecycleSchema.ChangeSelection.class,schema.read(selection(RegistrationLifecycleSchema.CHANGE)));assertInstanceOf(RegistrationLifecycleSchema.RenewalSelection.class,schema.read(selection(RegistrationLifecycleSchema.RENEWAL)));}
 @Test void foreignPathFieldsAreRejected()throws Exception{var n=selection(RegistrationLifecycleSchema.IVD);n.putArray("changes");assertThrows(Exception.class,()->schema.read(n));}
 @Test void prefixCannotSelectMorePermissivePath()throws Exception{String encoded=schema.definition(selection(RegistrationLifecycleSchema.IVD));assertThrows(Exception.class,()->schema.definition(encoded.replace(RegistrationLifecycleSchema.PREFIXES.get(RegistrationLifecycleSchema.IVD),RegistrationLifecycleSchema.PREFIXES.get(RegistrationLifecycleSchema.RENEWAL))));}
 @Test void missingIvdCatalogueIsExplicitlyBlocked()throws Exception{var fixed=new PermissionSchema().object();fixed.set("selection",selection(RegistrationLifecycleSchema.IVD));var result=RegistrationLifecyclePipeline.plan(fixed);assertTrue(result.required("blocked").asBoolean());assertTrue(result.required("unknown_reasons").toString().contains("IVD_APPROVED_CATALOGUE_MISSING"));assertFalse(result.required("legal_approval").asBoolean());}
 @Test void missingHistoryAndRenewalConditionsCannotPass()throws Exception{var fixed=new PermissionSchema().object();fixed.set("selection",selection(RegistrationLifecycleSchema.RENEWAL));var result=RegistrationLifecyclePipeline.plan(fixed);assertTrue(result.required("blocked").asBoolean());assertTrue(result.required("unknown_reasons").toString().contains("ORIGINAL_CERTIFICATE_AND_COMPLETE_HISTORY_MISSING"));assertTrue(result.required("unknown_reasons").toString().contains("MANDATORY_STANDARD_UPDATE"));}
}
