package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.*;
import com.ruoyi.medical.persistence.mapper.*;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.function.ThrowingSupplier;

/** Create-only authored drafts; neither configuration permission nor content registration grants execution. */
@Component
public final class ConfigurationAuthoringService {
    static final String PURPOSE="CONFIGURATION_AUTHORING";
    static final String DEPENDENCY_PURPOSE="SIMULATION_EVALUATION";
    private static final Map<String,String> AGENT_EDGES=Map.of("role_version","SIMULATION_ROLE","prompt_revision","SIMULATION_PROMPT","model_config_revision","SIMULATION_MODEL");
    private final EnterpriseAccess access;
    private final ConfigurationAuthoringMapper mapper;
    private final EvaluationMapper evaluations;
    private final ArtifactRegistryMapper artifacts;
    private final ModelConfigurationService models;
    private final PermissionMapper permissions;
    private final ResourceReadAuthority authority;
    private final EvaluationReferences references;
    private final DependencyLineage lineage;
    private final EvaluationDependencyPolicy limits;
    private final PermissionSchema json;
    private final ConfigurationAuthoringSchema schema;
    private final IdentityDirectoryPolicy policy;
    private final TenantWatermarkMapper watermarks;
    private final JdbcTemplate jdbc;
    public ConfigurationAuthoringService(EnterpriseAccess access,ConfigurationAuthoringMapper mapper,EvaluationMapper evaluations,ArtifactRegistryMapper artifacts,ModelConfigurationService models,
            PermissionMapper permissions,ResourceReadAuthority authority,EvaluationReferences references,DependencyLineage lineage,
            EvaluationDependencyPolicy limits,PermissionSchema json,ConfigurationAuthoringSchema schema,IdentityDirectoryPolicy policy,
            TenantWatermarkMapper watermarks,@Qualifier("masterDataSource") DataSource master) {
        this.access=access;this.mapper=mapper;this.evaluations=evaluations;this.artifacts=artifacts;this.models=models;this.permissions=permissions;this.authority=authority;
        this.references=references;this.lineage=lineage;this.limits=limits;this.json=json;this.schema=schema;this.policy=policy;
        this.watermarks=watermarks;jdbc=new JdbcTemplate(master);
    }
    public JsonNode create(ApplicationSessionIdentity.Verified subject,String context,String key,byte[] bytes,boolean agent) {
        return access.execute(subject,admission->checked(()->{
            if(admission.simulationWorker())denied(ErrorCode.ACCESS_DENIED);
            var request=schema.request(bytes,agent);String project=nullable(request,"project_id");
            var witness=require(admission,project,null,agent?"agent.configure":"capability.configure");
            var values=values(admission,agent);values.put("key",key);values.put("requestHash",schema.requestHash(request,context,subject.sessionId(),agent));
            var prior=mapper.currentOperation(values);if(prior.size()>1)mismatch();
            if(!prior.isEmpty())return replay(admission,values,request,prior.get(0),agent);
            current(admission,context);project(project);
            var refs=roots(request,agent);references.require(admission,project,refs,DEPENDENCY_PURPOSE);
            if(!agent)references.artifact(project,refs.get(0));
            else selectedConfigurations(admission,request);
            requireArtifactClassifications(refs);
            JsonNode manifest=agent?dependencyManifest(refs):null;
            String config=UUID.randomUUID().toString(),version=UUID.randomUUID().toString();
            values.put("projectId",project);values.put("configId",config);values.put("versionId",version);
            values.put("name",agent?request.path("name").textValue():null);
            var fixed=fixed(admission.context().tenant_id(),config,version,request,manifest,admission.actorId(),(LocalDateTime)values.get("now"));
            values.put("contentHash",schema.hash(fixed));
            if(agent){values.put("dependencyManifest",json.canonical(manifest));values.put("limits",json.canonical(request.path("limits")));}
            else {
                values.put("kind",request.path("kind").textValue());values.put("artifactDigest",request.path("source_artifact").path("content_sha256").textValue());
                values.put("inputSchema",json.canonical(request.path("input_schema_ref")));values.put("outputSchema",json.canonical(request.path("output_schema_ref")));
                values.put("toolScope",json.canonical(request.path("tool_scope")));
            }
            one(mapper.insertHead(values));one(mapper.insertVersion(values));
            one(agent?mapper.insertAgent(values):mapper.insertCapability(values));
            for(var edge:edges(request,agent).entrySet()) {
                values.put("upstreamId",edge.getValue().version_id());values.put("relation",edge.getKey());one(mapper.insertLineage(values));
            }
            one(mapper.initializeHead(values));
            var stored=single(mapper.stored(values));var reread=original(admission,values,stored,agent);
            if(!fixed.equals(reread))mismatch();
            return complete(admission,values,stored,witness);
        }));
    }
    public JsonNode read(ApplicationSessionIdentity.Verified subject,String context,String version,boolean agent) {
        WireValues.uuid(version);return access.execute(subject,admission->checked(()->{
            current(admission,context);var values=values(admission,agent);values.put("versionId",version);
            var stored=single(mapper.stored(values));var fixed=original(admission,values,stored,agent);
            var view=json.object();view.set("definition",fixed);view.put("content_sha256",stored.contentHash());view.put("status","DRAFT");
            view.put("revision","1");view.put("production_eligible",false);return schema.envelope(view,UUID.randomUUID().toString());
        }));
    }
    private JsonNode original(EnterpriseAccess.Admission admission,Map<String,Object> values,ConfigurationAuthoringProtocol.Stored stored,boolean agent)throws IOException {
        if(!admission.context().tenant_id().equals(stored.tenantId())||!stored.versionId().equals(values.get("versionId"))
                ||!stored.versionId().equals(stored.currentVersion())||!"DRAFT".equals(stored.status())||!"DRAFT".equals(stored.headStatus())
                ||!"1.0.0".equals(stored.schemaVersion())||!schema.manifest().equals(stored.manifest())||!policy.classification().equals(stored.classification()))mismatch();
        require(admission,stored.projectId(),stored.configId(),"configuration.read");
        var projection=schema.read(stored.projection());var request=json.object();request.put("schema_version","1.0.0");request.put("execution_mode","SIMULATION");request.set("project_id",json.tree(stored.projectId()));
        var parents=mapper.parents(values);var expected=agent?new HashSet<>(AGENT_EDGES.values()):Set.of("CAPABILITY_SOURCE_ARTIFACT");
        if(parents.size()!=expected.size())mismatch();var seen=new HashSet<String>();
        for(var parent:parents) {
            if(!expected.contains(parent.relation())||!seen.add(parent.relation()))mismatch();
            String field=agent?AGENT_EDGES.entrySet().stream().filter(e->e.getValue().equals(parent.relation())).findFirst().orElseThrow().getKey():"source_artifact";
            request.set(field,json.tree(new PermissionProtocol.Basis(parent.versionId(),parent.hash())));
        }
        JsonNode manifest=null;
        if(agent) {if(!"AGENT".equals(stored.kind()))mismatch();request.put("name",stored.name());request.set("limits",projection.path("limits"));manifest=projection.path("dependency_manifest");}
        else {
            request.put("kind",stored.kind());for(String field:List.of("input_schema_ref","output_schema_ref","tool_scope"))request.set(field,projection.path(field));
            if(!request.path("source_artifact").path("content_sha256").equals(projection.path("artifact_digest")))mismatch();
        }
        schema.validate(request,agent);
        var fixed=fixed(stored.tenantId(),stored.configId(),stored.versionId(),request,manifest,stored.createdBy(),stored.createdAt());
        String hash=schema.hash(fixed);if(!hash.equals(stored.contentHash())||!hash.equals(stored.registryHash()))mismatch();
        references.require(admission,stored.projectId(),List.of(new PermissionProtocol.Basis(stored.versionId(),hash)),DEPENDENCY_PURPOSE);
        requireArtifactClassifications(List.of(new PermissionProtocol.Basis(stored.versionId(),hash)));
        if(!agent)references.artifact(stored.projectId(),roots(request,false).get(0));
        else {
            selectedConfigurations(admission,request);
            var graph=lineage.inspect(stored.versionId(),limits.limits());schema.manifest(manifest);
            SimulationEvaluationCandidateAuthority.requireFrozenGraph(new EvaluationProtocol.Candidate(stored.versionId(),hash,stored.configId(),stored.createdBy(),manifest,
                    request.path("limits"),stored.schemaVersion(),stored.manifest()),graph);
            values.put("versionIds",graph.versions().keySet().stream().sorted().toList());
            SimulationEvaluationCandidateAuthority.requireConfigurationGraph(graph,evaluations.configurationStates(values),
                    request.path("role_version").path("version_id").textValue(),request.path("model_config_revision").path("version_id").textValue(),request.path("prompt_revision").path("version_id").textValue());
        }
        return fixed;
    }
    private void selectedConfigurations(EnterpriseAccess.Admission admission,JsonNode request)throws IOException {
        for(String field:List.of("role_version","prompt_revision")) {
            var values=values(admission,false);values.put("versionId",request.path(field).path("version_id").textValue());
            var row=single(mapper.stored(values));original(admission,values,row,false);
            if(!request.path(field).path("content_sha256").textValue().equals(row.contentHash())
                    ||!(field.equals("role_version")?"ROLE":"PROMPT").equals(row.kind()))mismatch();
        }
        var model=models.read(admission,request.path("model_config_revision").path("version_id").textValue(),true);
        if(!request.path("model_config_revision").path("content_sha256").equals(model.path("data").path("content_sha256")))mismatch();
    }
    private void requireArtifactClassifications(List<PermissionProtocol.Basis> refs) {
        for(var ref:refs)for(var version:lineage.inspect(ref.version_id(),limits.limits()).versions().values())
            if("ARTIFACT".equals(version.entityKind())&&!policy.classification().equals(single(artifacts.artifact(version.id())).classification()))
                denied(ErrorCode.ACCESS_DENIED);
    }
    private ObjectNode fixed(String tenant,String config,String version,JsonNode request,JsonNode manifest,String actor,LocalDateTime time) {
        var fixed=json.object();fixed.put("schema_version","1.0.0");fixed.put("tenant_id",tenant);fixed.put("config_id",config);fixed.put("version_id",version);
        fixed.set("request",request);if(manifest!=null)fixed.set("dependency_manifest",manifest);
        fixed.put("created_by",actor);fixed.put("created_at",time.toInstant(ZoneOffset.UTC).toString());
        schema.verify(manifest==null?"FixedCapability":"FixedAgent",fixed);return fixed;
    }
    JsonNode dependencyManifest(List<PermissionProtocol.Basis> refs) {
        var all=new TreeMap<String,String>();var relations=new HashSet<SourceReadProtocol.ParentRow>();
        for(var ref:refs) {
            var graph=lineage.inspect(ref.version_id(),limits.limits());
            if(!ref.content_sha256().equals(graph.versions().get(ref.version_id()).contentSha256()))mismatch();
            graph.versions().forEach((id,row)->{String old=all.putIfAbsent(id,row.contentSha256());if(old!=null&&!old.equals(row.contentSha256()))mismatch();});
            relations.addAll(graph.graph().edgeSet());
            if(all.size()+1>limits.maximumVersions()||relations.size()+refs.size()>limits.maximumRelations())denied(ErrorCode.CONTEXT_LIMIT);
        }
        var manifest=json.object();manifest.put("schema_version","1.0.0");manifest.put("manifest_kind","SIMULATION_CANDIDATE_DEPENDENCIES");
        var dependencies=manifest.putArray("dependencies");all.forEach((id,hash)->dependencies.add(json.tree(new PermissionProtocol.Basis(id,hash))));
        schema.manifest(manifest);return manifest;
    }
    private Map<String,PermissionProtocol.Basis> edges(JsonNode request,boolean agent) {
        var edges=new TreeMap<String,PermissionProtocol.Basis>();
        if(agent)AGENT_EDGES.forEach((field,relation)->edges.put(relation,basis(request.path(field))));
        else edges.put("CAPABILITY_SOURCE_ARTIFACT",basis(request.path("source_artifact")));return edges;
    }
    private List<PermissionProtocol.Basis> roots(JsonNode request,boolean agent){return List.copyOf(edges(request,agent).values());}
    private static PermissionProtocol.Basis basis(JsonNode node){return new PermissionProtocol.Basis(node.path("version_id").textValue(),node.path("content_sha256").textValue());}
    private JsonNode replay(EnterpriseAccess.Admission admission,Map<String,Object> values,JsonNode request,SelectionOperationRow operation,boolean agent)throws IOException {
        if(!values.get("requestHash").equals(operation.requestSha256()))denied(ErrorCode.IDEMPOTENCY_CONFLICT);
        if(!"COMPLETED".equals(operation.state())||!Integer.valueOf(201).equals(operation.httpStatus())||!"1.0.0".equals(operation.schemaVersion())||!schema.manifest().equals(operation.schemaManifestSha256()))mismatch();
        values.put("versionId",operation.targetId());var stored=single(mapper.stored(values));
        require(admission,stored.projectId(),stored.configId(),agent?"agent.configure":"capability.configure");
        var fixed=original(admission,values,stored,agent);if(!request.equals(fixed.path("request"))||!admission.actorId().equals(stored.createdBy()))mismatch();
        var response=schema.read(operation.responseRef());schema.verify("ReceiptEnvelope",response);var receipt=response.path("data");
        if(!stored.versionId().equals(receipt.path("version_id").textValue())||!stored.configId().equals(receipt.path("config_id").textValue())
                ||!operation.operationId().equals(receipt.path("operation_id").textValue())||!stored.contentHash().equals(receipt.path("content_sha256").textValue())
                ||!stored.tenantId().equals(receipt.path("tenant_id").textValue())||!stored.tenantId().equals(receipt.path("current_context").path("tenant_id").textValue())
                ||!json.tree(stored.projectId()).equals(receipt.path("project_id")))mismatch();
        return response;
    }
    private JsonNode complete(EnterpriseAccess.Admission admission,Map<String,Object> values,ConfigurationAuthoringProtocol.Stored stored,PermissionProtocol.GrantRow witness)throws IOException {
        var context=advanceContext(admission,values);String operationId=UUID.randomUUID().toString(),requestId=UUID.randomUUID().toString();
        var receipt=json.object();receipt.put("schema_version","1.0.0");receipt.put("operation_id",operationId);receipt.put("tenant_id",stored.tenantId());
        receipt.set("project_id",json.tree(stored.projectId()));receipt.put("config_id",stored.configId());receipt.put("version_id",stored.versionId());
        receipt.put("content_sha256",stored.contentHash());receipt.put("status","DRAFT");receipt.put("revision","1");receipt.put("production_eligible",false);receipt.set("current_context",json.tree(context));
        var response=schema.envelope(receipt,requestId);values.put("operationId",operationId);values.put("response",json.canonical(response));one(mapper.saveOperation(values));
        var audit=json.object();audit.put("schema_version","1.0.0");audit.put("operation_id",operationId);audit.put("request_sha256",(String)values.get("requestHash"));audit.set("after",receipt);
        audit.put("authority_grant_id",witness.grantId());audit.put("authority_revision",witness.revision().toString());
        String auditId=UUID.randomUUID().toString(),eventId=UUID.randomUUID().toString();
        one(permissions.saveAudit(admission.actorId(),stored.projectId(),auditId,"configuration.draft_created",json.canonical(audit),requestId,schema.manifest(),policy.classification()));
        one(permissions.saveEvent(admission.actorId(),eventId,stored.configId(),"1","configuration.draft_created",json.canonical(receipt)));
        values.put("auditId",auditId);values.put("eventId",eventId);values.put("requestId",requestId);values.put("resulting","1");
        if(!schema.read(single(mapper.audit(values))).equals(audit)||!schema.read(single(mapper.event(values))).equals(receipt))mismatch();
        var operation=single(mapper.currentOperation(values));if(!"COMPLETED".equals(operation.state())||!Integer.valueOf(201).equals(operation.httpStatus())
                ||!"1.0.0".equals(operation.schemaVersion())||!schema.manifest().equals(operation.schemaManifestSha256())
                ||!operationId.equals(operation.operationId())||!stored.versionId().equals(operation.targetId())
                ||!values.get("requestHash").equals(operation.requestSha256())||!response.equals(schema.read(operation.responseRef())))mismatch();
        return response;
    }
    private BusinessSessionProtocol.Context advanceContext(EnterpriseAccess.Admission admission, Map<String,Object> values) {
        values.put("governanceEpoch", admission.context().governance_epoch()); values.put("authEpoch", admission.context().auth_epoch());
        String governance = PermissionSchema.next(admission.context().governance_epoch()), revision = PermissionSchema.next(admission.context().context_revision());
        one(mapper.advanceGovernance(values));
        one(jdbc.update("""
                UPDATE platform_session_context SET context_revision=?,governance_epoch=?,revision=revision+1,updated_at=UTC_TIMESTAMP(6)
                WHERE context_id=? AND session_id=? AND user_id=? AND tenant_id=? AND context_revision=? AND auth_epoch=? AND governance_epoch=?
                """, revision, governance, admission.contextId(), admission.subject().sessionId(), admission.subject().user().user_id(),
                admission.context().tenant_id(), admission.context().context_revision(), admission.context().auth_epoch(), admission.context().governance_epoch()));
        var expected = new BusinessSessionProtocol.Context(admission.context().tenant_id(), revision, admission.context().auth_epoch(), governance);
        var current = jdbc.query("""
                SELECT tenant_id,context_revision,auth_epoch,governance_epoch FROM platform_session_context
                WHERE context_id=? AND session_id=? AND user_id=? AND tenant_id=?
                """, (row, ordinal) -> new BusinessSessionProtocol.Context(row.getString(1), row.getString(2), row.getString(3), row.getString(4)),
                admission.contextId(), admission.subject().sessionId(), admission.subject().user().user_id(), admission.context().tenant_id());
        var epochs = Objects.requireNonNull(watermarks.lockCurrentTenant());
        if (!current.equals(List.of(expected)) || !epochs.authEpoch().toString().equals(expected.auth_epoch())
                || !epochs.governanceEpoch().toString().equals(expected.governance_epoch())) mismatch();
        return expected;
    }

