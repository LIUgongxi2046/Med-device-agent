package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.CommonProtocol;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.InterfaceProtocol;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import com.ruoyi.medical.contracts.SchemaRejection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import static org.junit.jupiter.api.Assertions.*;

/** Pure local RECORD_ONLY contract checks; no service, database, model, or network execution. */
class GruSchemaTest {
    private static final JsonMapper JSON = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build();
    private static final String PROJECT = id(1), DOCUMENT = id(2), REVISION = id(3), SNAPSHOT = id(4);
    private static final String SESSION = id(5), CONVERSATION = id(6);
    private static final String CREATE_ROUTE = "/api/gru-conversations";
    private static final String APPEND_ROUTE = CREATE_ROUTE + "/" + CONVERSATION + "/messages";
    private final GruSchema schema = new GruSchema();
    private final SchemaCatalogue shared = SchemaCatalogue.fromClasspath();

    GruSchemaTest() throws Exception { }

    @Test void acceptsClosedLocalCreateAndAppendWithoutAddingExecutionClaims() throws Exception {
        var create = create(); var append = append();
        assertEquals(create, schema.request("Create", bytes(create)));
        assertEquals(append, schema.request("Append", bytes(append)));
        assertEquals("RECORD_ONLY", append.required("execution_mode").textValue());
        assertTrue(append.required("selection").isNull());
        assertFalse(append.has("run_id")); assertFalse(append.has("candidate_ids"));
    }

    @Test void requiresEveryDeclaredFieldIncludingExplicitNullSelectionAndNetworkRevisions() throws Exception {
        for (String type : List.of("Create", "Append")) {
            var valid = request(type); var fields = new ArrayList<String>(); valid.fieldNames().forEachRemaining(fields::add);
            for (String field : fields) {
                var bad = valid.deepCopy(); bad.remove(field);
                invalid(() -> schema.request(type, bytes(bad)), type + ": " + field);
            }
            for (String field : List.of("mode", "collection_versions", "source_versions", "network_enabled",
                    "network_policy_revision", "approved_query_revision")) {
                var bad = valid.deepCopy(); ((ObjectNode) bad.required("scope")).remove(field);
                invalid(() -> schema.request(type, bytes(bad)), type + ": scope." + field);
            }
        }
    }

    @Test void rejectsCallerSuppliedIdentityAuthorityStorageAndExecutionFields() throws Exception {
        for (String type : List.of("Create", "Append")) {
            for (String field : List.of("tenant_id", "owner_id", "created_by", "created_at", "conversation_id",
                    "message_id", "run_id", "operation_id", "candidate_ids", "pending_action_ids", "status",
                    "sender_kind", "access_set_id", "content_ref", "content_hash", "scope_manifest_hash",
                    "schema_manifest", "context_revision", "receipt", "execution_state", "professional_approval")) {
                var bad = request(type).put(field, "injected");
                invalid(() -> schema.request(type, bytes(bad)), type + ": " + field);
            }
            var bad = request(type); ((ObjectNode) bad.required("scope")).put("all_sources", true);
            invalid(() -> schema.request(type, bytes(bad)), type + ": unknown SourceScope field");
        }
        invalid(() -> schema.request("Create", bytes(create().put("text", "not a create field"))), "create text");
        invalid(() -> schema.request("Append", bytes(append().put("document_id", DOCUMENT))), "append document retarget");
    }

    @Test void validatesSchemaVersionAndCanonicalUuidFieldsWithoutCoercion() throws Exception {
        for (String type : List.of("Create", "Append")) {
            invalid(() -> schema.request(type, bytes(request(type).put("schema_version", "2.0.0"))), type + ": version");
            var fields = type.equals("Create")
                    ? List.of("project_id", "document_id", "document_revision", "scope_snapshot_id")
                    : List.of("document_revision", "scope_snapshot_id");
            for (String field : fields) {
                for (String value : List.of("not-a-uuid", "ABCDEF00-0000-4000-8000-000000000001", ""))
                    invalid(() -> schema.request(type, bytes(request(type).put(field, value))), type + ": " + field);
                invalid(() -> schema.request(type, bytes(request(type).putNull(field))), type + ": null " + field);
                invalid(() -> schema.request(type, bytes(request(type).put(field, 1))), type + ": numeric " + field);
            }
        }
    }

    @Test void acceptsAllFourFrozenLocalSourceScopeModesAndPreservesExplicitEmptySets() throws Exception {
        for (String mode : List.of("PROJECT_APPROVED", "COLLECTION_VERSIONS", "SOURCE_VERSIONS", "COMBINED")) {
            for (String type : List.of("Create", "Append")) {
                var value = request(type); value.set("scope", scope(mode));
                var accepted = schema.request(type, bytes(value));
                var bound = schema.scope(accepted.required("scope"));
                assertEquals(mode, bound.mode().name()); assertFalse(bound.networkEnabled());
                assertEquals(value.required("scope"), JSON.valueToTree(bound));
                var empty = scope(mode); empty.withArray("collection_versions").removeAll(); empty.withArray("source_versions").removeAll();
                value.set("scope", empty);
                assertEquals(empty, schema.request(type, bytes(value)).required("scope"), "No widening of an explicit empty set");
            }
        }
    }

