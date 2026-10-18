package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import com.ruoyi.medical.contracts.SchemaRejection;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import org.apache.commons.codec.digest.DigestUtils;

/** Validates actual canonical payloads; this class deliberately grants no authority. */
public final class SimulationInvocationSchema {
    static final long MAX = 9_007_199_254_740_991L;
    private final SchemaCatalogue catalogue;
    private final JsonNode counter;
    private final JsonMapper json = JsonMapper.builder()
        .enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    public SimulationInvocationSchema() throws IOException, NoSuchAlgorithmException {
        catalogue=SchemaCatalogue.fromClasspath();
        try(var input=java.util.Objects.requireNonNull(getClass().getResourceAsStream("/simulation-counter-identity.json"))) { counter=json.readTree(input); }
        validate("CounterIdentity",counter);
    }
    JsonNode request(byte[] bytes) throws IOException {
        if(bytes==null || bytes.length==0 || bytes.length>16_777_216) invalid();
        JsonNode value=json.readTree(bytes); validate("SimulationInvocationAdmission",value); check(value); return value.deepCopy();
    }
    void validate(String definition,JsonNode node) { catalogue.validate("simulation-execution",node,definition); }
    JsonNode parse(String value) throws IOException { return json.readTree(value); }
    JsonNode parse(byte[] value) throws IOException {
        if(value==null||value.length==0||value.length>16_777_216) invalid();
        return json.readTree(value);
    }
    String canonical(JsonNode node) throws IOException {
        numbers(node);
        return new org.erdtman.jcs.JsonCanonicalizer(json.writeValueAsBytes(node)).getEncodedString();
    }
    String hash(JsonNode node) throws IOException { return DigestUtils.sha256Hex(canonical(node).getBytes(StandardCharsets.UTF_8)); }
    String manifest() { return catalogue.schemaDigest("simulation-execution"); }
    JsonNode receipt(JsonNode request,byte[] resultBytes,byte[] provenanceBytes) throws IOException {
        var result=parse(resultBytes); var provenance=parse(provenanceBytes);
        catalogue.validate("model-stream",result,"ModelResult");
        catalogue.validate("local-simulation-provenance",provenance,null);
        if(!java.util.Arrays.equals(resultBytes,canonical(result).getBytes(StandardCharsets.UTF_8))
            ||!java.util.Arrays.equals(provenanceBytes,canonical(provenance).getBytes(StandardCharsets.UTF_8))) mismatch();
        var binding=request.required("binding"); var id=binding.required("identity"); var fixture=request.required("simulation_case");
        for(String field:List.of("tenant_id","project_id","run_id","step_id","action_id","invocation_id","context_manifest_id","model_config_revision")) same(id.get(field),provenance.get(field));
        for(String field:List.of("invocation_id","context_manifest_id","model_config_revision")) same(id.get(field),result.get(field));
        for(String field:List.of("case_id","case_sha256","settings_sha256","parameters_sha256")) same(binding.get(field),provenance.get(field));
        same(binding.get("case_receipt_id"),provenance.get("receipt_id"));
        same(binding.get("local_request_sha256"),provenance.get("request_sha256"));
        same(binding.required("exact_input").get("content_sha256"),provenance.get("input_sha256"));
        for(String field:List.of("blocks","usage","reason")) same(fixture.get(field),result.get(field));
        if(!result.path("replay_state_id").isNull()||!("SIMULATION:"+fixture.path("case_id").textValue()).equals(result.path("provider_request_id").textValue())
            ||!hash(result).equals(provenance.path("model_result_sha256").textValue())
            ||!catalogue.schemaRef("model-stream","ModelResult").equals(provenance.path("model_result_schema_ref").textValue())
            ||!catalogue.schemaDigest("model-stream").equals(provenance.path("model_result_schema_sha256").textValue())
            ||!((result.path("usage").isNull()?"UNKNOWN":"SIMULATION_FIXTURE").equals(provenance.path("usage_origin").textValue()))
            ||"aborted".equals(result.path("reason").path("kind").textValue())
            ||resultBytes.length>binding.required("caps").path("maximum_output_units").longValue()) mismatch();
        var payload=json.createObjectNode(); payload.set("result",result); payload.set("provenance",provenance);
        payload.put("result_sha256",hash(result)); payload.put("provenance_sha256",hash(provenance));
        payload.put("receipt_id",binding.path("case_receipt_id").textValue());
        payload.put("status","RECORDED_UNSETTLED"); return payload;
    }
    void check(JsonNode value) throws IOException {
        var proof=value.required("authority_proof"); var binding=value.required("binding"); var id=binding.required("identity");
        for(String field:List.of("tenant_id","project_id","step_id","action_id","context_manifest_id","model_config_revision")) same(proof.get(field),id.get(field));
        same(proof.get("parent_run_id"),id.get("run_id"));
        for(String field:List.of("configuration_sha256","case_id","case_sha256","settings_sha256","parameters_sha256")) same(proof.get(field),binding.get(field));
        var input=value.required("exact_input"); var params=value.required("request_parameters"); var fixture=value.required("simulation_case"); var settings=value.required("settings");
        String inputHash=hash(input),parametersHash=hash(params),caseHash=hash(fixture),settingsHash=hash(settings);
        for(var pair:List.of(new String[]{"input_sha256",inputHash},new String[]{"parameters_sha256",parametersHash},new String[]{"case_sha256",caseHash},new String[]{"settings_sha256",settingsHash}))
            if(!pair[1].equals(proof.path(pair[0]).textValue())) mismatch();
        for(String field:List.of("input_sha256","parameters_sha256","settings_sha256","case_id")) same(proof.get(field),fixture.get(field));
        same(binding.get("case_receipt_id"),fixture.get("receipt_id"));
        var measure=binding.required("exact_input");
        same(proof.get("counter"),measure.get("counter"));
        same(counter,proof.get("counter"));
        if(!inputHash.equals(measure.path("content_sha256").textValue()) || measure.path("units").longValue()!=canonical(input).getBytes(StandardCharsets.UTF_8).length) mismatch();
        for(String cap:List.of("maximum_input_units","maximum_output_units")) same(proof.get(cap),binding.required("caps").get(cap));
        if(measure.path("units").longValue()>proof.path("maximum_input_units").longValue()) mismatch();
        sum(measure.path("units").longValue(),proof.path("maximum_output_units").longValue());
        if(proof.path("enterprise_account_id").equals(proof.path("parent_account_id"))) mismatch();
        var request=json.createObjectNode(); request.set("identity",id); request.set("case",fixture); request.set("settings",settings);
        request.set("parameters",params); request.set("exact_input",input);
        request.put("provenance_schema_sha256",catalogue.schemaDigest("local-simulation-provenance"));
        request.put("model_result_schema_sha256",catalogue.schemaDigest("model-stream"));
        if(!hash(request).equals(binding.path("local_request_sha256").textValue())) mismatch();
        var issued=PermissionAuthority.time(proof.path("issued_at").textValue());
        var expires=PermissionAuthority.time(proof.path("expires_at").textValue());
        if(!expires.isAfter(issued) || PermissionAuthority.time(proof.path("lease_expires_at").textValue()).isBefore(expires)
            || !PermissionAuthority.time(proof.path("retention_deadline").textValue()).isAfter(expires)) mismatch();
    }
    static void numbers(JsonNode node) throws IOException {
        if(node.isIntegralNumber() && node.bigIntegerValue().abs().compareTo(java.math.BigInteger.valueOf(MAX))>0) invalid();
        if(node.isNumber() && !Double.isFinite(node.doubleValue())) invalid();
        if(node.isTextual()) unicode(node.textValue());
        if(node.isObject()) { var fields=node.fieldNames(); while(fields.hasNext()) unicode(fields.next()); }
        if(node.isContainerNode()) for(var child:node) numbers(child);
    }
    private static void unicode(String value) throws IOException {
        StandardCharsets.UTF_8.newEncoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
            .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT).encode(java.nio.CharBuffer.wrap(value));
    }
    static long sum(long a,long b) { if(a<0||b<0||a>MAX||b>MAX-a) invalid(); return a+b; }
    static void same(JsonNode a,JsonNode b) { if(a==null||b==null||!a.equals(b)) mismatch(); }
    static void mismatch() { throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH); }
    static void invalid() { throw new SchemaRejection(ErrorCode.SCHEMA_INVALID); }
}
