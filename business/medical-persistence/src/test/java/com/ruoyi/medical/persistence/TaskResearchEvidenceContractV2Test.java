package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** Deferred: publication selects empty bundles too; payload shape never upgrades metadata. */
class TaskResearchEvidenceContractV2Test {
 @Test void emptyCandidateUsesExplicitPublication()throws Exception {
  var c=SchemaCatalogue.fromClasspath();var json=new ObjectMapper();var value=json.createObjectNode();
  for(String field:java.util.List.of("retrieval_ids","selected_refs","conflict_refs","missing_requirement_ids","read_receipt_ids"))value.putArray(field);
  var question=value.putArray("question_coverage").addObject().put("question_id","00000000-0000-4000-8000-000000000001");question.putArray("evidence_links");question.putArray("missing_requirement_ids");
  var publication=c.requirePublished(c.schemaRef("artifacts-v2","EvidenceBundle"),c.schemaDigest("artifacts-v2"));
  assertTrue(TaskResearchExecutionService.projectionBundle(value,publication).selectedRefs().isEmpty());
  assertThrows(Exception.class,()->c.requirePublished(publication.schemaRef(),c.schemaDigest("artifacts")));
 }
}
