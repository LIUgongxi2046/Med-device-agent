package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import com.ruoyi.medical.contracts.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Actual fixed fact-input check. Never consumes a different target's field/document check result. */
final class SharedFactExactCheck {
 static JsonNode evaluate(JsonNode content,JsonNode definition,Map<String,HumanEvidenceCitationReads.Citation> citations)throws Exception{
  var out=JsonNodeFactory.instance.objectNode();String raw=content.required("raw_value").asText();String hash=WireValues.sha256(raw.getBytes(StandardCharsets.UTF_8));
  boolean matched=true;var evidence=out.putArray("evidence");
  var selected=content.has("value_evidence_link_ids")?content.required("value_evidence_link_ids"):content.required("evidence_link_ids");
  var declared=new java.util.HashSet<String>();content.required("evidence_link_ids").forEach(x->declared.add(x.asText()));
  if(!selected.isArray()||selected.isEmpty())throw new IllegalArgumentException("VALUE_EVIDENCE_REQUIRED");
  var seen=new java.util.HashSet<String>();for(var id:selected)if(!declared.contains(id.asText())||!seen.add(id.asText()))throw new IllegalArgumentException("VALUE_EVIDENCE_SCOPE_MISMATCH");
  for(var id:selected){
   var proof=citations.get(id.asText());boolean same=proof!=null&&proof.located()&&proof.literal()!=null&&raw.equals(proof.literal())&&hash.equals(proof.literalSha256());matched&=same;
   evidence.addObject().put("evidence_link_id",id.asText()).put("literal_sha256",proof==null?"":proof.literalSha256()).put("exact_literal",same);
  }
  boolean unitless=definition.required("unit").isNull()&&content.required("unit").isNull();matched&=unitless;
  String operator=definition.required("operator").asText();out.put("operator",operator).put("raw_value_sha256",hash).put("unitless",unitless);
  if("TEXT_EXACT".equals(operator))matched&=content.required("decimal_value").isNull()&&definition.required("scale").isNull();
  else if("DECIMAL_EXACT".equals(operator)){
   // Parsing is allowed only after the raw bytes are hash-equal to every real reopened literal.
   if(!matched||content.required("decimal_value").isNull()||definition.required("scale").isNull())matched=false;
   else try{String original=citations.get(selected.get(0).asText()).literal();var parsed=new StrictDecimalLexicalParser(16000).parse(original,hash,0,original.codePointCount(0,original.length()));out.put("parsed_decimal",parsed.decimalValue()).put("parsed_scale",parsed.scale()).put("parser_contract",parsed.contract());matched=parsed.decimalValue().equals(content.required("decimal_value").asText())&&parsed.scale()==definition.required("scale").asInt();}catch(IllegalArgumentException failure){matched=false;out.put("parse_rejection",failure.getMessage());}
  }else matched=false;
  return out.put("result",matched?"PASS":"FAIL");
 }
}
