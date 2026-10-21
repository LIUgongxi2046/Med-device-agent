package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import org.springframework.stereotype.Component;
/** Resolves the exact role already pinned by the independently approved TASK input.
 * Metadata identity is not a new execution grant; callers retain their session/input authority checks. */
@Component final class TaskResearchEvidenceContract {
 private final FormalCapabilityIdentityAuthority roles;private final PermissionSchema json;
 TaskResearchEvidenceContract(FormalCapabilityIdentityAuthority roles,PermissionSchema json){this.roles=roles;this.json=json;}
 SchemaCatalogue.PublishedSchema require(String tenant,String actor,String project,JsonNode manifest)throws Exception {
  var role=roles.requireRegisteredIdentity(tenant,actor,json.basis(manifest.required("role_version")),"ROLE",project);
  var declaration=role.canonicalDefinition().required("request").required("output_schema_ref");
  var c=SchemaCatalogue.fromClasspath();var p=c.requirePublished(declaration.required("schema_ref").asText(),declaration.required("schema_sha256").asText());
  if(!"EvidenceBundle".equals(p.definition())||(!"artifacts".equals(p.name())&&!"artifacts-v2".equals(p.name())))throw new TenantAccessException(ErrorCode.SCHEMA_UNSUPPORTED);
  return p;
 }
}
