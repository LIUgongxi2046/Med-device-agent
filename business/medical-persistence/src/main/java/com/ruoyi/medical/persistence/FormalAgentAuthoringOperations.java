package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.persistence.mapper.*;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.util.function.ThrowingSupplier;
@Component public final class FormalAgentAuthoringOperations {
 private final FormalAgentDraftDirectory directory;
 private final EnterpriseAccess access;private final FormalAgentCandidateService candidates;private final FormalRuntimeCapabilityAuthoringService runtime;
 private final FormalAgentPublicationSchema schema;private final FormalRuntimeCapabilitySchema runtimeSchema;private final FormalRuntimeCapabilityIdentityAuthority runtimeIdentities;
 private final AgentPublicationJournal journal;private final FormalAgentCandidateMapper mapper;private final AgentAuthoringCursor cursors;private final PermissionSchema json;
 public FormalAgentAuthoringOperations(EnterpriseAccess access,FormalAgentCandidateService candidates,FormalRuntimeCapabilityAuthoringService runtime,FormalAgentPublicationSchema schema,FormalRuntimeCapabilitySchema runtimeSchema,FormalRuntimeCapabilityIdentityAuthority runtimeIdentities,AgentPublicationJournal journal,FormalAgentCandidateMapper mapper,AgentAuthoringCursor cursors,PermissionSchema json,FormalAgentDraftDirectory directory){this.directory=directory;this.access=access;this.candidates=candidates;this.runtime=runtime;this.schema=schema;this.runtimeSchema=runtimeSchema;this.runtimeIdentities=runtimeIdentities;this.journal=journal;this.mapper=mapper;this.cursors=cursors;this.json=json;}
 public JsonNode author(ApplicationSessionIdentity.Verified s,String context,String key,byte[] bytes){return access.execute(s,a->checked(()->{current(a,context);return envelope(candidates.author(a,key,bytes));}));}
 public JsonNode capability(ApplicationSessionIdentity.Verified s,String context,String key,byte[] bytes){return access.execute(s,a->checked(()->{current(a,context);return envelope(runtime.author(a,key,runtimeSchema.request("FormalCapability",bytes)));}));}
 public JsonNode record(ApplicationSessionIdentity.Verified s,String context,String record,boolean capability){WireValues.uuid(record);return access.execute(s,a->checked(()->{current(a,context);return envelope(capability?runtimeRecord(a,record):candidates.read(a,record));}));}
 public JsonNode receipt(ApplicationSessionIdentity.Verified s,String context,String record,String operation,boolean capability){WireValues.uuid(record);WireValues.uuid(operation);return access.execute(s,a->checked(()->{current(a,context);if(!capability)return envelope(candidates.receipt(a,record,operation));var original=journal.read(a,record);if(!original.createdBy().equals(a.actorId())||!original.operationId().equals(operation))throw new TenantAccessException(ErrorCode.ACCESS_DENIED);runtimeRecord(a,record);return envelope(schema.read(original.receiptJson()));}));}
 private JsonNode runtimeRecord(EnterpriseAccess.Admission a,String record)throws Exception {return directory.runtimeRecord(a,record);}

 private JsonNode envelope(JsonNode data){return json.tree(new InterfaceProtocol.Envelope("1.0.0",UUID.randomUUID().toString(),"1",data));}
 private static void current(EnterpriseAccess.Admission a,String context){PermissionSchema.revision(context);if(!context.equals(a.context().context_revision()))throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);}
 private static <T>T checked(ThrowingSupplier<T> s){return s.get((m,e)->e instanceof com.fasterxml.jackson.core.JsonProcessingException?new SchemaRejection(ErrorCode.SCHEMA_INVALID):new IllegalStateException("FORMAL_AGENT_AUTHORING_FAILED",e));}
}
