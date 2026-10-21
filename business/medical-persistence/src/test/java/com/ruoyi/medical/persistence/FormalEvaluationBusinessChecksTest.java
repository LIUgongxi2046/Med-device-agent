package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** Deferred offline tests. These public fixtures are deliberately not production authority. */
final class FormalEvaluationBusinessChecksTest {
 private final PermissionSchema json=new PermissionSchema();
 FormalEvaluationBusinessChecksTest()throws Exception{}
 private ObjectNode bundle(){var p=json.object();for(String k:List.of("retrieval_ids","question_coverage","selected_refs","conflict_refs","missing_requirement_ids","read_receipt_ids"))p.putArray(k);return p;}
 @Test void validShapeWithoutApprovedCompilerNeverPasses()throws Exception{
  var r=new FormalEvaluationBusinessChecks(json).check("N02",id(),id(),json.object(),bundle(),ref("EvidenceBundle"),SchemaCatalogue.fromClasspath().schemaDigest("artifacts"),List.of());
  assertEquals("WAITING_INPUT",r.state());assertTrue(r.output().required("normalized").isNull());assertFalse(r.output().required("professional_approval").asBoolean());
 }
 @Test void missingRequiredQuestionRemainsExplicitInsufficient()throws Exception{
  var compiler=json.object();compiler.putObject("case_original");var fixed=compiler.putObject("node_business_original");String question=id(),requirement=id();var q=fixed.putArray("questions").addObject().put("question_id",question);q.putArray("requirement_ids").add(requirement);q.putArray("completion_rule_ids");
  var r=new FormalEvaluationBusinessChecks(json).check("N02",id(),id(),compiler,bundle(),ref("EvidenceBundle"),SchemaCatalogue.fromClasspath().schemaDigest("artifacts"),List.of());
  assertEquals("WAITING_INPUT",r.state());assertEquals("INSUFFICIENT",r.output().required("normalized").required("question_coverage").get(0).required("status").asText());assertEquals(requirement,r.output().required("normalized").required("missing_requirement_ids").get(0).asText());
 }
 @Test void inventedReceiptIsRejectedRatherThanConvertedIntoSourceProof()throws Exception{
  var compiler=json.object();compiler.putObject("case_original");compiler.putObject("node_business_original").putArray("questions");var projection=bundle();projection.withArray("read_receipt_ids").add(id());
  assertThrows(TenantAccessException.class,()->new FormalEvaluationBusinessChecks(json).check("N02",id(),id(),compiler,projection,ref("EvidenceBundle"),SchemaCatalogue.fromClasspath().schemaDigest("artifacts"),List.of()));
 }
 @Test void outputSchemaDoesNotPermitProfessionalApproval()throws Exception{
  var r=new FormalEvaluationBusinessChecks(json).check("N02",id(),id(),json.object(),bundle(),ref("EvidenceBundle"),SchemaCatalogue.fromClasspath().schemaDigest("artifacts"),List.of());var forged=(ObjectNode)r.output().deepCopy();forged.put("professional_approval",true);
  assertThrows(SchemaRejection.class,()->FormalEvaluationBusinessSchema.validateOrPublished(FormalEvaluationBusinessSchema.REF,FormalEvaluationBusinessSchema.digest(),ContentReadAuthorization.canonicalBytes(forged)));
 }
 @Test void n07CallerSavedFlagIsNotAnOriginalSavedBody()throws Exception{
  var compiler=json.object();compiler.putObject("case_original");compiler.putObject("node_business_original").put("document_saved",true);compiler.putArray("predecessor_outputs");
  var projection=json.object().put("target_kind","DOCUMENT").put("target_revision",id()).put("target_sha256","a".repeat(64)).put("rule_revision",id()).put("coverage_complete",true);for(String key:List.of("required_claim_ids","required_rule_ids","claim_checks","rule_checks","issue_proposals"))projection.putArray(key);
  var result=new FormalEvaluationBusinessChecks(json).check("N07",id(),id(),compiler,projection,ref("ReviewFindings"),SchemaCatalogue.fromClasspath().schemaDigest("artifacts"),List.of());assertEquals("WAITING_INPUT",result.state());assertTrue(result.output().required("normalized").isNull());assertEquals("APPROVED_SAVED_DOCUMENT_BODY_REQUIRED",result.output().required("blockers").get(0).asText());
 }
 private static String ref(String name){return "https://schemas.medical-agent.invalid/1.0.0/agent-design/artifacts.schema.json#/$defs/"+name;}
 private static String id(){return UUID.randomUUID().toString();}
}
