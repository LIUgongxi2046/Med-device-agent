package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** Deferred source-only tests. Existing publication declarations never infer an upgrade. */
class TaskReviewEvidenceContractV2Test {
 @Test void roleRequiresExactPublishedReviewDefinition()throws Exception {
  var c=SchemaCatalogue.fromClasspath();var role=new ObjectMapper().createObjectNode();var d=role.putObject("output_schema_ref");
  d.put("schema_ref",c.schemaRef("artifacts-v2","ReviewFindings")).put("schema_sha256",c.schemaDigest("artifacts-v2"));
  assertTrue(TaskReviewEvidenceContract.require(role,c).v2());
  d.put("schema_sha256",c.schemaDigest("artifacts"));assertThrows(Exception.class,()->TaskReviewEvidenceContract.require(role,c));
  d.put("schema_ref",c.schemaRef("artifacts","ReviewFindings"));assertFalse(TaskReviewEvidenceContract.require(role,c).v2());
  d.put("schema_ref",c.schemaRef("artifacts","SectionDraft"));assertThrows(Exception.class,()->TaskReviewEvidenceContract.require(role,c));
 }
}
