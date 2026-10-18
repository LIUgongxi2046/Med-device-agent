package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.SchemaRejection;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Synthetic contract fixtures only: no database, user account, source grant, or approval is created. */
class RuleVersionSchemaTest {
    private static final String BASIS = "00000000-0000-4000-8000-000000000001";
    private static final String RULE = "00000000-0000-4000-8000-000000000002";
    private static final String USER = "00000000-0000-4000-8000-000000000003";
    private static final String TENANT = "00000000-0000-4000-8000-000000000004";
    private static final String HASH = "a".repeat(64);
    private PermissionSchema json;
    private TemplateApplicabilitySchema applicability;
    private RuleVersionSchema schema;
    private RuleExpression engine;

    @BeforeEach void initialize() throws Exception {
        json = new PermissionSchema(); applicability = new TemplateApplicabilitySchema(json); engine = new RuleExpression();
        schema = new RuleVersionSchema(json, applicability, engine);
    }

    private ObjectNode input() {
        var basis = new PermissionProtocol.Basis(BASIS, HASH);
        var constant = new TemplateApplicabilityProtocol.Constant("limit", json.tree(3), List.of(basis));
        var expression = new TemplateApplicabilityProtocol.Expression("1.0.0", RuleExpression.ENGINE,
                "#attributes['value'] >= #constants['limit']", List.of("value"), List.of(constant));
        return (ObjectNode) json.tree(new RuleVersionProtocol.VersionInput("1.0.0", null, RuleVersionSchema.CONTENT_REF,
                new TemplateApplicabilityProtocol.RuleContent("1.0.0", RuleExpression.ENGINE, expression, List.of(basis)), "Synthetic unit-test expression"));
    }
    private byte[] bytes(JsonNode input) throws Exception { return json.canonical(input).getBytes(StandardCharsets.UTF_8); }
    private ObjectNode expression(ObjectNode input) { return (ObjectNode) input.required("content").required("expression"); }
    private RuleVersionProtocol.Validated valid() throws Exception { return schema.version(bytes(input())); }
    private RuleVersionProtocol.Stored stored() throws Exception {
        var validated = valid(); return new RuleVersionProtocol.Stored(TENANT, RULE, "DRAFT", "1", validated.request().content(),
                validated.content_sha256(), null, validated.request().reason(), BASIS, "1", USER, "2026-10-16T00:00:00Z", validated.dependency_refs());
    }
    private RuleVersionProtocol.Baseline baseline() throws Exception {
        var stored = stored(); return new RuleVersionProtocol.Baseline("1.0.0", RULE, stored.content_sha256(), schema.contentManifestSha256(), stored.dependency_refs(), HASH);
    }

