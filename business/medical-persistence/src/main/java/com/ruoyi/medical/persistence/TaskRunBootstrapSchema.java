package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.networknt.schema.*;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.SchemaRejection;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.apache.commons.codec.digest.DigestUtils;

/** Closed integrity contract only. JSON/rows passing this validator are not an execution,
 * publication, physical-transfer or current-permission proof. Bootstrap services must
 * separately require their private current authority and actual transport acknowledgement. */
public final class TaskRunBootstrapSchema {
    private static final Set<String> TYPES=Set.of("ReplayIntent","ReplayRegistrationReceipt",
        "WorkflowReference","WorkflowLaunchIntent","WorkflowLaunchReceipt");
    private static final List<String> IDENTITY=List.of("tenant_id","project_id","run_id","principal_id",
        "task_id","initiator_id","command_id");
    private final JsonMapper parser=JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .disable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private final JsonSchema schema;
    private final String manifest;

    public TaskRunBootstrapSchema() throws IOException {
        byte[] bytes;
        try(var input=Objects.requireNonNull(getClass().getResourceAsStream("/task-run-bootstrap.schema.json"))) {
            bytes=input.readAllBytes();
        }
        schema=JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(parser.readTree(bytes),
            SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());
        for(String type:TYPES)definition(type).initializeValidators();
        manifest=DigestUtils.sha256Hex(bytes);
    }
    public String manifest(){return manifest;}
    public JsonNode read(String value)throws IOException{return parser.readTree(value);}
    public String canonical(Object value)throws IOException{return new String(ContentReadAuthorization.canonicalBytes(value),StandardCharsets.UTF_8);}
    public String hash(Object value)throws IOException{return DigestUtils.sha256Hex(ContentReadAuthorization.canonicalBytes(value));}

    public JsonNode validate(String type,JsonNode node)throws IOException {
        if(!TYPES.contains(type)||node==null||!definition(type).validate(node).isEmpty())invalid();
        if(type.equals("ReplayIntent")) {
            var payload=read(text(node,"canonical_payload"));
            if(!canonical(payload).equals(text(node,"canonical_payload")))invalid();
            byte[] bytes=text(node,"canonical_payload").getBytes(StandardCharsets.UTF_8);
            if(!Integer.toString(bytes.length).equals(text(node,"size_bytes"))
                ||!DigestUtils.sha256Hex(bytes).equals(text(node,"replay_sha256")))invalid();
            String key=DigestUtils.sha256Hex((text(node,"run_id")+"/RUN/REPLAY_MANIFEST").getBytes(StandardCharsets.US_ASCII));
            if(!key.equals(text(node,"write_key")))invalid();
            String objectKey="runtime/"+text(node,"tenant_id")+"/"+text(node,"run_id")+"/"+text(node,"write_id")+"/"+text(node,"planned_artifact_id");
            if(!objectKey.equals(text(node,"object_key")))invalid();
        }
        if(type.equals("ReplayRegistrationReceipt"))positive(node.required("object_ref").required("size_bytes").textValue());
        if(type.equals("WorkflowReference")) {
            if(node.required("maximum_parallel_steps").intValue()>node.required("maximum_steps").intValue())invalid();
        }
        if(type.equals("WorkflowLaunchIntent")) {
            var reference=validate("WorkflowReference",node.required("reference"));
            for(String id:List.of("tenant_id","project_id","run_id"))same(node,id,reference,id);
            if(!hash(reference).equals(text(node,"reference_sha256"))
                ||reference.required("deadline_epoch_millis").longValue()<=Instant.parse(text(node,"created_at")).toEpochMilli())invalid();
            workflow(node);
        }
        if(type.equals("WorkflowLaunchReceipt"))workflow(node);
        return node;
    }

