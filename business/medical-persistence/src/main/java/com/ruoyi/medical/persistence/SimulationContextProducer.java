package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import com.ruoyi.medical.contracts.WireValues;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import com.ruoyi.medical.persistence.mapper.SimulationFixtureMapper;
import com.ruoyi.medical.persistence.mapper.SourceReadMapper;
import java.math.BigInteger;
import java.util.*;
import org.springframework.stereotype.Component;

/** Produces immutable simulation context from server-verified originals and complete current lineage. */
@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix="medical.simulation-execution",name="enabled",havingValue="true")
public final class SimulationContextProducer {
    private static final String PURPOSE="SIMULATION_EVALUATION";
    private final SimulationInvocationSources sources;
    private final SimulationFixtureMapper mapper;
    private final SourceReadMapper sourceRows;
    private final PermissionMapper permissions;
    private final PermissionSchema json;
    private final DependencyLineage lineage;
    private final EvaluationReferences references;
    private final DependencyManifestSchema manifests;
    private final DependencyAccessAuthority dependencies;
    private final RetrievalPolicy deployment;
    private final SchemaCatalogue catalogue;
    private final SimulationContextDependencies contextDependencies;
    private final SimulationWorkerAccess worker;

    public SimulationContextProducer(SimulationInvocationSources sources,SimulationFixtureMapper mapper,
            SourceReadMapper sourceRows,PermissionMapper permissions,PermissionSchema json,DependencyLineage lineage,
            EvaluationReferences references,DependencyManifestSchema manifests,DependencyAccessAuthority dependencies,
            RetrievalPolicy deployment,SimulationContextDependencies contextDependencies,SimulationWorkerAccess worker) throws Exception {
        this.sources=sources;this.mapper=mapper;this.sourceRows=sourceRows;this.permissions=permissions;this.json=json;
        this.lineage=lineage;this.references=references;this.manifests=manifests;this.dependencies=dependencies;
        this.worker=worker;this.contextDependencies=contextDependencies;this.deployment=deployment;this.catalogue=SchemaCatalogue.fromClasspath();
    }
    /** Constructor deliberately private: caller JSON never constitutes preparation authority. */
    public static final class Prepared {
        private final SimulationContextProducer owner;
        private final SimulationInvocationSources.PreparedFixture fixture;
        private Prepared(SimulationContextProducer owner,SimulationInvocationSources.PreparedFixture fixture){this.owner=owner;this.fixture=fixture;}
        public SimulationInvocationSources.Registration registration(){return fixture.registration();}
        public JsonNode configuration(){return fixture.configuration();}
        public JsonNode fixture(){return fixture.fixture();}
        public JsonNode exactInput(){return fixture.exactInput();}
        public JsonNode settings(){return SimulationFixtureContent.settings(configuration());}
        public JsonNode sourceBinding(){
            var json=new com.fasterxml.jackson.databind.json.JsonMapper().findAndRegisterModules();var node=json.createObjectNode();
            node.set("registration",json.valueToTree(registration()));node.set("case_original",json.valueToTree(fixture.caseOriginal().reference()));
            node.set("input_original",json.valueToTree(fixture.inputOriginal().reference()));return node;
        }
        public String inputOriginalSha256(){return fixture.inputOriginal().sha256();}
        SimulationInvocationSources.PreparedFixture inputArtifactPreparation(){return fixture;}
    }
    public Prepared prepare(ApplicationSessionIdentity.Verified subject,JsonNode start) throws Exception {
        return new Prepared(this,sources.prepareForContext(subject,start));
    }
    public Prepared prepare(jakarta.servlet.http.HttpServletRequest request,String tenant,String run,JsonNode start) throws Exception {
        return new Prepared(this,sources.prepareForContext(worker,request,tenant,run,start));
    }
    public void require(EnterpriseAccess.Admission admission,Prepared prepared) {
        if(prepared==null||prepared.owner!=this)denied();sources.revalidateForContext(admission,prepared.fixture);
    }
    public void revalidate(EnterpriseAccess.Admission admission,Prepared prepared){require(admission,prepared);}
    public void require(EnterpriseAccess.Admission admission,Prepared prepared,JsonNode proof)throws Exception {
        require(admission,prepared);sources.requirePreparedContext(admission,prepared.fixture,proof);
    }

