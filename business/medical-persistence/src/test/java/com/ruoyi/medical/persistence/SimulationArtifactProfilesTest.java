package com.ruoyi.medical.persistence;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import java.util.List;
import org.junit.jupiter.api.Test;

class SimulationArtifactProfilesTest {
    @Test void exactInputHasOnlyTwoExplicitOwnersAndConfigurationIsTheNewDefault()throws Exception {
        var current=SimulationInputArtifactProfile.profile();assertEquals("CONFIGURATION",current.resourceKind());
        assertEquals("configuration.read",current.action());
        assertEquals("SOURCE",SimulationArtifactProfiles.known(current.schemaRef(),"SOURCE").orElseThrow().resourceKind());
        assertEquals(current,SimulationArtifactProfiles.known(current.schemaRef(),"CONFIGURATION").orElseThrow());
        assertThrows(TenantAccessException.class,()->SimulationArtifactProfiles.known(current.schemaRef(),"TASK"));
    }
    @Test void preparationProfilesAreSourceOwnedAndDoNotRecognizeArbitrarySchemas()throws Exception {
        for(String kind:List.of("EXPECTED_ITEMS","SAMPLE_MANIFEST","AUTHORIZED_USE_EVIDENCE")) {
            var profile=SimulationEvaluationPreparationProfile.profile(kind);
            assertEquals(profile,SimulationArtifactProfiles.known(profile.schemaRef(),"SOURCE").orElseThrow());
            assertThrows(TenantAccessException.class,()->SimulationArtifactProfiles.known(profile.schemaRef(),"CONFIGURATION"));
        }
        assertTrue(SimulationArtifactProfiles.known("urn:unregistered:schema","SOURCE").isEmpty());
    }
    @Test void actualSettledResultSchemasUseEvaluationReadProfileWithoutChangingTheirManifest()throws Exception {
        var catalogue=SchemaCatalogue.fromClasspath();
        for(String document:List.of("model-stream","local-simulation-provenance")) {
            String ref=catalogue.schemaRef(document,document.equals("model-stream")?"ModelResult":null);
            var profile=SimulationArtifactProfiles.known(ref,"EVALUATION").orElseThrow();
            assertEquals("evaluation.read",profile.action());assertEquals(catalogue.schemaDigest(document),profile.artifactSchemaManifest());
            assertThrows(TenantAccessException.class,()->SimulationArtifactProfiles.known(ref,"SOURCE"));
        }
    }
    ArtifactRegistryProtocol.ReadRequirement requirement(String purpose,String kind,String project,String action,String ref,String hash)throws Exception {
        return new ArtifactRegistryProtocol.ReadRequirement(new PermissionProtocol.Scope(EvaluationFixture.id(2),project,kind,EvaluationFixture.id(4)),
            action,purpose,ref,hash,hash,new DependencyLineage.Limits(512,2048));
    }
    @Test void theOnlyLocalSchemaExceptionPinsItsActualKindPurposeOwnerAndDigest()throws Exception {
        var artifact=mock(ArtifactRegistryProtocol.ArtifactRow.class);
        when(artifact.artifactKind()).thenReturn("SIM_AUTHORIZED_USE_EVIDENCE");
        String ref=SimulationAuthorizedUseEvidenceSchema.REF,hash=SimulationAuthorizedUseEvidenceSchema.schemaDocumentSha256();
        var valid=requirement("SIMULATION_EVALUATION","SOURCE",null,"source.read",ref,hash);
        assertTrue(ArtifactRegistry.requireLocalSimulationSchema(artifact,valid));
        for(var invalid:List.of(requirement("EVALUATION_RESULTS","SOURCE",null,"source.read",ref,hash),
                requirement("SIMULATION_EVALUATION","CONFIGURATION",null,"configuration.read",ref,hash),
                requirement("SIMULATION_EVALUATION","SOURCE",EvaluationFixture.id(1),"source.read",ref,hash),
                requirement("SIMULATION_EVALUATION","SOURCE",null,"artifact.register",ref,hash),
                requirement("SIMULATION_EVALUATION","SOURCE",null,"source.read",ref,"a".repeat(64))))
            assertThrows(TenantAccessException.class,()->ArtifactRegistry.requireLocalSimulationSchema(artifact,invalid));
        when(artifact.artifactKind()).thenReturn("AUTHORIZED_USE_EVIDENCE");
        assertThrows(TenantAccessException.class,()->ArtifactRegistry.requireLocalSimulationSchema(artifact,valid));
    }
    @Test void noOtherLocalRefCanUseTheExplicitException()throws Exception {
        var artifact=mock(ArtifactRegistryProtocol.ArtifactRow.class);
        String ref="urn:medical:simulation-authorized-use-evidence:1.0.0#/$defs/Specification";
        assertFalse(ArtifactRegistry.requireLocalSimulationSchema(artifact,requirement("SIMULATION_EVALUATION","SOURCE",null,"source.read",ref,"a".repeat(64))));
        assertThrows(RuntimeException.class,()->SchemaCatalogue.fromClasspath().requirePublished(ref,"a".repeat(64)));
    }
    @Test void simulationPreparationBindingsCannotBeUpdatedOrDeletedThroughTheProtectedMapper() {
        try(var ignored=TenantScope.open(new TenantScope.Identity(EvaluationFixture.id(2),EvaluationFixture.id(5),java.math.BigInteger.ONE,java.math.BigInteger.ONE,java.math.BigInteger.ONE))) {
            var interceptor=new ProtectedTenantInterceptor();
            for(String table:List.of("simulation_fixture_input_artifact","simulation_input_artifact_operation","simulation_rule_binding","simulation_rule_approval_binding","simulation_evaluation_preparation")) {
                assertThrows(TenantAccessException.class,()->interceptor.parserSingle("UPDATE "+table+" SET classification='PUBLIC'",null));
                assertThrows(TenantAccessException.class,()->interceptor.parserSingle("DELETE FROM "+table,null));
            }
        }
    }
}
