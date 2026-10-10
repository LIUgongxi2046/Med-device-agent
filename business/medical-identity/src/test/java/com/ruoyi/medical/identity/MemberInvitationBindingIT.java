package com.ruoyi.medical.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.SchemaRejection;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import static org.junit.jupiter.api.Assertions.*;

public class MemberInvitationBindingIT {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static JsonNode actual;
    private static String username;

    public static void main(String[] args) throws Exception {
        readActualAuthenticatedOwner();
        var checks = new MemberInvitationBindingIT();
        checks.actualLocalBindingPreservesAllFourFields();
        checks.unknownFieldsAndMissingFieldsFailAtValidation();
        checks.authenticationBranchCannotBeSubstituted();
        checks.duplicateIdentityFieldsFailDuringJsonRead();
        var result = JSON.createObjectNode();
        result.put("scope", "ACTUAL_AUTHENTICATED_MEMBER_INVITATION_BINDING");
        result.put("checks", 4); result.set("identity_binding", actual);
        Files.write(Path.of(args[0]), JSON.writeValueAsBytes(result));
        System.out.println("MEMBER_INVITATION_BINDING_CHECK checks=4 source=ACTUAL_LOCAL_LOGIN");
    }

    @BeforeAll
    static void readActualAuthenticatedOwner() throws Exception {
        Path credentials = Path.of(java.util.Objects.requireNonNull(System.getProperty("medical.project-root")))
                .resolve(".implementation-work/runtime/owner-credentials.json");
        var owner = JSON.readTree(Files.readAllBytes(credentials));
        username = owner.required("username").textValue();
        assertEquals(System.getProperty("user.name"), username);
        var cookies = new CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER);
        var http = HttpClient.newBuilder().cookieHandler(cookies).followRedirects(HttpClient.Redirect.NEVER).build();
        var client = RestClient.builder().baseUrl("http://127.0.0.1:18080")
                .requestFactory(new JdkClientHttpRequestFactory(http))
                .messageConverters(converters -> converters.add(new FormHttpMessageConverter())).build();
        var csrf = JSON.readTree(client.get().uri("/auth/csrf").retrieve().body(byte[].class));
        var form = new LinkedMultiValueMap<String, String>();
        form.add("username", username); form.add("password", owner.required("password").textValue());
        var login = client.post().uri("/auth/login").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .header(HttpHeaders.ORIGIN, "http://127.0.0.1:18080")
                .header(csrf.required("header_name").textValue(), csrf.required("token").textValue())
                .body(form).retrieve().toBodilessEntity();
        assertEquals(302, login.getStatusCode().value());
        var response = http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:18080/api/session/identity-binding"))
                .header("X-Schema-Version", "1.0.0").GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, response.statusCode());
        actual = JSON.readTree(response.body()).required("data");
        assertEquals("local", actual.required("mode").textValue());
        // 输入来自实际登录响应，仅核验邀请描述及完整字段比较。
        var logoutCsrf = JSON.readTree(client.get().uri("/auth/csrf").retrieve().body(byte[].class));
        var logout = client.post().uri("/auth/logout").header(HttpHeaders.ORIGIN, "http://127.0.0.1:18080")
                .header(logoutCsrf.required("header_name").textValue(), logoutCsrf.required("token").textValue())
                .retrieve().toBodilessEntity();
        assertEquals(204, logout.getStatusCode().value());
    }

    @Test
    void actualLocalBindingPreservesAllFourFields() throws Exception {
        var described = new IdentityBindingSchema().readBinding(JSON.writeValueAsBytes(actual));
        assertEquals("local", described.mode());
        assertEquals(actual.required("user_id").textValue(), described.user_id());
        assertNull(described.issuer()); assertNull(described.subject());
        var serialized = AuthenticatedIdentityBinding.local(described.user_id());
        assertTrue(serialized.matches(described));
        assertFalse(serialized.matches(new AuthenticatedIdentityBinding.View("local", described.user_id(), username, null)));
        assertFalse(serialized.matches(new AuthenticatedIdentityBinding.View("local", described.user_id(), null, username)));
        assertFalse(serialized.matches(new AuthenticatedIdentityBinding.View("oidc", described.user_id(), null, null)));
    }

    @Test
    void unknownFieldsAndMissingFieldsFailAtValidation() throws Exception {
        var schema = new IdentityBindingSchema();
        var unknown = actual.deepCopy(); ((com.fasterxml.jackson.databind.node.ObjectNode) unknown).put("username", username);
        assertThrows(SchemaRejection.class, () -> schema.readBinding(JSON.writeValueAsBytes(unknown)));
        var missing = actual.deepCopy(); ((com.fasterxml.jackson.databind.node.ObjectNode) missing).remove("issuer");
        assertThrows(SchemaRejection.class, () -> schema.readBinding(JSON.writeValueAsBytes(missing)));
    }

    @Test
    void authenticationBranchCannotBeSubstituted() throws Exception {
        var substituted = actual.deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) substituted).put("mode", "oidc");
        assertThrows(SchemaRejection.class, () -> new IdentityBindingSchema().readBinding(JSON.writeValueAsBytes(substituted)));
        assertThrows(SchemaRejection.class, () -> new IdentityBindingSchema().readBinding(new byte[0]));
    }

    @Test
    void duplicateIdentityFieldsFailDuringJsonRead() throws Exception {
        var output = new java.io.ByteArrayOutputStream();
        try (var writer = JSON.createGenerator(output)) {
            writer.writeStartObject();
            var fields = actual.fields();
            while (fields.hasNext()) {
                var field = fields.next(); writer.writeObjectField(field.getKey(), field.getValue());
            }
            writer.writeObjectField("user_id", actual.required("user_id")); writer.writeEndObject();
        }
        assertThrows(com.fasterxml.jackson.core.JsonProcessingException.class,
                () -> new IdentityBindingSchema().readBinding(output.toByteArray()));
    }
}
