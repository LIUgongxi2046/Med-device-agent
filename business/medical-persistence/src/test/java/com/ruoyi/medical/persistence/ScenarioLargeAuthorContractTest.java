package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;

/** Deferred compatibility tests. Never an approved real scenario or a run. */
final class ScenarioLargeAuthorContractTest {
 private final PermissionSchema json=new PermissionSchema();
 private final ScenarioSpecificationSchema schema=new ScenarioSpecificationSchema(json);
 ScenarioLargeAuthorContractTest()throws Exception{}
 private ObjectNode basis(){return json.object().put("version_id","00000000-0000-4000-8000-000000000001").put("content_sha256","a".repeat(64));}
 private ObjectNode author(String version){var a=json.object().put("schema_version",version).put("project_id","00000000-0000-4000-8000-000000000002").put("title","Synthetic only");a.set("capability",basis());var fields=a.putObject("specification");for(String field:ScenarioSpecificationService.FIELDS){var f=fields.putObject(field).put("definition","Synthetic only");f.putArray("evidence").add(basis());}var scope=a.putObject("applicability");scope.putArray("document_types").add("SYNTHETIC");scope.putArray("templates").add(basis());return a;}
 @Test void publishedVersionOneManifestIsUnchanged(){assertEquals("71cf38ff7b7f8b2b9b2cf36e0636d97c2a779c69f3bed654049d7e47916604c3",schema.manifest("1.0.0"));assertNotEquals(schema.manifest("1.0.0"),schema.manifest("1.1.0"));}
 @Test void onlyExplicitVersionOneOneAuthorAcceptsLargeInputs(){var a=author("1.0.0");((ObjectNode)a.required("specification").required("INPUTS")).put("definition","x".repeat(16001));assertThrows(SchemaRejection.class,()->schema.validate("Author",a));a.put("schema_version","1.1.0");assertEquals(a,schema.validate("Author",a));}
 @Test void otherSpecificationFieldsAreNotSilentlyWidened(){var a=author("1.1.0");((ObjectNode)a.required("specification").required("HUMAN_REVIEW")).put("definition","x".repeat(16001));assertThrows(SchemaRejection.class,()->schema.validate("Author",a));}
 @Test void versionOneOneCannotRedefineReviewPublishOrDisable(){for(String kind:java.util.List.of("Review","Publish","Disable"))assertThrows(SchemaRejection.class,()->schema.validate(kind,author("1.1.0")));}
 @Test void actualUtf8RequestBytesStillHaveOriginalLimit(){var a=author("1.1.0");((ObjectNode)a.required("specification").required("INPUTS")).put("definition","字".repeat(100000));assertThrows(SchemaRejection.class,()->schema.request("Author",a.toString().getBytes(StandardCharsets.UTF_8)));}
 @Test void storedVersionMustBeRecognized(){assertThrows(SchemaRejection.class,()->schema.manifest("2.0.0"));}
}
