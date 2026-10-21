package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.identity.BusinessSessionProtocol;
import com.ruoyi.medical.persistence.mapper.ArtifactRegistryMapper;
import com.ruoyi.medical.persistence.mapper.ProjectMapper;
import com.ruoyi.medical.persistence.mapper.SourceReadMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real non-lazy Spring configuration, controllers, readiness logic, and response schema.
 * Existing authority/service prerequisites, identity admission, and DataSource are explicit
 * Mockito boundaries. No business operation, SQL, provider call, or object-store I/O is proved.
 * RuntimeObjectTransport, when enabled, is real; its deployed SourceUploadTransport is mocked.
 */
class FormalEvaluationPlanFeatureAvailabilityTest {
    private static final String ENABLED = "medical.formal-evaluation-plan.enabled";
    private static final String DIRECTORY = "medical.formal-evaluation-plan.object-directory";
    private static final List<String> FEATURES = List.of("bound_preparation", "execution_recovery", "plan_preview",
            "plan_preparation", "status", "object_recovery");
    // Kept explicit rather than derived from @ConditionalOnBean: a missing guard must fail a test.
    private static final List<Class<?>> CORE_PREREQUISITES = List.of(
            FormalEvaluationInputService.class, FormalEvaluationContextStore.class, FormalEvaluationContextSchema.class,
            ModelEligibilityAuthority.class, FormalAgentCandidateAuthority.class, AgentExecutionPolicyAuthority.class,
            FormalCapabilityIdentityAuthority.class, TemplateApplicabilityService.class, AgentPublicationDependencies.class,
            FormalEvaluationContextReadAuthority.class, EvaluationService.class, DependencyManifestSchema.class,
            ModelConfigurationService.class, EvaluationReferences.class, SourceReadAuthority.class, RunCreateBudget.class,
            ScopeSnapshotStore.class, ResourceReadAuthority.class, RunTokenAuthoritySchema.class, TemplateVersionStore.class,
            ProjectService.class, TaskPlanPolicy.class, DependencyLineage.class, ProjectMapper.class,
            SourceReadMapper.class, ArtifactRegistryMapper.class);
    private static final Set<Class<?>> READ_PREREQUISITES = Set.of(EvaluationService.class,
            FormalEvaluationContextStore.class, FormalEvaluationContextReadAuthority.class,
            FormalEvaluationContextSchema.class, AgentPublicationDependencies.class, ResourceReadAuthority.class,
            DependencyManifestSchema.class, FormalAgentCandidateAuthority.class, AgentExecutionPolicyAuthority.class,
            DependencyLineage.class, SourceReadMapper.class, ArtifactRegistryMapper.class);

    private final EnterpriseAccess access = mock(EnterpriseAccess.class);
    private final EnterpriseAccess.Admission admission = mock(EnterpriseAccess.Admission.class);
    private final ApplicationSessionIdentity.Verified subject = mock(ApplicationSessionIdentity.Verified.class);
    private final PermissionSchema json = new PermissionSchema();
    private final FormalEvaluationPlanPreparationSchema schema = new FormalEvaluationPlanPreparationSchema(json);
    @TempDir Path temporary;

    FormalEvaluationPlanFeatureAvailabilityTest() throws Exception {
        when(admission.context()).thenReturn(new BusinessSessionProtocol.Context(
                "00000000-0000-4000-8000-000000000001", "1", "1", "1"));
        when(access.execute(eq(subject), any())).thenAnswer(invocation ->
                ((Function<EnterpriseAccess.Admission, Object>) invocation.getArgument(1)).apply(admission));
    }

    @Test void disabledByDefaultAndExplicitFalseNeverCreatePlanDomainBeans() {
        for (String enabled : List.of("absent", "false")) {
            try (var fixture = configured(enabled, Set.of(), null, true)) {
                var context = fixture.context();
                assertStartedNonLazily(fixture);
                assertTrue(context.getBeansOfType(FormalEvaluationPlanPreparationController.class).isEmpty());
                assertTrue(context.getBeansOfType(FormalEvaluationPlanPreparationService.class).isEmpty());
                assertTrue(context.getBeansOfType(FormalEvaluationPlanPreparationReadService.class).isEmpty());
                assertTrue(context.getBeansOfType(FormalEvaluationPlanPreparationLedger.class).isEmpty());
                assertTrue(context.getBeansOfType(FormalEvaluationPlanPreparationRegistration.class).isEmpty());
                assertTrue(context.getBeansOfType(FormalEvaluationBoundExecutions.class).isEmpty());
                assertFalse(context.containsBean("formalEvaluationPlanObjectTransport"));
                var result = availability(context);
                assertFeatures(result, false, false, false);
                assertEquals("FEATURE_DISABLED", result.required("unavailable_reason").asText());
                verifyNoInteractions(context.getBean(SourceUploadTransport.class), context.getBean(DataSource.class));
            }
        }
    }

