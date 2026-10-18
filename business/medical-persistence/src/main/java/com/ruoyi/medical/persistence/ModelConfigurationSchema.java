package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.networknt.schema.*;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.InterfaceProtocol;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import com.ruoyi.medical.contracts.SchemaRejection;
import java.io.IOException;
import java.net.URI;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Component;

@Component
public final class ModelConfigurationSchema {
    static final int MAXIMUM_REQUEST_BYTES = 65_536;
    private final JsonMapper mapper = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private final JsonSchema schema;
    private final JsonSchema simulationSchema;
    private final String simulationManifest;
    private final PermissionSchema json;
    private final SchemaCatalogue shared;
    private final String manifest;
    public ModelConfigurationSchema(PermissionSchema json) throws IOException, NoSuchAlgorithmException {
        this.json = json; shared = SchemaCatalogue.fromClasspath();
        var document = mapper.readTree(resource("model-configuration.schema.json"));
        schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(document,
                SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());
        schema.initializeValidators(); var names = document.required("$defs").fieldNames();
        while (names.hasNext()) definition(names.next()).initializeValidators();
        var metadata = json.object(); metadata.put("schema_version", "1.0.0");
        for (String name : List.of("model-configuration.schema.json", "permission-decisions.schema.json", "contracts/catalogue.json"))
            metadata.put(name, DigestUtils.sha256Hex(resource(name)));
        manifest = DigestUtils.sha256Hex(json.canonical(metadata));
        var simulationDocument = mapper.readTree(resource("simulation-model-configuration.schema.json"));
        simulationSchema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(simulationDocument,
                SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());
        simulationSchema.initializeValidators();
        var simulationNames = simulationDocument.required("$defs").fieldNames();
        while (simulationNames.hasNext()) definition(simulationNames.next(), true).initializeValidators();
        String provenance = DigestUtils.sha256Hex(resource("model-simulation-provenance.schema.json"));
        if (!provenance.equals(simulationDocument.at("/$defs/Definition/properties/provenance_schema_sha256/const").textValue())) invalid();
        metadata.put("simulation-model-configuration.schema.json", DigestUtils.sha256Hex(resource("simulation-model-configuration.schema.json")));
        metadata.put("model-simulation-provenance.schema.json", provenance);
        simulationManifest = DigestUtils.sha256Hex(json.canonical(metadata));
    }
    ModelConfigurationProtocol.Save save(byte[] bytes) throws IOException { return save(bytes, false); }
    ModelConfigurationProtocol.Save save(byte[] bytes, boolean simulation) throws IOException {
        if (bytes.length > MAXIMUM_REQUEST_BYTES) invalid();
        var value = mapper.treeToValue(parse("Save", bytes, simulation), ModelConfigurationProtocol.Save.class);
        var revision = PermissionSchema.revision(value.expected_revision());
        if (value.config_id() == null ? revision.signum() != 0 || value.based_on_revision() != null
                : revision.signum() <= 0 || value.based_on_revision() == null) invalid();
        if (value.config_id() != null) PermissionSchema.next(value.expected_revision());
        validate(value.definition()); ProjectSchema.text(value.reason(), 2000); dependencies(value.based_on_revision(), value.definition());
        return value;
    }
    ModelConfigurationProtocol.Fixed fixed(String content) throws IOException {
        var tree = mapper.readTree(content); verify("Fixed", tree);
        var value = mapper.treeToValue(tree, ModelConfigurationProtocol.Fixed.class);
        if (PermissionSchema.revision(value.authority_revision()).signum() <= 0) invalid();
        validate(value.definition()); ProjectSchema.text(value.reason(), 2000); PermissionAuthority.time(value.created_at());
        dependencies(value.based_on_revision(), value.definition()); return value;
    }
    private void validate(ModelConfigurationProtocol.ConfigurationDefinition definition) {
        var limits = definition.limits();
        for (String count : List.of(limits.context_window(), limits.max_output_tokens(), limits.max_concurrency(),
                limits.requests_per_minute(), limits.tokens_per_minute()))
            if (PermissionSchema.revision(count).signum() <= 0) invalid();
        if (PermissionSchema.revision(limits.max_output_tokens()).compareTo(PermissionSchema.revision(limits.context_window())) > 0) invalid();
        if (limits.temperature_enabled() && !limits.reasoning_efforts().contains("none")) invalid();
        if (definition instanceof ModelConfigurationProtocol.SimulationDefinition simulation) {
            ProjectSchema.text(simulation.model_id(), 256); ProjectSchema.text(simulation.model_version(), 256); return;
        }
        var value = (ModelConfigurationProtocol.Definition) definition;
        for (var field : List.of(value.provider(), value.region(), value.model_id(), value.model_version(), value.model_protocol_revision()))
            ProjectSchema.text(field, 256);
        URI endpoint;
        try { endpoint = URI.create(value.base_url()); } catch (IllegalArgumentException failure) { invalid(); return; }
        // This is storage validation, not an SSRF/network authorization. No endpoint is fetched here.
        if (!"https".equals(endpoint.getScheme()) || endpoint.getHost() == null || endpoint.getRawUserInfo() != null
                || endpoint.getRawQuery() != null || endpoint.getRawFragment() != null
                || endpoint.getPort() == 0 || endpoint.getPort() > 65535 || !endpoint.normalize().equals(endpoint)) invalid();
        PermissionSchema.revision(value.data_policy().retention_days());
        for (String field : value.data_policy().allowed_classifications()) ProjectSchema.text(field, 24);
        for (String field : value.data_policy().allowed_regions()) ProjectSchema.text(field, 128);
        if (!value.data_policy().allowed_regions().contains(value.region())) invalid();
    }
    List<PermissionProtocol.Basis> dependencies(PermissionProtocol.Basis base, ModelConfigurationProtocol.ConfigurationDefinition value) {
        var refs = new TreeMap<String,PermissionProtocol.Basis>();
        var requested = new java.util.ArrayList<PermissionProtocol.Basis>();
        if (value instanceof ModelConfigurationProtocol.Definition external) {
            requested.add(external.price_basis()); requested.add(external.data_policy().policy_basis());
        }
        if (base != null) requested.add(base);
        for (var ref : requested) {
            var prior = refs.putIfAbsent(ref.version_id(), ref);
            if (prior != null && !prior.equals(ref)) invalid();
        }
        return List.copyOf(refs.values());
    }
    JsonNode publicDefinition(ModelConfigurationProtocol.ConfigurationDefinition value) {
        var view = json.tree(value).deepCopy();
        if (!simulation(value)) ((com.fasterxml.jackson.databind.node.ObjectNode) view).remove(List.of("auth_ref", "credential_revision"));
        verify("PublicDefinition", view, simulation(value)); return view;
    }
    JsonNode read(String value) throws IOException { return mapper.readTree(value); }
    JsonNode envelope(String kind, JsonNode value, String requestId, String revision) {
        verify(kind, value); PermissionSchema.revision(revision);
        var result = json.tree(new InterfaceProtocol.Envelope("1.0.0", requestId, revision, value));
        shared.validate("interfaces", result, "Envelope");
        if (!value.required("revision").textValue().equals(revision)) invalid(); return result;
    }
    JsonNode receipt(String content, boolean simulation) throws IOException {
        var value = read(content); verify("Receipt", value.path("data"), simulation); return receipt(content);
    }
    JsonNode receipt(String content) throws IOException {
        var value = read(content); shared.validate("interfaces", value, "Envelope");
        if (!envelope("Receipt", value.required("data"), value.required("request_id").textValue(),
                value.required("resource_revision").textValue()).equals(value)) invalid();
        var context = value.required("data").required("current_context");
        if (!context.required("tenant_id").equals(value.required("data").required("tenant_id"))) invalid();
        for (String key : List.of("context_revision", "auth_epoch", "governance_epoch")) PermissionSchema.revision(context.required(key).textValue());
        return value;
    }
    String hash(ModelConfigurationProtocol.Save value, String context, String session) throws IOException {
        var request = json.object(); request.put("method", "POST"); request.put("path", simulation(value.definition()) ? "/api/simulation-model-config-revisions" : "/api/model-config-revisions");
        request.put("schema_version", "1.0.0"); request.putNull("query"); request.put("context_header", context);
        request.put("session_id", session); request.set("body", json.tree(value)); return DigestUtils.sha256Hex(json.canonical(request));
    }
    void verify(String kind, JsonNode value) {
        verify(kind, value, value != null && (value.path("execution_mode").asText().equals("SIMULATION")
                || value.path("definition").path("execution_mode").asText().equals("SIMULATION")));
    }
    void verify(String kind, JsonNode value, boolean simulation) { if (value == null || !definition(kind, simulation).validate(value).isEmpty()) invalid(); }
    static boolean simulation(ModelConfigurationProtocol.ConfigurationDefinition value) { return value instanceof ModelConfigurationProtocol.SimulationDefinition; }
    static String mode(ModelConfigurationProtocol.ConfigurationDefinition value) { return simulation(value) ? "SIMULATION" : "EXTERNAL"; }
    String manifestSha256(boolean simulation) { return simulation ? simulationManifest : manifest; }
    JsonNode projection(ModelConfigurationProtocol.ConfigurationDefinition value) {
        if (value instanceof ModelConfigurationProtocol.SimulationDefinition simulation) {
            var result = json.object(); result.set("definition", json.tree(simulation)); result.put("provider", "SIMULATION"); result.put("region", "LOCAL");
            result.put("model_id", simulation.model_id()); result.put("model_version", simulation.model_version()); result.set("limits", json.tree(simulation.limits()));
            result.put("request_parameter_schema_ref", "agent-design/model-stream.schema.json#/$defs/RequestParameters");
            result.put("model_protocol_revision", "1.0.0"); return result;
        }
        var external = (ModelConfigurationProtocol.Definition) value;
        com.fasterxml.jackson.databind.node.ObjectNode result = json.tree(external).deepCopy();
        result.remove(List.of("price_basis", "request_parameter_schema_sha256")); result.put("price_version", external.price_basis().version_id()); return result;
    }
    String manifestSha256() { return manifest; }
    private JsonNode parse(String kind, byte[] bytes, boolean simulation) throws IOException { var value = mapper.readTree(bytes); verify(kind, value, simulation); return value; }
    private JsonSchema definition(String kind) { return definition(kind, false); }
    private JsonSchema definition(String kind, boolean simulation) { return (simulation ? simulationSchema : schema).getSubSchema(new JsonNodePath(PathType.JSON_POINTER).append("$defs").append(kind)); }
    private byte[] resource(String name) throws IOException {
        try (var stream = Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream(name))) { return stream.readAllBytes(); }
    }
    private static void invalid() { throw new SchemaRejection(ErrorCode.SCHEMA_INVALID); }
}
