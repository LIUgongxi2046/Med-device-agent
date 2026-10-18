package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.Mockito.*;

/** Servlet boundary only; identity and service doubles do not represent live auth, CSRF or database tests. */
class DocumentTechnicalCheckControllerTest {
    static final String DOCUMENT="00000000-0000-4000-8000-000000000001",CHECK="00000000-0000-4000-8000-000000000002",KEY="technical-check-key-0001";
    final ApplicationSessionIdentity identities=mock(ApplicationSessionIdentity.class);
    final ApplicationSessionIdentity.Verified subject=mock(ApplicationSessionIdentity.Verified.class);
    final DocumentTechnicalCheckService service=mock(DocumentTechnicalCheckService.class);
    final Authentication authentication=mock(Authentication.class);
    final HttpServletResponse response=mock(HttpServletResponse.class);
    final DocumentTechnicalCheckPolicy policy=new DocumentTechnicalCheckPolicy(true,32,16,8,64,60);
    final DocumentTechnicalCheckController controller=new DocumentTechnicalCheckController(identities,service,new PermissionSchema(),policy);
    DocumentTechnicalCheckControllerTest()throws Exception{}

    @Test void exactBoundedBytesReachServiceAfterIdentityWithCreatedEnvelope()throws Exception{
        byte[] bytes=new byte[32];Arrays.fill(bytes,(byte)' ');bytes[0]='{';bytes[1]='}';
        var request=request(headers());when(request.getContentLengthLong()).thenReturn(-1L);when(request.getInputStream()).thenReturn(stream(bytes));
        when(identities.requireCurrent(request,authentication)).thenReturn(subject);
        var receipt=JsonNodeFactory.instance.objectNode().put("check_id",CHECK);
        when(service.create(eq(subject),eq("1"),eq(DOCUMENT),eq(KEY),aryEq(bytes))).thenReturn(receipt);
        var value=controller.create(request,response,authentication,DOCUMENT);assertEquals(receipt,value.required("data"));
        var order=inOrder(identities,request,service);order.verify(identities).requireCurrent(request,authentication);order.verify(request).getContentLengthLong();order.verify(request).getInputStream();
        order.verify(service).create(eq(subject),eq("1"),eq(DOCUMENT),eq(KEY),aryEq(bytes));
        verify(response).setStatus(201);verify(response).setHeader("Cache-Control","no-store");verify(response).setHeader("X-Schema-Version","1.0.0");verify(response).setHeader("X-Resource-Revision","1");
    }
    @Test void declaredAndStreamingOverflowDoNotReachService()throws Exception{
        var declared=request(headers());when(declared.getContentLengthLong()).thenReturn(33L);when(identities.requireCurrent(declared,authentication)).thenReturn(subject);
        assertEquals(ErrorCode.FILE_TOO_LARGE,assertThrows(TenantAccessException.class,()->controller.create(declared,response,authentication,DOCUMENT)).code());verify(declared,never()).getInputStream();
        var chunked=request(headers());when(chunked.getContentLengthLong()).thenReturn(-1L);when(identities.requireCurrent(chunked,authentication)).thenReturn(subject);
        var count=new AtomicInteger();when(chunked.getInputStream()).thenReturn(new ServletInputStream(){
            public int read(){count.incrementAndGet();return 'x';}public boolean isFinished(){return false;}public boolean isReady(){return true;}public void setReadListener(ReadListener listener){throw new UnsupportedOperationException();}
        });
        assertEquals(ErrorCode.FILE_TOO_LARGE,assertThrows(TenantAccessException.class,()->controller.create(chunked,response,authentication,DOCUMENT)).code());assertEquals(33,count.get());verifyNoInteractions(service);
    }
    @Test void authenticationFailureDoesNotConsumeBody()throws Exception{
        var request=request(headers());when(identities.requireCurrent(request,authentication)).thenThrow(new AuthenticationCredentialsNotFoundException("required"));
        assertThrows(AuthenticationCredentialsNotFoundException.class,()->controller.create(request,response,authentication,DOCUMENT));
        verify(request,never()).getInputStream();verify(request,never()).getContentLengthLong();verifyNoInteractions(service);
    }
    @Test void requiredHeadersAndNoQueryAreEnforcedBeforeIdentityAndBody()throws Exception{
        for(String key:headers().keySet())for(var supplied:List.of(List.<String>of(),List.of(headers().get(key).get(0),headers().get(key).get(0)))){
            var values=new HashMap<>(headers());values.put(key,supplied);var request=request(values);
            assertThrows(SchemaRejection.class,()->controller.create(request,response,authentication,DOCUMENT));verify(request,never()).getInputStream();
        }
        var wrong=new HashMap<>(headers());wrong.put("X-Schema-Version",List.of("2.0.0"));var request=request(wrong);
        assertEquals(ErrorCode.SCHEMA_UNSUPPORTED,assertThrows(SchemaRejection.class,()->controller.create(request,response,authentication,DOCUMENT)).code());
        var query=request(headers());when(query.getQueryString()).thenReturn("x=1");assertThrows(SchemaRejection.class,()->controller.read(query,response,authentication,CHECK));
        verifyNoInteractions(identities,service);
    }
    @Test void readsAndRecoveryUseTheirExactTargetsWithoutReadingRequestBody()throws Exception{
        var request=request(Map.of("X-Schema-Version",List.of("1.0.0"),"X-Context-Revision",List.of("1")));when(identities.requireCurrent(request,authentication)).thenReturn(subject);
        var read=JsonNodeFactory.instance.objectNode().put("currentness","CURRENT");var recovery=JsonNodeFactory.instance.objectNode().put("state","NOT_FOUND");
        when(service.read(subject,"1",CHECK)).thenReturn(read);when(service.recovery(subject,"1",DOCUMENT,KEY)).thenReturn(recovery);
        assertEquals(read,controller.read(request,response,authentication,CHECK).required("data"));
        assertEquals(recovery,controller.recovery(request,response,authentication,DOCUMENT,KEY).required("data"));
        verify(service).read(subject,"1",CHECK);verify(service).recovery(subject,"1",DOCUMENT,KEY);verify(request,never()).getInputStream();
        assertThrows(RuntimeException.class,()->controller.recovery(request,response,authentication,DOCUMENT,"bad"));verifyNoMoreInteractions(service);
    }
    @Test void enabledConfigurationAndStreamingControllerSignatureRemainExplicit()throws Exception{
        var condition=DocumentTechnicalCheckController.class.getAnnotation(org.springframework.boot.autoconfigure.condition.ConditionalOnProperty.class);
        assertEquals("medical.document-technical-check",condition.prefix());assertEquals("true",condition.havingValue());assertFalse(condition.matchIfMissing());
        var method=DocumentTechnicalCheckController.class.getMethod("create",HttpServletRequest.class,HttpServletResponse.class,Authentication.class,String.class);
        for(var parameter:method.getParameters()){assertFalse(parameter.isAnnotationPresent(org.springframework.web.bind.annotation.RequestBody.class));assertNotEquals(byte[].class,parameter.getType());}
        assertThrows(IllegalArgumentException.class,()->new DocumentTechnicalCheckPolicy(true,null,16,8,64,60));
    }
    static Map<String,List<String>> headers(){return Map.of("X-Schema-Version",List.of("1.0.0"),"X-Context-Revision",List.of("1"),"Idempotency-Key",List.of(KEY),"X-CSRF-TOKEN",List.of("csrf-test"));}
    static HttpServletRequest request(Map<String,List<String>> headers){var r=mock(HttpServletRequest.class);when(r.getHeaders(anyString())).thenAnswer(call->Collections.enumeration(headers.getOrDefault(call.getArgument(0),List.of())));return r;}
    static ServletInputStream stream(byte[] bytes){var input=new ByteArrayInputStream(bytes);return new ServletInputStream(){public int read(){return input.read();}public boolean isFinished(){return input.available()==0;}public boolean isReady(){return true;}public void setReadListener(ReadListener listener){throw new UnsupportedOperationException();}};}
}
