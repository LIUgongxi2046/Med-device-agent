package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.*;
import com.ruoyi.medical.contracts.*;
import java.util.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;

/** Explicit closed TASK transport contract; verification schema and purpose remain unchanged. */
public final class TaskInvocationHttpSchema {
 private final FormalInvocationSchema json;private final Map<String,JsonSchema> definitions=new HashMap<>();private final SchemaCatalogue published;
 public TaskInvocationHttpSchema(FormalInvocationSchema json)throws Exception{this.json=json;published=SchemaCatalogue.fromClasspath();try(var in=Objects.requireNonNull(getClass().getResourceAsStream("/task-invocation-http.schema.json"))){var root=json.parse(new String(in.readAllBytes(),StandardCharsets.UTF_8));var factory=JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);for(String name:List.of("Reserve","BeginSend","Receipt","Unknown")){var wrapper=((com.fasterxml.jackson.databind.node.ObjectNode)root.deepCopy()).put("$ref","#/$defs/"+name);definitions.put(name,factory.getSchema(wrapper));}}try(var in=Objects.requireNonNull(getClass().getResourceAsStream("/task-invocation-recovery.schema.json"))){definitions.put("Recovery",JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(json.parse(new String(in.readAllBytes(),StandardCharsets.UTF_8))));}}
 JsonNode read(String definition,byte[] bytes)throws Exception{var n=json.parse(StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes)).toString());if(!definitions.containsKey(definition)||!definitions.get(definition).validate(n).isEmpty())throw new TenantAccessException(ErrorCode.SCHEMA_INVALID);if("Reserve".equals(definition))published.validate("model-stream",n.required("request_parameters"),"RequestParameters");return n;}
 RunTokenAuthority.ActionRequest action(JsonNode b){var n=b.required("worker_action");var s=n.required("scope");return new RunTokenAuthority.ActionRequest(t(n,"run_id"),t(n,"step_id"),t(n,"action_id"),new BigInteger(t(n,"lease_generation")),t(n,"input_sha256"),t(n,"arguments_sha256"),t(n,"kind"),t(n,"tool_version"),new PermissionProtocol.Scope(t(s,"tenant_id"),t(s,"project_id"),t(s,"resource_kind"),t(s,"resource_id")),t(n,"action"),t(n,"purpose"));}
 private static String t(JsonNode n,String k){return n.required(k).textValue();}
}
