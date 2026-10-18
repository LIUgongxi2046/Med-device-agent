package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.node.*;
import com.ruoyi.medical.contracts.SchemaRejection;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class FormalEvaluationPolicySchemaTest {
 final FormalEvaluationPolicySchema schema=new FormalEvaluationPolicySchema();final FormalEvaluationPolicyServiceTest f=new FormalEvaluationPolicyServiceTest();
 FormalEvaluationPolicySchemaTest()throws Exception{}
 @Test void closedAuthorEnvelopeKeepsOriginalStatisticalSpecification()throws Exception{assertEquals(f.f.schema.canonical(f.author()),f.f.schema.canonical(schema.request("Author",f.f.schema.canonical(f.author()).getBytes(StandardCharsets.UTF_8))));}
 @Test void duplicateKeysTrailingTokensAndInvalidUtf8Rejected(){for(byte[] bytes:new byte[][]{"{\"x\":1,\"x\":2}".getBytes(),"{} {}".getBytes(),new byte[]{(byte)255},new byte[FormalEvaluationPolicySchema.MAXIMUM_BYTES+1]})assertThrows(SchemaRejection.class,()->schema.request("Author",bytes));}
 @Test void absentHumanAssessmentRejected(){var n=f.author();n.remove("sampling_assessment");assertThrows(SchemaRejection.class,()->schema.validate("Author",n));}
 @Test void unknownRootAuthorityClaimRejected(){var n=f.author();((ObjectNode)n.required("root_uses").get(0)).put("approved",true);assertThrows(SchemaRejection.class,()->schema.validate("Author",n));}
 @Test void callerCannotSupplyStatisticalPolicyIdentity(){var n=f.author();((ObjectNode)n.required("specification")).put("policy_id",PolicyStorageFixture.id(100));assertThrows(SchemaRejection.class,()->schema.validate("Author",n));}
 @Test void unsupportedVersionRejected(){var n=f.author().put("schema_version","2.0.0");assertThrows(SchemaRejection.class,()->schema.validate("Author",n));}
 @Test void missingExpertDomainsRejected(){var n=f.author();((ObjectNode)n.required("specification")).remove("expert_domains");assertThrows(SchemaRejection.class,()->schema.validate("Author",n));}
 @Test void missingOrExtraExpertDimensionRejected(){for(String dimension:new String[]{"EVAL-02","EVAL-99"}){var n=f.author();((ObjectNode)n.required("specification").required("expert_domains")).put(dimension,"QUALIFIED_DOMAIN");assertThrows(SchemaRejection.class,()->schema.validate("Author",n));}var n=f.author();((ObjectNode)n.required("specification")).putObject("expert_domains");assertThrows(SchemaRejection.class,()->schema.validate("Author",n));}
 @Test void blankOversizeOrUnpairedUnicodeExpertDomainRejected(){for(String domain:new String[]{""," ","\u2003","x".repeat(129),"line\nfeed",String.valueOf((char)0xd800)}){var n=f.author();((ObjectNode)n.required("specification").required("expert_domains")).put("EVAL-01",domain);assertThrows(SchemaRejection.class,()->schema.validate("Author",n));}}
 @Test void expertDomainsObjectAndStringValuesAreClosed(){for(var value:new com.fasterxml.jackson.databind.JsonNode[]{f.f.json.object(),f.f.json.object().put("domain","x"),com.fasterxml.jackson.databind.node.NullNode.instance}){var n=f.author();((ObjectNode)n.required("specification").required("expert_domains")).set("EVAL-01",value);assertThrows(SchemaRejection.class,()->schema.validate("Author",n));}}
 @Test void domainTextIsPreservedWithoutNormalization(){var n=f.author();String exact="  审评 e\u0301 🔎  ";((ObjectNode)n.required("specification").required("expert_domains")).put("EVAL-01",exact);assertEquals(exact,schema.validate("Author",n).required("specification").required("expert_domains").required("EVAL-01").asText());}
 @Test void domainsMustCoverAllDeclaredDimensions(){var n=f.author();var spec=(ObjectNode)n.required("specification");((ArrayNode)spec.required("dimensions")).add(((ObjectNode)spec.required("dimensions").get(0)).deepCopy().put("dimension","EVAL-03"));assertThrows(SchemaRejection.class,()->schema.validate("Author",n));((ObjectNode)spec.required("expert_domains")).put("EVAL-03","OTHER_REVIEW_DOMAIN");assertDoesNotThrow(()->schema.validate("Author",n));}
}
