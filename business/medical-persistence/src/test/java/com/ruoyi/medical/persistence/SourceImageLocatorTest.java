package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Deferred unit fixtures only; never OCR or production evidence. */
class SourceImageLocatorTest {
    @Test void versionedImageLocationPreservesOriginalIdentity() throws Exception {
        var value=JsonMapper.builder().build().readTree("""
            {"schema_version":"1.0.0","kind":"IMAGE","frame_index":0,
             "original_sha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
             "width":100,"height":100,"x0":0.1,"y0":0.2,"x1":0.8,"y1":0.9,
             "coordinate_system":"ORIGINAL_TOP_LEFT_NORMALIZED"}
            """);
        SourceLocatorSchema.validate(SchemaCatalogue.fromClasspath(),value);
        assertEquals("IMAGE",value.required("kind").textValue());
        var reversed=value.deepCopy(); ((com.fasterxml.jackson.databind.node.ObjectNode)reversed).put("x0",0.9);
        assertThrows(Exception.class,()->SourceLocatorSchema.validate(SchemaCatalogue.fromClasspath(),reversed));
    }
}
