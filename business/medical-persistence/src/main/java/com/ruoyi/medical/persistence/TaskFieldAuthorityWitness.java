package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.math.BigInteger;
import java.util.Set;

/** Lossless integer identity encoding for new 096 internal authority witnesses only.
 * Does not change historical witness contracts, global JSON canonicalization or business payload schemas. */
final class TaskFieldAuthorityWitness {
    private static final JsonMapper READER=JsonMapper.builder().addModule(new JavaTimeModule())
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build();
    private static final Set<String> INTEGER_IDENTITIES=Set.of("revision","gate_revision","expected_count","verified_count");
    private TaskFieldAuthorityWitness() { }
    static JsonNode tree(Object value) {
        var result=AgentPublicationWitness.tree(value).deepCopy(); normalize(result); return result;
    }
    static PermissionProtocol.GrantRow grant(JsonNode original) throws IOException {
        var normalized=tree(original);var revision=normalized.path("revision");
        if(!normalized.isObject()||!revision.isTextual()||new BigInteger(revision.textValue()).signum()<=0)
            throw new IllegalArgumentException("Missing or invalid exact grant revision");
        return READER.treeToValue(normalized,PermissionProtocol.GrantRow.class);
    }
    private static void normalize(JsonNode value) {
        if(value.isObject()) {
            var fields=value.fields();
            while(fields.hasNext()) {
                var field=fields.next(); var child=field.getValue();
                if(INTEGER_IDENTITIES.contains(field.getKey())&&!child.isNull()) {
                    String exact=child.isIntegralNumber()?child.bigIntegerValue().toString():child.isTextual()?child.textValue():null;
                    if(exact==null||!exact.matches("0|[1-9][0-9]*")||new BigInteger(exact).bitLength()>64)
                        throw new IllegalArgumentException("Invalid exact authority integer: "+field.getKey());
                    ((ObjectNode)value).put(field.getKey(),exact);
                } else normalize(child);
            }
        } else if(value.isArray()) for(var child:value) normalize(child);
    }
}
