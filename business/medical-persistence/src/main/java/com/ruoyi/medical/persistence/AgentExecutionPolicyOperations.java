package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.persistence.mapper.AgentExecutionPolicyMapper;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.util.function.ThrowingSupplier;

@Component public final class AgentExecutionPolicyOperations {
 private final EnterpriseAccess access;private final AgentExecutionPolicyService service;private final AgentExecutionPolicySchema schema;
 private final AgentExecutionPolicyMapper mapper;private final AgentAuthoringCursor cursors;private final PermissionSchema json;
 public AgentExecutionPolicyOperations(EnterpriseAccess access,AgentExecutionPolicyService service,AgentExecutionPolicySchema schema,AgentExecutionPolicyMapper mapper,AgentAuthoringCursor cursors,PermissionSchema json){this.access=access;this.service=service;this.schema=schema;this.mapper=mapper;this.cursors=cursors;this.json=json;}
 public JsonNode author(ApplicationSessionIdentity.Verified subject,String context,String key,byte[] bytes){return access.execute(subject,a->checked(()->{current(a,context);return envelope(service.author(a,key,bytes));}));}
 public JsonNode decide(ApplicationSessionIdentity.Verified subject,String context,String key,byte[] bytes){return access.execute(subject,a->checked(()->{current(a,context);return envelope(service.decide(a,key,bytes));}));}
 public JsonNode read(ApplicationSessionIdentity.Verified subject,String context,String version){WireValues.uuid(version);return access.execute(subject,a->checked(()->{current(a,context);return envelope(service.read(a,version));}));}
 public JsonNode record(ApplicationSessionIdentity.Verified subject,String context,String record){WireValues.uuid(record);return access.execute(subject,a->checked(()->{current(a,context);return envelope(service.record(a,record));}));}
 public JsonNode receipt(ApplicationSessionIdentity.Verified subject,String context,String version,String record,String operation){WireValues.uuid(version);WireValues.uuid(record);WireValues.uuid(operation);return access.execute(subject,a->checked(()->{current(a,context);return envelope(service.receipt(a,version,record,operation));}));}
 public JsonNode query(ApplicationSessionIdentity.Verified subject,String context,byte[] bytes){return access.execute(subject,a->checked(()->{
  current(a,context);var query=schema.bind("Query",bytes,AgentExecutionPolicyProtocol.ListQuery.class);
  var owner=new AgentAuthoringCursor.Owner(a.context().tenant_id(),a.actorId(),subject.sessionId(),context,a.context().auth_epoch(),a.context().governance_epoch(),query.limit(),"EXECUTION_POLICY:"+Objects.toString(query.projectId(),"ENTERPRISE"));
  String after=cursors.resolve(query.cursor(),owner);var v=AgentExecutionPolicyStore.values(a,null);v.put("projectId",query.projectId());v.put("afterId",after);v.put("scanLimit",query.limit()+1);var candidates=mapper.list(v);
  if(candidates.size()>query.limit()+1)AgentExecutionPolicyStore.bad();var response=json.object().put("schema_version","1.0.0");var items=response.putArray("items");String last=after;
  for(int i=0;i<Math.min(query.limit(),candidates.size());i++){String id=candidates.get(i);WireValues.uuid(id);if(id.compareTo(last)<=0)AgentExecutionPolicyStore.bad();last=id;try{items.add(service.read(a,id));}catch(TenantAccessException e){if(e.code()!=ErrorCode.ACCESS_DENIED)throw e;}}
  if(candidates.size()>query.limit())response.put("next_cursor",cursors.issue(owner,last));else response.putNull("next_cursor");return envelope(schema.validate("List",response));
 }));}
 private JsonNode envelope(JsonNode data){return json.tree(new InterfaceProtocol.Envelope("1.0.0",UUID.randomUUID().toString(),"1",data));}
 private static void current(EnterpriseAccess.Admission a,String context){PermissionSchema.revision(context);if(!a.context().context_revision().equals(context))throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);}
 private static <T>T checked(ThrowingSupplier<T> call){return call.get((message,e)->e instanceof com.fasterxml.jackson.core.JsonProcessingException?new SchemaRejection(ErrorCode.SCHEMA_INVALID):new IllegalStateException("EXECUTION_POLICY_OPERATION_FAILED",e));}
}