    @Test void rejectsInvalidModeMembershipDuplicateVersionsAndUnknownFrozenFields() throws Exception {
        var values = new ArrayList<ObjectNode>();
        values.add(scope("OTHER"));
        var project = scope("PROJECT_APPROVED"); project.withArray("source_versions").add(id(20)); values.add(project);
        var collections = scope("COLLECTION_VERSIONS"); collections.withArray("source_versions").add(id(20)); values.add(collections);
        var sources = scope("SOURCE_VERSIONS"); sources.withArray("collection_versions").add(id(21)); values.add(sources);
        var duplicate = scope("SOURCE_VERSIONS"); duplicate.withArray("source_versions").add(id(20)); values.add(duplicate);
        var malformed = scope("SOURCE_VERSIONS"); malformed.withArray("source_versions").add("unknown"); values.add(malformed);
        values.add(scope("PROJECT_APPROVED").put("network_enabled", "false"));
        values.add(scope("PROJECT_APPROVED").put("unpublished_mode", "ALL"));
        for (var badScope : values) {
            var request = create(); request.set("scope", badScope);
            invalid(() -> schema.request("Create", bytes(request)), badScope.toString());
        }
    }

    @Test void rejectsWellFormedNetworkScopesAsUnsupportedAndMalformedNetworkScopesAsInvalid() throws Exception {
        for (String type : List.of("Create", "Append")) {
            var network = scope("PROJECT_APPROVED").put("network_enabled", true)
                    .put("network_policy_revision", id(22)).put("approved_query_revision", id(23));
            shared.validate("interfaces", network, "SourceScope");
            var request = request(type); request.set("scope", network);
            assertEquals(ErrorCode.SCHEMA_UNSUPPORTED,
                    assertThrows(SchemaRejection.class, () -> schema.request(type, bytes(request))).code());
            for (String field : List.of("network_policy_revision", "approved_query_revision")) {
                var invalidNetwork = network.deepCopy().putNull(field); var bad = request(type); bad.set("scope", invalidNetwork);
                invalid(() -> schema.request(type, bytes(bad)), type + ": network missing " + field);
                var disabled = scope("PROJECT_APPROVED").put(field, id(22)); var disabledRequest = request(type); disabledRequest.set("scope", disabled);
                invalid(() -> schema.request(type, bytes(disabledRequest)), type + ": disabled network revision");
            }
        }
    }

    @Test void acceptsOnlyTheFourIntentsWithExplicitRecordOnlyExecution() throws Exception {
        for (String intent : List.of("QUERY", "RETRIEVE", "CHECK", "PROPOSE_EDIT"))
            assertEquals(intent, schema.request("Append", bytes(append().put("intent", intent))).required("intent").textValue());
        for (String intent : List.of("EXECUTE", "APPROVE", "SIGN", "QUERY_AND_SEND", "query", ""))
            invalid(() -> schema.request("Append", bytes(append().put("intent", intent))), intent);
        for (String mode : List.of("EXECUTE", "LIVE", "SIMULATION", "RECORD", "record_only", ""))
            invalid(() -> schema.request("Append", bytes(append().put("execution_mode", mode))), mode);
    }

    @Test void expectedRevisionIsAnUnsignedDecimalStringWithNoOverflowOrCoercion() throws Exception {
        for (String revision : List.of("0", "1", "9007199254740992", "18446744073709551615"))
            assertEquals(revision, schema.request("Append", bytes(append().put("expected_revision", revision))).required("expected_revision").textValue());
        for (String revision : List.of("", "01", "+1", "-1", "1.0", "1e2", " 1", "18446744073709551616", "99999999999999999999"))
            invalid(() -> schema.request("Append", bytes(append().put("expected_revision", revision))), revision);
        invalid(() -> schema.request("Append", bytes(append().put("expected_revision", 1))), "numeric revision");
        invalid(() -> schema.request("Append", bytes(append().putNull("expected_revision"))), "null revision");
    }

    @Test void preservesExactUserTextIncludingWhitespaceCombiningCharactersAndSupplementaryCodePoints() throws Exception {
        String text = "  原文 👩‍🔬 e\u0301\r\n\t尾部  ";
        var request = append().put("text", text);
        var accepted = schema.request("Append", bytes(request));
        assertEquals(text, accepted.required("text").textValue());
        assertArrayEquals(text.getBytes(StandardCharsets.UTF_8), accepted.required("text").textValue().getBytes(StandardCharsets.UTF_8));
    }

    @Test void rejectsBlankUserTextAndNonTextControlsWithoutSilentlyNormalizing() throws Exception {
        for (String text : List.of("", " \t\r\n", "\u2003", "\u0000", "x\u0001y", "x\u007fy"))
            invalid(() -> schema.request("Append", bytes(append().put("text", text))), "invalid text");
        invalid(() -> schema.request("Append", bytes(append().putNull("text"))), "null text");
        invalid(() -> schema.request("Append", bytes(append().put("text", 12))), "numeric text");
    }

    @Test void enforcesMessageAndReasonLengthsAndRawByteCapacityWithoutTruncating() throws Exception {
        String text = "x".repeat(16384), reason = "r".repeat(2000);
        var limit = append().put("text", text).put("reason", reason);
        assertEquals(limit, schema.request("Append", bytes(limit)));
        invalid(() -> schema.request("Append", bytes(limit.deepCopy().put("text", text + "x"))), "text overflow");
        for (String type : List.of("Create", "Append")) {
            invalid(() -> schema.request(type, bytes(request(type).put("reason", reason + "r"))), type + ": reason overflow");
            for (String value : List.of("", " ", "\u2003", " reason", "reason ", "reason\n"))
                invalid(() -> schema.request(type, bytes(request(type).put("reason", value))), type + ": reason");
            invalid(() -> schema.request(type, new byte[GruSchema.MAX_BYTES + 1]), type + ": raw byte capacity");
        }
        var base = bytes(append()); var padded = new byte[GruSchema.MAX_BYTES];
        System.arraycopy(base, 0, padded, 0, base.length); java.util.Arrays.fill(padded, base.length, padded.length, (byte) ' ');
        assertEquals(append(), schema.request("Append", padded));
    }

