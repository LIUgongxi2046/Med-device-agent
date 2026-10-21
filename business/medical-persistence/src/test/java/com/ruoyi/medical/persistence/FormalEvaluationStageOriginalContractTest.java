package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Deferred, offline contract regressions only. Test values never confer production authority. */
final class FormalEvaluationStageOriginalContractTest {
 private final PermissionSchema json=new PermissionSchema();
 FormalEvaluationStageOriginalContractTest()throws Exception{}
 @Test void ruleOriginalRequiresApprovedContentCopyNotJustSelector()throws Exception{
  var schema=new FormalEvaluationStageInputSchema();var copy=json.object().put("rule_id",id());copy.set("rule_revision",json.tree(new PermissionProtocol.Basis(id(),"a".repeat(64))));
  assertThrows(SchemaRejection.class,()->schema.validate("1.0.0","RuleOriginal",copy));
  var content=copy.putObject("content").put("schema_version","1.0.0").put("engine_revision","spring-spel-6.2.19-boolean-v1");content.putArray("basis_manifest").add(json.tree(new PermissionProtocol.Basis(id(),"b".repeat(64))));
  var expression=content.putObject("expression").put("schema_version","1.0.0").put("engine_revision","spring-spel-6.2.19-boolean-v1").put("expression","true");expression.putArray("input_keys");expression.putArray("constants");
  assertDoesNotThrow(()->schema.validate("1.0.0","RuleOriginal",copy));content.put("approved",true);assertThrows(SchemaRejection.class,()->schema.validate("1.0.0","RuleOriginal",copy));
 }
 @Test void explicitNestedVersionDoesNotDefaultMissingOrUnsupportedEnvelopeVersions()throws Exception{
  var schema=new FormalEvaluationStageInputSchema();var missing=json.object();
  assertThrows(SchemaRejection.class,()->schema.validate("Case",missing));
  assertThrows(SchemaRejection.class,()->schema.validate("3.0.0","RuleOriginal",missing));
 }
 @Test void sameBusinessRuleCannotSelectTwoRevisions(){
  var original=json.object().put("node_kind","N02");var inputs=original.putObject("node_business_original").putArray("completion_inputs");String rule=id();for(int i=0;i<2;i++){var input=inputs.addObject().put("rule_id",rule);input.set("rule_revision",json.tree(new PermissionProtocol.Basis(id(),"a".repeat(64))));}
  assertThrows(TenantAccessException.class,()->FormalEvaluationCaseOriginals.selectedRules(original));
 }
 @Test void businessOutputIsPublishedAndStillCannotApprove()throws Exception{
  var catalogue=SchemaCatalogue.fromClasspath();assertEquals(FormalEvaluationBusinessSchema.digest(),catalogue.schemaDigest("evaluation-business-output"));
  var output=json.object().put("schema_version","1.0.0").put("node_kind","N03").put("projection_schema_ref",catalogue.schemaRef("artifacts","FieldCheckResult")).put("projection_schema_sha256",catalogue.schemaDigest("artifacts")).put("professional_approval",false);var projection=output.putObject("projection").put("target_artifact_id",id()).put("rule_revision",id());projection.putArray("entries");projection.putArray("method_coverage");projection.putArray("missing_requirement_ids");output.putNull("normalized");output.putNull("body");output.putArray("blockers").add("MISSING_ORIGINAL");
  assertDoesNotThrow(()->catalogue.bindPublished(FormalEvaluationBusinessSchema.REF,FormalEvaluationBusinessSchema.digest(),ContentReadAuthorization.canonicalBytes(output)));
  output.put("professional_approval",true);assertThrows(SchemaRejection.class,()->FormalEvaluationBusinessSchema.validateOrPublished(FormalEvaluationBusinessSchema.REF,FormalEvaluationBusinessSchema.digest(),ContentReadAuthorization.canonicalBytes(output)));
 }
 @Test void assemblyApplicabilityIsAnOriginalTemplateViewNotBooleanFlags()throws Exception{
  var catalogue=SchemaCatalogue.fromClasspath();var definition=catalogue.definitionDocument(FormalEvaluationStageInputSchema.URI+"#/$defs/N06");var applicability=definition.required("properties").required("applicability");assertTrue(applicability.has("$ref"));assertTrue(applicability.required("$ref").asText().contains("TemplateView"));
 }
 private static String id(){return UUID.randomUUID().toString();}
}
