package com.ruoyi.medical.persistence;

import static com.ruoyi.medical.persistence.TaskProfessionalPackageAuthoringSchema.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.IdentityDirectoryPolicy;
import com.ruoyi.medical.persistence.mapper.*;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/** Explicit project capability authority, semantic dependencies and genuine source-content closure. */
@Component
@ConditionalOnProperty(prefix="medical.task-professional-package-authoring",name="enabled",havingValue="true")
public final class TaskProfessionalPackageAuthoringAuthority {
    private final JdbcTemplate db;private final PermissionSchema json;private final TaskProfessionalPackageAuthoringSchema schema;
    private final ResourceReadAuthority resources;private final AgentPublicationDependencies dependencies;private final FormalCapabilityIdentityAuthority capabilities;
    private final FormalAgentCandidateAuthority candidates;private final ArtifactRegistryMapper artifacts;private final SourceReadMapper sources;
    private final DependencyLineage lineage;private final EvaluationDependencyPolicy policy;private final EvaluationReferences profiles;private final IdentityDirectoryPolicy identity;
    private final VersionResourceMapper owners;
    @org.springframework.beans.factory.annotation.Autowired
    public TaskProfessionalPackageAuthoringAuthority(DataSource data,PermissionSchema json,TaskProfessionalPackageAuthoringSchema schema,
            ResourceReadAuthority resources,AgentPublicationDependencies dependencies,FormalCapabilityIdentityAuthority capabilities,FormalAgentCandidateAuthority candidates,
            ArtifactRegistryMapper artifacts,SourceReadMapper sources,DependencyLineage lineage,EvaluationDependencyPolicy policy,EvaluationReferences profiles,
            IdentityDirectoryPolicy identity,VersionResourceMapper owners){db=new JdbcTemplate(data);db.setQueryTimeout(5);this.json=json;this.schema=schema;this.resources=resources;this.dependencies=dependencies;this.capabilities=capabilities;this.candidates=candidates;this.artifacts=artifacts;this.sources=sources;this.lineage=lineage;this.policy=policy;this.profiles=profiles;this.identity=identity;this.owners=owners;}
    private TaskProfessionalPackageAuthoringAuthority(TaskProfessionalPackageAuthoringAuthority original,TaskProfessionalPackageAuthoringSchema selected){db=original.db;json=original.json;schema=selected;resources=original.resources;dependencies=original.dependencies;capabilities=original.capabilities;candidates=original.candidates;artifacts=original.artifacts;sources=original.sources;lineage=original.lineage;policy=original.policy;profiles=original.profiles;identity=original.identity;owners=original.owners;}
    TaskProfessionalPackageAuthoringAuthority forSchema(TaskProfessionalPackageAuthoringSchema selected){return new TaskProfessionalPackageAuthoringAuthority(this,selected);}
    record Content(List<String> roots,List<ArtifactRegistryProtocol.MemberRow> members){Content{roots=List.copyOf(roots);members=List.copyOf(members);}}
    record Checked(JsonNode proof,Content content,String classification){Checked{proof=proof.deepCopy();}@Override public JsonNode proof(){return proof.deepCopy();}}
    PermissionProtocol.GrantRow require(EnterpriseAccess.Admission a,String context,String project)throws Exception {
        active(a);WireValues.uuid(project);if(!context.equals(a.context().context_revision()))throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);
        var status=one(db.query("SELECT status FROM project WHERE tenant_id=? AND project_id=? LIMIT 2 FOR UPDATE",(r,n)->r.getString(1),a.context().tenant_id(),project));
        if(!Set.of("PENDING","ACTIVE").contains(status))throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
        return resources.require(a,new PermissionProtocol.Scope(a.context().tenant_id(),project,"PROJECT",project),"capability.configure",PURPOSE);
    }
    JsonNode preparation(EnterpriseAccess.Admission a,String context,String project)throws Exception {
        require(a,context,project);policy.requireConfigured();for(String k:KINDS)requireOutputProfile(k);
        var p=json.object().put("schema_version",schema.version()).put("project_id",project).put("context_revision",context).put("maximum_package_bytes",MAXIMUM_BYTES).put("maximum_choices",MAXIMUM_CHOICES).put("recovery_mode","ORIGINAL_ONLY_MAY_REGISTER");
        var kinds=p.putArray("kinds");for(String kind:List.of("PROMPT","PROFILE","ROLE")){var k=json.object().put("kind",kind);k.set("schema",schema.descriptor(kind));kinds.add(k);}
        var prompts=p.putArray("prompts");for(var row:bounded(db.queryForList("SELECT c.capability_version,c.content_sha256 FROM capability_version c JOIN lineage_edge e ON e.tenant_id=c.tenant_id AND e.consumer_version_id=c.capability_version AND e.relation_kind='FORMAL_CAPABILITY_SOURCE' JOIN artifact_content_binding b ON b.tenant_id=e.tenant_id AND b.artifact_id=e.upstream_version_id WHERE c.tenant_id=? AND c.project_id=? AND c.kind='PROMPT' AND c.status='DRAFT' AND b.schema_ref=? ORDER BY c.capability_version LIMIT 257",a.context().tenant_id(),project,schema.ref("PROMPT"))))try{prompts.add(prompt(a,project,new PermissionProtocol.Basis(str(row,"capability_version"),str(row,"content_sha256"))));}catch(TenantAccessException e){unavailable(e);}
        var profiles=p.putArray("profiles");for(var row:bounded(db.queryForList("SELECT a.artifact_id,a.sha256 FROM artifact a JOIN artifact_content_binding b ON b.tenant_id=a.tenant_id AND b.artifact_id=a.artifact_id WHERE a.tenant_id=? AND a.project_id=? AND b.schema_ref=? ORDER BY a.artifact_id LIMIT 257",a.context().tenant_id(),project,schema.ref("PROFILE"))))try{var artifact=artifact(a,project,new PermissionProtocol.Basis(str(row,"artifact_id"),str(row,"sha256")),schema.ref("PROFILE"));var v=json.object();v.set("artifact",artifact);v.set("schema",schema.descriptor("PROFILE"));profiles.add(v);}catch(TenantAccessException e){unavailable(e);}
        var adapters=p.putArray("adapters");adapterChoices(a,project).values().forEach(adapters::add);
        var sourceChoices=p.putArray("sources");for(var row:bounded(db.queryForList("SELECT v.source_version,v.original_sha256 FROM source_version v JOIN source s ON s.tenant_id=v.tenant_id AND s.source_id=v.source_id WHERE v.tenant_id=? AND (s.owner_project_id IS NULL OR s.owner_project_id=?) ORDER BY v.source_version LIMIT 257",a.context().tenant_id(),project)))try{sourceChoices.add(source(a,project,new PermissionProtocol.Basis(str(row,"source_version"),str(row,"original_sha256"))));}catch(TenantAccessException e){unavailable(e);}
        var prerequisites=p.putArray("prerequisites");if(adapters.isEmpty())prerequisites.add("MANAGED_ADAPTER_IMPORT_REQUIRED");for(String x:List.of("PHYSICAL_PROFILE_DEPLOYMENT_REQUIRED","INDEPENDENT_CAPABILITY_AUTHORING_REQUIRED","INDEPENDENT_RELEASE_APPROVAL_REQUIRED"))prerequisites.add(x);
        var digest=p.deepCopy();digest.put("tenant_id",a.context().tenant_id()).put("created_by",a.actorId());digest.set("current_context",json.tree(a.context()));p.put("preparation_sha256",schema.hash(digest));schema.validate("Preparation",p);return p;
    }
    Checked check(EnterpriseAccess.Admission a,String context,String project,JsonNode request)throws Exception {
        var grant=require(a,context,project);schema.validateRequest(request);String kind=request.required("kind").textValue();requireOutputProfile(kind);
        var roots=new TreeMap<String,PermissionProtocol.Basis>();var pins=json.object();var packageValue=request.required("package");
        for(var s:request.required("content_sources")){var b=basis(s);pins.set(b.version_id(),source(a,project,b));add(roots,b);}
        if("ROLE".equals(kind)){var b=basis(packageValue.required("prompt_basis"));pins.set("prompt",prompt(a,project,b));add(roots,b);var f=artifactBasis(packageValue.required("profile_artifact"));var actual=artifact(a,project,f,schema.ref("PROFILE"));if(!actual.equals(packageValue.required("profile_artifact")))bad();pins.set("profile",actual);add(roots,f);}
        if("PROFILE".equals(kind)){var adapter=packageValue.required("adapter_artifact");var choice=adapterChoices(a,project).get(adapter.required("artifact_id").textValue());if(choice==null||!choice.required("artifact").equals(adapter))throw new TenantAccessException(ErrorCode.ACCESS_DENIED);pins.set("adapter",choice);add(roots,artifactBasis(adapter));}
        var governed=dependencies.require(a,project,List.copyOf(roots.values()),PURPOSE);
        var content=content(roots,governed);String classification=identity.classification();for(String id:governed.versionIds()){for(var s:sources.source(id))classification=FormalEvaluationContextPackages.max(classification,s.classification(),one(sources.rights(s.rightsRevision())).classification());for(var ar:artifacts.artifact(id))classification=FormalEvaluationContextPackages.max(classification,ar.classification());}
        var proof=json.object().put("tenant_id",a.context().tenant_id()).put("project_id",project).put("actor_id",a.actorId()).put("classification",classification);proof.set("grant",AgentPublicationWitness.tree(grant));proof.set("selected",pins);proof.set("dependencies",governed.authorizations());proof.set("content",json.tree(content));
        return new Checked(proof,content,classification);
    }
    List<PermissionProtocol.Basis> packageReads(EnterpriseAccess.Admission a,String project,JsonNode request)throws Exception{
        var result=new ArrayList<PermissionProtocol.Basis>();if("ROLE".equals(request.required("kind").textValue())){var p=request.required("package");result.add(capabilities.require(a,basis(p.required("prompt_basis")),"PROMPT",project,PURPOSE).sourceArtifact());result.add(artifactBasis(p.required("profile_artifact")));}return List.copyOf(result);
    }
    JsonNode artifact(EnterpriseAccess.Admission a,String project,PermissionProtocol.Basis b,String expectedSchema)throws Exception {
        var row=one(artifacts.artifact(b.version_id()));var binding=one(artifacts.binding(b.version_id()));dependencies.require(a,project,List.of(b),PURPOSE);
        if(!project.equals(row.projectId())||!project.equals(binding.projectId())||!a.context().tenant_id().equals(row.tenantId())||!b.content_sha256().equals(row.sha256())||!row.sha256().equals(row.contentSha256())||expectedSchema!=null&&!expectedSchema.equals(binding.schemaRef()))bad();
        StoredObjectReferences.decode(row.storedObject(),row.reference());var n=json.object().put("artifact_id",b.version_id()).put("sha256",row.sha256()).put("size_bytes",row.sizeBytes().toString()).put("media_type",row.mediaType());schema.validate(expectedSchema==null?"AnyArtifact":"Artifact",n);if(expectedSchema!=null&&!schema.packageHashForRef(expectedSchema).equals(binding.schemaDocumentSha256()))bad();return n;
    }
    private JsonNode prompt(EnterpriseAccess.Admission a,String project,PermissionProtocol.Basis b)throws Exception{var v=capabilities.require(a,b,"PROMPT",project,PURPOSE);var metadata=capabilities.requireRegisteredIdentity(a.context().tenant_id(),a.actorId(),b,"PROMPT",project);var n=json.object().put("config_id",metadata.configId()).put("status","DRAFT");n.set("version",json.tree(b));n.set("source_artifact",artifact(a,project,v.sourceArtifact(),schema.ref("PROMPT")));n.set("schema",schema.descriptor("PROMPT"));return n;}
    private JsonNode source(EnterpriseAccess.Admission a,String project,PermissionProtocol.Basis b)throws Exception{var s=one(sources.source(b.version_id()));var rights=one(sources.rights(s.rightsRevision()));if(!b.content_sha256().equals(s.originalSha256()))bad();dependencies.require(a,project,List.of(b),PURPOSE);var n=json.object().put("source_id",s.sourceId()).put("classification",s.classification());n.set("version",json.tree(b));n.set("rights",json.tree(new PermissionProtocol.Basis(rights.rightsRevision(),rights.contentSha256())));return n;}
    private Map<String,ObjectNode> adapterChoices(EnterpriseAccess.Admission a,String project)throws Exception {
        var choices=new TreeMap<String,ObjectNode>();for(var row:bounded(db.queryForList("SELECT version_id,content_sha256 FROM formal_agent_candidate_binding WHERE tenant_id=? AND project_id=? ORDER BY version_id LIMIT 257",a.context().tenant_id(),project)))try {
            var b=new PermissionProtocol.Basis(str(row,"version_id"),str(row,"content_sha256"));var candidate=candidates.require(a,b,project,PURPOSE);if("DISABLED".equals(candidate.status()))continue;
            for(var adapter:candidate.runtimeManifest().required("event_adapters"))if("C07".equals(adapter.required("component").textValue())){var impl=adapter.required("implementation");var actual=artifact(a,project,artifactBasis(impl),null);if(!actual.equals(impl))bad();var binding=one(artifacts.binding(artifactBasis(impl).version_id()));var choice=choices.get(artifactBasis(impl).version_id());if(choice==null){choice=json.object().put("component","C07");choice.set("artifact",actual);choice.set("schema",json.object().put("schema_ref",binding.schemaRef()).put("schema_sha256",binding.schemaDocumentSha256()));choice.putArray("candidates");choices.put(artifactBasis(impl).version_id(),choice);}var c=json.object().put("status",candidate.status());c.set("version",json.tree(b));choice.withArray("candidates").add(c);}
        }catch(TenantAccessException e){unavailable(e);}return choices;
    }
    private Content content(Map<String,PermissionProtocol.Basis> selected,AgentPublicationDependencies.Checked proof)throws Exception {
        var rights=new TreeMap<String,String>();for(String id:proof.versionIds())for(var s:sources.source(id))rights.put(s.sourceVersion(),s.rightsRevision());
        var roots=new TreeSet<String>();for(var root:selected.values()) {var graph=lineage.inspect(root.version_id(),policy.limits());for(var v:graph.versions().values()){if("SOURCE_VERSION".equals(v.entityKind()))roots.add(v.id());if("ARTIFACT".equals(v.entityKind()))for(var ar:artifacts.artifact(v.id()))for(var member:artifacts.members(ar.accessSetId()))roots.add(member.upstreamVersionId());}}
        var versions=new TreeMap<String,ArtifactRegistryProtocol.VersionRow>();for(String root:roots){var graph=lineage.inspect(root,policy.limits());graph.versions().forEach((id,v)->{var prior=versions.putIfAbsent(id,v);if(prior!=null&&!prior.equals(v))bad();});}if(versions.size()+1>policy.maximumVersions())throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);
        var members=new ArrayList<ArtifactRegistryProtocol.MemberRow>();if(!versions.isEmpty()){var rows=owners.ownersAcrossDomains(List.copyOf(versions.keySet()),Arrays.stream(VersionResourceProtocol.Domain.values()).map(Enum::name).toList());for(var v:versions.values()){var owner=one(rows.stream().filter(x->x.owner().versionId().equals(v.id())).toList()).owner();String right=rights.get(owner.sourceVersion());if(!"SOURCE".equals(owner.resourceKind())||right==null||owner.conversationId()!=null)bad();members.add(new ArtifactRegistryProtocol.MemberRow(v.id(),right));}}
        return new Content(List.copyOf(roots),members);
    }
    private void requireOutputProfile(String kind)throws Exception{var p=profiles.profile(schema.ref(kind),PURPOSE,"PROJECT");if(!"PROJECT".equals(p.resourceKind())||!"project.read".equals(p.action())||!schema.packageHash(kind).equals(p.schemaDocumentSha256())||!schema.packageHash(kind).equals(p.artifactSchemaManifest()))throw new TenantAccessException(ErrorCode.SCHEMA_UNSUPPORTED);}
    private static void add(Map<String,PermissionProtocol.Basis> roots,PermissionProtocol.Basis b){var old=roots.putIfAbsent(b.version_id(),b);if(old!=null&&!old.equals(b))bad();}
    static void active(EnterpriseAccess.Admission a){FormalEvaluationExecutionAdmission.current(a);if(a.formalWorker()||a.controlWorker()||a.recoveryWorker())throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}
    private static void unavailable(TenantAccessException e){if(!Set.of(ErrorCode.ACCESS_DENIED,ErrorCode.OBJECT_UNAVAILABLE,ErrorCode.SCOPE_STALE,ErrorCode.SOURCE_NOT_READY,ErrorCode.RIGHTS_PENDING).contains(e.code()))throw e;}
    static <T>List<T> bounded(List<T> rows){if(rows.size()>MAXIMUM_CHOICES)throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);return rows;}
    static <T>T one(List<T> rows){if(rows.size()!=1)bad();return rows.get(0);}static String str(Map<String,Object> row,String name){var value=row.get(name);if(value==null)bad();return value.toString();}
}