    @Test void disabledAvailabilityNeverConsultsBeanFactoryIncludingNamedTransport() {
        for (String enabled : List.of("absent", "false")) {
            var beans = mock(ListableBeanFactory.class);
            var environment = new MockEnvironment();
            if (!"absent".equals(enabled)) environment.withProperty(ENABLED, enabled);
            environment.withProperty(DIRECTORY, temporary.resolve("must-not-be-inspected").toString());
            var availability = new FormalEvaluationPlanFeatureAvailability(access, beans, environment, json, schema);
            var result = availability.read(subject, "1");
            assertFeatures(result, false, false, false);
            assertEquals("FEATURE_DISABLED", result.required("unavailable_reason").asText());
            verifyNoInteractions(beans);
        }
    }

    @TestFactory Stream<DynamicTest> enabledWithEachMissingOptionalPrerequisiteStartsAndFailsClosed() {
        return CORE_PREREQUISITES.stream().map(missing -> DynamicTest.dynamicTest(
                "missing " + missing.getSimpleName(), () -> {
                    try (var fixture = configured("true", Set.of(missing), null, true)) {
                        var context = fixture.context();
                        assertStartedNonLazily(fixture);
                        assertTrue(context.getBeanFactory().containsSingleton("formalEvaluationPlanPreparationController"));
                        assertTrue(context.getBeansOfType(FormalEvaluationPlanPreparationService.class).isEmpty(), missing.getName());
                        assertTrue(context.getBeansOfType(FormalEvaluationPlanPreparationAuthority.class).isEmpty(), missing.getName());
                        assertTrue(context.getBeansOfType(FormalEvaluationBoundExecutions.class).isEmpty(), missing.getName());
                        boolean readReady = !READ_PREREQUISITES.contains(missing);
                        assertEquals(readReady, context.getBeanFactory().containsSingleton("formalEvaluationPlanPreparationReadService"));
                        assertEquals(readReady, context.getBeanFactory().containsSingleton("formalEvaluationPlanPreparationLedger"));
                        assertEquals(readReady, context.getBeanFactory().containsSingleton("formalEvaluationPlanPreparationRegistration"));
                        assertFeatures(availability(context), false, readReady, false);
                        assertEquals("DEPENDENCY_UNAVAILABLE", availability(context).required("unavailable_reason").asText());
                        verifyNoInteractions(context.getBean(SourceUploadTransport.class), context.getBean(DataSource.class));
                    }
                }));
    }

    @Test void readOnlyStatusAndExecutionRecoverySurviveMissingExecuteModelBudgetAndObjectTransportTogether() {
        var omitted = Set.<Class<?>>of(FormalEvaluationInputService.class,
                ModelEligibilityAuthority.class, ModelConfigurationService.class, RunCreateBudget.class);
        try (var fixture = configured("true", omitted, temporary.resolve("absent-directory"), false)) {
            var context = fixture.context();
            assertStartedNonLazily(fixture);
            assertFalse(mockingDetails(context.getBean(FormalEvaluationPlanPreparationReadService.class)).isMock());
            assertFalse(mockingDetails(context.getBean(FormalEvaluationPlanPreparationLedger.class)).isMock());
            assertFalse(mockingDetails(context.getBean(FormalEvaluationPlanPreparationRegistration.class)).isMock());
            assertTrue(context.getBeansOfType(FormalEvaluationPlanPreparationService.class).isEmpty());
            assertTrue(context.getBeansOfType(SourceUploadTransport.class).isEmpty());
            assertFalse(context.containsBean("formalEvaluationPlanObjectTransport"));
            assertFeatures(availability(context), false, true, false);
            verifyNoInteractions(context.getBean(DataSource.class));
        }
    }

    @Test void missingReadAuthorityNeverActivatesCoreThatDependsOnReadWiringLedger() {
        try (var fixture = configured("true", Set.of(FormalEvaluationContextReadAuthority.class), null, true)) {
            var context = fixture.context();
            assertStartedNonLazily(fixture);
            assertTrue(context.getBeansOfType(FormalEvaluationPlanPreparationLedger.class).isEmpty());
            assertTrue(context.getBeansOfType(FormalEvaluationPlanPreparationReadService.class).isEmpty());
            assertTrue(context.getBeansOfType(FormalEvaluationPlanPreparationService.class).isEmpty());
            assertTrue(context.getBeansOfType(FormalEvaluationExecutionAdmission.class).isEmpty());
            assertTrue(context.getBeansOfType(FormalEvaluationPlanPreparationAuthority.class).isEmpty());
            assertFeatures(availability(context), false, false, false);
        }
    }

