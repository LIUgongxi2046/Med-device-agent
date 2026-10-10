package com.ruoyi.medical.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import com.ruoyi.medical.persistence.TemplateApplicabilityService;
import com.ruoyi.medical.persistence.TemplateApplicabilityController;
import com.ruoyi.medical.persistence.RuleExpression;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import static org.junit.jupiter.api.Assertions.*;

public final class TemplateApplicabilityHttpTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String ORIGIN = "http://127.0.0.1:18080";

    @Test void actualApplicationUsesCurrentTemplatesAndOriginalFrameworkAuthentication() throws Exception {
        var root = Path.of(System.getProperty("medical.projectRoot"));
        var secrets = JSON.readTree(Files.readAllBytes(root.resolve(".implementation-work/runtime/service-secrets.json")));
        var management = JSON.readTree(Files.readAllBytes(root.resolve(".implementation-work/runtime/local-management-secret.json")));
        var invitation = JSON.readTree(Files.readAllBytes(root.resolve(".implementation-work/runtime/invitation-response-secret.json")));
        var frameworkToken = JSON.readTree(Files.readAllBytes(root.resolve(".implementation-work/runtime/ruoyi-token-secret.json")));
        var owner = JSON.readTree(Files.readAllBytes(root.resolve(".implementation-work/runtime/owner-credentials.json")));
        var application = assertInstanceOf(ServletWebServerApplicationContext.class,
                new SpringApplicationBuilder(Class.forName("com.ruoyi.medical.runtime.MedicalApplication"))
                .properties(Map.of("MEDICAL_LOCAL_DATABASE_PASSWORD", secrets.required("database_password").textValue(),
                        "MEDICAL_LOCAL_REDIS_PASSWORD", secrets.required("redis_password").textValue(),
                        "MEDICAL_LOCAL_MANAGEMENT_HMAC_KEY", management.required("key").textValue(),
                        "MEDICAL_LOCAL_MANAGEMENT_HMAC_KEY_ID", management.required("key_id").textValue(),
                        "MEDICAL_INVITATION_RESPONSE_KEY", invitation.required("key").textValue(),
                        "MEDICAL_INVITATION_RESPONSE_KEY_ID", invitation.required("key_id").textValue(),
                        "MEDICAL_RUOYI_TOKEN_SECRET", frameworkToken.required("key").textValue()))
                .run("--spring.config.name=application-medical-local", "--server.address=127.0.0.1", "--server.port=0",
                        "--server.shutdown=immediate", "--spring.main.banner-mode=off"));
        var result = JSON.createObjectNode();
        try (application) {
            result.put("max_heap_bytes", Runtime.getRuntime().maxMemory());
            assertNotNull(application.getBean(TemplateApplicabilityService.class));
            assertNotNull(application.getBean(TemplateApplicabilityController.class));
            assertNotNull(application.getBean(RuleExpression.class)); assertEquals(278, SchemaCatalogue.fromClasspath().definitionCount());
            var jdbc = new JdbcTemplate(application.getBean("masterDataSource", javax.sql.DataSource.class));
            var before = counts(jdbc);
            int port = application.getWebServer().getPort(); String origin = "http://127.0.0.1:" + port;
            var cookies = new CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER);
            var http = HttpClient.newBuilder().cookieHandler(cookies).followRedirects(HttpClient.Redirect.NEVER).build();
            var client = RestClient.builder().baseUrl(origin).requestFactory(new JdkClientHttpRequestFactory(http)).build();
            String project = java.util.Objects.requireNonNull(jdbc.queryForObject("SELECT user_id FROM platform_local_account WHERE username=? AND status='ACTIVE'",
                    String.class, owner.required("username").textValue()));
            String path = "/api/projects/" + project + "/applicable-templates?document_type=REGISTRATION_DIRECTORY&profile_revision=" + project;
            var anonymous = http.send(HttpRequest.newBuilder(URI.create(origin + path)).header("X-Schema-Version", "1.0.0")
                    .header("X-Context-Revision", "1").GET().build(), HttpResponse.BodyHandlers.ofByteArray());
            assertEquals(401, anonymous.statusCode()); assertEquals("AUTHENTICATION_REQUIRED", JSON.readTree(anonymous.body()).required("code").textValue());
            var csrf = JSON.readTree(client.get().uri("/auth/csrf").retrieve().body(byte[].class));
            var form = new LinkedMultiValueMap<String,String>(); form.add("username", owner.required("username").textValue()); form.add("password", owner.required("password").textValue());
            assertEquals(302, client.post().uri("/auth/login").contentType(MediaType.APPLICATION_FORM_URLENCODED).header("Origin", ORIGIN)
                    .header(csrf.required("header_name").textValue(), csrf.required("token").textValue()).body(form).retrieve().toBodilessEntity().getStatusCode().value());
            var account = JSON.readTree(client.get().uri("/auth/account").retrieve().body(byte[].class));
            assertEquals(owner.required("username").textValue(), account.required("username").textValue());
            var denied = http.send(HttpRequest.newBuilder(URI.create(origin + path)).header("X-Schema-Version", "1.0.0")
                    .header("X-Context-Revision", "1").GET().build(), HttpResponse.BodyHandlers.ofByteArray());
            assertEquals(403, denied.statusCode()); assertEquals("ACCESS_DENIED", JSON.readTree(denied.body()).required("code").textValue());
            var version = http.send(HttpRequest.newBuilder(URI.create(origin + path)).header("X-Schema-Version", "unsupported")
                    .header("X-Context-Revision", "1").GET().build(), HttpResponse.BodyHandlers.ofByteArray());
            assertEquals(422, version.statusCode()); assertEquals("SCHEMA_UNSUPPORTED", JSON.readTree(version.body()).required("code").textValue());
            var malformed = http.send(HttpRequest.newBuilder(URI.create(origin + path + "&page=2")).header("X-Schema-Version", "1.0.0")
                    .header("X-Context-Revision", "1").GET().build(), HttpResponse.BodyHandlers.ofByteArray());
            assertEquals(422, malformed.statusCode()); assertEquals("SCHEMA_INVALID", JSON.readTree(malformed.body()).required("code").textValue());
            var current = JSON.readTree(client.get().uri("/auth/csrf").retrieve().body(byte[].class));
            assertEquals(204, client.post().uri("/auth/logout").header("Origin", ORIGIN)
                    .header(current.required("header_name").textValue(), current.required("token").textValue()).retrieve().toBodilessEntity().getStatusCode().value());
            assertEquals(before, counts(jdbc));
            result.put("anonymous_http_status", anonymous.statusCode()); result.put("unscoped_http_status", denied.statusCode());
            result.put("unsupported_version_http_status", version.statusCode()); result.put("unbound_page_http_status", malformed.statusCode());
            result.put("current_schema_definitions", SchemaCatalogue.fromClasspath().definitionCount()); result.put("actual_http_port", port);
            result.set("business_counts", before); result.put("business_counts_unchanged", true); result.put("framework_login_and_logout_verified", true);
        }
        assertFalse(application.isActive()); result.put("temporary_application_closed", true); result.put("customer_success_verified", false);
        Files.write(Path.of(System.getProperty("medical.httpEvidence")), JSON.writeValueAsBytes(result));
    }

    private static JsonNode counts(JdbcTemplate jdbc) {
        var result = JSON.createObjectNode();
        for (String table : java.util.List.of("tenant", "project", "template_version", "rule_version", "permission_grant", "task_head", "operation_record"))
            result.put(table, java.util.Objects.requireNonNull(jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class)));
        return result;
    }
}
