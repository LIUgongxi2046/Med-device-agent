package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.networknt.schema.*;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.SchemaRejection;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Closed local integrity protocol; deliberately not a published authorization or release schema. */
@org.springframework.stereotype.Component
public final class EvaluationAcceptanceEvidenceSchema {
    private final JsonMapper reader = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, DeserializationFeature.FAIL_ON_TRAILING_TOKENS,
            DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES).build();
    private final JsonSchema schema;
    private final PermissionSchema json;
    public EvaluationAcceptanceEvidenceSchema(PermissionSchema json) throws IOException {
        this.json=Objects.requireNonNull(json);
        try(var stream=Objects.requireNonNull(getClass().getResourceAsStream("/evaluation-acceptance-evidence.schema.json"))) {
            schema=JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(reader.readTree(stream),
                SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());
        }
        schema.initializeValidators();
    }
    public EvaluationAcceptanceEvidenceVerifier.AcceptanceDecision decision(byte[] bytes) throws IOException {
        if(bytes==null||bytes.length==0||bytes.length>65536)invalid();
        try {
            var node=reader.readTree(bytes);validate("AcceptanceDecision",node);
            var value=reader.treeToValue(node,EvaluationAcceptanceEvidenceVerifier.AcceptanceDecision.class);
            ProjectSchema.text(value.reason(),2000);revision(value.human_decision().authority_revision());return value;
        } catch(com.fasterxml.jackson.core.JsonProcessingException e) { throw new SchemaRejection(ErrorCode.SCHEMA_INVALID); }
    }
    public EvaluationAcceptanceStatisticsProtocol.Policy policy(byte[] bytes) throws IOException {
        if(bytes==null||bytes.length==0||bytes.length>262144)invalid();
        try {
            var node=reader.readTree(bytes);validate("Policy",node);
            return reader.treeToValue(node,EvaluationAcceptanceStatisticsProtocol.Policy.class);
        } catch(com.fasterxml.jackson.core.JsonProcessingException e) {throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}
    }
    public void validate(String name,JsonNode node) {
        if(node==null||!schema.getSubSchema(new JsonNodePath(PathType.JSON_POINTER).append("$defs").append(name)).validate(node).isEmpty())invalid();
    }
    void validateExpected(EvaluationAcceptanceEvidenceVerifier.ExpectedBinding expected) {
        validate("ExpectedBinding",json.tree(expected));revision(expected.evaluation_revision());revision(expected.human_decision().authority_revision());
    }
    EvaluationAcceptanceEvidenceVerifier.AcceptanceDecision validateDecision(EvaluationAcceptanceEvidenceVerifier.AcceptanceDecision decision)throws IOException {
        return decision(json.canonicalObject(decision).getBytes(StandardCharsets.UTF_8));
    }
    private static void revision(String value){ if(PermissionSchema.revision(value).signum()<=0)invalid(); }
    private static void invalid(){throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}
}