    @Test void textLengthCountsCodePointsWhileRequestCapacityCountsUtf8Bytes() throws Exception {
        // Semantic length is independent of the wire representation and its JSON envelope.
        String maximumText = "😀".repeat(16384);
        var atCharacterLimit = append().put("text", maximumText);
        assertEquals(16384, maximumText.codePointCount(0, maximumText.length()));
        assertSame(atCharacterLimit, schema.validate("Append", atCharacterLimit));
        invalid(() -> schema.validate("Append", append().put("text", maximumText + "😀")), "code-point capacity");

        // Even unescaped UTF-8 text consumes all 64 KiB before the required envelope is added.
        assertEquals(GruSchema.MAX_BYTES, maximumText.getBytes(StandardCharsets.UTF_8).length);
        byte[] oversized = JSON.writeValueAsString(atCharacterLimit).getBytes(StandardCharsets.UTF_8);
        assertTrue(oversized.length > GruSchema.MAX_BYTES);
        invalid(() -> schema.request("Append", oversized), "complete UTF-8 request byte capacity");
        String text = "😀".repeat(1000); byte[] bounded = bytes(append().put("text", text));
        assertTrue(bounded.length <= GruSchema.MAX_BYTES);
        assertEquals(text, schema.request("Append", bounded).required("text").textValue());
    }

    @Test void rejectsEmptyMalformedDuplicateTrailingAndNonObjectJson() throws Exception {
        for (String type : List.of("Create", "Append")) {
            invalid(() -> schema.request(type, null), type + ": null bytes");
            invalid(() -> schema.request(type, new byte[0]), type + ": empty bytes");
            for (String value : List.of(" ", "null", "[]", "true", "1", "\"text\"", "{}", "{", "{\"schema_version\":}", "/*comment*/{}"))
                invalid(() -> schema.request(type, value.getBytes(StandardCharsets.UTF_8)), type + ": " + value);
            String original = JSON.writeValueAsString(request(type));
            invalid(() -> schema.request(type, (original + " {}").getBytes(StandardCharsets.UTF_8)), type + ": trailing object");
            invalid(() -> schema.request(type, (original + "garbage").getBytes(StandardCharsets.UTF_8)), type + ": trailing garbage");
            String duplicate = original.replace("\"schema_version\":", "\"schema_version\":\"1.0.0\",\"schema_version\":");
            invalid(() -> schema.request(type, duplicate.getBytes(StandardCharsets.UTF_8)), type + ": duplicate top-level key");
            String nested = original.replace("\"network_enabled\":", "\"network_enabled\":false,\"network_enabled\":");
            invalid(() -> schema.request(type, nested.getBytes(StandardCharsets.UTF_8)), type + ": duplicate nested key");
        }
    }

    @Test void rejectsMalformedUtf8AndUnpairedEscapedSurrogatesAsSchemaInvalid() throws Exception {
        String original = JSON.writeValueAsString(append());
        byte[] malformed = original.getBytes(StandardCharsets.UTF_8);
        int marker = original.indexOf("question"); assertTrue(marker >= 0); malformed[marker] = (byte) 0xff;
        invalid(() -> schema.request("Append", malformed), "malformed UTF-8");
        for (String escaped : List.of("\\ud800", "\\udc00", "x\\ud800x")) {
            String bad = original.replace("question", escaped);
            invalid(() -> schema.request("Append", bad.getBytes(StandardCharsets.UTF_8)), "unpaired escaped surrogate");
        }
    }

    @Test void bindsTheFrozenSelectionWithoutInventingASeparateLocalShape() throws Exception {
        var selection = selection(); var request = append(); request.set("selection", selection);
        // JSON integer node implementations may differ after parsing/binding; canonical values must not.
        assertEquals(schema.canonical(selection), schema.canonical(schema.request("Append", bytes(request)).required("selection")));
        assertEquals(schema.canonical(selection), schema.canonical(JSON.valueToTree(schema.selection(selection))));
        assertNull(schema.selection(JSON.nullNode()));
        var unknown = selection.deepCopy().put("selected_text", "caller-supplied"); request.set("selection", unknown);
        invalid(() -> schema.request("Append", bytes(request)), "unknown Selection property");
        var empty = selection.deepCopy(); empty.withArray("segments").removeAll(); request.set("selection", empty);
        invalid(() -> schema.request("Append", bytes(request)), "empty Selection");
        var unknownSegment = selection.deepCopy(); ((ObjectNode) unknownSegment.withArray("segments").get(0)).put("start_offset", 1);
        request.set("selection", unknownSegment);
        invalid(() -> schema.request("Append", bytes(request)), "unknown segment property");
    }

