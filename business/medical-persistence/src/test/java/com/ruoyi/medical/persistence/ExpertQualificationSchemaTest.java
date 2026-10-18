package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.persistence.ExpertQualificationProtocol.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.ruoyi.medical.persistence.FormalInputFixture.*;
final class ExpertQualificationSchemaTest {
 final ExpertQualificationSchema schema=new ExpertQualificationSchema(new PermissionSchema());ExpertQualificationSchemaTest()throws Exception{}
 ObjectNode author(){return (ObjectNode)schema.tree(new Author("1.0.0",null,id(3),"PTR expert review",List.of("EVAL-01","EVAL-12"),"EVALUATION_RESULTS","2026-10-18T00:00:00Z","2027-10-18T00:00:00Z",List.of(new Evidence("SOURCE",new PermissionProtocol.Basis(id(10),"a".repeat(64)))),"Human evidence submission"));}
 @Test void closedTypedRequestAcceptsGenuineBoundedFields()throws Exception{var a=author();assertEquals(a,schema.request("Author",schema.canonical(a).getBytes(StandardCharsets.UTF_8)));ExpertQualificationSchema.semantics(schema.bind(a,Author.class));}
 @Test void noInventedScoreApprovalRealOrLicenceFieldCanEnterRequest(){for(String field:List.of("score","production_eligible","expert_approved","REAL","medical_licence")){var a=author().put(field,true);assertThrows(SchemaRejection.class,()->schema.validate("Author",a));}}
 @Test void explicitPurposeCannotBeMissingBlankLowercaseOrTooLong(){for(String purpose:List.of("","evaluation_results","A".repeat(65))){var a=author().put("purpose",purpose);assertThrows(SchemaRejection.class,()->schema.validate("Author",a));}var a=author();a.remove("purpose");assertThrows(SchemaRejection.class,()->schema.validate("Author",a));}
 @Test void onlyFrozenTwelveDimensionsAreAccepted(){var a=author();a.putArray("dimensions").add("EXPERT-01");assertThrows(SchemaRejection.class,()->schema.validate("Author",a));}
 @Test void duplicateDimensionsAndEvidenceAreRejected(){var a=author();a.putArray("dimensions").add("EVAL-01").add("EVAL-01");assertThrows(SchemaRejection.class,()->schema.validate("Author",a));var b=author();((com.fasterxml.jackson.databind.node.ArrayNode)b.path("evidence")).add(b.path("evidence").get(0));final var x=b;assertThrows(SchemaRejection.class,()->schema.validate("Author",x));}
 @Test void twoHashesOrKindsForSameEvidenceVersionAreRejectedSemantically()throws Exception{var a=author();((com.fasterxml.jackson.databind.node.ArrayNode)a.path("evidence")).add(schema.tree(new Evidence("ARTIFACT",new PermissionProtocol.Basis(id(10),"b".repeat(64)))));schema.validate("Author",a);var typed=schema.bind(a,Author.class);assertThrows(SchemaRejection.class,()->ExpertQualificationSchema.semantics(typed));}
 @Test void EmptyEvidenceCannotBecomeQualification(){var a=author();a.putArray("evidence");assertThrows(SchemaRejection.class,()->schema.validate("Author",a));}
 @Test void EvidenceRequiresActualSourceOrArtifactKind(){var a=author();((ObjectNode)a.path("evidence").get(0)).put("kind","CERTIFICATE_STRING");assertThrows(SchemaRejection.class,()->schema.validate("Author",a));}
 @Test void validityMustBeStrictAndPersistableAtMicroseconds()throws Exception{for(String time:List.of("2026-10-18T00:00:00Z","2026-10-17T00:00:00Z")){var a=author().put("valid_to",time);assertThrows(SchemaRejection.class,()->ExpertQualificationSchema.semantics(schema.bind(a,Author.class)));}var a=author().put("valid_from","2026-10-18T00:00:00.000000001Z");assertThrows(SchemaRejection.class,()->ExpertQualificationSchema.semantics(schema.bind(a,Author.class)));}
 @Test void datesMustBeExplicitUtc(){var a=author().put("valid_from","2026-10-18T01:00:00+01:00");assertThrows(SchemaRejection.class,()->schema.validate("Author",a));}
 @Test void duplicateKeysTrailingJsonInvalidUtf8CannotBeAccepted()throws Exception{for(byte[] raw:List.of("{\"a\":1,\"a\":2}".getBytes(StandardCharsets.UTF_8),"{} {}".getBytes(StandardCharsets.UTF_8),new byte[]{(byte)0xff})){assertThrows(SchemaRejection.class,()->schema.parse(raw));}}
 @Test void requestTotalUtf8LimitIsIndependentOfTextLengths(){assertThrows(SchemaRejection.class,()->schema.request("Author",new byte[ExpertQualificationSchema.MAXIMUM_REQUEST_BYTES+1]));}
 @Test void originalRequestHashBindsActorTenantKindAndSchemaButNotSession()throws Exception{var a=author();String expected=schema.operationHash(id(1),id(2),"AUTHOR",a);assertEquals(expected,schema.operationHash(id(1),id(2),"AUTHOR",a.deepCopy()));assertNotEquals(expected,schema.operationHash(id(1),id(3),"AUTHOR",a));assertNotEquals(expected,schema.operationHash(id(9),id(2),"AUTHOR",a));}
 @Test void publicPrivateProofConstructorsAreActuallyPrivate(){for(Class<?> type:List.of(ExpertQualificationAuthority.VerifiedExpertQualification.class,ExpertQualificationAuthority.VerifiedQualifiedScore.class,EvaluationService.CreatedScoreWitness.class,EvaluationService.AcceptanceSnapshot.class))for(var constructor:type.getDeclaredConstructors())assertTrue(java.lang.reflect.Modifier.isPrivate(constructor.getModifiers()));}
 @Test void governanceDoesNotInventExpertApprovePermission(){assertDoesNotThrow(()->new PermissionSchema().scope(new PermissionSchema().tree(new PermissionProtocol.Scope(id(1),null,"ENTERPRISE",id(1)))));}
}
