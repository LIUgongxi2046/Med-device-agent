package com.ruoyi.medical.identity;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsExchange;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.CertPathValidatorException;
import java.security.cert.X509Certificate;
import java.security.cert.X509CRL;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManagerFactory;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public final class ServiceCertificateNativeTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Set<String> CIPHERS = Set.of("TLS_AES_256_GCM_SHA384", "TLS_AES_128_GCM_SHA256");

    @Test void verifiesActualTlsPeerCertificatesAgainstCurrentRegistrationAndSignedCrl() throws Exception {
        Path root = Path.of(System.getProperty("medical.projectRoot")).toRealPath();
        Path assets = root.resolve(".implementation-work/service-certificates/" + UUID.randomUUID());
        Files.createDirectories(assets.getParent());
        var prepare = new ProcessBuilder(root.resolve(".implementation-work/document-venv/bin/python").toString(),
            root.resolve("scripts/prepare_service_certificates.py").toString(), "--output", assets.toString())
            .directory(root.toFile()).redirectErrorStream(true)
            .redirectOutput(assets.resolveSibling(assets.getFileName() + ".prepare.log").toFile()).start();
        assertTrue(prepare.waitFor(60, TimeUnit.SECONDS)); assertEquals(0, prepare.exitValue());
        var manifest = JSON.readTree(Files.readAllBytes(assets.resolve("manifest.json")));
        var sessions = new LinkedBlockingQueue<SSLSession>();
        var server = HttpsServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var ssl = context(assets, "server");
        server.setHttpsConfigurator(new HttpsConfigurator(ssl) {
            @Override public void configure(HttpsParameters parameters) {
                var settings = getSSLContext().getDefaultSSLParameters();
                settings.setProtocols(new String[]{"TLSv1.3"});
                settings.setCipherSuites(CIPHERS.toArray(String[]::new));
                settings.setNeedClientAuth(true); parameters.setSSLParameters(settings);
            }
        });
        // 此入口只记录实际TLS握手证据，不提供任何业务权限。
        server.createContext("/peer", exchange -> {
            var session = ((HttpsExchange) exchange).getSSLSession();
            sessions.add(session); exchange.getResponseHeaders().set("Cache-Control", "no-store");
            exchange.sendResponseHeaders(204, -1); exchange.close();
        });
        server.start(); int port = server.getAddress().getPort();
        var evidence = JSON.createObjectNode();
        try {
            SSLSession approved = peer(assets, "approved", port, sessions);
            assertEquals("TLSv1.3", approved.getProtocol());
            assertTrue(CIPHERS.contains(approved.getCipherSuite()));
            Class<?> identity = assertDoesNotThrow(() -> Class.forName("com.ruoyi.medical.identity.ServiceCertificateIdentity"),
                "实际客户端证书已通过TLS握手，服务身份验证器必须存在");
            Class<?> trustType = Class.forName(identity.getName() + "$Trust");
            Path registration = assets.resolve("registration.json");
            var initial = JSON.createObjectNode().put("revision", manifest.required("trust_revision").textValue()).put("crl", "current.crl.pem");
            var pins = initial.putArray("pins");
            for (String name : List.of("approved", "revoked", "ambiguous")) pins.add(manifest.required("certificates").required(name).textValue());
            Files.write(registration, JSON.writeValueAsBytes(initial));
            ServiceCertificateIdentity.TrustSource current = () -> assertDoesNotThrow(() -> {
                var configured = JSON.readTree(Files.readAllBytes(registration));
                var factory = CertificateFactory.getInstance("X.509");
                X509Certificate ca; X509CRL crl;
                try (var input = Files.newInputStream(assets.resolve("ca.pem"))) { ca = (X509Certificate) factory.generateCertificate(input); }
                try (var input = Files.newInputStream(assets.resolve(configured.required("crl").textValue()))) { crl = (X509CRL) factory.generateCRL(input); }
                var registered = new java.util.HashSet<String>(); configured.required("pins").forEach(pin -> registered.add(pin.textValue()));
                return (ServiceCertificateIdentity.Trust) trustType.getConstructor(String.class, List.class, List.class, Map.class, Set.class, Set.class)
                    .newInstance(configured.required("revision").textValue(), List.of(ca), List.of(crl),
                        Map.of(manifest.required("service_uri").textValue(), Set.copyOf(registered)), Set.of("TLSv1.3"), CIPHERS);
            });
            Object verifier = identity.getConstructor(ServiceCertificateIdentity.TrustSource.class).newInstance(current);
            var require = identity.getMethod("require", SSLSession.class);
            Object verified = assertDoesNotThrow(() -> require.invoke(verifier, approved));
            assertEquals(manifest.required("service_uri").textValue(), verified.getClass().getMethod("serviceUri").invoke(verified));
            assertEquals(manifest.required("certificates").required("approved").textValue(), verified.getClass().getMethod("certificateSha256").invoke(verified));
            assertEquals(manifest.required("trust_revision").textValue(), verified.getClass().getMethod("trustRevision").invoke(verified));
            var checks = evidence.putArray("checks"); checks.add("approved_actual_tls_peer");
            for (String name : List.of("unpinned", "unregistered", "ambiguous", "revoked")) {
                var actual = peer(assets, name, port, sessions);
                var denied = assertThrows(InvocationTargetException.class, () -> require.invoke(verifier, actual));
                if (name.equals("revoked")) assertEquals(CertPathValidatorException.BasicReason.REVOKED,
                    assertInstanceOf(CertPathValidatorException.class, denied.getCause()).getReason());
                else assertInstanceOf(SecurityException.class, denied.getCause());
                checks.add(name + "_certificate_denied");
            }
            for (String name : List.of("none", "untrusted", "expired")) {
                var client = client(assets, name);
                assertThrows(IOException.class, () -> client.send(request(port), HttpResponse.BodyHandlers.discarding()));
                assertNull(sessions.poll()); checks.add(name + "_tls_handshake_denied");
            }
            var changed = initial.deepCopy(); changed.put("revision", UUID.randomUUID().toString());
            changed.putArray("pins").add(manifest.required("certificates").required("revoked").textValue());
            Files.write(registration, JSON.writeValueAsBytes(changed));
            assertInstanceOf(SecurityException.class, assertThrows(InvocationTargetException.class, () -> require.invoke(verifier, approved)).getCause());
            checks.add("current_registration_removed_denied");
            changed = initial.deepCopy(); changed.put("revision", UUID.randomUUID().toString()); changed.put("crl", "expired.crl.pem");
            Files.write(registration, JSON.writeValueAsBytes(changed));
            assertInstanceOf(CertPathValidatorException.class, assertThrows(InvocationTargetException.class, () -> require.invoke(verifier, approved)).getCause());
            checks.add("expired_signed_crl_denied");
            evidence.put("scope", "ACTUAL_TLS_SERVICE_CERTIFICATE_COMPONENT"); evidence.put("port", port);
            evidence.put("service_certificate_identity_verified", true); evidence.put("servlet_integration_verified", false);
            evidence.put("worker_identity_verified", false); evidence.put("business_success_verified", false);
            evidence.put("professional_approval_created", false); evidence.put("customer_success_verified", false);
            evidence.set("certificate_manifest", manifest);
        } finally {
            server.stop(0);
            try (var files = Files.list(assets.resolve("private"))) { for (var file : files.toList()) Files.delete(file); }
            Files.delete(assets.resolve("private"));
        }
        assertFalse(Files.exists(assets.resolve("private")));
        try (var socket = new Socket()) { assertThrows(IOException.class, () -> socket.connect(new InetSocketAddress("127.0.0.1", port), 1000)); }
        evidence.put("private_credentials_removed", true); evidence.put("server_closed", true);
        Path result = Path.of(System.getProperty("medical.serviceCertificateEvidence", assets.resolve("result.json").toString()));
        Files.createDirectories(result.getParent()); Files.write(result, JSON.writeValueAsBytes(evidence));
    }

    private static SSLSession peer(Path assets, String name, int port, LinkedBlockingQueue<SSLSession> sessions) throws Exception {
        var response = client(assets, name).send(request(port), HttpResponse.BodyHandlers.discarding());
        assertEquals(204, response.statusCode()); assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
        return java.util.Objects.requireNonNull(sessions.poll(5, TimeUnit.SECONDS));
    }

    private static HttpRequest request(int port) { return HttpRequest.newBuilder(URI.create("https://127.0.0.1:" + port + "/peer"))
        .timeout(java.time.Duration.ofSeconds(10)).GET().build(); }

    private static HttpClient client(Path assets, String name) throws Exception {
        return HttpClient.newBuilder().sslContext(context(assets, name)).version(HttpClient.Version.HTTP_1_1)
            .followRedirects(HttpClient.Redirect.NEVER).connectTimeout(java.time.Duration.ofSeconds(5)).build();
    }

    static SSLContext context(Path assets, String name) throws Exception {
        char[] password = Files.readString(assets.resolve("private/password")).toCharArray();
        var trust = KeyStore.getInstance("PKCS12");
        try (var input = Files.newInputStream(assets.resolve("private/trust.p12"))) { trust.load(input, password); }
        var trusts = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()); trusts.init(trust);
        KeyManager[] keys = new KeyManager[0];
        if (!name.equals("none")) {
            var store = KeyStore.getInstance("PKCS12");
            try (var input = Files.newInputStream(assets.resolve("private/" + name + ".p12"))) { store.load(input, password); }
            var manager = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()); manager.init(store, password);
            keys = manager.getKeyManagers();
        }
        java.util.Arrays.fill(password, '\0');
        var context = SSLContext.getInstance("TLSv1.3"); context.init(keys, trusts.getTrustManagers(), null); return context;
    }
}
