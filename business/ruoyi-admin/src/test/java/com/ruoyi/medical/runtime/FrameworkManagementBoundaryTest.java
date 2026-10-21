package com.ruoyi.medical.runtime;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.access.prepost.PreAuthorize;
import static org.junit.jupiter.api.Assertions.*;

/** Deferred source-authored checks. No execution evidence is claimed. */
public class FrameworkManagementBoundaryTest {
    @Test void generatorCannotActivateInProductionOrWithoutLocalIdentity() {
        assertThrows(IllegalStateException.class, () -> new FrameworkDevelopmentConfiguration(new MockEnvironment()
                .withProperty("medical.identity.mode", "local").withProperty("medical.framework.environment", "PRODUCTION")));
        assertThrows(IllegalStateException.class, () -> new FrameworkDevelopmentConfiguration(new MockEnvironment()
                .withProperty("medical.identity.mode", "oidc").withProperty("medical.framework.environment", "DEVELOPMENT")));
    }
    @Test void originalNativeControllersRemainGuardedAndPrefixed() {
        assertTrue(RuoYiManagementConfiguration.CONTROLLERS.contains(com.ruoyi.web.controller.system.SysUserController.class));
        assertTrue(RuoYiManagementConfiguration.CONTROLLERS.contains(com.ruoyi.web.controller.system.SysRoleController.class));
        assertTrue(RuoYiManagementConfiguration.CONTROLLERS.contains(com.ruoyi.generator.controller.GenController.class));
        assertEquals("system:user:import", RuoYiManagementConfiguration.AUXILIARY_PERMISSIONS.get("SysUserController#importTemplate"));
    }
    @Test void registrationRequiresTypedCompiledJobAndBoundedIdentity() {
        assertThrows(IllegalArgumentException.class, () -> new FrameworkJobRegistration(0, "DEFAULT", "safe", null));
        assertThrows(IllegalArgumentException.class, () -> new FrameworkJobRegistration(1, "../outside", "safe", DummyJob.class));
        assertThrows(IllegalArgumentException.class, () -> new FrameworkJobRegistration(1, "DEFAULT", "bean.call()", DummyJob.class));
        assertEquals("registered-1", new FrameworkJobRegistration(1, "DEFAULT", "safe", DummyJob.class).jobKey().getName());
    }
    @Test void everyMonitorEndpointKeepsOriginalMethodAuthorization() {
        for (var type : java.util.List.of(FrameworkMonitorController.class, FrameworkJobMonitorController.class, FrameworkJobCommandController.class, FrameworkJobLifecycleController.class, FrameworkJobExportController.class, FrameworkToolController.class)) {
            for (var method : type.getDeclaredMethods()) {
                boolean endpoint = java.util.Arrays.stream(method.getAnnotations()).anyMatch(annotation ->
                        annotation.annotationType().getName().startsWith("org.springframework.web.bind.annotation.")
                        && annotation.annotationType().getSimpleName().endsWith("Mapping"));
                if (endpoint) assertNotNull(method.getAnnotation(PreAuthorize.class), type.getSimpleName() + "." + method.getName());
            }
        }
    }
    @Test void nativeOptionsAndGeneratorColumnPathKeepExactBoundary() throws Exception {
        assertEquals("system:post:query", RuoYiManagementConfiguration.AUXILIARY_PERMISSIONS.get("SysPostController#optionselect"));
        var column = com.ruoyi.generator.controller.GenController.class.getMethod("columnList", Long.class);
        assertNotNull(column.getParameters()[0].getAnnotation(org.springframework.web.bind.annotation.PathVariable.class));
    }
    @Test void medicalAuditNeverPersistsConfigValueOrRawResult() throws Exception {
        var aspect = new com.ruoyi.framework.aspectj.LogAspect();
        org.springframework.test.util.ReflectionTestUtils.setField(aspect, "medicalIdentityMode", "local");
        var request = new org.springframework.mock.web.MockHttpServletRequest("PUT", "/api/system/config");
        org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(new org.springframework.web.context.request.ServletRequestAttributes(request));
        try {
            var point = org.mockito.Mockito.mock(org.aspectj.lang.JoinPoint.class);
            var annotation = org.mockito.Mockito.mock(com.ruoyi.common.annotation.Log.class);
            org.mockito.Mockito.when(annotation.businessType()).thenReturn(com.ruoyi.common.enums.BusinessType.UPDATE);
            org.mockito.Mockito.when(annotation.operatorType()).thenReturn(com.ruoyi.common.enums.OperatorType.MANAGE);
            org.mockito.Mockito.when(annotation.excludeParamNames()).thenReturn(new String[0]);
            org.mockito.Mockito.when(annotation.isSaveRequestData()).thenReturn(true);
            org.mockito.Mockito.when(annotation.isSaveResponseData()).thenReturn(true);
            var config = new com.ruoyi.system.domain.SysConfig(); config.setConfigId(7L); config.setConfigValue("sensitive-one");
            org.mockito.Mockito.when(point.getArgs()).thenReturn(new Object[]{config});
            var first = new com.ruoyi.system.domain.SysOperLog();
            aspect.getControllerMethodDescription(point, annotation, first, java.util.Map.of("code", 200, "configValue", "sensitive-one"));
            config.setConfigValue("sensitive-two");
            var second = new com.ruoyi.system.domain.SysOperLog();
            aspect.getControllerMethodDescription(point, annotation, second, java.util.Map.of("code", 200, "noticeContent", "private-message"));
            assertEquals(first.getOperParam(), second.getOperParam(), "secret values must not even affect the projection digest");
            assertFalse(first.getJsonResult().contains("sensitive-one"));
            assertFalse(second.getJsonResult().contains("private-message"));
        } finally { org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes(); }
    }
    static final class DummyJob implements FrameworkRegisteredJob {
        @Override public void execute(org.quartz.JobExecutionContext ignored) { throw new AssertionError("never executed"); }
    }
}
