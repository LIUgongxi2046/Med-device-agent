package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.InterfaceProtocol;
import com.ruoyi.medical.contracts.SchemaRejection;
import com.ruoyi.medical.contracts.WireValues;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.identity.IdentityErrorResponseWriter;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import com.ruoyi.medical.persistence.mapper.SourceUploadMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.util.function.ThrowingSupplier;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("source-lease-native")
public final class SourceCompletionLeaseNative {
    private static final JsonMapper LEASE_JSON = JsonMapper.builder().addModule(new JavaTimeModule())
        .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();
    private final ApplicationContext application;
    private final ApplicationSessionIdentity identities;
    private final EnterpriseAccess access;
    private final SourceUploadService uploads;
    private final SourceUploadSchema schema;
    private final PermissionSchema json;
    private final SourceUploadMapper mapper;
    private final PermissionMapper permissions;
    private final Map<String,Object> first = new ConcurrentHashMap<>(), second = new ConcurrentHashMap<>(), old = new ConcurrentHashMap<>();
    private Object secondStore;

    public SourceCompletionLeaseNative(ApplicationContext application, ApplicationSessionIdentity identities,
            EnterpriseAccess access, SourceUploadService uploads, SourceUploadSchema schema, PermissionSchema json,
            SourceUploadMapper mapper, PermissionMapper permissions) {
        this.application = application; this.identities = identities; this.access = access; this.uploads = uploads;
        this.schema = schema; this.json = json; this.mapper = mapper; this.permissions = permissions;
    }

    @PostMapping(value="/api/source-completion-lease-native", consumes="application/json")
    public synchronized JsonNode execute(HttpServletRequest request, HttpServletResponse response, Authentication authentication, @RequestBody byte[] bytes,
            @RequestHeader("X-Schema-Version") String version, @RequestHeader("X-Context-Revision") String context,
            @RequestHeader("Idempotency-Key") String key) throws Exception {
        if (!"1.0.0".equals(version)) throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED);
        json.headers(context, key);
        var body = schema.readJson(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
        if (body.size() != 3 || !body.has("upload_id") || !body.has("operation_id") || !body.has("action"))
            throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
        String upload = body.required("upload_id").textValue(), operation = body.required("operation_id").textValue();
        WireValues.uuid(upload); WireValues.uuid(operation);
        String requestId = java.util.UUID.randomUUID().toString();
        response.setHeader("Cache-Control", "no-store"); response.setHeader("X-Schema-Version", "1.0.0");
        response.setHeader("X-Request-Id", requestId);
        return access.execute(identities.requireCurrent(request, authentication), admission -> ThrowingSupplier.of(() -> {
            if (!context.equals(admission.context().context_revision())) throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);
            uploads.require(admission, upload, false);
            // 调用真实内部组件；反射允许Red阶段直接验证尚未提供的业务类。
            Class<?> type = Class.forName("com.ruoyi.medical.persistence.SourceCompletionLeaseStore");
            Object firstStore = application.getBean(type);
            if (secondStore == null) secondStore = type.getConstructor(SourceUploadMapper.class, PermissionMapper.class, PermissionSchema.class)
                .newInstance(mapper, permissions, json);
            String identity = admission.context().tenant_id() + "/" + operation;
            Object lease = switch (body.required("action").textValue()) {
                case "CLAIM_A" -> type.getMethod("claim", String.class, String.class).invoke(firstStore, upload, operation);
                case "CLAIM_B" -> type.getMethod("claim", String.class, String.class).invoke(secondStore, upload, operation);
                case "RENEW_A" -> {
                    Object previous = java.util.Objects.requireNonNull(first.get(identity)); old.put(identity, previous);
                    yield type.getMethod("renew", previous.getClass()).invoke(firstStore, previous);
                }
                case "RENEW_B" -> {
                    Object previous = java.util.Objects.requireNonNull(second.get(identity));
                    yield type.getMethod("renew", previous.getClass()).invoke(secondStore, previous);
                }
                case "RENEW_OLD_A" -> {
                    Object previous = java.util.Objects.requireNonNull(old.get(identity));
                    yield type.getMethod("renew", previous.getClass()).invoke(firstStore, previous);
                }
                default -> throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
            };
            if (java.util.Set.of("CLAIM_A", "RENEW_A").contains(body.required("action").textValue())) first.put(identity, lease);
            if (java.util.Set.of("CLAIM_B", "RENEW_B").contains(body.required("action").textValue())) second.put(identity, lease);
            var result = (ObjectNode) LEASE_JSON.valueToTree(lease.getClass().getMethod("row").invoke(lease));
            result.put("recover_existing", (Boolean) lease.getClass().getMethod("recoverExisting").invoke(lease));
            result.put("command_sha256", DigestUtils.sha256Hex((String) lease.getClass().getMethod("command").invoke(lease)));
            return json.tree(new InterfaceProtocol.Envelope("1.0.0", requestId, result.required("revision").asText(), result));
        }).get());
    }

    @ExceptionHandler(InvocationTargetException.class)
    void domain(InvocationTargetException failure, HttpServletResponse response) throws Exception {
        if (!(failure.getCause() instanceof TenantAccessException denial)) throw failure;
        new IdentityErrorResponseWriter().write(response, denial.code() == ErrorCode.ACCESS_DENIED ? 403 : 409, denial.code());
    }

    @ExceptionHandler(TenantAccessException.class)
    void denied(TenantAccessException failure, HttpServletResponse response) throws IOException {
        new IdentityErrorResponseWriter().write(response, failure.code() == ErrorCode.ACCESS_DENIED ? 403 : 409, failure.code());
    }
}
