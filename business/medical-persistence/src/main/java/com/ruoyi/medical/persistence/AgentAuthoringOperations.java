package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.persistence.mapper.AgentPublicationMapper;
import java.io.IOException;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.util.function.ThrowingSupplier;

/** Transactional public operations for the bounded 073 authoring/policy module. */
@Component public final class AgentAuthoringOperations {
 private final EnterpriseAccess access;private final FormalCapabilityAuthoringService capabilities;private final FormalCapabilityIdentityAuthority identities;
 private final AgentPublicationJournal journal;private final AgentPublicationMapper mapper;
 private final AgentPublicationSchema schema;private final PermissionSchema json;private final AgentAuthoringCursor cursors;
 public AgentAuthoringOperations(EnterpriseAccess access,FormalCapabilityAuthoringService capabilities,FormalCapabilityIdentityAuthority identities,AgentPublicationJournal journal,AgentPublicationMapper mapper,AgentPublicationSchema schema,PermissionSchema json,AgentAuthoringCursor cursors){this.access=access;this.capabilities=capabilities;this.identities=identities;this.journal=journal;this.mapper=mapper;this.schema=schema;this.json=json;this.cursors=cursors;}
 public JsonNode write(ApplicationSessionIdentity.Verified subject,String context,String key,byte[] bytes,String kind){if(!"FORMAL_CAPABILITY_AUTHOR".equals(kind))throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);return access.execute(subject,a->checked(()->{current(a,context);var request=schema.request("FormalCapability",bytes);return envelope(capabilities.author(a,key,request));}));}

 public JsonNode read(ApplicationSessionIdentity.Verified subject,String context,String id){WireValues.uuid(id);return access.execute(subject,a->checked(()->{current(a,context);return envelope(view(a,id));}));}
 public JsonNode receipt(ApplicationSessionIdentity.Verified subject,String context,String id,String operation){WireValues.uuid(id);WireValues.uuid(operation);return access.execute(subject,a->checked(()->{current(a,context);view(a,id);var row=journal.read(a,id);if(!row.createdBy().equals(a.actorId())||!row.operationId().equals(operation))throw new TenantAccessException(ErrorCode.ACCESS_DENIED);return envelope(schema.validate("Receipt",schema.read(row.receiptJson())));}));}
 public JsonNode query(ApplicationSessionIdentity.Verified subject,String context,byte[] bytes){return access.execute(subject,a->checked(()->{
  current(a,context);var request=schema.request("CapabilityQuery",bytes);String kind=request.path("kind").asText();var owner=new AgentAuthoringCursor.Owner(a.context().tenant_id(),a.actorId(),subject.sessionId(),context,a.context().auth_epoch(),a.context().governance_epoch(),50,kind);
  String after=cursors.resolve(request.path("cursor").isNull()?null:request.path("cursor").asText(),owner);var v=journal.values(a);v.put("afterId",after);v.put("kind",kind);var candidates=mapper.candidateRecords(v);if(candidates.size()>51)bad();var data=json.object().put("schema_version","1.0.0");var items=data.putArray("items");
  int scan=Math.min(50,candidates.size());String previous=after;for(int i=0;i<scan;i++){String id=candidates.get(i);WireValues.uuid(id);if(id.compareTo(previous)<=0)bad();previous=id;try{items.add(view(a,id));}catch(TenantAccessException e){if(!Set.of(ErrorCode.ACCESS_DENIED,ErrorCode.SOURCE_NOT_READY,ErrorCode.SOURCE_MISSING).contains(e.code()))throw e;}}
  if(candidates.size()>50)data.put("next_cursor",cursors.issue(owner,previous));else data.putNull("next_cursor");return envelope(schema.validate("List",data));
 }));}
 private JsonNode view(EnterpriseAccess.Admission a,String id)throws IOException {
  var row=journal.read(a,id);JsonNode content;String status;
  if(!"FORMAL_CAPABILITY_AUTHOR".equals(row.kind()))throw new TenantAccessException(ErrorCode.OBJECT_UNAVAILABLE);
  var request=schema.read(row.requestJson());content=identities.require(a,new PermissionProtocol.Basis(row.resourceId(),row.contentSha256()),request.path("kind").asText(),row.projectId(),"AGENT_PUBLICATION").canonicalDefinition();status="DRAFT";

  var data=json.object().put("schema_version","1.0.0").put("record_id",id).put("resource_id",row.resourceId()).put("kind",row.kind()).put("content_sha256",row.contentSha256()).put("status",status).put("production_eligible",false);data.set("content",content);return schema.validate("RecordView",data);
 }
 private JsonNode envelope(JsonNode data){return json.tree(new InterfaceProtocol.Envelope("1.0.0",UUID.randomUUID().toString(),"1",data));}
 private static void current(EnterpriseAccess.Admission a,String context){if(!a.context().context_revision().equals(context))throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);}
 private static <T>T checked(ThrowingSupplier<T> call){return call.get((m,e)->e instanceof com.fasterxml.jackson.core.JsonProcessingException?new SchemaRejection(ErrorCode.SCHEMA_INVALID):new IllegalStateException("AGENT_AUTHORING_INVALID",e));}
 private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
