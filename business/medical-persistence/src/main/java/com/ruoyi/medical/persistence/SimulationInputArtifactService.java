package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import com.ruoyi.medical.contracts.WireValues;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.persistence.mapper.*;
import java.math.BigInteger;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.util.function.ThrowingSupplier;

/** Registers verified SOURCE original bytes as an ExactInput ARTIFACT; never writes an object or grants access. */
@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix="medical.simulation-input-artifact",name="enabled",havingValue="true")
public final class SimulationInputArtifactService {
    private static final String PURPOSE="SIMULATION_EVALUATION";
    private static final DependencyLineage.Limits LIMITS=new DependencyLineage.Limits(512,2048);
    private final EnterpriseAccess access;
    private final SimulationInvocationSources sources;
    private final SimulationFixtureMapper fixtures;
    private final SimulationInputArtifactMapper mapper;
    private final SourceReadMapper sourceRows;
    private final ArtifactRegistryMapper artifacts;
    private final ResourceReadAuthority resources;
    private final PermissionMapper permissions;
    private final PermissionSchema json;
    private final DependencyLineage lineage;
    private final EvaluationReferences references;
    private final DependencyManifestSchema manifests;
    private final DependencyAccessAuthority dependencies;
    private final RetrievalPolicy deployment;
    private final SchemaCatalogue catalogue;
    private final SimulationInputArtifactSchema schema;
    private final EvaluationDependencyPolicy policy;
    public record Link(String tenantId,String projectId,String caseId,String configRevision,String inputSourceVersion,
            String inputRightsRevision,String artifactId) { }
    private record Closure(List<ArtifactRegistryProtocol.MemberRow> members,List<SourceReadProtocol.ReadRequirement> requirements,
            String classification,List<String> roots,Map<String,ArtifactRegistryProtocol.ReadRequirement> artifactRequirements) { }
    public record Operation(String tenantId,String actorId,String key,String requestHash,String receipt,String schemaHash,String schemaVersion,String createdBy,String canonicalRequest,String bodyHash) { }
    public SimulationInputArtifactService(EnterpriseAccess access,SimulationInvocationSources sources,SimulationFixtureMapper fixtures,
            SimulationInputArtifactMapper mapper,SourceReadMapper sourceRows,ArtifactRegistryMapper artifacts,ResourceReadAuthority resources,
            PermissionMapper permissions,PermissionSchema json,DependencyLineage lineage,EvaluationReferences references,
            DependencyManifestSchema manifests,DependencyAccessAuthority dependencies,RetrievalPolicy deployment,EvaluationDependencyPolicy policy)throws Exception {
        this.access=access;this.sources=sources;this.fixtures=fixtures;this.mapper=mapper;this.sourceRows=sourceRows;this.artifacts=artifacts;
        this.resources=resources;this.permissions=permissions;this.json=json;this.lineage=lineage;this.references=references;
        this.manifests=manifests;this.dependencies=dependencies;this.deployment=deployment;this.policy=policy;
        catalogue=SchemaCatalogue.fromClasspath();schema=new SimulationInputArtifactSchema();
    }
    public JsonNode register(ApplicationSessionIdentity.Verified subject,String contextRevision,String caseId,String key,JsonNode request)throws Exception {
        WireValues.uuid(caseId);SimulationInputArtifactSchema.key(key);schema.validate("Request",request);PermissionSchema.revision(contextRevision);
        String project=nullable(request,"project_id"),config=request.required("model_config_revision").textValue();
        String requestHash=schema.requestHash(caseId,request,contextRevision,subject.sessionId());
        // Authorize the explicit producer on the real source resource before any remote object read.
        access.execute(subject,a->ThrowingSupplier.of(()->{
            context(a,contextRevision);var initial=values(a,project,config,caseId);initial.put("key",key);
            var priorOperation=mapper.operation(initial);if(priorOperation.size()>1)mismatch();
            if(!priorOperation.isEmpty()){verifyOperation(a,priorOperation.get(0),key,requestHash);verifyLookupRequest(a,priorOperation.get(0),key,caseId,request);}
            var row=one(fixtures.registration(initial));registerGrant(a,row);return true;
        }).get());
        var start=request.deepCopy();((com.fasterxml.jackson.databind.node.ObjectNode)start).put("case_id",caseId);
        var prepared=sources.prepareForContext(subject,start); // full immutable GET + raw hash + published ExactInput validation
        return access.execute(subject,a->ThrowingSupplier.of(()->{
            context(a,contextRevision);var row=prepared.registration();var values=values(a,project,config,caseId);
            same(one(mapper.lockFixture(values)),row.caseReceiptId());sources.revalidateForContext(a,prepared);
            values.put("key",key);values.put("requestHash",requestHash);
            var operations=mapper.operation(values);if(operations.size()>1)mismatch();
            if(!operations.isEmpty()){verifyOperation(a,operations.get(0),key,requestHash);verifyLookupRequest(a,operations.get(0),key,caseId,request);}
            var source=registerGrant(a,row);var closure=closure(a,row);
            var prior=mapper.link(values);if(prior.size()>1)mismatch();
            Link link;boolean created=prior.isEmpty();
            if(prior.isEmpty()){
                String artifact=UUID.randomUUID().toString(),set=UUID.randomUUID().toString();
                var dependency=new ArtifactRegistryProtocol.DependencyManifest("1.0.0",a.context().tenant_id(),project,set,closure.members());
                String dependencyJson=json.canonical(json.tree(dependency)),dependencyHash=hash(json.tree(dependency));
                var manifest=new ArtifactRegistryProtocol.ManifestRow(a.context().tenant_id(),project,set,dependencyJson,dependencyHash,dependencyHash,
                    BigInteger.valueOf(closure.members().size()),"1.0.0",manifests.manifestSha256());manifests.verify(manifest,closure.members());
                values.put("now",Objects.requireNonNull(permissions.databaseTime()));values.put("classification",closure.classification());
                values.put("versionId",set);values.put("kind","DEPENDENCY_ACCESS_SET");values.put("hash",dependencyHash);count(fixtures.insertContextVersion(values));
                values.put("accessId",set);values.put("dependencyJson",dependencyJson);values.put("memberCount",closure.members().size());
                values.put("dependencySchema",manifests.manifestSha256());count(fixtures.insertContextAccessSet(values));count(fixtures.insertContextDependencyManifest(values));
                for(var member:closure.members()){values.put("member",member);count(fixtures.insertContextDependencyMember(values));}
                values.put("relationKind","SIMULATION_INPUT_SOURCE");values.put("consumer",set);
                for(String root:closure.roots()){values.put("upstream",root);count(fixtures.insertContextLineage(values));}
                var ref=prepared.inputOriginal().reference();
                values.put("versionId",artifact);values.put("kind","ARTIFACT");values.put("hash",ref.sha256());count(fixtures.insertContextVersion(values));
                values.put("artifactId",artifact);values.put("reference",ref);values.put("storedObject",StoredObjectReferences.encode(ref));
                values.put("schemaHash",catalogue.schemaDigest("simulation-execution"));values.put("schemaRef",catalogue.schemaRef("simulation-execution","ExactInput"));
                values.put("configId",configurationId(prepared));values.put("dependencyHash",dependencyHash);
                count(mapper.insertArtifact(values));count(mapper.insertBinding(values));
                // Artifact graph contains the exact source closure, not the access-set pseudo-version.
                values.put("consumer",artifact);for(String root:closure.roots()){values.put("upstream",root);count(fixtures.insertContextLineage(values));}
                values.put("inputSourceVersion",row.inputSourceVersion());values.put("inputRightsRevision",row.inputRightsRevision());count(mapper.insertLink(values));
                link=one(mapper.link(values));
            }else link=prior.get(0);
            verify(a,prepared,source,closure,link);sources.revalidateForContext(a,prepared);registerGrant(a,row);
            var receipt=receipt(prepared,source,link);
            if(!operations.isEmpty()){
                JsonNode saved=schema.receipt(operations.get(0).receipt());same(receipt,saved);return saved;
            }
            values.put("receipt",json.canonical(receipt));values.put("operationSchema",schema.manifest());
            values.put("canonicalRequest",schema.canonicalRequest(caseId,request));values.put("bodyHash",schema.bodyHash(caseId,request));
            values.put("now",Objects.requireNonNull(permissions.databaseTime()));values.put("classification",closure.classification());
            count(mapper.insertOperation(values));
            var operation=one(mapper.operation(values));verifyOperation(a,operation,key,requestHash);verifyLookupRequest(a,operation,key,caseId,request);same(receipt,schema.receipt(operation.receipt()));
            if(created){
                var event=receipt.deepCopy();event.put("tenant_id",a.context().tenant_id());event.set("project_id",json.tree(project));
                event.put("dependency_manifest_sha256",one(artifacts.binding(link.artifactId())).dependencyManifestSha256());
                event.put("object_ref_sha256",hash(json.tree(prepared.inputOriginal().reference())));
                event.put("request_sha256",requestHash);event.put("workflow_schema_sha256",schema.manifest());
                String payload=json.canonical(event),eventId=UUID.randomUUID().toString();
                count(permissions.saveAudit(a.actorId(),project,UUID.randomUUID().toString(),"simulation.input-artifact.registered",payload,eventId,schema.manifest(),closure.classification()));
                count(permissions.saveEvent(a.actorId(),eventId,link.artifactId(),"1","simulation.input-artifact.registered",payload));
            }
            return receipt;
        }).get());
    }
    /** Recovery is read-only and deliberately independent of the former context/session request hash. */
    public JsonNode lookup(ApplicationSessionIdentity.Verified subject,String contextRevision,String caseId,JsonNode lookup)throws Exception {
        WireValues.uuid(caseId);PermissionSchema.revision(contextRevision);
        var lookupSchema=new SimulationInputArtifactLookupSchema();lookupSchema.validateRequest(lookup);
        String key=lookup.required("original_key").textValue();JsonNode original=lookup.required("original_request");
        String project=nullable(original,"project_id"),config=original.required("model_config_revision").textValue();
        Operation before=access.execute(subject,a->ThrowingSupplier.of(()->{
            context(a,contextRevision);var values=values(a,project,config,caseId);values.put("key",key);
            var rows=mapper.operation(values);if(rows.size()>1)mismatch();if(rows.isEmpty())return null;
            var operation=rows.get(0);verifyLookupRequest(a,operation,key,caseId,original);return operation;
        }).get());
        if(before==null)return lookupSchema.result(null);
        var start=original.deepCopy();((com.fasterxml.jackson.databind.node.ObjectNode)start).put("case_id",caseId);
        var prepared=sources.prepareForContext(subject,start);
        return access.execute(subject,a->ThrowingSupplier.of(()->{
            context(a,contextRevision);var values=values(a,project,config,caseId);values.put("key",key);
            var operation=one(mapper.operation(values));same(before,operation);verifyLookupRequest(a,operation,key,caseId,original);
            JsonNode saved=schema.receipt(operation.receipt());var input=saved.required("input_ref");
            requireRegisteredInput(a,prepared,new PermissionProtocol.Basis(input.required("version_id").textValue(),input.required("content_sha256").textValue()));
            var row=prepared.registration();var link=one(mapper.link(values));
            same(receipt(prepared,source(a,row),link),saved);return lookupSchema.result(saved);
        }).get());
    }
    private void verifyLookupRequest(EnterpriseAccess.Admission a,Operation operation,String key,String caseId,JsonNode original)throws Exception {
        operationIdentity(a,operation,key);
        var stored=schema.storedRequest(operation.canonicalRequest(),operation.bodyHash());
        if(!schema.bodyHash(caseId,original).equals(operation.bodyHash())
                ||!schema.canonicalRequest(caseId,original).equals(schema.canonicalRequest(stored.required("case_id").textValue(),stored.required("request"))))
            throw new TenantAccessException(ErrorCode.IDEMPOTENCY_CONFLICT);
    }
    private com.fasterxml.jackson.databind.node.ObjectNode receipt(SimulationInvocationSources.PreparedFixture prepared,
            SourceReadProtocol.SourceRow source,Link link)throws Exception {
        var row=prepared.registration();var receipt=json.object();receipt.put("schema_version","1.0.0");
        receipt.put("input_data_kind","SIMULATION");receipt.put("production_eligible",false);
        receipt.putObject("input_ref").put("version_id",link.artifactId()).put("content_sha256",prepared.inputOriginal().sha256());
        receipt.putObject("source_binding").put("source_id",source.sourceId()).put("source_version",row.inputSourceVersion())
            .put("rights_revision",row.inputRightsRevision()).put("case_id",row.caseId()).put("model_config_revision",row.configRevision());
        schema.validate("Receipt",receipt);return receipt;
    }
    private String configurationId(SimulationInvocationSources.PreparedFixture prepared){
        var config=prepared.configuration();var row=prepared.registration();
        same(row.projectId(),nullable(config,"project_id"));same(row.configRevision(),config.required("model_config_revision").textValue());
        String id=config.required("config_id").textValue();WireValues.uuid(id);return id;
    }
    /** Consumer read: never registers an artifact, emits events, or requires producer permission. */
    public void requireRegisteredInput(EnterpriseAccess.Admission a,SimulationInvocationSources.PreparedFixture prepared,
            PermissionProtocol.Basis inputRef)throws Exception {
        Objects.requireNonNull(inputRef);WireValues.uuid(inputRef.version_id());WireValues.hash(inputRef.content_sha256());
        sources.revalidateForContext(a,prepared);
        var row=prepared.registration();var source=source(a,row);
        var link=one(mapper.link(values(a,row.projectId(),row.configRevision(),row.caseId())));
        same(link.artifactId(),inputRef.version_id());same(prepared.inputOriginal().sha256(),inputRef.content_sha256());
        resources.require(a,new PermissionProtocol.Scope(row.tenantId(),row.projectId(),"ARTIFACT",link.artifactId()),"artifact.read",PURPOSE);
        resources.require(a,new PermissionProtocol.Scope(row.tenantId(),source.ownerProjectId(),"SOURCE",source.sourceId()),"source.read",PURPOSE);
        verify(a,prepared,source,closure(a,row),link);
        sources.revalidateForContext(a,prepared);
    }
    private void verifyOperation(EnterpriseAccess.Admission a,Operation operation,String key,String hash)throws Exception {
        operationIdentity(a,operation,key);
        if(!hash.equals(operation.requestHash()))throw new TenantAccessException(ErrorCode.IDEMPOTENCY_CONFLICT);
    }
    private void operationIdentity(EnterpriseAccess.Admission a,Operation operation,String key)throws Exception {
        same(a.context().tenant_id(),operation.tenantId());same(a.actorId(),operation.actorId());same(key,operation.key());
        same(schema.manifest(),operation.schemaHash());same("1.0.0",operation.schemaVersion());same(a.actorId(),operation.createdBy());
        schema.receipt(operation.receipt());schema.storedRequest(operation.canonicalRequest(),operation.bodyHash());
    }
    private SourceReadProtocol.SourceRow source(EnterpriseAccess.Admission a,SimulationInvocationSources.Registration row) {
        var source=one(sourceRows.source(row.inputSourceVersion()));same(a.context().tenant_id(),row.tenantId());same(row.tenantId(),source.tenantId());
        same(row.inputRightsRevision(),source.rightsRevision());same(row.inputSourceVersion(),source.sourceVersion());return source;
    }
    private SourceReadProtocol.SourceRow registerGrant(EnterpriseAccess.Admission a,SimulationInvocationSources.Registration row)throws Exception {
        var source=source(a,row);
        resources.require(a,new PermissionProtocol.Scope(a.context().tenant_id(),source.ownerProjectId(),"SOURCE",source.sourceId()),"artifact.register",PURPOSE);
        return source;
    }
    private Closure closure(EnterpriseAccess.Admission a,SimulationInvocationSources.Registration row)throws Exception {
        var roots=new TreeSet<>(List.of(row.caseSourceVersion(),row.inputSourceVersion()));var graphs=new ArrayList<DependencyLineage.VerifiedLineage>();
        var all=new TreeMap<String,ArtifactRegistryProtocol.VersionRow>();var basis=new ArrayList<PermissionProtocol.Basis>();
        for(String root:roots){var graph=lineage.inspect(root,new DependencyLineage.Limits(256,1024));graphs.add(graph);
            for(var entry:graph.versions().entrySet()){var old=all.putIfAbsent(entry.getKey(),entry.getValue());if(old!=null&&!old.equals(entry.getValue()))mismatch();}
            basis.add(new PermissionProtocol.Basis(root,graph.versions().get(root).contentSha256()));}
        if(all.size()>256)throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);
        references.require(a,row.projectId(),basis,PURPOSE);
        var actual=new TreeMap<String,SourceReadProtocol.SourceRow>();String classification=row.classification();
        for(String id:all.keySet()){var found=sourceRows.source(id);if(found.size()>1)mismatch();if(!found.isEmpty()){
            actual.put(id,found.get(0));classification=SimulationContextDependencies.maximum(classification,found.get(0).classification());}}
        var members=new ArrayList<ArtifactRegistryProtocol.MemberRow>();
        for(String id:all.keySet()){
            var reachable=new TreeSet<String>();for(var graph:graphs)if(graph.versions().containsKey(id))reachable.addAll(graph.reachableFrom(id));
            String source=reachable.stream().filter(actual::containsKey).findFirst().orElseThrow(()->new TenantAccessException(ErrorCode.CONTENT_MISMATCH));
            members.add(new ArtifactRegistryProtocol.MemberRow(id,actual.get(source).rightsRevision()));
        }
        var requirements=new ArrayList<SourceReadProtocol.ReadRequirement>();
        for(var source:actual.values())requirements.add(new SourceReadProtocol.ReadRequirement(source.sourceVersion(),source.rightsRevision(),row.projectId(),null,null,null,
            SourceReadProtocol.Use.REVIEW,PURPOSE,deployment.environment(),deployment.region(),null));
        var artifactRequirements=new LinkedHashMap<String,ArtifactRegistryProtocol.ReadRequirement>();
        for(var version:all.values())if("ARTIFACT".equals(version.entityKind())){
            classification=SimulationContextDependencies.maximum(classification,one(artifacts.artifact(version.id())).classification());
            var binding=one(artifacts.binding(version.id()));
            var profile=references.profile(binding.schemaRef(),PURPOSE,binding.resourceKind());same(profile.resourceKind(),binding.resourceKind());
            artifactRequirements.put(version.id(),new ArtifactRegistryProtocol.ReadRequirement(
                new PermissionProtocol.Scope(a.context().tenant_id(),binding.projectId(),binding.resourceKind(),binding.resourceId()),
                profile.action(),PURPOSE,profile.schemaRef(),profile.schemaDocumentSha256(),profile.artifactSchemaManifest(),LIMITS));
        }
        return new Closure(List.copyOf(members),List.copyOf(requirements),classification,List.copyOf(roots),Map.copyOf(artifactRequirements));
    }
    private void verify(EnterpriseAccess.Admission a,SimulationInvocationSources.PreparedFixture prepared,SourceReadProtocol.SourceRow source,Closure closure,Link link)throws Exception {
        var row=prepared.registration();same(new Link(row.tenantId(),row.projectId(),row.caseId(),row.configRevision(),row.inputSourceVersion(),row.inputRightsRevision(),link.artifactId()),link);
        if(link.artifactId().equals(source.sourceVersion()))mismatch();
        var artifact=one(artifacts.artifact(link.artifactId()));var binding=one(artifacts.binding(link.artifactId()));var version=one(artifacts.version(link.artifactId()));
        same(row.tenantId(),artifact.tenantId());same(row.projectId(),artifact.projectId());same(link.artifactId(),artifact.artifactId());
        same("SIMULATION_EXACT_INPUT",artifact.artifactKind());same("1.0.0",artifact.schemaVersion());same(catalogue.schemaDigest("simulation-execution"),artifact.schemaManifest());
        same(prepared.inputOriginal().reference(),StoredObjectReferences.decode(artifact.storedObject(),artifact.reference()));same(artifact.sha256(),artifact.contentSha256());same(closure.classification(),artifact.classification());
        same("ARTIFACT",version.entityKind());same(link.artifactId(),version.id());same(artifact.sha256(),version.contentSha256());same(artifact.projectId(),version.projectId());
        same(row.tenantId(),binding.tenantId());same(row.projectId(),binding.projectId());same(link.artifactId(),binding.artifactId());
        if("CONFIGURATION".equals(binding.resourceKind())){
            same(configurationId(prepared),binding.resourceId());
            resources.require(a,new PermissionProtocol.Scope(row.tenantId(),row.projectId(),"CONFIGURATION",binding.resourceId()),"configuration.read",PURPOSE);
        }else if("SOURCE".equals(binding.resourceKind())){
            // Legacy same-scope registrations only; never erase the fixture's project scope.
            same(row.projectId(),source.ownerProjectId());same(source.sourceId(),binding.resourceId());
        }else mismatch();
        same(catalogue.schemaRef("simulation-execution","ExactInput"),binding.schemaRef());
        same(catalogue.schemaDigest("simulation-execution"),binding.schemaDocumentSha256());same(artifact.accessSetId(),binding.accessSetId());
        var manifest=one(artifacts.manifest(artifact.accessSetId()));same(row.tenantId(),manifest.tenantId());same(row.projectId(),manifest.projectId());
        same(binding.dependencyManifestSha256(),manifest.manifestSha256());var verified=manifests.verify(manifest,artifacts.members(artifact.accessSetId()));
        same(closure.members(),verified.members());lineage.require(link.artifactId(),closure.members(),LIMITS);
        dependencies.require(a,artifact.accessSetId(),new DependencyAccessProtocol.ReadRequirement(row.projectId(),PURPOSE,LIMITS),closure.requirements(),
            new VersionResourceProtocol.ReadContext(SourceReadProtocol.Use.REVIEW,null),closure.artifactRequirements());
    }
    private static Map<String,Object> values(EnterpriseAccess.Admission a,String project,String config,String caseId){var result=new HashMap<String,Object>();
        result.put("userId",a.actorId());result.put("projectId",project);result.put("configRevision",config);result.put("caseId",caseId);return result;}
    private static void context(EnterpriseAccess.Admission a,String revision){if(!revision.equals(a.context().context_revision()))throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);}
    private static String nullable(JsonNode node,String key){return node.required(key).isNull()?null:node.required(key).textValue();}
    private static String hash(JsonNode node)throws Exception{return WireValues.sha256(ContentReadAuthorization.canonicalBytes(node));}
    private static <T>T one(List<T> rows){if(rows.size()!=1)mismatch();return rows.get(0);}
    private static void count(int result){if(result!=1)mismatch();}
    private static void same(Object expected,Object actual){if(!Objects.equals(expected,actual))mismatch();}
    private static void mismatch(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
