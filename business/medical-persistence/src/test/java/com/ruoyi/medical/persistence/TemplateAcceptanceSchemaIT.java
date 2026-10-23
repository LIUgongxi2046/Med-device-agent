package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.json.JsonMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TemplateAcceptanceSchemaIT {
    @Test void actualPreparedTemplatePreservesDependenciesAcrossCanonicalEncoding() throws Exception {
        var root = Path.of(System.getProperty("medical.projectRoot")).toRealPath();
        var reader = JsonMapper.builder().build();
        var ledger = reader.readTree(Files.readAllBytes(root.resolve(".implementation-work/runtime/acceptance-template-workflow.json")));
        assertEquals("SIMULATION", ledger.required("data_kind").textValue());
        var json = new PermissionSchema();
        var schema = new TemplateDefinitionSchema(json, RegistrationDirectory.fromClasspath());
        var original = schema.version(reader.writeValueAsBytes(ledger.required("template_input")));
        var encoded = schema.version(json.canonicalObject(original.request()).getBytes(StandardCharsets.UTF_8));
        assertEquals(original, encoded);
        assertEquals(5, original.dependency_refs().size());
    }
}
