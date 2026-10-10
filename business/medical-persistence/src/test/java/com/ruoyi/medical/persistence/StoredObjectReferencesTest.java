package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.medical.contracts.InterfaceProtocol.ObjectRef;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.WireValues;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import static org.junit.jupiter.api.Assertions.*;

class StoredObjectReferencesTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final List<ObjectRef> REFERENCES = new ArrayList<>();
    private static final List<Path> ORIGINALS = new ArrayList<>();
    private static Path root, spool, deploymentFile;
    private static JsonNode deployment;
    private static Connection database;
    private static S3ObjectReader reader;

    @BeforeAll static void actualDatabaseAndStoredOriginals() throws Exception {
        root = Path.of(System.getProperty("medical.projectRoot")).toRealPath();
        var serviceSecrets = root.resolve(".implementation-work/runtime/service-secrets.json");
        assertEquals(PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(serviceSecrets));
        var credentials = new Properties(); credentials.setProperty("user", "medical_runtime");
        credentials.setProperty("password", JSON.readTree(Files.readAllBytes(serviceSecrets)).required("database_password").textValue());
        database = DriverManager.getConnection("jdbc:mysql://127.0.0.1:13306/medical_local?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC", credentials);
        database.setReadOnly(true); database.setAutoCommit(false);
        try (var statement = database.createStatement(); var result = statement.executeQuery("SELECT DATABASE(),@@server_uuid,VERSION()")) {
            assertTrue(result.next()); assertEquals("medical_local", result.getString(1));
            assertEquals("5ecfb746-be18-11f1-95e4-d822203bc633", result.getString(2)); assertEquals("8.4.3", result.getString(3));
            assertFalse(result.next());
        }
        deploymentFile = root.resolve(".implementation-work/runtime/object-storage/deployment.json");
        deployment = JSON.readTree(Files.readAllBytes(deploymentFile));
        var secretFile = deploymentFile.resolveSibling("service-secret.json");
        assertEquals(PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(secretFile));
        var secrets = JSON.readTree(Files.readAllBytes(secretFile)); assertEquals(deployment.required("service_id"), secrets.required("service_id"));
        var stored = JSON.readTree(Files.readAllBytes(root.resolve(
            ".implementation-work/IC-05-OBJECT-STORAGE-20261005-OBJECT-STORAGE-FINAL-01/objects.json")));
        assertEquals("LOCAL_PRIVATE_STORAGE_PUBLIC_ORIGINALS", stored.required("scope").textValue());
        assertFalse(stored.required("customer_records_created").booleanValue()); assertFalse(stored.required("formal_evidence_eligible").booleanValue());
        long maximum = 0;
        for (var object : stored.required("objects")) {
            var reference = new ObjectRef(object.required("object_key").textValue(), object.required("object_version").textValue(),
                object.required("sha256").textValue(), object.required("size_bytes").textValue(), object.required("media_type").textValue());
            Path original = root.resolve(object.required("source_ref").textValue()).toRealPath();
            assertEquals(reference.sha256(), WireValues.sha256(Files.readAllBytes(original)));
            assertEquals(reference.sizeBytes(), Long.toString(Files.size(original)));
            REFERENCES.add(reference); ORIGINALS.add(original); maximum = Math.max(maximum, Files.size(original));
        }
        assertEquals(2, REFERENCES.size());
        spool = Files.createTempDirectory(root.resolve(".implementation-work"), "stored-reference-",
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        reader = new S3ObjectReader(java.net.URI.create(deployment.required("endpoint").textValue()), deployment.required("bucket").textValue(),
            "us-east-1", S3ObjectReader.Network.LOCAL_LOOPBACK, StaticCredentialsProvider.create(AwsBasicCredentials.create(
                secrets.required("access_key").textValue(), secrets.required("secret_key").textValue())), spool, maximum,
            Math.toIntExact(Files.size(ORIGINALS.get(0)) / 2));
        currentDeployment();
    }

    private static void currentDeployment() {
        assertEquals(deployment, assertDoesNotThrow(() -> JSON.readTree(Files.readAllBytes(deploymentFile))));
        assertTrue(ProcessHandle.of(deployment.required("pid").longValue()).orElseThrow().isAlive());
    }

    private static String databaseJson(String encoded, ObjectRef reference) throws Exception {
        // 只读实际数据库表达式；没有创建企业、来源、制品或批准记录。
        try (var statement = database.prepareStatement("""
                SELECT CAST(CAST(? AS JSON) AS CHAR),
                       JSON_UNQUOTE(JSON_EXTRACT(CAST(? AS JSON),'$.object_key')) <=> ?,
                       JSON_UNQUOTE(JSON_EXTRACT(CAST(? AS JSON),'$.object_version')) <=> ?,
                       JSON_UNQUOTE(JSON_EXTRACT(CAST(? AS JSON),'$.sha256')) <=> ?,
                       JSON_EXTRACT(CAST(? AS JSON),'$.size_bytes') <=> CAST(? AS UNSIGNED),
                       JSON_UNQUOTE(JSON_EXTRACT(CAST(? AS JSON),'$.media_type')) <=> ?
                """)) {
            statement.setString(1, encoded);
            List<String> fields = List.of(reference.objectKey(), reference.objectVersion(), reference.sha256(), reference.sizeBytes(), reference.mediaType());
            for (int index = 0; index < fields.size(); index++) {
                statement.setString(2 + index * 2, encoded); statement.setString(3 + index * 2, fields.get(index));
            }
            try (var result = statement.executeQuery()) {
                assertTrue(result.next()); String value = result.getString(1);
                for (int index = 2; index <= 6; index++) assertTrue(result.getBoolean(index));
                assertFalse(result.next()); return value;
            }
        }
    }

    @Test void storedRepresentationMatchesAllFiveActualDatabaseComparisons() throws Exception {
        for (var reference : REFERENCES) {
            String encoded = StoredObjectReferences.encode(reference);
            String value = databaseJson(encoded, reference);
            assertTrue(JSON.readTree(value).required("size_bytes").isIntegralNumber());
            assertEquals(new BigInteger(reference.sizeBytes()), JSON.readTree(value).required("size_bytes").bigIntegerValue());
            assertEquals(reference, StoredObjectReferences.decode(value, reference));
        }
    }

    @Test void actualDatabaseRepresentationCanBeConsumedByFixedVersionReader() throws Exception {
        for (int index = 0; index < REFERENCES.size(); index++) {
            var reference = REFERENCES.get(index);
            String encoded = StoredObjectReferences.encode(reference);
            try (var verified = reader.readStored(databaseJson(encoded, reference), reference, StoredObjectReferencesTest::currentDeployment)) {
                assertEquals(reference, verified.reference());
                assertArrayEquals(Files.readAllBytes(ORIGINALS.get(index)), verified.chunk(0, Integer.parseInt(reference.sizeBytes())));
                assertEquals(reference.sha256(), verified.fullSha256());
            }
        }
    }

    @Test void scalarConflictsRejectContentBeforeActualObjectReading() throws Exception {
        var reference = REFERENCES.get(0); var encoded = StoredObjectReferences.encode(reference);
        for (String field : List.of("object_key", "object_version", "sha256", "size_bytes", "media_type")) {
            var changed = (com.fasterxml.jackson.databind.node.ObjectNode) JSON.readTree(encoded);
            if (field.equals("size_bytes")) changed.put(field, new BigInteger(reference.sizeBytes()).add(BigInteger.ONE));
            else changed.put(field, changed.required(field).textValue() + "_");
            requireMismatch(() -> reader.readStored(JSON.writeValueAsString(changed), reference, StoredObjectReferencesTest::currentDeployment));
        }
        requireMismatch(() -> StoredObjectReferences.decode(encoded, REFERENCES.get(1)));
        try (var entries = Files.list(spool)) { assertEquals(0, entries.count()); }
    }

    @Test void closedStoredFieldsAndNumericRepresentationAreRequired() throws Exception {
        var reference = REFERENCES.get(0); var encoded = StoredObjectReferences.encode(reference);
        for (String field : List.of("object_key", "object_version", "sha256", "size_bytes", "media_type")) {
            var missing = (com.fasterxml.jackson.databind.node.ObjectNode) JSON.readTree(encoded); missing.remove(field);
            requireMismatch(() -> StoredObjectReferences.decode(JSON.writeValueAsString(missing), reference));
        }
        var extra = (com.fasterxml.jackson.databind.node.ObjectNode) JSON.readTree(encoded); extra.put("source_ref", ORIGINALS.get(0).toString());
        requireMismatch(() -> StoredObjectReferences.decode(JSON.writeValueAsString(extra), reference));
        var textualSize = (com.fasterxml.jackson.databind.node.ObjectNode) JSON.readTree(encoded); textualSize.put("size_bytes", reference.sizeBytes());
        requireMismatch(() -> StoredObjectReferences.decode(JSON.writeValueAsString(textualSize), reference));
        var fractional = (com.fasterxml.jackson.databind.node.ObjectNode) JSON.readTree(encoded);
        fractional.put("size_bytes", new java.math.BigDecimal(reference.sizeBytes() + ".0"));
        requireMismatch(() -> StoredObjectReferences.decode(JSON.writeValueAsString(fractional), reference));
        requireMismatch(() -> StoredObjectReferences.decode(JSON.writeValueAsString(List.of(JSON.readTree(encoded))), reference));
        requireMismatch(() -> StoredObjectReferences.decode("null", reference));
    }

    @Test void matureJsonParserRejectsDuplicateFieldsAndTrailingValues() throws Exception {
        var reference = REFERENCES.get(0); var encoded = StoredObjectReferences.encode(reference);
        assertThrows(com.fasterxml.jackson.core.JsonProcessingException.class, () -> StoredObjectReferences.decode(encoded + encoded, reference));
        var output = new java.io.StringWriter();
        try (var generator = JSON.getFactory().createGenerator(output)) {
            generator.writeStartObject();
            var fields = JSON.readTree(encoded).fields();
            while (fields.hasNext()) {
                var field = fields.next(); generator.writeFieldName(field.getKey()); generator.writeTree(field.getValue());
            }
            generator.writeNumberField("size_bytes", new BigInteger(reference.sizeBytes())); generator.writeEndObject();
        }
        assertThrows(com.fasterxml.jackson.core.JsonProcessingException.class, () -> StoredObjectReferences.decode(output.toString(), reference));
    }

    @Test void actualUnsignedDatabaseExpressionRemainsExactAndCannotMatchOriginalSize() throws Exception {
        BigInteger maximum;
        try (var statement = database.createStatement(); var result = statement.executeQuery("SELECT CAST(-1 AS UNSIGNED)")) {
            assertTrue(result.next()); maximum = new BigInteger(result.getString(1)); assertFalse(result.next());
        }
        assertEquals(BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE), maximum);
        var reference = REFERENCES.get(0);
        var changed = (com.fasterxml.jackson.databind.node.ObjectNode) JSON.readTree(StoredObjectReferences.encode(reference));
        changed.put("size_bytes", maximum);
        try (var statement = database.prepareStatement("SELECT CAST(CAST(? AS JSON) AS CHAR)")) {
            statement.setString(1, JSON.writeValueAsString(changed));
            try (var result = statement.executeQuery()) {
                assertTrue(result.next()); String stored = result.getString(1);
                assertEquals(maximum, JSON.readTree(stored).required("size_bytes").bigIntegerValue());
                requireMismatch(() -> StoredObjectReferences.decode(stored, reference)); assertFalse(result.next());
            }
        }
    }

    @Test void nonphysicalReferencesAndMalformedUnicodeAreRejectedBeforeStorage() throws Exception {
        var reference = REFERENCES.get(0);
        for (var changed : List.of(
            new ObjectRef("", reference.objectVersion(), reference.sha256(), reference.sizeBytes(), reference.mediaType()),
            new ObjectRef(reference.objectKey(), "", reference.sha256(), reference.sizeBytes(), reference.mediaType()),
            new ObjectRef(reference.objectKey(), reference.objectVersion(), reference.sha256(), "0", reference.mediaType()),
            new ObjectRef(reference.objectKey(), reference.objectVersion(), reference.sha256(), reference.sizeBytes(), "")))
            requireMismatch(() -> StoredObjectReferences.encode(changed));
        var malformed = new ObjectRef(reference.objectKey() + '\uD800', reference.objectVersion(), reference.sha256(), reference.sizeBytes(), reference.mediaType());
        assertThrows(java.nio.charset.CharacterCodingException.class, () -> StoredObjectReferences.encode(malformed));
    }

    private static void requireMismatch(org.junit.jupiter.api.function.Executable action) {
        assertEquals(ErrorCode.CONTENT_MISMATCH, assertThrows(TenantAccessException.class, action).code());
    }

    @AfterAll static void closeActualResources() throws Exception {
        reader.close(); database.close();
        try (var entries = Files.list(spool)) { assertEquals(0, entries.count()); }
        Files.delete(spool);
    }
}
