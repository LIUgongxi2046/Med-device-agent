package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Closed typed N03 input binding, independent of N02 and IA-04 DOCUMENT/CANDIDATE. */
public final class TaskStrictFieldSchema {
    public static final String INPUT_REF="urn:medical:task-strict-field-input:1.0.0";
    public static String inputRef(String version){if(!Set.of("1.0.0","2.0.0").contains(version))bad();return "urn:medical:task-strict-field-input:"+version;}
    public static final String RESULT_REF="urn:medical:task-strict-field-result:1.0.0";
    public static final int MAXIMUM_BYTES=8_388_608;
    private final JsonMapper json=JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .disable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
        .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES).enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build();
    private final TaskFieldTargetSchema targets;
    private final StrictFieldComparisonSchema comparisons;
    public TaskStrictFieldSchema(TaskFieldTargetSchema targets,StrictFieldComparisonSchema comparisons){this.targets=Objects.requireNonNull(targets);this.comparisons=Objects.requireNonNull(comparisons);}
    public String manifest() throws Exception {return manifest("1.0.0");}
    public String manifest(String version) throws Exception { inputRef(version);try(var in=Objects.requireNonNull(getClass().getResourceAsStream("2.0.0".equals(version)?"/task-strict-field-v2.schema.json":"/task-strict-field.schema.json"))){return WireValues.sha256(in.readAllBytes());} }
    public TaskStrictFieldInput input(byte[] bytes)throws Exception {
        if(bytes==null||bytes.length>MAXIMUM_BYTES)bad();
        try {return validate(json.readValue(bytes,TaskStrictFieldInput.class));}
        catch(java.io.IOException|IllegalArgumentException e){throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}
    }
    public TaskStrictFieldInput validate(TaskStrictFieldInput input)throws Exception {
        if("2.0.0".equals(input.schemaVersion())){var catalogue=SchemaCatalogue.fromClasspath();catalogue.validate("task-strict-field-input-v2",tree(input),null);catalogue.validate("strict-field-typed-v2",input.targetManifest(),"TargetManifest");}
        if(TaskStrictFieldTypedProtocol.typed(input.targetManifest().path("schema_ref").asText())){
            ProfessionalRuntimeObservations.closed(input.targetManifest(),Set.of("schema_version","schema_ref","target_revision","request_sha256","target","bindings","request","task_input_sha256","plan_sha256","profile_revision","scope_snapshot","method_coverage","missing_requirement_ids"));
            var request=json.treeToValue(input.targetManifest().required("request"),TaskStrictFieldTypedProtocol.Author.class);
            if(!input.schemaVersion().equals(request.schemaVersion())||!input.schemaVersion().equals(input.targetManifest().path("schema_version").asText())||!TaskStrictFieldTypedProtocol.targetRef(input.schemaVersion()).equals(input.targetManifest().path("schema_ref").asText()))bad();
            if(!hash(request).equals(input.targetManifest().required("request_sha256").asText())||!tree(request.entries()).equals(input.targetManifest().required("bindings")))bad();
        }else {if(!"1.0.0".equals(input.schemaVersion()))bad();targets.validate("TargetManifest",input.targetManifest());}
        var target=comparisons.target(input.targetManifest().required("target"));var identity=target.identity();if(!input.schemaVersion().equals(target.schemaVersion()))bad();
        if(!identity.tenantId().equals(input.tenantId())||!identity.projectId().equals(input.projectId())||!identity.taskId().equals(input.taskId())
                ||!identity.planRevision().equals(input.planRevision())||!input.planSha256().equals(input.targetManifest().required("plan_sha256").textValue())
                ||!input.snapshotId().equals(input.targetManifest().required("scope_snapshot").textValue())
                ||!targets.hash(input.targetManifest()).equals(input.targetObject().sha256()))bad();
        Set<String> actual=new HashSet<>();for(var entry:target.entries())actual.add(entry.entryId());
        Set<String> selected=new HashSet<>();for(var p:input.policies())selected.add(p.entryId());if(!actual.equals(selected))bad();
        Set<String> sources=new HashSet<>();for(var pin:input.fixedSources())sources.add(pin.sourceVersion());
        for(var entry:target.entries())if(entry.provenance()==null||entry.provenance().source()==null||entry.provenance().source().evidenceRef()==null
                ||!sources.contains(entry.provenance().source().evidenceRef().sourceVersion()))bad();
        if(canonical(input).getBytes(StandardCharsets.UTF_8).length>MAXIMUM_BYTES)bad();return input;
    }
    <T>T bind(JsonNode value,Class<T> type)throws Exception{return json.treeToValue(value,type);}
    RunTokenAuthority.ActionRequest action(JsonNode value)throws Exception{return json.treeToValue(value,RunTokenAuthority.ActionRequest.class);}
    public JsonNode tree(Object value){return json.valueToTree(value);}
    public String canonical(Object value)throws Exception{return new String(ContentReadAuthorization.canonicalBytes(tree(value)),StandardCharsets.UTF_8);}
    public String hash(Object value)throws Exception{return WireValues.sha256(ContentReadAuthorization.canonicalBytes(tree(value)));}
    public JsonNode read(String raw)throws Exception {if(raw==null||raw.length()>MAXIMUM_BYTES)bad();return json.readTree(raw);}
    public StrictFieldComparisonProtocol.Target target(TaskStrictFieldInput input)throws Exception{return comparisons.target(input.targetManifest().required("target"));}
    private static void bad(){throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}
}
