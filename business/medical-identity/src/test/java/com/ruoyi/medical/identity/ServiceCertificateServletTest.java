package com.ruoyi.medical.identity;

import com.fasterxml.jackson.databind.json.JsonMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.util.net.SSLHostConfig;
import org.apache.tomcat.util.net.SSLHostConfigCertificate;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import static org.junit.jupiter.api.Assertions.*;

public final class ServiceCertificateServletTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Set<String> CIPHERS = Set.of("TLS_AES_256_GCM_SHA384", "TLS_AES_128_GCM_SHA256");

    @Test void authorizesActualServletCertificatesAndRejectsBrowserCredentialsAndCurrentRevocation() throws Exception {
        Path root = Path.of(System.getProperty("medical.projectRoot")).toRealPath();
        Path assets = root.resolve(".implementation-work/service-certificates/" + UUID.randomUUID());
        Files.createDirectories(assets.getParent());
        var prepare = new ProcessBuilder(System.getProperty("medical.test.python", root.resolve(".implementation-work/document-venv/bin/python").toString()),
            root.resolve("scripts/prepare_service_certificates.py").toString(), "--output", assets.toString())
            .directory(root.toFile()).redirectErrorStream(true).redirectOutput(assets.resolveSibling(assets.getFileName() + ".prepare.log").toFile()).start();
        assertTrue(prepare.waitFor(60, TimeUnit.SECONDS)); assertEquals(0, prepare.exitValue());
        assertEquals(PosixFilePermissions.fromString("rwx------"), Files.getPosixFilePermissions(assets.resolve("private")));
        try (var files = Files.list(assets.resolve("private"))) {
            for (var file : files.toList()) assertEquals(PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(file));
        }
        var manifest = JSON.readTree(Files.readAllBytes(assets.resolve("manifest.json")));
        Path configuration = Files.createDirectory(assets.resolve("configuration"),
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        Path registration = configuration.resolve("trust.json");
        var initial = ServiceCertificateTrustStoreTest.configuration(assets);
        String serviceUri = manifest.required("service_uri").textValue();
        ServiceCertificateTrustStoreTest.publish(registration, initial);
        var tomcat = new Tomcat(); Path base = Files.createDirectory(assets.resolve("tomcat"));
        tomcat.setBaseDir(base.toString()); tomcat.setHostname("127.0.0.1");
        var connector = new Connector("org.apache.coyote.http11.Http11NioProtocol");
        connector.setPort(0); connector.setProperty("address", "127.0.0.1"); connector.setProperty("SSLEnabled", "true");
        connector.setScheme("https"); connector.setSecure(true);
        var host = new SSLHostConfig(); host.setProtocols("TLSv1.3"); host.setCipherSuites(String.join(",", CIPHERS));
        host.setCertificateVerification("required"); host.setTruststoreFile(assets.resolve("private/trust.p12").toString());
        host.setTruststorePassword(Files.readString(assets.resolve("private/password"))); host.setTruststoreType("PKCS12");
        var certificate = new SSLHostConfigCertificate(host, SSLHostConfigCertificate.Type.EC);
        certificate.setCertificateKeystoreFile(assets.resolve("private/server.p12").toString());
        certificate.setCertificateKeystorePassword(Files.readString(assets.resolve("private/password")));
        certificate.setCertificateKeystoreType("PKCS12"); host.addCertificate(certificate); connector.addSslHostConfig(host);
        tomcat.setConnector(connector);
        var context = tomcat.addContext("", base.toString());
        // Surefire non-forked execution isolates test dependencies from the JVM system loader.
        context.setParentClassLoader(getClass().getClassLoader());
        var web = new AnnotationConfigWebApplicationContext();
        web.setClassLoader(getClass().getClassLoader());
        web.getEnvironment().setActiveProfiles("service-certificate-servlet-native"); web.register(NativeMvc.class);
        var servlet = Tomcat.addServlet(context, "service-peer", new DispatcherServlet(web)); servlet.setLoadOnStartup(1);
        context.addServletMappingDecoded("/", "service-peer");
        var evidence = JSON.createObjectNode(); int port;
        try {
            var store = new ServiceCertificateTrustStore(configuration, Files.getOwner(configuration), 1048576L);
            var identity = new ServiceCertificateIdentity(store::read);
            web.addBeanFactoryPostProcessor(factory -> factory.registerSingleton("serviceCertificateIdentity", identity));
            tomcat.start(); port = connector.getLocalPort(); assertTrue(port > 0);
            var good = client(assets, "approved");
            var response = send(good, port, null, null);
            assertEquals(200, response.statusCode()); assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
            String originalSession = response.headers().firstValue("X-Native-Tls-Session").orElseThrow();
            assertFalse(originalSession.isBlank());
            var body = JSON.readTree(response.body());
            assertEquals(manifest.required("service_uri").textValue(), body.required("service_uri").textValue());
            assertEquals(manifest.required("certificates").required("approved").textValue(), body.required("certificate_sha256").textValue());
            assertEquals(manifest.required("trust_revision").textValue(), body.required("trust_revision").textValue());
            var checks = evidence.putArray("checks"); checks.add("actual_servlet_identity_success");
            for (String name : List.of("unpinned", "unregistered", "ambiguous", "revoked")) {
                var denied = send(client(assets, name), port, null, null);
                assertEquals(403, denied.statusCode()); assertEquals("ACCESS_DENIED", JSON.readTree(denied.body()).required("code").textValue());
                assertEquals("no-store", denied.headers().firstValue("Cache-Control").orElseThrow()); checks.add(name + "_http_denied");
            }
            assertEquals(403, send(good, port, "Cookie", "MEDICAL_SESSION=untrusted-browser-input").statusCode());
            assertEquals(403, send(good, port, "Origin", "https://127.0.0.1").statusCode());
            checks.add("browser_cookie_denied"); checks.add("browser_origin_denied");
            for (String name : List.of("none", "untrusted", "expired")) {
                var invalid = client(assets, name); int activePort = port;
                assertThrows(IOException.class, () -> send(invalid, activePort, null, null)); checks.add(name + "_tls_denied");
            }
            var changed = initial.deepCopy(); changed.put("revision", UUID.randomUUID().toString());
            changed.withObject("registered_certificates").putArray(serviceUri).add(manifest.required("certificates").required("revoked").textValue());
            ServiceCertificateTrustStoreTest.publish(registration, changed);
            var currentDenied = send(good, port, null, null);
            assertEquals(403, currentDenied.statusCode());
            assertEquals(originalSession, currentDenied.headers().firstValue("X-Native-Tls-Session").orElseThrow());
            checks.add("same_connection_current_registration_denied");
            changed = initial.deepCopy(); changed.put("revision", UUID.randomUUID().toString());
            changed.putArray("revocations").add(ServiceCertificateTrustStoreTest.crl(assets, "expired"));
            ServiceCertificateTrustStoreTest.publish(registration, changed);
            assertEquals(403, send(good, port, null, null).statusCode()); checks.add("expired_crl_http_denied");
            ServiceCertificateTrustStoreTest.publish(registration, initial);
            assertEquals(200, send(good, port, null, null).statusCode()); checks.add("current_valid_registration_reopened");
            Files.setPosixFilePermissions(registration, PosixFilePermissions.fromString("rw-r--r--"));
            assertEquals(403, send(good, port, null, null).statusCode()); checks.add("current_file_permission_denied");
            Files.setPosixFilePermissions(registration, PosixFilePermissions.fromString("rw-------"));
            Files.delete(registration);
            var unavailable = send(good, port, null, null);
            assertEquals(503, unavailable.statusCode()); assertEquals("no-store", unavailable.headers().firstValue("Cache-Control").orElseThrow());
            checks.add("current_file_missing_unavailable");
            ServiceCertificateTrustStoreTest.publish(registration, initial);
            assertEquals(200, send(good, port, null, null).statusCode()); checks.add("current_file_restored");
            evidence.put("scope", "ACTUAL_TOMCAT_SERVLET_MTLS_SERVICE_IDENTITY"); evidence.put("port", port);
            evidence.set("certificate_manifest", manifest); evidence.put("servlet_integration_verified", true);
            evidence.put("current_trust_store_verified", true);
            evidence.put("worker_identity_verified", false); evidence.put("business_success_verified", false);
            evidence.put("professional_approval_created", false); evidence.put("customer_success_verified", false);
        } finally {
            tomcat.stop(); tomcat.destroy();
            try (var files = Files.list(configuration)) { for (var file : files.toList()) Files.delete(file); }
            Files.delete(configuration);
            try (var files = Files.list(assets.resolve("private"))) { for (var file : files.toList()) Files.delete(file); }
            Files.delete(assets.resolve("private"));
        }
        assertFalse(Files.exists(assets.resolve("private")));
        try (var socket = new Socket()) { assertThrows(IOException.class, () -> socket.connect(new InetSocketAddress("127.0.0.1", port), 1000)); }
        evidence.put("private_credentials_removed", true); evidence.put("server_closed", true);
        String configured = System.getProperty("medical.serviceCertificateEvidence");
        Path result = configured == null ? assets.resolve("servlet-result.json") : Path.of(configured).resolveSibling("service-certificate-servlet-result.json");
        Files.createDirectories(result.getParent()); Files.write(result, JSON.writeValueAsBytes(evidence));
    }

    private static HttpClient client(Path assets, String name) throws Exception {
        return HttpClient.newBuilder().sslContext(ServiceCertificateNativeTest.context(assets, name))
            .version(HttpClient.Version.HTTP_1_1).connectTimeout(java.time.Duration.ofSeconds(5)).build();
    }
    private static HttpResponse<byte[]> send(HttpClient client, int port, String header, String value) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("https://127.0.0.1:" + port + "/peer"))
            .timeout(java.time.Duration.ofSeconds(10)).GET();
        if (header != null) request.header(header, value);
        return client.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @Profile("service-certificate-servlet-native")
    public static class NativeMvc {
        @Bean NativeController peer(ServiceCertificateIdentity identity) { return new NativeController(identity); }
    }

    @RestController
    @Profile("service-certificate-servlet-native")
    public static final class NativeController {
        private final ServiceCertificateIdentity identity;
        NativeController(ServiceCertificateIdentity identity) { this.identity = identity; }
        @GetMapping("/peer") ResponseEntity<Map<String, String>> require(HttpServletRequest request) throws IOException, GeneralSecurityException {
            var verified = identity.require(request);
            return ResponseEntity.ok().header("Cache-Control", "no-store")
                .header("X-Native-Tls-Session", java.util.Objects.requireNonNull(request.getAttribute("jakarta.servlet.request.ssl_session_id")).toString())
                .body(Map.of("service_uri", verified.serviceUri(),
                "certificate_sha256", verified.certificateSha256(), "trust_revision", verified.trustRevision()));
        }
        @ExceptionHandler({SecurityException.class, GeneralSecurityException.class})
        ResponseEntity<Map<String, String>> denied(Exception failure, HttpServletRequest request) {
            return ResponseEntity.status(403).header("Cache-Control", "no-store")
                .header("X-Native-Tls-Session", java.util.Objects.requireNonNull(request.getAttribute("jakarta.servlet.request.ssl_session_id")).toString())
                .body(Map.of("code", "ACCESS_DENIED"));
        }
        @ExceptionHandler(IOException.class)
        ResponseEntity<Map<String, String>> unavailable(IOException failure) {
            return ResponseEntity.status(503).header("Cache-Control", "no-store").body(Map.of("code", "SERVICE_UNAVAILABLE"));
        }
    }
}