    @Test void configuredDirectoryWithoutDeployedTransportSkipsTransportButKeepsRealCoreAndReads() {
        Path nonexistent = temporary.resolve("not-created");
        assertFalse(Files.exists(nonexistent));
        try (var fixture = configured("true", Set.of(), nonexistent, false)) {
            var context = fixture.context();
            assertStartedNonLazily(fixture);
            assertRealCoreAndReads(context);
            assertTrue(context.getBeansOfType(SourceUploadTransport.class).isEmpty());
            assertFalse(context.containsBean("formalEvaluationPlanObjectTransport"));
            assertFeatures(availability(context), true, true, false);
            assertEquals("DEPENDENCY_UNAVAILABLE", availability(context).required("unavailable_reason").asText());
            assertFalse(Files.exists(nonexistent));
            verifyNoInteractions(context.getBean(DataSource.class));
        }
    }

    @Test void absentObjectDirectoryDoesNotConstructTransportEvenWhenDeploymentAdapterExists() {
        try (var fixture = configured("true", Set.of(), null, true)) {
            var context = fixture.context();
            assertStartedNonLazily(fixture);
            assertRealCoreAndReads(context);
            assertFalse(context.containsBean("formalEvaluationPlanObjectTransport"));
            assertFeatures(availability(context), true, true, false);
            verifyNoInteractions(context.getBean(SourceUploadTransport.class), context.getBean(DataSource.class));
        }
    }

    @Test void actualConfiguredInstancesAndPrivateDirectoryEnableAllPreparationReadinessFlags() throws Exception {
        Path directory = Files.createDirectory(temporary.resolve("objects"),
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        try (var fixture = configured("true", Set.of(), directory, true)) {
            var context = fixture.context();
            assertStartedNonLazily(fixture);
            assertRealCoreAndReads(context);
            assertTrue(context.getBeanFactory().containsSingleton("formalEvaluationPlanObjectTransport"));
            assertFalse(mockingDetails(context.getBean("formalEvaluationPlanObjectTransport", RuntimeObjectTransport.class)).isMock());
            var result = availability(context);
            assertFeatures(result, true, true, true);
            assertTrue(result.required("unavailable_reason").isNull());
            verifyNoInteractions(context.getBean(SourceUploadTransport.class), context.getBean(DataSource.class));
            try (var files = Files.list(directory)) { assertEquals(0, files.count()); }
        }
    }

    @Test void controllerAndServiceClassDefinitionsAloneDoNotConstituteReadiness() {
        var beans = new DefaultListableBeanFactory();
        for (Class<?> type : List.of(FormalEvaluationPlanPreparationController.class,
                FormalEvaluationPlanPreparationService.class, FormalEvaluationBoundExecutions.class,
                FormalEvaluationExecutionInputs.class, FormalEvaluationExecutionAdmission.class,
                FormalEvaluationPlanPreparationAuthority.class, FormalEvaluationContextStore.class,
                FormalEvaluationPlanExpansion.class, FormalEvaluationPlanPreparationReadService.class,
                FormalEvaluationPlanPreparationLedger.class, FormalEvaluationPlanPreparationRegistration.class)) {
            var definition = new RootBeanDefinition(type);
            definition.setLazyInit(true);
            beans.registerBeanDefinition(type.getSimpleName(), definition);
        }
        var transport = new RootBeanDefinition(RuntimeObjectTransport.class);
        transport.setLazyInit(true);
        beans.registerBeanDefinition("formalEvaluationPlanObjectTransport", transport);
        var availability = new FormalEvaluationPlanFeatureAvailability(access, beans,
                new MockEnvironment().withProperty(ENABLED, "true"), json, schema);
        var result = availability.read(subject, "1");
        assertFeatures(result, false, false, false);
        assertEquals("DEPENDENCY_UNAVAILABLE", result.required("unavailable_reason").asText());
    }

    @Test void staleContextIsRejectedBeforeAnyReadinessInspection() {
        var beans = mock(ListableBeanFactory.class);
        var availability = new FormalEvaluationPlanFeatureAvailability(access, beans,
                new MockEnvironment().withProperty(ENABLED, "true"), json, schema);
        assertThrows(TenantAccessException.class, () -> availability.read(subject, "2"));
        verifyNoInteractions(beans);
    }

    private Fixture configured(String enabled, Set<Class<?>> omitted, Path directory, boolean deployedTransport) {
        var context = new AnnotationConfigApplicationContext();
        var eager = new AtomicBoolean();
        var properties = new java.util.HashMap<String, Object>();
        if (!"absent".equals(enabled)) properties.put(ENABLED, enabled);
        if (directory != null) properties.put(DIRECTORY, directory.toString());
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("fixture", properties));
        context.registerBean(EnterpriseAccess.class, () -> access);
        context.registerBean(PermissionSchema.class, () -> json);
        context.registerBean(FormalEvaluationPlanPreparationSchema.class, () -> schema);
        registerMock(context, ApplicationSessionIdentity.class);
        registerMock(context, DataSource.class);
        for (Class<?> type : CORE_PREREQUISITES) if (!omitted.contains(type)) registerMock(context, type);
        if (deployedTransport) registerMock(context, SourceUploadTransport.class);
        context.registerBean("unrelatedApplication", String.class, () -> { eager.set(true); return "still usable"; });
        context.register(FormalEvaluationPlanPreparationConfiguration.class, FormalEvaluationPlanPreparationController.class,
                FormalEvaluationPlanFeatureAvailability.class, FormalEvaluationPlanFeatureAvailabilityController.class);
        try {
            context.refresh();
            return new Fixture(context, eager);
        } catch (RuntimeException | Error failure) {
            context.close();
            throw failure;
        }
    }