    @Test void workflowManifestIsDeterministicAndRetainsOriginalConsumerContentManifest() throws Exception {
        assertEquals(applicability.ruleManifestSha256(), schema.contentManifestSha256());
        assertEquals(schema.manifestSha256(), new RuleVersionSchema(json, applicability, engine).manifestSha256());
        assertTrue(schema.manifestSha256().matches("[0-9a-f]{64}"));
    }
    @Test void canonicalRuleIsReadAndEvaluatedByOriginalBusinessConsumer() throws Exception {
        var value = valid(); var content = value.request().content();
        var row = new TemplateApplicabilityProtocol.RuleRow(TENANT, RULE, content.engine_revision(), json.canonicalObject(content.expression()),
                json.canonicalObject(content.basis_manifest()), "DRAFT", value.content_sha256(), value.content_sha256(), "1.0.0",
                schema.contentManifestSha256(), USER, LocalDateTime.parse("2026-10-16T00:00:00"), "TEST");
        var original = applicability.rule(row); assertEquals(content, original);
        var compiled = engine.compile(original.expression());
        assertEquals("APPLICABLE", engine.evaluate(compiled, List.of(new ProjectProtocol.Attribute("value", "KNOWN", json.tree(4), List.of(), "Test"))).status());
        assertEquals("NOT_APPLICABLE", engine.evaluate(compiled, List.of(new ProjectProtocol.Attribute("value", "KNOWN", json.tree(2), List.of(), "Test"))).status());
        assertEquals(new TemplateApplicabilityProtocol.Result("UNKNOWN", List.of("value")), engine.evaluate(compiled, List.of()));
    }
    @Test void closedRequestRefusesForgedTenantStatusAuthorityAndServerIdentities() throws Exception {
        for (String key : List.of("tenant_id", "status", "created_by", "authority_grant", "rule_version", "revision", "approved")) {
            var node = input(); node.put(key, USER);
            assertThrows(SchemaRejection.class, () -> schema.version(bytes(node)), key);
        }
    }
    @Test void rejectsExecutableAstUndeclaredInputsAndMalformedSyntax() throws Exception {
        for (String expression : List.of("T(java.lang.System).getProperties()", "new java.lang.String('x')",
                "#attributes['value'].toString()", "#attributes['value'] = 'x'", "@environment.getProperty('HOME')",
                "#attributes['value'] matches '.*'", "#attributes['secret'] == #constants['limit']", "#attributes['value'", "'literal'", "null", "#attributes['value'] == #constants['limit'] and 'literal'")) {
            var node = input(); expression(node).put("expression", expression);
            assertThrows(SchemaRejection.class, () -> schema.version(bytes(node)), expression);
        }
    }
    @Test void logicalOperandsAreCheckedThroughoutNestedComparisons() throws Exception {
        for (String text : List.of("'literal'", "null", "(true and 'literal') == true", "(false or null) != true",
                "(!'literal') == false", "(not null) == true", "true == (false or ('x' and true))",
                "(true == (true and null)) or false")) {
            var node = input(); var expression = expression(node); expression.put("expression", text);
            expression.putArray("input_keys"); expression.putArray("constants");
            assertThrows(SchemaRejection.class, () -> schema.version(bytes(node)), text);
        }
    }
    @Test void validNestedComparisonsAndBooleanAttributeRootsRemainSupported() throws Exception {
        for (String text : List.of("(true and false) == false", "(not false) == true", "('x' == 'x') and (null == null)",
                "true == (false or true)")) {
            var input = new TemplateApplicabilityProtocol.Expression("1.0.0", RuleExpression.ENGINE, text, List.of(), List.of());
            assertEquals("APPLICABLE", engine.evaluate(engine.compile(input), List.of()).status(), text);
        }
        var root = new TemplateApplicabilityProtocol.Expression("1.0.0", RuleExpression.ENGINE, "#attributes['flag']", List.of("flag"), List.of());
        assertEquals("APPLICABLE", engine.evaluate(engine.compile(root),
                List.of(new ProjectProtocol.Attribute("flag", "KNOWN", json.tree(true), List.of(), "Test"))).status());
        assertEquals("UNKNOWN", engine.evaluate(engine.compile(root), List.of()).status());
    }
    @Test void knownNonBooleanConstantsCannotOccupyBooleanContexts() throws Exception {
        for (JsonNode value : List.of(json.tree(3), json.tree("true"), json.tree(List.of(true)), json.tree(java.util.Map.of("flag", true)))) {
            for (String text : List.of("#constants['limit']", "true and #constants['limit']", "(not #constants['limit']) == false",
                    "true == (false or #constants['limit'])")) {
                var node = input(); var expression = expression(node); expression.put("expression", text); expression.putArray("input_keys");
                ((ObjectNode) expression.required("constants").get(0)).set("value", value);
                assertThrows(SchemaRejection.class, () -> schema.version(node.toString().getBytes(StandardCharsets.UTF_8)), text + ": " + value);
            }
        }
        var node = input(); var expression = expression(node); expression.put("expression", "true and #constants['limit']"); expression.putArray("input_keys");
        ((ObjectNode) expression.required("constants").get(0)).put("value", true);
        var valid = schema.version(node.toString().getBytes(StandardCharsets.UTF_8));
        assertEquals("APPLICABLE", engine.evaluate(engine.compile(valid.request().content().expression()), List.of()).status());
    }
    @Test void lossyNumericCanonicalizationIsRejectedBeforeAnyPersistence() throws Exception {
        for (String literal : List.of("9007199254740993", "-9007199254740993", "0.12345678901234567890123456789", "1e-400", "1e400")) {
            var node = input(); ((ObjectNode) expression(node).required("constants").get(0)).set("value",
                    com.fasterxml.jackson.databind.node.DecimalNode.valueOf(new java.math.BigDecimal(literal)));
            assertEquals(ErrorCode.SCHEMA_INVALID, assertThrows(SchemaRejection.class,
                    () -> schema.version(node.toString().getBytes(StandardCharsets.UTF_8)), literal).code());
        }
        for (String literal : List.of("0.1", "9007199254740991", "9007199254740992", "-17", "12.25")) {
            var raw = input().toString().replace("\"value\":3", "\"value\":" + literal);
            var valid = schema.version(raw.getBytes(StandardCharsets.UTF_8));
            assertEquals(valid, schema.version(valid.request()), literal);
            assertEquals(new java.math.BigDecimal(literal), valid.request().content().expression().constants().get(0).value().decimalValue(), literal);
        }
    }
    @Test void mathematicallyEquivalentNumberFormsHaveOneStableIdentity() throws Exception {
        var integer = valid();
        for (String literal : List.of("3", "3.0", "3e0", "3.000")) {
            var raw = input().toString().replace("\"value\":3", "\"value\":" + literal);
            var equivalent = schema.version(raw.getBytes(StandardCharsets.UTF_8));
            assertEquals(integer, equivalent, literal);
            assertEquals(integer.request().content().basis_manifest(), equivalent.request().content().basis_manifest());
            assertEquals(schema.hash(null, integer.request(), "1", USER), schema.hash(null, equivalent.request(), "1", USER));
            assertEquals(equivalent, schema.version(equivalent.request()));
        }
    }
    @Test void everyDeclaredConstantNeedsAnExactMemberOfCompleteEvidenceManifest() throws Exception {
        var unknown = input(); ((ObjectNode) unknown.required("content").required("expression").required("constants").get(0)
                .required("basis_refs").get(0)).put("version_id", USER);
        assertThrows(SchemaRejection.class, () -> schema.version(bytes(unknown)));
        var changed = input(); ((ObjectNode) changed.required("content").required("expression").required("constants").get(0)
                .required("basis_refs").get(0)).put("content_sha256", "b".repeat(64));
        assertThrows(SchemaRejection.class, () -> schema.version(bytes(changed)));
        var empty = input(); ((ObjectNode) empty.required("content")).putArray("basis_manifest");
        assertThrows(SchemaRejection.class, () -> schema.version(bytes(empty)));
    }
    @Test void duplicateBindingsAndUnconsumedInputsAreRejected() throws Exception {
        var duplicate = input(); var constants = (com.fasterxml.jackson.databind.node.ArrayNode) expression(duplicate).required("constants"); constants.add(constants.get(0).deepCopy());
        assertThrows(SchemaRejection.class, () -> schema.version(bytes(duplicate)));
        var inputs = input(); expression(inputs).putArray("input_keys").add("value").add("unused");
        assertThrows(SchemaRejection.class, () -> schema.version(bytes(inputs)));
    }
    @Test void duplicateJsonKeysTrailingTokensWrongSchemaAndBlankReasonsAreRejected() throws Exception {
        var serialized = json.canonical(input());
        assertThrows(com.fasterxml.jackson.core.JsonProcessingException.class,
                () -> schema.version(serialized.replaceFirst("\\{", "{\"reason\":\"injected\",").getBytes(StandardCharsets.UTF_8)));
        assertThrows(com.fasterxml.jackson.core.JsonProcessingException.class, () -> schema.version((serialized + " {}").getBytes(StandardCharsets.UTF_8)));
        var wrong = input(); wrong.put("content_schema_ref", "https://example.invalid/code");
        assertThrows(SchemaRejection.class, () -> schema.version(bytes(wrong)));
        var blank = input(); blank.put("reason", "  "); assertThrows(SchemaRejection.class, () -> schema.version(bytes(blank)));
    }
    @Test void baseReferenceJoinsCompleteDependencySetWithoutChangingRuleContentIdentity() throws Exception {
        var original = valid(); var node = input(); node.set("base_version", json.tree(new PermissionProtocol.Basis(RULE, HASH)));
        var updated = schema.version(bytes(node)); assertEquals(original.content_sha256(), updated.content_sha256());
        assertEquals(2, updated.dependency_refs().size()); assertEquals(RULE, updated.dependency_refs().get(1).version_id());
        node.set("base_version", json.tree(new PermissionProtocol.Basis(BASIS, "b".repeat(64))));
        assertThrows(SchemaRejection.class, () -> schema.version(bytes(node)));
    }
    @Test void creationReceiptAndReadViewPreserveImmutableDraftAndRejectHashTampering() throws Exception {
        var stored = stored(); var response = schema.created(stored, USER, BASIS);
        assertEquals(response, schema.creationReceipt(json.canonical(response)));
        assertEquals(json.tree(stored), schema.readView(stored, BASIS).required("data").required("rule"));
        var forgedApproval = response.deepCopy(); ((ObjectNode) forgedApproval.required("data").required("rule")).put("status", "APPROVED").put("revision", "2");
        assertThrows(SchemaRejection.class, () -> schema.creationReceipt(json.canonical(forgedApproval)));
        ((ObjectNode) response.required("data").required("rule")).put("content_sha256", "b".repeat(64));
        assertThrows(SchemaRejection.class, () -> schema.creationReceipt(json.canonical(response)));
    }
    @Test void decisionRequiresFixedTargetExactBaselinePositiveCasRevisionAndHumanReason() throws Exception {
        var decision = new RuleVersionProtocol.Decide("1.0.0", RULE, "1", "APPROVE", "Synthetic decision protocol test only", baseline());
        assertEquals(decision, schema.decide(bytes(json.tree(decision))));
        for (String revision : List.of("0", "-1", "01", "18446744073709551616")) {
            var value = (ObjectNode) json.tree(decision); value.put("expected_revision", revision);
            assertThrows(SchemaRejection.class, () -> schema.decide(bytes(value)));
        }
        var target = (ObjectNode) json.tree(decision); target.put("target_revision", USER);
        assertThrows(SchemaRejection.class, () -> schema.decide(bytes(target)));
        var forged = (ObjectNode) json.tree(decision); forged.put("actor_id", USER);
        assertThrows(SchemaRejection.class, () -> schema.decide(bytes(forged)));
    }
    @Test void previewBindsFullContentAndEvidenceManifestAndDoesNotClaimApproval() throws Exception {
        var response = schema.preview(stored(), baseline(), USER);
        assertEquals("DRAFT", response.required("data").required("rule").required("status").textValue());
        assertEquals(json.tree(baseline()), response.required("data").required("baseline_manifest"));
        var bad = new RuleVersionProtocol.Baseline("1.0.0", RULE, "b".repeat(64), schema.contentManifestSha256(), stored().dependency_refs(), HASH);
        assertThrows(SchemaRejection.class, () -> schema.preview(stored(), bad, USER));
    }
    @Test void requestHashBindsSessionContextMethodTargetAndEntireBody() throws Exception {
        var request = valid().request(); String hash = schema.hash(null, request, "1", USER);
        assertEquals(hash, schema.hash(null, request, "1", USER));
        assertNotEquals(hash, schema.hash(null, request, "2", USER));
        assertNotEquals(hash, schema.hash(null, request, "1", BASIS));
        assertNotEquals(hash, schema.hash(RULE, request, "1", USER));
        var changed = new RuleVersionProtocol.VersionInput("1.0.0", null, RuleVersionSchema.CONTENT_REF, request.content(), "Different reason");
        assertNotEquals(hash, schema.hash(null, changed, "1", USER));
    }
    @Test void approvalStateMachineReusesTemplateTransitionsAndDisabledIsTerminal() {
        assertEquals("APPROVED", RuleVersionApprovalService.nextStatus("DRAFT", "APPROVE"));
        assertEquals("RETURNED", RuleVersionApprovalService.nextStatus("APPROVED", "RETURN"));
        assertEquals("APPROVED", RuleVersionApprovalService.nextStatus("RETURNED", "APPROVE"));
        for (String action : List.of("APPROVE", "RETURN", "DISABLE"))
            assertEquals(ErrorCode.REVISION_CONFLICT, assertThrows(TenantAccessException.class, () -> RuleVersionApprovalService.nextStatus("DISABLED", action)).code());
        assertThrows(SchemaRejection.class, () -> RuleVersionApprovalService.nextStatus("DRAFT", "PUBLISH"));
    }
}
