package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import com.ruoyi.medical.contracts.WireValues;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;

/** Content invariants mirror SimulationProvider; this class does not confer source authority. */
final class SimulationFixtureContent {
    private SimulationFixtureContent() { }
    static ObjectNode settings(JsonNode configuration) {
        var definition=configuration.required("definition"); var limits=definition.required("limits");
        var value=JsonMapper.builder().build().createObjectNode();
        value.set("model_id",definition.required("model_id")); value.set("model_version",definition.required("model_version"));
        value.put("max_output_tokens",new java.math.BigInteger(limits.required("max_output_tokens").textValue()));
        for(String field:java.util.List.of("deadline_seconds","temperature_enabled","reasoning_efforts","stop_limit")) value.set(field,limits.required(field));
        return value;
    }
    static void verify(SchemaCatalogue schemas,JsonNode configuration,JsonNode fixture,JsonNode input,JsonNode parameters) throws Exception {
        schemas.validate("simulation-execution",fixture,"SimulationCase"); schemas.validate("simulation-execution",input,"ExactInput");
        schemas.validate("simulation-execution",parameters,"RequestParameters");
        var definition=configuration.required("definition");
        same("SIMULATION",definition.required("execution_mode").textValue());
        same("medical_model_gateway.simulation.SimulationProvider",definition.required("simulator_id").textValue());
        same("1.0.0",definition.required("simulator_version").textValue());
        same(false,definition.required("production_eligible").booleanValue());
        same("0.00000000",definition.required("provider_charge_amount").textValue());
        same("LOCAL_SIMULATION_NO_PROVIDER_REQUEST",definition.required("charge_basis").textValue());
        same("urn:medical:local-simulation-provenance:1.0.0",definition.required("provenance_schema_ref").textValue());
        schemas.requirePublished(definition.required("provenance_schema_ref").textValue(),definition.required("provenance_schema_sha256").textValue());
        same(definition.required("case_binding").required("case_id"),fixture.required("case_id"));
        same(definition.required("case_binding").required("case_sha256").textValue(),hash(fixture));
        same(fixture.required("input_sha256").textValue(),hash(input));
        same(fixture.required("parameters_sha256").textValue(),hash(parameters));
        var settings=settings(configuration); schemas.validate("simulation-execution",settings,"ProviderSettings");
        same(fixture.required("settings_sha256").textValue(),hash(settings));
        if(parameters.required("max_output_tokens").bigIntegerValue().compareTo(settings.required("max_output_tokens").bigIntegerValue())>0) mismatch();
        JsonNode effort=parameters.required("reasoning_effort"),temperature=parameters.required("temperature");
        if(!effort.isNull() && !contains(settings.required("reasoning_efforts"),effort)) mismatch();
        if(!temperature.isNull() && (!settings.required("temperature_enabled").booleanValue() || !"none".equals(effort.asText())
                || temperature.doubleValue()<0 || temperature.doubleValue()>2)) mismatch();
        if(parameters.required("stop").size()>settings.required("stop_limit").intValue()) mismatch();
        if(!input.required("tools").isEmpty() && !"none".equals(effort.asText())) mismatch();
        var definitions=new HashMap<String,JsonNode>();
        for(var tool:input.required("tools")) {
            var function=tool.required("function");
            if(definitions.putIfAbsent(function.required("name").textValue(),function.required("parameters"))!=null) mismatch();
            // Closed catalogue resolver rejects arbitrary remote schema resolution.
            schemas.compileApiSchema(function.required("parameters")).initializeValidators();
        }
        var pending=new HashSet<String>(); var known=new HashSet<String>();
        for(var message:input.required("messages")) {
            String role=message.required("role").textValue();
            if(!pending.isEmpty() && !"tool".equals(role)) mismatch();
            if("assistant".equals(role)) {
                if(message.required("content").isNull() && message.required("tool_calls").isEmpty()) mismatch();
                for(var call:message.required("tool_calls")) {
                    String id=call.required("id").textValue(); if(!known.add(id)) mismatch(); pending.add(id);
                }
            } else if("tool".equals(role) && !pending.remove(message.required("tool_call_id").textValue())) mismatch();
        }
        if(!pending.isEmpty()) mismatch();
        var tools=new HashSet<String>(); String reason=fixture.required("reason").required("kind").textValue();
        for(var block:fixture.required("blocks")) {
            if(!"tool-call".equals(block.required("type").textValue())) continue;
            if(!tools.add(block.required("id").textValue())) mismatch();
            var tool=definitions.get(block.required("name").textValue()); if(tool==null) mismatch();
            JsonNode arguments=JsonMapper.builder().enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build().readTree(block.required("arguments").textValue());
            if(arguments==null || !arguments.isObject() || !schemas.compileApiSchema(tool).validate(arguments).isEmpty()) mismatch();
        }
        if("aborted".equals(reason) || ("tool-calls".equals(reason)!=!tools.isEmpty())) mismatch();
        if("error".equals(reason)==fixture.required("reason").required("failure").isNull()) mismatch();
        if(("stop".equals(reason)||"max-tokens".equals(reason)) && fixture.required("blocks").isEmpty()) mismatch();
        if(!fixture.required("usage").isNull()) same(fixture.required("receipt_id"),fixture.required("usage").required("source_receipt_id"));
    }
    private static boolean contains(JsonNode items,JsonNode wanted) { for(var item:items) if(item.equals(wanted)) return true; return false; }
    private static String hash(JsonNode value) throws Exception { SimulationInvocationSchema.numbers(value); return WireValues.sha256(ContentReadAuthorization.canonicalBytes(value)); }
    private static void same(Object a,Object b) { if(!Objects.equals(a,b)) mismatch(); }
    private static void mismatch() { throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH); }
}
