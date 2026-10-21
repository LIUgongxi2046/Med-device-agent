package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import jakarta.servlet.http.HttpServletRequest;

/** Host-enforced full-scope AT-05; model-origin targeted calls are a different authority path. */
public final class TaskStrictFieldAt05Adapter {
    private final TaskStrictFieldNode node;
    public TaskStrictFieldAt05Adapter(TaskStrictFieldNode node){this.node=node;}
    public JsonNode execute(HttpServletRequest http,String token,RunTokenAuthority.ActionRequest action,JsonNode args)throws Exception {
        var receipt=node.execute(http,token,action,args);
        // Artifact identity is the actual physical FIELD_CHECK_RESULT, never the target or internal result UUID.
        var result=com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode().put("schema_version","1.0.0")
            .put("action_id",action.actionId()).put("tool_id","AT-05").put("status","SUCCEEDED")
            .put("artifact_id",receipt.required("artifact_id").textValue()).put("resource_revision",receipt.required("artifact_id").textValue())
            .put("summary","Deterministic FIELD_TARGET comparison registered; professional confirmation remains UNKNOWN.").putNull("next_cursor");
        String digest=WireValues.sha256(ContentReadAuthorization.canonicalBytes(args));result.putObject("coverage").put("requested_scope_sha256",digest).put("delivered_scope_sha256",digest).put("complete",true);
        var payload=result.putObject("payload").put("kind","RECEIPT").put("resource_kind","FIELD_CHECK")
            .put("resource_id",receipt.required("result_id").textValue()).put("resource_state",receipt.required("disposition").textValue());payload.putArray("check_ids").add(receipt.required("result_id"));
        SchemaCatalogue.fromClasspath().validate("tool-success",result,null);return result;
    }
}
