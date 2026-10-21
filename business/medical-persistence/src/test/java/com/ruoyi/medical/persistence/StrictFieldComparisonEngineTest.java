package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.ArtifactProtocol;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.SchemaRejection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.Test;
import static com.ruoyi.medical.contracts.ArtifactProtocol.ComparisonResult.*;
import static com.ruoyi.medical.persistence.StrictFieldComparisonProtocol.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real shared binders, no runtime authority mocks and no persisted business result. */
class StrictFieldComparisonEngineTest {
    static final JsonMapper JSON = JsonMapper.builder().build();
    static final String HASH = "a".repeat(64);
    static final StrictFieldComparisonEngine.Limits LIMITS =
        new StrictFieldComparisonEngine.Limits(100, 20, 100, 1_000_000, 1_000_000, 100_000, 10_000_000, 60_000);
    static final StrictFieldComparisonSchema schema = createSchema();
    static final StrictFieldComparisonEngine engine = new StrictFieldComparisonEngine(schema);
    static StrictFieldComparisonSchema createSchema() {
        try { return new StrictFieldComparisonSchema(); } catch (Exception failure) { throw new ExceptionInInitializerError(failure); }
    }
    StrictFieldComparisonEngineTest() throws Exception { }

    @Test void equalTextIsComputationOnlyWithRealSharedComparisonAndIndependentSource() throws Exception {
        var target = target(" A😀e\u0301\r\n "); var request = request(target, " A😀e\u0301\r\n ");
        var result = compare(target, request); var entry = result.entries().get(0);
        assertEquals("PROPOSED", schema.target(target).assessment());
        assertEquals("COMPUTATION_ONLY", result.assessment());
        assertEquals("EXACT_SUPPLIED_TARGET_ENTRIES", result.coverage());
        assertEquals(MATCH, entry.result()); assertEquals(Reason.EXACT_EQUAL, entry.reason());
        assertEquals(Outcome.COMPUTED, entry.outcome());
        assertEquals(id(11), entry.fieldId()); assertEquals(id(12), entry.fieldDefinitionId());
        assertEquals(ArtifactProtocol.FieldComparison.class, entry.comparison().getClass());
        assertEquals(id(40), entry.comparison().evidenceRefs().get(0).sourceId());
        assertEquals(id(20), schema.target(target).entries().get(0).provenance().source().evidenceRef().sourceId());
        assertEquals(schema.hash(target), result.targetSha256());
        assertEquals(schema.hash(request), result.requestSha256());
        assertNull(entry.comparison().conversionRuleId());
        for (String forbidden : List.of("target_artifact_id", "artifact_id", "operation_id", "approval_id", "verified"))
            assertFalse(schema.canonical(result).contains('"' + forbidden + '"'));
    }
    @Test void textNeverTrimsCaseFoldsNormalizesOrUsesCandidateAsSource() throws Exception {
        for (String other : List.of("x", " X ", " X", "X ", " X \n")) {
            var target = target(" X "); var request = request(target, other);
            if (other.equals(" X ")) assertEquals(MATCH, first(target, request).result());
            else assertDecision(target, request, CONFLICT, Reason.VALUE_MISMATCH);
        }
        var target = target("é"); assertDecision(target, request(target, "e\u0301"), CONFLICT, Reason.VALUE_MISMATCH);
        target = target("A😀Z"); assertDecision(target, request(target, "A😁Z"), CONFLICT, Reason.VALUE_MISMATCH);
    }
    @Test void missingSourceNeverGetsClonedOrInventedAsEmptyValue() throws Exception {
        var target = target("same"); var request = request(target, "different");
        req(request).putNull("source_observation");
        var result = first(target, request); assertEquals(MISSING, result.result());
        assertEquals(Reason.SOURCE_OBSERVATION_MISSING, result.reason()); assertNull(result.comparison());
    }
    @Test void sourceAndCandidateEvidenceAreBothRequired() throws Exception {
        var target = target("x"); entry(target).putNull("provenance");
        assertDecision(target, request(target, "x"), MISSING, Reason.CANDIDATE_PROVENANCE_MISSING);
        target = target("x"); var request = request(target, "x"); obs(request).putNull("provenance");
        assertDecision(target, request, MISSING, Reason.SOURCE_PROVENANCE_MISSING);
        request = request(target, "x"); req(request).putNull("expected_source");
        assertDecision(target, request, MISSING, Reason.SOURCE_EXPECTATION_MISSING);
        request = request(target, "x"); source(request).putNull("evidence_ref");
        assertDecision(target, request, MISSING, Reason.ORIGINAL_EVIDENCE_MISSING);
    }
    @Test void missingReceiptsOrSourceExactPinProduceMissingNotApproval() throws Exception {
        var target = target("x"); prov(entry(target)).putArray("read_receipt_ids");
        assertDecision(target, request(target, "x"), MISSING, Reason.READ_RECEIPTS_MISSING);
        target = target("x"); var request = request(target, "x"); prov(obs(request)).putArray("read_receipt_ids");
        assertDecision(target, request, MISSING, Reason.READ_RECEIPTS_MISSING);
        request = request(target, "x"); source(request).putNull("source_exact");
        assertDecision(target, request, MISSING, Reason.SOURCE_EXACT_REFERENCE_MISSING);
    }
    @Test void suppliedExpectedSourceMustMatchObservedFixedIdentityExactly() throws Exception {
        for (String component : List.of("source_id", "source_version", "parse_version", "chunk_id", "approval_revision")) {
            var target = target("x"); var request = request(target, "x");
            ((ObjectNode) source(request).required("evidence_ref")).put(component, id(99));
            assertDecision(target, request, CONFLICT, Reason.SOURCE_IDENTITY_MISMATCH);
        }
        var target = target("x"); var request = request(target, "x");
        ((ObjectNode) source(request).required("source_exact")).put("ordinal", 99);
        assertDecision(target, request, CONFLICT, Reason.SOURCE_IDENTITY_MISMATCH);
    }
    @Test void provenanceCannotCrossTenantOrProjectEvenWhenValuesEqual() throws Exception {
        for (String scope : List.of("tenant_id", "project_id")) {
            var target = target("x"); var request = request(target, "x");
            ((ObjectNode) source(request).required("evidence_ref")).put(scope, id(99));
            assertDecision(target, request, CONFLICT, Reason.EVIDENCE_SCOPE_MISMATCH);
        }
    }
    @Test void rawCodepointIntervalAndDigestRemainExact() throws Exception {
        var target = target("😀x"); var request = request(target, "😀x");
        assertEquals(MATCH, first(target, request).result());
        prov(obs(request)).put("end_codepoint", 3);
        assertDecision(target, request, CONFLICT, Reason.RAW_LEXICAL_EVIDENCE_MISMATCH);
        request = request(target, "😀x"); prov(obs(request)).put("raw_text_sha256", HASH);
        assertDecision(target, request, CONFLICT, Reason.RAW_LEXICAL_EVIDENCE_MISMATCH);
    }
    @Test void modelAndConditionSetsAreExactButOrderIsNotSemantic() throws Exception {
        var target = target("x"); entry(target).putArray("model_ids").add(id(70)).add(id(71));
        var request = request(target, "x"); obs(request).putArray("model_ids").add(id(71)).add(id(70));
        assertEquals(MATCH, first(target, request).result());
        obs(request).putArray("model_ids").add(id(70));
        assertDecision(target, request, CONFLICT, Reason.MODEL_SET_MISMATCH);
        request = request(target, "x"); obs(request).putArray("condition_ids").add(id(99));
        assertDecision(target, request, CONFLICT, Reason.CONDITION_SET_MISMATCH);
    }
    @Test void unitAndDeclaredPrecisionAreNeverConvertedOrInferred() throws Exception {
        var target = target("x"); var request = request(target, "x"); val(obs(request)).put("unit", "mm");
        assertDecision(target, request, CONFLICT, Reason.UNIT_MISMATCH);
        request = request(target, "x"); val(obs(request)).put("precision", 0);
        assertDecision(target, request, CONFLICT, Reason.PRECISION_MISMATCH);
    }
    @Test void unknownApplicabilityAndUnresolvedContextsRemainUnknown() throws Exception {
        var target = target("x"); entry(target).put("applicability", "UNKNOWN");
        assertDecision(target, request(target, "x"), UNKNOWN_CONDITION, Reason.UNKNOWN_APPLICABILITY);
        target = target("x"); var request = request(target, "x"); obs(request).put("applicability", "UNKNOWN");
        assertDecision(target, request, UNKNOWN_CONDITION, Reason.UNKNOWN_APPLICABILITY);
        target = target("x"); entry(target).put("model_context", "UNRESOLVED").putArray("model_ids");
        assertDecision(target, request(target, "x"), UNKNOWN_CONDITION, Reason.UNRESOLVED_MODEL_CONTEXT);
        target = target("x"); request = request(target, "x"); obs(request).put("condition_context", "UNRESOLVED").putArray("condition_ids");
        assertDecision(target, request, UNKNOWN_CONDITION, Reason.UNRESOLVED_CONDITION_CONTEXT);
    }
    @Test void emptyContextCanMatchOnlyWhenBothExplicitlyDeclareKnownGlobal() throws Exception {
        var target = target("x");
        entry(target).putArray("model_ids"); entry(target).putArray("condition_ids");
        assertThrows(SchemaRejection.class, () -> schema.target(target));
        entry(target).put("model_context", "KNOWN_GLOBAL").put("condition_context", "KNOWN_GLOBAL");
        var request = request(target, "x"); assertEquals(MATCH, first(target, request).result());
        obs(request).remove("model_context"); assertThrows(SchemaRejection.class, () -> schema.request(request));
    }
    @Test void unsupportedOperatorKindOrPayloadNeverBecomesBusinessSuccess() throws Exception {
        var target = target("x"); var request = request(target, "x");
        ((ObjectNode) req(request).required("rule")).put("operator", "TEXT_FUZZY");
        assertUnsupported(target, request, Reason.UNSUPPORTED_OPERATOR);
        request = request(target, "x"); val(obs(request)).put("value_kind", "LONG_TEXT");
        assertUnsupported(target, request, Reason.UNSUPPORTED_VALUE_KIND);
        request = request(target, "x"); val(obs(request)).putArray("selected_ids").add(id(90));
        assertUnsupported(target, request, Reason.UNSUPPORTED_VALUE_COMPONENTS);
        request = request(target, "x"); source(request).put("kind", "MODEL_GUESS");
        assertUnsupported(target, request, Reason.UNSUPPORTED_PROVENANCE);
    }
    @Test void decimalSupportsExactBoundedPlainLexemesWithoutDoubleRounding() throws Exception {
        for (String number : List.of("0", "-0", "0.000000000001", "-0.000000000001",
                "99999999999999999999999999.999999999999", "9007199254740993")) {
            var target = decimalTarget(number); var request = decimalRequest(target, number);
            assertEquals(MATCH, first(target, request).result());
            assertEquals(number, first(target, request).comparison().candidateValue().rawText());
        }
        var target = decimalTarget("9007199254740992");
        assertDecision(target, decimalRequest(target, "9007199254740993"), CONFLICT, Reason.VALUE_MISMATCH);
    }
    @Test void negativeZeroRemainsLexicallyPreservedAndNumericallyEqualAtSameScale() throws Exception {
        var target = decimalTarget("-0.00"); var result = first(target, decimalRequest(target, "0.00"));
        assertEquals(MATCH, result.result()); assertEquals("-0.00", result.comparison().candidateValue().rawText());
        assertEquals("0.00", result.comparison().sourceValue().rawText());
    }
    @Test void decimalScaleAndUnitHaveToMatchExactly() throws Exception {
        var target = decimalTarget("1.0");
        assertDecision(target, decimalRequest(target, "1.00"), CONFLICT, Reason.PRECISION_MISMATCH);
        var request = decimalRequest(target, "1.0"); val(obs(request)).put("unit", "cm");
        assertDecision(target, request, CONFLICT, Reason.UNIT_MISMATCH);
    }
    @Test void decimalParserMustBeExplicitlyPinnedAndSupportedOnBothSides() throws Exception {
        var target = decimalTarget("1.00"); var request = decimalRequest(target, "1.00");
        source(request).putNull("parsed_decimal");
        assertDecision(target, request, MISSING, Reason.PARSED_DECIMAL_EVIDENCE_MISSING);
        request = decimalRequest(target, "1.00"); parsed(source(request)).put("parser_contract", "LOCALE_GUESS");
        assertUnsupported(target, request, Reason.UNSUPPORTED_PARSER);
        target = decimalTarget("1.00"); parsed((ObjectNode) prov(entry(target)).required("source")).remove("parser_sha256");
        ObjectNode malformed = target; assertThrows(SchemaRejection.class, () -> schema.target(malformed));
    }
    @Test void rawDecimalExponentPlusLocaleGroupingAndLeadingZerosAreUnsupportedNotGuessed() throws Exception {
        for (String raw : List.of("1e0", "+1", "01", "1,0", "1,000", " 1", "1 ", "１", ".1", "1.")) {
            var target = decimalTarget("1"); var request = decimalRequest(target, "1");
            val(obs(request)).put("raw_text", raw); updateRaw(prov(obs(request)), raw);
            assertUnsupported(target, request, Reason.UNSUPPORTED_DECIMAL_LEXICAL);
        }
    }
    @Test void lexicalDecimalAndParsedEvidenceMustBeExactlyConsistent() throws Exception {
        var target = decimalTarget("1.00"); var request = decimalRequest(target, "1.00");
        val(obs(request)).put("raw_text", "1.01"); updateRaw(prov(obs(request)), "1.01");
        assertDecision(target, request, CONFLICT, Reason.DECIMAL_LEXICAL_MISMATCH);
        request = decimalRequest(target, "1.00"); parsed(source(request)).put("decimal_value", "1.0");
        assertDecision(target, request, CONFLICT, Reason.DECIMAL_LEXICAL_MISMATCH);
        request = decimalRequest(target, "1.00"); parsed(source(request)).put("precision", 99);
        assertDecision(target, request, CONFLICT, Reason.PRECISION_MISMATCH);
    }
    @Test void largeDeclaredPrecisionIsNotRoundingAuthority() throws Exception {
        var target = decimalTarget("1.00"); entry(target).withObject("candidate_value").put("precision", 999999999);
        parsed((ObjectNode) prov(entry(target)).required("source")).put("precision", 999999999);
        var request = decimalRequest(target, "1.00"); val(obs(request)).put("precision", 999999999);
        assertDecision(target, request, CONFLICT, Reason.PRECISION_MISMATCH);
    }
    @Test void publishedDecimalDomainOverflowIsMalformedNotSilentlyRounded() throws Exception {
        for (String raw : List.of("100000000000000000000000000", "0.0000000000001")) {
            var target = decimalTarget(raw); assertThrows(SchemaRejection.class, () -> schema.target(target));
        }
        var target = decimalTarget("1"); entry(target).withObject("candidate_value").put("decimal_value", "1e0");
        assertThrows(SchemaRejection.class, () -> schema.target(target));
    }
    @Test void decimalNullOrMissingRequiredComponentsRemainMalformed() throws Exception {
        for (String key : List.of("raw_text", "decimal_value", "precision")) {
            var target = decimalTarget("1.00"); entry(target).withObject("candidate_value").putNull(key);
            assertThrows(SchemaRejection.class, () -> schema.target(target));
            var missing = decimalTarget("1.00"); entry(missing).withObject("candidate_value").remove(key);
            assertThrows(SchemaRejection.class, () -> schema.target(missing));
        }
    }
    @Test void nullableTextValueIsMissingRatherThanCopiedFromOtherSide() throws Exception {
        var target = target("x"); var request = request(target, "x"); val(obs(request)).putNull("raw_text");
        assertDecision(target, request, MISSING, Reason.RAW_VALUE_MISSING);
    }
    @Test void nullableRowAndExplicitRowBindingsArePreservedWithoutInference() throws Exception {
        var target = target("x"); entry(target).put("row_id", id(95));
        var entry = first(target, request(target, "x"));
        assertEquals(id(95), entry.rowId()); assertEquals(id(95), entry.comparison().rowId());
    }
    @Test void fixedTargetDigestAndAllIdentityPinsAreRequired() throws Exception {
        for (String identity : List.of("tenant_id", "project_id", "task_id", "task_revision", "plan_revision", "template_version", "assignment_id")) {
            var target = target("x"); var request = request(target, "x");
            request.withObject("expected_identity").put(identity, id(99)); assertMismatch(target, request);
        }
        for (String pin : List.of("template_sha256", "assignment_sha256")) {
            var target = target("x"); var request = request(target, "x");
            request.withObject("expected_identity").put(pin, "b".repeat(64)); assertMismatch(target, request);
        }
        var target = target("x"); var request = request(target, "x"); request.put("target_sha256", "b".repeat(64));
        assertMismatch(target, request); request = request(target, "x"); req(request).put("target_entry_sha256", "b".repeat(64));
        assertMismatch(target, request);
    }
    @Test void duplicateOmittedExtraAndWrongMappedEntriesAreRejected() throws Exception {
        var target = target("x"); var request = request(target, "x");
        request.withArray("entries").add(req(request).deepCopy());
        ObjectNode duplicate = request; assertThrows(SchemaRejection.class, () -> schema.request(duplicate));
        request = request(target, "x"); var extra = req(request).deepCopy().put("entry_id", id(99)); request.withArray("entries").add(extra);
        assertMismatch(target, request);
        request = request(target, "x"); req(request).put("entry_id", id(99)); assertMismatch(target, request);
        var second = entry(target).deepCopy().put("entry_id", id(91)).put("field_id", id(92)); target.withArray("entries").add(second);
        request = request(target, "x"); assertMismatch(target, request);
    }
    @Test void distinctOccurrencesCanReuseDefinitionButCannotConflateOccurrenceIdentity() throws Exception {
        var target = target("x"); var second = entry(target).deepCopy().put("entry_id", id(91)).put("field_id", id(92));
        target.withArray("entries").add(second); assertEquals(2, schema.target(target).entries().size());
        second.put("field_id", id(11)); assertThrows(SchemaRejection.class, () -> schema.target(target));
        target.withArray("entries").remove(1); entry(target).put("field_id", id(12));
        assertThrows(SchemaRejection.class, () -> schema.target(target));
    }
    @Test void sectionNodeMappingAndDuplicateContextsAreNotInferred() throws Exception {
        var target = target("x"); entry(target).put("section_id", id(99));
        ObjectNode wrongSection = target; assertThrows(SchemaRejection.class, () -> schema.target(wrongSection));
        target = target("x"); entry(target).put("node_id", id(99));
        ObjectNode wrongNode = target; assertThrows(SchemaRejection.class, () -> schema.target(wrongNode));
        target = target("x"); entry(target).withArray("model_ids").add(id(70));
        ObjectNode duplicateModel = target; assertThrows(SchemaRejection.class, () -> schema.target(duplicateModel));
    }
    @Test void unknownPropertiesDuplicateJsonAndTrailingContentAreRejected() throws Exception {
        var target = target("x"); target.put("verified", true);
        assertThrows(SchemaRejection.class, () -> schema.target(target));
        String good = schema.canonical(target("x"));
        assertThrows(SchemaRejection.class, () -> schema.target(good + " {}"));
        assertThrows(SchemaRejection.class, () -> schema.target(good.replace("\"assessment\":\"PROPOSED\"", "\"assessment\":\"PROPOSED\",\"assessment\":\"PROPOSED\"")));
        var bad = target("x"); entry(bad).withObject("candidate_value").put("invented", true);
        assertThrows(SchemaRejection.class, () -> schema.target(bad));
        var request = request(target("x"), "x"); source(request).withObject("evidence_ref").put("verified", true);
        assertThrows(SchemaRejection.class, () -> schema.request(request));
    }
    @Test void publicRecordConstructionCannotBypassDuplicateCoverageValidation() throws Exception {
        var good = schema.target(target("x")); var request = schema.request(request(target("x"), "x"));
        var bypass = new Target(good.schemaVersion(), good.assessment(), good.identity(), good.sections(),
            List.of(good.entries().get(0), good.entries().get(0)));
        assertThrows(SchemaRejection.class, () -> engine.compare(bypass, request, LIMITS));
    }
    @Test void schemaBindingsAndResultsHaveNoMutableJsonEscape() throws Exception {
        var original = target("x"); var typed = schema.target(original); entry(original).put("field_id", id(99));
        assertEquals(id(11), typed.entries().get(0).fieldId());
        var tree = schema.tree(typed); ((ObjectNode) tree.at("/entries/0")).put("field_id", id(99));
        assertEquals(id(11), typed.entries().get(0).fieldId());
        assertThrows(UnsupportedOperationException.class, () -> typed.entries().clear());
        assertThrows(UnsupportedOperationException.class, () -> typed.entries().get(0).modelIds().clear());
        assertThrows(UnsupportedOperationException.class, () -> typed.entries().get(0).provenance().readReceiptIds().clear());
        var copied = new ArrayList<>(typed.entries());
        var created = new Target("1.0.0", "PROPOSED", typed.identity(), typed.sections(), copied); copied.clear();
        assertEquals(1, created.entries().size());
        var result = compare(target("x"), request(target("x"), "x"));
        assertThrows(UnsupportedOperationException.class, () -> result.entries().clear());
    }
    @Test void verifyRecomputesRatherThanAcceptingSelfConsistentCallerResult() throws Exception {
        var target = schema.target(target("x")); var request = schema.request(request(target("x"), "x"));
        var actual = engine.compare(target, request, LIMITS);
        assertEquals(actual, engine.verifyAgainstInputs(target, request, actual, LIMITS));
        var altered = schema.tree(actual); ((ObjectNode) altered.at("/entries/0")).put("reason", "VALUE_MISMATCH").put("result", "CONFLICT");
        ((ObjectNode) altered.at("/entries/0/comparison")).put("result", "CONFLICT");
        var forged = schema.result(altered);
        assertEquals(ErrorCode.CONTENT_MISMATCH, assertThrows(TenantAccessException.class,
            () -> engine.verifyAgainstInputs(target, request, forged, LIMITS)).code());
    }
    @Test void resultMatrixRejectsUnsupportedBusinessSuccessAndMissingComparisons() throws Exception {
        var actual = compare(target("x"), request(target("x"), "x"));
        var missing = schema.tree(actual); ((ObjectNode) missing.at("/entries/0")).putNull("comparison");
        assertThrows(SchemaRejection.class, () -> schema.result(missing));
        var falseReason = schema.tree(actual); ((ObjectNode) falseReason.at("/entries/0")).put("reason", "UNSUPPORTED_PARSER");
        assertThrows(SchemaRejection.class, () -> schema.result(falseReason));
        var mixed = schema.tree(actual); ((ObjectNode) mixed.at("/entries/0")).put("result", "MISSING");
        assertThrows(SchemaRejection.class, () -> schema.result(mixed));
    }
    @Test void oversizedRawRecordIsRejectedBeforeJsonSerialization() throws Exception {
        var raw = "x".repeat(1_100_000);
        var target = target(raw); var typed = schema.target(target);
        var request = schema.request(request(target("x"), "x"));
        assertEquals(ErrorCode.CONTEXT_LIMIT, assertThrows(TenantAccessException.class,
            () -> engine.compare(typed, request, LIMITS)).code());
    }
    @Test void allFiveSourceExactKindsRemainTextOnlyWithoutSemanticGuessing() throws Exception {
        for (String kind : List.of("MODEL", "PTR_NUMBER", "DOCUMENT_NUMBER", "STANDARD_NUMBER", "STANDARD_YEAR")) {
            var target = target(" 2026 "); ((ObjectNode) prov(entry(target)).at("/source/source_exact")).put("field_kind", kind);
            var request = request(target, " 2026 "); source(request).withObject("source_exact").put("field_kind", kind);
            req(request).set("expected_source", source(request).deepCopy());
            assertEquals(MATCH, first(target, request).result());
        }
    }
    @Test void candidateAndExpectedSourceScopeAreCheckedAndCandidateParserCannotBeGuessed() throws Exception {
        var target = target("x"); ((ObjectNode) prov(entry(target)).at("/source/evidence_ref")).put("tenant_id", id(99));
        assertDecision(target, request(target, "x"), CONFLICT, Reason.EVIDENCE_SCOPE_MISMATCH);
        target = target("x"); var request = request(target, "x"); req(request).withObject("expected_source").withObject("evidence_ref").put("project_id", id(99));
        assertDecision(target, request, CONFLICT, Reason.EVIDENCE_SCOPE_MISMATCH);
        target = decimalTarget("1"); parsed((ObjectNode) prov(entry(target)).required("source")).put("parser_contract", "GUESS");
        assertUnsupported(target, decimalRequest(target, "1"), Reason.UNSUPPORTED_PARSER);
    }
    @Test void parserOverloadsAgreeAndMalformedUnicodeFailsClosed() throws Exception {
        var target = target("😀é"); var request = request(target, "😀é");
        assertEquals(schema.target(target), schema.target(schema.canonical(target)));
        assertEquals(schema.request(request), schema.request(schema.canonical(request)));
        var invalid = target("x"); entry(invalid).withObject("candidate_value").put("raw_text", "\uD800");
        assertThrows(SchemaRejection.class, () -> schema.target(invalid));
        String invalidJson = schema.canonical(target("x")).replace("\"raw_text\":\"x\"", "\"raw_text\":\"\\uD800\"");
        assertThrows(SchemaRejection.class, () -> schema.target(invalidJson));
    }
    @Test void oversizedJsonTreeAndUnitFailBeforeUnboundedCopyOrEncoding() throws Exception {
        var excessive = target("x"); entry(excessive).withObject("candidate_value").put("raw_text", "x".repeat(8_388_609));
        assertThrows(SchemaRejection.class, () -> schema.target(excessive));
        var unit = target("x"); entry(unit).withObject("candidate_value").put("unit", "x".repeat(1_100_000));
        var typed = schema.target(unit); var request = schema.request(request(target("x"), "x"));
        assertEquals(ErrorCode.CONTEXT_LIMIT, assertThrows(TenantAccessException.class,
            () -> engine.compare(typed, request, LIMITS)).code());
    }
    @Test void oversizedDirectRecordListsFailBeforeSerialization() throws Exception {
        var good = schema.target(target("x")); var original = good.entries().get(0);
        var ids = new ArrayList<String>(); for (int i = 100; i < 201; i++) ids.add(id(i));
        var source = original.provenance();
        var oversized = new Provenance(source.source(), ids, source.startCodepoint(), source.endCodepoint(), source.rawTextSha256());
        var entry = new TargetEntry(original.entryId(), original.fieldId(), original.fieldDefinitionId(), original.sectionId(), original.nodeId(), original.rowId(),
            original.modelIds(), original.conditionIds(), original.modelContext(), original.conditionContext(), original.applicability(), original.candidateValue(), oversized);
        var target = new Target("1.0.0", "PROPOSED", good.identity(), good.sections(), List.of(entry));
        var request = schema.request(request(target("x"), "x"));
        assertEquals(ErrorCode.CONTEXT_LIMIT, assertThrows(TenantAccessException.class, () -> engine.compare(target, request, LIMITS)).code());
        var sections = new Target("1.0.0", "PROPOSED", good.identity(), List.of(new Section(id(8), ids)), good.entries());
        assertEquals(ErrorCode.CONTEXT_LIMIT, assertThrows(TenantAccessException.class, () -> engine.compare(sections, request, LIMITS)).code());
        var candidate = original.candidateValue();
        var selected = new ArtifactProtocol.FieldValue(candidate.valueKind(), candidate.rawText(), candidate.decimalValue(), candidate.precision(), candidate.unit(), ids, null);
        var selectedEntry = new TargetEntry(original.entryId(), original.fieldId(), original.fieldDefinitionId(), original.sectionId(), original.nodeId(), original.rowId(),
            original.modelIds(), original.conditionIds(), original.modelContext(), original.conditionContext(), original.applicability(), selected, original.provenance());
        var selections = new Target("1.0.0", "PROPOSED", good.identity(), good.sections(), List.of(selectedEntry));
        assertEquals(ErrorCode.CONTEXT_LIMIT, assertThrows(TenantAccessException.class, () -> engine.compare(selections, request, LIMITS)).code());
    }
    @Test void exactCanonicalInputAndResultByteBoundariesAreInclusive() throws Exception {
        var target = schema.target(target("x")); var request = schema.request(request(target("x"), "x"));
        int inputBytes = schema.canonical(target).getBytes(StandardCharsets.UTF_8).length
            + schema.canonical(request).getBytes(StandardCharsets.UTF_8).length;
        var expected = engine.compare(target, request, LIMITS);
        int outputBytes = schema.canonical(expected).getBytes(StandardCharsets.UTF_8).length;
        var exact = new StrictFieldComparisonEngine.Limits(1, 1, 2, inputBytes, outputBytes, 2, 1000000, 60000);
        assertEquals(expected, engine.compare(target, request, exact));
        var underInput = new StrictFieldComparisonEngine.Limits(1, 1, 2, inputBytes - 1, outputBytes, 2, 1000000, 60000);
        assertEquals(ErrorCode.CONTEXT_LIMIT, assertThrows(TenantAccessException.class,
            () -> engine.compare(target, request, underInput)).code());
        var underOutput = new StrictFieldComparisonEngine.Limits(1, 1, 2, inputBytes, outputBytes - 1, 2, 1000000, 60000);
        assertEquals(ErrorCode.CONTEXT_LIMIT, assertThrows(TenantAccessException.class,
            () -> engine.compare(target, request, underOutput)).code());
    }
    @Test void jsonNodeAndStringInputsRejectOversizedMultibyteUtf8() throws Exception {
        var input = target("x");
        ((ObjectNode) input.at("/entries/0/candidate_value")).put("raw_text", "界".repeat(3_000_000));
        assertThrows(SchemaRejection.class, () -> schema.target(input));
        assertThrows(SchemaRejection.class, () -> schema.target(JSON.writeValueAsString(input)));
    }
    @Test void callerBudgetsFailClosedWithoutPartialSuccess() throws Exception {
        var target = schema.target(target("x")); var request = schema.request(request(target("x"), "x"));
        for (var limits : List.of(new StrictFieldComparisonEngine.Limits(10, 10, 10, 1, 100000, 1000, 1000000, 60000),
                new StrictFieldComparisonEngine.Limits(10, 10, 10, 100000, 1, 1000, 1000000, 60000),
                new StrictFieldComparisonEngine.Limits(10, 10, 10, 100000, 100000, 1, 1000000, 60000),
                new StrictFieldComparisonEngine.Limits(10, 10, 10, 100000, 100000, 1000, 1, 60000)))
            assertEquals(ErrorCode.CONTEXT_LIMIT, assertThrows(TenantAccessException.class,
                () -> engine.compare(target, request, limits)).code());
    }

