package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import com.ruoyi.medical.contracts.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Offline tests of a draft text-input contract. No upload/registration producer or approval is asserted. */
final class FormalEvaluationInputSchemaTest {
 final PermissionSchema json=new PermissionSchema();final FormalEvaluationInputSchema schema=new FormalEvaluationInputSchema(json);
 FormalEvaluationInputSchemaTest()throws Exception{}
 ObjectNode document(){var d=json.object().put("schema_version","1.0.0").put("document_type","PTR").put("language","zh-CN");d.putArray("sections").addObject().put("section_id","section-01").put("title","测试章节").put("text","第一行\r\n第二行\t👩‍🔬 é");return d;}
 ObjectNode section(ObjectNode d){return (ObjectNode)d.required("sections").get(0);}byte[] bytes(JsonNode n)throws Exception{return json.canonical(n).getBytes(StandardCharsets.UTF_8);}
 void shapeRejected(JsonNode n){assertThrows(SchemaRejection.class,()->schema.validate(n));}
 @Test void closedTypedTextRoundtripsOriginalUnicodeAndWhitespace()throws Exception{var d=document();assertEquals(d,schema.document(bytes(d)));assertEquals("第一行\r\n第二行\t👩‍🔬 é",schema.document(bytes(d)).at("/sections/0/text").asText());}
 @Test void sectionOrderAndOriginalCombiningSequencesArePreserved()throws Exception{var d=document();((ArrayNode)d.required("sections")).addObject().put("section_id","section-02").put("title","").put("text","é");var actual=schema.document(bytes(d));assertEquals("section-01",actual.at("/sections/0/section_id").asText());assertEquals("section-02",actual.at("/sections/1/section_id").asText());assertTrue(actual.at("/sections/0/text").asText().contains("é"));assertEquals("é",actual.at("/sections/1/text").asText());}
 @Test void rejectsScoresApprovalRealityOrProductionClaims(){for(String key:List.of("score","approved","evidence_class","REAL","production_eligible","expert_id")){var d=document();d.put(key,true);shapeRejected(d);}}
 @Test void rejectsNestedScoreAndApprovalFields(){for(String key:List.of("score","approved","source_versions","expected_ref")){var d=document();section(d).put(key,"not permitted");shapeRejected(d);}}
 @Test void rejectsMissingAnyRootField(){for(String key:List.of("schema_version","document_type","language","sections")){var d=document();d.remove(key);shapeRejected(d);}}
 @Test void rejectsMissingAnySectionField(){for(String key:List.of("section_id","title","text")){var d=document();section(d).remove(key);shapeRejected(d);}}
 @Test void rejectsUnsupportedVersion(){var d=document().put("schema_version","2.0.0");shapeRejected(d);}
 @Test void rejectsEmptySections(){var d=document();d.putArray("sections");shapeRejected(d);}
 @Test void rejectsTooManySections(){var d=document();var a=d.putArray("sections");for(int i=0;i<257;i++)a.addObject().put("section_id","s"+i).put("title","").put("text","x");shapeRejected(d);}
 @Test void acceptsMaximumUniqueSectionCount()throws Exception{var d=document();var a=d.putArray("sections");for(int i=0;i<256;i++)a.addObject().put("section_id","s"+i).put("title","").put("text","x");assertEquals(256,schema.document(bytes(d)).required("sections").size());}
 @Test void rejectsDuplicateSectionIdentityDespiteDifferentTitleOrText(){var d=document();((ArrayNode)d.required("sections")).addObject().put("section_id","section-01").put("title","another").put("text","different body");shapeRejected(d);}
 @Test void rejectsEmptyBlankAndNullCharacterBodies(){for(String text:List.of(""," ","\r\n\t","\u2003","x\u0000y")){var d=document();section(d).put("text",text);shapeRejected(d);}}
 @Test void rejectsBlankDocumentType(){var d=document().put("document_type","\t");shapeRejected(d);}
 @Test void acceptsEmptyTitleWithoutInventingContent()throws Exception{var d=document();section(d).put("title","");assertEquals("",schema.document(bytes(d)).at("/sections/0/title").asText());}
 @Test void validatesSectionIdPatternAndLength(){for(String id:List.of(""," leading","s/path","s?query","a".repeat(129))){var d=document();section(d).put("section_id",id);shapeRejected(d);}}
 @Test void validatesLanguageAndDocumentTypeBound(){for(String language:List.of("","x","zh_CN","en--US","en-US ","a".repeat(65))){shapeRejected(document().put("language",language));}shapeRejected(document().put("document_type","x".repeat(65)));}
 @Test void sectionTextLimitCountsUnicodeCodePoints()throws Exception{var d=document();section(d).put("text","😀".repeat(65536));assertEquals(65536,schema.document(bytes(d)).at("/sections/0/text").asText().codePointCount(0,131072));section(d).put("text","😀".repeat(65537));shapeRejected(d);}
 @Test void titleLimitCountsUnicodeCodePoints()throws Exception{var d=document();section(d).put("title","😀".repeat(256));schema.document(bytes(d));section(d).put("title","😀".repeat(257));shapeRejected(d);}
 @Test void individualTextLimitDoesNotBypassTotalUtf8Capacity(){var d=document();var sections=d.putArray("sections");for(int n=0;n<8;n++)sections.addObject().put("section_id","s"+n).put("title","").put("text","😀".repeat(65536));shapeRejected(d);}
 ObjectNode exactCapacity()throws Exception{var d=document();var sections=d.putArray("sections");for(int n=0;n<32;n++)sections.addObject().put("section_id","s"+n).put("title","").put("text","x");int left=FormalEvaluationInputSchema.MAXIMUM_BYTES-bytes(d).length;for(var s:sections){int added=Math.min(65535,left);((ObjectNode)s).put("text","x".repeat(added+1));left-=added;}assertEquals(0,left);return d;}
 @Test void exactMaximumCanonicalUtf8BytesAreAccepted()throws Exception{var d=exactCapacity();byte[] raw=bytes(d);assertEquals(FormalEvaluationInputSchema.MAXIMUM_BYTES,raw.length);assertEquals(32,schema.document(raw).required("sections").size());}
 @Test void oneByteBeyondCanonicalCapacityIsRejected()throws Exception{var d=exactCapacity();((ObjectNode)d.required("sections").get(31)).put("title","x");shapeRejected(d);assertThrows(SchemaRejection.class,()->schema.document(bytes(d)));}
 @Test void prettyJsonAndAlternateObjectOrderAreRejectedAsRawCanonicalMismatch()throws Exception{var d=document();String pretty=new com.fasterxml.jackson.databind.ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(d);assertEquals(ErrorCode.CONTENT_MISMATCH,assertThrows(TenantAccessException.class,()->schema.document(pretty.getBytes(StandardCharsets.UTF_8))).code());String raw="{\"schema_version\":\"1.0.0\",\"document_type\":\"PTR\",\"language\":\"en\",\"sections\":[{\"section_id\":\"s\",\"title\":\"\",\"text\":\"x\"}]}";assertThrows(TenantAccessException.class,()->schema.document(raw.getBytes(StandardCharsets.UTF_8)));}
 @Test void duplicateKeysMalformedJsonAndTrailingJsonAreRejected(){for(String raw:List.of("{\"schema_version\":\"1.0.0\",\"schema_version\":\"1.0.0\"}","{} {}","{"))assertThrows(SchemaRejection.class,()->schema.document(raw.getBytes(StandardCharsets.UTF_8)));}
 @Test void wrongRootAndPrimitiveTypesAreRejected(){for(JsonNode n:List.of(json.object().putArray("x"),json.object().nullNode(),json.object().textNode("document")))shapeRejected(n);var d=document();section(d).put("text",12);shapeRejected(d);}
 @Test void nullEmptyAndOversizeRawInputsAreRejected(){assertThrows(SchemaRejection.class,()->schema.document(null));assertThrows(SchemaRejection.class,()->schema.document(new byte[0]));assertThrows(SchemaRejection.class,()->schema.document(new byte[FormalEvaluationInputSchema.MAXIMUM_BYTES+1]));}
 @Test void invalidUtf8AndNonUtf8InputCannotPassCanonicalIdentity()throws Exception{assertThrows(SchemaRejection.class,()->schema.document(new byte[]{'{','"','x','"',':','"',(byte)0xff,'"','}'}));assertThrows(SchemaRejection.class,()->schema.document(new String(bytes(document()),StandardCharsets.UTF_8).getBytes(StandardCharsets.UTF_16)));}
 @Test void unpairedUtf16SurrogatesCannotBecomeCanonicalUtf8()throws Exception{for(String text:List.of("\ud800","\udc00")){var d=document();section(d).put("text",text);assertThrows(Exception.class,()->schema.validate(d));}}
 @Test void literalBackslashEscapesDoNotChangeOriginalInputText()throws Exception{var d=document();section(d).put("text","literal \\n and \\u0041 stay literal");assertEquals(section(d).required("text"),schema.document(bytes(d)).at("/sections/0/text"));}
 @Test void schemaIdentityAndKindAreExplicitAndDeterministic()throws Exception{assertEquals("urn:medical:formal-evaluation-input:1.0.0#/$defs/Document",FormalEvaluationInputSchema.REF);assertEquals("FORMAL_EVALUATION_INPUT",FormalEvaluationInputSchema.ARTIFACT_KIND);try(var in=getClass().getResourceAsStream("/formal-evaluation-input.schema.json")){assertEquals(org.apache.commons.codec.digest.DigestUtils.sha256Hex(in.readAllBytes()),schema.schemaSha256());}}
}