    @Test void canonicalNullAndNullableSelectionRoundTripWithoutInventingAnObject() throws Exception {
        assertEquals("null", schema.canonical(null));
        assertEquals("null", schema.canonical(JSON.nullNode()));
        var request = schema.request("Append", bytes(append()));
        String storedSelection = schema.canonical(request.required("selection"));
        assertEquals("null", storedSelection);
        var restored = schema.read(storedSelection); assertTrue(restored.isNull());
        assertNull(schema.selection(restored));
        assertDoesNotThrow(() -> schema.selection(REVISION, body(), restored));
        for (JsonNode value : List.of(append(), selection(), JSON.createArrayNode()))
            assertEquals(new String(ContentReadAuthorization.canonicalBytes(value), StandardCharsets.UTF_8), schema.canonical(value));
    }

    @Test void validatesSelectionAgainstTheExactFixedBodyAndDocumentRevision() throws Exception {
        var selected = selection(); var body = body();
        assertDoesNotThrow(() -> DocumentBodySemantics.verify(shared, JSON.valueToTree(body), new DocumentBodySemantics.Limits(100, 100, 1000)));
        assertDoesNotThrow(() -> schema.selection(REVISION, body, selected));
        assertDoesNotThrow(() -> schema.selection(REVISION, body, JSON.nullNode()));
        assertEquals(ErrorCode.SELECTION_STALE,
                assertThrows(TenantAccessException.class, () -> schema.selection(id(90), body, selected)).code());
        var stale = selected.deepCopy(); ((ObjectNode) stale.withArray("segments").get(0)).put("node_sha256", "0".repeat(64));
        assertEquals(ErrorCode.SELECTION_STALE,
                assertThrows(TenantAccessException.class, () -> schema.selection(REVISION, body, stale)).code());
        var staleQuote = selected.deepCopy(); ((ObjectNode) staleQuote.withArray("segments").get(0)).put("quote_sha256", "0".repeat(64));
        assertEquals(ErrorCode.SELECTION_STALE,
                assertThrows(TenantAccessException.class, () -> schema.selection(REVISION, body, staleQuote)).code());
        var unknown = selected.deepCopy(); ((ObjectNode) unknown.withArray("segments").get(0)).put("node_id", id(99));
        assertEquals(ErrorCode.SELECTION_INVALID,
                assertThrows(TenantAccessException.class, () -> schema.selection(REVISION, body, unknown)).code());
        var outside = selected.deepCopy(); ((ObjectNode) outside.withArray("segments").get(0)).put("end_cp", 7);
        assertEquals(ErrorCode.SELECTION_INVALID,
                assertThrows(TenantAccessException.class, () -> schema.selection(REVISION, body, outside)).code());
    }

    @Test void recordOnlyMessageUsesExactlyTheFrozenMessageDefinition() throws Exception {
        var value = message(); shared.validate("resource-details", value, "Message");
        assertSame(value, schema.message(value));
        var fields = new HashSet<String>(); value.fieldNames().forEachRemaining(fields::add);
        assertEquals(Set.of("message_id", "conversation_id", "project_id", "document_revision", "selection", "scope",
                "sender_kind", "content", "run_id", "operation_id", "candidate_ids", "created_at"), fields);
        assertEquals("USER", value.required("sender_kind").textValue());
        assertTrue(value.required("run_id").isNull()); assertTrue(value.required("candidate_ids").isEmpty());
        assertEquals("  原文 e\u0301 😀\n", value.required("content").textValue());
    }

    @Test void rejectsFabricatedAgentMessagesRunsCandidatesAndLocalFieldsInFrozenMessages() throws Exception {
        for (String sender : List.of("AGENT", "SYSTEM", "ASSISTANT"))
            invalid(() -> schema.message(message().put("sender_kind", sender)), "sender " + sender);
        invalid(() -> schema.message(message().put("run_id", id(40))), "fabricated run");
        var candidates = message(); candidates.withArray("candidate_ids").add(id(41));
        invalid(() -> schema.message(candidates), "fabricated candidate");
        for (String field : List.of("execution_mode", "execution_state", "intent", "schema_version", "content_ref", "pending_action_ids"))
            invalid(() -> schema.message(message().put(field, "injected")), "unpublished Message field " + field);
        for (String field : List.of("selection", "run_id", "operation_id", "candidate_ids", "created_at")) {
            var missing = message(); missing.remove(field); invalid(() -> schema.message(missing), "missing " + field);
        }
        invalid(() -> schema.message(message().put("created_at", "2026-10-18")), "non-timestamp");
    }

    @Test void requestIdentityCanonicalizesObjectOrderButPreservesTextAndScopeArrayOrder() throws Exception {
        var request = append(); var fields = new ArrayList<String>(); request.fieldNames().forEachRemaining(fields::add);
        Collections.reverse(fields); var reordered = JSON.createObjectNode(); fields.forEach(field -> reordered.set(field, request.get(field)));
        String expected = schema.requestHash(APPEND_ROUTE, request, "7", SESSION);
        assertEquals(expected, schema.requestHash(APPEND_ROUTE, reordered, "7", SESSION));
        assertNotEquals(expected, schema.requestHash(APPEND_ROUTE, request.deepCopy().put("text", "question "), "7", SESSION));
        assertNotEquals(schema.hash(append().put("text", "é")), schema.hash(append().put("text", "e\u0301")));
        var ordered = append(); var first = scope("SOURCE_VERSIONS"); first.withArray("source_versions").add(id(24)); ordered.set("scope", first);
        var changed = ordered.deepCopy(); var second = ((ObjectNode) changed.required("scope")).withArray("source_versions");
        second.removeAll(); second.add(id(24)).add(id(20));
        assertNotEquals(schema.requestHash(APPEND_ROUTE, ordered, "7", SESSION), schema.requestHash(APPEND_ROUTE, changed, "7", SESSION));
    }

