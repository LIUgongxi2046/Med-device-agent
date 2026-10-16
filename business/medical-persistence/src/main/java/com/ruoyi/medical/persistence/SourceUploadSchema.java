package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonNodePath;
import com.networknt.schema.PathType;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import com.ruoyi.medical.contracts.SchemaRejection;
import com.ruoyi.medical.contracts.WireValues;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FilenameUtils;
import org.springframework.stereotype.Component;

@Component
public final class SourceUploadSchema {
    private static final String REFERENCE = "urn:medical:source-uploads:1.0.0#/$defs/Create";
    private static final Map<String, String> MEDIA = Map.of(
        "pdf", "application/pdf", "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "csv", "text/csv", "pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "md", "text/markdown", "markdown", "text/markdown", "txt", "text/plain");
    private final JsonMapper mapper = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private final PermissionSchema json;
    private final JsonSchema schema;
    private final JsonSchema completionCommands;
    private final JsonSchema completionResults;
    private final String completionResultManifest;
    private final String schemaSha256;

    public SourceUploadSchema(PermissionSchema json) throws IOException, NoSuchAlgorithmException {
        this.json = Objects.requireNonNull(json);
        try (var input = Objects.requireNonNull(getClass().getResourceAsStream("/source-uploads.schema.json"))) {
            byte[] bytes = input.readAllBytes(); schemaSha256 = DigestUtils.sha256Hex(bytes);
            schema = SchemaCatalogue.fromClasspath().compileApiSchema(mapper.readTree(bytes));
            schema.initializeValidators();
            definition("Created").initializeValidators(); definition("Registration").initializeValidators();
            definition("Completion").initializeValidators();
        }
        try(var input=Objects.requireNonNull(getClass().getResourceAsStream("/source-completion-command.schema.json"))) {
            completionCommands=SchemaCatalogue.fromClasspath().compileApiSchema(mapper.readTree(input));
            completionCommands.initializeValidators();
        }
        try(var input=Objects.requireNonNull(getClass().getResourceAsStream("/source-completion-result.schema.json"))) {
            byte[] document=input.readAllBytes(); completionResultManifest=DigestUtils.sha256Hex(document);
            completionResults=SchemaCatalogue.fromClasspath().compileApiSchema(mapper.readTree(document));
            completionResults.initializeValidators();
        }
    }

