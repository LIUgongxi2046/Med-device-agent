package com.ruoyi.medical.persistence;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class EvaluationDependencyUseTest {
    final PermissionSchema json = new PermissionSchema();
    final EvaluationSchema schema = new EvaluationSchema(json);
    EvaluationDependencyUseTest() throws Exception { }
    EvaluationProtocol.Create request(List<String> evidence, String environment) throws Exception {
        var samples = new java.util.ArrayList<EvaluationProtocol.Sample>();
        for (int i=0;i<evidence.size();i++) samples.add(EvaluationFixture.sample(1000+i,evidence.get(i),"DEVELOPMENT"));
        var base=EvaluationFixture.create(schema,samples,List.of("EVAL-02"),1);
        var node=(com.fasterxml.jackson.databind.node.ObjectNode)json.tree(base);node.put("environment",environment);
        return schema.create(json.canonical(node).getBytes(StandardCharsets.UTF_8));
    }
    @Test void onlyEntirelySimulatedValidatedDefinitionUsesSimulationDependencies() throws Exception {
        assertEquals(EvaluationDependencyUse.SIMULATION,EvaluationDependencyUse.forDefinition(request(List.of("SIMULATION","SIMULATION"),"SIMULATION")));
        assertEquals("SIMULATION_EVALUATION",EvaluationDependencyUse.SIMULATION.purpose());
    }
    @Test void environmentAloneAndMixedSamplesNeverWidenDependencyPurpose() throws Exception {
        assertEquals(EvaluationDependencyUse.RESULTS,EvaluationDependencyUse.forDefinition(request(List.of("REAL"),"SIMULATION")));
        assertEquals(EvaluationDependencyUse.RESULTS,EvaluationDependencyUse.forDefinition(request(List.of("SIMULATION","REAL"),"SIMULATION")));
    }
    @Test void ordinaryEvaluationsKeepPriorPurposeEvenWhenObservingSimulationEvidence() throws Exception {
        assertEquals(EvaluationDependencyUse.RESULTS,EvaluationDependencyUse.forDefinition(request(List.of("SIMULATION"),"PRODUCTION")));
        assertEquals(EvaluationDependencyUse.RESULTS,EvaluationDependencyUse.forDefinition(request(List.of("REAL"),"PRODUCTION")));
        assertEquals("EVALUATION_RESULTS",EvaluationDependencyUse.RESULTS.purpose());
    }
    @Test void changedEvidenceWithoutUpdatingFixedSampleManifestIsRejectedBySchema() throws Exception {
        var good=request(List.of("REAL"),"SIMULATION");var changed=(com.fasterxml.jackson.databind.node.ObjectNode)json.tree(good);
        ((com.fasterxml.jackson.databind.node.ObjectNode)changed.path("samples").get(0)).put("evidence_class","SIMULATION");
        assertThrows(com.ruoyi.medical.contracts.SchemaRejection.class,()->schema.create(json.canonical(changed).getBytes(StandardCharsets.UTF_8)));
    }
    @Test void emptySampleDefinitionCannotSelectExecution() throws Exception {
        assertEquals(EvaluationDependencyUse.RESULTS,EvaluationDependencyUse.forDefinition(request(List.of(),"SIMULATION")));
    }
    EvaluationReferences references(List<EvaluationDependencyPolicy.ArtifactProfile> profiles) {
        return new EvaluationReferences(null,null,null,null,null,new EvaluationDependencyPolicy("SIMULATION","TEST",512,2048,profiles));
    }
    @Test void builtinExactInputProfileOnlyAppliesToExplicitSimulationPurpose() throws Exception {
        var profile=SimulationInputArtifactProfile.profile();var refs=references(List.of());
        assertEquals(profile,refs.profile(profile.schemaRef(),"SIMULATION_EVALUATION"));
        assertThrows(TenantAccessException.class,()->refs.profile(profile.schemaRef(),"EVALUATION_RESULTS"));
        assertThrows(TenantAccessException.class,()->refs.profile("urn:unregistered:artifact","SIMULATION_EVALUATION"));
    }
    @Test void configuredProfileCannotOverrideBuiltinSchemaOwnerOrManifest() throws Exception {
        var profile=SimulationInputArtifactProfile.profile();
        var changed=new EvaluationDependencyPolicy.ArtifactProfile("ARTIFACT","artifact.read",profile.schemaRef(),profile.schemaDocumentSha256(),profile.artifactSchemaManifest());
        assertThrows(TenantAccessException.class,()->references(List.of(changed)).profile(profile.schemaRef(),"SIMULATION_EVALUATION"));
        assertEquals(profile,references(List.of(profile)).profile(profile.schemaRef(),"SIMULATION_EVALUATION"));
    }
}
