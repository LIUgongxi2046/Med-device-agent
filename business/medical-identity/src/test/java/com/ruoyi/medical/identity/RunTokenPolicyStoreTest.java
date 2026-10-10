package com.ruoyi.medical.identity;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.ruoyi.medical.contracts.WireValues;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class RunTokenPolicyStoreTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String NAME = "com.ruoyi.medical.identity.RunTokenPolicyStore";
    private static final String ISSUER = "urn:medical:local:run-token-policy";
    private static final String AUDIENCE = "urn:medical:local:source-processing";
    private static KeyPair original, replacement, weak, elliptic;
    private Path directory, current;
    private ObjectNode configuration;

    @BeforeAll static void actualKeyMaterial() throws Exception {
        var rsa = KeyPairGenerator.getInstance("RSA"); rsa.initialize(2048);
        original = rsa.generateKeyPair(); replacement = rsa.generateKeyPair();
        rsa.initialize(1024); weak = rsa.generateKeyPair();
        var ec = KeyPairGenerator.getInstance("EC"); ec.initialize(256); elliptic = ec.generateKeyPair();
    }

    @BeforeEach void currentPrivateConfiguration() throws Exception {
        Path parent = Path.of(System.getProperty("medical.projectRoot")).toRealPath()
            .resolve(".implementation-work/run-token-policy-native");
        Files.createDirectories(parent);
        directory = Files.createDirectory(parent.resolve(UUID.randomUUID().toString()),
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        current = directory.resolve("run-token-policy.json");
        configuration = JSON.createObjectNode().put("schema_version", "1.0.0").put("revision", UUID.randomUUID().toString())
            .put("issuer", ISSUER).put("audience", AUDIENCE)
            .put("public_key_der_base64", Base64.getEncoder().encodeToString(original.getPublic().getEncoded()))
            .put("maximum_lifetime_seconds", 180).put("maximum_token_bytes", 8192);
        publish(configuration);
    }

    @AfterEach void removeActualConfiguration() throws Exception {
        Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwx------"));
        try (var files = Files.list(directory)) { for (var path : files.toList()) Files.delete(path); }
        Files.delete(directory);
    }

    private static Class<?> implementation() {
        return assertDoesNotThrow(() -> Class.forName(NAME), "实际签名验证必须消费当前受保护服务器Policy");
    }

    private Object store() throws Exception {
        return implementation().getConstructor(Path.class, UserPrincipal.class, long.class)
            .newInstance(directory, Files.getOwner(directory), 1048576L);
    }

    private static Object read(Object store) throws Exception { return store.getClass().getMethod("read").invoke(store); }
    private static Object value(Object result, String name) throws Exception { return result.getClass().getMethod(name).invoke(result); }
    private static Object verify(Object store, String token) throws Exception {
        return store.getClass().getMethod("verify", String.class).invoke(store, token);
    }
    private static void deniedRead(Object store, Class<? extends Throwable> type) {
        assertInstanceOf(type, assertThrows(InvocationTargetException.class, () -> read(store)).getCause());
    }

    private void publish(ObjectNode value) throws Exception {
        Path staged = Files.createTempFile(directory, "policy-", ".json",
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        Files.write(staged, JSON.writeValueAsBytes(value));
        Files.move(staged, current, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    private static String signed(KeyPair pair, String audience) throws Exception {
        long now = Instant.now().getEpochSecond();
        var claims = JSON.createObjectNode().put("iss", ISSUER).put("aud", audience).put("iat", now - 1).put("exp", now + 30)
            .put("tenant_id", UUID.randomUUID().toString()).putNull("project_id")
            .put("initiator_id", UUID.randomUUID().toString()).put("run_id", UUID.randomUUID().toString())
            .putNull("step_id").put("jti", UUID.randomUUID().toString()).put("auth_epoch", "18446744073709551615");
        claims.putArray("action_scope").add("source.read");
        var header = new JWSHeader.Builder(JWSAlgorithm.RS256).type(new JOSEObjectType("medical-run+jwt"))
            .keyID(WireValues.sha256(pair.getPublic().getEncoded())).build();
        var jwt = new JWSObject(header, new Payload(JSON.writeValueAsBytes(claims)));
        jwt.sign(new RSASSASigner((RSAPrivateKey) pair.getPrivate())); return jwt.serialize();
    }

    @Test void readsCurrentConfigurationAndVerifiesActualSignedToken() throws Exception {
        Object store = store(); Object snapshot = read(store);
        assertEquals(configuration.required("revision").textValue(), value(snapshot, "revision"));
        assertEquals(WireValues.sha256(Files.readAllBytes(current)), value(snapshot, "contentSha256"));
        var policy = (RunTokenSignature.Policy) value(snapshot, "policy");
        assertEquals(ISSUER, policy.issuer()); assertEquals(AUDIENCE, policy.audience());
        assertEquals(180, policy.maximumLifetime().getSeconds()); assertEquals(8192, policy.maximumBytes());
        assertArrayEquals(original.getPublic().getEncoded(), policy.publicKey().getEncoded());
        String token = signed(original, AUDIENCE);
        var verified = (RunTokenSignature.Verified) verify(store, token);
        assertEquals(WireValues.sha256(token.getBytes(java.nio.charset.StandardCharsets.UTF_8)), verified.tokenSha256());
        assertEquals(AUDIENCE, verified.audience()); assertNull(verified.projectId());
        for (var constructor : snapshot.getClass().getDeclaredConstructors()) assertTrue(Modifier.isPrivate(constructor.getModifiers()));
        assertEquals("CurrentRunTokenPolicy", snapshot.toString());
    }

    @Test void atomicRotationUsesCurrentKeyAndRejectsOriginalToken() throws Exception {
        Object store = store(); String oldToken = signed(original, AUDIENCE);
        assertNotNull(verify(store, oldToken));
        var next = configuration.deepCopy().put("revision", UUID.randomUUID().toString())
            .put("public_key_der_base64", Base64.getEncoder().encodeToString(replacement.getPublic().getEncoded()));
        publish(next);
        assertEquals(next.required("revision").textValue(), value(read(store), "revision"));
        assertInstanceOf(SecurityException.class, assertThrows(InvocationTargetException.class, () -> verify(store, oldToken)).getCause());
        assertNotNull(verify(store, signed(replacement, AUDIENCE)));
    }

    @Test void changedAudienceDeadlineAndCapacityImmediatelyAffectVerification() throws Exception {
        Object store = store(); String token = signed(original, AUDIENCE);
        publish(configuration.deepCopy().put("audience", "urn:medical:local:other-processing"));
        assertInstanceOf(SecurityException.class, assertThrows(InvocationTargetException.class, () -> verify(store, token)).getCause());
        publish(configuration.deepCopy().put("maximum_lifetime_seconds", 1));
        assertInstanceOf(SecurityException.class, assertThrows(InvocationTargetException.class, () -> verify(store, token)).getCause());
        publish(configuration.deepCopy().put("maximum_token_bytes", 1));
        assertInstanceOf(SecurityException.class, assertThrows(InvocationTargetException.class, () -> verify(store, token)).getCause());
        publish(configuration); assertNotNull(verify(store, token));
    }

    @Test void missingCurrentFileNeverUsesPreviousConfiguration() throws Exception {
        Object store = store(); assertNotNull(read(store)); Files.delete(current);
        deniedRead(store, IOException.class);
        assertInstanceOf(IOException.class, assertThrows(InvocationTargetException.class, () -> verify(store, signed(original, AUDIENCE))).getCause());
        publish(configuration); assertNotNull(read(store));
    }

    @Test void privatePermissionsAndSingleLinkAreRequiredOnEveryRead() throws Exception {
        Object store = store();
        Files.setPosixFilePermissions(current, PosixFilePermissions.fromString("rw-r--r--")); deniedRead(store, SecurityException.class);
        Files.setPosixFilePermissions(current, PosixFilePermissions.fromString("rw-------"));
        Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwxr-xr-x")); deniedRead(store, SecurityException.class);
        Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwx------"));
        Path link = directory.resolve("linked.json"); Files.createLink(link, current); deniedRead(store, SecurityException.class);
        Files.delete(link); assertNotNull(read(store));
        Path originalPath = directory.resolve("original.json"); Files.move(current, originalPath);
        Files.createSymbolicLink(current, originalPath); deniedRead(store, SecurityException.class);
    }

    @Test void directoryAliasDifferentOwnerAndConfigurationCapacityAreRejected() throws Exception {
        var constructor = implementation().getConstructor(Path.class, UserPrincipal.class, long.class);
        Path alias = directory.resolveSibling(directory.getFileName() + "-alias"); Files.createSymbolicLink(alias, directory);
        try {
            assertInstanceOf(SecurityException.class, assertThrows(InvocationTargetException.class,
                () -> constructor.newInstance(alias, Files.getOwner(directory), 1048576L)).getCause());
        } finally { Files.delete(alias); }
        UserPrincipal root = directory.getFileSystem().getUserPrincipalLookupService().lookupPrincipalByName("root");
        assertNotEquals(root, Files.getOwner(directory));
        assertInstanceOf(SecurityException.class, assertThrows(InvocationTargetException.class,
            () -> constructor.newInstance(directory, root, 1048576L)).getCause());
        assertInstanceOf(SecurityException.class, assertThrows(InvocationTargetException.class,
            () -> constructor.newInstance(directory, Files.getOwner(directory), 1L)).getCause());
        assertInstanceOf(IllegalArgumentException.class, assertThrows(InvocationTargetException.class,
            () -> constructor.newInstance(directory, Files.getOwner(directory), 0L)).getCause());
    }

    @ParameterizedTest @ValueSource(strings = {"schema_version", "revision", "issuer", "audience", "public_key_der_base64",
        "maximum_lifetime_seconds", "maximum_token_bytes"})
    void everyPolicyFieldIsRequired(String field) throws Exception {
        Object store = store(); var next = configuration.deepCopy(); next.remove(field); publish(next); deniedRead(store, SecurityException.class);
    }

    @Test void unknownDuplicateAndTrailingJsonAreRejected() throws Exception {
        Object store = store(); publish(configuration.deepCopy().put("unknown", true)); deniedRead(store, SecurityException.class);
        Files.writeString(current, "{\"schema_version\":\"1.0.0\",\"schema_version\":\"1.0.0\"}"); deniedRead(store, IOException.class);
        Files.writeString(current, JSON.writeValueAsString(configuration) + " {}"); deniedRead(store, IOException.class);
    }

    @ParameterizedTest @ValueSource(strings = {"maximum_lifetime_seconds", "maximum_token_bytes"})
    void explicitPositiveIntegerLimitsAreRequired(String field) throws Exception {
        Object store = store();
        for (int invalid : new int[]{0, -1}) { publish(configuration.deepCopy().put(field, invalid)); deniedRead(store, SecurityException.class); }
        publish(configuration.deepCopy().put(field, 1.5)); deniedRead(store, SecurityException.class);
        publish(configuration.deepCopy().put(field, "180")); deniedRead(store, SecurityException.class);
        publish(configuration.deepCopy().put(field, 2147483648L)); deniedRead(store, SecurityException.class);
    }

    @Test void actualRsaStrengthAndMatureDerEncodingAreRequired() throws Exception {
        Object store = store();
        publish(configuration.deepCopy().put("public_key_der_base64", Base64.getEncoder().encodeToString(weak.getPublic().getEncoded())));
        deniedRead(store, IllegalArgumentException.class);
        publish(configuration.deepCopy().put("public_key_der_base64", Base64.getEncoder().encodeToString(elliptic.getPublic().getEncoded())));
        deniedRead(store, java.security.GeneralSecurityException.class);
        byte[] encoded = java.util.Arrays.copyOf(original.getPublic().getEncoded(), original.getPublic().getEncoded().length + 1);
        publish(configuration.deepCopy().put("public_key_der_base64", Base64.getEncoder().encodeToString(encoded)));
        assertThrows(InvocationTargetException.class, () -> read(store));
        publish(configuration); String base64 = configuration.required("public_key_der_base64").textValue();
        publish(configuration.deepCopy().put("public_key_der_base64", " " + base64)); deniedRead(store, IllegalArgumentException.class);
    }

    @Test void currentIssuerAndAudienceRequireValidText() throws Exception {
        Object store = store(); publish(configuration.deepCopy().put("issuer", "relative-issuer")); deniedRead(store, SecurityException.class);
        publish(configuration.deepCopy().put("issuer", ISSUER + "#fragment")); deniedRead(store, IllegalArgumentException.class);
        publish(configuration.deepCopy().put("audience", "")); deniedRead(store, SecurityException.class);
        publish(configuration.deepCopy().put("revision", UUID.randomUUID().toString().toUpperCase())); deniedRead(store, SecurityException.class);
    }
}
