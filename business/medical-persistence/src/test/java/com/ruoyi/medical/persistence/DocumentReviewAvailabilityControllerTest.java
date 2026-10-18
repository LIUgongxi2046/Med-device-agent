package com.ruoyi.medical.persistence;

import com.ruoyi.medical.identity.*;
import java.math.BigInteger;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mock.web.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class DocumentReviewAvailabilityControllerTest {
    @Test @SuppressWarnings("unchecked") void actualRegistrationOnlyNoConfigurationOrPermissionLeak() throws Exception {
        var identities=mock(ApplicationSessionIdentity.class);var access=mock(EnterpriseAccess.class);var beans=mock(ListableBeanFactory.class);
        var json=new PermissionSchema();var schema=new DocumentReviewReturnSchema(json);var subject=mock(ApplicationSessionIdentity.Verified.class);
        var context=new BusinessSessionProtocol.Context(DocumentReviewReturnSchemaTest.id(1),"2","3","4");
        when(identities.requireCurrent(any(),any())).thenReturn(subject);
        when(access.execute(eq(subject),any())).thenAnswer(c->((Function<EnterpriseAccess.Admission,Object>)c.getArgument(1))
                .apply(new EnterpriseAccess.Admission(subject,DocumentReviewReturnSchemaTest.id(2),context,new TenantWatermarks(BigInteger.valueOf(3),BigInteger.valueOf(4)))));
        var controller=new DocumentReviewAvailabilityController(identities,access,beans,json,schema);
        for(boolean enabled:new boolean[]{false,true}) {
            when(beans.getBeanNamesForType(DocumentReviewReturnController.class,false,false)).thenReturn(enabled?new String[]{"review"}:new String[0]);
            var request=new MockHttpServletRequest();request.addHeader("X-Schema-Version","1.0.0");request.addHeader("X-Context-Revision","2");
            var response=new MockHttpServletResponse();var data=controller.read(request,response,null).required("data");
            assertEquals(enabled,data.required("review_return_enabled").booleanValue());assertEquals(3,data.size());
            assertEquals("no-store",response.getHeader("Cache-Control"));assertFalse(data.has("permissions"));assertFalse(data.has("configuration"));
        }
        var condition=DocumentReviewReturnController.class.getAnnotation(ConditionalOnProperty.class);
        assertFalse(condition.matchIfMissing());assertEquals("true",condition.havingValue());assertEquals("medical.review-return",condition.prefix());
    }
    @Test @SuppressWarnings("unchecked") void staleContextNeverInspectsAvailability() throws Exception {
        var identities=mock(ApplicationSessionIdentity.class);var access=mock(EnterpriseAccess.class);var beans=mock(ListableBeanFactory.class);
        var json=new PermissionSchema();var subject=mock(ApplicationSessionIdentity.Verified.class);when(identities.requireCurrent(any(),any())).thenReturn(subject);
        when(access.execute(eq(subject),any())).thenAnswer(c->((Function<EnterpriseAccess.Admission,Object>)c.getArgument(1)).apply(new EnterpriseAccess.Admission(subject,
                DocumentReviewReturnSchemaTest.id(2),new BusinessSessionProtocol.Context(DocumentReviewReturnSchemaTest.id(1),"2","1","1"),new TenantWatermarks(BigInteger.ONE,BigInteger.ONE))));
        var request=new MockHttpServletRequest();request.addHeader("X-Schema-Version","1.0.0");request.addHeader("X-Context-Revision","1");
        var controller=new DocumentReviewAvailabilityController(identities,access,beans,json,new DocumentReviewReturnSchema(json));
        assertThrows(TenantAccessException.class,()->controller.read(request,new MockHttpServletResponse(),null));verifyNoInteractions(beans);
    }
}