    private static <T> void registerMock(AnnotationConfigApplicationContext context, Class<T> type) {
        // Register an existing test double, so annotations on an upstream class cannot import
        // unrelated production configurations (for example TemplateDependencyPolicy binding).
        context.getBeanFactory().registerSingleton("fixture_" + type.getName(), mock(type));
    }

    private void assertStartedNonLazily(Fixture fixture) {
        assertTrue(fixture.context().isActive());
        assertTrue(fixture.eager().get(), "unrelated application's supplier must run during refresh");
        assertTrue(fixture.context().getBeanFactory().containsSingleton("unrelatedApplication"));
        assertEquals("still usable", fixture.context().getBean("unrelatedApplication"));
        assertNotNull(fixture.context().getBean(FormalEvaluationPlanFeatureAvailabilityController.class));
    }

    private static void assertRealCoreAndReads(AnnotationConfigApplicationContext context) {
        var expected = Map.<String, Class<?>>ofEntries(
                Map.entry("formalEvaluationPlanPreparationController", FormalEvaluationPlanPreparationController.class),
                Map.entry("formalEvaluationPlanPreparationService", FormalEvaluationPlanPreparationService.class),
                Map.entry("formalEvaluationPlanPreparationReadService", FormalEvaluationPlanPreparationReadService.class),
                Map.entry("formalEvaluationPlanPreparationAuthority", FormalEvaluationPlanPreparationAuthority.class),
                Map.entry("formalEvaluationPlanPreparationLedger", FormalEvaluationPlanPreparationLedger.class),
                Map.entry("formalEvaluationPlanPreparationRegistration", FormalEvaluationPlanPreparationRegistration.class),
                Map.entry("formalEvaluationExecutionAdmission", FormalEvaluationExecutionAdmission.class),
                Map.entry("formalEvaluationExecutionInputs", FormalEvaluationExecutionInputs.class),
                Map.entry("formalEvaluationBoundExecutions", FormalEvaluationBoundExecutions.class),
                Map.entry("formalEvaluationPlanExpansion", FormalEvaluationPlanExpansion.class));
        expected.forEach((name, type) -> {
            assertTrue(context.getBeanFactory().containsSingleton(name), name + " must exist before lookup");
            assertFalse(mockingDetails(context.getBean(type)).isMock(), name + " must be a production instance");
        });
    }

    private JsonNode availability(AnnotationConfigApplicationContext context) {
        return context.getBean(FormalEvaluationPlanFeatureAvailability.class).read(subject, "1");
    }

    private static void assertFeatures(JsonNode result, boolean core, boolean reads, boolean objects) {
        var features = result.required("features");
        assertEquals(FEATURES.size(), features.size());
        assertEquals(core, features.required("bound_preparation").asBoolean());
        assertEquals(core, features.required("plan_preview").asBoolean());
        assertEquals(reads, features.required("execution_recovery").asBoolean());
        assertEquals(reads, features.required("status").asBoolean());
        assertEquals(objects, features.required("plan_preparation").asBoolean());
        assertEquals(objects, features.required("object_recovery").asBoolean());
    }

    private record Fixture(AnnotationConfigApplicationContext context, AtomicBoolean eager) implements AutoCloseable {
        @Override public void close() { context.close(); }
    }
}