    @Test void requestIdentityBindsRouteSessionContextManifestAndEveryRequestField() throws Exception {
        var request = append(); String hash = schema.requestHash(APPEND_ROUTE, request, "7", SESSION);
        assertEquals(64, hash.length()); assertTrue(hash.matches("[0-9a-f]{64}"));
        String independentlyComputed = DigestUtils.sha256Hex(ContentReadAuthorization.canonicalBytes(Map.of(
                "method", "POST", "route", APPEND_ROUTE, "request", request, "context_revision", "7",
                "session_id", SESSION, "manifest", schema.manifest())));
        assertEquals(independentlyComputed, hash);
        assertNotEquals(hash, schema.requestHash(CREATE_ROUTE, request, "7", SESSION));
        assertNotEquals(hash, schema.requestHash(CREATE_ROUTE + "/" + id(60) + "/messages", request, "7", SESSION));
        assertNotEquals(hash, schema.requestHash(APPEND_ROUTE, request, "8", SESSION));
        assertNotEquals(hash, schema.requestHash(APPEND_ROUTE, request, "7", id(61)));
        for (String field : List.of("expected_revision", "document_revision", "scope_snapshot_id", "intent", "text", "reason")) {
            var changed = request.deepCopy();
            changed.put(field, switch (field) {
                case "expected_revision" -> "2"; case "document_revision", "scope_snapshot_id" -> id(62);
                case "intent" -> "CHECK"; default -> "changed";
            });
            assertNotEquals(hash, schema.requestHash(APPEND_ROUTE, changed, "7", SESSION), field);
        }
        var selected = request.deepCopy(); selected.set("selection", selection());
        assertNotEquals(hash, schema.requestHash(APPEND_ROUTE, selected, "7", SESSION));
        var scoped = request.deepCopy(); scoped.set("scope", scope("SOURCE_VERSIONS"));
        assertNotEquals(hash, schema.requestHash(APPEND_ROUTE, scoped, "7", SESSION));
    }

    @Test void manifestBindsLocalSchemaAndBothFrozenSharedDocuments() throws Exception {
        byte[] local;
        try (var input = GruSchema.class.getResourceAsStream("/gru-records.schema.json")) {
            assertNotNull(input); local = input.readAllBytes();
        }
        String expected = DigestUtils.sha256Hex(ContentReadAuthorization.canonicalBytes(Map.of(
                "local", DigestUtils.sha256Hex(local), "interfaces", shared.schemaDigest("interfaces"),
                "resource-details", shared.schemaDigest("resource-details"))));
        assertEquals(expected, schema.manifest()); assertEquals(schema.manifest(), new GruSchema().manifest());
    }

    @Test void acceptsEachClosedLocalResponseWithoutChangingItsFrozenContext() {
        for (String type : List.of("Context", "Receipt", "View", "Read", "List")) {
            var value = response(type);
            assertSame(value, schema.response(type, value), type);
        }
        var created = response("Receipt");
        assertTrue(created.required("message_id").isNull());
        assertSame(created, schema.response("Receipt", created));
        var appended = created.deepCopy().put("message_id", id(7)).put("revision", "2");
        assertSame(appended, schema.response("Receipt", appended));
        var empty = response("Read").put("revision", "1");
        empty.withArray("messages").removeAll(); empty.withArray("message_records").removeAll();
        ((ObjectNode) empty.required("page_info")).put("total", "0");
        assertSame(empty, schema.response("Read", empty));
    }

    @Test void everyLocalResponseIsClosedAndRequiresEveryDeclaredField() {
        for (String type : List.of("Context", "Receipt", "View", "Read", "List")) {
            var value = response(type); var fields = new ArrayList<String>(); value.fieldNames().forEachRemaining(fields::add);
            for (String field : fields) {
                var missing = value.deepCopy(); missing.remove(field);
                invalid(() -> schema.response(type, missing), type + ": missing " + field);
            }
            for (String field : List.of("run_id", "candidate_ids", "stream_cursor", "pending_action_ids", "approval"))
                invalid(() -> schema.response(type, value.deepCopy().put(field, "invented")), type + ": unknown " + field);
            invalid(() -> schema.response(type, value.deepCopy().put("schema_version", "2.0.0")), type + ": version");
            invalid(() -> schema.response(type, JSON.nullNode()), type + ": null");
        }
        for (String type : List.of("Receipt", "View", "Read"))
            invalid(() -> schema.response(type, response(type).put("execution_state", "COMPLETED")), type + ": execution claim");
        for (String type : List.of("View", "Read", "List"))
            invalid(() -> schema.response(type, response(type).put("mode", "LIVE")), type + ": live mode");
        for (String type : List.of("View", "Read"))
            invalid(() -> schema.response(type, response(type).put("status", "ARCHIVED")), type + ": unsupported status");
    }

    @Test void responseScopeValidationReusesTheFrozenShapeIncludingNestedListItems() {
        for (String type : List.of("Context", "View", "Read")) {
            var invalidScope = response(type); ((ObjectNode) invalidScope.required("scope")).put("unpublished_scope", true);
            invalid(() -> schema.response(type, invalidScope), type + ": unknown scope field");
            var missingPolicy = response(type); ((ObjectNode) missingPolicy.required("scope")).remove("network_policy_revision");
            invalid(() -> schema.response(type, missingPolicy), type + ": missing explicit null policy");
        }
        var listed = response("List"); var view = (ObjectNode) listed.withArray("items").get(0);
        ((ObjectNode) view.required("scope")).put("mode", "EVERYTHING");
        invalid(() -> schema.response("List", listed), "invalid SourceScope inside List");
        var extra = response("List"); ((ObjectNode) extra.withArray("items").get(0)).put("run_id", id(40));
        invalid(() -> schema.response("List", extra), "unknown field inside nested View");
    }

