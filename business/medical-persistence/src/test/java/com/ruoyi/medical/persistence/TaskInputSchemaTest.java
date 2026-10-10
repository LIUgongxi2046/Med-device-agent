package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.medical.contracts.CommonBindings;
import com.ruoyi.medical.contracts.CommonProtocol;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import com.ruoyi.medical.contracts.SchemaRejection;
import com.ruoyi.medical.contracts.TypedSchemaBindings;
import java.util.HashSet;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TaskInputSchemaTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    private JsonNode document() throws Exception {
        try (var input = getClass().getResourceAsStream("/tasks.schema.json")) {
            assertNotNull(input, "任务创建和计划输入必须具有封闭的完整定义");
            return JSON.readTree(input);
        }
    }

    @Test void creationRequiresEveryFixedInputWithoutAcceptingApprovalOrIdentity() throws Exception {
        var creation = document().required("$defs").required("Create");
        assertFalse(creation.required("additionalProperties").booleanValue());
        var required = new HashSet<String>(); creation.required("required").forEach(value -> required.add(value.textValue()));
        assertEquals(new HashSet<>(List.of("schema_version", "project_id", "profile_revision", "document_type", "template_version",
                "agent_release_id", "scope_selection", "owner_id", "goal")), required);
        var properties = creation.required("properties");
        for (String name : List.of("tenant_id", "task_id", "task_revision", "status", "approved_by", "created_by", "auth_epoch", "governance_epoch"))
            assertFalse(properties.has(name), name);
        assertTrue(properties.has("based_on_document_revision"));
    }

    @Test void taskInputsReuseThePublishedSourceAndCandidateDefinitions() throws Exception {
        var shared = SchemaCatalogue.fromClasspath(); var definitions = document().required("$defs");
        assertEquals(shared.schemaRef("interfaces", "SourceScope"),
                definitions.required("Create").required("properties").required("scope_selection").required("$ref").textValue());
        var version = definitions.required("PlanVersionInput");
        assertFalse(version.required("additionalProperties").booleanValue());
        assertEquals(shared.schemaRef("artifacts", "PlanCandidate"), version.required("properties").required("content").required("$ref").textValue());
        assertEquals(shared.schemaRef("artifacts", "PlanCandidate"), version.required("properties").required("schema_ref").required("const").textValue());
        assertTrue(version.required("properties").has("expected_revision"));
    }

    @Test void taskDocumentTypesUseTheCompletePublishedTemplateTypes() throws Exception {
        var shared = SchemaCatalogue.fromClasspath();
        assertEquals(shared.schemaRef("templates", "DocumentType"), document().required("$defs").required("Create")
                .required("properties").required("document_type").required("$ref").textValue());
    }

    @Test void explicitEmptyScopeKeepsTheNetworkDisabledAndBothSelectionsEmpty() throws Exception {
        var scope = JSON.createObjectNode(); scope.put("mode", "SOURCE_VERSIONS");
        scope.putArray("collection_versions"); scope.putArray("source_versions");
        scope.put("network_enabled", false); scope.putNull("network_policy_revision"); scope.putNull("approved_query_revision");
        var catalogue = SchemaCatalogue.fromClasspath();
        var binding = TypedSchemaBindings.bind(catalogue, CommonBindings.SOURCE_SCOPE, scope);
        assertEquals(CommonProtocol.ScopeMode.SOURCE_VERSIONS, binding.read().mode());
        assertTrue(binding.read().collectionVersions().isEmpty()); assertTrue(binding.read().sourceVersions().isEmpty());
        assertFalse(binding.read().networkEnabled());
        assertEquals(scope, JSON.readTree(binding.payload().canonicalJson()));
        scope.put("network_enabled", true);
        assertThrows(SchemaRejection.class, () -> TypedSchemaBindings.bind(catalogue, CommonBindings.SOURCE_SCOPE, scope));
    }

    @Test void everyExplicitEmptyModeRemainsEmptyAfterBinding() throws Exception {
        var scope = JSON.createObjectNode(); scope.putArray("collection_versions"); scope.putArray("source_versions");
        scope.put("network_enabled", false); scope.putNull("network_policy_revision"); scope.putNull("approved_query_revision");
        var catalogue = SchemaCatalogue.fromClasspath();
        for (var mode : CommonProtocol.ScopeMode.values()) {
            scope.put("mode", mode.name());
            var actual = TypedSchemaBindings.bind(catalogue, CommonBindings.SOURCE_SCOPE, scope).read();
            assertEquals(mode, actual.mode()); assertTrue(actual.sourceVersions().isEmpty()); assertTrue(actual.collectionVersions().isEmpty());
            assertFalse(actual.networkEnabled()); assertNull(actual.approvedQueryRevision()); assertNull(actual.networkPolicyRevision());
        }
        scope.put("approved", true);
        assertThrows(SchemaRejection.class, () -> TypedSchemaBindings.bind(catalogue, CommonBindings.SOURCE_SCOPE, scope));
    }

    @Test void actualReferenceDatasetCannotDeclareItselfATaskOrPlanInput() throws Exception {
        var actual = Files.readAllBytes(Path.of(System.getProperty("medical.projectRoot"), "data-design/sample_dataset.json"));
        var schema = new TaskInputSchema(new PermissionSchema());
        assertThrows(SchemaRejection.class, () -> schema.create(actual));
        assertThrows(SchemaRejection.class, () -> schema.plan(actual));
        assertThrows(SchemaRejection.class, () -> SchemaCatalogue.fromClasspath().validate("artifacts", JSON.readTree(actual), "PlanCandidate"));
    }

    @Test void emptyInputsAreRejectedAtTheSchemaBoundary() throws Exception {
        var schema = new TaskInputSchema(new PermissionSchema());
        assertThrows(SchemaRejection.class, () -> schema.create(new byte[0]));
        assertThrows(SchemaRejection.class, () -> schema.plan(new byte[0]));
    }
}
