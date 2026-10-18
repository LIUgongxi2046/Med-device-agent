package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.persistence.mapper.*;
import java.io.IOException;
import java.util.*;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.function.ThrowingSupplier;
@Component public final class FormalAgentDraftDirectory {
    private final EnterpriseAccess access;
    private final FormalAgentCandidateMapper mapper;
    private final FormalAgentCandidateService agents;
    private final AgentPublicationJournal journal;
    private final FormalRuntimeCapabilityIdentityAuthority runtime;
    private final FormalRuntimeCapabilitySchema runtimeSchema;
    private final FormalAgentDraftDirectorySchema schema;
    private final AgentAuthoringCursor cursors;
    private final PermissionSchema json;
    private final ListableBeanFactory beans;
    public FormalAgentDraftDirectory(EnterpriseAccess access,FormalAgentCandidateMapper mapper,FormalAgentCandidateService agents,AgentPublicationJournal journal,FormalRuntimeCapabilityIdentityAuthority runtime,FormalRuntimeCapabilitySchema runtimeSchema,FormalAgentDraftDirectorySchema schema,AgentAuthoringCursor cursors,PermissionSchema json,ListableBeanFactory beans){
        this.access=access;
        this.mapper=mapper;
        this.agents=agents;
        this.journal=journal;
        this.runtime=runtime;
        this.runtimeSchema=runtimeSchema;
        this.schema=schema;
        this.cursors=cursors;
        this.json=json;
        this.beans=beans;
    }
    public JsonNode availability(ApplicationSessionIdentity.Verified s,String context){
        return access.execute(s,a->{
            current(a,context);var d=json.object().put("schema_version","1.0.0").put("maximum_page_size",20);d.set("current_context",json.tree(a.context()));d.putObject("features").put("formal_agent_authoring",enabled());return envelope(schema.validate("Availability",d),context);
        }
        );
    }
    boolean enabled(){
        return beans.getBeanNamesForType(FormalAgentAuthoringController.class,false,false).length>0;
    }
    public JsonNode query(ApplicationSessionIdentity.Verified s,String context,byte[] bytes){
        return access.execute(s,a->checked(()->{
            current(a,context);if(!enabled())throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);var q=schema.request(bytes);String kind=q.required("kind").asText(),project=q.path("project_id").isNull()?null:q.path("project_id").asText();int limit=q.required("limit").intValue();   var owner=new AgentAuthoringCursor.Owner(a.context().tenant_id(),a.actorId(),s.sessionId(),context,a.context().auth_epoch(),a.context().governance_epoch(),limit,"080:"+kind+":"+project);   String after=cursors.resolve(q.path("cursor").isNull()?null:q.path("cursor").asText(),owner);var v=journal.values(a);v.put("projectId",project);v.put("afterId",after);v.put("scanLimit",limit+1);v.put("capabilityKind",kind);   var ids="AGENT".equals(kind)?mapper.list(v):mapper.runtimeList(v);if(ids.size()>limit+1)bad();String ordered=after;for(String id:ids){
                WireValues.uuid(id);if(id.compareTo(ordered)<=0)bad();ordered=id;
            }
            String previous=after;var result=json.object().put("schema_version","1.0.0");var items=result.putArray("items");   for(int i=0;i<Math.min(limit,ids.size());i++){
                String id=ids.get(i);WireValues.uuid(id);if(id.compareTo(previous)<=0)bad();previous=id;    try {
                    JsonNode view;if("AGENT".equals(kind)){
                        var bindings=mapper.candidate(FormalAgentCandidateStore.values(a,id));var binding=AgentPublicationJournal.one(bindings);view=agents.read(a,binding.authorRecordId());
                    }
                    else {
                        var records=mapper.runtimeAuthor(FormalAgentCandidateStore.values(a,id));var record=AgentPublicationJournal.one(records);view=runtimeRecord(a,record.recordId());
                    }
                    items.add(summary(view,kind,project));
                }
                catch(TenantAccessException e){
                    if(!Set.of(ErrorCode.ACCESS_DENIED,ErrorCode.SOURCE_NOT_READY,ErrorCode.SOURCE_MISSING).contains(e.code()))throw e;
                }
            }
            if(ids.size()>limit)result.put("next_cursor",cursors.issue(owner,previous));else result.putNull("next_cursor");return envelope(schema.validate("Page",result),"1");
        }
        ));
    }
    JsonNode runtimeRecord(EnterpriseAccess.Admission a,String id)throws IOException {
        var original=journal.read(a,id);
        if(!"AUTHOR".equals(original.kind()))throw new TenantAccessException(ErrorCode.OBJECT_UNAVAILABLE);
        var request=runtimeSchema.validate("FormalCapability",runtimeSchema.read(original.requestJson()));
        var proof=runtime.require(a,new PermissionProtocol.Basis(original.resourceId(),original.contentSha256()),request.required("kind").asText(),original.projectId(),"AGENT_PUBLICATION");
        var out=json.object().put("schema_version","1.0.0").put("record_id",id).put("resource_id",original.resourceId()).put("kind","AUTHOR").put("content_sha256",original.contentSha256()).put("status","DRAFT").put("production_eligible",false);
        out.set("content",proof.canonicalDefinition());
        return schema.validate("RuntimeView",out);
    }
    private JsonNode summary(JsonNode view,String kind,String project)throws IOException {
        boolean agent="AGENT".equals(kind);
        var fixed=view.required(agent?"fixed":"content");
        var request=agent?fixed:fixed.required("request");
        if(!Objects.equals(project,request.path("project_id").isNull()?null:request.path("project_id").asText())||!agent&&!kind.equals(request.path("kind").asText()))bad();
        var out=json.object().put("record_id",agent?fixed.required("author_record_id").asText():view.required("record_id").asText()).put("version_id",fixed.required("version_id").asText()).put("config_id",fixed.required(agent?"agent_id":"config_id").asText()).put("kind",kind).put("created_by",fixed.required("created_by").asText()).put("created_at",fixed.required("created_at").asText()).put("status",view.required("status").asText()).put("production_eligible",false).put("content_sha256",runtimeSchema.hash(fixed));
        out.set("project_id",request.required("project_id"));
        if(agent)out.set("name",fixed.required("name"));
        else out.putNull("name");
        return schema.validate("Summary",out);
    }
    private JsonNode envelope(JsonNode d,String revision){
        return json.tree(new InterfaceProtocol.Envelope("1.0.0",UUID.randomUUID().toString(),revision,d));
    }
    private static void current(EnterpriseAccess.Admission a,String context){
        PermissionSchema.revision(context);
        if(!context.equals(a.context().context_revision()))throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);
    }
    private static <T>T checked(ThrowingSupplier<T> f){
        return f.get((message,error)->error instanceof com.fasterxml.jackson.core.JsonProcessingException ? new SchemaRejection(ErrorCode.SCHEMA_INVALID) : new IllegalStateException("FORMAL_DRAFT_DIRECTORY_READ_FAILED",error));
    }
    private static void bad(){
        throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);
    }
}