    private PermissionProtocol.GrantRow require(EnterpriseAccess.Admission admission,String project,String config,String action)throws IOException {
        var scope=config!=null?new PermissionProtocol.Scope(admission.context().tenant_id(),project,"CONFIGURATION",config)
                :new PermissionProtocol.Scope(admission.context().tenant_id(),project,project==null?"ENTERPRISE":"PROJECT",project==null?admission.context().tenant_id():project);
        return authority.require(admission,scope,action,action.equals("configuration.read")?DEPENDENCY_PURPOSE:PURPOSE);
    }
    private void project(String project){if(project!=null&&!Set.of("PENDING","ACTIVE").contains(single(permissions.projectStatus(project))))denied(ErrorCode.ACCESS_DENIED);}
    private Map<String,Object> values(EnterpriseAccess.Admission admission,boolean agent) {
        var values=new HashMap<String,Object>();values.put("userId",admission.actorId());values.put("agent",agent);
        values.put("headKind",agent?"AGENT":"CAPABILITY");values.put("entityKind",agent?"AGENT_VERSION":"CAPABILITY_VERSION");
        values.put("operationName",agent?"SIMULATION-AGENT-CREATE":"CP-05");values.put("manifest",schema.manifest());
        values.put("classification",policy.classification());values.put("now",Objects.requireNonNull(permissions.databaseTime()));return values;
    }
    private static String nullable(JsonNode node,String field){return node.path(field).isNull()?null:node.path(field).textValue();}
    private static void current(EnterpriseAccess.Admission admission,String header){if(!admission.context().context_revision().equals(header))denied(ErrorCode.CONTEXT_CHANGED);}
    private static <T>T checked(ThrowingSupplier<T> work){return work.get((message,failure)->failure instanceof com.fasterxml.jackson.core.JsonProcessingException
            ?new SchemaRejection(ErrorCode.SCHEMA_INVALID):new IllegalStateException("CONFIGURATION_AUTHORING_INVALID",failure));}
    private static <T>T single(List<T> rows){if(rows.size()!=1)denied(ErrorCode.OBJECT_UNAVAILABLE);return Objects.requireNonNull(rows.get(0));}
    private static void one(int count){if(count!=1)denied(ErrorCode.REVISION_CONFLICT);}
    private static void mismatch(){denied(ErrorCode.CONTENT_MISMATCH);}
    private static void denied(ErrorCode code){throw new TenantAccessException(code);}
}
