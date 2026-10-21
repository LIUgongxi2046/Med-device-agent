package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
/** A deterministic N03 route is versioned by its approved role, never by returned evidence. */
record TaskStrictFieldEvidenceContract(String version) {
 static TaskStrictFieldEvidenceContract require(JsonNode role,String version)throws Exception {
  if(!java.util.Set.of("1.0.0","2.0.0").contains(version))bad();
  var c=SchemaCatalogue.fromClasspath();var out=role.required("output_schema_ref");
  var p=c.requirePublished(out.required("schema_ref").asText(),out.required("schema_sha256").asText());
  if(!"FieldCheckResult".equals(p.definition())||!("2.0.0".equals(version)?"artifacts-v2":"artifacts").equals(p.name()))bad();
  if("2.0.0".equals(version)) {var in=role.required("input_schema_ref");
   var ip=c.requirePublished(in.required("schema_ref").asText(),in.required("schema_sha256").asText());
   if(!"task-strict-field-input-v2".equals(ip.name()))bad();
  }
  return new TaskStrictFieldEvidenceContract(version);
 }
 String ref()throws Exception{return SchemaCatalogue.fromClasspath().schemaRef("2.0.0".equals(version)?"artifacts-v2":"artifacts","FieldCheckResult");}
 String sha()throws Exception{return SchemaCatalogue.fromClasspath().schemaDigest("2.0.0".equals(version)?"artifacts-v2":"artifacts");}
 private static void bad(){throw new TenantAccessException(ErrorCode.SCHEMA_UNSUPPORTED);}
}
