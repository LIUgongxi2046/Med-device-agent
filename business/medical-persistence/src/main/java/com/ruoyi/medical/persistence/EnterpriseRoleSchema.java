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
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.List;
import java.util.Objects;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Component;

@Component
public final class EnterpriseRoleSchema {
    private final JsonMapper json = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private final JsonSchema schema;
    private final PermissionSchema permissions;
    private final InvitationSchema invitations;
    private final SchemaCatalogue shared;
    private final String manifest;

    public EnterpriseRoleSchema(PermissionSchema permissions, InvitationSchema invitations) throws IOException, NoSuchAlgorithmException {
        this.permissions = permissions; this.invitations = invitations; shared = SchemaCatalogue.fromClasspath();
        byte[] bytes = resource("enterprise-roles.schema.json");
        JsonNode document = json.readTree(bytes);
        schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(document,
                SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());
        schema.initializeValidators();
        var names = document.required("$defs").fieldNames();
        while (names.hasNext()) definition(names.next()).initializeValidators();
        var metadata = permissions.object(); metadata.put("schema_version", "1.0.0");
        for (String name : List.of("enterprise-roles.schema.json", "member-invitations.schema.json", "permission-decisions.schema.json", "contracts/catalogue.json")) {
            metadata.put(name, DigestUtils.sha256Hex(resource(name)));
        }
        manifest = DigestUtils.sha256Hex(permissions.canonical(metadata));
    }

    EnterpriseRoleProtocol.Publish publish(byte[] bytes, String roleId) throws IOException {
        JsonNode node = json.readTree(bytes); validate("Publish", node);
        var value = json.treeToValue(node, EnterpriseRoleProtocol.Publish.class);
        var role = invitations.role(permissions.canonical(node.required("definition")));
        if (!roleId.equals(role.role_id())) invalid();
        permissions.basis(node.required("approval_basis"));
        text(value.name(), 120); text(value.reason(), 2000);
        PermissionSchema.revision(value.expected_head_revision());
        var from = PermissionAuthority.time(value.valid_from());
        if (value.valid_to() != null && !PermissionAuthority.time(value.valid_to()).isAfter(from)) invalid();
        return value;
    }

    EnterpriseRoleProtocol.Revoke revoke(byte[] bytes) throws IOException {
        JsonNode node = json.readTree(bytes); validate("Revoke", node);
        var value = json.treeToValue(node, EnterpriseRoleProtocol.Revoke.class);
        if (PermissionSchema.revision(value.expected_revision()).signum() == 0) invalid();
        text(value.reason(), 2000); return value;
    }

    JsonNode envelope(String kind, JsonNode value, String requestId, String revision) throws IOException {
        verify(kind, value);
        if (revision != null) PermissionSchema.revision(revision);
        JsonNode node = permissions.tree(new InterfaceProtocol.Envelope("1.0.0", requestId, revision, value));
        shared.validate("interfaces", node, "Envelope");
        if (kind.equals("Role") && !node.required("resource_revision").equals(value.required("revision"))
                || kind.equals("Receipt") && !node.required("resource_revision").equals(value.required("role").required("revision"))) invalid();
        return node;
    }

    JsonNode readReceipt(String text) throws IOException {
        JsonNode node = json.readTree(text); shared.validate("interfaces", node, "Envelope");
        if (!envelope("Receipt", node.required("data"), node.required("request_id").textValue(),
                node.required("resource_revision").textValue()).equals(node)) invalid();
        return node;
    }

    void verify(String kind, JsonNode value) throws IOException {
        validate(kind, value);
        if (kind.equals("Role")) {
            var role = invitations.role(permissions.canonical(value.required("definition")));
            if (!role.role_id().equals(value.required("role_id").textValue())
                    || !DigestUtils.sha256Hex(permissions.canonicalObject(role)).equals(value.required("content_sha256").textValue())) invalid();
            text(value.required("name").textValue(), 120); text(value.required("reason").textValue(), 2000);
            for (String name : List.of("revision", "authority_revision")) if (PermissionSchema.revision(value.required(name).textValue()).signum() == 0) invalid();
            if (PermissionSchema.revision(value.required("head").required("revision").textValue()).signum() == 0) invalid();
            var from = PermissionAuthority.time(value.required("valid_from").textValue());
            PermissionAuthority.time(value.required("approved_at").textValue());
            if (!value.required("valid_to").isNull() && !PermissionAuthority.time(value.required("valid_to").textValue()).isAfter(from)) invalid();
        } else if (kind.equals("Listing")) {
            java.util.Set<String> ids = new java.util.HashSet<>();
            for (var role : value.required("roles")) {
                verify("Role", role);
                if (!ids.add(role.required("role_id").textValue())
                        || !role.required("version_id").equals(role.required("head").required("current_version"))) invalid();
            }
            if (!value.required("next_after").isNull() && (ids.isEmpty()
                    || !value.required("next_after").equals(value.required("roles").get(ids.size()-1).required("role_id")))) invalid();
        } else if (kind.equals("Receipt")) {
            var role = value.required("role"); verify("Role", role);
            if (!role.required("tenant_id").equals(value.required("current_context").required("tenant_id"))
                    || !role.required("status").textValue().equals(value.required("decision").textValue().equals("PUBLISH") ? "ACTIVE" : "REVOKED")) invalid();
            for (String name : List.of("context_revision", "auth_epoch", "governance_epoch")) {
                PermissionSchema.revision(value.required("current_context").required(name).textValue());
            }
            if (value.required("decision").textValue().equals("PUBLISH")
                    && (!value.required("revoked_grants").isEmpty() || !value.required("terminated_invitations").isEmpty())) invalid();
        }
    }

    String hash(String path, Object request, String context, String session) throws IOException {
        var node = permissions.object(); node.put("method", "POST"); node.put("path", path); node.put("schema_version", "1.0.0");
        node.putNull("query"); node.put("context_header", context); node.put("session_id", session); node.set("body", permissions.tree(request));
        return DigestUtils.sha256Hex(permissions.canonical(node));
    }
    String manifestSha256() { return manifest; }
    private void validate(String name, JsonNode node) { if (node == null || !definition(name).validate(node).isEmpty()) invalid(); }
    private JsonSchema definition(String name) { return schema.getSubSchema(new JsonNodePath(PathType.JSON_POINTER).append("$defs").append(name)); }
    private static void text(String value, int max) {
        if (value == null || value.isBlank() || !value.equals(value.strip()) || !Normalizer.isNormalized(value, Normalizer.Form.NFC)
                || value.codePointCount(0, value.length()) > max || value.codePoints().anyMatch(Character::isISOControl)) invalid();
    }
    private byte[] resource(String name) throws IOException {
        try (var input = Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream(name))) { return input.readAllBytes(); }
    }
    private static void invalid() { throw new SchemaRejection(ErrorCode.SCHEMA_INVALID); }
}
