package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
/** Selected solely from the approved role's exact registered output declaration. */
record TaskReviewEvidenceContract(boolean v2) {
 static TaskReviewEvidenceContract require(JsonNode roleRequest,SchemaCatalogue catalogue)throws Exception {
  var d=roleRequest.required("output_schema_ref");var p=catalogue.requirePublished(d.required("schema_ref").asText(),d.required("schema_sha256").asText());
  if(!"ReviewFindings".equals(p.definition())||!java.util.Set.of("artifacts","artifacts-v2").contains(p.name()))throw new TenantAccessException(ErrorCode.SCHEMA_UNSUPPORTED);
  return new TaskReviewEvidenceContract("artifacts-v2".equals(p.name()));
 }
 String version(){return v2?"2.0.0":"1.0.0";}
 String artifacts(){return v2?"artifacts-v2":"artifacts";}
 String packages(){return v2?"task-source-verifier-package-v2":"task-source-verifier-package";}
 String artifactRef(String definition)throws Exception{return SchemaCatalogue.fromClasspath().schemaRef(artifacts(),definition);}
 String artifactSha()throws Exception{return SchemaCatalogue.fromClasspath().schemaDigest(artifacts());}
}
