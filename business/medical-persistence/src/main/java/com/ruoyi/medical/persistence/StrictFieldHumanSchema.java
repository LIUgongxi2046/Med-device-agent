package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import java.util.Objects;

/** Closed additive DC-05 contract; callers cannot supply machine outcomes or approvals. */
public final class StrictFieldHumanSchema {
    private final TaskSourceVerifierSchema json=new TaskSourceVerifierSchema();
    private final com.networknt.schema.JsonSchema root;
    private final String manifest;private final JsonNode definition;
    public StrictFieldHumanSchema() throws Exception {
        try(var in=Objects.requireNonNull(getClass().getResourceAsStream("/strict-field-human.schema.json"))){
            byte[] bytes=in.readAllBytes();manifest=WireValues.sha256(bytes);definition=json.read(bytes);
            root=com.networknt.schema.JsonSchemaFactory.getInstance(com.networknt.schema.SpecVersion.VersionFlag.V202012)
                .getSchema(json.read(bytes),com.networknt.schema.SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());
        }
    }
    public JsonNode request(byte[] bytes)throws Exception {if(bytes.length>32768)throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);var n=json.read(bytes);validate(n);return n;}
    public void validate(JsonNode n){if(!root.validate(n).isEmpty())throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}
    public void result(String kind,JsonNode value){var selected=definition.deepCopy();((com.fasterxml.jackson.databind.node.ObjectNode)selected).put("$ref","#/$defs/"+kind);var validator=com.networknt.schema.JsonSchemaFactory.getInstance(com.networknt.schema.SpecVersion.VersionFlag.V202012).getSchema(selected,com.networknt.schema.SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());if(!validator.validate(value).isEmpty())throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}
    public String manifest(){return manifest;}
}
