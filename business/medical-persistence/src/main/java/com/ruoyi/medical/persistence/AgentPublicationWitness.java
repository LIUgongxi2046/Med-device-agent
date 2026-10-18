package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.ruoyi.medical.contracts.ErrorCode;
import java.io.IOException;
import java.util.List;

/** Local deterministic historical witness encoding; it never queries or creates current permissions. */
public final class AgentPublicationWitness {
 private AgentPublicationWitness(){}
 private static final JsonMapper MAPPER=JsonMapper.builder().addModule(new JavaTimeModule()).disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build();
 public static JsonNode tree(Object value){return MAPPER.valueToTree(value);}
 public static PermissionProtocol.Scope scope(JsonNode n)throws IOException{return MAPPER.treeToValue(n,PermissionProtocol.Scope.class);}
 public static void requireRole(com.ruoyi.medical.persistence.mapper.AgentPublicationMapper.Journal row,JsonNode request,JsonNode auth,PermissionSchema schema)throws IOException{
  var permissions=request.path("tool_scope").path("permissions");var witnesses=auth.path("role_permissions");int size="ROLE".equals(request.path("kind").asText())?permissions.size():0;
  if(!witnesses.isArray()||witnesses.size()!=size)throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);
  for(int i=0;i<size;i++){
   var permission=permissions.get(i);var witness=witnesses.get(i);var scope=scope(permission.path("scope"));var grant=MAPPER.treeToValue(witness.path("grant"),PermissionProtocol.GrantRow.class);
   if(!permission.equals(witness.path("permission"))||!row.tenantId().equals(scope.tenant_id())||scope.project_id()!=null&&!java.util.Objects.equals(row.projectId(),scope.project_id())||grant==null||new PermissionAuthority(schema).findResource(scope,row.createdBy(),auth.path("identity_mode").asText(),permission.path("action").asText(),permission.path("purpose").asText(),List.of(grant),row.createdAt()).isEmpty())throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);
  }
 }
 public static void requireGovernance(com.ruoyi.medical.persistence.mapper.AgentPublicationMapper.Journal row,JsonNode auth,String action,String purpose,PermissionSchema schema)throws IOException {
  var grant=MAPPER.treeToValue(auth.path("governance"),PermissionProtocol.GrantRow.class);
  String mode=auth.path("identity_mode").asText();var scope=new PermissionProtocol.Scope(row.tenantId(),row.projectId(),row.projectId()==null?"ENTERPRISE":"PROJECT",row.projectId()==null?row.tenantId():row.projectId());
  if(grant==null||new PermissionAuthority(schema).findResource(scope,row.createdBy(),mode,action,purpose,List.of(grant),row.createdAt()).isEmpty())throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);
 }
}
