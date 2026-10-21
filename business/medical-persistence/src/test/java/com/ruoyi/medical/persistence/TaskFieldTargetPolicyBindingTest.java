package com.ruoyi.medical.persistence;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class TaskFieldTargetPolicyBindingTest {
    @Test void explicitDeploymentBoundsBindWithoutInventedDefaults() {
        var values=Map.<String,Object>of(
                "medical.task-field-target.maximum-mappings","100",
                "medical.task-field-target.maximum-inventory-fields","200",
                "medical.task-field-target.maximum-target-bytes","8192",
                "medical.task-field-target.maximum-original-bytes","16384",
                "medical.task-field-target.maximum-parsed-bytes","32768",
                "medical.task-field-target.environment","test",
                "medical.task-field-target.region","local",
                "medical.task-field-target.maximum-versions","50",
                "medical.task-field-target.maximum-relations","100");
        var policy=new Binder(new MapConfigurationPropertySource(values))
                .bind("medical.task-field-target",Bindable.of(TaskFieldTargetPolicy.class)).get();
        assertEquals(8192,policy.maximumTargetBytes());assertEquals(50,policy.maximumVersions());
        assertEquals("test",policy.environment());assertDoesNotThrow(policy::requireConfigured);
        assertDoesNotThrow(policy::lineageLimits);
    }
    @Test void absentOrPartialConfigurationCannotEnableProduction() {
        assertFalse(new Binder(new MapConfigurationPropertySource(Map.of()))
                .bind("medical.task-field-target",Bindable.of(TaskFieldTargetPolicy.class)).isBound());
        var partial=new Binder(new MapConfigurationPropertySource(Map.of("medical.task-field-target.environment","test")))
                .bind("medical.task-field-target",Bindable.of(TaskFieldTargetPolicy.class)).get();
        assertEquals(0,partial.maximumTargetBytes());
        assertEquals(com.ruoyi.medical.contracts.ErrorCode.DEPENDENCY_UNAVAILABLE,
                assertThrows(TenantAccessException.class,partial::requireConfigured).code());
    }
}
