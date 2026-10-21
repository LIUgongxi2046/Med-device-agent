package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.networknt.schema.JsonNodePath;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.PathType;
import com.ruoyi.medical.contracts.ArtifactBindings;
import com.ruoyi.medical.contracts.SourceEvidenceBindings;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import com.ruoyi.medical.contracts.SchemaRejection;
import com.ruoyi.medical.contracts.TypedSchemaBindings;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.apache.commons.codec.digest.DigestUtils;
import static com.ruoyi.medical.persistence.StrictFieldComparisonProtocol.*;

/** Shape, immutable binding and self-consistency only. This is never a source/rule authority. */
public final class StrictFieldComparisonSchema {
    private static final int MAXIMUM_PARSE_BYTES = 8_388_608;
    private static final Set<String> TYPES = Set.of("Target", "Request", "Result");
    private final JsonMapper json = JsonMapper.builder()
        .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .disable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION)
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
        .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
        .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
        .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build();
    private final SchemaCatalogue catalogue;
    private final JsonSchema schema;
    private final String sha256;private final JsonSchema schemaV2;private final String sha256V2;

    public StrictFieldComparisonSchema() throws Exception {
        catalogue = SchemaCatalogue.fromClasspath();
        byte[] bytes;
        try (var in = Objects.requireNonNull(getClass().getResourceAsStream("/strict-field-comparison.schema.json"))) {
            bytes = in.readAllBytes();
        }
        sha256 = DigestUtils.sha256Hex(bytes);
        // The published catalogue's closed resolver supplies the real shared FieldValue,
        // FieldComparison and EvidenceRef definitions, not local weakened substitutes.
        schema = catalogue.compileApiSchema(json.readTree(bytes));
        try(var in=Objects.requireNonNull(getClass().getResourceAsStream("/strict-field-comparison-v2.schema.json"))){byte[] newer=in.readAllBytes();sha256V2=DigestUtils.sha256Hex(newer);schemaV2=catalogue.compileApiSchema(json.readTree(newer));}
        for (String type : TYPES) definition(type).initializeValidators();
    }
    public String schemaSha256() { return sha256; }
    public String schemaSha256(String version){if("1.0.0".equals(version))return sha256;if("2.0.0".equals(version))return sha256V2;invalid();return null;}
    public JsonNode tree(Object value) { return json.valueToTree(value); }
    public String canonical(Object value) throws IOException {
        var node = tree(value); unicode(node);
        return new String(ContentReadAuthorization.canonicalBytes(node), StandardCharsets.UTF_8);
    }
    public String hash(Object value) throws IOException {
        return DigestUtils.sha256Hex(canonical(value).getBytes(StandardCharsets.UTF_8));
    }
    public Target target(String value) throws IOException { return target(read(value)); }
    public Request request(String value) throws IOException { return request(read(value)); }
    public Target target(JsonNode value) throws IOException {
        var saved = validate("Target", value);
        var sections = new HashMap<String, Set<String>>();
        for (var section : saved.required("sections")) {
            var nodes = strings(section.required("requirement_node_ids"));
            if (sections.put(section.required("section_id").textValue(), nodes) != null) invalid();
        }
        var entries = new HashSet<String>(); var occurrences = new HashSet<String>();
        for (var entry : saved.required("entries")) {
            if (!entries.add(entry.required("entry_id").textValue())
                    || !occurrences.add(entry.required("field_id").textValue())
                    || entry.required("field_id").equals(entry.required("field_definition_id"))) invalid();
            var nodes = sections.get(entry.required("section_id").textValue());
            if (nodes == null || !nodes.contains(entry.required("node_id").textValue())) invalid();
            context(entry); field(entry.required("candidate_value")); provenance(entry.required("provenance"));
        }
        return bind(saved, Target.class);
    }
    public Request request(JsonNode value) throws IOException {
        var saved = validate("Request", value); var seen = new HashSet<String>();
        for (var entry : saved.required("entries")) {
            if (!seen.add(entry.required("entry_id").textValue())) invalid();
            source(entry.required("expected_source"));
            var observation = entry.required("source_observation");
            if (!observation.isNull()) {
                context(observation); field(observation.required("value"));
                provenance(observation.required("provenance"));
            }
        }
        return bind(saved, Request.class);
    }
    public Result result(JsonNode value) throws IOException {
        var saved = validate("Result", value); var seen = new HashSet<String>();
        if (!schemaSha256(saved.required("schema_version").asText()).equals(saved.required("schema_sha256").textValue())) invalid();
        for (var entry : saved.required("entries")) {
            if (!seen.add(entry.required("entry_id").textValue())) invalid();
            boolean unsupported = entry.required("outcome").textValue().equals("UNSUPPORTED");
            if (unsupported != entry.required("result").isNull()) invalid();
            var comparison = entry.required("comparison");
            Reason reason = Reason.valueOf(entry.required("reason").textValue());
            String expected = switch (reason) {
                case EXACT_EQUAL -> "MATCH";
                case VALUE_MISMATCH, MODEL_SET_MISMATCH, CONDITION_SET_MISMATCH, UNIT_MISMATCH,
                     PRECISION_MISMATCH, SOURCE_IDENTITY_MISMATCH, EVIDENCE_SCOPE_MISMATCH,
                     RAW_LEXICAL_EVIDENCE_MISMATCH, DECIMAL_LEXICAL_MISMATCH -> "CONFLICT";
                case UNKNOWN_APPLICABILITY, UNRESOLVED_MODEL_CONTEXT, UNRESOLVED_CONDITION_CONTEXT -> "UNKNOWN_CONDITION";
                case SOURCE_OBSERVATION_MISSING, SOURCE_EXPECTATION_MISSING, CANDIDATE_PROVENANCE_MISSING,
                     SOURCE_PROVENANCE_MISSING, ORIGINAL_EVIDENCE_MISSING, READ_RECEIPTS_MISSING,
                     SOURCE_EXACT_REFERENCE_MISSING, PARSED_DECIMAL_EVIDENCE_MISSING, RAW_VALUE_MISSING -> "MISSING";
                case UNSUPPORTED_OPERATOR, UNSUPPORTED_VALUE_KIND, UNSUPPORTED_PROVENANCE, UNSUPPORTED_PARSER,
                     UNSUPPORTED_DECIMAL_LEXICAL, UNSUPPORTED_VALUE_COMPONENTS -> null;
            };
            if (unsupported != (expected == null)
                    || expected != null && !expected.equals(entry.required("result").textValue())) invalid();
            boolean absent = unsupported || reason == Reason.SOURCE_OBSERVATION_MISSING;
            if (comparison.isNull() != absent) invalid();
            if (!comparison.isNull()) {
                TypedSchemaBindings.bind(catalogue,"2.0.0".equals(saved.path("schema_version").asText())?TypedSchemaBindings.withSchema(ArtifactBindings.FIELD_COMPARISON,"artifacts-v2"):ArtifactBindings.FIELD_COMPARISON,comparison);
                for (String key : List.of("field_id", "row_id", "result"))
                    if (!entry.required(key).equals(comparison.required(key))) invalid();
                if (!entry.at("/rule/rule_id").equals(comparison.required("comparison_rule_id"))
                        || !comparison.required("conversion_rule_id").isNull()) invalid();
            }
        }
        return bind(saved, Result.class);
    }
    private JsonNode read(String value) throws IOException {
        if (value == null || value.length() > MAXIMUM_PARSE_BYTES) invalid();
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAXIMUM_PARSE_BYTES) invalid();
        try {
            // Reject malformed UTF-16 before UTF-8 encoding could replace it.
            StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT).encode(CharBuffer.wrap(value));
            return json.readTree(StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString());
        } catch (IOException | IllegalArgumentException failure) { throw new SchemaRejection(ErrorCode.SCHEMA_INVALID); }
    }
    private JsonNode validate(String type, JsonNode value) throws IOException {
        if (value == null || !TYPES.contains(type)) invalid();
        boundedTree(value, new long[]{0}, 0);
        encodedSize(value);
        JsonNode saved = value.deepCopy();
        String version=saved.path("schema_version").asText();schemaSha256(version);
        JsonSchema selected="2.0.0".equals(version)?schemaV2.getSubSchema(new JsonNodePath(PathType.JSON_POINTER).append("$defs").append(type)):definition(type);
        if (!selected.validate(saved).isEmpty()) invalid();
        unicode(saved); return saved;
    }
    private <T> T bind(JsonNode value, Class<T> type) throws IOException {
        try { return json.treeToValue(value, type); }
        catch (IOException | IllegalArgumentException failure) { throw new SchemaRejection(ErrorCode.SCHEMA_INVALID); }
    }
    private void field(JsonNode value) throws IOException {
        try { TypedSchemaBindings.bind(catalogue, ArtifactBindings.FIELD_VALUE, value); }
        catch (IOException | IllegalArgumentException failure) { throw new SchemaRejection(ErrorCode.SCHEMA_INVALID); }
    }
    private void source(JsonNode value) throws IOException {
        if (value.isNull()) return;
        if (!value.required("evidence_ref").isNull()) {
            try { SourceEvidenceBindings.bind(catalogue,value.required("evidence_ref")); }
            catch (IOException | IllegalArgumentException failure) { throw new SchemaRejection(ErrorCode.SCHEMA_INVALID); }
        }
    }
    private void provenance(JsonNode value) throws IOException {
        if (value.isNull()) return;
        if (value.required("end_codepoint").intValue() < value.required("start_codepoint").intValue()) invalid();
        source(value.required("source"));
    }
    private static void context(JsonNode value) {
        for (String kind : List.of("model", "condition")) {
            String state = value.required(kind + "_context").textValue();
            boolean empty = value.required(kind + "_ids").isEmpty();
            if (state.equals("KNOWN_GLOBAL") && !empty || state.equals("KNOWN_SCOPED") && empty) invalid();
        }
    }
    private static Set<String> strings(JsonNode values) {
        var result = new HashSet<String>(); for (var value : values) result.add(value.textValue()); return result;
    }
    /** Count real UTF-8 JSON bytes without retaining a second encoded input. */
    private void encodedSize(JsonNode value) throws IOException {
        try {
            json.writeValue(new java.io.OutputStream() {
                private long count;
                @Override public void write(int ignored) throws IOException { add(1); }
                @Override public void write(byte[] bytes, int offset, int length) throws IOException { add(length); }
                private void add(int length) throws IOException {
                    if (length > MAXIMUM_PARSE_BYTES - count) throw new IOException("SCHEMA_INPUT_LIMIT");
                    count += length;
                }
            }, value);
        } catch (IOException failure) { throw new SchemaRejection(ErrorCode.SCHEMA_INVALID); }
    }
    private static void boundedTree(JsonNode value, long[] size, int depth) {
        if (depth > 32 || ++size[0] > MAXIMUM_PARSE_BYTES) invalid();
        if (value.isTextual()) {
            size[0] += value.textValue().length();
            if (size[0] > MAXIMUM_PARSE_BYTES) invalid();
        }
        if (value.isObject()) {
            var names = value.fieldNames();
            while (names.hasNext()) { size[0] += names.next().length(); if (size[0] > MAXIMUM_PARSE_BYTES) invalid(); }
        }
        if (value.isContainerNode()) for (var child : value) boundedTree(child, size, depth + 1);
    }
    private static void unicode(JsonNode value) throws IOException {
        try {
            if (value.isTextual()) StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(value.textValue()));
            if (value.isObject()) {
                var names = value.fieldNames();
                while (names.hasNext()) StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
                    .encode(CharBuffer.wrap(names.next()));
            }
            if (value.isContainerNode()) for (var child : value) unicode(child);
        } catch (java.nio.charset.CharacterCodingException failure) { throw new SchemaRejection(ErrorCode.SCHEMA_INVALID); }
    }
    private JsonSchema definition(String type) {
        return schema.getSubSchema(new JsonNodePath(PathType.JSON_POINTER).append("$defs").append(type));
    }
    private static void invalid() { throw new SchemaRejection(ErrorCode.SCHEMA_INVALID); }
}
