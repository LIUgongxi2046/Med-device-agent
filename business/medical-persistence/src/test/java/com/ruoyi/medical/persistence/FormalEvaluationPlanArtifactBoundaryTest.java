package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.persistence.mapper.ArtifactRegistryMapper;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import java.math.BigInteger;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FormalEvaluationPlanArtifactBoundaryTest extends PlanPreparationFixture {
    FormalEvaluationPlanArtifactBoundaryTest() throws Exception { }

    ArtifactRegistryProtocol.ArtifactRow artifact(String kind) {
        return new ArtifactRegistryProtocol.ArtifactRow(tenant, project, id(10), kind, "{}", "original/key",
                "version", hash('a'), BigInteger.ONE, "application/json", id(11), hash('a'), "1.0.0", hash('a'), "INTERNAL");
    }
    ArtifactRegistryProtocol.BindingRow binding(String resource, String ref) {
        return new ArtifactRegistryProtocol.BindingRow(tenant, project, id(10), resource, id(12), ref,
                hash('a'), id(11), hash('a'));
    }

    @Test void newKindOrImmutableOwnershipBlocksGenericReadIncludingKindRenaming() {
        for (String kind : List.of("EVALUATION_PLAN_PREPARATION", "SIMULATION_EXACT_INPUT", "MODEL_VERIFICATION_INPUT", "DOCUMENT_BODY")) {
            assertThrows(TenantAccessException.class, () -> FormalEvaluationPlanArtifactBoundary.requireGenericAllowed(
                    artifact(kind), binding("SOURCE", BuiltinEvaluationDocumentContextCompiler.OUTPUT), List.of(id(90))));
        }
        assertThrows(TenantAccessException.class, () -> FormalEvaluationPlanArtifactBoundary.requireGenericAllowed(
                artifact("EVALUATION_PLAN_PREPARATION"), binding("EVALUATION", BuiltinEvaluationDocumentContextCompiler.OUTPUT), List.of()));
    }

    @Test void unownedLegacySourceConfigurationAndEvaluationExactInputsKeepExistingGuards() {
        for (String kind : List.of("SIMULATION_EXACT_INPUT", "MODEL_VERIFICATION_INPUT", "N00_INPUT", "PLAN_CANDIDATE")) {
            for (String owner : List.of("SOURCE", "CONFIGURATION", "EVALUATION", "PROJECT")) {
                assertDoesNotThrow(() -> FormalEvaluationPlanArtifactBoundary.requireGenericAllowed(
                        artifact(kind), binding(owner, BuiltinEvaluationDocumentContextCompiler.OUTPUT), List.of()));
            }
        }
    }

    @Test void absentOrCorruptMultipleOwnershipCannotSilentlyBecomeUnowned() {
        for (List<String> owners : Arrays.asList(null, List.of(id(90), id(91)))) {
            assertThrows(TenantAccessException.class, () -> FormalEvaluationPlanArtifactBoundary.requireGenericAllowed(
                    artifact("RENAMED"), binding("SOURCE", BuiltinEvaluationDocumentContextCompiler.OUTPUT), owners));
        }
    }

    @Test void actualRegistryInvokesImmutableOwnershipBeforeGenericArtifactContentGrant() throws Exception {
        var mapper = mock(ArtifactRegistryMapper.class);
        var resources = mock(ResourceReadAuthority.class);
        when(mapper.artifact(id(10))).thenReturn(List.of(artifact("RENAMED")));
        when(mapper.evaluationExactInputOwners(id(10))).thenReturn(List.of());
        when(mapper.binding(id(10))).thenReturn(List.of(binding("SOURCE", BuiltinEvaluationDocumentContextCompiler.OUTPUT)));
        when(mapper.evaluationPlanPreparationOwners(id(10))).thenReturn(List.of(id(90)));
        var registry = new ArtifactRegistry(mapper, resources, new DependencyManifestSchema(), mock(DependencyLineage.class));
        var required = new ArtifactRegistryProtocol.ReadRequirement(new PermissionProtocol.Scope(tenant, project, "SOURCE", id(12)),
                "source.read", "EVALUATION_RESULTS", BuiltinEvaluationDocumentContextCompiler.OUTPUT, hash('a'), hash('a'), new DependencyLineage.Limits(512, 2048));
        assertEquals(ErrorCode.ACCESS_DENIED, assertThrows(TenantAccessException.class,
                () -> registry.requireRegistration(admission, id(10), required)).code());
        verify(mapper).evaluationPlanPreparationOwners(id(10));
        verify(resources, times(1)).require(any(), any(), any(), any());
        verify(mapper, never()).manifest(any());
    }

    @Test void missingMigrationFailsRatherThanReturningFalseOwnership() throws Exception {
        var mapper = mock(ArtifactRegistryMapper.class);
        when(mapper.artifact(id(10))).thenReturn(List.of(artifact("RENAMED")));
        when(mapper.evaluationExactInputOwners(id(10))).thenReturn(List.of());
        when(mapper.binding(id(10))).thenReturn(List.of(binding("SOURCE", BuiltinEvaluationDocumentContextCompiler.OUTPUT)));
        when(mapper.evaluationPlanPreparationOwners(id(10))).thenThrow(new org.springframework.jdbc.BadSqlGrammarException("owner lookup", "SELECT", new java.sql.SQLException("missing090")));
        var registry = new ArtifactRegistry(mapper, mock(ResourceReadAuthority.class), new DependencyManifestSchema(), mock(DependencyLineage.class));
        var required = new ArtifactRegistryProtocol.ReadRequirement(new PermissionProtocol.Scope(tenant, project, "SOURCE", id(12)),
                "source.read", "EVALUATION_RESULTS", BuiltinEvaluationDocumentContextCompiler.OUTPUT, hash('a'), hash('a'), new DependencyLineage.Limits(512, 2048));
        assertThrows(org.springframework.jdbc.BadSqlGrammarException.class, () -> registry.requireRegistration(admission, id(10), required));
    }

    @Test void actualMybatisOwnershipStatementIsBoundedAndTenantInjected() throws Exception {
        var config = new Configuration();
        String path = "medical-mappers/ArtifactRegistryMapper.xml";
        try (var in = getClass().getClassLoader().getResourceAsStream(path)) {
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
        var statement = config.getMappedStatement(prefix + "evaluationPlanPreparationOwners");
        var bound = statement.getBoundSql(Map.of("id", id(10)));
        assertEquals(List.of("id"), bound.getParameterMappings().stream().map(p -> p.getProperty()).toList());
        assertFalse(statement.isUseCache()); assertTrue(statement.isFlushCacheRequired());
        tx(() -> {
            TenantScope.requireSqlAccess(prefix + "evaluationPlanPreparationOwners");
            String sql = new ProtectedTenantInterceptor().parserSingle(bound.getSql(), null);
            assertTrue(sql.contains("i.tenant_id = '" + tenant + "'"), sql);
            assertTrue(sql.contains("artifact_id = ?"), sql);
            assertTrue(sql.contains("LIMIT 2"), sql);
            assertNotNull(net.sf.jsqlparser.parser.CCJSqlParserUtil.parse(sql));
            return null;
        });
    }

    @Test void newKindDeniesWithoutAnImmutableOwnerRecordBeforeContentGrant() throws Exception {
        var mapper = mock(ArtifactRegistryMapper.class);
        var resources = mock(ResourceReadAuthority.class);
        when(mapper.artifact(id(10))).thenReturn(List.of(artifact("EVALUATION_PLAN_PREPARATION")));
        when(mapper.binding(id(10))).thenReturn(List.of(binding("SOURCE", BuiltinEvaluationDocumentContextCompiler.OUTPUT)));
        when(mapper.evaluationExactInputOwners(id(10))).thenReturn(List.of());
        when(mapper.evaluationPlanPreparationOwners(id(10))).thenReturn(List.of());
        var registry = new ArtifactRegistry(mapper, resources, new DependencyManifestSchema(), mock(DependencyLineage.class));
        var required = new ArtifactRegistryProtocol.ReadRequirement(new PermissionProtocol.Scope(tenant, project, "SOURCE", id(12)),
                "source.read", "EVALUATION_RESULTS", BuiltinEvaluationDocumentContextCompiler.OUTPUT, hash('a'), hash('a'), new DependencyLineage.Limits(512, 2048));
        assertEquals(ErrorCode.ACCESS_DENIED, assertThrows(TenantAccessException.class,
                () -> registry.requireRegistration(admission, id(10), required)).code());
        verify(mapper).evaluationPlanPreparationOwners(id(10));
        verify(resources, times(1)).require(any(), any(), any(), any());
        verify(mapper, never()).manifest(any());
    }

    @Test void existingExactInputOwnershipStillDeniesBeforePlanOwnershipCheck() throws Exception {
        var mapper = mock(ArtifactRegistryMapper.class);
        var resources = mock(ResourceReadAuthority.class);
        when(mapper.artifact(id(10))).thenReturn(List.of(artifact("RENAMED")));
        when(mapper.binding(id(10))).thenReturn(List.of(binding("SOURCE", BuiltinEvaluationDocumentContextCompiler.OUTPUT)));
        when(mapper.evaluationExactInputOwners(id(10))).thenReturn(List.of(id(91)));
        var registry = new ArtifactRegistry(mapper, resources, new DependencyManifestSchema(), mock(DependencyLineage.class));
        var required = new ArtifactRegistryProtocol.ReadRequirement(new PermissionProtocol.Scope(tenant, project, "SOURCE", id(12)),
                "source.read", "EVALUATION_RESULTS", BuiltinEvaluationDocumentContextCompiler.OUTPUT, hash('a'), hash('a'), new DependencyLineage.Limits(512, 2048));
        assertEquals(ErrorCode.ACCESS_DENIED, assertThrows(TenantAccessException.class,
                () -> registry.requireRegistration(admission, id(10), required)).code());
        verify(mapper).evaluationExactInputOwners(id(10));
        verify(mapper, never()).evaluationPlanPreparationOwners(any());
        verify(resources, times(1)).require(any(), any(), any(), any());
    }

    @Test void allActualEmittedJdbcStatementsParseWithoutSqlRewriting() throws Exception {
        var statements = new com.fasterxml.jackson.databind.ObjectMapper().readTree(
                getClass().getResourceAsStream("/formal-evaluation-plan-preparation/actual-sql.json"));
        assertTrue(statements.isArray());
        assertTrue(statements.size() >= 30);
        for (var entry : statements) {
            String sql = entry.textValue();
            var parsed = net.sf.jsqlparser.parser.CCJSqlParserUtil.parse(sql);
            assertTrue(parsed instanceof net.sf.jsqlparser.statement.select.Select
                    || parsed instanceof net.sf.jsqlparser.statement.insert.Insert
                    || parsed instanceof net.sf.jsqlparser.statement.update.Update, sql);
        }
    }

    @Test void allThreeNewTablesRejectTenantSpoofThroughActualInterceptor() throws Exception {
        tx(() -> {
            for (String table : List.of("formal_evaluation_plan_preparation_intent", "formal_evaluation_plan_preparation_attempt", "formal_evaluation_plan_preparation_registered")) {
                assertThrows(TenantAccessException.class, () -> new ProtectedTenantInterceptor().parserSingle(
                        "INSERT INTO " + table + " (tenant_id) VALUES ('" + id(999) + "')", null));
            }
            return null;
        });
    }

    @Test void allThreeNewTablesRejectMutationThroughActualInterceptor() throws Exception {
        tx(() -> {
            for (String table : List.of("formal_evaluation_plan_preparation_intent", "formal_evaluation_plan_preparation_attempt", "formal_evaluation_plan_preparation_registered")) {
                var guard = new ProtectedTenantInterceptor();
                assertThrows(TenantAccessException.class, () -> guard.parserSingle("UPDATE " + table + " SET classification='PUBLIC'", null));
                assertThrows(TenantAccessException.class, () -> guard.parserSingle("DELETE FROM " + table, null));
            }
            return null;
        });
    }
}
