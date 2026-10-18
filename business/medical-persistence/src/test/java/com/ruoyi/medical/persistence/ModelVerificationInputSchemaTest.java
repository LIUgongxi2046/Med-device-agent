package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ModelVerificationInputSchemaTest {
 final PermissionSchema json=new PermissionSchema();final ModelVerificationInputWorkflowSchema wire=new ModelVerificationInputWorkflowSchema(json);final ModelVerificationInputSchema schema=new ModelVerificationInputSchema(json);
 ModelVerificationInputSchemaTest()throws Exception{}
 byte[] bytes(String s){return s.getBytes(StandardCharsets.UTF_8);}
 ObjectNode input(){var d=json.object();d.putArray("messages").addObject().put("role","user").put("content","literal \\u1234, 😀 é");d.putArray("tools");return d;}
 @Test void exactInputIsPublishedRealDataShapeAndPreservesBytes()throws Exception{var d=input();assertEquals(d,schema.document(bytes(json.canonical(d))));assertEquals("EXACT_INPUT",ModelVerificationInputSchema.ARTIFACT_KIND);}
 @Test void rawPrettyDocumentCannotSilentlyChangeOriginalHash()throws Exception{var raw=new com.fasterxml.jackson.databind.ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsBytes(input());assertThrows(Exception.class,()->schema.document(raw));}
 @Test void exactInputRejectsDuplicateJsonKeysTrailingBodyAndMalformedUtf8(){for(var bytes:List.of(bytes("{\"messages\":[],\"messages\":[],\"tools\":[]}"),bytes("{} {}"),new byte[]{'{','"','x','"',':','"',(byte)255,'"','}'}))assertThrows(Exception.class,()->schema.document(bytes));}
 @Test void boundsRawBytesBeforeParsing(){assertThrows(Exception.class,()->schema.document(new byte[ModelVerificationInputSchema.MAXIMUM_BYTES+1]));assertThrows(Exception.class,()->schema.document(new byte[0]));}
 @Test void noScoresApprovalsOrSimulationIdentityCanBeAdded()throws Exception{for(String k:List.of("approved","score","simulation_id","evidence_class")){var d=input().put(k,"invented");assertThrows(Exception.class,()->schema.document(bytes(json.canonical(d))));}}
 @Test void purposeProjectModelAndProfileAreRequired()throws Exception{try(var f=new VerificationInputFixture()){for(String k:List.of("business_purpose","project_id","model_config_revision","profile_revision","scope_selection","source","rights_revision")){var r=f.request();r.remove(k);assertThrows(Exception.class,()->wire.validate("Register",r));}}}
 @Test void requestCannotSelectEnterpriseOrInventSnapshot()throws Exception{try(var f=new VerificationInputFixture()){assertThrows(Exception.class,()->wire.validate("Register",f.request().putNull("project_id")));assertThrows(Exception.class,()->wire.validate("Register",f.request().put("scope_snapshot_id",VerificationInputFixture.id(999))));}}
 @Test void networkApprovalNotFabricatedByFirstPreparationProfile()throws Exception{try(var f=new VerificationInputFixture()){var r=f.request();((ObjectNode)r.path("scope_selection")).put("network_enabled",true);assertThrows(Exception.class,()->wire.validate("Register",r));}}
 @Test void coverageFollowsFrozen076DigestNames()throws Exception{var coverage=json.object().put("selection_digest","a".repeat(64)).put("delivered_digest","a".repeat(64)).put("complete",true).putNull("next_cursor");wire.validate("Coverage",coverage);coverage.put("selection_sha256","a".repeat(64));assertThrows(Exception.class,()->wire.validate("Coverage",coverage));}
 @Test void currentOperationLookupAcceptsExactNewName()throws Exception{var request=json.object().put("operation_id",ModelVerificationInputService.OPERATION).put("idempotency_key","original-key-12345");assertEquals(ModelVerificationInputService.OPERATION,new OperationSchema(json).lookup(bytes(json.canonical(request))).operationId());}
}
