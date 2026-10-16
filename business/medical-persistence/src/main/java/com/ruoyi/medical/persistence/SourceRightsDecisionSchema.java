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
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.InterfaceProtocol;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import com.ruoyi.medical.contracts.SchemaRejection;
import com.ruoyi.medical.contracts.WireValues;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Component;

@Component
public final class SourceRightsDecisionSchema {
    private final JsonMapper reader=JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS,DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
    private final JsonSchema schema;
    private final PermissionSchema json;
    private final SchemaCatalogue shared;
    private final String manifest;

    public SourceRightsDecisionSchema(PermissionSchema json) throws IOException,NoSuchAlgorithmException {
        this.json=json; shared=SchemaCatalogue.fromClasspath();
        byte[] document;
        try(var input=Objects.requireNonNull(getClass().getResourceAsStream("/source-rights-decisions.schema.json"))) { document=input.readAllBytes(); }
        schema=JsonSchemaFactory.getInstance(VersionFlag.V202012).getSchema(reader.readTree(document),
            SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());
        schema.initializeValidators();
        for(String name:java.util.List.of("PreviewInput","DecisionInput","Proposal","Preview","Receipt","Proofs")) definition(name).initializeValidators();
        var identity=json.object(); identity.put("schema_version","1.0.0");
        identity.put("source-rights-decisions.schema.json",DigestUtils.sha256Hex(document));
        try(var input=Objects.requireNonNull(getClass().getResourceAsStream("/source-rights.schema.json"))) {
            identity.put("source-rights.schema.json",DigestUtils.sha256Hex(input.readAllBytes()));
        }
        manifest=DigestUtils.sha256Hex(json.canonical(identity));
    }
    SourceRightsProtocol.PreviewInput preview(byte[] bytes) throws IOException {
        if(bytes.length>131072) reject();
        var node=reader.readTree(bytes); validate("PreviewInput",node);
        var input=reader.treeToValue(node,SourceRightsProtocol.PreviewInput.class); proposal(input.proposal()); return input;
    }
    SourceRightsProtocol.DecisionInput decision(byte[] bytes) throws IOException {
        if(bytes.length>131072) reject();
        var node=reader.readTree(bytes); validate("DecisionInput",node);
        var input=reader.treeToValue(node,SourceRightsProtocol.DecisionInput.class);
        WireValues.revision(input.expected_revision()); WireValues.uuid(input.base_rights().version_id());
        WireValues.hash(input.base_rights().content_sha256()); WireValues.hash(input.reviewed_manifest_sha256());
        ProjectSchema.text(input.reason(),2000); proposal(input.proposal()); return input;
    }
    private static void proposal(SourceRightsProtocol.Proposal input) {
        if(input==null) return;
        if(!input.allowed_actions().contains("STORE")
                || input.allowed_actions().contains("EXTERNAL_PROCESS") && input.providers().isEmpty()) reject();
        if(input.valid_from()!=null) WireValues.utcTime(input.valid_from());
        if(input.valid_to()!=null) WireValues.utcTime(input.valid_to());
        var identifiers=new java.util.HashSet<String>();
        for(var evidence:input.evidence_manifest().authorization_evidence()) {
            WireValues.uuid(evidence.version_id()); WireValues.hash(evidence.content_sha256());
            if(!identifiers.add(evidence.version_id())) reject();
        }
    }
    JsonNode envelope(String name,JsonNode data,String requestId,String revision) {
        validate(name,data); var result=json.tree(new InterfaceProtocol.Envelope("1.0.0",requestId,revision,data));
        shared.validate("interfaces",result,"Envelope"); return result;
    }
    JsonNode receipt(String value) throws IOException {
        var node=reader.readTree(value); shared.validate("interfaces",node,"Envelope"); validate("Receipt",node.required("data")); return node;
    }
    String requestHash(String tenant,String source,SourceRightsProtocol.DecisionInput input,String context,String session) throws IOException {
        var value=requestManifest(tenant,source,input,context,session); return DigestUtils.sha256Hex(json.canonical(value));
    }
    JsonNode requestManifest(String tenant,String source,SourceRightsProtocol.DecisionInput input,String context,String session) {
        var value=json.object(); value.put("schema_version","1.0.0"); value.put("tenant_id",tenant); value.put("method","POST");
        value.put("path","/api/source-versions/"+source+"/rights-decisions"); value.put("context_header",context);
        value.put("session_id",session); value.set("body",json.tree(input)); return value;
    }
    String manifestSha256() { return manifest; }
    void proofs(JsonNode value) {
        validate("Proofs",value);
        for(var item:value) {
            var reference=item.required("object_ref"); var evidence=item.required("evidence");
            WireValues.revision(reference.required("size_bytes").textValue()); WireValues.utcTime(item.required("verified_at").textValue());
            if(!item.required("full_sha256").equals(reference.required("sha256"))
                    || !evidence.required("artifact").required("content_sha256").equals(reference.required("sha256"))
                    || !evidence.required("size_bytes").equals(reference.required("size_bytes"))
                    || !evidence.required("media_type").equals(reference.required("media_type"))) reject();
        }
    }
    private void validate(String name,JsonNode value) { if(value==null || !definition(name).validate(value).isEmpty()) reject(); }
    private JsonSchema definition(String name) { return schema.getSubSchema(new JsonNodePath(PathType.JSON_POINTER).append("$defs").append(name)); }
    private static void reject() { throw new SchemaRejection(ErrorCode.SCHEMA_INVALID); }
}
