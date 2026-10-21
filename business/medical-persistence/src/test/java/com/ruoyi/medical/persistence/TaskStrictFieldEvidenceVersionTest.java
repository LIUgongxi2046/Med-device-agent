package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Deferred: source-only implementation session explicitly did not run this suite. */
class TaskStrictFieldEvidenceVersionTest {
 @Test void legacyHttpCannotSmuggleVersionTwoBody() {
  assertThrows(Exception.class,()->StrictFieldWireVersion.require("{\"schema_version\":\"2.0.0\"}".getBytes(StandardCharsets.UTF_8),"1.0.0"));
 }
 @Test void versionTwoHttpCannotSmuggleLegacyBody() {
  assertThrows(Exception.class,()->StrictFieldWireVersion.require("{\"schema_version\":\"1.0.0\"}".getBytes(StandardCharsets.UTF_8),"2.0.0"));
 }
 @Test void roleMustDeclareBothNewInputAndActualFieldOutput()throws Exception {
  var c=SchemaCatalogue.fromClasspath();var role=new JsonMapper().createObjectNode();
  role.putObject("input_schema_ref").put("schema_ref",c.schemaRef("task-strict-field-input-v2",null)).put("schema_sha256",c.schemaDigest("task-strict-field-input-v2"));
  var out=role.putObject("output_schema_ref").put("schema_ref",c.schemaRef("artifacts-v2","FieldCheckResult")).put("schema_sha256",c.schemaDigest("artifacts-v2"));
  assertEquals(c.schemaRef("artifacts-v2","FieldCheckResult"),TaskStrictFieldEvidenceContract.require(role,"2.0.0").ref());
  assertThrows(Exception.class,()->TaskStrictFieldEvidenceContract.require(role,"1.0.0"));
  out.put("schema_ref",c.schemaRef("artifacts-v2","EvidenceBundle"));
  assertThrows(Exception.class,()->TaskStrictFieldEvidenceContract.require(role,"2.0.0"));
 }
 @Test void comparisonSchemaVersionsHaveDistinctImmutableDigests()throws Exception {
  var schema=new StrictFieldComparisonSchema();assertNotEquals(schema.schemaSha256(),schema.schemaSha256("2.0.0"));
  assertThrows(Exception.class,()->schema.schemaSha256("3.0.0"));
 }
 @Test void registeredTypedRootRejectsIncompleteOrUnknownShape()throws Exception {
  var c=SchemaCatalogue.fromClasspath();var n=new JsonMapper().createObjectNode().put("schema_version","2.0.0").put("schema_ref",TaskStrictFieldTypedProtocol.TARGET_REF_V2);
  assertThrows(Exception.class,()->c.validate("strict-field-typed-v2",n,"TargetManifest"));
 }
}
