package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.networknt.schema.JsonNodePath;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.PathType;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion.VersionFlag;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.InterfaceProtocol;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import com.ruoyi.medical.contracts.SchemaRejection;
import com.ruoyi.medical.identity.IdentityBindingSchema;
import com.ruoyi.medical.identity.InvitationCredential;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Objects;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Component;

@Component
public final class InvitationSchema {
    private final JsonMapper json = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();
    private final JsonSchema schema;
    private final PermissionSchema permissions;
    private final IdentityBindingSchema bindings;
    private final SchemaCatalogue shared;
    private final String manifest;

    public InvitationSchema(PermissionSchema permissions, IdentityBindingSchema bindings) throws IOException, NoSuchAlgorithmException {
        this.permissions = permissions; this.bindings = bindings; shared = SchemaCatalogue.fromClasspath();
        byte[] bytes = resource("member-invitations.schema.json");
        JsonNode document = json.readTree(bytes);
        schema = JsonSchemaFactory.getInstance(VersionFlag.V202012).getSchema(document,
                SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());
        schema.initializeValidators();
        var names = document.required("$defs").fieldNames();
        while (names.hasNext()) definition(names.next()).initializeValidators();
        var metadata = permissions.object(); metadata.put("schema_version", "1.0.0");
        for (String name : List.of("member-invitations.schema.json", "permission-decisions.schema.json", "identity-binding.schema.json", "contracts/catalogue.json")) {
            metadata.put(name, DigestUtils.sha256Hex(resource(name)));
        }
        manifest = DigestUtils.sha256Hex(permissions.canonical(metadata));
    }

    public InvitationProtocol.Create create(byte[] bytes) throws IOException {
        JsonNode node = read(bytes); validate("Create", node);
        bindings.readBinding(permissions.canonical(node.required("identity_binding")).getBytes(StandardCharsets.UTF_8));
        permissions.scope(node.required("scope")); permissions.basis(node.required("basis"));
        PermissionAuthority.time(node.required("expires_at").textValue());
        var value = json.treeToValue(node, InvitationProtocol.Create.class);
        if (value.roles().stream().map(InvitationProtocol.RoleReference::role_id).distinct().count() != value.roles().size()) invalid();
        return value;
    }

    public InvitationProtocol.Accept accept(byte[] bytes) throws IOException {
        JsonNode node = read(bytes); validate("Accept", node);
        var value = json.treeToValue(node, InvitationProtocol.Accept.class);
        PermissionSchema.revision(value.expected_revision());
        InvitationCredential.validate(value.credential());
        return value;
    }

    public InvitationProtocol.RoleDefinition role(String text) throws IOException {
        JsonNode node = json.readTree(text); validate("RoleDefinition", node);
        permissions.condition(permissions.canonical(node.required("condition")));
        return json.treeToValue(node, InvitationProtocol.RoleDefinition.class);
    }

    public List<InvitationProtocol.RoleReference> roles(String text) throws IOException {
        JsonNode node = json.readTree(text); validate("Roles", node);
        List<InvitationProtocol.RoleReference> result = List.of(json.treeToValue(node, InvitationProtocol.RoleReference[].class));
        if (result.stream().map(InvitationProtocol.RoleReference::role_id).distinct().count() != result.size()) invalid();
        return result;
    }

    public List<InvitationProtocol.Authorization> authorizations(String text) throws IOException {
        JsonNode node = json.readTree(text); validate("Authorizations", node);
        for (JsonNode item : node) {
            permissions.condition(permissions.canonical(item.required("condition")));
            PermissionSchema.revision(item.required("authority_revision").textValue());
            if (!item.required("valid_to").isNull()) PermissionAuthority.time(item.required("valid_to").textValue());
        }
        return List.of(json.treeToValue(node, InvitationProtocol.Authorization[].class));
    }

    public JsonNode envelope(JsonNode receipt, String requestId, boolean creation) {
        validate(creation ? "CreationReceipt" : "AcceptanceReceipt", receipt);
        for (String name : creation ? List.of("revision", "auth_epoch") : List.of("revision", "member_revision", "auth_epoch")) {
            PermissionSchema.revision(receipt.required(name).textValue());
        }
        JsonNode result = permissions.tree(new InterfaceProtocol.Envelope("1.0.0", requestId, receipt.required("revision").textValue(), receipt));
        shared.validate("interfaces", result, "Envelope"); return result;
    }

    public JsonNode readEnvelope(String text, boolean creation) throws IOException {
        JsonNode node = json.readTree(text); shared.validate("interfaces", node, "Envelope");
        JsonNode normalized = envelope(node.required("data"), node.required("request_id").textValue(), creation);
        if (!normalized.equals(node)) invalid(); return node;
    }

    public JsonNode encrypted(String value) throws IOException { JsonNode node = json.readTree(value); validate("Encrypted", node); return node; }
    public JsonNode read(byte[] bytes) throws IOException { return json.readTree(bytes); }
    public String manifestSha256() { return manifest; }
    public void validate(String name, JsonNode node) { if (node == null || !definition(name).validate(node).isEmpty()) invalid(); }
    private JsonSchema definition(String name) { return schema.getSubSchema(new JsonNodePath(PathType.JSON_POINTER).append("$defs").append(name)); }
    private byte[] resource(String name) throws IOException {
        try (var input = Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream(name))) { return input.readAllBytes(); }
    }
    private static void invalid() { throw new SchemaRejection(ErrorCode.SCHEMA_INVALID); }
}