    @Test void readResponseDelegatesEveryMessageToTheClosedUserOnlyFrozenDefinition() {
        for (String sender : List.of("AGENT", "SYSTEM")) {
            var read = response("Read"); ((ObjectNode) read.withArray("messages").get(0)).put("sender_kind", sender);
            invalid(() -> schema.response("Read", read), "fabricated " + sender);
        }
        var run = response("Read"); ((ObjectNode) run.withArray("messages").get(0)).put("run_id", id(40));
        invalid(() -> schema.response("Read", run), "fabricated message run");
        var candidate = response("Read"); ((ObjectNode) candidate.withArray("messages").get(0)).withArray("candidate_ids").add(id(41));
        invalid(() -> schema.response("Read", candidate), "fabricated message candidate");
        var extra = response("Read"); ((ObjectNode) extra.withArray("messages").get(0)).put("execution_state", "NOT_EXECUTED");
        invalid(() -> schema.response("Read", extra), "local metadata inside frozen Message");
        var missing = response("Read"); ((ObjectNode) missing.withArray("messages").get(0)).remove("selection");
        invalid(() -> schema.response("Read", missing), "missing required frozen Message field");
        var scope = response("Read"); ((ObjectNode) scope.withArray("messages").get(0).required("scope")).put("unknown", true);
        invalid(() -> schema.response("Read", scope), "unknown field in message SourceScope");
    }

    @Test void readMetadataAndPagingRemainClosedAndRecordOnly() {
        var unknownRecord = response("Read"); ((ObjectNode) unknownRecord.withArray("message_records").get(0)).put("run_id", id(40));
        invalid(() -> schema.response("Read", unknownRecord), "unknown message-record field");
        var executed = response("Read"); ((ObjectNode) executed.withArray("message_records").get(0)).put("execution_state", "COMPLETED");
        invalid(() -> schema.response("Read", executed), "executed message record");
        var intent = response("Read"); ((ObjectNode) intent.withArray("message_records").get(0)).put("intent", "APPROVE");
        invalid(() -> schema.response("Read", intent), "unsupported message intent");
        var unknownPage = response("Read"); ((ObjectNode) unknownPage.required("page_info")).put("stream_cursor", "invented");
        invalid(() -> schema.response("Read", unknownPage), "unknown page field");
        for (String field : List.of("page", "page_size")) {
            var invalidPage = response("Read"); ((ObjectNode) invalidPage.required("page_info")).put(field, 0);
            invalid(() -> schema.response("Read", invalidPage), "nonpositive " + field);
        }
        var oversizedPage = response("Read"); ((ObjectNode) oversizedPage.required("page_info")).put("page_size", 101);
        invalid(() -> schema.response("Read", oversizedPage), "oversized page");
        var total = response("Read"); ((ObjectNode) total.required("page_info")).put("total", 1);
        invalid(() -> schema.response("Read", total), "numeric total instead of decimal string");
        var watermark = response("Read"); ((ObjectNode) watermark.required("page_info")).put("dataset_watermark", "unknown");
        invalid(() -> schema.response("Read", watermark), "invalid watermark");
        var next = response("Read"); ((ObjectNode) next.required("page_info")).put("next_page", 1);
        invalid(() -> schema.response("Read", next), "invalid next page");
    }

    @Test void boundedReadAndListRejectOverflowAndUnpairedMessageMetadata() {
        var shortMetadata = response("Read"); shortMetadata.withArray("message_records").removeAll();
        invalid(() -> schema.response("Read", shortMetadata), "missing message metadata");
        var noMessage = response("Read"); noMessage.withArray("messages").removeAll();
        invalid(() -> schema.response("Read", noMessage), "orphaned message metadata");
        var page = response("Read").put("revision", "101");
        var messages = page.withArray("messages"); messages.removeAll();
        var records = page.withArray("message_records"); records.removeAll();
        for (int index = 0; index < 100; index++) {
            messages.add(message().put("message_id", id(1000 + index)));
            records.add(messageRecord(id(1000 + index), Integer.toString(index + 2)));
        }
        ((ObjectNode) page.required("page_info")).put("page_size", 100).put("total", "100");
        assertSame(page, schema.response("Read", page));
        messages.add(message().put("message_id", id(1100))); records.add(messageRecord(id(1100), "102"));
        invalid(() -> schema.response("Read", page), "more than 100 page entries");
        var list = response("List"); list.withArray("items").removeAll();
        for (int index = 0; index < 100; index++) list.withArray("items").add(response("View").put("conversation_id", id(2000 + index)));
        assertSame(list, schema.response("List", list));
        list.withArray("items").add(response("View").put("conversation_id", id(2100)));
        invalid(() -> schema.response("List", list), "more than 100 conversation views");
    }

    @Test void everyResponseScopeRemainsNetworkDisabledEvenWhenTheSharedScopeIsWellFormed() {
        var network = scope("PROJECT_APPROVED").put("network_enabled", true)
                .put("network_policy_revision", id(22)).put("approved_query_revision", id(23));
        shared.validate("interfaces", network, "SourceScope");
        for (String type : List.of("Context", "View", "Read")) {
            var value = response(type); value.set("scope", network);
            invalid(() -> schema.response(type, value), type + ": network-enabled response");
        }
        var listed = response("List"); ((ObjectNode) listed.withArray("items").get(0)).set("scope", network);
        invalid(() -> schema.response("List", listed), "network-enabled nested View");
    }

