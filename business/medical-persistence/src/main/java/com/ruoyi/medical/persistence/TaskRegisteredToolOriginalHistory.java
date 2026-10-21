package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static com.ruoyi.medical.persistence.TaskRegisteredToolAuthority.*;

/** Immutable C07 history integrity only; deliberately no current authority graph dependency. */
final class TaskRegisteredToolOriginalHistory {
    private TaskRegisteredToolOriginalHistory(){}
    static void require(JdbcTemplate db,PermissionSchema json,TaskInvocationProtocol.Request request,String actor,
            JsonNode dependencies,JsonNode entry,JsonNode pin)throws Exception {
        var n=request.node();var call=pin.required("tool_call");String tool=tool(text(call,"tool_name"));
        if("AT-08".equals(tool))throw new TenantAccessException(ErrorCode.STEP_NOT_READY);
        var schema=new TaskRegisteredToolSchema();var catalogue=SchemaCatalogue.fromClasspath();String id=TaskRegisteredToolReceipts.receiptId(n.session_id(),text(call,"tool_call_id"));
        same(id,text(pin,"tool_receipt_id"));same(n.session_id(),text(call,"session_id"));same(n.supervisor_receipt_id(),text(call,"supervision_receipt_id"));same(n.supervisor_receipt_sha256(),text(call,"supervision_sha256"));
        var response=entry.required("response");catalogue.validate("tool-success",response,null);same(tool,text(response,"tool_id"));same("SUCCEEDED",text(response,"status"));
        var document=read(db,json,schema,n,actor,id,TaskRegisteredToolReceipts.ACTION,text(response,"action_id"));var body=document.required("body");schema.validate("Result",body);
        same(text(document,"receipt_sha256"),text(pin,"tool_receipt_sha256"));same(hash(response),text(pin,"response_sha256"));same(response,body.required("response"));same(call,body.required("authority").required("tool_call"));
        for(var p:Map.ofEntries(Map.entry("tenant_id",n.tenant_id()),Map.entry("project_id",n.project_id()),Map.entry("actor_id",actor),Map.entry("run_id",n.run_id()),Map.entry("step_id",n.step_id()),Map.entry("session_id",n.session_id()),Map.entry("input_id",n.node_input_artifact_id()),Map.entry("input_sha256",n.node_input_sha256()),Map.entry("input_receipt_sha256",n.node_input_receipt_sha256()),Map.entry("receipt_id",id),Map.entry("classification",n.classification())).entrySet())same(p.getValue(),text(body,p.getKey()));
        same(dependencies.required("host_binding"),body.required("binding"));validateRequest(tool,body.required("request"),catalogue);
        same(text(call,"tool_call_id"),text(body.required("request"),"call_id"));same(text(call,"arguments_sha256"),text(body.required("request"),"arguments_sha256"));
        String start=id("TASK_REGISTERED_TOOL_STARTED",n.session_id(),text(call,"tool_call_id"));var started=read(db,json,schema,n,actor,start,TaskRegisteredToolReceipts.START,start).required("body");
        var expected=(com.fasterxml.jackson.databind.node.ObjectNode)body.deepCopy();expected.remove(List.of("response","response_sha256","physical"));expected.put("kind","TASK_REGISTERED_TOOL_STARTED").put("receipt_id",start);same(expected,started);
        if("AT-09".equals(tool)){ApprovedWebAcquisitionService.requireOriginalReceipt(db,json,n.tenant_id(),n.project_id(),actor,body);return;}
        var payload=response.required("payload");same(id,text(payload,"read_receipt_id"));same(WireValues.sha256(text(payload,"content").getBytes(StandardCharsets.UTF_8)),text(payload,"content_sha256"));
        if("AT-03".equals(tool)) {same("ARTIFACT",text(payload,"kind"));same("EVIDENCE_BUNDLE",text(payload,"content_kind"));same(catalogue.schemaRef("artifacts","EvidenceBundle"),text(payload,"content_schema_ref"));catalogue.validate("artifacts",schema.parse(text(payload,"content")),"EvidenceBundle");same(text(body.required("authority").required("predecessor"),"evidence_sha256"),text(payload,"content_sha256"));}
        else {same("SKILL",text(payload,"kind"));same(text(body.required("physical").required("pins").required("artifact"),"content_sha256"),text(payload,"content_sha256"));same(body.required("authority").required("skill").required("definition").required("request").required("source_artifact"),body.required("physical").required("pins").required("artifact"));}
    }
    private static JsonNode read(JdbcTemplate db,PermissionSchema json,TaskRegisteredToolSchema schema,TaskInvocationProtocol.Node n,String actor,String id,String action,String request)throws Exception {
        var row=one(db.queryForList("SELECT resource_ref,created_at FROM audit_event WHERE tenant_id=? AND project_id=? AND audit_id=? AND action=? AND subject_id=? AND created_by=? AND result='SUCCESS' AND reason_code IS NULL AND trace_id=? AND request_id=? AND schema_version='1.0.0' AND schema_manifest_sha256=? AND classification=? LIMIT 2",n.tenant_id(),n.project_id(),id,action,actor,actor,n.run_id(),request,schema.manifest(),n.classification()));
        var value=schema.validate("Document",schema.parse(str(row,"resource_ref")));same(hash(value.required("body")),text(value,"receipt_sha256"));same(json.canonical(value),str(row,"resource_ref"));
        var event=one(db.queryForList("SELECT payload FROM outbox_event WHERE tenant_id=? AND event_id=? AND aggregate_id=? AND aggregate_revision=1 AND event_type=? AND occurred_at=? LIMIT 2",n.tenant_id(),id,n.run_id(),action,row.get("created_at")));same(value,schema.parse(str(event,"payload")));return value;
    }
}
