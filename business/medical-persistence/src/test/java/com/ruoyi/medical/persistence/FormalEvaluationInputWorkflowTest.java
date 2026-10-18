package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.ruoyi.medical.persistence.FormalInputFixture.*;
final class FormalEvaluationInputWorkflowTest {
 final PermissionSchema json=new PermissionSchema();final FormalEvaluationInputWorkflowSchema schema=new FormalEvaluationInputWorkflowSchema(json);FormalEvaluationInputWorkflowTest()throws Exception{}
 ObjectNode request(){var n=json.object().put("schema_version","1.0.0").putNull("project_id").put("rights_revision",RIGHTS).put("purpose",PURPOSE);n.set("source",json.tree(new PermissionProtocol.Basis(SOURCE,hash("actual bytes"))));return n;}
 @Test void registerIsClosedAndHasNoGeneratedArtifactOrQualificationFields(){for(String field:List.of("artifact_id","score","approved","production_eligible","evidence_class","created_by","operation_id","tenant_id")){var n=request().put(field,"caller supplied");assertThrows(SchemaRejection.class,()->schema.validate("Register",n),field);}}
 @Test void allRequestFieldsAreMandatoryIncludingNullProjectAndExplicitPurpose(){for(String field:List.of("schema_version","project_id","rights_revision","purpose","source")){var n=request();n.remove(field);assertThrows(SchemaRejection.class,()->schema.validate("Register",n),field);}}
 @Test void purposeUsesActualPermissionAuthority64CharacterBoundary(){schema.validate("Register",request().put("purpose","A".repeat(64)));assertThrows(SchemaRejection.class,()->schema.validate("Register",request().put("purpose","A".repeat(65))));for(String invalid:List.of("","document_body","DOCUMENT BODY"," A"))assertThrows(SchemaRejection.class,()->schema.validate("Register",request().put("purpose",invalid)));}
 @Test void publicRequestCannotChooseProject(){assertThrows(SchemaRejection.class,()->schema.validate("Register",request().put("project_id",id(80))));}
 @Test void basisIsFixedClosedAndHasNoVersionFallback(){var n=request();((ObjectNode)n.path("source")).put("latest",true);assertThrows(SchemaRejection.class,()->schema.validate("Register",n));((ObjectNode)n.path("source")).remove("latest");((ObjectNode)n.path("source")).put("content_sha256","A".repeat(64));assertThrows(SchemaRejection.class,()->schema.validate("Register",n));}
 @Test void requestJsonRejectsMalformedDuplicateTrailingAndUtf16(){for(byte[] raw:List.of("{\"a\":1,\"a\":2}".getBytes(StandardCharsets.UTF_8),"{} {}".getBytes(StandardCharsets.UTF_8),"{".getBytes(StandardCharsets.UTF_8),"{}".getBytes(StandardCharsets.UTF_16)))assertThrows(SchemaRejection.class,()->schema.request(raw));}
 @Test void requestTotalBytesAndIdempotencyKeyAreBounded(){assertThrows(SchemaRejection.class,()->schema.request(new byte[8193]));for(String key:List.of("short","space key not allowed","x".repeat(129)))assertThrows(SchemaRejection.class,()->FormalEvaluationInputWorkflowSchema.key(key));FormalEvaluationInputWorkflowSchema.key("x".repeat(16));FormalEvaluationInputWorkflowSchema.key("x".repeat(128));}
 @Test void operationHashBindsTenantActorAndExactCanonicalRequest()throws Exception{String first=schema.operationHash(TENANT,ACTOR,request());assertEquals(first,schema.operationHash(TENANT,ACTOR,schema.request(schema.canonical(request()).getBytes(StandardCharsets.UTF_8))));assertNotEquals(first,schema.operationHash(id(90),ACTOR,request()));assertNotEquals(first,schema.operationHash(TENANT,id(91),request()));assertNotEquals(first,schema.operationHash(TENANT,ACTOR,request().put("purpose","TEMPLATE_MAINTENANCE")));}
 @Test void workflowManifestIsActualClasspathDocumentHash()throws Exception{try(var in=getClass().getResourceAsStream("/formal-evaluation-input-workflow.schema.json")){assertEquals(org.apache.commons.codec.digest.DigestUtils.sha256Hex(in.readAllBytes()),schema.manifest());}}
 @Test void receiptReadAndAvailabilityDefinitionsAreClosed()throws Exception{for(String def:List.of("Receipt","Read","Availability"))assertThrows(SchemaRejection.class,()->schema.validate(def,json.object().put("extra",true)));}
}
