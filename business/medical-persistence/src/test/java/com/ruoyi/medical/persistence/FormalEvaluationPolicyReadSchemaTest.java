package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Actual fixed 084 service outputs validate against an independent sealed historical projection. */
class FormalEvaluationPolicyReadSchemaTest {
 final FormalEvaluationPolicyReadSchema schema=new FormalEvaluationPolicyReadSchema();
 final FormalEvaluationPolicyServiceTest f=new FormalEvaluationPolicyServiceTest();
 FormalEvaluationPolicyReadSchemaTest()throws Exception{}
 ObjectNode read(JsonNode receipt)throws Exception{return (ObjectNode)f.service.read(f.subject,"1",receipt.required("resource_id").asText());}
 @Test void actualDraftApprovedAndRejectedReadOutputsAreClosedAndHistorical()throws Exception{
  assertEquals("DRAFT",schema.validate(read(f.draft())).path("content").path("status").asText());
  var approved=new FormalEvaluationPolicyServiceTest();var receipt=approved.approve();assertEquals("APPROVE",schema.validate(approved.service.read(approved.subject,"1",receipt.path("resource_id").asText())).path("content").path("status").asText());
  var d=f.draft();f.f.actor=PolicyStorageFixture.id(3);var rejected=f.service.decide(f.subject,"1","formal-policy-reject-001",f.decision(d,"REJECT"));assertEquals("REJECT",schema.validate(read(rejected)).path("content").path("status").asText());
 }
 @Test void responseCannotClaimProductionEligibility()throws Exception{var n=read(f.approve()).put("production_eligible",true);assertThrows(SchemaRejection.class,()->schema.validate(n));}
 @Test void outerContentAndPolicyAreClosed()throws Exception{var n=read(f.approve());assertThrows(SchemaRejection.class,()->schema.validate(n.deepCopy().put("grant",true)));var content=n.deepCopy();((ObjectNode)content.required("content")).put("read_grant",true);assertThrows(SchemaRejection.class,()->schema.validate(content));var policy=n.deepCopy();((ObjectNode)policy.required("content").required("policy")).put("execution_enabled",true);assertThrows(SchemaRejection.class,()->schema.validate(policy));}
 @Test void draftAuthorAndRootUsesAreClosed()throws Exception{var n=read(f.draft());((ObjectNode)n.required("content").required("author").required("root_uses").get(0)).put("approved",true);assertThrows(SchemaRejection.class,()->schema.validate(n));}
 @Test void allOuterFieldsAndContentFieldsAreRequired()throws Exception{var n=read(f.approve());for(var it=n.fieldNames();it.hasNext();){var bad=n.deepCopy();bad.remove(it.next());assertThrows(SchemaRejection.class,()->schema.validate(bad));}for(var it=n.required("content").fieldNames();it.hasNext();){var bad=n.deepCopy();((ObjectNode)bad.required("content")).remove(it.next());assertThrows(SchemaRejection.class,()->schema.validate(bad));}}
 @Test void historicalReadDoesNotDemandNewPublishApproval()throws Exception{var original=f.approve();clearInvocations(f.f.resources,f.input);doThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED)).when(f.f.resources).require(eq(f.f.a),any(),eq("agent.publish"),anyString());assertFalse(schema.validate(read(original)).path("production_eligible").asBoolean());verify(f.input).prepare(eq(f.subject),eq("1"),any(),eq(false));verify(f.f.resources,never()).require(eq(f.f.a),any(),eq("agent.publish"),anyString());}
}
