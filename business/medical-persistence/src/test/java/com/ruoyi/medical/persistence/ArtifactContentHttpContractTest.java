package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.SchemaRejection;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.junit.jupiter.api.Assertions.*;

/** Hermetic contract tests; these do not claim authenticated database or production HTTP integration. */
class ArtifactContentHttpContractTest {
    @Test void byteLimitsAndCursorAreExplicitAndBounded() {
        assertEquals(65536, ArtifactContentController.query(Map.of()).limitBytes());
        for (String limit : List.of("1", "100", "65536", "1048576"))
            assertEquals(Integer.parseInt(limit), ArtifactContentController.query(Map.of("limit", new String[]{limit})).limitBytes());
        assertEquals("opaque", ArtifactContentController.query(Map.of("cursor", new String[]{"opaque"})).cursor());
        for (String invalid : List.of("", "0", "-1", "01", "+1", "1.0", "1e3", "1048577", "99999999999999", " 1"))
            reject(() -> ArtifactContentController.query(Map.of("limit", new String[]{invalid})));
        for (String name : List.of("tenant_id", "project_id", "schema_ref", "resource_id", "purpose", "object_key", "encoding"))
            reject(() -> ArtifactContentController.query(Map.of(name, new String[]{"injected"})));
        reject(() -> ArtifactContentController.query(Map.of("limit", new String[]{"1", "2"})));
        reject(() -> ArtifactContentController.query(Map.of("cursor", new String[]{"a", "b"})));
        reject(() -> ArtifactContentController.query(Map.of("cursor", new String[]{""})));
        reject(() -> ArtifactContentController.query(Map.of("cursor", new String[]{"a".repeat(8193)})));
        reject(() -> ArtifactContentController.query(Map.of("limit", new String[]{})));
        reject(() -> ArtifactContentController.query(Map.of("limit", new String[]{null})));
    }

    @Test void invalidHeadersAndIdsFailBeforeIdentityOrStorageAccess() {
        var controller = new ArtifactContentController(null, null);
        var valid = Map.of("X-Schema-Version", List.of("1.0.0"), "X-Context-Revision", List.of("1"));
        for (String name : valid.keySet()) {
            var missing = new HashMap<>(valid); missing.remove(name);
            reject(() -> controller.content(request(missing), null, null, UUID.randomUUID().toString(), "1.0.0", "1"));
            var duplicate = new HashMap<>(valid); duplicate.put(name, List.of(valid.get(name).get(0), valid.get(name).get(0)));
            reject(() -> controller.content(request(duplicate), null, null, UUID.randomUUID().toString(), "1.0.0", "1"));
        }
        assertEquals(ErrorCode.SCHEMA_UNSUPPORTED, assertThrows(SchemaRejection.class,
            () -> controller.content(request(valid), null, null, UUID.randomUUID().toString(), "2.0.0", "1")).code());
        reject(() -> controller.content(request(valid), null, null, "not-a-uuid", "1.0.0", "1"));
        reject(() -> controller.content(request(valid), null, null, UUID.randomUUID().toString(), "1.0.0", "18446744073709551616"));
    }

