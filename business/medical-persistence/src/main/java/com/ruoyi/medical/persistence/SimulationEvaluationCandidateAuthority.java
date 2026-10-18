package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.WireValues;
import com.ruoyi.medical.persistence.mapper.EvaluationMapper;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Restricted prepublication evaluation membership, never a production release or TASK authority. */
@Component
public final class SimulationEvaluationCandidateAuthority {
    private static final String PURPOSE="SIMULATION_EVALUATION";
    private static final Set<String> ENABLED=Set.of("DRAFT","VALIDATING","EVALUATING","REVIEW_PENDING","PUBLISHED");
    private final EvaluationMapper mapper;
    private final EvaluationSchema schema;
    private final DependencyLineage lineage;
    private final EvaluationDependencyPolicy policy;
    private final EvaluationReferences references;
    private final ResourceReadAuthority resources;
    private final com.ruoyi.medical.contracts.SchemaCatalogue catalogue;

    public SimulationEvaluationCandidateAuthority(EvaluationMapper mapper,EvaluationSchema schema,DependencyLineage lineage,
            EvaluationDependencyPolicy policy,EvaluationReferences references,ResourceReadAuthority resources) throws IOException,java.security.NoSuchAlgorithmException {
        this.mapper=mapper;this.schema=schema;this.lineage=lineage;this.policy=policy;this.references=references;this.resources=resources;
        this.catalogue=com.ruoyi.medical.contracts.SchemaCatalogue.fromClasspath();
    }
    public record ConfigurationState(String versionId,String kind,String status,String headStatus,String executionMode) { }

    void require(EnterpriseAccess.Admission admission,JsonNode proof,String roleVersion,String configRevision)throws IOException {
        Objects.requireNonNull(admission);WireValues.uuid(roleVersion);WireValues.uuid(configRevision);
        String tenant=admission.context().tenant_id(),evaluation=text(proof,"evaluation_id"),candidate=text(proof,"agent_version_id");
        String project=proof.path("project_id").isNull()?null:text(proof,"project_id");
        if(!tenant.equals(text(proof,"tenant_id"))||!configRevision.equals(text(proof,"model_config_revision")))mismatch();
        var values=new HashMap<String,Object>();values.put("userId",admission.actorId());values.put("evaluationId",evaluation);
        var stored=single(mapper.definition(values));
        resources.require(admission,new PermissionProtocol.Scope(tenant,project,"EVALUATION",evaluation),"evaluation.read",PURPOSE);
        var frozen=requireDefinition(tenant,project,proof,stored);
        var requested=new PermissionProtocol.Basis(frozen.version_id(),frozen.content_sha256());
        values.put("candidateId",candidate);var current=single(mapper.candidate(values));
        requireSnapshot(tenant,project,frozen,current);
        var graph=lineage.inspect(candidate,policy.limits());
        catalogue.validate("simulation-execution",frozen.dependency_manifest(),"SimulationCandidateDependencyManifest");
        requireFrozenGraph(frozen,graph);
        values.put("versionIds",graph.versions().keySet().stream().sorted().toList());
        var states=mapper.configurationStates(values);
        values.put("contextId",text(proof,"context_manifest_id"));values.put("projectId",project);
        String prompt=single(mapper.simulationPrompt(values));WireValues.uuid(prompt);
        requireConfigurationGraph(graph,states,roleVersion,configRevision,prompt);
        // This traverses the complete graph again under current owner grants, source rights and artifact policies.
        // No caller boolean, frozen approval, PUBLISHED release, or evaluation result can substitute for it.
        references.require(admission,project,List.of(requested),PURPOSE);
    }

    EvaluationProtocol.Candidate requireDefinition(String tenant,String project,JsonNode proof,EvaluationProtocol.Stored stored)throws IOException {
        String evaluation=text(proof,"evaluation_id"),candidate=text(proof,"agent_version_id");
        var definition=schema.definition(stored.definition());String hash=schema.hash(definition);
        if(!tenant.equals(stored.tenantId())||!tenant.equals(definition.tenant_id())||!evaluation.equals(stored.evaluationId())
                ||!evaluation.equals(definition.evaluation_id())||!Objects.equals(project,stored.projectId())
                ||!Objects.equals(project,definition.request().project_id())||!hash.equals(stored.contentHash())
                ||!hash.equals(stored.registryHash())||!hash.equals(text(proof,"evaluation_definition_sha256"))
                ||!"1.0.0".equals(stored.schemaVersion())||!schema.manifestSha256().equals(stored.manifest())
                ||!definition.created_by().equals(stored.createdBy())
                ||!PermissionAuthority.time(definition.created_at()).equals(stored.createdAt())
                ||stored.revision()==null||stored.revision().signum()<=0)mismatch();
        if(!Set.of("QUEUED","RUNNING").contains(stored.status())||stored.resultJson()!=null||stored.resultHash()!=null)denied();
        var frozen=single(definition.candidate_snapshots().stream().filter(c->candidate.equals(c.version_id())).toList());
        var requested=single(definition.request().candidates().stream().filter(c->candidate.equals(c.version_id())).toList());
        if(!requested.content_sha256().equals(frozen.content_sha256()))mismatch();
        if(definition.candidate_snapshots().size()!=definition.request().candidates().size())mismatch();
        var snapshots=new HashMap<String,String>();
        for(var item:definition.candidate_snapshots())if(snapshots.putIfAbsent(item.version_id(),item.content_sha256())!=null)mismatch();
        for(var item:definition.request().candidates())if(!item.content_sha256().equals(snapshots.get(item.version_id())))mismatch();
        return frozen;
    }

