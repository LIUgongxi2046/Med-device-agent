package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** Deferred, not executed in this source-only implementation session. */
class FormalEvaluationEvidenceVersionTest {
 @Test void caseRegistrationSelectsExplicitIndependentPublication()throws Exception {
  var schema=new FormalEvaluationStageInputSchema();var c=SchemaCatalogue.fromClasspath();var n=new JsonMapper().createObjectNode().put("schema_version","2.0.0").put("run_id","10000000-0000-4000-8000-000000000001").put("step_id","10000000-0000-4000-8000-000000000002");
  n.putObject("artifact").put("version_id","10000000-0000-4000-8000-000000000003").put("content_sha256","a".repeat(64));
  assertDoesNotThrow(()->schema.validate("Register",n));
  assertThrows(Exception.class,()->c.validate(FormalEvaluationStageInputSchema.NAME,n,"Register"));
  assertNotEquals(schema.digest(),schema.digest("2.0.0"));
 }
 @Test void actualV1DeclarationCannotValidateV2BusinessOutput()throws Exception {
  var n=new JsonMapper().createObjectNode().put("schema_version","2.0.0");
  assertThrows(Exception.class,()->FormalEvaluationDeterministicSchema.validateDeclared("BUSINESS_OUTPUT",FormalEvaluationBusinessSchema.REF,FormalEvaluationBusinessSchema.digest(),n.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
 }
 @Test void unknownCaseVersionCannotBeGuessedFromEvidence() {
  assertThrows(Exception.class,()->FormalEvaluationStageInputSchema.caseRef("3.0.0"));
 }
}
