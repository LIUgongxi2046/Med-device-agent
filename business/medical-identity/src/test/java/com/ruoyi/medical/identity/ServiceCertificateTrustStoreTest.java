package com.ruoyi.medical.identity;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.security.cert.CertificateFactory;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public final class ServiceCertificateTrustStoreTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test void readsCurrentProtectedTrustAndRejectsInvalidFilesAndSignedRevocation() throws Exception {
        Path root = Path.of(System.getProperty("medical.projectRoot")).toRealPath();
        Path assets = root.resolve(".implementation-work/service-certificates/" + UUID.randomUUID());
        Files.createDirectories(assets.getParent());
        var prepare = new ProcessBuilder(System.getProperty("medical.test.python", root.resolve(".implementation-work/document-venv/bin/python").toString()),
            root.resolve("scripts/prepare_service_certificates.py").toString(), "--output", assets.toString())
            .directory(root.toFile()).redirectErrorStream(true)
            .redirectOutput(assets.resolveSibling(assets.getFileName() + ".prepare.log").toFile()).start();
        assertTrue(prepare.waitFor(60, TimeUnit.SECONDS)); assertEquals(0, prepare.exitValue());
        Path directory = Files.createDirectory(assets.resolve("configuration"),
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        Path current = directory.resolve("trust.json");
        try {
            ObjectNode initial = configuration(assets);
            publish(current, initial);
            assertEquals(PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(current));
            Class<?> type = assertDoesNotThrow(() -> Class.forName("com.ruoyi.medical.identity.ServiceCertificateTrustStore"),
                "受保护的实际CA与签名CRL必须通过正式配置读取器消费");
            Object store = type.getConstructor(Path.class, UserPrincipal.class, long.class)
                .newInstance(directory, Files.getOwner(directory), 1048576L);
            var read = type.getMethod("read");
            var trust = (ServiceCertificateIdentity.Trust) read.invoke(store);
            assertEquals(initial.required("revision").textValue(), trust.revision());
            assertEquals(1, trust.authorities().size()); assertEquals(1, trust.revocations().size());
            String uri = initial.required("registered_certificates").fieldNames().next();
            assertEquals(3, trust.registeredCertificates().get(uri).size());
            var changed = initial.deepCopy(); changed.put("revision", UUID.randomUUID().toString());
            changed.withObject("registered_certificates").putArray(uri).add(
                JSON.readTree(Files.readAllBytes(assets.resolve("manifest.json"))).required("certificates").required("revoked").textValue());
            publish(current, changed);
            var reread = (ServiceCertificateIdentity.Trust) read.invoke(store);
            assertEquals(changed.required("revision").textValue(), reread.revision());
            assertEquals(1, reread.registeredCertificates().get(uri).size());
            Files.setPosixFilePermissions(current, PosixFilePermissions.fromString("rw-r--r--"));
            assertInstanceOf(SecurityException.class, assertThrows(InvocationTargetException.class, () -> read.invoke(store)).getCause());
            Files.setPosixFilePermissions(current, PosixFilePermissions.fromString("rw-------"));
            Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwxr-xr-x"));
            assertInstanceOf(SecurityException.class, assertThrows(InvocationTargetException.class, () -> read.invoke(store)).getCause());
            Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwx------"));
            Files.delete(current); Files.createSymbolicLink(current, assets.resolve("manifest.json"));
            assertInstanceOf(SecurityException.class, assertThrows(InvocationTargetException.class, () -> read.invoke(store)).getCause());
            Files.delete(current); publish(current, initial);
            Path linked = directory.resolve("trust-linked.json");
            Files.createLink(linked, current);
            assertInstanceOf(SecurityException.class, assertThrows(InvocationTargetException.class, () -> read.invoke(store)).getCause());
            Files.delete(linked);
            UserPrincipal otherOwner = directory.getFileSystem().getUserPrincipalLookupService().lookupPrincipalByName("root");
            assertNotEquals(otherOwner, Files.getOwner(directory));
            assertInstanceOf(SecurityException.class, assertThrows(InvocationTargetException.class,
                () -> type.getConstructor(Path.class, UserPrincipal.class, long.class).newInstance(directory, otherOwner, 1048576L)).getCause());
            var oversized = type.getConstructor(Path.class, UserPrincipal.class, long.class);
            assertInstanceOf(SecurityException.class, assertThrows(InvocationTargetException.class,
                () -> oversized.newInstance(directory, Files.getOwner(directory), 1L)).getCause());
            var unknown = initial.deepCopy().put("unknown_field", true); publish(current, unknown);
            assertInstanceOf(SecurityException.class, assertThrows(InvocationTargetException.class, () -> read.invoke(store)).getCause());
            Files.writeString(current, "{\"schema_version\":\"1.0.0\",\"schema_version\":\"1.0.0\"}");
            assertInstanceOf(java.io.IOException.class, assertThrows(InvocationTargetException.class, () -> read.invoke(store)).getCause());
            Files.writeString(current, JSON.writeValueAsString(initial) + " {}");
            assertInstanceOf(java.io.IOException.class, assertThrows(InvocationTargetException.class, () -> read.invoke(store)).getCause());
            var invalidSignature = initial.deepCopy();
            byte[] encoded = Base64.getDecoder().decode(crl(assets, "current")); encoded[encoded.length - 1] ^= 1;
            invalidSignature.putArray("revocations").add(Base64.getEncoder().encodeToString(encoded));
            publish(current, invalidSignature);
            assertInstanceOf(java.security.SignatureException.class,
                assertThrows(InvocationTargetException.class, () -> read.invoke(store)).getCause());
            var weakProtocol = initial.deepCopy(); weakProtocol.putArray("protocols").add("TLSv1.2"); publish(current, weakProtocol);
            assertInstanceOf(SecurityException.class, assertThrows(InvocationTargetException.class, () -> read.invoke(store)).getCause());
            var expired = initial.deepCopy(); expired.putArray("revocations").add(crl(assets, "expired"));
            publish(current, expired);
            assertInstanceOf(java.security.GeneralSecurityException.class,
                assertThrows(InvocationTargetException.class, () -> read.invoke(store)).getCause());
            publish(current, initial);
            assertEquals(initial.required("revision").textValue(), ((ServiceCertificateIdentity.Trust) read.invoke(store)).revision());
        } finally {
            Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwx------"));
            try (var files = Files.list(directory)) { for (var file : files.toList()) Files.delete(file); }
            Files.delete(directory);
            try (var files = Files.list(assets.resolve("private"))) { for (var file : files.toList()) Files.delete(file); }
            Files.delete(assets.resolve("private"));
        }
        assertFalse(Files.exists(directory)); assertFalse(Files.exists(assets.resolve("private")));
    }

    static ObjectNode configuration(Path assets) throws Exception {
        var manifest = JSON.readTree(Files.readAllBytes(assets.resolve("manifest.json")));
        var result = JSON.createObjectNode().put("schema_version", "1.0.0").put("revision", manifest.required("trust_revision").textValue());
        var factory = CertificateFactory.getInstance("X.509");
        try (var input = Files.newInputStream(assets.resolve("ca.pem"))) {
            result.putArray("authorities").add(Base64.getEncoder().encodeToString(factory.generateCertificate(input).getEncoded()));
        }
        result.putArray("revocations").add(crl(assets, "current"));
        var pins = result.putObject("registered_certificates").putArray(manifest.required("service_uri").textValue());
        for (String name : List.of("approved", "revoked", "ambiguous")) pins.add(manifest.required("certificates").required(name).textValue());
        result.putArray("protocols").add("TLSv1.3");
        result.putArray("ciphers").add("TLS_AES_256_GCM_SHA384").add("TLS_AES_128_GCM_SHA256");
        return result;
    }

    static String crl(Path assets, String name) throws Exception {
        try (var input = Files.newInputStream(assets.resolve(name + ".crl.pem"))) {
            var revoked = (java.security.cert.X509CRL) CertificateFactory.getInstance("X.509").generateCRL(input);
            return Base64.getEncoder().encodeToString(revoked.getEncoded());
        }
    }

    static void publish(Path current, ObjectNode value) throws Exception {
        Path staged = Files.createTempFile(current.getParent(), "trust-", ".json",
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        Files.write(staged, JSON.writeValueAsBytes(value));
        Files.move(staged, current, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }
}
