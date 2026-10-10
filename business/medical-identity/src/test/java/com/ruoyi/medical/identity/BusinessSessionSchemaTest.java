package com.ruoyi.medical.identity;

import com.ruoyi.medical.contracts.SchemaRejection;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BusinessSessionSchemaTest {
    @Test
    void productionSchemaManifestLoadsAndEmptyRequestFailsAtValidation() throws Exception {
        var schema = new BusinessSessionSchema();
        assertTrue(schema.manifestSha256().matches("[0-9a-f]{64}"));
        assertThrows(SchemaRejection.class, () -> schema.selection(new byte[0], "0", null));
    }
}
