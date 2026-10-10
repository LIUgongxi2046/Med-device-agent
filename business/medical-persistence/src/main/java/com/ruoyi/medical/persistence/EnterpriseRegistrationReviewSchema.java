package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.networknt.schema.*;
import com.ruoyi.medical.contracts.*;
import java.io.IOException;
import java.text.Normalizer;
import java.util.HashSet;
import java.util.Objects;
import org.apache.commons.codec.digest.DigestUtils;
import org.erdtman.jcs.JsonCanonicalizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name="medical.identity.mode", havingValue="local")
public final class EnterpriseRegistrationReviewSchema {
    private final JsonMapper json = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .propertyNamingStrategy(com.fasterxml.jackson.databind.PropertyNamingStrategies.SNAKE_CASE).build();
    private final JsonSchema schema;
    private final SchemaCatalogue shared;
    private final EnterpriseRegistrationSchema registrations;
    private final PermissionSchema permissions;
    private final String manifest;

    public EnterpriseRegistrationReviewSchema(EnterpriseRegistrationSchema registrations, PermissionSchema permissions) throws Exception {
        this.registrations = registrations; this.permissions = permissions; shared = SchemaCatalogue.fromClasspath();
        byte[] bytes = resource("enterprise-registration-review.schema.json");
        schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(json.readTree(bytes),
                SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());
        schema.initializeValidators();
        var metadata = object(); metadata.put("schema_version", "1.0.0");
        metadata.put("enterprise-registration-review.schema.json", DigestUtils.sha256Hex(bytes));
        metadata.put("registration_manifest", registrations.manifest()); metadata.put("permission_manifest", permissions.manifestSha256());
        metadata.put("contracts/catalogue.json", DigestUtils.sha256Hex(resource("contracts/catalogue.json")));
        manifest = hash(metadata);
    }

