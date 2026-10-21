package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;import com.fasterxml.jackson.databind.json.JsonMapper;import com.ruoyi.medical.contracts.*;import java.util.*;
/** Additive closed case originals; published089/091 remain unchanged N01 contracts. */
public final class FormalEvaluationStageInputSchema {
 public static final String NAME="evaluation-stage-input",URI="urn:medical:evaluation-stage-input:1.0.0",CASE=URI+"#/$defs/Case",PROMPT=URI+"#/$defs/PromptPackage",TOOL=URI+"#/$defs/ToolPackage";
 public static final String NAME_V2="evaluation-stage-input-v2",URI_V2="urn:medical:evaluation-stage-input:2.0.0",CASE_V2=URI_V2+"#/$defs/Case";
 public static String version(JsonNode value){String version=value.path("schema_version").asText();if(!Set.of("1.0.0","2.0.0").contains(version))throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED);return version;}
 public static String caseRef(String version){if("1.0.0".equals(version))return CASE;if("2.0.0".equals(version))return CASE_V2;throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED);}
 public static String name(String version){caseRef(version);return "2.0.0".equals(version)?NAME_V2:NAME;}
 private final SchemaCatalogue catalogue;private final JsonMapper parser=JsonMapper.builder().enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
 public FormalEvaluationStageInputSchema()throws Exception{catalogue=SchemaCatalogue.fromClasspath();}
 public String digest(){return digest("1.0.0");}public String digest(String version){return catalogue.schemaDigest(name(version));}public void validate(String type,JsonNode value){validate(version(value),type,value);}
 /** Nested closed definitions have no schema_version; their owner must supply the published version. */
 public void validate(String version,String type,JsonNode value){catalogue.validate(name(version),value,type);}
 public JsonNode read(byte[] raw)throws Exception{if(raw==null||raw.length==0||raw.length>16777216)throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);return parser.readTree(raw);}
}
