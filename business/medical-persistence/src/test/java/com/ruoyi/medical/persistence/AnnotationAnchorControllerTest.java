package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.SchemaRejection;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.AdditionalMatchers.aryEq;

/** Direct servlet contract checks, not live HTTP/session/CSRF integration. */
class AnnotationAnchorControllerTest {
    private final PermissionSchema json = new PermissionSchema();
    AnnotationAnchorControllerTest() throws Exception { }
    @Test void invalidHeadersIdsAndAnyPaginationOrInjectedQueryFailBeforeIdentity() throws Exception {
        var identities = mock(ApplicationSessionIdentity.class); var service = mock(AnnotationAnchorService.class);
        var controller = new AnnotationAnchorController(identities, service, json);
        var valid = Map.of("X-Schema-Version", List.of("1.0.0"), "X-Context-Revision", List.of("1"));
        for (String name : valid.keySet()) {
            var missing = new HashMap<>(valid); missing.remove(name);
            reject(() -> controller.list(request(missing, null), null, null, AnnotationAnchorServiceTest.ANNOTATION, "1.0.0", "1"));
            var duplicate = new HashMap<>(valid); duplicate.put(name, List.of("1", "1"));
            reject(() -> controller.list(request(duplicate, null), null, null, AnnotationAnchorServiceTest.ANNOTATION, "1.0.0", "1"));
        }
        for (String query : List.of("", "page=1", "limit=1", "cursor=anything", "tenant_id=other", "document_revision=other"))
            reject(() -> controller.list(request(valid, query), null, null, AnnotationAnchorServiceTest.ANNOTATION, "1.0.0", "1"));
        reject(() -> controller.list(request(valid, null), null, null, "not-an-id", "1.0.0", "1"));
        assertEquals(ErrorCode.SCHEMA_UNSUPPORTED, assertThrows(SchemaRejection.class,
                () -> controller.list(request(valid, null), null, null, AnnotationAnchorServiceTest.ANNOTATION, "2.0.0", "1")).code());
        reject(() -> controller.append(request(valid, null), null, null, AnnotationAnchorServiceTest.ANNOTATION, "1.0.0", "1", "test-key-000000001"));
        verifyNoInteractions(identities, service);
    }
    @Test void actualVerifiedIdentityAndOriginalRequestReachServiceAndResponsesDoNotCache() throws Exception {
        var identities = mock(ApplicationSessionIdentity.class); var service = mock(AnnotationAnchorService.class);
        var controller = new AnnotationAnchorController(identities, service, json); var authentication = mock(Authentication.class);
        var subject = mock(ApplicationSessionIdentity.Verified.class); var headers = new HashMap<String,List<String>>();
        headers.put("X-Schema-Version", List.of("1.0.0")); headers.put("X-Context-Revision", List.of("1")); headers.put("Idempotency-Key", List.of("test-key-000000001"));
        var request = request(headers, null); when(identities.requireCurrent(request, authentication)).thenReturn(subject);
        var envelope = json.object().put("request_id", AnnotationAnchorServiceTest.id(10)); var response = mock(HttpServletResponse.class);
        when(service.history(subject, "1", AnnotationAnchorServiceTest.ANNOTATION)).thenReturn(envelope);
        assertSame(envelope, controller.list(request, response, authentication, AnnotationAnchorServiceTest.ANNOTATION, "1.0.0", "1"));
        byte[] body = "exact original body".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        when(request.getInputStream()).thenReturn(stream(body));
        when(service.reanchor(eq(subject), eq("1"), eq("test-key-000000001"), eq(AnnotationAnchorServiceTest.ANNOTATION), aryEq(body))).thenReturn(envelope);
        var saved = controller.append(request, response, authentication, AnnotationAnchorServiceTest.ANNOTATION, "1.0.0", "1", "test-key-000000001");
        assertEquals(201, saved.getStatusCode().value()); assertSame(envelope, saved.getBody());
        verify(response, times(2)).setHeader("Cache-Control", "no-store"); verify(response, times(2)).setHeader("X-Content-Type-Options", "nosniff");
        verify(identities, times(2)).requireCurrent(request, authentication);
    }
    @Test void safeErrorEnvelopePreservesMeaningWithoutFreeTextOrDatabaseDetails() throws Exception {
        var controller = new AnnotationAnchorController(null, null, json);
        for (var item : Map.of(ErrorCode.ACCESS_DENIED, 403, ErrorCode.SCOPE_STALE, 409, ErrorCode.IDEMPOTENCY_CONFLICT, 409,
                ErrorCode.REVISION_CONFLICT, 409, ErrorCode.CONTEXT_LIMIT, 422, ErrorCode.FILE_TOO_LARGE, 413).entrySet()) {
            var output = new Response(); controller.failed(new RuntimeException("private reply or SQL", new TenantAccessException(item.getKey())), output.servlet());
            assertEquals(item.getValue(), output.status); assertEquals("no-store", output.headers.get("Cache-Control"));
            var body = new JsonMapper().readTree(output.bytes.toByteArray()); assertEquals(item.getKey().name(), body.required("code").asText());
            assertFalse(body.toString().contains("private"));
        }
        var output = new Response(); controller.failed(new IOException("source secret or SQL"), output.servlet());
        assertEquals(500, output.status); assertFalse(output.bytes.toString().contains("source secret"));
    }
    @Test void extensionDoesNotReplaceFrozenLegacyEndpoint() throws Exception {
        var method = AnnotationAnchorController.class.getMethod("list", HttpServletRequest.class, HttpServletResponse.class, Authentication.class,
                String.class, String.class, String.class);
        assertArrayEquals(new String[]{"/api/annotation-threads/{id}/anchors"}, method.getAnnotation(org.springframework.web.bind.annotation.GetMapping.class).value());
    }
    @Test void oversizedDeclaredAndChunkedBodiesAreRejectedBeforeServiceAndAreReadOnlyToBound() throws Exception {
        var identities = mock(ApplicationSessionIdentity.class); var service = mock(AnnotationAnchorService.class);
        var controller = new AnnotationAnchorController(identities, service, json); var subject = mock(ApplicationSessionIdentity.Verified.class);
        var headers = Map.of("X-Schema-Version", List.of("1.0.0"), "X-Context-Revision", List.of("1"), "Idempotency-Key", List.of("test-key-000000001"));
        var declared = request(headers, null); when(identities.requireCurrent(eq(declared), isNull())).thenReturn(subject);
        when(declared.getContentLengthLong()).thenReturn(1048577L);
        assertEquals(ErrorCode.FILE_TOO_LARGE, assertThrows(TenantAccessException.class,
                () -> controller.append(declared, null, null, AnnotationAnchorServiceTest.ANNOTATION, "1.0.0", "1", "test-key-000000001")).code());
        verify(declared, never()).getInputStream();
        var chunked = request(headers, null); when(identities.requireCurrent(eq(chunked), isNull())).thenReturn(subject);
        when(chunked.getContentLengthLong()).thenReturn(-1L); var reads = new java.util.concurrent.atomic.AtomicInteger();
        when(chunked.getInputStream()).thenReturn(new jakarta.servlet.ServletInputStream() {
            @Override public int read() { reads.incrementAndGet(); return 'x'; }
            @Override public boolean isFinished() { return false; }
            @Override public boolean isReady() { return true; }
            @Override public void setReadListener(jakarta.servlet.ReadListener listener) { throw new UnsupportedOperationException(); }
        });
        assertEquals(ErrorCode.FILE_TOO_LARGE, assertThrows(TenantAccessException.class,
                () -> controller.append(chunked, null, null, AnnotationAnchorServiceTest.ANNOTATION, "1.0.0", "1", "test-key-000000001")).code());
        assertEquals(1048577, reads.get()); verifyNoInteractions(service);
    }
    @Test void exactlyOneDocumentRevisionQueryIsRequired() throws Exception {
        var identities=mock(ApplicationSessionIdentity.class);var service=mock(AnnotationAnchorService.class);var controller=new AnnotationAnchorController(identities,service,json);
        var headers=Map.of("X-Schema-Version",List.of("1.0.0"),"X-Context-Revision",List.of("1"));
        var missing=request(headers,null);when(missing.getParameterMap()).thenReturn(Map.of());
        reject(()->controller.document(missing,null,null,AnnotationAnchorServiceTest.DOC,"1.0.0","1",null));
        var duplicate=request(headers,"document_revision=x&document_revision=y");when(duplicate.getParameterMap()).thenReturn(Map.of("document_revision",new String[]{"x","y"}));when(duplicate.getParameterValues("document_revision")).thenReturn(new String[]{"x","y"});
        reject(()->controller.document(duplicate,null,null,AnnotationAnchorServiceTest.DOC,"1.0.0","1",AnnotationAnchorServiceTest.NEW));verifyNoInteractions(identities,service);
    }
    private static jakarta.servlet.ServletInputStream stream(byte[] bytes) {
        var input = new java.io.ByteArrayInputStream(bytes);
        return new jakarta.servlet.ServletInputStream() {
            @Override public int read() { return input.read(); }
            @Override public boolean isFinished() { return input.available() == 0; }
            @Override public boolean isReady() { return true; }
            @Override public void setReadListener(jakarta.servlet.ReadListener listener) { throw new UnsupportedOperationException(); }
        };
    }
    private static HttpServletRequest request(Map<String,List<String>> headers, String query) {
        var request = mock(HttpServletRequest.class);
        when(request.getHeaders(anyString())).thenAnswer(call -> Collections.enumeration(headers.getOrDefault(call.getArgument(0), List.of())));
        when(request.getQueryString()).thenReturn(query); return request;
    }
    private static void reject(org.junit.jupiter.api.function.Executable work) { assertEquals(ErrorCode.SCHEMA_INVALID, assertThrows(SchemaRejection.class, work).code()); }
    private static final class Response {
        int status; final Map<String,String> headers = new HashMap<>(); final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        HttpServletResponse servlet() throws IOException {
            var response = mock(HttpServletResponse.class);
            doAnswer(call -> { status = call.getArgument(0); return null; }).when(response).setStatus(anyInt());
            doAnswer(call -> { headers.put(call.getArgument(0), call.getArgument(1)); return null; }).when(response).setHeader(anyString(), anyString());
            when(response.getOutputStream()).thenReturn(new ServletOutputStream() {
                @Override public void write(int value) { bytes.write(value); }
                @Override public boolean isReady() { return true; }
                @Override public void setWriteListener(WriteListener listener) { throw new UnsupportedOperationException(); }
            }); return response;
        }
    }
}
