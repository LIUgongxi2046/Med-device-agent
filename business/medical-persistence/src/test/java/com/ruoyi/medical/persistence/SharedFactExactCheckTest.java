package com.ruoyi.medical.persistence;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/** Deferred source only: deliberately not executed during code-first development. */
class SharedFactExactCheckTest {
 private final TaskSourceVerifierSchema json=new TaskSourceVerifierSchema();
 private com.fasterxml.jackson.databind.JsonNode content(String raw,String decimal)throws Exception{return json.read("{\"raw_value\":\""+raw+"\",\"decimal_value\":"+decimal+",\"unit\":null,\"evidence_link_ids\":[\"link\"]}");}
 private Map<String,HumanEvidenceCitationReads.Citation> literal(String value)throws Exception{return Map.of("link",new HumanEvidenceCitationReads.Citation(null,null,true,com.ruoyi.medical.contracts.WireValues.sha256(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)),value));}
 @Test void exactOriginalLiteralIsRequired()throws Exception{
  var rule=json.read("{\"operator\":\"TEXT_EXACT\",\"unit\":null,\"scale\":null}");
  assertEquals("PASS",SharedFactExactCheck.evaluate(content("actual","null"),rule,literal("actual")).required("result").asText());
  assertEquals("FAIL",SharedFactExactCheck.evaluate(content("claimed","null"),rule,literal("actual")).required("result").asText());
 }
 @Test void decimalScaleIsNotRoundedOrNormalized()throws Exception{
  var rule=json.read("{\"operator\":\"DECIMAL_EXACT\",\"unit\":null,\"scale\":2}");
  assertEquals("PASS",SharedFactExactCheck.evaluate(content("1.00","\"1.00\""),rule,literal("1.00")).required("result").asText());
  assertEquals("FAIL",SharedFactExactCheck.evaluate(content("1.00","\"1\""),rule,literal("1.00")).required("result").asText());
 }
 @Test void hashOnlyCannotFabricateOriginal()throws Exception{
  var proof=literal("actual").get("link");var hashOnly=new HumanEvidenceCitationReads.Citation(null,null,true,proof.literalSha256());
  var rule=json.read("{\"operator\":\"TEXT_EXACT\",\"unit\":null,\"scale\":null}");
  assertEquals("FAIL",SharedFactExactCheck.evaluate(content("actual","null"),rule,Map.of("link",hashOnly)).required("result").asText());
 }
}
