package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Deferred source-only cases; no compilation or execution in this implementation session. */
class DocumentReviewEvidenceContractDeferredTest {
 @Test void v1NeverAcceptsV2CompilationPins()throws Exception {
  var pins=JsonMapper.builder().build().createObjectNode().put("evidence_contract","2.0.0");
  assertThrows(TenantAccessException.class,()->DocumentReviewInvocationService.requireCompilationContract(pins,new TaskReviewEvidenceContract(false)));
 }
 @Test void v2RequiresExactRegisteredFindingsAndBody()throws Exception {
  var catalogue=SchemaCatalogue.fromClasspath();
  var pins=JsonMapper.builder().build().createObjectNode().put("evidence_contract","2.0.0")
   .put("output_schema_ref",catalogue.schemaRef("artifacts-v2","ReviewFindings"))
   .put("output_schema_sha256",catalogue.schemaDigest("artifacts-v2"))
   .put("body_schema_ref",catalogue.schemaRef("document-body-v2","DocumentBody"))
   .put("body_schema_sha256",catalogue.schemaDigest("document-body-v2"));
  assertDoesNotThrow(()->DocumentReviewInvocationService.requireCompilationContract(pins,new TaskReviewEvidenceContract(true)));
  pins.put("body_schema_ref",catalogue.schemaRef("interfaces","DocumentBody")).put("body_schema_sha256",catalogue.schemaDigest("interfaces"));
  assertThrows(TenantAccessException.class,()->DocumentReviewInvocationService.requireCompilationContract(pins,new TaskReviewEvidenceContract(true)));
 }
}