    @Test void absentOrIncompleteDedicatedKeyNeverEnablesReads() {
        var absent = new ArtifactContentReadPolicy(null, null, null, 0, 0, 0, 0, 0, 0);
        assertEquals(ErrorCode.DEPENDENCY_UNAVAILABLE, assertThrows(TenantAccessException.class, absent::requireConfigured).code());
        String id = UUID.randomUUID().toString(), key = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]);
        var valid = new ArtifactContentReadPolicy(id, key, Duration.ofMinutes(5), 65536, 1048576, 8, 524288, 262144, 2);
        valid.requireLimit(1); valid.requireLimit(65536);
        assertEquals(ErrorCode.FILE_TOO_LARGE, assertThrows(TenantAccessException.class, () -> valid.requireLimit(65537)).code());
        assertThrows(RuntimeException.class, () -> new ArtifactContentReadPolicy(null, key, Duration.ofMinutes(5), 65536, 1048576, 8, 524288, 262144, 2));
        assertThrows(IllegalArgumentException.class, () -> new ArtifactContentReadPolicy(id, "AAAA", Duration.ofMinutes(5), 65536, 1048576, 8, 524288, 262144, 2));
        assertThrows(IllegalArgumentException.class, () -> new ArtifactContentReadPolicy(id, key, Duration.ZERO, 65536, 1048576, 8, 524288, 262144, 2));
        assertThrows(IllegalArgumentException.class, () -> new ArtifactContentReadPolicy(id, key, Duration.ofNanos(1), 65536, 1048576, 8, 524288, 262144, 2));
        assertThrows(IllegalArgumentException.class, () -> new ArtifactContentReadPolicy(id, key, Duration.ofMinutes(5), 65535, 1048576, 8, 524288, 262144, 2));
        assertThrows(IllegalArgumentException.class, () -> new ArtifactContentReadPolicy(id, key, Duration.ofMinutes(5), 1048577, 1048576, 8, 524288, 262144, 2));
    }

    @Test void documentLookupIsTenantGuardedAndCorrelatesTheFixedDomainRevision() throws Exception {
        String name = "com.ruoyi.medical.persistence.mapper.DocumentReadMapper.artifactOwner";
        assertEquals(ErrorCode.ACCESS_DENIED, assertThrows(TenantAccessException.class, () -> TenantScope.requireSqlAccess(name)).code());
        assertEquals(ErrorCode.ACCESS_DENIED, assertThrows(TenantAccessException.class,
            () -> TenantScope.requireSubjectParameter(name, Map.of("userId", UUID.randomUUID().toString()))).code());
        var configuration = new MedicalPersistenceConfiguration().medicalSqlSessionFactory(new DriverManagerDataSource()).getConfiguration();
        var statement = configuration.getMappedStatement(name);
        assertFalse(statement.isUseCache()); assertTrue(statement.isFlushCacheRequired());
        String sql = statement.getBoundSql(Map.of("artifactId", UUID.randomUUID().toString())).getSql().replaceAll("\\s+", " ");
        assertTrue(sql.contains("m.document_revision=a.document_revision"));
        assertTrue(sql.contains("m.body_artifact_id=a.artifact_id"));
        assertTrue(sql.contains("b.resource_kind='DOCUMENT' AND b.resource_id=m.document_id"));
        assertTrue(sql.contains("m.tenant_id=a.tenant_id AND m.project_id=a.project_id"));
        assertTrue(sql.contains("b.tenant_id=a.tenant_id AND b.project_id=a.project_id"));
        assertTrue(sql.contains("FOR UPDATE"));
        assertEquals(6, statement.getResultMaps().get(0).getConstructorResultMappings().size());
    }

    @Test void failureResponsesAreNonCachingSchemaBoundAndContainNoObjectDetails() throws Exception {
        var controller = new ArtifactContentController(null, null);
        for (var failure : Map.of(ErrorCode.ACCESS_DENIED, 403, ErrorCode.FILE_TOO_LARGE, 413,
                ErrorCode.DEPENDENCY_UNAVAILABLE, 422, ErrorCode.CURSOR_INVALID, 409, ErrorCode.LEASE_STALE, 409).entrySet()) {
            var output = new Response(); controller.denied(new TenantAccessException(failure.getKey()), output.servlet());
            assertEquals(failure.getValue(), output.status); assertEquals("no-store", output.headers.get("Cache-Control"));
            assertEquals("1.0.0", output.headers.get("X-Schema-Version"));
            var body = new JsonMapper().readTree(output.bytes.toByteArray());
            assertEquals(failure.getKey().name(), body.required("code").textValue());
            assertEquals(output.headers.get("X-Request-Id"), body.required("request_id").textValue());
            assertFalse(body.toString().contains("object_key")); assertFalse(body.toString().contains("credential"));
        }
        var wrapped = new Response();
        controller.failed(new RuntimeException("private/object/path", new TenantAccessException(ErrorCode.ACCESS_DENIED)), wrapped.servlet());
        assertEquals(403, wrapped.status); assertFalse(wrapped.bytes.toString().contains("private/object/path"));
        var unexpected = new Response(); controller.failed(new IOException("sensitive SQL or provider response"), unexpected.servlet());
        assertEquals(500, unexpected.status); assertEquals("no-store", unexpected.headers.get("Cache-Control"));
        assertFalse(unexpected.bytes.toString().contains("sensitive"));
    }

    private static HttpServletRequest request(Map<String,List<String>> headers) {
        return (HttpServletRequest) Proxy.newProxyInstance(HttpServletRequest.class.getClassLoader(), new Class<?>[]{HttpServletRequest.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "getHeaders" -> Collections.enumeration(headers.getOrDefault(args[0], List.of()));
                case "getParameterMap" -> Map.of();
                default -> throw new AssertionError("Unexpected request call: " + method.getName());
            });
    }

    private static void reject(org.junit.jupiter.api.function.Executable action) {
        assertEquals(ErrorCode.SCHEMA_INVALID, assertThrows(SchemaRejection.class, action).code());
    }

    private static final class Response {
        int status;
        final Map<String,String> headers = new HashMap<>();
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        HttpServletResponse servlet() {
            return (HttpServletResponse) Proxy.newProxyInstance(HttpServletResponse.class.getClassLoader(), new Class<?>[]{HttpServletResponse.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "setStatus" -> status = (Integer) args[0];
                        case "setHeader" -> headers.put((String) args[0], (String) args[1]);
                        case "setCharacterEncoding", "setContentType" -> { }
                        case "getOutputStream" -> { return new ServletOutputStream() {
                            @Override public void write(int value) { bytes.write(value); }
                            @Override public boolean isReady() { return true; }
                            @Override public void setWriteListener(WriteListener listener) { throw new UnsupportedOperationException(); }
                        }; }
                        default -> throw new AssertionError("Unexpected response call: " + method.getName());
                    }
                    return null;
                });
        }
    }
}
