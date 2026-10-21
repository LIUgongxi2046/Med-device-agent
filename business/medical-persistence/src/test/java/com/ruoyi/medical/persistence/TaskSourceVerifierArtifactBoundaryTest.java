package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.persistence.mapper.ArtifactRegistryMapper;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TaskSourceVerifierArtifactBoundaryTest extends NodeInputObjectFixture {
    TaskSourceVerifierArtifactBoundaryTest() throws Exception { }

    @Test void dedicatedKindsAndSchemasCannotUseGenericRead() {
        var artifact = mock(ArtifactRegistryProtocol.ArtifactRow.class);
        var binding = mock(ArtifactRegistryProtocol.BindingRow.class);
        for (String kind : List.of("TASK_SOURCE_VERIFIER_INPUT", "TASK_SOURCE_VERIFIER_EXACT_INPUT")) {
            when(artifact.artifactKind()).thenReturn(kind);
            assertThrows(TenantAccessException.class, () -> TaskSourceVerifierArtifactBoundary.requireGenericAllowed(artifact, binding, List.of(), List.of()));
        }
        when(artifact.artifactKind()).thenReturn("RELABELLED");
        for (String schema : List.of(TaskSourceVerifierSchema.INPUT_REF, TaskSourceVerifierInvocationSchema.EXACT_INPUT_REF, TaskSourceVerifierSchema.ENVELOPE_REF)) {
            when(binding.schemaRef()).thenReturn(schema);
            assertThrows(TenantAccessException.class, () -> TaskSourceVerifierArtifactBoundary.requireGenericAllowed(artifact, binding, List.of(), List.of()));
        }
    }

    @Test void immutableIntentOwnersBlockRelabelledAndPreRegistrationObjects() {
        var artifact = mock(ArtifactRegistryProtocol.ArtifactRow.class);
        var binding = mock(ArtifactRegistryProtocol.BindingRow.class);
        when(artifact.artifactKind()).thenReturn("REVIEW_COVERAGE");
        when(binding.schemaRef()).thenReturn("urn:ordinary");
        assertThrows(TenantAccessException.class, () -> TaskSourceVerifierArtifactBoundary.requireGenericAllowed(artifact, binding, List.of("original-input-or-envelope"), List.of()));
        assertThrows(TenantAccessException.class, () -> TaskSourceVerifierArtifactBoundary.requireGenericAllowed(artifact, binding, List.of(), List.of("original-exact")));
        assertThrows(TenantAccessException.class, () -> TaskSourceVerifierArtifactBoundary.requireGenericAllowed(artifact, binding, null, List.of()));
        assertThrows(TenantAccessException.class, () -> TaskSourceVerifierArtifactBoundary.requireGenericAllowed(artifact, binding, List.of(), null));
        assertThrows(TenantAccessException.class, () -> TaskSourceVerifierArtifactBoundary.requireGenericAllowed(null, binding, List.of(), List.of()));
        assertThrows(TenantAccessException.class, () -> TaskSourceVerifierArtifactBoundary.requireGenericAllowed(artifact, null, List.of(), List.of()));
    }

    @Test void publicOutputAndUnrelatedHistoricalEnvelopeAreNotReinterpreted() {
        var artifact = mock(ArtifactRegistryProtocol.ArtifactRow.class);
        var binding = mock(ArtifactRegistryProtocol.BindingRow.class);
        when(binding.schemaRef()).thenReturn("urn:medical:artifacts:1.0.0");
        for (String kind : List.of("REVIEW_FINDINGS", "REVIEW_COVERAGE", "STEP_ENVELOPE")) {
            when(artifact.artifactKind()).thenReturn(kind);
            assertDoesNotThrow(() -> TaskSourceVerifierArtifactBoundary.requireGenericAllowed(artifact, binding, List.of(), List.of()));
        }
    }

    @Test void genericRegistryRejectsImmutableOwnerBeforeArtifactGrantOrContentAccess() throws Exception {
        for (boolean exact : List.of(false, true)) {
            var mapper = mock(ArtifactRegistryMapper.class);
            var authority = mock(ResourceReadAuthority.class);
            var artifact = new ArtifactRegistryProtocol.ArtifactRow(tenant, project, id(10), "REVIEW_COVERAGE", "{}", "key", "version", hash('a'), BigInteger.ONE, "application/json", id(11), hash('a'), "1.0.0", hash('a'), "INTERNAL");
            var binding = new ArtifactRegistryProtocol.BindingRow(tenant, project, id(10), "SOURCE", id(12), BuiltinEvaluationDocumentContextCompiler.OUTPUT, hash('a'), id(11), hash('a'));
            when(mapper.artifact(id(10))).thenReturn(List.of(artifact));
            when(mapper.binding(id(10))).thenReturn(List.of(binding));
            if (exact) when(mapper.sourceVerifierExactInputOwners(id(10))).thenReturn(List.of(id(44)));
            else when(mapper.sourceVerifierInputOwners(id(10))).thenReturn(List.of(id(44)));
            var service = new ArtifactRegistry(mapper, authority, new DependencyManifestSchema(), mock(DependencyLineage.class));
            var request = new ArtifactRegistryProtocol.ReadRequirement(new PermissionProtocol.Scope(tenant, project, "SOURCE", id(12)), "source.read", "TASK_EXECUTION", BuiltinEvaluationDocumentContextCompiler.OUTPUT, hash('a'), hash('a'), new DependencyLineage.Limits(512, 2048));
            assertEquals(ErrorCode.ACCESS_DENIED, assertThrows(TenantAccessException.class, () -> service.requireRegistration(admission, id(10), request)).code());
            verify(mapper).sourceVerifierInputOwners(id(10));
            verify(mapper).sourceVerifierExactInputOwners(id(10));
            verify(authority, times(1)).require(any(), any(), any(), any());
            verify(mapper, never()).manifest(any());
            verify(mapper, never()).version(any());
        }
    }

    @Test void ownershipSqlUsesImmutableIntentWithTenantAndDoesNotHidePublicOutputs() throws Exception {
        var configuration = new Configuration();
        String resource = "medical-mappers/ArtifactRegistryMapper.xml";
        try (var in = Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream(resource))) {
            new XMLMapperBuilder(in, configuration, resource, configuration.getSqlFragments()).parse();
        }
        for (String query : List.of("sourceVerifierInputOwners", "sourceVerifierExactInputOwners")) {
            String name = ArtifactRegistryMapper.class.getName() + "." + query;
            var statement = configuration.getMappedStatement(name);
            var bound = statement.getBoundSql(Map.of("id", id(10)));
            assertEquals(List.of("id"), bound.getParameterMappings().stream().map(p -> p.getProperty()).toList());
            assertFalse(statement.isUseCache());
            assertTrue(statement.isFlushCacheRequired());
            tx(() -> {
                TenantScope.requireSqlAccess(name);
                String sql = new ProtectedTenantInterceptor().parserSingle(bound.getSql(), null);
                assertTrue(sql.contains("i.tenant_id = '" + tenant + "'"), sql);
                assertTrue(sql.contains("artifact_id = ?"), sql);
                assertTrue(sql.contains("LIMIT 2"), sql);
                assertFalse(sql.contains("artifact_content_binding"), sql);
                assertFalse(sql.contains("JOIN"), sql);
                if (query.equals("sourceVerifierInputOwners")) {
                    assertTrue(sql.contains("task_source_verifier_object_intent"), sql);
                    assertTrue(sql.contains("'TASK_SOURCE_VERIFIER_INPUT'"), sql);
                    assertTrue(sql.contains("'STEP_ENVELOPE'"), sql);
                    assertFalse(sql.contains("REVIEW_FINDINGS"), sql);
                    assertFalse(sql.contains("REVIEW_COVERAGE"), sql);
                } else {
                    assertTrue(sql.contains("task_source_verifier_exact_intent"), sql);
                    assertFalse(sql.contains("kind"), sql);
                }
                return null;
            });
        }
    }
}