    JsonNode request(byte[] bytes, String key) throws IOException {
        if (bytes.length > 65536 || key == null || !key.matches("[!-~]{16,128}")) invalid();
        JsonNode body = json.readTree(bytes); requestNode(body); return body;
    }
    void requestNode(JsonNode body) throws IOException {
        validate("Request", body);
        String reason = body.required("reason").textValue();
        if (reason.isBlank() || !reason.equals(reason.strip()) || !Normalizer.isNormalized(reason, Normalizer.Form.NFC)
                || reason.codePoints().anyMatch(code -> Character.isISOControl(code) || Character.getType(code) == Character.FORMAT)
                || !java.nio.charset.StandardCharsets.UTF_8.newEncoder().canEncode(reason)) invalid();
        var actions = new HashSet<String>();
        for (JsonNode grant : body.required("governance_grants")) {
            String action = grant.required("action").textValue();
            if (!actions.add(action)) invalid();
            var condition = permissions.condition(canonical(grant.required("condition")));
            String purpose = switch (action) { case "member.manage" -> "PERMISSION_ADMINISTRATION"; case "role.manage" -> "ROLE_ADMINISTRATION"; case "project.create" -> "PROJECT_ADMINISTRATION"; default -> throw new IllegalStateException("GOVERNANCE_ACTION_INVALID"); };
            if (!condition.identity_modes().contains("local") || condition.purposes() != null && !condition.purposes().contains(purpose)) invalid();
            if (!grant.required("valid_to").isNull()) PermissionAuthority.time(grant.required("valid_to").textValue());
        }
        if (body.required("decision").textValue().equals("APPROVE") && !actions.contains("member.manage")) invalid();
    }
    String requestHash(JsonNode body, String registration, String subject) throws IOException {
        var node = object(); node.put("method", "POST"); node.put("path", "/api/enterprise-registration-reviews/" + registration + "/decisions");
        node.put("schema_version", "1.0.0"); node.put("subject_id", subject); node.set("body", body); return hash(node);
    }
    JsonNode parse(String value) throws IOException { return json.readTree(value); }
    JsonNode envelope(String definition, JsonNode body, String request, String revision) throws IOException {
        verify(definition, body);
        JsonNode result = json.valueToTree(new InterfaceProtocol.Envelope("1.0.0", request, revision, body));
        shared.validate("interfaces", result, "Envelope"); return result;
    }
    JsonNode readEnvelope(String value) throws IOException {
        JsonNode result = parse(value); shared.validate("interfaces", result, "Envelope"); verify("Receipt", result.required("data"));
        if (!result.required("resource_revision").equals(result.required("data").required("revision"))) invalid();
        return result;
    }
    void verify(String definition, JsonNode node) throws IOException {
        validate(definition, node);
        if (definition.equals("Page")) { for (var item : node.required("items")) verify("Detail", item); }
        if (definition.equals("Detail")) {
            registrations.detail(node.required("application"));
            JsonNode review = node.required("review");
            if (review.isNull()) {
                if (!node.required("status").textValue().equals("PENDING_REVIEW") || !node.required("revision").textValue().equals("1")) invalid();
            } else {
                verify("Receipt", review);
                if (!node.required("status").equals(review.required("status")) || !node.required("revision").equals(review.required("revision"))
                        || !node.required("application").required("registration_id").equals(review.required("registration_id"))
                        || !node.required("application").required("content_sha256").equals(review.required("registration_content_sha256"))) invalid();
            }
        }
        if (definition.equals("Receipt")) {
            JsonNode request = node.required("request"); requestNode(request);
            boolean approved = request.required("decision").textValue().equals("APPROVE");
            if (!node.required("decision").equals(request.required("decision")) || !node.required("status").textValue().equals(approved ? "APPROVED" : "REJECTED")
                    || node.required("tenant_id").isNull() == approved || node.required("member_id").isNull() == approved || node.required("basis").isNull() == approved
                    || node.required("governance_grants").size() != request.required("governance_grants").size()) invalid();
            var ids = new HashSet<String>();
            for (int index = 0; index < node.required("governance_grants").size(); index++) {
                var grant = node.required("governance_grants").get(index); var input = request.required("governance_grants").get(index);
                if (!ids.add(grant.required("grant_id").textValue()) || !grant.required("valid_from").equals(node.required("decided_at"))) invalid();
                for (String field : java.util.List.of("action", "condition", "valid_to")) if (!grant.required(field).equals(input.required(field))) invalid();
            }
            if (approved) {
                var basis = node.required("basis");
                if (!basis.required("content_sha256").textValue().equals(hash(basisContent(node)))) invalid();
            }
            var content = node.deepCopy(); ((com.fasterxml.jackson.databind.node.ObjectNode) content).remove("content_sha256");
            if (!node.required("content_sha256").textValue().equals(hash(content))) invalid();
        }
    }
    JsonNode basisContent(JsonNode receipt) {
        var value = object();
        for (String field : java.util.List.of("schema_version", "registration_id", "registration_content_sha256", "request", "decided_by", "decided_at", "tenant_id", "member_id")) value.set(field, receipt.required(field));
        return value;
    }
    void validate(String name, JsonNode node) {
        var definition = schema.getSubSchema(new JsonNodePath(PathType.JSON_POINTER).append("$defs").append(name));
        if (node == null || !definition.validate(node).isEmpty()) invalid();
    }
    com.fasterxml.jackson.databind.node.ObjectNode object() { return json.createObjectNode(); }
    String canonical(JsonNode node) throws IOException { return new JsonCanonicalizer(json.writeValueAsBytes(node)).getEncodedString(); }
    String hash(JsonNode node) throws IOException { return DigestUtils.sha256Hex(canonical(node)); }
    String manifest() { return manifest; }
    private byte[] resource(String name) throws IOException { try (var stream = Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream(name))) { return stream.readAllBytes(); } }
    private static void invalid() { throw new SchemaRejection(ErrorCode.SCHEMA_INVALID); }
}
