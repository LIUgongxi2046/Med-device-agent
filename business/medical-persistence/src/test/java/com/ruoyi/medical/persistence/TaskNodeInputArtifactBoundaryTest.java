package com.ruoyi.medical.persistence;
import org.junit.jupiter.api.Test;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.persistence.mapper.ArtifactRegistryMapper;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class TaskNodeInputArtifactBoundaryTest extends NodeInputObjectFixture {
 TaskNodeInputArtifactBoundaryTest() throws Exception { }
 ArtifactRegistryProtocol.ArtifactRow artifact(String kind) {
  return new ArtifactRegistryProtocol.ArtifactRow(tenant,project,id(10),kind,"{}","original/key","version",hash('a'),java.math.BigInteger.ONE,"application/json",id(11),hash('a'),"1.0.0",hash('a'),"INTERNAL");
 }
 ArtifactRegistryProtocol.BindingRow binding(String resource,String ref) {
  return new ArtifactRegistryProtocol.BindingRow(tenant,project,id(10),resource,id(12),ref,hash('a'),id(11),hash('a'));
 }

 @Test void originalIntentOwnerBlocksRenamedTargetAndMissingOwnerQueryFailsClosed(){
  var artifact=mock(ArtifactRegistryProtocol.ArtifactRow.class);var binding=mock(ArtifactRegistryProtocol.BindingRow.class);
  when(artifact.artifactKind()).thenReturn("OTHER");when(binding.schemaRef()).thenReturn("urn:other");
  assertThrows(TenantAccessException.class,()->TaskNodeInputArtifactBoundary.requireGenericAllowed(artifact,binding,java.util.List.of(TaskFieldTargetSchemaTest.id(1))));
  assertThrows(TenantAccessException.class,()->TaskNodeInputArtifactBoundary.requireGenericAllowed(artifact,binding,null));
  assertDoesNotThrow(()->TaskNodeInputArtifactBoundary.requireGenericAllowed(artifact,binding,java.util.List.of()));
 }
 @Test void eitherKindOrSchemaDeniesEvenWithNoClaimedOwner(){
  var artifact=mock(ArtifactRegistryProtocol.ArtifactRow.class);var binding=mock(ArtifactRegistryProtocol.BindingRow.class);
  when(artifact.artifactKind()).thenReturn(TaskNodeInputRegistration.KIND);
  assertThrows(TenantAccessException.class,()->TaskNodeInputArtifactBoundary.requireGenericAllowed(artifact,binding,java.util.List.of()));
  when(artifact.artifactKind()).thenReturn("OTHER");when(binding.schemaRef()).thenReturn(TaskNodeInputSchema.REF);
  assertThrows(TenantAccessException.class,()->TaskNodeInputArtifactBoundary.requireGenericAllowed(artifact,binding,java.util.List.of()));
 }
    @Test void actualRegistryInvokesImmutableOwnershipBeforeGenericArtifactContentGrant() throws Exception {
        var mapper = mock(ArtifactRegistryMapper.class);
        var resources = mock(ResourceReadAuthority.class);
        when(mapper.artifact(id(10))).thenReturn(List.of(artifact("RENAMED")));
        when(mapper.binding(id(10))).thenReturn(List.of(binding("SOURCE", BuiltinEvaluationDocumentContextCompiler.OUTPUT)));
        when(mapper.taskNodeInputOwners(id(10))).thenReturn(List.of(id(90)));
        var registry = new ArtifactRegistry(mapper, resources, new DependencyManifestSchema(), mock(DependencyLineage.class));
        var required = new ArtifactRegistryProtocol.ReadRequirement(new PermissionProtocol.Scope(tenant, project, "SOURCE", id(12)),
                "source.read", "EVALUATION_RESULTS", BuiltinEvaluationDocumentContextCompiler.OUTPUT, hash('a'), hash('a'), new DependencyLineage.Limits(512, 2048));
        assertEquals(ErrorCode.ACCESS_DENIED, assertThrows(TenantAccessException.class,
                () -> registry.requireRegistration(admission, id(10), required)).code());
        verify(mapper).taskNodeInputOwners(id(10));
        verify(resources, times(1)).require(any(), any(), any(), any());
        verify(mapper, never()).manifest(any());
    }

    @Test void missingMigrationFailsRatherThanReturningFalseOwnership() throws Exception {
        var mapper = mock(ArtifactRegistryMapper.class);
        when(mapper.artifact(id(10))).thenReturn(List.of(artifact("RENAMED")));
        when(mapper.binding(id(10))).thenReturn(List.of(binding("SOURCE", BuiltinEvaluationDocumentContextCompiler.OUTPUT)));
        when(mapper.taskNodeInputOwners(id(10))).thenThrow(new org.springframework.jdbc.BadSqlGrammarException("owner lookup", "SELECT", new java.sql.SQLException("missing097")));
        var registry = new ArtifactRegistry(mapper, mock(ResourceReadAuthority.class), new DependencyManifestSchema(), mock(DependencyLineage.class));
        var required = new ArtifactRegistryProtocol.ReadRequirement(new PermissionProtocol.Scope(tenant, project, "SOURCE", id(12)),
                "source.read", "EVALUATION_RESULTS", BuiltinEvaluationDocumentContextCompiler.OUTPUT, hash('a'), hash('a'), new DependencyLineage.Limits(512, 2048));
        assertThrows(org.springframework.jdbc.BadSqlGrammarException.class, () -> registry.requireRegistration(admission, id(10), required));
    }

    @Test void actualMybatisOwnershipStatementIsBoundedAndTenantInjected() throws Exception {
        var config = new Configuration();
        String path = "medical-mappers/ArtifactRegistryMapper.xml";
        try (var in = Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream(path))) {
            new XMLMapperBuilder(in, config, path, config.getSqlFragments()).parse();
        }
        String prefix = "com.ruoyi.medical.persistence.mapper.ArtifactRegistryMapper.";
        assertEquals(Set.of("artifact", "binding", "manifest", "members", "version",
                        "evaluationExactInputOwners", "evaluationNodeInputOwners", "evaluationPlanPreparationOwners",
                        "taskFieldTargetOwners", "taskNodeInputOwners", "runtimeProfessionalOwners",
                        "planDraftInputOwners", "planDraftExactInputOwners",
                        "sectionWriterInputOwners", "sectionWriterExactInputOwners",
                        "sourceVerifierInputOwners", "sourceVerifierExactInputOwners",
                        "candidateAssemblyOwners", "documentReviewExactInputOwners",
                        "evaluationRuntimeObjectOwners", "evaluationCallExactInputOwners"),
                new HashSet<>(config.getMappedStatementNames().stream().filter(n -> n.startsWith(prefix)).map(n -> n.substring(prefix.length())).toList()));
        var statement = config.getMappedStatement(prefix + "taskNodeInputOwners");
        var bound = statement.getBoundSql(Map.of("id", id(10)));
        assertEquals(List.of("id"), bound.getParameterMappings().stream().map(p -> p.getProperty()).toList());
        assertFalse(statement.isUseCache()); assertTrue(statement.isFlushCacheRequired());
        tx(() -> {
            TenantScope.requireSqlAccess(prefix + "taskNodeInputOwners");
            String sql = new ProtectedTenantInterceptor().parserSingle(bound.getSql(), null);
            assertTrue(sql.contains("i.tenant_id = '" + tenant + "'"), sql);
            assertTrue(sql.contains("artifact_id = ?"), sql);
            assertTrue(sql.contains("LIMIT 2"), sql);
            assertNotNull(net.sf.jsqlparser.parser.CCJSqlParserUtil.parse(sql));
            return null;
        });
    }

}
