package com.ruoyi.medical.persistence;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.SchemaRejection;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;

/** Deferred test source only. Synthetic schema data is not professional evidence or an approved sample. */
final class ScenarioSpecificationSchemaTest {
 private final PermissionSchema json=new PermissionSchema();
 private final ScenarioSpecificationSchema schema=new ScenarioSpecificationSchema(json);
 ScenarioSpecificationSchemaTest()throws Exception{}
 private ObjectNode basis(){return json.object().put("version_id","00000000-0000-4000-8000-000000000001").put("content_sha256","a".repeat(64));}
 private ObjectNode author(){
  var request=json.object().put("schema_version","1.0.0").put("project_id","00000000-0000-4000-8000-000000000002").put("title","Synthetic contract fixture only");request.set("capability",basis());
  var fields=request.putObject("specification");for(String field:ScenarioSpecificationService.FIELDS){var value=fields.putObject(field).put("definition","Synthetic contract fixture only");value.putArray("evidence").add(basis());}
  var applicability=request.putObject("applicability");applicability.putArray("document_types").add("SYNTHETIC_TEST");applicability.putArray("templates").add(basis());return request;
 }
 @Test void acceptsCompleteIndependentContract(){assertEquals(author(),schema.validate("Author",author()));}
 @Test void everyOneOfEightFieldsIsMandatory(){for(String field:ScenarioSpecificationService.FIELDS){var request=author();((ObjectNode)request.required("specification")).remove(field);assertThrows(SchemaRejection.class,()->schema.validate("Author",request));}}
 @Test void proseWithoutGenuineEvidenceReferenceIsNotACompleteSpecification(){for(String field:ScenarioSpecificationService.FIELDS){var request=author();((ObjectNode)request.required("specification").required(field)).putArray("evidence");assertThrows(SchemaRejection.class,()->schema.validate("Author",request));}}
 @Test void whitespaceAndExtraExecutionClaimAreRejected(){var request=author();((ObjectNode)request.required("specification").required("TASK_RULES")).put("definition","   ");assertThrows(SchemaRejection.class,()->schema.validate("Author",request));var forged=author().put("execution_eligible",true);assertThrows(SchemaRejection.class,()->schema.validate("Author",forged));}
 @Test void templateApplicabilityCannotBeOmitted(){var request=author();((ObjectNode)request.required("applicability")).remove("templates");assertThrows(SchemaRejection.class,()->schema.validate("Author",request));}
 @Test void duplicateJsonAndOversizePayloadAreRejected()throws Exception{assertThrows(Exception.class,()->schema.request("Author","{\"schema_version\":\"1.0.0\",\"schema_version\":\"1.0.0\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8)));assertThrows(SchemaRejection.class,()->schema.request("Author",new byte[ScenarioSpecificationSchema.MAXIMUM_BYTES+1]));}
 @Test void executionProofHasNoPublicOrPackageConstructor(){for(var constructor:ScenarioSpecificationService.Proof.class.getDeclaredConstructors())assertTrue(Modifier.isPrivate(constructor.getModifiers()));}
}