    public ValidatedCreate create(byte[] body, BigInteger maximumBytes) throws IOException {
        // 上限由服务器提供；声明只保存来源信息，不能产生核准或扩大许可。
        capacity(maximumBytes);
        JsonNode value = request(body);
        if (value == null || !schema.validate(value).isEmpty()) invalid();
        BigInteger size = WireValues.revision(value.required("size_bytes").textValue());
        if (size.signum() <= 0) invalid();
        if (size.compareTo(maximumBytes) > 0) throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);
        String filename = value.required("file_name").textValue(); ProjectSchema.text(filename, 512);
        if (!FilenameUtils.getName(filename).equals(filename) || filename.indexOf(':') >= 0) invalid();
        String media = MEDIA.get(FilenameUtils.getExtension(filename).toLowerCase(Locale.ROOT));
        if (media == null || !media.equals(value.required("media_type").textValue()))
            throw new TenantAccessException(ErrorCode.FILE_TYPE_UNSUPPORTED);
        JsonNode provenance = value.required("provenance"), rights = value.required("rights_declaration");
        ProjectSchema.text(provenance.required("owner").textValue(), 512);
        dates(provenance, "effective_from", "effective_to"); times(rights, "valid_from", "valid_to");
        String canonical = json.canonical(value);
        return new ValidatedCreate(value, canonical, DigestUtils.sha256Hex(canonical), REFERENCE, schemaSha256, size);
    }

    public ValidatedCompletion completion(byte[] body, BigInteger maximumBytes, ValidatedCreate declaration) throws IOException {
        capacity(maximumBytes); Objects.requireNonNull(declaration);
        JsonNode value = request(body); verify("Completion", value);
        BigInteger revision = WireValues.revision(value.required("expected_revision").textValue());
        BigInteger size = WireValues.revision(value.required("size_bytes").textValue());
        if (size.compareTo(maximumBytes) > 0) throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);
        JsonNode original = declaration.content();
        if (!size.equals(declaration.sizeBytes()) || !value.required("version_intent").equals(original.required("version_intent"))
                || !Objects.equals(nullable(value, "source_id"), nullable(original, "source_id"))
                || nullable(original, "sha256") != null && !original.required("sha256").equals(value.required("sha256"))) mismatch();
        BigInteger count = size.add(UploadTransferProtocol.PART_BYTES).subtract(BigInteger.ONE).divide(UploadTransferProtocol.PART_BYTES);
        if (count.compareTo(BigInteger.valueOf(4294967295L)) > 0 || !count.equals(BigInteger.valueOf(value.required("parts").size()))) mismatch();
        var parts = new ArrayList<CompletionPart>();
        BigInteger received = BigInteger.ZERO;
        for (JsonNode item : value.required("parts")) {
            long number = item.required("part_no").longValue();
            BigInteger partSize = WireValues.revision(item.required("size_bytes").textValue());
            if (number != parts.size() + 1L || !partSize.equals(size.subtract(received).min(UploadTransferProtocol.PART_BYTES))) mismatch();
            String digest = item.required("sha256").textValue(); WireValues.hash(digest);
            parts.add(new CompletionPart(number, partSize, digest)); received = received.add(partSize);
        }
        String digest = value.required("sha256").textValue(); WireValues.hash(digest);
        if (!received.equals(size) || parts.size() == 1 && !parts.get(0).sha256().equals(digest)) mismatch();
        String canonical = json.canonical(value);
        return new ValidatedCompletion(value, canonical, DigestUtils.sha256Hex(canonical),
            "urn:medical:source-uploads:1.0.0#/$defs/Completion", schemaSha256, revision, size, digest, parts);
    }

    private JsonNode request(byte[] body) throws IOException {
        String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(Objects.requireNonNull(body))).toString();
        return mapper.readTree(text);
    }

    private static void capacity(BigInteger maximumBytes) {
        Objects.requireNonNull(maximumBytes); WireValues.revision(maximumBytes.toString());
        if (maximumBytes.signum() <= 0) throw new IllegalArgumentException("上传容量必须具有明确的服务器限制");
    }

    private static String nullable(JsonNode value, String field) {
        JsonNode node = value.get(field); return node == null || node.isNull() ? null : node.textValue();
    }

    String schemaSha256() { return schemaSha256; }
    JsonNode readJson(String value) throws IOException { return mapper.readTree(Objects.requireNonNull(value)); }
    JsonNode completionCommand(String value) throws IOException {
        var command=readJson(value);
        if(command==null || !completionCommands.validate(command).isEmpty()) mismatch();
        for(String name:List.of("source_revision","session_revision")) WireValues.revision(command.required(name).textValue());
        WireValues.revision(command.required("context").required("context_revision").textValue());
        for(String name:List.of("auth_epoch","governance_epoch")) {
            WireValues.revision(command.required("context").required(name).textValue());
            WireValues.revision(command.required("watermarks").required(name).textValue());
            if(!command.required("context").required(name).equals(command.required("watermarks").required(name))) mismatch();
        }
        return command;
    }
    JsonNode completionResult(String value) throws IOException {
        var result=readJson(value); if(result==null || !completionResults.validate(result).isEmpty()) mismatch();
        if(!"FAILED".equals(result.required("status").textValue())) {
            var object=result.required("original_object_ref"); WireValues.revision(object.required("size_bytes").textValue());
            if(!object.required("sha256").equals(result.required("original_sha256"))) mismatch();
        }
        return result;
    }
    String completionResultManifest() { return completionResultManifest; }
    void verify(String kind, JsonNode value) { if (value == null || !definition(kind).validate(value).isEmpty()) invalid(); }
    private JsonSchema definition(String kind) { return schema.getSubSchema(new JsonNodePath(PathType.JSON_POINTER).append("$defs").append(kind)); }

    private static void dates(JsonNode node, String from, String to) {
        if (!node.required(from).isNull() && !node.required(to).isNull()
                && LocalDate.parse(node.required(to).textValue()).isBefore(LocalDate.parse(node.required(from).textValue()))) invalid();
    }

    private static void times(JsonNode node, String from, String to) {
        if (!node.required(from).isNull() && !node.required(to).isNull()
                && !WireValues.utcTime(node.required(to).textValue()).isAfter(WireValues.utcTime(node.required(from).textValue()))) invalid();
    }

    public record ValidatedCreate(JsonNode content, String canonicalJson, String requestSha256,
            String schemaRef, String schemaSha256, BigInteger sizeBytes) {
        public ValidatedCreate { content = content.deepCopy(); }
        @Override public JsonNode content() { return content.deepCopy(); }
    }

    public record CompletionPart(long partNo, BigInteger sizeBytes, String sha256) { }

    public record ValidatedCompletion(JsonNode content, String canonicalJson, String requestSha256, String schemaRef,
            String schemaSha256, BigInteger expectedRevision, BigInteger sizeBytes, String sha256, List<CompletionPart> parts) {
        public ValidatedCompletion { content = content.deepCopy(); parts = List.copyOf(parts); }
        @Override public JsonNode content() { return content.deepCopy(); }
    }

    private static void invalid() { throw new SchemaRejection(ErrorCode.SCHEMA_INVALID); }
    private static void mismatch() { throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH); }
}
