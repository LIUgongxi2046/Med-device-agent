package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class PublicationDisableRecoverySchemaTest {
 final PermissionSchema json=new PermissionSchema();
 final FormalAgentPublicationSchema original=new FormalAgentPublicationSchema(json);
 final FormalPublicationDisableRecoverySchema schema=new FormalPublicationDisableRecoverySchema(original);
 PublicationDisableRecoverySchemaTest()throws Exception{}
 String id(int n){return PublicationLedgerFixture.id(n);}
 ObjectNode request(){return json.object().put("schema_version","1.0.0").put("release_id",id(1)).put("expected_revision","2").put("reason","Original disable request");}
 ObjectNode recovery(){var n=json.object().put("schema_version","1.0.0").put("original_key","original-disable-key-001");n.set("request",request());return n;}
 byte[] bytes(JsonNode n)throws Exception{return json.canonical(n).getBytes(StandardCharsets.UTF_8);}
 ObjectNode receipt()throws Exception{var n=json.object().put("schema_version","1.0.0").put("state","COMPLETED").put("original_key","original-disable-key-001").put("actor_id",id(2)).put("release_id",id(1)).put("expected_revision","2").put("resulting_revision","3").put("request_body_sha256",original.hash(request()));n.set("receipt",new AgentPublicationSchema(json).receipt(id(3),id(4),"PUBLICATION","a".repeat(64),original.hash(request())));return n;}
 @Test void exactOriginalBodyAndClosedReceiptAreAccepted()throws Exception{assertEquals(recovery(),schema.recovery(bytes(recovery())));assertEquals(receipt(),schema.receipt(receipt()));}
 @Test void parserRejectsNullEmptyOversizeAndMissingOriginalIdentity()throws Exception{for(byte[] b:new byte[][]{null,new byte[0],new byte[FormalPublicationDisableRecoverySchema.MAXIMUM_BYTES+1],"{}".getBytes()})assertThrows(SchemaRejection.class,()->schema.recovery(b));for(String key:new String[]{"short","space key not valid","x".repeat(129)})assertThrows(SchemaRejection.class,()->schema.recovery(bytes(recovery().put("original_key",key))));}
 @Test void outerAndOriginalBodyAreBothClosed()throws Exception{assertThrows(SchemaRejection.class,()->schema.recovery(bytes(recovery().put("write",true))));var n=recovery();((ObjectNode)n.required("request")).put("enable",true);assertThrows(SchemaRejection.class,()->schema.recovery(bytes(n)));}
 @Test void allProjectionPropertiesRequiredAndNoUncheckedExtras()throws Exception{for(var it=receipt().fieldNames();it.hasNext();){var n=receipt();n.remove(it.next());assertThrows(SchemaRejection.class,()->schema.receipt(n));}assertThrows(SchemaRejection.class,()->schema.receipt(receipt().put("production_eligible",true)));var n=receipt();((ObjectNode)n.required("receipt")).put("original_authorizations",true);assertThrows(SchemaRejection.class,()->schema.receipt(n));}
 @Test void noProcessingNotFoundOrEnabledSuccessIsRepresentable()throws Exception{for(String state:new String[]{"PROCESSING","NOT_FOUND","PUBLISHED","ENABLED"})assertThrows(SchemaRejection.class,()->schema.receipt(receipt().put("state",state)));}
 @Test void nestedReceiptMustBePublicationAndRevisionsMustRemainDecimalStrings()throws Exception{var n=receipt();((ObjectNode)n.required("receipt")).put("kind","POLICY");assertThrows(SchemaRejection.class,()->schema.receipt(n));for(String value:new String[]{"0","-1","2e0","02","2.0"})assertThrows(SchemaRejection.class,()->schema.receipt(receipt().put("expected_revision",value)));assertThrows(SchemaRejection.class,()->schema.receipt(receipt().put("resulting_revision",3)));}
 @Test void disableDefinitionIsIdenticalToFrozenOriginal()throws Exception{try(var in=getClass().getResourceAsStream("/formal-agent-publication-disable-recovery.schema.json");var frozen=getClass().getResourceAsStream("/formal-agent-publication.schema.json")){var parser=com.fasterxml.jackson.databind.json.JsonMapper.builder().build();var local=parser.readTree(in);var original=parser.readTree(frozen);for(String name:new String[]{"Disable","Revision","Reason","Id","Hash"})assertEquals(original.path("$defs").path(name),local.path("$defs").path(name),name);}}
}
