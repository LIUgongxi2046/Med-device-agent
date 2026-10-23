package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.*;
import com.ruoyi.medical.persistence.mapper.ConfigurationDiscoveryMapper;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import com.ruoyi.medical.persistence.mapper.TaskCreationAgentDirectoryMapper;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.security.core.Authentication;
import org.springframework.util.function.ThrowingSupplier;
import org.springframework.web.bind.annotation.*;

@RestController
public final class TaskCreationAgentDirectoryController {
    private static final String PURPOSE="TASK_CREATION";
    private final ApplicationSessionIdentity identities;
    private final EnterpriseAccess access;
    private final ResourceReadAuthority resources;
    private final PermissionMapper permissions;
    private final TaskCreationAgentDirectoryMapper mapper;
    private final ConfigurationDiscoveryMapper clock;
    private final ConfigurationDiscoveryCursor cursors;
    private final AgentReleaseStore releases;
    private final AgentReleaseScopeAuthority scopes;
    private final TemplateDependencyPolicy policy;
    private final IdentityDirectoryPolicy identity;
    private final TaskCreationAgentDirectorySchema schema;
    private final PermissionSchema json;
    public TaskCreationAgentDirectoryController(ApplicationSessionIdentity identities,EnterpriseAccess access,ResourceReadAuthority resources,
            PermissionMapper permissions,TaskCreationAgentDirectoryMapper mapper,ConfigurationDiscoveryMapper clock,ConfigurationDiscoveryCursor cursors,
            AgentReleaseStore releases,AgentReleaseScopeAuthority scopes,TemplateDependencyPolicy policy,IdentityDirectoryPolicy identity,
            TaskCreationAgentDirectorySchema schema,PermissionSchema json) {
        this.identities=identities;this.access=access;this.resources=resources;this.permissions=permissions;this.mapper=mapper;this.clock=clock;this.cursors=cursors;
        this.releases=releases;this.scopes=scopes;this.policy=policy;this.identity=identity;this.schema=schema;this.json=json;
    }
    @GetMapping("/api/projects/{projectId}/task-creation-agent-releases")
    public JsonNode read(HttpServletRequest request,HttpServletResponse response,Authentication authentication,@PathVariable String projectId)throws IOException {
        WireValues.uuid(projectId);String context=BusinessAuditController.headers(request,response);var query=schema.query(request.getParameterMap());
        if(request.getInputStream().read()!=-1)throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
        return access.execute(identities.requireCurrent(request,authentication),admission->ThrowingSupplier.of(()->{
            if(!context.equals(admission.context().context_revision()))fail(ErrorCode.CONTEXT_CHANGED);
            String tenant=admission.context().tenant_id();var target=new PermissionProtocol.Scope(tenant,projectId,"PROJECT",projectId);
            var read=resources.require(admission,target,"project.read",PURPOSE);var create=resources.require(admission,target,"task.create",PURPOSE);
            if(!List.of("ACTIVE").equals(permissions.projectStatus(projectId)))fail(ErrorCode.ACCESS_DENIED);
            var values=new HashMap<String,Object>();values.put("userId",admission.actorId());values.put("projectId",projectId);
            String initialClock=Objects.requireNonNull(clock.authorizationClock(values)),binding=binding(admission,projectId,query,initialClock);
            String after=cursors.after(query.after(),binding);values.put("after",after);values.put("q",query.q());values.put("limit",query.limit()+1);
            var candidates=mapper.candidates(values);if(candidates.size()>query.limit()+1)mismatch();
            var result=json.object().put("schema_version","1.0.0").put("tenant_id",tenant).put("project_id",projectId);
            result.set("current_context",json.tree(admission.context()));result.set("query",schema.filters(query));var items=result.putArray("items");
            var verified=new ArrayList<AgentReleaseScopeAuthority.VerifiedScope>();String previous=after,last=null;
            for(var candidate:candidates){WireValues.uuid(candidate.releaseId());if(!tenant.equals(candidate.tenantId())||previous!=null&&previous.compareTo(candidate.releaseId())>=0)mismatch();previous=candidate.releaseId();}
            for(var candidate:candidates.subList(0,Math.min(query.limit(),candidates.size()))) {
                last=candidate.releaseId();WireValues.uuid(candidate.agentId());WireValues.uuid(candidate.agentVersionId());WireValues.uuid(candidate.evaluationId());WireValues.uuid(candidate.artifactId());
                ProjectSchema.text(candidate.name(),100);
                if(candidate.projectId()!=null&&!projectId.equals(candidate.projectId()))mismatch();
                if(resources.find(admission,new PermissionProtocol.Scope(tenant,candidate.projectId(),"CONFIGURATION",candidate.agentId()),"configuration.read",PURPOSE).isEmpty()
                    ||resources.find(admission,new PermissionProtocol.Scope(tenant,candidate.evaluationProjectId(),"EVALUATION",candidate.evaluationId()),"evaluation.read",PURPOSE).isEmpty()
                    ||resources.find(admission,new PermissionProtocol.Scope(tenant,candidate.artifactProjectId(),"ARTIFACT",candidate.artifactId()),"artifact.read",PURPOSE).isEmpty())continue;
                var stored=releases.read(admission,candidate.releaseId(),PURPOSE);var agent=stored.agent();
                if(!"PUBLISHED".equals(agent.status())||!candidate.agentId().equals(agent.agentId())||!candidate.agentVersionId().equals(agent.agentVersionId())
                    ||!Objects.equals(candidate.projectId(),agent.projectId())||!candidate.evaluationId().equals(agent.evaluationId())
                    ||!candidate.artifactId().equals(stored.release().manifestArtifactId())||!candidate.revision().equals(agent.revision())||!candidate.updatedAt().equals(agent.updatedAt()))mismatch();
                var permitted=scopes.findForCreation(stored,target,identity.classification(),policy.limits());if(permitted.isEmpty())continue;
                verified.add(permitted.get());var manifest=stored.content().manifest();
                var item=json.object().put("release_id",candidate.releaseId()).put("agent_id",agent.agentId()).put("agent_version_id",agent.agentVersionId())
                    .put("name",candidate.name()).put("status","PUBLISHED").put("current",true).put("released_at",DatabaseTimestamp.format(stored.release().releasedAt()))
                    .put("max_cost_amount",manifest.limits().maxCostAmount()).put("currency",manifest.limits().currency());
                var templates=item.putArray("template_versions");for(var template:manifest.templateVersions())templates.add(json.tree(new PermissionProtocol.Basis(template.versionId(),template.contentSha256())));
                var source=manifest.releaseScope().sourceScopePolicy();item.set("source_scope_policy",json.tree(new PermissionProtocol.Basis(source.versionId(),source.contentSha256())));items.add(item);
            }
            boolean complete=candidates.size()<=query.limit();result.put("complete",complete);result.set("next_after",complete?json.tree(null):json.tree(cursors.next(Objects.requireNonNull(last),binding)));
            if(!initialClock.equals(clock.authorizationClock(values)))fail(ErrorCode.CURSOR_INVALID);
            for(var scope:verified)scopes.requireUnchanged(scope);
            if(!read.equals(resources.require(admission,target,"project.read",PURPOSE))||!create.equals(resources.require(admission,target,"task.create",PURPOSE)))fail(ErrorCode.SCOPE_STALE);
            var output=schema.envelope(result);response.setHeader("X-Request-Id",output.required("request_id").asText());return output;
        }).get());
    }
    private String binding(EnterpriseAccess.Admission admission,String projectId,TaskCreationAgentDirectorySchema.Query query,String clock)throws IOException {
        var value=json.object().put("kind","TASK_CREATION_AGENT_DIRECTORY").put("project_id",projectId).put("actor",admission.actorId()).put("session",admission.subject().sessionId())
                .put("identity_mode",admission.subject().binding().view().mode()).put("context_id",admission.contextId()).put("authorization_clock",clock).put("classification",identity.classification());
        value.set("context",json.tree(admission.context()));value.set("query",schema.filters(query));value.set("limits",json.tree(policy.limits()));return DigestUtils.sha256Hex(json.canonical(value));
    }
    private static void fail(ErrorCode code){throw new TenantAccessException(code);}
    private static void mismatch(){fail(ErrorCode.CONTENT_MISMATCH);}
    @ExceptionHandler(SchemaRejection.class)void invalid(SchemaRejection failure,HttpServletResponse response)throws IOException{new IdentityErrorResponseWriter().write(response,422,failure.code());}
    @ExceptionHandler(TenantAccessException.class)void denied(TenantAccessException failure,HttpServletResponse response)throws IOException{new IdentityErrorResponseWriter().write(response,RuleVersionController.errorStatus(failure.code()),failure.code());}
}
