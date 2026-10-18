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

class FormalAgentPublicationFeatureAvailabilityTest {
    private static final String TENANT = "00000000-0000-4000-8000-000000000001";
    private static final String CONTEXT_ID = "00000000-0000-4000-8000-000000000002";
    private final EnterpriseAccess access = mock(EnterpriseAccess.class);
    private final ApplicationSessionIdentity.Verified subject = mock(ApplicationSessionIdentity.Verified.class);
    private final BusinessSessionProtocol.Context context = new BusinessSessionProtocol.Context(TENANT, "7", "2", "5");

    @SuppressWarnings("unchecked")
    private FormalAgentPublicationFeatureAvailability service(ListableBeanFactory beans) throws Exception {
        var admission = new EnterpriseAccess.Admission(subject, CONTEXT_ID, context, new TenantWatermarks(BigInteger.TWO, BigInteger.valueOf(5)));
        when(access.execute(eq(subject), any())).thenAnswer(call -> ((Function<EnterpriseAccess.Admission, Object>) call.getArgument(1)).apply(admission));
        return new FormalAgentPublicationFeatureAvailability(access, beans, new PermissionSchema());
    }

    @Test void absentControllerReportsOneFalseFlagAndNoGrantFields() throws Exception {
        var view = service(new DefaultListableBeanFactory()).read(subject, "7");
        assertEquals(3, view.size()); assertEquals(3, view.required("features").size());
        assertFalse(view.required("features").required("publication_routes").booleanValue());
        assertFalse(view.at("/features/acceptance_routes").booleanValue());
        assertEquals(TENANT, view.required("current_context").required("tenant_id").textValue());
        assertFalse(view.has("allowed_actions")); assertFalse(view.has("permissions")); assertFalse(view.has("configuration"));
        verify(access).execute(eq(subject), any());
    }

    @Test void flagsReflectControllerAndActualModelPortWithoutInstantiation() throws Exception {
        for(int mask=0;mask<4;mask++){
            var beans=new DefaultListableBeanFactory();
            if((mask&1)!=0)beans.registerBeanDefinition("controller",new RootBeanDefinition(FormalAgentPublicationController.class));
            if((mask&2)!=0)beans.registerBeanDefinition("modelPort",new RootBeanDefinition(FormalEvaluationProfessionalCompletions.class));
            var view=service(beans).read(subject,"7");
            assertEquals((mask&1)!=0,view.at("/features/publication_routes").booleanValue());
            assertEquals((mask&2)!=0,view.at("/features/professional_completion_registered").booleanValue());
            assertFalse(beans.containsSingleton("controller"));assertFalse(beans.containsSingleton("modelPort"));
        }
    }

    @Test void existingEndpointSwitchesStillDefaultDisabled() {
        for (Class<?> type : new Class<?>[]{FormalAgentPublicationController.class}) {
            var condition = type.getAnnotation(ConditionalOnProperty.class);
            assertNotNull(condition); assertFalse(condition.matchIfMissing()); assertEquals("true", condition.havingValue());
            assertArrayEquals(new String[]{"enabled"}, condition.name());
        }
        assertNull(FormalAgentPublicationFeatureAvailabilityController.class.getAnnotation(ConditionalOnProperty.class));
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
        var missing = (ObjectNode) valid.deepCopy(); ((ObjectNode) missing.required("features")).remove("publication_routes");
        assertThrows(SchemaRejection.class, () -> service.validate(missing));
        var wrong = (ObjectNode) valid.deepCopy(); ((ObjectNode) wrong.required("features")).put("publication_routes", "true");
        assertThrows(SchemaRejection.class, () -> service.validate(wrong));
        var grant = (ObjectNode) valid.deepCopy(); ((ObjectNode) grant.required("features")).put("rule.approve", true);
        assertThrows(SchemaRejection.class, () -> service.validate(grant));
    }
}
