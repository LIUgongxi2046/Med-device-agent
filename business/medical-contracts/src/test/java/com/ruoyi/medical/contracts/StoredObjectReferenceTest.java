package com.ruoyi.medical.contracts;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static com.ruoyi.medical.contracts.SchemaTypeAssertions.JSON;
import static org.junit.jupiter.api.Assertions.*;

class StoredObjectReferenceTest {
    private static final List<InterfaceProtocol.ObjectRef> REFERENCES = new ArrayList<>();

    @BeforeAll static void loadActualStoredOriginals() throws Exception {
        Path root = Path.of(System.getProperty("medical.projectRoot")).toRealPath();
        JsonNode record = JSON.readTree(Files.readAllBytes(root.resolve(
            ".implementation-work/IC-05-OBJECT-STORAGE-20261005-OBJECT-STORAGE-FINAL-01/objects.json")));
        assertEquals("LOCAL_PRIVATE_STORAGE_PUBLIC_ORIGINALS", record.required("scope").textValue());
        assertFalse(record.required("customer_records_created").booleanValue());
        assertFalse(record.required("formal_evidence_eligible").booleanValue());
        for (JsonNode object : record.required("objects")) {
            byte[] bytes = Files.readAllBytes(root.resolve(object.required("source_ref").textValue()));
            assertEquals(WireValues.sha256(bytes), object.required("sha256").textValue());
            assertEquals(Long.toString(bytes.length), object.required("size_bytes").textValue());
            REFERENCES.add(new InterfaceProtocol.ObjectRef(object.required("object_key").textValue(),
                object.required("object_version").textValue(), object.required("sha256").textValue(),
                object.required("size_bytes").textValue(), object.required("media_type").textValue()));
        }
        assertEquals(2, REFERENCES.size());
    }

    @Test void actualFixedVersionsRoundTripWithOriginalSchemaAndUnsignedSize() throws Exception {
        var catalogue = SchemaCatalogue.fromClasspath();
        for (var reference : REFERENCES) {
            var bound = TypedSchemaBindings.bind(catalogue, InterfaceBindings.OBJECT_REF, JSON.valueToTree(reference));
            assertEquals(reference, bound.read());
        }
        assertEquals(REFERENCES.get(0).objectKey(), REFERENCES.get(1).objectKey());
        assertNotEquals(REFERENCES.get(0).objectVersion(), REFERENCES.get(1).objectVersion());
    }

    @Test void incorrectDigestRepresentationFailsDuringReferenceConstruction() {
        for (var value : REFERENCES) {
            for (String digest : new String[]{null, value.sha256().toUpperCase(Locale.ROOT), value.sha256().substring(1)}) {
                assertThrows(IllegalArgumentException.class, () -> new InterfaceProtocol.ObjectRef(
                    value.objectKey(), value.objectVersion(), digest, value.sizeBytes(), value.mediaType()));
            }
        }
    }

    @Test void nullOrOversizedIdentityFieldsFailDuringReferenceConstruction() {
        var value = REFERENCES.get(0);
        assertThrows(IllegalArgumentException.class, () -> new InterfaceProtocol.ObjectRef(
            null, value.objectVersion(), value.sha256(), value.sizeBytes(), value.mediaType()));
        assertThrows(IllegalArgumentException.class, () -> new InterfaceProtocol.ObjectRef(
            value.objectKey(), null, value.sha256(), value.sizeBytes(), value.mediaType()));
        assertThrows(IllegalArgumentException.class, () -> new InterfaceProtocol.ObjectRef(
            value.objectKey(), value.objectVersion(), value.sha256(), value.sizeBytes(), null));
        assertThrows(IllegalArgumentException.class, () -> new InterfaceProtocol.ObjectRef(
            value.objectKey().repeat(1025 / value.objectKey().length() + 1), value.objectVersion(), value.sha256(), value.sizeBytes(), value.mediaType()));
        assertThrows(IllegalArgumentException.class, () -> new InterfaceProtocol.ObjectRef(
            value.objectKey(), value.objectVersion().repeat(257 / value.objectVersion().length() + 1), value.sha256(), value.sizeBytes(), value.mediaType()));
        assertThrows(IllegalArgumentException.class, () -> new InterfaceProtocol.ObjectRef(
            value.objectKey(), value.objectVersion(), value.sha256(), value.sizeBytes(), value.mediaType().repeat(129 / value.mediaType().length() + 1)));
    }

    @Test void unsignedSizeCannotBecomeFloatingPointNegativeOrOverflow() {
        var value = REFERENCES.get(0);
        for (String size : new String[]{null, "-" + value.sizeBytes(), value.sizeBytes() + ".0", "18446744073709551616"}) {
            assertThrows(IllegalArgumentException.class, () -> new InterfaceProtocol.ObjectRef(
                value.objectKey(), value.objectVersion(), value.sha256(), size, value.mediaType()));
        }
    }
}
