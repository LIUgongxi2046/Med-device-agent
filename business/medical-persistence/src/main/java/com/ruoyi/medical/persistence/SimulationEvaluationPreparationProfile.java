package com.ruoyi.medical.persistence;
import com.ruoyi.medical.contracts.*;
import java.io.IOException;
import java.util.*;
/** Closed, source-owned profiles. Never creates grants. */
public final class SimulationEvaluationPreparationProfile {
 private SimulationEvaluationPreparationProfile() { }
 public static String artifactKind(String kind){return "AUTHORIZED_USE_EVIDENCE".equals(kind)?"SIM_AUTHORIZED_USE_EVIDENCE":kind;}
 public static EvaluationDependencyPolicy.ArtifactProfile profile(String kind)throws IOException {
  String ref,hash;
  switch(kind){
   case "EXPECTED_ITEMS" -> {ref=EvaluationSchema.EXPECTED_ITEMS_REF;hash=frozenHash();}
   case "SAMPLE_MANIFEST" -> {ref=EvaluationSchema.SAMPLE_MANIFEST_REF;hash=frozenHash();}
   case "AUTHORIZED_USE_EVIDENCE" -> {ref=SimulationAuthorizedUseEvidenceSchema.schemaRef();hash=SimulationAuthorizedUseEvidenceSchema.schemaDocumentSha256();}
   default -> throw new TenantAccessException(ErrorCode.SCHEMA_UNSUPPORTED);
  }
  return new EvaluationDependencyPolicy.ArtifactProfile("SOURCE","source.read",ref,hash,hash);
 }
 public static Optional<EvaluationDependencyPolicy.ArtifactProfile> forSchema(String ref)throws IOException {
  for(String kind:List.of("EXPECTED_ITEMS","SAMPLE_MANIFEST","AUTHORIZED_USE_EVIDENCE")){var p=profile(kind);if(p.schemaRef().equals(ref))return Optional.of(p);}
  return Optional.empty();
 }
 private static String frozenHash()throws IOException {
  try{return SchemaCatalogue.fromClasspath().schemaDigest("evaluation-results");}
  catch(java.security.NoSuchAlgorithmException e){throw new IOException("SCHEMA_DIGEST_UNAVAILABLE",e);}
 }
}
