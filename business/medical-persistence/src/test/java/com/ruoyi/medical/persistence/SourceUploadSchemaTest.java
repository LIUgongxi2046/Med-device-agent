package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.JsonSchema;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import java.nio.file.Files;
import java.nio.file.Path;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import com.ruoyi.medical.contracts.SchemaRejection;
import com.ruoyi.medical.contracts.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SourceUploadSchemaTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    // 业务标识属于获准SIMULATION，资料内容采用登记的官方原件。
    private static final String PROJECT = "31dd0d5d-060a-4ed8-a56a-e6ad9617c60d";
    private static final String BATCH = "d6f4cc72-8db7-4e80-a13f-119cc7cbe59b";

    private JsonSchema schema() throws Exception {
        try (var input = getClass().getResourceAsStream("/source-uploads.schema.json")) {
            assertNotNull(input, "UploadCreate必须提供完整来源与权利声明定义");
            var compiled = SchemaCatalogue.fromClasspath().compileApiSchema(JSON.readTree(input));
            compiled.initializeValidators();
            return compiled;
        }
    }

    private ObjectNode request() throws Exception {
        var dataset = JSON.readTree(Files.readAllBytes(Path.of(System.getProperty("medical.projectRoot"), "data-design/sample_dataset.json")));
        JsonNode source = null;
        for (var candidate : dataset.required("sources"))
            if ("official-clinical-2021-73-a4".equals(candidate.required("source_id").textValue())) source = candidate;
        assertNotNull(source);
        var value = JSON.createObjectNode(); value.put("schema_version", "1.0.0"); value.put("batch_id", BATCH);
        value.put("project_id", PROJECT); value.put("ownership", "PROJECT");
        value.put("file_name", "clinical-evaluation-2021.docx");
        value.put("media_type", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        value.put("size_bytes", source.required("size_bytes").bigIntegerValue().toString()); value.set("sha256", source.required("sha256"));
        value.put("version_intent", "NEW_SOURCE"); value.set("classification", source.required("sensitivity"));
        var provenance = value.putObject("provenance"); provenance.set("owner", source.required("owner"));
        provenance.set("publisher", source.required("owner")); provenance.put("maintainer_id", PROJECT);
        provenance.set("original_url", source.required("official_url")); provenance.set("final_url", source.required("official_url"));
        provenance.set("publisher_version", source.required("publisher_version"));
        for (var name : List.of("published_at", "issued_on", "effective_from", "effective_to", "observed_status", "verified_at",
                "acquired_at", "acquired_by", "acquisition_method", "update_frequency", "next_check_at", "retention_policy_id", "legal_hold"))
            provenance.putNull(name);
        provenance.putArray("status_evidence");
        var rights = value.putObject("rights_declaration"); rights.put("purpose", "LOCAL_PARSER_CHECK");
        rights.set("license", source.required("license")); rights.putArray("allowed_actions").add("READ").add("STORE");
        rights.putArray("providers"); rights.putArray("regions"); rights.putNull("valid_from"); rights.putNull("valid_to");
        rights.putArray("authorization_evidence");
        return value;
    }

    private ObjectNode completionRequest() throws Exception {
        var original = request();
        byte[] bytes = Files.readAllBytes(Path.of(System.getProperty("medical.projectRoot"),
            "ui-delivery/assets/public-evidence/clinical-evaluation-2021.docx"));
        String digest = org.apache.commons.codec.digest.DigestUtils.sha256Hex(bytes);
        assertEquals(original.required("sha256").textValue(), digest);
        assertEquals(original.required("size_bytes").textValue(), Integer.toString(bytes.length));
        var value = JSON.createObjectNode(); value.put("schema_version", "1.0.0"); value.put("expected_revision", "2");
        value.put("version_intent", "NEW_SOURCE"); value.put("size_bytes", Integer.toString(bytes.length)); value.put("sha256", digest);
        var part = value.putArray("parts").addObject(); part.put("part_no", 1); part.put("size_bytes", Integer.toString(bytes.length));
        part.put("sha256", digest);
        return value;
    }

    @Test void registeredOriginalCompletionKeepsTheEntirePartManifestAndVersionIntent() throws Exception {
        var schema = new SourceUploadSchema(new PermissionSchema()); var value = completionRequest();
        assertDoesNotThrow(() -> schema.verify("Completion", value));
    }

    @Test void completionBindingPreservesCanonicalIdentityAndIndependentContent() throws Exception {
        var schema = new SourceUploadSchema(new PermissionSchema()); var value = completionRequest();
        var original = schema.create(JSON.writeValueAsBytes(request()), BigInteger.valueOf(37870));
        var completed = schema.completion(JSON.writeValueAsBytes(value), BigInteger.valueOf(37870), original);
        assertEquals(value, completed.content()); assertEquals(value, JSON.readTree(completed.canonicalJson()));
        assertEquals(BigInteger.TWO, completed.expectedRevision()); assertEquals(original.sizeBytes(), completed.sizeBytes());
        assertEquals("urn:medical:source-uploads:1.0.0#/$defs/Completion", completed.schemaRef());
        assertEquals(original.schemaSha256(), completed.schemaSha256());
        assertEquals(value.required("sha256").textValue(), completed.sha256());
        assertEquals(List.of(new SourceUploadSchema.CompletionPart(1L, completed.sizeBytes(), completed.sha256())), completed.parts());
        ((ObjectNode) completed.content()).removeAll(); assertEquals(value, completed.content());
        assertThrows(UnsupportedOperationException.class, () -> completed.parts().clear());
        var reordered = JSON.createObjectNode(); reordered.set("parts", value.required("parts")); reordered.set("sha256", value.required("sha256"));
        reordered.set("size_bytes", value.required("size_bytes")); reordered.set("version_intent", value.required("version_intent"));
        reordered.set("expected_revision", value.required("expected_revision")); reordered.set("schema_version", value.required("schema_version"));
        assertEquals(completed.requestSha256(), schema.completion(JSON.writeValueAsBytes(reordered), BigInteger.valueOf(37870), original).requestSha256());
        value.put("expected_revision", "3");
        assertNotEquals(completed.requestSha256(), schema.completion(JSON.writeValueAsBytes(value), BigInteger.valueOf(37870), original).requestSha256());
    }

    @Test void completionRejectsMissingDeclarationsAndClientAuthorityObjectAndApprovalFields() throws Exception {
        var schema = new SourceUploadSchema(new PermissionSchema()); var value = completionRequest();
        var names = value.fieldNames();
        while (names.hasNext()) {
            String name = names.next(); var changed = value.deepCopy(); changed.remove(name);
            assertThrows(SchemaRejection.class, () -> schema.verify("Completion", changed), name);
        }
        for (String field : List.of("tenant_id", "project_id", "object_key", "object_version", "grant_id", "scan_passed", "approved_by", "processing_status")) {
            var changed = value.deepCopy(); changed.put(field, PROJECT);
            assertThrows(SchemaRejection.class, () -> schema.verify("Completion", changed), field);
        }
        for (String field : List.of("object_key", "object_version", "state", "write_id")) {
            var changed = value.deepCopy(); ((ObjectNode) changed.required("parts").get(0)).put(field, PROJECT);
            assertThrows(SchemaRejection.class, () -> schema.verify("Completion", changed), field);
        }
    }

    @Test void completionVersionIntentAndOriginalSourceCannotChangeAfterCreation() throws Exception {
        var schema = new SourceUploadSchema(new PermissionSchema()); var create = request(); var value = completionRequest();
        value.put("version_intent", "NEW_VERSION");
        assertThrows(SchemaRejection.class, () -> schema.verify("Completion", value));
        value.put("source_id", PROJECT);
        var original = schema.create(JSON.writeValueAsBytes(create), BigInteger.valueOf(37870));
        assertEquals(ErrorCode.CONTENT_MISMATCH, assertThrows(TenantAccessException.class,
            () -> schema.completion(JSON.writeValueAsBytes(value), BigInteger.valueOf(37870), original)).code());
        create.put("version_intent", "NEW_VERSION"); create.put("source_id", PROJECT);
        var newVersion = schema.create(JSON.writeValueAsBytes(create), BigInteger.valueOf(37870));
        assertDoesNotThrow(() -> schema.completion(JSON.writeValueAsBytes(value), BigInteger.valueOf(37870), newVersion));
        value.put("source_id", BATCH);
        assertEquals(ErrorCode.CONTENT_MISMATCH, assertThrows(TenantAccessException.class,
            () -> schema.completion(JSON.writeValueAsBytes(value), BigInteger.valueOf(37870), newVersion)).code());
        value.put("version_intent", "NEW_SOURCE");
        assertThrows(SchemaRejection.class, () -> schema.verify("Completion", value));
    }

    @Test void completionRequiresExactSizeOriginalDigestAndServerCapacity() throws Exception {
        var schema = new SourceUploadSchema(new PermissionSchema()); var value = completionRequest();
        var original = schema.create(JSON.writeValueAsBytes(request()), BigInteger.valueOf(37870));
        assertEquals(ErrorCode.FILE_TOO_LARGE, assertThrows(TenantAccessException.class,
            () -> schema.completion(JSON.writeValueAsBytes(value), BigInteger.valueOf(37869), original)).code());
        value.put("size_bytes", "37869");
        assertEquals(ErrorCode.CONTENT_MISMATCH, assertThrows(TenantAccessException.class,
            () -> schema.completion(JSON.writeValueAsBytes(value), BigInteger.valueOf(37870), original)).code());
        value.put("size_bytes", "37870");
        String partial = org.apache.commons.codec.digest.DigestUtils.sha256Hex(java.util.Arrays.copyOf(Files.readAllBytes(Path.of(System.getProperty("medical.projectRoot"),
            "ui-delivery/assets/public-evidence/clinical-evaluation-2021.docx")), 16384));
        value.put("sha256", partial);
        assertEquals(ErrorCode.CONTENT_MISMATCH, assertThrows(TenantAccessException.class,
            () -> schema.completion(JSON.writeValueAsBytes(value), BigInteger.valueOf(37870), original)).code());
    }

    @Test void completionRejectsEmptyMissingDuplicatedReorderedAndConflictingPartManifests() throws Exception {
        var schema = new SourceUploadSchema(new PermissionSchema()); var value = completionRequest();
        var original = schema.create(JSON.writeValueAsBytes(request()), BigInteger.valueOf(37870));
        var empty = value.deepCopy(); empty.putArray("parts");
        assertThrows(SchemaRejection.class, () -> schema.completion(JSON.writeValueAsBytes(empty), BigInteger.valueOf(37870), original));
        var repeated = value.deepCopy(); ((com.fasterxml.jackson.databind.node.ArrayNode) repeated.required("parts")).add(value.required("parts").get(0));
        assertEquals(ErrorCode.CONTENT_MISMATCH, assertThrows(TenantAccessException.class,
            () -> schema.completion(JSON.writeValueAsBytes(repeated), BigInteger.valueOf(37870), original)).code());
        var moved = value.deepCopy(); ((ObjectNode) moved.required("parts").get(0)).put("part_no", 2);
        assertEquals(ErrorCode.CONTENT_MISMATCH, assertThrows(TenantAccessException.class,
            () -> schema.completion(JSON.writeValueAsBytes(moved), BigInteger.valueOf(37870), original)).code());
        var shortPart = value.deepCopy(); ((ObjectNode) shortPart.required("parts").get(0)).put("size_bytes", "37869");
        assertEquals(ErrorCode.CONTENT_MISMATCH, assertThrows(TenantAccessException.class,
            () -> schema.completion(JSON.writeValueAsBytes(shortPart), BigInteger.valueOf(37870), original)).code());
        var wrongDigest = value.deepCopy(); ((ObjectNode) wrongDigest.required("parts").get(0)).put("sha256", org.apache.commons.codec.digest.DigestUtils.sha256Hex(java.util.Arrays.copyOf(
            Files.readAllBytes(Path.of(System.getProperty("medical.projectRoot"), "ui-delivery/assets/public-evidence/clinical-evaluation-2021.docx")), 16384)));
        assertEquals(ErrorCode.CONTENT_MISMATCH, assertThrows(TenantAccessException.class,
            () -> schema.completion(JSON.writeValueAsBytes(wrongDigest), BigInteger.valueOf(37870), original)).code());
    }

    @Test void completionKeepsPositiveUnsignedRevisionAndRejectsOverflowAndInvalidPartNumbers() throws Exception {
        var schema = new SourceUploadSchema(new PermissionSchema()); var original = schema.create(JSON.writeValueAsBytes(request()), BigInteger.valueOf(37870));
        var value = completionRequest();
        for (String revision : List.of("0", "-1", "01", "1.1")) {
            value.put("expected_revision", revision);
            assertThrows(SchemaRejection.class, () -> schema.completion(JSON.writeValueAsBytes(value), BigInteger.valueOf(37870), original));
        }
        value.put("expected_revision", "18446744073709551616");
        assertThrows(IllegalArgumentException.class, () -> schema.completion(JSON.writeValueAsBytes(value), BigInteger.valueOf(37870), original));
        value.put("expected_revision", "18446744073709551615");
        assertEquals(new BigInteger("18446744073709551615"), schema.completion(JSON.writeValueAsBytes(value), BigInteger.valueOf(37870), original).expectedRevision());
        for (long partNo : List.of(0L, -1L, 4294967296L)) {
            ((ObjectNode) value.required("parts").get(0)).put("part_no", partNo);
            assertThrows(SchemaRejection.class, () -> schema.completion(JSON.writeValueAsBytes(value), BigInteger.valueOf(37870), original));
        }
    }

    @Test void completionBindsTheActualDigestWhenItsOriginalDeclarationWasUnknown() throws Exception {
        var schema = new SourceUploadSchema(new PermissionSchema()); var create = request(); create.remove("sha256");
        var original = schema.create(JSON.writeValueAsBytes(create), BigInteger.valueOf(37870)); var value = completionRequest();
        assertEquals(value.required("sha256").textValue(), schema.completion(JSON.writeValueAsBytes(value), BigInteger.valueOf(37870), original).sha256());
        value.putNull("sha256");
        assertThrows(SchemaRejection.class, () -> schema.completion(JSON.writeValueAsBytes(value), BigInteger.valueOf(37870), original));
    }

    @Test void completionRejectsMalformedUtf8DuplicateFieldsAndTrailingJsonContent() throws Exception {
        var schema = new SourceUploadSchema(new PermissionSchema()); var original = schema.create(JSON.writeValueAsBytes(request()), BigInteger.valueOf(37870));
        String text = JSON.writeValueAsString(completionRequest());
        assertThrows(java.io.IOException.class, () -> schema.completion((text + " {}").getBytes(StandardCharsets.UTF_8), BigInteger.valueOf(37870), original));
        assertThrows(java.io.IOException.class, () -> schema.completion(("{\"expected_revision\":\"2\"," + text.substring(1))
            .getBytes(StandardCharsets.UTF_8), BigInteger.valueOf(37870), original));
        assertThrows(java.io.IOException.class, () -> schema.completion(new byte[]{(byte) 0xc3, (byte) 0x28}, BigInteger.valueOf(37870), original));
    }

    @Test void registeredOriginalKeepsEveryDeclarationWithoutCreatingApproval() throws Exception {
        var schema = schema(); var request = request();
        assertTrue(schema.validate(request).isEmpty());
        for (var field : List.of("status", "approved_by", "rights_revision", "tenant_id", "object_key", "source_version")) {
            var changed = request.deepCopy(); changed.put(field, PROJECT);
            assertFalse(schema.validate(changed).isEmpty(), field);
        }
        for (var field : List.of("approved", "approved_by", "production_status", "rights_revision")) {
            var changed = request.deepCopy(); ((ObjectNode) changed.required("rights_declaration")).put(field, PROJECT);
            assertFalse(schema.validate(changed).isEmpty(), field);
        }
    }

    @Test void provenanceAndRightsFieldsCannotDisappear() throws Exception {
        var schema = schema(); var original = request();
        for (var container : List.of("provenance", "rights_declaration")) {
            var names = original.required(container).fieldNames();
            while (names.hasNext()) {
                var field = names.next(); var changed = original.deepCopy(); ((ObjectNode) changed.required(container)).remove(field);
                assertFalse(schema.validate(changed).isEmpty(), container + "." + field);
            }
            var changed = original.deepCopy(); ((ObjectNode) changed.required(container)).put("approval", true);
            assertFalse(schema.validate(changed).isEmpty(), container);
        }
    }

    @Test void sessionOwnershipRequiresItsExplicitSessionAndRejectsOtherOwnership() throws Exception {
        var schema = schema(); var value = request(); value.put("ownership", "SESSION");
        assertFalse(schema.validate(value).isEmpty()); value.put("session_id", BATCH);
        assertTrue(schema.validate(value).isEmpty()); value.put("ownership", "ENTERPRISE");
        assertFalse(schema.validate(value).isEmpty()); value.remove("session_id");
        assertTrue(schema.validate(value).isEmpty());
    }

    @Test void newVersionNeedsTheOriginalSourceAndNewSourceCannotSelectAnExistingSource() throws Exception {
        var schema = schema(); var value = request(); value.put("version_intent", "NEW_VERSION");
        assertFalse(schema.validate(value).isEmpty()); value.put("source_id", PROJECT);
        assertTrue(schema.validate(value).isEmpty()); value.put("version_intent", "NEW_SOURCE");
        assertFalse(schema.validate(value).isEmpty()); value.remove("source_id");
        assertTrue(schema.validate(value).isEmpty());
    }

    @Test void sizesUsePositiveUnsignedIntegersAndUnknownDigestRemainsExplicit() throws Exception {
        var schema = schema(); var value = request();
        for (var size : List.of("0", "-1", "1.1", "01")) {
            value.put("size_bytes", size); assertFalse(schema.validate(value).isEmpty(), size);
        }
        value.put("size_bytes", "18446744073709551615"); assertTrue(schema.validate(value).isEmpty());
        value.put("size_bytes", 37870); assertFalse(schema.validate(value).isEmpty());
        value.put("size_bytes", "37870"); value.remove("sha256"); assertTrue(schema.validate(value).isEmpty());
        value.putNull("sha256"); assertTrue(schema.validate(value).isEmpty());
        value.put("sha256", "unknown"); assertFalse(schema.validate(value).isEmpty());
    }

    @Test void classificationsAndDeclarationActionsUseTheirCompletePublishedEnums() throws Exception {
        var schema = schema(); var original = request();
        for (var classification : List.of("PUBLIC", "INTERNAL", "RESTRICTED", "PERSONAL_HEALTH")) {
            original.put("classification", classification); assertTrue(schema.validate(original).isEmpty());
        }
        original.put("classification", "APPROVED"); assertFalse(schema.validate(original).isEmpty());
        original.put("classification", "PUBLIC"); var rights = (ObjectNode) original.required("rights_declaration");
        for (var action : List.of("READ", "STORE", "ENTERPRISE_USE", "DISTRIBUTE", "EXTERNAL_PROCESS")) {
            rights.putArray("allowed_actions").add(action); assertTrue(schema.validate(original).isEmpty());
        }
        rights.putArray("allowed_actions").add("APPROVE"); assertFalse(schema.validate(original).isEmpty());
        rights.putArray("allowed_actions").add("READ").add("READ"); assertFalse(schema.validate(original).isEmpty());
    }

    @Test void acquisitionAndObservationTimesRejectInvalidDatesAndNonUtcOffsets() throws Exception {
        var schema = schema(); var value = request(); var provenance = (ObjectNode) value.required("provenance");
        for (var field : List.of("published_at", "verified_at", "acquired_at", "next_check_at")) {
            provenance.put(field, "2026-10-07T10:20:30.123456Z"); assertTrue(schema.validate(value).isEmpty());
            provenance.put(field, "2026-10-07T18:20:30+08:00"); assertFalse(schema.validate(value).isEmpty());
            provenance.put(field, "2026-02-30T10:20:30Z"); assertFalse(schema.validate(value).isEmpty()); provenance.putNull(field);
        }
        provenance.put("issued_on", "2026-02-30"); assertFalse(schema.validate(value).isEmpty());
    }

    @Test void bindingKeepsFullDeclarationsAndChangesItsIdentityWhenDeclaredActionsChange() throws Exception {
        var schema = new SourceUploadSchema(new PermissionSchema()); var value = request();
        var original = schema.create(JSON.writeValueAsBytes(value), BigInteger.valueOf(37870));
        assertEquals(value, JSON.readTree(original.canonicalJson())); assertEquals(value, original.content());
        assertEquals(BigInteger.valueOf(37870), original.sizeBytes());
        ((ObjectNode) original.content()).removeAll(); assertEquals(value, original.content());
        var changed = value.deepCopy(); ((ObjectNode) changed.required("rights_declaration")).putArray("allowed_actions");
        var emptyDeclaration = schema.create(JSON.writeValueAsBytes(changed), BigInteger.valueOf(37870));
        assertTrue(emptyDeclaration.content().required("rights_declaration").required("allowed_actions").isEmpty());
        assertNotEquals(original.requestSha256(), emptyDeclaration.requestSha256());
        assertEquals(original.schemaRef(), emptyDeclaration.schemaRef());
        assertEquals(original.schemaSha256(), emptyDeclaration.schemaSha256());
    }

    @Test void bindingRejectsOverflowServerCapacityAndFileNameMediaConflicts() throws Exception {
        var schema = new SourceUploadSchema(new PermissionSchema()); var value = request();
        var capacity = assertThrows(TenantAccessException.class,
            () -> schema.create(JSON.writeValueAsBytes(value), BigInteger.valueOf(37869)));
        assertEquals(ErrorCode.FILE_TOO_LARGE, capacity.code());
        value.put("file_name", "clinical-evaluation-2021.doc");
        var media = assertThrows(TenantAccessException.class,
            () -> schema.create(JSON.writeValueAsBytes(value), BigInteger.valueOf(37870)));
        assertEquals(ErrorCode.FILE_TYPE_UNSUPPORTED, media.code());
        value.put("file_name", "../clinical-evaluation-2021.docx");
        assertThrows(SchemaRejection.class, () -> schema.create(JSON.writeValueAsBytes(value), BigInteger.valueOf(37870)));
        value.put("file_name", "clinical-evaluation-2021.docx"); value.put("size_bytes", "18446744073709551616");
        assertThrows(IllegalArgumentException.class, () -> schema.create(JSON.writeValueAsBytes(value), BigInteger.valueOf(37870)));
    }

    @Test void bindingRejectsInvalidJsonEncodingDuplicatesTrailingValuesAndReversedDates() throws Exception {
        var schema = new SourceUploadSchema(new PermissionSchema()); var value = request();
        var bytes = JSON.writeValueAsBytes(value); var text = new String(bytes, StandardCharsets.UTF_8);
        assertThrows(java.io.IOException.class, () -> schema.create((text + " {}").getBytes(StandardCharsets.UTF_8), BigInteger.valueOf(37870)));
        assertThrows(java.io.IOException.class, () -> schema.create(("{\"batch_id\":\"" + BATCH + "\"," + text.substring(1))
            .getBytes(StandardCharsets.UTF_8), BigInteger.valueOf(37870)));
        assertThrows(java.io.IOException.class, () -> schema.create(new byte[]{(byte) 0xc3, (byte) 0x28}, BigInteger.valueOf(37870)));
        var provenance = (ObjectNode) value.required("provenance");
        provenance.put("effective_from", "2026-10-07"); provenance.put("effective_to", "2026-10-06");
        assertThrows(SchemaRejection.class, () -> schema.create(JSON.writeValueAsBytes(value), BigInteger.valueOf(37870)));
        provenance.putNull("effective_from"); provenance.putNull("effective_to");
        var rights = (ObjectNode) value.required("rights_declaration");
        rights.put("valid_from", "2026-10-07T10:20:30.123456Z"); rights.put("valid_to", "2026-10-07T10:20:30.123456Z");
        assertThrows(SchemaRejection.class, () -> schema.create(JSON.writeValueAsBytes(value), BigInteger.valueOf(37870)));
    }
}
