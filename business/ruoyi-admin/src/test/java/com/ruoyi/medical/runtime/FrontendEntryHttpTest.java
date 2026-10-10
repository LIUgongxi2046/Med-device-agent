package com.ruoyi.medical.runtime;

import com.fasterxml.jackson.databind.json.JsonMapper;
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
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;

public final class FrontendEntryHttpTest {
    @Test void actualBackendServesOriginalVueLoginAndProtectedBusinessApis() throws Exception {
        var json = JsonMapper.builder().build();
        var root = Path.of(System.getProperty("medical.projectRoot"));
        var runtime = root.resolve(".implementation-work/runtime");
        var secrets = json.readTree(Files.readAllBytes(runtime.resolve("service-secrets.json")));
        var management = json.readTree(Files.readAllBytes(runtime.resolve("local-management-secret.json")));
        var invitation = json.readTree(Files.readAllBytes(runtime.resolve("invitation-response-secret.json")));
        var token = json.readTree(Files.readAllBytes(runtime.resolve("ruoyi-token-secret.json")));
        var application = assertInstanceOf(ServletWebServerApplicationContext.class,
                new SpringApplicationBuilder(MedicalApplication.class).properties(Map.of(
                    "MEDICAL_LOCAL_DATABASE_PASSWORD", secrets.required("database_password").textValue(),
                    "MEDICAL_LOCAL_REDIS_PASSWORD", secrets.required("redis_password").textValue(),
                    "MEDICAL_LOCAL_MANAGEMENT_HMAC_KEY", management.required("key").textValue(),
                    "MEDICAL_LOCAL_MANAGEMENT_HMAC_KEY_ID", management.required("key_id").textValue(),
                    "MEDICAL_INVITATION_RESPONSE_KEY", invitation.required("key").textValue(),
                    "MEDICAL_INVITATION_RESPONSE_KEY_ID", invitation.required("key_id").textValue(),
                    "MEDICAL_RUOYI_TOKEN_SECRET", token.required("key").textValue()))
                .run("--spring.config.name=application-medical-local", "--server.address=127.0.0.1", "--server.port=18085",
                    "--medical.identity.local.browser-origin=http://127.0.0.1:18085", "--server.shutdown=immediate",
                    "--spring.main.banner-mode=off"));
        var evidence = json.createObjectNode();
        try (application) {
            var jdbc = new JdbcTemplate(application.getBean("masterDataSource", javax.sql.DataSource.class));
            var before = jdbc.queryForMap("SELECT (SELECT COUNT(*) FROM tenant) enterprises,"
                    + "(SELECT COUNT(*) FROM project) projects,(SELECT COUNT(*) FROM task_head) tasks");
            String origin = "http://127.0.0.1:" + application.getWebServer().getPort();
            var http = HttpClient.newHttpClient();
            var login = http.send(HttpRequest.newBuilder(URI.create(origin + "/auth/login")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, login.statusCode());
            assertTrue(login.body().contains("id=\"app\""), "后端登录入口必须提供原Vue应用");
            byte[] frontend;
            try (var resource = getClass().getResourceAsStream("/static/index.html")) {
                assertNotNull(resource, "原Vue正式构建必须属于应用制品");
                frontend = resource.readAllBytes();
            }
            assertEquals(new String(frontend, java.nio.charset.StandardCharsets.UTF_8), login.body());
            int staticFiles = 0;
            var frontendDirectory = Path.of(System.getProperty("medical.frontendDirectory")).toRealPath();
            try (var files = Files.walk(frontendDirectory)) {
                for (var file : files.filter(Files::isRegularFile).sorted().toList()) {
                    String relative = frontendDirectory.relativize(file).toString().replace('\\', '/');
                    byte[] expected = Files.readAllBytes(file);
                    try (var packaged = getClass().getResourceAsStream("/static/" + relative)) {
                        assertNotNull(packaged, relative);
                        assertArrayEquals(expected, packaged.readAllBytes(), relative);
                    }
                    var response = http.send(HttpRequest.newBuilder(URI.create(origin + "/" + relative)).GET().build(),
                            HttpResponse.BodyHandlers.ofByteArray());
                    assertEquals(200, response.statusCode(), relative);
                    assertArrayEquals(expected, response.body(), relative);
                    staticFiles++;
                }
            }
            assertTrue(staticFiles > 1);
            String projectTemplates = "/projects/" + java.util.UUID.randomUUID() + "/templates";
            String projectUploads = "/projects/" + java.util.UUID.randomUUID() + "/uploads";
            String taskConfiguration = "/projects/" + java.util.UUID.randomUUID() + "/tasks/" + java.util.UUID.randomUUID() + "/config";
            for (String route : java.util.List.of("/access", "/login", "/account", "/projects", "/templates", "/library", "/enterprises/roles", projectTemplates, projectUploads, taskConfiguration)) {
                var page = http.send(HttpRequest.newBuilder(URI.create(origin + route)).GET().build(),
                        HttpResponse.BodyHandlers.ofByteArray());
                assertEquals(200, page.statusCode(), route); assertArrayEquals(frontend, page.body());
            }
            var refreshed = http.send(HttpRequest.newBuilder(URI.create(origin + projectTemplates)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
            assertEquals(200, refreshed.statusCode()); assertArrayEquals(frontend, refreshed.body());
            var uploadRefresh = http.send(HttpRequest.newBuilder(URI.create(origin + projectUploads)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
            assertEquals(200, uploadRefresh.statusCode()); assertArrayEquals(frontend, uploadRefresh.body());
            var taskRefresh = http.send(HttpRequest.newBuilder(URI.create(origin + taskConfiguration)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
            assertEquals(200, taskRefresh.statusCode()); assertArrayEquals(frontend, taskRefresh.body());
            var anonymous = http.send(HttpRequest.newBuilder(URI.create(origin + "/api/session"))
                    .header("X-Schema-Version", "1.0.0").GET().build(), HttpResponse.BodyHandlers.ofByteArray());
            assertEquals(401, anonymous.statusCode());
            assertEquals("AUTHENTICATION_REQUIRED", json.readTree(anonymous.body()).required("code").textValue());
            if (Boolean.getBoolean("medical.frontendBrowser")) {
                var output = Path.of(System.getProperty("medical.navigationOutput"));
                var browser = new ProcessBuilder("/usr/local/bin/node", "tests/browser/frontend-entry-checks.js", output.toString(), origin)
                        .directory(root.toFile()).redirectErrorStream(true).redirectOutput(output.resolve("frontend-browser-output.log").toFile()).start();
                try {
                    assertTrue(browser.waitFor(120, java.util.concurrent.TimeUnit.SECONDS)); assertEquals(0, browser.exitValue());
                } finally { if (browser.isAlive()) browser.destroyForcibly(); }
            }
            assertEquals(before, jdbc.queryForMap("SELECT (SELECT COUNT(*) FROM tenant) enterprises,"
                    + "(SELECT COUNT(*) FROM project) projects,(SELECT COUNT(*) FROM task_head) tasks"));
            evidence.put("port", application.getWebServer().getPort()); evidence.put("vue_entry_verified", true);
            evidence.put("static_files_verified", staticFiles);
            evidence.put("project_template_page_route", projectTemplates);
            evidence.put("project_template_direct_entry_and_refresh_verified", true);
            evidence.put("project_upload_page_route", projectUploads);
            evidence.put("project_upload_direct_entry_and_refresh_verified", true);
            evidence.put("task_configuration_page_route", taskConfiguration);
            evidence.put("task_configuration_direct_entry_and_refresh_verified", true);
            evidence.put("anonymous_session_status", 401); evidence.put("customer_success_verified", false);
        }
        assertFalse(application.isActive()); evidence.put("temporary_application_closed", true);
        Files.write(Path.of(System.getProperty("medical.frontendEvidence")), json.writeValueAsBytes(evidence));
    }
}
