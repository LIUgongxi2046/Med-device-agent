package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import org.jgrapht.graph.DirectedPseudograph;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Real schema, exact graph and stored metadata checks. No execution grants or approved rows are fabricated. */
class SimulationEvaluationCandidateAuthorityTest {
    private final PermissionSchema json=new PermissionSchema();
    SimulationEvaluationCandidateAuthorityTest()throws Exception { }
    private static String id(int n){return EvaluationFixture.id(n);}
    private static final String HASH="a".repeat(64);
    private DependencyLineage.VerifiedLineage graph() {
        var graph=new DirectedPseudograph<String,SourceReadProtocol.ParentRow>(null,null,false);
        var versions=new LinkedHashMap<String,ArtifactRegistryProtocol.VersionRow>();
        var kinds=List.of("AGENT_VERSION","CAPABILITY_VERSION","MODEL_CONFIG","CAPABILITY_VERSION","SOURCE_VERSION");
        for(int i=0;i<kinds.size();i++){graph.addVertex(id(i+1));versions.put(id(i+1),new ArtifactRegistryProtocol.VersionRow(id(i+1),id(90),kinds.get(i),HASH));}
        for(int i=2;i<=5;i++)graph.addEdge(id(1),id(i),new SourceReadProtocol.ParentRow(id(1),id(i),"CONFIGURATION_DEPENDENCY"));
        return new DependencyLineage.VerifiedLineage(id(1),graph,versions);
    }
    private ObjectNode manifest(){
        var manifest=json.object().put("schema_version","1.0.0").put("manifest_kind","SIMULATION_CANDIDATE_DEPENDENCIES");
        var dependencies=manifest.putArray("dependencies");
        for(int i=2;i<=5;i++)dependencies.addObject().put("version_id",id(i)).put("content_sha256",HASH);
        return manifest;
    }
    private EvaluationProtocol.Candidate candidate(ObjectNode manifest){return new EvaluationProtocol.Candidate(id(1),HASH,id(8),id(9),manifest,json.object(),"1.0.0",HASH);}
    private List<SimulationEvaluationCandidateAuthority.ConfigurationState> states(){return List.of(
        new SimulationEvaluationCandidateAuthority.ConfigurationState(id(1),"AGENT","DRAFT","DRAFT",null),
        new SimulationEvaluationCandidateAuthority.ConfigurationState(id(2),"ROLE","EVALUATING","DRAFT",null),
        new SimulationEvaluationCandidateAuthority.ConfigurationState(id(3),"MODEL","DRAFT","DRAFT","SIMULATION"),
        new SimulationEvaluationCandidateAuthority.ConfigurationState(id(4),"PROMPT","VALIDATING","DRAFT",null));}
    @Test void acceptsExplicitCompleteGraphBeforePublicationAndRequiresActualRoleModelPrompt(){
        assertDoesNotThrow(()->SimulationEvaluationCandidateAuthority.requireFrozenGraph(candidate(manifest()),graph()));
        assertDoesNotThrow(()->SimulationEvaluationCandidateAuthority.requireConfigurationGraph(graph(),states(),id(2),id(3),id(4)));
        assertThrows(TenantAccessException.class,()->SimulationEvaluationCandidateAuthority.requireConfigurationGraph(graph(),states(),id(4),id(3),id(2)));
        assertThrows(TenantAccessException.class,()->SimulationEvaluationCandidateAuthority.requireConfigurationGraph(graph(),states(),id(99),id(3),id(4)));
    }
    @Test void refusesLegacyManifestOmittedAddedChangedAndDuplicateDependencies(){
        assertThrows(TenantAccessException.class,()->SimulationEvaluationCandidateAuthority.requireFrozenGraph(candidate(json.object()),graph()));
        var omitted=manifest();omitted.withArray("dependencies").remove(3);
        assertThrows(TenantAccessException.class,()->SimulationEvaluationCandidateAuthority.requireFrozenGraph(candidate(omitted),graph()));
        var added=manifest();added.withArray("dependencies").addObject().put("version_id",id(99)).put("content_sha256",HASH);
        assertThrows(TenantAccessException.class,()->SimulationEvaluationCandidateAuthority.requireFrozenGraph(candidate(added),graph()));
        var changed=manifest();((ObjectNode)changed.withArray("dependencies").get(0)).put("content_sha256","b".repeat(64));
        assertThrows(TenantAccessException.class,()->SimulationEvaluationCandidateAuthority.requireFrozenGraph(candidate(changed),graph()));
        var duplicate=manifest();duplicate.withArray("dependencies").add(duplicate.withArray("dependencies").get(0).deepCopy());
        assertThrows(TenantAccessException.class,()->SimulationEvaluationCandidateAuthority.requireFrozenGraph(candidate(duplicate),graph()));
    }
    @Test void refusesDisabledHeadsMissingRowsExternalModelsAndDuplicateOwnerRows(){
        for(int i=0;i<states().size();i++){
            var rows=new ArrayList<>(states());var old=rows.get(i);
            rows.set(i,new SimulationEvaluationCandidateAuthority.ConfigurationState(old.versionId(),old.kind(),old.status(),"DISABLED",old.executionMode()));
            assertThrows(TenantAccessException.class,()->SimulationEvaluationCandidateAuthority.requireConfigurationGraph(graph(),rows,id(2),id(3),id(4)));
            rows.set(i,new SimulationEvaluationCandidateAuthority.ConfigurationState(old.versionId(),old.kind(),"DISABLED",old.headStatus(),old.executionMode()));
            assertThrows(TenantAccessException.class,()->SimulationEvaluationCandidateAuthority.requireConfigurationGraph(graph(),rows,id(2),id(3),id(4)));
        }
        var missing=new ArrayList<>(states());missing.remove(3);
        assertThrows(TenantAccessException.class,()->SimulationEvaluationCandidateAuthority.requireConfigurationGraph(graph(),missing,id(2),id(3),id(4)));
        var duplicate=new ArrayList<>(states());duplicate.add(states().get(0));
        assertThrows(TenantAccessException.class,()->SimulationEvaluationCandidateAuthority.requireConfigurationGraph(graph(),duplicate,id(2),id(3),id(4)));
        var external=new ArrayList<>(states());external.set(2,new SimulationEvaluationCandidateAuthority.ConfigurationState(id(3),"MODEL","DRAFT","DRAFT","EXTERNAL"));
        assertThrows(TenantAccessException.class,()->SimulationEvaluationCandidateAuthority.requireConfigurationGraph(graph(),external,id(2),id(3),id(4)));
    }
    @Test void actualSchemaValidatesIndependentlyVersionedManifestAndRejectsUnknownFields()throws Exception {
        var schemas=com.ruoyi.medical.contracts.SchemaCatalogue.fromClasspath();
        assertDoesNotThrow(()->schemas.validate("simulation-execution",manifest(),"SimulationCandidateDependencyManifest"));
        var unknown=manifest().put("production_eligible",true);
        assertThrows(com.ruoyi.medical.contracts.SchemaRejection.class,()->schemas.validate("simulation-execution",unknown,"SimulationCandidateDependencyManifest"));
        assertThrows(com.ruoyi.medical.contracts.SchemaRejection.class,()->schemas.validate("simulation-execution",json.object(),"SimulationCandidateDependencyManifest"));
    }
    @Test void frozenCandidateMustMatchActualAgentMetadataWithoutRequiringPublished()throws Exception {
        var schema=new EvaluationSchema(json);
        var authority=new SimulationEvaluationCandidateAuthority(null,schema,null,null,null,null);
        var frozen=candidate(manifest());
        var row=new EvaluationProtocol.AgentRow(id(80),id(90),id(1),HASH,HASH,id(8),id(9),json.canonicalObject(manifest()),"{}","DRAFT","1.0.0",HASH);
        assertDoesNotThrow(()->authority.requireSnapshot(id(80),id(90),frozen,row));
        var changed=new EvaluationProtocol.AgentRow(row.tenantId(),row.projectId(),row.versionId(),row.contentHash(),row.registryHash(),id(77),row.createdBy(),row.dependencyManifest(),row.limits(),row.status(),row.schemaVersion(),row.manifest());
        assertThrows(TenantAccessException.class,()->authority.requireSnapshot(id(80),id(90),frozen,changed));
        assertThrows(TenantAccessException.class,()->authority.requireSnapshot(id(81),id(90),frozen,row));
        assertThrows(TenantAccessException.class,()->authority.requireSnapshot(id(80),id(91),frozen,row));
    }
    @Test void selectedCandidateMustBelongToActualHashedDefinitionAndClosedBatchCannotExecute()throws Exception {
        var schema=new EvaluationSchema(json);
        var authority=new SimulationEvaluationCandidateAuthority(null,schema,null,null,null,null);
        var definition=EvaluationFixture.definition(schema,List.of(EvaluationFixture.sample(60,"REAL","ACCEPTANCE")),List.of("EVAL-01"),1);
        String hash=schema.hash(definition);
        var row=new EvaluationProtocol.Stored(id(2),id(1),id(3),json.canonicalObject(definition),hash,hash,"{}","RUNNING",java.math.BigInteger.ONE,
                null,null,"1.0.0",schema.manifestSha256(),definition.created_by(),PermissionAuthority.time(definition.created_at()),"INTERNAL");
        var proof=json.object().put("evaluation_id",id(3)).put("agent_version_id",id(20)).put("evaluation_definition_sha256",hash);
        assertEquals(id(20),authority.requireDefinition(id(2),id(1),proof,row).version_id());
        var outsider=proof.deepCopy().put("agent_version_id",id(99));
        assertThrows(TenantAccessException.class,()->authority.requireDefinition(id(2),id(1),outsider,row));
        var changedHash=proof.deepCopy().put("evaluation_definition_sha256","f".repeat(64));
        assertThrows(TenantAccessException.class,()->authority.requireDefinition(id(2),id(1),changedHash,row));
        var otherBatch=proof.deepCopy().put("evaluation_id",id(99));
        assertThrows(TenantAccessException.class,()->authority.requireDefinition(id(2),id(1),otherBatch,row));
        var closed=new EvaluationProtocol.Stored(row.tenantId(),row.projectId(),row.evaluationId(),row.definition(),row.contentHash(),row.registryHash(),row.projection(),"COMPLETED",row.revision(),
                null,null,row.schemaVersion(),row.manifest(),row.createdBy(),row.createdAt(),row.classification());
        assertThrows(TenantAccessException.class,()->authority.requireDefinition(id(2),id(1),proof,closed));
    }

}