    void requireSnapshot(String tenant,String project,EvaluationProtocol.Candidate frozen,EvaluationProtocol.AgentRow current)throws IOException {
        if(!tenant.equals(current.tenantId())||current.projectId()!=null&&!current.projectId().equals(project)
                ||!frozen.version_id().equals(current.versionId())||!frozen.content_sha256().equals(current.contentHash())
                ||!frozen.content_sha256().equals(current.registryHash()))mismatch();
        if(!ENABLED.contains(current.status()))denied();
        var snapshot=new EvaluationProtocol.Candidate(current.versionId(),current.contentHash(),current.agentId(),current.createdBy(),
                schema.read(current.dependencyManifest()),schema.read(current.limits()),current.schemaVersion(),current.manifest());
        if(!frozen.equals(snapshot))mismatch();
    }

    /** Simulation candidates require an exact, hash-bound transitive manifest; legacy untyped manifests fail closed. */
    static void requireFrozenGraph(EvaluationProtocol.Candidate candidate,DependencyLineage.VerifiedLineage graph) {
        var root=graph.versions().get(candidate.version_id());
        if(root==null||!"AGENT_VERSION".equals(root.entityKind())||!candidate.content_sha256().equals(root.contentSha256()))mismatch();
        if(!candidate.dependency_manifest().isObject()||candidate.dependency_manifest().size()!=3
                ||!"1.0.0".equals(text(candidate.dependency_manifest(),"schema_version"))
                ||!"SIMULATION_CANDIDATE_DEPENDENCIES".equals(text(candidate.dependency_manifest(),"manifest_kind")))mismatch();
        var declared=new HashMap<String,String>();var dependencies=candidate.dependency_manifest().path("dependencies");
        if(!dependencies.isArray())mismatch();
        for(var dependency:dependencies) {
            if(!dependency.isObject()||dependency.size()!=2)mismatch();
            String id=text(dependency,"version_id"),hash=text(dependency,"content_sha256");WireValues.uuid(id);WireValues.hash(hash);
            if(candidate.version_id().equals(id)||declared.putIfAbsent(id,hash)!=null)mismatch();
        }
        var expected=new HashMap<String,String>();graph.versions().forEach((id,version)->{if(!id.equals(candidate.version_id()))expected.put(id,version.contentSha256());});
        if(!declared.equals(expected))mismatch();
    }

    static void requireConfigurationGraph(DependencyLineage.VerifiedLineage graph,List<ConfigurationState> states,
            String role,String config,String prompt) {
        var expected=new java.util.HashSet<String>();
        for(var version:graph.versions().values())if(Set.of("AGENT_VERSION","CAPABILITY_VERSION","MODEL_CONFIG").contains(version.entityKind()))expected.add(version.id());
        var actual=new HashMap<String,ConfigurationState>();
        for(var state:states) {
            if(!expected.contains(state.versionId())||actual.putIfAbsent(state.versionId(),state)!=null)mismatch();
            if(!ENABLED.contains(state.status())||!ENABLED.contains(state.headStatus()))denied();
            String kind=graph.versions().get(state.versionId()).entityKind();
            if("AGENT_VERSION".equals(kind)&&!"AGENT".equals(state.kind())
                    ||"MODEL_CONFIG".equals(kind)&&(!"MODEL".equals(state.kind())||!"SIMULATION".equals(state.executionMode()))
                    ||"CAPABILITY_VERSION".equals(kind)&&!Set.of("ROLE","SKILL","TOOL","PROMPT","SCENARIO").contains(state.kind()))mismatch();
        }
        if(!actual.keySet().equals(expected))mismatch();
        requireKind(actual,role,"ROLE");requireKind(actual,config,"MODEL");requireKind(actual,prompt,"PROMPT");
    }
    private static void requireKind(Map<String,ConfigurationState> states,String id,String kind) {
        if(!states.containsKey(id)||!kind.equals(states.get(id).kind()))mismatch();
    }
    private static String text(JsonNode node,String field) {
        var value=node.get(field);if(value==null||!value.isTextual()||value.textValue().isBlank())mismatch();return value.textValue();
    }
    private static <T>T single(List<T> values){if(values.size()!=1)mismatch();return values.get(0);}
    private static void mismatch(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
    private static void denied(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}
}
