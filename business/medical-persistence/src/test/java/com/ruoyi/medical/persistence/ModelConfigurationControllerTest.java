package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.SchemaRejection;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.Mockito.*;

/** Endpoint-local servlet boundary tests, without a live HTTP server, database, session or CSRF integration. */
class ModelConfigurationControllerTest {
    private static final String KEY = "model-body-test-key-000001";
    private final PermissionSchema json = new PermissionSchema();
    private final ApplicationSessionIdentity identities = mock(ApplicationSessionIdentity.class);
    private final ModelConfigurationService service = mock(ModelConfigurationService.class);
    private final ModelConfigurationController controller = new ModelConfigurationController(identities, service, json);
    private final Authentication authentication = mock(Authentication.class);
    private final ApplicationSessionIdentity.Verified subject = mock(ApplicationSessionIdentity.Verified.class);
    ModelConfigurationControllerTest() throws Exception { }

    @Test void declaredOverflowRejectsAfterAuthenticationWithoutOpeningStreamOrCallingService() throws Exception {
        for (boolean simulation : List.of(false, true)) {
            var request = request(headers()); when(request.getContentLengthLong()).thenReturn(65_537L);
            when(identities.requireCurrent(request, authentication)).thenReturn(subject);
            assertEquals(ErrorCode.FILE_TOO_LARGE, assertThrows(TenantAccessException.class, () -> save(simulation, request, null)).code());
            var order = inOrder(identities, request); order.verify(identities).requireCurrent(request, authentication); order.verify(request).getContentLengthLong();
            verify(request, never()).getInputStream();
        }
        verifyNoInteractions(service);
    }
    @Test void chunkedAndUnderreportedBodiesReadAtMostLimitPlusOneBytes() throws Exception {
        for (boolean simulation : List.of(false, true)) for (long declared : List.of(-1L, 1L, 65_536L)) {
            var request = request(headers()); when(request.getContentLengthLong()).thenReturn(declared);
            when(identities.requireCurrent(request, authentication)).thenReturn(subject);
            var reads = new AtomicInteger(); when(request.getInputStream()).thenReturn(infinite(reads));
            assertEquals(ErrorCode.FILE_TOO_LARGE, assertThrows(TenantAccessException.class, () -> save(simulation, request, null)).code());
            assertEquals(65_537, reads.get());
        }
        verifyNoInteractions(service);
    }
    @Test void boundedRequestBytesRemainExactAndReachOnlyTheSelectedServiceAfterIdentity() throws Exception {
        for (boolean simulation : List.of(false, true)) for (int size : List.of(2, 65_536)) {
            byte[] bytes = new byte[size]; java.util.Arrays.fill(bytes, (byte)' '); bytes[0]='{'; bytes[1]='}';
            var request = request(headers()); when(request.getContentLengthLong()).thenReturn(-1L); when(request.getInputStream()).thenReturn(stream(bytes));
            when(identities.requireCurrent(request, authentication)).thenReturn(subject);
            var envelope = json.object().put("request_id", SimulationModelConfigurationFixtures.CASE); var response = mock(HttpServletResponse.class);
            if (simulation) when(service.saveSimulation(eq(subject),eq("1"),eq(KEY),aryEq(bytes))).thenReturn(envelope);
            else when(service.save(eq(subject),eq("1"),eq(KEY),aryEq(bytes))).thenReturn(envelope);
            assertSame(envelope,save(simulation,request,response));
            var order=inOrder(identities,request,service); order.verify(identities).requireCurrent(request,authentication);
            order.verify(request).getContentLengthLong(); order.verify(request).getInputStream();
            if(simulation) order.verify(service).saveSimulation(eq(subject),eq("1"),eq(KEY),aryEq(bytes));
            else order.verify(service).save(eq(subject),eq("1"),eq(KEY),aryEq(bytes));
            verify(response).setHeader("Cache-Control","no-store"); verify(response).setHeader("X-Schema-Version","1.0.0");
            verify(response).setHeader("X-Request-Id",SimulationModelConfigurationFixtures.CASE);
        }
        verify(service,times(2)).save(eq(subject),eq("1"),eq(KEY),any(byte[].class));
        verify(service,times(2)).saveSimulation(eq(subject),eq("1"),eq(KEY),any(byte[].class));
    }
    @Test void failedAuthenticationNeverReadsEvenDeclaredOversizedBody() throws Exception {
        for(boolean simulation:List.of(false,true)) {
            var request=request(headers()); when(request.getContentLengthLong()).thenReturn(Long.MAX_VALUE);
            when(identities.requireCurrent(request,authentication)).thenThrow(new AuthenticationCredentialsNotFoundException("required"));
            assertThrows(AuthenticationCredentialsNotFoundException.class,()->save(simulation,request,null));
            verify(request,never()).getContentLengthLong(); verify(request,never()).getInputStream();
        }
        verifyNoInteractions(service);
    }
    @Test void missingDuplicateOrMismatchedRequiredHeadersFailBeforeAuthenticationOrRead() throws Exception {
        for(boolean simulation:List.of(false,true)) for(String name:headers().keySet()) {
            for(List<String> supplied:List.of(List.<String>of(),List.of(headers().get(name).get(0),headers().get(name).get(0)),List.of("wrong-value"))) {
                var values=new HashMap<>(headers()); values.put(name,supplied); var request=request(values);
                assertEquals(ErrorCode.SCHEMA_INVALID,assertThrows(SchemaRejection.class,()->save(simulation,request,null)).code());
                verify(request,never()).getInputStream(); verify(request,never()).getContentLengthLong();
            }
        }
        verifyNoInteractions(identities,service);
    }
    @Test void fixedReadEndpointsAlsoRejectMissingOrDuplicateVersionAndContextHeaders() throws Exception {
        for(boolean simulation:List.of(false,true)) for(String name:List.of("X-Schema-Version","X-Context-Revision")) {
            for(List<String> supplied:List.of(List.<String>of(),List.of(headers().get(name).get(0),headers().get(name).get(0)))) {
                var values=new HashMap<>(headers()); values.put(name,supplied); var request=request(values);
                assertEquals(ErrorCode.SCHEMA_INVALID,assertThrows(SchemaRejection.class,()-> {
                    if(simulation) controller.readSimulation(request,null,authentication,SimulationModelConfigurationFixtures.CASE,"1.0.0","1");
                    else controller.read(request,null,authentication,SimulationModelConfigurationFixtures.CASE,"1.0.0","1");
                }).code());
            }
        }
        verifyNoInteractions(identities,service);
    }
    @Test void overflowMapsToExplicit413AndExistingDenialStatusesStayUnchanged() throws Exception {
        for(var expected:Map.of(ErrorCode.FILE_TOO_LARGE,413,ErrorCode.ACCESS_DENIED,403,ErrorCode.REVISION_CONFLICT,409).entrySet()) {
            var output=new Output();controller.denied(new TenantAccessException(expected.getKey()),output.response());
            assertEquals(expected.getValue(),output.status);var body=new JsonMapper().readTree(output.bytes.toByteArray());
            assertEquals(expected.getKey().name(),body.required("code").textValue());assertEquals("no-store",output.headers.get("Cache-Control"));
        }
    }
    @Test void mvcDoesNotBufferRequestBodiesBeforeEitherControllerMethod() throws Exception {
        for(String name:List.of("save","saveSimulation")) {
            var method=ModelConfigurationController.class.getMethod(name,HttpServletRequest.class,HttpServletResponse.class,Authentication.class,String.class,String.class,String.class);
            for(var parameter:method.getParameters()) {
                assertFalse(parameter.isAnnotationPresent(org.springframework.web.bind.annotation.RequestBody.class));assertNotEquals(byte[].class,parameter.getType());
            }
            assertEquals(org.springframework.http.HttpStatus.CREATED,method.getAnnotation(org.springframework.web.bind.annotation.ResponseStatus.class).value());
        }
        assertEquals(65_536,ModelConfigurationSchema.MAXIMUM_REQUEST_BYTES);
    }
    private JsonNode save(boolean simulation,HttpServletRequest request,HttpServletResponse response) throws IOException {
        return simulation ? controller.saveSimulation(request,response,authentication,"1.0.0","1",KEY)
                : controller.save(request,response,authentication,"1.0.0","1",KEY);
    }
    private static Map<String,List<String>> headers() {return Map.of("X-Schema-Version",List.of("1.0.0"),"X-Context-Revision",List.of("1"),"Idempotency-Key",List.of(KEY));}
    private static HttpServletRequest request(Map<String,List<String>> headers) {
        var request=mock(HttpServletRequest.class);when(request.getHeaders(anyString())).thenAnswer(call->Collections.enumeration(headers.getOrDefault(call.getArgument(0),List.of())));return request;
    }
    private static ServletInputStream infinite(AtomicInteger reads) {return new ServletInputStream() {
        @Override public int read(){reads.incrementAndGet();return 'x';}
        @Override public boolean isFinished(){return false;} @Override public boolean isReady(){return true;}
        @Override public void setReadListener(ReadListener listener){throw new UnsupportedOperationException();}
    };}
    private static ServletInputStream stream(byte[] bytes) {
        var input=new ByteArrayInputStream(bytes);return new ServletInputStream() {
            @Override public int read(){return input.read();}
            @Override public boolean isFinished(){return input.available()==0;} @Override public boolean isReady(){return true;}
            @Override public void setReadListener(ReadListener listener){throw new UnsupportedOperationException();}
        };
    }
    private static final class Output {
        int status;final ByteArrayOutputStream bytes=new ByteArrayOutputStream();final Map<String,String> headers=new HashMap<>();
        HttpServletResponse response() throws IOException {
            var response=mock(HttpServletResponse.class);doAnswer(call->{status=call.getArgument(0);return null;}).when(response).setStatus(anyInt());
            doAnswer(call->{headers.put(call.getArgument(0),call.getArgument(1));return null;}).when(response).setHeader(anyString(),anyString());
            when(response.getOutputStream()).thenReturn(new ServletOutputStream(){
                @Override public void write(int value){bytes.write(value);}@Override public boolean isReady(){return true;}
                @Override public void setWriteListener(WriteListener listener){throw new UnsupportedOperationException();}
            });return response;
        }
    }
}
