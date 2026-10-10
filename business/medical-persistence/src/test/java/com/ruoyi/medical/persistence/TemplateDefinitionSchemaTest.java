package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import com.networknt.schema.JsonNodePath;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.PathType;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TemplateDefinitionSchemaTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    private JsonNode document() throws Exception {
        try (var source = getClass().getResourceAsStream("/templates.schema.json")) {
            assertNotNull(source, "模板草稿必须具有包含字段、附件和条件的完整封闭定义");
            return JSON.readTree(source);
        }
    }

    private JsonSchema definition(String name) throws Exception {
        var root = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(document(),
                SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());
        var schema = root.getSubSchema(new JsonNodePath(PathType.JSON_POINTER).append("$defs").append(name));
        schema.initializeValidators();
        return schema;
    }

    @Test void allElevenFieldTypesHaveExplicitNamesAndNoProductDefault() throws Exception {
        var prd = Files.readString(Path.of(System.getProperty("medical.projectRoot"), "PRD.md"));
        assertTrue(prd.contains("文本、长文、日期、数值、枚举、多选、关系、表格、图片、公式、文件"));
        var types = List.of("TEXT", "LONG_TEXT", "DATE", "NUMBER", "ENUM", "MULTI_SELECT", "RELATION", "TABLE", "IMAGE", "FORMULA", "FILE");
        assertEquals(types, JSON.convertValue(document().required("$defs").required("ValueType").required("enum"), List.class));
        var schema = definition("ValueType");
        for (String type : types) assertTrue(schema.validate(TextNode.valueOf(type)).isEmpty());
        var constraints = document().required("$defs").required("Constraints");
        assertFalse(constraints.required("additionalProperties").booleanValue());
        assertFalse(constraints.required("properties").has("default"));
        assertTrue(document().findValues("default").isEmpty());
    }

    @Test void everyDocumentEntryHasItsOwnType() throws Exception {
        var design = Files.readString(Path.of(System.getProperty("medical.projectRoot"), "ui-delivery/document-reading-design.md"));
        assertTrue(design.contains("产品技术要求、注册文档目录、综述资料、说明书、标签样稿、基本原则清单、风险管理、研究报告、临床评价、质量体系、变更资料、延续资料及注册资料总稿"));
        var expected = List.of("PRODUCT_TECHNICAL_REQUIREMENTS", "REGISTRATION_DIRECTORY", "OVERVIEW", "INSTRUCTIONS_FOR_USE",
                "LABEL", "ESSENTIAL_PRINCIPLES", "RISK_MANAGEMENT", "RESEARCH_REPORT", "CLINICAL_EVALUATION",
                "QUALITY_SYSTEM", "CHANGE_REGISTRATION", "RENEWAL_REGISTRATION", "REGISTRATION_DOSSIER");
        assertEquals(expected, JSON.convertValue(document().required("$defs").required("DocumentType").required("enum"), List.class));
        for (String type : expected) assertTrue(definition("DocumentType").validate(TextNode.valueOf(type)).isEmpty());
    }

    @Test void actualNormativeDirectoryIsConsumedWithoutDeletingConditionalNodes() throws Exception {
        document();
        JsonNode nodes;
        try (var input = getClass().getResourceAsStream("/registration-directory-2021-121.json")) { nodes = JSON.readTree(input).required("nodes"); }
        var type = Class.forName("com.ruoyi.medical.persistence.TemplateDefinitionSchema");
        var current = type.getConstructor(PermissionSchema.class, RegistrationDirectory.class)
                .newInstance(new PermissionSchema(), RegistrationDirectory.fromClasspath());
        var method = type.getMethod("requireOfficialDirectory", String.class, JsonNode.class);
        assertDoesNotThrow(() -> method.invoke(current, "NON_IVD_FIRST_REGISTRATION_2021_121", nodes));
        var missing = (com.fasterxml.jackson.databind.node.ArrayNode) nodes.deepCopy();
        missing.remove(7);
        assertThrows(java.lang.reflect.InvocationTargetException.class,
                () -> method.invoke(current, "NON_IVD_FIRST_REGISTRATION_2021_121", missing));
    }

    @Test void unknownApplicabilityRemainsAnIndependentResult() throws Exception {
        var schema = definition("Applicability");
        for (String result : List.of("APPLICABLE", "NOT_APPLICABLE", "UNKNOWN"))
            assertTrue(schema.validate(TextNode.valueOf(result)).isEmpty());
        assertFalse(schema.validate(JSON.nullNode()).isEmpty());
        assertFalse(schema.validate(JSON.getNodeFactory().booleanNode(false)).isEmpty());
    }

    @Test void realAttachmentsHaveNoNarrativeGenerationMode() throws Exception {
        var schema = definition("AttachmentMode");
        assertTrue(schema.validate(TextNode.valueOf("AUTHORIZED_ORIGINAL_ONLY")).isEmpty());
        assertFalse(schema.validate(TextNode.valueOf("NARRATIVE")).isEmpty());
        assertFalse(schema.validate(TextNode.valueOf("APPROVED_CALCULATION")).isEmpty());
        assertFalse(schema.validate(JSON.nullNode()).isEmpty());
    }

    @Test void aTemplateVersionCannotSupplyItsOwnApprovalOrStatus() throws Exception {
        var root = document().required("$defs");
        var request = root.required("VersionInput");
        assertFalse(request.required("additionalProperties").booleanValue());
        var properties = request.required("properties");
        for (String name : List.of("tenant_id", "created_by", "approved_by", "status", "template_version", "content_sha256"))
            assertFalse(properties.has(name));
        var content = root.required("Content");
        assertFalse(content.required("additionalProperties").booleanValue());
        for (String name : List.of("nodes", "field_definitions", "repeat_definitions", "attachment_requirements",
                "cross_document_rules", "condition_schema", "basis_manifest", "format_profile"))
            assertTrue(content.required("properties").has(name), name);
    }

    @Test void declarativeConditionsReferenceFixedRulesWithoutExecutableCode() throws Exception {
        var rule = document().required("$defs").required("Condition");
        assertFalse(rule.required("additionalProperties").booleanValue());
        var properties = rule.required("properties");
        for (String name : List.of("expression", "script", "javascript", "java", "python", "url")) assertFalse(properties.has(name));
        assertTrue(properties.has("rule_revision"));
        assertTrue(properties.has("input_keys"));
    }

    @Test void repeatRowsAndOutputLocationsPreserveStableIdentities() throws Exception {
        var root = document().required("$defs");
        var repeat = root.required("Repeat");
        for (String name : List.of("repeat_id", "node_id", "field_ids", "min_count", "max_count", "count_basis", "row_id_required"))
            assertTrue(repeat.required("properties").has(name), name);
        assertTrue(repeat.required("properties").required("row_id_required").required("const").booleanValue());
        var mapping = root.required("OutputMapping");
        for (String name : List.of("node_id", "repeat_id", "column_key", "internal_only", "ordinal"))
            assertTrue(mapping.required("properties").has(name), name);
    }

    @Test void referenceCatalogueCannotBecomeAnApprovedCustomerTemplateRequest() throws Exception {
        document();
        JsonNode catalogue;
        try (var input = getClass().getResourceAsStream("/registration-directory-2021-121.json")) { catalogue = JSON.readTree(input); }
        assertEquals("REFERENCE_ONLY", catalogue.required("reference_state").textValue());
        var current = new TemplateDefinitionSchema(new PermissionSchema(), RegistrationDirectory.fromClasspath());
        var failure = assertThrows(com.ruoyi.medical.contracts.SchemaRejection.class,
                () -> current.version(JSON.writeValueAsBytes(catalogue)));
        assertEquals(com.ruoyi.medical.contracts.ErrorCode.SCHEMA_INVALID, failure.code());
    }
}
