package com.ruoyi.medical.contracts;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.core.json.JsonWriteFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion.VersionFlag;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.erdtman.jcs.JsonCanonicalizer;
import org.erdtman.jcs.NumberToJSON;

public final class SchemaCatalogue {
    private static SchemaCatalogue classpathCatalogue;
    static final String EVALUATION_CONTEXT_EXTENSION_PATH="contracts/extensions/evaluation-context-compiler.catalogue.json";
    private static final String EVALUATION_CONTEXT_EXTENSION_SHA256="2dcccbadff66b247c8025c781fb1fafc2a167d28c5d45ac0b808fb66611b5000";
    static final String TASK_RUNTIME_EXTENSION_PATH = "contracts/extensions/task-runtime.catalogue.json";
    private static final String TASK_RUNTIME_EXTENSION_SHA256 = "044860cfe13730927433a208daf56e2adbb89c1d9e8ef3d61937424d812fcb6c";
    static final String SIMULATION_EXTENSION_PATH = "contracts/extensions/simulation-execution.catalogue.json";
    private static final String SIMULATION_EXTENSION_SHA256 = "c3837a5c8de536a6504bbb296cd63389fac07ced1eda93ba721d94fcdad8446a";
    static final String EXTENSION_PATH = "contracts/extensions/evaluation-results.catalogue.json";
    private static final String EXTENSION_SHA256 = "a1b3dce03c553d0e9166d76c30e5af287c3bb06dfe90c9cc696632a1663c891d";
    private final ObjectMapper json = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .disable(JsonWriteFeature.WRITE_HEX_UPPER_CASE).build();
    private final Map<String, JsonNode> documents = new HashMap<>();
    private final Map<String, String> uris = new HashMap<>();
    private final Map<String, String> digests = new HashMap<>();
    private final Map<String, JsonSchema> validators = new HashMap<>();
    private final Map<String, PublishedSchema> publications = new HashMap<>();
    private final ObjectMapper payloadJson = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).enable(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS).build();
    private final SchemaValidatorsConfig config = SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build();
    private final JsonSchemaFactory factory;

    @FunctionalInterface
    private interface Source { byte[] read(String path) throws IOException; }

    public static SchemaCatalogue fromProject(Path root) throws IOException, NoSuchAlgorithmException {
        Path allowed = root.toRealPath();
        return new SchemaCatalogue(path -> {
            Path resolved = allowed.resolve(path).toRealPath();
            if (!resolved.startsWith(allowed)) throw new IllegalArgumentException("Schema 路径超出登记目录");
            return Files.readAllBytes(resolved);
        });
    }

    public static synchronized SchemaCatalogue fromClasspath() throws IOException, NoSuchAlgorithmException {
        // 同一不可变制品共享完整已核查Schema；初始化成功以后才发布给其他线程。
        if (classpathCatalogue == null) {
            classpathCatalogue = new SchemaCatalogue(path -> {
                InputStream stream = SchemaCatalogue.class.getClassLoader().getResourceAsStream(path);
                if (stream == null) throw new IOException("Schema 制品缺少登记资源");
                try (stream) { return stream.readAllBytes(); }
            });
        }
        return classpathCatalogue;
    }

    private SchemaCatalogue(Source source) throws IOException, NoSuchAlgorithmException {
        JsonNode manifest = publishedManifest(source.read("contracts/catalogue.json"), source.read(EXTENSION_PATH), source.read(SIMULATION_EXTENSION_PATH), source.read(TASK_RUNTIME_EXTENSION_PATH));
        appendEvaluationContext(manifest,source.read("contracts/catalogue.json"),source.read(EVALUATION_CONTEXT_EXTENSION_PATH));
        if (!"1.0.0".equals(manifest.path("schema_version").asText())) throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED);
        URI base = URI.create(manifest.required("base_uri").asText());
        Map<String, String> registered = new HashMap<>();
        ObjectMapper yaml = new YAMLMapper();
        for (JsonNode entry : manifest.required("schemas")) {
            String name = entry.required("name").asText();
            if (documents.containsKey(name)) throw new IllegalArgumentException("Schema 名称重复");
            String path = entry.required("path").asText();
            if (!path.matches("[A-Za-z0-9][A-Za-z0-9._/-]*")
                    || java.util.Arrays.stream(path.split("/", -1)).anyMatch(part -> part.isEmpty() || part.equals(".") || part.equals("..")))
                throw new IllegalArgumentException("Schema 路径必须使用固定相对路径");
            byte[] bytes = source.read(path);
            String digest = entry.required("sha256").asText();
            if (!WireValues.sha256(bytes).equals(digest)) throw new IllegalArgumentException("Schema 摘要不一致");
            JsonNode schema = (path.endsWith(".yaml") ? yaml : json).readTree(bytes);
            String uri;
            if (schema.has("$id")) {
                if (!schema.required("$id").isTextual()) throw new IllegalArgumentException("Schema 声明身份必须使用文本");
                URI declared = URI.create(schema.required("$id").textValue());
                if (!declared.isAbsolute() || declared.getRawFragment() != null || declared.getRawQuery() != null)
                    throw new IllegalArgumentException("Schema 声明身份必须使用完整固定引用");
                uri = declared.toString();
            } else {
                uri = base.resolve(path).toString();
            }
            documents.put(name, schema);
            uris.put(name, uri);
            digests.put(name, digest);
            if (registered.putIfAbsent(uri, json.writeValueAsString(schema)) != null)
                throw new IllegalArgumentException("Schema 发布引用重复");
        }
        // 未登记的引用立即拒绝，阻止默认加载器访问网络或其他文件。
        factory = JsonSchemaFactory.getInstance(VersionFlag.V202012, builder -> builder.schemaLoaders(loaders -> loaders.schemas(iri -> {
            String data = registered.get(iri);
            if (data == null) throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED);
            return data;
        })));
        for (String name : documents.keySet()) {
            register(name, null);
            var definitions = documents.get(name).required("$defs").fieldNames();
            while (definitions.hasNext()) register(name, definitions.next());
        }
    }

    // This additive publication has its own trusted identity. Never rewrite the legacy
    // catalogue: its raw digest is part of persisted permission and artifact manifests.
    static JsonNode publishedManifest(byte[] baseBytes, byte[] extensionBytes) throws IOException, NoSuchAlgorithmException {
        if (!EXTENSION_SHA256.equals(WireValues.sha256(extensionBytes)))
            throw new IllegalArgumentException("Schema 扩展目录摘要不一致");
        ObjectMapper parser = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
        JsonNode extension = parser.readTree(extensionBytes);
        if (!"1.0.0".equals(extension.path("schema_version").asText())
                || !WireValues.sha256(baseBytes).equals(extension.path("base_catalogue_sha256").asText()))
            throw new IllegalArgumentException("Schema 扩展目录基础身份不一致");
        JsonNode manifest = parser.readTree(baseBytes);
        var schemas = (com.fasterxml.jackson.databind.node.ArrayNode) manifest.required("schemas");
        java.util.Set<String> names = new java.util.HashSet<>();
        for (JsonNode entry : schemas) if (!names.add(entry.required("name").asText()))
            throw new IllegalArgumentException("Schema 名称重复");
        for (JsonNode entry : extension.required("schemas")) {
            if (!names.add(entry.required("name").asText())) throw new IllegalArgumentException("Schema 名称重复");
            schemas.add(entry.deepCopy());
        }
        return manifest;
    }

    static JsonNode publishedManifest(byte[] baseBytes, byte[] extensionBytes, byte[] simulationBytes) throws IOException, NoSuchAlgorithmException {
        JsonNode manifest = publishedManifest(baseBytes, extensionBytes);
        if (!SIMULATION_EXTENSION_SHA256.equals(WireValues.sha256(simulationBytes)))
            throw new IllegalArgumentException("Schema 模拟扩展目录摘要不一致");
        JsonNode extra = new ObjectMapper().readTree(simulationBytes);
        if (!"1.0.0".equals(extra.path("schema_version").asText())
                || !WireValues.sha256(baseBytes).equals(extra.path("base_catalogue_sha256").asText()))
            throw new IllegalArgumentException("Schema 模拟扩展基础身份不一致");
        var schemas = (com.fasterxml.jackson.databind.node.ArrayNode) manifest.required("schemas");
        java.util.Set<String> names = new java.util.HashSet<>();
        for (JsonNode entry : schemas) names.add(entry.required("name").asText());
        for (JsonNode entry : extra.required("schemas")) {
            if (!names.add(entry.required("name").asText())) throw new IllegalArgumentException("Schema 名称重复");
            schemas.add(entry.deepCopy());
        }
        return manifest;
    }

    static JsonNode publishedManifest(byte[] baseBytes, byte[] extensionBytes, byte[] simulationBytes, byte[] taskRuntimeBytes) throws IOException, NoSuchAlgorithmException {
        JsonNode manifest = publishedManifest(baseBytes, extensionBytes, simulationBytes);
        if (!TASK_RUNTIME_EXTENSION_SHA256.equals(WireValues.sha256(taskRuntimeBytes)))
            throw new IllegalArgumentException("Schema 任务运行扩展目录摘要不一致");
        JsonNode extra = new ObjectMapper().readTree(taskRuntimeBytes);
        if (!"1.0.0".equals(extra.path("schema_version").asText())
                || !WireValues.sha256(baseBytes).equals(extra.path("base_catalogue_sha256").asText()))
            throw new IllegalArgumentException("Schema 任务运行扩展基础身份不一致");
        var schemas = (com.fasterxml.jackson.databind.node.ArrayNode) manifest.required("schemas");
        java.util.Set<String> names = new java.util.HashSet<>();
        for (JsonNode entry : schemas) names.add(entry.required("name").asText());
        for (JsonNode entry : extra.required("schemas")) {
            if (!names.add(entry.required("name").asText())) throw new IllegalArgumentException("Schema 名称重复");
            schemas.add(entry.deepCopy());
        }
        return manifest;
    }

    private static void appendEvaluationContext(JsonNode manifest,byte[] baseBytes,byte[] bytes)throws IOException,NoSuchAlgorithmException {
        if(!EVALUATION_CONTEXT_EXTENSION_SHA256.equals(WireValues.sha256(bytes)))throw new IllegalArgumentException("Evaluation context extension digest mismatch");
        JsonNode extension=JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build().readTree(bytes);
        if(!"1.0.0".equals(extension.path("schema_version").asText())||!WireValues.sha256(baseBytes).equals(extension.path("base_catalogue_sha256").asText()))throw new IllegalArgumentException("Evaluation context extension base mismatch");
        var names=new java.util.HashSet<String>();for(var entry:manifest.required("schemas"))names.add(entry.required("name").asText());
        var entries=(com.fasterxml.jackson.databind.node.ArrayNode)manifest.required("schemas");for(var entry:extension.required("schemas")){if(!names.add(entry.required("name").asText()))throw new IllegalArgumentException("Duplicate evaluation context schema");entries.add(entry.deepCopy());}
    }

    private void register(String name, String definition) {
        String reference = schemaRef(name, definition);
        compile(reference);
        if (publications.putIfAbsent(reference, new PublishedSchema(name, definition, reference, digests.get(name))) != null) {
            throw new IllegalArgumentException("Schema 发布引用重复");
        }
    }

    public PublishedSchema requirePublished(String schemaRef, String expectedSha256) {
        // 固定原引用与文件摘要共同确定版本；不解释客户端URL或查询参数。
        PublishedSchema published = publications.get(schemaRef);
        if (published == null || !published.schemaSha256().equals(expectedSha256)) {
            throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED);
        }
        return published;
    }

    public ValidatedPayload bindPublished(String schemaRef, String expectedSha256, byte[] body) throws IOException {
        PublishedSchema published = requirePublished(schemaRef, expectedSha256);
        String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(Objects.requireNonNull(body))).toString();
        JsonNode value = payloadJson.readTree(text);
        if (value == null || value.isMissingNode()) throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
        return bind(published.name(), value, published.definition());
    }

    /** Digest of the exact registered schema bytes; never a caller-selected trust root. */
    public String schemaDigest(String name) {
        String digest = digests.get(name);
        if (digest == null) throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED);
        return digest;
    }

    public String schemaRef(String name, String definition) {
        if (!documents.containsKey(name)) throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED);
        String uri = uris.get(name);
        if (definition != null) {
            if (!documents.get(name).path("$defs").has(definition)) throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED);
            uri += "#/$defs/" + definition;
        }
        return uri;
    }

    private JsonSchema compile(String uri) {
        JsonSchema schema = factory.getSchema(SchemaLocation.of(uri), config);
        schema.initializeValidators();
        validators.put(uri, schema);
        return schema;
    }

    public void validate(String name, JsonNode value, String definition) {
        String uri = schemaRef(name, definition);
        if (!validators.get(uri).validate(Objects.requireNonNull(value)).isEmpty()) {
            throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
        }
    }

    public JsonSchema compileApiSchema(JsonNode document) {
        // 补充API复用同一封闭加载器；固定发布目录及已有内容摘要保持原值。
        return factory.getSchema(Objects.requireNonNull(document), config);
    }

    public ValidatedPayload bind(String name, JsonNode value, String definition) throws IOException {
        JsonNode saved = value.deepCopy();
        validate(name, saved, definition);
        WireValues.checkCanonicalNumbers(saved);
        byte[] bytes = canonicalBytes(saved);
        return new ValidatedPayload(schemaRef(name, definition), digests.get(name), new String(bytes, StandardCharsets.UTF_8));
    }

    private byte[] canonicalBytes(JsonNode value) throws IOException {
        if (value.isContainerNode()) return new JsonCanonicalizer(json.writeValueAsBytes(value)).getEncodedUTF8();
        if (value.isNumber()) return NumberToJSON.serializeNumber(value.doubleValue()).getBytes(StandardCharsets.UTF_8);
        if (value.isTextual() || value.isBoolean() || value.isNull()) return json.writeValueAsBytes(value);
        throw new IllegalArgumentException("载荷必须使用 JSON 数据类型");
    }

    public int definitionCount() { return validators.size() - documents.size(); }

    public record PublishedSchema(String name, String definition, String schemaRef, String schemaSha256) { }

    public record ValidatedPayload(String schemaRef, String schemaSha256, String canonicalJson) {
        public byte[] canonicalBytes() { return canonicalJson.getBytes(StandardCharsets.UTF_8); }
    }
}