    @Test void readResponseRejectsMismatchedSidecarIdentityDuplicateMessagesAndCrossContextMessages() {
        var wrongRecord = response("Read"); ((ObjectNode) wrongRecord.withArray("message_records").get(0)).put("message_id", id(90));
        invalid(() -> schema.response("Read", wrongRecord), "sidecar names another message");
        var duplicate = response("Read").put("revision", "3");
        duplicate.withArray("messages").add(message()); duplicate.withArray("message_records").add(messageRecord(id(7), "3"));
        ((ObjectNode) duplicate.required("page_info")).put("total", "2");
        invalid(() -> schema.response("Read", duplicate), "duplicate message identity");
        for (String field : List.of("conversation_id", "project_id", "document_revision")) {
            var different = response("Read"); ((ObjectNode) different.withArray("messages").get(0)).put(field, id(90));
            invalid(() -> schema.response("Read", different), "message has different " + field);
        }
        var differentScope = response("Read"); ((ObjectNode) differentScope.withArray("messages").get(0)).set("scope", scope("SOURCE_VERSIONS"));
        invalid(() -> schema.response("Read", differentScope), "message belongs to another fixed scope");
    }

    @Test void recoveryRequestKeepsOriginalAppendClosedAndRequiresOriginalKey() throws Exception {
        var value = JSON.createObjectNode().put("schema_version", "1.0.0").put("idempotency_key", "original-append-key");value.set("request", append());
        assertEquals(value, schema.request("RecoveryRequest", bytes(value)));
        invalid(() -> schema.request("RecoveryRequest", bytes(value.deepCopy().put("idempotency_key", "short"))), "original key");
        var altered = value.deepCopy();((ObjectNode)altered.required("request")).put("execution_mode", "EXECUTE");
        invalid(() -> schema.request("RecoveryRequest", bytes(altered)), "cannot submit execution through recovery");
    }

    @Test void recoveryProofStatusRevisionAndReceiptBindingsAreClosed() throws Exception {
        var proof = recoveryProof();assertSame(proof,schema.response("Recovery", proof));
        for(String key:List.of("actor_id","current_context","idempotency_key","request_body_sha256","expected_revision","current_revision","checked_at","receipt")){
            var altered=proof.deepCopy();altered.remove(key);invalid(()->schema.response("Recovery",altered),key);
        }
        invalid(()->schema.response("Recovery",proof.deepCopy().put("current_revision","1")),"stale proof needs strictly advanced CAS");
        invalid(()->schema.response("Recovery",proof.deepCopy().put("status","UNKNOWN")),"advanced head cannot be unknown with proven absent key");
        var unknown=proof.deepCopy().put("status","UNKNOWN").put("current_revision","1");assertSame(unknown,schema.response("Recovery",unknown));
        var found=proof.deepCopy().put("status","FOUND");found.set("receipt",response("Receipt").put("message_id",id(7)).put("revision","2"));assertSame(found,schema.response("Recovery",found));
        ((ObjectNode)found.required("receipt")).put("request_body_sha256","b".repeat(64));invalid(()->schema.response("Recovery",found),"receipt body mismatch");
    }

    @Test void recoveryHasSeparateBoundedEnvelopeWhileOriginalAppendKeeps64KiBLimit() throws Exception {
        int lower=1,upper=16384;ObjectNode original=append();
        while(lower<=upper){int count=(lower+upper)/2;var candidate=append().put("text","😀".repeat(count));
            if(schema.canonical(candidate).getBytes(StandardCharsets.UTF_8).length<=GruSchema.MAX_BYTES){original=candidate;lower=count+1;}else upper=count-1;
        }
        byte[] originalBytes=schema.canonical(original).getBytes(StandardCharsets.UTF_8);
        assertTrue(originalBytes.length>GruSchema.MAX_BYTES-4);assertEquals(original,schema.request("Append",originalBytes));
        var recovery=JSON.createObjectNode().put("schema_version","1.0.0").put("idempotency_key","k".repeat(128));recovery.set("request",original);
        byte[] outer=schema.canonical(recovery).getBytes(StandardCharsets.UTF_8);assertTrue(outer.length>GruSchema.MAX_BYTES);assertTrue(outer.length<GruSchema.MAX_RECOVERY_BYTES);
        assertEquals(recovery,schema.request("RecoveryRequest",outer));
        var oversized=recovery.deepCopy();oversized.set("request",append().put("text","😀".repeat(16384)));
        invalid(()->schema.request("RecoveryRequest",bytes(oversized)),"inner canonical append still capped at 64KiB");
        invalid(()->schema.request("Append",bytes(oversized.required("request"))),"ordinary append cap unchanged");
        byte[] tooLarge=new byte[GruSchema.MAX_RECOVERY_BYTES+1];java.util.Arrays.fill(tooLarge,(byte)' ');
        invalid(()->schema.request("RecoveryRequest",tooLarge),"outer recovery bounded at 128KiB");
    }

