package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.networknt.schema.*;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.SchemaRejection;
import java.io.IOException;
import java.util.Objects;

/** Additive local API contract. Frozen published simulation and evaluation schemas are unchanged. */
final class SimulationEvaluationSchema {
    private final JsonMapper json=JsonMapper.builder().enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .disable(com.fasterxml.jackson.core.StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION)
        .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private final JsonSchema schema;
    private final String manifest;
    SimulationEvaluationSchema()throws IOException {
        byte[] bytes;try(var in=Objects.requireNonNull(getClass().getResourceAsStream("/simulation-evaluation-start.schema.json"))){bytes=in.readAllBytes();}
        manifest=org.apache.commons.codec.digest.DigestUtils.sha256Hex(bytes);
        schema=JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(json.readTree(bytes),SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());
        var names=json.readTree(bytes).required("$defs").fieldNames();while(names.hasNext())definition(names.next()).initializeValidators();
    }
    JsonNode read(String definition,byte[] bytes)throws IOException {
        if(bytes==null||bytes.length==0||bytes.length>65536)invalid();
        var node=json.readTree(bytes);if(node==null||!definition(definition).validate(node).isEmpty())invalid();
        SimulationInvocationSchema.numbers(node);
        if(node.has("expected_revision"))PermissionSchema.revision(node.path("expected_revision").asText());
        if(node.has("lease_generation"))PermissionSchema.revision(node.path("lease_generation").asText());
        return node;
    }
    String manifest(){return manifest;}
    private JsonSchema definition(String name){return schema.getSubSchema(new JsonNodePath(PathType.JSON_POINTER).append("$defs").append(name));}
    private static void invalid(){throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}
}
