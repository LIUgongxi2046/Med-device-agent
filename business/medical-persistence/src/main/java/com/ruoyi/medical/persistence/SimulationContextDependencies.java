package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.WireValues;
import com.ruoyi.medical.persistence.mapper.SimulationFixtureMapper;
import java.util.*;
import org.springframework.stereotype.Component;

/** Simulation-only configuration lineage, separate from source-license dependency memberships. */
@Component
public final class SimulationContextDependencies {
    private final SimulationFixtureMapper mapper;
    private final DependencyLineage lineage;
    private final EvaluationReferences references;
    private final PermissionSchema json;
    private final String schemaHash;
    private final com.networknt.schema.JsonSchema validator;
    private final com.ruoyi.medical.persistence.mapper.SourceReadMapper sources;
    private final RetrievalPolicy deployment;
    public SimulationContextDependencies(SimulationFixtureMapper mapper,DependencyLineage lineage,EvaluationReferences references,PermissionSchema json,com.ruoyi.medical.persistence.mapper.SourceReadMapper sources,RetrievalPolicy deployment)throws Exception {
        this.sources=sources;this.deployment=deployment;this.mapper=mapper;this.lineage=lineage;this.references=references;this.json=json;
        byte[] schema;
        try(var in=Objects.requireNonNull(getClass().getResourceAsStream("/simulation-context-dependencies.schema.json"))){schema=in.readAllBytes();}
        schemaHash=WireValues.sha256(schema);
        validator=com.networknt.schema.JsonSchemaFactory.getInstance(com.networknt.schema.SpecVersion.VersionFlag.V202012)
            .getSchema(new JsonMapper().readTree(schema),com.networknt.schema.SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());
        validator.initializeValidators();
    }
    public record Classification(String versionId,String classification) { }
    public record Stored(String tenantId,String projectId,String contextId,String candidateId,String promptRevision,
            String manifestJson,String contentHash,String schemaVersion,String schemaManifest) { }
    JsonNode current(EnterpriseAccess.Admission admission,String project,String context,String candidate,String prompt)throws Exception {
        var graph=lineage.inspect(candidate,new DependencyLineage.Limits(256,1024));
        var root=graph.versions().get(candidate);var promptVersion=graph.versions().get(prompt);
        if(root==null||!"AGENT_VERSION".equals(root.entityKind())||promptVersion==null||!"CAPABILITY_VERSION".equals(promptVersion.entityKind()))mismatch();
        references.require(admission,project,List.of(new PermissionProtocol.Basis(candidate,root.contentSha256())),"SIMULATION_EVALUATION");
        var node=json.object();node.put("schema_version","1.0.0");node.put("tenant_id",admission.context().tenant_id());node.set("project_id",json.tree(project));
        node.put("classification",classification(admission,graph));
        node.put("context_manifest_id",context);node.put("candidate_id",candidate);node.put("prompt_revision",prompt);
        var versions=node.putArray("versions");
        graph.versions().keySet().stream().sorted().forEach(id->{var version=graph.versions().get(id);var item=versions.addObject();
            item.put("id",id);item.set("project_id",json.tree(version.projectId()));item.put("entity_kind",version.entityKind());item.put("content_sha256",version.contentSha256());});
        var relations=node.putArray("relations");
        graph.graph().edgeSet().stream().sorted(Comparator.comparing(SourceReadProtocol.ParentRow::consumerVersionId)
            .thenComparing(SourceReadProtocol.ParentRow::upstreamVersionId).thenComparing(SourceReadProtocol.ParentRow::relationKind)).forEach(edge->{
                var item=relations.addObject();item.put("consumer_version_id",edge.consumerVersionId());item.put("upstream_version_id",edge.upstreamVersionId());item.put("relation_kind",edge.relationKind());});
        if(!validator.validate(node).isEmpty())mismatch();return node;
    }
    void persist(EnterpriseAccess.Admission admission,SimulationInvocationSources.Context context,String candidate)throws Exception {
        JsonNode manifest=current(admission,context.projectId(),context.contextId(),candidate,context.promptRevision());
        var values=new HashMap<String,Object>();values.put("userId",context.createdBy());values.put("projectId",context.projectId());
        values.put("contextId",context.contextId());values.put("candidateId",candidate);values.put("promptRevision",context.promptRevision());
        values.put("graphJson",json.canonical(manifest));values.put("graphHash",hash(manifest));values.put("graphSchema",schemaHash);
        values.put("now",context.createdAt());values.put("classification",context.classification());
        if(mapper.insertSimulationContextGraph(values)!=1)mismatch();require(admission,context,candidate);
    }
    void require(EnterpriseAccess.Admission admission,SimulationInvocationSources.Context context,String candidate)throws Exception {
        var values=new HashMap<String,Object>();values.put("userId",admission.actorId());values.put("contextId",context.contextId());
        var rows=mapper.contextGraph(values);if(rows.size()!=1)mismatch();var stored=rows.get(0);
        if(!Objects.equals(stored.tenantId(),context.tenantId())||!Objects.equals(stored.projectId(),context.projectId())
                ||!Objects.equals(stored.contextId(),context.contextId())||!Objects.equals(stored.candidateId(),candidate)
                ||!Objects.equals(stored.promptRevision(),context.promptRevision())||!"1.0.0".equals(stored.schemaVersion())||!schemaHash.equals(stored.schemaManifest()))mismatch();
        var parsed=JsonMapper.builder().enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build().readTree(stored.manifestJson());
        var current=current(admission,context.projectId(),context.contextId(),candidate,context.promptRevision());
        if(!current.equals(parsed)||!hash(current).equals(stored.contentHash()))mismatch();
        if(!maximum(context.classification(),current.required("classification").textValue()).equals(context.classification()))mismatch();
        var candidateSources=new TreeMap<String,String>();
        for(var version:current.required("versions")){
            String id=version.required("id").textValue();var sourceRows=sources.source(id);
            if(sourceRows.size()>1)mismatch();if(!sourceRows.isEmpty())candidateSources.put(id,sourceRows.get(0).rightsRevision());
        }
        if(!candidateSources.isEmpty()){
            var covered=new TreeMap<String,String>();for(var source:sourceRequirements(context.accessSetId(),context.projectId()))covered.put(source.sourceVersion(),source.rightsRevision());
            for(var source:candidateSources.entrySet())if(!Objects.equals(source.getValue(),covered.get(source.getKey())))mismatch();
        }
    }
    private String classification(EnterpriseAccess.Admission admission,DependencyLineage.VerifiedLineage graph){
        var expected=new TreeSet<String>();
        for(var version:graph.versions().values())if(Set.of("AGENT_VERSION","CAPABILITY_VERSION","MODEL_CONFIG").contains(version.entityKind()))expected.add(version.id());
        var values=new HashMap<String,Object>();values.put("userId",admission.actorId());values.put("versionIds",List.copyOf(expected));
        var actual=new TreeMap<String,String>();String highest="PUBLIC";
        for(var row:mapper.contextConfigurationClassifications(values)){
            if(!expected.contains(row.versionId())||actual.putIfAbsent(row.versionId(),row.classification())!=null)mismatch();
            highest=maximum(highest,row.classification());
        }
        if(!expected.equals(actual.keySet()))mismatch();
        for(String id:graph.versions().keySet()){
            var rows=sources.source(id);if(rows.size()>1)mismatch();if(!rows.isEmpty())highest=maximum(highest,rows.get(0).classification());
        }
        return highest;
    }
    static String maximum(String first,String second){
        var levels=List.of("PUBLIC","INTERNAL","RESTRICTED","PERSONAL_HEALTH");
        if(!levels.contains(first)||!levels.contains(second))mismatch();return levels.get(Math.max(levels.indexOf(first),levels.indexOf(second)));
    }
    List<SourceReadProtocol.ReadRequirement> sourceRequirements(String accessId,String project){
        var graph=lineage.inspect(accessId,new DependencyLineage.Limits(257,1027));
        var requirements=new ArrayList<SourceReadProtocol.ReadRequirement>();
        for(String id:new TreeSet<>(graph.versions().keySet())){
            var rows=sources.source(id);if(rows.size()>1)mismatch();if(rows.isEmpty())continue;var source=rows.get(0);
            requirements.add(new SourceReadProtocol.ReadRequirement(id,source.rightsRevision(),project,null,null,null,
                SourceReadProtocol.Use.REVIEW,"SIMULATION_EVALUATION",deployment.environment(),deployment.region(),null));
        }
        return requirements;
    }
    private static String hash(JsonNode node)throws Exception{return WireValues.sha256(ContentReadAuthorization.canonicalBytes(node));}
    private static void mismatch(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}