    public void requireReplayIntent(JsonNode intent,RuntimeReplaySchema.VerifiedInputs replay)throws IOException {
        validate("ReplayIntent",intent);Objects.requireNonNull(replay);
        var payload=replay.content();
        for(String field:IDENTITY)if(!field.equals("command_id"))same(intent,field,payload,field);
        same(intent,"release_id",payload,"agent_release_id");
        same(intent,"publication_approval_sha256",payload,"publication_approval_sha256");
        if(!intent.required("execution_policy").equals(payload.required("execution_policy_version"))
            ||!canonical(payload).equals(text(intent,"canonical_payload"))
            ||!replay.rawSha256().equals(text(intent,"replay_sha256"))
            ||!replay.schemaSha256().equals(text(intent,"replay_schema_sha256")))invalid();
    }
    public void requireRegistration(JsonNode intent,JsonNode receipt)throws IOException {
        validate("ReplayIntent",intent);validate("ReplayRegistrationReceipt",receipt);
        identity(intent,receipt);same(intent,"write_id",receipt,"write_id");
        for(String field:List.of("authority_sha256","classification"))same(intent,field,receipt,field);
        for(String field:List.of("tenant_id","project_id"))same(receipt,field,receipt.required("content_manifest"),field);
        same(intent,"command_sha256",receipt,"command_sha256");same(intent,"execution_policy",receipt,"execution_policy");
        same(intent,"planned_artifact_id",receipt,"artifact_id");
        for(String field:List.of("replay_schema_ref","replay_schema_sha256"))same(intent,field,receipt,field);
        var object=receipt.required("object_ref");
        for(String field:List.of("object_key","size_bytes","media_type"))same(intent,field,object,field);
        same(intent,"replay_sha256",object,"sha256");
        if(!hash(intent).equals(text(receipt,"intent_sha256"))
            ||Instant.parse(text(receipt,"registered_at")).isBefore(Instant.parse(text(intent,"created_at"))))invalid();
    }
    public void requireLaunch(JsonNode registration,JsonNode launch)throws IOException {
        validate("ReplayRegistrationReceipt",registration);validate("WorkflowLaunchIntent",launch);
        identity(registration,launch);same(registration,"command_sha256",launch,"command_sha256");
        var reference=launch.required("reference");
        same(registration,"artifact_id",reference,"input_manifest_id");
        same(registration.required("execution_policy"),"version_id",reference,"policy_revision");
        same(registration.required("object_ref"),"sha256",reference,"input_sha256");
        if(!hash(registration).equals(text(launch,"replay_registration_sha256"))
            ||Instant.parse(text(launch,"created_at")).isBefore(Instant.parse(text(registration,"registered_at"))))invalid();
    }
    public void requireAcceptance(JsonNode launch,JsonNode receipt)throws IOException {
        validate("WorkflowLaunchIntent",launch);validate("WorkflowLaunchReceipt",receipt);
        identity(launch,receipt);
        for(String field:List.of("launch_id","workflow_id","reference_sha256"))same(launch,field,receipt,field);
        same(launch.required("reference"),"input_sha256",receipt,"input_sha256");
        if(!hash(launch).equals(text(receipt,"launch_intent_sha256"))
            ||Instant.parse(text(receipt,"accepted_at")).isBefore(Instant.parse(text(launch,"created_at"))))invalid();
    }
    private JsonSchema definition(String type){return schema.getSubSchema(new JsonNodePath(PathType.JSON_POINTER).append("$defs").append(type));}
    private static String text(JsonNode n,String field){return n.required(field).textValue();}
    private static void positive(String value){if(new BigInteger(value).compareTo(BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE))>0)invalid();}
    private static void identity(JsonNode left,JsonNode right){for(String field:IDENTITY)same(left,field,right,field);}
    private static void same(JsonNode left,String field,JsonNode right,String target){if(!left.required(field).equals(right.required(target)))invalid();}
    private static void workflow(JsonNode node){if(!(text(node,"tenant_id")+"/"+text(node,"run_id")).equals(text(node,"workflow_id")))invalid();}
    private static void invalid(){throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}
}
