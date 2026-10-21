package com.ruoyi.medical.contracts;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.charset.StandardCharsets;
import static com.ruoyi.medical.contracts.SchemaTypeAssertions.JSON;
import static org.junit.jupiter.api.Assertions.*;

/** Pure offline contract checks. Synthetic IDs are not object-store versions or storage receipts. */
class SyntheticObjectReferenceContractTest {
    @TempDir static Path directory;
    private static final List<InterfaceProtocol.ObjectRef> REFERENCES = new ArrayList<>();

    @BeforeAll static void prepareSyntheticReferences() throws Exception {
        REFERENCES.clear();
        String objectKey="synthetic-contract-only/original";
        for(int version=1;version<=2;version++) {
            byte[] original=("SYNTHETIC CONTRACT CONTENT " + version + "\n").getBytes(StandardCharsets.UTF_8);
            Path source=directory.resolve("original-" + version + ".txt");
            Files.write(source,original);
            byte[] bytes=Files.readAllBytes(source);
            assertArrayEquals(original,bytes);
            String digest=WireValues.sha256(original);
            assertEquals(digest,WireValues.sha256(bytes));
            assertEquals(Long.toString(original.length),Long.toString(Files.size(source)));
            REFERENCES.add(new InterfaceProtocol.ObjectRef(objectKey,"SYNTHETIC-NOT-STORED-v" + version,
                digest,Long.toString(bytes.length),"text/plain"));
        }
        assertEquals(2,REFERENCES.size());
    }

    @Test void syntheticReferencesRoundTripWithPublishedSchemaAndUnsignedSize() throws Exception {
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
