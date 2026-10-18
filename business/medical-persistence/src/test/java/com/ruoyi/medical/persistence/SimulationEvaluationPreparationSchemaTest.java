package com.ruoyi.medical.persistence;
import org.junit.jupiter.api.Test;
import com.ruoyi.medical.contracts.*;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;
class SimulationEvaluationPreparationSchemaTest {
 final SimulationEvaluationPreparationSchema schema=new SimulationEvaluationPreparationSchema();
 SimulationEvaluationPreparationSchemaTest()throws Exception { }
 @Test void duplicateKeysTrailingJsonAndNonCanonicalBytesFailClosed()throws Exception {
  assertThrows(Exception.class,()->schema.parse("{\"a\":1,\"a\":2}".getBytes(StandardCharsets.UTF_8)));
  assertThrows(Exception.class,()->schema.parse("{} {}".getBytes(StandardCharsets.UTF_8)));
  assertThrows(TenantAccessException.class,()->schema.canonicalDocument("{ \"a\": \"b\" }".getBytes(StandardCharsets.UTF_8)));
  assertEquals("b",schema.canonicalDocument("{\"a\":\"b\"}".getBytes(StandardCharsets.UTF_8)).required("a").textValue());
 }
 @Test void registerHasClosedKindsEnterpriseScopeAndNoCallerObjectOrGrantFields()throws Exception {
  var p=new PermissionSchema();var request=p.object().put("schema_version","1.0.0").putNull("project_id").put("kind","EXPECTED_ITEMS")
   .put("source_version",EvaluationFixture.id(1)).put("rights_revision",EvaluationFixture.id(2)).put("document_sha256","a".repeat(64));
  assertEquals(request,schema.validate("Register",request));request.put("project_id",EvaluationFixture.id(3));assertThrows(SchemaRejection.class,()->schema.validate("Register",request));
  request.putNull("project_id");request.put("approved",true);assertThrows(SchemaRejection.class,()->schema.validate("Register",request));request.remove("approved");
  request.put("kind","ANY_SCHEMA");assertThrows(SchemaRejection.class,()->schema.validate("Register",request));
 }
 @Test void fixedProfilesCannotChooseOwnerOrArbitrarySchema()throws Exception {
  for(String kind:java.util.List.of("EXPECTED_ITEMS","SAMPLE_MANIFEST","AUTHORIZED_USE_EVIDENCE")){
   var p=SimulationEvaluationPreparationProfile.profile(kind);assertEquals("SOURCE",p.resourceKind());assertEquals("source.read",p.action());assertEquals(p.schemaDocumentSha256(),p.artifactSchemaManifest());
   assertEquals(p,SimulationEvaluationPreparationProfile.forSchema(p.schemaRef()).orElseThrow());
  }
  assertEquals("SIM_AUTHORIZED_USE_EVIDENCE",SimulationEvaluationPreparationProfile.artifactKind("AUTHORIZED_USE_EVIDENCE"));
  assertTrue(SimulationEvaluationPreparationProfile.forSchema("urn:unknown").isEmpty());assertThrows(TenantAccessException.class,()->SimulationEvaluationPreparationProfile.profile("ANY"));
 }
}
