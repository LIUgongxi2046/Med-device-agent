package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.networknt.schema.JsonNodePath;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.PathType;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion.VersionFlag;
import com.ruoyi.medical.contracts.WireValues;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Objects;
import org.erdtman.jcs.JsonCanonicalizer;
import org.springframework.stereotype.Component;

@Component
public final class RunTokenAuthoritySchema {
    private final JsonMapper json=JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .disable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private final JsonSchema schema;
    private final String manifest;

    public RunTokenAuthoritySchema() throws IOException {
        byte[] bytes;
        try(var input=Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream("run-token-authority.schema.json"))) {
            bytes=input.readAllBytes();
        }
        manifest=org.apache.commons.codec.digest.DigestUtils.sha256Hex(bytes);
        schema=JsonSchemaFactory.getInstance(VersionFlag.V202012).getSchema(json.readTree(bytes),
            SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());
        var names=json.readTree(bytes).required("$defs").fieldNames();
        while(names.hasNext()) definition(names.next()).initializeValidators();
    }

    JsonNode read(String name,String raw) throws IOException {
        JsonNode node=json.readTree(raw);
        if(node==null || !definition(name).validate(node).isEmpty()) invalid();
        return node;
    }

    JsonNode scopeManifest(String raw,String tenant,String project,String actor,String run,String kind) throws IOException {
        var node=read("ScopeManifest",raw); identity(node,tenant,project,actor,run);
        if(!kind.equals(node.required("execution_kind").textValue()) || "TASK".equals(kind) && project==null) invalid();
        for(var value:node.required("resources")) scope(value,tenant,project);
        return node;
    }

    JsonNode principalManifest(String raw,String tenant,String project,String actor,String run,String principal,JsonNode scope) throws IOException {
        WireValues.uuid(principal);
        var node=read("PrincipalManifest",raw); identity(node,tenant,project,actor,run);
        if(!principal.equals(node.required("principal_id").textValue()) || !sha256(scope).equals(node.required("scope_sha256").textValue())) invalid();
        for(var permission:node.required("permissions")) {
            var resource=permission.required("scope"); scope(resource,tenant,project);
            if(!contains(scope.required("resources"),resource)) invalid();
        }
        for(var service:node.required("services")) {
            var uri=URI.create(service.required("service_uri").textValue());
            if(!uri.isAbsolute() || uri.getFragment()!=null) invalid();
        }
        return node;
    }

    List<Permission> permissions(JsonNode principal) throws IOException {
        var result=new java.util.ArrayList<Permission>();
        for(var entry:principal.required("permissions")) result.add(json.treeToValue(entry,Permission.class));
        return List.copyOf(result);
    }

    void role(String raw,JsonNode principal) throws IOException {
        var role=read("RoleScope",raw);
        for(var entry:principal.required("permissions")) if(!contains(role.required("permissions"),entry)) invalid();
    }

    void service(JsonNode principal,String uri,String audience) {
        boolean allowed=false;
        for(var entry:principal.required("services")) if(uri.equals(entry.required("service_uri").textValue())
                && audience.equals(entry.required("audience").textValue())) allowed=true;
        if(!allowed) invalid();
    }

    String manifestSha256() { return manifest; }
    com.fasterxml.jackson.databind.node.ObjectNode object() { return json.createObjectNode(); }
    JsonNode tree(Object value) { return json.valueToTree(value); }
    String canonical(JsonNode value) throws IOException { return new JsonCanonicalizer(json.writeValueAsBytes(value)).getEncodedString(); }
    String sha256(JsonNode value) throws IOException { return org.apache.commons.codec.digest.DigestUtils.sha256Hex(canonical(value).getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
    private JsonSchema definition(String name) { return schema.getSubSchema(new JsonNodePath(PathType.JSON_POINTER).append("$defs").append(name)); }
    private static boolean contains(JsonNode entries,JsonNode target) {
        for(var entry:entries) if(entry.equals(target)) return true;
        return false;
    }
    private static void identity(JsonNode node,String tenant,String project,String actor,String run) {
        for(var value:List.of(tenant,actor,run)) WireValues.uuid(value);
        if(project!=null) WireValues.uuid(project);
        if(!tenant.equals(node.required("tenant_id").textValue()) || !Objects.equals(project,node.required("project_id").textValue())
                || !actor.equals(node.required("initiator_id").textValue()) || !run.equals(node.required("run_id").textValue())) invalid();
    }
    private static void scope(JsonNode node,String tenant,String project) {
        String owner=node.required("project_id").textValue(),kind=node.required("resource_kind").textValue(),id=node.required("resource_id").textValue();
        WireValues.uuid(id);
        if(owner!=null) WireValues.uuid(owner);
        if(!tenant.equals(node.required("tenant_id").textValue()) || owner!=null && !owner.equals(project)
                || "ENTERPRISE".equals(kind) && (owner!=null || !tenant.equals(id))
                || "PROJECT".equals(kind) && (owner==null || !owner.equals(id))) invalid();
    }
    private static void invalid() { throw new SecurityException("RUN_AUTHORITY_MANIFEST_INVALID"); }
    record Permission(PermissionProtocol.Scope scope,String action,String purpose) { }
}
