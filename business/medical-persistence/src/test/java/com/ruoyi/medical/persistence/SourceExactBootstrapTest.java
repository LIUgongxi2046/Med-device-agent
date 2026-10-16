package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SourceExactBootstrapTest {
    @Test
    void structuredExactDefinitionsHaveIndependentClosedPublicationAndSearchInputs() throws Exception {
        var resource=getClass().getResourceAsStream("/source-exact.schema.json");
        assertNotNull(resource,"EXACT必须提供原文字段核准和等值查询定义");
        try(resource) {
            var definitions=JsonMapper.builder().build().readTree(resource).required("$defs");
            for(String name:java.util.List.of("Field","PublicationInput","Manifest","PublicationReceipt","SearchInput","Page"))
                assertFalse(definitions.required(name).required("additionalProperties").booleanValue());
            assertEquals(5,definitions.required("FieldKind").required("enum").size());
            assertEquals("EXACT",definitions.required("Page").required("properties").required("mode").required("const").textValue());
        }
    }
}
