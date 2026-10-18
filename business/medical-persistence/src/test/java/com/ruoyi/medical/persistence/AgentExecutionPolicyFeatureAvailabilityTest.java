package com.ruoyi.medical.persistence;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.SchemaRejection;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.identity.BusinessSessionProtocol;
import java.math.BigInteger;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

class AgentExecutionPolicyFeatureAvailabilityTest {
    private static final String TENANT = "00000000-0000-4000-8000-000000000001";
    private static final String CONTEXT_ID = "00000000-0000-4000-8000-000000000002";
    private final EnterpriseAccess access = mock(EnterpriseAccess.class);
    private final ApplicationSessionIdentity.Verified subject = mock(ApplicationSessionIdentity.Verified.class);
    private final BusinessSessionProtocol.Context context = new BusinessSessionProtocol.Context(TENANT, "7", "2", "5");

    @SuppressWarnings("unchecked")
    private AgentExecutionPolicyFeatureAvailability service(ListableBeanFactory beans) throws Exception {
        var admission = new EnterpriseAccess.Admission(subject, CONTEXT_ID, context, new TenantWatermarks(BigInteger.TWO, BigInteger.valueOf(5)));
        when(access.execute(eq(subject), any())).thenAnswer(call -> ((Function<EnterpriseAccess.Admission, Object>) call.getArgument(1)).apply(admission));
        return new AgentExecutionPolicyFeatureAvailability(access, beans, new PermissionSchema());
    }

    @Test void absentControllerReportsOneFalseFlagAndNoGrantFields() throws Exception {
        var view = service(new DefaultListableBeanFactory()).read(subject, "7");
        assertEquals(3, view.size()); assertEquals(2, view.required("features").size());
        assertFalse(view.required("features").required("policy_authoring").booleanValue());
        assertEquals(TENANT, view.required("current_context").required("tenant_id").textValue());
        assertFalse(view.has("allowed_actions")); assertFalse(view.has("permissions")); assertFalse(view.has("configuration"));
        verify(access).execute(eq(subject), any());
    }

    @Test void flagsReflectControllerAndActualModelPortWithoutInstantiation() throws Exception {
        for(int mask=0;mask<4;mask++){
            var beans=new DefaultListableBeanFactory();
            if((mask&1)!=0)beans.registerBeanDefinition("controller",new RootBeanDefinition(AgentExecutionPolicyController.class));
            if((mask&2)!=0)beans.registerBeanDefinition("modelPort",new RootBeanDefinition(AgentExecutionPolicyModelPort.class));
            var view=service(beans).read(subject,"7");
            assertEquals((mask&1)!=0,view.at("/features/policy_authoring").booleanValue());
            assertEquals(mask==3,view.at("/features/model_policy_approval").booleanValue());
            assertFalse(beans.containsSingleton("controller"));assertFalse(beans.containsSingleton("modelPort"));
        }
    }

    @Test void existingEndpointSwitchesStillDefaultDisabled() {
        for (Class<?> type : new Class<?>[]{AgentExecutionPolicyController.class}) {
            var condition = type.getAnnotation(ConditionalOnProperty.class);
            assertNotNull(condition); assertFalse(condition.matchIfMissing()); assertEquals("true", condition.havingValue());
            assertArrayEquals(new String[]{"enabled"}, condition.name());
        }
        assertNull(AgentExecutionPolicyFeatureAvailabilityController.class.getAnnotation(ConditionalOnProperty.class));
    }

    @Test void wrongCurrentContextRejectsBeforeInspectingControllerRegistration() throws Exception {
        var beans = mock(ListableBeanFactory.class); var service = service(beans);
        var failure = assertThrows(TenantAccessException.class, () -> service.read(subject, "6"));
        assertEquals(ErrorCode.CONTEXT_CHANGED, failure.code());
        verifyNoInteractions(beans);
    }

    @Test void fixedSchemaRejectsArbitraryPropertiesMissingFlagsAndStringBooleans() throws Exception {
        var service = service(new DefaultListableBeanFactory()); var valid = service.read(subject, "7");
        var extra = (ObjectNode) valid.deepCopy(); extra.put("secret_configuration", "not allowed");
        assertThrows(SchemaRejection.class, () -> service.validate(extra));
        var missing = (ObjectNode) valid.deepCopy(); ((ObjectNode) missing.required("features")).remove("policy_authoring");
        assertThrows(SchemaRejection.class, () -> service.validate(missing));
        var wrong = (ObjectNode) valid.deepCopy(); ((ObjectNode) wrong.required("features")).put("policy_authoring", "true");
        assertThrows(SchemaRejection.class, () -> service.validate(wrong));
        var grant = (ObjectNode) valid.deepCopy(); ((ObjectNode) grant.required("features")).put("rule.approve", true);
        assertThrows(SchemaRejection.class, () -> service.validate(grant));
    }
}
