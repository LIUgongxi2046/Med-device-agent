package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import java.util.*;
/** Explicit per-node profiles. A shared supervisor transport is pinned, never a shared TASK identity. */
public final class FormalEvaluationDeployments {
 private final Map<String,FormalEvaluationDeployment> nodes;
 public FormalEvaluationDeployments(JsonNode document,String digest)throws Exception {
  if(!WireValues.sha256(ContentReadAuthorization.canonicalBytes(document)).equals(digest))bad();
  ProfessionalRuntimeObservations.closed(document,Set.of("schema_version","deployments"));if(!"1.0.0".equals(document.required("schema_version").asText()))bad();
  var values=document.required("deployments");if(!values.isArray()||values.isEmpty()||values.size()>10)bad();var found=new LinkedHashMap<String,FormalEvaluationDeployment>();JsonNode first=null;
  for(var item:values){ProfessionalRuntimeObservations.closed(item,Set.of("deployment","sha256"));var deployment=new FormalEvaluationDeployment(item.required("deployment"),item.required("sha256").asText());var value=deployment.value();String kind=value.required("node_kind").asText();if(found.putIfAbsent(kind,deployment)!=null)bad();if(first==null)first=value;else for(String key:List.of("supervisor_endpoint","supervisor_service_uri","supervisor_certificate_sha256","supervisor_trust_revision"))if(!first.required(key).equals(value.required(key)))bad();}nodes=Map.copyOf(found);
 }
 public FormalEvaluationDeployment require(String node){var result=nodes.get(node);if(result==null)throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);return result;}
 public FormalEvaluationDeployment supervisor(){return nodes.values().iterator().next();}
 private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