    public SimulationInvocationSources.Context persist(EnterpriseAccess.Admission admission,Prepared prepared,
            String contextId,String promptRevision,String candidateId) throws Exception {
        require(admission,prepared);WireValues.uuid(contextId);WireValues.uuid(promptRevision);WireValues.uuid(candidateId);
        var registration=prepared.registration();String tenant=admission.context().tenant_id(),project=registration.projectId();
        var roots=new TreeSet<>(List.of(registration.caseSourceVersion(),registration.inputSourceVersion()));
        var candidateGraph=lineage.inspect(candidateId,new DependencyLineage.Limits(256,1024));
        for(String id:candidateGraph.versions().keySet()){
            var rows=sourceRows.source(id);if(rows.size()>1)mismatch();if(!rows.isEmpty())roots.add(id);
        }
        var graphs=new ArrayList<DependencyLineage.VerifiedLineage>();
        var all=new TreeMap<String,ArtifactRegistryProtocol.VersionRow>();
        var basis=new ArrayList<PermissionProtocol.Basis>();
        for(String root:roots){
            var graph=lineage.inspect(root,new DependencyLineage.Limits(256,1024));graphs.add(graph);
            for(var entry:graph.versions().entrySet()){
                var previous=all.putIfAbsent(entry.getKey(),entry.getValue());if(previous!=null&&!previous.equals(entry.getValue()))mismatch();
            }
            basis.add(new PermissionProtocol.Basis(root,graph.versions().get(root).contentSha256()));
        }
        if(all.size()>256)throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);
        var candidateManifest=contextDependencies.current(admission,project,contextId,candidateId,promptRevision);
        references.require(admission,project,basis,PURPOSE);
        var actualSources=new TreeMap<String,SourceReadProtocol.SourceRow>();
        for(String id:all.keySet()){
            var rows=sourceRows.source(id);if(rows.size()>1)mismatch();if(!rows.isEmpty())actualSources.put(id,rows.get(0));
        }
        var members=new ArrayList<ArtifactRegistryProtocol.MemberRow>();
        for(String id:all.keySet()){
            var reachable=new TreeSet<String>();for(var graph:graphs)if(graph.versions().containsKey(id))reachable.addAll(graph.reachableFrom(id));
            String source=reachable.stream().filter(actualSources::containsKey).findFirst().orElseThrow(()->new TenantAccessException(ErrorCode.CONTENT_MISMATCH));
            members.add(new ArtifactRegistryProtocol.MemberRow(id,actualSources.get(source).rightsRevision()));
        }
        if(members.isEmpty())mismatch();
        String accessId=UUID.randomUUID().toString();var now=Objects.requireNonNull(permissions.databaseTime());
        String actor=admission.actorId(),classification=SimulationContextDependencies.maximum(registration.classification(),candidateManifest.required("classification").textValue());
        var classifications=List.of("PUBLIC","INTERNAL","RESTRICTED","PERSONAL_HEALTH");
        for(var source:actualSources.values()){
            if(!classifications.contains(source.classification()))mismatch();
            classification=classifications.get(Math.max(classifications.indexOf(classification),classifications.indexOf(source.classification())));
        }
        var dependency=new ArtifactRegistryProtocol.DependencyManifest("1.0.0",tenant,project,accessId,members);
        String dependencyJson=json.canonical(json.tree(dependency)),dependencyHash=hash(json.tree(dependency));
        var manifestRow=new ArtifactRegistryProtocol.ManifestRow(tenant,project,accessId,dependencyJson,dependencyHash,dependencyHash,
            BigInteger.valueOf(members.size()),"1.0.0",manifests.manifestSha256());
        manifests.verify(manifestRow,members);
        var values=new HashMap<String,Object>();values.put("userId",actor);values.put("projectId",project);values.put("now",now);
        values.put("classification",classification);values.put("versionId",accessId);values.put("kind","DEPENDENCY_ACCESS_SET");values.put("hash",dependencyHash);
        count(mapper.insertContextVersion(values));values.put("accessId",accessId);values.put("dependencyJson",dependencyJson);
        values.put("memberCount",members.size());values.put("dependencySchema",manifests.manifestSha256());
        count(mapper.insertContextAccessSet(values));count(mapper.insertContextDependencyManifest(values));
        for(var member:members){values.put("member",member);count(mapper.insertContextDependencyMember(values));}
        values.put("consumer",accessId);values.put("relationKind","SIMULATION_CONTEXT_INPUT");
        for(String root:roots){values.put("upstream",root);count(mapper.insertContextLineage(values));}
        var requirements=new ArrayList<SourceReadProtocol.ReadRequirement>();
        for(var source:actualSources.values())requirements.add(new SourceReadProtocol.ReadRequirement(source.sourceVersion(),source.rightsRevision(),project,
            null,null,null,SourceReadProtocol.Use.REVIEW,PURPOSE,deployment.environment(),deployment.region(),null));
        dependencies.require(admission,accessId,new DependencyAccessProtocol.ReadRequirement(project,PURPOSE,new DependencyLineage.Limits(257,1027)),
            requirements,new VersionResourceProtocol.ReadContext(SourceReadProtocol.Use.REVIEW,null),Map.of());
        var sourceManifest=json.object().arrayNode();
        sourceManifest.add(source("CASE",registration.caseSourceVersion(),registration.caseRightsRevision(),prepared.fixture.caseOriginal().sha256()));
        sourceManifest.add(source("EXACT_INPUT",registration.inputSourceVersion(),registration.inputRightsRevision(),prepared.fixture.inputOriginal().sha256()));
        var manifest=json.object();manifest.put("schema_version","1.0.0");manifest.put("input_data_kind","SIMULATION");manifest.put("production_eligible",false);
        manifest.put("tenant_id",tenant);manifest.set("project_id",json.tree(project));manifest.put("context_manifest_id",contextId);
        manifest.set("exact_input_ref",json.tree(prepared.fixture.inputOriginal().reference()));manifest.set("source_manifest",sourceManifest);
        manifest.put("prompt_revision",promptRevision);manifest.put("dependency_access_set_id",accessId);manifest.put("created_by",actor);
        manifest.put("created_at",now.toInstant(java.time.ZoneOffset.UTC).toString());manifest.put("classification",classification);
        var validated=catalogue.bind("simulation-execution",manifest,"SimulationContextManifest");String contentHash=hash(manifest);
        var context=new SimulationInvocationSources.Context(tenant,project,contextId,json.canonical(manifest.required("exact_input_ref")),accessId,
            contentHash,contentHash,"SIMULATION",json.canonical(sourceManifest),promptRevision,"1.0.0",validated.schemaSha256(),actor,now,classification);
        values.put("versionId",contextId);values.put("kind","CONTEXT_MANIFEST");values.put("hash",contentHash);count(mapper.insertContextVersion(values));
        values.put("row",context);count(mapper.insertSimulationContext(values));
        values.put("consumer",contextId);values.put("upstream",accessId);count(mapper.insertContextLineage(values));
        values.put("contextId",contextId);var persisted=mapper.context(values);if(persisted.size()!=1)mismatch();
        var saved=persisted.get(0);var parser=new com.fasterxml.jackson.databind.json.JsonMapper();
        var normalized=new SimulationInvocationSources.Context(saved.tenantId(),saved.projectId(),saved.contextId(),
            json.canonical(parser.readTree(saved.exactInputRef())),saved.accessSetId(),saved.contentHash(),saved.registryHash(),saved.executionMode(),
            json.canonical(parser.readTree(saved.sourceManifest())),saved.promptRevision(),saved.schemaVersion(),saved.manifest(),saved.createdBy(),saved.createdAt(),saved.classification());
        if(!context.equals(normalized))mismatch();
        contextDependencies.persist(admission,persisted.get(0),candidateId);
        require(admission,prepared);return persisted.get(0);
    }
    private JsonNode source(String kind,String version,String revision,String digest){var node=json.object();node.put("kind",kind);node.put("source_version",version);
        node.put("rights_revision",revision);node.put("original_sha256",digest);node.put("purpose",PURPOSE);return node;}
    private static String hash(JsonNode node)throws Exception{return WireValues.sha256(ContentReadAuthorization.canonicalBytes(node));}
    private static void count(int count){if(count!=1)mismatch();}
    private static void mismatch(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
    private static void denied(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}
}
