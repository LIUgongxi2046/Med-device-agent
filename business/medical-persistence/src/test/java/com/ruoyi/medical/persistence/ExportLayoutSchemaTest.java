package com.ruoyi.medical.persistence;

import static org.junit.jupiter.api.Assertions.*;
import com.ruoyi.medical.contracts.SchemaRejection;
import org.junit.jupiter.api.Test;

/** Deferred: intentionally not executed during implementation-only work. */
final class ExportLayoutSchemaTest {
 @Test void unknownAndNotRunAreValidButNotPassingEvidence() throws Exception {
  var schema=new ExportSchema();var value=schema.object();
  value.put("schema_version","1.0.0");value.put("expected_revision","3");
  value.put("output_sha256","a".repeat(64));value.put("channel_profile_revision","11111111-1111-4111-8111-111111111111");
  value.put("channel_check","UNKNOWN");value.put("visual_check","NOT_RUN");value.putNull("evidence_artifact_id");
  value.putNull("evidence_sha256");value.putNull("previous_verification_id");value.put("notes","Actual review has not run.");
  assertDoesNotThrow(()->schema.verify("VerificationInput",value));
  value.put("visual_check","PASSED");
  assertThrows(SchemaRejection.class,()->schema.verify("VerificationInput",value));
  value.put("visual_check","NOT_RUN");value.put("channel_check","APPROVED");
  assertThrows(SchemaRejection.class,()->schema.verify("VerificationInput",value));
 }
 @Test void unboundedOrInventedNavigationSettingsAreRejected() throws Exception {
  var schema=new ExportSchema();var value=schema.object();value.put("schema_version","1.0.0");
  for(var field:java.util.List.of("document_revision","format_profile_revision","numbering_rule_revision"))
   value.put(field,"11111111-1111-4111-8111-111111111111");
  value.putNull("channel_name");value.putNull("channel_specification_artifact_id");value.putNull("channel_specification_sha256");
  value.put("toc_title","Contents");value.put("toc_depth",2);value.put("page_number_start",1);value.putArray("attachment_node_ids");
  assertDoesNotThrow(()->schema.verify("LayoutProfile",value));
  value.put("toc_depth",10);assertThrows(SchemaRejection.class,()->schema.verify("LayoutProfile",value));
  value.put("toc_depth",2);value.put("channel_approved",true);assertThrows(SchemaRejection.class,()->schema.verify("LayoutProfile",value));
 }
}
