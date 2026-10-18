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

class DocumentRevisionDirectoryFeatureAvailabilityTest {
    private static final String TENANT = "00000000-0000-4000-8000-000000000001";
    private static final String CONTEXT_ID = "00000000-0000-4000-8000-000000000002";
    private final EnterpriseAccess access = mock(EnterpriseAccess.class);
    private final ApplicationSessionIdentity.Verified subject = mock(ApplicationSessionIdentity.Verified.class);
    private final BusinessSessionProtocol.Context context = new BusinessSessionProtocol.Context(TENANT, "7", "2", "5");

    @SuppressWarnings("unchecked")
    private DocumentRevisionDirectoryFeatureAvailability service(ListableBeanFactory beans) throws Exception {
        var admission = new EnterpriseAccess.Admission(subject, CONTEXT_ID, context, new TenantWatermarks(BigInteger.TWO, BigInteger.valueOf(5)));
        when(access.execute(eq(subject), any())).thenAnswer(call -> ((Function<EnterpriseAccess.Admission, Object>) call.getArgument(1)).apply(admission));
        if(beans instanceof DefaultListableBeanFactory factory)factory.registerSingleton("directoryPolicy",new DocumentRevisionDirectoryPolicy(true,10,20,10,20,60));
        return new DocumentRevisionDirectoryFeatureAvailability(access, beans, new PermissionSchema());
    }

    @Test void absentControllerReportsOneFalseFlagAndNoGrantFields() throws Exception {
        var view = service(new DefaultListableBeanFactory()).read(subject, "7");
        assertEquals(4, view.size()); assertEquals(1, view.required("features").size());
        assertFalse(view.required("features").required("document_history").booleanValue());
        assertEquals(TENANT, view.required("current_context").required("tenant_id").textValue());
        assertTrue(view.required("maximum_page_size").isNull()); assertFalse(view.has("allowed_actions")); assertFalse(view.has("permissions")); assertFalse(view.has("configuration"));
        verify(access).execute(eq(subject), any());
    }

    @Test void flagsReflectOnlyRegisteredControllersWithoutInstantiatingThem() throws Exception {
        Class<?>[] types = {DocumentRevisionDirectoryController.class};
        String[] names = {"document_history"};
        for (int mask = 0; mask < 2; mask++) {
            var beans = new DefaultListableBeanFactory();
            for (int i = 0; i < types.length; i++) if ((mask & (1 << i)) != 0) beans.registerBeanDefinition(names[i], new RootBeanDefinition(types[i]));
            var view = service(beans).read(subject, "7");
            for (int i = 0; i < types.length; i++) {
                assertEquals((mask & (1 << i)) != 0, view.required("features").required(names[i]).booleanValue());
                assertFalse(beans.containsSingleton(names[i]));
            }
        }
    }

    @Test void existingEndpointSwitchesStillDefaultDisabled() {
        for (Class<?> type : new Class<?>[]{DocumentRevisionDirectoryController.class}) {
            var condition = type.getAnnotation(ConditionalOnProperty.class);
            assertNotNull(condition); assertFalse(condition.matchIfMissing()); assertEquals("true", condition.havingValue());
            assertArrayEquals(new String[]{"enabled"}, condition.name());
        }
        assertNull(DocumentRevisionDirectoryFeatureAvailabilityController.class.getAnnotation(ConditionalOnProperty.class));
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
        var missing = (ObjectNode) valid.deepCopy(); ((ObjectNode) missing.required("features")).remove("document_history");
        assertThrows(SchemaRejection.class, () -> service.validate(missing));
        var wrong = (ObjectNode) valid.deepCopy(); ((ObjectNode) wrong.required("features")).put("document_history", "true");
        assertThrows(SchemaRejection.class, () -> service.validate(wrong));
        var grant = (ObjectNode) valid.deepCopy(); ((ObjectNode) grant.required("features")).put("rule.approve", true);
        assertThrows(SchemaRejection.class, () -> service.validate(grant));
    }
}