    Result compare(ObjectNode target, ObjectNode request) throws Exception {
        return engine.compare(schema.target(target), schema.request(request), LIMITS);
    }
    EntryResult first(ObjectNode target, ObjectNode request) throws Exception { return compare(target, request).entries().get(0); }
    void assertDecision(ObjectNode target, ObjectNode request, ArtifactProtocol.ComparisonResult result, Reason reason) throws Exception {
        var actual = first(target, request); assertEquals(result, actual.result()); assertEquals(reason, actual.reason());
    }
    void assertUnsupported(ObjectNode target, ObjectNode request, Reason reason) throws Exception {
        var actual = first(target, request); assertEquals(Outcome.UNSUPPORTED, actual.outcome());
        assertEquals(reason, actual.reason()); assertNull(actual.result()); assertNull(actual.comparison());
    }
    void assertMismatch(ObjectNode target, ObjectNode request) throws Exception {
        assertEquals(ErrorCode.CONTENT_MISMATCH, assertThrows(TenantAccessException.class, () -> compare(target, request)).code());
    }
    ObjectNode target(String raw) {
        var target = JSON.createObjectNode().put("schema_version", "1.0.0").put("assessment", "PROPOSED");
        var identity = target.putObject("identity");
        int i = 1; for (String key : List.of("tenant_id", "project_id", "task_id", "task_revision", "plan_revision", "template_version", "assignment_id")) identity.put(key, id(i++));
        identity.put("template_sha256", HASH).put("assignment_sha256", HASH);
        target.putArray("sections").addObject().put("section_id", id(8)).putArray("requirement_node_ids").add(id(9));
        var entry = target.putArray("entries").addObject().put("entry_id", id(10)).put("field_id", id(11))
            .put("field_definition_id", id(12)).put("section_id", id(8)).put("node_id", id(9)).putNull("row_id");
        context(entry); entry.set("candidate_value", value(raw)); entry.set("provenance", provenance(raw, 20)); return target;
    }
    ObjectNode request(ObjectNode target, String raw) throws Exception {
        var request = JSON.createObjectNode().put("schema_version", "1.0.0").put("target_sha256", schema.hash(target));
        request.set("expected_identity", target.required("identity").deepCopy());
        var entry = request.putArray("entries").addObject().put("entry_id", id(10)).put("target_entry_sha256", schema.hash(entry(target)));
        entry.putObject("rule").put("rule_revision", id(80)).put("rule_id", id(81)).put("rule_sha256", HASH).put("operator", "TEXT_EXACT");
        var observation = entry.putObject("source_observation");
        for (String key : List.of("model_ids", "condition_ids", "model_context", "condition_context", "applicability")) observation.set(key, entry(target).required(key).deepCopy());
        observation.set("value", value(raw)); observation.set("provenance", provenance(raw, 40));
        entry.set("expected_source", prov(observation).required("source").deepCopy()); return request;
    }
    ObjectNode decimalTarget(String raw) {
        var target = target(raw); decimalValue(entry(target).withObject("candidate_value"), raw);
        decimalProvenance(prov(entry(target)), raw); return target;
    }
    ObjectNode decimalRequest(ObjectNode target, String raw) throws Exception {
        var request = request(target, raw); req(request).withObject("rule").put("operator", "DECIMAL_EXACT");
        decimalValue(val(obs(request)), raw); decimalProvenance(prov(obs(request)), raw);
        req(request).set("expected_source", source(request).deepCopy()); return request;
    }
    static void decimalValue(ObjectNode value, String raw) {
        value.put("value_kind", "DECIMAL").put("decimal_value", raw).put("precision", scale(raw));
    }
    static void decimalProvenance(ObjectNode provenance, String raw) {
        var source = (ObjectNode) provenance.required("source"); source.put("kind", "PARSED_DECIMAL_REVISION").putNull("source_exact");
        source.putObject("parsed_decimal").put("evidence_revision", id(85)).put("evidence_sha256", HASH)
            .put("parser_revision", id(86)).put("parser_sha256", HASH).put("parser_contract", StrictFieldComparisonEngine.DECIMAL_PARSER_CONTRACT)
            .put("raw_text_sha256", digest(raw)).put("decimal_value", raw).put("precision", scale(raw));
    }
    static ObjectNode value(String raw) {
        var value = JSON.createObjectNode().put("value_kind", "TEXT").put("raw_text", raw).putNull("decimal_value")
            .putNull("precision").putNull("unit").putNull("object_ref"); value.putArray("selected_ids"); return value;
    }
    static ObjectNode provenance(String raw, int sourceId) {
        var provenance = JSON.createObjectNode(); updateRaw(provenance, raw); provenance.putArray("read_receipt_ids").add(id(sourceId + 9));
        var source = provenance.putObject("source").put("kind", "SOURCE_EXACT_REVISION").putNull("parsed_decimal");
        source.putObject("source_exact").put("field_revision", id(sourceId + 7)).put("field_manifest_sha256", HASH).put("ordinal", 0).put("field_kind", "MODEL");
        var evidence = source.putObject("evidence_ref").put("schema_version", "1.0.0").put("tenant_id", id(1)).put("project_id", id(2))
            .put("source_id", id(sourceId)).put("source_version", id(sourceId + 1)).put("parse_version", id(sourceId + 2)).put("chunk_id", id(sourceId + 3))
            .put("locator_kind", "TXT").put("quote_hash", HASH).put("approval_revision", id(sourceId + 4));
        evidence.putObject("locator").put("kind", "TXT").put("encoding", "UTF-8").put("newline_policy", "PRESERVE")
            .put("char_start", 0).put("char_end", 100).put("line_start", 1).put("line_end", 1);
        return provenance;
    }
    static void context(ObjectNode value) {
        value.putArray("model_ids").add(id(70)); value.putArray("condition_ids").add(id(72));
        value.put("model_context", "KNOWN_SCOPED").put("condition_context", "KNOWN_SCOPED").put("applicability", "APPLICABLE");
    }
    static void updateRaw(ObjectNode provenance, String raw) {
        provenance.put("start_codepoint", 0).put("end_codepoint", raw.codePointCount(0, raw.length())).put("raw_text_sha256", digest(raw));
    }
    static ObjectNode entry(ObjectNode target) { return (ObjectNode) target.at("/entries/0"); }
    static ObjectNode req(ObjectNode request) { return (ObjectNode) request.at("/entries/0"); }
    static ObjectNode obs(ObjectNode request) { return (ObjectNode) request.at("/entries/0/source_observation"); }
    static ObjectNode source(ObjectNode request) { return (ObjectNode) request.at("/entries/0/source_observation/provenance/source"); }
    static ObjectNode prov(ObjectNode owner) { return (ObjectNode) owner.required("provenance"); }
    static ObjectNode val(ObjectNode owner) { return (ObjectNode) owner.required("value"); }
    static ObjectNode parsed(ObjectNode source) { return (ObjectNode) source.required("parsed_decimal"); }
    static long scale(String raw) { int dot = raw.indexOf('.'); return dot < 0 ? 0 : raw.length() - dot - 1; }
    static String digest(String text) { return DigestUtils.sha256Hex(text.getBytes(StandardCharsets.UTF_8)); }
    static String id(int value) { return "00000000-0000-0000-0000-" + String.format("%012d", value); }
}