    private ObjectNode recoveryProof(){
        var value=JSON.createObjectNode().put("schema_version","1.0.0").put("status","NOT_APPLIED_STALE").put("actor_id",id(9)).put("conversation_id",CONVERSATION)
            .put("idempotency_key","original-append-key").put("request_body_sha256","a".repeat(64)).put("expected_revision","1").put("current_revision","2")
            .put("checked_at","2026-10-18T00:00:00Z").putNull("receipt");
        value.putObject("current_context").put("tenant_id",id(10)).put("context_revision","1").put("auth_epoch","1").put("governance_epoch","1");return value;
    }

    private ObjectNode response(String type) {
        if (type.equals("Receipt")) return JSON.createObjectNode().put("schema_version", "1.0.0")
                .put("conversation_id", CONVERSATION).putNull("message_id").put("operation_id", id(8))
                .put("revision", "1").put("execution_state", "NOT_EXECUTED").put("request_body_sha256", "a".repeat(64));
        if (type.equals("List")) {
            var list = JSON.createObjectNode().put("schema_version", "1.0.0").put("project_id", PROJECT).put("mode", "RECORD_ONLY");
            list.putArray("items").add(response("View")); return list;
        }
        var context = create(); context.remove("reason");
        if (type.equals("Context")) return context;
        var view = context.put("conversation_id", CONVERSATION).put("revision", "2").put("status", "ACTIVE")
                .put("owner_id", id(9)).put("mode", "RECORD_ONLY").put("execution_state", "NOT_EXECUTED")
                .put("created_at", "2026-10-18T00:00:00Z");
        if (type.equals("View")) return view;
        view.putArray("messages").add(message()); view.putArray("message_records").add(messageRecord(id(7), "2"));
        view.putObject("page_info").put("page", 1).put("page_size", 50).put("total", "1")
                .put("dataset_watermark", "a".repeat(64)).putNull("next_page");
        return view;
    }
    private ObjectNode messageRecord(String messageId, String revision) {
        return JSON.createObjectNode().put("message_id", messageId).put("intent", "QUERY")
                .put("execution_state", "NOT_EXECUTED").put("revision", revision);
    }

    private ObjectNode create() {
        var value = JSON.createObjectNode().put("schema_version", "1.0.0").put("project_id", PROJECT)
                .put("document_id", DOCUMENT).put("document_revision", REVISION).put("scope_snapshot_id", SNAPSHOT)
                .put("reason", "Record the fixed document context");
        value.set("scope", scope("PROJECT_APPROVED")); return value;
    }
    private ObjectNode append() {
        var value = JSON.createObjectNode().put("schema_version", "1.0.0").put("expected_revision", "1")
                .put("document_revision", REVISION).put("scope_snapshot_id", SNAPSHOT).putNull("selection")
                .put("intent", "QUERY").put("execution_mode", "RECORD_ONLY").put("text", "question")
                .put("reason", "Record the user message");
        value.set("scope", scope("PROJECT_APPROVED")); return value;
    }
    private ObjectNode request(String type) { return type.equals("Create") ? create() : append(); }
    private ObjectNode scope(String mode) {
        var value = JSON.createObjectNode().put("mode", mode).put("network_enabled", false)
                .putNull("network_policy_revision").putNull("approved_query_revision");
        var collections = value.putArray("collection_versions"); var sources = value.putArray("source_versions");
        if (mode.equals("COLLECTION_VERSIONS") || mode.equals("COMBINED")) collections.add(id(21));
        if (mode.equals("SOURCE_VERSIONS") || mode.equals("COMBINED")) sources.add(id(20));
        return value;
    }
    private InterfaceProtocol.DocumentBody body() {
        var root = new InterfaceProtocol.Node(id(32), null, 0, List.of(), List.of(),
                new InterfaceProtocol.ContainerContent(InterfaceProtocol.ContainerKind.DOCUMENT, "Fixed document fixture"));
        var node = new InterfaceProtocol.Node(id(31), root.nodeId(), 0, List.of(), List.of(),
                new InterfaceProtocol.TextContent("TEXT", "甲😀e\u0301\n乙", List.of()));
        return new InterfaceProtocol.DocumentBody("1.0.0", id(30), List.of(root, node), List.of(), List.of(), List.of());
    }
    private ObjectNode selection() throws Exception {
        var node = body().nodes().get(1);
        String nodeHash = DigestUtils.sha256Hex(shared.bind("interfaces", JSON.valueToTree(node), "Node").canonicalBytes());
        var value = new CommonProtocol.Selection(REVISION, List.of(new CommonProtocol.TextSelection(
                "TEXT", node.nodeId(), nodeHash, 1, 4, DigestUtils.sha256Hex("😀e\u0301".getBytes(StandardCharsets.UTF_8)))));
        return JSON.valueToTree(value);
    }
    private ObjectNode message() {
        var value = JSON.createObjectNode().put("message_id", id(7)).put("conversation_id", CONVERSATION)
                .put("project_id", PROJECT).put("document_revision", REVISION).putNull("selection")
                .put("sender_kind", "USER").put("content", "  原文 e\u0301 😀\n").putNull("run_id")
                .put("operation_id", id(8)).put("created_at", "2026-10-18T00:00:00Z");
        value.set("scope", scope("PROJECT_APPROVED")); value.putArray("candidate_ids"); return value;
    }
    private static byte[] bytes(JsonNode value) throws Exception { return JSON.writeValueAsBytes(value); }
    private static void invalid(Executable operation, String description) {
        assertEquals(ErrorCode.SCHEMA_INVALID, assertThrows(SchemaRejection.class, operation, description).code(), description);
    }
    private static String id(int value) { return String.format("00000000-0000-4000-8000-%012d", value); }
}
